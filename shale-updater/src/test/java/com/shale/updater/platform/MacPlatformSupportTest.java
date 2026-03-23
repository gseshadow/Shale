package com.shale.updater.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class MacPlatformSupportTest {

	@Test
	void restartAppUsesDesktopOpenStrategyWhenSupported() throws Exception {
		Path installDir = Path.of("/Applications/Shale.app");
		RecordingMacPlatformSupport platformSupport = new RecordingMacPlatformSupport(true, null);

		platformSupport.restartApp(installDir);

		assertEquals(installDir, platformSupport.openedPath);
	}

	@Test
	void restartAppFailsClearlyWhenDesktopOpenIsUnavailable() {
		RecordingMacPlatformSupport platformSupport = new RecordingMacPlatformSupport(false, null);

		IOException error = assertThrows(IOException.class, () -> platformSupport.restartApp(Path.of("/Applications/Shale.app")));

		assertEquals("Desktop OPEN action is not supported for macOS relaunch", error.getMessage());
	}

	@Test
	void restartAppSurfacesDesktopOpenFailure() {
		IOException launchFailure = new IOException("Desktop launch failed");
		RecordingMacPlatformSupport platformSupport = new RecordingMacPlatformSupport(true, launchFailure);

		IOException error = assertThrows(IOException.class, () -> platformSupport.restartApp(Path.of("/Applications/Shale.app")));

		assertEquals("Desktop launch failed", error.getMessage());
	}

	private static final class RecordingMacPlatformSupport extends MacPlatformSupport {
		private final boolean desktopSupported;
		private final IOException openFailure;
		private Path openedPath;

		private RecordingMacPlatformSupport(boolean desktopSupported, IOException openFailure) {
			this.desktopSupported = desktopSupported;
			this.openFailure = openFailure;
		}

		@Override
		boolean isDesktopOpenSupported() {
			return desktopSupported;
		}

		@Override
		void openAppBundle(Path installDir) throws IOException {
			if (openFailure != null) {
				throw openFailure;
			}
			openedPath = installDir;
		}
	}
}
