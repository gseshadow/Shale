package com.shale.ui.controller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.shale.core.dto.LinkTypeDto;
import com.shale.core.service.CaseServicePort;
import com.shale.ui.component.CommittedChangeTracker;
import com.shale.ui.component.dialog.AppDialogs;
import com.shale.ui.component.factory.LinkTypeIndicatorFactory;
import com.shale.ui.services.LiveUpdateEvents;
import com.shale.ui.services.UiRuntimeBridge;
import com.shale.ui.util.ActionButtonFactory;
import com.shale.ui.util.ControlStyles;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/** Link-Type-specific definition UI; all persistence remains behind CaseServicePort. */
public final class LinkTypeManagementPane {
    private static final Logger LOG = LoggerFactory.getLogger(LinkTypeManagementPane.class);
    private final CaseServicePort service; private final int tenantId; private final int actorId;
    private final Executor executor; private final LinkTypeManagementLauncher.Publisher publisher;
    private final CommittedChangeTracker changed; private final AtomicBoolean mutationInFlight = new AtomicBoolean();
    private final UiRuntimeBridge runtimeBridge;
    private final java.util.function.Consumer<UiRuntimeBridge.EntityUpdatedEvent> liveHandler = this::onLiveUpdate;
    private final VBox root = new VBox(12); private final FlowPane cards = new FlowPane(10, 10); private final Label status = new Label();
    private volatile boolean disposed; private int loadGeneration;

    public LinkTypeManagementPane(CaseServicePort service, int tenantId, int actorId, Executor executor,
            LinkTypeManagementLauncher.Publisher publisher, CommittedChangeTracker changed) {
        this(service, tenantId, actorId, executor, publisher, null, changed);
    }
    public LinkTypeManagementPane(CaseServicePort service, int tenantId, int actorId, Executor executor,
            LinkTypeManagementLauncher.Publisher publisher, UiRuntimeBridge runtimeBridge, CommittedChangeTracker changed) {
        this.service=service; this.tenantId=tenantId; this.actorId=actorId; this.executor=executor; this.publisher=publisher; this.changed=changed;
        this.runtimeBridge=runtimeBridge;
        Button add = ActionButtonFactory.semantic("Add Link Type", e -> showEditor(null).ifPresent(i -> save(null, i)), ControlStyles.Purpose.PRIMARY, ControlStyles.Size.STANDARD);
        status.getStyleClass().add("search-summary-text"); status.setWrapText(true); cards.setPrefWrapLength(820);
        root.getChildren().addAll(new HBox(8, add), status, cards); if(runtimeBridge!=null)runtimeBridge.subscribeEntityUpdated(liveHandler); reload(null);
    }
    public Node node(){ return root; } boolean mutationInFlight(){ return mutationInFlight.get(); }
    public void dispose(){ disposed=true; loadGeneration++; if(runtimeBridge!=null)runtimeBridge.unsubscribeEntityUpdated(liveHandler); } int loadGeneration(){ return loadGeneration; }
    private void onLiveUpdate(UiRuntimeBridge.EntityUpdatedEvent event){if(event!=null&&event.shaleClientId()==tenantId&&LiveUpdateEvents.ENTITY_LINK_TYPE.equals(event.entityType()))Platform.runLater(()->reload(null));}

    void reload(String message) {
        if(disposed)return; int generation=++loadGeneration; status.setText("Loading link types…"); cards.getChildren().clear();
        executor.execute(() -> { try { var rows=buildRows(service.listLinkTypesForAdministration(tenantId, actorId),tenantId);
            Platform.runLater(() -> applyLoad(generation,rows,message));
        } catch(RuntimeException ex){ LOG.error("Link Type administration load failed tenantId={} actorId={}",tenantId,actorId,ex);
            Platform.runLater(() -> {if(current(generation))status.setText("Link types could not be loaded. Try again.");}); }});
    }
    void applyLoad(int generation,List<ViewRow> rows,String message){ if(!current(generation))return;
        cards.getChildren().setAll(rows.stream().map(this::card).toList()); status.setText(message==null?(rows.isEmpty()?"No link types are configured for this tenant.":""):message); }
    private boolean current(int generation){return !disposed&&generation==loadGeneration;}

