package com.shale.ui.notification;

import com.shale.core.service.NotificationServicePort;
import com.shale.core.service.NotificationServicePort.NotificationCursor;
import com.shale.core.service.NotificationServicePort.NotificationPage;
import com.shale.core.service.NotificationServicePort.NotificationSummary;
import java.time.Clock;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Session-owned, cursor-based durable notification synchronization. */
public final class NotificationPollingService implements AutoCloseable {
	private static final Logger log = LoggerFactory.getLogger(NotificationPollingService.class);
	private final NotificationServicePort source;
	private final NotificationCenterService center;
	private final DurableNotificationService mapper;
	private final NotificationPrivacyProjector projector;
	private final DesktopNotificationPresenter presenter;
	private final ScheduledExecutorService scheduler;
	private final Consumer<Runnable> uiExecutor;
	private final Clock clock;
	private final DoubleSupplier jitter;
	private final Config config;
	private final boolean ownsScheduler;
	private final Set<Long> presentedOrAttempted = new HashSet<>();
	private long generation;
	private Session session;
	private ScheduledFuture<?> scheduled;
	private boolean closed;

	public NotificationPollingService(NotificationServicePort source, NotificationCenterService center,
			DurableNotificationService mapper, DesktopNotificationPresenter presenter, Consumer<Runnable> uiExecutor) {
		this(source, center, mapper, new NotificationPrivacyProjector(), presenter, daemonScheduler(), uiExecutor,
				Clock.systemUTC(), Math::random, Config.defaults(), true);
	}

	NotificationPollingService(NotificationServicePort source, NotificationCenterService center,
			DurableNotificationService mapper, NotificationPrivacyProjector projector,
			DesktopNotificationPresenter presenter, ScheduledExecutorService scheduler,
			Consumer<Runnable> uiExecutor, Clock clock, DoubleSupplier jitter, Config config,
			boolean ownsScheduler) {
		this.source = Objects.requireNonNull(source, "source");
		this.center = Objects.requireNonNull(center, "center");
		this.mapper = Objects.requireNonNull(mapper, "mapper");
		this.projector = Objects.requireNonNull(projector, "projector");
		this.presenter = Objects.requireNonNull(presenter, "presenter");
		this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
		this.uiExecutor = Objects.requireNonNull(uiExecutor, "uiExecutor");
		this.clock = Objects.requireNonNull(clock, "clock");
		this.jitter = Objects.requireNonNull(jitter, "jitter");
		this.config = Objects.requireNonNull(config, "config");
		this.ownsScheduler = ownsScheduler;
	}

	public synchronized void start(int shaleClientId, int userId) {
		if (closed) throw new IllegalStateException("polling service is closed");
		if (shaleClientId <= 0 || userId <= 0) return;
		stopInternal();
		long token = ++generation;
		session = new Session(shaleClientId, userId, token, null, false, 0);
		presentedOrAttempted.clear();
		schedule(token, Duration.ZERO);
	}

	public synchronized void stop() {
		stopInternal();
		generation++;
	}

	private void stopInternal() {
		if (scheduled != null) scheduled.cancel(true);
		scheduled = null;
		session = null;
		presentedOrAttempted.clear();
	}

	private synchronized void schedule(long token, Duration delay) {
		if (!active(token)) return;
		scheduled = scheduler.schedule(() -> poll(token), Math.max(0, delay.toMillis()), TimeUnit.MILLISECONDS);
	}

	private void poll(long token) {
		Session snapshot;
		synchronized (this) { if (!active(token)) return; snapshot = session; scheduled = null; }
		try {
			if (!snapshot.baselineEstablished()) {
				long highWater = source.notificationHighWaterMark(snapshot.tenantId(), snapshot.userId());
				if (!active(token)) return;
				snapshot = snapshot.withCursor(NotificationCursor.after(highWater), true).withFailures(0);
				setSession(snapshot);
			}
			do {
				NotificationPage page = source.listNotifications(snapshot.tenantId(), snapshot.userId(), snapshot.cursor(), config.pageSize());
				if (!active(token)) return;
				validatePage(snapshot.cursor(), page);
				reconcileAndPresent(token, page.items());
				snapshot = snapshot.withCursor(page.nextCursor(), true).withFailures(0);
				setSession(snapshot);
				if (!page.hasMore()) break;
			} while (active(token));
			if (active(token)) schedule(token, config.pollInterval());
		} catch (IllegalArgumentException | SecurityException failClosed) {
			log.warn("Notification polling stopped tenantId={} userId={} code=fail_closed", snapshot.tenantId(), snapshot.userId());
			stopIfActive(token);
		} catch (RuntimeException transientFailure) {
			if (!active(token)) return;
			int failures = Math.min(30, snapshot.failures() + 1);
			Session failed = snapshot.withFailures(failures);
			setSession(failed);
			Duration delay = retryDelay(failures);
			log.warn("Notification polling retry tenantId={} userId={} retry={} delayMs={} code=transient",
					snapshot.tenantId(), snapshot.userId(), failures, delay.toMillis());
			schedule(token, delay);
		}
	}

