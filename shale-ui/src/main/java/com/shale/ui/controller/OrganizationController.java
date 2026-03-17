package com.shale.ui.controller;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import com.shale.core.model.Organization;
import com.shale.data.dao.OrganizationDao;
import com.shale.ui.component.dialog.ContactPickerDialog;
import com.shale.ui.component.factory.CaseCardFactory;
import com.shale.ui.component.factory.CaseCardFactory.CaseCardModel;
import com.shale.ui.services.UiRuntimeBridge;
import com.shale.ui.state.AppState;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.Node;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.stage.Window;

public final class OrganizationController {

	@FXML private Label organizationTitleLabel;
	@FXML private Label organizationTypeLabel;
	@FXML private Label lastUpdatedLabel;
	@FXML private Label errorLabel;
	@FXML private Button editButton;
	@FXML private Button saveButton;
	@FXML private Button cancelButton;
	@FXML private HBox remoteUpdateBanner;
	@FXML private Button reloadRemoteButton;
	@FXML private FlowPane relatedCasesFlow;
	@FXML private Label relatedCasesEmptyLabel;
	@FXML private Button addCaseButton;

	@FXML private Label nameValue;
	@FXML private TextField nameEditor;
	@FXML private Label typeValue;
	@FXML private Label phoneValue;
	@FXML private TextField phoneEditor;
	@FXML private Label faxValue;
	@FXML private TextField faxEditor;
	@FXML private Label emailValue;
	@FXML private TextField emailEditor;
	@FXML private Label websiteValue;
	@FXML private TextField websiteEditor;
	@FXML private Label address1Value;
	@FXML private TextField address1Editor;
	@FXML private Label address2Value;
	@FXML private TextField address2Editor;
	@FXML private Label cityValue;
	@FXML private TextField cityEditor;
	@FXML private Label stateValue;
	@FXML private TextField stateEditor;
	@FXML private Label postalCodeValue;
	@FXML private TextField postalCodeEditor;
	@FXML private Label countryValue;
	@FXML private TextField countryEditor;
	@FXML private Label notesValue;
	@FXML private TextArea notesEditor;

	private Integer organizationId;
	private OrganizationDao organizationDao;
	private Organization currentOrganization;
	private boolean editMode;
	private AppState appState;
	private UiRuntimeBridge runtimeBridge;
	private Consumer<UiRuntimeBridge.EntityUpdatedEvent> liveOrganizationUpdatedHandler;
	private boolean liveSubscribed;
	private boolean pendingRemoteUpdate;
	private List<OrganizationDao.RelatedCaseRow> relatedCases = List.of();
	private CaseCardFactory caseCardFactory;
	private Consumer<Integer> onOpenCase;

