package com.shale.ui.controller;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.shale.core.model.Organization;
import com.shale.core.service.OrganizationServicePort;
import com.shale.data.dao.OrganizationDao;
import com.shale.ui.component.dialog.AppDialogs;
import com.shale.ui.controller.support.OrganizationTypeAssignmentStage;
import com.shale.ui.state.AppState;
import com.shale.ui.util.ControlStyles;
import com.shale.ui.util.WindowSizingUtil;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/** Edit lifecycle wrapper around the shared staged Organization editor. */
final class EditOrganizationDialog {
    private static final Logger LOG = LoggerFactory.getLogger(EditOrganizationDialog.class);
    record LoadResult(Organization organization, byte[] rowVer, OrganizationTypeAssignmentStage assignments,
            OrganizationServicePort.OrganizationStructuredContactProfile contacts) {
        LoadResult { rowVer = rowVer == null ? null : rowVer.clone(); }
        @Override public byte[] rowVer() { return rowVer == null ? null : rowVer.clone(); }
    }
    private final int organizationId; private final OrganizationDao dao; private final OrganizationServicePort service;
    private final AppState state; private final Executor executor;
    private final Consumer<OrganizationServicePort.OrganizationAggregateResult> saved; private final Runnable closed;
    private final Dialog<Void> dialog=new Dialog<>(); private final Label status=new Label("Loading authoritative Organization details…");
    private final ScrollPane scroll=new ScrollPane(); private final ButtonType saveType=new ButtonType("Save Changes",ButtonData.OK_DONE);
    private LoadResult baseline; private OrganizationAggregateEditor editor; private boolean saving,loading=true,forceClose; private long generation;

