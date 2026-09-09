package com.shale.ui.controller;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import com.shale.core.service.OrganizationServicePort;
import com.shale.core.service.OrganizationServicePort.CreateOrganizationAggregateCommand;
import com.shale.core.service.OrganizationServicePort.OrganizationFields;
import com.shale.ui.component.OrganizationTypeAssignmentPane;
import com.shale.ui.controller.support.OrganizationTypeAssignmentStage;
import com.shale.ui.state.AppState;
import com.shale.ui.util.ControlStyles;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import com.shale.ui.component.EnhancedTextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public final class NewOrganizationController {

    @FXML private Label validationLabel;
    @FXML private TextField nameField;
    @FXML private OrganizationTypeAssignmentPane organizationTypeAssignments;
    @FXML private TextField phoneField;
    @FXML private TextField faxField;
    @FXML private TextField emailField;
    @FXML private TextField websiteField;
    @FXML private TextField address1Field;
    @FXML private TextField address2Field;
    @FXML private TextField cityField;
    @FXML private TextField stateField;
    @FXML private TextField postalCodeField;
    @FXML private TextField countryField;
    @FXML private EnhancedTextArea notesArea;
    @FXML private Button cancelButton;
    @FXML private Button createOrganizationButton;

    private AppState appState;
    private OrganizationServicePort organizationService;
    private Stage stage;
    private Consumer<Integer> onOrganizationCreated;
    private boolean saving;

    public void init(AppState appState, OrganizationServicePort organizationService, Stage stage, Consumer<Integer> onOrganizationCreated) {
        this.appState = appState;
        this.organizationService = organizationService;
        this.stage = stage;
        this.onOrganizationCreated = onOrganizationCreated;
    }

    @FXML
    private void initialize() {
        ControlStyles.apply(cancelButton, ControlStyles.Purpose.SECONDARY);
        ControlStyles.apply(createOrganizationButton, ControlStyles.Purpose.PRIMARY);
        organizationTypeAssignments.setMessageHandler(message -> { if (message == null || message.isBlank()) hideValidation(); else showValidation(message); });
        for (var control : List.of(nameField, phoneField, faxField, emailField, websiteField,
                address1Field, address2Field, cityField, stateField, postalCodeField, countryField)) ControlStyles.formControl(control);

        Platform.runLater(this::loadOrganizationTypes);
    }

    private void loadOrganizationTypes() {
        if (organizationService == null || organizationTypeAssignments == null) { showValidation("Organization creation is not configured."); return; }
        try { organizationTypeAssignments.setStage(OrganizationTypeAssignmentStage.forCreate(
                organizationService.listEffectiveOrganizationTypes(requireClientId()))); hideValidation(); }
        catch (RuntimeException ex) { showValidation("Unable to load organization types."); }
    }

    @FXML
    private void onCreateOrganization() {
        if (saving) {
            return;
        }

        Optional<String> validationError = validate();
        if (validationError.isPresent()) {
            showValidation(validationError.get());
            return;
        }

        OrganizationFields fields = new OrganizationFields(safeText(nameField.getText()), safeText(phoneField.getText()),
                safeText(faxField.getText()), safeText(emailField.getText()), safeText(websiteField.getText()),
                safeText(address1Field.getText()), safeText(address2Field.getText()), safeText(cityField.getText()),
                safeText(stateField.getText()), safeText(postalCodeField.getText()), safeText(countryField.getText()), safeText(notesArea.getText()));
        CreateOrganizationAggregateCommand request = new CreateOrganizationAggregateCommand(requireClientId(), requireActorId(),
                fields, organizationTypeAssignments.getStage().commandAssignments());

        setSaving(true);
        try {
            int organizationId = organizationService.createOrganizationAggregate(request).organizationId();
            hideValidation();
            if (onOrganizationCreated != null) {
                onOrganizationCreated.accept(organizationId);
            }
            closeStage();
        } catch (RuntimeException ex) {
            showValidation("Unable to create organization.");
        } finally {
            setSaving(false);
        }
    }

    @FXML
    private void onCancel() {
        closeStage();
    }

    private Optional<String> validate() {
        String name = safeText(nameField.getText());
        if (name == null || name.isBlank()) {
            return Optional.of("Name is required.");
        }

        if (organizationTypeAssignments == null || organizationTypeAssignments.getStage() == null
                || !organizationTypeAssignments.getStage().isValid()) return Optional.of("Exactly one primary Organization Type is required.");

        try {
            requireClientId();
        } catch (RuntimeException ex) {
            return Optional.of("No tenant is selected.");
        }

        return Optional.empty();
    }

    private int requireClientId() {
        Integer clientId = appState == null ? null : appState.getShaleClientId();
        if (clientId == null || clientId <= 0) {
            throw new RuntimeException("No tenant selected.");
        }
        return clientId;
    }

    private int requireActorId() { Integer id=appState==null?null:appState.getUserId(); if(id==null||id<=0)throw new RuntimeException("No user selected."); return id; }

    private void setSaving(boolean saving) {
        this.saving = saving;
        if (createOrganizationButton != null) {
            createOrganizationButton.setDisable(saving);
        }
        if (cancelButton != null) {
            cancelButton.setDisable(saving);
        }
    }

    private void closeStage() {
        if (stage != null) {
            stage.close();
        }
    }

    private void showValidation(String message) {
        if (validationLabel != null) {
            validationLabel.setText(message);
            validationLabel.setVisible(true);
            validationLabel.setManaged(true);
        }
    }

    private void hideValidation() {
        if (validationLabel != null) {
            validationLabel.setText("");
            validationLabel.setVisible(false);
            validationLabel.setManaged(false);
        }
    }

    private static String safeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String fallback(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }
}
