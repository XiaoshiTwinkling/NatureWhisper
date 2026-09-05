package com.xiaoshi.sky;

import com.xiaoshi.config.NatureWhisperConfig;
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
		Vec3d nightTop = new Vec3d(0.02, 0.035, 0.09);
		Vec3d horizon = new Vec3d(0.86, 0.78, 0.66);
		Vec3d base = lerp(dayTop, nightTop, 1.0 - day);
		Vec3d out = lerp(base, horizon, dusk * 0.45);
		// Configurable extra darkness at night — pushed hard so nights are very dark but stars pop.
		NatureWhisperConfig cfg = NatureWhisperConfig.get();
		double extra = cfg.darkerNights ? cfg.nightDarkness : 0.0;
		double scale = 1.0 - extra * 0.92 * (1.0 - day);
		return out.multiply(scale);
	}

	/** 0 (night) .. 1 (full day), used for skylight and fog day-factor. */
	public static double skyBrightness(Celestial.SkyState state) {
		double brightness = clamp((state.sunAltitudeDeg + 8.0) / 32.0, 0.0, 1.0);
		NatureWhisperConfig cfg = NatureWhisperConfig.get();
		if (cfg.darkerNights) {
			// Squeeze twilight/night values down so nights are clearly darker than vanilla.
			brightness *= 1.0 - 0.78 * cfg.nightDarkness * (1.0 - brightness);
		}
		return brightness;
	}

	/** 0 (day/twilight) .. 1 (fully dark night); stars only appear once it is really dark. */
	public static double starBrightness(Celestial.SkyState state) {
		return clamp((-state.sunAltitudeDeg - 6.0) / 12.0, 0.0, 1.0);
	}

	/** How much moonlight suppresses faint stars: 1 = no suppression, down to ~0.15 under a high full moon. */
	public static double moonlessFactor(Celestial.SkyState state) {
		double moonAltitudeDeg = Math.asin(Math.max(-1.0, Math.min(1.0, state.moonY))) / Math.PI * 180.0;
		double illumination = (1.0 - Math.cos(state.moonElongationDeg * Math.PI / 180.0)) / 2.0;
		double highFactor = clamp(moonAltitudeDeg / 25.0, 0.0, 1.0);
		return 1.0 - 0.85 * illumination * highFactor;
	}

	private static Vec3d lerp(Vec3d a, Vec3d b, double t) {
		t = clamp(t, 0.0, 1.0);
		return new Vec3d(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t);
	}

	private static double clamp(double value, double min, double max) {
		return value < min ? min : (value > max ? max : value);
	}
}
