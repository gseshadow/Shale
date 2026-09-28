package com.shale.desktop.identity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class MachineIdentityUpgradeContractTest {
	private static Path repo(String path) {
		Path direct = Path.of(path);
		return Files.exists(direct) ? direct : Path.of("..").resolve(path);
	}

	@Test
	void updaterAndMsiOperateOnInstallPayloadNotMachineIdentityStorage() throws Exception {
		String updater = Files.readString(repo("shale-updater/src/main/java/com/shale/updater/InstallService.java"));
		String msi = Files.readString(repo("build/scripts/build-shale-windows-msi.bat"));
		String mac = Files.readString(repo("build/scripts/build-shale-macos.sh"));

		assertTrue(updater.contains("installDir"), "Updater replacement must remain scoped to its explicit install directory");
		assertFalse(updater.contains("ProgramData"));
		assertFalse(updater.contains("/Library/Application Support/Shale"));
		assertFalse(msi.contains("machine-id"), "Ordinary MSI upgrade/uninstall must not own or remove machine-id");
		assertFalse(mac.contains("machine-id"), "macOS bundle replacement must not own or remove machine-id");
	}
}
