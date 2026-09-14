package com.shale.ui.controller;

import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import com.shale.core.service.CaseServicePort;
import com.shale.ui.component.DefinitionManagementResult;
import com.shale.ui.component.DefinitionManagementSession;

import javafx.stage.Window;

/** The single construction/launch boundary used by Settings and Case > Dates. */
public final class CaseDateTypeManagementLauncher {
    private final CaseServicePort service;
    private final Executor executor;
    private final IntConsumer publisher;

    public CaseDateTypeManagementLauncher(CaseServicePort service, Executor executor, IntConsumer publisher) {
        this.service = service; this.executor = executor; this.publisher = publisher;
    }

    public void open(Window owner, int tenantId, int actorId, Consumer<DefinitionManagementResult> onChanged) {
        DefinitionManagementSession session = new DefinitionManagementSession();
        CaseDateTypeManagementPane pane = new CaseDateTypeManagementPane(service, tenantId, actorId, executor, publisher, session.changes());
        session.show(owner, "Manage Case Date Types",
                "Manage date types and their presentation. Existing Case Date occurrences keep their historical values.",
                pane.node(), () -> !pane.mutationInFlight(), pane::dispose, onChanged);
    }
}
