package com.xiaoshi.mixin.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.xiaoshi.sky.Celestial;
import com.xiaoshi.sky.SkyPalette;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the vanilla celestial drawing (sun/moon/stars) inside {@code renderSky} with the
 * NatureWhisper star-sky system. Only the overworld NORMAL sky type is affected, and only when we
 * are not underwater/in fog/blind (those paths keep vanilla). Textures are still vanilla's; no
 * custom shader is used.
 */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
	private static final Identifier SUN_TEXTURE = Identifier.of("minecraft", "textures/environment/sun.png");
	private static final Identifier MOON_PHASES_TEXTURE = Identifier.of("minecraft", "textures/environment/moon_phases.png");

	@Shadow
	private ClientWorld world;

	@Shadow
	@Final
	private MinecraftClient client;

	@Shadow
	private VertexBuffer lightSkyBuffer;

	@Shadow
	private VertexBuffer starsBuffer;

	@Shadow
	protected abstract boolean hasBlindnessOrDarkness(Camera camera);

	@Inject(method = "renderSky", at = @At("HEAD"), cancellable = true)
	private void naturewhisper$renderOurSky(Matrix4f positionMatrix, Matrix4f projectionMatrix, float tickDelta, Camera camera, boolean thickFog, Runnable runnable, CallbackInfo ci) {
		if (this.world == null || this.world.getDimensionEffects().getSkyType() != net.minecraft.client.render.DimensionEffects.SkyType.NORMAL) {
			return;
		}
		if (thickFog) {
			return;
		}
		CameraSubmersionType sub = camera.getSubmersionType();
		if (sub == CameraSubmersionType.LAVA || sub == CameraSubmersionType.POWDER_SNOW) {
			return;
		}
		if (this.hasBlindnessOrDarkness(camera)) {
			return;
		}
		ci.cancel();
		this.naturewhisper$renderSky(positionMatrix, projectionMatrix, camera, runnable);
	}

	private void naturewhisper$renderSky(Matrix4f positionMatrix, Matrix4f projectionMatrix, Camera camera, Runnable runnable) {
		ClientPlayerEntity player = this.client.player;
		double latDeg = player != null ? Celestial.latitudeOf(player.getZ()) : Celestial.TROPIC_LATITUDE;
		Celestial.SkyState state = Celestial.compute(this.world.getTimeOfDay(), latDeg);

		MatrixStack matrices = new MatrixStack();
		matrices.multiplyPositionMatrix(positionMatrix);

		// Sky dome: our own smooth zenith→horizon gradient (no vanilla single-colour disc).
		RenderSystem.depthMask(false);
		drawGradientSky(matrices, projectionMatrix, state);

		RenderSystem.enableBlend();
		RenderSystem.blendFuncSeparate(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE, GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ZERO);

		float rain = this.world.getRainGradient(1.0F);
		float cloud = 1.0F - rain;

		// Sun.
		drawBody(matrices, projectionMatrix, state.sunX, state.sunY, state.sunZ, 100.0, 30.0, state.latitudeDeg, SUN_TEXTURE, 0.0F, 0.0F, 1.0F, 1.0F, cloud);

		// Moon (drawn on the same frame; phase chooses the sprite sub-rectangle).
		int phase = state.moonPhase;
		float u0 = (phase % 4) / 4.0F;
		float v0 = ((phase / 4) % 2) / 2.0F;
		drawBody(matrices, projectionMatrix, state.moonX, state.moonY, state.moonZ, 100.0, 20.0, state.latitudeDeg, MOON_PHASES_TEXTURE, u0, v0, u0 + 0.25F, v0 + 0.5F, cloud);

		// Stars: rotate the shared fixed-star buffer so it wheels about the observer's celestial pole.
		starField(matrices, projectionMatrix, state, cloud);

		runnable.run();
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
		RenderSystem.disableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.depthMask(true);
	}

	/** Builds and draws a smooth sky dome coloured per-vertex from the horizon up to the zenith. */
	private static void drawGradientSky(MatrixStack matrices, Matrix4f projection, Celestial.SkyState state) {
		RenderSystem.setShader(GameRenderer::getPositionColorProgram);
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
		Matrix4f m = matrices.peek().getPositionMatrix();

		double day = SkyPalette.skyBrightness(state);
		double night = 1.0 - day;
		Vec3d zenith = SkyPalette.skyColor(state);
		Vec3d horizon = lerp(zenith, new Vec3d(0.84, 0.80, 0.72), 0.55);
		Vec3d belowHorizon = new Vec3d(0.03, 0.04, 0.08);

		double sunAz = state.sunAzimuthDeg;
		int azimuths = 32;
		int elevSteps = 14;
		double eMin = -8.0;
		double eMax = 90.0;
		double radius = 640.0;

		BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
		for (int ring = 0; ring < elevSteps; ring++) {
			double e0 = eMin + (eMax - eMin) * ring / elevSteps;
			double e1 = eMin + (eMax - eMin) * (ring + 1) / elevSteps;
			for (int i = 0; i <= azimuths; i++) {
				double a = (i % azimuths) / (double) azimuths * 2.0 * Math.PI;
				skyVertex(builder, m, e0, a, radius, state, sunAz, day, night, zenith, horizon, belowHorizon);
				skyVertex(builder, m, e1, a, radius, state, sunAz, day, night, zenith, horizon, belowHorizon);
			}
		}
		BufferRenderer.drawWithGlobalProgram(builder.end());
	}

	private static void skyVertex(BufferBuilder builder, Matrix4f m, double elevationDeg, double azimuthRad, double radius, Celestial.SkyState state, double sunAz, double day, double night, Vec3d zenith, Vec3d horizon, Vec3d belowHorizon) {
		double e = elevationDeg * Math.PI / 180.0;
		double ce = Math.cos(e);
		double dirX = Math.sin(azimuthRad) * ce;
		double dirY = Math.sin(e);
		double dirZ = -Math.cos(azimuthRad) * ce;

		Vec3d color;
		if (elevationDeg < 0.0) {
			color = lerp(belowHorizon, zenith, 0.25 + 0.15 * (elevationDeg + 8.0) / 8.0);
		} else {
			color = lerp(horizon, zenith, Math.min(1.0, elevationDeg / 90.0));
		}
		// Dim toward night.
		color = lerp(color, lerp(new Vec3d(0, 0, 0), color, 0.25), night * 0.85);
		// Warm glow toward the (low) sun.
		double azDeg = azimuthRad * 180.0 / Math.PI;
		double delta = Math.abs(azDeg - sunAz);
		delta = Math.min(delta, 360.0 - delta);
		double warm = Math.exp(-delta / 30.0) * Math.exp(-Math.abs(elevationDeg) / 18.0) * day;
		color = color.add(new Vec3d(0.6 * warm, 0.32 * warm, 0.06 * warm));

		builder.vertex(m, (float) (dirX * radius), (float) (dirY * radius), (float) (dirZ * radius))
			.color((float) color.x, (float) color.y, (float) color.z, 1.0F);
	}

	private static Vec3d lerp(Vec3d a, Vec3d b, double t) {
		t = Math.max(0.0, Math.min(1.0, t));
		return new Vec3d(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t);
	}

	/** Draws a textured billboard (sun disk or one moon-phase cell) at a world direction. */
	private static void drawBody(MatrixStack matrices, Matrix4f projectionMatrix, double dx, double dy, double dz, double distance, double half, double latDeg, Identifier texture, float u0, float v0, float u1, float v1, float cloud) {
		Vector3f dir = new Vector3f((float) dx, (float) dy, (float) dz).normalize();
		Vector3f center = new Vector3f(dir).mul((float) distance);

		// Natural orientation: the texture "up" tracks the projected celestial pole (the small,
		// seasonally varying roll), plus a fixed +30° aesthetic roll about the view vector.
		double latRad = latDeg * Math.PI / 180.0;
		Vector3f pole = new Vector3f(0.0F, (float) Math.sin(latRad), (float) -Math.cos(latRad));
		Vector3f up = new Vector3f(pole);
		up.fma(-dir.dot(pole), dir);
		if (up.lengthSquared() < 1.0E-6F) {
			up.set(0.0F, 0.0F, 1.0F);
			up.fma(-dir.dot(up), dir);
			if (up.lengthSquared() < 1.0E-6F) {
				up.set(1.0F, 0.0F, 0.0F);
			}
		}
		up.normalize();
		Vector3f right = new Vector3f(dir).cross(up).normalize();

		float roll = (float) Math.toRadians(60.0);
		float cosR = (float) Math.cos(roll);
		float sinR = (float) Math.sin(roll);
		Vector3f rolledRight = new Vector3f();
		rolledRight.set(right.x * cosR + up.x * sinR, right.y * cosR + up.y * sinR, right.z * cosR + up.z * sinR);
		Vector3f rolledUp = new Vector3f();
		rolledUp.set(-right.x * sinR + up.x * cosR, -right.y * sinR + up.y * cosR, -right.z * sinR + up.z * cosR);
		right = rolledRight;
		up = rolledUp;

		RenderSystem.setShader(GameRenderer::getPositionTexProgram);
		RenderSystem.setShaderTexture(0, texture);
		RenderSystem.setShaderColor(cloud, cloud, cloud, 1.0F);

		BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
		Matrix4f matrix = matrices.peek().getPositionMatrix();
		addCorner(builder, matrix, center, right, up, half, u0, v0);
		addCorner(builder, matrix, center, right, up, half, u1, v0);
		addCorner(builder, matrix, center, right, up, half, u1, v1);
		addCorner(builder, matrix, center, right, up, half, u0, v1);
		BufferRenderer.drawWithGlobalProgram(builder.end());
	}

	private static void addCorner(BufferBuilder builder, Matrix4f matrix, Vector3f center, Vector3f right, Vector3f up, double half, float u, float v) {
		double ux = (u == 1 ? half : -half);
		double uy = (v == 1 ? half : -half);
		float x = center.x + right.x * (float) ux + up.x * (float) uy;
		float y = center.y + right.y * (float) ux + up.y * (float) uy;
		float z = center.z + right.z * (float) ux + up.z * (float) uy;
		builder.vertex(matrix, x, y, z).texture(u, v);
	}

	private void starField(MatrixStack matrices, Matrix4f projectionMatrix, Celestial.SkyState state, float cloud) {
		double latRad = state.latitudeDeg * Math.PI / 180.0;
		double alt = state.sunAltitudeDeg;
		// Stars brighten once the sun is a few degrees below the horizon.
		double brightness = MathHelper.clamp((-alt - 4.0) / 10.0, 0.0, 1.0) * cloud;

		Vector3f pole = new Vector3f(0.0F, (float) Math.sin(latRad), (float) -Math.cos(latRad));

		matrices.push();
		// Align world −Z (vanilla star pole) to our tilted celestial pole, then spin about it.
		Quaternionf align = new Quaternionf().rotateTo(new Vector3f(0.0F, 0.0F, -1.0F), pole);
		Quaternionf spin = new Quaternionf().rotateAxis((float) (state.starAngleDeg * Math.PI / 180.0), pole);
		align.mul(spin, align);
		matrices.multiply(align);

		RenderSystem.setShaderColor((float) brightness, (float) brightness, (float) brightness, 1.0F);
		net.minecraft.client.render.BackgroundRenderer.clearFog();
		this.starsBuffer.bind();
		this.starsBuffer.draw(matrices.peek().getPositionMatrix(), projectionMatrix, GameRenderer.getPositionProgram());
		this.starsBuffer.unbind();
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
		matrices.pop();
	}
}
