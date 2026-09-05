package com.xiaoshi.mixin.client;

import com.xiaoshi.render.VolumetricCloudRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the vanilla flat cloud layer with the NatureWhisper volumetric cloud renderer. Runs in
 * the exact z-order and with the same matrices as vanilla clouds would have.
 */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererCloudsMixin {
	@Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
	private void naturewhisper$renderOwnClouds(MatrixStack matrices, Matrix4f positionMatrix, Matrix4f projectionMatrix, float tickDelta, double camX, double camY, double camZ, CallbackInfo ci) {
		ci.cancel();
		ClientWorld world = MinecraftClient.getInstance().world;
		if (world != null && world.getDimension().natural()) {
			VolumetricCloudRenderer.render(world, matrices, camX, camY, camZ);
		}
	}
}
