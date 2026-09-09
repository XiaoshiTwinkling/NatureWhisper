package com.xiaoshi.iris;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Optional Iris integration. Everything here is safe to call whether or not Iris is present: the
 * iris classes are only touched through {@link Class#forName} reflection so the mod runs fine
 * without Iris on the classpath.
 */
public final class IrisCompat {
	/** The shaderpack this mod installs into the user's shaderpacks folder. */
	public static final String PACK_DIR = "NatureWhisper";

	private static Boolean irisLoaded;
	private static Boolean packInUse;

	private IrisCompat() {
	}

	/** True when the Iris mod is on the classpath (regardless of shader state). */
	public static boolean irisModLoaded() {
		if (irisLoaded == null) {
			irisLoaded = FabricLoader.getInstance().isModLoaded("iris");
		}
		return irisLoaded;
	}

	/**
	 * True when a shader pipeline is actually active (Iris present + user has enabled shaders and
	 * selected a pack). While true, NatureWhisper must NOT draw its own sky/post via vanilla mixins,
	 * because Iris/Sodium owns those framebuffer passes.
	 */
	public static boolean shaderPackActive() {
		if (!irisModLoaded()) {
			return false;
		}
		if (packInUse != null) {
			return packInUse;
		}
		try {
			Class<?> apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
			Object api = apiClass.getMethod("getInstance").invoke(null);
			packInUse = (Boolean) apiClass.getMethod("isShaderPackInUse").invoke(api);
		} catch (ReflectiveOperationException | RuntimeException e) {
			packInUse = false;
		}
		return packInUse;
	}

	/** Resets cached state (e.g. when the user switches pack in Iris's UI at runtime). */
	public static void invalidateCache() {
		packInUse = null;
	}
}
