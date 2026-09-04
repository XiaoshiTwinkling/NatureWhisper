package com.xiaoshi;

import com.xiaoshi.climate.SectionClimatePopulator;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.chunk.WorldChunk;

public class NatureWhisperClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// This entrypoint is suitable for setting up client-specific logic, such as rendering.
		ClientChunkEvents.CHUNK_LOAD.register((ClientWorld world, WorldChunk chunk) -> {
			// Mirror the server-side biome climate on the client so the F3+G overlay shows the same
			// smoothed values without a dedicated sync mechanism (for now).
			int chunkX = chunk.getPos().x;
			int chunkZ = chunk.getPos().z;
			SectionClimatePopulator.refreshAround((x, z) -> {
				if (x == chunkX && z == chunkZ) {
					return chunk;
				}
				return world.isChunkLoaded(x, z) ? world.getChunk(x, z) : null;
			}, chunkX, chunkZ);
		});
	}
}
