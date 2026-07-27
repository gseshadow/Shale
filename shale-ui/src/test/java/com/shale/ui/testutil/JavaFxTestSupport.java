package com.shale.ui.testutil;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import javafx.application.Platform;

public final class JavaFxTestSupport {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final Object STARTUP_LOCK = new Object();
    private static volatile boolean toolkitReady;

    private JavaFxTestSupport() {
    }

    public static void ensureToolkitStarted() {
        if (toolkitReady) {
            return;
        }
        synchronized (STARTUP_LOCK) {
            if (toolkitReady) {
                return;
            }

            CountDownLatch started = new CountDownLatch(1);
            try {
                Platform.startup(() -> {
                    Platform.setImplicitExit(false);
                    started.countDown();
                });
            } catch (IllegalStateException exception) {
                if (!"Toolkit already initialized".equals(exception.getMessage())) {
                    throw exception;
                }
                Platform.runLater(() -> {
                    Platform.setImplicitExit(false);
                    started.countDown();
                });
            }

            await(started, "JavaFX toolkit initialization");
            toolkitReady = true;
        }
    }

    public static void runAndWait(ThrowingRunnable action) {
        runAndWait(() -> {
            action.run();
            return null;
        });
    }

    public static <T> T runAndWait(ThrowingSupplier<T> action) {
        if (Platform.isFxApplicationThread()) {
            try {
                return action.get();
            } catch (Throwable failure) {
                return rethrow(failure);
            }
        }

        ensureToolkitStarted();
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                result.set(action.get());
            } catch (Throwable thrown) {
                failure.set(thrown);
            } finally {
                finished.countDown();
            }
        });
        await(finished, "JavaFX action");
        if (failure.get() != null) {
            return rethrow(failure.get());
        }
        return result.get();
    }

    private static void await(CountDownLatch latch, String operation) {
        try {
            if (!latch.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                rethrowVoid(new TimeoutException(operation + " did not complete within " + TIMEOUT.toSeconds() + " seconds"));
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            rethrowVoid(exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T, E extends Throwable> T rethrow(Throwable failure) throws E {
        throw (E) failure;
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void rethrowVoid(Throwable failure) throws E {
        throw (E) failure;
    }

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Throwable;
    }

    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T get() throws Throwable;
    }
}
