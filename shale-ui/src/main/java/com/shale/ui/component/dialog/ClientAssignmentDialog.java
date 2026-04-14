package com.shale.ui.component.dialog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.shale.core.dto.CaseOverviewDto;
import com.shale.data.dao.CaseDao;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

public final class ClientAssignmentDialog {

	@FunctionalInterface
	public interface ClientCreateHandler {
		CaseDao.ContactRow create(String firstName, String lastName) throws Exception;
	}

	public record Result(List<CaseOverviewDto.ContactSummary> assignedClients) {}

	private final Stage stage;
	private Optional<Result> result = Optional.empty();
	private final ClientCreateHandler clientCreateHandler;

	private final TextField searchField = new TextField();
	private final ListView<CaseDao.ContactRow> availableList = new ListView<>();
	private final ListView<CaseDao.ContactRow> assignedList = new ListView<>();
	private final Label errorLabel = new Label();

	private final ObservableList<CaseDao.ContactRow> availableMaster = FXCollections.observableArrayList();
	private final ObservableList<CaseDao.ContactRow> availableFiltered = FXCollections.observableArrayList();
	private final ObservableList<CaseDao.ContactRow> assignedItems = FXCollections.observableArrayList();

	public ClientAssignmentDialog(
			Window owner,
			List<CaseDao.ContactRow> allClients,
			List<CaseOverviewDto.ContactSummary> assignedClients,
			ClientCreateHandler clientCreateHandler) {
		this.clientCreateHandler = Objects.requireNonNull(clientCreateHandler, "clientCreateHandler");

		stage = new Stage();
		AppDialogs.applySecondaryWindowChrome(stage);
		stage.initOwner(owner);
		stage.initModality(Modality.APPLICATION_MODAL);
		stage.setTitle("Manage Clients");

		Map<Integer, CaseDao.ContactRow> allById = new LinkedHashMap<>();
		for (CaseDao.ContactRow row : (allClients == null ? List.<CaseDao.ContactRow>of() : allClients)) {
			if (row == null || row.id() <= 0 || safeText(row.displayName()).isBlank())
				continue;
			allById.putIfAbsent(row.id(), row);
		}

		Map<Integer, CaseDao.ContactRow> assignedById = new LinkedHashMap<>();
		for (CaseOverviewDto.ContactSummary assigned : (assignedClients == null ? List.<CaseOverviewDto.ContactSummary>of() : assignedClients)) {
			if (assigned == null || assigned.contactId() == null || assigned.contactId() <= 0)
				continue;
			CaseDao.ContactRow existing = allById.get(assigned.contactId());
			if (existing != null) {
				assignedById.putIfAbsent(existing.id(), existing);
				continue;
			}
			String fallbackName = safeText(assigned.displayName()).isBlank()
					? "Contact #" + assigned.contactId()
					: assigned.displayName();
			assignedById.putIfAbsent(assigned.contactId(), new CaseDao.ContactRow(assigned.contactId(), fallbackName));
		}

		for (CaseDao.ContactRow row : allById.values()) {
			if (!assignedById.containsKey(row.id()))
				availableMaster.add(row);
		}
		assignedItems.addAll(assignedById.values());
		sortContacts(availableMaster);
		sortContacts(assignedItems);

		availableFiltered.setAll(availableMaster);
		availableList.setItems(availableFiltered);
		assignedList.setItems(assignedItems);
		availableList.setPrefWidth(300);
		assignedList.setPrefWidth(300);
		availableList.setCellFactory(lv -> contactCell());
		assignedList.setCellFactory(lv -> contactCell());

		searchField.setPromptText("Search available clients...");
		searchField.textProperty().addListener((obs, oldV, newV) -> applyAvailableFilter(newV));

		Button addButton = new Button("→");
		Button removeButton = new Button("←");
		Button newClientButton = new Button("New Client");

		addButton.setOnAction(e -> moveToAssigned(availableList.getSelectionModel().getSelectedItem()));
		removeButton.setOnAction(e -> moveToAvailable(assignedList.getSelectionModel().getSelectedItem()));
		newClientButton.setOnAction(e -> onCreateClient());

		VBox middleButtons = new VBox(10, addButton, removeButton, newClientButton);
		middleButtons.setAlignment(Pos.CENTER);

		VBox left = new VBox(8, new Label("Available Clients"), searchField, availableList);
		VBox right = new VBox(8, new Label("Assigned Clients"), assignedList);
		VBox.setVgrow(availableList, Priority.ALWAYS);
		VBox.setVgrow(assignedList, Priority.ALWAYS);

		Button cancelButton = new Button("Cancel");
		cancelButton.setCancelButton(true);
		cancelButton.setOnAction(e -> {
			result = Optional.empty();
			stage.close();
		});
		Button saveButton = new Button("Save");
		saveButton.setDefaultButton(true);
		saveButton.setOnAction(e -> {
			List<CaseOverviewDto.ContactSummary> out = assignedItems.stream()
					.map(c -> new CaseOverviewDto.ContactSummary(c.id(), c.displayName()))
					.toList();
			result = Optional.of(new Result(out));
			stage.close();
		});

		errorLabel.setManaged(false);
		errorLabel.setVisible(false);
		errorLabel.setStyle("-fx-text-fill: #b42318;");

		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		HBox actions = new HBox(10, errorLabel, spacer, cancelButton, saveButton);
		actions.setAlignment(Pos.CENTER_RIGHT);

		HBox body = new HBox(14, left, middleButtons, right);
		HBox.setHgrow(left, Priority.ALWAYS);
		HBox.setHgrow(right, Priority.ALWAYS);

		VBox dialogBody = new VBox(12, body, actions);
		dialogBody.setPadding(new Insets(14));
		VBox root = AppDialogs.createSecondaryWindowShell(stage, "Manage Clients", () -> {
			result = Optional.empty();
			stage.close();
		}, dialogBody);
		root.getStyleClass().add("secondary-window-shell");
		Scene scene = new Scene(root, 760, 420);
		stage.setScene(scene);
	}

