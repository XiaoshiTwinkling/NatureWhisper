package com.xiaoshi.sky;

/**
 * Deterministic celestial mechanics for the NatureWhisper star-sky system.
 *
 * <p>Conventions: world +Y = up, world −Z = north, +X = east. Geographic latitude maps to world Z:
 * world Z = 0 sits on the Tropic of Cancer (23.5°N); walking north (−Z) raises latitude, south
 * (+Z) lowers it. Epoch time-of-day 0 = day 0 = spring equinox dawn (Sun, and by convention the new
 * Moon, rise due east). One day = 24000 ticks, one year = 120 days, Moon sidereal period = 27.3 days,
 * axial tilt = 23.44°.
 *
 * <p>The Moon's orbit is inclined ~5.14° to the ecliptic with a slowly regressing node (period scaled
 * from the real 18.6 years), which is what makes solar/lunar eclipses rare, deterministic events.
 */
public final class Celestial {
	public static final double TROPIC_LATITUDE = 23.5; // world Z = 0 sits here
	public static final double OBLIQUITY = 23.44;
	public static final long TICKS_PER_DAY = 24000L;
	public static final long YEAR_DAYS = 120L;
	public static final long YEAR_TICKS = YEAR_DAYS * TICKS_PER_DAY;
	public static final double MOON_PERIOD_DAYS = 27.3;
	public static final double MOON_INCLINATION = 5.14;
	/** Real node regression 18.6 years, scaled to our year length. */
	public static final long NODE_PERIOD_DAYS = (long) Math.round(18.6 * YEAR_DAYS);
	/** World blocks per degree of latitude (world Z = 0 sits on the Tropic of Cancer). */
	public static final double BLOCKS_PER_DEGREE = 100_000.0;

	/** Brightest magnitude included in the shipped catalog (build-time cap; rendering caps separately). */
	public static final double CATALOG_MAX_MAGNITUDE = 8.0;

	private static final double TO_RAD = Math.PI / 180.0;
	private static final double TO_DEG = 180.0 / Math.PI;

	private Celestial() {
	}

	/** Geographic latitude (°, + = north) for a world Z coordinate. */
	public static double latitudeOf(double worldZ) {
		double lat = TROPIC_LATITUDE - worldZ / BLOCKS_PER_DEGREE;
		return Math.max(-90.0, Math.min(90.0, lat));
	}

	/** A frozen snapshot of the sky state at one moment and place. */
	public static final class SkyState {
		public final long timeOfDay;
		public final long withinDay;
		public final long day;
		public final double yearFraction;
		public final double latitudeDeg;
		public final double sunDeclinationDeg;
		public final double sunAltitudeDeg;
		public final double sunAzimuthDeg;
		public final double moonDeclinationDeg;
		public final double moonEclipticLatitudeDeg;
		public final double moonElongationDeg;
		public final double moonNodeDeg;
		public final int moonPhase; // 0..7 (0 = full, 4 = new)
		public final double starAngleDeg;
		public final double dayLengthFraction; // fraction of the day the sun is up
		public final double dailyInsolation; // ~ proportional to total daily solar radiation
		public final boolean polarDay;
		public final boolean polarNight;
		public final int eclipseKind; // 0 none, 1 solar, 2 lunar
		public final double eclipseMagnitude; // 0..1
		public final double sunX, sunY, sunZ, moonX, moonY, moonZ;

		SkyState(long tod, long within, long day, double yearFrac, double lat, double sunDecl, double sunAlt,
				double sunAz, double moonDecl, double moonBeta, double elong, double node, int phase, double starAng,
				double dayLen, double insol, boolean polarDay, boolean polarNight, int eclipse, double mag,
				double sx, double sy, double sz, double mx, double my, double mz) {
			this.timeOfDay = tod;
			this.withinDay = within;
			this.day = day;
			this.yearFraction = yearFrac;
			this.latitudeDeg = lat;
			this.sunDeclinationDeg = sunDecl;
			this.sunAltitudeDeg = sunAlt;
			this.sunAzimuthDeg = sunAz;
			this.moonDeclinationDeg = moonDecl;
			this.moonEclipticLatitudeDeg = moonBeta;
			this.moonElongationDeg = elong;
			this.moonNodeDeg = node;
			this.moonPhase = phase;
			this.starAngleDeg = starAng;
			this.dayLengthFraction = dayLen;
			this.dailyInsolation = insol;
			this.polarDay = polarDay;
			this.polarNight = polarNight;
			this.eclipseKind = eclipse;
			this.eclipseMagnitude = mag;
			this.sunX = sx;
			this.sunY = sy;
			this.sunZ = sz;
			this.moonX = mx;
			this.moonY = my;
			this.moonZ = mz;
		}
	}

	public static SkyState compute(long timeOfDay) {
		return compute(timeOfDay, TROPIC_LATITUDE);
	}

