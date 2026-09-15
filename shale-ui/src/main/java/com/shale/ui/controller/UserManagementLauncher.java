package com.shale.ui.controller;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import com.shale.data.dao.UserDao;
import com.shale.ui.component.DefinitionManagementResult;
import com.shale.ui.component.DefinitionManagementSession;
import com.shale.ui.component.DefinitionManagementWindow;
import javafx.stage.Window;

/** Single construction boundary for tenant User Management. */
public final class UserManagementLauncher {
    private final UserDao userDao;
    private final Executor executor;
    public UserManagementLauncher(UserDao userDao, Executor executor) {
        this.userDao=Objects.requireNonNull(userDao,"userDao"); this.executor=Objects.requireNonNull(executor,"executor");
    }
    public void open(Window owner, int tenantId, int actorUserId, Consumer<DefinitionManagementResult> onClosed) {
        DefinitionManagementSession session=new DefinitionManagementSession();
        UserManagementPane pane=new UserManagementPane(userDao,executor,session.changes(),tenantId,actorUserId);
        session.show(owner,"User Management","Manage users for the current tenant.",pane.node(),
                DefinitionManagementWindow.ContentMode.FIXED,()->!pane.mutationInFlight(),pane::dispose,onClosed);
        pane.open();
    }
}
