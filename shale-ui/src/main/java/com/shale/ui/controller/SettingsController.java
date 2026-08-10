package com.shale.ui.controller;

import com.shale.core.dto.CaseStatusDto;
import com.shale.core.dto.PracticeAreaDto;
import com.shale.core.dto.LinkTypeDto;
import com.shale.core.dto.EffectiveCaseDateTypeDto;
import com.shale.core.dto.CaseDateSemanticRoleMappingDto;
import com.shale.core.dto.MaterialTypeDto;
import com.shale.core.dto.RequestMethodDto;
import com.shale.core.dto.RequestStatusDto;
import com.shale.core.service.CaseServicePort;
import com.shale.core.service.MaterialRequestServicePort;
import com.shale.data.dao.UserDao;
import com.shale.ui.component.dialog.AppDialogs;
import com.shale.ui.component.UserCard;
import com.shale.ui.component.factory.UserCardFactory;
import com.shale.ui.component.factory.UserCardFactory.UserCardModel;
import com.shale.ui.notification.NotificationPreferenceKey;
import com.shale.ui.notification.NotificationPreferences;
import com.shale.ui.notification.NotificationPreferencesService;
import com.shale.ui.services.LiveUpdateEvents;
import com.shale.ui.services.UiRuntimeBridge;
import com.shale.ui.state.AppState;
import com.shale.ui.util.ActionButtonFactory;
import com.shale.ui.util.ControlStyles;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Dialog;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TableCell;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.paint.Color;
import javafx.stage.Window;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.layout.GridPane;
import javafx.scene.shape.Circle;
import javafx.css.PseudoClass;
import com.shale.ui.util.ColorUtil;
import com.shale.ui.component.factory.StatusIndicatorFactory;
import com.shale.ui.component.factory.PracticeAreaIndicatorFactory;
import com.shale.ui.component.factory.LinkTypeIndicatorFactory;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SettingsController {
	@FXML
	private CheckBox taskAssignedToMeCheck;
	@FXML
	private CheckBox taskOverdueCheck;
	@FXML
	private CheckBox taskDueTodayCheck;
	@FXML
	private CheckBox taskDueTomorrowCheck;
	@FXML
	private CheckBox appUpdatesCheck;
	@FXML
	private CheckBox connectivityCheck;
	@FXML
	private CheckBox taskOverdueBannerCheck;
	@FXML
	private CheckBox taskDueTodayBannerCheck;
	@FXML
	private CheckBox appUpdatesBannerCheck;
	@FXML
	private CheckBox connectivityBannerCheck;
	@FXML
	private Label notificationSettingsStatusLabel;
	@FXML
	private Button applyNotificationPreferencesButton;
	@FXML
	private Button resetNotificationPreferencesButton;
	@FXML
	private Button viewAuditLogButton;
	@FXML
	private VBox auditSection;
	@FXML
	private VBox caseStatusAdministrationSection;
	@FXML
	private VBox caseStatusCardsContainer;
	@FXML
	private HBox caseStatusActionRow;
	@FXML
	private Label caseStatusSettingsStatusLabel;
	@FXML
	private VBox practiceAreaAdministrationSection;
	@FXML
	private VBox practiceAreaCardsContainer;
	@FXML
	private HBox practiceAreaActionRow;
	@FXML
	private Label practiceAreaSettingsStatusLabel;
	@FXML
	private VBox linkTypeAdministrationSection;
	@FXML
	private VBox linkTypeCardsContainer;
	@FXML
	private HBox linkTypeActionRow;
	@FXML
	private Label linkTypeSettingsStatusLabel;
	@FXML private VBox caseDateTypeAdministrationSection;
	@FXML private VBox caseDateTypeCardsContainer;
	@FXML private VBox caseDateRoleMappingsContainer;
	@FXML private HBox caseDateTypeActionRow;
	@FXML private Label caseDateTypeSettingsStatusLabel;
	@FXML private VBox requestAdministrationSection;
	@FXML private VBox materialTypeCardsContainer;
	@FXML private HBox materialTypeActionRow;
	@FXML private Label materialTypeSettingsStatusLabel;
	@FXML private VBox requestMethodCardsContainer;
	@FXML private HBox requestMethodActionRow;
	@FXML private Label requestMethodSettingsStatusLabel;
	@FXML private VBox requestStatusCardsContainer;
	@FXML private HBox requestStatusActionRow;
	@FXML private Label requestStatusSettingsStatusLabel;
	@FXML
	private TableView<UserManagementViewRow> userManagementTable;
	@FXML
	private TableColumn<UserManagementViewRow, UserManagementViewRow> userNameColumn;
	@FXML
	private TableColumn<UserManagementViewRow, String> userEmailColumn;
	@FXML
	private TableColumn<UserManagementViewRow, String> userInitialsColumn;
	@FXML
	private TableColumn<UserManagementViewRow, String> userRolesColumn;
	@FXML
	private TableColumn<UserManagementViewRow, String> userStatusColumn;
	@FXML
	private CheckBox showInactiveUsersCheck;
	@FXML private TextField userSearchField;
	@FXML private Button addUserButton;
	@FXML private Button editUserButton;
	@FXML private Button refreshUsersButton;
	@FXML private Button removeUserButton;
	@FXML
	private Button deactivateUserButton;
	@FXML
	private Button reactivateUserButton;
	@FXML
	private Button resetPasswordButton;
	@FXML
	private Label userManagementStatusLabel;

	private NotificationPreferencesService notificationPreferencesService;
	private AppState appState;
	private CaseServicePort caseService;
	private MaterialRequestServicePort materialRequestService;
	private UserDao userDao;
	private Runnable onOpenAuditLog;
	private boolean fxmlReady;
	private final List<CaseStatusViewRow> caseStatusRows = new ArrayList<>();
	private final List<PracticeAreaViewRow> practiceAreaRows = new ArrayList<>();
	private final List<LinkTypeViewRow> linkTypeRows = new ArrayList<>();
	private UiRuntimeBridge runtimeBridge;
	private final java.util.function.Consumer<UiRuntimeBridge.EntityUpdatedEvent> linkTypeLiveHandler = this::handleLinkTypeLiveEvent;
	private CaseStatusViewRow selectedCaseStatusRow;
	private PracticeAreaViewRow selectedPracticeAreaRow;
	private LinkTypeViewRow selectedLinkTypeRow;
	private CaseDateTypeViewRow selectedCaseDateTypeRow;
	private Button editCaseDateTypeButton;
	private Button toggleCaseDateTypeButton;
	private Button removeCaseDateTypeButton;
	private RequestLookupSelection selectedMaterialTypeRow;
	private RequestLookupSelection selectedRequestMethodRow;
	private RequestLookupSelection selectedRequestStatusRow;
	private boolean materialTypeMutationRunning;
	private boolean requestMethodMutationRunning;
	private boolean requestStatusMutationRunning;
	private int requestLookupLoadGeneration;
	private static final PseudoClass SELECTED_CARD = PseudoClass.getPseudoClass("selected");
	private int caseStatusLoadGeneration;
	private int practiceAreaLoadGeneration;
	private int linkTypeLoadGeneration;
	private int caseDateTypeLoadGeneration;
	private int userManagementLoadGeneration;
	private final List<UserManagementViewRow> managedUserRows = new ArrayList<>();
	private final UserCardFactory userManagementCardFactory = new UserCardFactory(null);
	private boolean userMutationRunning;

	private final ExecutorService settingsLoadExecutor = Executors.newFixedThreadPool(4, runnable -> {
		Thread thread = new Thread(runnable, "settings-section-loader");
		thread.setDaemon(true);
		return thread;
	});

	@FXML
	private void initialize() {
		fxmlReady = true;
		configureSettingsSemanticButtons();
		configureUserManagementSemanticButtons();
		configureLookupActionRows();
		configureUserManagementTable();
		updateAdminControlsVisibility();
		if (notificationPreferencesService != null) {
			loadFromPreferences();
		}
		loadAdminSectionsAsync();
	}

	private void configureSettingsSemanticButtons() {
		ControlStyles.apply(applyNotificationPreferencesButton, ControlStyles.Purpose.PRIMARY, ControlStyles.Size.STANDARD);
		ControlStyles.apply(resetNotificationPreferencesButton, ControlStyles.Purpose.SECONDARY, ControlStyles.Size.STANDARD);
		ControlStyles.apply(viewAuditLogButton, ControlStyles.Purpose.SECONDARY, ControlStyles.Size.STANDARD);
	}

	private void configureUserManagementSemanticButtons() {
		ControlStyles.apply(addUserButton, ControlStyles.Purpose.PRIMARY, ControlStyles.Size.STANDARD);
		ControlStyles.apply(editUserButton, ControlStyles.Purpose.SECONDARY, ControlStyles.Size.STANDARD);
		ControlStyles.apply(deactivateUserButton, ControlStyles.Purpose.DANGER, ControlStyles.Size.STANDARD);
		ControlStyles.apply(reactivateUserButton, ControlStyles.Purpose.SECONDARY, ControlStyles.Size.STANDARD);
		ControlStyles.apply(resetPasswordButton, ControlStyles.Purpose.SECONDARY, ControlStyles.Size.STANDARD);
		ControlStyles.apply(refreshUsersButton, ControlStyles.Purpose.GHOST, ControlStyles.Size.STANDARD);
		ControlStyles.apply(removeUserButton, ControlStyles.Purpose.DANGER, ControlStyles.Size.STANDARD);
	}

	public void init(NotificationPreferencesService notificationPreferencesService, AppState appState, Runnable onOpenAuditLog, CaseServicePort caseService, MaterialRequestServicePort materialRequestService, UserDao userDao, UiRuntimeBridge runtimeBridge) {
		this.notificationPreferencesService = Objects.requireNonNull(notificationPreferencesService, "notificationPreferencesService");
		this.appState = Objects.requireNonNull(appState, "appState");
		this.onOpenAuditLog = Objects.requireNonNull(onOpenAuditLog, "onOpenAuditLog");
		this.runtimeBridge = runtimeBridge;
		if (this.runtimeBridge != null) this.runtimeBridge.subscribeEntityUpdated(linkTypeLiveHandler);
		this.caseService = Objects.requireNonNull(caseService, "caseService");
		this.materialRequestService = Objects.requireNonNull(materialRequestService, "materialRequestService");
		this.userDao = Objects.requireNonNull(userDao, "userDao");
		if (fxmlReady) {
			loadFromPreferences();
			updateAdminControlsVisibility();
			loadAdminSectionsAsync();
		}
	}

	public void init(NotificationPreferencesService notificationPreferencesService, AppState appState, Runnable onOpenAuditLog, CaseServicePort caseService, UserDao userDao, UiRuntimeBridge runtimeBridge) {
		init(notificationPreferencesService, appState, onOpenAuditLog, caseService, new MaterialRequestServicePort() {
			@Override public List<MaterialTypeDto> listEffectiveMaterialTypes(int tenantId) { return List.of(); }
			@Override public List<RequestMethodDto> listEffectiveRequestMethods(int tenantId) { return List.of(); }
			@Override public List<RequestStatusDto> listEffectiveRequestStatuses(int tenantId) { return List.of(); }
			@Override public List<com.shale.core.dto.MaterialRequestSummaryDto> listMaterialRequests(long caseId, int tenantId) { return List.of(); }
			@Override public Optional<com.shale.core.dto.MaterialRequestDetailDto> getMaterialRequest(long caseId, long materialRequestId, int tenantId, int actorUserId) { return Optional.empty(); }
			@Override public List<com.shale.core.dto.MaterialRequestFollowUpDto> listFollowUps(long caseId, long materialRequestId, int tenantId, int actorUserId) { return List.of(); }
			@Override public com.shale.core.dto.MaterialRequestDetailDto createMaterialRequest(CreateMaterialRequestCommand command) { throw new UnsupportedOperationException(); }
			@Override public com.shale.core.dto.MaterialRequestDetailDto updateMaterialRequest(UpdateMaterialRequestCommand command) { throw new UnsupportedOperationException(); }
		}, userDao, runtimeBridge);
	}

	@FXML
	private void onApplyNotificationPreferences() {
		if (notificationPreferencesService == null) {
			return;
		}
		NotificationPreferences preferences = notificationPreferencesService.getForCurrentUser();
		Map<NotificationPreferenceKey, Boolean> selected = selectedValues();
		for (Map.Entry<NotificationPreferenceKey, Boolean> entry : selected.entrySet()) {
			preferences = preferences.withEnabled(entry.getKey(), entry.getValue());
		}
		notificationPreferencesService.setForCurrentUser(preferences);
		if (notificationSettingsStatusLabel != null) {
			notificationSettingsStatusLabel.setText("Notification settings applied for this session.");
		}
	}

	@FXML
	private void onResetNotificationPreferences() {
		loadFromPreferences();
		if (notificationSettingsStatusLabel != null) {
			notificationSettingsStatusLabel.setText("Notification settings reset to saved values.");
		}
	}

	@FXML
	private void publishLinkTypeChanged(int linkTypeId, String change) {
		if (runtimeBridge == null) return;
		try {
			runtimeBridge.publishLinkTypeChanged(linkTypeId, requireTenantId(), requireActorUserId(), change);
			runtimeBridge.publishEntityAuditActivityAdded(null, requireTenantId(), requireActorUserId());
		} catch (RuntimeException ignored) { }
	}

	private void publishCaseDateTypeChanged(int typeId) {
		if (runtimeBridge == null) return;
		try { runtimeBridge.publishCaseDateTypeChanged(typeId, requireTenantId(), requireActorUserId()); } catch (RuntimeException ignored) { }
	}

	private void handleLinkTypeLiveEvent(UiRuntimeBridge.EntityUpdatedEvent event) {
		if (event == null || appState == null || !LiveUpdateEvents.ENTITY_LINK_TYPE.equals(event.entityType())) return;
		Integer tenantId = appState.getShaleClientId();
		if (tenantId == null || event.shaleClientId() != tenantId || !isAdminUser()) return;
		Platform.runLater(() -> loadLinkTypesAsync(null));
	}

	@FXML
	private void onViewAuditLog(ActionEvent event) {
		if (!isAdminUser() || onOpenAuditLog == null) {
			return;
		}
		try {
			onOpenAuditLog.run();
		} catch (RuntimeException ex) {
			AppDialogs.showError(settingsWindow(event), "Audit Log", "Unable to open the audit log. " + rootMessage(ex));
		}
	}

	private Window settingsWindow(ActionEvent event) {
		if (event != null && event.getSource() instanceof Node node && node.getScene() != null) {
			return node.getScene().getWindow();
		}
		return auditSection == null || auditSection.getScene() == null ? null : auditSection.getScene().getWindow();
	}

	private void loadAdminSectionsAsync() {
		if (!fxmlReady || !isAdminUser()) return;
		loadCaseStatusesAsync(null);
		loadPracticeAreasAsync(null);
		loadLinkTypesAsync(null);
		loadCaseDateTypesAsync(null);
		loadRequestLookupsAsync();
		loadManagedUsersAsync(null);
	}

	private void loadRequestLookupsAsync() {
		if (materialRequestService == null) return;
		final int generation = ++requestLookupLoadGeneration;
		final int tenantId;
		final int actorUserId;
		try { tenantId = requireTenantId(); actorUserId = requireActorUserId(); } catch (RuntimeException ex) { setMaterialTypeMessage(rootMessage(ex)); return; }
		showRequestLookupLoadingIfEmpty(materialTypeCardsContainer, "Loading material types…");
		showRequestLookupLoadingIfEmpty(requestMethodCardsContainer, "Loading request methods…");
		showRequestLookupLoadingIfEmpty(requestStatusCardsContainer, "Loading request statuses…");
		settingsLoadExecutor.submit(() -> {
			try {
				List<MaterialTypeDto> materialTypes = buildMaterialTypeRows(materialRequestService.listMaterialTypesForAdministration(tenantId, actorUserId), tenantId);
				List<RequestMethodDto> methods = buildRequestMethodRows(materialRequestService.listRequestMethodsForAdministration(tenantId, actorUserId), tenantId);
				List<RequestStatusDto> statuses = buildRequestStatusRows(materialRequestService.listRequestStatusesForAdministration(tenantId, actorUserId), tenantId);
				Platform.runLater(() -> {
					if (generation != requestLookupLoadGeneration) return;
					renderMaterialTypeCards(materialTypes);
					renderRequestMethodCards(methods);
					renderRequestStatusCards(statuses);
				});
			} catch (RuntimeException ex) {
				Platform.runLater(() -> {
					if (generation != requestLookupLoadGeneration) return;
					if (materialTypeCardsContainer != null) materialTypeCardsContainer.getChildren().setAll(loadingLabel("Failed to load material types. " + rootMessage(ex)));
					if (requestMethodCardsContainer != null) requestMethodCardsContainer.getChildren().setAll(loadingLabel("Failed to load request methods. " + rootMessage(ex)));
					if (requestStatusCardsContainer != null) requestStatusCardsContainer.getChildren().setAll(loadingLabel("Failed to load request statuses. " + rootMessage(ex)));
				});
			}
		});
	}

	private void showRequestLookupLoadingIfEmpty(VBox container, String message) {
		if (container != null && container.getChildren().isEmpty()) container.getChildren().setAll(loadingLabel(message));
	}

	private void setCaseStatusLoadingState(String message) {
		if (caseStatusCardsContainer != null) caseStatusCardsContainer.getChildren().setAll(loadingLabel(message));
		setCaseStatusMessage(message);
	}

	private void applyCaseStatusRows(int generation, List<CaseStatusViewRow> rows, String successMessage) {
		if (generation != caseStatusLoadGeneration) return;
		Integer selectedId = selectedCaseStatusRow == null ? null : selectedCaseStatusRow.id();
		caseStatusRows.clear();
		caseStatusRows.addAll(rows);
		selectedCaseStatusRow = rows.stream()
				.filter(row -> selectedId != null && row.id() == selectedId)
				.findFirst()
				.orElse(null);
		renderCaseStatusCards();
		setCaseStatusMessage(successMessage != null && !successMessage.isBlank() ? successMessage : rows.isEmpty() ? "No case statuses are configured for this tenant." : "");
	}

	private void setPracticeAreaLoadingState(String message) {
		if (practiceAreaCardsContainer != null) practiceAreaCardsContainer.getChildren().setAll(loadingLabel(message));
		setPracticeAreaMessage(message);
	}

	private void applyPracticeAreaRows(int generation, List<PracticeAreaViewRow> rows, String successMessage) {
		if (generation != practiceAreaLoadGeneration) return;
		Integer selectedId = selectedPracticeAreaRow == null ? null : selectedPracticeAreaRow.id();
		practiceAreaRows.clear();
		practiceAreaRows.addAll(rows);
		selectedPracticeAreaRow = rows.stream()
				.filter(row -> selectedId != null && row.id() == selectedId)
				.findFirst()
				.orElse(null);
		renderPracticeAreaCards();
		setPracticeAreaMessage(successMessage != null && !successMessage.isBlank() ? successMessage : rows.isEmpty() ? "No practice areas are configured for this tenant." : "");
	}

	private Label loadingLabel(String message) {
		Label label = new Label(message);
		label.getStyleClass().add("search-summary-text");
		label.setWrapText(true);
		return label;
	}

	private void configureLookupActionRows() {
		if (caseStatusActionRow != null) {
			caseStatusActionRow.getChildren().setAll(
					semanticButton("Add Status", ControlStyles.Purpose.PRIMARY, event -> onAddCaseStatus()),
					semanticButton("Edit Status", ControlStyles.Purpose.SECONDARY, event -> onEditCaseStatus()),
					semanticButton("Move Up", ControlStyles.Purpose.GHOST, event -> onMoveCaseStatusUp()),
					semanticButton("Move Down", ControlStyles.Purpose.GHOST, event -> onMoveCaseStatusDown()),
					caseStatusSettingsStatusLabel);
		}
		if (linkTypeActionRow != null) {
			linkTypeActionRow.getChildren().setAll(
					semanticButton("Add Link Type", ControlStyles.Purpose.PRIMARY, event -> onAddLinkType()),
					semanticButton("Edit/Customize", ControlStyles.Purpose.SECONDARY, event -> onEditLinkType()),
					semanticButton("Activate/Deactivate", ControlStyles.Purpose.GHOST, event -> onToggleLinkTypeActive()),
					semanticButton("Reset/Remove", ControlStyles.Purpose.SECONDARY, event -> onResetOrRemoveLinkType()),
					linkTypeSettingsStatusLabel);
		}
		if (practiceAreaActionRow != null) {
			practiceAreaActionRow.getChildren().setAll(
					semanticButton("Add Practice Area", ControlStyles.Purpose.PRIMARY, event -> onAddPracticeArea()),
					semanticButton("Edit Practice Area", ControlStyles.Purpose.SECONDARY, event -> onEditPracticeArea()),
					semanticButton("Deactivate Practice Area", ControlStyles.Purpose.GHOST, event -> onRemovePracticeArea()),
					practiceAreaSettingsStatusLabel);
		}
		if (caseDateTypeActionRow != null) configureCaseDateTypeActionRow();
		if (materialTypeActionRow != null) configureRequestActionRow(materialTypeActionRow, "Material Type", materialTypeSettingsStatusLabel, this::onAddMaterialType, this::onEditMaterialType, this::onToggleMaterialTypeActive, this::onResetOrRemoveMaterialType);
		if (requestMethodActionRow != null) configureRequestActionRow(requestMethodActionRow, "Request Method", requestMethodSettingsStatusLabel, this::onAddRequestMethod, this::onEditRequestMethod, this::onToggleRequestMethodActive, this::onResetOrRemoveRequestMethod);
		if (requestStatusActionRow != null) configureRequestActionRow(requestStatusActionRow, "Request Status", requestStatusSettingsStatusLabel, this::onAddRequestStatus, this::onEditRequestStatus, this::onToggleRequestStatusActive, this::onResetOrRemoveRequestStatus);
	}

	private Button semanticButton(String text, ControlStyles.Purpose purpose, javafx.event.EventHandler<ActionEvent> handler) {
		return ActionButtonFactory.semantic(text, handler, purpose, ControlStyles.Size.STANDARD);
	}

	private void configureRequestActionRow(HBox row, String label, Label status, Runnable add, Runnable edit, Runnable toggle, Runnable reset) {
		row.getChildren().setAll(
				semanticButton("Add " + label, ControlStyles.Purpose.PRIMARY, e -> add.run()),
				semanticButton("Edit/Customize", ControlStyles.Purpose.SECONDARY, e -> edit.run()),
				semanticButton("Activate/Deactivate", ControlStyles.Purpose.GHOST, e -> toggle.run()),
				semanticButton("Reset/Remove", ControlStyles.Purpose.SECONDARY, e -> reset.run()), status);
	}

	private void configureCaseDateTypeActionRow() {
		Button add = semanticButton("Add Case Date Type", ControlStyles.Purpose.PRIMARY, e -> onAddCaseDateType());
		editCaseDateTypeButton = semanticButton("Edit", ControlStyles.Purpose.SECONDARY, e -> onEditCaseDateType());
		toggleCaseDateTypeButton = semanticButton("Activate/Deactivate", ControlStyles.Purpose.GHOST, e -> onToggleCaseDateTypeActive());
		removeCaseDateTypeButton = semanticButton("Remove", ControlStyles.Purpose.DANGER, e -> onResetOrRemoveCaseDateType());
		caseDateTypeActionRow.getChildren().setAll(add, editCaseDateTypeButton, toggleCaseDateTypeButton, removeCaseDateTypeButton, caseDateTypeSettingsStatusLabel);
		updateCaseDateTypeActionState(null);
	}

	private void renderMaterialTypeCards(List<MaterialTypeDto> rows) { if (materialTypeCardsContainer == null) return; List<MaterialTypeDto> effective = rows == null ? List.of() : rows; materialTypeCardsContainer.getChildren().setAll(effective.isEmpty() ? List.of(loadingLabel("No material types are configured for this tenant.")) : effective.stream().map(r -> buildRequestLookupCard(RequestLookupKind.MATERIAL_TYPE, RequestLookupSelection.material(r))).toList()); setMaterialTypeMessage(""); }
	private void renderRequestMethodCards(List<RequestMethodDto> rows) { if (requestMethodCardsContainer == null) return; List<RequestMethodDto> effective = rows == null ? List.of() : rows; requestMethodCardsContainer.getChildren().setAll(effective.isEmpty() ? List.of(loadingLabel("No request methods are configured for this tenant.")) : effective.stream().map(r -> buildRequestLookupCard(RequestLookupKind.REQUEST_METHOD, RequestLookupSelection.method(r))).toList()); setRequestMethodMessage(""); }
	private void renderRequestStatusCards(List<RequestStatusDto> rows) { if (requestStatusCardsContainer == null) return; List<RequestStatusDto> effective = rows == null ? List.of() : rows; requestStatusCardsContainer.getChildren().setAll(effective.isEmpty() ? List.of(loadingLabel("No request statuses are configured for this tenant.")) : effective.stream().map(r -> buildRequestLookupCard(RequestLookupKind.REQUEST_STATUS, RequestLookupSelection.status(r))).toList()); setRequestStatusMessage(""); }
	private VBox buildRequestLookupCard(RequestLookupKind kind, RequestLookupSelection row) {
		VBox card = new VBox(8);
		card.getStyleClass().addAll("shale-entity-card", "shale-entity-card-compact", "shale-entity-card-selectable", "shale-density-compact");
		card.setUserData(row);
		card.setFocusTraversable(true);
		card.pseudoClassStateChanged(SELECTED_CARD, isSelectedRequestLookup(kind, row));
		card.setOnMouseClicked(event -> {
			if (event.getButton() == MouseButton.PRIMARY && !isActionControl(event.getTarget())) selectRequestLookup(kind, row);
		});
		card.setOnKeyPressed(event -> {
			if (event.getCode() == KeyCode.SPACE || event.getCode() == KeyCode.ENTER) {
				selectRequestLookup(kind, row);
				event.consume();
			}
		});
		HBox header = new HBox(10); header.setAlignment(Pos.CENTER_LEFT); Circle dot = new Circle(6); String css = safe(ColorUtil.toCssBackgroundColorOrNull(row.color())); if (!css.isBlank()) dot.setStyle("-fx-background-color: " + css + "; -fx-fill: " + css + ";"); Label name = new Label(row.name()); name.getStyleClass().add("app-dialog-field-label"); Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS); header.getChildren().addAll(dot, name, spacer, LinkTypeIndicatorFactory.createLinkTypePill(row.name(), row.color(), LinkTypeIndicatorFactory.PillSize.COMPACT)); HBox metadata = new HBox(6); metadata.setAlignment(Pos.CENTER_LEFT); metadata.getChildren().addAll(metadataPill(row.active() ? "Active" : "Inactive"), metadataPill(row.scopeLabel())); if (!row.systemKey().isBlank()) metadata.getChildren().add(metadataPill("System: " + row.systemKey())); if (!row.color().isBlank()) metadata.getChildren().add(metadataPill(row.color())); HBox actions = new HBox(8); actions.setAlignment(Pos.CENTER_LEFT); Button edit = cardButton(row.global() ? "Customize" : "Edit", ControlStyles.Purpose.GHOST); edit.setOnAction(e -> { selectRequestLookup(kind,row); editRequestLookup(kind); e.consume(); }); Button toggle = cardButton(row.active() ? "Deactivate" : "Activate", ControlStyles.Purpose.GHOST); toggle.setOnAction(e -> { selectRequestLookup(kind,row); toggleRequestLookup(kind); e.consume(); }); Button reset = cardButton(row.custom() ? "Remove" : "Reset to Default", ControlStyles.Purpose.SECONDARY); reset.setDisable(row.global()); reset.setOnAction(e -> { selectRequestLookup(kind,row); resetRequestLookup(kind); e.consume(); }); Label help = new Label(row.description().isBlank() ? row.lifecycleText() : row.description()); help.getStyleClass().add("search-summary-text"); help.setWrapText(true); actions.getChildren().addAll(edit,toggle,reset,help); card.getChildren().addAll(header,metadata,actions); return card;
	}



	@FXML private void onAddCaseDateType(){ if(!requireAdminLookupManagement("Case Date Types"))return; showCaseDateTypeDialog(null).ifPresent(input->{try{EffectiveCaseDateTypeDto saved=caseService.createCaseDateType(new CaseServicePort.CaseDateTypeCommand(null,requireTenantId(),requireActorUserId(),input.systemKey(),input.name(),input.description(),input.category(),input.color(),input.supportsTime(),input.sortOrder(),input.active(),null)); publishCaseDateTypeChanged(saved.id()); loadCaseDateTypesAsync("Case date type added.");}catch(RuntimeException ex){showCaseDateTypeError(ex);}}); }
	@FXML private void onEditCaseDateType(){ if(!requireAdminLookupManagement("Case Date Types"))return; CaseDateTypeViewRow selected=selectedCaseDateTypeRow(); if(selected==null)return; if(!selected.canEdit()){explainProtectedCaseDateType();return;} showCaseDateTypeDialog(selected.type()).ifPresent(input->{try{caseService.updateCaseDateType(new CaseServicePort.CaseDateTypeCommand(selected.id(),requireTenantId(),requireActorUserId(),selected.systemKeyForSave(),input.name(),input.description(),input.category(),input.color(),input.supportsTime(),input.sortOrder(),input.active(),selected.rowVer())); publishCaseDateTypeChanged(selected.id()); loadCaseDateTypesAsync("Case date type updated.");}catch(RuntimeException ex){showCaseDateTypeError(ex);}}); }
	@FXML private void onToggleCaseDateTypeActive(){ if(!requireAdminLookupManagement("Case Date Types"))return; CaseDateTypeViewRow selected=selectedCaseDateTypeRow(); if(selected==null)return; if(!selected.canToggleActive()){explainProtectedCaseDateType();return;} try{caseService.setCaseDateTypeActive(new CaseServicePort.SetCaseDateTypeActiveCommand(requireTenantId(),requireActorUserId(),selected.id(),!selected.active(),selected.rowVer())); publishCaseDateTypeChanged(selected.id()); loadCaseDateTypesAsync(selected.active()?"Case date type deactivated for new occurrences.":"Case date type activated.");}catch(RuntimeException ex){showCaseDateTypeError(ex);} }
	@FXML private void onResetOrRemoveCaseDateType(){ if(!requireAdminLookupManagement("Case Date Types"))return; CaseDateTypeViewRow selected=selectedCaseDateTypeRow(); if(selected==null)return; if(!selected.canRemove()){explainProtectedCaseDateType();return;} String action="Remove"; if(!AppDialogs.showConfirmation(caseDateTypeCardsContainer==null||caseDateTypeCardsContainer.getScene()==null?null:caseDateTypeCardsContainer.getScene().getWindow(),"Case Date Types",action+" "+selected.name()+"?","This affects future type selections only. Existing Case Dates retain their stored type presentation.",action,AppDialogs.DialogActionKind.DANGER))return; try{caseService.resetCaseDateTypeOverride(new CaseServicePort.ResetCaseDateTypeOverrideCommand(requireTenantId(),requireActorUserId(),selected.id(),selected.rowVer())); publishCaseDateTypeChanged(selected.id()); loadCaseDateTypesAsync("Custom case date type removed from future selections.");}catch(RuntimeException ex){showCaseDateTypeError(ex);} }
	private void loadCaseDateTypesAsync(String successMessage){ if(caseService==null||caseDateTypeCardsContainer==null)return; if(!requireAdminLookupManagement("Case Date Types")){selectedCaseDateTypeRow=null;caseDateTypeCardsContainer.getChildren().clear();updateCaseDateTypeActionState(null);return;} final int generation=++caseDateTypeLoadGeneration; final int tenantId; final int actorUserId; try{tenantId=requireTenantId();actorUserId=requireActorUserId();}catch(RuntimeException ex){setCaseDateTypeMessage(rootMessage(ex));return;} caseDateTypeCardsContainer.getChildren().setAll(loadingLabel("Loading case date types…")); settingsLoadExecutor.submit(()->{try{List<EffectiveCaseDateTypeDto> types=caseService.listCaseDateTypesForAdministration(tenantId,actorUserId); List<CaseDateTypeViewRow> rows=buildCaseDateTypeRows(types,tenantId); List<CaseDateSemanticRoleMappingDto> mappings=caseService.listCaseDateSemanticRoleMappings(tenantId,actorUserId); Platform.runLater(()->{applyCaseDateTypeRows(generation,rows,successMessage);renderCaseDateRoleMappings(mappings,types);});}catch(RuntimeException ex){Platform.runLater(()->{if(generation!=caseDateTypeLoadGeneration)return;selectedCaseDateTypeRow=null;updateCaseDateTypeActionState(null);caseDateTypeCardsContainer.getChildren().setAll(loadingLabel("Failed to load case date types. "+rootMessage(ex)));});}}); }
	static List<CaseDateTypeViewRow> buildCaseDateTypeRows(List<EffectiveCaseDateTypeDto> rows,int tenantId){Map<String,EffectiveCaseDateTypeDto> globals=new java.util.LinkedHashMap<>(),tenantKeyed=new java.util.LinkedHashMap<>();List<CaseDateTypeViewRow> out=new ArrayList<>();for(EffectiveCaseDateTypeDto r:rows==null?List.<EffectiveCaseDateTypeDto>of():rows){if(r==null||(r.shaleClientId()!=null&&r.shaleClientId()!=tenantId))continue;String key=safe(r.systemKey()).trim().toLowerCase(java.util.Locale.ROOT);if(r.shaleClientId()==null&&!key.isBlank())globals.put(key,r);else if(!key.isBlank())tenantKeyed.put(key,r);else if(!r.deleted())out.add(new CaseDateTypeViewRow(r,LinkTypeScope.TENANT_CUSTOM));}for(var e:globals.entrySet()){EffectiveCaseDateTypeDto t=tenantKeyed.get(e.getKey());out.add(t==null||t.deleted()?new CaseDateTypeViewRow(e.getValue(),LinkTypeScope.GLOBAL_DEFAULT):new CaseDateTypeViewRow(t,LinkTypeScope.TENANT_OVERRIDE));}for(var e:tenantKeyed.entrySet())if(!globals.containsKey(e.getKey())&&!e.getValue().deleted())out.add(new CaseDateTypeViewRow(e.getValue(),LinkTypeScope.TENANT_CUSTOM));out.sort(java.util.Comparator.comparing(CaseDateTypeViewRow::name,String.CASE_INSENSITIVE_ORDER).thenComparingInt(CaseDateTypeViewRow::id));return List.copyOf(out);}
	private void applyCaseDateTypeRows(int generation,List<CaseDateTypeViewRow> rows,String successMessage){if(generation!=caseDateTypeLoadGeneration)return;Integer selectedId=selectedCaseDateTypeRow==null?null:selectedCaseDateTypeRow.id();selectedCaseDateTypeRow=preserveCaseDateTypeSelection(rows,selectedId);renderCaseDateTypeCards(rows);updateCaseDateTypeActionState(selectedCaseDateTypeRow);setCaseDateTypeMessage(successMessage==null?"":successMessage);}
	static CaseDateTypeViewRow preserveCaseDateTypeSelection(List<CaseDateTypeViewRow> rows,Integer selectedId){return rows.stream().filter(row->selectedId!=null&&row.id()==selectedId).findFirst().orElse(null);}
	private void renderCaseDateRoleMappings(List<CaseDateSemanticRoleMappingDto> mappings, List<EffectiveCaseDateTypeDto> types) {
		if (caseDateRoleMappingsContainer == null) return;
		List<EffectiveCaseDateTypeDto> eligible = types.stream()
				.filter(type -> type.shaleClientId() != null && type.active() && !type.deleted())
				.toList();
		VBox section = new VBox(8);
		section.getStyleClass().addAll("shale-entity-card", "shale-entity-card-compact", "shale-density-compact");
		if (eligible.isEmpty()) {
			Label empty = new Label("Global defaults are in use because no custom Case Date Types are available.");
			empty.getStyleClass().add("search-summary-text");
			empty.setWrapText(true);
			section.getChildren().add(empty);
		}
		for (CaseDateSemanticRoleMappingDto mapping : mappings) {
			section.getChildren().add(buildCaseDateRoleMappingRow(mapping, eligible));
		}
		caseDateRoleMappingsContainer.getChildren().setAll(section);
	}

	private VBox buildCaseDateRoleMappingRow(CaseDateSemanticRoleMappingDto mapping,
			List<EffectiveCaseDateTypeDto> eligible) {
		VBox row = new VBox(5);
		row.setMaxWidth(Double.MAX_VALUE);
		Label role = new Label(mapping.roleName());
		role.getStyleClass().add("app-dialog-field-label");
		role.setWrapText(true);
		Label effective = new Label("Effective type: " + mapping.effectiveTypeName() + " · "
				+ (mapping.tenantOverride() ? "Tenant override" : "Inherited global default"));
		effective.getStyleClass().add("search-summary-text");
		effective.setWrapText(true);
		row.getChildren().addAll(role, effective);

		FlowPane actions = new FlowPane(8, 6);
		actions.setPrefWrapLength(520);
		if (!eligible.isEmpty()) {
			ComboBox<EffectiveCaseDateTypeDto> selector = ControlStyles.formControl(new ComboBox<>());
			selector.setPromptText("Select a custom Case Date Type");
			selector.setMaxWidth(360);
			selector.getItems().setAll(eligible);
			selector.setConverter(new javafx.util.StringConverter<>() {
				@Override public String toString(EffectiveCaseDateTypeDto value) { return value == null ? "" : value.name(); }
				@Override public EffectiveCaseDateTypeDto fromString(String value) { return null; }
			});
			eligible.stream().filter(type -> type.id() == mapping.effectiveTypeId()).findFirst().ifPresent(selector::setValue);
			Button save = ActionButtonFactory.semantic(mapping.tenantOverride() ? "Change" : "Save override", event -> {
				EffectiveCaseDateTypeDto selected = selector.getValue();
				if (selected == null) { setCaseDateTypeMessage("Select an eligible tenant Case Date Type."); return; }
				try {
					caseService.saveCaseDateSemanticRoleMapping(new CaseServicePort.SaveCaseDateSemanticRoleMappingCommand(
							requireTenantId(), requireActorUserId(), mapping.roleKey(), selected.id(),
							mapping.tenantMappingId(), mapping.tenantMappingRowVer()));
					publishCaseDateTypeChanged(selected.id());
					loadCaseDateTypesAsync("Protected role mapping saved.");
				} catch (RuntimeException ex) { showCaseDateTypeError(ex); }
			}, ControlStyles.Purpose.PRIMARY, ControlStyles.Size.SMALL);
			actions.getChildren().addAll(selector, save);
		}
		if (mapping.tenantOverride()) {
			Button reset = ActionButtonFactory.semantic("Reset to global default", event -> {
				try {
					caseService.resetCaseDateSemanticRoleMapping(new CaseServicePort.ResetCaseDateSemanticRoleMappingCommand(
							requireTenantId(), requireActorUserId(), mapping.roleKey(), mapping.tenantMappingId(),
							mapping.tenantMappingRowVer()));
					publishCaseDateTypeChanged(mapping.effectiveTypeId());
					loadCaseDateTypesAsync("Protected role mapping reset to the global default.");
				} catch (RuntimeException ex) { showCaseDateTypeError(ex); }
			}, ControlStyles.Purpose.SECONDARY, ControlStyles.Size.SMALL);
			actions.getChildren().add(reset);
		}
		if (!actions.getChildren().isEmpty()) row.getChildren().add(actions);
		return row;
	}

	private void renderCaseDateTypeCards(List<CaseDateTypeViewRow> rows){caseDateTypeCardsContainer.getChildren().setAll(rows.isEmpty()?List.of(loadingLabel("No case date types are configured for this tenant.")):rows.stream().map(this::buildCaseDateTypeCard).toList());}
	private VBox buildCaseDateTypeCard(CaseDateTypeViewRow row){VBox card=new VBox(8);card.getStyleClass().addAll("shale-entity-card","shale-entity-card-compact","shale-entity-card-selectable","shale-density-compact");card.setUserData(row);card.setFocusTraversable(true);card.pseudoClassStateChanged(SELECTED_CARD,selectedCaseDateTypeRow!=null&&selectedCaseDateTypeRow.id()==row.id());card.setOnMouseClicked(e->{if(e.getButton()==MouseButton.PRIMARY&&!isActionControl(e.getTarget()))selectCaseDateTypeRow(row);});card.setOnKeyPressed(e->{if(e.getCode()==KeyCode.ENTER||e.getCode()==KeyCode.SPACE){selectCaseDateTypeRow(row);e.consume();}});HBox h=new HBox(10);h.setAlignment(Pos.CENTER_LEFT);Circle dot=new Circle(6);String css=safe(ColorUtil.toCssBackgroundColorOrNull(row.color()));if(!css.isBlank())dot.setStyle("-fx-background-color: "+css+"; -fx-fill: "+css+";");Label name=new Label(row.name());name.getStyleClass().add("app-dialog-field-label");h.getChildren().addAll(dot,name,metadataPill(row.category()),metadataPill(row.supportsTime()?"Timed or all-day":"All-day only"));HBox meta=new HBox(6);meta.getChildren().addAll(metadataPill(row.active()?"Active":"Inactive"),metadataPill(row.scopeLabel()),metadataPill(row.protectedType()?"Protected system type":"Custom type"));Label help=new Label(row.protectedType()?PROTECTED_CASE_DATE_TYPE_MESSAGE:"Select this type to manage it with the actions below.");help.getStyleClass().add("search-summary-text");help.setWrapText(true);card.getChildren().addAll(h,meta,help);return card;}
	private void selectCaseDateTypeRow(CaseDateTypeViewRow row){selectedCaseDateTypeRow=row;updateSelectionStyles(caseDateTypeCardsContainer,row.id());updateCaseDateTypeActionState(row);setCaseDateTypeMessage(row.protectedType()?PROTECTED_CASE_DATE_TYPE_MESSAGE:"");}
	private CaseDateTypeViewRow selectedCaseDateTypeRow(){if(selectedCaseDateTypeRow==null)setCaseDateTypeMessage("Select a case date type first.");return selectedCaseDateTypeRow;}
	private static final String PROTECTED_CASE_DATE_TYPE_MESSAGE="Built into Shale. This Case Date Type cannot be customized, activated, deactivated, removed, or reset.";
	private void explainProtectedCaseDateType(){setCaseDateTypeMessage(PROTECTED_CASE_DATE_TYPE_MESSAGE);}
	private void updateCaseDateTypeActionState(CaseDateTypeViewRow row){boolean editable=row!=null&&row.canEdit();boolean toggle=row!=null&&row.canToggleActive();boolean remove=row!=null&&row.canRemove();if(editCaseDateTypeButton!=null){editCaseDateTypeButton.setDisable(!editable);editCaseDateTypeButton.setTooltip(new Tooltip(row!=null&&row.protectedType()?PROTECTED_CASE_DATE_TYPE_MESSAGE:"Edit the selected custom Case Date Type."));}if(toggleCaseDateTypeButton!=null){toggleCaseDateTypeButton.setDisable(!toggle);toggleCaseDateTypeButton.setText(row==null?"Activate/Deactivate":row.active()?"Deactivate":"Activate");toggleCaseDateTypeButton.setTooltip(new Tooltip(row!=null&&row.protectedType()?PROTECTED_CASE_DATE_TYPE_MESSAGE:"Change whether the selected custom type is available for new Case Dates."));}if(removeCaseDateTypeButton!=null){removeCaseDateTypeButton.setDisable(!remove);removeCaseDateTypeButton.setText("Remove");removeCaseDateTypeButton.setTooltip(new Tooltip(row!=null&&row.protectedType()?PROTECTED_CASE_DATE_TYPE_MESSAGE:"Remove the selected custom type from future selections."));}}
	private Optional<CaseDateTypeInput> showCaseDateTypeDialog(EffectiveCaseDateTypeDto existing){Dialog<CaseDateTypeInput> dialog=new Dialog<>();String title=existing==null?"Add Case Date Type":"Edit Case Date Type";dialog.setTitle(title);AppDialogs.applySecondaryDialogShell(dialog,title);dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK,ButtonType.CANCEL);TextField name=new TextField(existing==null?"":existing.name());TextField description=new TextField(existing==null?"":safe(existing.description()));ChoiceBox<String> category=new ChoiceBox<>();category.getItems().setAll("DEADLINE","TRIAL","HEARING","MEDIATION","DEPOSITION","NOTICE","APPOINTMENT","MILESTONE","OTHER");category.getSelectionModel().select(existing==null?"OTHER":existing.calendarCategory());ControlStyles.formControl(category);ColorPicker colorPicker=new ColorPicker(dbColorToFx(existing==null?null:existing.color()));CheckBox active=new CheckBox("Active");active.setSelected(existing==null||existing.active());CheckBox supports=new CheckBox("Supports time of day");supports.setSelected(existing!=null&&existing.supportsTime());GridPane grid=new GridPane();grid.setHgap(8);grid.setVgap(8);grid.add(new Label("Name"),0,0);grid.add(name,1,0);grid.add(new Label("Description"),0,1);grid.add(description,1,1);grid.add(new Label("Calendar Category"),0,2);grid.add(category,1,2);grid.add(new Label("Color"),0,3);grid.add(colorPicker,1,3);grid.add(supports,1,4);grid.add(active,1,5);dialog.getDialogPane().setContent(grid);styleLookupDialog(dialog,name,colorPicker,active);dialog.setResultConverter(b->{if(b!=ButtonType.OK)return null;return new CaseDateTypeInput(trim(name.getText()),trim(description.getText()),category.getValue(),fxColorToDb(colorPicker.getValue()),supports.isSelected(),existing==null?null:safe(existing.systemKey()),existing==null?null:existing.sortOrder(),active.isSelected());});try{return dialog.showAndWait();}catch(RuntimeException ex){AppDialogs.showError(dialog.getOwner(),"Case Date Types",rootMessage(ex));return Optional.empty();}}
	private void setCaseDateTypeMessage(String msg){if(caseDateTypeSettingsStatusLabel!=null)caseDateTypeSettingsStatusLabel.setText(msg==null?"":msg);} private void showCaseDateTypeError(RuntimeException ex){AppDialogs.showError(caseDateTypeCardsContainer==null||caseDateTypeCardsContainer.getScene()==null?null:caseDateTypeCardsContainer.getScene().getWindow(),"Case Date Types",rootMessage(ex));}
	public record CaseDateTypeViewRow(EffectiveCaseDateTypeDto type, LinkTypeScope scope){int id(){return type.id();}String name(){return safe(type.name());}String color(){return safe(type.color());}String category(){return safe(type.calendarCategory());}boolean supportsTime(){return type.supportsTime();}boolean active(){return type.active();}boolean global(){return scope==LinkTypeScope.GLOBAL_DEFAULT;}boolean custom(){return scope==LinkTypeScope.TENANT_CUSTOM;}boolean protectedType(){return !custom();}boolean canEdit(){return custom();}boolean canToggleActive(){return custom();}boolean canRemove(){return custom();}boolean canReset(){return false;}String scopeLabel(){return switch(scope){case GLOBAL_DEFAULT->"Global/default";case TENANT_OVERRIDE->"Tenant override";case TENANT_CUSTOM->"Tenant custom";};}byte[] rowVer(){return type.rowVer();}String systemKeyForSave(){return safe(type.systemKey()).isBlank()?null:type.systemKey();}}
	private record CaseDateTypeInput(String name,String description,String category,String color,boolean supportsTime,String systemKey,Integer sortOrder,boolean active){}



	@FXML
	private void onAddLinkType() {
		if (!requireAdminLookupManagement("Link Types")) return;
		showLinkTypeDialog(null).ifPresent(input -> {
			try {
				LinkTypeDto saved = caseService.createLinkType(new CaseServicePort.LinkTypeCommand(null, requireTenantId(), requireActorUserId(), input.name(), input.color(), input.active(), input.systemKey(), null));
				publishLinkTypeChanged(saved.id(), LiveUpdateEvents.CHANGE_CREATED);
				loadLinkTypesAsync("Link type added.");
			} catch (RuntimeException ex) { showLinkTypeError(ex); }
		});
	}

	@FXML
	private void onEditLinkType() {
		if (!requireAdminLookupManagement("Link Types")) return;
		LinkTypeViewRow selected = selectedLinkTypeRow();
		if (selected == null) return;
		showLinkTypeDialog(selected.linkType()).ifPresent(input -> {
			try {
				LinkTypeDto saved = caseService.updateLinkType(new CaseServicePort.LinkTypeCommand(selected.id(), requireTenantId(), requireActorUserId(), input.name(), input.color(), input.active(), linkTypeSystemKeyForSave(selected.linkType()), selected.rowVer()));
				publishLinkTypeChanged(saved.id(), selected.global() ? LiveUpdateEvents.CHANGE_CREATED : LiveUpdateEvents.CHANGE_UPDATED);
				loadLinkTypesAsync(selected.global() ? "Tenant override saved for global link type." : "Link type updated.");
			} catch (RuntimeException ex) { showLinkTypeError(ex); }
		});
	}

	@FXML
	private void onToggleLinkTypeActive() {
		if (!requireAdminLookupManagement("Link Types")) return;
		LinkTypeViewRow selected = selectedLinkTypeRow();
		if (selected == null) return;
		try {
			LinkTypeDto saved = caseService.setLinkTypeActive(new CaseServicePort.SetLinkTypeActiveCommand(requireTenantId(), requireActorUserId(), selected.id(), !selected.active(), selected.rowVer()));
			publishLinkTypeChanged(saved.id(), selected.active() ? LiveUpdateEvents.CHANGE_DEACTIVATED : LiveUpdateEvents.CHANGE_ACTIVATED);
			loadLinkTypesAsync(selected.active() ? "Link type deactivated for future selections." : "Link type activated.");
		} catch (RuntimeException ex) { showLinkTypeError(ex); }
	}

	@FXML
	private void onResetOrRemoveLinkType() {
		if (!requireAdminLookupManagement("Link Types")) return;
		LinkTypeViewRow selected = selectedLinkTypeRow();
		if (selected == null) return;
		String action = selected.custom() ? "Remove" : "Reset to Default";
		boolean confirmed = AppDialogs.showConfirmation(
				linkTypeCardsContainer == null || linkTypeCardsContainer.getScene() == null ? null : linkTypeCardsContainer.getScene().getWindow(),
				"Link Types",
				action + " " + selected.getName() + "?",
				"This affects future effective selections. Existing links retain their stored Link Type relationship.",
				action,
				selected.custom() ? AppDialogs.DialogActionKind.DANGER : AppDialogs.DialogActionKind.PRIMARY);
		if (!confirmed) return;
		try {
			caseService.resetLinkTypeOverride(new CaseServicePort.ResetLinkTypeOverrideCommand(requireTenantId(), requireActorUserId(), selected.id()));
			publishLinkTypeChanged(selected.id(), LiveUpdateEvents.CHANGE_OVERRIDE_RESET);
			loadLinkTypesAsync(selected.custom() ? "Custom link type removed from future selections. Existing links retain their stored Link Type relationship." : "Tenant override reset to global default for future selections. Existing links retain their stored Link Type relationship.");
		} catch (RuntimeException ex) { showLinkTypeError(ex); }
	}

	private void loadLinkTypesAsync(String successMessage) {
		if (caseService == null || linkTypeCardsContainer == null) return;
		if (!requireAdminLookupManagement("Link Types")) { linkTypeRows.clear(); selectedLinkTypeRow = null; linkTypeCardsContainer.getChildren().clear(); return; }
		final int generation = ++linkTypeLoadGeneration;
		final int tenantId;
		final int actorUserId;
		try { tenantId = requireTenantId(); actorUserId = requireActorUserId(); } catch (RuntimeException ex) { setLinkTypeMessage(rootMessage(ex)); return; }
		setLinkTypeLoadingState("Loading link types…");
		settingsLoadExecutor.submit(() -> {
			try {
				List<LinkTypeViewRow> rows = buildLinkTypeRows(caseService.listLinkTypesForAdministration(tenantId, actorUserId), tenantId);
				Platform.runLater(() -> applyLinkTypeRows(generation, rows, successMessage));
			} catch (RuntimeException ex) {
				System.err.println("Failed to load Settings link types: " + rootMessage(ex));
				Platform.runLater(() -> { if (generation != linkTypeLoadGeneration) return; linkTypeRows.clear(); selectedLinkTypeRow = null; linkTypeCardsContainer.getChildren().clear(); setLinkTypeMessage("Failed to load link types. " + rootMessage(ex)); });
			}
		});
	}

	static List<LinkTypeViewRow> buildLinkTypeRows(List<LinkTypeDto> rows, int tenantId) {
		Map<String, LinkTypeDto> globals = new java.util.LinkedHashMap<>();
		Map<String, LinkTypeDto> tenantKeyed = new java.util.LinkedHashMap<>();
		List<LinkTypeViewRow> out = new ArrayList<>();
		for (LinkTypeDto row : rows == null ? List.<LinkTypeDto>of() : rows) {
			if (row == null || (row.shaleClientId() != null && row.shaleClientId() != tenantId)) continue;
			String key = safe(row.systemKey()).trim().toLowerCase(java.util.Locale.ROOT);
			if (row.shaleClientId() == null && !key.isBlank()) globals.put(key, row);
			else if (!key.isBlank()) tenantKeyed.put(key, row);
			else if (!row.deleted()) out.add(new LinkTypeViewRow(row, LinkTypeScope.TENANT_CUSTOM));
		}
		for (Map.Entry<String, LinkTypeDto> e : globals.entrySet()) {
			LinkTypeDto tenant = tenantKeyed.get(e.getKey());
			out.add(tenant == null || tenant.deleted() ? new LinkTypeViewRow(e.getValue(), LinkTypeScope.GLOBAL_DEFAULT) : new LinkTypeViewRow(tenant, LinkTypeScope.TENANT_OVERRIDE));
		}
		for (Map.Entry<String, LinkTypeDto> e : tenantKeyed.entrySet()) if (!globals.containsKey(e.getKey()) && !e.getValue().deleted()) out.add(new LinkTypeViewRow(e.getValue(), LinkTypeScope.TENANT_CUSTOM));
		out.sort(java.util.Comparator.comparing(LinkTypeViewRow::getName, String.CASE_INSENSITIVE_ORDER).thenComparingInt(LinkTypeViewRow::id));
		return List.copyOf(out);
	}

	private void setLinkTypeLoadingState(String message) { if (linkTypeCardsContainer != null) linkTypeCardsContainer.getChildren().setAll(loadingLabel(message)); setLinkTypeMessage(message); }
	private void applyLinkTypeRows(int generation, List<LinkTypeViewRow> rows, String successMessage) { if (generation != linkTypeLoadGeneration) return; Integer selectedId = selectedLinkTypeRow == null ? null : selectedLinkTypeRow.id(); linkTypeRows.clear(); linkTypeRows.addAll(rows); selectedLinkTypeRow = rows.stream().filter(row -> selectedId != null && row.id() == selectedId).findFirst().orElse(null); renderLinkTypeCards(); setLinkTypeMessage(successMessage != null && !successMessage.isBlank() ? successMessage : rows.isEmpty() ? "No link types are configured for this tenant." : ""); }
	private void renderLinkTypeCards() { if (linkTypeCardsContainer == null) return; linkTypeCardsContainer.getChildren().clear(); for (LinkTypeViewRow row : linkTypeRows) linkTypeCardsContainer.getChildren().add(buildLinkTypeCard(row)); }
	private VBox buildLinkTypeCard(LinkTypeViewRow row) {
		VBox card = new VBox(8); card.getStyleClass().addAll("shale-entity-card", "shale-entity-card-compact", "shale-entity-card-selectable", "shale-density-compact"); card.setUserData(row); card.setFocusTraversable(true); card.pseudoClassStateChanged(SELECTED_CARD, selectedLinkTypeRow != null && selectedLinkTypeRow.id() == row.id());
		card.setOnMouseClicked(event -> { if (event.getButton() == MouseButton.PRIMARY && !isActionControl(event.getTarget())) selectLinkTypeRow(row); });
		card.setOnKeyPressed(event -> { if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE) { selectLinkTypeRow(row); event.consume(); } });
		HBox header = new HBox(10); header.setAlignment(Pos.CENTER_LEFT); Circle dot = new Circle(6); String colorCss = safe(ColorUtil.toCssBackgroundColorOrNull(row.getColor())); if (!colorCss.isBlank()) dot.setStyle("-fx-background-color: " + colorCss + "; -fx-fill: " + colorCss + ";"); Label name = new Label(row.getName()); name.getStyleClass().add("app-dialog-field-label"); Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS); header.getChildren().addAll(dot, name, spacer, LinkTypeIndicatorFactory.createLinkTypePill(row.getName(), row.getColor(), LinkTypeIndicatorFactory.PillSize.COMPACT));
		HBox metadata = new HBox(6); metadata.setAlignment(Pos.CENTER_LEFT); metadata.getChildren().addAll(metadataPill(row.getActiveState()), metadataPill(row.scopeLabel())); if (!row.getSystemKey().isBlank()) metadata.getChildren().add(metadataPill("System: " + row.getSystemKey())); if (!row.getColor().isBlank()) metadata.getChildren().add(metadataPill(row.getColor()));
		HBox actions = new HBox(8); actions.setAlignment(Pos.CENTER_LEFT); Button edit = cardButton(row.global() ? "Customize" : "Edit", ControlStyles.Purpose.GHOST); edit.setOnAction(event -> { selectLinkTypeRow(row); onEditLinkType(); event.consume(); }); Button toggle = cardButton(row.active() ? "Deactivate" : "Activate", ControlStyles.Purpose.GHOST); toggle.setOnAction(event -> { selectLinkTypeRow(row); onToggleLinkTypeActive(); event.consume(); }); Button reset = cardButton(row.custom() ? "Remove" : "Reset to Default", ControlStyles.Purpose.SECONDARY); reset.setDisable(row.global()); reset.setOnAction(event -> { selectLinkTypeRow(row); onResetOrRemoveLinkType(); event.consume(); }); Label help = new Label(row.lifecycleText()); help.getStyleClass().add("search-summary-text"); help.setWrapText(true); actions.getChildren().addAll(edit, toggle, reset, help);
		card.getChildren().addAll(header, metadata, actions); return card;
	}
	private void selectLinkTypeRow(LinkTypeViewRow row) { selectedLinkTypeRow = row; updateSelectionStyles(linkTypeCardsContainer, row.id()); }
	private LinkTypeViewRow selectedLinkTypeRow() { if (selectedLinkTypeRow == null) setLinkTypeMessage("Select a link type first."); return selectedLinkTypeRow; }
	private Optional<LinkTypeInput> showLinkTypeDialog(LinkTypeDto existing) {
		Dialog<LinkTypeInput> dialog = new Dialog<>(); String dialogTitle = existing == null ? "Add Link Type" : (existing.shaleClientId() == null ? "Customize Link Type" : "Edit Link Type"); dialog.setTitle(dialogTitle); AppDialogs.applySecondaryDialogShell(dialog, dialogTitle); dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
		TextField name = new TextField(existing == null ? "" : existing.name()); name.setPromptText("100 characters max"); CheckBox active = new CheckBox("Active"); active.setSelected(existing == null || existing.active()); ColorPicker colorPicker = new ColorPicker(dbColorToFx(existing == null ? null : existing.color())); GridPane grid = new GridPane(); grid.setHgap(8); grid.setVgap(8); grid.add(new Label("Name"),0,0); grid.add(name,1,0); grid.add(new Label("Color"),0,1); grid.add(colorPicker,1,1); grid.add(active,1,2); if (existing != null && !safe(existing.systemKey()).isBlank()) { grid.add(new Label("System Key"),0,3); grid.add(new Label(existing.systemKey()),1,3); } dialog.getDialogPane().setContent(grid);
		styleLookupDialog(dialog, name, colorPicker, active);
		dialog.setResultConverter(button -> { if (button != ButtonType.OK) return null; String trimmedName = trim(name.getText()); if (trimmedName.isBlank()) throw new IllegalArgumentException("Name is required."); if (trimmedName.length() > 100) throw new IllegalArgumentException("Name must be 100 characters or fewer."); String systemKey = linkTypeSystemKeyForSave(existing); if (systemKey != null && systemKey.length() > 64) throw new IllegalArgumentException("SystemKey must be 64 characters or fewer."); String color = fxColorToDb(colorPicker.getValue()); if (color.length() > 20) throw new IllegalArgumentException("Color must be 20 characters or fewer."); return new LinkTypeInput(trimmedName, color, active.isSelected(), systemKey); });
		try { return dialog.showAndWait(); } catch (RuntimeException ex) { AppDialogs.showError(dialog.getOwner(), "Link Types", rootMessage(ex)); return Optional.empty(); }
	}
	private void showLinkTypeError(RuntimeException ex) { AppDialogs.showError(linkTypeCardsContainer == null || linkTypeCardsContainer.getScene() == null ? null : linkTypeCardsContainer.getScene().getWindow(), "Link Types", rootMessage(ex)); }


	@FXML
	private void onAddPracticeArea() {
		if (!requireAdminLookupManagement("Practice Areas")) return;
		showPracticeAreaDialog(null).ifPresent(input -> {
			caseService.createPracticeArea(new CaseServicePort.PracticeAreaCommand(
					null, requireTenantId(), input.name(), input.color(), input.active(), input.systemKey()));
			loadPracticeAreasAsync("Practice area added.");
		});
	}

	@FXML
	private void onEditPracticeArea() {
		if (!requireAdminLookupManagement("Practice Areas")) return;
		PracticeAreaViewRow selected = selectedPracticeAreaRow();
		if (selected == null) return;
		showPracticeAreaDialog(selected.practiceArea()).ifPresent(input -> {
			caseService.updatePracticeArea(new CaseServicePort.PracticeAreaCommand(
					selected.id(), requireTenantId(), input.name(), input.color(), input.active(), input.systemKey()));
			loadPracticeAreasAsync("Practice area updated.");
		});
	}

	@FXML
	private void onRemovePracticeArea() {
		if (!requireAdminLookupManagement("Practice Areas")) return;
		PracticeAreaViewRow selected = selectedPracticeAreaRow();
		if (selected == null) return;
		try {
			caseService.deactivatePracticeArea(requireTenantId(), selected.id());
			loadPracticeAreasAsync("Practice area removed from new selections. Existing cases keep their value.");
		} catch (RuntimeException ex) {
			AppDialogs.showError(practiceAreaCardsContainer.getScene().getWindow(), "Practice Areas", rootMessage(ex));
		}
	}


	private void loadPracticeAreas() {
		loadPracticeAreasAsync(null);
	}

	private void loadPracticeAreasAsync(String successMessage) {
		if (caseService == null || practiceAreaCardsContainer == null) return;
		if (!requireAdminLookupManagement("Practice Areas")) {
			practiceAreaRows.clear();
			selectedPracticeAreaRow = null;
			practiceAreaCardsContainer.getChildren().clear();
			return;
		}
		final int generation = ++practiceAreaLoadGeneration;
		final int tenantId;
		try {
			tenantId = requireTenantId();
		} catch (RuntimeException ex) {
			setPracticeAreaMessage(rootMessage(ex));
			return;
		}
		setPracticeAreaLoadingState("Loading practice areas…");
		settingsLoadExecutor.submit(() -> {
			try {
				List<PracticeAreaViewRow> rows = new ArrayList<>();
				for (PracticeAreaDto area : caseService.listPracticeAreas(tenantId, true)) rows.add(new PracticeAreaViewRow(area));
				Platform.runLater(() -> applyPracticeAreaRows(generation, rows, successMessage));
			} catch (RuntimeException ex) {
				System.err.println("Failed to load Settings practice areas: " + rootMessage(ex));
				Platform.runLater(() -> {
					if (generation != practiceAreaLoadGeneration) return;
					practiceAreaRows.clear();
					selectedPracticeAreaRow = null;
					practiceAreaCardsContainer.getChildren().clear();
					setPracticeAreaMessage("Failed to load practice areas. " + rootMessage(ex));
				});
			}
		});
	}

	private void renderPracticeAreaCards() {
		if (practiceAreaCardsContainer == null) return;
		practiceAreaCardsContainer.getChildren().clear();
		for (PracticeAreaViewRow row : practiceAreaRows) {
			practiceAreaCardsContainer.getChildren().add(buildPracticeAreaCard(row));
		}
	}

	private VBox buildPracticeAreaCard(PracticeAreaViewRow row) {
		VBox card = new VBox(8);
		card.getStyleClass().addAll("shale-entity-card", "shale-entity-card-compact", "shale-entity-card-selectable", "shale-density-compact");
		card.setUserData(row); card.setFocusTraversable(true);
		card.pseudoClassStateChanged(SELECTED_CARD, selectedPracticeAreaRow != null && selectedPracticeAreaRow.id() == row.id());
		card.setOnMouseClicked(event -> { if (event.getButton() == MouseButton.PRIMARY && !isActionControl(event.getTarget())) selectPracticeAreaRow(row); });
		card.setOnKeyPressed(event -> { if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE) { selectPracticeAreaRow(row); event.consume(); } });

		HBox header = new HBox(10);
		header.setAlignment(Pos.CENTER_LEFT);
		Circle dot = new Circle(6);
		dot.getStyleClass().addAll("shale-indicator-dot", "shale-indicator-practice-area");
		String colorCss = safe(ColorUtil.toCssBackgroundColorOrNull(row.getColor()));
		if (!colorCss.isBlank()) dot.setStyle("-fx-background-color: " + colorCss + "; -fx-fill: " + colorCss + ";");
		Label name = new Label(row.getName());
		name.getStyleClass().add("app-dialog-field-label");
		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		Label preview = PracticeAreaIndicatorFactory.createPracticeAreaPill(row.getName(), row.getColor(), PracticeAreaIndicatorFactory.PillSize.COMPACT);
		header.getChildren().addAll(dot, name, spacer, preview);

		HBox metadata = new HBox(6);
		metadata.setAlignment(Pos.CENTER_LEFT);
		metadata.getChildren().addAll(metadataPill(row.getActiveState()), metadataPill(row.scopeLabel()));
		if (!row.getSystemKey().isBlank()) metadata.getChildren().add(metadataPill("System: " + row.getSystemKey()));
		if (row.deleted()) metadata.getChildren().add(metadataPill("Deleted"));
		if (!row.getColor().isBlank()) metadata.getChildren().add(metadataPill(row.getColor()));

		HBox actions = new HBox(8);
		actions.setAlignment(Pos.CENTER_LEFT);
		Button edit = cardButton("Edit", ControlStyles.Purpose.GHOST);
		edit.setOnAction(event -> { selectPracticeAreaRow(row); onEditPracticeArea(); event.consume(); });
		Button remove = cardButton("Deactivate", ControlStyles.Purpose.GHOST);
		remove.setOnAction(event -> { selectPracticeAreaRow(row); onRemovePracticeArea(); event.consume(); });
		Label restriction = new Label(row.global() ? "Global/default practice area: editing creates or updates a tenant-scoped override when supported." : "Tenant-specific/custom practice area.");
		restriction.getStyleClass().add("search-summary-text");
		actions.getChildren().addAll(edit, remove, restriction);

		card.getChildren().addAll(header, metadata, actions);
		return card;
	}

	private void selectPracticeAreaRow(PracticeAreaViewRow row) {
		selectedPracticeAreaRow = row;
		updateSelectionStyles(practiceAreaCardsContainer, row.id());
	}

	private Optional<PracticeAreaInput> showPracticeAreaDialog(PracticeAreaDto existing) {
		Dialog<PracticeAreaInput> dialog = new Dialog<>();
		String dialogTitle = existing == null ? "Add Practice Area" : "Edit Practice Area";
		dialog.setTitle(dialogTitle);
		AppDialogs.applySecondaryDialogShell(dialog, dialogTitle);
		dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
		TextField name = new TextField(existing == null ? "" : existing.name());
		CheckBox active = new CheckBox("Active");
		active.setSelected(existing == null || existing.active());
		ColorPicker colorPicker = new ColorPicker(dbColorToFx(existing == null ? null : existing.color()));
		GridPane grid = new GridPane(); grid.setHgap(8); grid.setVgap(8);
		grid.add(new Label("Name"), 0, 0); grid.add(name, 1, 0);
		grid.add(new Label("Color"), 0, 1); grid.add(colorPicker, 1, 1);
		grid.add(active, 1, 2);
		if (existing != null && !safe(existing.systemKey()).isBlank()) {
			grid.add(new Label("System Key"), 0, 3);
			grid.add(new Label(existing.systemKey()), 1, 3);
		}
		dialog.getDialogPane().setContent(grid);
		styleLookupDialog(dialog, name, colorPicker, active);
		dialog.setResultConverter(button -> {
			if (button != ButtonType.OK) return null;
			String trimmedName = name.getText() == null ? "" : name.getText().trim();
			if (trimmedName.isBlank()) throw new IllegalArgumentException("Name is required.");
			return new PracticeAreaInput(trimmedName, fxColorToDb(colorPicker.getValue()), active.isSelected(), practiceAreaSystemKeyForSave(existing));
		});
		try { return dialog.showAndWait(); }
		catch (RuntimeException ex) { AppDialogs.showError(dialog.getOwner(), "Practice Areas", rootMessage(ex)); return Optional.empty(); }
	}

	private PracticeAreaViewRow selectedPracticeAreaRow() {
		if (selectedPracticeAreaRow == null) setPracticeAreaMessage("Select a practice area first.");
		return selectedPracticeAreaRow;
	}

	@FXML
	private void onAddCaseStatus() {
		if (!requireAdminLookupManagement("Case Statuses")) return;
		showCaseStatusDialog(null).ifPresent(input -> {
			caseService.createCaseStatus(new CaseServicePort.CaseStatusCommand(
					null,
					requireTenantId(),
					input.name(),
					input.closed(),
					input.sortOrder(),
					input.color(),
					input.lifecycleKey(),
					input.systemKey()));
			loadCaseStatusesAsync("Case status added.");
		});
	}

	@FXML
	private void onEditCaseStatus() {
		if (!requireAdminLookupManagement("Case Statuses")) return;
		CaseStatusViewRow selected = selectedStatusRow();
		if (selected == null) return;
		showCaseStatusDialog(selected.status()).ifPresent(input -> {
			caseService.updateCaseStatus(new CaseServicePort.CaseStatusCommand(
					selected.id(),
					requireTenantId(),
					input.name(),
					input.closed(),
					input.sortOrder(),
					input.color(),
					input.lifecycleKey(),
					input.systemKey()));
			loadCaseStatusesAsync("Case status updated.");
		});
	}

	@FXML
	private void onMoveCaseStatusUp() { if (requireAdminLookupManagement("Case Statuses")) moveSelectedStatus(-1); }

	@FXML
	private void onMoveCaseStatusDown() { if (requireAdminLookupManagement("Case Statuses")) moveSelectedStatus(1); }


	private void loadCaseStatuses() {
		loadCaseStatusesAsync(null);
	}

	private void loadCaseStatusesAsync(String successMessage) {
		if (caseService == null || caseStatusCardsContainer == null) return;
		if (!requireAdminLookupManagement("Case Statuses")) {
			caseStatusRows.clear();
			selectedCaseStatusRow = null;
			caseStatusCardsContainer.getChildren().clear();
			return;
		}
		final int generation = ++caseStatusLoadGeneration;
		final int tenantId;
		try {
			tenantId = requireTenantId();
		} catch (RuntimeException ex) {
			setCaseStatusMessage(rootMessage(ex));
			return;
		}
		setCaseStatusLoadingState("Loading case statuses…");
		settingsLoadExecutor.submit(() -> {
			try {
				List<CaseStatusViewRow> rows = new ArrayList<>();
				for (CaseStatusDto status : caseService.listCaseStatuses(tenantId, true)) rows.add(new CaseStatusViewRow(status));
				Platform.runLater(() -> applyCaseStatusRows(generation, rows, successMessage));
			} catch (RuntimeException ex) {
				System.err.println("Failed to load Settings case statuses: " + rootMessage(ex));
				Platform.runLater(() -> {
					if (generation != caseStatusLoadGeneration) return;
					caseStatusRows.clear();
					selectedCaseStatusRow = null;
					caseStatusCardsContainer.getChildren().clear();
					setCaseStatusMessage("Failed to load case statuses. " + rootMessage(ex));
				});
			}
		});
	}

	private void renderCaseStatusCards() {
		if (caseStatusCardsContainer == null) return;
		caseStatusCardsContainer.getChildren().clear();
		for (int i = 0; i < caseStatusRows.size(); i++) {
			caseStatusCardsContainer.getChildren().add(buildCaseStatusCard(caseStatusRows.get(i), i));
		}
	}

	private VBox buildCaseStatusCard(CaseStatusViewRow row, int index) {
		VBox card = new VBox(8);
		card.getStyleClass().addAll("shale-entity-card", "shale-entity-card-compact", "shale-entity-card-selectable", "shale-density-compact");
		card.setUserData(row); card.setFocusTraversable(true);
		card.pseudoClassStateChanged(SELECTED_CARD, selectedCaseStatusRow != null && selectedCaseStatusRow.id() == row.id());
		card.setOnMouseClicked(event -> { if (event.getButton() == MouseButton.PRIMARY && !isActionControl(event.getTarget())) selectCaseStatusRow(row); });
		card.setOnKeyPressed(event -> { if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE) { selectCaseStatusRow(row); event.consume(); } });

		HBox header = new HBox(10);
		header.setAlignment(Pos.CENTER_LEFT);
		Label name = new Label(row.getName());
		name.getStyleClass().add("app-dialog-field-label");
		Node preview = StatusIndicatorFactory.createStatusPill(row.getName(), row.color());
		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		header.getChildren().addAll(name, spacer, preview);

		HBox metadata = new HBox(6);
		metadata.setAlignment(Pos.CENTER_LEFT);
		metadata.getChildren().addAll(
				metadataPill(row.getClosedState()),
				metadataPill("Sort " + row.getSortOrder()),
				metadataPill(row.scopeLabel()));
		if (!row.getLifecycleKey().isBlank()) metadata.getChildren().add(metadataPill("Lifecycle: " + row.getLifecycleKey()));
		if (!row.getSystemKey().isBlank()) metadata.getChildren().add(metadataPill("System: " + row.getSystemKey()));

		HBox actions = new HBox(8);
		actions.setAlignment(Pos.CENTER_LEFT);
		Button edit = cardButton("Edit", ControlStyles.Purpose.GHOST);
		edit.setOnAction(event -> { selectCaseStatusRow(row); onEditCaseStatus(); event.consume(); });
		Button up = cardButton("Move Up", ControlStyles.Purpose.GHOST);
		up.setDisable(index == 0);
		up.setOnAction(event -> { selectCaseStatusRow(row); moveSelectedStatus(-1); event.consume(); });
		Button down = cardButton("Move Down", ControlStyles.Purpose.GHOST);
		down.setDisable(index >= caseStatusRows.size() - 1);
		down.setOnAction(event -> { selectCaseStatusRow(row); moveSelectedStatus(1); event.consume(); });
		Label restriction = new Label(row.global() ? "Global/default status: editing creates a tenant override; reordering requires tenant-specific status." : "Tenant-specific/custom status.");
		restriction.getStyleClass().add("search-summary-text");
		actions.getChildren().addAll(edit, up, down, restriction);

		card.getChildren().addAll(header, metadata, actions);
		return card;
	}

	private void updateSelectionStyles(VBox container, int selectedId) {
		if (container == null) return;
		for (Node node : container.getChildren()) {
			Object value = node.getUserData();
			int id = value instanceof LinkTypeViewRow row ? row.id()
					: value instanceof PracticeAreaViewRow row ? row.id()
					: value instanceof CaseStatusViewRow row ? row.id()
					: value instanceof CaseDateTypeViewRow row ? row.id() : Integer.MIN_VALUE;
			node.pseudoClassStateChanged(SELECTED_CARD, id == selectedId);
		}
	}

	private Label metadataPill(String text) {
		Label label = new Label(text == null || text.isBlank() ? "—" : text);
		label.getStyleClass().addAll("shale-indicator-chip");
		return label;
	}

	private Button cardButton(String text, ControlStyles.Purpose purpose) {
		return ActionButtonFactory.semantic(text, null, purpose, ControlStyles.Size.SMALL);
	}

	private void styleLookupDialog(Dialog<?> dialog, Control... controls) {
		for (Control control : controls) ControlStyles.formControl(control);
		Node ok = dialog.getDialogPane().lookupButton(ButtonType.OK);
		Node cancel = dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
		if (ok instanceof ButtonBase button) ControlStyles.apply(button, ControlStyles.Purpose.PRIMARY);
		if (cancel instanceof ButtonBase button) ControlStyles.apply(button, ControlStyles.Purpose.SECONDARY);
	}

	private void selectCaseStatusRow(CaseStatusViewRow row) {
		selectedCaseStatusRow = row;
		updateSelectionStyles(caseStatusCardsContainer, row.id());
	}

	private void moveSelectedStatus(int delta) {
		if (!requireAdminLookupManagement("Case Statuses")) return;
		CaseStatusViewRow selected = selectedStatusRow();
		if (selected == null) return;
		int index = caseStatusRows.indexOf(selected);
		int otherIndex = index + delta;
		if (otherIndex < 0 || otherIndex >= caseStatusRows.size()) return;
		CaseStatusViewRow other = caseStatusRows.get(otherIndex);
		try {
			caseService.reorderCaseStatuses(requireTenantId(), selected.id(), other.id());
			selectedCaseStatusRow = selected;
			loadCaseStatusesAsync(null);
		} catch (RuntimeException ex) {
			AppDialogs.showError(caseStatusCardsContainer.getScene().getWindow(), "Case Statuses", rootMessage(ex));
		}
	}

	private Optional<CaseStatusInput> showCaseStatusDialog(CaseStatusDto existing) {
		Dialog<CaseStatusInput> dialog = new Dialog<>();
		String dialogTitle = existing == null ? "Add Status" : "Edit Status";
		dialog.setTitle(dialogTitle);
		AppDialogs.applySecondaryDialogShell(dialog, dialogTitle);
		dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
		TextField name = new TextField(existing == null ? "" : existing.name());
		CheckBox closed = new CheckBox("Closed status");
		closed.setSelected(existing != null && existing.closed());
		ColorPicker colorPicker = new ColorPicker(dbColorToFx(existing == null ? null : existing.color()));
		GridPane grid = new GridPane(); grid.setHgap(8); grid.setVgap(8);
		grid.add(new Label("Name"), 0, 0); grid.add(name, 1, 0);
		grid.add(closed, 1, 1);
		grid.add(new Label("Color"), 0, 2); grid.add(colorPicker, 1, 2);
		if (existing != null && !safe(existing.lifecycleKey()).isBlank()) {
			grid.add(new Label("Lifecycle Key"), 0, 3);
			grid.add(new Label(existing.lifecycleKey()), 1, 3);
		}
		if (existing != null && !safe(existing.systemKey()).isBlank()) {
			grid.add(new Label("System Key"), 0, 4);
			grid.add(new Label(existing.systemKey()), 1, 4);
		}
		dialog.getDialogPane().setContent(grid);
		styleLookupDialog(dialog, name, colorPicker, closed);
		dialog.setResultConverter(button -> {
			if (button != ButtonType.OK) return null;
			String trimmedName = name.getText() == null ? "" : name.getText().trim();
			if (trimmedName.isBlank()) throw new IllegalArgumentException("Name is required.");
			return new CaseStatusInput(
					trimmedName,
					closed.isSelected(),
					sortOrderForSave(existing),
					fxColorToDb(colorPicker.getValue()),
					lifecycleKeyForSave(existing),
					systemKeyForSave(existing));
		});
		try { return dialog.showAndWait(); }
		catch (RuntimeException ex) { AppDialogs.showError(dialog.getOwner(), "Case Statuses", rootMessage(ex)); return Optional.empty(); }
	}

	private CaseStatusViewRow selectedStatusRow() {
		if (selectedCaseStatusRow == null) setCaseStatusMessage("Select a case status first.");
		return selectedCaseStatusRow;
	}

	private boolean requireAdminLookupManagement(String sectionName) {
		if (isAdminUser()) {
			return true;
		}
		String message = "Only admin users can manage " + sectionName.toLowerCase() + ".";
		if ("Case Statuses".equals(sectionName)) {
			setCaseStatusMessage(message);
		} else if ("Practice Areas".equals(sectionName)) {
			setPracticeAreaMessage(message);
		} else if ("Link Types".equals(sectionName)) {
			setLinkTypeMessage(message);
		} else if ("Case Date Types".equals(sectionName)) {
			setCaseDateTypeMessage(message);
		}
		return false;
	}

	private int requireActorUserId() {
		Integer id = appState == null ? null : appState.getUserId();
		if (id == null || id <= 0) throw new IllegalStateException("No actor user is selected.");
		return id;
	}

	private int requireTenantId() {
		Integer id = appState == null ? null : appState.getShaleClientId();
		if (id == null || id <= 0) throw new IllegalStateException("No tenant is selected.");
		return id;
	}

	private static final Color DEFAULT_STATUS_COLOR = Color.rgb(108, 117, 125);


	static Color dbColorToFx(String value) {
		String normalized = value == null ? "" : value.trim();
		if (normalized.matches("(?i)^#[0-9a-f]{6}$")) {
			try { return Color.web(normalized); } catch (RuntimeException ignored) { return DEFAULT_STATUS_COLOR; }
		}
		if (normalized.matches("(?i)^0x[0-9a-f]{8}$")) {
			try {
				int red = Integer.parseInt(normalized.substring(2, 4), 16);
				int green = Integer.parseInt(normalized.substring(4, 6), 16);
				int blue = Integer.parseInt(normalized.substring(6, 8), 16);
				int alpha = Integer.parseInt(normalized.substring(8, 10), 16);
				return Color.rgb(red, green, blue, alpha / 255.0);
			} catch (RuntimeException ignored) {
				return DEFAULT_STATUS_COLOR;
			}
		}
		return DEFAULT_STATUS_COLOR;
	}

	static String fxColorToDb(Color color) {
		Color safeColor = color == null ? DEFAULT_STATUS_COLOR : color;
		return String.format("#%02X%02X%02X",
				toColorByte(safeColor.getRed()),
				toColorByte(safeColor.getGreen()),
				toColorByte(safeColor.getBlue()));
	}

	private static int toColorByte(double value) {
		return Math.max(0, Math.min(255, (int) Math.round(value * 255.0)));
	}

	static Integer sortOrderForSave(CaseStatusDto existing) {
		return existing == null ? null : existing.sortOrder();
	}

	static String lifecycleKeyForSave(CaseStatusDto existing) {
		return existing == null ? null : existing.lifecycleKey();
	}

	static String practiceAreaSystemKeyForSave(PracticeAreaDto existing) {
		return existing == null ? null : existing.systemKey();
	}

	static String linkTypeSystemKeyForSave(LinkTypeDto existing) {
		return existing == null ? null : existing.systemKey();
	}

	static String systemKeyForSave(CaseStatusDto existing) {
		return existing == null ? null : existing.systemKey();
	}

	private void setCaseStatusMessage(String message) { if (caseStatusSettingsStatusLabel != null) caseStatusSettingsStatusLabel.setText(message == null ? "" : message); }
	@FXML
	private void onAddUser() {
		if (!isAdminUser()) {
			AppDialogs.showError(null, "Add User", "Only admin users can create users.");
			return;
		}
		showAddUserDialog().ifPresent(request -> {
			try {
				userDao.createUser(request);
				loadManagedUsersAsync(null);
				AppDialogs.showInfo(null, "Add User", "User added.");
			} catch (RuntimeException ex) {
				AppDialogs.showError(null, "Add User", rootMessage(ex));
			}
		});
	}

	private Optional<UserDao.UserCreateRequest> showAddUserDialog() {
		Dialog<UserDao.UserCreateRequest> dialog = new Dialog<>();
		dialog.setTitle("Add User");
		AppDialogs.applySecondaryDialogShell(dialog, "Add User");
		dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
		TextField firstName = new TextField();
		TextField lastName = new TextField();
		TextField email = new TextField();
		Label emailValidation = new Label("");
		emailValidation.getStyleClass().add("dialog-error-text");
		email.focusedProperty().addListener((obs, oldValue, focused) -> {
			if (!focused) validateAddUserEmail(email, emailValidation);
		});
		PasswordField password = new PasswordField();
		TextField initials = new TextField();
		ColorPicker colorPicker = new ColorPicker(DEFAULT_STATUS_COLOR);
		CheckBox attorney = new CheckBox("Attorney");
		CheckBox admin = new CheckBox("Admin");
		GridPane grid = new GridPane(); grid.setHgap(8); grid.setVgap(8);
		grid.add(new Label("First Name"), 0, 0); grid.add(firstName, 1, 0);
		grid.add(new Label("Last Name"), 0, 1); grid.add(lastName, 1, 1);
		grid.add(new Label("Email"), 0, 2); grid.add(email, 1, 2);
		grid.add(emailValidation, 1, 3);
		grid.add(new Label("Temporary Password"), 0, 4); grid.add(password, 1, 4);
		grid.add(new Label("Initials"), 0, 5); grid.add(initials, 1, 5);
		grid.add(new Label("Color"), 0, 6); grid.add(colorPicker, 1, 6);
		grid.add(attorney, 1, 7);
		grid.add(admin, 1, 8);
		dialog.getDialogPane().setContent(grid);
		dialog.setResultConverter(button -> {
			if (button != ButtonType.OK) return null;
			String duplicateMessage = validateAddUserEmail(email, emailValidation);
			if (!duplicateMessage.isBlank()) throw new IllegalArgumentException(duplicateMessage);
			return new UserDao.UserCreateRequest(
					trim(firstName.getText()),
					trim(lastName.getText()),
					trim(email.getText()),
					password.getText(),
					fxColorToDb(colorPicker.getValue()),
					trim(initials.getText()),
					attorney.isSelected(),
					admin.isSelected());
		});
		try { return dialog.showAndWait(); }
		catch (RuntimeException ex) { AppDialogs.showError(dialog.getOwner(), "Add User", rootMessage(ex)); return Optional.empty(); }
	}


	private String validateAddUserEmail(TextField emailField, Label emailValidation) {
		String normalized = UserDao.normalizeEmail(trim(emailField == null ? null : emailField.getText()));
		if (normalized.isBlank() || userDao == null) {
			if (emailValidation != null) emailValidation.setText("");
			return "";
		}
		try {
			UserDao.ExistingEmailRow existing = userDao.findExistingEmailForCurrentTenant(normalized);
			String message = existing == null ? "" : UserDao.duplicateEmailMessage(existing.deleted());
			if (emailValidation != null) emailValidation.setText(message);
			return message;
		} catch (RuntimeException ex) {
			String message = rootMessage(ex);
			if (emailValidation != null) emailValidation.setText(message);
			return message;
		}
	}

	private void applyUserFilter(){
		if(userManagementTable==null)return;String q=userSearchField==null?"":trim(userSearchField.getText()).toLowerCase(java.util.Locale.ROOT);
		List<UserManagementViewRow> filtered=managedUserRows.stream().filter(r->q.isBlank()||r.searchText().contains(q)).toList(); userManagementTable.getItems().setAll(filtered);
		if(filtered.isEmpty())setUserManagementMessage(managedUserRows.isEmpty()?"No users exist for this tenant.":"No users match the current search.");
	}
	@FXML private void onRefreshUsers(){loadManagedUsersAsync(null);}
	@FXML private void onRemoveUserFromTenant(){
		UserManagementViewRow selected=selectedManagedUser();if(selected==null||userMutationRunning)return;
		String warning="They will no longer be able to sign in and will disappear from User Management and assignment lists. Historical records will be preserved.";
		if(!AppDialogs.showConfirmation(null,"Remove from Tenant","Remove "+selected.name()+" from this tenant?",warning,"Remove from Tenant",AppDialogs.DialogActionKind.DANGER))return;
		userMutationRunning=true;updateUserActionButtons(selected);setUserManagementMessage("Removing user from tenant…");int removedId=selected.id();int generation=++userManagementLoadGeneration;
		settingsLoadExecutor.submit(()->{try{userDao.removeUserFromTenant(removedId,selected.rowVer());Platform.runLater(()->{if(generation!=userManagementLoadGeneration)return;userMutationRunning=false;managedUserRows.removeIf(r->r.id()==removedId);applyUserFilter();userManagementTable.getSelectionModel().clearSelection();updateUserActionButtons(null);setUserManagementMessage("User removed from tenant.");loadManagedUsersAsync("User removed from tenant.");});}catch(RuntimeException ex){Platform.runLater(()->{if(generation!=userManagementLoadGeneration)return;userMutationRunning=false;updateUserActionButtons(selected);AppDialogs.showError(null,"Remove from Tenant",rootMessage(ex));setUserManagementMessage(rootMessage(ex));});}});
	}
	@FXML private void onEditUser(){
		UserManagementViewRow selected=selectedManagedUser();if(selected==null||userMutationRunning)return;
		showEditUserDialog(selected).ifPresent(request->{userMutationRunning=true;updateUserActionButtons(selected);setUserManagementMessage("Saving changes…");settingsLoadExecutor.submit(()->{try{var result=userDao.updateManagedUser(request);Platform.runLater(()->{userMutationRunning=false;loadManagedUsersAsync(result.changed()?"User updated.":"No changes to save.");});}catch(RuntimeException ex){Platform.runLater(()->{userMutationRunning=false;updateUserActionButtons(selected);AppDialogs.showError(null,"Edit User",rootMessage(ex));setUserManagementMessage(rootMessage(ex));});}});});
	}
	private Optional<UserDao.UserUpdateRequest> showEditUserDialog(UserManagementViewRow row){
		Dialog<UserDao.UserUpdateRequest> d=new Dialog<>();d.setTitle("Edit User");AppDialogs.applySecondaryDialogShell(d,"Edit User");
		ButtonType save=new ButtonType("Save Changes",javafx.scene.control.ButtonBar.ButtonData.OK_DONE),cancel=new ButtonType("Cancel",javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);d.getDialogPane().getButtonTypes().setAll(save,cancel);
		TextField first=ControlStyles.formControl(new TextField(row.firstName())),last=ControlStyles.formControl(new TextField(row.lastName())),email=ControlStyles.formControl(new TextField(row.email())),phone=ControlStyles.formControl(new TextField(row.phone())),initials=ControlStyles.formControl(new TextField(row.initials()));ColorPicker color=ControlStyles.formControl(new ColorPicker(dbColorToFx(row.color())));CheckBox attorney=ControlStyles.formControl(new CheckBox("Attorney — eligible for attorney assignments")),admin=ControlStyles.formControl(new CheckBox("Administrator — may manage tenant settings and users"));attorney.setSelected(row.attorney());admin.setSelected(row.admin());
		GridPane g=new GridPane();g.setHgap(12);g.setVgap(10);g.add(new Label("Identity"),0,0,2,1);g.add(new Label("First name"),0,1);g.add(first,1,1);g.add(new Label("Last name"),0,2);g.add(last,1,2);g.add(new Label("Email / login"),0,3);g.add(email,1,3);g.add(new Label("Phone"),0,4);g.add(phone,1,4);g.add(new Label("Initials"),0,5);g.add(initials,1,5);g.add(new Label("User color"),0,6);g.add(color,1,6);g.add(new Label("Application roles"),0,7,2,1);g.add(attorney,1,8);g.add(admin,1,9);g.add(new Label("User ID "+row.id()+" · Status "+row.getStatus()+" (managed separately)"),0,10,2,1);d.getDialogPane().setContent(g);ControlStyles.apply((ButtonBase)d.getDialogPane().lookupButton(save),ControlStyles.Purpose.PRIMARY);ControlStyles.apply((ButtonBase)d.getDialogPane().lookupButton(cancel),ControlStyles.Purpose.SECONDARY);
		Node saveButton=d.getDialogPane().lookupButton(save);saveButton.addEventFilter(ActionEvent.ACTION,e->{boolean invalid=trim(first.getText()).isBlank()||trim(last.getText()).isBlank()||!UserDao.normalizeEmail(email.getText()).contains("@");ControlStyles.setInvalid(first,trim(first.getText()).isBlank());ControlStyles.setInvalid(last,trim(last.getText()).isBlank());ControlStyles.setInvalid(email,!UserDao.normalizeEmail(email.getText()).contains("@"));if(invalid)e.consume();});
		d.setResultConverter(b->{if(b!=save)return null;java.util.Set<Integer> roles=new java.util.HashSet<>();if(admin.isSelected())roles.add(com.shale.core.semantics.RoleSemantics.ROLE_ADMIN);if(attorney.isSelected())roles.add(com.shale.core.semantics.RoleSemantics.ROLE_ATTORNEY);return new UserDao.UserUpdateRequest(row.id(),row.rowVer(),first.getText(),last.getText(),email.getText(),phone.getText(),initials.getText(),fxColorToDb(color.getValue()),roles);});return d.showAndWait();
	}

	@FXML
	private void onToggleInactiveUsers() { loadManagedUsersAsync(null); }

	@FXML
	private void onDeactivateUser() {
		UserManagementViewRow selected = selectedManagedUser();
		if (selected == null) return;
		boolean confirmed = AppDialogs.showConfirmation(null, "Deactivate User", "Deactivate this user?", "This will disable their access while preserving historical records.", "Deactivate", AppDialogs.DialogActionKind.DANGER);
		if (!confirmed) return;
		try {
			userDao.deactivateUser(selected.id());
			loadManagedUsersAsync("User deactivated.");
			setUserManagementMessage("User deactivated.");
		} catch (RuntimeException ex) {
			AppDialogs.showError(null, "Deactivate User", rootMessage(ex));
		}
	}

	@FXML
	private void onReactivateUser() {
		UserManagementViewRow selected = selectedManagedUser();
		if (selected == null) return;
		try {
			userDao.reactivateUser(selected.id());
			loadManagedUsersAsync("User reactivated.");
			setUserManagementMessage("User reactivated.");
		} catch (RuntimeException ex) {
			AppDialogs.showError(null, "Reactivate User", rootMessage(ex));
		}
	}

	@FXML
	private void onResetUserPassword() {
		UserManagementViewRow selected = selectedManagedUser();
		if (selected == null) return;
		Dialog<String> dialog = new Dialog<>();
		dialog.setTitle("Reset Password");
		AppDialogs.applySecondaryDialogShell(dialog, "Reset Password");
		dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
		PasswordField password = new PasswordField();
		PasswordField confirm = new PasswordField();
		Label validation = new Label("");
		validation.getStyleClass().add("dialog-error-text");
		password.textProperty().addListener((obs, oldValue, newValue) -> validation.setText(""));
		confirm.textProperty().addListener((obs, oldValue, newValue) -> validation.setText(""));
		GridPane grid = new GridPane(); grid.setHgap(8); grid.setVgap(8);
		grid.add(new Label("New Password"), 0, 0); grid.add(password, 1, 0);
		grid.add(new Label("Confirm Password"), 0, 1); grid.add(confirm, 1, 1);
		grid.add(validation, 1, 2);
		dialog.getDialogPane().setContent(grid);
		Node okButton = dialog.getDialogPane().lookupButton(ButtonType.OK);
		okButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
			String message = resetPasswordValidationMessage(password.getText(), confirm.getText());
			if (!message.isBlank()) {
				validation.setText(message);
				event.consume();
			}
		});
		dialog.setResultConverter(button -> button == ButtonType.OK ? password.getText() : null);
		try {
			dialog.showAndWait().ifPresent(newPassword -> {
				boolean confirmed = AppDialogs.showConfirmation(null, "Reset Password", "Reset password for " + selected.name() + "?", "Password access will change immediately.", "Reset", AppDialogs.DialogActionKind.PRIMARY);
				if (!confirmed) return;
				userDao.resetPassword(selected.id(), newPassword);
				AppDialogs.showInfo(null, "Reset Password", "Password successfully updated.");
			});
		} catch (RuntimeException ex) {
			AppDialogs.showError(null, "Reset Password", rootMessage(ex));
		}
	}

	static String resetPasswordValidationMessage(String password, String confirmPassword) {
		if (password == null || password.isBlank()) return "Password is required.";
		if (confirmPassword == null || confirmPassword.isBlank()) return "Confirm password is required.";
		if (!Objects.equals(password, confirmPassword)) return "Passwords do not match.";
		return "";
	}

	private void configureUserManagementTable() {
		if (userManagementTable == null) return;
		userNameColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
		userNameColumn.setCellFactory(column -> new TableCell<>() {
			@Override protected void updateItem(UserManagementViewRow row, boolean empty) {
				super.updateItem(row, empty);
				setText(null); setGraphic(null); pseudoClassStateChanged(PseudoClass.getPseudoClass("inactive"), false);
				if (empty || row == null) return;
				UserCard card = userManagementCardFactory.create(
						new UserCardModel(row.id(), row.name(), row.color(), row.initials()),
						UserCardFactory.Variant.MINI);
				card.setInactive(row.deleted());
				card.setMaxWidth(Double.MAX_VALUE);
				setGraphic(card);
			}
		});
		userEmailColumn.setCellValueFactory(new PropertyValueFactory<>("email"));
		userInitialsColumn.setCellValueFactory(new PropertyValueFactory<>("initials"));
		userRolesColumn.setCellValueFactory(new PropertyValueFactory<>("roles"));
		userStatusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
		userManagementTable.getSelectionModel().selectedItemProperty().addListener((obs, oldRow, newRow) -> updateUserActionButtons(newRow));
		if(userSearchField!=null) userSearchField.textProperty().addListener((obs,o,n)->applyUserFilter());
		userManagementTable.setOnMouseClicked(e->{if(e.getButton()==MouseButton.PRIMARY&&e.getClickCount()==2&&selectedManagedUser()!=null)onEditUser();});
		userManagementTable.setOnKeyPressed(e->{if(e.getCode()==KeyCode.ENTER&&userManagementTable.getSelectionModel().getSelectedItem()!=null){onEditUser();e.consume();}});
	}

	private void loadManagedUsers() {
		loadManagedUsersAsync(null);
	}

	private void loadManagedUsersAsync(String successMessage) {
		if (userDao == null || userManagementTable == null || !isAdminUser()) return;
		final int generation = ++userManagementLoadGeneration;
		boolean includeInactive = showInactiveUsersCheck != null && showInactiveUsersCheck.isSelected();
		int selectedId=userManagementTable.getSelectionModel().getSelectedItem()==null?0:userManagementTable.getSelectionModel().getSelectedItem().id();
		updateUserActionButtons(userManagementTable.getSelectionModel().getSelectedItem());
		setUserManagementMessage("Loading users…");
		settingsLoadExecutor.submit(() -> {
			try {
				List<UserManagementViewRow> rows = new ArrayList<>();
				for (UserDao.UserManagementRow row : userDao.listUsersForManagement(includeInactive)) rows.add(new UserManagementViewRow(row));
				Platform.runLater(() -> {
					if (generation != userManagementLoadGeneration) return;
					managedUserRows.clear(); managedUserRows.addAll(rows); applyUserFilter();
					if(selectedId>0) managedUserRows.stream().filter(r->r.id()==selectedId).findFirst().ifPresent(userManagementTable.getSelectionModel()::select);
					updateUserActionButtons(userManagementTable.getSelectionModel().getSelectedItem());
					setUserManagementMessage(successMessage != null && !successMessage.isBlank() ? successMessage : rows.isEmpty() ? "No users found for this tenant." : "");
				});
			} catch (RuntimeException ex) {
				System.err.println("Failed to load Settings users: " + rootMessage(ex));
				Platform.runLater(() -> {
					if (generation != userManagementLoadGeneration) return;
					userManagementTable.getItems().clear();
					updateUserActionButtons(null);
					setUserManagementMessage("Failed to load users. " + rootMessage(ex));
				});
			}
		});
	}

	private UserManagementViewRow selectedManagedUser() {
		UserManagementViewRow selected = userManagementTable == null ? null : userManagementTable.getSelectionModel().getSelectedItem();
		if (selected == null) setUserManagementMessage("Select a user first.");
		return selected;
	}

	private void updateUserActionButtons(UserManagementViewRow selected) {
		boolean has = selected != null && !selected.removed() && !userMutationRunning;
		boolean self = has && appState != null && appState.getUserId() != null && selected.id() == appState.getUserId();
		if(editUserButton!=null)editUserButton.setDisable(!has);
		if (deactivateUserButton != null) deactivateUserButton.setDisable(!has || selected.deleted() || self);
		if (reactivateUserButton != null) reactivateUserButton.setDisable(!has || !selected.deleted());
		if (resetPasswordButton != null) resetPasswordButton.setDisable(!has || selected.deleted());
		if (removeUserButton != null) removeUserButton.setDisable(!has || self);
		if (addUserButton != null) addUserButton.setDisable(userMutationRunning);
		if (refreshUsersButton != null) refreshUsersButton.setDisable(userMutationRunning);
	}

	private void setUserManagementMessage(String message) { if (userManagementStatusLabel != null) userManagementStatusLabel.setText(message == null ? "" : message); }

	private static String trim(String value) { return value == null ? "" : value.trim(); }

	private void setPracticeAreaMessage(String message) { if (practiceAreaSettingsStatusLabel != null) practiceAreaSettingsStatusLabel.setText(message == null ? "" : message); }
	private void setLinkTypeMessage(String message) { if (linkTypeSettingsStatusLabel != null) linkTypeSettingsStatusLabel.setText(message == null ? "" : message); }
	private void setMaterialTypeMessage(String message) { if (materialTypeSettingsStatusLabel != null) materialTypeSettingsStatusLabel.setText(message == null ? "" : message); }
	private void setRequestMethodMessage(String message) { if (requestMethodSettingsStatusLabel != null) requestMethodSettingsStatusLabel.setText(message == null ? "" : message); }
	private void setRequestStatusMessage(String message) { if (requestStatusSettingsStatusLabel != null) requestStatusSettingsStatusLabel.setText(message == null ? "" : message); }
	private static String safe(String value) { return value == null ? "" : value; }
	private static String rootMessage(Throwable ex) { Throwable t = ex; while (t.getCause() != null) t = t.getCause(); return t.getMessage() == null ? t.toString() : t.getMessage(); }

	public static final class CaseStatusViewRow {
		private final CaseStatusDto status;
		CaseStatusViewRow(CaseStatusDto status) { this.status = status; }
		public int getId() { return status.id(); }
		public int id() { return status.id(); }
		public String getName() { return safe(status.name()); }
		public String getClosedState() { return status.closed() ? "Closed" : "Open"; }
		public Integer getSortOrder() { return status.sortOrder(); }
		public String getLifecycleKey() { return safe(status.lifecycleKey()); }
		public String getSystemKey() { return safe(status.systemKey()); }
		public String color() { return status.color(); }
		public boolean global() { return status.shaleClientId() == null; }
		public String scopeLabel() { return global() ? "Global/default" : "Tenant/custom"; }
		CaseStatusDto status() { return status; }
	}


	public enum LinkTypeScope { GLOBAL_DEFAULT, TENANT_OVERRIDE, TENANT_CUSTOM }

	public enum RequestLookupKind { MATERIAL_TYPE, REQUEST_METHOD, REQUEST_STATUS }

	private RequestLookupSelection selectedRequestLookup(RequestLookupKind kind) { return switch (kind) { case MATERIAL_TYPE -> selectedMaterialTypeRow; case REQUEST_METHOD -> selectedRequestMethodRow; case REQUEST_STATUS -> selectedRequestStatusRow; }; }
	private void selectRequestLookup(RequestLookupKind kind, RequestLookupSelection row) {
		switch (kind) { case MATERIAL_TYPE -> selectedMaterialTypeRow = row; case REQUEST_METHOD -> selectedRequestMethodRow = row; case REQUEST_STATUS -> selectedRequestStatusRow = row; }
		updateRequestLookupSelectionStyles(kind);
	}
	private boolean isSelectedRequestLookup(RequestLookupKind kind, RequestLookupSelection row) {
		RequestLookupSelection selected = selectedRequestLookup(kind);
		return selected != null && selected.id() == row.id();
	}
	private void updateRequestLookupSelectionStyles(RequestLookupKind kind) {
		VBox container = switch (kind) { case MATERIAL_TYPE -> materialTypeCardsContainer; case REQUEST_METHOD -> requestMethodCardsContainer; case REQUEST_STATUS -> requestStatusCardsContainer; };
		if (container == null) return;
		for (Node node : container.getChildren()) {
			if (node.getUserData() instanceof RequestLookupSelection row) node.pseudoClassStateChanged(SELECTED_CARD, isSelectedRequestLookup(kind, row));
		}
	}
	private static boolean isActionControl(Object target) {
		Node node = target instanceof Node n ? n : null;
		while (node != null) {
			if (node instanceof ButtonBase) return true;
			Parent parent = node.getParent();
			node = parent;
		}
		return false;
	}
	private void onAddMaterialType(){ addRequestLookup(RequestLookupKind.MATERIAL_TYPE); } private void onEditMaterialType(){ editRequestLookup(RequestLookupKind.MATERIAL_TYPE); } private void onToggleMaterialTypeActive(){ toggleRequestLookup(RequestLookupKind.MATERIAL_TYPE); } private void onResetOrRemoveMaterialType(){ resetRequestLookup(RequestLookupKind.MATERIAL_TYPE); }
	private void onAddRequestMethod(){ addRequestLookup(RequestLookupKind.REQUEST_METHOD); } private void onEditRequestMethod(){ editRequestLookup(RequestLookupKind.REQUEST_METHOD); } private void onToggleRequestMethodActive(){ toggleRequestLookup(RequestLookupKind.REQUEST_METHOD); } private void onResetOrRemoveRequestMethod(){ resetRequestLookup(RequestLookupKind.REQUEST_METHOD); }
	private void onAddRequestStatus(){ addRequestLookup(RequestLookupKind.REQUEST_STATUS); } private void onEditRequestStatus(){ editRequestLookup(RequestLookupKind.REQUEST_STATUS); } private void onToggleRequestStatusActive(){ toggleRequestLookup(RequestLookupKind.REQUEST_STATUS); } private void onResetOrRemoveRequestStatus(){ resetRequestLookup(RequestLookupKind.REQUEST_STATUS); }
	private void addRequestLookup(RequestLookupKind kind){ if (!requireAdminLookupManagement(label(kind)) || mutationRunning(kind)) return; showRequestLookupDialog(kind,null).ifPresent(input -> mutateRequestLookup(kind, () -> { switch(kind){ case MATERIAL_TYPE -> materialRequestService.createMaterialType(new MaterialRequestServicePort.MaterialTypeCommand(null,requireTenantId(),requireActorUserId(),input.name(),input.description(),input.color(),input.active(),input.systemKey(),input.sortOrder(),null)); case REQUEST_METHOD -> materialRequestService.createRequestMethod(new MaterialRequestServicePort.RequestMethodCommand(null,requireTenantId(),requireActorUserId(),input.name(),input.color(),input.active(),input.systemKey(),null,null)); case REQUEST_STATUS -> materialRequestService.createRequestStatus(new MaterialRequestServicePort.RequestStatusCommand(null,requireTenantId(),requireActorUserId(),input.name(),input.color(),input.active(),input.systemKey(),input.sortOrder(),null)); } }, label(kind)+" added.")); }
	private void editRequestLookup(RequestLookupKind kind){ RequestLookupSelection row=selectedRequestLookup(kind); if(row==null){message(kind,"Select a "+label(kind).toLowerCase(java.util.Locale.ROOT)+" first.");return;} if(mutationRunning(kind))return; showRequestLookupDialog(kind,row).ifPresent(input -> mutateRequestLookup(kind, () -> { switch(kind){ case MATERIAL_TYPE -> materialRequestService.updateMaterialType(new MaterialRequestServicePort.MaterialTypeCommand(row.id(),requireTenantId(),requireActorUserId(),input.name(),input.description(),input.color(),input.active(),row.systemKey().isBlank()?input.systemKey():row.systemKey(),input.sortOrder(),row.rowVer())); case REQUEST_METHOD -> materialRequestService.updateRequestMethod(new MaterialRequestServicePort.RequestMethodCommand(row.id(),requireTenantId(),requireActorUserId(),input.name(),input.color(),input.active(),row.systemKey().isBlank()?input.systemKey():row.systemKey(),row.sortOrder(),row.rowVer())); case REQUEST_STATUS -> materialRequestService.updateRequestStatus(new MaterialRequestServicePort.RequestStatusCommand(row.id(),requireTenantId(),requireActorUserId(),input.name(),input.color(),input.active(),row.systemKey().isBlank()?input.systemKey():row.systemKey(),input.sortOrder(),row.rowVer())); } }, row.global()?"Tenant override saved.":label(kind)+" updated.")); }
	private void toggleRequestLookup(RequestLookupKind kind){ RequestLookupSelection row=selectedRequestLookup(kind); if(row==null){message(kind,"Select a "+label(kind).toLowerCase(java.util.Locale.ROOT)+" first.");return;} mutateRequestLookup(kind, () -> { var c=new MaterialRequestServicePort.SetLookupActiveCommand(requireTenantId(),requireActorUserId(),row.id(),!row.active(),row.rowVer()); switch(kind){ case MATERIAL_TYPE -> materialRequestService.setMaterialTypeActive(c); case REQUEST_METHOD -> materialRequestService.setRequestMethodActive(c); case REQUEST_STATUS -> materialRequestService.setRequestStatusActive(c); } }, row.active()?label(kind)+" deactivated for future selections.":label(kind)+" activated."); }
	private void resetRequestLookup(RequestLookupKind kind){ RequestLookupSelection row=selectedRequestLookup(kind); if(row==null){message(kind,"Select a "+label(kind).toLowerCase(java.util.Locale.ROOT)+" first.");return;} String action=row.custom()?"Remove":"Reset to Default"; if(row.global()){message(kind,"Global defaults do not need reset.");return;} if(!AppDialogs.showConfirmation(null,label(kind),action+" "+row.name()+"?","Existing records retain their stored lookup relationship.",action,row.custom()?AppDialogs.DialogActionKind.DANGER:AppDialogs.DialogActionKind.PRIMARY))return; mutateRequestLookup(kind, () -> { var c=new MaterialRequestServicePort.ResetLookupOverrideCommand(requireTenantId(),requireActorUserId(),row.id()); switch(kind){ case MATERIAL_TYPE -> materialRequestService.resetMaterialTypeOverride(c); case REQUEST_METHOD -> materialRequestService.resetRequestMethodOverride(c); case REQUEST_STATUS -> materialRequestService.resetRequestStatusOverride(c); } }, row.custom()?label(kind)+" removed from future selections.":"Tenant override reset to global default."); }
	private Optional<RequestLookupInput> showRequestLookupDialog(RequestLookupKind kind, RequestLookupSelection existing){ Dialog<RequestLookupInput> d=new Dialog<>(); String title=(existing==null?"Add ":existing.global()?"Customize ":"Edit ")+label(kind); d.setTitle(title); AppDialogs.applySecondaryDialogShell(d,title); d.getDialogPane().getButtonTypes().setAll(ButtonType.OK,ButtonType.CANCEL); TextField name=new TextField(existing==null?"":existing.name()); TextField description=new TextField(existing==null?"":existing.description()); TextField sort=new TextField(existing==null?"0":String.valueOf(existing.sortOrder())); CheckBox active=new CheckBox("Active"); active.setSelected(existing==null||existing.active()); ColorPicker colorPicker=new ColorPicker(dbColorToFx(existing==null?null:existing.color())); colorPicker.setMaxWidth(Double.MAX_VALUE); GridPane grid=new GridPane(); grid.setHgap(8); grid.setVgap(8); grid.add(new Label("Name"),0,0);grid.add(name,1,0); int r=1; if(kind==RequestLookupKind.MATERIAL_TYPE){grid.add(new Label("Description"),0,r);grid.add(description,1,r++);} grid.add(new Label("Color"),0,r);grid.add(colorPicker,1,r++); if(kind!=RequestLookupKind.REQUEST_METHOD){grid.add(new Label("Sort Order"),0,r);grid.add(sort,1,r++);} grid.add(active,1,r++); if(existing!=null&&!existing.systemKey().isBlank()){grid.add(new Label("System Key"),0,r);grid.add(new Label(existing.systemKey()),1,r);} d.getDialogPane().setContent(grid); styleLookupDialog(d,name,description,sort,colorPicker,active); d.setResultConverter(b->{if(b!=ButtonType.OK)return null; String nm=trim(name.getText()); if(nm.isBlank())throw new IllegalArgumentException("Name is required."); int sortOrder=existing==null?0:existing.sortOrder(); if(kind!=RequestLookupKind.REQUEST_METHOD){try{sortOrder=Integer.parseInt(trim(sort.getText()).isBlank()?"0":trim(sort.getText()));}catch(NumberFormatException ex){throw new IllegalArgumentException("Sort Order must be a number.");}} return new RequestLookupInput(nm, kind==RequestLookupKind.MATERIAL_TYPE?trim(description.getText()):null, fxColorToDb(colorPicker.getValue()), active.isSelected(), existing==null?null:existing.systemKey(), sortOrder);}); try{return d.showAndWait();}catch(RuntimeException ex){AppDialogs.showError(null,label(kind),rootMessage(ex));return Optional.empty();}}
	private void mutateRequestLookup(RequestLookupKind kind,Runnable work,String success){ if(mutationRunning(kind))return; setMutationRunning(kind,true); message(kind,"Saving…"); settingsLoadExecutor.submit(()->{try{work.run(); Platform.runLater(()->{setMutationRunning(kind,false);message(kind,success);loadRequestLookupsAsync();});}catch(RuntimeException ex){Platform.runLater(()->{setMutationRunning(kind,false);AppDialogs.showError(null,label(kind),rootMessage(ex));message(kind,rootMessage(ex));});}});}
	private boolean mutationRunning(RequestLookupKind kind){return switch(kind){case MATERIAL_TYPE->materialTypeMutationRunning;case REQUEST_METHOD->requestMethodMutationRunning;case REQUEST_STATUS->requestStatusMutationRunning;};}
	private void setMutationRunning(RequestLookupKind kind,boolean v){switch(kind){case MATERIAL_TYPE->materialTypeMutationRunning=v;case REQUEST_METHOD->requestMethodMutationRunning=v;case REQUEST_STATUS->requestStatusMutationRunning=v;}}
	private void message(RequestLookupKind kind,String m){switch(kind){case MATERIAL_TYPE->setMaterialTypeMessage(m);case REQUEST_METHOD->setRequestMethodMessage(m);case REQUEST_STATUS->setRequestStatusMessage(m);}}
	private static String label(RequestLookupKind kind){return switch(kind){case MATERIAL_TYPE->"Material Type";case REQUEST_METHOD->"Request Method";case REQUEST_STATUS->"Request Status";};}
	static List<MaterialTypeDto> buildMaterialTypeRows(List<MaterialTypeDto> rows,int tenantId){Map<String,MaterialTypeDto> g=new java.util.LinkedHashMap<>(),t=new java.util.LinkedHashMap<>();List<MaterialTypeDto> out=new ArrayList<>();for(MaterialTypeDto r:rows==null?List.<MaterialTypeDto>of():rows){if(r.shaleClientId()!=null&&r.shaleClientId()!=tenantId)continue;String k=safe(r.systemKey()).toLowerCase(java.util.Locale.ROOT);if(r.shaleClientId()==null&&!k.isBlank())g.put(k,r);else if(!k.isBlank())t.put(k,r);else if(!r.deleted())out.add(r);}for(var e:g.entrySet()){MaterialTypeDto o=t.get(e.getKey());out.add(o==null||o.deleted()?e.getValue():o);}for(var e:t.entrySet())if(!g.containsKey(e.getKey())&&!e.getValue().deleted())out.add(e.getValue());out.sort(java.util.Comparator.comparing(MaterialTypeDto::sortOrder).thenComparing(MaterialTypeDto::name,String.CASE_INSENSITIVE_ORDER));return List.copyOf(out);}
	static List<RequestMethodDto> buildRequestMethodRows(List<RequestMethodDto> rows,int tenantId){Map<String,RequestMethodDto> g=new java.util.LinkedHashMap<>(),t=new java.util.LinkedHashMap<>();List<RequestMethodDto> out=new ArrayList<>();for(RequestMethodDto r:rows==null?List.<RequestMethodDto>of():rows){if(r.shaleClientId()!=null&&r.shaleClientId()!=tenantId)continue;String k=safe(r.systemKey()).toLowerCase(java.util.Locale.ROOT);if(r.shaleClientId()==null&&!k.isBlank())g.put(k,r);else if(!k.isBlank())t.put(k,r);else if(!r.deleted())out.add(r);}for(var e:g.entrySet()){RequestMethodDto o=t.get(e.getKey());out.add(o==null||o.deleted()?e.getValue():o);}for(var e:t.entrySet())if(!g.containsKey(e.getKey())&&!e.getValue().deleted())out.add(e.getValue());out.sort(java.util.Comparator.comparing(RequestMethodDto::sortOrder).thenComparing(RequestMethodDto::name,String.CASE_INSENSITIVE_ORDER));return List.copyOf(out);}
	static List<RequestStatusDto> buildRequestStatusRows(List<RequestStatusDto> rows,int tenantId){Map<String,RequestStatusDto> g=new java.util.LinkedHashMap<>(),t=new java.util.LinkedHashMap<>();List<RequestStatusDto> out=new ArrayList<>();for(RequestStatusDto r:rows==null?List.<RequestStatusDto>of():rows){if(r.shaleClientId()!=null&&r.shaleClientId()!=tenantId)continue;String k=safe(r.systemKey()).toLowerCase(java.util.Locale.ROOT);if(r.shaleClientId()==null&&!k.isBlank())g.put(k,r);else if(!k.isBlank())t.put(k,r);else if(!r.deleted())out.add(r);}for(var e:g.entrySet()){RequestStatusDto o=t.get(e.getKey());out.add(o==null||o.deleted()?e.getValue():o);}for(var e:t.entrySet())if(!g.containsKey(e.getKey())&&!e.getValue().deleted())out.add(e.getValue());out.sort(java.util.Comparator.comparing(RequestStatusDto::sortOrder).thenComparing(RequestStatusDto::name,String.CASE_INSENSITIVE_ORDER));return List.copyOf(out);}
	private record RequestLookupInput(String name,String description,String color,boolean active,String systemKey,int sortOrder){}
	private record RequestLookupSelection(int id, String name, String description, String color, String systemKey, int sortOrder, boolean active, boolean global, byte[] rowVer) { static RequestLookupSelection material(MaterialTypeDto d){return new RequestLookupSelection(d.id(),safe(d.name()),safe(d.description()),safe(d.color()),safe(d.systemKey()),d.sortOrder(),d.active()&&!d.deleted(),d.shaleClientId()==null,d.rowVer());} static RequestLookupSelection method(RequestMethodDto d){return new RequestLookupSelection(d.id(),safe(d.name()),"",safe(d.color()),safe(d.systemKey()),d.sortOrder(),d.active()&&!d.deleted(),d.shaleClientId()==null,d.rowVer());} static RequestLookupSelection status(RequestStatusDto d){return new RequestLookupSelection(d.id(),safe(d.name()),"",safe(d.color()),safe(d.systemKey()),d.sortOrder(),d.active()&&!d.deleted(),d.shaleClientId()==null,d.rowVer());} boolean custom(){return !global&&systemKey.isBlank();} String scopeLabel(){return global?"Global/default":custom()?"Tenant custom":"Tenant override";} String lifecycleText(){return global?"Editing or changing active state creates a tenant override; global rows are never changed.":custom()?"Tenant custom value can be edited, activated/deactivated, or removed.":"Tenant override masks the global default until reset.";} }

	public static final class LinkTypeViewRow {
		private final LinkTypeDto linkType;
		private final LinkTypeScope scope;
		LinkTypeViewRow(LinkTypeDto linkType, LinkTypeScope scope) { this.linkType = linkType; this.scope = scope; }
		public int getId() { return linkType.id(); }
		public int id() { return linkType.id(); }
		public String getName() { return safe(linkType.name()); }
		public String getColor() { return safe(linkType.color()); }
		public String getActiveState() { return linkType.active() && !linkType.deleted() ? "Active" : "Inactive"; }
		public String getSystemKey() { return safe(linkType.systemKey()); }
		public boolean active() { return linkType.active() && !linkType.deleted(); }
		public boolean global() { return scope == LinkTypeScope.GLOBAL_DEFAULT; }
		public boolean custom() { return scope == LinkTypeScope.TENANT_CUSTOM; }
		public String scopeLabel() { return switch (scope) { case GLOBAL_DEFAULT -> "Global/default"; case TENANT_OVERRIDE -> "Tenant override"; case TENANT_CUSTOM -> "Tenant custom"; }; }
		public String lifecycleText() { return switch (scope) { case GLOBAL_DEFAULT -> "Editing or changing active state creates a tenant override; global rows are never changed."; case TENANT_OVERRIDE -> "Tenant override masks the global default until reset."; case TENANT_CUSTOM -> "Tenant custom type can be edited, activated/deactivated, or removed."; }; }
		byte[] rowVer() { return linkType.rowVer(); }
		LinkTypeDto linkType() { return linkType; }
	}

	public static final class UserManagementViewRow {
		private final UserDao.UserManagementRow row; UserManagementViewRow(UserDao.UserManagementRow row){this.row=row;}
		public int getId(){return row.id();} public int id(){return row.id();} public String getName(){return safe(row.name())+"  (#"+row.id()+")";} public String name(){return safe(row.name());}
		public String getEmail(){return safe(row.email());} public String email(){return getEmail();} public String firstName(){return safe(row.firstName());} public String lastName(){return safe(row.lastName());} public String phone(){return safe(row.phone());} public String initials(){return safe(row.initials());} public String color(){return safe(row.color());}
		public String getInitials(){return initials();} public String getRoles(){return (row.admin()?"Administrator":"")+(row.admin()&&row.attorney()?", ":"")+(row.attorney()?"Attorney":"");} public String getStatus(){return row.deleted()?"Inactive":"Active";} public boolean deleted(){return row.deleted();} public boolean removed(){return row.removed();} public boolean admin(){return row.admin();} public boolean attorney(){return row.attorney();} public byte[] rowVer(){return row.rowVer()==null?null:row.rowVer().clone();}
		String searchText(){return (name()+" "+email()+" "+initials()+" "+getRoles()+" "+id()).toLowerCase(java.util.Locale.ROOT);}
	}

	public static final class PracticeAreaViewRow {
		private final PracticeAreaDto practiceArea;
		PracticeAreaViewRow(PracticeAreaDto practiceArea) { this.practiceArea = practiceArea; }
		public int getId() { return practiceArea.id(); }
		public int id() { return practiceArea.id(); }
		public String getName() { return safe(practiceArea.name()); }
		public String getColor() { return safe(practiceArea.color()); }
		public String getActiveState() { return practiceArea.active() && !practiceArea.deleted() ? "Active" : "Inactive"; }
		public String getSystemKey() { return safe(practiceArea.systemKey()); }
		public boolean deleted() { return practiceArea.deleted(); }
		public boolean active() { return practiceArea.active(); }
		public boolean global() { return practiceArea.shaleClientId() == null; }
		public String scopeLabel() { return global() ? "Global/default" : "Tenant/custom"; }
		PracticeAreaDto practiceArea() { return practiceArea; }
	}

	private record LinkTypeInput(String name, String color, boolean active, String systemKey) {}

	private record PracticeAreaInput(String name, String color, boolean active, String systemKey) {}

	private record CaseStatusInput(
			String name,
			boolean closed,
			Integer sortOrder,
			String color,
			String lifecycleKey,
			String systemKey) {}

	private void loadFromPreferences() {
		if (notificationPreferencesService == null) {
			return;
		}
		NotificationPreferences preferences = notificationPreferencesService.getForCurrentUser();
		setChecked(taskAssignedToMeCheck, preferences.isEnabled(NotificationPreferenceKey.TASK_ASSIGNED_TO_ME));
		setChecked(taskOverdueCheck, preferences.isEnabled(NotificationPreferenceKey.TASK_DUE_OVERDUE));
		setChecked(taskDueTodayCheck, preferences.isEnabled(NotificationPreferenceKey.TASK_DUE_TODAY));
		setChecked(taskDueTomorrowCheck, preferences.isEnabled(NotificationPreferenceKey.TASK_DUE_TOMORROW));
		setChecked(appUpdatesCheck, preferences.isEnabled(NotificationPreferenceKey.APP_UPDATES));
		setChecked(connectivityCheck, preferences.isEnabled(NotificationPreferenceKey.CONNECTIVITY_STATUS));
		setChecked(taskOverdueBannerCheck, preferences.isEnabled(NotificationPreferenceKey.TASK_DUE_OVERDUE_BANNER));
		setChecked(taskDueTodayBannerCheck, preferences.isEnabled(NotificationPreferenceKey.TASK_DUE_TODAY_BANNER));
		setChecked(appUpdatesBannerCheck, preferences.isEnabled(NotificationPreferenceKey.APP_UPDATES_BANNER));
		setChecked(connectivityBannerCheck, preferences.isEnabled(NotificationPreferenceKey.CONNECTIVITY_BANNER));
		if (notificationSettingsStatusLabel != null) {
			notificationSettingsStatusLabel.setText("");
		}
	}

	private Map<NotificationPreferenceKey, Boolean> selectedValues() {
		Map<NotificationPreferenceKey, Boolean> values = new EnumMap<>(NotificationPreferenceKey.class);
		values.put(NotificationPreferenceKey.TASK_ASSIGNED_TO_ME, isChecked(taskAssignedToMeCheck));
		values.put(NotificationPreferenceKey.TASK_DUE_OVERDUE, isChecked(taskOverdueCheck));
		values.put(NotificationPreferenceKey.TASK_DUE_TODAY, isChecked(taskDueTodayCheck));
		values.put(NotificationPreferenceKey.TASK_DUE_TOMORROW, isChecked(taskDueTomorrowCheck));
		values.put(NotificationPreferenceKey.APP_UPDATES, isChecked(appUpdatesCheck));
		values.put(NotificationPreferenceKey.CONNECTIVITY_STATUS, isChecked(connectivityCheck));
		values.put(NotificationPreferenceKey.TASK_DUE_OVERDUE_BANNER, isChecked(taskOverdueBannerCheck));
		values.put(NotificationPreferenceKey.TASK_DUE_TODAY_BANNER, isChecked(taskDueTodayBannerCheck));
		values.put(NotificationPreferenceKey.APP_UPDATES_BANNER, isChecked(appUpdatesBannerCheck));
		values.put(NotificationPreferenceKey.CONNECTIVITY_BANNER, isChecked(connectivityBannerCheck));
		return values;
	}

	private static boolean isChecked(CheckBox checkBox) {
		return checkBox != null && checkBox.isSelected();
	}

	private static void setChecked(CheckBox checkBox, boolean selected) {
		if (checkBox != null) {
			checkBox.setSelected(selected);
		}
	}

	private boolean isAdminUser() {
		return appState != null && appState.isAdmin();
	}

	@FXML
	private VBox userAdministrationSection;

	private void updateAdminControlsVisibility() {
		boolean visible = isAdminUser();
		if (auditSection != null) {
			auditSection.setVisible(visible);
			auditSection.setManaged(visible);
		}
		if (caseStatusAdministrationSection != null) {
			caseStatusAdministrationSection.setVisible(visible);
			caseStatusAdministrationSection.setManaged(visible);
		}
		if (practiceAreaAdministrationSection != null) {
			practiceAreaAdministrationSection.setVisible(visible);
			practiceAreaAdministrationSection.setManaged(visible);
		}
		if (linkTypeAdministrationSection != null) {
			linkTypeAdministrationSection.setVisible(visible);
			linkTypeAdministrationSection.setManaged(visible);
		}
		if (requestAdministrationSection != null) {
			requestAdministrationSection.setVisible(visible);
			requestAdministrationSection.setManaged(visible);
		}
		if (userAdministrationSection != null) {
			userAdministrationSection.setVisible(visible);
			userAdministrationSection.setManaged(visible);
		}
	}
}
