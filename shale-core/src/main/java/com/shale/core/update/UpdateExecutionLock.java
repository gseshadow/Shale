package com.shale.core.update;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

/** Per-install-owner, OS-backed update execution lock. File existence is never authority. */
public final class UpdateExecutionLock implements AutoCloseable {
	public static final String FILE_NAME = "update-execution.lock";
	private final FileChannel channel;
	private final FileLock lock;

	private UpdateExecutionLock(FileChannel channel, FileLock lock) {
		this.channel = channel;
		this.lock = lock;
	}

	public static Path path(Path applicationSupportDirectory) {
		return applicationSupportDirectory.resolve("updates").resolve(FILE_NAME);
	}

	public static Optional<UpdateExecutionLock> tryAcquire(Path lockPath) throws IOException {
		Files.createDirectories(lockPath.toAbsolutePath().normalize().getParent());
		FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
		try {
			FileLock lock = channel.tryLock();
			if (lock == null) { channel.close(); return Optional.empty(); }
			return Optional.of(new UpdateExecutionLock(channel, lock));
		} catch (OverlappingFileLockException ex) {
			channel.close();
			return Optional.empty();
		} catch (IOException | RuntimeException ex) {
			channel.close();
			throw ex;
		}
	}

	@Override public void close() throws IOException {
		try { if (lock.isValid()) lock.release(); }
		finally { channel.close(); }
	}
}