	private void reconcileAndPresent(long token, List<NotificationSummary> rows) {
		for (NotificationSummary row : rows) {
			if (!active(token)) return;
			AppNotification appNotification = mapper.fromSummary(row);
			if (appNotification != null) uiExecutor.accept(() -> { if (active(token)) center.pushNotification(appNotification); });
			if (appNotification == null || !mapper.isPresentationEnabled(row)) continue;
			synchronized (this) { if (!active(token) || !presentedOrAttempted.add(row.id())) continue; }
			try {
				presenter.present(projector.project(row));
			} catch (RuntimeException ignored) {
				log.warn("Notification presenter failed notificationId={} category={} code=presenter_failed",
						row.id(), NotificationPrivacyProjector.allowlistedCategory(row.category()));
			}
		}
	}

	private static void validatePage(NotificationCursor prior, NotificationPage page) {
		if (page == null || page.nextCursor() == null) throw new IllegalArgumentException("Invalid cursor page");
		long previous = prior.afterNotificationId();
		long last = previous;
		for (NotificationSummary row : page.items()) {
			if (row == null || row.id() <= previous || row.id() < last) throw new IllegalArgumentException("Invalid cursor page");
			last = row.id();
		}
		if (!page.items().isEmpty() && page.nextCursor().afterNotificationId() < last) throw new IllegalArgumentException("Invalid cursor page");
		if (page.hasMore() && page.items().isEmpty()) throw new IllegalArgumentException("Invalid cursor page");
	}

	private Duration retryDelay(int failures) {
		long multiplier = 1L << Math.min(20, Math.max(0, failures - 1));
		long base = Math.min(config.maximumRetryDelay().toMillis(), config.initialRetryDelay().toMillis() * multiplier);
		double unit = Math.max(0, Math.min(1, jitter.getAsDouble()));
		long spread = Math.round(base * config.jitterFraction());
		long adjusted = base - spread + Math.round(2 * spread * unit);
		return Duration.ofMillis(Math.min(config.maximumRetryDelay().toMillis(), Math.max(1, adjusted)));
	}

	private synchronized boolean active(long token) { return !closed && session != null && generation == token && session.generation() == token; }
	private synchronized void setSession(Session replacement) { if (active(replacement.generation())) session = replacement; }
	private synchronized void stopIfActive(long token) { if (active(token)) { stopInternal(); generation++; } }

	@Override public synchronized void close() {
		if (closed) return;
		stopInternal();
		closed = true;
		generation++;
		if (ownsScheduler) scheduler.shutdownNow();
	}

	private static ScheduledExecutorService daemonScheduler() {
		return Executors.newSingleThreadScheduledExecutor(r -> { Thread t = new Thread(r, "notification-polling-worker"); t.setDaemon(true); return t; });
	}

	record Session(int tenantId, int userId, long generation, NotificationCursor cursor, boolean baselineEstablished, int failures) {
		Session withCursor(NotificationCursor value, boolean baseline) { return new Session(tenantId,userId,generation,value,baseline,failures); }
		Session withFailures(int value) { return new Session(tenantId,userId,generation,cursor,baselineEstablished,value); }
	}

	public record Config(Duration pollInterval, Duration initialRetryDelay, Duration maximumRetryDelay,
			double jitterFraction, int pageSize) {
		public Config {
			if (pollInterval.isNegative() || pollInterval.isZero() || initialRetryDelay.isNegative()
					|| initialRetryDelay.isZero() || maximumRetryDelay.compareTo(initialRetryDelay) < 0
					|| jitterFraction < 0 || jitterFraction > 1 || pageSize < 1 || pageSize > 100) {
				throw new IllegalArgumentException("Invalid notification polling configuration");
			}
		}
		public static Config defaults() { return new Config(Duration.ofSeconds(60), Duration.ofSeconds(5), Duration.ofMinutes(5), .20, 50); }
	}
}
