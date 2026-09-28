package com.shale.desktop.identity;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

final class FileMachineIdentityStore implements MachineIdentityStore {
	private static final Set<OpenOption> LOCK_OPTIONS = Set.of(
			StandardOpenOption.CREATE, StandardOpenOption.WRITE);
	private static final ConcurrentHashMap<Path, ReentrantLock> JVM_LOCKS = new ConcurrentHashMap<>();

	interface FileOperations {
		void createDirectories(Path directory) throws IOException;
		String readString(Path file) throws IOException;
		boolean exists(Path file);
		FileChannel open(Path file, Set<? extends OpenOption> options) throws IOException;
		void writeAndSync(Path file, byte[] content) throws IOException;
		void atomicMove(Path source, Path target, boolean replace) throws IOException;
		void deleteIfExists(Path file) throws IOException;
	}

	private static final class NioFileOperations implements FileOperations {
		public void createDirectories(Path directory) throws IOException { Files.createDirectories(directory); }
		public String readString(Path file) throws IOException { return Files.readString(file, StandardCharsets.UTF_8); }
		public boolean exists(Path file) { return Files.exists(file); }
		public FileChannel open(Path file, Set<? extends OpenOption> options) throws IOException {
			return FileChannel.open(file, options);
		}
		public void writeAndSync(Path file, byte[] content) throws IOException {
			try (FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
				ByteBuffer buffer = ByteBuffer.wrap(content);
				while (buffer.hasRemaining()) channel.write(buffer);
				channel.force(true);
			}
		}
		public void atomicMove(Path source, Path target, boolean replace) throws IOException {
			try {
				if (replace) Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
				else Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException exception) {
				throw new IOException("Machine identity storage does not support atomic moves", exception);
			}
		}
		public void deleteIfExists(Path file) throws IOException { Files.deleteIfExists(file); }
	}

	private final Path identityFile;
	private final FileOperations files;

	FileMachineIdentityStore(Path identityFile) {
		this(identityFile, new NioFileOperations());
	}

	FileMachineIdentityStore(Path identityFile, FileOperations files) {
		this.identityFile = identityFile.toAbsolutePath().normalize();
		this.files = files;
	}

	@Override
	public StoredMachineIdentity getOrCreate(Supplier<UUID> generator) throws IOException {
		Path parent = identityFile.getParent();
		if (parent == null) throw new IOException("Machine identity path has no parent directory");
		files.createDirectories(parent);
		Path lockPath = parent.resolve("machine-id.lock");
		ReentrantLock jvmLock = JVM_LOCKS.computeIfAbsent(lockPath, ignored -> new ReentrantLock());
		jvmLock.lock();
		try (FileChannel channel = files.open(lockPath, LOCK_OPTIONS); var ignored = channel.lock()) {
			return initializeWhileLocked(generator);
		} finally {
			jvmLock.unlock();
		}
	}

	private StoredMachineIdentity initializeWhileLocked(Supplier<UUID> generator) throws IOException {
		boolean recovered = false;
		if (files.exists(identityFile)) {
			try {
				return new StoredMachineIdentity(parse(files.readString(identityFile)),
						StoredMachineIdentity.State.EXISTING);
			} catch (IllegalArgumentException invalid) {
				Path corrupt = identityFile.resolveSibling("machine-id.corrupt-" + UUID.randomUUID());
				files.atomicMove(identityFile, corrupt, false);
				recovered = true;
			}
		}

		UUID generated = generator.get();
		Path temporary = identityFile.resolveSibling("machine-id.tmp-" + UUID.randomUUID());
		try {
			files.writeAndSync(temporary, (generated.toString() + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
			files.atomicMove(temporary, identityFile, false);
		} finally {
			files.deleteIfExists(temporary);
		}
		UUID persisted = parse(files.readString(identityFile));
		return new StoredMachineIdentity(persisted, recovered
				? StoredMachineIdentity.State.RECOVERED_CORRUPT
				: StoredMachineIdentity.State.CREATED);
	}

	private static UUID parse(String content) {
		String value = content.endsWith("\r\n") ? content.substring(0, content.length() - 2)
				: content.endsWith("\n") ? content.substring(0, content.length() - 1) : content;
		UUID parsed = UUID.fromString(value);
		if (!parsed.toString().equals(value)) {
			throw new IllegalArgumentException("Machine identity is not canonical");
		}
		return parsed;
	}
}
