package com.xiaoshi.cloud;

import net.minecraft.registry.tag.FluidTags;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import com.xiaoshi.climate.SectionClimate;

/**
 * Seeds each chunk column's cloud state from the biome climate when the chunk first loads.
 *
 * <p>The climate-driven <em>setpoint</em> coverage rises with the column's base humidity and falls
 * as temperature rises past a condensation proxy (warmer air holds more moisture, so the same
 * humidity produces less cloud); open-water columns get a boost (marine-layer moisture). Each macro
 * cell is initialised around that setpoint with a small deterministic jitter so neighbouring
 * columns read as patches rather than a uniform sheet. Cloud type and horizontal scale are derived
 * from the same inputs.
 */
public final class CloudFieldPopulator {
	private static final double HUMIDITY_FLOOR = 0.45;
	private static final double HUMIDITY_GAIN = 2.2;
	private static final double TEMP_REFERENCE = 12.0;
	private static final double TEMP_PENALTY = 0.012;
	private static final double WATER_BOOST = 0.18;
	private static final float CELL_JITTER = 0.15F;
	private static final float CLEAR_THRESHOLD = 0.06F;

	private CloudFieldPopulator() {
	}

	/** (Re-)seeds the cloud state of one chunk column. Deterministic for identical biome climate. */
	public static void initializeColumn(World world, WorldChunk chunk) {
		if (!(chunk instanceof ColumnCloud cloud)) {
			return;
		}

		double humidity = 0.5;
		double temperature = 15.0;
		int sectionIndex = world.getSectionIndex(CloudConstants.CLOUD_LAYER_CENTER_Y);
		if (sectionIndex >= 0 && sectionIndex < world.countVerticalSections()) {
			ChunkSection section = chunk.getSection(sectionIndex);
			if (section instanceof SectionClimate climate) {
				humidity = climate.naturewhisper$getBaseHumidity();
				temperature = climate.naturewhisper$getBaseTemperature();
			}
		}

		boolean water = hasWaterSurface(chunk, world.getBottomY());
		float setpoint = setpoint(humidity, temperature, water);
		cloud.naturewhisper$setSurfaceWater(water);
		cloud.naturewhisper$setSetpoint(setpoint);

		float type = cloudTypeFor(setpoint, (float) temperature);
		cloud.naturewhisper$setCloudType(type);
		cloud.naturewhisper$setCloudScale(scaleFor(type));

		int chunkX = chunk.getPos().x;
		int chunkZ = chunk.getPos().z;
		for (int lx = 0; lx < CloudConstants.CELLS_PER_AXIS; lx++) {
			for (int lz = 0; lz < CloudConstants.CELLS_PER_AXIS; lz++) {
				int macroX = chunkX * CloudConstants.CELLS_PER_AXIS + lx;
				int macroZ = chunkZ * CloudConstants.CELLS_PER_AXIS + lz;
				float jitter = jitter(macroX, macroZ) * CELL_JITTER;
				cloud.naturewhisper$setCoverage(lx, lz, clamp01(setpoint + jitter));
			}
		}
	}

	private static float setpoint(double humidity, double temperature, boolean water) {
		double value = (humidity - HUMIDITY_FLOOR) * HUMIDITY_GAIN
			- (temperature - TEMP_REFERENCE) * TEMP_PENALTY
			+ (water ? WATER_BOOST : 0.0);
		return clamp01((float) value);
	}

	private static float cloudTypeFor(float setpoint, float temperature) {
		if (setpoint < CLEAR_THRESHOLD) {
			return 0.0F;
		}
		if (temperature <= 0.0F) {
			return 1.0F; // stratus
		}
		if (setpoint > 0.7F && temperature > 22.0F) {
			return 3.0F; // cumulonimbus
		}
		return temperature >= 14.0F ? 2.0F : 1.0F; // cumulus vs cool stratus
	}

	private static float scaleFor(float type) {
		if (type == 1.0F) {
			return 0.75F; // broad sheets
		}
		if (type == 3.0F) {
			return 0.3F; // tall narrow towers
		}
		if (type == 2.0F) {
			return 0.45F; // puffy cumulus
		}
		return 0.5F;
	}

	/** True when the topmost non-air block along the column centre is open water. */
	private static boolean hasWaterSurface(WorldChunk chunk, int bottomY) {
		ChunkSection[] sections = chunk.getSectionArray();
		for (int i = sections.length - 1; i >= 0; i--) {
			ChunkSection section = sections[i];
			if (section == null) {
				continue;
			}
			int sectionBottom = bottomY + (i << 4);
			for (int ly = 15; ly >= 0; ly--) {
				int y = sectionBottom + ly;
				if (!section.getBlockState(8, ly, 8).isAir()) {
					return section.getFluidState(8, ly, 8).isIn(FluidTags.WATER);
				}
			}
		}
		return false;
	}

	private static float jitter(int macroX, int macroZ) {
		int h = macroX * 374761393 + macroZ * 668265263;
		h = (h ^ (h >> 13)) * 1274126177;
		h = h ^ (h >> 16);
		return (h & 0xFFFFFF) / (float) 0x800000 - 1.0F; // roughly [-1, 1]
	}

	private static float clamp01(float value) {
		return value < 0.0F ? 0.0F : (value > 1.0F ? 1.0F : value);
	}
}
