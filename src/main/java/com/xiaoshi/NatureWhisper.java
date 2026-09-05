package com.xiaoshi;

import com.xiaoshi.climate.ClimateSimulator;
import com.xiaoshi.climate.SectionClimatePopulator;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
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

		// Drive the climate simulation every second around each player of every natural world. The
		// client derives the same values from the same functions and its synced clock, so nothing is
		// sent over the network.
		ServerTickEvents.END_SERVER_TICK.register((MinecraftServer server) -> {
			if (server.getTicks() % ClimateSimulator.CADENCE_TICKS != 0) {
				return;
			}
			for (ServerWorld world : server.getWorlds()) {
				if (!world.getDimension().natural()) {
					continue;
				}
				ServerChunkManager chunkManager = world.getChunkManager();
				long timeOfDay = world.getTimeOfDay();
				for (PlayerEntity player : world.getPlayers()) {
					ChunkPos pos = player.getChunkPos();
					int chunkX = pos.x;
					int chunkZ = pos.z;
					ClimateSimulator.simulateAround(world, (x, z) ->
						chunkManager.isChunkLoaded(x, z) ? chunkManager.getWorldChunk(x, z) : null,
						chunkX, chunkZ, ClimateSimulator.SIM_RADIUS_SECTIONS, timeOfDay);
				}
			}
		});
	}
}
