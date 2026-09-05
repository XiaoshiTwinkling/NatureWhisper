package com.xiaoshi.cloud;

import com.xiaoshi.climate.SectionClimate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

/**
 * Evolves the per-chunk cloud coverage field each second.
 *
 * <p>Each macro cell is advected semi-Lagrangian: its centre is back-traced by the wind at the
 * cloud layer and the coverage is sampled bilinearly from the surrounding macro cells. The
 * advected value is then relaxed towards the column's climate-derived setpoint (clouds condense
 * where it is humid/cool/watery, evaporate where it is dry/warm). When the back-trace would leave
 * the loaded area the cell is not advected and only relaxes, so cloud mass recycles instead of
 * draining at the edge of the loaded region.
 *
 * <p>The wind used is the existing climate wind of the air section at the cloud-layer altitude,
 * which the {@link com.xiaoshi.climate.ClimateSimulator} has just recomputed. Because the server
 * and the client run the same steps on the same clock, the coverage field converges without any
 * network traffic.
 */
public final class CloudSimulator {
	/** Coverage relaxation rate per 20-tick step. */
	private static final double RELAX = 0.05;

	private CloudSimulator() {
	}

	private static final class Column {
		final int chunkX;
		final int chunkZ;
		final WorldChunk chunk;
		final float[] snapshot = new float[CloudConstants.CELLS_PER_COLUMN];
		final float[] next = new float[CloudConstants.CELLS_PER_COLUMN];
		float setpoint;

		Column(WorldChunk chunk) {
			this.chunk = chunk;
			this.chunkX = chunk.getPos().x;
			this.chunkZ = chunk.getPos().z;
		}
	}

	/**
	 * Advances coverage by one cadence for every loaded column within {@code radius} chunks of
	 * (centreChunkX, centreChunkZ). Loader must return loaded columns or null (never generate).
	 */
	public static void step(World world, BiFunction<Integer, Integer, WorldChunk> getLoadedChunk, int centreChunkX, int centreChunkZ, int radius) {
		int cloudSectionIndex = world.getSectionIndex(CloudConstants.CLOUD_LAYER_CENTER_Y);
		if (cloudSectionIndex < 0 || cloudSectionIndex >= world.countVerticalSections()) {
			return;
		}

		List<Column> columns = new ArrayList<>();
		Map<Long, Column> byPosition = new HashMap<>();
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				int chunkX = centreChunkX + dx;
				int chunkZ = centreChunkZ + dz;
				WorldChunk chunk = getLoadedChunk.apply(chunkX, chunkZ);
				if (chunk == null || !(chunk instanceof ColumnCloud cloud)) {
					continue;
				}
				Column column = new Column(chunk);
				column.setpoint = cloud.naturewhisper$getSetpoint();
				for (int lx = 0; lx < CloudConstants.CELLS_PER_AXIS; lx++) {
					for (int lz = 0; lz < CloudConstants.CELLS_PER_AXIS; lz++) {
						column.snapshot[lx * CloudConstants.CELLS_PER_AXIS + lz] = cloud.naturewhisper$getCoverage(lx, lz);
					}
				}
				columns.add(column);
				byPosition.put(ChunkPos.toLong(chunkX, chunkZ), column);
			}
		}
		if (columns.isEmpty()) {
			return;
		}

		for (Column column : columns) {
			double windX = 0.0;
			double windZ = 0.0;
			ChunkSection cloudSection = column.chunk.getSection(cloudSectionIndex);
			if (cloudSection instanceof SectionClimate climate) {
				double dirX = climate.naturewhisper$getWindDirectionX();
				double dirZ = climate.naturewhisper$getWindDirectionZ();
				double strength = climate.naturewhisper$getWindStrength();
				double length = Math.sqrt(dirX * dirX + dirZ * dirZ);
				if (length > 1.0E-4) {
					windX = dirX / length * strength;
					windZ = dirZ / length * strength;
				}
			}
			for (int lx = 0; lx < CloudConstants.CELLS_PER_AXIS; lx++) {
				for (int lz = 0; lz < CloudConstants.CELLS_PER_AXIS; lz++) {
					double centreX = column.chunkX * 16.0 + lx * CloudConstants.CELL_SIZE_BLOCKS + CloudConstants.CELL_SIZE_BLOCKS / 2.0;
					double centreZ = column.chunkZ * 16.0 + lz * CloudConstants.CELL_SIZE_BLOCKS + CloudConstants.CELL_SIZE_BLOCKS / 2.0;
					// One 20-tick step = 1 second of drift.
					double sampled = sampleContinuous(byPosition, centreX - windX, centreZ - windZ);
					if (Double.isNaN(sampled)) {
						// Back-trace left the loaded area: do not advect, just relax in place.
						sampled = column.snapshot[lx * CloudConstants.CELLS_PER_AXIS + lz];
					}
					double value = sampled + (column.setpoint - sampled) * RELAX;
					column.next[lx * CloudConstants.CELLS_PER_AXIS + lz] = (float) Math.max(0.0, Math.min(1.0, value));
				}
			}
		}

		for (Column column : columns) {
			ColumnCloud cloud = (ColumnCloud) column.chunk;
			for (int lx = 0; lx < CloudConstants.CELLS_PER_AXIS; lx++) {
				for (int lz = 0; lz < CloudConstants.CELLS_PER_AXIS; lz++) {
					cloud.naturewhisper$setCoverage(lx, lz, column.next[lx * CloudConstants.CELLS_PER_AXIS + lz]);
				}
			}
		}
	}

	/** Bilinear coverage sample at a world XZ position in macro-cell space; NaN when unloaded. */
	private static double sampleContinuous(Map<Long, Column> columns, double worldX, double worldZ) {
		double fx = worldX / CloudConstants.CELL_SIZE_BLOCKS;
		double fz = worldZ / CloudConstants.CELL_SIZE_BLOCKS;
		int i0 = (int) Math.floor(fx);
		int j0 = (int) Math.floor(fz);
		double ux = fx - i0;
		double uz = fz - j0;
		double c00 = coverageAt(columns, i0, j0);
		double c10 = coverageAt(columns, i0 + 1, j0);
		double c01 = coverageAt(columns, i0, j0 + 1);
		double c11 = coverageAt(columns, i0 + 1, j0 + 1);
		if (Double.isNaN(c00) || Double.isNaN(c10) || Double.isNaN(c01) || Double.isNaN(c11)) {
			return Double.NaN;
		}
		return c00 * (1.0 - ux) * (1.0 - uz)
			+ c10 * ux * (1.0 - uz)
			+ c01 * (1.0 - ux) * uz
			+ c11 * ux * uz;
	}

	/** Coverage of one macro cell by its global macro index; NaN when that column is not loaded. */
	private static double coverageAt(Map<Long, Column> columns, int macroX, int macroZ) {
		int chunkX = Math.floorDiv(macroX, CloudConstants.CELLS_PER_AXIS);
		int chunkZ = Math.floorDiv(macroZ, CloudConstants.CELLS_PER_AXIS);
		Column column = columns.get(ChunkPos.toLong(chunkX, chunkZ));
		if (column == null) {
			return Double.NaN;
		}
		int lx = Math.floorMod(macroX, CloudConstants.CELLS_PER_AXIS);
		int lz = Math.floorMod(macroZ, CloudConstants.CELLS_PER_AXIS);
		return column.snapshot[lx * CloudConstants.CELLS_PER_AXIS + lz];
	}
}
