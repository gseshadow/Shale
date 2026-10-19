package com.shale.ui.services;

import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** One running load and one replaceable pending load; even uncancellable JDBC cannot build a backlog. */
public final class LatestSearchRunner {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(LatestSearchRunner.class);
    private final Executor worker;
    private final Executor dispatcher;
    private final Executor canceller;
    private Runnable cancellation;
    private long generation;
    private boolean running;
    private Runnable pending;

    public LatestSearchRunner(Executor worker, Executor dispatcher) {
        this(worker, dispatcher, Runnable::run);
    }

    public LatestSearchRunner(Executor worker, Executor dispatcher, Executor canceller) {
        this.worker = worker;
        this.dispatcher = dispatcher;
        this.canceller = canceller;
    }

    public synchronized long invalidate() {
        pending = null;
        cancelActive();
        return ++generation;
    }

    /** Called by the worker; the captured statement belongs only to this generation. */
    public synchronized void onCancel(long token, Runnable action) {
        if (isCurrent(token)) cancellation = action;
        else if (action != null) canceller.execute(action);
    }

    private void cancelActive() {
        Runnable action = cancellation;
        cancellation = null;
        if (action != null) canceller.execute(action);
    }

    public synchronized boolean isCurrent(long token) { return token == generation; }

    public synchronized <T> void submit(long token, Supplier<T> loader, Consumer<T> success, Consumer<Throwable> failure) {
        if (!isCurrent(token)) return;
        long submitted = com.shale.core.util.PerformanceLogging.start();
        pending = () -> {
            if (!isCurrent(token)) return;
            com.shale.data.dao.SearchPerformance.log(LOG, "executor_queue", token, "all", 0,
                    com.shale.core.util.PerformanceLogging.elapsedMs(submitted));
            try {
                T result = com.shale.data.dao.SearchPerformance.withRequest(token, loader);
                long dispatched = com.shale.core.util.PerformanceLogging.start();
                dispatcher.execute(() -> {
                    if (isCurrent(token)) {
                        com.shale.data.dao.SearchPerformance.log(LOG, "fx_dispatch", token, "all", 0,
                                com.shale.core.util.PerformanceLogging.elapsedMs(dispatched));
                        success.accept(result);
                    }
                });
            } catch (RuntimeException ex) {
                dispatcher.execute(() -> { if (isCurrent(token)) failure.accept(ex); });
            } finally {
                synchronized (this) { if (isCurrent(token)) cancellation = null; }
            }
        };
        if (!running) {
            running = true;
            try { worker.execute(this::drain); }
            catch (RuntimeException ex) { running = false; pending = null; throw ex; }
        }
    }

    private void drain() {
        while (true) {
            Runnable next;
            synchronized (this) {
                next = pending;
                pending = null;
                if (next == null) { running = false; return; }
            }
            next.run();
        }
    }
}
