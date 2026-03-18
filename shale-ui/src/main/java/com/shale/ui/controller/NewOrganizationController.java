package com.shale.ui.controller;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import com.shale.data.dao.OrganizationDao;
import com.shale.data.dao.OrganizationDao.OrganizationCreateRequest;
import com.shale.ui.state.AppState;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public final class NewOrganizationController {

    @FXML private Label validationLabel;
    @FXML private TextField nameField;
    @FXML private ComboBox<OrganizationDao.OrganizationTypeRow> organizationTypeComboBox;
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
    @FXML private TextArea notesArea;
    @FXML private Button cancelButton;
    @FXML private Button createOrganizationButton;

    private AppState appState;
    private OrganizationDao organizationDao;
    private Stage stage;
    private Consumer<Integer> onOrganizationCreated;
    private boolean saving;

    public void init(AppState appState, OrganizationDao organizationDao, Stage stage, Consumer<Integer> onOrganizationCreated) {
        this.appState = appState;
        this.organizationDao = organizationDao;
        this.stage = stage;
        this.onOrganizationCreated = onOrganizationCreated;
    }

    @FXML
    private void initialize() {
        if (organizationTypeComboBox != null) {
            organizationTypeComboBox.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
                @Override
                protected void updateItem(OrganizationDao.OrganizationTypeRow item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : fallback(item.name()));
                }
            });
            organizationTypeComboBox.setButtonCell(new javafx.scene.control.ListCell<>() {
                @Override
                protected void updateItem(OrganizationDao.OrganizationTypeRow item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : fallback(item.name()));
                }
            });
        }

        Platform.runLater(this::loadOrganizationTypes);
    }

    private void loadOrganizationTypes() {
        if (organizationDao == null || organizationTypeComboBox == null) {
            showValidation("Organization creation is not configured.");
            return;
        }

        try {
            List<OrganizationDao.OrganizationTypeRow> types = organizationDao.findOrganizationTypes();
            organizationTypeComboBox.setItems(FXCollections.observableArrayList(types));
            if (!types.isEmpty()) {
                organizationTypeComboBox.getSelectionModel().selectFirst();
            }
            hideValidation();
        } catch (RuntimeException ex) {
            showValidation("Unable to load organization types.");
        }
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

        OrganizationDao.OrganizationTypeRow selectedType = organizationTypeComboBox.getSelectionModel().getSelectedItem();
        OrganizationCreateRequest request = new OrganizationCreateRequest(
                requireClientId(),
                selectedType.organizationTypeId(),
                safeText(nameField.getText()),
                safeText(phoneField.getText()),
                safeText(faxField.getText()),
                safeText(emailField.getText()),
                safeText(websiteField.getText()),
                safeText(address1Field.getText()),
                safeText(address2Field.getText()),
                safeText(cityField.getText()),
                safeText(stateField.getText()),
                safeText(postalCodeField.getText()),
                safeText(countryField.getText()),
                safeText(notesArea.getText())
        );

        setSaving(true);
        try {
            int organizationId = organizationDao.create(request);
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

        OrganizationDao.OrganizationTypeRow selectedType = organizationTypeComboBox == null
                ? null
                : organizationTypeComboBox.getSelectionModel().getSelectedItem();
        if (selectedType == null || selectedType.organizationTypeId() <= 0) {
            return Optional.of("Organization Type is required.");
        }

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
