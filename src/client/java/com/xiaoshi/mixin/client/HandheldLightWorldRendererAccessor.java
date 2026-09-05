package com.xiaoshi.mixin.client;

import net.minecraft.client.render.BuiltChunkStorage;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes WorldRenderer's private chunk-section scheduler (which takes section coordinates) plus
 * the BuiltChunkStorage field used to check whether the renderer is initialised yet — it is null
 * right after switching worlds or before the first frame, when scheduling would otherwise NPE.
 */
@Mixin(WorldRenderer.class)
public interface HandheldLightWorldRendererAccessor {
	@Invoker("scheduleChunkRender")
	void naturewhisper$scheduleChunkRender(int x, int y, int z, boolean important);

	@Accessor("chunks")
	BuiltChunkStorage naturewhisper$getChunks();
}
