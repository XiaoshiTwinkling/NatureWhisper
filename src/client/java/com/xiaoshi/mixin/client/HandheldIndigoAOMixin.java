package com.xiaoshi.mixin.client;

import com.xiaoshi.config.NatureWhisperConfig;
import com.xiaoshi.light.HandheldLightEngine;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * With fabric-api present, chunk smooth-lighting is executed by Indigo's AoCalculator, whose static
 * getLightmapCoordinates feeds the per-position AO light seeds — with the emissive fix enabled it
 * reads the static lightmap directly and bypasses the vanilla WorldRenderer hook. Injecting the
 * dynamic light here keeps opaque blocks lit under smooth lighting, mirroring LambDynamicLights'
 * AoCalculator hook. Indigo internals are matched by name, so @Pseudo/@Dynamic with require=0 keep
 * this from crashing when the names don't line up.
 */
@Pseudo
@Mixin(targets = "net.fabricmc.fabric.impl.client.indigo.renderer.aocalc.AoCalculator", remap = false)
public abstract class HandheldIndigoAOMixin {
	@Dynamic
	@Inject(method = "getLightmapCoordinates", at = @At("RETURN"), require = 0, cancellable = true)
	private static void naturewhisper$raiseIndigoAO(
			BlockRenderView world, BlockState state, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
		if (!NatureWhisperConfig.get().handheldLighting) {
			return;
		}
		cir.setReturnValue(HandheldLightEngine.INSTANCE.getLightmapWithDynamicLight(pos, cir.getReturnValueI()));
	}
}
