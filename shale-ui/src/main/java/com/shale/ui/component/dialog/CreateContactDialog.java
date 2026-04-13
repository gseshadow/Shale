package com.shale.ui.component.dialog;

import java.util.Objects;
import java.util.Optional;

import com.shale.data.dao.ContactDao;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

public final class CreateContactDialog {

    private final Stage stage;
    private final TextField firstNameField = new TextField();
    private final TextField lastNameField = new TextField();
    private final TextField emailField = new TextField();
    private final TextField phoneField = new TextField();
    private final CheckBox clientCheckBox = new CheckBox("Mark as client");
    private final Label errorLabel = new Label();
    private ContactDao.CreateContactRequest result;

    public CreateContactDialog(Window owner) {
        stage = AppDialogs.createModalStage(owner, "New Contact");
        stage.setResizable(false);

        Label titleLabel = new Label("Create a new contact");
        titleLabel.getStyleClass().add("app-dialog-title");

        Label subtitleLabel = new Label("Create the contact first, then it will be linked to the current case automatically.");
        subtitleLabel.getStyleClass().add("app-dialog-message");
        subtitleLabel.setWrapText(true);

        firstNameField.setPromptText("First name");
        lastNameField.setPromptText("Last name");
        emailField.setPromptText("Email");
        phoneField.setPromptText("Phone");

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(10);
        form.add(new Label("First Name"), 0, 0);
        form.add(firstNameField, 1, 0);
        form.add(new Label("Last Name"), 0, 1);
        form.add(lastNameField, 1, 1);
        form.add(new Label("Email"), 0, 2);
        form.add(emailField, 1, 2);
        form.add(new Label("Phone"), 0, 3);
        form.add(phoneField, 1, 3);
        form.add(clientCheckBox, 1, 4);
        GridPane.setHgrow(firstNameField, Priority.ALWAYS);
        GridPane.setHgrow(lastNameField, Priority.ALWAYS);
        GridPane.setHgrow(emailField, Priority.ALWAYS);
        GridPane.setHgrow(phoneField, Priority.ALWAYS);

        errorLabel.setWrapText(true);
        errorLabel.setStyle("-fx-text-fill: #b42318;");
        errorLabel.setManaged(false);
        errorLabel.setVisible(false);

        Button cancelButton = new Button("Cancel");
        cancelButton.getStyleClass().addAll("app-dialog-button", "app-dialog-button-secondary");
        cancelButton.setCancelButton(true);
        cancelButton.setOnAction(e -> {
            result = null;
            stage.close();
        });

        Button createButton = new Button("Create Contact");
        createButton.getStyleClass().addAll("app-dialog-button", "app-dialog-button-primary");
        createButton.setDefaultButton(true);
        createButton.setOnAction(e -> onCreate());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(10, spacer, cancelButton, createButton);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.getStyleClass().add("app-dialog-actions");

        VBox header = new VBox(8, titleLabel, subtitleLabel);
        header.getStyleClass().add("app-dialog-header");

        VBox body = new VBox(16, header, form, errorLabel, actions);
        body.setPadding(new Insets(18));
        VBox root = AppDialogs.createSecondaryWindowShell(stage, "New Contact", () -> {
            result = null;
            stage.close();
        }, body);
        root.setMinWidth(440);

        Scene scene = new Scene(root);
        scene.getStylesheets().add(Objects.requireNonNull(
                getClass().getResource("/css/app.css")).toExternalForm());
        stage.setScene(scene);
    }

    public Optional<ContactDao.CreateContactRequest> showAndWait(int shaleClientId) {
        result = null;
        hideError();
        firstNameField.requestFocus();
        stage.showAndWait();
        if (result == null) {
            return Optional.empty();
        }
        return Optional.of(new ContactDao.CreateContactRequest(
                shaleClientId,
                result.firstName(),
                result.lastName(),
                result.email(),
                result.phone(),
                result.client()));
    }

    private void onCreate() {
        String firstName = normalize(firstNameField.getText());
        String lastName = normalize(lastNameField.getText());
        if (firstName == null && lastName == null) {
            showError("Enter at least a first name or last name.");
            return;
        }
        result = new ContactDao.CreateContactRequest(
                0,
                firstName,
                lastName,
                normalize(emailField.getText()),
                normalize(phoneField.getText()),
                clientCheckBox.isSelected());
        stage.close();
    }

    private void showError(String message) {
        errorLabel.setText(message == null ? "" : message);
        errorLabel.setManaged(true);
        errorLabel.setVisible(true);
    }

    private void hideError() {
        errorLabel.setText("");
        errorLabel.setManaged(false);
        errorLabel.setVisible(false);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
