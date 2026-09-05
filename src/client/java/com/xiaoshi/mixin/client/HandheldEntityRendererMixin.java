package com.xiaoshi.mixin.client;

import com.xiaoshi.light.HandheldLightEngine;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Entity brightness reads the world's static lightmap (World#getLightLevel) and so never sees the
 * terrain light injection. This lifts the block light at EntityRenderer#getBlockLight's return so
 * players/mobs near a light source don't render as dark silhouettes.
 */
@Mixin(EntityRenderer.class)
public abstract class HandheldEntityRendererMixin {
	@Inject(
			method = "getBlockLight(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/BlockPos;)I",
			at = @At("RETURN"),
			cancellable = true)
	private void naturewhisper$injectHandheldLight(Entity entity, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
		int dynamic = (int) HandheldLightEngine.INSTANCE.getLightLevel(pos);
		if (dynamic > cir.getReturnValueI()) {
			cir.setReturnValue(dynamic);
		}
	}
}
