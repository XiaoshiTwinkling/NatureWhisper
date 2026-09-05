package com.xiaoshi.render;

import com.xiaoshi.climate.ClimateSimulator;
import com.xiaoshi.climate.SectionClimate;
import com.xiaoshi.cloud.CloudConstants;
import com.xiaoshi.cloud.ColumnCloud;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.NatureCloudLayer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import org.joml.Matrix4f;

/**
 * Cloud renderer (v8): a drifting "cloud sea" of flat translucent sheets, smoothly advected.
 *
 * <p>Cloud content is placed at {@code source + drift} where {@code drift} advances continuously by
 * the wind (measured once per simulation cadence, applied every tick), so the whole sea slides
 * smoothly instead of jumping cell by cell. From the ground only the sheet undersides are drawn,
 * which halves the geometry. Coverage comes from {@link ColumnCloud}; a low base covers the far sea.
 */
public final class VolumetricCloudRenderer {
	public static boolean ENABLED = true;

	/** Wind speed multiplier (blocks/s), exaggerated so drift is obvious. */
	private static final double WIND_MULTIPLIER = 30.0;
	/** Base cloudiness used where no chunk data is loaded. */
	private static final float FAR_COVERAGE = 0.42F;

	private static final int RADIUS_CHUNKS = 14;
	private static final int WINDOW_CELLS = RADIUS_CHUNKS * CloudConstants.CELLS_PER_AXIS;

	private static final RenderLayer CLOUD_LAYER = NatureCloudLayer.clouds();

	// Continuous drift, in blocks, plus the per-second wind measured at the last cadence.
	private static double driftX;
	private static double driftZ;
	private static double velocityX;
	private static double velocityZ;
	private static long lastWindTick = Long.MIN_VALUE;
	private static long lastAdvanceTick = Long.MIN_VALUE;

	private VolumetricCloudRenderer() {
	}

	public static void render(ClientWorld world, MatrixStack matrices, double camX, double camY, double camZ) {
		if (!ENABLED || world == null) {
			return;
		}

		int y0 = CloudConstants.CLOUD_LAYER_CENTER_Y - CloudConstants.CLOUD_LAYER_HALF_THICKNESS;
		int y1 = CloudConstants.CLOUD_LAYER_CENTER_Y + CloudConstants.CLOUD_LAYER_HALF_THICKNESS;
		if (camY >= y1 + 24.0) {
			return;
		}

		measureWind(world, camX, camZ);
		advanceDrift(world.getTime());

		Matrix4f matrix = matrices.peek().getPositionMatrix();
		VertexConsumerProvider.Immediate immediate = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
		VertexConsumer buffer = immediate.getBuffer(CLOUD_LAYER);

		int centreMacroX = (int) Math.floor(camX / CloudConstants.CELL_SIZE_BLOCKS);
		int centreMacroZ = (int) Math.floor(camZ / CloudConstants.CELL_SIZE_BLOCKS);

		for (int ox = -WINDOW_CELLS; ox <= WINDOW_CELLS; ox++) {
			for (int oz = -WINDOW_CELLS; oz <= WINDOW_CELLS; oz++) {
				int srcX = centreMacroX + ox;
				int srcZ = centreMacroZ + oz;
				float cov = coverageAt(world, srcX, srcZ);
				if (cov <= 0.04F) {
					continue;
				}
				addSheet(buffer, matrix, srcX, srcZ, camX, camY, camZ, cov, y0, y1);
			}
		}

		immediate.draw(CLOUD_LAYER);
	}

	private static void addSheet(VertexConsumer buffer, Matrix4f matrix, int srcX, int srcZ, double camX, double camY, double camZ, float cov, int y0, int y1) {
		double cell = CloudConstants.CELL_SIZE_BLOCKS;
		float h1 = hash(srcX, srcZ);
		float h2 = hash(srcX * 3 + 7, srcZ * 5 - 13);

		// World X/Z = source cell centre + wind drift, so the whole pattern slides smoothly.
		double centreX = srcX * cell + driftX + (h2 - 0.5F) * cell * 0.5 - camX;
		double centreZ = srcZ * cell + driftZ + (fract(h1 * 7.31F) - 0.5F) * cell * 0.5 - camZ;

		double half = cell * (0.45 + 0.55 * cov);
		double thickness = 3.0 + 6.0 * cov;
		double baseY = y0 + (y1 - y0 - thickness) * (0.25 + 0.5 * h1);

		float alpha = Math.min(0.55F, 0.03F + cov * 0.34F);

		double x0 = centreX - half;
		double x1 = centreX + half;
		double z0 = centreZ - half;
		double z1 = centreZ + half;
		double by0 = baseY - camY;

		// Underside only; flying above the deck is handled by the caller's early return.
		quad(buffer, matrix, x0, by0, z0, x1, by0, z0, x1, by0, z1, x0, by0, z1, 0.92F, 0.94F, 0.97F, alpha);
	}

