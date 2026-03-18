package com.shale.ui.controller;

import com.shale.data.dao.ContactDao;
import com.shale.data.dao.ContactDao.ContactDetailRow;
import com.shale.data.dao.ContactDao.ContactProfileUpdateRequest;
import com.shale.ui.state.AppState;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.time.LocalDate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ContactViewController {

    @FXML private Label contactTitleLabel;
    @FXML private Label contactSubtitleLabel;
    @FXML private Label lastUpdatedLabel;
    @FXML private Label errorLabel;
    @FXML private Button editButton;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;

    @FXML private Label displayNameValue;
    @FXML private Label nameValue;
    @FXML private TextField nameEditor;
    @FXML private Label firstNameValue;
    @FXML private TextField firstNameEditor;
    @FXML private Label lastNameValue;
    @FXML private TextField lastNameEditor;
    @FXML private Label emailValue;
    @FXML private TextField emailEditor;
    @FXML private Label phoneValue;
    @FXML private TextField phoneEditor;
    @FXML private Label addressHomeValue;
    @FXML private TextArea addressHomeEditor;
    @FXML private Label dateOfBirthValue;
    @FXML private DatePicker dateOfBirthEditor;
    @FXML private Label conditionValue;
    @FXML private TextArea conditionEditor;
    @FXML private Label clientValue;
    @FXML private CheckBox clientEditor;
    @FXML private Label deceasedValue;
    @FXML private CheckBox deceasedEditor;

    private int contactId;
    private ContactDao contactDao;
    private AppState appState;
    private ContactDetailRow currentContact;
    private boolean editMode;

    private final ExecutorService dbExec = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "contact-view-loader");
        t.setDaemon(true);
        return t;
    });

    public void init(int contactId, ContactDao contactDao, AppState appState) {
        this.contactId = contactId;
        this.contactDao = contactDao;
        this.appState = appState;
    }

    @FXML
    private void initialize() {
        if (editButton != null) {
            editButton.setOnAction(e -> onEdit());
        }
        if (saveButton != null) {
            saveButton.setOnAction(e -> onSave());
        }
        if (cancelButton != null) {
            cancelButton.setOnAction(e -> onCancel());
        }

        setEditMode(false);
        Platform.runLater(this::loadContact);
    }

    private void loadContact() {
        Integer tenantId = appState == null ? null : appState.getShaleClientId();
        if (contactDao == null || tenantId == null || tenantId <= 0 || contactId <= 0) {
            setError("Contact details are unavailable right now.");
            return;
        }

        setBusy(true);
        dbExec.submit(() -> {
            try {
                ContactDetailRow row = contactDao.findById(contactId, tenantId);
                Platform.runLater(() -> {
                    setBusy(false);
                    if (row == null) {
                        setError("This contact could not be found for the current tenant.");
                        return;
                    }
                    currentContact = row;
                    renderFromCurrent();
                    setEditMode(false);
                    clearError();
                });
            } catch (RuntimeException ex) {
                Platform.runLater(() -> {
                    setBusy(false);
                    setError("Unable to load this contact.");
                });
            }
        });
    }

    private void onEdit() {
        if (!canEditContact()) {
            setError("You do not have permission to edit this contact.");
            return;
        }
        if (currentContact == null) {
            setError("Contact details are unavailable.");
            return;
        }
        writeEditorsFromCurrent();
        setEditMode(true);
        clearError();
    }

    private void onCancel() {
        if (currentContact != null) {
            writeEditorsFromCurrent();
            renderFromCurrent();
        }
        setEditMode(false);
        clearError();
    }

    private void onSave() {
        if (!canEditContact()) {
            setError("You do not have permission to save this contact.");
            return;
        }
        if (currentContact == null || contactDao == null) {
            setError("Contact details are unavailable.");
            return;
        }

        ContactProfileUpdateRequest request = new ContactProfileUpdateRequest(
                currentContact.id(),
                currentContact.shaleClientId(),
                safeText(nameEditor == null ? null : nameEditor.getText()),
                safeText(firstNameEditor == null ? null : firstNameEditor.getText()),
                safeText(lastNameEditor == null ? null : lastNameEditor.getText()),
                safeText(emailEditor == null ? null : emailEditor.getText()),
                safeText(phoneEditor == null ? null : phoneEditor.getText()),
                safeText(addressHomeEditor == null ? null : addressHomeEditor.getText()),
                dateOfBirthEditor == null ? null : dateOfBirthEditor.getValue(),
                safeText(conditionEditor == null ? null : conditionEditor.getText()),
                deceasedEditor != null && deceasedEditor.isSelected(),
                clientEditor != null && clientEditor.isSelected());

        setBusy(true);
        dbExec.submit(() -> {
            try {
                boolean updated = contactDao.updateBasicProfile(request);
                if (!updated) {
                    Platform.runLater(() -> {
                        setBusy(false);
                        setError("Contact could not be saved.");
                    });
                    return;
                }

                ContactDetailRow reloaded = contactDao.findById(currentContact.id(), currentContact.shaleClientId());
                Platform.runLater(() -> {
                    setBusy(false);
                    if (reloaded == null) {
                        setError("Contact could not be reloaded after save.");
                        return;
                    }
                    currentContact = reloaded;
                    renderFromCurrent();
                    setEditMode(false);
                    clearError();
                });
            } catch (RuntimeException ex) {
                Platform.runLater(() -> {
                    setBusy(false);
                    setError("Failed to save contact.");
                });
            }
        });
    }

    private void renderFromCurrent() {
        if (currentContact == null) {
            return;
        }

        if (contactTitleLabel != null) {
            contactTitleLabel.setText(fallback(currentContact.displayName(), "Contact"));
        }
        if (contactSubtitleLabel != null) {
            contactSubtitleLabel.setText("Contact #" + currentContact.id());
        }
        if (lastUpdatedLabel != null) {
            lastUpdatedLabel.setText("Last updated: " + ContactDao.formatTimestamp(currentContact.updatedAt()));
        }

        if (displayNameValue != null) {
            displayNameValue.setText(fallback(currentContact.displayName()));
        }
        if (nameValue != null) {
            nameValue.setText(fallback(currentContact.name()));
        }
        if (firstNameValue != null) {
            firstNameValue.setText(fallback(currentContact.firstName()));
        }
        if (lastNameValue != null) {
            lastNameValue.setText(fallback(currentContact.lastName()));
        }
        if (emailValue != null) {
            emailValue.setText(fallback(currentContact.email()));
        }
        if (phoneValue != null) {
            phoneValue.setText(fallback(currentContact.phone()));
        }
        if (addressHomeValue != null) {
            addressHomeValue.setText(fallback(currentContact.addressHome()));
        }
        if (dateOfBirthValue != null) {
            dateOfBirthValue.setText(formatDate(currentContact.dateOfBirth()));
        }
        if (conditionValue != null) {
            conditionValue.setText(fallback(currentContact.condition()));
        }
        if (clientValue != null) {
            clientValue.setText(booleanLabel(currentContact.client()));
        }
        if (deceasedValue != null) {
            deceasedValue.setText(booleanLabel(currentContact.deceased()));
        }

        writeEditorsFromCurrent();
    }

    private void writeEditorsFromCurrent() {
        if (currentContact == null) {
            return;
        }
        if (nameEditor != null) {
            nameEditor.setText(safe(currentContact.name()));
        }
        if (firstNameEditor != null) {
            firstNameEditor.setText(safe(currentContact.firstName()));
        }
        if (lastNameEditor != null) {
            lastNameEditor.setText(safe(currentContact.lastName()));
        }
        if (emailEditor != null) {
            emailEditor.setText(safe(currentContact.email()));
        }
        if (phoneEditor != null) {
            phoneEditor.setText(safe(currentContact.phone()));
        }
        if (addressHomeEditor != null) {
            addressHomeEditor.setText(safe(currentContact.addressHome()));
        }
        if (dateOfBirthEditor != null) {
            dateOfBirthEditor.setValue(currentContact.dateOfBirth());
        }
        if (conditionEditor != null) {
            conditionEditor.setText(safe(currentContact.condition()));
        }
        if (clientEditor != null) {
            clientEditor.setSelected(currentContact.client());
        }
        if (deceasedEditor != null) {
            deceasedEditor.setSelected(currentContact.deceased());
        }
    }

    private void setEditMode(boolean enabled) {
        this.editMode = enabled && canEditContact();

        setVisibleManaged(editButton, canEditContact() && !editMode);
        setVisibleManaged(saveButton, canEditContact() && editMode);
        setVisibleManaged(cancelButton, canEditContact() && editMode);

        toggleEdit(nameValue, nameEditor);
        toggleEdit(firstNameValue, firstNameEditor);
        toggleEdit(lastNameValue, lastNameEditor);
        toggleEdit(emailValue, emailEditor);
        toggleEdit(phoneValue, phoneEditor);
        toggleEdit(addressHomeValue, addressHomeEditor);
        toggleEdit(dateOfBirthValue, dateOfBirthEditor);
        toggleEdit(conditionValue, conditionEditor);
        toggleEdit(clientValue, clientEditor);
        toggleEdit(deceasedValue, deceasedEditor);
    }

    private void setBusy(boolean busy) {
        if (editButton != null) {
            editButton.setDisable(busy);
        }
        if (saveButton != null) {
            saveButton.setDisable(busy);
        }
        if (cancelButton != null) {
            cancelButton.setDisable(busy);
        }
        if (nameEditor != null) {
            nameEditor.setDisable(busy);
        }
        if (firstNameEditor != null) {
            firstNameEditor.setDisable(busy);
        }
        if (lastNameEditor != null) {
            lastNameEditor.setDisable(busy);
        }
        if (emailEditor != null) {
            emailEditor.setDisable(busy);
        }
        if (phoneEditor != null) {
            phoneEditor.setDisable(busy);
        }
        if (addressHomeEditor != null) {
            addressHomeEditor.setDisable(busy);
        }
        if (dateOfBirthEditor != null) {
            dateOfBirthEditor.setDisable(busy);
        }
        if (conditionEditor != null) {
            conditionEditor.setDisable(busy);
        }
        if (clientEditor != null) {
            clientEditor.setDisable(busy);
        }
        if (deceasedEditor != null) {
            deceasedEditor.setDisable(busy);
        }
    }

    private boolean canEditContact() {
        return appState != null && appState.getUserId() > 0;
    }

    private void toggleEdit(Node valueNode, Node editorNode) {
        setVisibleManaged(valueNode, !editMode);
        setVisibleManaged(editorNode, editMode);
    }

    private static void setVisibleManaged(Node node, boolean visible) {
        if (node == null) {
            return;
        }
        node.setVisible(visible);
        node.setManaged(visible);
    }

    private void setError(String message) {
        if (errorLabel != null) {
            errorLabel.setText(message == null ? "" : message);
            setVisibleManaged(errorLabel, message != null && !message.isBlank());
        }
    }

    private void clearError() {
        setError("");
    }

    private static String formatDate(LocalDate value) {
        return value == null ? "—" : value.toString();
    }

    private static String booleanLabel(boolean value) {
        return value ? "Yes" : "No";
    }

    private static String fallback(String value) {
        return fallback(value, "—");
    }

    private static String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String safeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
