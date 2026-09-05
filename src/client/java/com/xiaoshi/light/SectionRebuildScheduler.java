package com.xiaoshi.light;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.ChunkSectionPos;
import com.xiaoshi.mixin.client.HandheldLightWorldRendererAccessor;

/**
 * Schedules chunk-section rebuilds so the baked lightmaps follow dynamic lights: rebuild requests
 * are throttled and only fire for sections affected when a source enters/leaves a section or keeps
 * moving. Runs on the client tick thread only.
 */
public final class SectionRebuildScheduler {
	/** Minimum ticks between consecutive rebuild requests. */
	private static final int THROTTLE_TICKS = 4;
	/** Movement threshold in blocks that marks a source as moving. */
	private static final double MOVE_EPSILON = 0.1;

	/** Sections actually scheduled for rebuild last round (packed section coordinates). */
	private final LongSet baked = new LongOpenHashSet();
	private final LongSet current = new LongOpenHashSet();

	private int cooldown;
	private boolean hasLast;
	private double lastX;
	private double lastY;
	private double lastZ;

	void update(java.util.List<LightSource> sources, MinecraftClient client) {
		this.cooldown--;
		if (sources.isEmpty() || client.worldRenderer == null) {
			this.clear(client.worldRenderer);
			return;
		}

		LightSource probe = sources.get(0); // Local player comes first and acts as the movement probe
		boolean moving;
		if (!this.hasLast) {
			moving = true;
		} else {
			double dx = probe.x() - this.lastX;
			double dy = probe.y() - this.lastY;
			double dz = probe.z() - this.lastZ;
			moving = dx * dx + dy * dy + dz * dz > MOVE_EPSILON * MOVE_EPSILON;
		}
		this.lastX = probe.x();
		this.lastY = probe.y();
		this.lastZ = probe.z();
		this.hasLast = true;

		this.current.clear();
		for (LightSource s : sources) {
			this.addSections(s.x(), s.y(), s.z(), HandheldLightEngine.MAX_RADIUS, this.current);
		}

		boolean membershipChanged = !this.current.equals(this.baked);
		if ((moving || membershipChanged) && this.cooldown <= 0) {
			// Union: current sections need brightening, sections in baked that were left need extinguishing.
			LongOpenHashSet toSchedule = new LongOpenHashSet(this.current);
			toSchedule.addAll(this.baked);
			if (this.schedule(toSchedule, client.worldRenderer)) {
				this.baked.clear();
				this.baked.addAll(this.current);
				this.cooldown = THROTTLE_TICKS;
			}
			// On a failed schedule (renderer not ready yet) baked is kept, so the next tick retries.
		}
	}

	/** Extinguishes every previously lit section on disable/disconnect. */
	void clear(WorldRenderer worldRenderer) {
		this.hasLast = false;
		if (this.baked.isEmpty() || worldRenderer == null) {
			return;
		}
		if (this.schedule(this.baked, worldRenderer)) {
			this.baked.clear();
			this.cooldown = THROTTLE_TICKS;
		}
	}

	/** @return false if the BuiltChunkStorage is not initialised yet, so nothing was scheduled (retry later). */
	private boolean schedule(LongSet sections, WorldRenderer worldRenderer) {
		HandheldLightWorldRendererAccessor accessor = (HandheldLightWorldRendererAccessor) worldRenderer;
		if (accessor.naturewhisper$getChunks() == null) {
			return false;
		}
		for (long packed : sections) {
			accessor.naturewhisper$scheduleChunkRender(
					ChunkSectionPos.unpackX(packed),
					ChunkSectionPos.unpackY(packed),
					ChunkSectionPos.unpackZ(packed),
					true);
		}
		return true;
	}

	/** Adds every 16³ section intersected by the radius-{@code radius} sphere (in block coords) to target. */
	private void addSections(double x, double y, double z, double radius, LongSet target) {
		int minX = (int) Math.floor((x - radius) / 16.0);
		int maxX = (int) Math.floor((x + radius) / 16.0);
		int minY = (int) Math.floor((y - radius) / 16.0);
		int maxY = (int) Math.floor((y + radius) / 16.0);
		int minZ = (int) Math.floor((z - radius) / 16.0);
		int maxZ = (int) Math.floor((z + radius) / 16.0);
		for (int sx = minX; sx <= maxX; sx++) {
			double loX = sx * 16.0;
			double cdx = this.clampAxis(x, loX, loX + 16.0);
			if (cdx > radius) {
				continue;
			}
			for (int sy = minY; sy <= maxY; sy++) {
				double loY = sy * 16.0;
				double cdy = this.clampAxis(y, loY, loY + 16.0);
				double cdxdy = cdx * cdx + cdy * cdy;
				if (cdxdy > radius * radius) {
					continue;
				}
				for (int sz = minZ; sz <= maxZ; sz++) {
					double loZ = sz * 16.0;
					double cdz = this.clampAxis(z, loZ, loZ + 16.0);
					if (cdxdy + cdz * cdz <= radius * radius) {
						target.add(ChunkSectionPos.asLong(sx, sy, sz));
					}
				}
			}
		}
	}

	private double clampAxis(double value, double lo, double hi) {
		if (value < lo) {
			return lo - value;
		}
		if (value > hi) {
			return value - hi;
		}
		return 0.0;
	}
}
