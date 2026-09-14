package com.shale.ui.controller;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import com.shale.core.service.OrganizationServicePort;
import com.shale.ui.component.DefinitionManagementResult;
import com.shale.ui.component.DefinitionManagementSession;

import javafx.stage.Window;

/** Single construction boundary shared by Settings and Organization context. */
public final class OrganizationTypeManagementLauncher {
    private final OrganizationServicePort service;
    private final Executor executor;

    public OrganizationTypeManagementLauncher(OrganizationServicePort service, Executor executor) {
        this.service = Objects.requireNonNull(service);
        this.executor = Objects.requireNonNull(executor);
    }

    public void open(Window owner, int tenantId, int actorId, Consumer<DefinitionManagementResult> onClosed) {
        DefinitionManagementSession session = new DefinitionManagementSession();
        OrganizationTypeAdminPane pane = new OrganizationTypeAdminPane(
                service, tenantId, actorId, executor, session.changes());
        session.show(owner, "Manage Organization Types",
                "Manage organization types, colors, and availability. Existing Organization assignments are preserved.",
                pane.node(), () -> !pane.mutationInFlight(), pane::dispose, onClosed);
    }
}
