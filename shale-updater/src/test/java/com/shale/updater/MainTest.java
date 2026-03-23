package com.shale.updater;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.shale.updater.platform.Platform;
import com.shale.updater.platform.PlatformSupport;

final class MainTest {

	@Test
	void restartOrLogManualReopenPreservesInstallSuccessWhenRelaunchFails() {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		PrintStream originalOut = System.out;
		boolean success;

		try (PrintStream capture = new PrintStream(output, true, StandardCharsets.UTF_8)) {
			System.setOut(capture);
			success = Main.restartOrLogManualReopen(new FailingPlatformSupport(), Path.of("/Applications/Shale.app"));
		} finally {
			System.setOut(originalOut);
		}

		String log = output.toString(StandardCharsets.UTF_8);
		assertTrue(success, "install success should remain successful even when relaunch fails");
		assertTrue(log.contains("Install succeeded, but relaunch failed: relaunch unavailable"));
		assertTrue(log.contains("Shale was updated successfully. Please reopen the app manually from: /Applications/Shale.app"));
	}

	private static final class FailingPlatformSupport implements PlatformSupport {
		@Override
		public Platform platform() {
			return Platform.MAC;
		}

		@Override
		public void stopRunningApp(Path installDir) {
		}

		@Override
		public void restartApp(Path installDir) throws Exception {
			throw new IllegalStateException("relaunch unavailable");
		}

		@Override
		public String appExecutableName() {
			return "Shale.app";
		}
	}
}
