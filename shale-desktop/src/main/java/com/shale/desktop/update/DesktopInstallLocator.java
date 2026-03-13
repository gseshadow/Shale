package com.shale.desktop.update;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Path;

public final class DesktopInstallLocator {

	private DesktopInstallLocator() {
	}

	public static Path detectInstallDir() {
		try {
			File codeSource = new File(
					DesktopInstallLocator.class
							.getProtectionDomain()
							.getCodeSource()
							.getLocation()
							.toURI());

			Path path = codeSource.toPath().toAbsolutePath().normalize();
			System.out.println("Code source path: " + path);

			Path parent = path.getParent();
			if (parent != null && parent.getFileName() != null) {
				String parentName = parent.getFileName().toString();

				// Packaged app: ...\Shale\app\some.jar
				if ("app".equalsIgnoreCase(parentName)) {
					Path installDir = parent.getParent();
					if (installDir != null) {
						System.out.println("Detected packaged install dir (jar in app): " + installDir);
						return installDir;
					}
				}

				// Packaged app: ...\Shale\app\classes
				if ("classes".equalsIgnoreCase(parentName)) {
					Path appDir = parent.getParent();
					if (appDir != null
							&& appDir.getFileName() != null
							&& "app".equalsIgnoreCase(appDir.getFileName().toString())) {
						Path installDir = appDir.getParent();
						if (installDir != null) {
							System.out.println("Detected packaged install dir (classes under app): " + installDir);
							return installDir;
						}
					}
				}
			}

			// Dev mode: ...\target\classes
			if (parent != null
					&& parent.getFileName() != null
					&& "classes".equalsIgnoreCase(parent.getFileName().toString())) {
				Path maybeTarget = parent.getParent();
				if (maybeTarget != null
						&& maybeTarget.getFileName() != null
						&& "target".equalsIgnoreCase(maybeTarget.getFileName().toString())) {
					System.out.println("Detected dev-mode install dir: " + maybeTarget);
					return maybeTarget;
				}
			}

			System.out.println("Falling back to parent dir: " + parent);
			return parent;
		} catch (URISyntaxException ex) {
			throw new IllegalStateException("Unable to detect install directory", ex);
		}
	}
}