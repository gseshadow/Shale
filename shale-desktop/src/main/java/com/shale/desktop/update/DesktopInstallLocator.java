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

			Path path = codeSource.toPath().toAbsolutePath();
			System.out.println("Code source path: " + path);

			// Example packaged layout often puts jars under app/
			// ...\Shale\app\shale-desktop-1.0.0.jar
			Path parent = path.getParent();
			if (parent != null && parent.getFileName() != null && "app".equalsIgnoreCase(parent.getFileName().toString())) {
				Path installDir = parent.getParent();
				if (installDir != null) {
					return installDir;
				}
			}

			// fallback for dev mode or unexpected layout
			return path.getParent();
		} catch (URISyntaxException ex) {
			throw new IllegalStateException("Unable to detect install directory", ex);
		}
	}
}