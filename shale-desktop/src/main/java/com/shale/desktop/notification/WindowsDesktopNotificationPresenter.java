package com.shale.desktop.notification;

import com.shale.ui.notification.DesktopNotificationPresenter;
import com.shale.ui.notification.NativeNotificationPresentation;
import com.shale.ui.notification.PresentationResult;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WindowsDesktopNotificationPresenter implements DesktopNotificationPresenter {
	private static final Logger log = LoggerFactory.getLogger(WindowsDesktopNotificationPresenter.class);
	private final WindowsNotificationBridge bridge;
	private final ExecutorService executor;
	private final AtomicLong generation = new AtomicLong();
	private volatile State state = State.NEW;

	WindowsDesktopNotificationPresenter(WindowsNotificationBridge bridge) {
		this(bridge, Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "windows-toast-worker"); t.setDaemon(true); return t; }));
	}
	WindowsDesktopNotificationPresenter(WindowsNotificationBridge bridge, ExecutorService executor) {
		this.bridge = Objects.requireNonNull(bridge); this.executor = Objects.requireNonNull(executor);
	}
	@Override public PresentationResult present(NativeNotificationPresentation presentation) {
		Objects.requireNonNull(presentation);
		if (state == State.UNSUPPORTED || state == State.CLOSED) return PresentationResult.UNSUPPORTED;
		long token = generation.get();
		try {
			return executor.submit(() -> presentOnWorker(token, presentation.heading(), presentation.message())).get();
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt(); return PresentationResult.FAILED;
		} catch (RejectedExecutionException ex) { return PresentationResult.UNSUPPORTED;
		} catch (ExecutionException ex) { log.warn("Windows notification code=presentation_failed"); return PresentationResult.FAILED; }
	}
	private PresentationResult presentOnWorker(long token, String heading, String message) {
		if (token != generation.get() || state == State.CLOSED) return PresentationResult.UNSUPPORTED;
		try {
			if (state == State.NEW) {
				if (bridge.initialize(WindowsNotificationIdentity.APP_USER_MODEL_ID) != WindowsNotificationBridge.PRESENTED) {
					state = State.UNSUPPORTED; return PresentationResult.UNSUPPORTED;
				}
				state = State.READY;
			}
			if (token != generation.get()) return PresentationResult.UNSUPPORTED;
			return switch (bridge.show(heading, message)) {
				case WindowsNotificationBridge.PRESENTED -> PresentationResult.PRESENTED;
				case WindowsNotificationBridge.UNSUPPORTED -> { state = State.UNSUPPORTED; yield PresentationResult.UNSUPPORTED; }
				default -> PresentationResult.FAILED;
			};
		} catch (Throwable failure) {
			if (state == State.NEW) state = State.UNSUPPORTED;
			log.warn("Windows notification code=native_failure");
			return state == State.UNSUPPORTED ? PresentationResult.UNSUPPORTED : PresentationResult.FAILED;
		}
	}
	@Override public void invalidate() { generation.incrementAndGet(); }
	@Override public void close() {
		if (state == State.CLOSED) return;
		generation.incrementAndGet(); state = State.CLOSED;
		try { executor.submit(() -> { try { bridge.close(); } catch (Throwable failure) { log.warn("Windows notification code=close_failed"); } }).get(); }
		catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
		catch (ExecutionException | RejectedExecutionException ex) { log.warn("Windows notification code=close_failed"); }
		finally { executor.shutdownNow(); }
	}
	private enum State { NEW, READY, UNSUPPORTED, CLOSED }
}
