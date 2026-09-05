package com.xiaoshi.screen;

import com.xiaoshi.config.NatureWhisperConfig;
import com.xiaoshi.sky.StarFieldRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** In-game configuration screen (config key; intended for ModMenu too). */
public class NatureWhisperConfigScreen extends Screen {
	private static final double[] NIGHT_LEVELS = { 0.0, 0.5, 1.0 };
	private static final double[] MAG_LEVELS = { 4.0, 5.0, 6.5, 8.0, 10.0 };

	private final Screen parent;
	private ButtonWidget darkerNights;
	private ButtonWidget nightLevel;
	private ButtonWidget magLimit;
	private ButtonWidget motionBlur;
	private ButtonWidget depthOfField;

	public NatureWhisperConfigScreen(Screen parent) {
		super(Text.translatable("screen.naturewhisper.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int w = this.width;
		int y = 40;
		darkerNights = ButtonWidget.builder(Text.empty(), b -> {
			NatureWhisperConfig.get().darkerNights = !NatureWhisperConfig.get().darkerNights;
			refresh();
		}).dimensions(w / 2 - 155, y, 155, 20).build();
		nightLevel = ButtonWidget.builder(Text.empty(), b -> {
			NatureWhisperConfig cfg = NatureWhisperConfig.get();
			cfg.nightDarkness = next(cfg.nightDarkness, NIGHT_LEVELS);
			refresh();
		}).dimensions(w / 2 + 5, y, 150, 20).build();

		y += 24;
		magLimit = ButtonWidget.builder(Text.empty(), b -> {
			NatureWhisperConfig cfg = NatureWhisperConfig.get();
			cfg.maxRenderMagnitude = next(cfg.maxRenderMagnitude, MAG_LEVELS);
			refresh();
		}).dimensions(w / 2 - 155, y, 310, 20).build();

		y += 24;
		motionBlur = ButtonWidget.builder(Text.empty(), b -> {
			NatureWhisperConfig cfg = NatureWhisperConfig.get();
			cfg.motionBlurEnabled = !cfg.motionBlurEnabled;
			refresh();
		}).dimensions(w / 2 - 155, y, 155, 20).build();
		depthOfField = ButtonWidget.builder(Text.empty(), b -> {
			NatureWhisperConfig cfg = NatureWhisperConfig.get();
			cfg.depthOfFieldEnabled = !cfg.depthOfFieldEnabled;
			refresh();
		}).dimensions(w / 2 + 5, y, 150, 20).build();

		this.addDrawableChild(darkerNights);
		this.addDrawableChild(nightLevel);
		this.addDrawableChild(magLimit);
		this.addDrawableChild(motionBlur);
		this.addDrawableChild(depthOfField);
		this.addDrawableChild(ButtonWidget.builder(Text.translatable("screen.naturewhisper.done"), b -> this.close())
			.dimensions(w / 2 - 75, y + 30, 150, 20).build());
		refresh();
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float tickDelta) {
		super.render(context, mouseX, mouseY, tickDelta);
		// Draw the title last so it is never hidden behind the background.
		context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 15, 0xFFFFFF);
	}

	@Override
	public void close() {
		NatureWhisperConfig.save();
		StarFieldRenderer.invalidate();
		if (this.client != null) {
			this.client.setScreen(this.parent);
		}
	}

	private void refresh() {
		NatureWhisperConfig cfg = NatureWhisperConfig.get();
		darkerNights.setMessage(Text.translatable("screen.naturewhisper.darkerNights")
			.append(": ").append(Text.literal(cfg.darkerNights ? "ON" : "OFF")));
		nightLevel.setMessage(Text.translatable("screen.naturewhisper.nightDarkness")
			.append(": ").append(Text.literal(fmt(cfg.nightDarkness))));
		magLimit.setMessage(Text.translatable("screen.naturewhisper.magLimit")
			.append(": ").append(Text.literal(fmt(cfg.maxRenderMagnitude))));
		motionBlur.setMessage(Text.translatable("screen.naturewhisper.motionBlur")
			.append(": ").append(Text.literal(cfg.motionBlurEnabled ? "ON" : "OFF")));
		depthOfField.setMessage(Text.translatable("screen.naturewhisper.dof")
			.append(": ").append(Text.literal(cfg.depthOfFieldEnabled ? "ON" : "OFF")));
	}

	private static double next(double value, double[] levels) {
		for (int i = 0; i < levels.length; i++) {
			if (levels[i] >= value) {
				return levels[(i + 1) % levels.length];
			}
		}
		return levels[0];
	}

	private static String fmt(double v) {
		if (v == Math.rint(v)) {
			return String.valueOf((long) v);
		}
		return String.valueOf(Math.round(v * 100.0) / 100.0);
	}
}
