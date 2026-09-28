package com.shale.desktop.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class MachineIdentityProviderTest {
	@TempDir Path tempDir;

	@Test
	void firstUsePersistsCanonicalRandomUuidAndRestartReturnsIt() throws Exception {
		Path file = tempDir.resolve("nested/Shale/machine-id");
		AtomicInteger generated = new AtomicInteger();
		UUID expected = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
		MachineIdentityProvider first = provider(file, () -> { generated.incrementAndGet(); return expected; });

		MachineIdentityResult created = first.getOrCreate();
		MachineIdentityResult sameProvider = first.getOrCreate();
		MachineIdentityResult restarted = provider(file, () -> { throw new AssertionError("must not regenerate"); })
				.getOrCreate();

		assertEquals(expected, created.machineId().orElseThrow());
		assertEquals(created, sameProvider);
		assertEquals(created, restarted);
		assertEquals(1, generated.get(), "A persisted machine identity must not be regenerated");
		assertEquals(expected.toString(), Files.readString(file).trim(),
				"The file contract is one canonical lowercase UUID");
		assertTrue(Files.isDirectory(file.getParent()), "First use must create the machine data directory");
	}

	@Test
	void existingIdentityAllowsTrailingNewline() throws Exception {
		Path file = identityFile();
		UUID expected = UUID.randomUUID();
		Files.writeString(file, expected + "\n");

		assertEquals(expected, provider(file, () -> { throw new AssertionError("must not regenerate"); })
				.getOrCreate().machineId().orElseThrow());
	}

	@Test
	void malformedEmptyAndTruncatedValuesArePreservedThenRecovered() throws Exception {
		for (String invalid : List.of("not-a-uuid", "", "550e8400-e29b-41d4-a716",
				" 550e8400-e29b-41d4-a716-446655440000")) {
			Path caseDir = tempDir.resolve("case-" + UUID.randomUUID());
			Files.createDirectories(caseDir);
			Path file = caseDir.resolve("machine-id");
			Files.writeString(file, invalid);
			UUID replacement = UUID.randomUUID();

			MachineIdentityResult result = provider(file, () -> replacement).getOrCreate();

			assertEquals(replacement, result.machineId().orElseThrow());
			assertEquals(replacement.toString(), Files.readString(file).trim());
			try (var files = Files.list(caseDir)) {
				assertEquals(1, files.filter(path -> path.getFileName().toString().startsWith("machine-id.corrupt-"))
						.count(), "Malformed evidence should be retained once for diagnosis");
			}
		}
	}

	@Test
	void concurrentProvidersConvergeOnOnePersistedIdentityWithoutTemporaryFiles() throws Exception {
		Path file = tempDir.resolve("shared/machine-id");
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		Callable<UUID> call = () -> {
			ready.countDown();
			assertTrue(start.await(5, TimeUnit.SECONDS));
			return provider(file, UUID::randomUUID).getOrCreate().machineId().orElseThrow();
		};
		try (var executor = Executors.newFixedThreadPool(2)) {
			var first = executor.submit(call);
			var second = executor.submit(call);
			assertTrue(ready.await(5, TimeUnit.SECONDS));
			start.countDown();
			UUID firstId = first.get(5, TimeUnit.SECONDS);
			UUID secondId = second.get(5, TimeUnit.SECONDS);
			assertEquals(firstId, secondId, "Concurrent first launches must observe the same winner");
			assertEquals(firstId.toString(), Files.readString(file).trim());
		}
		try (var files = Files.list(file.getParent())) {
			assertFalse(files.anyMatch(path -> path.getFileName().toString().startsWith("machine-id.tmp-")),
					"Atomic initialization must not leave partial temporary identity files");
		}
	}

	@Test
	void temporaryWriteFailureReturnsExplicitUnavailableResultAndNoEphemeralIdentity() {
		Path file = tempDir.resolve("write-failure/machine-id");
		MachineIdentityProvider provider = new MachineIdentityProvider(
				new FileMachineIdentityStore(file, new DelegatingFiles() {
					@Override public void writeAndSync(Path path, byte[] content) throws IOException {
						throw new IOException("simulated write denial");
					}
				}), UUID::randomUUID);

		assertUnavailable(provider.getOrCreate());
		assertFalse(Files.exists(file), "A failed write must not masquerade as stable identity");
	}

	@Test
	void atomicMoveFailureReturnsExplicitUnavailableResultAndCleansTemporaryFile() throws Exception {
		Path file = tempDir.resolve("move-failure/machine-id");
		MachineIdentityProvider provider = new MachineIdentityProvider(
				new FileMachineIdentityStore(file, new DelegatingFiles() {
					@Override public void atomicMove(Path source, Path target, boolean replace) throws IOException {
						throw new IOException("simulated atomic move failure");
					}
				}), UUID::randomUUID);

		assertUnavailable(provider.getOrCreate());
		assertFalse(Files.exists(file));
		try (var files = Files.list(file.getParent())) {
			assertFalse(files.anyMatch(path -> path.getFileName().toString().startsWith("machine-id.tmp-")));
		}
	}

	@Test
	void readPermissionFailureReturnsExplicitUnavailableResult() throws Exception {
		Path file = identityFile();
		Files.writeString(file, UUID.randomUUID().toString());
		MachineIdentityProvider provider = new MachineIdentityProvider(
				new FileMachineIdentityStore(file, new DelegatingFiles() {
					@Override public String readString(Path path) throws IOException {
						throw new IOException("simulated permission denial");
					}
				}), UUID::randomUUID);

		assertUnavailable(provider.getOrCreate());
	}

	@Test
	void identityHasNoUserOrTenantInput() {
		assertEquals(2, MachineIdentityProvider.class.getDeclaredConstructors()[0].getParameterCount(),
				"Machine identity construction must remain independent of Shale user and tenant context");
	}

	private Path identityFile() throws IOException {
		Path file = tempDir.resolve(UUID.randomUUID() + "/machine-id");
		Files.createDirectories(file.getParent());
		return file;
	}

	private static MachineIdentityProvider provider(Path file, java.util.function.Supplier<UUID> generator) {
		return new MachineIdentityProvider(new FileMachineIdentityStore(file), generator);
	}

	private static void assertUnavailable(MachineIdentityResult result) {
		assertFalse(result.isAvailable(), "Persistence failures must not return an ephemeral machine UUID");
		assertEquals(MachineIdentityResult.Failure.STORAGE_ACCESS_FAILED, result.failure().orElseThrow());
	}

	private static class DelegatingFiles implements FileMachineIdentityStore.FileOperations {
		public void createDirectories(Path directory) throws IOException { Files.createDirectories(directory); }
		public String readString(Path file) throws IOException { return Files.readString(file); }
		public boolean exists(Path file) { return Files.exists(file); }
		public FileChannel open(Path file, Set<? extends OpenOption> options) throws IOException {
			return FileChannel.open(file, options);
		}
		public void writeAndSync(Path file, byte[] content) throws IOException {
			try (FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
				channel.write(ByteBuffer.wrap(content));
				channel.force(true);
			}
		}
		public void atomicMove(Path source, Path target, boolean replace) throws IOException {
			if (replace) Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			else Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
		}
		public void deleteIfExists(Path file) throws IOException { Files.deleteIfExists(file); }
	}
}
