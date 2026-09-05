package com.xiaoshi;

import com.xiaoshi.climate.ClimateSimulator;
import com.xiaoshi.climate.SectionClimatePopulator;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.ChunkPos;
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

		// Advance the deterministic climate model on the client too, gated by the same clock cadence
		// as the server, so the F3+G debug cube shows live values that match the authoritative ones.
		ClientTickEvents.END_CLIENT_TICK.register((MinecraftClient client) -> {
			ClientWorld world = client.world;
			if (world == null || client.player == null) {
				return;
			}
			if (!world.getDimension().natural()) {
				return;
			}
			if (client.world.getTime() % ClimateSimulator.CADENCE_TICKS != 0) {
				return;
			}
			ChunkPos pos = client.player.getChunkPos();
			ClimateSimulator.simulateAround(world, (x, z) ->
				world.isChunkLoaded(x, z) ? world.getChunk(x, z) : null,
				pos.x, pos.z, ClimateSimulator.SIM_RADIUS_SECTIONS, world.getTimeOfDay());
		});
	}
}
