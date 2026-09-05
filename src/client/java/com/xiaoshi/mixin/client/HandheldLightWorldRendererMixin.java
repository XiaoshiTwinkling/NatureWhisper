package com.xiaoshi.mixin.client;

import com.xiaoshi.config.NatureWhisperConfig;
import com.xiaoshi.light.HandheldLightEngine;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Injects the handheld dynamic light into the lightmap coordinate used by terrain baking. The
 * static WorldRenderer#getLightmapCoordinates is invoked by block-face baking (BlockModelRenderer
 * and the AO BrightnessCache); here its block component is replaced by the dynamic light whenever
 * that is stronger.
 */
@Mixin(WorldRenderer.class)
public abstract class HandheldLightWorldRendererMixin {
	@Inject(
			method = "getLightmapCoordinates(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;)I",
			at = @At("RETURN"),
			cancellable = true)
	private static void naturewhisper$injectHandheldLight(
			BlockRenderView world, BlockState state, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
		if (!NatureWhisperConfig.get().handheldLighting) {
			return;
		}
		cir.setReturnValue(HandheldLightEngine.INSTANCE.getLightmapWithDynamicLight(pos, cir.getReturnValueI()));
	}
}