	private final ExecutorService dbExec = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "organization-detail-loader");
		t.setDaemon(true);
		return t;
	});

	public void init(int organizationId, OrganizationDao organizationDao, AppState appState, UiRuntimeBridge runtimeBridge, Consumer<Integer> onOpenCase) {
		this.organizationId = organizationId;
		this.organizationDao = organizationDao;
		this.appState = appState;
		this.runtimeBridge = runtimeBridge;
		this.onOpenCase = onOpenCase;
		this.caseCardFactory = new CaseCardFactory(onOpenCase);
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
		if (reloadRemoteButton != null) {
			reloadRemoteButton.setOnAction(e -> onReloadRemote());
		}
		if (addCaseButton != null) {
			addCaseButton.setOnAction(e -> onAddCase());
		}

		setEditMode(false);
		hideRemoteUpdateBanner();

		if (organizationTitleLabel != null) {
			organizationTitleLabel.sceneProperty().addListener((obs, oldScene, newScene) -> {
				if (newScene == null) {
					unsubscribeLiveOrganizationUpdates();
				} else {
					subscribeLiveOrganizationUpdates();
				}
			});
		}

		subscribeLiveOrganizationUpdates();
		Platform.runLater(this::loadOrganization);
	}

	private void loadOrganization() {
		if (organizationDao == null || organizationId == null) {
			setError("Organization view is not configured.");
			return;
		}

		setBusy(true);
		dbExec.submit(() -> {
			try {
				Organization loaded = organizationDao.findById(organizationId);
				Platform.runLater(() -> {
					setBusy(false);
					if (loaded == null) {
						relatedCases = List.of();
						renderRelatedCases();
						setError("Organization not found.");
						return;
					}

					currentOrganization = loaded;
					renderFromCurrent();
					clearError();
					loadRelatedCasesSafe();
				});
			} catch (Exception ex) {
				Platform.runLater(() -> {
					setBusy(false);
					setError("Failed to load organization details.");
				});
			}
		});
	}



	private void loadRelatedCasesSafe() {
		if (organizationDao == null || organizationId == null) {
			relatedCases = List.of();
			renderRelatedCases();
			return;
		}

		dbExec.submit(() -> {
			try {
				List<OrganizationDao.RelatedCaseRow> loadedRelatedCases = organizationDao.findRelatedCases(organizationId);
				Platform.runLater(() -> {
					relatedCases = loadedRelatedCases == null ? List.of() : loadedRelatedCases;
					renderRelatedCases();
				});
			} catch (Exception ex) {
				System.out.println("Failed to load related cases for organization id=" + organizationId + ": " + ex.getMessage());
				Platform.runLater(() -> {
					relatedCases = List.of();
					renderRelatedCases();
				});
			}
		});
	}



	private void onAddCase() {
		if (organizationDao == null || organizationId == null || addCaseButton == null) {
			return;
		}

		Window owner = addCaseButton.getScene() == null ? null : addCaseButton.getScene().getWindow();
		dbExec.submit(() -> {
			try {
				List<OrganizationDao.SelectableCaseRow> selectable = organizationDao.findLinkableCases(organizationId);
				Platform.runLater(() -> showAddCasePicker(owner, selectable));
			} catch (Exception ex) {
				Platform.runLater(() -> setError("Failed to load cases for linking."));
			}
		});
	}

	private void showAddCasePicker(Window owner, List<OrganizationDao.SelectableCaseRow> selectable) {
		List<OrganizationDao.SelectableCaseRow> options = selectable == null ? List.of() : selectable;
		if (options.isEmpty()) {
			setError("No available cases to link.");
			return;
		}

		ContactPickerDialog<OrganizationDao.SelectableCaseRow> picker = new ContactPickerDialog<>(
				owner,
				"Add Case",
				options,
				row -> row == null ? "" : fallback(row.name()) + " (#" + row.id() + ")",
				null);

		Optional<OrganizationDao.SelectableCaseRow> selected = picker.showAndWait();
		if (selected.isEmpty()) {
			return;
		}

		OrganizationDao.SelectableCaseRow chosen = selected.get();
		dbExec.submit(() -> {
			try {
				organizationDao.linkCaseToOrganization(organizationId, chosen.id());
				Platform.runLater(() -> {
					clearError();
					loadRelatedCasesSafe();
				});
			} catch (Exception ex) {
				Platform.runLater(() -> setError("Failed to link case to organization."));
			}
		});
	}

	private void onEdit() {
		if (currentOrganization == null) {
			return;
		}
		writeEditorsFromOrganization(currentOrganization);
		setEditMode(true);
	}

	private void onCancel() {
		if (pendingRemoteUpdate) {
			setEditMode(false);
			onReloadRemote();
			return;
		}

		if (currentOrganization != null) {
			writeEditorsFromOrganization(currentOrganization);
			renderFromCurrent();
		}
		setEditMode(false);
		clearError();
	}

	private void onSave() {
		if (currentOrganization == null || organizationDao == null) {
			setError("Organization details are unavailable.");
			return;
		}

		Organization updated = Organization.builder()
				.id(currentOrganization.getId())
				.shaleClientId(currentOrganization.getShaleClientId())
				.organizationTypeId(currentOrganization.getOrganizationTypeId())
				.organizationTypeName(currentOrganization.getOrganizationTypeName())
				.name(safeText(nameEditor.getText()))
				.phone(safeText(phoneEditor.getText()))
				.fax(safeText(faxEditor.getText()))
				.email(safeText(emailEditor.getText()))
				.website(safeText(websiteEditor.getText()))
				.address1(safeText(address1Editor.getText()))
				.address2(safeText(address2Editor.getText()))
				.city(safeText(cityEditor.getText()))
				.state(safeText(stateEditor.getText()))
				.postalCode(safeText(postalCodeEditor.getText()))
				.country(safeText(countryEditor.getText()))
				.notes(safeText(notesEditor.getText()))
				.deleted(currentOrganization.isDeleted())
				.createdAt(currentOrganization.getCreatedAt())
				.updatedAt(currentOrganization.getUpdatedAt())
				.build();

		setBusy(true);
		dbExec.submit(() -> {
			try {
				organizationDao.update(updated);
				publishOrganizationUpdated(updated);
				Organization reloaded = organizationDao.findById(currentOrganization.getId());
				Platform.runLater(() -> {
					setBusy(false);
					if (reloaded == null) {
						setError("Organization could not be reloaded after save.");
						return;
					}
					currentOrganization = reloaded;
					pendingRemoteUpdate = false;
					hideRemoteUpdateBanner();
					renderFromCurrent();
					setEditMode(false);
					clearError();
				});
			} catch (Exception ex) {
				Platform.runLater(() -> {
					setBusy(false);
					setError("Failed to save organization.");
				});
			}
		});
	}


	private void onReloadRemote() {
		pendingRemoteUpdate = false;
		hideRemoteUpdateBanner();
		loadOrganization();
	}

	private void subscribeLiveOrganizationUpdates() {
		if (runtimeBridge == null || liveSubscribed) {
			return;
		}

		liveOrganizationUpdatedHandler = this::handleLiveOrganizationUpdatedEvent;
		runtimeBridge.subscribeEntityUpdated(liveOrganizationUpdatedHandler);
		liveSubscribed = true;
	}

	private void unsubscribeLiveOrganizationUpdates() {
		if (!liveSubscribed || runtimeBridge == null || liveOrganizationUpdatedHandler == null) {
			return;
		}

		runtimeBridge.unsubscribeEntityUpdated(liveOrganizationUpdatedHandler);
		liveSubscribed = false;
	}

	private void handleLiveOrganizationUpdatedEvent(UiRuntimeBridge.EntityUpdatedEvent event) {
		if (shouldIgnoreLiveEvent(event)) {
			return;
		}

		runOnFx(() -> {
			if (editMode) {
				pendingRemoteUpdate = true;
				showRemoteUpdateBanner();
				return;
			}

			onReloadRemote();
		});
	}

	private boolean shouldIgnoreLiveEvent(UiRuntimeBridge.EntityUpdatedEvent event) {
		if (event == null || organizationId == null || event.entityType() == null) {
			return true;
		}
		if (!"Organization".equals(event.entityType())) {
			return true;
		}
		if (event.entityId() != organizationId.longValue()) {
			return true;
		}
		return isOwnEcho(event);
	}

	private boolean isOwnEcho(UiRuntimeBridge.EntityUpdatedEvent event) {
		if (runtimeBridge == null) {
			return false;
		}
		String mine = runtimeBridge.getClientInstanceId();
		return mine != null && !mine.isBlank() && mine.equals(event.clientInstanceId());
	}

	private void showRemoteUpdateBanner() {
		setVisibleManaged(remoteUpdateBanner, true);
		setVisibleManaged(reloadRemoteButton, true);
	}

	private void hideRemoteUpdateBanner() {
		setVisibleManaged(remoteUpdateBanner, false);
		setVisibleManaged(reloadRemoteButton, false);
	}

	private void publishOrganizationUpdated(Organization organization) {
		if (organization == null || organization.getId() == null || appState == null || runtimeBridge == null
				|| appState.getShaleClientId() == null || appState.getUserId() == null) {
			return;
		}

		try {
			int clientId = appState.getShaleClientId();
			int userId = appState.getUserId();
			runtimeBridge.publishOrganizationUpdated(organization.getId(), clientId, userId);
		} catch (Exception ex) {
			System.out.println("OrganizationUpdated publish skipped: " + ex.getMessage());
		}
	}

	private void renderFromCurrent() {
		Organization o = currentOrganization;
		if (o == null) {
			return;
		}

		organizationTitleLabel.setText(fallback(o.getName()));
		String type = fallback(o.getOrganizationTypeName());
		organizationTypeLabel.setText("Type: " + type);
		typeValue.setText(type);
		nameValue.setText(fallback(o.getName()));
		phoneValue.setText(fallback(o.getPhone()));
		faxValue.setText(fallback(o.getFax()));
		emailValue.setText(fallback(o.getEmail()));
		websiteValue.setText(fallback(o.getWebsite()));
		address1Value.setText(fallback(o.getAddress1()));
		address2Value.setText(fallback(o.getAddress2()));
		cityValue.setText(fallback(o.getCity()));
		stateValue.setText(fallback(o.getState()));
		postalCodeValue.setText(fallback(o.getPostalCode()));
		countryValue.setText(fallback(o.getCountry()));
		notesValue.setText(fallback(o.getNotes()));

		if (o.getUpdatedAt() != null) {
			String formatted = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a")
					.withZone(ZoneId.systemDefault())
					.format(o.getUpdatedAt());
			lastUpdatedLabel.setText("Last updated: " + formatted);
		} else {
			lastUpdatedLabel.setText("Last updated: —");
		}

		writeEditorsFromOrganization(o);
	}

	private void writeEditorsFromOrganization(Organization o) {
		nameEditor.setText(safeText(o.getName()));
		phoneEditor.setText(safeText(o.getPhone()));
		faxEditor.setText(safeText(o.getFax()));
		emailEditor.setText(safeText(o.getEmail()));
		websiteEditor.setText(safeText(o.getWebsite()));
		address1Editor.setText(safeText(o.getAddress1()));
		address2Editor.setText(safeText(o.getAddress2()));
		cityEditor.setText(safeText(o.getCity()));
		stateEditor.setText(safeText(o.getState()));
		postalCodeEditor.setText(safeText(o.getPostalCode()));
		countryEditor.setText(safeText(o.getCountry()));
		notesEditor.setText(safeText(o.getNotes()));
	}


	private void renderRelatedCases() {
		if (!Platform.isFxApplicationThread()) {
			runOnFx(this::renderRelatedCases);
			return;
		}

		if (relatedCasesFlow == null) {
			return;
		}

		if (caseCardFactory == null) {
			caseCardFactory = new CaseCardFactory(onOpenCase);
		}

		List<Node> cards = relatedCases.stream()
				.map(c -> caseCardFactory.create(new CaseCardModel(
						c.id(),
						c.name(),
						c.intakeDate(),
						c.statuteOfLimitationsDate(),
						c.responsibleAttorneyName(),
						c.responsibleAttorneyColor()
				)))
				.toList();

		relatedCasesFlow.getChildren().setAll(cards);

		boolean empty = cards.isEmpty();
		if (relatedCasesEmptyLabel != null) {
			relatedCasesEmptyLabel.setVisible(empty);
			relatedCasesEmptyLabel.setManaged(empty);
			// StackPane overlays both nodes; keep the active one on top.
			if (empty) {
				relatedCasesEmptyLabel.toFront();
			} else {
				relatedCasesFlow.toFront();
			}
		}
	}


	private void setEditMode(boolean enabled) {
		this.editMode = enabled;
		setVisibleManaged(editButton, !enabled);
		setVisibleManaged(saveButton, enabled);
		setVisibleManaged(cancelButton, enabled);

		toggleField(nameValue, nameEditor, enabled);
		toggleField(phoneValue, phoneEditor, enabled);
		toggleField(faxValue, faxEditor, enabled);
		toggleField(emailValue, emailEditor, enabled);
		toggleField(websiteValue, websiteEditor, enabled);
		toggleField(address1Value, address1Editor, enabled);
		toggleField(address2Value, address2Editor, enabled);
		toggleField(cityValue, cityEditor, enabled);
		toggleField(stateValue, stateEditor, enabled);
		toggleField(postalCodeValue, postalCodeEditor, enabled);
		toggleField(countryValue, countryEditor, enabled);
		toggleField(notesValue, notesEditor, enabled);
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
	}

	private static void toggleField(Label valueNode, javafx.scene.Node editorNode, boolean editMode) {
		setVisibleManaged(valueNode, !editMode);
		setVisibleManaged(editorNode, editMode);
	}

	private static void runOnFx(Runnable runnable) {
		if (Platform.isFxApplicationThread()) {
			runnable.run();
		} else {
			Platform.runLater(runnable);
		}
	}

	private static void setVisibleManaged(javafx.scene.Node node, boolean visible) {
		if (node == null) {
			return;
		}
		node.setVisible(visible);
		node.setManaged(visible);
	}

	private void setError(String message) {
		if (errorLabel == null) {
			return;
		}
		errorLabel.setText(message);
		errorLabel.setVisible(true);
		errorLabel.setManaged(true);
	}

	private void clearError() {
		if (errorLabel == null) {
			return;
		}
		errorLabel.setText("");
		errorLabel.setVisible(false);
		errorLabel.setManaged(false);
	}

	private static String fallback(String text) {
		if (text == null || text.isBlank()) {
			return "—";
		}
		return text;
	}

	private static String safeText(String text) {
		if (text == null) {
			return "";
		}
		return text.trim();
	}
}
