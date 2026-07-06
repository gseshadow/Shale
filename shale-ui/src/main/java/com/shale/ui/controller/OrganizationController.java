package com.shale.ui.controller;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import com.shale.core.model.Organization;
import com.shale.data.dao.OrganizationDao;
import com.shale.ui.component.dialog.AppDialogs;
import com.shale.ui.component.factory.CaseCardFactory;
import com.shale.ui.component.factory.CaseCardFactory.CaseCardModel;
import com.shale.ui.controller.support.CaseListFilterSortSupport;
import com.shale.ui.services.UiRuntimeBridge;
import com.shale.ui.state.AppState;
import com.shale.ui.util.PerfLog;
import com.shale.ui.util.ReadOnlyTextDisplaySupport;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.Tooltip;
import javafx.scene.paint.Color;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

public final class OrganizationController {

	private static final Map<String, Organization> DETAIL_CACHE = new ConcurrentHashMap<>();
	private static final Map<Integer, List<OrganizationDao.OrganizationTypeRow>> TYPE_OPTIONS_CACHE = new ConcurrentHashMap<>();

	@FXML private Label organizationTitleLabel;
	@FXML private Label lastUpdatedLabel;
	@FXML private Label errorLabel;
	@FXML private Button editButton;
	@FXML private Button saveButton;
	@FXML private Button cancelButton;
	@FXML private Button deleteOrganizationButton;
	@FXML private HBox remoteUpdateBanner;
	@FXML private Button reloadRemoteButton;
	@FXML private VBox relatedCasesContainer;
	@FXML private Label relatedCasesEmptyLabel;
	@FXML private TextField relatedCasesSearchField;
	@FXML private ChoiceBox<String> relatedCasesSortChoice;

	@FXML private Label nameValue;
	@FXML private TextField nameEditor;
	@FXML private Label typeValue;
	@FXML private ComboBox<OrganizationDao.OrganizationTypeRow> typeEditor;
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
	@FXML private Button editNameButton;
	@FXML private Button editTypeButton;
	@FXML private Button editPhoneButton;
	@FXML private Button editFaxButton;
	@FXML private Button editEmailButton;
	@FXML private Button editWebsiteButton;
	@FXML private Button editAddress1Button;
	@FXML private Button editAddress2Button;
	@FXML private Button editCityButton;
	@FXML private Button editStateButton;
	@FXML private Button editPostalCodeButton;
	@FXML private Button editCountryButton;
	@FXML private Button editNotesButton;

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
	private List<OrganizationDao.OrganizationTypeRow> organizationTypeOptions = List.of();
	private CaseCardFactory caseCardFactory;
	private Consumer<Integer> onOpenCase;
	private Runnable onOrganizationDeleted;

