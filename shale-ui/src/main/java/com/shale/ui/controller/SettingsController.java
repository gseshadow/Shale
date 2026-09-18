package com.shale.ui.controller;

import static com.shale.core.service.ContactServicePort.*;

import com.shale.core.dto.EffectiveCaseDateTypeDto;
import com.shale.core.dto.CaseDateSemanticRoleMappingDto;
import com.shale.core.dto.MaterialTypeDto;
import com.shale.core.dto.RequestMethodDto;
import com.shale.core.dto.RequestStatusDto;
import com.shale.core.service.CaseServicePort;
import com.shale.core.service.MaterialRequestServicePort;
import com.shale.core.service.ContactServicePort;
import com.shale.core.service.OrganizationServicePort;
import com.shale.data.dao.UserDao;
import com.shale.ui.component.dialog.AppDialogs;
import com.shale.ui.component.spellcheck.UserDictionarySession;
import com.shale.ui.component.SettingsManagementRow;
import com.shale.ui.notification.NotificationPreferenceKey;
import com.shale.ui.notification.NotificationPreferences;
import com.shale.ui.notification.NotificationPreferencesService;
import com.shale.ui.services.UiRuntimeBridge;
import com.shale.ui.state.AppState;
import com.shale.ui.util.ActionButtonFactory;
import com.shale.ui.util.ControlStyles;
import com.shale.ui.util.ControlAvailability;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Button;
import javafx.scene.Node;
import javafx.stage.Window;
import javafx.scene.layout.HBox;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SettingsController {
	private static final Logger LOG = LoggerFactory.getLogger(SettingsController.class);
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
	private SettingsManagementRow auditLogRow;
	@FXML
	private SettingsManagementRow caseStatusesRow;
	private Button manageCaseStatusesButton;
	@FXML
	private SettingsManagementRow practiceAreasRow;
	private Button managePracticeAreasButton;
	@FXML
	private SettingsManagementRow linkTypesRow;
	private Button manageLinkTypesButton;
	@FXML
	private SettingsManagementRow caseDatesRow;
	@FXML private SettingsManagementRow caseDateMappingsRow;
	@FXML private VBox caseDateRoleMappingsContent;
	private Button manageCaseDateTypesButton;
	@FXML
	private VBox caseDateRoleMappingsContainer;
	@FXML
	private Label caseDateMappingStatusLabel;
	@FXML
	private SettingsManagementRow requestFieldsRow;
	private Button manageRequestFieldsButton;
	@FXML
	private SettingsManagementRow contactClassificationsRow;
	@FXML private SettingsManagementRow caseTeamRolesRow;
	private Button manageCaseTeamRolesButton;
	private Button manageContactClassificationsButton;
	@FXML private SettingsManagementRow organizationTypesRow;
	private Button manageOrganizationTypesButton;
	@FXML private SettingsManagementRow customDictionaryRow;
	private Button manageCustomDictionaryButton;
	@FXML private SettingsManagementRow notificationPreferencesRow;
	@FXML private VBox notificationPreferencesContent;
	@FXML private SettingsManagementRow userManagementRow;
	@FXML private VBox personalGroup, caseConfigurationGroup, requestConfigurationGroup,
			contactOrganizationConfigurationGroup, administrationGroup;

	private NotificationPreferencesService notificationPreferencesService;
	private AppState appState;
	private CaseServicePort caseService;
	private MaterialRequestServicePort materialRequestService;
	private ContactServicePort contactService;
	private OrganizationServicePort organizationService;
	private UserDao userDao;
	private Runnable onOpenAuditLog;
	private boolean fxmlReady;
	private UiRuntimeBridge runtimeBridge;
	private int caseDateMappingLoadGeneration;

	private final ExecutorService settingsLoadExecutor = Executors.newFixedThreadPool(4, runnable ->
	{
		Thread thread = new Thread(runnable, "settings-section-loader");
		thread.setDaemon(true);
		return thread;
	});

	@FXML
	private void initialize() {
		fxmlReady = true;
		bindDirectoryRows();
		configureSettingsSemanticButtons();
		configureContactClassifications();
		configureOrganizationTypes();
		configureCaseTeamRoles();
		configureCustomDictionary();
		updateAdminControlsVisibility();
		if (notificationPreferencesService != null) {
			loadFromPreferences();
		}
	}

	private void bindDirectoryRows() {
		manageCustomDictionaryButton = bind(customDictionaryRow, this::onManageCustomDictionary);
		manageCaseStatusesButton = bind(caseStatusesRow, this::onManageCaseStatuses);
		managePracticeAreasButton = bind(practiceAreasRow, this::onManagePracticeAreas);
		manageLinkTypesButton = bind(linkTypesRow, this::onManageLinkTypes);
		manageCaseTeamRolesButton = bind(caseTeamRolesRow, this::onManageCaseTeamRoles);
		manageCaseDateTypesButton = bind(caseDatesRow, this::onManageCaseDateTypes);
		manageRequestFieldsButton = bind(requestFieldsRow, this::onManageRequestFields);
		manageContactClassificationsButton = bind(contactClassificationsRow, this::onManageContactClassifications);
		manageOrganizationTypesButton = bind(organizationTypesRow, this::onManageOrganizationTypes);
		viewAuditLogButton = bind(auditLogRow, this::onViewAuditLog);
		bind(notificationPreferencesRow, event -> toggleInline(notificationPreferencesContent, notificationPreferencesRow, false));
		bind(userManagementRow, this::onManageUsers);
		bind(caseDateMappingsRow, event -> {
			boolean opening = !caseDateRoleMappingsContent.isManaged();
			toggleInline(caseDateRoleMappingsContent, caseDateMappingsRow, false);
			if (opening) loadCaseDateRoleMappingsAsync(null);
		});
	}

	private static Button bind(SettingsManagementRow row, javafx.event.EventHandler<ActionEvent> handler) {
		row.setOnAction(handler);
		return row.getActionButton();
	}

	private void toggleInline(VBox content, SettingsManagementRow row, boolean ignored) {
		boolean show = !content.isManaged();
		setVisibleManaged(content, show);
		row.setExpanded(show);
		row.setActionText(show ? "Close" : "Open");
	}

	private void configureCustomDictionary() {
		if (manageCustomDictionaryButton == null) return;
		boolean available = appState != null && appState.getShaleClientId() != null
				&& appState.getShaleClientId() > 0 && appState.getUserId() != null && appState.getUserId() > 0;
		ControlAvailability.apply(manageCustomDictionaryButton, customDictionaryRow, available, this::onManageCustomDictionary);
	}

	@FXML private void onManageCustomDictionary(ActionEvent event) {
		if (appState == null || appState.getShaleClientId() == null || appState.getShaleClientId() <= 0
				|| appState.getUserId() == null || appState.getUserId() <= 0) return;
		new CustomDictionaryManagementLauncher(UserDictionarySession.current(), settingsLoadExecutor)
				.open(settingsWindow(event), result -> { });
	}

	private void configureSettingsSemanticButtons() {
		ControlStyles.apply(applyNotificationPreferencesButton, ControlStyles.Purpose.PRIMARY, ControlStyles.Size.STANDARD);
		ControlStyles.apply(resetNotificationPreferencesButton, ControlStyles.Purpose.SECONDARY, ControlStyles.Size.STANDARD);
		ControlStyles.apply(viewAuditLogButton, ControlStyles.Purpose.SECONDARY, ControlStyles.Size.STANDARD);
		if (manageCaseDateTypesButton != null) ControlStyles.apply(manageCaseDateTypesButton, ControlStyles.Purpose.SECONDARY, ControlStyles.Size.STANDARD);
		if (manageCaseStatusesButton != null) ControlStyles.apply(manageCaseStatusesButton, ControlStyles.Purpose.SECONDARY, ControlStyles.Size.STANDARD);
		if (manageLinkTypesButton != null) ControlStyles.apply(manageLinkTypesButton, ControlStyles.Purpose.SECONDARY, ControlStyles.Size.STANDARD);
		if (managePracticeAreasButton != null) ControlStyles.apply(managePracticeAreasButton, ControlStyles.Purpose.SECONDARY, ControlStyles.Size.STANDARD);
		if (manageRequestFieldsButton != null) ControlStyles.apply(manageRequestFieldsButton, ControlStyles.Purpose.SECONDARY, ControlStyles.Size.STANDARD);
		if (manageContactClassificationsButton != null) ControlStyles.apply(manageContactClassificationsButton, ControlStyles.Purpose.SECONDARY, ControlStyles.Size.STANDARD);
		if (manageCustomDictionaryButton != null) ControlStyles.apply(manageCustomDictionaryButton, ControlStyles.Purpose.SECONDARY, ControlStyles.Size.STANDARD);
	}

	public void init(NotificationPreferencesService notificationPreferencesService, AppState appState, Runnable onOpenAuditLog, CaseServicePort caseService,
			MaterialRequestServicePort materialRequestService, ContactServicePort contactService, UserDao userDao, UiRuntimeBridge runtimeBridge) {
		this.notificationPreferencesService = Objects.requireNonNull(notificationPreferencesService, "notificationPreferencesService");
		this.appState = Objects.requireNonNull(appState, "appState");
		this.onOpenAuditLog = Objects.requireNonNull(onOpenAuditLog, "onOpenAuditLog");
		this.runtimeBridge = runtimeBridge;
		this.caseService = Objects.requireNonNull(caseService, "caseService");
		this.materialRequestService = Objects.requireNonNull(materialRequestService, "materialRequestService");
		this.contactService = Objects.requireNonNull(contactService, "contactService");
		this.userDao = Objects.requireNonNull(userDao, "userDao");
		if (fxmlReady) {
			configureCustomDictionary();
			configureContactClassifications();
			configureCaseTeamRoles();
			loadFromPreferences();
			updateAdminControlsVisibility();
		}
	}

	public void init(NotificationPreferencesService notificationPreferencesService, AppState appState, Runnable onOpenAuditLog, CaseServicePort caseService,
			MaterialRequestServicePort materialRequestService, ContactServicePort contactService, OrganizationServicePort organizationService,
			UserDao userDao, UiRuntimeBridge runtimeBridge) {
		this.organizationService=Objects.requireNonNull(organizationService,"organizationService");
		init(notificationPreferencesService,appState,onOpenAuditLog,caseService,materialRequestService,contactService,userDao,runtimeBridge);
		if(fxmlReady)configureOrganizationTypes();
	}

	private void configureCaseTeamRoles() {
		if(manageCaseTeamRolesButton!=null){ControlStyles.apply(manageCaseTeamRolesButton,ControlStyles.Purpose.SECONDARY,ControlStyles.Size.STANDARD);manageCaseTeamRolesButton.setDisable(caseService==null||appState==null||!appState.isAdmin());}
	}

	@FXML private void onManageCaseTeamRoles(ActionEvent event){if(!requireAdminLookupManagement("Case Team Roles")||caseService==null)return;new CaseTeamRoleManagementLauncher(caseService,settingsLoadExecutor).open(settingsWindow(event),requireTenantId(),requireActorUserId(),result->{ });}

	private void configureContactClassifications() {
		if (manageContactClassificationsButton != null)
			manageContactClassificationsButton.setDisable(contactService == null || appState == null || !appState.isAdmin());
	}

	@FXML
	private void onManageContactClassifications(ActionEvent event) {
		if (!requireAdminLookupManagement("Contact Classifications") || contactService == null) return;
		new ContactClassificationManagementLauncher(contactService, settingsLoadExecutor)
				.open(settingsWindow(event), requireTenantId(), requireActorUserId(), result -> { });
	}

	private void configureOrganizationTypes(){
		if(manageOrganizationTypesButton!=null) {
			ControlStyles.apply(manageOrganizationTypesButton,ControlStyles.Purpose.SECONDARY,ControlStyles.Size.STANDARD);
			manageOrganizationTypesButton.setDisable(organizationService==null||appState==null||!appState.isAdmin());
		}
	}

	@FXML private void onManageOrganizationTypes(ActionEvent event) {
		if(!requireAdminLookupManagement("Organization Types")||organizationService==null)return;
		new OrganizationTypeManagementLauncher(organizationService,settingsLoadExecutor)
				.open(settingsWindow(event),requireTenantId(),requireActorUserId(),result->{ });
	}

	/**
	 * Compatibility overload for existing embedders that do not yet supply Contact
	 * administration.
	 */
	public void init(NotificationPreferencesService notificationPreferencesService, AppState appState,
			Runnable onOpenAuditLog, CaseServicePort caseService, MaterialRequestServicePort materialRequestService,
			UserDao userDao, UiRuntimeBridge runtimeBridge) {
		init(notificationPreferencesService, appState, onOpenAuditLog, caseService, materialRequestService,
				noOpContactService(), userDao, runtimeBridge);
	}

	public void init(NotificationPreferencesService notificationPreferencesService, AppState appState, Runnable onOpenAuditLog, CaseServicePort caseService, UserDao userDao,
			UiRuntimeBridge runtimeBridge) {
		init(notificationPreferencesService, appState, onOpenAuditLog, caseService, new MaterialRequestServicePort() {
			@Override
			public List<MaterialTypeDto> listEffectiveMaterialTypes(int tenantId) {
				return List.of();
			}

			@Override
			public List<RequestMethodDto> listEffectiveRequestMethods(int tenantId) {
				return List.of();
			}

			@Override
			public List<RequestStatusDto> listEffectiveRequestStatuses(int tenantId) {
				return List.of();
			}

			@Override
			public List<com.shale.core.dto.MaterialRequestSummaryDto> listMaterialRequests(long caseId, int tenantId) {
				return List.of();
			}

			@Override
			public Optional<com.shale.core.dto.MaterialRequestDetailDto> getMaterialRequest(long caseId, long materialRequestId, int tenantId, int actorUserId) {
				return Optional.empty();
			}

			@Override
			public List<com.shale.core.dto.MaterialRequestFollowUpDto> listFollowUps(long caseId, long materialRequestId, int tenantId, int actorUserId) {
				return List.of();
			}

			@Override
			public com.shale.core.dto.MaterialRequestDetailDto createMaterialRequest(CreateMaterialRequestCommand command) {
				throw new UnsupportedOperationException();
			}

			@Override
			public com.shale.core.dto.MaterialRequestDetailDto updateMaterialRequest(UpdateMaterialRequestCommand command) {
				throw new UnsupportedOperationException();
			}
		}, noOpContactService(), userDao, runtimeBridge);
	}

	private static ContactServicePort noOpContactService() {
		return new ContactServicePort() {
			@Override
			public List<ContactSummary> searchContacts(int t, String q, int l) {
				return List.of();
			}

			@Override
			public Optional<ContactDetail> getContactDetail(int c, int t) {
				return Optional.empty();
			}

			@Override
			public List<Definition> getEffectiveContactTypes(int t) {
				return List.of();
			}

			@Override
			public List<Definition> getEffectiveSpecialties(int t) {
				return List.of();
			}

			@Override
			public List<CredentialDefinition> getEffectiveCredentialDefinitions(int t) {
				return List.of();
			}

			@Override
			public List<AdministrationDefinition> listDefinitionsForAdministration(DefinitionCategory c, int t, int a) {
				throw new UnsupportedOperationException("Contact classification administration service is not configured.");
			}

			@Override
			public Optional<ClassificationProfile> getClassificationProfile(int c, int t) {
				return Optional.empty();
			}

			@Override
			public int createContact(CreateContactCommand c) {
				throw new UnsupportedOperationException();
			}

			@Override
			public boolean updateContact(UpdateContactCommand c) {
				return false;
			}

			@Override
			public boolean softDeleteContact(int c, int t, int a) {
				return false;
			}

			@Override
			public DefinitionMutationResult createDefinition(CreateDefinitionCommand c) {
				throw new UnsupportedOperationException();
			}

			@Override
			public DefinitionMutationResult updateDefinition(UpdateDefinitionCommand c) {
				throw new UnsupportedOperationException();
			}

			@Override
			public DefinitionMutationResult setDefinitionActive(DefinitionLifecycleCommand c) {
				throw new UnsupportedOperationException();
			}

			@Override
			public DefinitionMutationResult removeDefinition(DefinitionLifecycleCommand c) {
				throw new UnsupportedOperationException();
			}

			@Override
			public DefinitionMutationResult restoreDefinition(DefinitionLifecycleCommand c) {
				throw new UnsupportedOperationException();
			}

			@Override
			public AssignmentMutationResult assignClassification(AssignClassificationCommand c) {
				throw new UnsupportedOperationException();
			}

			@Override
			public AssignmentMutationResult removeClassification(AssignmentLifecycleCommand c) {
				throw new UnsupportedOperationException();
			}

			@Override
			public AssignmentMutationResult restoreClassification(AssignmentLifecycleCommand c) {
				throw new UnsupportedOperationException();
			}

			@Override
			public List<AssignmentMutationResult> reorderCredentials(ReorderCredentialsCommand c) {
				return List.of();
			}

			@Override
			public ContactProfileMutationResult updateContactProfile(UpdateContactProfileCommand c) {
				throw new UnsupportedOperationException();
			}

			@Override
			public DirectoryPage getContactDirectoryPage(int shaleClientId, int actorUserId, int page, int pageSize, String query, ContactServicePort.DirectoryFilters filters) {
				// TODO Auto-generated method stub
				return null;
			}
		};
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
		if (runtimeBridge == null)
			return;
		try {
			runtimeBridge.publishLinkTypeChanged(linkTypeId, requireTenantId(), requireActorUserId(), change);
			runtimeBridge.publishEntityAuditActivityAdded(null, requireTenantId(), requireActorUserId());
		} catch (RuntimeException ignored) {
		}
	}

	private void publishCaseDateTypeChanged(int typeId) {
		if (runtimeBridge == null)
			return;
		try {
			runtimeBridge.publishCaseDateTypeChanged(typeId, requireTenantId(), requireActorUserId());
		} catch (RuntimeException ignored) {
		}
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
		return auditLogRow == null || auditLogRow.getScene() == null ? null : auditLogRow.getScene().getWindow();
	}

	@FXML
	private void onManageRequestFields(ActionEvent event) {
		if (!requireAdminLookupManagement("Request Fields") || materialRequestService == null) return;
		new RequestDefinitionManagementLauncher(materialRequestService, settingsLoadExecutor)
				.open(settingsWindow(event), requireTenantId(), requireActorUserId(), result -> { });
	}

	@FXML
	private void onManageCaseDateTypes(ActionEvent event) {
		if (!requireAdminLookupManagement("Case Date Types") || caseService == null) return;
		new CaseDateTypeManagementLauncher(caseService, settingsLoadExecutor, this::publishCaseDateTypeChanged)
				.open(settingsWindow(event), requireTenantId(), requireActorUserId(), result -> {
					if (result.changed()) loadCaseDateRoleMappingsAsync("Case Date settings refreshed.");
				});
	}

	@FXML
	private void onManagePracticeAreas(ActionEvent event) {
		if (!requireAdminLookupManagement("Practice Areas") || caseService == null) return;
		new PracticeAreaManagementLauncher(caseService, settingsLoadExecutor)
				.open(settingsWindow(event), requireTenantId(), result -> { });
	}

	@FXML
	private void onManageCaseStatuses(ActionEvent event) {
		if (!requireAdminLookupManagement("Case Statuses") || caseService == null) return;
		new CaseStatusManagementLauncher(caseService, settingsLoadExecutor)
				.open(settingsWindow(event), requireTenantId(), requireActorUserId(), result -> { });
	}

	@FXML
	private void onManageLinkTypes(ActionEvent event) {
		if (!requireAdminLookupManagement("Link Types") || caseService == null) return;
		new LinkTypeManagementLauncher(caseService, settingsLoadExecutor, this::publishLinkTypeChanged, runtimeBridge)
				.open(settingsWindow(event), requireTenantId(), requireActorUserId(), result -> { });
	}

	private void loadCaseDateRoleMappingsAsync(String successMessage) {
		if (caseService == null || caseDateRoleMappingsContainer == null || !isAdminUser()) return;
		final int generation = ++caseDateMappingLoadGeneration;
		final int tenantId = requireTenantId(), actorUserId = requireActorUserId();
		caseDateRoleMappingsContainer.getChildren().setAll(loadingLabel("Loading protected mappings…"));
		settingsLoadExecutor.submit(() -> {
			try {
				List<EffectiveCaseDateTypeDto> types = caseService.listCaseDateTypesForAdministration(tenantId, actorUserId);
				List<CaseDateSemanticRoleMappingDto> mappings = caseService.listCaseDateSemanticRoleMappings(tenantId, actorUserId);
				Platform.runLater(() -> { if (generation == caseDateMappingLoadGeneration) { renderCaseDateRoleMappings(mappings, types); setCaseDateMappingMessage(successMessage); } });
			} catch (RuntimeException ex) {
				LOG.error("Case Date protected mapping load failed tenantId={} actorId={}", tenantId, actorUserId, ex);
				Platform.runLater(() -> { if (generation == caseDateMappingLoadGeneration) caseDateRoleMappingsContainer.getChildren().setAll(loadingLabel("Protected mappings could not be loaded.")); });
			}
		});
	}

	private Label loadingLabel(String message) {
		Label label = new Label(message);
		label.getStyleClass().add("search-summary-text");
		label.setWrapText(true);
		return label;
	}

	private void renderCaseDateRoleMappings(List<CaseDateSemanticRoleMappingDto> mappings, List<EffectiveCaseDateTypeDto> types) {
		if (caseDateRoleMappingsContainer == null)
			return;
		List<EffectiveCaseDateTypeDto> eligible = types.stream()
				.filter(type -> type.shaleClientId() != null && type.active() && !type.deleted())
				.toList();
		FlowPane section = new FlowPane(10, 10);
		section.setPrefWrapLength(760);
		if (eligible.isEmpty()) {
			Label empty = new Label("No custom types are available for overrides.");
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
		VBox row = new VBox(6);
		row.getStyleClass().addAll("shale-entity-card", "shale-entity-card-compact", "case-date-built-in-card", "shale-density-compact");
		row.setMinWidth(240);
		row.setPrefWidth(340);
		row.setMaxWidth(380);
		Label role = new Label(mapping.roleName());
		role.getStyleClass().add("app-dialog-field-label");
		role.setWrapText(true);
		HBox badges = new HBox(6, metadataPill("Built-in"), metadataPill("Required"));
		Label effective = new Label(mapping.tenantOverride()
				? mapping.effectiveTypeName() + " is currently used for this required date."
				: "Using the built-in default.");
		effective.getStyleClass().add("search-summary-text");
		effective.setWrapText(true);
		row.getChildren().addAll(role, badges, effective);

		FlowPane actions = new FlowPane(8, 6);
		actions.setPrefWrapLength(520);
		if (!eligible.isEmpty()) {
			ComboBox<EffectiveCaseDateTypeDto> selector = ControlStyles.formControl(new ComboBox<>());
			selector.setPromptText("Select a custom Case Date Type");
			selector.setMaxWidth(360);
			selector.getItems().setAll(eligible);
			selector.setConverter(new javafx.util.StringConverter<>() {
				@Override
				public String toString(EffectiveCaseDateTypeDto value) {
					return value == null ? "" : value.name();
				}

				@Override
				public EffectiveCaseDateTypeDto fromString(String value) {
					return null;
				}
			});
			eligible.stream().filter(type -> type.id() == mapping.effectiveTypeId()).findFirst().ifPresent(selector::setValue);
			Button save = ActionButtonFactory.semantic(mapping.tenantOverride() ? "Change" : "Save override", event ->
			{
				EffectiveCaseDateTypeDto selected = selector.getValue();
				if (selected == null) {
					setCaseDateMappingMessage("Select an eligible tenant Case Date Type.");
					return;
				}
				try {
					caseService.saveCaseDateSemanticRoleMapping(new CaseServicePort.SaveCaseDateSemanticRoleMappingCommand(
							requireTenantId(), requireActorUserId(), mapping.roleKey(), selected.id(),
							mapping.tenantMappingId(), mapping.tenantMappingRowVer()));
					publishCaseDateTypeChanged(selected.id());
					loadCaseDateRoleMappingsAsync("Protected role mapping saved.");
				} catch (RuntimeException ex) {
					showCaseDateMappingError(ex);
				}
			}, ControlStyles.Purpose.PRIMARY, ControlStyles.Size.SMALL);
			actions.getChildren().addAll(selector, save);
		}
		if (mapping.tenantOverride()) {
			Button reset = ActionButtonFactory.semantic("Reset to global default", event ->
			{
				try {
					caseService.resetCaseDateSemanticRoleMapping(new CaseServicePort.ResetCaseDateSemanticRoleMappingCommand(
							requireTenantId(), requireActorUserId(), mapping.roleKey(), mapping.tenantMappingId(),
							mapping.tenantMappingRowVer()));
					publishCaseDateTypeChanged(mapping.effectiveTypeId());
					loadCaseDateRoleMappingsAsync("Protected role mapping reset to the global default.");
				} catch (RuntimeException ex) {
					showCaseDateMappingError(ex);
				}
			}, ControlStyles.Purpose.SECONDARY, ControlStyles.Size.SMALL);
			actions.getChildren().add(reset);
		}
		if (!actions.getChildren().isEmpty())
			row.getChildren().add(actions);
		return row;
	}

	private Label metadataPill(String text) {
		Label label = new Label(text == null || text.isBlank() ? "—" : text);
		label.getStyleClass().add("shale-indicator-chip");
		return label;
	}

	private void setCaseDateMappingMessage(String message) {
		if (caseDateMappingStatusLabel != null) {
			caseDateMappingStatusLabel.setText(message == null ? "" : message);
		}
	}

	private void showCaseDateMappingError(RuntimeException error) {
		LOG.error("Protected Case Date mapping mutation failed", error);
		Window owner = caseDateRoleMappingsContainer == null || caseDateRoleMappingsContainer.getScene() == null
				? null : caseDateRoleMappingsContainer.getScene().getWindow();
		AppDialogs.showError(owner, "Protected Case Date Mappings",
				"The protected Case Date mapping could not be saved. Contact an administrator if the problem continues.");
	}

	private boolean requireAdminLookupManagement(String sectionName) {
		if (isAdminUser()) {
			return true;
		}
		return false;
	}

	private int requireActorUserId() {
		Integer id = appState == null ? null : appState.getUserId();
		if (id == null || id <= 0)
			throw new IllegalStateException("No actor user is selected.");
		return id;
	}

	private int requireTenantId() {
		Integer id = appState == null ? null : appState.getShaleClientId();
		if (id == null || id <= 0)
			throw new IllegalStateException("No tenant is selected.");
		return id;
	}

	@FXML
	private void onManageUsers(ActionEvent event) {
		if (!hasAdminContext() || userDao == null) return;
		final int tenantId = requireTenantId();
		final int actorUserId = requireActorUserId();
		new UserManagementLauncher(userDao, settingsLoadExecutor)
				.open(settingsWindow(event), tenantId, actorUserId, result -> { });
	}

	private static String rootMessage(Throwable ex) {
		Throwable current = ex;
		while (current.getCause() != null && current.getCause() != current) current = current.getCause();
		String message = current.getMessage();
		return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
	}

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


	private boolean hasAdminContext() {
		return isAdminUser() && appState.getShaleClientId() != null && appState.getShaleClientId() > 0
				&& appState.getUserId() != null && appState.getUserId() > 0;
	}

	private void updateAdminControlsVisibility() {
		boolean admin = isAdminUser();
		setVisibleManaged(auditLogRow, admin);
		setVisibleManaged(caseDateMappingsRow, admin && caseService != null);
		setVisibleManaged(userManagementRow, hasAdminContext() && userDao != null);
		if (!admin) {
			setVisibleManaged(caseDateRoleMappingsContent, false);
		}
		boolean context = hasAdminContext();
		ControlAvailability.apply(managePracticeAreasButton, practiceAreasRow,
				context && caseService != null, this::onManagePracticeAreas);
		ControlAvailability.apply(manageCaseStatusesButton, caseStatusesRow,
				context && caseService != null, this::onManageCaseStatuses);
		ControlAvailability.apply(manageLinkTypesButton, linkTypesRow,
				context && caseService != null, this::onManageLinkTypes);
		ControlAvailability.apply(manageCaseTeamRolesButton, caseTeamRolesRow,
				context && caseService != null, this::onManageCaseTeamRoles);
		ControlAvailability.apply(manageCaseDateTypesButton, caseDatesRow,
				context && caseService != null, this::onManageCaseDateTypes);
		ControlAvailability.apply(manageRequestFieldsButton, requestFieldsRow,
				context && materialRequestService != null, this::onManageRequestFields);
		ControlAvailability.apply(manageContactClassificationsButton, contactClassificationsRow,
				context && contactService != null, this::onManageContactClassifications);
		ControlAvailability.apply(manageOrganizationTypesButton, organizationTypesRow,
				context && organizationService != null, this::onManageOrganizationTypes);
		setVisibleManaged(caseConfigurationGroup, hasManagedChild(caseConfigurationGroup));
		setVisibleManaged(requestConfigurationGroup, hasManagedChild(requestConfigurationGroup));
		setVisibleManaged(contactOrganizationConfigurationGroup, hasManagedChild(contactOrganizationConfigurationGroup));
		setVisibleManaged(administrationGroup, hasManagedChild(administrationGroup));
	}

	private static boolean hasManagedChild(VBox group) {
		return group != null && group.getChildren().stream().skip(1).anyMatch(Node::isManaged);
	}

	private static void setVisibleManaged(Node node, boolean visible) {
		if (node != null) {
			node.setVisible(visible);
			node.setManaged(visible);
		}
	}
}