    static List<ViewRow> buildRows(List<LinkTypeDto> source,int tenantId){
        Map<String,LinkTypeDto> globals=new LinkedHashMap<>(), tenants=new LinkedHashMap<>(); List<ViewRow> out=new ArrayList<>();
        for(LinkTypeDto row:source==null?List.<LinkTypeDto>of():source){ if(row==null||(row.shaleClientId()!=null&&row.shaleClientId()!=tenantId))continue;
            String key=safe(row.systemKey()).trim().toLowerCase(Locale.ROOT); if(row.shaleClientId()==null&&!key.isBlank())globals.put(key,row);
            else if(!key.isBlank())tenants.put(key,row); else if(!row.deleted())out.add(new ViewRow(row,Scope.TENANT_CUSTOM)); }
        globals.forEach((key,global)->{LinkTypeDto tenant=tenants.get(key); out.add(tenant==null||tenant.deleted()?new ViewRow(global,Scope.GLOBAL_DEFAULT):new ViewRow(tenant,Scope.TENANT_OVERRIDE));});
        tenants.forEach((key,tenant)->{if(!globals.containsKey(key)&&!tenant.deleted())out.add(new ViewRow(tenant,Scope.TENANT_CUSTOM));});
        out.sort(Comparator.comparing(ViewRow::name,String.CASE_INSENSITIVE_ORDER).thenComparingInt(ViewRow::id)); return List.copyOf(out);
    }
    private Node card(ViewRow row){ VBox card=new VBox(7); card.getStyleClass().addAll("shale-entity-card","shale-entity-card-compact"); card.setMinWidth(300); card.setPrefWidth(380);
        Label name=new Label(row.name()); name.getStyleClass().add("app-dialog-field-label"); Region spacer=new Region(); HBox.setHgrow(spacer,Priority.ALWAYS);
        HBox header=new HBox(8,name,spacer,LinkTypeIndicatorFactory.createLinkTypePill(row.name(),row.color(),LinkTypeIndicatorFactory.PillSize.COMPACT)); header.setAlignment(Pos.CENTER_LEFT);
        Label details=new Label((row.active()?"Active":"Inactive")+" · "+row.scopeLabel()); details.getStyleClass().add("search-summary-text");
        Button edit=button(row.global()?"Customize":"Edit",ControlStyles.Purpose.GHOST,()->showEditor(row.dto()).ifPresent(i->save(row,i)));
        Button toggle=button(row.active()?"Deactivate":"Activate",ControlStyles.Purpose.GHOST,()->toggle(row));
        Button reset=button(row.custom()?"Remove Custom":"Reset Override",row.custom()?ControlStyles.Purpose.DANGER:ControlStyles.Purpose.SECONDARY,()->remove(row)); reset.setDisable(row.global());
        card.getChildren().addAll(header,details,new HBox(8,edit,toggle,reset)); return card; }
    private Button button(String text,ControlStyles.Purpose purpose,Runnable action){return ActionButtonFactory.semantic(text,e->action.run(),purpose,ControlStyles.Size.SMALL);}

    private void save(ViewRow row,Input input){ String success=row==null?"Link type added.":row.global()?"Tenant override saved for global link type.":"Link type updated.";
        mutate(success,()->{LinkTypeDto saved=row==null?service.createLinkType(command(null,input,null,null,tenantId,actorId)):
            service.updateLinkType(command(row.id(),input,row.dto().systemKey(),row.dto().rowVer(),tenantId,actorId));
            return new Published(saved.id(),row==null||row.global()?LiveUpdateEvents.CHANGE_CREATED:LiveUpdateEvents.CHANGE_UPDATED);}); }
    private void toggle(ViewRow row){mutate(row.active()?"Link type deactivated for future selections.":"Link type activated.",()->{
        LinkTypeDto saved=service.setLinkTypeActive(new CaseServicePort.SetLinkTypeActiveCommand(tenantId,actorId,row.id(),!row.active(),row.dto().rowVer()));
        return new Published(saved.id(),row.active()?LiveUpdateEvents.CHANGE_DEACTIVATED:LiveUpdateEvents.CHANGE_ACTIVATED);});}
    private void remove(ViewRow row){if(row.global())return; String action=row.custom()?"Remove":"Reset to Default";
        if(!AppDialogs.showConfirmation(root.getScene()==null?null:root.getScene().getWindow(),"Link Types",action+" "+row.name()+"?","This affects future selections only. Existing links retain their stored Link Type relationship.",action,row.custom()?AppDialogs.DialogActionKind.DANGER:AppDialogs.DialogActionKind.PRIMARY))return;
        mutate(row.custom()?"Custom link type removed from future selections.":"Tenant override reset to global default.",()->{service.resetLinkTypeOverride(new CaseServicePort.ResetLinkTypeOverrideCommand(tenantId,actorId,row.id()));return new Published(row.id(),LiveUpdateEvents.CHANGE_OVERRIDE_RESET);});}
    private void mutate(String success,Mutation operation){if(disposed||!mutationInFlight.compareAndSet(false,true))return; status.setText("Saving…");int generation=++loadGeneration;
        executor.execute(()->{try{Published event=operation.run();if(disposed||generation!=loadGeneration)return;changed.markCommitted();try{publisher.publish(event.id(),event.change());}catch(RuntimeException ex){LOG.warn("Link Type committed but publication failed tenantId={} typeId={}",tenantId,event.id(),ex);}Platform.runLater(()->{if(current(generation)){mutationInFlight.set(false);reload(success);}});
        }catch(RuntimeException ex){LOG.error("Link Type administration mutation failed tenantId={} actorId={}",tenantId,actorId,ex);Platform.runLater(()->{if(current(generation)){mutationInFlight.set(false);status.setText("The Link Type could not be saved. Review the values or reload and try again.");}});}});}

