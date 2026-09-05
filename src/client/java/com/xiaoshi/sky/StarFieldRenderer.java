package com.xiaoshi.sky;

import com.mojang.blaze3d.systems.RenderSystem;
import com.xiaoshi.config.NatureWhisperConfig;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Renders the real star catalog as a static coloured point field.
 *
 * <p>Each star is stored as a tiny POSITION_COLOR quad centred on its equatorial base direction
 * ({@code (cosδcosα, cosδsinα, −sinδ)·R}). The whole buffer is drawn each frame under
 * {@code align·S_{−Z}(−s)}: {@code align} maps the star pole −Z to the observer's celestial pole,
 * and the spin advances the star field with the sidereal clock. Brightness is by apparent
 * magnitude (vertex alpha), colour by B-V, and a single global factor dims everything with
 * twilight/moonlight/weather.
 */
public final class StarFieldRenderer {
	private static final float RADIUS = 100.0F;

	private static VertexBuffer buffer;
	private static boolean built;
	private static boolean catalogMissing;
	/** Number of stars actually placed into the vertex buffer (after the config magnitude cap). */
	public static int drawnStars;

	/** Short diagnostic string for the sky debug HUD. */
	public static String status() {
		return "starfield built=" + built + " drawn=" + drawnStars + " missing=" + catalogMissing;
	}

	private StarFieldRenderer() {
	}

	public static boolean drawDiagnostics = false; // temporary debug markers

	/** Attempts to draw the real star field; returns true when it did (catalog present + buffer drawn). */
	public static boolean draw(MatrixStack matrices, Matrix4f projection, Celestial.SkyState state, double cloud) {
		if (!ensureBuilt()) {
			return false;
		}

		double twilight = SkyPalette.starBrightness(state);
		double moonless = SkyPalette.moonlessFactor(state);
		double env = Math.max(0.0, Math.min(1.0, twilight * moonless * cloud));

		if (drawDiagnostics) {
			// Full-brightness markers independent of our star buffer/rotation/colour so we can tell
			// "star layer not drawn at all" apart from "stars are too small/rotated away".
			// Red = straight up (+Y); cyan = world north horizon (−Z); both 100 blocks out.
			drawMarker(matrices, projection, 0.0F, 1.0F, 0.0F, 1.0F, 0.0F, 0.0F); // up (red)
			drawMarker(matrices, projection, 0.0F, 0.0F, -1.0F, 0.0F, 1.0F, 1.0F); // north (cyan)
		}

		matrices.push();
		Vector3f pole = new Vector3f(0.0F, (float) Math.sin(state.latitudeDeg * Math.PI / 180.0),
			(float) -Math.cos(state.latitudeDeg * Math.PI / 180.0));
		Quaternionf align = new Quaternionf().rotateTo(new Vector3f(0.0F, 0.0F, -1.0F), pole);
		Quaternionf spin = new Quaternionf().rotateAxis((float) (-state.starAngleDeg * Math.PI / 180.0), 0.0F, 0.0F, -1.0F);
		align.mul(spin, align);
		matrices.multiply(align);

		RenderSystem.setShader(GameRenderer::getPositionColorProgram);
		RenderSystem.setShaderColor((float) env, (float) env, (float) env, 1.0F);
		RenderSystem.disableDepthTest();
		RenderSystem.disableCull();
		buffer.bind();
		buffer.draw(matrices.peek().getPositionMatrix(), projection, GameRenderer.getPositionColorProgram());
		buffer.unbind();
		RenderSystem.enableCull();
		RenderSystem.enableDepthTest();
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
		matrices.pop();
		return true;
	}

