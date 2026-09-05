package com.xiaoshi.mixin.client;

import com.xiaoshi.NatureWhisper;
import java.io.IOException;
import java.util.Map;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.resource.ResourceFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Registers the NatureWhisper volumetric-cloud core shader alongside the vanilla ones. Vanilla's
 * own reload path already closes and rebuilds the {@code programs} map, so this inject re-runs on
 * every F3+T / resource reload automatically.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Shadow
	@Final
	private Map<String, ShaderProgram> programs;

	@Inject(method = "loadPrograms", at = @At("TAIL"))
	private void naturewhisper$registerCloudProgram(ResourceFactory resourceFactory, CallbackInfo ci) {
		try {
			ShaderProgram program = new ShaderProgram(resourceFactory, "cloud_volumetric", VertexFormats.POSITION_COLOR);
			this.programs.put("cloud_volumetric", program);
		} catch (IOException exception) {
			NatureWhisper.LOGGER.warn("Failed to load naturewhisper cloud shader", exception);
		}
	}
}
