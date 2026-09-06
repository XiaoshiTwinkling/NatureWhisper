package com.xiaoshi.gi;

import com.mojang.blaze3d.platform.TextureUtil;
import com.xiaoshi.NatureWhisper;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.Optional;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

/**
 * Minimal self-managed GPU helpers for the ray-traced lighting passes: compile GLSL from our
 * assets, draw a fullscreen triangle (big-triangle trick, no attributes), create 2D colour targets
 * and 3D voxel textures. These passes need per-frame matrices/vecs and sampler3D, which the vanilla
 * PostEffectProcessor JSON pipeline cannot express, so we drive the GL directly.
 */
final class GiGpu {
	private static int fullscreenVao = -1;

	private GiGpu() {
	}

	static String read(ResourceManager rm, String path) throws IOException {
		Optional<Resource> res = rm.getResource(Identifier.of(NatureWhisper.MOD_ID, path));
		if (res.isEmpty()) {
			throw new IOException("Missing shader resource: " + path);
		}
		StringBuilder sb = new StringBuilder();
		String line;
		try (java.io.BufferedReader reader = res.get().getReader()) {
			while ((line = reader.readLine()) != null) {
				sb.append(line).append('\n');
			}
		}
		return sb.toString();
	}

	static int program(String vs, String fs) throws IOException {
		int vertex = compile(GL20.GL_VERTEX_SHADER, vs);
		int fragment = compile(GL20.GL_FRAGMENT_SHADER, fs);
		int program = GL20.glCreateProgram();
		GL20.glAttachShader(program, vertex);
		GL20.glAttachShader(program, fragment);
		GL20.glLinkProgram(program);
		if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
			String log = GL20.glGetProgramInfoLog(program);
			GL20.glDeleteProgram(program);
			throw new IOException("Shader link failed: " + log);
		}
		GL20.glDeleteShader(vertex);
		GL20.glDeleteShader(fragment);
		return program;
	}

	private static int compile(int type, String src) throws IOException {
		int shader = GL20.glCreateShader(type);
		GL20.glShaderSource(shader, src);
		GL20.glCompileShader(shader);
		if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
			String log = GL20.glGetShaderInfoLog(shader);
			GL20.glDeleteShader(shader);
			throw new IOException("Shader compile failed: " + log);
		}
		return shader;
	}

	/** Draws a fullscreen triangle using gl_VertexID; needs any bound VAO in core profile. */
	static void drawFullscreen(int program) {
		if (fullscreenVao == -1) {
			fullscreenVao = GL30.glGenVertexArrays();
		}
		GL30.glBindVertexArray(fullscreenVao);
		GL20.glUseProgram(program);
		GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
		GL30.glBindVertexArray(0);
	}

	static int uniform(int program, String name) {
		return GL20.glGetUniformLocation(program, name);
	}

	static void uniformMat4(int location, Matrix4f matrix) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			FloatBuffer buf = stack.mallocFloat(16);
			matrix.get(buf);
			GL20.glUniformMatrix4fv(location, false, buf);
		}
	}

	/** A colour target we can render into and then sample back. */
	static final class Target {
		final int fbo;
		final int color;
		final int width;
		final int height;

		Target(int width, int height) {
			this.width = width;
			this.height = height;
			this.fbo = GL30.glGenFramebuffers();
			this.color = TextureUtil.generateTextureId();
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.color);
			GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA16F, width, height, 0, GL11.GL_RGBA, GL11.GL_FLOAT, (FloatBuffer) null);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
			GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.fbo);
			GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, this.color, 0);
			GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
		}

		void begin() {
			GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, this.fbo);
			GL11.glViewport(0, 0, this.width, this.height);
		}

		void delete() {
			GL30.glDeleteFramebuffers(this.fbo);
			TextureUtil.releaseTextureId(this.color);
		}
	}

	/** Allocates a 3D texture (RGBA8 used for albedo/occupancy, RGBA16F for light). */
	static int texture3d(int width, int height, int depth, boolean floatFormat) {
		int id = TextureUtil.generateTextureId();
		GL11.glBindTexture(GL12.GL_TEXTURE_3D, id);
		GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
		GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
		GL11.glTexParameteri(GL12.GL_TEXTURE_3D, GL12.GL_TEXTURE_WRAP_R, GL12.GL_CLAMP_TO_EDGE);
		if (floatFormat) {
			GL12.glTexImage3D(GL12.GL_TEXTURE_3D, 0, GL30.GL_RGBA16F, width, height, depth, 0, GL11.GL_RGBA, GL11.GL_FLOAT, (FloatBuffer) null);
		} else {
			GL12.glTexImage3D(GL12.GL_TEXTURE_3D, 0, GL11.GL_RGBA8, width, height, depth, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
		}
		GL11.glBindTexture(GL12.GL_TEXTURE_3D, 0);
		return id;
	}
}