	private final ExecutorService dbExec = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "organization-detail-loader");
		t.setDaemon(true);
		return t;
	});

	public void init(
			int organizationId,
			OrganizationDao organizationDao,
			AppState appState,
			UiRuntimeBridge runtimeBridge,
			Consumer<Integer> onOpenCase,
			Runnable onOrganizationDeleted) {
		this.organizationId = organizationId;
		this.organizationDao = organizationDao;
		this.appState = appState;
		this.runtimeBridge = runtimeBridge;
		this.onOpenCase = onOpenCase;
		this.onOrganizationDeleted = onOrganizationDeleted;
		this.caseCardFactory = new CaseCardFactory(onOpenCase);
	}

	@FXML
	private void initialize() {
		if (editButton != null) {
			editButton.setOnAction(e -> onEdit());
			setVisibleManaged(editButton, false);
		}
		initializeInlineEditButtons();
		if (saveButton != null) {
			saveButton.setOnAction(e -> onSave());
		}
		if (cancelButton != null) {
			cancelButton.setOnAction(e -> onCancel());
		}
		if (deleteOrganizationButton != null) {
			deleteOrganizationButton.setOnAction(e -> onDeleteOrganization());
			setVisibleManaged(deleteOrganizationButton, false);
		}
		if (reloadRemoteButton != null) {
			reloadRemoteButton.setOnAction(e -> onReloadRemote());
		}
		CaseListFilterSortSupport.initializeControls(relatedCasesSearchField, relatedCasesSortChoice, this::renderRelatedCases);

		if (typeEditor != null) {
			typeEditor.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
				@Override
				protected void updateItem(OrganizationDao.OrganizationTypeRow item, boolean empty) {
					super.updateItem(item, empty);
					setText(empty || item == null ? null : fallback(item.name()));
				}
			});
			typeEditor.setButtonCell(new javafx.scene.control.ListCell<>() {
				@Override
				protected void updateItem(OrganizationDao.OrganizationTypeRow item, boolean empty) {
					super.updateItem(item, empty);
					setText(empty || item == null ? null : fallback(item.name()));
				}
			});
		}

		setEditMode(false);
		hideRemoteUpdateBanner();
		refreshAdminActions();

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

	private void initializeInlineEditButtons() {
		configureTextEditButton(editNameButton, "Name", true, OrganizationField.NAME);
		configureTypeEditButton();
		configureTextEditButton(editPhoneButton, "Phone", false, OrganizationField.PHONE);
		configureTextEditButton(editFaxButton, "Fax", false, OrganizationField.FAX);
		configureTextEditButton(editEmailButton, "Email", false, OrganizationField.EMAIL);
		configureTextEditButton(editWebsiteButton, "Website", false, OrganizationField.WEBSITE);
		configureTextEditButton(editAddress1Button, "Address1", false, OrganizationField.ADDRESS1);
		configureTextEditButton(editAddress2Button, "Address2", false, OrganizationField.ADDRESS2);
		configureTextEditButton(editCityButton, "City", false, OrganizationField.CITY);
		configureTextEditButton(editStateButton, "State", false, OrganizationField.STATE);
		configureTextEditButton(editPostalCodeButton, "Postal Code", false, OrganizationField.POSTAL_CODE);
		configureTextEditButton(editCountryButton, "Country", false, OrganizationField.COUNTRY);
		configureTextAreaEditButton(editNotesButton, "Notes", OrganizationField.NOTES);
	}

	private void configureTextEditButton(Button button, String fieldLabel, boolean required, OrganizationField field) {
		configureInlineEditButton(button, fieldLabel, () -> showOrganizationTextFieldDialog(
				"Edit " + fieldLabel,
				fieldLabel,
				field.textValue(currentOrganization),
				required,
				button,
				value -> saveSingleOrganizationField(field, value)));
	}

	private void configureTextAreaEditButton(Button button, String fieldLabel, OrganizationField field) {
		configureInlineEditButton(button, fieldLabel, () -> showOrganizationTextAreaDialog(
				"Edit " + fieldLabel,
				fieldLabel,
				field.textValue(currentOrganization),
				button,
				value -> saveSingleOrganizationField(field, value)));
	}

	private void configureTypeEditButton() {
		configureInlineEditButton(editTypeButton, "Organization Type", () -> showOrganizationTypeDialog(
				currentOrganization == null ? null : currentOrganization.getOrganizationTypeId(),
				editTypeButton,
				this::saveOrganizationTypeField));
	}

	private void configureInlineEditButton(Button button, String fieldLabel, Runnable action) {
		if (button == null) {
			return;
		}
		button.setTooltip(new Tooltip("Edit " + fieldLabel));
		button.setOnAction(e -> {
			if (currentOrganization == null) {
				return;
			}
			clearError();
			action.run();
		});
	}

	private void loadOrganization() {
		long loadStarted = PerfLog.start();
		if (organizationDao == null || organizationId == null) {
			setError("Organization view is not configured.");
			return;
		}

		setBusy(true);
		PerfLog.log("organizations.detail.load", "queued", "organizationId=" + organizationId + " tenantId=" + currentTenantId());
		dbExec.submit(() -> {
			try {
				String cacheKey = detailCacheKey(organizationId);
				Organization loaded = cacheKey == null ? null : DETAIL_CACHE.get(cacheKey);
				boolean cacheHit = loaded != null;
				if (loaded == null) {
					long daoStarted = PerfLog.start();
					PerfLog.log("organizations.detail.dao", "start", "organizationId=" + organizationId + " tenantId=" + currentTenantId() + " cacheHit=false");
					loaded = organizationDao.findById(organizationId);
					PerfLog.logDone("organizations.detail.dao", "organizationId=" + organizationId + " found=" + (loaded != null) + " fullDetailHydration=true", daoStarted);
					if (loaded != null && cacheKey != null) {
						DETAIL_CACHE.put(cacheKey, loaded);
					}
				} else {
					PerfLog.log("organizations.detail.cache", "hit", "organizationId=" + organizationId + " tenantId=" + currentTenantId());
				}
				Integer tenantId = currentTenantId();
				List<OrganizationDao.OrganizationTypeRow> loadedTypeOptions = tenantId == null ? null : TYPE_OPTIONS_CACHE.get(tenantId);
				if (loadedTypeOptions == null) {
					long typesStarted = PerfLog.start();
					loadedTypeOptions = organizationDao.findOrganizationTypes();
					if (tenantId != null) {
						TYPE_OPTIONS_CACHE.put(tenantId, loadedTypeOptions == null ? List.of() : loadedTypeOptions);
					}
					PerfLog.logDone("organizations.detail.types.dao", "tenantId=" + tenantId + " rows=" + (loadedTypeOptions == null ? 0 : loadedTypeOptions.size()) + " cacheHit=false", typesStarted);
				} else {
					PerfLog.log("organizations.detail.types.cache", "hit", "tenantId=" + tenantId + " rows=" + loadedTypeOptions.size());
				}
				final Organization loadedForUi = loaded;
				final List<OrganizationDao.OrganizationTypeRow> typeOptionsForUi = loadedTypeOptions == null ? List.of() : loadedTypeOptions;
				final boolean cacheHitForUi = cacheHit;
				Platform.runLater(() -> {
					setBusy(false);
					if (loadedForUi == null) {
						relatedCases = List.of();
						renderRelatedCases();
						setError("Organization not found.");
						return;
					}

					currentOrganization = loadedForUi;
					organizationTypeOptions = typeOptionsForUi;
					resetRelatedCaseControls();
					renderFromCurrent();
					clearError();
					PerfLog.logDone("organizations.detail.load", "phase=detailApplied organizationId=" + organizationId + " cacheHit=" + cacheHitForUi, loadStarted);
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
		long relatedStarted = PerfLog.start();
		if (organizationDao == null || organizationId == null) {
			relatedCases = List.of();
			renderRelatedCases();
			return;
		}

		dbExec.submit(() -> {
			try {
				PerfLog.log("organizations.relatedCases.dao", "start", "organizationId=" + organizationId);
				List<OrganizationDao.RelatedCaseRow> loadedRelatedCases = organizationDao.findRelatedCases(organizationId);
				int rowCount = loadedRelatedCases == null ? 0 : loadedRelatedCases.size();
				Platform.runLater(() -> {
					relatedCases = loadedRelatedCases == null ? List.of() : loadedRelatedCases;
					renderRelatedCases();
					PerfLog.logDone("organizations.relatedCases.load", "organizationId=" + organizationId + " rows=" + rowCount, relatedStarted);
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


	private void showOrganizationTextFieldDialog(String title, String label, String currentValue, boolean required, Button ownerButton, Consumer<String> onSave) {
		Dialog<String> dialog = new Dialog<>();
		AppDialogs.applySecondaryDialogShell(dialog, title);
		dialog.initOwner(dialogOwner(ownerButton));
		ButtonType saveType = new ButtonType("Save", ButtonData.OK_DONE);
		dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

		TextField field = new TextField(safeText(currentValue));
		Label error = new Label();
		error.setTextFill(Color.web("#b42318"));
		error.setVisible(false);
		error.setManaged(false);
		dialog.getDialogPane().setContent(new VBox(8, new Label(label), new Label("Current: " + displayCurrentValue(currentValue)), field, error));

		Node save = dialog.getDialogPane().lookupButton(saveType);
		save.addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
			if (required && safeText(field.getText()).trim().isBlank()) {
				error.setText(label + " is required.");
				error.setVisible(true);
				error.setManaged(true);
				e.consume();
			}
		});
		installUnsavedOrganizationDialogConfirmation(dialog, () -> !Objects.equals(safeText(currentValue), safeText(field.getText())));
		dialog.setResultConverter(button -> button == saveType ? field.getText() : null);
		dialog.showAndWait().ifPresent(onSave);
	}

	private void showOrganizationTextAreaDialog(String title, String label, String currentValue, Button ownerButton, Consumer<String> onSave) {
		Dialog<String> dialog = new Dialog<>();
		AppDialogs.applySecondaryDialogShell(dialog, title);
		dialog.initOwner(dialogOwner(ownerButton));
		ButtonType saveType = new ButtonType("Save", ButtonData.OK_DONE);
		dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

		TextArea area = new TextArea(safeText(currentValue));
		area.setPrefRowCount(8);
		area.setWrapText(true);
		dialog.getDialogPane().setContent(new VBox(8, new Label(label), new Label("Current value shown below."), area));
		installUnsavedOrganizationDialogConfirmation(dialog, () -> !Objects.equals(safeText(currentValue), safeText(area.getText())));
		dialog.setResultConverter(button -> button == saveType ? area.getText() : null);
		dialog.showAndWait().ifPresent(onSave);
	}

	private void showOrganizationTypeDialog(Integer currentTypeId, Button ownerButton, Consumer<Integer> onSave) {
		Dialog<OrganizationDao.OrganizationTypeRow> dialog = new Dialog<>();
		AppDialogs.applySecondaryDialogShell(dialog, "Edit Organization Type");
		dialog.initOwner(dialogOwner(ownerButton));
		ButtonType saveType = new ButtonType("Save", ButtonData.OK_DONE);
		dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

		ComboBox<OrganizationDao.OrganizationTypeRow> picker = new ComboBox<>(FXCollections.observableArrayList(organizationTypeOptions));
		picker.setMaxWidth(Double.MAX_VALUE);
		picker.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
			@Override
			protected void updateItem(OrganizationDao.OrganizationTypeRow item, boolean empty) {
				super.updateItem(item, empty);
				setText(empty || item == null ? null : fallback(item.name()));
			}
		});
		picker.setButtonCell(new javafx.scene.control.ListCell<>() {
			@Override
			protected void updateItem(OrganizationDao.OrganizationTypeRow item, boolean empty) {
				super.updateItem(item, empty);
				setText(empty || item == null ? null : fallback(item.name()));
			}
		});
		picker.getSelectionModel().select(findOrganizationTypeRow(currentTypeId));
		dialog.getDialogPane().setContent(new VBox(8,
				new Label("Organization Type"),
				new Label("Current: " + displayCurrentValue(currentOrganization == null ? null : currentOrganization.getOrganizationTypeName())),
				picker));
		installUnsavedOrganizationDialogConfirmation(dialog, () -> !Objects.equals(currentTypeId, selectedOrganizationTypeId(picker)));
		dialog.setResultConverter(button -> button == saveType ? picker.getSelectionModel().getSelectedItem() : null);
		Optional<OrganizationDao.OrganizationTypeRow> selected = dialog.showAndWait();
		selected.map(OrganizationDao.OrganizationTypeRow::organizationTypeId).ifPresent(onSave);
	}

	private void installUnsavedOrganizationDialogConfirmation(Dialog<?> dialog, java.util.function.BooleanSupplier hasChanges) {
		Node cancel = dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
		if (cancel == null) {
			return;
		}
		cancel.addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
			if (hasChanges == null || !hasChanges.getAsBoolean()) {
				return;
			}
			boolean confirmed = AppDialogs.showConfirmation(
					dialog.getOwner(),
					"Discard Changes?",
					"Discard unsaved changes?",
					"Canceling will discard the changes in this field.",
					"Discard Changes",
					AppDialogs.DialogActionKind.DANGER);
			if (!confirmed) {
				e.consume();
			}
		});
	}

	private void saveSingleOrganizationField(OrganizationField field, String value) {
		if (currentOrganization == null || field == null) {
			return;
		}
		Organization.Builder builder = copyCurrentOrganization();
		field.apply(builder, safeText(value));
		saveOrganizationSnapshot(builder.build());
	}

	private void saveOrganizationTypeField(Integer organizationTypeId) {
		if (currentOrganization == null) {
			return;
		}
		OrganizationDao.OrganizationTypeRow selected = findOrganizationTypeRow(organizationTypeId);
		saveOrganizationSnapshot(copyCurrentOrganization()
				.organizationTypeId(organizationTypeId)
				.organizationTypeName(selected == null ? currentOrganization.getOrganizationTypeName() : selected.name())
				.build());
	}

	private void saveOrganizationSnapshot(Organization updated) {
		long saveStarted = PerfLog.start();
		if (updated == null || organizationDao == null) {
			setError("Organization details are unavailable.");
			return;
		}

		setBusy(true);
		dbExec.submit(() -> {
			try {
				PerfLog.log("organizations.field.save", "start", "organizationId=" + updated.getId());
				organizationDao.update(updated);
				invalidateDetailCache(updated.getId());
				publishOrganizationUpdated(updated.getId());
				Organization reloaded = organizationDao.findById(updated.getId());
				Platform.runLater(() -> applySavedOrganization(reloaded, updated.getId(), saveStarted));
			} catch (Exception ex) {
				Platform.runLater(() -> {
					setBusy(false);
					setError("Failed to save organization.");
				});
			}
		});
	}

	private void applySavedOrganization(Organization reloaded, Integer updatedId, long saveStarted) {
		setBusy(false);
		if (reloaded == null) {
			setError("Organization could not be reloaded after save.");
			return;
		}
		currentOrganization = reloaded;
		cacheDetail(reloaded);
		pendingRemoteUpdate = false;
		hideRemoteUpdateBanner();
		renderFromCurrent();
		setEditMode(false);
		clearError();
		PerfLog.logDone("organizations.field.save", "phase=apply organizationId=" + updatedId, saveStarted);
	}

	private Organization.Builder copyCurrentOrganization() {
		return Organization.builder()
				.id(currentOrganization.getId())
				.shaleClientId(currentOrganization.getShaleClientId())
				.organizationTypeId(currentOrganization.getOrganizationTypeId())
				.organizationTypeName(currentOrganization.getOrganizationTypeName())
				.name(safeText(currentOrganization.getName()))
				.phone(safeText(currentOrganization.getPhone()))
				.fax(safeText(currentOrganization.getFax()))
				.email(safeText(currentOrganization.getEmail()))
				.website(safeText(currentOrganization.getWebsite()))
				.address1(safeText(currentOrganization.getAddress1()))
				.address2(safeText(currentOrganization.getAddress2()))
				.city(safeText(currentOrganization.getCity()))
				.state(safeText(currentOrganization.getState()))
				.postalCode(safeText(currentOrganization.getPostalCode()))
				.country(safeText(currentOrganization.getCountry()))
				.notes(safeText(currentOrganization.getNotes()))
				.deleted(currentOrganization.isDeleted())
				.createdAt(currentOrganization.getCreatedAt())
				.updatedAt(currentOrganization.getUpdatedAt());
	}

	private Integer selectedOrganizationTypeId(ComboBox<OrganizationDao.OrganizationTypeRow> picker) {
		OrganizationDao.OrganizationTypeRow selected = picker == null ? null : picker.getSelectionModel().getSelectedItem();
		return selected == null ? null : selected.organizationTypeId();
	}

	private String displayCurrentValue(String value) {
		String safe = safeText(value);
		return safe.isBlank() ? "—" : safe;
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
				.organizationTypeId(resolveSelectedOrganizationTypeId())
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

		saveOrganizationSnapshot(updated);
	}

	private void onDeleteOrganization() {
		long deleteStarted = PerfLog.start();
		if (organizationDao == null || currentOrganization == null || currentOrganization.getId() == null) {
			setError("Organization details are unavailable.");
			return;
		}
		if (!isAdminUser()) {
			setError("Only admin users can delete organizations.");
			return;
		}
		if (!confirmDeleteOrganization()) {
			return;
		}

		setBusy(true);
		dbExec.submit(() -> {
			try {
				PerfLog.log("organizations.delete", "start", "organizationId=" + currentOrganization.getId() + " tenantId=" + appState.getShaleClientId());
				boolean deleted = organizationDao.softDeleteOrganization(currentOrganization.getId(), appState.getShaleClientId());
				if (deleted) {
					invalidateDetailCache(currentOrganization.getId());
				}
				Platform.runLater(() -> {
					setBusy(false);
					if (!deleted) {
						setError("Organization could not be deleted.");
						return;
					}
					publishOrganizationUpdated(currentOrganization.getId());
					pendingRemoteUpdate = false;
					hideRemoteUpdateBanner();
					clearError();
					navigateAfterDelete();
					PerfLog.logDone("organizations.delete", "phase=apply organizationId=" + currentOrganization.getId(), deleteStarted);
				});
			} catch (Exception ex) {
				Platform.runLater(() -> {
					setBusy(false);
					setError("Failed to delete organization.");
				});
			}
		});
	}

	private boolean confirmDeleteOrganization() {
		Window owner = dialogOwner(deleteOrganizationButton);
		if (owner == null) {
			owner = dialogOwner(editButton);
		}
		return AppDialogs.showConfirmation(
				owner,
				"Delete Organization",
				"Delete this organization?",
				"This will remove it from active lists.",
				"Delete Organization",
				AppDialogs.DialogActionKind.DANGER);
	}

	private Window dialogOwner(Button button) {
		if (button != null && button.getScene() != null) {
			return button.getScene().getWindow();
		}
		return null;
	}

	private void navigateAfterDelete() {
		if (onOrganizationDeleted != null) {
			onOrganizationDeleted.run();
		}
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

	private void publishOrganizationUpdated(Integer organizationId) {
		if (organizationId == null || organizationId <= 0 || appState == null || runtimeBridge == null
				|| appState.getShaleClientId() == null || appState.getUserId() == null) {
			return;
		}

		try {
			int clientId = appState.getShaleClientId();
			int userId = appState.getUserId();
			runtimeBridge.publishOrganizationUpdated(organizationId, clientId, userId);
		} catch (Exception ex) {
			System.out.println("OrganizationUpdated publish skipped: " + ex.getMessage());
		}
	}

	private void renderFromCurrent() {
		long renderStarted = PerfLog.start();
		Organization o = currentOrganization;
		if (o == null) {
			return;
		}

		organizationTitleLabel.setText(fallback(o.getName()));
		String type = fallback(o.getOrganizationTypeName());
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
		refreshAdminActions();
		PerfLog.logDone("organizations.detail.render", "organizationId=" + (o == null ? null : o.getId()) + " fxThread=" + Platform.isFxApplicationThread(), renderStarted);
	}

	private void writeEditorsFromOrganization(Organization o) {
		if (typeEditor != null) {
			typeEditor.setItems(FXCollections.observableArrayList(organizationTypeOptions));
			typeEditor.getSelectionModel().select(findOrganizationTypeRow(o.getOrganizationTypeId()));
		}
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

	private Integer resolveSelectedOrganizationTypeId() {
		OrganizationDao.OrganizationTypeRow selected = typeEditor == null ? null : typeEditor.getSelectionModel().getSelectedItem();
		if (selected != null && selected.organizationTypeId() > 0) {
			return selected.organizationTypeId();
		}
		return currentOrganization == null ? null : currentOrganization.getOrganizationTypeId();
	}

	private OrganizationDao.OrganizationTypeRow findOrganizationTypeRow(Integer organizationTypeId) {
		if (organizationTypeId == null) {
			return null;
		}
		for (OrganizationDao.OrganizationTypeRow row : organizationTypeOptions) {
			if (row != null && row.organizationTypeId() == organizationTypeId.intValue()) {
				return row;
			}
		}
		return null;
	}

	private void renderRelatedCases() {
		if (!Platform.isFxApplicationThread()) {
			runOnFx(this::renderRelatedCases);
			return;
		}

		if (relatedCasesContainer == null) {
			return;
		}

		if (caseCardFactory == null) {
			caseCardFactory = new CaseCardFactory(onOpenCase);
		}
		String query = CaseListFilterSortSupport.normalizedQuery(relatedCasesSearchField);
		Comparator<OrganizationDao.RelatedCaseRow> comparator = CaseListFilterSortSupport.comparator(
				relatedCasesSortChoice,
				OrganizationDao.RelatedCaseRow::name,
				OrganizationDao.RelatedCaseRow::intakeDate,
				OrganizationDao.RelatedCaseRow::statuteOfLimitationsDate);

		List<Node> cards = relatedCases.stream()
				.filter(row -> CaseListFilterSortSupport.matchesQuery(query, row.name(), row.responsibleAttorneyName()))
				.sorted(comparator)
				.map(this::createRelatedCaseCardContainer)
				.toList();

		relatedCasesContainer.getChildren().setAll(cards);

		boolean empty = cards.isEmpty();
		if (relatedCasesEmptyLabel != null) {
			relatedCasesEmptyLabel.setVisible(empty);
			relatedCasesEmptyLabel.setManaged(empty);
			if (empty) {
				relatedCasesEmptyLabel.toFront();
				if (!query.isEmpty()) {
					relatedCasesEmptyLabel.setText("No related cases match your search");
				} else {
					relatedCasesEmptyLabel.setText("No related cases");
				}
			} else {
				relatedCasesContainer.toFront();
			}
		}
	}

	private Node createRelatedCaseCardContainer(OrganizationDao.RelatedCaseRow row) {
		Node card = caseCardFactory.create(new CaseCardModel(
				row.id(),
				row.name(),
				row.intakeDate(),
				row.statuteOfLimitationsDate(),
				row.tortClaimsNoticeDeadline(),
				row.responsibleAttorneyName(),
				row.responsibleAttorneyColor(),
				row.nonEngagementLetterSent(),
				row.primaryStatusName(),
				row.primaryStatusColor(),
				row.practiceAreaColor()
		), CaseCardFactory.Variant.FULL);
		if (card instanceof Region region) {
			region.setMaxWidth(Double.MAX_VALUE);
			region.setPrefWidth(380);
			region.setMaxWidth(420);
		}
		Label relationshipMeta = new Label(formatRelationshipMeta(row.partyRoleName(), row.side(), row.primary()));
		relationshipMeta.getStyleClass().add("muted");
		relationshipMeta.setWrapText(true);
		return new VBox(4, card, relationshipMeta);
	}

	private static String formatRelationshipMeta(String roleName, String side, boolean primary) {
		String role = safe(roleName).isBlank() ? "Relationship" : safe(roleName).trim();
		String sideLabel = safe(side).isBlank() ? "unclassified" : safe(side).trim();
		return primary ? role + " • " + sideLabel + " • primary" : role + " • " + sideLabel;
	}

	private Integer currentTenantId() {
		return appState == null ? null : appState.getShaleClientId();
	}

	private String detailCacheKey(Integer id) {
		Integer tenantId = currentTenantId();
		if (tenantId == null || tenantId <= 0 || id == null || id <= 0) {
			return null;
		}
		return tenantId + ":" + id;
	}

	private void cacheDetail(Organization organization) {
		if (organization == null) {
			return;
		}
		String cacheKey = detailCacheKey(organization.getId());
		if (cacheKey != null) {
			DETAIL_CACHE.put(cacheKey, organization);
		}
	}

	private void invalidateDetailCache(Integer id) {
		String cacheKey = detailCacheKey(id);
		if (cacheKey != null) {
			DETAIL_CACHE.remove(cacheKey);
			PerfLog.log("organizations.detail.cache", "invalidate", "organizationId=" + id + " tenantId=" + currentTenantId());
		}
	}

	private static String safe(String value) {
		return value == null ? "" : value;
	}

	private void resetRelatedCaseControls() {
		CaseListFilterSortSupport.resetControls(relatedCasesSearchField, relatedCasesSortChoice);
	}

	private void setEditMode(boolean enabled) {
		this.editMode = enabled;
		setVisibleManaged(editButton, false);
		setVisibleManaged(saveButton, enabled);
		setVisibleManaged(cancelButton, enabled);
		refreshAdminActions();

		toggleField(nameValue, nameEditor, enabled);
		toggleField(typeValue, typeEditor, enabled);
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
		if (deleteOrganizationButton != null) {
			deleteOrganizationButton.setDisable(busy);
		}
		setInlineEditButtonsDisabled(busy);
	}

	private void setInlineEditButtonsDisabled(boolean disabled) {
		for (Button button : List.of(editNameButton, editTypeButton, editPhoneButton, editFaxButton, editEmailButton,
				editWebsiteButton, editAddress1Button, editAddress2Button, editCityButton, editStateButton,
				editPostalCodeButton, editCountryButton, editNotesButton)) {
			if (button != null) {
				button.setDisable(disabled);
			}
		}
	}

	private void refreshAdminActions() {
		boolean showDelete = isAdminUser() && !editMode && currentOrganization != null;
		setVisibleManaged(deleteOrganizationButton, showDelete);
	}

	private boolean isAdminUser() {
		return appState != null && appState.isAdmin();
	}

	private static void toggleField(Label valueNode, javafx.scene.Node editorNode, boolean editMode) {
		if (editorNode instanceof TextInputControl textInput) {
			setVisibleManaged(valueNode, false);
			setVisibleManaged(editorNode, true);
			ReadOnlyTextDisplaySupport.apply(textInput, editMode);
			return;
		}
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

	private enum OrganizationField {
		NAME {
			@Override String textValue(Organization o) { return o == null ? "" : o.getName(); }
			@Override void apply(Organization.Builder builder, String value) { builder.name(value); }
		},
		PHONE {
			@Override String textValue(Organization o) { return o == null ? "" : o.getPhone(); }
			@Override void apply(Organization.Builder builder, String value) { builder.phone(value); }
		},
		FAX {
			@Override String textValue(Organization o) { return o == null ? "" : o.getFax(); }
			@Override void apply(Organization.Builder builder, String value) { builder.fax(value); }
		},
		EMAIL {
			@Override String textValue(Organization o) { return o == null ? "" : o.getEmail(); }
			@Override void apply(Organization.Builder builder, String value) { builder.email(value); }
		},
		WEBSITE {
			@Override String textValue(Organization o) { return o == null ? "" : o.getWebsite(); }
			@Override void apply(Organization.Builder builder, String value) { builder.website(value); }
		},
		ADDRESS1 {
			@Override String textValue(Organization o) { return o == null ? "" : o.getAddress1(); }
			@Override void apply(Organization.Builder builder, String value) { builder.address1(value); }
		},
		ADDRESS2 {
			@Override String textValue(Organization o) { return o == null ? "" : o.getAddress2(); }
			@Override void apply(Organization.Builder builder, String value) { builder.address2(value); }
		},
		CITY {
			@Override String textValue(Organization o) { return o == null ? "" : o.getCity(); }
			@Override void apply(Organization.Builder builder, String value) { builder.city(value); }
		},
		STATE {
			@Override String textValue(Organization o) { return o == null ? "" : o.getState(); }
			@Override void apply(Organization.Builder builder, String value) { builder.state(value); }
		},
		POSTAL_CODE {
			@Override String textValue(Organization o) { return o == null ? "" : o.getPostalCode(); }
			@Override void apply(Organization.Builder builder, String value) { builder.postalCode(value); }
		},
		COUNTRY {
			@Override String textValue(Organization o) { return o == null ? "" : o.getCountry(); }
			@Override void apply(Organization.Builder builder, String value) { builder.country(value); }
		},
		NOTES {
			@Override String textValue(Organization o) { return o == null ? "" : o.getNotes(); }
			@Override void apply(Organization.Builder builder, String value) { builder.notes(value); }
		};

		abstract String textValue(Organization organization);
		abstract void apply(Organization.Builder builder, String value);
	}

}