	private static void quad(VertexConsumer buffer, Matrix4f matrix, double ax, double ay, double az, double bx, double by, double bz, double cx, double cy, double cz, double dx, double dy, double dz, float r, float g, float b, float a) {
		buffer.vertex(matrix, (float) ax, (float) ay, (float) az).color(r, g, b, a);
		buffer.vertex(matrix, (float) bx, (float) by, (float) bz).color(r, g, b, a);
		buffer.vertex(matrix, (float) cx, (float) cy, (float) cz).color(r, g, b, a);
		buffer.vertex(matrix, (float) dx, (float) dy, (float) dz).color(r, g, b, a);
	}

	private static void measureWind(ClientWorld world, double camX, double camZ) {
		long worldTime = world.getTime();
		if (worldTime % ClimateSimulator.CADENCE_TICKS != 0 || worldTime == lastWindTick) {
			return;
		}
		lastWindTick = worldTime;

		int chunkX = (int) Math.floor(camX / 16.0);
		int chunkZ = (int) Math.floor(camZ / 16.0);
		if (!world.isChunkLoaded(chunkX, chunkZ)) {
			return;
		}
		int cloudSectionIndex = world.getSectionIndex(CloudConstants.CLOUD_LAYER_CENTER_Y);
		if (cloudSectionIndex < 0) {
			return;
		}
		ChunkSection section = world.getChunk(chunkX, chunkZ).getSection(cloudSectionIndex);
		if (!(section instanceof SectionClimate climate)) {
			return;
		}
		double dirX = climate.naturewhisper$getWindDirectionX();
		double dirZ = climate.naturewhisper$getWindDirectionZ();
		double strength = climate.naturewhisper$getWindStrength();
		double length = Math.sqrt(dirX * dirX + dirZ * dirZ);
		if (length < 1.0E-4) {
			return;
		}
		velocityX = dirX / length * strength * WIND_MULTIPLIER;
		velocityZ = dirZ / length * strength * WIND_MULTIPLIER;
	}

	/** Advance the drift continuously by the wind (blocks per tick). */
	private static void advanceDrift(long worldTime) {
		if (lastAdvanceTick == Long.MIN_VALUE) {
			lastAdvanceTick = worldTime;
			return;
		}
		long dt = worldTime - lastAdvanceTick;
		lastAdvanceTick = worldTime;
		if (dt <= 0 || dt > 40) {
			return;
		}
		double step = dt / 20.0; // seconds
		driftX += velocityX * step;
		driftZ += velocityZ * step;
		// Keep the drift bounded so content does not leave the loaded field entirely.
		double maxDrift = WINDOW_CELLS * CloudConstants.CELL_SIZE_BLOCKS * 0.9;
		driftX = Math.max(-maxDrift, Math.min(maxDrift, driftX));
		driftZ = Math.max(-maxDrift, Math.min(maxDrift, driftZ));
	}

	private static float coverageAt(ClientWorld world, int macroX, int macroZ) {
		int chunkX = Math.floorDiv(macroX, CloudConstants.CELLS_PER_AXIS);
		int chunkZ = Math.floorDiv(macroZ, CloudConstants.CELLS_PER_AXIS);
		if (!world.isChunkLoaded(chunkX, chunkZ)) {
			return FAR_COVERAGE + (hash(macroX, macroZ) - 0.5F) * 0.2F;
		}
		Chunk chunk = world.getChunk(chunkX, chunkZ);
		if (!(chunk instanceof ColumnCloud cloud)) {
			return FAR_COVERAGE;
		}
		int lx = Math.floorMod(macroX, CloudConstants.CELLS_PER_AXIS);
		int lz = Math.floorMod(macroZ, CloudConstants.CELLS_PER_AXIS);
		return cloud.naturewhisper$getCoverage(lx, lz);
	}

	private static float hash(int x, int z) {
		int h = x * 374761393 + z * 668265263;
		h = (h ^ (h >> 13)) * 1274126177;
		h = h ^ (h >> 16);
		return fract((h & 0xFFFFFF) / (float) 0x1000000);
	}

	private static float fract(float value) {
		return value - (float) Math.floor(value);
	}
}
