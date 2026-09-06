package com.xiaoshi.gi;

import com.xiaoshi.config.NatureWhisperConfig;
import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Client-side snapshot of the world around the player as a small voxel grid (one voxel per block).
 * Occupancy is taken from full-cube block states; each voxel also keeps a rough albedo tint
 * (block map colour) and an emissive RGB seeded from light-emitting blocks. Rebuilt in whole blocks
 * on the render thread but throttled, so the GI pass samples immutable arrays.
 */
final class VoxelWorld {
	final int size; // per axis, in blocks
	int minX;
	int minY;
	int minZ;

	byte[] solid;
	byte[] r;
	byte[] g;
	byte[] b;
	float[] emissiveR;
	float[] emissiveG;
	float[] emissiveB;

	private int originBlockX = Integer.MIN_VALUE;
	private int originBlockZ = Integer.MIN_VALUE;

	VoxelWorld(int size) {
		this.size = size;
		int n = size * size * size;
		this.solid = new byte[n];
		this.r = new byte[n];
		this.g = new byte[n];
		this.b = new byte[n];
		this.emissiveR = new float[n];
		this.emissiveG = new float[n];
		this.emissiveB = new float[n];
	}

	/** Re-fills the grid centred on the player when the centre has moved far enough. */
	boolean update(MinecraftClient client) {
		ClientWorld world = client.world;
		if (world == null || client.player == null || !NatureWhisperConfig.get().rayTracingEnabled) {
			return false;
		}
		Vec3d pos = client.gameRenderer.getCamera().getPos();
		int cx = ((int) Math.floor(pos.x)) >> 4;
		int cz = ((int) Math.floor(pos.z)) >> 4;
		if (cx == this.originBlockX && cz == this.originBlockZ) {
			return false;
		}
		this.originBlockX = cx;
		this.originBlockZ = cz;
		this.fill(world, pos);
		return true;
	}

	private void fill(ClientWorld world, Vec3d centre) {
		int half = this.size / 2;
		this.minX = (int) Math.floor(centre.x) - half;
		this.minY = (int) Math.floor(centre.y) - half;
		this.minZ = (int) Math.floor(centre.z) - half;

		BlockPos.Mutable cursor = new BlockPos.Mutable();
		int index = 0;
		for (int y = 0; y < this.size; y++) {
			int wy = this.minY + y;
			if (wy < world.getBottomY() || wy >= world.getTopY()) {
				index += this.size * this.size;
				continue;
			}
			for (int x = 0; x < this.size; x++) {
				for (int z = 0; z < this.size; z++) {
					cursor.set(this.minX + x, wy, this.minZ + z);
					BlockState state = world.getBlockState(cursor);
					this.solid[index] = 0;
					this.r[index] = 0;
					this.g[index] = 0;
					this.b[index] = 0;
					this.emissiveR[index] = 0.0f;
					this.emissiveG[index] = 0.0f;
					this.emissiveB[index] = 0.0f;
					if (!state.isAir()) {
						boolean full = state.isOpaqueFullCube(world, cursor);
						if (full) {
							this.solid[index] = 1;
							int color = state.getMapColor(world, cursor).getRenderColor(MapColor.Brightness.HIGH);
							this.r[index] = (byte) (color >>> 16 & 0xFF);
							this.g[index] = (byte) (color >>> 8 & 0xFF);
							this.b[index] = (byte) (color & 0xFF);
						}
						int lum = state.getLuminance();
						if (lum > 0) {
							int color = state.getMapColor(world, cursor).getRenderColor(MapColor.Brightness.HIGH);
							float i = lum / 15.0f;
							this.emissiveR[index] = i * ((color >>> 16 & 0xFF) / 255.0f);
							this.emissiveG[index] = i * ((color >>> 8 & 0xFF) / 255.0f);
							this.emissiveB[index] = i * ((color & 0xFF) / 255.0f);
						}
					}
					index++;
				}
			}
		}
	}
}
