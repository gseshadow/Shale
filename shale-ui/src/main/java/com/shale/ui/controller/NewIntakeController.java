package com.shale.ui.controller;

import com.google.gson.Gson;
import com.shale.core.platform.AppPaths;
import com.shale.data.dao.CaseDao;
import com.shale.data.dao.OrganizationDao;
import com.shale.ui.component.dialog.AppDialogs;
import com.shale.ui.component.EnhancedTextArea;
import com.shale.ui.component.factory.PracticeAreaCardFactory;
import com.shale.ui.component.factory.PracticeAreaCardFactory.PracticeAreaCardModel;
import com.shale.ui.component.factory.StatusCardFactory;
import com.shale.ui.component.factory.StatusCardFactory.StatusCardModel;
import com.shale.ui.controller.support.PartyAddWorkflowDialog;
import com.shale.ui.controller.support.NewIntakeDatesConfiguration;
import com.shale.ui.controller.support.NewIntakeDatesConfiguration.ConfiguredDate;
import com.shale.ui.controller.support.NewIntakeDatesConfiguration.Selection;
import com.shale.core.dto.EffectiveCaseDateTypeDto;
import com.shale.core.dto.FormConfigurationDto;
import com.shale.core.model.CaseDateSemanticRole;
import com.shale.core.service.CaseServicePort;
import com.shale.core.service.FormConfigurationServicePort;
import com.shale.ui.util.ActionButtonFactory;
import com.shale.ui.util.ControlStyles;
import com.shale.ui.services.UiRuntimeBridge;
import com.shale.ui.services.LiveUpdateEvents;
import com.shale.ui.state.AppState;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.geometry.Insets;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.StringConverter;
import javafx.util.converter.LocalDateStringConverter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.sql.SQLException;
import java.util.stream.Collectors;

public final class NewIntakeController {

	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
	private static final DateTimeFormatter TIME_PARSE_FORMAT = DateTimeFormatter.ofPattern("H:mm");
	private static final Gson GSON = new Gson();
	private static final String DRAFTS_DIR = "drafts";
	private static final String INTAKE_DRAFT_PREFIX = "new-intake";
	private static final String ESTATE_CASE_NAME_PREFIX = "Estate of ";
	private static final long PRACTICE_AREA_PREFLIGHT_TIMEOUT_SECONDS = 5;
	private static final String INVALID_DATE_PROPERTY = "shale.newIntake.invalidDate";

	@FXML private Label validationLabel;

	@FXML private TextField caseNameField;
	@FXML private TextField timeOfIntakeField;
	@FXML private CheckBox estateCaseCheckBox;

	@FXML private TextField clientFirstNameField;
	@FXML private TextField clientLastNameField;
	@FXML private TextField clientAddressField;
	@FXML private TextField clientPhoneField;
	@FXML private TextField clientEmailField;
	@FXML private DatePicker clientDateOfBirthPicker;
	@FXML private CheckBox clientDeceasedCheckBox;
	@FXML private EnhancedTextArea clientConditionArea;

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
	@FXML private EnhancedTextArea descriptionArea;
	@FXML private EnhancedTextArea summaryArea;
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
	@FXML private VBox datesSection;
	@FXML private HBox datesAdminActions;
	@FXML private Label datesStatusLabel;
	@FXML private GridPane legacyDatesGrid;
	@FXML private VBox configuredDatesBox;
	@FXML private VBox datesCustomizationBox;

