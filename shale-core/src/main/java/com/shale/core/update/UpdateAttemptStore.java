package com.shale.core.update;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

/** Atomic, bounded, non-secret local correlation state shared by Shale and its updater. */
public final class UpdateAttemptStore {
	public static final Duration RECONCILIATION_WINDOW = Duration.ofDays(30);
	public static final Duration RETENTION = Duration.ofDays(90);
	private static final long MAX_BYTES = 4096;
	private final Path directory;
	private final Clock clock;

	public UpdateAttemptStore(Path directory) {
		this(directory, Clock.systemUTC());
	}

	public UpdateAttemptStore(Path directory, Clock clock) {
		this.directory = directory.toAbsolutePath().normalize();
		this.clock = clock;
	}

	public Path directory() { return directory; }

	public synchronized void create(UpdateAttempt attempt) throws IOException {
		if (Files.exists(path(attempt.attemptId()))) return;
		write(attempt);
	}

	public synchronized Optional<UpdateAttempt> read(UUID id) throws IOException {
		Path path = path(id);
		if (!Files.exists(path)) return Optional.empty();
		if (Files.size(path) > MAX_BYTES) throw new IOException("Update attempt file exceeds size limit");
		Properties p = new Properties();
		try (InputStream in = Files.newInputStream(path)) { p.load(in); }
		if (!id.toString().equals(p.getProperty("attemptId"))) throw new IOException("Attempt identity mismatch");
		try {
			return Optional.of(new UpdateAttempt(id, required(p, "fromVersion"), nullable(p, "targetVersion"),
					required(p, "releaseChannel"), required(p, "clientType"),
					UpdateAttemptState.valueOf(required(p, "state")), enumOrNull(p, "failureCode", UpdateFailureCode.class),
					nullable(p, "actualVersion"), Instant.parse(required(p, "startedAt")),
					Instant.parse(required(p, "lastUpdatedAt"))));
		} catch (RuntimeException ex) {
			throw new IOException("Invalid update attempt state", ex);
		}
	}

	public synchronized UpdateAttempt transition(UUID id, UpdateAttemptState next, UpdateFailureCode failure,
			String targetVersion, String actualVersion) throws IOException {
		UpdateAttempt current = read(id).orElseThrow(() -> new IOException("Update attempt not found"));
		if (current.state().terminal()) return current;
		if (current.state() == next) {
			if (current.targetVersion() != null || targetVersion == null) return current;
			UpdateAttempt enriched = new UpdateAttempt(id, current.fromVersion(), targetVersion,
					current.releaseChannel(), current.clientType(), current.state(), null, current.actualVersion(),
					current.startedAt(), clock.instant());
			write(enriched);
			return enriched;
		}
		if (!allowed(current.state(), next)) throw new IOException("Invalid update attempt transition");
		UpdateAttempt updated = new UpdateAttempt(id, current.fromVersion(),
				targetVersion == null ? current.targetVersion() : targetVersion,
				current.releaseChannel(), current.clientType(), next, failure, actualVersion,
				current.startedAt(), clock.instant());
		write(updated);
		return updated;
	}

	public synchronized List<UpdateAttempt> readAll() throws IOException {
		if (!Files.isDirectory(directory)) return List.of();
		List<UpdateAttempt> attempts = new ArrayList<>();
		try (var files = Files.list(directory)) {
			for (Path file : files.filter(p -> p.getFileName().toString().endsWith(".properties")).toList()) {
				try { attempts.add(read(UUID.fromString(file.getFileName().toString().replace(".properties", ""))).orElseThrow()); }
				catch (RuntimeException ignored) { /* unrelated/malformed names are not telemetry */ }
			}
		}
		return attempts;
	}

	public synchronized void delete(UUID id) throws IOException { Files.deleteIfExists(path(id)); }

	private static boolean allowed(UpdateAttemptState from, UpdateAttemptState to) {
		if (to == UpdateAttemptState.FAILED || to == UpdateAttemptState.OUTCOME_UNKNOWN) return true;
		return switch (from) {
			case STARTED -> to == UpdateAttemptState.UPDATER_LAUNCHED;
			case UPDATER_LAUNCHED -> to == UpdateAttemptState.INSTALL_APPLIED;
			case INSTALL_APPLIED -> to == UpdateAttemptState.COMPLETED;
			default -> false;
		};
	}

	private void write(UpdateAttempt a) throws IOException {
		Files.createDirectories(directory);
		Properties p = new Properties();
		p.setProperty("attemptId", a.attemptId().toString());
		p.setProperty("fromVersion", a.fromVersion());
		if (a.targetVersion() != null) p.setProperty("targetVersion", a.targetVersion());
		p.setProperty("releaseChannel", a.releaseChannel()); p.setProperty("clientType", a.clientType());
		p.setProperty("state", a.state().name());
		if (a.failureCode() != null) p.setProperty("failureCode", a.failureCode().name());
		if (a.actualVersion() != null) p.setProperty("actualVersion", a.actualVersion());
		p.setProperty("startedAt", a.startedAt().toString()); p.setProperty("lastUpdatedAt", a.lastUpdatedAt().toString());
		Path destination = path(a.attemptId());
		Path temporary = Files.createTempFile(directory, a.attemptId() + ".", ".tmp");
		try {
			try (OutputStream out = Files.newOutputStream(temporary, StandardOpenOption.TRUNCATE_EXISTING)) { p.store(out, null); }
			if (Files.size(temporary) > MAX_BYTES) throw new IOException("Update attempt file exceeds size limit");
			Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} finally { Files.deleteIfExists(temporary); }
	}

	private Path path(UUID id) { return directory.resolve(id + ".properties"); }
	private static String required(Properties p, String key) { String value=p.getProperty(key); if(value==null||value.isBlank()) throw new IllegalArgumentException(key); return value; }
	private static String nullable(Properties p, String key) { String value=p.getProperty(key); return value==null||value.isBlank()?null:value; }
	private static <E extends Enum<E>> E enumOrNull(Properties p,String key,Class<E> type){String value=nullable(p,key);return value==null?null:Enum.valueOf(type,value);}
}
