package com.shale.ui.whatsnew;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.shale.core.dto.ApplicationReleaseItemView;
import com.shale.core.dto.ApplicationReleaseView;
import com.shale.core.dto.UserReleaseStateView;
import com.shale.core.model.ClientType;
import com.shale.core.model.PublicationStatus;
import com.shale.core.model.ReleaseChannel;
import com.shale.core.model.SemanticVersion;
import com.shale.core.service.ApplicationReleaseReadServicePort;
import com.shale.core.service.UserReleaseStateServicePort;

/** Non-critical desktop orchestration for the post-login What's New experience. */
public final class WhatsNewCoordinator implements AutoCloseable {
	private static final Logger LOG = LoggerFactory.getLogger(WhatsNewCoordinator.class);
	private static final SemanticVersion CATALOG_FLOOR = new SemanticVersion(0, 0, 0);
	private static final ClientType CLIENT_TYPE = ClientType.DESKTOP;
	private static final ReleaseChannel CHANNEL = ReleaseChannel.PRODUCTION;

	@FunctionalInterface public interface Presenter {
		void show(WhatsNewPresentation presentation, Runnable onDismissed);
	}

	private final ApplicationReleaseReadServicePort releases;
	private final UserReleaseStateServicePort states;
	private final Supplier<String> versionSource;
	private final Executor worker;
	private final Consumer<Runnable> uiDispatcher;
	private final Presenter presenter;
	private final ExecutorService ownedExecutor;
	private final AtomicBoolean attempted = new AtomicBoolean();
	private volatile UserContext context;
	private volatile long generation;

	public WhatsNewCoordinator(ApplicationReleaseReadServicePort releases, UserReleaseStateServicePort states,
			Supplier<String> versionSource, Consumer<Runnable> uiDispatcher, Presenter presenter) {
		this(releases, states, versionSource, newWorker(), uiDispatcher, presenter, true);
	}

	WhatsNewCoordinator(ApplicationReleaseReadServicePort releases, UserReleaseStateServicePort states,
			Supplier<String> versionSource, Executor worker, Consumer<Runnable> uiDispatcher, Presenter presenter) {
		this(releases, states, versionSource, worker, uiDispatcher, presenter, false);
	}

	private WhatsNewCoordinator(ApplicationReleaseReadServicePort releases, UserReleaseStateServicePort states,
			Supplier<String> versionSource, Executor worker, Consumer<Runnable> uiDispatcher, Presenter presenter,
			boolean ownsExecutor) {
		this.releases = Objects.requireNonNull(releases, "releases");
		this.states = Objects.requireNonNull(states, "states");
		this.versionSource = Objects.requireNonNull(versionSource, "versionSource");
		this.worker = Objects.requireNonNull(worker, "worker");
		this.uiDispatcher = Objects.requireNonNull(uiDispatcher, "uiDispatcher");
		this.presenter = Objects.requireNonNull(presenter, "presenter");
		this.ownedExecutor = ownsExecutor ? (ExecutorService) worker : null;
	}

	/** Starts at most once for the current authenticated user context. */
	public void start(int tenantId, int userId) {
		if (tenantId <= 0 || userId <= 0) return;
		UserContext requested = new UserContext(tenantId, userId);
		synchronized (this) {
			if (!requested.equals(context)) {
				context = requested;
				generation++;
				attempted.set(false);
			}
			if (!attempted.compareAndSet(false, true)) return;
			long requestedGeneration = generation;
			worker.execute(() -> evaluate(requested, requestedGeneration));
		}
	}

	/** Clears the in-memory launch guard when desktop authentication is torn down. */
	public synchronized void reset() {
		context = null;
		generation++;
		attempted.set(false);
	}

