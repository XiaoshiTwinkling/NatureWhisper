package com.xiaoshi.mixin;

import com.xiaoshi.climate.SectionClimate;
import net.minecraft.world.chunk.ChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives every {@link ChunkSection} (16×16×16 sub-chunk) a set of climate attributes.
 *
 * <p>Both of ChunkSection's constructors are hooked so that every section instance that
 * is ever created starts from the default climate. Persistence and per-section logic are
 * intentionally left unimplemented for now.
 */
@Mixin(ChunkSection.class)
public abstract class ChunkSectionMixin implements SectionClimate {
	@Unique
	private float naturewhisper$baseTemperature;

	@Unique
	private float naturewhisper$baseHumidity;

	@Unique
	private float naturewhisper$temperature;

	@Unique
	private float naturewhisper$humidity;

	@Unique
	private float naturewhisper$windDirectionX;

	@Unique
	private float naturewhisper$windDirectionZ;

	@Unique
	private float naturewhisper$windStrength;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void naturewhisper$initClimate(CallbackInfo ci) {
		this.naturewhisper$baseTemperature = SectionClimate.DEFAULT_TEMPERATURE;
		this.naturewhisper$baseHumidity = SectionClimate.DEFAULT_HUMIDITY;
		this.naturewhisper$temperature = SectionClimate.DEFAULT_TEMPERATURE;
		this.naturewhisper$humidity = SectionClimate.DEFAULT_HUMIDITY;
		this.naturewhisper$windDirectionX = SectionClimate.DEFAULT_WIND_DIRECTION_X;
		this.naturewhisper$windDirectionZ = SectionClimate.DEFAULT_WIND_DIRECTION_Z;
		this.naturewhisper$windStrength = SectionClimate.DEFAULT_WIND_STRENGTH;
	}

	@Override
	public float naturewhisper$getBaseTemperature() {
		return this.naturewhisper$baseTemperature;
	}

	@Override
	public void naturewhisper$setBaseTemperature(float baseTemperature) {
		this.naturewhisper$baseTemperature = baseTemperature;
	}

	@Override
	public float naturewhisper$getBaseHumidity() {
		return this.naturewhisper$baseHumidity;
	}

	@Override
	public void naturewhisper$setBaseHumidity(float baseHumidity) {
		this.naturewhisper$baseHumidity = baseHumidity;
	}

	@Override
	public float naturewhisper$getTemperature() {
		return this.naturewhisper$temperature;
	}

	@Override
	public void naturewhisper$setTemperature(float temperature) {
		this.naturewhisper$temperature = temperature;
	}

	@Override
	public float naturewhisper$getHumidity() {
		return this.naturewhisper$humidity;
	}

	@Override
	public void naturewhisper$setHumidity(float humidity) {
		this.naturewhisper$humidity = humidity;
	}

	@Override
	public float naturewhisper$getWindDirectionX() {
		return this.naturewhisper$windDirectionX;
	}

	@Override
	public float naturewhisper$getWindDirectionZ() {
		return this.naturewhisper$windDirectionZ;
	}

	@Override
	public void naturewhisper$setWindDirection(float x, float z) {
		this.naturewhisper$windDirectionX = x;
		this.naturewhisper$windDirectionZ = z;
	}

	@Override
	public float naturewhisper$getWindStrength() {
		return this.naturewhisper$windStrength;
	}

	@Override
	public void naturewhisper$setWindStrength(float windStrength) {
		this.naturewhisper$windStrength = windStrength;
	}
}
