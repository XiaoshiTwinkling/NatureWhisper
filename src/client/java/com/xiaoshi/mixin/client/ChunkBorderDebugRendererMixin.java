package com.xiaoshi.mixin.client;

import com.xiaoshi.climate.SectionClimate;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.debug.ChunkBorderDebugRenderer;
import net.minecraft.client.render.debug.DebugRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws per-sub-chunk climate debug visuals while the F3+G chunk-border debug is active.
 *
 * <p>Piggy-backs on {@link ChunkBorderDebugRenderer#render}: that method is only invoked by
 * {@link DebugRenderer} when the F3+G toggle is on, so this overlay shows and hides together
 * with the vanilla chunk borders. It walks a 5×5×5 cube of sub-chunks centred on the player and,
 * for each one that is loaded, draws a wind-direction arrow centred on the sub-chunk centre plus
 * temperature and humidity tags.
 */
@Mixin(ChunkBorderDebugRenderer.class)
public abstract class ChunkBorderDebugRendererMixin {
	private static final int COLOR_WIND = 0xFFFFB300;
	private static final int COLOR_TEMPERATURE = 0xFFFF7040;
	private static final int COLOR_HUMIDITY = 0xFF40C0FF;

	/** The 5×5×5 debug cube is centred on the player's sub-chunk (radius 2 in every axis). */
	@Unique
	private static final int DEBUG_RADIUS = 2;

	/** Arrow shaft is this long (blocks) at zero wind, growing with wind strength up to MAX. */
	@Unique
	private static final double BASE_ARROW_LENGTH = 3.0;
	@Unique
	private static final double MAX_ARROW_LENGTH = 10.0;
	@Unique
	private static final float TEXT_SCALE = 0.05F;

	@Inject(method = "render", at = @At("TAIL"))
	private void naturewhisper$renderClimateDebug(MatrixStack matrices, VertexConsumerProvider vertexConsumers, double cameraX, double cameraY, double cameraZ, CallbackInfo ci) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientWorld world = client.world;
		if (world == null) {
			return;
		}
		Camera camera = client.gameRenderer.getCamera();
		if (!camera.isReady()) {
			return;
		}

		BlockPos pos = camera.getBlockPos();
		int centreSectionYIndex = world.getSectionIndex(pos.getY());
		int verticalSectionCount = world.countVerticalSections();
		if (centreSectionYIndex < 0 || centreSectionYIndex >= verticalSectionCount) {
			return;
		}

		int centreSectionX = pos.getX() >> 4;
		int centreSectionZ = pos.getZ() >> 4;

		int minYIndex = Math.max(0, centreSectionYIndex - DEBUG_RADIUS);
		int maxYIndex = Math.min(verticalSectionCount - 1, centreSectionYIndex + DEBUG_RADIUS);

		for (int sectionYIndex = minYIndex; sectionYIndex <= maxYIndex; sectionYIndex++) {
			double centreY = (world.sectionIndexToCoord(sectionYIndex) << 4) + 8.0;
			for (int dx = -DEBUG_RADIUS; dx <= DEBUG_RADIUS; dx++) {
				for (int dz = -DEBUG_RADIUS; dz <= DEBUG_RADIUS; dz++) {
					int chunkX = centreSectionX + dx;
					int chunkZ = centreSectionZ + dz;
					if (!world.isChunkLoaded(chunkX, chunkZ)) {
						continue;
					}
					Chunk chunk = world.getChunk(chunkX, chunkZ);
					if (chunk == null) {
						continue;
					}
					ChunkSection section = chunk.getSection(sectionYIndex);
					if (section == null) {
						continue;
					}
					if (!(section instanceof SectionClimate climate)) {
						continue;
					}

					double centreX = (chunkX << 4) + 8.0;
					double centreZ = (chunkZ << 4) + 8.0;

					float windX = climate.naturewhisper$getWindDirectionX();
					float windZ = climate.naturewhisper$getWindDirectionZ();
					float windStrength = climate.naturewhisper$getWindStrength();

					double dirX;
					double dirZ;
					double length = Math.hypot(windX, windZ);
					if (length > 1.0E-4) {
						dirX = windX / length;
						dirZ = windZ / length;
					} else {
						dirX = 1.0;
						dirZ = 0.0;
					}

					this.naturewhisper$drawWindArrow(
						matrices, vertexConsumers, cameraX, cameraY, cameraZ,
						centreX, centreY, centreZ, dirX, dirZ, windStrength
					);

					DebugRenderer.drawString(
						matrices, vertexConsumers,
						String.format(Locale.ROOT, "T %.1f", climate.naturewhisper$getTemperature()),
						centreX, centreY + 0.7, centreZ, COLOR_TEMPERATURE, TEXT_SCALE
					);
					DebugRenderer.drawString(
						matrices, vertexConsumers,
						String.format(Locale.ROOT, "H %.0f%%", climate.naturewhisper$getHumidity() * 100.0F),
						centreX, centreY - 0.7, centreZ, COLOR_HUMIDITY, TEXT_SCALE
					);
				}
			}
		}
	}

	/**
	 * Draws a horizontal wind arrow centred at the sub-chunk centre. The shaft points along the
	 * wind direction and its length grows with wind strength, so both the direction vector and the
	 * strength scalar are readable at a glance.
	 */
	@Unique
	private void naturewhisper$drawWindArrow(MatrixStack matrices, VertexConsumerProvider vertexConsumers, double cameraX, double cameraY, double cameraZ, double centreX, double centreY, double centreZ, double dirX, double dirZ, float windStrength) {
		double length = Math.min(MAX_ARROW_LENGTH, BASE_ARROW_LENGTH + Math.max(windStrength, 0.0));
		double half = length / 2.0;

		double tailX = centreX - dirX * half;
		double tailZ = centreZ - dirZ * half;
		double headX = centreX + dirX * half;
		double headZ = centreZ + dirZ * half;

		// Arrowhead: a small V opening backwards from the tip.
		double back = Math.max(0.75, half * 0.4);
		double wing = back * 0.6;
		double baseX = headX - dirX * back;
		double baseZ = headZ - dirZ * back;
		double perpX = -dirZ;
		double perpZ = dirX;
		double leftX = baseX + perpX * wing;
		double leftZ = baseZ + perpZ * wing;
		double rightX = baseX - perpX * wing;
		double rightZ = baseZ - perpZ * wing;

		// Use a width that vanilla's chunk-border renderer never uses so our line-strip stays its
		// own separate buffer (line strips cannot be restarted mid-buffer).
		VertexConsumer lines = vertexConsumers.getBuffer(RenderLayer.getDebugLineStrip(3.0D));
		Matrix4f matrix = matrices.peek().getPositionMatrix();
		double rY = centreY - cameraY;

		lines.vertex(matrix, (float) (tailX - cameraX), (float) rY, (float) (tailZ - cameraZ)).color(COLOR_WIND);
		lines.vertex(matrix, (float) (headX - cameraX), (float) rY, (float) (headZ - cameraZ)).color(COLOR_WIND);
		lines.vertex(matrix, (float) (leftX - cameraX), (float) rY, (float) (leftZ - cameraZ)).color(COLOR_WIND);
		lines.vertex(matrix, (float) (rightX - cameraX), (float) rY, (float) (rightZ - cameraZ)).color(COLOR_WIND);
		lines.vertex(matrix, (float) (headX - cameraX), (float) rY, (float) (headZ - cameraZ)).color(COLOR_WIND);
	}
}
