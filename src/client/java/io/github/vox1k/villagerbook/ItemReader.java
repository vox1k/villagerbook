package io.github.vox1k.villagerbook;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/** Turns an ItemStack into a small serializable snapshot (id, name, count, enchantments). */
final class ItemReader {
	private ItemReader() {
	}

	static Models.ItemInfo read(ItemStack stack) {
		Models.ItemInfo info = new Models.ItemInfo();
		info.id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		info.name = stack.getHoverName().getString();
		info.count = stack.getCount();
		// Enchanted books keep their enchantments in STORED_ENCHANTMENTS, tools and armor in ENCHANTMENTS.
		addEnchants(info, stack.get(DataComponents.STORED_ENCHANTMENTS));
		addEnchants(info, stack.get(DataComponents.ENCHANTMENTS));
		return info;
	}

	private static void addEnchants(Models.ItemInfo info, ItemEnchantments enchantments) {
		if (enchantments == null || enchantments.isEmpty()) {
			return;
		}
		for (var entry : enchantments.entrySet()) {
			Models.Ench e = new Models.Ench();
			e.id = entry.getKey().getRegisteredName();
			e.level = entry.getIntValue();
			e.name = enchantmentName(e.id);
			info.enchants.add(e);
		}
	}

	/** "minecraft:mending" -> translation key "enchantment.minecraft.mending" -> localized name. */
	private static String enchantmentName(String id) {
		int colon = id.indexOf(':');
		String namespace = colon >= 0 ? id.substring(0, colon) : "minecraft";
		String path = colon >= 0 ? id.substring(colon + 1) : id;
		String key = "enchantment." + namespace + "." + path.replace('/', '.');
		String translated = Component.translatable(key).getString();
		return translated.equals(key) ? path : translated;
	}
}