	/** Full-brightness, unrotated diagnostic marker (a coloured square) at a fixed world direction. */
	private static void drawMarker(MatrixStack matrices, Matrix4f projection, float dx, float dy, float dz, float r, float g, float b) {
		RenderSystem.setShader(GameRenderer::getPositionColorProgram);
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
		Vector3f dir = new Vector3f(dx, dy, dz).normalize();
		Vector3f center = new Vector3f(dir).mul(100.0F);
		Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F);
		up.fma(-dir.dot(up), dir);
		if (up.lengthSquared() < 1.0E-6F) {
			up.set(1.0F, 0.0F, 0.0F);
		}
		up.normalize();
		Vector3f right = new Vector3f(dir).cross(up).normalize();
		float half = 8.0F;
		BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
		Matrix4f m = matrices.peek().getPositionMatrix();
		builder.vertex(m, center.x - right.x * half, center.y - right.y * half, center.z - right.z * half).color(r, g, b, 1.0F);
		builder.vertex(m, center.x + right.x * half, center.y + right.y * half, center.z + right.z * half).color(r, g, b, 1.0F);
		builder.vertex(m, center.x + right.x * half + up.x * half, center.y + right.y * half + up.y * half, center.z + right.z * half + up.z * half).color(r, g, b, 1.0F);
		builder.vertex(m, center.x - right.x * half + up.x * half, center.y - right.y * half + up.y * half, center.z - right.z * half + up.z * half).color(r, g, b, 1.0F);
		net.minecraft.client.render.BufferRenderer.drawWithGlobalProgram(builder.end());
	}

	/** Drops the cached buffer so the next frame rebuilds it (e.g. after a config change). */
	public static void invalidate() {
		if (buffer != null) {
			buffer.close();
			buffer = null;
		}
		built = false;
		catalogMissing = false;
	}

	private static boolean ensureBuilt() {
		if (built) {
			return true;
		}
		if (catalogMissing) {
			return false;
		}
		StarCatalog catalog = StarCatalog.load();
		if (catalog == null) {
			catalogMissing = true;
			return false;
		}
		build(catalog);
		built = true;
		return true;
	}

	private static void build(StarCatalog catalog) {
		BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
		float[] rgb = new float[3];
		drawnStars = 0;
		for (int i = 0; i < catalog.count; i++) {
			if (catalog.magnitude[i] > (float) NatureWhisperConfig.get().maxRenderMagnitude) {
				continue;
			}
			drawnStars++;			double a = Math.toRadians(catalog.raDeg[i]);
			double d = Math.toRadians(catalog.decDeg[i]);
			double cosD = Math.cos(d);
			Vector3f base = new Vector3f(
				(float) (Math.cos(a) * cosD),
				(float) (Math.sin(a) * cosD),
				(float) -Math.sin(d));

			colorFromBv(catalog.bv[i], rgb);
			float alpha = luminance(catalog.magnitude[i]);
			float half = sizeScale(catalog.magnitude[i]);

			Vector3f center = new Vector3f(base).mul(RADIUS);
			Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F);
			Vector3f t1 = new Vector3f(base).cross(up);
			if (t1.lengthSquared() < 1.0E-6F) {
				up.set(0.0F, 0.0F, 1.0F);
				t1 = new Vector3f(base).cross(up);
			}
			t1.normalize();
			Vector3f t2 = new Vector3f(base).cross(t1).normalize();

			emitCorner(builder, center, t1, t2, half, half, rgb, alpha);
			emitCorner(builder, center, t1, t2, -half, half, rgb, alpha);
			emitCorner(builder, center, t1, t2, -half, -half, rgb, alpha);
			emitCorner(builder, center, t1, t2, half, -half, rgb, alpha);
		}
		buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
		net.minecraft.client.render.BuiltBuffer builtBuffer = builder.end();
		buffer.bind();
		buffer.upload(builtBuffer);
		VertexBuffer.unbind();
	}

	private static void emitCorner(BufferBuilder builder, Vector3f center, Vector3f t1, Vector3f t2, float u, float v, float[] rgb, float alpha) {
		float x = center.x + t1.x * u + t2.x * v;
		float y = center.y + t1.y * u + t2.y * v;
		float z = center.z + t1.z * u + t2.z * v;
		builder.vertex(x, y, z).color(rgb[0], rgb[1], rgb[2], alpha);
	}

	/** Visible brightness 0..1 from apparent magnitude (mag 3.5 ⇒ 1, faintest stars get a small floor). */
	static float luminance(float magnitude) {
		double lum = Math.pow(10.0, 0.4 * (3.5 - magnitude));
		return (float) Math.max(0.03, Math.min(1.0, lum));
	}

	/** World half-extent of a star's quad at radius 100 (brighter ⇒ bigger). */
	static float sizeScale(float magnitude) {
		return (float) Math.max(0.03, Math.min(0.26, 0.26 - 0.035 * magnitude));
	}

	/** Linear colour interpolation along the Johnson B-V axis. */
	static void colorFromBv(float bv, float[] out) {
		float v = Math.max(-0.4F, Math.min(2.0F, bv));
		float[][] anchors = {
			{ -0.4F, 0.71F, 0.80F, 1.00F },
			{ 0.0F, 0.97F, 0.97F, 1.00F },
			{ 0.3F, 1.00F, 0.95F, 0.84F },
			{ 0.6F, 1.00F, 0.85F, 0.62F },
			{ 1.0F, 1.00F, 0.72F, 0.45F },
			{ 1.5F, 1.00F, 0.62F, 0.35F },
			{ 2.0F, 1.00F, 0.55F, 0.32F },
		};
		float[] prev = anchors[0];
		for (int i = 1; i < anchors.length; i++) {
			float[] next = anchors[i];
			if (v <= next[0]) {
				float t = (v - prev[0]) / Math.max(1.0E-6F, next[0] - prev[0]);
				out[0] = prev[1] + (next[1] - prev[1]) * t;
				out[1] = prev[2] + (next[2] - prev[2]) * t;
				out[2] = prev[3] + (next[3] - prev[3]) * t;
				return;
			}
			prev = next;
		}
		out[0] = prev[1];
		out[1] = prev[2];
		out[2] = prev[3];
	}
}
