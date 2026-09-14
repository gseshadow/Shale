package com.shale.ui.controller;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import com.shale.core.service.ContactServicePort;
import com.shale.ui.component.DefinitionManagementResult;
import com.shale.ui.component.DefinitionManagementWindow;

import javafx.stage.Window;

/** Single construction boundary used by Settings and the Contact classification surface. */
public final class ContactClassificationManagementLauncher {
    private final ContactServicePort service;
    private final Executor executor;

    public ContactClassificationManagementLauncher(ContactServicePort service, Executor executor) {
        this.service = Objects.requireNonNull(service);
        this.executor = Objects.requireNonNull(executor);
    }

    public void open(Window owner, int tenantId, int actorId, Consumer<DefinitionManagementResult> onClosed) {
        AtomicBoolean changed = new AtomicBoolean();
        ContactClassificationAdminPane pane = new ContactClassificationAdminPane(
                service, tenantId, actorId, executor, changed);
        DefinitionManagementWindow.show(owner, "Manage Contact Classifications",
                "Manage contact types, specialties, and credentials. Existing Contact assignments are preserved.",
                pane.node(), changed, () -> !pane.mutationInFlight(), pane::dispose, onClosed);
    }
}
