package com.xiaoshi.cloud;

/** Mutable per-chunk-column cloud state. Index of coverage cells is {@code cellX * CELLS_PER_AXIS + cellZ}. */
public final class CloudColumnState {
	public final float[] coverage = new float[CloudConstants.CELLS_PER_COLUMN];
	/** Coarse type code: 0 clear, 1 stratus, 2 cumulus, 3 cumulonimbus. */
	public float cloudType;
	/** Horizontal patch-scale parameter 0..1 (fine puffs .. broad sheets). */
	public float cloudScale;
	/** Equilibrium coverage this column relaxes towards (climate-derived), 0..1. */
	public float setpoint;
	/** True when the column surface top is open water. */
	public boolean surfaceWater;
}
