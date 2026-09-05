package com.xiaoshi.light;

import com.xiaoshi.config.NatureWhisperConfig;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

/**
 * Client-side handheld-light engine. Every local and nearby remote player counts as a light
 * source; each tick it snapshots the sources on the client thread, and the chunk-bake/render
 * threads read only the immutable snapshot, so no locking is needed.
 */
public final class HandheldLightEngine {
	public static final HandheldLightEngine INSTANCE = new HandheldLightEngine();

	/** Dynamic light radius in blocks, kept near a single chunk section to bound rebuild cost. */
	public static final double MAX_RADIUS = 7.75;
	public static final double MAX_RADIUS_SQUARED = MAX_RADIUS * MAX_RADIUS;

	private volatile List<LightSource> sources = List.of();
	private final SectionRebuildScheduler scheduler = new SectionRebuildScheduler();

	private HandheldLightEngine() {
	}

	public void tick(MinecraftClient client) {
		ClientWorld world = client.world;
		boolean enabled = world != null && client.player != null && NatureWhisperConfig.get().handheldLighting;
		if (!enabled) {
			this.sources = List.of();
			this.scheduler.clear(client.worldRenderer);
			return;
		}
		AbstractClientPlayerEntity player = client.player;
		List<LightSource> collected = LightSource.collect(world, player);
		this.sources = collected;
		this.scheduler.update(collected, client);
	}

	/** Dynamic light level (0..15) at the given block position; callable from bake/render threads. */
	public double getLightLevel(BlockPos pos) {
		double best = 0.0;
		for (LightSource s : this.sources) {
			double dx = pos.getX() + 0.5 - s.x();
			double dy = pos.getY() + 0.5 - s.y();
			double dz = pos.getZ() + 0.5 - s.z();
			double distanceSquared = dx * dx + dy * dy + dz * dz;
			if (distanceSquared > MAX_RADIUS_SQUARED) {
				continue;
			}
			double level = (1.0 - Math.sqrt(distanceSquared) / MAX_RADIUS) * (double) s.luminance();
			if (level > best) {
				best = level;
			}
		}
		return MathHelper.clamp(best, 0.0, 15.0);
	}

	/**
	 * Merges dynamic light into a packed lightmap coordinate (sky &lt;&lt; 20 | block &lt;&lt; 4),
	 * replacing the block component only when it is brighter than vanilla. The bit operations mirror
	 * LambDynamicLights (the block component is scaled by 16.0 to keep fractional precision).
	 */
	public int getLightmapWithDynamicLight(BlockPos pos, int packed) {
		double level = this.getLightLevel(pos);
		if (level <= 0.0) {
			return packed;
		}
		int blockLevel = packed >> 4 & 0xF;
		if (level <= (double) blockLevel) {
			return packed;
		}
		int luminance = (int) (level * 16.0);
		return packed & 0xfff00000 | luminance & 0x000fffff;
	}
}
