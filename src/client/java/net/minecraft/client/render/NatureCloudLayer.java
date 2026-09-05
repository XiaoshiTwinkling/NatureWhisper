package net.minecraft.client.render;

import net.minecraft.client.MinecraftClient;

/**
 * Builds the cloud {@link RenderLayer}. It lives in the {@code net.minecraft.client.render} package
 * so it can reach {@link RenderPhase}'s protected phase singletons that a mod package cannot name
 * (e.g. {@code TRANSLUCENT_TRANSPARENCY}); only the shader is NatureWhisper's.
 */
public final class NatureCloudLayer {
	private NatureCloudLayer() {
	}

	public static RenderLayer clouds() {
		return RenderLayer.of(
			"naturewhisper_clouds",
			VertexFormats.POSITION_COLOR,
			VertexFormat.DrawMode.QUADS,
			0x40000,
			false,
			false,
			RenderLayer.MultiPhaseParameters.builder()
				.program(new RenderPhase.ShaderProgram(() -> MinecraftClient.getInstance().gameRenderer.getProgram("cloud_volumetric")))
				.transparency(RenderPhase.TRANSLUCENT_TRANSPARENCY)
				.depthTest(RenderPhase.LEQUAL_DEPTH_TEST)
				.build(false)
		);
	}
}
