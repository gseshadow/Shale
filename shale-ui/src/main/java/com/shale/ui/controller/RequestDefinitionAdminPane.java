package com.shale.ui.controller;

import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.*;
import java.util.function.Supplier;

import com.shale.core.dto.*;
import com.shale.core.service.MaterialRequestServicePort;
import com.shale.ui.component.CommittedChangeTracker;
import com.shale.ui.component.dialog.AppDialogs;
import com.shale.ui.util.ColorUtil;
import com.shale.ui.util.ControlStyles;

import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

/** Request-family-specific management UI; the shared window remains domain agnostic. */
public final class RequestDefinitionAdminPane {
    enum Category { MATERIAL_TYPE, REQUEST_METHOD, REQUEST_STATUS }
    record Row(int id, Integer tenant, String key, String name, String description, String color,
            int order, boolean active, boolean deleted, byte[] rowVer) {
        boolean global() { return tenant == null; }
        boolean custom() { return tenant != null && (key == null || key.isBlank()); }
        boolean override() { return tenant != null && key != null && !key.isBlank(); }
    }

    private final MaterialRequestServicePort service;
    private final int tenantId, actorId;
    private final Executor worker;
    private final CommittedChangeTracker changes;
    private final AtomicBoolean loading = new AtomicBoolean(), mutating = new AtomicBoolean(), disposed = new AtomicBoolean();
    private final AtomicInteger generation = new AtomicInteger();
    private final TabPane tabs = new TabPane();
    private final VBox list = new VBox(10), root = new VBox(12);
    private final Label status = new Label();
    private final Button add = button("Add", ControlStyles.Purpose.PRIMARY), refresh = button("Refresh", ControlStyles.Purpose.SECONDARY);
    private volatile List<Row> rows = List.of();

