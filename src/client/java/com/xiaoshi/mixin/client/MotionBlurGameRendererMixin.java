package com.xiaoshi.mixin.client;

import com.xiaoshi.post.DepthOfFieldPass;
import com.xiaoshi.post.MotionBlurPass;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Runs the post-effect passes right after the world renderer has drawn the scene and before the
 * vanilla depth clear that precedes the hand, so depth-of-field can read the real scene depth and
 * the hand/HUD stay sharp. Depth of field goes first (spatial), motion blur second (temporal);
 * each pass skips itself when disabled.
 */
@Mixin(GameRenderer.class)
public abstract class MotionBlurGameRendererMixin {
	@Inject(
			method = "renderWorld(Lnet/minecraft/client/render/RenderTickCounter;)V",
			at = @At(
					value = "INVOKE",
					target = "net/minecraft/client/render/WorldRenderer.render(Lnet/minecraft/client/render/RenderTickCounter;ZLnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/GameRenderer;Lnet/minecraft/client/render/LightmapTextureManager;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
					shift = At.Shift.AFTER))
	private void naturewhisper$postProcessAfterWorld(RenderTickCounter tickCounter, CallbackInfo ci) {
		MinecraftClient client = MinecraftClient.getInstance();
		float tickDelta = tickCounter.getLastFrameDuration();
		DepthOfFieldPass.INSTANCE.render(client, tickDelta);
		MotionBlurPass.INSTANCE.render(client, tickDelta);
	}
}
