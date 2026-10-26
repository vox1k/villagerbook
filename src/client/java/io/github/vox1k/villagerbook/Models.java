package io.github.vox1k.villagerbook;

import java.util.ArrayList;
import java.util.List;

/**
 * Plain data classes that are saved to JSON with Gson.
 * Fields are public on purpose: it keeps the saved file simple and Gson-friendly.
 */
public final class Models {
	private Models() {
	}

	public static class Ench {
		public String id;    // e.g. "minecraft:mending"
		public String name;  // localized name at the time of recording
		public int level;
	}

	public static class ItemInfo {
		public String id;    // e.g. "minecraft:enchanted_book"
		public String name;  // localized display name at the time of recording
		public int count;
		public List<Ench> enchants = new ArrayList<>();

		/** Text used by /vb find: item id, name, and every enchantment id and name. */
		public String searchText() {
			StringBuilder sb = new StringBuilder();
			sb.append(id).append(' ').append(name);
			for (Ench e : enchants) {
				sb.append(' ').append(e.id).append(' ').append(e.name);
			}
			return sb.toString().toLowerCase();
		}

		/** Short human readable form, e.g. "Enchanted Book [Mending 1]". */
		public String describe() {
			StringBuilder sb = new StringBuilder();
			if (count > 1) {
				sb.append(count).append("x ");
			}
			sb.append(name);
			if (!enchants.isEmpty()) {
				sb.append(" [");
				for (int i = 0; i < enchants.size(); i++) {
					if (i > 0) {
						sb.append(", ");
					}
					Ench e = enchants.get(i);
					sb.append(e.name).append(' ').append(e.level);
				}
				sb.append(']');
			}
			return sb.toString();
		}
	}

	public static class Trade {
		public ItemInfo sell;
		public ItemInfo costA;
		public ItemInfo costB; // null when the trade has only one cost item
		public boolean outOfStock;
	}

	public static class VillagerInfo {
		public String uuid;
		public String kind;        // "minecraft:villager" or "minecraft:wandering_trader"
		public String profession;  // trade screen title, localized
		public int level;
		public String dimension;   // e.g. "minecraft:overworld"
		public int x;
		public int y;
		public int z;
		public long lastSeen;      // epoch millis
		public List<Trade> trades = new ArrayList<>();
	}
}