	public Optional<Result> showAndWait() {
		result = Optional.empty();
		hideError();
		searchField.requestFocus();
		stage.showAndWait();
		return result;
	}

	private void onCreateClient() {
		Optional<NewClientDialog.Result> created = new NewClientDialog(stage).showAndWait();
		if (created.isEmpty())
			return;
		try {
			CaseDao.ContactRow createdRow = clientCreateHandler.create(created.get().firstName(), created.get().lastName());
			if (createdRow == null || createdRow.id() <= 0)
				throw new IllegalStateException("Contact creation did not return a valid contact.");
			moveToAssigned(createdRow);
			availableMaster.removeIf(c -> c.id() == createdRow.id());
			applyAvailableFilter(searchField.getText());
			hideError();
		} catch (Exception ex) {
			showError("Failed to create client. " + safeText(ex.getMessage()));
		}
	}

	private void moveToAssigned(CaseDao.ContactRow row) {
		if (row == null)
			return;
		if (assignedItems.stream().anyMatch(c -> c.id() == row.id()))
			return;
		availableMaster.removeIf(c -> c.id() == row.id());
		assignedItems.add(row);
		sortContacts(assignedItems);
		applyAvailableFilter(searchField.getText());
	}

	private void moveToAvailable(CaseDao.ContactRow row) {
		if (row == null)
			return;
		assignedItems.removeIf(c -> c.id() == row.id());
		if (availableMaster.stream().noneMatch(c -> c.id() == row.id()))
			availableMaster.add(row);
		sortContacts(availableMaster);
		applyAvailableFilter(searchField.getText());
	}

	private void applyAvailableFilter(String rawQuery) {
		String q = safeText(rawQuery).trim().toLowerCase();
		if (q.isEmpty()) {
			availableFiltered.setAll(availableMaster);
			return;
		}
		List<CaseDao.ContactRow> filtered = new ArrayList<>();
		for (CaseDao.ContactRow row : availableMaster) {
			String display = safeText(row.displayName()).toLowerCase();
			if (display.contains(q) || String.valueOf(row.id()).contains(q))
				filtered.add(row);
		}
		availableFiltered.setAll(filtered);
	}

	private ListCell<CaseDao.ContactRow> contactCell() {
		return new ListCell<>() {
			@Override
			protected void updateItem(CaseDao.ContactRow item, boolean empty) {
				super.updateItem(item, empty);
				setText(empty || item == null ? null : safeText(item.displayName()) + "  (#" + item.id() + ")");
			}
		};
	}

	private void sortContacts(ObservableList<CaseDao.ContactRow> rows) {
		FXCollections.sort(rows, (a, b) -> safeText(a.displayName()).compareToIgnoreCase(safeText(b.displayName())));
	}

	private void showError(String message) {
		errorLabel.setText(safeText(message));
		errorLabel.setManaged(true);
		errorLabel.setVisible(true);
	}

	private void hideError() {
		errorLabel.setText("");
		errorLabel.setManaged(false);
		errorLabel.setVisible(false);
	}

	private static String safeText(String value) {
		return value == null ? "" : value.trim();
	}
}
