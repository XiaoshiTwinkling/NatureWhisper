package com.xiaoshi;

import com.xiaoshi.climate.ClimateSimulator;
import com.xiaoshi.climate.SectionClimatePopulator;
import com.xiaoshi.config.NatureWhisperConfig;
import com.xiaoshi.screen.NatureWhisperConfigScreen;
import com.xiaoshi.sky.Celestial;
import com.xiaoshi.sky.StarFieldRenderer;
import java.util.Locale;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;
import org.lwjgl.glfw.GLFW;

public class NatureWhisperClient implements ClientModInitializer {
	private static final KeyBinding SKY_DEBUG_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
		"key.naturewhisper.skydebug", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.naturewhisper"));

	private static final KeyBinding CONFIG_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
		"key.naturewhisper.config", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_P, "key.categories.naturewhisper"));

	private static final Identifier VIGNETTE_TEXTURE = Identifier.of("naturewhisper", "textures/misc/vignette.png");

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

		// Star-sky system debug overlay (hold K in a world).
		HudRenderCallback.EVENT.register((context, unused) -> {
			NatureWhisperConfig cfg = NatureWhisperConfig.get();
			if (cfg.depthOfFieldEnabled) {
				drawVignette(context);
			}
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.world == null || !SKY_DEBUG_KEY.isPressed()) {
				return;
			}
			double lat = Celestial.latitudeOf(client.player.getZ());
			Celestial.SkyState s = Celestial.compute(client.world.getTimeOfDay(), lat);
			int x = 10;
			int y = 10;
			int color = 0xFFFFFF;
			line(context, x, y, String.format(Locale.ROOT, "tod=%d day=%d lat=%.2f°N", s.timeOfDay, s.day, lat), color);
			line(context, x, y += 9, String.format(Locale.ROOT, "year=%.3f  declSun=%.2f°  declMoon=%.2f°", s.yearFraction, s.sunDeclinationDeg, s.moonDeclinationDeg), color);
			line(context, x, y += 9, String.format(Locale.ROOT, "sun alt=%.2f° az=%.2f°  daylen=%.2f  insol=%.2f", s.sunAltitudeDeg, s.sunAzimuthDeg, s.dayLengthFraction, s.dailyInsolation), color);
			String polar = s.polarDay ? " polar-day" : (s.polarNight ? " polar-night" : "");
			line(context, x, y += 9, String.format(Locale.ROOT, "moon elong=%.1f° phase=%d beta=%.1f%s", s.moonElongationDeg, s.moonPhase, s.moonEclipticLatitudeDeg, polar), color);
			String ecl = s.eclipseKind == 1 ? "SOLAR" : (s.eclipseKind == 2 ? "LUNAR" : "none");
			line(context, x, y += 9, String.format(Locale.ROOT, "eclipse=%s mag=%.2f  stars=%.1f°", ecl, s.eclipseMagnitude, s.starAngleDeg), color);
			line(context, x, y += 9, StarFieldRenderer.status(), color);
		});

		// Open the configuration screen with P.
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (CONFIG_KEY.wasPressed() && client.currentScreen == null) {
				client.setScreen(new NatureWhisperConfigScreen(null));
			}
		});
	}

	private static void drawVignette(DrawContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		int w = client.getWindow().getScaledWidth();
		int h = client.getWindow().getScaledHeight();
		context.drawTexture(VIGNETTE_TEXTURE, 0, 0, w, h, 0.0F, 0.0F, 128, 128, 128, 128);
	}

	private static void line(DrawContext context, int x, int y, String text, int color) {
		context.drawText(MinecraftClient.getInstance().textRenderer, Text.literal(text), x, y, color, false);
	}
}
