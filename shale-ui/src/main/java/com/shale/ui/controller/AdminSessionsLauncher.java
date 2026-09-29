package com.shale.ui.controller;
import java.util.Objects;import java.util.concurrent.Executor;import java.util.function.Consumer;
import com.shale.core.service.UserServicePort;import com.shale.ui.component.*;import com.shale.ui.services.UiRuntimeBridge;import com.shale.ui.state.AppState;import javafx.stage.Window;
public final class AdminSessionsLauncher{
 private final UiRuntimeBridge runtime;private final AppState state;private final UserServicePort users;private final Executor executor;
 public AdminSessionsLauncher(UiRuntimeBridge r,AppState s,UserServicePort u,Executor e){runtime=Objects.requireNonNull(r);state=Objects.requireNonNull(s);users=Objects.requireNonNull(u);executor=Objects.requireNonNull(e);}
 public void open(Window owner,Consumer<DefinitionManagementResult> closed){AdminSessionsPane pane=new AdminSessionsPane(runtime,state,users,executor);DefinitionManagementSession session=new DefinitionManagementSession();session.show(owner,"Sessions","Review and revoke durable sessions for users in this tenant.",pane,DefinitionManagementWindow.ContentMode.SCROLLABLE,()->!pane.mutationInFlight(),pane::dispose,closed);pane.open();}
}
