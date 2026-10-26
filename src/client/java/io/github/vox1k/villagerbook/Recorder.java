package io.github.vox1k.villagerbook;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;

/**
 * Records villagers and their trades.
 *
 * How it works: the trade packet does not say which villager it belongs to, so we remember the
 * entity the player right-clicked last. When a trade screen opens a moment later, its trades are
 * attached to that entity. Everything recorded is exactly what the server already showed the player.
 */
final class Recorder {
	/** A right-click is attached to a trade screen only if the screen opens within this time. */
	private static final long CLICK_WINDOW_MS = 10_000;

	private record Clicked(String uuid, String kind, String dimension, int x, int y, int z, long at) {
	}

	private static Clicked lastClicked;
	private static Clicked boundTo;
	private static MerchantMenu lastMenu;
	private static String lastSignature = "";

	private Recorder() {
	}

	static void init() {
		UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			// The event also fires on the integrated server thread; we only care about the client side.
			if (level.isClientSide()) {
				onUseEntity(level, entity);
			}
			return InteractionResult.PASS;
		});
		ClientTickEvents.END_CLIENT_TICK.register(Recorder::onTick);
	}

	private static void onUseEntity(Level level, Entity entity) {
		var pos = entity.blockPosition();
		lastClicked = new Clicked(
			entity.getUUID().toString(),
			BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(),
			dimensionName(level),
			pos.getX(), pos.getY(), pos.getZ(),
			System.currentTimeMillis());
	}

	private static void onTick(Minecraft client) {
		if (client.player == null || client.level == null) {
			return;
		}
		if (!(client.gui.screen() instanceof MerchantScreen screen)) {
			return;
		}
		MerchantMenu menu = screen.getMenu();
		var offers = menu.getOffers();
		// The trades arrive in a packet just after the screen opens, so the list is empty for a moment.
		if (offers.isEmpty()) {
			return;
		}

		if (menu != lastMenu) {
			// A new trade screen: decide which villager it belongs to.
			lastMenu = menu;
			lastSignature = "";
			boolean recentClick = lastClicked != null
				&& System.currentTimeMillis() - lastClicked.at() < CLICK_WINDOW_MS;
			boundTo = recentClick ? lastClicked : null;
		}
		if (boundTo == null) {
			return;
		}

		// Only save again when something actually changed (for example after the villager levels up).
		StringBuilder signature = new StringBuilder().append(menu.getTraderLevel());
		for (MerchantOffer offer : offers) {
			signature.append('|')
				.append(offer.getResult().getCount()).append(',')
				.append(offer.getCostA().getCount()).append(',')
				.append(offer.isOutOfStock());
		}
		String sig = signature.toString();
		if (sig.equals(lastSignature)) {
			return;
		}
		lastSignature = sig;

		Models.VillagerInfo info = new Models.VillagerInfo();
		info.uuid = boundTo.uuid();
		info.kind = boundTo.kind();
		info.profession = screen.getTitle().getString();
		info.level = menu.getTraderLevel();
		info.dimension = boundTo.dimension();
		info.x = boundTo.x();
		info.y = boundTo.y();
		info.z = boundTo.z();
		info.lastSeen = System.currentTimeMillis();
		for (MerchantOffer offer : offers) {
			Models.Trade trade = new Models.Trade();
			trade.sell = ItemReader.read(offer.getResult());
			trade.costA = ItemReader.read(offer.getCostA());
			ItemStack costB = offer.getCostB();
			trade.costB = costB.isEmpty() ? null : ItemReader.read(costB);
			trade.outOfStock = offer.isOutOfStock();
			info.trades.add(trade);
		}

		boolean changed = Store.current(client).put(info);
		if (changed) {
			client.player.sendSystemMessage(Component.literal(
				"[VillagerBook] Записано: " + info.profession + ", сделок: " + info.trades.size()));
		}
	}

	/** "minecraft:overworld", "minecraft:the_nether", ... */
	static String dimensionName(Level level) {
		// ResourceKey prints as "ResourceKey[minecraft:dimension / minecraft:overworld]".
		String s = String.valueOf(level.dimension());
		int slash = s.lastIndexOf(" / ");
		int end = s.lastIndexOf(']');
		return (slash >= 0 && end > slash) ? s.substring(slash + 3, end) : s;
	}
}
