package com.xiaoshi.climate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

/**
 * Fills each sub-chunk's temperature/humidity from the biome field of a newly loaded chunk and
 * smooths it across neighbouring sub-chunks.
 *
 * <p>Since 1.20.5 vanilla no longer exposes a per-biome humidity number (the stored "downfall"
 * lives in a private nested record), humidity is derived here from stable public biome properties:
 * the {@link Biome.Precipitation} type plus {@link Biome#getTemperature()}. Temperature uses the
 * public biome temperature (raw 0..2 scale). A base value is computed for a sub-chunk by averaging
 * over its 4×4×4 biome cells, then every section is re-assigned as a weighted average (1/2
 * separable kernel) of the base values of the loaded 3×3×3 sub-chunk neighbourhood around it.
 * Because the same deterministic function runs whenever a chunk in a region loads (and every chunk
 * re-smooths its own ±1 chunk columns), a fully loaded area converges to a smoothly varying field
 * and biome borders are spread over a few sub-chunks instead of jumping.
 *
 * <p>The computation is world-side agnostic: both the server and the client run it, so the F3+G
 * overlay can show the same values without a network round-trip (yet).
 */
public final class SectionClimatePopulator {
	/** Converts a raw biome temperature (0..2) into the °C-ish scale used by SectionClimate. */
	private static final double RAW_TEMP_TO_CELSIUS = 20.0;

	private static final int BIOME_CELLS = 4;

	/** Radius (chunks/sections) of the climate smoothing window; larger = smoother, wider-range fields. */
	private static final int SMOOTH_RADIUS = 2;

	/**
	 * Separable axis weight for a smoothing offset. The kernel spans ±SMOOTH_RADIUS so adjacent
	 * sub-chunks share a wide influence and the resulting field changes gradually.
	 */
	private static double axisWeight(int offset) {
		switch (Math.abs(offset)) {
			case 0:
				return 3.0;
			case 1:
				return 2.0;
			case 2:
				return 1.0;
			default:
				return 0.0;
		}
	}

	private SectionClimatePopulator() {
	}

	/** A loaded chunk column together with the precomputed per-section base climate. */
	private static final class Column {
		final int chunkX;
		final int chunkZ;
		final WorldChunk chunk;
		final double[] baseTemperature;
		final double[] baseHumidity;

		Column(int chunkX, int chunkZ, WorldChunk chunk) {
			this.chunkX = chunkX;
			this.chunkZ = chunkZ;
			this.chunk = chunk;
			int count = chunk.getSectionArray().length;
			this.baseTemperature = new double[count];
			this.baseHumidity = new double[count];
		}
	}

	/**
	 * Re-smooths every loaded chunk column within ±1 of (chunkX, chunkZ). The loader must return
	 * the already-loaded column for a position, or {@code null} when it is not loaded, and must not
	 * trigger generation.
	 */
	public static void refreshAround(BiFunction<Integer, Integer, WorldChunk> getLoadedChunk, int chunkX, int chunkZ) {
		List<Column> columns = new ArrayList<>();
		for (int dx = -SMOOTH_RADIUS; dx <= SMOOTH_RADIUS; dx++) {
			for (int dz = -SMOOTH_RADIUS; dz <= SMOOTH_RADIUS; dz++) {
				WorldChunk chunk = getLoadedChunk.apply(chunkX + dx, chunkZ + dz);
				if (chunk != null) {
					columns.add(new Column(chunkX + dx, chunkZ + dz, chunk));
				}
			}
		}
		if (columns.isEmpty()) {
			return;
		}

		Map<Long, Column> byPosition = new HashMap<>();
		for (Column column : columns) {
			computeBase(column);
			byPosition.put(ChunkPos.toLong(column.chunkX, column.chunkZ), column);
		}

		for (Column column : columns) {
			smoothColumn(byPosition, column);
		}
	}

	/** Base climate of a section = mean over its 4×4×4 biome cells. */
	private static void computeBase(Column column) {
		ChunkSection[] sections = column.chunk.getSectionArray();
		for (int i = 0; i < sections.length; i++) {
			ChunkSection section = sections[i];
			if (section == null) {
				continue;
			}
			double temperature = 0.0;
			double humidity = 0.0;
			for (int lx = 0; lx < BIOME_CELLS; lx++) {
				for (int ly = 0; ly < BIOME_CELLS; ly++) {
					for (int lz = 0; lz < BIOME_CELLS; lz++) {
						RegistryEntry<Biome> entry = section.getBiome(lx, ly, lz);
						Biome biome = entry.value();
						temperature += biome.getTemperature();
						humidity += humidityOf(biome);
					}
				}
			}
			int cellCount = BIOME_CELLS * BIOME_CELLS * BIOME_CELLS;
			column.baseTemperature[i] = temperature / cellCount;
			column.baseHumidity[i] = humidity / cellCount;
		}
	}

	private static void smoothColumn(Map<Long, Column> columns, Column target) {
		ChunkSection[] sections = target.chunk.getSectionArray();
		for (int i = 0; i < sections.length; i++) {
			ChunkSection section = sections[i];
			if (!(section instanceof SectionClimate climate)) {
				continue;
			}
			double weightSum = 0.0;
			double temperature = 0.0;
			double humidity = 0.0;
			for (int dx = -SMOOTH_RADIUS; dx <= SMOOTH_RADIUS; dx++) {
				for (int dz = -SMOOTH_RADIUS; dz <= SMOOTH_RADIUS; dz++) {
					Column neighbour = columns.get(ChunkPos.toLong(target.chunkX + dx, target.chunkZ + dz));
					if (neighbour == null) {
						continue;
					}
					double horizontalWeight = axisWeight(dx) * axisWeight(dz);
					for (int dy = -SMOOTH_RADIUS; dy <= SMOOTH_RADIUS; dy++) {
						int neighbourIndex = i + dy;
						if (neighbourIndex < 0 || neighbourIndex >= neighbour.baseTemperature.length) {
							continue;
						}
						double weight = horizontalWeight * axisWeight(dy);
						weightSum += weight;
						temperature += weight * neighbour.baseTemperature[neighbourIndex];
						humidity += weight * neighbour.baseHumidity[neighbourIndex];
					}
				}
			}
			if (weightSum <= 0.0) {
				continue;
			}
			climate.naturewhisper$setBaseTemperature((float) (temperature / weightSum * RAW_TEMP_TO_CELSIUS));
			climate.naturewhisper$setTemperature(climate.naturewhisper$getBaseTemperature());
			climate.naturewhisper$setBaseHumidity((float) Math.max(0.0, Math.min(1.0, humidity / weightSum)));
			climate.naturewhisper$setHumidity(climate.naturewhisper$getBaseHumidity());
		}
	}

	/**
	 * Humidity proxy derived from stable public biome properties. Biomes that never rain/snow are
	 * dry, snowy biomes get a mid value, and rainy biomes get progressively more humid the warmer
	 * they are (tropics vs cool forests). Vanilla 1.21.1 keeps its real 0..1 "downfall" in a
	 * private nested record, so it is not reachable from a mod.
	 */
	private static double humidityOf(Biome biome) {
		double temperature = biome.getTemperature();
		Biome.Precipitation precipitation = biome.getPrecipitation(BlockPos.ORIGIN);
		if (precipitation == Biome.Precipitation.NONE) {
			return 0.15;
		}
		if (precipitation == Biome.Precipitation.SNOW) {
			return 0.55;
		}
		// RAIN: warmer biomes carry more moisture in the air.
		return Math.min(1.0, 0.65 + Math.max(0.0, temperature - 0.3) * 0.35);
	}
}
