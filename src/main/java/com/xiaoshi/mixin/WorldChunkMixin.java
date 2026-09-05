package com.xiaoshi.mixin;

import com.xiaoshi.cloud.CloudColumnState;
import com.xiaoshi.cloud.CloudConstants;
import com.xiaoshi.cloud.ColumnCloud;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives every {@link WorldChunk} (16×16 column) its cloud state. Both the server and the client
 * hold chunk columns as {@code WorldChunk}, so this single common mixin keeps the deterministic
 * no-networking scheme working: both sides seed and later advect the same field.
 */
@Mixin(WorldChunk.class)
public abstract class WorldChunkMixin implements ColumnCloud {
	@Unique
	private CloudColumnState naturewhisper$cloudState;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void naturewhisper$initCloudState(CallbackInfo ci) {
		if (this.naturewhisper$cloudState == null) {
			this.naturewhisper$cloudState = new CloudColumnState();
		}
	}

	@Override
	public float naturewhisper$getCoverage(int localCellX, int localCellZ) {
		CloudColumnState state = this.naturewhisper$cloudState;
		if (state == null) {
			return 0.0F;
		}
		return state.coverage[localCellX * CloudConstants.CELLS_PER_AXIS + localCellZ];
	}

	@Override
	public void naturewhisper$setCoverage(int localCellX, int localCellZ, float coverage) {
		if (this.naturewhisper$cloudState == null) {
			return;
		}
		this.naturewhisper$cloudState.coverage[localCellX * CloudConstants.CELLS_PER_AXIS + localCellZ] = coverage;
	}

	@Override
	public float naturewhisper$getCloudType() {
		CloudColumnState state = this.naturewhisper$cloudState;
		return state == null ? 0.0F : state.cloudType;
	}

	@Override
	public void naturewhisper$setCloudType(float cloudType) {
		if (this.naturewhisper$cloudState != null) {
			this.naturewhisper$cloudState.cloudType = cloudType;
		}
	}

	@Override
	public float naturewhisper$getCloudScale() {
		CloudColumnState state = this.naturewhisper$cloudState;
		return state == null ? 0.0F : state.cloudScale;
	}

	@Override
	public void naturewhisper$setCloudScale(float cloudScale) {
		if (this.naturewhisper$cloudState != null) {
			this.naturewhisper$cloudState.cloudScale = cloudScale;
		}
	}

	@Override
	public float naturewhisper$getSetpoint() {
		CloudColumnState state = this.naturewhisper$cloudState;
		return state == null ? 0.0F : state.setpoint;
	}

	@Override
	public void naturewhisper$setSetpoint(float setpoint) {
		if (this.naturewhisper$cloudState != null) {
			this.naturewhisper$cloudState.setpoint = setpoint;
		}
	}

	@Override
	public boolean naturewhisper$isSurfaceWater() {
		CloudColumnState state = this.naturewhisper$cloudState;
		return state != null && state.surfaceWater;
	}

	@Override
	public void naturewhisper$setSurfaceWater(boolean surfaceWater) {
		if (this.naturewhisper$cloudState != null) {
			this.naturewhisper$cloudState.surfaceWater = surfaceWater;
		}
	}
}
