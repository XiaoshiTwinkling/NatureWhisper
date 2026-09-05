package com.xiaoshi.cloud;

/** Shared constants for the NatureWhisper cloud field. */
public final class CloudConstants {
	/** Macro-cells per chunk axis: each chunk column holds CELLS_PER_AXIS × CELLS_PER_AXIS cells. */
	public static final int CELLS_PER_AXIS = 2;

	/** Side length (blocks) of one macro cell. */
	public static final int CELL_SIZE_BLOCKS = 16 / CELLS_PER_AXIS;

	/** Number of cells stored per chunk column. */
	public static final int CELLS_PER_COLUMN = CELLS_PER_AXIS * CELLS_PER_AXIS;

	/**
	 * World Y the cloud layer is centred on (overworld-ish). Kept in common code so server and
	 * client agree on where to sample wind for advection without depending on the client-only
	 * {@code DimensionEffects}.
	 */
	public static final int CLOUD_LAYER_CENTER_Y = 192;

	/** Half-thickness (blocks) of the volumetric cloud slab around the centre Y. */
	public static final int CLOUD_LAYER_HALF_THICKNESS = 20;

	private CloudConstants() {
	}
}
