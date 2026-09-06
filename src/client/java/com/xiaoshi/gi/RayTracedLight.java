package com.xiaoshi.gi;

import com.mojang.blaze3d.systems.RenderSystem;
import com.xiaoshi.NatureWhisper;
import com.xiaoshi.config.NatureWhisperConfig;
import com.xiaoshi.sky.Celestial;
import com.xiaoshi.sky.SkyPalette;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * Milestone-1 ray-traced lighting: voxelised world + one fullscreen composite pass that adds soft
 * voxel ambient occlusion, approximate sky visibility and a gentle indirect glow from emissive
 * blocks. Reads the main scene colour+depth, writes to a scratch target and copies back. Self
 * managed GL (no Iris, no vanilla post JSON) because we need sampler3D and per-frame vec uniforms.
 */
public final class RayTracedLight {
	public static final RayTracedLight INSTANCE = new RayTracedLight();

	private VoxelWorld voxel;
	private int voxelTex = -1;
	private int emissionTex = -1;
	private int giProgram = -1;
	private int copyProgram = -1;
	private GiGpu.Target target;
	private boolean broken;
	private int lastWidth;
	private int lastHeight;

	private RayTracedLight() {
	}

	public void render(MinecraftClient client, float tickDelta) {
		if (!NatureWhisperConfig.get().rayTracingEnabled || client.world == null || client.player == null) {
			return;
		}
		if (!this.ensure(client)) {
			return;
		}

		int width = client.getWindow().getFramebufferWidth();
		int height = client.getWindow().getFramebufferHeight();
		if (this.target == null || width != this.lastWidth || height != this.lastHeight) {
			if (this.target != null) {
				this.target.delete();
			}
			this.target = new GiGpu.Target(width, height);
			this.lastWidth = width;
			this.lastHeight = height;
		}

		if (this.voxel.update(client)) {
			this.uploadVoxels(client);
		}

		Camera camera = client.gameRenderer.getCamera();
		float far = client.gameRenderer.getFarPlaneDistance();

		this.target.begin();
		RenderSystem.disableBlend();
		RenderSystem.disableDepthTest();
		GL20.glUseProgram(this.giProgram);
		this.bindAndSetGiUniforms(client, camera, width, height, far);
		GiGpu.drawFullscreen(this.giProgram);

		// Copy the scratch target back onto the real main framebuffer.
		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, client.getFramebuffer().fbo);
		GL11.glViewport(0, 0, width, height);
		GL20.glUseProgram(this.copyProgram);
		this.bindTex(GL11.GL_TEXTURE_2D, 0, this.target.color);
		GL20.glUniform1i(GiGpu.uniform(this.copyProgram, "uScene"), 0);
		GiGpu.drawFullscreen(this.copyProgram);

