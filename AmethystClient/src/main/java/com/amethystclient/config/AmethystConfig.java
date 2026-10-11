package com.amethystclient.config;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import com.amethystclient.AmethystClient;
import com.amethystclient.esp.EspType;
import com.amethystclient.esp.GrowthType;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

public final class AmethystConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	public static AmethystConfig INSTANCE = new AmethystConfig();

	// ---- Block ESP ----
	public boolean espEnabled = true;
	public Map<String, Boolean> espTypeEnabled = new LinkedHashMap<>();
	public Map<String, Integer> espColors = new LinkedHashMap<>(); // 0xRRGGBB
	public float outlineThickness = 0.03f;
	public float outlineAlpha = 1.0f;
	public float fillAlpha = 0.12f;
	public int espRange = 128;
	public int maxBoxes = 400;
	public int espScanInterval = 10; // ticks

	// ---- Growth finder ----
	public boolean growthEnabled = true;
	public Map<String, Boolean> growthTypeEnabled = new LinkedHashMap<>();
	public Map<String, Integer> growthMinCount = new LinkedHashMap<>();
	public int growthRadius = 8;          // chunks
	public int growthChunksPerTick = 2;
	public boolean dripstoneOutsideCavesOnly = true;
	public int dripstoneMinLength = 4;
	public int chunkColor = 0xC77DFF;
	public float chunkThickness = 0.10f;
	public boolean chunkFullHeight = true;
	public boolean notifyChat = true;
	public boolean hudList = true;

	private void fillDefaults() {
		for (EspType t : EspType.values()) {
			espTypeEnabled.putIfAbsent(t.name(), true);
			espColors.putIfAbsent(t.name(), t.defaultColor);
		}
		for (GrowthType t : GrowthType.values()) {
			growthTypeEnabled.putIfAbsent(t.name(), t.defaultEnabled);
			growthMinCount.putIfAbsent(t.name(), t.defaultMinCount);
		}
		growthRadius = Math.max(2, Math.min(16, growthRadius));
		growthChunksPerTick = Math.max(1, Math.min(8, growthChunksPerTick));
		espScanInterval = Math.max(1, espScanInterval);
	}

	public boolean espOn(EspType t) { return espTypeEnabled.getOrDefault(t.name(), true); }
	public void setEspOn(EspType t, boolean v) { espTypeEnabled.put(t.name(), v); }
	public int colorOf(EspType t) { return espColors.getOrDefault(t.name(), t.defaultColor); }
	public void setColor(EspType t, int rgb) { espColors.put(t.name(), rgb & 0xFFFFFF); }

	public boolean growthOn(GrowthType t) { return growthTypeEnabled.getOrDefault(t.name(), t.defaultEnabled); }
	public void setGrowthOn(GrowthType t, boolean v) { growthTypeEnabled.put(t.name(), v); }
	public int minCount(GrowthType t) { return growthMinCount.getOrDefault(t.name(), t.defaultMinCount); }
	public void setMinCount(GrowthType t, int v) { growthMinCount.put(t.name(), Math.max(1, v)); }

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("amethystclient.json");
	}

	public static void load() {
		Path p = path();
		if (Files.exists(p)) {
			try (Reader r = Files.newBufferedReader(p)) {
				AmethystConfig c = GSON.fromJson(r, AmethystConfig.class);
				if (c != null) INSTANCE = c;
			} catch (Exception e) {
				AmethystClient.LOGGER.warn("Could not read config, using defaults", e);
			}
		}
		INSTANCE.fillDefaults();
		INSTANCE.save();
	}

	public void save() {
		try (Writer w = Files.newBufferedWriter(path())) {
			GSON.toJson(this, w);
		} catch (Exception e) {
			AmethystClient.LOGGER.warn("Could not save config", e);
		}
	}
}
