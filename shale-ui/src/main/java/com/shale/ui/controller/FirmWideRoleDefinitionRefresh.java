package com.shale.ui.controller;

import java.lang.ref.WeakReference;
import java.util.concurrent.CopyOnWriteArrayList;

import javafx.application.Platform;

/** In-process invalidation for open user editors after firm-wide definitions change. */
final class FirmWideRoleDefinitionRefresh {
    private static final CopyOnWriteArrayList<WeakReference<Listener>> LISTENERS = new CopyOnWriteArrayList<>();
    private FirmWideRoleDefinitionRefresh() { }

    static AutoCloseable subscribe(Listener listener) {
        WeakReference<Listener> reference = new WeakReference<>(listener);
        LISTENERS.add(reference);
        return () -> LISTENERS.remove(reference);
    }

    static void publish(int tenantId) {
        LISTENERS.removeIf(reference -> reference.get() == null);
        for (WeakReference<Listener> reference : LISTENERS) {
            Listener listener = reference.get();
            if (listener != null) {
                if (Platform.isFxApplicationThread()) listener.definitionsChanged(tenantId);
                else Platform.runLater(() -> listener.definitionsChanged(tenantId));
            }
        }
    }

    @FunctionalInterface interface Listener { void definitionsChanged(int tenantId); }
}
