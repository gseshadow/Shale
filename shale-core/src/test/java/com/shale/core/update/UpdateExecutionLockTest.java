package com.shale.core.update;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UpdateExecutionLockTest {
	@Test void osLockIsExclusiveReleasableAndIgnoresStaleFile(@TempDir Path root) throws Exception {
		Path path = UpdateExecutionLock.path(root);
		assertEquals(root.resolve("updates/update-execution.lock"), path);
		try (var first = UpdateExecutionLock.tryAcquire(path).orElseThrow()) {
			assertTrue(UpdateExecutionLock.tryAcquire(path).isEmpty(), "a second update execution must be refused");
			assertEquals(0, Files.size(path), "the coordination file must contain no identity or business data");
		}
		assertTrue(Files.exists(path), "a stale empty lock file documents that existence is not authority");
		try (var subsequent = UpdateExecutionLock.tryAcquire(path).orElseThrow()) {
			assertNotNull(subsequent);
		}
	}
}
