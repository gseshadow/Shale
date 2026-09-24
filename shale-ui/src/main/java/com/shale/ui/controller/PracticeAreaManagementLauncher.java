package com.shale.ui.controller;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import com.shale.core.service.CaseServicePort;
import com.shale.ui.component.DefinitionManagementResult;
import com.shale.ui.component.DefinitionManagementSession;

import javafx.stage.Window;

/** The single Practice Area management construction boundary shared by Settings and Case. */
public final class PracticeAreaManagementLauncher {
    private final CaseServicePort service;
    private final Executor executor;

    public PracticeAreaManagementLauncher(CaseServicePort service, Executor executor) {
        this.service = Objects.requireNonNull(service);
        this.executor = Objects.requireNonNull(executor);
    }

    public void open(Window owner, int tenantId, Consumer<DefinitionManagementResult> onClosed) {
        DefinitionManagementSession session = new DefinitionManagementSession();
        PracticeAreaManagementPane pane = new PracticeAreaManagementPane(service, tenantId, executor, session.changes());
        session.show(owner, "Manage Practice Areas",
                "Manage practice-area names, colors, and availability. Existing Cases retain their assigned Practice Area.",
                pane.node(), () -> !pane.mutationInFlight(), pane::dispose, onClosed);
    }
}