    EditOrganizationDialog(int organizationId,OrganizationDao dao,OrganizationServicePort service,AppState state,Executor executor,
            Consumer<OrganizationServicePort.OrganizationAggregateResult> saved,Runnable closed){this.organizationId=organizationId;this.dao=Objects.requireNonNull(dao);this.service=Objects.requireNonNull(service);this.state=Objects.requireNonNull(state);this.executor=Objects.requireNonNull(executor);this.saved=Objects.requireNonNull(saved);this.closed=Objects.requireNonNull(closed);}
    void show(Window owner){AppDialogs.applySecondaryDialogShell(dialog,"Edit Organization");if(owner!=null)dialog.initOwner(owner);dialog.initModality(Modality.WINDOW_MODAL);dialog.setResizable(true);dialog.getDialogPane().getButtonTypes().setAll(saveType,ButtonType.CANCEL);status.setWrapText(true);status.getStyleClass().add("dialog-error-text");scroll.setFitToWidth(true);scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);scroll.getStyleClass().add("contact-editor-section-scroll");VBox shell=new VBox(10,status,scroll);shell.getStyleClass().add("contact-editor-surface");VBox.setVgrow(scroll,Priority.ALWAYS);dialog.getDialogPane().setContent(shell);dialog.getDialogPane().getStyleClass().add("contact-editor-dialog");dialog.getDialogPane().setPrefSize(900,680);Button saveButton=button(saveType),cancelButton=button(ButtonType.CANCEL);ControlStyles.apply(saveButton,ControlStyles.Purpose.PRIMARY);ControlStyles.apply(cancelButton,ControlStyles.Purpose.SECONDARY);saveButton.setDisable(true);saveButton.addEventFilter(javafx.event.ActionEvent.ACTION,e->{e.consume();save();});cancelButton.addEventFilter(javafx.event.ActionEvent.ACTION,e->{e.consume();requestClose();});dialog.setOnCloseRequest(e->{if(!forceClose&&!confirmDiscard())e.consume();});dialog.setOnHidden(e->{generation++;closed.run();});dialog.show();if(dialog.getDialogPane().getScene().getWindow() instanceof Stage stage)WindowSizingUtil.sizeModalStage(stage,owner,900,680,680,480);reload();}
    private void reload(){loading=true;baseline=null;scroll.setContent(null);showStatus("Loading authoritative Organization details…");button(saveType).setDisable(true);long request=++generation;int tenant=requirePositive(state.getShaleClientId(),"No tenant is selected.");executor.execute(()->{try{Organization organization=dao.findById(organizationId);if(organization==null||!Objects.equals(organization.getShaleClientId(),tenant))throw new IllegalStateException("Organization was not found.");var definitions=service.listEffectiveOrganizationTypes(tenant);var profile=service.getOrganizationTypeProfile(organizationId,tenant).orElseThrow(()->new IllegalStateException("Organization Type profile was not found."));var stage=OrganizationTypeAssignmentStage.forEdit(definitions,profile);var contacts=service.findStructuredContactProfile(tenant,organizationId).orElseThrow(()->new IllegalStateException("Structured Organization contact profile was not found."));byte[] rowVer=dao.findOrganizationRowVer(organizationId,tenant);if(rowVer==null)throw new IllegalStateException("Organization concurrency data was not found.");Platform.runLater(()->applyLoad(request,new LoadResult(organization,rowVer,stage,contacts)));}catch(RuntimeException failure){Platform.runLater(()->applyLoadFailure(request));}});}
    private void applyLoad(long request,LoadResult loaded){if(request!=generation||!dialog.isShowing())return;baseline=loaded;loading=false;editor=OrganizationAggregateEditor.forEdit(loaded.organization(),loaded.assignments(),loaded.contacts());editor.setMessageHandler(this::showStatus);scroll.setContent(editor);if(!loaded.contacts().compatibilityConsistent()){showStatus("Organization contact data is inconsistent with its compatibility values. Reload after resolving the conflict; saving is disabled.");button(saveType).setDisable(true);}else{hideStatus();button(saveType).setDisable(false);}}
    private void applyLoadFailure(long request){if(request!=generation||!dialog.isShowing())return;loading=false;showStatus("Unable to load authoritative Organization details. Close this window and try again.");}
    private void save(){if(saving||loading||baseline==null)return;String validation=editor.validationError();if(validation!=null){showStatus(validation);return;}int tenant,actor;try{tenant=requirePositive(state.getShaleClientId(),"No tenant is selected.");actor=requirePositive(state.getUserId(),"You are not authorized to edit Organizations.");}catch(IllegalStateException failure){showStatus(failure.getMessage());return;}var command=new OrganizationServicePort.UpdateOrganizationAggregateCommand(organizationId,tenant,actor,baseline.rowVer(),editor.fields(),editor.assignmentStage().commandAssignments(),editor.contactMutation());saving=true;setControlsDisabled(true);showStatus("Saving Organization…");executor.execute(()->{try{var result=service.updateOrganizationAggregate(command);Platform.runLater(()->{if(!dialog.isShowing())return;saving=false;forceClose=true;dialog.close();saved.accept(result);});}catch(RuntimeException failure){LOG.warn("Organization aggregate save failed operation=updateOrganizationAggregate tenantId={} actorId={} organizationId={} exceptionClass={}",tenant,actor,organizationId,failure.getClass().getName(),failure);Platform.runLater(()->{if(!dialog.isShowing())return;saving=false;String message=failure.getMessage()==null?"":failure.getMessage().toLowerCase(java.util.Locale.ROOT);if(message.contains("changed by another user")||message.contains("row changed")){showStatus("Organization changed elsewhere. Authoritative values are being reloaded.");reload();}else{setControlsDisabled(false);showStatus(failure instanceof IllegalArgumentException&&!message.isBlank()?failure.getMessage():"Unable to save Organization. No changes were applied.");}});}});}
    private void requestClose(){if(confirmDiscard()){forceClose=true;dialog.close();}}
    private boolean confirmDiscard(){return !saving&&(editor==null||!editor.isDirty()||AppDialogs.showConfirmation(dialog.getOwner(),"Discard Changes?","Discard unsaved changes?","Closing will discard all Organization and Organization Type changes.","Discard Changes",AppDialogs.DialogActionKind.DANGER));}
    private void setControlsDisabled(boolean disabled){if(editor!=null)editor.setDisable(disabled);button(saveType).setDisable(disabled);button(ButtonType.CANCEL).setDisable(disabled);}
    private void showStatus(String message){status.setText(message==null?"":message);status.setVisible(true);status.setManaged(true);}private void hideStatus(){status.setText("");status.setVisible(false);status.setManaged(false);}private Button button(ButtonType type){return (Button)dialog.getDialogPane().lookupButton(type);}private static int requirePositive(Integer value,String message){if(value==null||value<=0)throw new IllegalStateException(message);return value;}
}
