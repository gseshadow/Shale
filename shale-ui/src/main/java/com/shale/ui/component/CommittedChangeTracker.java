package com.shale.ui.component;

import java.util.concurrent.atomic.AtomicBoolean;

/** Thread-safe, feature-neutral accumulation of successful committed mutations. */
public final class CommittedChangeTracker {
    private final AtomicBoolean changed = new AtomicBoolean();

    public void markCommitted() {
        changed.set(true);
    }

    public boolean hasCommittedChanges() {
        return changed.get();
    }
}
