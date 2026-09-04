package com.xiaoshi.climate;

import net.minecraft.world.chunk.ChunkSection;

/**
 * Climate attributes attached to every {@link ChunkSection} (16×16×16 sub-chunk).
 *
 * <p>Implemented by {@link com.xiaoshi.mixin.ChunkSectionMixin}; obtain an instance by
 * casting any chunk section: {@code (SectionClimate) section}.
 *
 * <p>Each section stores an immutable biome-derived {@code base} value (temperature °C, humidity
 * 0..1, seeded once at chunk load by {@link SectionClimatePopulator}) plus a live value that
 * {@link ClimateSimulator} re-derives every second from {@code base}, the real block column, and
 * the time of day. Wind (direction + strength) is likewise recomputed by the simulator.
 */
public interface SectionClimate {
	/** Placeholder default temperature, °C. */
	float DEFAULT_TEMPERATURE = 15.0F;
	/** Placeholder default relative humidity, 0..1. */
	float DEFAULT_HUMIDITY = 0.5F;
	/** Default unit wind direction, pointing +X (east). */
	float DEFAULT_WIND_DIRECTION_X = 1.0F;
	float DEFAULT_WIND_DIRECTION_Z = 0.0F;
	/** Placeholder default wind speed, m/s. */
	float DEFAULT_WIND_STRENGTH = 0.0F;

	/** Immutable (after chunk load) biome-derived base temperature, °C. */
	float naturewhisper$getBaseTemperature();

	void naturewhisper$setBaseTemperature(float baseTemperature);

	/** Immutable (after chunk load) biome-derived base humidity, 0..1. */
	float naturewhisper$getBaseHumidity();

	void naturewhisper$setBaseHumidity(float baseHumidity);

	float naturewhisper$getTemperature();

	void naturewhisper$setTemperature(float temperature);

	float naturewhisper$getHumidity();

	void naturewhisper$setHumidity(float humidity);

	/** X component of the unit wind direction vector. */
	float naturewhisper$getWindDirectionX();

	/** Z component of the unit wind direction vector. */
	float naturewhisper$getWindDirectionZ();

	/** Set both components of the unit wind direction vector. */
	void naturewhisper$setWindDirection(float x, float z);

	/** Wind strength (magnitude) scalar. */
	float naturewhisper$getWindStrength();

	void naturewhisper$setWindStrength(float windStrength);
}