    static CaseServicePort.LinkTypeCommand command(Integer id,Input input,String systemKey,byte[] rowVer,int tenantId,int actorId){return new CaseServicePort.LinkTypeCommand(id,tenantId,actorId,input.name(),input.color(),input.active(),systemKey,rowVer);}
    private Optional<Input> showEditor(LinkTypeDto existing){Dialog<Input> dialog=new Dialog<>();String title=existing==null?"Add Link Type":existing.shaleClientId()==null?"Customize Link Type":"Edit Link Type";AppDialogs.applySecondaryDialogShell(dialog,title);dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK,ButtonType.CANCEL);
        TextField name=new TextField(existing==null?"":existing.name());ColorPicker color=new ColorPicker(color(existing==null?null:existing.color()));CheckBox active=new CheckBox("Active");active.setSelected(existing==null||existing.active());Label validation=new Label();validation.getStyleClass().add("dialog-error-text");GridPane grid=new GridPane();grid.setHgap(8);grid.setVgap(8);grid.addRow(0,new Label("Name"),name);grid.addRow(1,new Label("Color"),color);grid.add(active,1,2);grid.add(validation,1,3);dialog.getDialogPane().setContent(grid);
        Button save=(Button)dialog.getDialogPane().lookupButton(ButtonType.OK);ControlStyles.apply(save,ControlStyles.Purpose.PRIMARY);save.addEventFilter(javafx.event.ActionEvent.ACTION,e->{String value=name.getText()==null?"":name.getText().trim();if(value.isEmpty()||value.length()>100){validation.setText(value.isEmpty()?"Name is required.":"Name must be 100 characters or fewer.");e.consume();}});
        dialog.setResultConverter(b->b==ButtonType.OK?new Input(name.getText().trim(),toDb(color.getValue()),active.isSelected()):null);return dialog.showAndWait();}
    private static Color color(String v){try{return v==null||v.isBlank()?Color.web("#6C757D"):Color.web(v);}catch(IllegalArgumentException ex){return Color.web("#6C757D");}}
    private static String toDb(Color c){return String.format("#%02X%02X%02X",Math.round(c.getRed()*255),Math.round(c.getGreen()*255),Math.round(c.getBlue()*255));} private static String safe(String s){return s==null?"":s;}
    enum Scope{GLOBAL_DEFAULT,TENANT_OVERRIDE,TENANT_CUSTOM} public record Input(String name,String color,boolean active){}
    record ViewRow(LinkTypeDto dto,Scope scope){int id(){return dto.id();}String name(){return safe(dto.name());}String color(){return safe(dto.color());}boolean active(){return dto.active()&&!dto.deleted();}boolean global(){return scope==Scope.GLOBAL_DEFAULT;}boolean custom(){return scope==Scope.TENANT_CUSTOM;}String scopeLabel(){return global()?"Global/default":custom()?"Tenant custom":"Tenant override";}}
    record Published(int id,String change){} @FunctionalInterface interface Mutation{Published run();}
}
