package com.xiaoshi.sky;

import com.xiaoshi.NatureWhisper;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Optional;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;

/** Loads the compact real-star catalog resource (assets/naturewhisper/sky/stars.dat). */
public final class StarCatalog {
	public static final Identifier STARS_RESOURCE = Identifier.of("naturewhisper", "sky/stars.dat");

	public final int count;
	public final float[] raDeg;
	public final float[] decDeg;
	public final float[] magnitude;
	public final float[] bv;

	private StarCatalog(int count, float[] raDeg, float[] decDeg, float[] magnitude, float[] bv) {
		this.count = count;
		this.raDeg = raDeg;
		this.decDeg = decDeg;
		this.magnitude = magnitude;
		this.bv = bv;
	}

	/** Returns the catalog, or null when the asset is missing/corrupt (caller falls back to vanilla). */
	public static StarCatalog load() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null) {
			return null;
		}
		Optional<Resource> resource = client.getResourceManager().getResource(STARS_RESOURCE);
		if (resource.isEmpty()) {
			NatureWhisper.LOGGER.warn("stars.dat missing — using the vanilla starfield");
			return null;
		}
		try (DataInputStream in = new DataInputStream(new BufferedInputStream(resource.get().getInputStream()))) {
			byte[] magic = new byte[4];
			in.readFully(magic);
			if (magic[0] != 'N' || magic[1] != 'W' || magic[2] != 'S' || magic[3] != 'T') {
				NatureWhisper.LOGGER.warn("stars.dat has an invalid header");
				return null;
			}
			int count = in.readInt();
			if (count <= 0 || count > 5_000_000) {
				NatureWhisper.LOGGER.warn("stars.dat has an unreasonable count: {}", count);
				return null;
			}
			float[] ra = new float[count];
			float[] dec = new float[count];
			float[] mag = new float[count];
			float[] bv = new float[count];
			for (int i = 0; i < count; i++) {
				ra[i] = in.readFloat();
				dec[i] = in.readFloat();
				mag[i] = in.readFloat();
				bv[i] = in.readFloat();
			}
			NatureWhisper.LOGGER.info("Loaded {} real stars from stars.dat", count);
			return new StarCatalog(count, ra, dec, mag, bv);
		} catch (IOException exception) {
			NatureWhisper.LOGGER.warn("Failed to read stars.dat", exception);
			return null;
		}
	}
}
