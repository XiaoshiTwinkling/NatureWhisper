package com.xiaoshi.mixin.client;

import com.xiaoshi.sky.Celestial;
import com.xiaoshi.sky.SkyPalette;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes the vanilla sky/lit appearance agree with the star-sky system: sky colour, skylight
 * brightness and star brightness are all derived from the real seasonal solar altitude (through
 * {@link SkyPalette}), so there is no leftover vanilla bright-blue horizon/fog band.
 */
@Mixin(ClientWorld.class)
public abstract class ClientWorldMixin {
	@Inject(method = "getSkyColor", at = @At("HEAD"), cancellable = true)
	private void naturewhisper$skyColor(Vec3d cameraPos, float tickDelta, CallbackInfoReturnable<Vec3d> cir) {
		Celestial.SkyState state = currentState();
		if (state == null) {
			return;
		}
		cir.setReturnValue(SkyPalette.skyColor(state));
		cir.cancel();
	}

	@Inject(method = "getSkyBrightness", at = @At("HEAD"), cancellable = true)
	private void naturewhisper$skyBrightness(float tickDelta, CallbackInfoReturnable<Float> cir) {
		Celestial.SkyState state = currentState();
		if (state == null) {
			return;
		}
		cir.setReturnValue((float) SkyPalette.skyBrightness(state));
		cir.cancel();
	}

	@Inject(method = "getStarBrightness", at = @At("HEAD"), cancellable = true)
	private void naturewhisper$starBrightness(float tickDelta, CallbackInfoReturnable<Float> cir) {
		Celestial.SkyState state = currentState();
		if (state == null) {
			return;
		}
		cir.setReturnValue((float) SkyPalette.starBrightness(state));
		cir.cancel();
	}

	private static Celestial.SkyState currentState() {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientWorld world = client.world;
		ClientPlayerEntity player = client.player;
		if (world == null) {
			return null;
		}
		double lat = player != null ? Celestial.latitudeOf(player.getZ()) : Celestial.TROPIC_LATITUDE;
		return Celestial.compute(world.getTimeOfDay(), lat);
	}
}