	private void evaluate(UserContext requested, long requestedGeneration) {
		try {
			SemanticVersion running = SemanticVersion.parse(versionSource.get());
			Optional<UserReleaseStateView> current = states.findCurrent(requested.tenantId, requested.userId,
					CLIENT_TYPE, CHANNEL);
			Selection selection = select(running, current);
			if (selection == null || !isCurrent(requested, requestedGeneration)) return;
			if (selection.presentation == null) {
				acknowledge(requested, requestedGeneration, selection.target, current.orElse(null));
				return;
			}
			uiDispatcher.accept(() -> {
				if (isCurrent(requested, requestedGeneration)) presenter.show(selection.presentation,
						() -> worker.execute(() -> acknowledge(requested, requestedGeneration, selection.target, current.orElse(null))));
			});
		} catch (RuntimeException failure) {
			LOG.warn("What's New evaluation was skipped for this desktop launch ({}).", failure.getClass().getSimpleName());
		}
	}

	private Selection select(SemanticVersion running, Optional<UserReleaseStateView> current) {
		SemanticVersion lower = current.map(UserReleaseStateView::version).orElse(CATALOG_FLOOR);
		List<ApplicationReleaseView> candidates = new ArrayList<>(releases.listPublishedReleasesAfter(CHANNEL, lower));
		candidates.removeIf(release -> release.publicationStatus() != PublicationStatus.PUBLISHED
				|| release.channel() != CHANNEL || release.version().compareTo(running) > 0
				|| (current.isPresent() && release.version().compareTo(lower) <= 0));
		candidates.sort(Comparator.comparing(ApplicationReleaseView::version).thenComparingLong(ApplicationReleaseView::id));
		if (candidates.isEmpty()) return null;
		if (current.isEmpty()) candidates = List.of(candidates.get(candidates.size() - 1));

		List<WhatsNewPresentation.ReleaseSection> sections = new ArrayList<>();
		for (ApplicationReleaseView release : candidates) {
			List<ApplicationReleaseItemView> items = new ArrayList<>(releases.listReleaseItems(release.id()));
			items.removeIf(item -> !item.active() || item.releaseId() != release.id());
			items.sort(Comparator.comparingInt(ApplicationReleaseItemView::sortOrder)
					.thenComparingLong(ApplicationReleaseItemView::id));
			if (!items.isEmpty()) sections.add(new WhatsNewPresentation.ReleaseSection(release.id(), release.version(),
					release.summary(), items.stream().map(WhatsNewCoordinator::item).toList()));
		}
		ApplicationReleaseView target = sections.isEmpty() ? candidates.get(candidates.size() - 1)
				: candidates.stream().filter(release -> release.id() == sections.get(sections.size() - 1).releaseId()).findFirst().orElseThrow();
		WhatsNewPresentation presentation = sections.isEmpty() ? null
				: new WhatsNewPresentation(running, target.version(), target.id(), sections);
		return new Selection(target, presentation);
	}

	private void acknowledge(UserContext requested, long requestedGeneration, ApplicationReleaseView target, UserReleaseStateView expected) {
		if (!isCurrent(requested, requestedGeneration)) return;
		try {
			states.acknowledge(requested.tenantId, requested.userId, CLIENT_TYPE, CHANNEL, target.id(),
					expected == null ? null : expected.rowVersion());
		} catch (RuntimeException failure) {
			try {
				Optional<UserReleaseStateView> durable = states.findCurrent(requested.tenantId, requested.userId,
						CLIENT_TYPE, CHANNEL);
				if (durable.isPresent() && durable.get().version().compareTo(target.version()) >= 0) return;
			} catch (RuntimeException reloadFailure) {
				failure.addSuppressed(reloadFailure);
			}
			LOG.warn("What's New dismissal could not be saved; it may be offered on a later launch ({}).",
					failure.getClass().getSimpleName());
		}
	}

	private boolean isCurrent(UserContext requested, long requestedGeneration) {
		return requested.equals(context) && requestedGeneration == generation;
	}
	private static WhatsNewPresentation.Item item(ApplicationReleaseItemView value) {
		return new WhatsNewPresentation.Item(value.id(), value.itemType(), value.title(), value.body(), value.resourceUrl());
	}
	private static ExecutorService newWorker() {
		return Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "whats-new-worker"); t.setDaemon(true); return t; });
	}
	@Override public void close() { reset(); if (ownedExecutor != null) ownedExecutor.shutdownNow(); }
	private record UserContext(int tenantId, int userId) {}
	private record Selection(ApplicationReleaseView target, WhatsNewPresentation presentation) {}
}
