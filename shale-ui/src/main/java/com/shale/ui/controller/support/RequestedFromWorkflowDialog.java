package com.shale.ui.controller.support;

import com.shale.data.dao.CaseDao;
import com.shale.data.dao.OrganizationDao;
import com.shale.ui.component.dialog.AppDialogs;
import com.shale.ui.component.factory.ContactCardFactory;
import com.shale.ui.component.factory.OrganizationCardFactory;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import com.shale.ui.util.WindowSizingUtil;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

public final class RequestedFromWorkflowDialog {
    public record Selection(String entityType, Long id, String label, boolean createNew, String contactFirstName, String contactLastName, String organizationName, Integer organizationTypeId, ContactCardFactory.ContactCardModel contactModel, OrganizationCardFactory.OrganizationCardModel organizationModel) {}
    private record EntityOption(String entityType, Long id, String label, String email, String phone, String organizationTypeName) {
        ContactCardFactory.ContactCardModel contactModel() { return new ContactCardFactory.ContactCardModel(id == null ? null : id.intValue(), label, null, email, phone); }
        OrganizationCardFactory.OrganizationCardModel organizationModel() { return new OrganizationCardFactory.OrganizationCardModel(id == null ? null : id.intValue(), label, null, organizationTypeName, null, null, null, null, null, null, null, null, null, null, null); }
    }
    public record DirectoryData(List<CaseDao.SelectableContactRow> contacts, List<CaseDao.SelectableOrganizationRow> organizations, List<OrganizationDao.OrganizationTypeRow> organizationTypes) {}

    private RequestedFromWorkflowDialog() {}

