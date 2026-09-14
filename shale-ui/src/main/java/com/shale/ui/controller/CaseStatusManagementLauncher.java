package com.shale.ui.controller;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import com.shale.core.service.CaseServicePort;
import com.shale.ui.component.DefinitionManagementResult;
import com.shale.ui.component.DefinitionManagementSession;

import javafx.stage.Window;

/** Single construction boundary for Case Status administration. */
public final class CaseStatusManagementLauncher {
    private final CaseServicePort service;
    private final Executor executor;

    public CaseStatusManagementLauncher(CaseServicePort service, Executor executor) {
        this.service = Objects.requireNonNull(service);
        this.executor = Objects.requireNonNull(executor);
    }

    public void open(Window owner, int tenantId, int actorUserId, Consumer<DefinitionManagementResult> onClosed) {
        DefinitionManagementSession session = new DefinitionManagementSession();
        CaseStatusManagementPane pane = new CaseStatusManagementPane(service, tenantId, actorUserId, executor, session.changes());
        session.show(owner, "Manage Case Statuses",
                "Manage case-status names, colors, workflow state, and order. Existing Cases keep their assigned status.",
                pane.node(), () -> !pane.mutationInFlight(), pane::dispose, onClosed);
    }
}
