package com.shale.desktop.update;

import com.shale.core.update.WorkstationUpdatePreference;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

final class FileWorkstationUpdatePreferenceStore implements WorkstationUpdatePreferenceStore {
	static final int MAX_BYTES = 4096;
	private static final ConcurrentHashMap<Path, ReentrantLock> JVM_LOCKS = new ConcurrentHashMap<>();
	private final Path file;

	FileWorkstationUpdatePreferenceStore(Path file) {
		this.file = file.toAbsolutePath().normalize();
	}

	@Override public WorkstationUpdatePreference load() throws IOException {
		if (!Files.exists(file)) return new WorkstationUpdatePreference(WorkstationUpdatePreference.Status.MISSING);
		byte[] bytes = Files.readAllBytes(file);
		if (bytes.length > MAX_BYTES) return corrupt();
		try {
			Map<String, String> values = parse(new String(bytes, StandardCharsets.UTF_8));
			if (!values.keySet().equals(java.util.Set.of("schemaVersion", "automaticUpdatesEnabled"))
					|| !"1".equals(values.get("schemaVersion"))) return corrupt();
			return switch (values.get("automaticUpdatesEnabled")) {
				case "true" -> new WorkstationUpdatePreference(WorkstationUpdatePreference.Status.ENABLED);
				case "false" -> new WorkstationUpdatePreference(WorkstationUpdatePreference.Status.DISABLED);
				default -> corrupt();
			};
		} catch (IllegalArgumentException malformed) {
			return corrupt();
		}
	}

	@Override public void save(boolean enabled) throws IOException {
		Path parent = file.getParent();
		if (parent == null) throw new IOException("Workstation preference path has no parent");
		Files.createDirectories(parent);
		Path lockFile = parent.resolve("automatic-update-preference.lock");
		ReentrantLock lock = JVM_LOCKS.computeIfAbsent(lockFile, ignored -> new ReentrantLock());
		lock.lock();
		try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
				var ignored = channel.lock()) {
			writeLocked(enabled);
		} finally {
			lock.unlock();
		}
	}

	private void writeLocked(boolean enabled) throws IOException {
		if (Files.exists(file) && load().status() == WorkstationUpdatePreference.Status.CORRUPT) {
			move(file, file.resolveSibling("automatic-update-preference.corrupt-" + UUID.randomUUID()), false);
		}
		Path temporary = file.resolveSibling("automatic-update-preference.tmp-" + UUID.randomUUID());
		byte[] content = ("schemaVersion=1\nautomaticUpdatesEnabled=" + enabled + "\n").getBytes(StandardCharsets.UTF_8);
		try {
			try (FileChannel output = FileChannel.open(temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
				ByteBuffer buffer = ByteBuffer.wrap(content);
				while (buffer.hasRemaining()) output.write(buffer);
				output.force(true);
			}
			move(temporary, file, true);
			WorkstationUpdatePreference reread = load();
			if (reread.unattendedExecutionPermitted() != enabled
					|| reread.status() == WorkstationUpdatePreference.Status.CORRUPT) {
				throw new IOException("Workstation preference verification failed");
			}
		} finally {
			Files.deleteIfExists(temporary);
		}
	}

	private static Map<String, String> parse(String content) {
		Map<String, String> values = new HashMap<>();
		for (String line : content.split("\\R", -1)) {
			if (line.isEmpty()) continue;
			int separator = line.indexOf('=');
			if (separator <= 0 || separator != line.lastIndexOf('=')) throw new IllegalArgumentException();
			if (values.put(line.substring(0, separator), line.substring(separator + 1)) != null) throw new IllegalArgumentException();
		}
		return values;
	}

	private static WorkstationUpdatePreference corrupt() {
		return new WorkstationUpdatePreference(WorkstationUpdatePreference.Status.CORRUPT);
	}

	private static void move(Path source, Path target, boolean replace) throws IOException {
		try {
			if (replace) Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			else Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException exception) {
			throw new IOException("Workstation preference storage does not support atomic moves", exception);
		}
	}
}
