package com.xiaoshi.climate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

/**
 * Time-driven climate model layered on top of the static biome climate seeded by
 * {@link SectionClimatePopulator}.
 *
 * <p>Three mechanisms are applied each refresh, deterministically on the same inputs (base
 * fields, real block columns, and the synced time of day), so the server's authoritative copies
 * and the client's debug copies converge without any network sync:
 *
 * <ol>
 *   <li>Altitude lapse: air above a column is progressively cooler the higher it is.</li>
 *   <li>Diurnal solar radiation: near the real surface (air, ground, open water are told apart by
 *   scanning the actual blocks of each column) temperature swings with a per-material phase lag and
 *   amplitude, driven by a precomputed first-order thermal-response curve per material.</li>
 *   <li>Gradient wind: within a horizontal layer the virtual temperature
 *   {@code Tv = (T + 273.15) * (1 + 0.61 * H)} rises with warmth and humidity, and air is taken to
 *   flow from high density (cold/dry) towards low density (warm/humid), i.e. along +∇Tv.</li>
 * </ol>
 *
 * <p>Every {@link ChunkSection} carries an immutable biome {@code base} value plus a live value.
 * Live values are recomputed here from {@code base} + geometry + clock and are never fed back as
 * input, so nothing drifts and two runs over the same region and time are bit-identical.
 */
public final class ClimateSimulator {
	/** How many ticks between refreshes (20 ticks = 1 s). */
	public static final int CADENCE_TICKS = 20;
	/** Radius of the refreshed square of sub-chunks (≈ radius * 16 blocks). 6 ≥ debug cube + gradient margin. */
	public static final int SIM_RADIUS_SECTIONS = 6;

	/** Block-column surface thermal-inertia classes, with their response time constant in days. */
	private enum SurfaceClass {
		LAND(0.10, 1.0),
		SNOW(0.12, 0.6),
		WATER(0.28, 0.45);

		final double tauDays;
		/** Extra amplitude compression beyond what the response time constant already gives. */
		final double diurnalGain;

		SurfaceClass(double tauDays, double diurnalGain) {
			this.tauDays = tauDays;
			this.diurnalGain = diurnalGain;
		}
	}

	private static final int CURVE_SAMPLES = 240;
	private static final double CURVE_H = 1.0 / CURVE_SAMPLES;
	private static final int CURVE_WARMUP_DAYS = 40;

	/**
	 * One diurnal temperature curve per {@link SurfaceClass}: mean-subtracted and normalised to ±1
	 * at each of the 240 samples of a day, so the phase lag each material shows is preserved while
	 * the shared {@link #AMP} sets the absolute scale. Precomputed in the class initialiser, so both
	 * sides share bit-identical constants.
	 */
	private static final double[][] CURVES = new double[SurfaceClass.values().length][];

	/** Cooler with height, °C per block (reference height = mean surface of the refresh region). */
	private static final double LAPSE = 0.06;
	/** Peak diurnal amplitude for the land surface, °C. Water/snow swing less through their curves. */
	private static final double AMP = 5.0;
	/** Vertical e-folding distance (blocks) of the diurnal swing above a surface. */
	private static final double AIR_DECAY = 32.0;
	/** Vertical e-folding distance (blocks) of the diurnal swing into rock/water. */
	private static final double GROUND_DECAY = 10.0;
	/** Scales virtual-temperature gradients (K per 32 blocks) into the debug wind-strength scalar. */
	private static final double WIND_SCALE = 0.12;
	private static final double MAX_WIND = 8.0;

	private static final int NONE = Integer.MIN_VALUE;

	static {
		for (SurfaceClass cls : SurfaceClass.values()) {
			double[] source = integrate(cls.tauDays);
			double peak = peakOf(source);
			double[] target = new double[CURVE_SAMPLES];
			for (int i = 0; i < CURVE_SAMPLES; i++) {
				target[i] = source[i] / peak;
			}
			CURVES[cls.ordinal()] = target;
		}
	}

