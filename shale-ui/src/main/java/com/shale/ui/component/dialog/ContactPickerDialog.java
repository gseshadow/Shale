package com.shale.ui.component.dialog;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;


import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

public final class ContactPickerDialog<T> {

	private final Stage stage = new Stage();
	private final TextField searchField = new TextField();
	private final ListView<T> listView = new ListView<>();
	private final Button okButton = new Button("OK");
	private final Button cancelButton = new Button("Cancel");

	private final ObservableList<T> allItems;
	private final Function<T, String> labelFn;

	private T selected;

	public ContactPickerDialog(
			Window owner,
			String title,
			List<T> items,
			Function<T, String> labelFn,
			T preselectOrNull) {
		this.allItems = FXCollections.observableArrayList(items);
		this.labelFn = labelFn;

		AppDialogs.applySecondaryWindowChrome(stage);

		if (owner != null) {
			stage.initOwner(owner);
		}
		stage.initModality(Modality.WINDOW_MODAL);
		stage.setTitle(title);
		stage.setResizable(true);

		searchField.setPromptText("Search...");
		listView.setItems(FXCollections.observableArrayList(allItems));

		listView.setCellFactory(lv -> new ListCell<>() {
			@Override
			protected void updateItem(T item, boolean empty) {
				super.updateItem(item, empty);
				setText(empty || item == null ? "" : safe(labelFn.apply(item)));
			}
		});

		if (preselectOrNull != null) {
			listView.getSelectionModel().select(preselectOrNull);
			listView.scrollTo(preselectOrNull);
		} else if (!allItems.isEmpty()) {
			listView.getSelectionModel().select(0);
		}

		okButton.setDefaultButton(true);
		okButton.setDisable(listView.getSelectionModel().getSelectedItem() == null);

		cancelButton.setCancelButton(true);

		// Events
		searchField.textProperty().addListener((obs, oldV, newV) -> applyFilter(newV));
		listView.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> okButton.setDisable(n == null));

		okButton.setOnAction(e ->
		{
			selected = listView.getSelectionModel().getSelectedItem();
			stage.close();
		});
		cancelButton.setOnAction(e ->
		{
			selected = null;
			stage.close();
		});

		listView.setOnMouseClicked(e ->
		{
			if (e.getClickCount() == 2) {
				selected = listView.getSelectionModel().getSelectedItem();
				stage.close();
			}
		});
		listView.setOnKeyPressed(e ->
		{
			if (e.getCode() == KeyCode.ENTER) {
				selected = listView.getSelectionModel().getSelectedItem();
				stage.close();
			}
		});

		Label titleLabel = new Label(title);
		titleLabel.getStyleClass().add("app-dialog-title");

		Label instructionsLabel = new Label("Search and select an item.");
		instructionsLabel.getStyleClass().add("app-dialog-message");
		instructionsLabel.setWrapText(true);

		VBox header = new VBox(8, titleLabel, instructionsLabel);
		header.getStyleClass().add("app-dialog-header");

		searchField.getStyleClass().add("app-dialog-search-field");
		listView.getStyleClass().add("app-dialog-list");
		okButton.getStyleClass().addAll("app-dialog-button", "app-dialog-button-primary");
		cancelButton.getStyleClass().addAll("app-dialog-button", "app-dialog-button-secondary");

		Region spacer = new Region();
		HBox actions = new HBox(10, spacer, cancelButton, okButton);
		actions.setAlignment(Pos.CENTER_RIGHT);
		actions.getStyleClass().add("app-dialog-actions");
		HBox.setHgrow(spacer, Priority.ALWAYS);

		HBox windowHeader = AppDialogs.createSecondaryWindowHeader(stage, title, () -> {
			selected = null;
			stage.close();
		});
		VBox root = new VBox(16, windowHeader, header, searchField, listView, actions);
		root.getStyleClass().add("app-dialog-root");
		VBox.setVgrow(listView, Priority.ALWAYS);

		root.setPadding(new Insets(18));
		root.setPrefSize(520, 600);
		root.setMinWidth(460);

		Scene scene = new Scene(root);
		scene.getStylesheets().add(Objects.requireNonNull(
				getClass().getResource("/css/app.css")).toExternalForm());
		stage.setScene(scene);
	}

	public Optional<T> showAndWait() {
		stage.showAndWait();
		return Optional.ofNullable(selected);
	}

	private void applyFilter(String queryRaw) {
		String q = safe(queryRaw).trim().toLowerCase(Locale.ROOT);
		if (q.isEmpty()) {
			listView.setItems(FXCollections.observableArrayList(allItems));
			if (!listView.getItems().isEmpty())
				listView.getSelectionModel().select(0);
			return;
		}

		List<T> filtered = allItems.stream()
				.filter(it -> safe(labelFn.apply(it)).toLowerCase(Locale.ROOT).contains(q))
				.collect(Collectors.toList());

		listView.setItems(FXCollections.observableArrayList(filtered));
		if (!filtered.isEmpty())
			listView.getSelectionModel().select(0);
	}

	private static String safe(String s) {
		return s == null ? "" : s;
	}
}
