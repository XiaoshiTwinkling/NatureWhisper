package com.xiaoshi.sky;

import net.minecraft.util.math.Vec3d;

/**
 * Shared, seasonal sky appearance driven by the real solar altitude. Used both by the custom sky
 * dome and by the overridden ClientWorld brightness/colour methods, so the horizon fog band, sky
 * dome and lighting all agree (no leftover vanilla bright-blue band).
 */
public final class SkyPalette {
	private SkyPalette() {
	}

	public static Vec3d skyColor(Celestial.SkyState state) {
		double alt = state.sunAltitudeDeg;
		double day = clamp((alt + 8.0) / 30.0, 0.0, 1.0);
		double dusk = Math.exp(-Math.abs(alt) * 0.25);
		Vec3d dayTop = new Vec3d(0.45, 0.66, 0.84);
		Vec3d nightTop = new Vec3d(0.03, 0.05, 0.12);
		Vec3d horizon = new Vec3d(0.86, 0.78, 0.66);
		Vec3d base = lerp(dayTop, nightTop, 1.0 - day);
		return lerp(base, horizon, dusk * 0.45);
	}

	/** 0 (night) .. 1 (full day), used for skylight and fog day-factor. */
	public static double skyBrightness(Celestial.SkyState state) {
		return clamp((state.sunAltitudeDeg + 8.0) / 32.0, 0.0, 1.0);
	}

	/** 0 (day/twilight) .. 1 (fully dark night); stars only appear once it is really dark. */
	public static double starBrightness(Celestial.SkyState state) {
		return clamp((-state.sunAltitudeDeg - 6.0) / 12.0, 0.0, 1.0);
	}

	private static Vec3d lerp(Vec3d a, Vec3d b, double t) {
		t = clamp(t, 0.0, 1.0);
		return new Vec3d(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t);
	}

	private static double clamp(double value, double min, double max) {
		return value < min ? min : (value > max ? max : value);
	}
}