    RequestDefinitionAdminPane(MaterialRequestServicePort service, int tenantId, int actorId,
            Executor worker, CommittedChangeTracker changes) {
        this.service=Objects.requireNonNull(service); this.tenantId=tenantId; this.actorId=actorId;
        this.worker=Objects.requireNonNull(worker); this.changes=Objects.requireNonNull(changes);
        tabs.getTabs().setAll(tab("Material Types", Category.MATERIAL_TYPE), tab("Request Methods", Category.REQUEST_METHOD),
                tab("Request Statuses", Category.REQUEST_STATUS));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE); tabs.setId("request-definition-tabs");
        tabs.getSelectionModel().selectedItemProperty().addListener((o,a,b)->requestLoad());
        add.setId("request-definition-add"); refresh.setId("request-definition-refresh");
        add.setOnAction(e->openEditor(null)); refresh.setOnAction(e->requestLoad());
        status.setId("request-definition-status"); status.setWrapText(true); status.getStyleClass().add("search-summary-text");
        list.setId("request-definition-list");
        FlowPane toolbar=new FlowPane(Orientation.HORIZONTAL,8,8,add,refresh);
        root.getChildren().setAll(tabs,toolbar,status,list); root.getStyleClass().add("request-definition-admin");
        requestLoad();
    }

    Node node(){ return root; }
    boolean mutationInFlight(){ return mutating.get(); }
    void dispose(){ disposed.set(true); generation.incrementAndGet(); }
    Category selected(){ return (Category)tabs.getSelectionModel().getSelectedItem().getUserData(); }
    private Tab tab(String label,Category category){Tab t=new Tab(label);t.setUserData(category);return t;}

    private void requestLoad(){ generation.incrementAndGet(); loadLatest(); }
    private void loadLatest(){
        if(disposed.get()||!loading.compareAndSet(false,true))return;
        int g=generation.get(); Category category=selected(); busy(true); status.setText(rows.isEmpty()?"Loading definitions…":"Refreshing definitions…");
        worker.execute(()->{try{
            if(Platform.isFxApplicationThread())throw new IllegalStateException("Request definition reads must run off the JavaFX thread.");
            List<Row> loaded=switch(category){
                case MATERIAL_TYPE->service.listMaterialTypesForAdministration(tenantId,actorId).stream().map(RequestDefinitionAdminPane::row).toList();
                case REQUEST_METHOD->service.listRequestMethodsForAdministration(tenantId,actorId).stream().map(RequestDefinitionAdminPane::row).toList();
                case REQUEST_STATUS->service.listRequestStatusesForAdministration(tenantId,actorId).stream().map(RequestDefinitionAdminPane::row).toList();};
            Platform.runLater(()->finishLoad(g,category,loaded,null));
        }catch(RuntimeException x){Platform.runLater(()->finishLoad(g,category,null,x));}});
    }
    private void finishLoad(int g,Category category,List<Row> loaded,RuntimeException failure){
        loading.set(false); if(disposed.get())return;
        if(g!=generation.get()||category!=selected()){loadLatest();return;}
        busy(false); if(failure!=null){status.setText("Definitions could not be loaded. Choose Refresh to try again.");return;}
        rows=effectiveAdministrationRows(loaded,tenantId); status.setText(""); render();
    }
    static List<Row> effectiveAdministrationRows(List<Row> source,int tenant){
        List<Row> safe=source==null?List.of():source; Map<String,Row> overrides=new HashMap<>();
        for(Row r:safe)if(Objects.equals(r.tenant(),tenant)&&r.key()!=null&&!r.key().isBlank())overrides.put(r.key().toLowerCase(Locale.ROOT),r);
        return safe.stream().filter(r->r.tenant()==null ? !overrides.containsKey(key(r))||overrides.get(key(r)).deleted()
                : Objects.equals(r.tenant(),tenant)&&(!r.deleted()||r.override()))
                .sorted(Comparator.comparingInt(Row::order).thenComparing(Row::name,String.CASE_INSENSITIVE_ORDER)).toList();
    }
    private static String key(Row r){return r.key()==null?"":r.key().toLowerCase(Locale.ROOT);}
    private void render(){if(rows.isEmpty()){list.getChildren().setAll(new Label("No definitions are configured."));return;}list.getChildren().setAll(rows.stream().map(this::card).toList());}
    private Node card(Row r){
        Label name=new Label(r.name());name.getStyleClass().add("search-section-title");
        Label meta=new Label((r.global()?"Global default":r.custom()?"Tenant custom":"Tenant override")+" • "+(r.active()?"Active":"Inactive")+" • Sort order "+r.order());
        meta.getStyleClass().add("search-summary-text");
        Region swatch=new Region();swatch.setMinSize(16,16);swatch.setPrefSize(16,16);String css=ColorUtil.toCssBackgroundColorOrNull(r.color());if(css!=null)swatch.setStyle("-fx-background-color: "+css+";");
        HBox heading=new HBox(8,swatch,name); FlowPane actions=new FlowPane(Orientation.HORIZONTAL,6,6);
        if(r.global())actions.getChildren().add(action("Customize",()->openEditor(r),ControlStyles.Purpose.SECONDARY));
        else {actions.getChildren().add(action("Edit",()->openEditor(r),ControlStyles.Purpose.SECONDARY));
            actions.getChildren().add(action(r.active()?"Deactivate":"Activate",()->setActive(r,!r.active()),ControlStyles.Purpose.SECONDARY));
            actions.getChildren().add(action(r.override()?"Reset Override":"Remove Custom",()->remove(r),ControlStyles.Purpose.DANGER));}
        VBox card=new VBox(7,heading,meta);if(selected()==Category.MATERIAL_TYPE&&r.description()!=null&&!r.description().isBlank())card.getChildren().add(new Label(r.description()));card.getChildren().add(actions);
        card.getStyleClass().addAll("shale-card-surface","shale-density-card-compact");return card;
    }

    private void openEditor(Row existing){
        Category category=selected(); boolean customize=existing!=null&&existing.global(); Dialog<Void>d=new Dialog<>();String heading=existing==null?"Add "+label(category):customize?"Customize "+label(category):"Edit "+label(category);
        d.setTitle(heading);if(root.getScene()!=null)d.initOwner(root.getScene().getWindow());AppDialogs.applySecondaryDialogShell(d,heading);d.getDialogPane().getButtonTypes().setAll(ButtonType.OK,ButtonType.CANCEL);
        TextField name=new TextField(existing==null?"":existing.name()), description=new TextField(existing==null?"":Objects.toString(existing.description(),"")), order=new TextField(existing==null?"0":Integer.toString(existing.order()));
        ColorPicker color=new ColorPicker(Color.web(existing==null||existing.color()==null?"#6C757D":existing.color()));CheckBox active=new CheckBox("Active");active.setSelected(existing==null||existing.active());
        GridPane form=new GridPane();form.setHgap(10);form.setVgap(10);int line=0;form.addRow(line++,new Label("Name *"),name);
        if(category==Category.MATERIAL_TYPE)form.addRow(line++,new Label("Description"),description);
        form.addRow(line++,new Label("Color *"),color);if(category!=Category.REQUEST_METHOD)form.addRow(line++,new Label("Sort Order"),order);form.add(active,1,line++);
        if(existing!=null&&existing.key()!=null&&!existing.key().isBlank())form.addRow(line,new Label("System Key"),new Label(existing.key()));d.getDialogPane().setContent(form);
        Button save=(Button)d.getDialogPane().lookupButton(ButtonType.OK);save.setText("Save");ControlStyles.apply(save,ControlStyles.Purpose.PRIMARY);ControlStyles.apply((Button)d.getDialogPane().lookupButton(ButtonType.CANCEL),ControlStyles.Purpose.SECONDARY);
        save.addEventFilter(javafx.event.ActionEvent.ACTION,e->{e.consume();String n=name.getText()==null?"":name.getText().trim();if(n.isBlank()){AppDialogs.showError(d.getOwner(),heading,"Name is required.");return;}int sort=existing==null?0:existing.order();if(category!=Category.REQUEST_METHOD)try{sort=Integer.parseInt(order.getText().trim());}catch(NumberFormatException x){AppDialogs.showError(d.getOwner(),heading,"Sort Order must be a number.");return;}final int authoritativeOrder=sort;String stored=ColorUtil.toStoredColor(color.getValue());
            Supplier<?> operation=()->switch(category){
                case MATERIAL_TYPE->existing==null||customize?service.createMaterialType(new MaterialRequestServicePort.MaterialTypeCommand(null,tenantId,actorId,n,description.getText(),stored,active.isSelected(),customize?existing.key():null,authoritativeOrder,null)):service.updateMaterialType(new MaterialRequestServicePort.MaterialTypeCommand(existing.id(),tenantId,actorId,n,description.getText(),stored,active.isSelected(),existing.key(),authoritativeOrder,existing.rowVer()));
                case REQUEST_METHOD->existing==null||customize?service.createRequestMethod(requestMethodCreateCommand(tenantId,actorId,n,stored,active.isSelected(),customize?existing.key():null)):service.updateRequestMethod(requestMethodEditCommand(existing.id(),tenantId,actorId,n,stored,active.isSelected(),existing.key(),existing.order(),existing.rowVer()));
                case REQUEST_STATUS->existing==null||customize?service.createRequestStatus(new MaterialRequestServicePort.RequestStatusCommand(null,tenantId,actorId,n,stored,active.isSelected(),customize?existing.key():null,authoritativeOrder,null)):service.updateRequestStatus(new MaterialRequestServicePort.RequestStatusCommand(existing.id(),tenantId,actorId,n,stored,active.isSelected(),existing.key(),authoritativeOrder,existing.rowVer()));};
            mutate(operation, d::close);
        });d.show();
    }
    static MaterialRequestServicePort.RequestMethodCommand requestMethodCreateCommand(int tenant,int actor,String name,String color,boolean active,String key){return new MaterialRequestServicePort.RequestMethodCommand(null,tenant,actor,name,color,active,key,null,null);}
    static MaterialRequestServicePort.RequestMethodCommand requestMethodEditCommand(int id,int tenant,int actor,String name,String color,boolean active,String key,int order,byte[] rv){return new MaterialRequestServicePort.RequestMethodCommand(id,tenant,actor,name,color,active,key,order,rv);}
    private void setActive(Row r,boolean active){var c=new MaterialRequestServicePort.SetLookupActiveCommand(tenantId,actorId,r.id(),active,r.rowVer());mutate(()->switch(selected()){case MATERIAL_TYPE->service.setMaterialTypeActive(c);case REQUEST_METHOD->service.setRequestMethodActive(c);case REQUEST_STATUS->service.setRequestStatusActive(c);},null);}
    private void remove(Row r){String verb=r.override()?"Reset Override":"Remove Custom";if(!AppDialogs.showConfirmation(root.getScene()==null?null:root.getScene().getWindow(),label(selected()),verb+" “"+r.name()+"”?","Existing Material Requests remain unchanged.",verb,AppDialogs.DialogActionKind.DANGER))return;var c=new MaterialRequestServicePort.ResetLookupOverrideCommand(tenantId,actorId,r.id());mutate(()->{switch(selected()){case MATERIAL_TYPE->service.resetMaterialTypeOverride(c);case REQUEST_METHOD->service.resetRequestMethodOverride(c);case REQUEST_STATUS->service.resetRequestStatusOverride(c);}return null;},null);}
    private void mutate(Supplier<?> work,Runnable success){if(disposed.get()||!mutating.compareAndSet(false,true))return;busy(true);status.setText("Saving…");worker.execute(()->{try{if(Platform.isFxApplicationThread())throw new IllegalStateException("Request definition mutations must run off the JavaFX thread.");work.get();changes.markCommitted();Platform.runLater(()->{mutating.set(false);if(disposed.get())return;if(success!=null)success.run();requestLoad();});}catch(RuntimeException x){Platform.runLater(()->{mutating.set(false);if(disposed.get())return;busy(false);status.setText("Change failed: "+Objects.toString(x.getMessage(),"Unknown error"));});}});}
    private void busy(boolean value){tabs.setDisable(value||mutating.get());add.setDisable(value||mutating.get());refresh.setDisable(value||mutating.get());}
    private static String label(Category c){return switch(c){case MATERIAL_TYPE->"Material Type";case REQUEST_METHOD->"Request Method";case REQUEST_STATUS->"Request Status";};}
    private static Button button(String text,ControlStyles.Purpose purpose){Button b=new Button(text);ControlStyles.apply(b,purpose,ControlStyles.Size.STANDARD);return b;}
    private static Button action(String text,Runnable work,ControlStyles.Purpose purpose){Button b=button(text,purpose);b.setOnAction(e->work.run());return b;}
    private static Row row(MaterialTypeDto d){return new Row(d.id(),d.shaleClientId(),d.systemKey(),d.name(),d.description(),d.color(),d.sortOrder(),d.active(),d.deleted(),d.rowVer());}
    private static Row row(RequestMethodDto d){return new Row(d.id(),d.shaleClientId(),d.systemKey(),d.name(),null,d.color(),d.sortOrder(),d.active(),d.deleted(),d.rowVer());}
    private static Row row(RequestStatusDto d){return new Row(d.id(),d.shaleClientId(),d.systemKey(),d.name(),null,d.color(),d.sortOrder(),d.active(),d.deleted(),d.rowVer());}
}
