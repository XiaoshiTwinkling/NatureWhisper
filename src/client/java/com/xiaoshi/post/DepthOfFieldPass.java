package com.xiaoshi.post;

import com.mojang.blaze3d.systems.RenderSystem;
import com.xiaoshi.NatureWhisper;
import com.xiaoshi.config.NatureWhisperConfig;
import java.io.IOException;
import java.util.Optional;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceFactory;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Depth of field. Focuses on whatever is under the crosshair within a 15-block probe: the scene's
 * depth (read from the main framebuffer's depth texture via a post auxtarget) is linearised and
 * compared against that focus distance, and only pixels farther than the focus plane are blurred.
 * Runs before the hand is drawn so the hand and HUD stay sharp.
 */
public final class DepthOfFieldPass {
	public static final DepthOfFieldPass INSTANCE = new DepthOfFieldPass();

	private static final Identifier POST_ID = Identifier.of(NatureWhisper.MOD_ID, "shaders/post/dof.json");
	private static final float NEAR = 0.05f;
	private static final double PROBE_DISTANCE = 30.0;

	private PostEffectProcessor processor;
	private boolean broken;

	private boolean hasFocus;
	private float smoothFocus;
	private int lastWidth;
	private int lastHeight;

	private DepthOfFieldPass() {
	}

	public void render(MinecraftClient client, float tickDelta) {
		if (!NatureWhisperConfig.get().depthOfFieldEnabled || client.world == null || client.player == null) {
			return;
		}
		// Depth of field follows the first-person crosshair; disable it in any third-person view.
		if (!client.options.getPerspective().isFirstPerson()) {
			return;
		}
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

		Camera camera = client.gameRenderer.getCamera();
		float far = client.gameRenderer.getFarPlaneDistance();

		float targetFocus = far;
		float focusDistance = this.probeFocusDistance(client, camera, tickDelta);
		boolean aimingNear = focusDistance > 0.0f;
		if (aimingNear) {
			targetFocus = MathHelper.clamp(focusDistance, 0.1f, far - 1.0f);
		}
		if (!this.hasFocus) {
			this.smoothFocus = targetFocus;
			this.hasFocus = true;
		} else {
			this.smoothFocus += (targetFocus - this.smoothFocus) * 0.3f;
		}

		float strength = (float) MathHelper.clamp(NatureWhisperConfig.get().depthOfFieldStrength, 0.0, 1.0);
		// Aiming at something within the probe blurs only farther content; looking far beyond the
		// probe blurs only the near foreground (gently).
		float nearBlur = aimingNear ? 0.0f : 1.0f;

		RenderSystem.disableBlend();
		RenderSystem.disableDepthTest();
		RenderSystem.resetTextureMatrix();
		this.processor.setUniforms("Near", NEAR);
		this.processor.setUniforms("Far", far);
		this.processor.setUniforms("Focus", this.smoothFocus);
		this.processor.setUniforms("Strength", strength);
		this.processor.setUniforms("NearBlur", nearBlur);
		this.processor.render(tickDelta);
		client.getFramebuffer().beginWrite(true);
		RenderSystem.enableDepthTest();
		RenderSystem.enableBlend();
	}

	/** Returns the distance from the eye to the focus target along the crosshair, or -1 if none within 30 blocks. */
	private float probeFocusDistance(MinecraftClient client, Camera camera, float tickDelta) {
		Vec3d eye = camera.getPos();
		Vec3d look = client.player.getRotationVec(tickDelta);
		Vec3d end = eye.add(look.multiply(PROBE_DISTANCE));

		double best = Double.POSITIVE_INFINITY;
		HitResult blockHit = client.world.raycast(new RaycastContext(
				eye, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, client.player));
		if (blockHit.getType() != HitResult.Type.MISS) {
			best = Math.min(best, eye.distanceTo(blockHit.getPos()));
		}
		// Entities along the crosshair count as focus targets too.
		Box area = new Box(eye, end).expand(1.5);
		for (Entity e : client.world.getOtherEntities(client.player, area, entity -> !entity.isSpectator())) {
			double t = rayHitsBox(eye, look, e.getBoundingBox());
			if (t >= 0.0 && t < best) {
				best = t;
			}
		}
		return best <= PROBE_DISTANCE ? (float) best : -1.0f;
	}

	/** Slab test: entry distance along the (unit) ray to the box, or -1 when it misses. */
	private static double rayHitsBox(Vec3d origin, Vec3d dir, Box box) {
		double tMin = 0.0;
		double tMax = Double.POSITIVE_INFINITY;
		double[] o = { origin.x, origin.y, origin.z };
		double[] d = { dir.x, dir.y, dir.z };
		double[] bMin = { box.minX, box.minY, box.minZ };
		double[] bMax = { box.maxX, box.maxY, box.maxZ };
		for (int axis = 0; axis < 3; axis++) {
			if (Math.abs(d[axis]) < 1.0E-7) {
				if (o[axis] < bMin[axis] || o[axis] > bMax[axis]) {
					return -1.0;
				}
			} else {
				double inv = 1.0 / d[axis];
				double t1 = (bMin[axis] - o[axis]) * inv;
				double t2 = (bMax[axis] - o[axis]) * inv;
				if (t1 > t2) {
					double tmp = t1;
					t1 = t2;
					t2 = tmp;
				}
				tMin = Math.max(tMin, t1);
				tMax = Math.min(tMax, t2);
				if (tMin > tMax) {
					return -1.0;
				}
			}
		}
		return tMin;
	}

	private boolean ensureProcessor(MinecraftClient client) {
		if (this.processor != null) {
			return true;
		}
		if (this.broken) {
			return false;
		}
		try {
			ResourceFactory base = client.getResourceManager();
			ResourceFactory redirect = id -> {
				Optional<Resource> own = base.getResource(Identifier.of(NatureWhisper.MOD_ID, id.getPath()));
				return own.isPresent() ? own : base.getResource(id);
			};
			this.processor = new PostEffectProcessor(client.getTextureManager(), redirect, client.getFramebuffer(), POST_ID);
		} catch (IOException | RuntimeException exception) {
			this.broken = true;
			NatureWhisper.LOGGER.error("Failed to load depth of field post effect", exception);
		}
		return this.processor != null;
	}
}
