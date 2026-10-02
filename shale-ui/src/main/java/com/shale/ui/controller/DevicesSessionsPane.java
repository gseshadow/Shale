package com.shale.ui.controller;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import com.shale.ui.component.dialog.AppDialogs;
import com.shale.ui.services.UiRuntimeBridge;
import com.shale.ui.state.AppState;
import com.shale.ui.util.ActionButtonFactory;
import com.shale.ui.util.ControlStyles;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Self-service Phase 9 presentation. Session rows are deliberately security-data-minimal. */
public final class DevicesSessionsPane extends VBox {
    private static final DateTimeFormatter TIME=DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a");
    private final UiRuntimeBridge runtime; private final AppState state; private final Executor executor;
    private final AtomicLong generation=new AtomicLong(); private final VBox rows=new VBox(8);
    private final Label message=new Label(); private final Button refresh,others; private boolean open,loading;
    private final Consumer<UiRuntimeBridge.ConnectivityEvent> connectivity=this::onConnectivity;
    public DevicesSessionsPane(UiRuntimeBridge runtime,AppState state,Executor executor){
        super(10);this.runtime=runtime;this.state=state;this.executor=executor;getStyleClass().add("devices-sessions-pane");
        message.setWrapText(true);message.getStyleClass().add("settings-state-message");
        refresh=ActionButtonFactory.semantic("Refresh",e->load(),ControlStyles.Purpose.SECONDARY,ControlStyles.Size.SMALL);
        others=ActionButtonFactory.semantic("Sign out all other sessions",e->confirmOthers(),ControlStyles.Purpose.DANGER,ControlStyles.Size.STANDARD);
        Label meaning=new Label("Active means the session is unexpired and has not been revoked; it does not mean Shale is currently running.");meaning.setWrapText(true);meaning.getStyleClass().add("search-summary-text");
        HBox actions=new HBox(8,refresh,others);actions.setAlignment(Pos.CENTER_LEFT);getChildren().addAll(meaning,message,rows,actions);
        sceneProperty().addListener((observable,previous,current)->{if(previous!=null&&current==null)close();});
    }
    public void open(){if(open)return;open=true;if(runtime!=null)runtime.subscribeConnectivity(connectivity);load();}
    public void close(){if(!open)return;open=false;loading=false;generation.incrementAndGet();if(runtime!=null)runtime.unsubscribeConnectivity(connectivity);rows.getChildren().clear();message.setText("");}
    private void onConnectivity(UiRuntimeBridge.ConnectivityEvent event){if(event!=null&&event.online())Platform.runLater(this::load);}
    void load(){if(loading)return;loading=true;long token=generation.incrementAndGet();Identity who=identity();rows.getChildren().clear();busy(true,"Loading sessions…");Optional<UiRuntimeBridge.UserSessionManagement> service=runtime==null?Optional.empty():runtime.userSessionManagement();if(service.isEmpty()){loading=false;busy(false,"Session management is unavailable for this compatibility-mode connection.");refresh.setDisable(true);others.setDisable(true);return;}executor.execute(()->{try{List<UiRuntimeBridge.UserSessionView> loaded=service.get().list();Platform.runLater(()->apply(token,who,loaded));}catch(RuntimeException failure){Platform.runLater(()->fail(token,who));}});}
    private void apply(long token,Identity who,List<UiRuntimeBridge.UserSessionView> loaded){if(stale(token,who))return;loading=false;busy(false,"");List<UiRuntimeBridge.UserSessionView> ordered=new ArrayList<>(loaded);ordered.sort(Comparator.comparing(UiRuntimeBridge.UserSessionView::currentSession).reversed().thenComparing(UiRuntimeBridge.UserSessionView::issuedAt,Comparator.reverseOrder()));ordered.forEach(s->rows.getChildren().add(card(s)));long activeOthers=loaded.stream().filter(s->!s.currentSession()&&s.revokedAt()==null&&s.expiresAt().isAfter(Instant.now())).count();if(loaded.isEmpty())message.setText("No durable sessions are available.");else if(activeOthers==0)message.setText("No other active sessions.");others.setDisable(activeOthers==0);}
    private VBox card(UiRuntimeBridge.UserSessionView s){String client=client(s.clientType());boolean expired=!s.expiresAt().isAfter(Instant.now());String status=s.revokedAt()!=null?"Revoked":expired?"Expired":"Active";Label title=new Label(client+" session");title.getStyleClass().add("settings-management-title");Label badge=new Label(status);badge.getStyleClass().addAll("shale-status-pill",s.revokedAt()!=null||expired?"session-status-inactive":"session-status-current");HBox head=new HBox(8,title,badge);if(s.currentSession()){Label current=new Label("Current session");current.getStyleClass().addAll("shale-status-pill","session-status-current");head.getChildren().add(current);}head.setAlignment(Pos.CENTER_LEFT);VBox facts=new VBox(3,line("Signed in",s.issuedAt()),line("Last refreshed",s.lastRefreshedAt()),line("Expires",s.expiresAt()),line("Revoked",s.revokedAt()));VBox card=new VBox(7,head,facts);card.getStyleClass().addAll("shale-card","session-card");if(s.revokedAt()==null&&!expired){Button signout=ActionButtonFactory.semantic(s.currentSession()?"Sign out this session":"Sign out "+client+" session",e->{if(s.currentSession())confirmCurrent();else confirmOne(s,client);},ControlStyles.Purpose.DANGER,ControlStyles.Size.SMALL);card.getChildren().add(signout);}return card;}
    private Label line(String name,Instant value){Label l=new Label(name+": "+(value==null?"Not available":TIME.format(value.atZone(ZoneId.systemDefault()))));l.setWrapText(true);l.getStyleClass().add("search-summary-text");return l;}
    private void confirmOne(UiRuntimeBridge.UserSessionView s,String client){if(s.currentSession())return;if(AppDialogs.showDestructiveConfirmation(getScene()==null?null:getScene().getWindow(),"Sign out session?","Sign out this "+client.toLowerCase(Locale.ROOT)+" session?","It will lose server access after its next validation. The application may not close immediately.","Sign out"))mutate(service->service.revoke(s.sessionId()));}
    private void confirmCurrent(){if(AppDialogs.showDestructiveConfirmation(getScene()==null?null:getScene().getWindow(),"Sign out this session?","Sign out this current session?","Your server session will be revoked and Shale will lock immediately, then return to sign-in after you acknowledge the session-ended message.","Sign out"))mutate(UiRuntimeBridge.UserSessionManagement::revokeCurrent);}
    private void confirmOthers(){if(AppDialogs.showDestructiveConfirmation(getScene()==null?null:getScene().getWindow(),"Sign out other sessions?","Sign out all other sessions?","This session remains signed in. Other active sessions will lose server access after validation and may not disappear immediately.","Sign out others"))mutate(UiRuntimeBridge.UserSessionManagement::revokeOthers);}
    private void mutate(java.util.function.Consumer<UiRuntimeBridge.UserSessionManagement> operation){loading=true;long token=generation.incrementAndGet();Identity who=identity();busy(true,"Applying sign-out…");Optional<UiRuntimeBridge.UserSessionManagement> service=runtime.userSessionManagement();if(service.isEmpty()){fail(token,who);return;}executor.execute(()->{try{operation.accept(service.get());Platform.runLater(()->{if(!stale(token,who)){loading=false;load();}});}catch(RuntimeException failure){Platform.runLater(()->fail(token,who));}});}
    private void fail(long token,Identity who){if(stale(token,who))return;loading=false;busy(false,"Sessions could not be loaded. Check your connection and try again.");}
    private void busy(boolean value,String text){message.setText(text);refresh.setDisable(value);others.setDisable(value);}
    private boolean stale(long token,Identity who){return !open||token!=generation.get()||!who.equals(identity());}
    private Identity identity(){return new Identity(state==null?null:state.getShaleClientId(),state==null?null:state.getUserId());}
    static String client(String raw){if(raw==null)return "Unknown";return switch(raw.toUpperCase(Locale.ROOT)){case "DESKTOP"->"Desktop";case "WEB"->"Web";case "MOBILE"->"Mobile";default->"Unknown";};}
    private record Identity(Integer tenant,Integer user){}
}
