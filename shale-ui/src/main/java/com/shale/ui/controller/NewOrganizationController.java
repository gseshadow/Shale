package com.shale.ui.controller;

import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.shale.core.service.OrganizationServicePort;
import com.shale.ui.component.dialog.AppDialogs;
import com.shale.ui.controller.support.OrganizationTypeAssignmentStage;
import com.shale.ui.state.AppState;
import com.shale.ui.util.ControlStyles;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/** Create lifecycle wrapper around the shared staged Organization editor. */
public final class NewOrganizationController {
    private static final Logger LOG = LoggerFactory.getLogger(NewOrganizationController.class);
    @FXML private Label validationLabel;
    @FXML private ScrollPane editorScroll;
    @FXML private Button cancelButton;
    @FXML private Button createOrganizationButton;
    private AppState appState; private OrganizationServicePort organizationService; private Stage stage;
    private Consumer<Integer> onOrganizationCreated; private OrganizationAggregateEditor editor; private boolean saving,forceClose;

    public void init(AppState appState,OrganizationServicePort organizationService,Stage stage,Consumer<Integer> onOrganizationCreated){this.appState=appState;this.organizationService=organizationService;this.stage=stage;this.onOrganizationCreated=onOrganizationCreated;stage.setOnCloseRequest(event->{if(!forceClose&&!confirmDiscard())event.consume();});loadOrganizationTypes();}
    @FXML private void initialize(){ControlStyles.apply(cancelButton,ControlStyles.Purpose.SECONDARY);ControlStyles.apply(createOrganizationButton,ControlStyles.Purpose.PRIMARY);createOrganizationButton.setDisable(true);}
    private void loadOrganizationTypes(){if(organizationService==null)return;showValidation("Loading Organization Types…");java.util.concurrent.CompletableFuture.supplyAsync(()->organizationService.listEffectiveOrganizationTypes(requireClientId())).whenComplete((definitions,failure)->Platform.runLater(()->{if(stage==null||!stage.isShowing())return;if(failure!=null){showValidation("Unable to load Organization Types.");return;}editor=OrganizationAggregateEditor.forCreate(OrganizationTypeAssignmentStage.forCreate(definitions));editor.setMessageHandler(message->{if(message==null||message.isBlank())hideValidation();else showValidation(message);});editorScroll.setContent(editor);createOrganizationButton.setDisable(false);hideValidation();}));}
    @FXML private void onCreateOrganization(){if(saving||editor==null)return;String validation=editor.validationError();if(validation!=null){showValidation(validation);return;}int tenant,actor;try{tenant=requireClientId();actor=requireActorId();}catch(RuntimeException failure){showValidation("A tenant and authorized user are required.");return;}var request=new OrganizationServicePort.CreateOrganizationAggregateCommand(tenant,actor,editor.fields(),editor.assignmentStage().commandAssignments(),editor.contactMutation());setSaving(true);showValidation("Creating Organization…");java.util.concurrent.CompletableFuture.supplyAsync(()->organizationService.createOrganizationAggregate(request)).whenComplete((result,failure)->Platform.runLater(()->{if(stage==null||!stage.isShowing())return;if(failure!=null){LOG.warn("Organization aggregate create failed operation=createOrganizationAggregate tenantId={} actorId={} exceptionClass={}",tenant,actor,failure.getClass().getName(),failure);setSaving(false);showValidation(failure.getCause() instanceof IllegalArgumentException&&failure.getCause().getMessage()!=null?failure.getCause().getMessage():"Unable to create Organization. No changes were applied.");return;}forceClose=true;stage.close();if(onOrganizationCreated!=null)onOrganizationCreated.accept(result.organizationId());}));}
    @FXML private void onCancel(){if(confirmDiscard()){forceClose=true;stage.close();}}
    private boolean confirmDiscard(){return !saving&&(editor==null||!editor.isDirty()||AppDialogs.showConfirmation(stage,"Discard Changes?","Discard unsaved changes?","Closing will discard all Organization and Organization Type changes.","Discard Changes",AppDialogs.DialogActionKind.DANGER));}
    private int requireClientId(){Integer id=appState==null?null:appState.getShaleClientId();if(id==null||id<=0)throw new IllegalStateException("No tenant selected.");return id;}private int requireActorId(){Integer id=appState==null?null:appState.getUserId();if(id==null||id<=0)throw new IllegalStateException("No user selected.");return id;}
    private void setSaving(boolean value){saving=value;createOrganizationButton.setDisable(value);cancelButton.setDisable(value);if(editor!=null)editor.setDisable(value);}
    private void showValidation(String message){validationLabel.setText(message);validationLabel.setVisible(true);validationLabel.setManaged(true);}private void hideValidation(){validationLabel.setText("");validationLabel.setVisible(false);validationLabel.setManaged(false);}
}
