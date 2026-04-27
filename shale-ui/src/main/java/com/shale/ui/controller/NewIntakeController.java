package com.shale.ui.controller;

import com.shale.data.dao.CaseDao;
import com.shale.data.dao.OrganizationDao;
import com.shale.ui.component.dialog.AppDialogs;
import com.shale.ui.component.factory.PracticeAreaCardFactory;
import com.shale.ui.component.factory.PracticeAreaCardFactory.PracticeAreaCardModel;
import com.shale.ui.component.factory.StatusCardFactory;
import com.shale.ui.component.factory.StatusCardFactory.StatusCardModel;
import com.shale.ui.controller.support.PartyAddWorkflowDialog;
import com.shale.ui.services.UiRuntimeBridge;
import com.shale.ui.state.AppState;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.geometry.Insets;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class NewIntakeController {

	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
	private static final DateTimeFormatter TIME_PARSE_FORMAT = DateTimeFormatter.ofPattern("H:mm");

	@FXML private Label validationLabel;

	@FXML private TextField caseNameField;
	@FXML private DatePicker dateOfIntakePicker;
	@FXML private TextField timeOfIntakeField;
	@FXML private CheckBox estateCaseCheckBox;

	@FXML private TextField clientFirstNameField;
	@FXML private TextField clientLastNameField;
	@FXML private TextField clientAddressField;
	@FXML private TextField clientPhoneField;
	@FXML private TextField clientEmailField;
	@FXML private DatePicker clientDateOfBirthPicker;
	@FXML private CheckBox clientDeceasedCheckBox;
	@FXML private TextArea clientConditionArea;

	@FXML private CheckBox callerIsClientCheckBox;
	@FXML private Label callerReuseLabel;
	@FXML private Label callerFirstNameRequiredIndicator;
	@FXML private Label callerLastNameRequiredIndicator;
	@FXML private Label callerPhoneRequiredIndicator;
	@FXML private GridPane callerFieldsGrid;
	@FXML private TextField callerFirstNameField;
	@FXML private TextField callerLastNameField;
	@FXML private TextField callerPhoneField;
	@FXML private TextField callerAddressField;
	@FXML private TextField callerEmailField;

	@FXML private StackPane practiceAreaHost;
	@FXML private Button selectPracticeAreaButton;
	@FXML private StackPane statusHost;
	@FXML private Button selectStatusButton;
	@FXML private TextArea descriptionArea;
	@FXML private TextArea summaryArea;
	@FXML private DatePicker dateMedicalNegligencePicker;
	@FXML private DatePicker dateMedicalNegligenceDiscoveredPicker;
	@FXML private DatePicker dateOfInjuryPicker;
	@FXML private DatePicker statuteOfLimitationsPicker;
	@FXML private DatePicker tortClaimsNoticePicker;
	@FXML private Button addPartyButton;
	@FXML private Label partiesEmptyLabel;
	@FXML private VBox partiesListBox;

	@FXML private Button cancelButton;
	@FXML private Button createIntakeButton;

	private AppState appState;
	private CaseDao caseDao;
	private OrganizationDao organizationDao;
	private UiRuntimeBridge runtimeBridge;
	private Stage stage;
	private Consumer<Integer> onCaseCreated;
	private boolean saving;
	private Boolean knownOnlineState;
	private final Consumer<UiRuntimeBridge.ConnectivityEvent> connectivityHandler = event -> {
		if (event != null) {
			knownOnlineState = event.online();
		}
	};

	private boolean caseNameManuallyOverridden;
	private boolean updatingCaseNameProgrammatically;

	private CaseDao.PracticeAreaRow selectedPracticeArea;
	private CaseDao.StatusRow selectedStatus;
	private PracticeAreaCardFactory practiceAreaCardFactory;
	private StatusCardFactory statusCardFactory;
	private List<PartyAddWorkflowDialog.AddPartyDraft> pendingParties = new java.util.ArrayList<>();
	private Map<Long, String> partyRoleLabelsById = Map.of();
	private Map<String, String> partySideLabelsByKey = Map.of();
	private IntakeFormSnapshot initialSnapshot;

	public void init(
			AppState appState,
			CaseDao caseDao,
			OrganizationDao organizationDao,
			UiRuntimeBridge runtimeBridge,
			Stage stage,
			Consumer<Integer> onCaseCreated) {
		this.appState = appState;
		this.caseDao = caseDao;
		this.organizationDao = organizationDao;
		this.runtimeBridge = runtimeBridge;
		this.stage = stage;
		this.onCaseCreated = onCaseCreated;
		if (this.runtimeBridge != null) {
			this.runtimeBridge.subscribeConnectivity(connectivityHandler);
		}
		if (this.stage != null) {
			this.stage.setOnHidden(event -> {
				if (this.runtimeBridge != null) {
					this.runtimeBridge.unsubscribeConnectivity(connectivityHandler);
				}
			});
			this.stage.setOnCloseRequest(event -> {
				if (!confirmDiscardIfDirty()) {
					event.consume();
				}
			});
		}
		Platform.runLater(this::preselectDefaultStatusIfAvailable);
		Platform.runLater(this::initializePartyMetadata);
	}

	@FXML
	private void initialize() {
		dateOfIntakePicker.setValue(LocalDate.now());
		timeOfIntakeField.setText(LocalTime.now().format(TIME_FORMAT));

		callerIsClientCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
			if (Boolean.TRUE.equals(newVal)) {
				copyCallerFieldsToClientIfEmpty();
			}
			applyCallerMode(Boolean.TRUE.equals(newVal));
			hideValidation();
		});
		applyCallerMode(false);

		clientFirstNameField.textProperty().addListener((obs, oldVal, newVal) -> autoGenerateCaseName());
		clientLastNameField.textProperty().addListener((obs, oldVal, newVal) -> autoGenerateCaseName());

		caseNameField.textProperty().addListener((obs, oldVal, newVal) -> {
			if (!updatingCaseNameProgrammatically) {
				caseNameManuallyOverridden = true;
			}
		});

		selectPracticeAreaButton.setOnAction(e -> onSelectPracticeArea());
		selectStatusButton.setOnAction(e -> onSelectStatus());
		if (addPartyButton != null) {
			addPartyButton.setOnAction(e -> onAddParty());
		}
		renderPracticeAreaMini(null, "—", null);
		renderStatusMini(null, "—", null);
		renderPendingParties();

		Platform.runLater(this::autoGenerateCaseName);
		Platform.runLater(this::captureInitialSnapshot);
	}

	private void initializePartyMetadata() {
		try {
			Map<Long, String> roleLabels = new LinkedHashMap<>();
			for (CaseDao.PartyRoleRow role : caseDao.listPartyRoles()) {
				roleLabels.put(role.id(), toPartyRoleLabel(role.name(), role.id()));
			}
			this.partyRoleLabelsById = Map.copyOf(roleLabels);
		} catch (RuntimeException ignored) {
			this.partyRoleLabelsById = Map.of();
		}
		try {
			Map<String, String> sideLabels = new LinkedHashMap<>();
			for (PartyAddWorkflowDialog.PartySideOption side : loadPartySideOptions()) {
				if (side.value() == null) continue;
				sideLabels.put(side.value().toLowerCase(), side.label());
			}
			this.partySideLabelsByKey = Map.copyOf(sideLabels);
		} catch (RuntimeException ignored) {
			this.partySideLabelsByKey = Map.of();
		}
	}

	private void onAddParty() {
		try {
			List<CaseDao.PartyRoleRow> partyRoles = caseDao.listPartyRoles();
			List<CaseDao.SelectableContactRow> contacts = caseDao.findSelectableContactsForTenant();
			List<CaseDao.SelectableOrganizationRow> organizations = caseDao.findSelectableOrganizationsForTenant();
			List<OrganizationDao.OrganizationTypeRow> organizationTypes = organizationDao == null ? List.of() : organizationDao.findOrganizationTypes();
			PartyAddWorkflowDialog.AddPartyDraft draft = PartyAddWorkflowDialog.show(
					stage,
					partyRoles,
					contacts,
					organizations,
					organizationTypes,
					loadPartySideOptions());
			if (draft == null) {
				return;
			}
			pendingParties.add(draft);
			renderPendingParties();
			hideValidation();
		} catch (RuntimeException ex) {
			showValidation("Unable to open Add Party flow.");
		}
	}

	private List<PartyAddWorkflowDialog.PartySideOption> loadPartySideOptions() {
		try {
			List<CaseDao.PartySideRow> sides = caseDao.listPartySides();
			List<PartyAddWorkflowDialog.PartySideOption> out = new java.util.ArrayList<>();
			for (CaseDao.PartySideRow side : sides) {
				if (side == null || side.systemKey() == null || side.systemKey().isBlank()) continue;
				String key = side.systemKey().trim().toLowerCase();
				String label = side.name() == null || side.name().isBlank() ? switch (key) {
					case "represented" -> "Represented";
					case "opposing" -> "Opposing";
					case "neutral" -> "Neutral";
					default -> "Unaffiliated";
				} : side.name().trim();
				out.add(new PartyAddWorkflowDialog.PartySideOption(label, key));
			}
			if (out.isEmpty()) {
				out.add(new PartyAddWorkflowDialog.PartySideOption("Represented", "represented"));
				out.add(new PartyAddWorkflowDialog.PartySideOption("Opposing", "opposing"));
				out.add(new PartyAddWorkflowDialog.PartySideOption("Neutral", "neutral"));
			}
			out.add(new PartyAddWorkflowDialog.PartySideOption("Unaffiliated", null));
			return out;
		} catch (RuntimeException ex) {
			return List.of(
					new PartyAddWorkflowDialog.PartySideOption("Represented", "represented"),
					new PartyAddWorkflowDialog.PartySideOption("Opposing", "opposing"),
					new PartyAddWorkflowDialog.PartySideOption("Neutral", "neutral"),
					new PartyAddWorkflowDialog.PartySideOption("Unaffiliated", null));
		}
	}

	private void renderPendingParties() {
		if (partiesListBox == null || partiesEmptyLabel == null) return;
		partiesListBox.getChildren().clear();
		partiesEmptyLabel.setManaged(pendingParties.isEmpty());
		partiesEmptyLabel.setVisible(pendingParties.isEmpty());
		for (int i = 0; i < pendingParties.size(); i++) {
			final int index = i;
			PartyAddWorkflowDialog.AddPartyDraft party = pendingParties.get(i);
			Label title = new Label(resolvePendingDisplayName(party));
			title.setStyle("-fx-font-weight: bold;");
			String roleLabel = partyRoleLabelsById.getOrDefault(party.partyRoleId(), "Role " + party.partyRoleId());
			String sideKey = safeTrim(party.side()).toLowerCase();
			String sideLabel = partySideLabelsByKey.getOrDefault(sideKey, sideKey.isBlank() ? "Unaffiliated" : sideKey);
			Label meta = new Label(roleLabel + " · " + sideLabel + (party.primary() ? " · Primary" : ""));
			meta.setStyle("-fx-opacity: 0.85;");
			VBox text = new VBox(4, title, meta);
			if (!safeTrim(party.notes()).isBlank()) {
				Label notes = new Label(safeTrim(party.notes()));
				notes.setWrapText(true);
				text.getChildren().add(notes);
			}
			Button removeButton = new Button("Remove");
			removeButton.getStyleClass().add("button-secondary");
			removeButton.setOnAction(e -> {
				pendingParties.remove(index);
				renderPendingParties();
			});
			Region spacer = new Region();
			HBox.setHgrow(spacer, Priority.ALWAYS);
			HBox actions = new HBox(8, spacer, removeButton);
			VBox card = new VBox(6, text, actions);
			card.setPadding(new Insets(10, 12, 10, 12));
			card.getStyleClass().add("secondary-panel");
			partiesListBox.getChildren().add(card);
		}
	}

	private String resolvePendingDisplayName(PartyAddWorkflowDialog.AddPartyDraft party) {
		if (party.createNew()) {
			if ("organization".equalsIgnoreCase(party.entityType())) {
				return safeTrim(party.organizationName()).isBlank() ? "New Organization" : safeTrim(party.organizationName());
			}
			String first = safeTrim(party.contactFirstName());
			String last = safeTrim(party.contactLastName());
			String value = (first + " " + last).trim();
			return value.isBlank() ? "New Contact" : value;
		}
		String explicitLabel = safeTrim(party.entityLabel());
		if (!explicitLabel.isBlank()) {
			return explicitLabel;
		}
		String labelPrefix = "organization".equalsIgnoreCase(party.entityType()) ? "Organization #" : "Contact #";
		return labelPrefix + (party.entityId() == null ? "—" : party.entityId());
	}

	private String toPartyRoleLabel(String roleName, long roleId) {
		String normalized = safeTrim(roleName).replace('_', ' ');
		if (normalized.isBlank()) {
			return "Role " + roleId;
		}
		String[] tokens = normalized.split("\\s+");
		for (int i = 0; i < tokens.length; i++) {
			String token = tokens[i];
			if (token.isBlank()) continue;
			tokens[i] = token.substring(0, 1).toUpperCase() + token.substring(1).toLowerCase();
		}
		return String.join(" ", tokens);
	}

	private void autoGenerateCaseName() {
		if (caseNameManuallyOverridden) {
			return;
		}
		String generated = buildCaseName(clientFirstNameField.getText(), clientLastNameField.getText());
		updatingCaseNameProgrammatically = true;
		caseNameField.setText(generated);
		updatingCaseNameProgrammatically = false;
	}

	private String buildCaseName(String first, String last) {
		String cleanFirst = safeTrim(first);
		String cleanLast = safeTrim(last);
		if (cleanFirst.isEmpty() && cleanLast.isEmpty()) {
			return "";
		}
		if (cleanLast.isEmpty()) {
			return cleanFirst;
		}
		if (cleanFirst.isEmpty()) {
			return cleanLast;
		}
		return cleanLast + ", " + cleanFirst;
	}

	private void applyCallerMode(boolean callerIsClient) {
		callerFieldsGrid.setDisable(callerIsClient);
		callerReuseLabel.setVisible(callerIsClient);
		callerReuseLabel.setManaged(callerIsClient);
		boolean callerFieldsRequired = !callerIsClient;
		setRequiredIndicator(callerFirstNameRequiredIndicator, callerFieldsRequired);
		setRequiredIndicator(callerLastNameRequiredIndicator, callerFieldsRequired);
		setRequiredIndicator(callerPhoneRequiredIndicator, callerFieldsRequired);
	}

	private void copyCallerFieldsToClientIfEmpty() {
		copyFieldIfSourcePresentAndTargetEmpty(callerFirstNameField, clientFirstNameField);
		copyFieldIfSourcePresentAndTargetEmpty(callerLastNameField, clientLastNameField);
		copyFieldIfSourcePresentAndTargetEmpty(callerPhoneField, clientPhoneField);
		copyFieldIfSourcePresentAndTargetEmpty(callerAddressField, clientAddressField);
		copyFieldIfSourcePresentAndTargetEmpty(callerEmailField, clientEmailField);
	}

	private void copyFieldIfSourcePresentAndTargetEmpty(TextField source, TextField target) {
		String sourceValue = safeTrim(source == null ? null : source.getText());
		String targetValue = safeTrim(target == null ? null : target.getText());
		if (!sourceValue.isEmpty() && targetValue.isEmpty() && target != null) {
			target.setText(sourceValue);
		}
	}

	private void onSelectPracticeArea() {
		try {
			List<CaseDao.PracticeAreaRow> areas = caseDao.listPracticeAreasForTenant(requireClientId());
			if (areas.isEmpty()) {
				showValidation("No practice areas are available for this tenant.");
				return;
			}

			Map<String, CaseDao.PracticeAreaRow> labelToRow = new LinkedHashMap<>();
			for (CaseDao.PracticeAreaRow area : areas) {
				String label = area.name() == null || area.name().isBlank() ? "Practice Area #" + area.id() : area.name();
				labelToRow.put(label, area);
			}

			String preselect = selectedPracticeArea == null ? labelToRow.keySet().iterator().next() : safeTrim(selectedPracticeArea.name());
			Optional<String> picked = showSecondaryChoiceDialog(
					"Change Practice Area",
					"Practice Area:",
					preselect,
					labelToRow.keySet());
			if (picked.isPresent()) {
				selectedPracticeArea = labelToRow.get(picked.get());
				renderPracticeAreaMini(selectedPracticeArea.id(), selectedPracticeArea.name(), selectedPracticeArea.color());
				hideValidation();
			}
		} catch (RuntimeException ex) {
			showValidation("Unable to load practice areas.");
		}
	}

	private void onSelectStatus() {
		try {
			List<CaseDao.StatusRow> statuses = caseDao.listStatusesForTenant(requireClientId());
			if (statuses.isEmpty()) {
				showValidation("No statuses are available for this tenant.");
				return;
			}

			Map<String, CaseDao.StatusRow> labelToRow = new LinkedHashMap<>();
			for (CaseDao.StatusRow status : statuses) {
				String label = status.name() == null || status.name().isBlank() ? "Status #" + status.id() : status.name();
				labelToRow.put(label, status);
			}

			String preselect = selectedStatus == null ? labelToRow.keySet().iterator().next() : safeTrim(selectedStatus.name());
			Optional<String> picked = showSecondaryChoiceDialog(
					"Change Status",
					"Status:",
					preselect,
					labelToRow.keySet());
			if (picked.isPresent()) {
				selectedStatus = labelToRow.get(picked.get());
				renderStatusMini(selectedStatus.id(), selectedStatus.name(), selectedStatus.color());
				hideValidation();
			}
		} catch (RuntimeException ex) {
			showValidation("Unable to load statuses.");
		}
	}

	private Optional<String> showSecondaryChoiceDialog(
			String title,
			String content,
			String preselect,
			java.util.Collection<String> options) {
		ChoiceDialog<String> dialog = new ChoiceDialog<>(preselect, options);
		dialog.setTitle(title);
		dialog.setHeaderText(null);
		dialog.setContentText(content);
		AppDialogs.applySecondaryDialogShell(dialog, title);
		Window owner = stage == null ? null : stage;
		if (owner != null) {
			dialog.initOwner(owner);
		}
		applyToolbarClassesToDialogButton(dialog.getDialogPane().lookupButton(javafx.scene.control.ButtonType.OK), "app-toolbar-button-primary");
		applyToolbarClassesToDialogButton(dialog.getDialogPane().lookupButton(javafx.scene.control.ButtonType.CANCEL), "app-toolbar-button-neutral");
		return dialog.showAndWait();
	}

	private void applyToolbarClassesToDialogButton(Node node, String variantClass) {
		if (!(node instanceof Button button)) {
			return;
		}
		if (!button.getStyleClass().contains("app-toolbar-button")) {
			button.getStyleClass().add("app-toolbar-button");
		}
		if (!button.getStyleClass().contains(variantClass)) {
			button.getStyleClass().add(variantClass);
		}
	}

	private void preselectDefaultStatusIfAvailable() {
		if (selectedStatus != null || caseDao == null || appState == null) {
			return;
		}
		try {
			List<CaseDao.StatusRow> statuses = caseDao.listStatusesForTenant(requireClientId());
			Optional<CaseDao.StatusRow> defaultOpenStatus = statuses.stream()
					.filter(Objects::nonNull)
					.filter(status -> !CaseDao.isTerminalStatus(status))
					.findFirst();
			if (defaultOpenStatus.isPresent()) {
				selectedStatus = defaultOpenStatus.get();
				renderStatusMini(selectedStatus.id(), selectedStatus.name(), selectedStatus.color());
				if (!hasUnsavedChanges()) {
					captureInitialSnapshot();
				}
			}
		} catch (RuntimeException ignored) {
			// If statuses cannot be loaded at initialization time, keep existing fallback (unselected).
		}
	}

	private void renderPracticeAreaMini(Integer practiceAreaId, String name, String colorCss) {
		if (practiceAreaHost == null)
			return;
		if (practiceAreaCardFactory == null) {
			practiceAreaCardFactory = new PracticeAreaCardFactory(id -> {
			});
		}
		PracticeAreaCardModel model = new PracticeAreaCardModel(
				practiceAreaId,
				(name == null || name.isBlank()) ? "—" : name,
				colorCss
		);
		practiceAreaHost.getChildren().setAll(practiceAreaCardFactory.create(model, PracticeAreaCardFactory.Variant.MINI));
	}

	private void renderStatusMini(Integer statusId, String statusName, String statusColorCss) {
		if (statusHost == null)
			return;
		if (statusCardFactory == null) {
			statusCardFactory = new StatusCardFactory(id -> {
			});
		}
		StatusCardModel model = new StatusCardModel(
				statusId,
				(statusName == null || statusName.isBlank()) ? "—" : statusName,
				null,
				statusColorCss
		);
		statusHost.getChildren().setAll(statusCardFactory.create(model, StatusCardFactory.Variant.MINI));
	}

	private int requireClientId() {
		Integer clientId = appState == null ? null : appState.getShaleClientId();
		if (clientId == null || clientId <= 0) {
			throw new RuntimeException("No tenant selected.");
		}
		return clientId;
	}

	@FXML
	private void onCreateIntake() {
		attemptCreateIntake(false);
	}

	private void attemptCreateIntake(boolean invokedFromOfflineRetry) {
		if (saving)
			return;
		System.out.println("[NewIntakeController] save clicked cachedOnlineState=" + knownOnlineState + " offlineRetry=" + invokedFromOfflineRetry);
		List<String> errors = validate();
		if (!errors.isEmpty()) {
			showValidation(errors.stream().collect(Collectors.joining("\n")));
			return;
		}

		boolean shouldBlockForOffline = shouldBlockCreateForOfflinePreflight();
		if (shouldBlockForOffline) {
			System.out.println("[NewIntakeController] create blocked by offline preflight.");
			showOfflinePreflightBlockedDialog();
			return;
		}

		setSaving(true);
		int tenantId = requireClientId();
		System.out.println("[NewIntakeController] create attempt started tenant=" + tenantId + " userId=" + (appState == null ? null : appState.getUserId()));
		try {
			CaseDao.NewIntakeCreateRequest request = new CaseDao.NewIntakeCreateRequest(
				requireClientId(),
				safeTrim(caseNameField.getText()),
				dateOfIntakePicker.getValue(),
				LocalTime.parse(safeTrim(timeOfIntakeField.getText()), TIME_PARSE_FORMAT),
				estateCaseCheckBox.isSelected(),
				selectedPracticeArea.id(),
				selectedStatus.id(),
				safeTrim(descriptionArea.getText()),
				safeTrim(summaryArea.getText()),
				dateMedicalNegligencePicker.getValue(),
				dateMedicalNegligenceDiscoveredPicker.getValue(),
				dateOfInjuryPicker.getValue(),
				statuteOfLimitationsPicker.getValue(),
				tortClaimsNoticePicker.getValue(),
				safeTrim(clientFirstNameField.getText()),
				safeTrim(clientLastNameField.getText()),
				safeTrim(clientAddressField.getText()),
				safeTrim(clientPhoneField.getText()),
				safeTrim(clientEmailField.getText()),
				clientDateOfBirthPicker.getValue(),
				clientDeceasedCheckBox.isSelected(),
				safeTrim(clientConditionArea.getText()),
				callerIsClientCheckBox.isSelected(),
				safeTrim(callerFirstNameField.getText()),
				safeTrim(callerLastNameField.getText()),
				safeTrim(callerPhoneField.getText()),
				safeTrim(callerAddressField.getText()),
				safeTrim(callerEmailField.getText()),
				pendingParties.stream().map(party -> new CaseDao.NewIntakePendingParty(
						party.entityType(),
						party.entityId(),
						party.partyRoleId(),
						party.side(),
						party.primary(),
						party.notes(),
						party.createNew(),
						party.contactFirstName(),
						party.contactLastName(),
						party.organizationName(),
						party.organizationTypeId())).toList(),
				appState == null ? null : appState.getUserId()
			);

			CaseDao.NewIntakeCreateResult result = caseDao.createIntake(request);
			captureInitialSnapshot();
			System.out.println("[NewIntakeController] create succeeded tenant=" + tenantId + " caseId=" + result.caseId());
			showSuccess("Intake created successfully.");
			if (stage != null)
				stage.close();
			if (onCaseCreated != null)
				onCaseCreated.accept(Math.toIntExact(result.caseId()));
		} catch (RuntimeException ex) {
			System.err.println("[NewIntakeController] DAO create failed tenant=" + tenantId + " error=" + ex.getMessage());
			ex.printStackTrace(System.err);
			showValidation("Unable to save intake. Your information has not been discarded. Please try again.");
		} finally {
			setSaving(false);
		}
	}

	private boolean shouldBlockCreateForOfflinePreflight() {
		if (!Boolean.FALSE.equals(knownOnlineState)) {
			System.out.println("[NewIntakeController] create allowed; cached connectivity is not offline.");
			return false;
		}

		Optional<Boolean> freshConnectivity = tryFreshConnectivityCheck();
		if (freshConnectivity.isPresent()) {
			boolean onlineNow = freshConnectivity.get();
			if (onlineNow) {
				knownOnlineState = true;
				System.out.println("[NewIntakeController] fresh connectivity check confirmed online; proceeding with create.");
				return false;
			}
			System.out.println("[NewIntakeController] fresh connectivity check confirmed offline; create blocked.");
			return true;
		}

		System.out.println("[NewIntakeController] fresh connectivity check unavailable; cached offline state treated as non-authoritative.");
		return false;
	}

	private Optional<Boolean> tryFreshConnectivityCheck() {
		if (runtimeBridge == null) {
			System.out.println("[NewIntakeController] fresh connectivity check skipped: runtime bridge unavailable.");
			return Optional.empty();
		}
		try {
			Optional<Boolean> result = runtimeBridge.recheckConnectivity();
			System.out.println("[NewIntakeController] fresh connectivity check result=" + result);
			return result;
		} catch (RuntimeException ex) {
			System.err.println("[NewIntakeController] fresh connectivity check failed: " + ex.getMessage());
			return Optional.empty();
		}
	}

	private void showOfflinePreflightBlockedDialog() {
		String message = "Shale could not confirm the connection, so the intake was not saved. Your information is still here. Reconnect and click Try Again, or keep editing.";
		showValidation(message);
		Optional<Boolean> decision = AppDialogs.showChoice(
				stage,
				"Connection Check Required",
				"Connection Check Required",
				message,
				List.of(
						AppDialogs.DialogAction.of("Try Again", true, AppDialogs.DialogActionKind.PRIMARY, true, false),
						AppDialogs.DialogAction.cancel("Keep Editing", false)));
		if (decision.orElse(false)) {
			attemptCreateIntake(true);
		}
	}

	@FXML
	private void onCancel() {
		requestClose();
	}

	private void requestClose() {
		if (stage != null && confirmDiscardIfDirty()) {
			stage.close();
		}
	}

	private boolean confirmDiscardIfDirty() {
		if (saving) {
			showValidation("Create Intake is in progress. Please wait.");
			return false;
		}
		if (!hasUnsavedChanges()) {
			return true;
		}
		Optional<Boolean> decision = AppDialogs.showChoice(
				stage,
				"Discard New Intake?",
				"Discard New Intake?",
				"You have unsaved information in this intake. Canceling will discard it. Do you want to continue?",
				List.of(
						AppDialogs.DialogAction.cancel("Keep Editing", false),
						AppDialogs.DialogAction.of("Discard", true, AppDialogs.DialogActionKind.DANGER, true, false)));
		return decision.orElse(false);
	}

	private boolean hasUnsavedChanges() {
		if (initialSnapshot == null) {
			return false;
		}
		return !initialSnapshot.equals(captureCurrentSnapshot());
	}

	private void captureInitialSnapshot() {
		this.initialSnapshot = captureCurrentSnapshot();
	}

	private IntakeFormSnapshot captureCurrentSnapshot() {
		return new IntakeFormSnapshot(
				safeTrim(caseNameField == null ? null : caseNameField.getText()),
				dateOfIntakePicker == null ? null : dateOfIntakePicker.getValue(),
				safeTrim(timeOfIntakeField == null ? null : timeOfIntakeField.getText()),
				estateCaseCheckBox != null && estateCaseCheckBox.isSelected(),
				safeTrim(clientFirstNameField == null ? null : clientFirstNameField.getText()),
				safeTrim(clientLastNameField == null ? null : clientLastNameField.getText()),
				safeTrim(clientAddressField == null ? null : clientAddressField.getText()),
				safeTrim(clientPhoneField == null ? null : clientPhoneField.getText()),
				safeTrim(clientEmailField == null ? null : clientEmailField.getText()),
				clientDateOfBirthPicker == null ? null : clientDateOfBirthPicker.getValue(),
				clientDeceasedCheckBox != null && clientDeceasedCheckBox.isSelected(),
				safeTrim(clientConditionArea == null ? null : clientConditionArea.getText()),
				callerIsClientCheckBox != null && callerIsClientCheckBox.isSelected(),
				safeTrim(callerFirstNameField == null ? null : callerFirstNameField.getText()),
				safeTrim(callerLastNameField == null ? null : callerLastNameField.getText()),
				safeTrim(callerPhoneField == null ? null : callerPhoneField.getText()),
				safeTrim(callerAddressField == null ? null : callerAddressField.getText()),
				safeTrim(callerEmailField == null ? null : callerEmailField.getText()),
				selectedPracticeArea == null ? null : selectedPracticeArea.id(),
				selectedStatus == null ? null : selectedStatus.id(),
				safeTrim(descriptionArea == null ? null : descriptionArea.getText()),
				safeTrim(summaryArea == null ? null : summaryArea.getText()),
				dateMedicalNegligencePicker == null ? null : dateMedicalNegligencePicker.getValue(),
				dateMedicalNegligenceDiscoveredPicker == null ? null : dateMedicalNegligenceDiscoveredPicker.getValue(),
				dateOfInjuryPicker == null ? null : dateOfInjuryPicker.getValue(),
				statuteOfLimitationsPicker == null ? null : statuteOfLimitationsPicker.getValue(),
				tortClaimsNoticePicker == null ? null : tortClaimsNoticePicker.getValue(),
				pendingParties == null ? List.of() : new ArrayList<>(pendingParties));
	}

	private void setSaving(boolean saving) {
		this.saving = saving;
		if (createIntakeButton != null)
			createIntakeButton.setDisable(saving);
		if (cancelButton != null)
			cancelButton.setDisable(saving);
		if (selectPracticeAreaButton != null)
			selectPracticeAreaButton.setDisable(saving);
		if (selectStatusButton != null)
			selectStatusButton.setDisable(saving);
		if (addPartyButton != null)
			addPartyButton.setDisable(saving);
	}

	private String firstMeaningfulMessage(Throwable throwable) {
		Throwable current = throwable;
		while (current != null) {
			String message = current.getMessage();
			if (message != null && !message.isBlank())
				return message;
			current = current.getCause();
		}
		return "Unexpected error";
	}

	private List<String> validate() {
		List<CaseDao.PracticeAreaRow> tenantPracticeAreas = loadTenantPracticeAreasForValidation();
		boolean hasTenantPracticeAreas = !tenantPracticeAreas.isEmpty();
		boolean selectedPracticeAreaValid = hasTenantPracticeAreas
				&& selectedPracticeArea != null
				&& tenantPracticeAreas.stream().anyMatch(area -> area.id() == selectedPracticeArea.id());
		return java.util.stream.Stream.of(
				required(caseNameField.getText(), "Case Name is required."),
				requiredDate(dateOfIntakePicker.getValue(), "Date of Intake is required."),
				validateIntakeTime(),
				required(clientFirstNameField.getText(), "Client First Name is required."),
				required(clientLastNameField.getText(), "Client Last Name is required."),
				required(clientPhoneField.getText(), "Client Phone Number is required."),
				!hasTenantPracticeAreas ? "No tenant practice areas are configured. Please contact support." : null,
				hasTenantPracticeAreas && !selectedPracticeAreaValid ? "Practice Area is required." : null,
				selectedStatus == null ? "Status is required." : null,
				callerRequiredWhenNotClient(callerFirstNameField.getText(), "Caller First Name is required when Caller is Client is unchecked."),
				callerRequiredWhenNotClient(callerLastNameField.getText(), "Caller Last Name is required when Caller is Client is unchecked."),
				callerRequiredWhenNotClient(callerPhoneField.getText(), "Caller Phone Number is required when Caller is Client is unchecked.")
		).filter(s -> s != null && !s.isBlank()).toList();
	}

	private List<CaseDao.PracticeAreaRow> loadTenantPracticeAreasForValidation() {
		try {
			return caseDao.listPracticeAreasForTenant(requireClientId());
		} catch (RuntimeException ex) {
			return List.of();
		}
	}

	private String required(String value, String message) {
		return safeTrim(value).isEmpty() ? message : null;
	}

	private String validateIntakeTime() {
		String value = safeTrim(timeOfIntakeField.getText());
		if (value.isEmpty()) {
			return "Time of Intake is required.";
		}
		try {
			LocalTime.parse(value, TIME_PARSE_FORMAT);
			return null;
		} catch (Exception e) {
			return "Time of Intake must use HH:mm format.";
		}
	}

	private String requiredDate(LocalDate value, String message) {
		return value == null ? message : null;
	}

	private String callerRequiredWhenNotClient(String value, String message) {
		if (callerIsClientCheckBox.isSelected()) {
			return null;
		}
		return safeTrim(value).isEmpty() ? message : null;
	}

	private void showValidation(String message) {
		validationLabel.setText(message);
		validationLabel.setTextFill(javafx.scene.paint.Paint.valueOf("#b42318"));
		validationLabel.setVisible(true);
		validationLabel.setManaged(true);
	}

	private void showSuccess(String message) {
		validationLabel.setText(message);
		validationLabel.setTextFill(javafx.scene.paint.Paint.valueOf("#157347"));
		validationLabel.setVisible(true);
		validationLabel.setManaged(true);
	}

	private void hideValidation() {
		validationLabel.setVisible(false);
		validationLabel.setManaged(false);
	}

	private void setRequiredIndicator(Label indicator, boolean visible) {
		if (indicator == null) {
			return;
		}
		indicator.setVisible(visible);
		indicator.setManaged(visible);
	}

	private static String safeTrim(String value) {
		return value == null ? "" : value.trim();
	}

	private record IntakeFormSnapshot(
			String caseName,
			LocalDate dateOfIntake,
			String timeOfIntake,
			boolean estateCase,
			String clientFirstName,
			String clientLastName,
			String clientAddress,
			String clientPhone,
			String clientEmail,
			LocalDate clientDateOfBirth,
			boolean clientDeceased,
			String clientCondition,
			boolean callerIsClient,
			String callerFirstName,
			String callerLastName,
			String callerPhone,
			String callerAddress,
			String callerEmail,
			Integer practiceAreaId,
			Integer statusId,
			String description,
			String summary,
			LocalDate medicalNegligenceDate,
			LocalDate medicalNegligenceDiscoveredDate,
			LocalDate injuryDate,
			LocalDate statuteOfLimitationsDate,
			LocalDate tortClaimsNoticeDate,
			List<PartyAddWorkflowDialog.AddPartyDraft> pendingParties) {
	}
}