    public static Selection show(Window owner, Supplier<DirectoryData> directoryLoader, Executor executor) {
        class State { int step=1; String mode; String entityType; boolean loading; EntityOption selected; DirectoryData data = new DirectoryData(List.of(), List.of(), List.of()); }
        State state = new State();
        Dialog<Selection> dialog = new Dialog<>();
        AppDialogs.applySecondaryDialogShell(dialog, "Requested From");
        dialog.setTitle("Requested From");
        if (owner != null) dialog.initOwner(owner);
        dialog.setResizable(true);
        ButtonType backType = new ButtonType("Back", ButtonBar.ButtonData.LEFT);
        ButtonType addType = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(backType, addType, ButtonType.CANCEL);
        Button back = (Button) dialog.getDialogPane().lookupButton(backType);
        Button add = (Button) dialog.getDialogPane().lookupButton(addType);
        Button cancel = (Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        PartyAddWorkflowDialog.applySharedDialogButtonStyle(back, false);
        PartyAddWorkflowDialog.applySharedDialogButtonStyle(add, true);
        PartyAddWorkflowDialog.applySharedDialogButtonStyle(cancel, false);

        Label title = new Label(); title.setStyle("-fx-font-size: 18px; -fx-font-weight: 700;");
        Label subtitle = new Label(); subtitle.setWrapText(true);
        Label status = new Label("Loading…"); status.getStyleClass().add("app-muted-text");
        TextField search = new TextField();
        ListView<EntityOption> results = new ListView<>(); results.setPrefHeight(420); results.setMinHeight(300); results.getStyleClass().addAll("app-dialog-list", "requested-from-results"); VBox.setVgrow(results, Priority.ALWAYS);
        ContactCardFactory contactCards = new ContactCardFactory(id -> {});
        OrganizationCardFactory organizationCards = new OrganizationCardFactory(id -> {});
        results.setCellFactory(lv -> new ListCell<>() { @Override protected void updateItem(EntityOption item, boolean empty) { super.updateItem(item, empty); setText(null); if (empty || item == null) { setGraphic(null); return; } Node card = "contact".equals(item.entityType())
                ? contactCards.create(new ContactCardFactory.ContactCardModel(item.id().intValue(), item.label(), null, item.email(), item.phone()), ContactCardFactory.Variant.MINI)
                : organizationCards.create(new OrganizationCardFactory.OrganizationCardModel(item.id().intValue(), item.label(), null, item.organizationTypeName(), null, null, null, null, null, null, null, null, null, null, null), OrganizationCardFactory.Variant.MINI); card.getStyleClass().add("requested-from-result-card"); setGraphic(card); getStyleClass().add("requested-from-result-cell"); }});
        TextField first = new TextField(); TextField last = new TextField(); TextField orgName = new TextField();
        ChoiceBox<OrganizationDao.OrganizationTypeRow> orgType = new ChoiceBox<>(); orgType.setConverter(new javafx.util.StringConverter<>() { public String toString(OrganizationDao.OrganizationTypeRow r){return r==null?"":safe(r.name());} public OrganizationDao.OrganizationTypeRow fromString(String s){return null;} });
        VBox box = new VBox(12, title, subtitle); box.setAlignment(Pos.TOP_CENTER); box.setPadding(new Insets(18)); box.setMinSize(560, 220); dialog.getDialogPane().setContent(box);

        Runnable filter = () -> { if (!"select".equals(state.mode) || state.entityType == null) return; String q = safe(search.getText()).toLowerCase(Locale.ROOT); List<EntityOption> opts = ("contact".equals(state.entityType) ? state.data.contacts().stream().map(c -> new EntityOption("contact", (long)c.id(), label(c), c.email(), c.phone(), null)) : state.data.organizations().stream().map(o -> new EntityOption("organization", (long)o.id(), safe(o.name()).isBlank()?"Organization #"+o.id():o.name(), null, null, o.organizationTypeName()))).filter(o -> q.isBlank() || haystack(o).contains(q)).toList(); results.getItems().setAll(opts); status.setText(opts.isEmpty() ? (q.isBlank()?"No " + state.entityType + "s exist.":"No records match the search.") : ""); };
        Runnable[] render = new Runnable[1];
        render[0] = () -> { box.getChildren().setAll(title, subtitle); add.setVisible(state.step==3); add.setManaged(state.step==3); back.setVisible(state.step>1); back.setManaged(state.step>1); add.setDisable(true); if(state.step==1){title.setText("Select Existing or Create New"); subtitle.setText("Choose how to add Requested From."); Button s=new Button("Select Existing"), c=new Button("Create New"); PartyAddWorkflowDialog.applySharedDialogButtonStyle(s,true); PartyAddWorkflowDialog.applySharedDialogButtonStyle(c,true); s.setMinWidth(200); c.setMinWidth(200); s.setOnAction(e->{state.mode="select";state.step=2;render[0].run();}); c.setOnAction(e->{state.mode="create";state.step=2;render[0].run();}); HBox h=new HBox(14,s,c);h.setAlignment(Pos.CENTER);box.getChildren().add(h);}
            else if(state.step==2){title.setText("Contact or Organization"); subtitle.setText("Choose the Requested From type."); Button c=new Button("Contact"), o=new Button("Organization"); PartyAddWorkflowDialog.applySharedDialogButtonStyle(c,true); PartyAddWorkflowDialog.applySharedDialogButtonStyle(o,true); c.setMinWidth(200); o.setMinWidth(200); c.setOnAction(e->{state.entityType="contact"; state.step=3; render[0].run();}); o.setOnAction(e->{state.entityType="organization"; state.step=3; render[0].run();}); HBox h=new HBox(14,c,o);h.setAlignment(Pos.CENTER);box.getChildren().add(h);}
            else if("select".equals(state.mode)){title.setText("Select Existing " + ("contact".equals(state.entityType)?"Contact":"Organization")); subtitle.setText("Search the eligible tenant directory."); search.setPromptText("contact".equals(state.entityType)?"Search contacts":"Search organizations"); search.getStyleClass().add("app-dialog-search-field"); search.setMaxWidth(Double.MAX_VALUE); box.getChildren().addAll(search, status, results); filter.run(); add.setDisable(state.selected==null || state.loading);}
            else {title.setText("Create New " + ("contact".equals(state.entityType)?"Contact":"Organization")); subtitle.setText("Enter Requested From details."); javafx.scene.layout.GridPane g=new javafx.scene.layout.GridPane(); g.setHgap(10); g.setVgap(10); if("contact".equals(state.entityType)){g.add(new Label("First Name"),0,0); g.add(first,1,0); g.add(new Label("Last Name"),0,1); g.add(last,1,1);} else {orgType.getItems().setAll(state.data.organizationTypes()); if(orgType.getValue()==null&&!orgType.getItems().isEmpty())orgType.setValue(orgType.getItems().get(0)); g.add(new Label("Name"),0,0); g.add(orgName,1,0); g.add(new Label("Organization Type"),0,1); g.add(orgType,1,1);} g.setMinHeight("contact".equals(state.entityType)?190:220); g.setMaxWidth(Double.MAX_VALUE); first.setMaxWidth(Double.MAX_VALUE); last.setMaxWidth(Double.MAX_VALUE); orgName.setMaxWidth(Double.MAX_VALUE); orgType.setMaxWidth(Double.MAX_VALUE); box.getChildren().add(g); VBox.setVgrow(g, Priority.ALWAYS); add.setDisable(!createValid(state.entityType, first, last, orgName, orgType));}
            applyWorkflowScreenSizing(dialog, owner, state.step, state.mode, box); };
        search.textProperty().addListener((o,a,b)->{state.selected=null; filter.run(); add.setDisable(true);});
        results.getSelectionModel().selectedItemProperty().addListener((o,a,b)->{state.selected=b; add.setDisable(b==null || state.loading);});
        first.textProperty().addListener((o,a,b)->add.setDisable(!createValid(state.entityType, first,last,orgName,orgType))); last.textProperty().addListener((o,a,b)->add.setDisable(!createValid(state.entityType, first,last,orgName,orgType))); orgName.textProperty().addListener((o,a,b)->add.setDisable(!createValid(state.entityType, first,last,orgName,orgType))); orgType.valueProperty().addListener((o,a,b)->add.setDisable(!createValid(state.entityType, first,last,orgName,orgType)));
        back.addEventFilter(javafx.event.ActionEvent.ACTION, e->{e.consume(); if(state.step==3){state.step=2; state.selected=null;} else if(state.step==2)state.step=1; render[0].run();});
        if (directoryLoader != null && executor != null) { state.loading=true; Task<DirectoryData> task=new Task<>(){protected DirectoryData call(){return directoryLoader.get();}}; task.setOnSucceeded(e->{state.loading=false; state.data=task.getValue()==null?state.data:task.getValue(); render[0].run();}); task.setOnFailed(e->{state.loading=false; status.setText("Loading failed."); AppDialogs.showError(owner,"Requests","Requested From choices could not be loaded. Please try again."); render[0].run();}); executor.execute(task); }
        dialog.setOnShown(e -> applyWorkflowScreenSizing(dialog, owner, state.step, state.mode, box));
        render[0].run();
        dialog.setResultConverter(bt->{ if(bt!=addType)return null; if("select".equals(state.mode)&&state.selected!=null)return new Selection(state.entityType,state.selected.id(),state.selected.label(),false,null,null,null,null,"contact".equals(state.entityType)?state.selected.contactModel():null,"organization".equals(state.entityType)?state.selected.organizationModel():null); return new Selection(state.entityType,null,null,true,trim(first.getText()),trim(last.getText()),trim(orgName.getText()),orgType.getValue()==null?null:orgType.getValue().organizationTypeId(),null,null); });
        return dialog.showAndWait().orElse(null);
    }
    static void applyWorkflowScreenSizing(Dialog<?> dialog, Window owner, int step, String mode, VBox content) {
        if (dialog == null) return;
        boolean selectFinal = step == 3 && "select".equals(mode);
        boolean createFinal = step == 3 && "create".equals(mode);
        double prefWidth = selectFinal || createFinal ? 800 : 560;
        double prefHeight = selectFinal ? 700 : createFinal ? 520 : 300;
        double minWidth = selectFinal || createFinal ? 720 : 520;
        double minHeight = selectFinal ? 600 : createFinal ? 440 : 280;
        DialogPane pane = dialog.getDialogPane();
        pane.setPrefSize(prefWidth, prefHeight);
        pane.setMinSize(minWidth, minHeight);
        if (content != null) {
            content.setMinWidth(Math.max(0, minWidth - 40));
            content.setPrefWidth(Math.max(0, prefWidth - 40));
        }
        Platform.runLater(() -> {
            pane.applyCss();
            pane.layout();
            Window window = pane.getScene() == null ? null : pane.getScene().getWindow();
            if (window instanceof Stage stage) {
                stage.setResizable(true);
                WindowSizingUtil.sizeModalStage(stage, owner, prefWidth, prefHeight, minWidth, minHeight);
                if (pane.getScene() != null) {
                    pane.getScene().getRoot().resize(stage.getWidth(), stage.getHeight());
                }
                pane.resize(stage.getWidth(), stage.getHeight());
                pane.applyCss();
                pane.layout();
                WindowSizingUtil.constrainToVisualBounds(stage, owner);
            }
        });
    }

    private static boolean createValid(String t, TextField f, TextField l, TextField o, ChoiceBox<OrganizationDao.OrganizationTypeRow> ot){return "contact".equals(t)?!safe(f.getText()).isBlank()||!safe(l.getText()).isBlank():!safe(o.getText()).isBlank()&&ot.getValue()!=null;}
    private static String label(CaseDao.SelectableContactRow c){String n=safe(c.displayName()); if(n.isBlank())n="Contact #"+c.id(); return n;}
    private static String haystack(EntityOption o){return String.join(" ", safe(o.label()), safe(o.email()), safe(o.phone()), safe(o.organizationTypeName())).toLowerCase(Locale.ROOT);}
    private static String safe(String s){return s==null?"":s;}
    private static String trim(String s){return s==null?null:s.trim();}
}
