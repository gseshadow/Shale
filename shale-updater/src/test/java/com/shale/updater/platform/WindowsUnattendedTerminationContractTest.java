package com.shale.updater.platform;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import com.shale.core.update.UpdateInvocationMode;

class WindowsUnattendedTerminationContractTest {
	@Test void absentOrUnknownModeRemainsManual() {
		assertEquals(UpdateInvocationMode.MANUAL, UpdateInvocationMode.fromArgument(null));
		assertEquals(UpdateInvocationMode.MANUAL, UpdateInvocationMode.fromArgument("legacy"));
		assertEquals(UpdateInvocationMode.UNATTENDED, UpdateInvocationMode.fromArgument("unattended"));
	}

	@Test void forceTerminationIsConfinedToManualMethod() throws Exception {
		String source = Files.readString(Path.of("src/main/java/com/shale/updater/platform/WindowsPlatformSupport.java"));
		int manual = source.indexOf("void stopRunningApp(Path installDir)");
		int modeAware = source.indexOf("boolean stopRunningApp(Path installDir, UpdateInvocationMode mode)");
		assertTrue(manual >= 0 && modeAware > manual);
		String unattendedBranch = source.substring(modeAware);
		assertFalse(unattendedBranch.contains("\"taskkill\""), "unattended mode must never gain a force-termination command");
		assertTrue(source.substring(manual, modeAware).contains("\"/F\""), "manual N-1 behavior remains compatible");
	}
}