	public static SkyState compute(long timeOfDay, double latitudeDeg) {
		long within = Math.floorMod(timeOfDay, TICKS_PER_DAY);
		long day = Math.floorDiv(timeOfDay, TICKS_PER_DAY);
		double yearFrac = Math.floorMod(timeOfDay, YEAR_TICKS) / (double) YEAR_TICKS;

		double phi = latitudeDeg * TO_RAD;
		double sinPhi = Math.sin(phi);
		double cosPhi = Math.cos(phi);

		double sunLong = 360.0 * yearFrac;
		double sunDecl = OBLIQUITY * Math.sin(sunLong * TO_RAD);
		double sunDeclRad = sunDecl * TO_RAD;
		double hourSun = ((6000.0 - within) / TICKS_PER_DAY) * 360.0 * TO_RAD;
		double[] sunDir = bodyDirection(sinPhi, cosPhi, hourSun, sunDeclRad);

		double sunAlt = altitudeOf(sunDir[1]);
		double sunAz = azimuthOf(sunDir[0], sunDir[2]);

		double moonLong = (timeOfDay / (MOON_PERIOD_DAYS * TICKS_PER_DAY)) * 360.0;
		double elong = mod360(moonLong - sunLong);
		double node = mod360(-360.0 * (timeOfDay / (NODE_PERIOD_DAYS * TICKS_PER_DAY)));
		double moonBeta = MOON_INCLINATION * Math.sin((moonLong - node) * TO_RAD);
		double moonDecl = OBLIQUITY * Math.sin(moonLong * TO_RAD);
		double moonDeclRad = moonDecl * TO_RAD;
		double hourMoon = hourSun + elong * TO_RAD;
		double[] moonDir = bodyDirection(sinPhi, cosPhi, hourMoon, moonDeclRad);

		double starAngle = mod360((363.0 * timeOfDay) / TICKS_PER_DAY);
		int phase = (int) ((Math.floor(elong / 45.0) + 4.0) % 8.0 + 8.0) % 8;

		// Day length & insolation from solar altitude geometry.
		double cosH0 = -Math.tan(phi) * Math.tan(sunDeclRad);
		double h0;
		boolean polarDay = false;
		boolean polarNight = false;
		if (cosH0 <= -1.0) {
			h0 = Math.PI;
			polarDay = true;
		} else if (cosH0 >= 1.0) {
			h0 = 0.0;
			polarNight = true;
		} else {
			h0 = Math.acos(cosH0);
		}
		double dayLength = h0 / Math.PI;
		// Standard daily insolation integral (normalised so equator/equinox ≈ 1).
		double insolation = (h0 * sinPhi * Math.sin(sunDeclRad) + cosPhi * Math.cos(sunDeclRad) * Math.sin(h0))
			* (2.0 / Math.PI);

		// Eclipses: syzygy (new = solar, full = lunar) with the Moon near the ecliptic node.
		double sepDeg = angularDistanceDeg(sunDir, moonDir);
		double elongFromNew = Math.min(elong, 360.0 - elong);
		double elongFromFull = Math.abs(elong - 180.0);
		int eclipse = 0;
		double magnitude = 0.0;
		if (elongFromNew < 1.0 && sepDeg < 1.2) {
			eclipse = 1;
			magnitude = Math.max(0.0, 1.0 - sepDeg / 1.2);
		} else if (elongFromFull < 2.0 && Math.abs(moonBeta) < 1.4) {
			eclipse = 2;
			magnitude = Math.max(0.0, 1.0 - Math.abs(moonBeta) / 1.4);
		}

		return new SkyState(timeOfDay, within, day, yearFrac, latitudeDeg, sunDecl, sunAlt, sunAz,
			moonDecl, moonBeta, elong, node, phase, starAngle,
			dayLength, insolation, polarDay, polarNight, eclipse, magnitude,
			sunDir[0], sunDir[1], sunDir[2], moonDir[0], moonDir[1], moonDir[2]);
	}

	private static double[] bodyDirection(double sinPhi, double cosPhi, double hour, double decl) {
		double sinDecl = Math.sin(decl);
		double cosDecl = Math.cos(decl);
		double cosH = Math.cos(hour);
		double sinH = Math.sin(hour);
		double x = cosDecl * sinH;
		double y = sinDecl * sinPhi + cosDecl * cosH * cosPhi;
		double z = -sinDecl * cosPhi + cosDecl * cosH * sinPhi;
		double len = Math.sqrt(x * x + y * y + z * z);
		if (len < 1.0E-12) {
			len = 1.0;
		}
		return new double[] { x / len, y / len, z / len };
	}

	private static double altitudeOf(double y) {
		return Math.asin(Math.max(-1.0, Math.min(1.0, y))) * TO_DEG;
	}

	private static double azimuthOf(double x, double z) {
		return mod360(Math.atan2(x, -z) * TO_DEG);
	}

	private static double angularDistanceDeg(double[] a, double[] b) {
		double dot = Math.max(-1.0, Math.min(1.0, a[0] * b[0] + a[1] * b[1] + a[2] * b[2]));
		return Math.acos(dot) * TO_DEG;
	}

	private static double mod360(double value) {
		return (value % 360.0 + 360.0) % 360.0;
	}
}
