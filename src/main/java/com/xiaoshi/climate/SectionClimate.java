package com.xiaoshi.climate;

import net.minecraft.world.chunk.ChunkSection;

/**
 * Climate attributes attached to every {@link ChunkSection} (16×16×16 sub-chunk).
 *
 * <p>Implemented by {@link com.xiaoshi.mixin.ChunkSectionMixin}; obtain an instance by
 * casting any chunk section: {@code (SectionClimate) section}.
 *
 * <p>The numeric scales below are placeholders chosen for a natural/real-world flavour
 * (temperature in °C, humidity 0..1, wind speed in m/s) and are not wired to any logic yet.
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
