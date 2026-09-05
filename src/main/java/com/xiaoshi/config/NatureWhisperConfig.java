package com.xiaoshi.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.xiaoshi.NatureWhisper;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** JSON configuration for NatureWhisper, stored at config/naturewhisper.json. */
public final class NatureWhisperConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public boolean darkerNights = true;
	public double nightDarkness = 1.0; // 0..1 extra darkness applied at night
	public double maxRenderMagnitude = 10.0; // stars brighter than this are rendered (catalog has all)

	// Post effects — not yet wired to rendering (motion blur & depth of field are GPU post passes).
	public boolean motionBlurEnabled = false;
	public double motionBlurStrength = 0.5;
	public boolean depthOfFieldEnabled = false;
	public double depthOfFieldStrength = 0.6;

	// Handheld lighting: a held light-emitting block item lights its surroundings client-side, so
	// modded clients see each other's lights.
	public boolean handheldLighting = true;

	private static NatureWhisperConfig instance = new NatureWhisperConfig();

	public static NatureWhisperConfig get() {
		return instance;
	}

	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("naturewhisper.json");
	}

	public static void load() {
		Path file = path();
		if (!Files.exists(file)) {
			save();
			return;
		}
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			NatureWhisperConfig loaded = GSON.fromJson(reader, NatureWhisperConfig.class);
			if (loaded != null) {
				instance = loaded;
			}
		} catch (IOException | RuntimeException exception) {
			NatureWhisper.LOGGER.warn("Failed to read naturewhisper.json; using defaults", exception);
		}
	}

	public static void save() {
		try {
			Files.createDirectories(path().getParent());
			try (Writer writer = Files.newBufferedWriter(path(), StandardCharsets.UTF_8)) {
				GSON.toJson(instance, writer);
			}
		} catch (IOException exception) {
			NatureWhisper.LOGGER.warn("Failed to write naturewhisper.json", exception);
		}
	}
}
