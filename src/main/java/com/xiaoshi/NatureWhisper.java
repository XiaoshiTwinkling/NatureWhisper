package com.xiaoshi;

import com.xiaoshi.climate.SectionClimatePopulator;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.chunk.WorldChunk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NatureWhisper implements ModInitializer {
	public static final String MOD_ID = "naturewhisper";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		ServerChunkEvents.CHUNK_LOAD.register((ServerWorld world, WorldChunk chunk) -> {
			ServerChunkManager chunkManager = world.getChunkManager();
			int chunkX = chunk.getPos().x;
			int chunkZ = chunk.getPos().z;
			SectionClimatePopulator.refreshAround((x, z) -> {
				if (x == chunkX && z == chunkZ) {
					return chunk;
				}
				return chunkManager.isChunkLoaded(x, z) ? chunkManager.getWorldChunk(x, z) : null;
			}, chunkX, chunkZ);
		});
	}
}
