package com.shale.ui.controller;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import com.shale.core.service.CaseServicePort;
import com.shale.ui.component.DefinitionManagementResult;
import com.shale.ui.component.DefinitionManagementSession;
import com.shale.ui.services.UiRuntimeBridge;

import javafx.stage.Window;

/** Single construction boundary shared by Settings and Case Links. */
public final class LinkTypeManagementLauncher {
    @FunctionalInterface public interface Publisher { void publish(int linkTypeId, String change); }
    private final CaseServicePort service;
    private final Executor executor;
    private final Publisher publisher;
    private final UiRuntimeBridge runtimeBridge;

    public LinkTypeManagementLauncher(CaseServicePort service, Executor executor, Publisher publisher) {
        this(service, executor, publisher, null);
    }

    public LinkTypeManagementLauncher(CaseServicePort service, Executor executor, Publisher publisher, UiRuntimeBridge runtimeBridge) {
        this.service = Objects.requireNonNull(service);
        this.executor = Objects.requireNonNull(executor);
        this.publisher = publisher == null ? (id, change) -> { } : publisher;
        this.runtimeBridge = runtimeBridge;
    }

    public void open(Window owner, int tenantId, int actorId, Consumer<DefinitionManagementResult> onClosed) {
        DefinitionManagementSession session = new DefinitionManagementSession();
        LinkTypeManagementPane pane = new LinkTypeManagementPane(service, tenantId, actorId, executor, publisher, runtimeBridge, session.changes());
        session.show(owner, "Manage Link Types",
                "Manage case-link types, colors, and availability. Existing Case Links retain their stored type identity and presentation.",
                pane.node(), () -> !pane.mutationInFlight(), pane::dispose, onClosed);
    }
}
