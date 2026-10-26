package io.github.vox1k.villagerbook;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

/** Villagers known on one server, saved to config/villagerbook/&lt;server&gt;.json. */
final class Store {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type LIST_TYPE = new TypeToken<List<Models.VillagerInfo>>() {
	}.getType();

	private static Store cached;
	private static String cachedKey;

	private final Path file;
	private final Map<String, Models.VillagerInfo> villagers = new LinkedHashMap<>();

	/** One search result: a trade together with the villager that offers it. */
	record Hit(Models.VillagerInfo villager, Models.Trade trade, long distanceSq) {
	}

	private Store(Path file) {
		this.file = file;
	}

	/** The store for the server the player is connected to right now. */
	static Store current(Minecraft mc) {
		String key = serverKey(mc);
		if (cached == null || !key.equals(cachedKey)) {
			Path dir = FabricLoader.getInstance().getConfigDir().resolve("villagerbook");
			cached = new Store(dir.resolve(key + ".json"));
			cached.load();
			cachedKey = key;
		}
		return cached;
	}

	private static String serverKey(Minecraft mc) {
		ServerData server = mc.getCurrentServer();
		String raw = (server != null && server.ip != null) ? server.ip : "singleplayer";
		String safe = raw.replaceAll("[^a-zA-Z0-9._-]", "_");
		return safe.length() > 100 ? safe.substring(0, 100) : safe;
	}

	private void load() {
		if (!Files.exists(file)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(file)) {
			List<Models.VillagerInfo> list = GSON.fromJson(reader, LIST_TYPE);
			if (list != null) {
				for (Models.VillagerInfo v : list) {
					if (v != null && v.uuid != null) {
						villagers.put(v.uuid, v);
					}
				}
			}
		} catch (IOException | RuntimeException e) {
			VillagerBookClient.LOGGER.warn("Could not read {}, moving it aside", file, e);
			try {
				Files.move(file, file.resolveSibling(file.getFileName() + ".broken"), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException ignored) {
				// nothing more we can do
			}
			villagers.clear();
		}
	}

	private void save() {
		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
			try (Writer writer = Files.newBufferedWriter(tmp)) {
				GSON.toJson(new ArrayList<>(villagers.values()), LIST_TYPE, writer);
			}
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			VillagerBookClient.LOGGER.warn("Could not save {}", file, e);
		}
	}

	/** Stores the villager. Returns true if it is new or its level or trades changed. */
	boolean put(Models.VillagerInfo info) {
		Models.VillagerInfo old = villagers.get(info.uuid);
		boolean changed = old == null
			|| old.level != info.level
			|| !GSON.toJson(old.trades).equals(GSON.toJson(info.trades));
		villagers.put(info.uuid, info);
		save();
		return changed;
	}

	Collection<Models.VillagerInfo> all() {
		return villagers.values();
	}

	int villagerCount() {
		return villagers.size();
	}

	/** Finds trades whose result item matches every word of the query; nearest first. */
	List<Hit> search(String query, String dimension, int px, int pz) {
		String[] words = query.toLowerCase().trim().split("\\s+");
		List<Hit> hits = new ArrayList<>();
		for (Models.VillagerInfo v : villagers.values()) {
			for (Models.Trade t : v.trades) {
				if (t.sell == null) {
					continue;
				}
				String haystack = t.sell.searchText();
				boolean matches = true;
				for (String word : words) {
					if (!haystack.contains(word)) {
						matches = false;
						break;
					}
				}
				if (matches) {
					long distanceSq = Long.MAX_VALUE;
					if (dimension != null && dimension.equals(v.dimension)) {
						long dx = (long) v.x - px;
						long dz = (long) v.z - pz;
						distanceSq = dx * dx + dz * dz;
					}
					hits.add(new Hit(v, t, distanceSq));
				}
			}
		}
		hits.sort(Comparator.comparingLong(Hit::distanceSq));
		return hits;
	}
}
