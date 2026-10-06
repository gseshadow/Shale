package com.shale.ui.services;

import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** One running load and one replaceable pending load; even uncancellable JDBC cannot build a backlog. */
public final class LatestSearchRunner {
    private final Executor worker;
    private final Executor dispatcher;
    private long generation;
    private boolean running;
    private Runnable pending;

    public LatestSearchRunner(Executor worker, Executor dispatcher) {
        this.worker = worker;
        this.dispatcher = dispatcher;
    }

    public synchronized long invalidate() {
        pending = null;
        return ++generation;
    }

    public synchronized boolean isCurrent(long token) { return token == generation; }

    public synchronized <T> void submit(long token, Supplier<T> loader, Consumer<T> success, Consumer<Throwable> failure) {
        if (!isCurrent(token)) return;
        pending = () -> {
            if (!isCurrent(token)) return;
            try {
                T result = loader.get();
                dispatcher.execute(() -> { if (isCurrent(token)) success.accept(result); });
            } catch (RuntimeException ex) {
                dispatcher.execute(() -> { if (isCurrent(token)) failure.accept(ex); });
            }
        };
        if (!running) {
            running = true;
            worker.execute(this::drain);
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
