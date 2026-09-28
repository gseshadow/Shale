package com.shale.ui.controller;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import com.shale.core.service.UserServicePort;
import com.shale.ui.component.DefinitionManagementResult;
import com.shale.ui.component.DefinitionManagementSession;

import javafx.stage.Window;

/** Construction boundary for tenant firm-wide role definition administration. */
public final class FirmWideRoleManagementLauncher {
    private final UserServicePort service;
    private final Executor executor;

    public FirmWideRoleManagementLauncher(UserServicePort service, Executor executor) {
        this.service = Objects.requireNonNull(service, "service");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public void open(Window owner, int tenantId, int actorUserId, Consumer<DefinitionManagementResult> onClosed) {
        DefinitionManagementSession session = new DefinitionManagementSession();
        FirmWideRoleAdminPane pane = new FirmWideRoleAdminPane(service, tenantId, actorUserId, executor, session.changes());
        session.show(owner, "Manage Firm-wide Roles",
                "Manage tenant-wide eligibility roles. These roles are separate from Case Team Roles.",
                pane.node(), () -> !pane.mutationInFlight(), pane::dispose, onClosed);
    }
}