	private ClimateSimulator() {
	}

	/** One loaded chunk column together with its sampled surface profile. */
	private static final class Column {
		final int chunkX;
		final int chunkZ;
		final int bottomY;
		final ChunkSection[] sections;
		/** Block Y at which air begins above this column (topmost material block + 1). */
		final int surfaceTopY;
		final SurfaceClass surfaceClass;

		Column(int chunkX, int chunkZ, int bottomY, ChunkSection[] sections, int surfaceTopY, SurfaceClass surfaceClass) {
			this.chunkX = chunkX;
			this.chunkZ = chunkZ;
			this.bottomY = bottomY;
			this.sections = sections;
			this.surfaceTopY = surfaceTopY;
			this.surfaceClass = surfaceClass;
		}

		boolean isAirSection(int index) {
			return (this.bottomY + (index << 4) + 8) > this.surfaceTopY;
		}
	}

	/**
	 * Recomputes temperature and wind over the loaded sub-chunk square of radius {@code radius}
	 * around (centreChunkX, centreChunkZ). The loader must return the already-loaded column or
	 * {@code null}, and must not trigger generation. {@code timeOfDay} drives the diurnal curves.
	 */
	public static void simulateAround(World world, BiFunction<Integer, Integer, WorldChunk> getLoadedChunk,
			int centreChunkX, int centreChunkZ, int radius, long timeOfDay) {
		List<Column> columns = new ArrayList<>();
		Map<Long, Column> byPosition = new HashMap<>();
		int bottomY = world.getBottomY();
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				int chunkX = centreChunkX + dx;
				int chunkZ = centreChunkZ + dz;
				WorldChunk chunk = getLoadedChunk.apply(chunkX, chunkZ);
				if (chunk == null) {
					continue;
				}
				Column column = profileColumn(bottomY, chunk);
				if (column == null) {
					continue;
				}
				columns.add(column);
				byPosition.put(ChunkPos.toLong(chunkX, chunkZ), column);
			}
		}
		if (columns.isEmpty()) {
			return;
		}

		double meanSum = 0.0;
		for (Column column : columns) {
			meanSum += column.surfaceTopY;
		}
		double meanSurfaceY = meanSum / columns.size();

		pass1(columns, timeOfDay, meanSurfaceY);
		pass2(columns, byPosition);
	}

	/**
	 * Samples the vertical block line through the centre of a column and classifies its surface:
	 * open water, snow/ice cover, or ordinary land. Reads real blocks only (loaded column, centre
	 * line), which keeps the server and client in agreement and lets caves/coastlines be averaged
	 * at sub-chunk resolution rather than cell-perfectly.
	 */
	private static Column profileColumn(int bottomY, WorldChunk chunk) {
		ChunkSection[] sections = chunk.getSectionArray();
		int matterTopY = NONE;
		int solidTopY = NONE;
		boolean matterIsWater = false;
		BlockState solidTopState = null;

		scan:
		for (int i = sections.length - 1; i >= 0; i--) {
			ChunkSection section = sections[i];
			if (section == null) {
				continue;
			}
			int sectionBottom = bottomY + (i << 4);
			for (int ly = 15; ly >= 0; ly--) {
				int y = sectionBottom + ly;
				BlockState state = section.getBlockState(8, ly, 8);
				if (state.isAir()) {
					continue;
				}
				boolean water = section.getFluidState(8, ly, 8).isIn(FluidTags.WATER);
				if (matterTopY == NONE) {
					matterTopY = y;
					matterIsWater = water;
				}
				if (!water && solidTopY == NONE) {
					solidTopY = y;
					solidTopState = state;
				}
				if (matterTopY != NONE && solidTopY != NONE) {
					break scan;
				}
			}
		}
		if (matterTopY == NONE) {
			return null;
		}

		SurfaceClass surfaceClass;
		if (matterIsWater) {
			surfaceClass = SurfaceClass.WATER;
		} else {
			surfaceClass = isSnowCovered(solidTopState) ? SurfaceClass.SNOW : SurfaceClass.LAND;
		}
		return new Column(chunk.getPos().x, chunk.getPos().z, bottomY, sections, matterTopY + 1, surfaceClass);
	}

	private static boolean isSnowCovered(BlockState top) {
		if (top == null) {
			return false;
		}
		return top.isOf(Blocks.SNOW)
			|| top.isOf(Blocks.SNOW_BLOCK)
			|| top.isOf(Blocks.POWDER_SNOW)
			|| top.isOf(Blocks.ICE)
			|| top.isOf(Blocks.PACKED_ICE)
			|| top.isOf(Blocks.BLUE_ICE)
			|| top.isOf(Blocks.FROSTED_ICE);
	}

	/** Altitude lapse + diurnal solar response applied to every section of every column. */
	private static void pass1(List<Column> columns, long timeOfDay, double meanSurfaceY) {
		for (Column column : columns) {
			double curveValue = sample(CURVES[column.surfaceClass.ordinal()], timeOfDay);
			double diurnal = curveValue * AMP * column.surfaceClass.diurnalGain;
			for (int i = 0; i < column.sections.length; i++) {
				ChunkSection section = column.sections[i];
				if (section == null || !(section instanceof SectionClimate climate)) {
					continue;
				}
				double base = climate.naturewhisper$getBaseTemperature();
				int centreY = column.bottomY + (i << 4) + 8;
				float temperature;
				if (centreY > column.surfaceTopY) {
					// Air: cool with altitude and let the swing fade away from the surface.
					double altitudeLapse = LAPSE * (centreY - meanSurfaceY);
					double exposure = StrictMath.exp(-(centreY - column.surfaceTopY) / AIR_DECAY);
					temperature = (float) (base - altitudeLapse + diurnal * exposure);
				} else {
					// Rock/water: the swing penetrates only a short way (thermal inertia below).
					double depth = column.surfaceTopY - centreY;
					double exposure = StrictMath.exp(-depth / GROUND_DECAY);
					temperature = (float) (base + diurnal * exposure);
				}
				climate.naturewhisper$setTemperature(temperature);
			}
		}
	}

	/** Horizontal gradient of the virtual temperature field drives the wind arrows. */
	private static void pass2(List<Column> columns, Map<Long, Column> byPosition) {
		for (Column column : columns) {
			for (int i = 0; i < column.sections.length; i++) {
				SectionClimate climate = climateAt(column, i);
				if (climate == null) {
					continue;
				}
				if (!column.isAirSection(i)) {
					climate.naturewhisper$setWindDirection(1.0F, 0.0F);
					climate.naturewhisper$setWindStrength(0.0F);
					continue;
				}
				Column east = byPosition.get(ChunkPos.toLong(column.chunkX + 1, column.chunkZ));
				Column west = byPosition.get(ChunkPos.toLong(column.chunkX - 1, column.chunkZ));
				Column south = byPosition.get(ChunkPos.toLong(column.chunkX, column.chunkZ + 1));
				Column north = byPosition.get(ChunkPos.toLong(column.chunkX, column.chunkZ - 1));
				SectionClimate climateE = neighbourAir(east, i);
				SectionClimate climateW = neighbourAir(west, i);
				SectionClimate climateS = neighbourAir(south, i);
				SectionClimate climateN = neighbourAir(north, i);
				if (climateE == null || climateW == null || climateS == null || climateN == null) {
					climate.naturewhisper$setWindDirection(1.0F, 0.0F);
					climate.naturewhisper$setWindStrength(0.0F);
					continue;
				}

				double tvE = virtualTemperature(climateE.naturewhisper$getTemperature(), climateE.naturewhisper$getHumidity());
				double tvW = virtualTemperature(climateW.naturewhisper$getTemperature(), climateW.naturewhisper$getHumidity());
				double tvS = virtualTemperature(climateS.naturewhisper$getTemperature(), climateS.naturewhisper$getHumidity());
				double tvN = virtualTemperature(climateN.naturewhisper$getTemperature(), climateN.naturewhisper$getHumidity());
				double gradientX = tvE - tvW;
				double gradientZ = tvS - tvN;
				double magnitude = StrictMath.sqrt(gradientX * gradientX + gradientZ * gradientZ);

				if (magnitude > 1.0E-6) {
					double strength = Math.min(MAX_WIND, magnitude * WIND_SCALE);
					climate.naturewhisper$setWindDirection((float) (gradientX / magnitude), (float) (gradientZ / magnitude));
					climate.naturewhisper$setWindStrength((float) strength);
				} else {
					climate.naturewhisper$setWindDirection(1.0F, 0.0F);
					climate.naturewhisper$setWindStrength(0.0F);
				}
			}
		}
	}

	/** The climate of a section if the section exists, else {@code null}. */
	private static SectionClimate climateAt(Column column, int index) {
		if (index < 0 || index >= column.sections.length) {
			return null;
		}
		ChunkSection section = column.sections[index];
		return section instanceof SectionClimate climate ? climate : null;
	}

	/** The climate of a neighbouring air section, or {@code null} if absent/underground. */
	private static SectionClimate neighbourAir(Column neighbour, int index) {
		if (neighbour == null) {
			return null;
		}
		if (!neighbour.isAirSection(index)) {
			return null;
		}
		return climateAt(neighbour, index);
	}

	/** Virtual temperature of a moist air parcel; warm + humid air is lighter. */
	private static double virtualTemperature(double temperature, double humidity) {
		return (temperature + 273.15) * (1.0 + 0.61 * humidity);
	}

	/** Linearly interpolated curve value for the current phase of the day. */
	private static double sample(double[] curve, long timeOfDay) {
		double phase = timeOfDay % 24000L;
		double x = phase * (double) CURVE_SAMPLES / 24000.0;
		int i = (int) x;
		if (i >= CURVE_SAMPLES) {
			i = CURVE_SAMPLES - 1;
		}
		double fraction = x - i;
		int next = i + 1 == CURVE_SAMPLES ? 0 : i + 1;
		return curve[i] + (curve[next] - curve[i]) * fraction;
	}

	/**
	 * Integrates the first-order thermal response {@code dx/du = (F(u) - x) / tau} to the solar
	 * forcing {@code F(u) = max(0, cos(2π(u - 0.5)))} (u in [0, 1), noon at u = 0.5) until it
	 * reaches the periodic steady state, and returns one mean-subtracted day of samples.
	 */
	private static double[] integrate(double tauDays) {
		double[] curve = new double[CURVE_SAMPLES];
		double x = 0.0;
		int totalDays = CURVE_WARMUP_DAYS + 1;
		for (int day = 0; day < totalDays; day++) {
			for (int k = 0; k < CURVE_SAMPLES; k++) {
				double u = (k + 0.5) * CURVE_H;
				double forcing = Math.max(0.0, StrictMath.cos(Math.PI * 2.0 * (u - 0.5)));
				x += (forcing - x) / tauDays * CURVE_H;
				if (day == totalDays - 1) {
					curve[k] = x;
				}
			}
		}
		double sum = 0.0;
		for (double value : curve) {
			sum += value;
		}
		double mean = sum / CURVE_SAMPLES;
		for (int k = 0; k < CURVE_SAMPLES; k++) {
			curve[k] -= mean;
		}
		return curve;
	}

	private static double peakOf(double[] curve) {
		double peak = 0.0;
		for (double value : curve) {
			peak = Math.max(peak, Math.abs(value));
		}
		return peak;
	}
}