	private AppState appState;
	private CaseServicePort caseService;
	private FormConfigurationServicePort formConfigurationService;
	private FormConfigurationDto loadedDatesConfiguration;
	private List<EffectiveCaseDateTypeDto> effectiveDateTypes = List.of();
	private final Map<String, ConfiguredDateInput> configuredDateInputs = new LinkedHashMap<>();
	private final Map<String, LocalDate> preservedConfiguredDateValues = new LinkedHashMap<>();
	private Integer intakeCaseDateTypeId;
	private final List<Selection> stagedDateSelections = new ArrayList<>();
	private long datesLoadGeneration;
	private boolean datesViewClosed;
	private boolean datesViewAttached;
	private boolean datesReloadRequired;
	private final ExecutorService datesExecutor = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "new-intake-dates"); t.setDaemon(true); return t;
	});
	private CaseDao caseDao;
	private OrganizationDao organizationDao;
	private UiRuntimeBridge runtimeBridge;
	private Stage stage;
	private Consumer<Integer> onCaseCreated;
	private boolean saving;
	private boolean successfulCompletion;
	private final ExecutorService intakeSaveExecutor = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "new-intake-save");
		t.setDaemon(true);
		return t;
	});
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
	private RecoveryActionPresenter recoveryActionPresenter = (owner, title, header, content, actions, minWidth) ->
			AppDialogs.showChoice(owner, title, header, content, actions, minWidth);

	public void init(
			AppState appState,
			CaseDao caseDao,
			OrganizationDao organizationDao,
			UiRuntimeBridge runtimeBridge,
			Stage stage,
			Consumer<Integer> onCaseCreated) {
		init(appState, caseDao, organizationDao, runtimeBridge, stage, onCaseCreated, null, null);
	}

	public void init(AppState appState, CaseDao caseDao, OrganizationDao organizationDao,
			UiRuntimeBridge runtimeBridge, Stage stage, Consumer<Integer> onCaseCreated,
			CaseServicePort caseService, FormConfigurationServicePort formConfigurationService) {
		this.appState = appState;
		this.caseDao = caseDao;
		this.organizationDao = organizationDao;
		this.runtimeBridge = runtimeBridge;
		this.stage = stage;
		this.onCaseCreated = onCaseCreated;
		this.caseService = caseService;
		this.formConfigurationService = formConfigurationService;
		if (this.runtimeBridge != null) {
			this.runtimeBridge.subscribeConnectivity(connectivityHandler);
		}
		if (this.stage != null) {
			this.stage.setOnHidden(event -> {
				datesViewClosed = true;
				datesLoadGeneration++;
				if (this.runtimeBridge != null) {
					this.runtimeBridge.unsubscribeConnectivity(connectivityHandler);
				}
				intakeSaveExecutor.shutdownNow();
				datesExecutor.shutdownNow();
			});
			this.stage.setOnCloseRequest(event -> {
				if (!mayCloseIntake()) {
					event.consume();
				}
			});
		}
		Platform.runLater(this::preselectDefaultStatusIfAvailable);
		Platform.runLater(this::initializePartyMetadata);
		Platform.runLater(this::offerDraftRestoreIfPresent);
		configureDatesAuthorization();
		loadDatesConfiguration();
	}

	@FXML
	private void initialize() {
		datesSection.sceneProperty().addListener((observable, oldScene, newScene) -> {
			if (newScene != null) datesViewAttached = true;
		});
		ControlStyles.apply(cancelButton, ControlStyles.Purpose.SECONDARY);
		ControlStyles.apply(createIntakeButton, ControlStyles.Purpose.PRIMARY);
		ControlStyles.apply(selectPracticeAreaButton, ControlStyles.Purpose.SECONDARY, ControlStyles.Size.SMALL);
		ControlStyles.apply(selectStatusButton, ControlStyles.Purpose.SECONDARY, ControlStyles.Size.SMALL);
		if (addPartyButton != null) ControlStyles.apply(addPartyButton, ControlStyles.Purpose.SECONDARY, ControlStyles.Size.SMALL);
		timeOfIntakeField.setText(LocalTime.now().format(TIME_FORMAT));
		List.of(clientDateOfBirthPicker, dateMedicalNegligencePicker,
				dateMedicalNegligenceDiscoveredPicker, dateOfInjuryPicker,
				statuteOfLimitationsPicker, tortClaimsNoticePicker).forEach(NewIntakeController::configureDatePicker);

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
		clientDeceasedCheckBox.selectedProperty().bindBidirectional(estateCaseCheckBox.selectedProperty());
		estateCaseCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
			caseNameManuallyOverridden = false;
			autoGenerateCaseName();
		});

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

	private void configureDatesAuthorization() {
		datesAdminActions.getChildren().clear();
		if (!isAuthorizedDatesAdmin()) return; // non-admins never receive an action node or handler
		Button customize = ActionButtonFactory.semantic("Customize Form", e -> enterDatesCustomization(),
				ControlStyles.Purpose.SECONDARY, ControlStyles.Size.SMALL);
		datesAdminActions.getChildren().add(customize);
	}

	private boolean isAuthorizedDatesAdmin() {
		return appState != null && appState.isAdmin() && appState.getShaleClientId() != null
				&& appState.getUserId() != null;
	}

	private void loadDatesConfiguration() {
		if (caseService == null || formConfigurationService == null || appState == null
				|| appState.getShaleClientId() == null || appState.getUserId() == null) return;
		long generation = ++datesLoadGeneration;
		int tenant = appState.getShaleClientId(), actor = appState.getUserId();
		datesStatusLabel.setText("Loading saved date fields…");
		CompletableFuture.supplyAsync(() -> new DatesLoad(
				formConfigurationService.load(tenant, actor, NewIntakeDatesConfiguration.FORM_KEY),
				caseService.listEffectiveCaseDateTypes(tenant, actor),
				caseService.resolveEffectiveCaseDateTypeId(tenant, actor, CaseDateSemanticRole.INTAKE)), datesExecutor)
				.whenComplete((result, failure) -> Platform.runLater(() -> {
					if (isDatesResultStale(generation)) return;
					if (failure != null) {
						legacyDatesGrid.setVisible(false); legacyDatesGrid.setManaged(false);
						configuredDatesBox.getChildren().clear(); configuredDateInputs.clear();
						datesStatusLabel.setText("Date fields could not be loaded. Reload New Intake before entering dates.");
						return;
					}
					loadedDatesConfiguration = result.configuration();
					effectiveDateTypes = result.types();
					intakeCaseDateTypeId = result.intakeCaseDateTypeId();
					datesReloadRequired = false;
					renderDatesNormalMode();
				}));
	}

	private void renderDatesNormalMode() {
		datesCustomizationBox.getChildren().clear();
		datesCustomizationBox.setVisible(false); datesCustomizationBox.setManaged(false);
		configuredDatesBox.getChildren().clear(); configuredDateInputs.clear();
		List<ConfiguredDate> fields = NewIntakeDatesConfiguration.renderable(loadedDatesConfiguration, effectiveDateTypes);
		boolean saved = loadedDatesConfiguration != null && loadedDatesConfiguration.id() != 0;
		legacyDatesGrid.setVisible(false); legacyDatesGrid.setManaged(false);
		configuredDatesBox.setVisible(true); configuredDatesBox.setManaged(true);
		datesStatusLabel.setText(fields.isEmpty() ? "No date fields are configured for this form."
				: saved ? "Date fields configured for this tenant." : "Using active Case Date Types for this tenant.");
		for (ConfiguredDate field : fields) {
			Label label = new Label(field.type().name() + (field.required() ? " *" : ""));
			DatePicker picker = ControlStyles.formControl(new DatePicker());
			configureDatePicker(picker);
			String fieldKey = field.fieldKey();
			LocalDate initialValue = NewIntakeDatesConfiguration.initialValue(fieldKey, field.type().id(),
					intakeCaseDateTypeId, LocalDate.now(), preservedConfiguredDateValues);
			picker.setValue(initialValue);
			picker.valueProperty().addListener((observable, oldValue, newValue) -> {
				preservedConfiguredDateValues.put(fieldKey, newValue);
				if (newValue != null) ControlStyles.setInvalid(picker, false);
			});
			HBox row = new HBox(16, label, picker); row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
			HBox.setHgrow(picker, Priority.ALWAYS); picker.setMaxWidth(Double.MAX_VALUE);
			configuredDatesBox.getChildren().add(row);
			configuredDateInputs.put(field.fieldKey(), new ConfiguredDateInput(field.type().id(), field.fieldKey(), field.required(), picker));
		}
	}

	private void enterDatesCustomization() {
		if (!isAuthorizedDatesAdmin()) return;
		if (datesReloadRequired) { loadDatesConfiguration(); return; }
		stagedDateSelections.clear();
		stagedDateSelections.addAll(NewIntakeDatesConfiguration.selections(loadedDatesConfiguration, effectiveDateTypes));
		renderDatesCustomization();
	}

	private void renderDatesCustomization() {
		legacyDatesGrid.setVisible(false); legacyDatesGrid.setManaged(false);
		configuredDatesBox.setVisible(false); configuredDatesBox.setManaged(false);
		datesCustomizationBox.getChildren().clear();
		datesCustomizationBox.setVisible(true); datesCustomizationBox.setManaged(true);
		datesStatusLabel.setText("Choose and explicitly order the date fields shown on New Intake.");
		ComboBox<EffectiveCaseDateTypeDto> selector = ControlStyles.formControl(new ComboBox<>());
		selector.setPromptText("Select an active case-date type");
		selector.getItems().setAll(effectiveDateTypes.stream().filter(t -> stagedDateSelections.stream().noneMatch(s -> s.type().id() == t.id())).toList());
		selector.setConverter(new javafx.util.StringConverter<>() {
			@Override public String toString(EffectiveCaseDateTypeDto value) { return value == null ? "" : value.name(); }
			@Override public EffectiveCaseDateTypeDto fromString(String value) { return null; }
		});
		Button add = ActionButtonFactory.semantic("Add", e -> { if (selector.getValue() != null) { stagedDateSelections.add(new Selection(selector.getValue(), false)); renderDatesCustomization(); } }, ControlStyles.Purpose.SECONDARY, ControlStyles.Size.SMALL);
		datesCustomizationBox.getChildren().add(new HBox(8, selector, add));
		for (int i = 0; i < stagedDateSelections.size(); i++) {
			int index = i; Selection selection = stagedDateSelections.get(i);
			Label name = new Label(selection.type().name()); HBox.setHgrow(name, Priority.ALWAYS); name.setMaxWidth(Double.MAX_VALUE);
			CheckBox required = ControlStyles.formControl(new CheckBox("Required"));
			required.setSelected(selection.required());
			required.setAccessibleHelp("Choose whether " + selection.type().name() + " must be completed on New Intake.");
			required.selectedProperty().addListener((observable, oldValue, newValue) ->
					stagedDateSelections.set(index, NewIntakeDatesConfiguration.withRequired(selection, newValue)));
			Button up = ActionButtonFactory.semantic("Up", e -> moveDateSelection(index, -1), ControlStyles.Purpose.GHOST, ControlStyles.Size.SMALL);
			Button down = ActionButtonFactory.semantic("Down", e -> moveDateSelection(index, 1), ControlStyles.Purpose.GHOST, ControlStyles.Size.SMALL);
			Button remove = ActionButtonFactory.semantic("Remove", e -> { stagedDateSelections.remove(index); renderDatesCustomization(); }, ControlStyles.Purpose.GHOST, ControlStyles.Size.SMALL);
			up.setDisable(i == 0); down.setDisable(i == stagedDateSelections.size() - 1);
			datesCustomizationBox.getChildren().add(new HBox(8, name, required, up, down, remove));
		}
		Button save = ActionButtonFactory.semantic("Save", e -> saveDatesCustomization(), ControlStyles.Purpose.PRIMARY, ControlStyles.Size.STANDARD);
		Button cancel = ActionButtonFactory.semantic("Cancel", e -> cancelDatesCustomization(), ControlStyles.Purpose.SECONDARY, ControlStyles.Size.STANDARD);
		datesCustomizationBox.getChildren().add(new HBox(8, save, cancel));
	}

	private void cancelDatesCustomization() {
		stagedDateSelections.clear();
		renderDatesNormalMode();
	}

	private void moveDateSelection(int index, int delta) {
		int target = index + delta; if (target < 0 || target >= stagedDateSelections.size()) return;
		java.util.Collections.swap(stagedDateSelections, index, target); renderDatesCustomization();
	}

	private void saveDatesCustomization() {
		if (!isAuthorizedDatesAdmin() || datesReloadRequired || formConfigurationService == null) return;
		int tenant = appState.getShaleClientId(), actor = appState.getUserId();
		byte[] rowVer = loadedDatesConfiguration == null ? null : loadedDatesConfiguration.rowVer();
		var command = new FormConfigurationServicePort.ReplaceCommand(tenant, actor,
				NewIntakeDatesConfiguration.FORM_KEY,
				List.of(NewIntakeDatesConfiguration.draft(List.copyOf(stagedDateSelections))), rowVer);
		datesCustomizationBox.setDisable(true);
		long generation = ++datesLoadGeneration;
		CompletableFuture.supplyAsync(() -> formConfigurationService.replace(command), datesExecutor)
				.whenComplete((saved, failure) -> Platform.runLater(() -> {
					if (isDatesResultStale(generation)) return;
					datesCustomizationBox.setDisable(false);
					if (failure != null) {
						datesReloadRequired = isConfigurationConflict(failure);
						String message = datesReloadRequired
								? "The form configuration changed elsewhere. Reload it explicitly before editing again."
								: "The form configuration could not be saved.";
						datesStatusLabel.setText(message);
						AppDialogs.showError(stage, "Customize New Intake", message);
						if (datesReloadRequired) configureReloadAction();
						return;
					}
					loadedDatesConfiguration = saved; renderDatesNormalMode();
				}));
	}

	private void configureReloadAction() {
		datesAdminActions.getChildren().clear();
		if (!isAuthorizedDatesAdmin()) return;
		datesAdminActions.getChildren().add(ActionButtonFactory.semantic("Reload Configuration", e -> {
			configureDatesAuthorization(); loadDatesConfiguration();
		}, ControlStyles.Purpose.SECONDARY, ControlStyles.Size.SMALL));
	}

	static boolean isConfigurationConflict(Throwable failure) {
		for (Throwable current = failure; current != null; current = current.getCause())
			if (current instanceof IllegalStateException && "Form configuration changed.".equals(current.getMessage())) return true;
		return false;
	}

	private boolean isDatesResultStale(long generation) {
		return datesViewClosed || generation != datesLoadGeneration
				|| (datesViewAttached && datesSection.getScene() == null);
	}

	private record DatesLoad(FormConfigurationDto configuration, List<EffectiveCaseDateTypeDto> types,
			int intakeCaseDateTypeId) {}
	public record ConfiguredDateInput(int caseDateTypeId, String fieldKey, boolean required, DatePicker input) {
		public LocalDate value() { return input.getValue(); }
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
		String generated = buildCaseName(clientFirstNameField.getText(), clientLastNameField.getText(),
				estateCaseCheckBox.isSelected());
		updatingCaseNameProgrammatically = true;
		caseNameField.setText(generated);
		updatingCaseNameProgrammatically = false;
	}

	static String buildCaseName(String first, String last, boolean estateCase) {
		String cleanFirst = safeTrim(first);
		String cleanLast = safeTrim(last);
		if (cleanFirst.isEmpty() && cleanLast.isEmpty()) {
			return "";
		}
		String normalName;
		if (cleanLast.isEmpty()) {
			normalName = cleanFirst;
		} else if (cleanFirst.isEmpty()) {
			normalName = cleanLast;
		} else {
			normalName = cleanLast + ", " + cleanFirst;
		}
		return estateCase ? ESTATE_CASE_NAME_PREFIX + normalName : normalName;
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
		if (datesReloadRequired) {
			showValidation("The intake form configuration changed. Reload the form before submitting again.");
			return;
		}
		System.out.println("[NewIntakeController] save clicked cachedOnlineState=" + knownOnlineState + " offlineRetry=" + invokedFromOfflineRetry);
		List<String> errors = validateRequiredFields();
		if (!errors.isEmpty()) {
			showValidation(errors.stream().collect(Collectors.joining("\n")));
			focusFirstMissingConfiguredDate();
			return;
		}

		boolean shouldBlockForOffline = shouldBlockCreateForOfflinePreflight();
		if (shouldBlockForOffline) {
			System.out.println("[NewIntakeController] create blocked by offline preflight.");
			showOfflinePreflightBlockedDialog();
			return;
		}

		int tenantId = requireClientId();
		setSaving(true);
		CompletableFuture
				.supplyAsync(this::loadTenantPracticeAreasForValidation, intakeSaveExecutor)
				.orTimeout(PRACTICE_AREA_PREFLIGHT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
				.whenComplete((practiceAreaState, throwable) -> Platform.runLater(() -> {
					if (throwable != null) {
						RuntimeException ex = normalizePreflightException(throwable);
						logPracticeAreaPreflightFailure(tenantId, ex);
						handlePracticeAreaPreflightFailure(ex);
						return;
					}
					if (practiceAreaState.unverifiedDueToConnectivity()) {
						RuntimeException ex = new RuntimeException(practiceAreaConnectivityMessage());
						logPracticeAreaPreflightFailure(tenantId, ex);
						handlePracticeAreaPreflightFailure(ex);
						return;
					}
					List<String> practiceAreaErrors = validatePracticeAreaSelection(practiceAreaState);
					if (!practiceAreaErrors.isEmpty()) {
						setSaving(false);
						showValidation(practiceAreaErrors.stream().collect(Collectors.joining("\n")));
						return;
					}
					startPrimaryIntakeSave(tenantId);
				}));
	}

	private void startPrimaryIntakeSave(int tenantId) {
		CaseDao.NewIntakeCreateRequest request = buildCreateRequest();
		intakeSaveExecutor.submit(() -> {
			try {
				List<CaseDao.IntakeDuplicateCase> duplicates=caseDao.findIntakeDuplicateCases(tenantId,request.caseName());
				Platform.runLater(() -> resolveDuplicateAndSave(tenantId,request,duplicates));
			} catch (RuntimeException ex) {
				logCreateFailure(tenantId, ex);
				Platform.runLater(() -> handleCreateFailure(ex));
			}
		});
	}

	private void resolveDuplicateAndSave(int tenantId, CaseDao.NewIntakeCreateRequest request,
			List<CaseDao.IntakeDuplicateCase> duplicates) {
		if (duplicates == null || duplicates.isEmpty()) { submitIntakeMutation(tenantId,request,null); return; }
		List<AppDialogs.DialogAction<DuplicateChoice>> actions=new ArrayList<>();
		for(CaseDao.IntakeDuplicateCase duplicate:duplicates) actions.add(AppDialogs.DialogAction.of(
				mergeActionLabel(duplicate,duplicates.size()),new DuplicateChoice(true,duplicate.caseId()),
				AppDialogs.DialogActionKind.PRIMARY,false,false));
		actions.add(AppDialogs.DialogAction.of("Create Separate Case",new DuplicateChoice(false,0),AppDialogs.DialogActionKind.SECONDARY,false,false));
		actions.add(AppDialogs.DialogAction.cancel("Cancel",null));
		String details=duplicates.stream().map(NewIntakeController::duplicateDescription).collect(Collectors.joining("\n"));
		Optional<DuplicateChoice> choice=AppDialogs.showChoice(stage,"Possible Duplicate Case",
				"A Case with this name already exists.",details,actions,Math.max(520,duplicates.size()>1?640:520));
		if(choice.isEmpty()||choice.get()==null){setSaving(false);return;}
		DuplicateChoice selected=choice.get(); submitIntakeMutation(tenantId,request,selected.merge()?selected.caseId():null);
	}

	private void submitIntakeMutation(int tenantId,CaseDao.NewIntakeCreateRequest request,Long mergeCaseId){
		intakeSaveExecutor.submit(()->{try{CaseDao.NewIntakeCreateResult result=mergeCaseId==null?caseDao.createIntake(request):caseDao.mergeIntake(mergeCaseId,request);
			Platform.runLater(()->handleCreateSuccess(tenantId,result,mergeCaseId!=null));}catch(RuntimeException ex){logCreateFailure(tenantId,ex);Platform.runLater(()->handleCreateFailure(ex));}});
	}

	record DuplicateChoice(boolean merge,long caseId) { }
	static String mergeActionLabel(CaseDao.IntakeDuplicateCase duplicate,int count){return count==1?"Merge Into Existing Case":"Merge Into Case "+caseReference(duplicate);}
	static String duplicateDescription(CaseDao.IntakeDuplicateCase d){return String.join(" · ",List.of(
			nonblank(d.caseName(),"Unnamed Case"),caseReference(d),"Status: "+nonblank(d.status(),"Unknown"),
			"Client: "+nonblank(d.clientName(),"Not shown"),"Intake: "+(d.intakeDate()==null?"Not recorded":d.intakeDate().toString())));}
	private static String caseReference(CaseDao.IntakeDuplicateCase d){return nonblank(d.caseNumber(),"Case ID "+d.caseId());}
	private static String nonblank(String value,String fallback){return value==null||value.isBlank()?fallback:value.trim();}

	private RuntimeException normalizePreflightException(Throwable throwable) {
		Throwable current = throwable;
		if (current instanceof CompletionException && current.getCause() != null) {
			current = current.getCause();
		}
		if (current instanceof TimeoutException) {
			return new RuntimeException("Practice-area database verification timed out after "
					+ PRACTICE_AREA_PREFLIGHT_TIMEOUT_SECONDS + " seconds.", current);
		}
		return current instanceof RuntimeException runtimeException
				? runtimeException
				: new RuntimeException(current);
	}

	private void logPracticeAreaPreflightFailure(int tenantId, RuntimeException ex) {
		System.err.println("[NewIntakeController] practice area preflight failed " + saveContext(tenantId));
		ex.printStackTrace(System.err);
	}

	private void handlePracticeAreaPreflightFailure(RuntimeException ex) {
		knownOnlineState = Boolean.FALSE;
		String message = practiceAreaConnectivityMessage();
		showValidation(message);
		setSaving(false);
		offerCreateFailureActions(message);
	}

	private CaseDao.NewIntakeCreateRequest buildCreateRequest() {
		FormConfigurationDto configuration = loadedDatesConfiguration;
		long configurationId = configuration == null ? 0 : configuration.id();
		byte[] configurationRowVer = configuration == null ? null : configuration.rowVer();
		ConfiguredDateInput intakeInput = intakeCaseDateTypeId == null ? null
				: configuredDateInputs.values().stream()
						.filter(input -> input.caseDateTypeId() == intakeCaseDateTypeId.intValue())
						.findFirst().orElse(null);
		return new CaseDao.NewIntakeCreateRequest(
				requireClientId(),
				safeTrim(caseNameField.getText()),
				intakeInput == null ? null : intakeInput.value(),
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
				appState == null ? null : appState.getUserId(),
				configurationId,
				configurationRowVer == null ? null : configurationRowVer.clone(),
				configuredDateInputs.values().stream().map(input -> new CaseDao.ConfiguredDateValue(
						input.fieldKey(), input.caseDateTypeId(), input.required(), input.value())).toList()
		);
	}

	private void handleCreateSuccess(int tenantId, CaseDao.NewIntakeCreateResult result) { handleCreateSuccess(tenantId,result,false); }

	private void handleCreateSuccess(int tenantId, CaseDao.NewIntakeCreateResult result, boolean merged) {
		successfulCompletion = true;
		deleteLocalDraftIfPresent();
		System.out.println("[NewIntakeController] create succeeded tenant=" + tenantId + " caseId=" + result.caseId());
		if (result.createdCaseDateCount() > 0 && runtimeBridge != null && appState != null
				&& appState.getUserId() != null) {
			runtimeBridge.publishCaseDatesChanged(result.caseId(), tenantId, appState.getUserId(),
					LiveUpdateEvents.CHANGE_CREATED);
		}
		showSuccess(merged ? "Intake information was added to the existing Case." : "Intake created successfully.");
		setSaving(false);
		if (stage != null)
			stage.close();
		if (onCaseCreated != null)
			onCaseCreated.accept(Math.toIntExact(result.caseId()));
	}

	private void handleCreateFailure(RuntimeException ex) {
		knownOnlineState = isConnectivityFailure(ex) ? Boolean.FALSE : knownOnlineState;
		boolean configurationFailure = ex instanceof CaseDao.IntakeConfigurationException;
		if (configurationFailure) datesReloadRequired = true;
		String message = isConnectivityFailure(ex)
				? "Shale could not connect to the database. Your intake was not saved. You can save a local backup and retry later."
				: configurationFailure ? ex.getMessage()
				: "Unable to save intake. Your information has not been discarded. Please try again.";
		showValidation(message);
		setSaving(false);
		offerCreateFailureActions(message);
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
		Optional<SaveBlockedAction> decision = showRecoveryActionDialog("Connection Check Required", message);
		SaveBlockedAction action = decision.orElse(SaveBlockedAction.KEEP_EDITING);
		if (action == SaveBlockedAction.TRY_AGAIN) {
			attemptCreateIntake(true);
		} else if (action == SaveBlockedAction.SAVE_DRAFT) {
			saveDraftLocally();
		} else if (action == SaveBlockedAction.COPY_TEXT) {
			copyIntakeTextToClipboard();
		}
	}

	private void offerCreateFailureActions(String message) {
		Optional<SaveBlockedAction> decision = showRecoveryActionDialog("Save Intake Failed", message);
		SaveBlockedAction action = decision.orElse(SaveBlockedAction.KEEP_EDITING);
		if (action == SaveBlockedAction.TRY_AGAIN) {
			attemptCreateIntake(true);
		} else if (action == SaveBlockedAction.SAVE_DRAFT) {
			saveDraftLocally();
		} else if (action == SaveBlockedAction.COPY_TEXT) {
			copyIntakeTextToClipboard();
		}
	}

	private Optional<SaveBlockedAction> showRecoveryActionDialog(String title, String message) {
		return recoveryActionPresenter.show(
				stage,
				title,
				title,
				message,
				recoveryDialogActions(),
				660);
	}

	private List<AppDialogs.DialogAction<SaveBlockedAction>> recoveryDialogActions() {
		return List.of(
				AppDialogs.DialogAction.of("Try Again", SaveBlockedAction.TRY_AGAIN, AppDialogs.DialogActionKind.PRIMARY, true, false),
				AppDialogs.DialogAction.of("Save Local Backup", SaveBlockedAction.SAVE_DRAFT, AppDialogs.DialogActionKind.SECONDARY, false, false),
				AppDialogs.DialogAction.of("Copy Intake Text", SaveBlockedAction.COPY_TEXT, AppDialogs.DialogActionKind.SECONDARY, false, false),
				AppDialogs.DialogAction.cancel("Keep Editing", SaveBlockedAction.KEEP_EDITING));
	}

	private void offerDraftRestoreIfPresent() {
		try {
			Path draftPath = resolveDraftPath();
			if (!Files.exists(draftPath)) {
				return;
			}
			Optional<Boolean> decision = AppDialogs.showChoice(
					stage,
					"Local Draft Found",
					"Restore local New Intake draft?",
					"Shale found a local draft for New Intake on this device.",
					List.of(
							AppDialogs.DialogAction.of("Restore Draft", true, AppDialogs.DialogActionKind.PRIMARY, true, false),
							AppDialogs.DialogAction.of("Discard Draft", false, AppDialogs.DialogActionKind.DANGER, false, false)));
			if (decision.isEmpty()) {
				return;
			}
			if (decision.get()) {
				restoreDraft(draftPath);
			} else {
				deleteDraftFile(draftPath);
			}
		} catch (RuntimeException ex) {
			System.err.println("[NewIntakeController] draft restore prompt failed: " + ex.getMessage());
		}
	}

	private void saveDraftLocally() {
		try {
			Path draftPath = resolveDraftPath();
			Files.createDirectories(draftPath.getParent());
			String payload = GSON.toJson(toLocalDraftPayload(captureCurrentSnapshot()));
			Files.writeString(draftPath, payload, StandardCharsets.UTF_8);
			showSuccess("Local backup saved. You can restore it next time New Intake is opened.");
			System.out.println("[NewIntakeController] local draft saved path=" + draftPath);
		} catch (Exception ex) {
			System.err.println("[NewIntakeController] local draft save failed: " + ex.getMessage());
			ex.printStackTrace(System.err);
			showValidation("Unable to save a local backup right now. Your form is still open. Use Copy Intake Text to keep an emergency copy.");
			copyIntakeTextToClipboard();
		}
	}

	private void restoreDraft(Path draftPath) {
		try {
			String payload = Files.readString(draftPath, StandardCharsets.UTF_8);
			LocalDraftPayload parsed = GSON.fromJson(payload, LocalDraftPayload.class);
			if (parsed == null || parsed.snapshot() == null) {
				showValidation("The local draft could not be restored.");
				return;
			}
			applySnapshot(fromLocalDraftPayload(parsed));
			showSuccess("Local draft restored.");
			System.out.println("[NewIntakeController] local draft restored path=" + draftPath);
		} catch (Exception ex) {
			System.err.println("[NewIntakeController] local draft restore failed: " + ex.getMessage());
			showValidation("Unable to restore the local draft.");
		}
	}

	private void applySnapshot(IntakeFormSnapshot snapshot) {
		if (snapshot == null) return;
		caseNameField.setText(snapshot.caseName());
		timeOfIntakeField.setText(snapshot.timeOfIntake());
		estateCaseCheckBox.setSelected(snapshot.estateCase());
		clientFirstNameField.setText(snapshot.clientFirstName());
		clientLastNameField.setText(snapshot.clientLastName());
		clientAddressField.setText(snapshot.clientAddress());
		clientPhoneField.setText(snapshot.clientPhone());
		clientEmailField.setText(snapshot.clientEmail());
		clientDateOfBirthPicker.setValue(snapshot.clientDateOfBirth());
		clientDeceasedCheckBox.setSelected(snapshot.clientDeceased());
		clientConditionArea.setText(snapshot.clientCondition());
		callerIsClientCheckBox.setSelected(snapshot.callerIsClient());
		applyCallerMode(snapshot.callerIsClient());
		callerFirstNameField.setText(snapshot.callerFirstName());
		callerLastNameField.setText(snapshot.callerLastName());
		callerPhoneField.setText(snapshot.callerPhone());
		callerAddressField.setText(snapshot.callerAddress());
		callerEmailField.setText(snapshot.callerEmail());
		descriptionArea.setText(snapshot.description());
		summaryArea.setText(snapshot.summary());
		dateMedicalNegligencePicker.setValue(snapshot.medicalNegligenceDate());
		dateMedicalNegligenceDiscoveredPicker.setValue(snapshot.medicalNegligenceDiscoveredDate());
		dateOfInjuryPicker.setValue(snapshot.injuryDate());
		statuteOfLimitationsPicker.setValue(snapshot.statuteOfLimitationsDate());
		tortClaimsNoticePicker.setValue(snapshot.tortClaimsNoticeDate());
		resolveAndApplyPracticeArea(snapshot.practiceAreaId());
		resolveAndApplyStatus(snapshot.statusId());
		pendingParties = snapshot.pendingParties() == null ? new ArrayList<>() : new ArrayList<>(snapshot.pendingParties());
		renderPendingParties();
		hideValidation();
	}

	private void resolveAndApplyPracticeArea(Integer practiceAreaId) {
		selectedPracticeArea = null;
		if (practiceAreaId == null || caseDao == null || appState == null) {
			renderPracticeAreaMini(null, "—", null);
			return;
		}
		try {
			for (CaseDao.PracticeAreaRow row : caseDao.listPracticeAreasForTenant(requireClientId())) {
				if (row != null && row.id() == practiceAreaId) {
					selectedPracticeArea = row;
					break;
				}
			}
		} catch (RuntimeException ignored) {
			selectedPracticeArea = null;
		}
		if (selectedPracticeArea == null) {
			renderPracticeAreaMini(null, "—", null);
			return;
		}
		renderPracticeAreaMini(selectedPracticeArea.id(), selectedPracticeArea.name(), selectedPracticeArea.color());
	}

	private void resolveAndApplyStatus(Integer statusId) {
		selectedStatus = null;
		if (statusId == null || caseDao == null || appState == null) {
			renderStatusMini(null, "—", null);
			return;
		}
		try {
			for (CaseDao.StatusRow row : caseDao.listStatusesForTenant(requireClientId())) {
				if (row != null && row.id() == statusId) {
					selectedStatus = row;
					break;
				}
			}
		} catch (RuntimeException ignored) {
			selectedStatus = null;
		}
		if (selectedStatus == null) {
			renderStatusMini(null, "—", null);
			return;
		}
		renderStatusMini(selectedStatus.id(), selectedStatus.name(), selectedStatus.color());
	}

	private void copyIntakeTextToClipboard() {
		try {
			ClipboardContent content = new ClipboardContent();
			content.putString(toReadableIntakeText(captureCurrentSnapshot()));
			Clipboard.getSystemClipboard().setContent(content);
			showSuccess("Intake text copied to the clipboard.");
		} catch (Exception ex) {
			System.err.println("[NewIntakeController] copy intake text failed: " + ex.getMessage());
			ex.printStackTrace(System.err);
			showValidation("Unable to copy intake text automatically. Your form is still open; please keep editing or copy fields manually.");
		}
	}

	private boolean isConnectivityFailure(Throwable throwable) {
		Throwable current = throwable;
		while (current != null) {
			if (current instanceof SQLException sqlEx) {
				String state = sqlEx.getSQLState();
				if (state != null && (state.startsWith("08") || state.equals("HYT00") || state.equals("HYT01"))) return true;
			}
			String name = current.getClass().getName().toLowerCase();
			String msg = String.valueOf(current.getMessage()).toLowerCase();
			if (name.contains("sqlserverexception") && (msg.contains("connection") || msg.contains("network") || msg.contains("socket") || msg.contains("timeout"))) return true;
			if (msg.contains("connection") || msg.contains("network") || msg.contains("socket") || msg.contains("timed out") || msg.contains("timeout")) return true;
			current = current.getCause();
		}
		return false;
	}

	private void logCreateFailure(int tenantId, RuntimeException ex) {
		System.err.println("[NewIntakeController] DAO create failed " + saveContext(tenantId) + " connectivity=" + isConnectivityFailure(ex) + " error=" + ex.getMessage());
		ex.printStackTrace(System.err);
	}

	private String saveContext(int tenantId) {
		return "tenant=" + tenantId
				+ " userId=" + (appState == null ? null : appState.getUserId())
				+ " userEmail=" + (appState == null ? null : appState.getUserEmail())
				+ " shaleClientId=" + (appState == null ? null : appState.getShaleClientId())
				+ " appVersion=" + System.getProperty("shale.version", System.getProperty("app.version", "unknown"));
	}

	private Path resolveDraftPath() {
		int tenantId = appState == null || appState.getShaleClientId() == null ? 0 : appState.getShaleClientId();
		int userId = appState == null || appState.getUserId() == null ? 0 : appState.getUserId();
		String fileName = INTAKE_DRAFT_PREFIX + "-" + tenantId + "-" + userId + ".json";
		return AppPaths.appSupportDir("Shale").resolve(DRAFTS_DIR).resolve(fileName);
	}

	private void deleteLocalDraftIfPresent() {
		try {
			deleteDraftFile(resolveDraftPath());
		} catch (RuntimeException ex) {
			System.err.println("[NewIntakeController] local draft delete failed: " + ex.getMessage());
		}
	}

	private void deleteDraftFile(Path draftPath) {
		if (draftPath == null) return;
		try {
			Files.deleteIfExists(draftPath);
		} catch (Exception ex) {
			System.err.println("[NewIntakeController] local draft delete failed path=" + draftPath + " error=" + ex.getMessage());
		}
	}

	@FXML
	private void onCancel() {
		requestClose();
	}

	public void requestClose() {
		if (stage != null && mayCloseIntake()) {
			stage.close();
		}
	}

	private boolean mayCloseIntake() {
		return evaluateClosePolicy(successfulCompletion, saving, hasUnsavedChanges(),
				this::confirmDiscard, () -> showValidation("Create Intake is in progress. Please wait."));
	}

	static boolean evaluateClosePolicy(boolean successfulCompletion, boolean saving, boolean dirty,
			BooleanSupplier confirmDiscard, Runnable savingWarning) {
		if (successfulCompletion) return true;
		if (saving) {
			savingWarning.run();
			return false;
		}
		return !dirty || confirmDiscard.getAsBoolean();
	}

	private boolean confirmDiscard() {
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
				null,
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
		PracticeAreaValidationResult practiceAreaState = loadTenantPracticeAreasForValidation();
		List<String> practiceAreaErrors = validatePracticeAreaSelection(practiceAreaState);
		return java.util.stream.Stream.concat(
				validateRequiredFields().stream(),
				practiceAreaErrors.stream()
		).filter(s -> s != null && !s.isBlank()).toList();
	}

	private List<String> validateRequiredFields() {
		List<String> errors = new ArrayList<>(java.util.stream.Stream.of(
				required(caseNameField.getText(), "Case Name is required."),
				validateIntakeTime(),
				required(clientFirstNameField.getText(), "Client First Name is required."),
				required(clientLastNameField.getText(), "Client Last Name is required."),
				required(clientPhoneField.getText(), "Client Phone Number is required."),
				selectedStatus == null ? "Status is required." : null,
				callerRequiredWhenNotClient(callerFirstNameField.getText(), "Caller First Name is required when Caller is Client is unchecked."),
				callerRequiredWhenNotClient(callerLastNameField.getText(), "Caller Last Name is required when Caller is Client is unchecked."),
				callerRequiredWhenNotClient(callerPhoneField.getText(), "Caller Phone Number is required when Caller is Client is unchecked.")
		).filter(s -> s != null && !s.isBlank()).toList());
		configuredDateInputs.values().stream().filter(input -> input.required() && input.value() == null)
				.forEach(input -> {
					ControlStyles.setInvalid(input.input(), true);
					errors.add("A required configured date is missing.");
				});
		allIntakeDatePickers().stream().filter(NewIntakeController::hasInvalidDateText).forEach(picker -> {
			ControlStyles.setInvalid(picker, true);
			errors.add("Enter dates in a valid format or leave optional dates blank.");
		});
		return List.copyOf(errors);
	}

	private List<DatePicker> allIntakeDatePickers() {
		List<DatePicker> pickers = new ArrayList<>(List.of(clientDateOfBirthPicker,
				dateMedicalNegligencePicker, dateMedicalNegligenceDiscoveredPicker, dateOfInjuryPicker,
				statuteOfLimitationsPicker, tortClaimsNoticePicker));
		configuredDateInputs.values().forEach(input -> pickers.add(input.input()));
		return pickers;
	}

	static void configureDatePicker(DatePicker picker) {
		Objects.requireNonNull(picker, "picker");
		StringConverter<LocalDate> delegate = new LocalDateStringConverter();
		picker.setConverter(new StringConverter<>() {
			@Override public String toString(LocalDate value) { return delegate.toString(value); }
			@Override public LocalDate fromString(String text) {
				if (text == null || text.trim().isEmpty()) {
					markDateTextValidity(picker, true);
					return null;
				}
				try {
					LocalDate parsed = delegate.fromString(text.trim());
					markDateTextValidity(picker, true);
					return parsed;
				} catch (RuntimeException invalidDate) {
					markDateTextValidity(picker, false);
					return picker.getValue();
				}
			}
		});
		picker.valueProperty().addListener((observable, oldValue, newValue) -> {
			if (!hasInvalidDateText(picker)) ControlStyles.setInvalid(picker, false);
		});
	}

	private static void markDateTextValidity(DatePicker picker, boolean valid) {
		picker.getProperties().put(INVALID_DATE_PROPERTY, !valid);
		ControlStyles.setInvalid(picker, !valid);
	}

	static boolean hasInvalidDateText(DatePicker picker) {
		return Boolean.TRUE.equals(picker.getProperties().get(INVALID_DATE_PROPERTY));
	}

	private void focusFirstMissingConfiguredDate() {
		configuredDateInputs.values().stream()
				.filter(input -> input.required() && input.value() == null)
				.findFirst()
				.ifPresent(input -> input.input().requestFocus());
	}

	private List<String> validatePracticeAreaSelection(PracticeAreaValidationResult practiceAreaState) {
		boolean selectedPracticeAreaValid = selectedPracticeArea != null
				&& (practiceAreaState.unverifiedDueToConnectivity()
						|| practiceAreaState.practiceAreas().stream().anyMatch(area -> area.id() == selectedPracticeArea.id()));
		String practiceAreaError = practiceAreaState.unverifiedDueToConnectivity()
				? practiceAreaConnectivityMessage()
				: practiceAreaState.practiceAreas().isEmpty() ? "No tenant practice areas are configured. Please contact support." : null;
		return java.util.stream.Stream.of(
				practiceAreaError,
				practiceAreaError == null && !selectedPracticeAreaValid ? "Practice Area is required." : null
		).filter(s -> s != null && !s.isBlank()).toList();
	}

	private String practiceAreaConnectivityMessage() {
		return "Shale could not connect to the database to verify practice areas. Your intake was not saved. You can save a local backup and retry later.";
	}

	private PracticeAreaValidationResult loadTenantPracticeAreasForValidation() {
		try {
			return new PracticeAreaValidationResult(caseDao.listPracticeAreasForTenant(requireClientId()), false);
		} catch (RuntimeException ex) {
			if (isConnectivityFailure(ex)) {
				System.err.println("[NewIntakeController] practice area validation connectivity failure " + saveContext(appState == null || appState.getShaleClientId() == null ? 0 : appState.getShaleClientId()));
				ex.printStackTrace(System.err);
				knownOnlineState = Boolean.FALSE;
				return new PracticeAreaValidationResult(List.of(), true);
			}
			return new PracticeAreaValidationResult(List.of(), false);
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

	record IntakeFormSnapshot(
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

	static LocalDraftPayload toLocalDraftPayload(IntakeFormSnapshot snapshot) {
		List<LocalDraftParty> parties = snapshot.pendingParties() == null ? List.of() : snapshot.pendingParties().stream()
				.map(party -> new LocalDraftParty(
						safeString(party.entityType()),
						party.entityId() == null ? "" : String.valueOf(party.entityId()),
						safeString(party.entityLabel()),
						String.valueOf(party.partyRoleId()),
						safeString(party.side()),
						party.primary(),
						safeString(party.notes()),
						party.createNew(),
						safeString(party.contactFirstName()),
						safeString(party.contactLastName()),
						safeString(party.organizationName()),
						party.organizationTypeId() == null ? "" : String.valueOf(party.organizationTypeId())))
				.toList();
		return new LocalDraftPayload(1, new LocalDraftSnapshot(
				safeString(snapshot.caseName()),
				isoDate(snapshot.dateOfIntake()),
				safeString(snapshot.timeOfIntake()),
				snapshot.estateCase(),
				safeString(snapshot.clientFirstName()),
				safeString(snapshot.clientLastName()),
				safeString(snapshot.clientAddress()),
				safeString(snapshot.clientPhone()),
				safeString(snapshot.clientEmail()),
				isoDate(snapshot.clientDateOfBirth()),
				snapshot.clientDeceased(),
				safeString(snapshot.clientCondition()),
				snapshot.callerIsClient(),
				safeString(snapshot.callerFirstName()),
				safeString(snapshot.callerLastName()),
				safeString(snapshot.callerPhone()),
				safeString(snapshot.callerAddress()),
				safeString(snapshot.callerEmail()),
				snapshot.practiceAreaId() == null ? "" : String.valueOf(snapshot.practiceAreaId()),
				snapshot.statusId() == null ? "" : String.valueOf(snapshot.statusId()),
				safeString(snapshot.description()),
				safeString(snapshot.summary()),
				isoDate(snapshot.medicalNegligenceDate()),
				isoDate(snapshot.medicalNegligenceDiscoveredDate()),
				isoDate(snapshot.injuryDate()),
				isoDate(snapshot.statuteOfLimitationsDate()),
				isoDate(snapshot.tortClaimsNoticeDate()),
				parties));
	}

	static IntakeFormSnapshot fromLocalDraftPayload(LocalDraftPayload payload) {
		LocalDraftSnapshot s = payload.snapshot();
		List<PartyAddWorkflowDialog.AddPartyDraft> parties = s.pendingParties() == null ? List.of() : s.pendingParties().stream()
				.map(party -> new PartyAddWorkflowDialog.AddPartyDraft(
						party.entityType(),
						parseLongOrNull(party.entityId()),
						party.entityLabel(),
						parseLongOrDefault(party.partyRoleId()),
						party.side(),
						party.primary(),
						party.notes(),
						party.createNew(),
						party.contactFirstName(),
						party.contactLastName(),
						party.organizationName(),
						parseIntOrNull(party.organizationTypeId())))
				.toList();
		return new IntakeFormSnapshot(s.caseName(), parseDateOrNull(s.dateOfIntake()), s.timeOfIntake(), s.estateCase(),
				s.clientFirstName(), s.clientLastName(), s.clientAddress(), s.clientPhone(), s.clientEmail(),
				parseDateOrNull(s.clientDateOfBirth()), s.clientDeceased(), s.clientCondition(), s.callerIsClient(),
				s.callerFirstName(), s.callerLastName(), s.callerPhone(), s.callerAddress(), s.callerEmail(),
				parseIntOrNull(s.practiceAreaId()), parseIntOrNull(s.statusId()), s.description(), s.summary(),
				parseDateOrNull(s.medicalNegligenceDate()), parseDateOrNull(s.medicalNegligenceDiscoveredDate()),
				parseDateOrNull(s.injuryDate()), parseDateOrNull(s.statuteOfLimitationsDate()), parseDateOrNull(s.tortClaimsNoticeDate()), parties);
	}

	static String toReadableIntakeText(IntakeFormSnapshot snapshot) {
		StringBuilder text = new StringBuilder("New Intake Backup\n");
		appendLine(text, "Case name", snapshot.caseName());
		appendLine(text, "Date of intake", isoDate(snapshot.dateOfIntake()));
		appendLine(text, "Time of intake", snapshot.timeOfIntake());
		appendLine(text, "Estate case", yesNo(snapshot.estateCase()));
		appendLine(text, "Client", (safeString(snapshot.clientFirstName()) + " " + safeString(snapshot.clientLastName())).trim());
		appendLine(text, "Client phone", snapshot.clientPhone());
		appendLine(text, "Client email", snapshot.clientEmail());
		appendLine(text, "Client address", snapshot.clientAddress());
		appendLine(text, "Client date of birth", isoDate(snapshot.clientDateOfBirth()));
		appendLine(text, "Client deceased", yesNo(snapshot.clientDeceased()));
		appendLine(text, "Caller is client", yesNo(snapshot.callerIsClient()));
		appendLine(text, "Caller", (safeString(snapshot.callerFirstName()) + " " + safeString(snapshot.callerLastName())).trim());
		appendLine(text, "Caller phone", snapshot.callerPhone());
		appendLine(text, "Caller email", snapshot.callerEmail());
		appendLine(text, "Description", snapshot.description());
		appendLine(text, "Summary", snapshot.summary());
		appendLine(text, "Medical negligence date", isoDate(snapshot.medicalNegligenceDate()));
		appendLine(text, "Medical negligence discovered", isoDate(snapshot.medicalNegligenceDiscoveredDate()));
		appendLine(text, "Injury date", isoDate(snapshot.injuryDate()));
		appendLine(text, "Statute of limitations", isoDate(snapshot.statuteOfLimitationsDate()));
		appendLine(text, "Tort claims notice", isoDate(snapshot.tortClaimsNoticeDate()));
		if (snapshot.pendingParties() != null && !snapshot.pendingParties().isEmpty()) {
			text.append("Pending parties:\n");
			for (PartyAddWorkflowDialog.AddPartyDraft party : snapshot.pendingParties()) {
				text.append("- ").append(safeString(party.entityLabel()));
				appendInline(text, "type", party.entityType());
				appendInline(text, "side", party.side());
				appendInline(text, "notes", party.notes());
				text.append('\n');
			}
		}
		return text.toString();
	}

	private static void appendLine(StringBuilder text, String label, String value) {
		try {
			text.append(label).append(": ").append(safeString(value)).append('\n');
		} catch (RuntimeException ignored) {
			text.append(label).append(": \n");
		}
	}

	private static void appendInline(StringBuilder text, String label, String value) {
		String safe = safeString(value);
		if (!safe.isBlank()) {
			text.append(" | ").append(label).append('=').append(safe);
		}
	}

	private static String yesNo(boolean value) {
		return value ? "Yes" : "No";
	}

	private static String isoDate(LocalDate value) {
		return value == null ? "" : value.toString();
	}

	private static String safeString(String value) {
		return value == null ? "" : value;
	}

	private static LocalDate parseDateOrNull(String value) {
		try {
			return safeString(value).isBlank() ? null : LocalDate.parse(value);
		} catch (RuntimeException ignored) {
			return null;
		}
	}

	private static Integer parseIntOrNull(String value) {
		try {
			return safeString(value).isBlank() ? null : Integer.parseInt(value);
		} catch (RuntimeException ignored) {
			return null;
		}
	}

	private static Long parseLongOrNull(String value) {
		try {
			return safeString(value).isBlank() ? null : Long.parseLong(value);
		} catch (RuntimeException ignored) {
			return null;
		}
	}

	private static long parseLongOrDefault(String value) {
		Long parsed = parseLongOrNull(value);
		return parsed == null ? 0 : parsed;
	}

	record LocalDraftPayload(int version, LocalDraftSnapshot snapshot) {
	}

	record LocalDraftSnapshot(
			String caseName,
			String dateOfIntake,
			String timeOfIntake,
			boolean estateCase,
			String clientFirstName,
			String clientLastName,
			String clientAddress,
			String clientPhone,
			String clientEmail,
			String clientDateOfBirth,
			boolean clientDeceased,
			String clientCondition,
			boolean callerIsClient,
			String callerFirstName,
			String callerLastName,
			String callerPhone,
			String callerAddress,
			String callerEmail,
			String practiceAreaId,
			String statusId,
			String description,
			String summary,
			String medicalNegligenceDate,
			String medicalNegligenceDiscoveredDate,
			String injuryDate,
			String statuteOfLimitationsDate,
			String tortClaimsNoticeDate,
			List<LocalDraftParty> pendingParties) {
	}

	record LocalDraftParty(
			String entityType,
			String entityId,
			String entityLabel,
			String partyRoleId,
			String side,
			boolean primary,
			String notes,
			boolean createNew,
			String contactFirstName,
			String contactLastName,
			String organizationName,
			String organizationTypeId) {
	}

	private record PracticeAreaValidationResult(
			List<CaseDao.PracticeAreaRow> practiceAreas,
			boolean unverifiedDueToConnectivity) {
	}

	private enum SaveBlockedAction {
		TRY_AGAIN,
		SAVE_DRAFT,
		COPY_TEXT,
		KEEP_EDITING
	}

	@FunctionalInterface
	private interface RecoveryActionPresenter {
		Optional<SaveBlockedAction> show(
				Window owner,
				String title,
				String header,
				String content,
				List<AppDialogs.DialogAction<SaveBlockedAction>> actions,
				double minWidth);
	}
}
