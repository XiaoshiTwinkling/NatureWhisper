package com.xiaoshi.post;

import com.mojang.blaze3d.systems.RenderSystem;
import com.xiaoshi.NatureWhisper;
import com.xiaoshi.config.NatureWhisperConfig;
import java.io.IOException;
import java.util.Optional;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.render.Camera;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceFactory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * Camera-motion-driven motion blur. Reuses the vanilla PostEffectProcessor pipeline over the main
 * framebuffer: a custom full-screen pass smears the scene along the screen-space direction of the
 * camera rotation, with a blur radius that grows with angular velocity. It runs just before the
 * player's hand is drawn, so the HUD and the held item stay sharp.
 */
public final class MotionBlurPass {
	public static final MotionBlurPass INSTANCE = new MotionBlurPass();

	private static final Identifier POST_ID = Identifier.of(NatureWhisper.MOD_ID, "shaders/post/motion_blur.json");

	/** Angular speed (degrees/frame) -> blur radius scale. */
	private static final float DEGREE_TO_RADIUS = 1.2f;
	/** Blur radius cap in texels. */
	private static final float MAX_RADIUS = 40.0f;
	/** Below this smoothed radius the pass is skipped (no visible blur). */
	private static final float MIN_RADIUS = 0.75f;
	/** Attack/decay smoothing of the blur radius. */
	private static final float RADIUS_SMOOTHING = 0.5f;

	private PostEffectProcessor processor;
	private boolean broken;

	private boolean hasPrev;
	private float prevYaw;
	private float prevPitch;
	private float smoothRadius;
	private int lastWidth;
	private int lastHeight;

	private MotionBlurPass() {
	}

	public void render(MinecraftClient client, float tickDelta) {
		if (!NatureWhisperConfig.get().motionBlurEnabled || client.world == null || client.player == null) {
			this.reset();
			return;
		}

		Camera camera = client.gameRenderer.getCamera();
		float yaw = camera.getYaw();
		float pitch = camera.getPitch();

		if (!this.hasPrev) {
			this.prevYaw = yaw;
			this.prevPitch = pitch;
			this.hasPrev = true;
			return;
		}

		float yawDelta = MathHelper.wrapDegrees(yaw - this.prevYaw);
		float pitchDelta = pitch - this.prevPitch;
		this.prevYaw = yaw;
		this.prevPitch = pitch;

		if (!this.ensureProcessor(client) || this.processor == null) {
			return;
		}

		int width = client.getWindow().getFramebufferWidth();
		int height = client.getWindow().getFramebufferHeight();
		if (width != this.lastWidth || height != this.lastHeight) {
			this.processor.setupDimensions(width, height);
			this.lastWidth = width;
			this.lastHeight = height;
		}

		float speed = (float) Math.sqrt(yawDelta * yawDelta + pitchDelta * pitchDelta);
		float strength = (float) MathHelper.clamp(NatureWhisperConfig.get().motionBlurStrength, 0.0, 1.0);
		float target = Math.min(MAX_RADIUS, DEGREE_TO_RADIUS * strength * speed);
		this.smoothRadius += (target - this.smoothRadius) * RADIUS_SMOOTHING;
		if (this.smoothRadius < MIN_RADIUS) {
			return;
		}

		// Turning right (positive yaw delta) pushes the world to the left, so the smear runs
		// along -yaw. Sign is easy to flip when tuning.
		float angle = (float) Math.toDegrees(Math.atan2(pitchDelta, -yawDelta));

		RenderSystem.disableBlend();
		RenderSystem.disableDepthTest();
		RenderSystem.resetTextureMatrix();
		this.processor.setUniforms("BlurAngle", angle);
		this.processor.setUniforms("BlurRadius", this.smoothRadius);
		this.processor.render(tickDelta);
		client.getFramebuffer().beginWrite(true);
		RenderSystem.enableDepthTest();
		RenderSystem.enableBlend();
	}

	private void reset() {
		this.hasPrev = false;
		this.smoothRadius = 0.0f;
	}

	private boolean ensureProcessor(MinecraftClient client) {
		if (this.processor != null) {
			return true;
		}
		if (this.broken) {
			return false;
		}
		try {
			// Loader always asks for minecraft:<path>; serve ours from the naturewhisper namespace
			// first and fall back to the original id (so vanilla programs like "blit" still load).
			ResourceFactory base = client.getResourceManager();
			ResourceFactory redirect = id -> {
				Optional<Resource> own = base.getResource(Identifier.of(NatureWhisper.MOD_ID, id.getPath()));
				return own.isPresent() ? own : base.getResource(id);
			};
			this.processor = new PostEffectProcessor(client.getTextureManager(), redirect, client.getFramebuffer(), POST_ID);
		} catch (IOException | RuntimeException exception) {
			this.broken = true;
			NatureWhisper.LOGGER.error("Failed to load motion blur post effect", exception);
		}
		return this.processor != null;
	}
}
