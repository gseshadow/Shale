package com.shale.ui.controller;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import com.shale.core.service.CaseServicePort;
import com.shale.ui.component.DefinitionManagementResult;
import com.shale.ui.component.DefinitionManagementSession;
import javafx.stage.Window;

/** The single Case Team Role management construction boundary shared by Settings and Case Team. */
public final class CaseTeamRoleManagementLauncher {
    private final CaseServicePort service;private final Executor executor;
    public CaseTeamRoleManagementLauncher(CaseServicePort service,Executor executor){this.service=Objects.requireNonNull(service);this.executor=Objects.requireNonNull(executor);}
    public void open(Window owner,int tenantId,int actorId,Consumer<DefinitionManagementResult> onClosed){DefinitionManagementSession session=new DefinitionManagementSession();CaseTeamRoleAdminPane pane=new CaseTeamRoleAdminPane(service,tenantId,actorId,executor,session.changes());session.show(owner,"Manage Case Team Roles","Manage team roles, colors, and availability. Existing assignments and historical role labels are preserved.",pane.node(),()->!pane.mutationInFlight(),pane::dispose,onClosed);}
}
