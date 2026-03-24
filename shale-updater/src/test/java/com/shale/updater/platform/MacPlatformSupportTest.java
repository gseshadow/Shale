package com.shale.updater.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

final class MacPlatformSupportTest {

	@Test
	void restartAppUsesOpenCommandStrategyWhenCommandSucceeds() throws Exception {
		Path installDir = Path.of("/Applications/Shale.app");
		RecordingMacPlatformSupport platformSupport = new RecordingMacPlatformSupport(new FakeProcess(0, ""), null);

		platformSupport.restartApp(installDir);

		assertEquals(List.of("/usr/bin/open", "-n", installDir.toString()), platformSupport.startedCommand);
	}

	@Test
	void restartAppFailsClearlyWhenOpenCommandReturnsNonZero() {
		RecordingMacPlatformSupport platformSupport = new RecordingMacPlatformSupport(new FakeProcess(1, "Launch failed"), null);

		IOException error = assertThrows(IOException.class, () -> platformSupport.restartApp(Path.of("/Applications/Shale.app")));

		assertTrue(error.getMessage().contains("macOS relaunch command exited with code 1"));
		assertTrue(error.getMessage().contains("Launch failed"));
	}

	@Test
	void restartAppSurfacesCommandLaunchFailure() {
		IOException launchFailure = new IOException("Desktop launch failed");
		RecordingMacPlatformSupport platformSupport = new RecordingMacPlatformSupport(null, launchFailure);

		IOException error = assertThrows(IOException.class, () -> platformSupport.restartApp(Path.of("/Applications/Shale.app")));

		assertEquals("Desktop launch failed", error.getMessage());
	}

	@Test
	void armPreReplacementRelaunchStartsDetachedHelper() {
		RecordingMacPlatformSupport platformSupport = new RecordingMacPlatformSupport(new FakeProcess(0, ""), null);

		boolean armed = platformSupport.armPreReplacementRelaunch(Path.of("/Applications/Shale.app"));

		assertTrue(armed);
		assertEquals(List.of("/bin/sh"), platformSupport.helperCommand.subList(0, 1));
		assertTrue(platformSupport.helperCommand.get(1).endsWith(".sh"));
		assertEquals("/Applications/Shale.app", platformSupport.helperCommand.get(2));
		assertTrue(platformSupport.helperLog.toString().endsWith(".log"));
	}

	@Test
	void armPreReplacementRelaunchReturnsFalseWhenHelperStartFails() {
		RecordingMacPlatformSupport platformSupport = new RecordingMacPlatformSupport(new FakeProcess(0, ""), null);
		platformSupport.helperFailure = new IOException("helper failed");

		boolean armed = platformSupport.armPreReplacementRelaunch(Path.of("/Applications/Shale.app"));

		assertFalse(armed);
	}

	private static final class RecordingMacPlatformSupport extends MacPlatformSupport {
		private final FakeProcess process;
		private final IOException openFailure;
		private List<String> startedCommand;
		private List<String> helperCommand;
		private Path helperLog;
		private IOException helperFailure;

		private RecordingMacPlatformSupport(FakeProcess process, IOException openFailure) {
			this.process = process;
			this.openFailure = openFailure;
		}

		@Override
		Process startRelaunchCommand(List<String> command) throws IOException {
			if (openFailure != null) {
				throw openFailure;
			}
			startedCommand = command;
			return process;
		}

		@Override
		Process startDetachedRelaunchHelper(List<String> command, Path helperLog) throws IOException {
			if (helperFailure != null) {
				throw helperFailure;
			}
			this.helperCommand = command;
			this.helperLog = helperLog;
			return process;
		}
	}

	private static final class FakeProcess extends Process {
		private final int exitCode;
		private final byte[] output;

		private FakeProcess(int exitCode, String output) {
			this.exitCode = exitCode;
			this.output = output.getBytes(StandardCharsets.UTF_8);
		}

		@Override
		public OutputStream getOutputStream() {
			return OutputStream.nullOutputStream();
		}

		@Override
		public InputStream getInputStream() {
			return new ByteArrayInputStream(output);
		}

		@Override
		public InputStream getErrorStream() {
			return InputStream.nullInputStream();
		}

		@Override
		public int waitFor() {
			return exitCode;
		}

		@Override
		public int exitValue() {
			return exitCode;
		}

		@Override
		public void destroy() {
		}

		@Override
		public Process destroyForcibly() {
			return this;
		}

		@Override
		public boolean isAlive() {
			return false;
		}
	}
}
