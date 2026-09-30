package com.shale.core.update;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WindowsInstallationRegistrationTest {
	@Test void derivesTheExistingOwnerAttemptStoreAndSharedLockWithoutProcessLocalAppData() {
		var registration=new WindowsInstallationRegistration(1,UUID.randomUUID(),"S-1-5-21-1-2-3-1001",
				Path.of("C:\\Users\\Owner\\AppData\\Local\\Shale"),Path.of("D:\\Profiles\\Owner\\AppData\\Local\\Shale"));
		var paths=OwnerUpdatePaths.from(registration);
		assertEquals(registration.supportRoot().resolve("update-attempts"),paths.attemptStore());
		assertEquals(UpdateExecutionLock.path(registration.supportRoot()),paths.executionLock());
		assertEquals(registration.installRoot().resolve("app").resolve("updater").resolve("ShaleUpdater.exe"),paths.updaterExecutable());
	}
	@Test void rejectsInvalidSidRelativeTraversalAndUnexpectedLayouts() {
		UUID id=UUID.randomUUID(); Path support=Path.of("C:\\Users\\Owner\\AppData\\Local\\Shale");
		assertThrows(IllegalArgumentException.class,()->new WindowsInstallationRegistration(1,id,"owner",support,support));
		assertThrows(IllegalArgumentException.class,()->new WindowsInstallationRegistration(1,id,"S-1-5-18",Path.of("Shale"),support));
		assertThrows(IllegalArgumentException.class,()->new WindowsInstallationRegistration(1,id,"S-1-5-18",Path.of("C:\\Safe\\..\\Shale"),support));
		assertThrows(IllegalArgumentException.class,()->new WindowsInstallationRegistration(1,id,"S-1-5-18",Path.of("C:\\Other"),support));
	}
	@Test void multipleOwnersAndInstallationsRemainDistinct() {
		var a=new WindowsInstallationRegistration(1,UUID.randomUUID(),"S-1-5-21-1-2-3-1001",Path.of("C:\\Users\\A\\Shale"),Path.of("C:\\Users\\A\\Local\\Shale"));
		var b=new WindowsInstallationRegistration(1,UUID.randomUUID(),"S-1-5-21-1-2-3-1002",Path.of("C:\\Users\\B\\Shale"),Path.of("C:\\Users\\B\\Local\\Shale"));
		assertNotEquals(a.installationId(),b.installationId()); assertNotEquals(a.ownerSid(),b.ownerSid()); assertNotEquals(OwnerUpdatePaths.from(a),OwnerUpdatePaths.from(b));
	}
}