		client.getFramebuffer().beginWrite(true);
		RenderSystem.enableDepthTest();
		RenderSystem.enableBlend();
	}

	private void bindAndSetGiUniforms(MinecraftClient client, Camera camera, int width, int height, float far) {
		int prog = this.giProgram;
		this.bindTex(GL11.GL_TEXTURE_2D, 0, client.getFramebuffer().getColorAttachment());
		this.bindTex(GL11.GL_TEXTURE_2D, 1, client.getFramebuffer().getDepthAttachment());
		this.bindTex(GL12.GL_TEXTURE_3D, 2, this.voxelTex);
		this.bindTex(GL12.GL_TEXTURE_3D, 3, this.emissionTex);
		this.u(prog, "uScene", 0);
		this.u(prog, "uDepth", 1);
		this.u(prog, "uVox", 2);
		this.u(prog, "uEmission", 3);

		Vec3d eye = camera.getPos();
		Quaternionf rot = new Quaternionf(camera.getRotation());
		Vector3f forward = rot.transform(new Vector3f(0.0F, 0.0F, -1.0F), new Vector3f());
		Vector3f right = rot.transform(new Vector3f(1.0F, 0.0F, 0.0F), new Vector3f());
		Vector3f up = rot.transform(new Vector3f(0.0F, 1.0F, 0.0F), new Vector3f());

		float fovDeg = 70.0F;
		net.minecraft.client.option.SimpleOption<Integer> fovOpt = client.options.getFov();
		if (fovOpt != null) {
			Integer v = fovOpt.getValue();
			if (v != null) {
				fovDeg = v;
			}
		}
		float tanY = (float) Math.tan(Math.toRadians(fovDeg / 2.0));
		float tanX = tanY * ((float) width / (float) height);

		this.v(prog, "uTexel", 1.0F / width, 1.0F / height);
		this.f(prog, "uFlipY", 1.0F);
		this.v(prog, "uCamPos", (float) eye.x, (float) eye.y, (float) eye.z);
		this.v(prog, "uCamRight", right.x, right.y, right.z);
		this.v(prog, "uCamUp", up.x, up.y, up.z);
		this.v(prog, "uCamForward", forward.x, forward.y, forward.z);
		this.f(prog, "uTanFovX", tanX);
		this.f(prog, "uTanFovY", tanY);
		this.f(prog, "uNear", 0.05F);
		this.f(prog, "uFar", far);
		this.v(prog, "uVoxOrigin", (float) this.voxel.minX, (float) this.voxel.minY, (float) this.voxel.minZ);
		this.f(prog, "uVoxSize", this.voxel.size);

		// Day/sky terms from the deterministic celestial model.
		double lat = Celestial.latitudeOf(eye.z);
		Celestial.SkyState state = Celestial.compute(client.world.getTimeOfDay(), lat);
		double day = MathHelper.clamp(SkyPalette.skyBrightness(state), 0.0, 1.0);
		Vec3d skyColor = SkyPalette.skyColor(state);
		this.v(prog, "uSkyAmbient",
				(float) (skyColor.x * day * 0.7), (float) (skyColor.y * day * 0.7), (float) (skyColor.z * day * 0.7));
		this.v(prog, "uSunColor", (float) day, (float) (day * 0.9), (float) (day * 0.7));
		this.f(prog, "uSunVis", (float) day);
		this.f(prog, "uStrength", (float) MathHelper.clamp(NatureWhisperConfig.get().rayTracingStrength, 0.0, 1.0));
	}

	private void uploadVoxels(MinecraftClient client) {
		int size = this.voxel.size;
		if (this.voxelTex == -1) {
			this.voxelTex = GiGpu.texture3d(size, size, size, false);
			this.emissionTex = GiGpu.texture3d(size, size, size, true);
		}
		int n = size * size * size;
		ByteBuffer albedo = ByteBuffer.allocateDirect(n * 4).order(ByteOrder.nativeOrder());
		FloatBuffer emission = ByteBuffer.allocateDirect(n * 4 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
		for (int i = 0; i < n; i++) {
			albedo.put(this.voxel.r[i]);
			albedo.put(this.voxel.g[i]);
			albedo.put(this.voxel.b[i]);
			albedo.put(this.voxel.solid[i]);
			emission.put(this.voxel.emissiveR[i]);
			emission.put(this.voxel.emissiveG[i]);
			emission.put(this.voxel.emissiveB[i]);
			emission.put(1.0F);
		}
		albedo.flip();
		emission.flip();

		GL11.glBindTexture(GL12.GL_TEXTURE_3D, this.voxelTex);
		GL12.glTexSubImage3D(GL12.GL_TEXTURE_3D, 0, 0, 0, 0, size, size, size,
				GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, albedo);
		GL11.glBindTexture(GL12.GL_TEXTURE_3D, this.emissionTex);
		GL12.glTexSubImage3D(GL12.GL_TEXTURE_3D, 0, 0, 0, 0, size, size, size,
				GL11.GL_RGBA, GL11.GL_FLOAT, emission);
		GL11.glBindTexture(GL12.GL_TEXTURE_3D, 0);
	}

	private boolean ensure(MinecraftClient client) {
		if (this.broken) {
			return false;
		}
		if (this.voxel == null) {
			this.voxel = new VoxelWorld(Math.max(8, NatureWhisperConfig.get().rayTracingDistance * 2));
		}
		if (this.giProgram == -1) {
			try {
				String vs = GiGpu.read(client.getResourceManager(), "shaders/gi/fullscreen.vert");
				String rtFs = GiGpu.read(client.getResourceManager(), "shaders/gi/rt.frag");
				String copyFs = GiGpu.read(client.getResourceManager(), "shaders/gi/copy.frag");
				this.giProgram = GiGpu.program(vs, rtFs);
				this.copyProgram = GiGpu.program(vs, copyFs);
			} catch (IOException exception) {
				this.broken = true;
				NatureWhisper.LOGGER.error("Failed to load ray-traced lighting shaders", exception);
				return false;
			}
		}
		return true;
	}

	private void bindTex(int target, int unit, int texture) {
		GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
		GL11.glBindTexture(target, texture);
	}

	private void u(int prog, String name, int value) {
		GL20.glUniform1i(GiGpu.uniform(prog, name), value);
	}

	private void f(int prog, String name, float v) {
		GL20.glUniform1f(GiGpu.uniform(prog, name), v);
	}

	private void v(int prog, String name, float x, float y) {
		GL20.glUniform2f(GiGpu.uniform(prog, name), x, y);
	}

	private void v(int prog, String name, float x, float y, float z) {
		GL20.glUniform3f(GiGpu.uniform(prog, name), x, y, z);
	}
}
