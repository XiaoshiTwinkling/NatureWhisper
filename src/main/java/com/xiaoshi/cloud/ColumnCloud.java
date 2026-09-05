package com.xiaoshi.cloud;

import net.minecraft.world.chunk.WorldChunk;

/**
 * Cloud parameters attached to each {@link WorldChunk} (16×16 column). The state lives on the
 * chunk and is read/written by casting: {@code (ColumnCloud) world.getChunk(x, z)}.
 *
 * <p>Seeded once at chunk load from the biome climate (see {@link CloudFieldPopulator}), then
 * evolved over time by {@link CloudSimulator}: coverage advects with the wind while relaxing
 * towards a climate-derived setpoint. Server and client run the same deterministic algorithm.
 */
public interface ColumnCloud {
	/** Coverage 0..1 of one macro cell (localCellX/Z in [0, CELLS_PER_AXIS)). */
	float naturewhisper$getCoverage(int localCellX, int localCellZ);

	void naturewhisper$setCoverage(int localCellX, int localCellZ, float coverage);

	/** Coarse cloud type: 0 clear, 1 stratus, 2 cumulus, 3 cumulonimbus. */
	float naturewhisper$getCloudType();

	void naturewhisper$setCloudType(float cloudType);

	/** Horizontal patch scale 0..1 (fine puffs .. broad sheets). */
	float naturewhisper$getCloudScale();

	void naturewhisper$setCloudScale(float cloudScale);

	/** Equilibrium coverage this column relaxes towards, 0..1. */
	float naturewhisper$getSetpoint();

	void naturewhisper$setSetpoint(float setpoint);

	/** True when the column's surface top is open water. */
	boolean naturewhisper$isSurfaceWater();

	void naturewhisper$setSurfaceWater(boolean surfaceWater);
}
