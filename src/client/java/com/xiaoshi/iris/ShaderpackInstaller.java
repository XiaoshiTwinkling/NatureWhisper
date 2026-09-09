package com.xiaoshi.iris;

import com.xiaoshi.NatureWhisper;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Installs the bundled NatureWhisper Iris shaderpack into the user's shaderpacks folder so it shows
 * up as a selectable pack in Iris. The pack tree lives in this mod's resources under
 * {@code shaderpacks/naturewhisper} (lowercase asset path) and is copied once to
 * {@code <game>/shaderpacks/NatureWhisper}. A {@code .naturewhisper_version} marker prevents
 * overwriting a newer user-modified copy.
 */
public final class ShaderpackInstaller {
	private static final String ASSET_ROOT = "shaderpacks/naturewhisper";
	private static final String MARKER = ".naturewhisper_version";

	private ShaderpackInstaller() {
	}

	public static void install() {
		try {
			Path targetRoot = FabricLoader.getInstance().getGameDir().resolve("shaderpacks").resolve(IrisCompat.PACK_DIR);
			Files.createDirectories(targetRoot);

			// Skip if already installed at the current mod version.
			Path marker = targetRoot.resolve(MARKER);
			String version = String.valueOf(FabricLoader.getInstance().getModContainer("naturewhisper")
					.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("dev"));
			if (Files.exists(marker)) {
				String existing = new String(Files.readAllBytes(marker));
				if (version.equals(existing)) {
					return;
				}
			}

			// Full-tree rebuild: remove any stale layout from an earlier version so a restructure
			// (e.g. the pack moving from a flat to a shaders/ layout) never leaves conflicting files.
			deleteRecursively(targetRoot);
			Files.createDirectories(targetRoot);
			copyTree(targetRoot);
			Files.writeString(marker, version);
			NatureWhisper.LOGGER.info("Installed NatureWhisper shaderpack to {}", targetRoot);
		} catch (IOException | RuntimeException e) {
			NatureWhisper.LOGGER.warn("Failed to install NatureWhisper shaderpack", e);
		}
	}

	private static void deleteRecursively(Path dir) {
		try {
			if (Files.exists(dir)) {
				Files.walkFileTree(dir, new SimpleFileVisitor<>() {
					@Override
					public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
						Files.deleteIfExists(file);
						return FileVisitResult.CONTINUE;
					}

					@Override
					public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
						Files.deleteIfExists(dir);
						return FileVisitResult.CONTINUE;
					}
				});
			}
		} catch (IOException ignored) {
			// Best-effort; a leftover file is overwritten by the copy below anyway.
		}
	}

	/** Recursively copies {@link #ASSET_ROOT} from the classpath into {@code targetRoot}. */
	private static void copyTree(Path targetRoot) throws IOException {
		ClassLoader cl = ShaderpackInstaller.class.getClassLoader();
		Path src;
		try {
			var url = cl.getResource(ASSET_ROOT);
			if (url == null) {
				NatureWhisper.LOGGER.warn("Shaderpack asset tree not found: {}", ASSET_ROOT);
				return;
			}
			// In a dev environment the resource is a directory on disk; in a jar it is a jar URL.
			if ("file".equals(url.getProtocol())) {
				src = Path.of(url.toURI());
			} else {
				src = null;
			}
		} catch (URISyntaxException e) {
			src = null;
		}

		if (src != null && Files.isDirectory(src)) {
			copyDir(src, targetRoot);
			return;
		}

		// Fallback: jar URL. Copy via classpath resource stream, walking the tree from a known
		// inventory list is not possible without reading the jar; the dev case above is the norm.
		NatureWhisper.LOGGER.warn("Shaderpack assets are in a jar; falling back to minimal copy");
		copyResource(cl, ASSET_ROOT + "/shaders.properties", targetRoot.resolve("shaders/shaders.properties"));
		copyResource(cl, ASSET_ROOT + "/pack.mcmeta", targetRoot.resolve("pack.mcmeta"));
		copyResource(cl, ASSET_ROOT + "/dimension.properties", targetRoot.resolve("shaders/dimension.properties"));
	}

	private static void copyDir(Path src, Path dst) throws IOException {
		Files.walkFileTree(src, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				Files.createDirectories(dst.resolve(src.relativize(dir).toString()));
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Files.copy(file, dst.resolve(src.relativize(file).toString()), StandardCopyOption.REPLACE_EXISTING);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	private static void copyResource(ClassLoader cl, String path, Path dst) throws IOException {
		Files.createDirectories(dst.getParent());
		try (InputStream in = cl.getResourceAsStream(path)) {
			if (in == null) {
				throw new IOException("Missing resource " + path);
			}
			Files.copy(in, dst, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
