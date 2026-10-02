package com.shale.core.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class AppPathsTest {
	@Test
	void windowsMachineDataUsesProgramDataRatherThanAUserOrInstallDirectory() {
		Path path = AppPaths.machineDataDir("Shale", AppPlatform.WINDOWS,
				Map.of("ProgramData", "C:\\ProgramData", "LOCALAPPDATA", "C:\\Users\\alice\\AppData\\Local"));

		assertEquals(Path.of("C:\\ProgramData", "Shale"), path,
				"Machine identity must be shared through ProgramData, not a Windows user profile");
	}

	@Test
	void windowsMachineDataFailsExplicitlyWithoutProgramData() {
		assertThrows(IllegalStateException.class,
				() -> AppPaths.machineDataDir("Shale", AppPlatform.WINDOWS, Map.of()),
				"Missing machine-wide storage must not fall back to an unstable per-user location");
	}

	@Test
	void macMachineDataUsesSystemApplicationSupport() {
		assertEquals(Path.of("/Library/Application Support/Shale"),
				AppPaths.machineDataDir("Shale", AppPlatform.MAC, Map.of()),
				"macOS machine identity must not use a user's Library directory");
	}

	@Test
	void unsupportedPlatformsAreRejectedInsteadOfInventingLinuxBehavior() {
		assertThrows(UnsupportedOperationException.class,
				() -> AppPaths.machineDataDir("Shale", AppPlatform.OTHER, Map.of()));
	}
}
