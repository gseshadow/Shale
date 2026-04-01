package com.shale.ui.component.dialog;

import java.util.Optional;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

public final class NewClientDialog {
	public record Result(String firstName, String lastName) {}

	private final Stage stage;
	private final TextField firstNameField = new TextField();
	private final TextField lastNameField = new TextField();
	private final Label errorLabel = new Label();
	private Optional<Result> result = Optional.empty();

	public NewClientDialog(Window owner) {
		stage = new Stage();
		stage.initOwner(owner);
		stage.initModality(Modality.APPLICATION_MODAL);
		stage.setTitle("New Client");
		stage.setResizable(false);

		firstNameField.setPromptText("First Name");
		lastNameField.setPromptText("Last Name");

		GridPane form = new GridPane();
		form.setHgap(10);
		form.setVgap(8);
		form.add(new Label("First Name"), 0, 0);
		form.add(firstNameField, 1, 0);
		form.add(new Label("Last Name"), 0, 1);
		form.add(lastNameField, 1, 1);
		GridPane.setHgrow(firstNameField, Priority.ALWAYS);
		GridPane.setHgrow(lastNameField, Priority.ALWAYS);

		errorLabel.setStyle("-fx-text-fill: #b42318;");
		errorLabel.setManaged(false);
		errorLabel.setVisible(false);

		Button cancelButton = new Button("Cancel");
		cancelButton.setCancelButton(true);
		cancelButton.setOnAction(e -> {
			result = Optional.empty();
			stage.close();
		});
		Button createButton = new Button("Create");
		createButton.setDefaultButton(true);
		createButton.setOnAction(e -> onCreate());

		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		HBox actions = new HBox(10, spacer, cancelButton, createButton);
		actions.setAlignment(Pos.CENTER_RIGHT);

		VBox root = new VBox(12, form, errorLabel, actions);
		root.setPadding(new Insets(12));
		Scene scene = new Scene(root, 360, 170);
		stage.setScene(scene);
	}

	public Optional<Result> showAndWait() {
		result = Optional.empty();
		errorLabel.setManaged(false);
		errorLabel.setVisible(false);
		stage.showAndWait();
		return result;
	}

	private void onCreate() {
		String first = normalize(firstNameField.getText());
		String last = normalize(lastNameField.getText());
		if (first == null && last == null) {
			errorLabel.setText("Enter first name or last name.");
			errorLabel.setManaged(true);
			errorLabel.setVisible(true);
			return;
		}
		result = Optional.of(new Result(first, last));
		stage.close();
	}

	private static String normalize(String value) {
		if (value == null)
			return null;
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
