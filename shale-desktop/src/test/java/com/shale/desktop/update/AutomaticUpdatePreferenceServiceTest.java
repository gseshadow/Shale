package com.shale.desktop.update;

import static org.junit.jupiter.api.Assertions.*;

import com.shale.core.update.WorkstationUpdatePreference;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class AutomaticUpdatePreferenceServiceTest {
	@TempDir Path tempDir;

	@Test void missingPreferenceIsExplicitlyDisabledWithoutCreatingAFile() {
		Path file = file();
		WorkstationUpdatePreference result = service(file).current();
		assertEquals(WorkstationUpdatePreference.Status.MISSING, result.status());
		assertFalse(result.unattendedExecutionPermitted());
		assertFalse(Files.exists(file), "Reading the safe default must not imply consent or create state");
	}

	@Test void enabledAndDisabledValuesSurviveRestartAndContainOnlyTheBoundedContract() throws Exception {
		Path file = file();
		assertEquals(AutomaticUpdatePreferenceService.ChangeResult.SAVED, service(file).change(true, true));
		assertEquals(WorkstationUpdatePreference.Status.ENABLED, service(file).current().status());
		assertEquals("schemaVersion=1\nautomaticUpdatesEnabled=true\n", Files.readString(file));
		assertEquals(AutomaticUpdatePreferenceService.ChangeResult.SAVED, service(file).change(false, true));
		assertEquals(WorkstationUpdatePreference.Status.DISABLED, service(file).current().status());
		String serialized = Files.readString(file);
		for (String forbidden : List.of("user", "tenant", "jwt", "jti", "machine", "email", "ip", "location", "metadata"))
			assertFalse(serialized.toLowerCase().contains(forbidden), "Preference must not serialize " + forbidden);
	}

	@Test void malformedUnsupportedInvalidAndOversizedFilesFailSafeAndCanBeReset() throws Exception {
		for (String invalid : List.of("garbage", "schemaVersion=2\nautomaticUpdatesEnabled=true\n",
				"schemaVersion=1\nautomaticUpdatesEnabled=yes\n", "x".repeat(FileWorkstationUpdatePreferenceStore.MAX_BYTES + 1))) {
			Path file = tempDir.resolve("case-" + invalid.hashCode()).resolve("automatic-update-preference.properties");
			Files.createDirectories(file.getParent()); Files.writeString(file, invalid);
			AutomaticUpdatePreferenceService service = service(file);
			assertEquals(WorkstationUpdatePreference.Status.CORRUPT, service.current().status());
			assertFalse(service.current().unattendedExecutionPermitted());
			assertEquals(AutomaticUpdatePreferenceService.ChangeResult.SAVED, service.change(true, true));
			assertEquals(WorkstationUpdatePreference.Status.ENABLED, service.current().status());
			try (var files = Files.list(file.getParent())) {
				assertTrue(files.anyMatch(p -> p.getFileName().toString().startsWith("automatic-update-preference.corrupt-")));
			}
		}
	}

	@Test void ordinaryUserCannotMutateAndIoFailureIsNotReportedAsSuccess() {
		WorkstationUpdatePreferenceStore store = new WorkstationUpdatePreferenceStore() {
			@Override public WorkstationUpdatePreference load() throws IOException { throw new IOException("denied"); }
			@Override public void save(boolean enabled) throws IOException { throw new IOException("denied"); }
		};
		AutomaticUpdatePreferenceService service = new AutomaticUpdatePreferenceService(store);
		assertEquals(AutomaticUpdatePreferenceService.ChangeResult.UNAUTHORIZED, service.change(true, false));
		assertEquals(AutomaticUpdatePreferenceService.ChangeResult.UNAVAILABLE, service.change(true, true));
		assertEquals(WorkstationUpdatePreference.Status.UNAVAILABLE, service.current().status());
	}

	@Test void concurrentProcessesLeaveOneCompleteValidPreferenceAndNoTemporaryFile() throws Exception {
		Path file = file(); CountDownLatch ready = new CountDownLatch(2); CountDownLatch start = new CountDownLatch(1);
		Callable<Void> enable = () -> { ready.countDown(); assertTrue(start.await(5, TimeUnit.SECONDS)); service(file).change(true, true); return null; };
		Callable<Void> disable = () -> { ready.countDown(); assertTrue(start.await(5, TimeUnit.SECONDS)); service(file).change(false, true); return null; };
		try (var executor = Executors.newFixedThreadPool(2)) {
			var a = executor.submit(enable); var b = executor.submit(disable); assertTrue(ready.await(5, TimeUnit.SECONDS)); start.countDown(); a.get(); b.get();
		}
		assertTrue(List.of(WorkstationUpdatePreference.Status.ENABLED, WorkstationUpdatePreference.Status.DISABLED).contains(service(file).current().status()));
		try (var files = Files.list(file.getParent())) {
			assertFalse(files.anyMatch(p -> p.getFileName().toString().contains(".tmp-")), "Atomic saves must clean temporary state");
		}
	}

	private Path file() { return tempDir.resolve("Shale/automatic-update-preference.properties"); }
	private static AutomaticUpdatePreferenceService service(Path file) {
		return new AutomaticUpdatePreferenceService(new FileWorkstationUpdatePreferenceStore(file));
	}
}
