package com.xiaoshi.light;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;

/**
 * Immutable snapshot of one handheld light. Render/chunk-build threads only ever read these
 * snapshots, never live entity state.
 *
 * @param player the player holding the light
 * @param x the x of the light (player position)
 * @param y the y of the light (eye height)
 * @param z the z of the light
 * @param luminance the emitted light level (0..15)
 */
public record LightSource(AbstractClientPlayerEntity player, double x, double y, double z, int luminance) {
	private static final double GATHER_RANGE = 16.0;
	private static final double GATHER_RANGE_SQUARED = GATHER_RANGE * GATHER_RANGE;

	/**
	 * Collects every nearby handheld light, including the local player. Each client already sees
	 * remote players' held items, so computing this per client is enough for players to see each
	 * other's lights — no packets required.
	 */
	public static List<LightSource> collect(ClientWorld world, AbstractClientPlayerEntity self) {
		List<LightSource> out = new ArrayList<>();
		boolean selfSeen = false;
		for (AbstractClientPlayerEntity p : world.getPlayers()) {
			if (p.isSpectator() || p.isRemoved()) {
				continue;
			}
			if (p.squaredDistanceTo(self.getX(), self.getY(), self.getZ()) > GATHER_RANGE_SQUARED) {
				continue;
			}
			int luminance = Math.max(
					ItemLuminance.of(p.getMainHandStack()),
					ItemLuminance.of(p.getOffHandStack()));
			if (luminance <= 0) {
				continue;
			}
			if (p == self) {
				selfSeen = true;
			}
			out.add(new LightSource(p, p.getX(), p.getEyeY(), p.getZ(), luminance));
		}
		if (!selfSeen) {
			int luminance = Math.max(
					ItemLuminance.of(self.getMainHandStack()),
					ItemLuminance.of(self.getOffHandStack()));
			if (luminance > 0) {
				out.add(new LightSource(self, self.getX(), self.getEyeY(), self.getZ(), luminance));
			}
		}
		// Local player first so the scheduler can use it as the "is it moving" probe.
		out.sort(Comparator.comparingInt(s -> s.player() == self ? 0 : 1));
		return List.copyOf(out);
	}
}
