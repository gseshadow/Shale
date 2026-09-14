package com.shale.ui.controller;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import com.shale.core.service.MaterialRequestServicePort;
import com.shale.ui.component.DefinitionManagementResult;
import com.shale.ui.component.DefinitionManagementSession;

import javafx.stage.Window;

/** The single construction boundary for request-definition administration. */
public final class RequestDefinitionManagementLauncher {
    private final MaterialRequestServicePort service;
    private final Executor executor;

    public RequestDefinitionManagementLauncher(MaterialRequestServicePort service, Executor executor) {
        this.service = Objects.requireNonNull(service);
        this.executor = Objects.requireNonNull(executor);
    }

    public void open(Window owner, int tenantId, int actorId, Consumer<DefinitionManagementResult> onClosed) {
        DefinitionManagementSession session = new DefinitionManagementSession();
        RequestDefinitionAdminPane pane = new RequestDefinitionAdminPane(
                service, tenantId, actorId, executor, session.changes());
        session.show(owner, "Manage Request Fields",
                "Manage material types, request methods, and request statuses. Existing Material Requests are preserved.",
                pane.node(), () -> !pane.mutationInFlight(), pane::dispose, onClosed);
    }
}
