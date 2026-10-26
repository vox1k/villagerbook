package io.github.vox1k.villagerbook;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** /vb find &lt;text&gt;, /vb list, /vb */
final class VbCommand {
	private static final int MAX_RESULTS = 8;

	private VbCommand() {
	}

	static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
			dispatcher.register(ClientCommands.literal("vb")
				.then(ClientCommands.literal("find")
					.then(ClientCommands.argument("query", StringArgumentType.greedyString())
						.executes(ctx -> find(ctx.getSource(), StringArgumentType.getString(ctx, "query")))))
				.then(ClientCommands.literal("list")
					.executes(ctx -> list(ctx.getSource())))
				.executes(ctx -> help(ctx.getSource()))));
	}

	private static int help(FabricClientCommandSource src) {
		src.sendFeedback(Component.literal("VillagerBook - записная книжка торговцев").withStyle(ChatFormatting.GOLD));
		src.sendFeedback(Component.literal("/vb find <текст> - найти, где продают предмет или зачарование (mending, алмаз, починка...)").withStyle(ChatFormatting.GRAY));
		src.sendFeedback(Component.literal("/vb list - сколько жителей записано").withStyle(ChatFormatting.GRAY));
		src.sendFeedback(Component.literal("Чтобы житель попал в книгу, просто откройте у него окно торговли.").withStyle(ChatFormatting.GRAY));
		return 1;
	}

	private static int list(FabricClientCommandSource src) {
		Store store = Store.current(Minecraft.getInstance());
		if (store.villagerCount() == 0) {
			src.sendFeedback(Component.literal("Пока никого нет. Откройте окно торговли у жителя.").withStyle(ChatFormatting.YELLOW));
			return 0;
		}
		Map<String, Integer> byProfession = new TreeMap<>();
		int trades = 0;
		for (Models.VillagerInfo v : store.all()) {
			byProfession.merge(v.profession == null ? "?" : v.profession, 1, Integer::sum);
			trades += v.trades.size();
		}
		src.sendFeedback(Component.literal("Записано жителей: " + store.villagerCount() + ", сделок: " + trades).withStyle(ChatFormatting.GREEN));
		for (Map.Entry<String, Integer> e : byProfession.entrySet()) {
			src.sendFeedback(Component.literal("  " + e.getKey() + ": " + e.getValue()).withStyle(ChatFormatting.GRAY));
		}
		return store.villagerCount();
	}

	private static int find(FabricClientCommandSource src, String query) {
		if (query.isBlank()) {
			return help(src);
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) {
			return 0;
		}
		Store store = Store.current(mc);
		var pos = mc.player.blockPosition();
		List<Store.Hit> hits = store.search(query, Recorder.dimensionName(mc.level), pos.getX(), pos.getZ());

		if (hits.isEmpty()) {
			src.sendFeedback(Component.literal("Ничего не нашлось по запросу \"" + query + "\". Записано жителей: "
				+ store.villagerCount()).withStyle(ChatFormatting.YELLOW));
			return 0;
		}

		int shown = Math.min(hits.size(), MAX_RESULTS);
		src.sendFeedback(Component.literal("Найдено: " + hits.size() + " (ближайшие сверху)").withStyle(ChatFormatting.GOLD));
		for (int i = 0; i < shown; i++) {
			Store.Hit hit = hits.get(i);
			Models.VillagerInfo v = hit.villager();
			Models.Trade t = hit.trade();

			String price = t.costA.describe() + (t.costB != null ? " + " + t.costB.describe() : "");
			String line1 = (i + 1) + ". " + v.profession + " (ур. " + v.level + "): "
				+ t.sell.describe() + " за " + price + (t.outOfStock ? " (распродано)" : "");

			String distance = hit.distanceSq() == Long.MAX_VALUE
				? "другое измерение"
				: (int) Math.sqrt(hit.distanceSq()) + " блоков";
			String line2 = "    " + dimensionLabel(v.dimension) + ", " + v.x + " " + v.y + " " + v.z
				+ " (" + distance + "), " + age(v.lastSeen);

			src.sendFeedback(Component.literal(line1).withStyle(ChatFormatting.GREEN));
			src.sendFeedback(Component.literal(line2).withStyle(ChatFormatting.GRAY));
		}
		if (hits.size() > shown) {
			src.sendFeedback(Component.literal("...и ещё " + (hits.size() - shown) + ". Уточните запрос.").withStyle(ChatFormatting.GRAY));
		}
		return hits.size();
	}

	private static String dimensionLabel(String dimension) {
		if (dimension == null) {
			return "?";
		}
		return switch (dimension) {
			case "minecraft:overworld" -> "Верхний мир";
			case "minecraft:the_nether" -> "Незер";
			case "minecraft:the_end" -> "Энд";
			default -> dimension;
		};
	}

	private static String age(long millis) {
		long minutes = Math.max(0, (System.currentTimeMillis() - millis) / 60_000);
		if (minutes < 60) {
			return minutes + " мин назад";
		}
		long hours = minutes / 60;
		if (hours < 48) {
			return hours + " ч назад";
		}
		return (hours / 24) + " дн назад";
	}
}
