package com.shale.ui.controller;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.shale.core.dto.CasePartyDto;
import com.shale.core.dto.CaseDetailDto;
import com.shale.core.dto.CaseOverviewDto;
import com.shale.core.dto.CaseTimelineEventDto;
import com.shale.core.dto.CaseUpdateDto;
import com.shale.core.dto.CaseTaskListItemDto;
import com.shale.core.dto.TaskDetailDto;
import com.shale.core.dto.TaskPriorityOptionDto;
import com.shale.core.semantics.RoleSemantics;
import com.shale.data.dao.CaseDao;
import com.shale.data.dao.ContactDao;
import com.shale.data.dao.OrganizationDao;
import com.shale.ui.component.ContactCard;
import com.shale.ui.component.OrganizationCard;
import com.shale.ui.component.factory.ContactCardFactory;
import com.shale.ui.document.CaseDocumentExportService;
import com.shale.ui.document.CaseDocumentFormat;
import com.shale.ui.document.CaseDocumentService;
import com.shale.ui.document.CaseDocumentType;
import com.shale.ui.document.GeneratedDocument;
import com.shale.ui.component.factory.OrganizationCardFactory;
import com.shale.ui.component.factory.PracticeAreaCardFactory;
import com.shale.ui.component.factory.PracticeAreaCardFactory.PracticeAreaCardModel;
import com.shale.ui.component.factory.StatusCardFactory;
import com.shale.ui.component.factory.StatusCardFactory.StatusCardModel;
import com.shale.ui.component.dialog.AppDialogs;
import com.shale.ui.component.dialog.ClientAssignmentDialog;
import com.shale.ui.component.dialog.ContactPickerDialog;
import com.shale.ui.component.dialog.CreateContactDialog;
import com.shale.ui.component.dialog.NewTaskDialog;
import com.shale.ui.component.factory.UserCardFactory;
import com.shale.ui.component.factory.TaskCardFactory;
import com.shale.ui.component.factory.UserCardFactory.UserCardModel;
import com.shale.ui.component.factory.UserCardFactory.Variant;
import com.shale.ui.component.dialog.TeamEditorDialog;
import com.shale.ui.component.dialog.TaskDetailDialog;
import com.shale.ui.services.CaseDetailService;
import com.shale.ui.services.CaseTaskService;
import com.shale.ui.services.UiRuntimeBridge;
import com.shale.ui.state.AppState;
import com.shale.ui.util.NavButtonStyler;
import com.shale.ui.util.UtcDateTimeDisplayFormatter;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ScrollPane;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * FXML controller for the Case scene and top-level coordinator for case view/edit flows.
 * <p>
 * Responsibility boundaries:
 * <ul>
 *   <li>{@code CaseOverviewRenderer}: overview/detail render orchestration</li>
 *   <li>{@code CaseOverviewEditor}: edit-mode lifecycle and draft-state transitions</li>
 *   <li>{@code CaseOverviewSaveCoordinator}: save validation/persist/publish pipeline</li>
 *   <li>{@code CaseOverviewLiveUpdateHandler}: remote case-update subscription/branching</li>
 *   <li>{@code CaseOverviewPickerCoordinator}: selectable overview relation pickers</li>
 *   <li>{@code CaseTeamCoordinator}: team loading/edit/render</li>
 *   <li>{@code CaseUpdatesPanelController}: case updates feed/compose/render</li>
 * </ul>
 */
public class CaseController {
	private static final String CASE_TASKS_SORT_DUE_ASC = "Due Date (Soonest)";
	private static final String CASE_TASKS_SORT_DUE_DESC = "Due Date (Latest)";
	private static final String CASE_TASKS_SORT_PRIORITY_ASC = "Priority (Low to High)";
	private static final String CASE_TASKS_SORT_PRIORITY_DESC = "Priority (High to Low)";

	// ----------------------------
	// FXML fields
	// ----------------------------

	@FXML
	private Label caseTitleLabel;
	@FXML
	private Label statusLabel;
	@FXML
	private StackPane statusHost;
	@FXML
	private StackPane assignedUserHost;
	@FXML
	private Label lastUpdatedLabel;
	@FXML
	private Button addTaskButton;
	@FXML
	private Button backToCasesButton;

	@FXML
	private VBox sectionButtonsBox;
	@FXML
	private BorderPane caseRootPane;
	@FXML
	private VBox overviewPane;
	@FXML
	private ScrollPane detailsScrollPane;
	@FXML
	private VBox detailsPane;
	@FXML
	private VBox tasksTabPane;
	@FXML
	private VBox genericPane;
	@FXML
	private Label contentTitleLabel;
	@FXML
	private Label genericTitleLabel;
	@FXML
	private TextArea placeholderTextArea;

	@FXML
	private ScrollPane organizationsScrollPane;
	@FXML
	private FlowPane organizationsFlow;
	@FXML
	private Label organizationsEmptyLabel;
	@FXML
	private Button addOrganizationButton;
	@FXML
	private ScrollPane timelineScrollPane;
	@FXML
	private VBox timelineListBox;
	@FXML
	private Label timelineEmptyLabel;

	@FXML
	private Label ovCaseStatusValue;
	@FXML
	private Label ovCaseNameValue;
	@FXML
	private TextField ovCaseNameEditor;
	@FXML
	private Label ovCaseNumberValue;
	@FXML
	private TextField ovCaseNumberEditor;

	@FXML
	private StackPane ovResponsibleAttorneyHost;
	@FXML
	private Button changeResponsibleAttorneyButton;

	@FXML
	private Button changeCallerButton;
	@FXML
	private StackPane ovCallerHost;

	@FXML
	private StackPane ovClientHost;

	// Legacy label; no longer used for rendering (Practice Area is a card in
	// ovPracticeAreaHost)
	@FXML
	private Label ovPracticeAreaValue;

	@FXML
	private Label ovTeamValue;
	@FXML
	private Label ovIntakeDateValue;
	@FXML
	private Label ovIncidentDateValue;
	@FXML
	private DatePicker ovIncidentDateEditor;
	@FXML
	private Label ovSolDateValue;
	@FXML
	private DatePicker ovSolDateEditor;

	@FXML
	private Label ovDescriptionValue;
	@FXML
	private TextArea ovDescriptionEditor;

	@FXML
	private Button deleteCaseButton;
	@FXML
	private Button editButton;
	@FXML
	private Button saveButton;
	@FXML
	private Button cancelButton;

	@FXML
	private MenuButton generateSummaryMenuButton;
	@FXML
	private MenuItem generateSummaryHtmlMenuItem;
	@FXML
	private MenuItem generateSummaryPdfMenuItem;
	@FXML
	private Label summaryGenerationStatusLabel;

	@FXML
	private Button detailsEditButton;
	@FXML
	private Button detailsSaveButton;
	@FXML
	private Button detailsCancelButton;

	@FXML
	private Label errorLabel;
	@FXML
	private Label detailsErrorLabel;
	@FXML
	private HBox remoteUpdateBanner;
	@FXML
	private Button reloadRemoteButton;

	@FXML
	private VBox tasksPanel;

	@FXML
	private FlowPane tasksTabFlow;
	@FXML
	private Label tasksTabEmptyLabel;
	@FXML
	private ChoiceBox<String> caseTasksSortChoice;

	@FXML
	private StackPane ovCaseStatusHost;
	@FXML
	private Button changeStatusButton;
	@FXML
	private Button changeClientButton;

	@FXML
	private StackPane ovPracticeAreaHost;
	@FXML
	private Button changePracticeAreaButton;

	@FXML
	private Button changeOpposingCounselButton;
	@FXML
	private StackPane ovOpposingCounselHost;

	@FXML
	private FlowPane teamFlow;
	@FXML
	private Button btnEditTeam;

	@FXML
	private TextArea caseUpdatesComposerArea;
	@FXML
	private Button submitCaseUpdateButton;
	@FXML
	private VBox caseUpdatesPane;
	@FXML
	private ScrollPane caseUpdatesScrollPane;
	@FXML
	private VBox caseUpdatesFeedBox;

	@FXML private Label detNameValue;
	@FXML private TextField detNameEditor;
	@FXML private Label detCaseNumberValue;
	@FXML private TextField detCaseNumberEditor;
	@FXML private Label detCaseStatusValue;
	@FXML private StackPane detCaseStatusHost;
	@FXML private HBox detCaseStatusEditorRow;
	@FXML private Button detChangeStatusButton;
	@FXML private Label detPracticeAreaIdValue;
	@FXML private StackPane detPracticeAreaHost;
	@FXML private HBox detPracticeAreaEditorRow;
	@FXML private Button detChangePracticeAreaButton;
	@FXML private Label detDescriptionValue;
	@FXML private TextArea detDescriptionEditor;
	@FXML private Label detCallerDateValue;
	@FXML private DatePicker detCallerDateEditor;
	@FXML private Label detCallerTimeValue;
	@FXML private TextField detCallerTimeEditor;
	@FXML private Label detAcceptedDateValue;
	@FXML private DatePicker detAcceptedDateEditor;
	@FXML private Label detClosedDateValue;
	@FXML private DatePicker detClosedDateEditor;
	@FXML private Label detDeniedDateValue;
	@FXML private DatePicker detDeniedDateEditor;
	@FXML private Label detDateOfMedicalNegligenceValue;
	@FXML private DatePicker detDateOfMedicalNegligenceEditor;
	@FXML private Label detDateMedicalNegligenceWasDiscoveredValue;
	@FXML private DatePicker detDateMedicalNegligenceWasDiscoveredEditor;
	@FXML private Label detDateOfInjuryValue;
	@FXML private DatePicker detDateOfInjuryEditor;
	@FXML private Label detStatuteOfLimitationsValue;
	@FXML private DatePicker detStatuteOfLimitationsEditor;
	@FXML private Label detTortNoticeDeadlineValue;
	@FXML private DatePicker detTortNoticeDeadlineEditor;
	@FXML private Label detDiscoveryDeadlineValue;
	@FXML private DatePicker detDiscoveryDeadlineEditor;
	@FXML private Label detClientEstateValue;
	@FXML private CheckBox detClientEstateEditor;
	@FXML private Label detOfficePrinterCodeValue;
	@FXML private TextField detOfficePrinterCodeEditor;
	@FXML private Label detMedicalRecordsReceivedValue;
	@FXML private CheckBox detMedicalRecordsReceivedEditor;
	@FXML private Label detFeeAgreementSignedValue;
	@FXML private CheckBox detFeeAgreementSignedEditor;
	@FXML private Label detDateFeeAgreementSignedValue;
	@FXML private DatePicker detDateFeeAgreementSignedEditor;
	@FXML private Label detAcceptedChronologyValue;
	@FXML private CheckBox detAcceptedChronologyEditor;
	@FXML private Label detAcceptedConsultantExpertSearchValue;
	@FXML private CheckBox detAcceptedConsultantExpertSearchEditor;
	@FXML private Label detAcceptedTestifyingExpertSearchValue;
	@FXML private CheckBox detAcceptedTestifyingExpertSearchEditor;
	@FXML private Label detAcceptedMedicalLiteratureValue;
	@FXML private CheckBox detAcceptedMedicalLiteratureEditor;
	@FXML private Label detAcceptedDetailValue;
	@FXML private TextArea detAcceptedDetailEditor;
	@FXML private Label detDeniedChronologyValue;
	@FXML private CheckBox detDeniedChronologyEditor;
	@FXML private Label detDeniedDetailValue;
	@FXML private TextArea detDeniedDetailEditor;
	@FXML private Label detSummaryValue;
	@FXML private TextArea detSummaryEditor;
	@FXML private Label detReceivedUpdatesValue;
	@FXML private CheckBox detReceivedUpdatesEditor;

	// ----------------------------
	// Constants
	// ----------------------------

	private static final int ROLE_RESPONSIBLE_ATTORNEY = RoleSemantics.ROLE_RESPONSIBLE_ATTORNEY;
	private static final int ROLE_ATTORNEY = RoleSemantics.ROLE_ATTORNEY;

	private static final java.util.Set<Integer> TEAM_ROLE_IDS = java.util.Set.copyOf(RoleSemantics.CASE_TEAM_ROLE_IDS);

	private static final List<String> SECTIONS = List.of(
			"Overview",
			"Parties",
			"Tasks",
			"Timeline",
			"Details",
			"Documents"
	);

	// ----------------------------
	// Dependencies / callbacks
	// ----------------------------

	private StatusCardFactory statusCardFactory;
	private Consumer<Integer> onOpenStatus;

	private UserCardFactory userCardFactory;
	private Consumer<Integer> onOpenUser;

	private ContactCardFactory contactCardFactory;
	private Consumer<Integer> onOpenContact;
	private Consumer<Integer> onOpenCase;

	private PracticeAreaCardFactory practiceAreaCardFactory;
	private Consumer<Integer> onOpenPracticeArea;

	private TaskCardFactory taskCardFactory;
	private final AtomicBoolean taskDetailDialogInFlight = new AtomicBoolean(false);
	private Consumer<Long> onOpenTask;

	private OrganizationCardFactory organizationCardFactory;
	private Consumer<Integer> onOpenOrganization;

	private CaseDao caseDao;
	private CaseDetailService caseDetailService;
	private CaseTaskService caseTaskService;
	private OrganizationDao organizationDao;
	private ContactDao contactDao;
	private AppState appState;
	private UiRuntimeBridge runtimeBridge;
	private CaseDocumentService caseDocumentService;
	private CaseDocumentExportService caseDocumentExportService;

	// ----------------------------
	// Controller state
	// ----------------------------

	private Integer caseId;
	private boolean overviewLoaded = false;
	private boolean editMode = false;
	private boolean detailsEditMode = false;

	private CaseDetailDto current;
	private CaseOverviewDto currentOverview;
	private Runnable onCaseDeleted;

	private CaseEditModel draft;

	// edit-mode drafts
	private Integer draftPrimaryStatusId;

	private Integer draftPrimaryCallerContactId;
	private String draftPrimaryCallerName;

	private List<CaseOverviewDto.ContactSummary> draftClientContacts;

	// Practice Area drafts
	private Integer draftPracticeAreaId;
	private String draftPracticeAreaName;
	private String draftPracticeAreaColor;

	// Responsible Attorney drafts
	private Integer draftResponsibleAttorneyUserId;

	// Opposing Counsel drafts
	private Integer draftPrimaryOpposingCounselContactId;
	private String draftPrimaryOpposingCounselName;

	// Team drafts
	private List<CaseDao.TeamAssignmentRow> draftTeamAssignments;

	private LocalDate draftIncidentDate;
	private LocalDate draftSolDate;
	private java.util.Map<Integer, CaseDao.UserRow> tenantUserById; // used to render team from draft
	private List<CasePartyDto> caseParties = List.of();
	private boolean partiesLoadedOnce = false;
	private List<CaseTaskListItemDto> caseTasks = List.of();
	private java.util.Map<Long, List<TaskCardFactory.AssignedUserModel>> caseTaskAssignedUsers = java.util.Map.of();
	private List<CaseUpdateDto> caseUpdates = List.of();
	private Long editingCaseUpdateId;
	private String editingCaseUpdateDraftText = "";
	private boolean savingCaseUpdateEdit = false;

	private final Map<String, Button> sectionButtons = new LinkedHashMap<>();
	private String activeSectionName = "Overview";
	private String initialSectionName = "Overview";
	private Consumer<String> onSectionNavigation;

	private final CaseOverviewRenderer overviewRenderer = new CaseOverviewRenderer();
	private final CaseOverviewEditor overviewEditor = new CaseOverviewEditor();
	private final CaseOverviewSaveCoordinator saveCoordinator = new CaseOverviewSaveCoordinator();
	private final CaseOverviewLiveUpdateHandler liveUpdateHandler = new CaseOverviewLiveUpdateHandler();
	private final CaseOverviewPickerCoordinator overviewPickerCoordinator = new CaseOverviewPickerCoordinator();
	private final CaseTeamCoordinator teamCoordinator = new CaseTeamCoordinator();
	private final CaseUpdatesPanelController updatesPanelController = new CaseUpdatesPanelController();
	private final CaseDetailsEditor detailsEditor = new CaseDetailsEditor();
	private final CaseDetailsSaveCoordinator detailsSaveCoordinator = new CaseDetailsSaveCoordinator();
	private CaseDetailsDraft detailsDraft;
	private CaseDetailsDraft detailsBaseline;
	private CaseDetailsDraft detailsLocalViewOverride;

	private record PartyRoleOption(long id, String label) {}
	private record PartyEntityOption(String entityType, Long id, String label) {}
	private record PartySideOption(String label, String value) {}
	private record PartyEditorResult(String entityType, Long entityId, long partyRoleId, String side, boolean primary, String notes) {}
	private record AddPartyDraft(
			String entityType,
			Long entityId,
			long partyRoleId,
			String side,
			boolean primary,
			String notes,
			boolean createNew,
			String contactFirstName,
			String contactLastName,
			String organizationName,
			Integer organizationTypeId) {}
	private record CallerPartySelection(Integer contactId, String displayName) {}
	private record OpposingCounselPartySelection(Integer contactId, String displayName) {}

	public void init(Integer caseId) {
		this.caseId = caseId;
		this.partiesLoadedOnce = false;
		refreshHeader();
		refreshOverviewPlaceholders();
	}

	public void init(Integer caseId, CaseDao caseDao, CaseDetailService caseDetailService, CaseTaskService caseTaskService, OrganizationDao organizationDao, ContactDao contactDao, AppState appState, UiRuntimeBridge runtimeBridge, Runnable onCaseDeleted) {
		this.caseId = caseId;
		this.partiesLoadedOnce = false;
		this.caseDao = caseDao;
		this.caseDetailService = caseDetailService;
		this.caseTaskService = caseTaskService;
		this.organizationDao = organizationDao;
		this.contactDao = contactDao;
		this.appState = appState;
		this.runtimeBridge = runtimeBridge;
		this.caseDocumentService = (caseDao == null || contactDao == null) ? null : new CaseDocumentService(caseDao, contactDao);
		this.caseDocumentExportService = this.caseDocumentService == null ? null : new CaseDocumentExportService(this.caseDocumentService);
		this.onCaseDeleted = onCaseDeleted;
		refreshHeader();
	}

	public void setOnOpenUser(Consumer<Integer> onOpenUser) {
		this.onOpenUser = onOpenUser;
		this.userCardFactory = new UserCardFactory(onOpenUser);
	}

	public void setOnOpenStatus(Consumer<Integer> onOpenStatus) {
		this.onOpenStatus = onOpenStatus;
		this.statusCardFactory = new StatusCardFactory(onOpenStatus);
	}

	public void setOnOpenContact(Consumer<Integer> onOpenContact) {
		this.onOpenContact = onOpenContact;
		this.contactCardFactory = new ContactCardFactory(onOpenContact);
	}

	public void setOnOpenCase(Consumer<Integer> onOpenCase) {
		this.onOpenCase = onOpenCase;
		this.taskCardFactory = buildTaskCardFactory(this::openTask);
	}

	public void setOnSectionNavigation(Consumer<String> onSectionNavigation) {
		this.onSectionNavigation = onSectionNavigation;
	}

	public void setInitialSection(String sectionKey) {
		String resolved = fromSectionKey(sectionKey);
		if (resolved != null) {
			this.initialSectionName = resolved;
		}
	}

	/** Optional - if you don’t set this, card click will Sys.out for now */
	public void setOnOpenPracticeArea(Consumer<Integer> onOpenPracticeArea) {
		this.onOpenPracticeArea = onOpenPracticeArea;
		this.practiceAreaCardFactory = new PracticeAreaCardFactory(onOpenPracticeArea);
	}


	public void setOnOpenTask(Consumer<Long> onOpenTask) {
		this.onOpenTask = onOpenTask;
		this.taskCardFactory = buildTaskCardFactory(this::openTask);
	}

	public void setOnOpenOrganization(Consumer<Integer> onOpenOrganization) {
		this.onOpenOrganization = onOpenOrganization;
		this.organizationCardFactory = new OrganizationCardFactory(onOpenOrganization);
	}

	private TaskCardFactory buildTaskCardFactory(Consumer<Long> openTaskHandler) {
		Consumer<Long> resolvedOpenHandler = openTaskHandler == null ? taskId -> {} : openTaskHandler;
		return new TaskCardFactory(
				resolvedOpenHandler,
				this::onToggleTaskComplete,
				onOpenCase,
				onOpenUser);
	}

	// ----------------------------
	// Initialization
	// ----------------------------

	@FXML
	private void initialize() {
		refreshHeader();
		refreshOverviewPlaceholders();
		setupSections();
		setupRelatedEntitiesLayout();
		wireEditButtons();
		wireDetailsEditButtons();
		setEditMode(false);
		detailsEditor.setEditMode(false);
		clearError();
		subscribeLiveCaseUpdates();

		if (changeResponsibleAttorneyButton != null)
			changeResponsibleAttorneyButton.setOnAction(e -> onChangeResponsibleAttorney());
		if (changeStatusButton != null)
			changeStatusButton.setOnAction(e -> onChangeStatus());
		if (changeCallerButton != null)
			changeCallerButton.setOnAction(e -> onChangeCaller());
		if (changeClientButton != null)
			changeClientButton.setOnAction(e -> onManageClients());
		if (changePracticeAreaButton != null)
			changePracticeAreaButton.setOnAction(e -> onChangePracticeArea());
		if (detChangeStatusButton != null)
			detChangeStatusButton.setOnAction(e -> onDetailsChangeStatus());
		if (detChangePracticeAreaButton != null)
			detChangePracticeAreaButton.setOnAction(e -> onDetailsChangePracticeArea());
		if (changeOpposingCounselButton != null)
			changeOpposingCounselButton.setOnAction(e -> onChangeOpposingCounsel());
		if (btnEditTeam != null)
			btnEditTeam.setOnAction(e -> onEditTeam());
		if (submitCaseUpdateButton != null)
			submitCaseUpdateButton.setOnAction(e -> onSubmitCaseUpdate());
		if (deleteCaseButton != null) {
			deleteCaseButton.setOnAction(e -> onDeleteCase());
			setVisibleManaged(deleteCaseButton, false);
		}
		if (addTaskButton != null)
			addTaskButton.setOnAction(e -> onAddTask());
		if (generateSummaryHtmlMenuItem != null)
			generateSummaryHtmlMenuItem.setOnAction(e -> onGenerateSummaryHtml());
		if (generateSummaryPdfMenuItem != null)
			generateSummaryPdfMenuItem.setOnAction(e -> onGenerateSummaryPdf());
		if (generateSummaryMenuButton != null && generateSummaryHtmlMenuItem == null && generateSummaryPdfMenuItem == null)
			generateSummaryMenuButton.setOnAction(e -> onGenerateSummaryHtml());
		if (caseTasksSortChoice != null) {
			caseTasksSortChoice.getItems().setAll(
					CASE_TASKS_SORT_DUE_ASC,
					CASE_TASKS_SORT_DUE_DESC,
					CASE_TASKS_SORT_PRIORITY_ASC,
					CASE_TASKS_SORT_PRIORITY_DESC);
			caseTasksSortChoice.getSelectionModel().select(CASE_TASKS_SORT_DUE_ASC);
			caseTasksSortChoice.getSelectionModel().selectedItemProperty()
					.addListener((obs, oldV, newV) -> refreshCaseTasks());
		}
		if (addOrganizationButton != null)
			addOrganizationButton.setOnAction(e -> onAddRelatedEntity());
		if (caseUpdatesComposerArea != null) {
			caseUpdatesComposerArea.setOnKeyPressed(e ->
			{
				if (e.isControlDown() && e.getCode() == javafx.scene.input.KeyCode.ENTER) {
					onSubmitCaseUpdate();
					e.consume();
				}
			});
		}
	}


	private void onGenerateSummaryPdf() {
		generateAndOpenSummary(CaseDocumentFormat.PDF);
	}

	private void onGenerateSummaryHtml() {
		generateAndOpenSummary(CaseDocumentFormat.HTML);
	}

	private void generateAndOpenSummary(CaseDocumentFormat format) {
		if (caseId == null || caseId <= 0) {
			showError("Load a case before generating a summary.");
			return;
		}
		if (appState == null || appState.getShaleClientId() == null || appState.getShaleClientId() <= 0) {
			showError("Unable to resolve tenant context for summary generation.");
			return;
		}
		if (caseDocumentExportService == null) {
			showError("Summary generation service is unavailable.");
			return;
		}

		final int tenantId = appState.getShaleClientId();
		final int activeCaseId = caseId;
		final String loadingText = format == CaseDocumentFormat.PDF ? "Generating PDF summary..." : "Generating HTML summary...";
		setSummaryGenerationBusy(true, loadingText);

		Task<GeneratedDocument> task = new Task<>() {
			@Override
			protected GeneratedDocument call() throws Exception {
				System.out.println("[Document] Generating " + format + " CASE_SUMMARY for caseId=" + activeCaseId + " shaleClientId=" + tenantId);
				return caseDocumentExportService.exportCaseSummary(activeCaseId, tenantId, CaseDocumentType.CASE_SUMMARY, format);
			}
		};

		task.setOnSucceeded(event -> {
			setSummaryGenerationBusy(false, null);
			GeneratedDocument generated = task.getValue();
			try {
				boolean opened = runtimeBridge != null && runtimeBridge.openPath(generated.path());
				if (!opened) {
					throw new IllegalStateException("Unable to open generated summary preview.");
				}
				System.out.println("[Document] Generated case summary " + format + " at " + generated.path());
			} catch (Exception ex) {
				System.err.println("[Document] Failed to open generated case summary " + format + ": " + ex.getMessage());
				showSummaryGenerationError("Could not open generated case summary.");
			}
		});

		task.setOnFailed(event -> {
			setSummaryGenerationBusy(false, null);
			Throwable ex = task.getException();
			System.err.println("[Document] Failed to generate case summary " + format + ": " + (ex == null ? "<unknown>" : ex.getMessage()));
			showSummaryGenerationError("Could not generate case summary " + format.name().toLowerCase() + ". Please try again.");
		});

		Thread worker = new Thread(task, "case-summary-export-" + format.name().toLowerCase() + "-" + activeCaseId);
		worker.setDaemon(true);
		worker.start();
	}

	private void setSummaryGenerationBusy(boolean busy, String message) {
		if (generateSummaryMenuButton != null) {
			generateSummaryMenuButton.setDisable(busy);
		}
		if (summaryGenerationStatusLabel != null) {
			boolean show = busy && message != null && !message.isBlank();
			summaryGenerationStatusLabel.setText(show ? message : "");
			summaryGenerationStatusLabel.setVisible(show);
			summaryGenerationStatusLabel.setManaged(show);
		}
	}

	private void showSummaryGenerationError(String message) {
		showError(message);
		Window owner = caseRootPane == null || caseRootPane.getScene() == null ? null : caseRootPane.getScene().getWindow();
		AppDialogs.showError(owner, "Case Summary", message);
	}

	private void setupRelatedEntitiesLayout() {
		if (organizationsScrollPane == null || organizationsFlow == null) {
			return;
		}

		Runnable refreshWrapLength = () -> {
			double viewportWidth = organizationsScrollPane.getViewportBounds().getWidth();
			double contentWidth = viewportWidth > 0 ? viewportWidth : organizationsScrollPane.getWidth();
			double wrapWidth = Math.max(0, contentWidth - 8);
			organizationsFlow.setPrefWrapLength(wrapWidth);
			organizationsFlow.setPrefWidth(wrapWidth);
		};

		organizationsScrollPane.viewportBoundsProperty().addListener((obs, oldBounds, newBounds) -> refreshWrapLength.run());
		organizationsScrollPane.widthProperty().addListener((obs, oldWidth, newWidth) -> refreshWrapLength.run());
		runOnFx(refreshWrapLength);
	}

	private void wireEditButtons() {
		if (editButton != null)
			editButton.setOnAction(e -> onEdit());
		if (saveButton != null)
			saveButton.setOnAction(e -> onSave());
		if (cancelButton != null)
			cancelButton.setOnAction(e -> onCancel());
		if (reloadRemoteButton != null)
			reloadRemoteButton.setOnAction(e -> onReloadRemote());
	}

	private void wireDetailsEditButtons() {
		if (detailsEditButton != null)
			detailsEditButton.setOnAction(e -> detailsEditor.beginEdit());
		if (detailsSaveButton != null)
			detailsSaveButton.setOnAction(e -> onSaveDetails());
		if (detailsCancelButton != null)
			detailsCancelButton.setOnAction(e -> detailsEditor.cancelEdit());
	}

	// ----------------------------
	// Sections / placeholders
	// ----------------------------

	private void setupSections() {
		if (sectionButtonsBox == null)
			return;

		sectionButtons.clear();
		sectionButtonsBox.getChildren().clear();

		for (String section : SECTIONS) {
			Button button = createSectionButton(section);
			button.setOnAction(e -> onSectionSelected(section, true));
			sectionButtons.put(section, button);
			sectionButtonsBox.getChildren().add(button);
		}

		onSectionSelected(initialSectionName, false);
	}

	private Button createSectionButton(String section) {
		Button button = new Button(section);
		button.setMaxWidth(Double.MAX_VALUE);
		button.setAlignment(Pos.CENTER_LEFT);
		NavButtonStyler.applyBaseStyle(button);
		return button;
	}


	private void refreshHeader() {
		if (caseTitleLabel == null || caseId == null)
			return;

		caseTitleLabel.setText("Case #" + caseId + " (Placeholder)");
		renderPrimaryStatusMini(null, "—", null);
		renderResponsibleAttorneyMini(null, "—", null);
		renderPracticeAreaMini(null, "—", null);

		if (lastUpdatedLabel != null)
			lastUpdatedLabel.setText("Last updated: —");
	}

	private void refreshOverviewPlaceholders() {
		if (ovCaseNameValue != null)
			ovCaseNameValue.setText(caseId == null ? "—" : "Case #" + caseId + " (Placeholder name)");
		if (ovCaseNumberValue != null)
			ovCaseNumberValue.setText(caseId == null ? "—" : String.valueOf(caseId));

		renderResponsibleAttorneyMini(null, "—", null);

		if (ovCaseStatusValue != null)
			ovCaseStatusValue.setText("—");

		renderCallerMini(null, "—");
		renderClientsMini(List.of());
		renderPracticeAreaMini(null, "—", null);

		if (ovTeamValue != null)
			ovTeamValue.setText("—");

		if (ovIntakeDateValue != null)
			ovIntakeDateValue.setText(formatDate(null));
		if (ovIncidentDateValue != null)
			ovIncidentDateValue.setText(formatDate(null));
		if (ovIncidentDateEditor != null)
			ovIncidentDateEditor.setValue(null);
		if (ovSolDateValue != null)
			ovSolDateValue.setText(formatDate(null));
		if (ovSolDateEditor != null)
			ovSolDateEditor.setValue(null);

		if (ovDescriptionValue != null)
			ovDescriptionValue.setText("");
		renderCaseUpdates(List.of());
	}

	// ----------------------------
	// Section navigation
	// ----------------------------

	private void onSectionSelected(String sectionName, boolean userInitiated) {
		if (sectionName == null)
			return;

		activeSectionName = sectionName;
		setActiveSectionButton(sectionName);
		switch (sectionName) {
		case "Overview" -> showOverview();
		case "Parties" -> showParties();
		case "Tasks" -> showTasksTab();
		case "Timeline" -> showTimeline();
		case "Details" -> showDetails();
		default -> showGeneric(sectionName);
		}

		if (userInitiated && onSectionNavigation != null) {
			onSectionNavigation.accept(toSectionKey(sectionName));
		}
	}


	private static String toSectionKey(String sectionName) {
		if (sectionName == null) {
			return null;
		}
		return switch (sectionName) {
		case "Overview" -> "OVERVIEW";
		case "Tasks" -> "TASKS";
		case "Timeline" -> "TIMELINE";
		case "Details" -> "DETAILS";
		case "Parties" -> "PARTIES";
		case "Documents" -> "DOCUMENTS";
		default -> sectionName.toUpperCase(Locale.ROOT);
		};
	}

	private static String fromSectionKey(String sectionKey) {
		if (sectionKey == null || sectionKey.isBlank()) {
			return null;
		}
		String normalized = sectionKey.trim().toUpperCase(Locale.ROOT);
		return switch (normalized) {
		case "OVERVIEW" -> "Overview";
		case "TASKS" -> "Tasks";
		case "TIMELINE" -> "Timeline";
		case "DETAILS" -> "Details";
		case "PARTIES" -> "Parties";
		case "DOCUMENTS" -> "Documents";
		default -> null;
		};
	}

	private void setActiveSectionButton(String activeSection) {
		Button activeButton = null;
		for (Map.Entry<String, Button> entry : sectionButtons.entrySet()) {
			if (Objects.equals(entry.getKey(), activeSection)) {
				activeButton = entry.getValue();
				break;
			}
		}

		NavButtonStyler.setActive(activeButton, sectionButtons.values());
	}

	private void showOverview() {
		setUpdatesPaneVisible(true);
		setPaneVisible(overviewPane, true);
		setVisibleManaged(detailsScrollPane, false);
		setPaneVisible(tasksTabPane, false);
		setPaneVisible(genericPane, false);
		setPaneVisible(tasksPanel, true);
		if (contentTitleLabel != null)
			contentTitleLabel.setText("Overview");
		loadOverviewOnce();
	}

	private void showTasksTab() {
		setUpdatesPaneVisible(false);
		setPaneVisible(overviewPane, false);
		setVisibleManaged(detailsScrollPane, false);
		setPaneVisible(tasksTabPane, true);
		setPaneVisible(genericPane, false);
		setPaneVisible(tasksPanel, false);
		loadCaseTasksAsync();
		renderTasksSection();
	}

	private void showDetails() {
		setUpdatesPaneVisible(false);
		setPaneVisible(overviewPane, false);
		setVisibleManaged(detailsScrollPane, true);
		setPaneVisible(tasksTabPane, false);
		setPaneVisible(genericPane, false);
		setPaneVisible(tasksPanel, false);
		if (contentTitleLabel != null)
			contentTitleLabel.setText("Details");
		renderDetailsFromCurrent();
	}

	private void showGeneric(String sectionName) {
		setUpdatesPaneVisible(false);
		setPaneVisible(overviewPane, false);
		setVisibleManaged(detailsScrollPane, false);
		setPaneVisible(tasksTabPane, false);
		setPaneVisible(genericPane, true);
		setPaneVisible(tasksPanel, false);

		if (genericTitleLabel != null)
			genericTitleLabel.setText(sectionName);

		setVisibleManaged(placeholderTextArea, true);
		setVisibleManaged(addOrganizationButton, false);
		setVisibleManaged(organizationsScrollPane, false);
		setVisibleManaged(organizationsFlow, false);
		setVisibleManaged(organizationsEmptyLabel, false);
		setVisibleManaged(timelineScrollPane, false);
		setVisibleManaged(timelineListBox, false);
		setVisibleManaged(timelineEmptyLabel, false);

		if (placeholderTextArea != null) {
			placeholderTextArea.setText(sectionName + " view is not implemented yet.");
		}
	}

	private void showTimeline() {
		setUpdatesPaneVisible(false);
		setPaneVisible(overviewPane, false);
		setVisibleManaged(detailsScrollPane, false);
		setPaneVisible(tasksTabPane, false);
		setPaneVisible(genericPane, true);
		setPaneVisible(tasksPanel, false);

		if (genericTitleLabel != null)
			genericTitleLabel.setText("Timeline");

		setVisibleManaged(addOrganizationButton, false);
		setVisibleManaged(placeholderTextArea, false);
		setVisibleManaged(organizationsScrollPane, false);
		setVisibleManaged(organizationsFlow, false);
		setVisibleManaged(organizationsEmptyLabel, false);
		setVisibleManaged(timelineScrollPane, true);
		setVisibleManaged(timelineListBox, true);
		setVisibleManaged(timelineEmptyLabel, false);
		loadCaseTimelineEventsAsync();
	}

	private void showParties() {
		setUpdatesPaneVisible(false);
		setPaneVisible(overviewPane, false);
		setVisibleManaged(detailsScrollPane, false);
		setPaneVisible(tasksTabPane, false);
		setPaneVisible(genericPane, true);
		setPaneVisible(tasksPanel, false);

		if (genericTitleLabel != null)
			genericTitleLabel.setText("Parties");

		setVisibleManaged(addOrganizationButton, true);
		if (addOrganizationButton != null) {
			addOrganizationButton.setText("Add Party");
		}
		setVisibleManaged(placeholderTextArea, false);
		setVisibleManaged(organizationsScrollPane, false);
		setVisibleManaged(organizationsFlow, false);
		setVisibleManaged(organizationsEmptyLabel, false);
		setVisibleManaged(timelineScrollPane, true);
		setVisibleManaged(timelineListBox, true);
		setVisibleManaged(timelineEmptyLabel, false);
		renderPartiesSection();
		if (caseDao != null && caseId != null && (!partiesLoadedOnce || caseParties == null || caseParties.isEmpty())) {
			refreshPartiesSectionAsync();
		}
	}

	private void loadCaseTimelineEventsAsync() {
		if (caseDao == null || caseId == null) {
			renderTimelineEvents(List.of());
			return;
		}

		final int activeCaseId = caseId;
		new Thread(() -> {
			try {
				List<CaseTimelineEventDto> events = caseDao.listCaseTimelineEvents(activeCaseId);
				runOnFx(() -> {
					if (caseId == null || caseId != activeCaseId)
						return;
					renderTimelineEvents(events);
				});
			} catch (Exception ex) {
				runOnFx(() -> showError("Failed to load timeline events. " + ex.getMessage()));
			}
		}, "case-timeline-load-" + activeCaseId).start();
	}

	private void renderTimelineEvents(List<CaseTimelineEventDto> events) {
		if (timelineListBox == null)
			return;

		timelineListBox.getChildren().clear();
		List<CaseTimelineEventDto> safeEvents = events == null ? List.of() : events;
		if (safeEvents.isEmpty()) {
			setVisibleManaged(timelineEmptyLabel, true);
			if (timelineScrollPane != null)
				timelineScrollPane.setVvalue(0.0);
			return;
		}

		setVisibleManaged(timelineEmptyLabel, false);
		for (CaseTimelineEventDto event : safeEvents) {
			if (event == null)
				continue;
			timelineListBox.getChildren().add(createTimelineEventCard(event));
		}
		if (timelineScrollPane != null)
			timelineScrollPane.setVvalue(0.0);
	}

	private Node createTimelineEventCard(CaseTimelineEventDto event) {
		Label titleLabel = new Label(safeText(event.getTitle()));
		titleLabel.setStyle("-fx-font-weight: bold;");
		titleLabel.setWrapText(true);

		String actorDisplayName = safeText(event.getActorDisplayName()).trim();
		String actorMeta = actorDisplayName.isBlank() ? "By system" : "By " + actorDisplayName;
		Label actorLabel = new Label(actorMeta);
		actorLabel.setStyle("-fx-opacity: 0.85;");

		Label timestampLabel = new Label(formatDateTime(event.getOccurredAt()));
		timestampLabel.setStyle("-fx-opacity: 0.75;");

		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		HBox metaRow = new HBox(8, actorLabel, spacer, timestampLabel);
		metaRow.setAlignment(Pos.CENTER_LEFT);

		VBox content = new VBox(6, titleLabel, metaRow);
		String body = safeText(event.getBody()).trim();
		if (!body.isBlank()) {
			Label bodyLabel = new Label(body);
			bodyLabel.setWrapText(true);
			content.getChildren().add(bodyLabel);
		}

		VBox card = new VBox(content);
		card.setPadding(new Insets(10, 12, 10, 12));
		card.getStyleClass().add("secondary-panel");
		return card;
	}

	private void renderPartiesSection() {
		if (timelineListBox == null)
			return;

		boolean partiesSectionActive = isSectionActive("Parties");
		timelineListBox.getChildren().clear();
		List<CasePartyDto> safeParties = caseParties == null ? List.of() : caseParties;
		if (safeParties.isEmpty()) {
			if (partiesSectionActive) {
				if (timelineEmptyLabel != null)
					timelineEmptyLabel.setText("No parties yet.");
				setVisibleManaged(timelineEmptyLabel, true);
			}
			return;
		}

		if (partiesSectionActive) {
			setVisibleManaged(timelineEmptyLabel, false);
		}

		Map<String, List<CasePartyDto>> grouped = safeParties.stream()
				.filter(Objects::nonNull)
				.collect(Collectors.groupingBy(
						p -> normalizedPartySideKey(p.getSide()),
						LinkedHashMap::new,
						Collectors.toList()));
		Map<String, String> sideLabelsByKey = loadPartySideLabelMap();

		List<String> sideOrder = List.of("represented", "opposing", "neutral", "unclassified");
		for (String sideKey : sideOrder) {
			List<CasePartyDto> group = grouped.get(sideKey);
			if (group == null || group.isEmpty()) {
				continue;
			}

			Label heading = new Label(toPartySideLabel(sideLabelsByKey, sideKey));
			heading.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-opacity: 0.92;");
			timelineListBox.getChildren().add(heading);

			List<CasePartyDto> sorted = group.stream()
					.sorted((a, b) -> {
						int primaryCompare = Boolean.compare(b.isPrimary(), a.isPrimary());
						if (primaryCompare != 0)
							return primaryCompare;
						return safeText(a.getDisplayName()).compareToIgnoreCase(safeText(b.getDisplayName()));
					})
					.toList();

			for (CasePartyDto party : sorted) {
				timelineListBox.getChildren().add(createPartyCard(party, sideLabelsByKey));
			}
		}
	}

	private Node createPartyCard(CasePartyDto party, Map<String, String> sideLabelsByKey) {
		String roleLabel = toPartyRoleLabel(party.getPartyRoleName(), party.getPartyRoleId());
		String sideLabel = toPartySideLabel(sideLabelsByKey, normalizedPartySideKey(party.getSide()));
		String notes = safeText(party.getNotes()).trim();
		Node summaryCard = createPartyEntityCard(party);

		Label metaLabel = new Label(formatPartyRelationshipMeta(roleLabel, sideLabel, party.isPrimary()));
		metaLabel.setStyle("-fx-opacity: 0.86;");
		metaLabel.setWrapText(true);

		VBox content = new VBox(6, summaryCard, metaLabel);
		if (!notes.isBlank()) {
			Label notesLabel = new Label(notes);
			notesLabel.setWrapText(true);
			notesLabel.setStyle("-fx-opacity: 0.9;");
			content.getChildren().add(notesLabel);
		}

		Button editButton = new Button("Edit");
		editButton.getStyleClass().add("button-secondary");
		editButton.setOnAction(e -> onEditParty(party));

		Button removeButton = new Button("Remove");
		removeButton.getStyleClass().add("button-secondary");
		removeButton.setOnAction(e -> onRemoveParty(party));

		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		HBox actions = new HBox(8, spacer, editButton, removeButton);

		VBox card = new VBox(6, content, actions);
		card.setPadding(new Insets(10, 12, 10, 12));
		card.getStyleClass().add("secondary-panel");
		return card;
	}

	private Node createPartyEntityCard(CasePartyDto party) {
		final double partiesCardWidth = 300;
		String entityType = safeText(party.getEntityType()).trim().toLowerCase(Locale.ROOT);
		if ("organization".equals(entityType) && party.getOrganizationId() != null) {
			OrganizationCardFactory factory = organizationCardFactory != null
					? organizationCardFactory
					: new OrganizationCardFactory(this::openOrganization);
			OrganizationCardFactory.OrganizationCardModel model = new OrganizationCardFactory.OrganizationCardModel(
					party.getOrganizationId().intValue(),
					safeText(party.getDisplayName()),
					null,
					null,
					null,
					null,
					null,
					null,
					null,
					null,
					null,
					null,
					null,
					null,
					null
			);
			OrganizationCard card = factory.create(model, OrganizationCardFactory.Variant.COMPACT);
			card.setSuppressPlaceholderLines(true);
			card.applyCompact();
			card.setMinWidth(partiesCardWidth);
			card.setPrefWidth(partiesCardWidth);
			card.setMaxWidth(partiesCardWidth);
			return card;
		}

		if ("contact".equals(entityType) && party.getContactId() != null) {
			ContactCardFactory factory = contactCardFactory != null
					? contactCardFactory
					: new ContactCardFactory(onOpenContact == null ? id -> {
					} : onOpenContact);
			ContactCardFactory.ContactCardModel model = new ContactCardFactory.ContactCardModel(
					party.getContactId().intValue(),
					safeText(party.getDisplayName()),
					null,
					null,
					null
			);
			ContactCard card = factory.create(model, ContactCardFactory.Variant.COMPACT);
			card.setSuppressPlaceholderLines(true);
			card.applyCompact();
			card.setMinWidth(partiesCardWidth);
			card.setPrefWidth(partiesCardWidth);
			card.setMaxWidth(partiesCardWidth);
			return card;
		}

		Label fallback = new Label(safeText(party.getDisplayName()).isBlank() ? "—" : safeText(party.getDisplayName()));
		fallback.setStyle("-fx-font-weight: bold;");
		fallback.setWrapText(true);
		return fallback;
	}

	private String formatPartyRelationshipMeta(String roleLabel, String sideLabel, boolean primary) {
		String base = roleLabel + " · " + sideLabel;
		return primary ? base + " · Primary" : base;
	}

	private List<PartySideOption> defaultPartySideOptions() {
		return List.of(
				new PartySideOption("Represented", "represented"),
				new PartySideOption("Opposing", "opposing"),
				new PartySideOption("Neutral", "neutral"),
				new PartySideOption("Unaffiliated", null)
		);
	}

	private List<PartySideOption> loadPartySideOptions() {
		if (caseDao == null) {
			return defaultPartySideOptions();
		}
		try {
			List<CaseDao.PartySideRow> sides = caseDao.listPartySides();
			List<PartySideOption> out = new java.util.ArrayList<>();
			for (CaseDao.PartySideRow side : sides) {
				if (side == null)
					continue;
				String key = safeText(side.systemKey()).trim().toLowerCase(Locale.ROOT);
				if (key.isBlank())
					continue;
				String label = safeText(side.name()).trim();
				if (label.isBlank())
					label = toPartySideLabel(Map.of(), key);
				out.add(new PartySideOption(label, key));
			}
			if (out.isEmpty()) {
				return defaultPartySideOptions();
			}
			out.add(new PartySideOption("Unaffiliated", null));
			return List.copyOf(out);
		} catch (Exception ignored) {
			return defaultPartySideOptions();
		}
	}

	private String normalizedPartySideKey(String side) {
		String normalized = safeText(side).trim().toLowerCase(Locale.ROOT);
		return switch (normalized) {
			case "represented" -> "represented";
			case "opposing" -> "opposing";
			case "neutral" -> "neutral";
			default -> "unclassified";
		};
	}

	private Map<String, String> loadPartySideLabelMap() {
		Map<String, String> labels = new LinkedHashMap<>();
		for (PartySideOption option : loadPartySideOptions()) {
			if (option == null || option.value == null)
				continue;
			labels.putIfAbsent(safeText(option.value).trim().toLowerCase(Locale.ROOT), safeText(option.label).trim());
		}
		return labels;
	}

	private String toPartySideLabel(Map<String, String> sideLabelsByKey, String sideKey) {
		String normalized = safeText(sideKey).trim().toLowerCase(Locale.ROOT);
		String mapped = sideLabelsByKey == null ? null : sideLabelsByKey.get(normalized);
		if (mapped != null && !mapped.isBlank()) {
			return mapped;
		}
		return switch (normalized) {
			case "represented" -> "Represented";
			case "opposing" -> "Opposing";
			case "neutral" -> "Neutral";
			default -> "Unaffiliated";
		};
	}

	private String toPartyRoleLabel(String roleName, long roleId) {
		String normalized = safeText(roleName).trim().replace('_', ' ');
		if (normalized.isBlank()) {
			return "Role " + roleId;
		}
		String[] tokens = normalized.split("\\s+");
		for (int i = 0; i < tokens.length; i++) {
			String token = tokens[i];
			if (token.isBlank()) {
				continue;
			}
			tokens[i] = token.substring(0, 1).toUpperCase(Locale.ROOT) + token.substring(1).toLowerCase(Locale.ROOT);
		}
		return String.join(" ", tokens);
	}

	private void onAddParty() {
		if (caseDao == null || caseId == null || appState == null || appState.getShaleClientId() <= 0)
			return;
		AddPartyDraft draft = showAddPartyWizardDialog();
		if (draft == null)
			return;

		final long activeCaseId = caseId.longValue();
		new Thread(() -> {
			try {
				Long entityId = draft.entityId();
				if (draft.createNew()) {
					entityId = createEntityForNewPartyDraft(draft);
				}
				if (entityId == null || entityId <= 0) {
					throw new IllegalStateException("A party entity must be selected or created.");
				}
				caseDao.addCaseParty(
							activeCaseId,
							draft.entityType().equals("contact") ? entityId : null,
							draft.entityType().equals("organization") ? entityId : null,
							draft.partyRoleId(),
							draft.side(),
							draft.primary(),
							draft.notes());
					runOnFx(this::refreshPartiesSectionAsync);
				} catch (Exception ex) {
					runOnFx(() -> showError("Failed to add party. " + ex.getMessage()));
				}
			}, "case-add-party-" + activeCaseId).start();
	}

	private void onEditParty(CasePartyDto party) {
		if (party == null || caseDao == null || caseId == null)
			return;
		PartyEditorResult result = showPartyEditorDialog(party);
		if (result == null)
			return;

		final long activeCaseId = caseId.longValue();
		new Thread(() -> {
			try {
				caseDao.updateCaseParty(
						party.getId(),
						activeCaseId,
						result.entityType.equals("contact") ? result.entityId : null,
						result.entityType.equals("organization") ? result.entityId : null,
						result.partyRoleId,
						result.side,
						result.primary,
						result.notes);
				runOnFx(this::refreshPartiesSectionAsync);
			} catch (Exception ex) {
				runOnFx(() -> showError("Failed to update party. " + ex.getMessage()));
			}
		}, "case-edit-party-" + activeCaseId + "-" + party.getId()).start();
	}

	private void onRemoveParty(CasePartyDto party) {
		if (party == null || caseDao == null || caseId == null)
			return;
		boolean confirmed = AppDialogs.showConfirmation(
				organizationDialogOwner(),
				"Remove Party",
				"Remove this party from the case?",
				safeText(party.getDisplayName()),
				"Remove Party",
				AppDialogs.DialogActionKind.DANGER);
		if (!confirmed)
			return;

		final long activeCaseId = caseId.longValue();
		new Thread(() -> {
			try {
				caseDao.removeCaseParty(party.getId());
				runOnFx(this::refreshPartiesSectionAsync);
			} catch (Exception ex) {
				runOnFx(() -> showError("Failed to remove party. " + ex.getMessage()));
			}
		}, "case-remove-party-" + activeCaseId + "-" + party.getId()).start();
	}

	private void refreshPartiesSectionAsync() {
		if (caseDao == null || caseId == null)
			return;
		final long activeCaseId = caseId.longValue();
		new Thread(() -> {
			try {
				List<CasePartyDto> refreshed = caseDao.listCaseParties(activeCaseId);
				runOnFx(() -> {
					caseParties = refreshed == null ? List.of() : refreshed;
					partiesLoadedOnce = true;
					renderPartiesSection();
					if (currentOverview != null) {
						currentOverview = applyCallerFromCaseParties(currentOverview, caseParties);
						applyOverviewEditSafe(currentOverview);
					}
				});
			} catch (Exception ex) {
				runOnFx(() -> showError("Failed to refresh parties for this case."));
			}
		}, "case-refresh-parties-" + activeCaseId).start();
	}

	private PartyEditorResult showPartyEditorDialog(CasePartyDto existing) {
		if (existing == null) {
			AddPartyDraft draft = showAddPartyWizardDialog();
			if (draft == null || draft.entityId() == null) {
				return null;
			}
			return new PartyEditorResult(draft.entityType(), draft.entityId(), draft.partyRoleId(), draft.side(), draft.primary(), draft.notes());
		}

		if (appState == null || appState.getShaleClientId() <= 0 || caseId == null || caseId <= 0) {
			showError("Unable to edit parties without an active client/case context.");
			return null;
		}
		List<CaseDao.PartyRoleRow> partyRoles = caseDao.listPartyRoles();
		List<CaseDao.SelectableContactRow> contacts = caseDao.findLinkableContacts(caseId.longValue());
		List<CaseDao.SelectableOrganizationRow> organizations = caseDao.findLinkableOrganizations(caseId.longValue());

		Dialog<PartyEditorResult> dialog = new Dialog<>();
		AppDialogs.applySecondaryDialogShell(dialog, "Edit Party");
		dialog.setTitle("Edit Party");
		dialog.initOwner(organizationDialogOwner());
		ButtonType saveType = new ButtonType("Save", ButtonData.OK_DONE);
		dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

		ChoiceBox<String> entityTypeChoice = new ChoiceBox<>();
		entityTypeChoice.getItems().addAll("Contact", "Organization");

		ChoiceBox<PartyEntityOption> entityChoice = new ChoiceBox<>();
		ChoiceBox<PartyRoleOption> roleChoice = new ChoiceBox<>();
		ChoiceBox<PartySideOption> sideChoice = new ChoiceBox<>();
		sideChoice.getItems().addAll(loadPartySideOptions());
		sideChoice.setConverter(new javafx.util.StringConverter<>() {
			@Override public String toString(PartySideOption object) { return object == null ? "" : object.label; }
			@Override public PartySideOption fromString(String string) { return null; }
		});

		CheckBox primaryCheck = new CheckBox("Primary");
		TextArea notesArea = new TextArea();
		notesArea.setPrefRowCount(3);
		notesArea.setWrapText(true);

		partyRoles.stream()
				.map(r -> new PartyRoleOption(r.id(), toPartyRoleLabel(r.name(), r.id())))
				.forEach(roleChoice.getItems()::add);
		roleChoice.setConverter(new javafx.util.StringConverter<>() {
			@Override public String toString(PartyRoleOption object) { return object == null ? "" : object.label; }
			@Override public PartyRoleOption fromString(String string) { return null; }
		});
		entityChoice.setConverter(new javafx.util.StringConverter<>() {
			@Override public String toString(PartyEntityOption object) { return object == null ? "" : object.label; }
			@Override public PartyEntityOption fromString(String string) { return null; }
		});

		Runnable loadEntities = () -> {
			String selectedType = entityTypeChoice.getValue();
			entityChoice.getItems().clear();
			if ("Organization".equalsIgnoreCase(selectedType)) {
				for (CaseDao.SelectableOrganizationRow org : organizations) {
					String label = safeText(org.name());
					String type = safeText(org.organizationTypeName());
					if (!type.isBlank()) {
						label = label + " — " + type;
					}
					entityChoice.getItems().add(new PartyEntityOption("organization", Long.valueOf(org.id()), label));
				}
			} else {
				for (CaseDao.SelectableContactRow contact : contacts) {
					String displayName = safeText(contact.displayName());
					if (displayName.isBlank()) {
						displayName = "Contact #" + contact.id();
					}
					String secondary = !safeText(contact.phone()).isBlank()
							? safeText(contact.phone())
							: safeText(contact.email());
					String label = secondary.isBlank() ? displayName : displayName + " — " + secondary;
					entityChoice.getItems().add(new PartyEntityOption("contact", Long.valueOf(contact.id()), label));
				}
			}
			if (!entityChoice.getItems().isEmpty()) {
				entityChoice.setValue(entityChoice.getItems().get(0));
			}
		};
		entityTypeChoice.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> loadEntities.run());

		entityTypeChoice.setValue("organization".equalsIgnoreCase(existing.getEntityType()) ? "Organization" : "Contact");
		loadEntities.run();
		entityChoice.getItems().stream()
				.filter(o -> Objects.equals(o.entityType, safeText(existing.getEntityType()).toLowerCase(Locale.ROOT))
						&& Objects.equals(o.id, "organization".equalsIgnoreCase(existing.getEntityType()) ? existing.getOrganizationId() : existing.getContactId()))
				.findFirst()
				.ifPresentOrElse(entityChoice::setValue, () -> {
					Long existingId = "organization".equalsIgnoreCase(existing.getEntityType()) ? existing.getOrganizationId() : existing.getContactId();
					String fallbackLabel = safeText(existing.getDisplayName()).isBlank() ? "Party #" + existingId : safeText(existing.getDisplayName());
					PartyEntityOption fallback = new PartyEntityOption(
							safeText(existing.getEntityType()).toLowerCase(Locale.ROOT),
							existingId,
							fallbackLabel);
					entityChoice.getItems().add(0, fallback);
					entityChoice.setValue(fallback);
				});
		roleChoice.getItems().stream()
				.filter(r -> r.id == existing.getPartyRoleId())
				.findFirst()
				.ifPresent(roleChoice::setValue);
		sideChoice.getItems().stream()
				.filter(s -> Objects.equals(s.value, normalizeSideForStorage(existing.getSide())))
				.findFirst()
				.ifPresentOrElse(sideChoice::setValue, () -> sideChoice.setValue(
						sideChoice.getItems().stream()
								.filter(s -> s != null && s.value == null)
								.findFirst()
								.orElse(sideChoice.getItems().isEmpty() ? null : sideChoice.getItems().get(0))));
		primaryCheck.setSelected(existing.isPrimary());
		notesArea.setText(safeText(existing.getNotes()));

		GridPane grid = new GridPane();
		grid.setHgap(10);
		grid.setVgap(10);
		grid.add(new Label("Entity Type"), 0, 0);
		grid.add(entityTypeChoice, 1, 0);
		grid.add(new Label("Entity"), 0, 1);
		grid.add(entityChoice, 1, 1);
		grid.add(new Label("Party Role"), 0, 2);
		grid.add(roleChoice, 1, 2);
		grid.add(new Label("Side"), 0, 3);
		grid.add(sideChoice, 1, 3);
		grid.add(primaryCheck, 1, 4);
		grid.add(new Label("Notes"), 0, 5);
		grid.add(notesArea, 1, 5);
		dialog.getDialogPane().setContent(grid);

		Node saveButton = dialog.getDialogPane().lookupButton(saveType);
		saveButton.disableProperty().bind(
				entityChoice.valueProperty().isNull()
						.or(roleChoice.valueProperty().isNull())
						.or(sideChoice.valueProperty().isNull())
		);
		sideChoice.setConverter(new javafx.util.StringConverter<>() {
			@Override public String toString(PartySideOption object) { return object == null ? "" : object.label; }
			@Override public PartySideOption fromString(String string) { return null; }
		});

		dialog.setResultConverter(button -> {
			if (button != saveType)
				return null;
			PartyEntityOption entity = entityChoice.getValue();
			PartyRoleOption role = roleChoice.getValue();
			PartySideOption side = sideChoice.getValue();
			if (entity == null || role == null || side == null)
				return null;
			return new PartyEditorResult(entity.entityType, entity.id, role.id, side.value, primaryCheck.isSelected(), notesArea.getText());
		});

		return dialog.showAndWait().orElse(null);
	}

	private AddPartyDraft showAddPartyWizardDialog() {
		if (appState == null || appState.getShaleClientId() <= 0 || caseId == null || caseId <= 0) {
			showError("Unable to add parties without an active client/case context.");
			return null;
		}
		List<CaseDao.PartyRoleRow> partyRoles = caseDao.listPartyRoles();
		List<CaseDao.SelectableContactRow> contacts = caseDao.findLinkableContacts(caseId.longValue());
		List<CaseDao.SelectableOrganizationRow> organizations = caseDao.findLinkableOrganizations(caseId.longValue());
		List<OrganizationDao.OrganizationTypeRow> organizationTypes = organizationDao == null ? List.of() : organizationDao.findOrganizationTypes();
		Long defaultPartyRoleId = partyRoles.stream()
			.filter(r -> "party".equalsIgnoreCase(safeText(r.name())))
			.map(CaseDao.PartyRoleRow::id)
			.findFirst()
			.orElse(partyRoles.isEmpty() ? null : partyRoles.get(0).id());

		class WizardState {
			int step = 1;
			String mode = null; // create | select
			String entityType = null; // contact | organization
			PartyEntityOption selectedEntity = null;
			String createContactFirstName = null;
			String createContactLastName = null;
			String createOrganizationName = null;
			Integer createOrganizationTypeId = organizationTypes.isEmpty() ? null : organizationTypes.get(0).organizationTypeId();
			Long partyRoleId = defaultPartyRoleId;
			String affiliation = null;
			boolean primary = false;
			String notes = null;
		}
		WizardState state = new WizardState();

		Dialog<AddPartyDraft> dialog = new Dialog<>();
		AppDialogs.applySecondaryDialogShell(dialog, "Add Party");
		dialog.setTitle("Add Party");
		dialog.initOwner(organizationDialogOwner());
		ButtonType backType = new ButtonType("Back", ButtonData.LEFT);
		ButtonType addType = new ButtonType("Add Party", ButtonData.OK_DONE);
		dialog.getDialogPane().getButtonTypes().addAll(backType, addType, ButtonType.CANCEL);

		Node backButton = dialog.getDialogPane().lookupButton(backType);
		Node addButton = dialog.getDialogPane().lookupButton(addType);

		Label titleLabel = new Label();
		titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: 700;");
		Label subtitleLabel = new Label();
		subtitleLabel.setWrapText(true);

		Button createNewButton = new Button("Create New");
		Button selectExistingButton = new Button("Select Existing");
		createNewButton.setMinWidth(200);
		selectExistingButton.setMinWidth(200);

		Button contactButton = new Button("Contact");
		Button organizationButton = new Button("Organization");
		contactButton.setMinWidth(200);
		organizationButton.setMinWidth(200);

		TextField createFirstNameField = new TextField();
		TextField createLastNameField = new TextField();
		TextField createOrganizationNameField = new TextField();
		ChoiceBox<OrganizationDao.OrganizationTypeRow> createOrganizationTypeChoice = new ChoiceBox<>();
		createOrganizationTypeChoice.getItems().setAll(organizationTypes);
		createOrganizationTypeChoice.setConverter(new javafx.util.StringConverter<>() {
			@Override public String toString(OrganizationDao.OrganizationTypeRow object) { return object == null ? "" : safeText(object.name()); }
			@Override public OrganizationDao.OrganizationTypeRow fromString(String string) { return null; }
		});
		if (!organizationTypes.isEmpty()) {
			createOrganizationTypeChoice.setValue(organizationTypes.get(0));
		}

		ChoiceBox<PartyRoleOption> roleChoice = new ChoiceBox<>();
		partyRoles.stream().map(r -> new PartyRoleOption(r.id(), toPartyRoleLabel(r.name(), r.id()))).forEach(roleChoice.getItems()::add);
		roleChoice.setConverter(new javafx.util.StringConverter<>() {
			@Override public String toString(PartyRoleOption object) { return object == null ? "" : object.label; }
			@Override public PartyRoleOption fromString(String string) { return null; }
		});
		if (!roleChoice.getItems().isEmpty()) {
			PartyRoleOption defaultRole = roleChoice.getItems().stream()
				.filter(r -> r.id() == defaultPartyRoleId)
				.findFirst()
				.orElse(roleChoice.getItems().get(0));
			roleChoice.setValue(defaultRole);
		}

		ChoiceBox<PartySideOption> sideChoice = new ChoiceBox<>();
		sideChoice.getItems().addAll(loadPartySideOptions());
		sideChoice.setConverter(new javafx.util.StringConverter<>() {
			@Override public String toString(PartySideOption object) { return object == null ? "" : object.label; }
			@Override public PartySideOption fromString(String string) { return null; }
		});
		sideChoice.setValue(sideChoice.getItems().stream()
				.filter(s -> s != null && s.value == null)
				.findFirst()
				.orElse(sideChoice.getItems().isEmpty() ? null : sideChoice.getItems().get(0)));

		CheckBox primaryCheck = new CheckBox("Primary");
		TextArea notesArea = new TextArea();
		notesArea.setPrefRowCount(3);
		notesArea.setWrapText(true);

		TextField searchField = new TextField();
		searchField.setPromptText("Search by name");
		javafx.scene.control.ListView<PartyEntityOption> existingList = new javafx.scene.control.ListView<>();
		existingList.setPrefHeight(260);
		existingList.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
			@Override
			protected void updateItem(PartyEntityOption item, boolean empty) {
				super.updateItem(item, empty);
				setText(empty || item == null ? null : item.label());
			}
		});

		VBox contentBox = new VBox(10);
		contentBox.setAlignment(Pos.TOP_CENTER);
		contentBox.setPadding(new Insets(16));
		contentBox.getChildren().addAll(titleLabel, subtitleLabel);
		dialog.getDialogPane().setContent(contentBox);

		Runnable refreshExistingList = () -> {
			String query = safeText(searchField.getText()).toLowerCase(Locale.ROOT);
			List<PartyEntityOption> options = new java.util.ArrayList<>();
			if ("organization".equals(state.entityType)) {
				for (CaseDao.SelectableOrganizationRow org : organizations) {
					String name = safeText(org.name());
					if (name.isBlank()) name = "Organization #" + org.id();
					String type = safeText(org.organizationTypeName());
					String label = type.isBlank() ? name : name + " — " + type;
					options.add(new PartyEntityOption("organization", Long.valueOf(org.id()), label));
				}
			} else {
				for (CaseDao.SelectableContactRow contact : contacts) {
					String name = safeText(contact.displayName());
					if (name.isBlank()) name = "Contact #" + contact.id();
					String secondary = safeText(contact.email());
					if (secondary.isBlank()) secondary = safeText(contact.phone());
					String label = secondary.isBlank() ? name : name + " — " + secondary;
					options.add(new PartyEntityOption("contact", Long.valueOf(contact.id()), label));
				}
			}
			if (!query.isBlank()) {
				options = options.stream().filter(o -> safeText(o.label()).toLowerCase(Locale.ROOT).contains(query)).toList();
			}
			existingList.getItems().setAll(options);
			if (state.selectedEntity != null) {
				existingList.getItems().stream()
					.filter(o -> Objects.equals(o.entityType(), state.selectedEntity.entityType()) && Objects.equals(o.id(), state.selectedEntity.id()))
					.findFirst().ifPresent(existingList.getSelectionModel()::select);
			}
			if (existingList.getSelectionModel().getSelectedItem() == null && !options.isEmpty()) {
				existingList.getSelectionModel().selectFirst();
				state.selectedEntity = existingList.getSelectionModel().getSelectedItem();
			}
		};

		Runnable syncStateFromControls = () -> {
			PartyRoleOption role = roleChoice.getValue();
			PartySideOption side = sideChoice.getValue();
			state.partyRoleId = role == null ? null : role.id();
			state.affiliation = side == null ? null : side.value();
			state.primary = primaryCheck.isSelected();
			state.notes = notesArea.getText();
			state.createContactFirstName = createFirstNameField.getText();
			state.createContactLastName = createLastNameField.getText();
			state.createOrganizationName = createOrganizationNameField.getText();
			OrganizationDao.OrganizationTypeRow orgType = createOrganizationTypeChoice.getValue();
			state.createOrganizationTypeId = orgType == null ? null : orgType.organizationTypeId();
			state.selectedEntity = existingList.getSelectionModel().getSelectedItem();
		};

		Runnable refreshAddButtonState = () -> {
			syncStateFromControls.run();
			boolean enabled = false;
			if (state.step == 3 && state.partyRoleId != null && state.affiliation != null) {
				if ("create".equals(state.mode)) {
					if ("contact".equals(state.entityType)) {
						enabled = !safeText(state.createContactFirstName).isBlank() || !safeText(state.createContactLastName).isBlank();
					} else if ("organization".equals(state.entityType)) {
						enabled = !safeText(state.createOrganizationName).isBlank() && state.createOrganizationTypeId != null && state.createOrganizationTypeId > 0;
					}
				} else if ("select".equals(state.mode)) {
					enabled = state.selectedEntity != null;
				}
			}
			addButton.setDisable(!enabled);
			setVisibleManaged(addButton, state.step == 3);
			setVisibleManaged(backButton, state.step > 1);
		};

		Runnable renderStep = () -> {
			contentBox.getChildren().setAll(titleLabel, subtitleLabel);
			if (state.step == 1) {
				titleLabel.setText("Step 1");
				subtitleLabel.setText("Create new or select from existing");
				HBox choices = new HBox(14, createNewButton, selectExistingButton);
				choices.setAlignment(Pos.CENTER);
				contentBox.getChildren().add(choices);
				dialog.getDialogPane().setPrefSize(560, 260);
				dialog.getDialogPane().setMinSize(560, 260);
			} else if (state.step == 2) {
				titleLabel.setText("Step 2");
				subtitleLabel.setText("Contact or Organization");
				HBox choices = new HBox(14, contactButton, organizationButton);
				choices.setAlignment(Pos.CENTER);
				contentBox.getChildren().add(choices);
				dialog.getDialogPane().setPrefSize(560, 260);
				dialog.getDialogPane().setMinSize(560, 260);
			} else if ("create".equals(state.mode)) {
				titleLabel.setText("Step 3: Create New");
				subtitleLabel.setText("Enter party details");
				GridPane form = new GridPane();
				form.setHgap(10);
				form.setVgap(10);
				if ("organization".equals(state.entityType)) {
					form.add(new Label("Name"), 0, 0);
					form.add(createOrganizationNameField, 1, 0);
					form.add(new Label("Organization Type"), 0, 1);
					form.add(createOrganizationTypeChoice, 1, 1);
					form.add(new Label("Party Role"), 0, 2);
					form.add(roleChoice, 1, 2);
					form.add(new Label("Affiliation"), 0, 3);
					form.add(sideChoice, 1, 3);
				} else {
					form.add(new Label("First Name"), 0, 0);
					form.add(createFirstNameField, 1, 0);
					form.add(new Label("Last Name"), 0, 1);
					form.add(createLastNameField, 1, 1);
					form.add(new Label("Party Role"), 0, 2);
					form.add(roleChoice, 1, 2);
					form.add(new Label("Affiliation"), 0, 3);
					form.add(sideChoice, 1, 3);
				}
				VBox formHost = new VBox(form);
				formHost.setAlignment(Pos.TOP_LEFT);
				formHost.setPadding(new Insets(8, 20, 8, 20));
				contentBox.getChildren().add(formHost);
				dialog.getDialogPane().setPrefSize(720, 420);
				dialog.getDialogPane().setMinSize(720, 420);
			} else {
				titleLabel.setText("Step 3: Select Existing");
				subtitleLabel.setText("Choose an existing contact or organization");
				refreshExistingList.run();
				GridPane relationships = new GridPane();
				relationships.setHgap(10);
				relationships.setVgap(10);
				relationships.add(new Label("Party Role"), 0, 0);
				relationships.add(roleChoice, 1, 0);
				relationships.add(new Label("Affiliation"), 0, 1);
				relationships.add(sideChoice, 1, 1);
				relationships.add(primaryCheck, 1, 2);
				relationships.add(new Label("Notes"), 0, 3);
				relationships.add(notesArea, 1, 3);
				VBox relationshipsHost = new VBox(relationships);
				relationshipsHost.setAlignment(Pos.TOP_LEFT);
				relationshipsHost.setPadding(new Insets(8, 20, 8, 20));
				contentBox.getChildren().addAll(searchField, existingList, relationshipsHost);
				dialog.getDialogPane().setPrefSize(820, 660);
				dialog.getDialogPane().setMinSize(820, 660);
			}
			if (dialog.getDialogPane().getScene() != null && dialog.getDialogPane().getScene().getWindow() != null) {
				dialog.getDialogPane().getScene().getWindow().sizeToScene();
			}
			refreshAddButtonState.run();
		};

		createNewButton.setOnAction(e -> {
			state.mode = "create";
			state.step = 2;
			renderStep.run();
		});
		selectExistingButton.setOnAction(e -> {
			state.mode = "select";
			state.step = 2;
			renderStep.run();
		});
		contactButton.setOnAction(e -> {
			state.entityType = "contact";
			state.step = 3;
			renderStep.run();
		});
		organizationButton.setOnAction(e -> {
			state.entityType = "organization";
			state.step = 3;
			renderStep.run();
		});

		backButton.addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
			e.consume();
			syncStateFromControls.run();
			if (state.step == 3) {
				state.step = 2;
			} else if (state.step == 2) {
				state.step = 1;
			}
			renderStep.run();
		});

		searchField.textProperty().addListener((obs, ov, nv) -> {
			if (state.step == 3 && "select".equals(state.mode)) {
				refreshExistingList.run();
				refreshAddButtonState.run();
			}
		});
		existingList.getSelectionModel().selectedItemProperty().addListener((obs, ov, nv) -> {
			state.selectedEntity = nv;
			refreshAddButtonState.run();
		});
		roleChoice.valueProperty().addListener((obs, ov, nv) -> refreshAddButtonState.run());
		sideChoice.valueProperty().addListener((obs, ov, nv) -> refreshAddButtonState.run());
		createFirstNameField.textProperty().addListener((obs, ov, nv) -> refreshAddButtonState.run());
		createLastNameField.textProperty().addListener((obs, ov, nv) -> refreshAddButtonState.run());
		createOrganizationNameField.textProperty().addListener((obs, ov, nv) -> refreshAddButtonState.run());
		createOrganizationTypeChoice.valueProperty().addListener((obs, ov, nv) -> refreshAddButtonState.run());
		primaryCheck.selectedProperty().addListener((obs, ov, nv) -> refreshAddButtonState.run());
		notesArea.textProperty().addListener((obs, ov, nv) -> refreshAddButtonState.run());

		renderStep.run();

		dialog.setResultConverter(button -> {
			if (button != addType) {
				return null;
			}
			syncStateFromControls.run();
			if (state.partyRoleId == null || state.affiliation == null || state.entityType == null || state.mode == null) {
				return null;
			}
			if ("create".equals(state.mode)) {
				return new AddPartyDraft(
					state.entityType,
					null,
					state.partyRoleId,
					state.affiliation,
					false,
					null,
					true,
					state.createContactFirstName,
					state.createContactLastName,
					state.createOrganizationName,
					state.createOrganizationTypeId);
			}
			if (state.selectedEntity == null) {
				return null;
			}
			return new AddPartyDraft(
					state.selectedEntity.entityType(),
					state.selectedEntity.id(),
					state.partyRoleId,
					state.affiliation,
					state.primary,
					state.notes,
					false,
					null,
					null,
					null,
					null);
		});

		return dialog.showAndWait().orElse(null);
	}

	private Long createEntityForNewPartyDraft(AddPartyDraft draft) {
		if (draft == null || !draft.createNew()) {
			return draft == null ? null : draft.entityId();
		}
		Integer shaleClientId = appState == null ? null : appState.getShaleClientId();
		if (shaleClientId == null || shaleClientId <= 0) {
			throw new IllegalStateException("No active tenant selected.");
		}
		if ("contact".equalsIgnoreCase(draft.entityType())) {
			if (contactDao == null) {
				throw new IllegalStateException("Contact creation is unavailable.");
			}
			int contactId = contactDao.createContact(new ContactDao.CreateContactRequest(
					shaleClientId,
					safeText(draft.contactFirstName()),
					safeText(draft.contactLastName()),
					null,
					null,
					false));
			return Long.valueOf(contactId);
		}
		if (organizationDao == null) {
			throw new IllegalStateException("Organization creation is unavailable.");
		}
		Integer organizationTypeId = draft.organizationTypeId();
		if (organizationTypeId == null || organizationTypeId <= 0) {
			throw new IllegalStateException("Organization Type is required.");
		}
		int organizationId = organizationDao.create(new OrganizationDao.OrganizationCreateRequest(
				shaleClientId,
				organizationTypeId,
				safeText(draft.organizationName()),
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null));
		return Long.valueOf(organizationId);
	}

	private static String normalizeSideForStorage(String side) {
		String normalized = side == null ? "" : side.trim().toLowerCase(Locale.ROOT);
		return switch (normalized) {
			case "represented", "opposing", "neutral" -> normalized;
			default -> null;
		};
	}



	private void loadCaseTasksAsync() {
		if (caseTaskService == null || appState == null || caseId == null) {
			return;
		}

		final long activeCaseId = caseId.longValue();
		final int shaleClientId = appState.getShaleClientId();
		if (shaleClientId <= 0) {
			return;
		}

		new Thread(() -> {
			try {
				List<CaseTaskListItemDto> tasks = caseTaskService.loadTasksForCase(
						activeCaseId,
						shaleClientId,
						selectedCaseTaskSort());
				List<Long> taskIds = (tasks == null ? List.<CaseTaskListItemDto>of() : tasks).stream()
						.map(CaseTaskListItemDto::id)
						.toList();
				java.util.Map<Long, List<TaskCardFactory.AssignedUserModel>> assignedByTask = caseTaskService
						.loadAssignedUsersForTasks(taskIds, shaleClientId)
						.stream()
						.collect(java.util.stream.Collectors.groupingBy(
								CaseTaskService.TaskAssignedUsersByTask::taskId,
								java.util.stream.Collectors.mapping(
										row -> new TaskCardFactory.AssignedUserModel(
												row.userId(),
												row.displayName(),
												row.color()),
										java.util.stream.Collectors.toList())));
				runOnFx(() -> {
					if (caseId == null || caseId.longValue() != activeCaseId) {
						return;
					}
					caseTasks = tasks == null ? List.of() : tasks;
					caseTaskAssignedUsers = assignedByTask;
					renderTasksSection();
				});
			} catch (Exception ex) {
				runOnFx(() -> {
					caseTasks = List.of();
					caseTaskAssignedUsers = java.util.Map.of();
					renderTasksSection();
				});
				System.err.println("Case tasks load failed for caseId=" + activeCaseId + ": " + ex.getMessage());
			}
		}, "case-load-tasks-" + activeCaseId).start();
	}

	private void setUpdatesPaneVisible(boolean showOnRight) {
		if (caseRootPane == null || caseUpdatesPane == null) {
			return;
		}
		if (showOnRight) {
			if (caseRootPane.getRight() != caseUpdatesPane) {
				caseRootPane.setRight(caseUpdatesPane);
			}
		} else if (caseRootPane.getRight() != null) {
			caseRootPane.setRight(null);
		}
	}

	private void renderTasksSection() {
		if (tasksTabFlow == null || tasksTabEmptyLabel == null) {
			return;
		}

		tasksTabFlow.getChildren().clear();
		if (caseTasks == null || caseTasks.isEmpty()) {
			setVisibleManaged(tasksTabEmptyLabel, true);
			tasksTabEmptyLabel.setText("No tasks for this case yet.");
			return;
		}

		TaskCardFactory factory = taskCardFactory != null
				? taskCardFactory
				: buildTaskCardFactory(this::openTask);

		for (CaseTaskListItemDto task : caseTasks) {
			TaskCardFactory.TaskCardModel model = new TaskCardFactory.TaskCardModel(
					task.id(),
					task.caseId(),
					task.caseName(),
					task.caseResponsibleAttorney(),
					task.caseResponsibleAttorneyColor(),
					task.title(),
					task.description(),
					task.priorityColorHex(),
					task.dueAt(),
					task.completedAt(),
					caseTaskAssignedUsers.getOrDefault(task.id(), List.of()));
			tasksTabFlow.getChildren().add(factory.create(model, TaskCardFactory.Variant.COMPACT));
		}

		setVisibleManaged(tasksTabEmptyLabel, false);
	}


	private boolean isSectionActive(String sectionName) {
		return Objects.equals(activeSectionName, sectionName);
	}


	private void onAddRelatedEntity() {
		if (isSectionActive("Parties")) {
			onAddParty();
		}
	}


	private void onAddTask() {
		if (caseTaskService == null || caseId == null || appState == null) {
			return;
		}
		Integer shaleClientId = appState.getShaleClientId();
		Integer currentUserId = appState.getUserId();
		if (shaleClientId == null || shaleClientId <= 0 || currentUserId == null || currentUserId <= 0) {
			showTaskActionError("You must be signed in to create tasks.");
			return;
		}

		List<TaskPriorityOptionDto> priorities;
		try {
			priorities = caseTaskService.loadActivePriorities(shaleClientId);
		} catch (Exception ex) {
			logTaskActionException("load-priorities", ex);
			showTaskActionError("Unable to load priorities right now.");
			return;
		}

		List<CaseTaskService.AssignableUserOption> assignableUsers;
		try {
			assignableUsers = caseTaskService.loadAssignableUsers(shaleClientId);
		} catch (Exception ex) {
			logTaskActionException("load-assignees", ex);
			showTaskActionError("Unable to load users right now.");
			return;
		}

		Optional<NewTaskDialog.CreateTaskInput> input = NewTaskDialog.showAndWait(
				taskDialogOwner(),
				priorities,
				assignableUsers);
		if (input.isEmpty()) {
			return;
		}

		CaseTaskService.CreateTaskRequest request = new CaseTaskService.CreateTaskRequest(
				shaleClientId,
				caseId.longValue(),
				input.get().title(),
				input.get().description(),
				input.get().dueAt(),
				input.get().priorityId(),
				input.get().assignedUserIds(),
				currentUserId);

		new Thread(() -> {
			try {
				caseTaskService.createTask(request);
				runOnFx(this::refreshCaseTasks);
			} catch (Exception ex) {
				logTaskActionException("create", ex);
				runOnFx(() -> showTaskActionError("Failed to create task for this case. " + rootCauseMessage(ex)));
			}
		}, "case-create-task-" + caseId).start();
	}

	private void onToggleTaskComplete(Long taskId) {
		if (taskId == null || taskId <= 0 || caseTaskService == null || appState == null) {
			return;
		}
		Integer shaleClientId = appState.getShaleClientId();
		if (shaleClientId == null || shaleClientId <= 0) {
			showTaskActionError("Unable to update task right now.");
			return;
		}
		boolean currentlyCompleted = findCaseTaskById(taskId)
				.map(task -> task.completedAt() != null)
				.orElse(false);
		new Thread(() -> {
			try {
				if (currentlyCompleted) {
					caseTaskService.uncompleteTask(taskId, shaleClientId);
				} else {
					caseTaskService.completeTask(taskId, shaleClientId);
				}
				runOnFx(this::refreshCaseTasks);
			} catch (Exception ex) {
				logTaskActionException("toggle-complete", ex);
				runOnFx(() -> showTaskActionError("Failed to update task completion. " + rootCauseMessage(ex)));
			}
		}, "case-toggle-task-" + taskId).start();
	}

	private void showTaskDetailPopup(Long taskId) {
	    if (taskId == null || taskId <= 0 || caseTaskService == null || appState == null) {
	        return;
	    }

	    Integer shaleClientId = appState.getShaleClientId();
	    Integer currentUserId = appState.getUserId();
	    if (shaleClientId == null || shaleClientId <= 0 || currentUserId == null || currentUserId <= 0) {
	        showTaskActionError("You must be signed in to edit tasks.");
	        return;
	    }
	    if (!taskDetailDialogInFlight.compareAndSet(false, true)) {
	        return;
	    }

	    new Thread(() -> {
	        try {
	            TaskDetailDto detail = caseTaskService.loadTaskDetail(taskId, shaleClientId);
	            List<TaskPriorityOptionDto> priorities = caseTaskService.loadActivePriorities(shaleClientId);
                List<CaseTaskService.AssignedTaskUserOption> assignedTeam =
                        detail == null
                                ? List.of()
                                : caseTaskService.loadAssignedUsersForTask(detail.id(), shaleClientId);

	            runOnFx(() -> {
	                try {
	                    if (detail == null) {
	                        showTaskActionError("Task was not found or may have been deleted.");
	                        refreshCaseTasks();
	                        return;
	                    }

	                    TaskDetailDialog.TaskDetailModel model = new TaskDetailDialog.TaskDetailModel(
	                            detail.id(),
	                            detail.caseId(),
	                            detail.caseName(),
	                            detail.caseResponsibleAttorney(),
	                            detail.caseResponsibleAttorneyColor(),
	                            detail.title(),
	                            detail.description(),
	                            detail.dueAt(),
	                            detail.priorityId(),
                                detail.createdByDisplayName(),
                                assignedTeam.stream()
                                        .map(member -> new TaskDetailDialog.AssignedTeamMember(
                                                member.userId(),
                                                member.displayName(),
                                                member.color()))
                                        .toList(),
	                            detail.completedAt() != null
	                    );

	                    Optional<TaskDetailDialog.TaskDetailResult> result =
	                            TaskDetailDialog.showAndWait(
	                                    taskDialogOwner(),
	                                    model,
	                                    priorities,
	                                    id -> caseTaskService.loadAssignableUsersForTask(id, shaleClientId),
	                                    new TaskDetailDialog.AssignmentEditor() {
	                                        @Override
	                                        public List<TaskDetailDialog.AssignedTeamMember> addAndReload(int userId) {
	                                            caseTaskService.addTaskAssignment(model.taskId(), shaleClientId, userId, currentUserId);
	                                            return caseTaskService.loadAssignedUsersForTask(model.taskId(), shaleClientId).stream()
	                                                    .map(member -> new TaskDetailDialog.AssignedTeamMember(
	                                                            member.userId(),
	                                                            member.displayName(),
	                                                            member.color()))
	                                                    .toList();
	                                        }

	                                        @Override
	                                        public List<TaskDetailDialog.AssignedTeamMember> removeAndReload(int userId) {
	                                            caseTaskService.removeTaskAssignment(model.taskId(), shaleClientId, userId);
	                                            return caseTaskService.loadAssignedUsersForTask(model.taskId(), shaleClientId).stream()
	                                                    .map(member -> new TaskDetailDialog.AssignedTeamMember(
	                                                            member.userId(),
	                                                            member.displayName(),
	                                                            member.color()))
	                                                    .toList();
	                                        }
	                                    },
	                                    onOpenCase);

	                    if (result.isEmpty()) {
	                        return;
	                    }

	                    TaskDetailDialog.TaskDetailResult action = result.get();
	                    if (action.action() == TaskDetailDialog.TaskDetailAction.DELETE) {
	                        deleteTaskFromDetail(taskId, shaleClientId);
	                        return;
	                    }

	                    TaskDetailDialog.SaveTaskPayload payload = action.payload();
	                    if (payload == null) {
	                        return;
	                    }

	                    saveTaskFromDetail(taskId, shaleClientId, currentUserId, payload);
	                } finally {
	                    taskDetailDialogInFlight.set(false);
	                }
	            });
	        } catch (Exception ex) {
	            logTaskActionException("load-detail", ex);
	            runOnFx(() -> {
	                taskDetailDialogInFlight.set(false);
	                showTaskActionError("Failed to load task details. " + rootCauseMessage(ex));
	            });
	        }
	    }, "case-task-detail-" + taskId).start();
	}

	private void saveTaskFromDetail(
			long taskId,
			int shaleClientId,
			int currentUserId,
			TaskDetailDialog.SaveTaskPayload payload) {
		CaseTaskService.UpdateTaskRequest request = new CaseTaskService.UpdateTaskRequest(
				taskId,
				shaleClientId,
				payload.title(),
				payload.description(),
				payload.dueAt(),
				payload.priorityId(),
				payload.completed(),
				currentUserId);

		new Thread(() -> {
			try {
				caseTaskService.updateTask(request);
				runOnFx(this::refreshCaseTasks);
			} catch (Exception ex) {
				logTaskActionException("save-detail", ex);
				runOnFx(() -> showTaskActionError("Failed to save task. " + rootCauseMessage(ex)));
			}
		}, "case-task-save-detail-" + taskId).start();
	}

	private void deleteTaskFromDetail(long taskId, int shaleClientId) {
		new Thread(() -> {
			try {
				caseTaskService.deleteTask(taskId, shaleClientId);
				runOnFx(this::refreshCaseTasks);
			} catch (Exception ex) {
				logTaskActionException("delete-detail", ex);
				runOnFx(() -> showTaskActionError("Failed to delete task. " + rootCauseMessage(ex)));
			}
		}, "case-task-delete-detail-" + taskId).start();
	}

	private void refreshCaseTasks() {
		loadCaseTasksAsync();
	}

	private CaseTaskService.CaseTasksSortOption selectedCaseTaskSort() {
		String selectedSort = caseTasksSortChoice == null ? null : caseTasksSortChoice.getValue();
		if (CASE_TASKS_SORT_DUE_DESC.equals(selectedSort)) {
			return CaseTaskService.CaseTasksSortOption.DUE_DATE_DESC;
		}
		if (CASE_TASKS_SORT_PRIORITY_ASC.equals(selectedSort)) {
			return CaseTaskService.CaseTasksSortOption.PRIORITY_ASC;
		}
		if (CASE_TASKS_SORT_PRIORITY_DESC.equals(selectedSort)) {
			return CaseTaskService.CaseTasksSortOption.PRIORITY_DESC;
		}
		return CaseTaskService.CaseTasksSortOption.DUE_DATE_ASC;
	}

	private Optional<CaseTaskListItemDto> findCaseTaskById(Long taskId) {
		if (taskId == null || caseTasks == null) {
			return Optional.empty();
		}
		for (CaseTaskListItemDto task : caseTasks) {
			if (task.id() == taskId.longValue()) {
				return Optional.of(task);
			}
		}
		return Optional.empty();
	}

	private void showTaskActionError(String message) {
		AppDialogs.showError(taskDialogOwner(), "Tasks", message);
	}

	private void logTaskActionException(String action, Exception ex) {
		System.err.println("Task action failed (" + action + ") for caseId=" + caseId + ": " + ex.getMessage());
		ex.printStackTrace();
	}

	private String rootCauseMessage(Throwable throwable) {
		if (throwable == null) {
			return "";
		}
		Throwable current = throwable;
		while (current.getCause() != null && current.getCause() != current) {
			current = current.getCause();
		}
		String message = current.getMessage();
		return (message == null || message.isBlank()) ? "" : "Details: " + message;
	}

	private Window taskDialogOwner() {
		if (tasksTabPane != null && tasksTabPane.getScene() != null) {
			return tasksTabPane.getScene().getWindow();
		}
		if (addTaskButton != null && addTaskButton.getScene() != null) {
			return addTaskButton.getScene().getWindow();
		}
		return null;
	}

	private void openTask(Long taskId) {
		showTaskDetailPopup(taskId);
	}

	private void openOrganization(Integer organizationId) {
		if (organizationId == null || onOpenOrganization == null) {
			return;
		}
		onOpenOrganization.accept(organizationId);
	}


	private Window organizationDialogOwner() {
		if (addOrganizationButton != null && addOrganizationButton.getScene() != null) {
			return addOrganizationButton.getScene().getWindow();
		}
		return null;
	}


	// ----------------------------
	// Overview loading
	// ----------------------------

	// ----------------------------
	// Loading + applying data
	// ----------------------------

	private void loadOverviewOnce() {
		if (overviewLoaded || caseDao == null || caseId == null)
			return;
		overviewLoaded = true;
		reloadCurrentCaseForViewMode();
	}

	private void reloadCurrentCaseForViewMode() {
		if (caseDao == null || caseId == null)
			return;
		final long activeCaseId = caseId.longValue();
		loadCaseUpdatesAsync();
		loadCaseTasksAsync();

		new Thread(() ->
		{
			CaseOverviewDto overview = caseDao.getOverview(activeCaseId);
			CaseDetailDto detail = caseDao.getDetail(activeCaseId);
			List<CasePartyDto> loadedParties = List.of();
			boolean partiesLoadSucceeded = false;
			try {
				loadedParties = caseDao.listCaseParties(activeCaseId);
				partiesLoadSucceeded = true;
			} catch (Exception partiesLoadError) {
				System.err.println("Case parties load failed for caseId=" + activeCaseId + ": " + partiesLoadError.getMessage());
			}
			final List<CasePartyDto> parties = loadedParties;
			final boolean partiesReady = partiesLoadSucceeded;

			runOnFx(() ->
			{
				if (overview == null || detail == null) {
					handleMissingCase();
					return;
				}

				caseParties = parties == null ? List.of() : parties;
				partiesLoadedOnce = partiesReady;
				renderPartiesSection();
				CaseOverviewDto effectiveOverview = applyCallerFromCaseParties(overview, caseParties);
				applyOverviewEditSafe(effectiveOverview);

				current = detail;
				detailsLocalViewOverride = null;
				renderDetailsFromCurrent();
				if (!editMode)
					applyDetail(detail);
				else
					applyLastUpdatedLabel(detail.getUpdatedAt());

				hideRemoteUpdateBanner();
				clearError();
				refreshDeleteAction();
			});
		}, "case-view-sync-" + activeCaseId).start();
	}

	private void refreshLastUpdatedLabelAsync() {
		if (caseDao == null || caseId == null)
			return;
		final long activeCaseId = caseId.longValue();

		new Thread(() ->
		{
			try {
				CaseDetailDto detail = caseDao.getDetail(activeCaseId);
				if (detail == null)
					return;
				LocalDateTime updatedAt = detail.getUpdatedAt();
				runOnFx(() ->
				{
					if (caseId == null || caseId.longValue() != activeCaseId)
						return;
					applyLastUpdatedLabel(updatedAt);
				});
			} catch (Exception ignored) {
			}
		}, "case-refresh-last-updated-" + activeCaseId).start();
	}

	private CaseOverviewDto applyCallerFromCaseParties(CaseOverviewDto overview, List<CasePartyDto> parties) {
		if (overview == null) {
			return null;
		}
		CallerPartySelection caller = resolveCallerFromCaseParties(parties);
		OpposingCounselPartySelection opposingCounsel = resolveOpposingCounselFromCaseParties(parties);
		List<CaseOverviewDto.ContactSummary> representedClients = resolveRepresentedClientsFromCaseParties(parties);
		boolean hasAnyCallerRows = hasCallerRows(parties);
		boolean hasAnyOpposingCounselRows = hasOpposingCounselRows(parties);
		Integer effectiveCallerId = caller == null
				? (hasAnyCallerRows ? overview.getPrimaryCallerContactId() : null)
				: caller.contactId();
		String effectiveCallerName = caller == null
				? (hasAnyCallerRows ? overview.getCaller() : null)
				: caller.displayName();
		Integer effectiveOpposingCounselId = opposingCounsel == null
				? (hasAnyOpposingCounselRows ? overview.getPrimaryOpposingCounselContactId() : null)
				: opposingCounsel.contactId();
		String effectiveOpposingCounselName = opposingCounsel == null
				? (hasAnyOpposingCounselRows ? overview.getOpposingCounsel() : null)
				: opposingCounsel.displayName();
		if (Objects.equals(overview.getPrimaryCallerContactId(), effectiveCallerId)
				&& Objects.equals(safeText(overview.getCaller()), safeText(effectiveCallerName))
				&& Objects.equals(overview.getPrimaryOpposingCounselContactId(), effectiveOpposingCounselId)
				&& Objects.equals(safeText(overview.getOpposingCounsel()), safeText(effectiveOpposingCounselName))
				&& Objects.equals(overview.getClients(), representedClients)) {
			return overview;
		}

		return new CaseOverviewDto(
				overview.getCaseId(),
				overview.getCaseNumber(),
				overview.getCaseName(),
				overview.getCaseStatus(),
				overview.getPrimaryStatusId(),
				overview.getPrimaryStatusColor(),
				overview.getResponsibleAttorneyUserId(),
				overview.getResponsibleAttorney(),
				overview.getResponsibleAttorneyColor(),
				overview.getPracticeAreaId(),
				overview.getPracticeArea(),
				overview.getPracticeAreaColor(),
				overview.getIntakeDate(),
				overview.getIncidentDate(),
				overview.getSolDate(),
					effectiveCallerId,
					overview.getPrimaryClientContactId(),
					effectiveOpposingCounselId,
					effectiveCallerName,
					overview.getClient(),
					representedClients,
					effectiveOpposingCounselName,
					overview.getTeam(),
					overview.getDescription());
	}

	private boolean hasCallerRows(List<CasePartyDto> parties) {
		if (parties == null || parties.isEmpty()) {
			return false;
		}
		return parties.stream()
				.filter(Objects::nonNull)
				.anyMatch(party -> matchesPartyRoleSystemKey(party, "caller"));
	}

	private boolean hasOpposingCounselRows(List<CasePartyDto> parties) {
		if (parties == null || parties.isEmpty()) {
			return false;
		}
		return parties.stream()
				.filter(Objects::nonNull)
				.filter(party -> matchesPartyRoleSystemKey(party, "counsel"))
				.anyMatch(party -> "opposing".equalsIgnoreCase(safeText(party.getSide()).trim()));
	}

	private List<CaseOverviewDto.ContactSummary> resolveRepresentedClientsFromCaseParties(List<CasePartyDto> parties) {
		if (parties == null || parties.isEmpty()) {
			return List.of();
		}
		return parties.stream()
				.filter(Objects::nonNull)
				.filter(party -> matchesPartyRoleSystemKey(party, "party"))
				.filter(party -> "represented".equalsIgnoreCase(safeText(party.getSide()).trim()))
				.sorted(Comparator
						.comparing(CasePartyDto::isPrimary, Comparator.reverseOrder())
						.thenComparing(p -> safeText(p.getDisplayName()), String.CASE_INSENSITIVE_ORDER)
						.thenComparing(CasePartyDto::getId))
				.map(party -> new CaseOverviewDto.ContactSummary(
						party.getContactId() == null ? null : party.getContactId().intValue(),
						safeText(party.getDisplayName())))
				.toList();
	}

	private CallerPartySelection resolveCallerFromCaseParties(List<CasePartyDto> parties) {
		if (parties == null || parties.isEmpty()) {
			return null;
		}
		CasePartyDto firstFallback = null;
		for (CasePartyDto party : parties) {
			if (party == null || party.getContactId() == null) {
				continue;
			}
			if (!matchesPartyRoleSystemKey(party, "caller")) {
				continue;
			}
			if (party.isPrimary()) {
				return new CallerPartySelection(party.getContactId().intValue(), safeText(party.getDisplayName()));
			}
			if (firstFallback == null) {
				firstFallback = party;
			}
		}
		if (firstFallback == null) {
			return null;
		}
		return new CallerPartySelection(firstFallback.getContactId().intValue(), safeText(firstFallback.getDisplayName()));
	}

	private OpposingCounselPartySelection resolveOpposingCounselFromCaseParties(List<CasePartyDto> parties) {
		if (parties == null || parties.isEmpty()) {
			return null;
		}
		CasePartyDto firstFallback = null;
		for (CasePartyDto party : parties) {
			if (party == null || party.getContactId() == null) {
				continue;
			}
			String side = safeText(party.getSide()).trim().toLowerCase(Locale.ROOT);
			if (!matchesPartyRoleSystemKey(party, "counsel") || !"opposing".equals(side)) {
				continue;
			}
			if (party.isPrimary()) {
				return new OpposingCounselPartySelection(party.getContactId().intValue(), safeText(party.getDisplayName()));
			}
			if (firstFallback == null) {
				firstFallback = party;
			}
		}
		if (firstFallback == null) {
			return null;
		}
		return new OpposingCounselPartySelection(firstFallback.getContactId().intValue(), safeText(firstFallback.getDisplayName()));
	}

	private boolean matchesPartyRoleSystemKey(CasePartyDto party, String systemKey) {
		if (party == null) {
			return false;
		}
		String normalizedKey = safeText(systemKey).trim().toLowerCase(Locale.ROOT);
		if (normalizedKey.isBlank()) {
			return false;
		}
		String partySystemKey = safeText(party.getPartyRoleSystemKey()).trim().toLowerCase(Locale.ROOT);
		if (normalizedKey.equals(partySystemKey)) {
			return true;
		}
		String legacyNameFallback = safeText(party.getPartyRoleName()).trim().toLowerCase(Locale.ROOT);
		return normalizedKey.equals(legacyNameFallback);
	}

	private void applyLastUpdatedLabel(LocalDateTime updatedAt) {
		if (lastUpdatedLabel != null)
			lastUpdatedLabel.setText("Last updated: " + formatDateTime(updatedAt));
	}

	// ----------------------------
	// Overview rendering
	// ----------------------------
	// Adding a new editable overview field (behavior-preserving checklist):
	// 1) Display/render path: update CaseOverviewRenderer view + edit-safe render helpers.
	// 2) Draft/edit path: snapshot/reset/apply draft in CaseOverviewEditor.
	// 3) Save path: include field in SaveCoordinator desired-value capture/publish.
	// 4) Live update path: map remote patch handling in CaseOverviewLiveUpdateHandler.

	// Thin wrapper keeps controller as FXML entrypoint while render ownership stays in renderer.
	private void applyOverview(CaseOverviewDto dto) {
		overviewRenderer.applyOverview(dto);
	}


	// Thin wrapper keeps controller wiring stable for callers outside renderer internals.
	private void applyDetail(CaseDetailDto detail) {
		overviewRenderer.applyDetail(detail);
	}


	// Thin wrapper keeps edit-safe refresh callsites unchanged after refactor.
	private void applyOverviewEditSafe(CaseOverviewDto dto) {
		overviewRenderer.applyOverviewEditSafe(dto);
	}


	// ----------------------------
	// Edit lifecycle
	// ----------------------------

	// FXML action wrapper: edit lifecycle orchestration lives in CaseOverviewEditor.
	private void onEdit() {
		overviewEditor.beginEdit();
	}

	// FXML action wrapper: preserves controller ownership while delegating behavior.
	private void onCancel() {
		overviewEditor.cancelEdit();
	}

	// FXML action wrapper for remote-conflict resolution during edit mode.
	private void onReloadRemote() {
		overviewEditor.reloadRemote();
	}

	private void clearDraftState() {
		overviewEditor.clearDraftState();
	}

	private void setEditMode(boolean enabled) {
		overviewEditor.setEditMode(enabled);
	}

	private void setBusy(boolean busy) {
		runOnFx(() ->
		{
			if (deleteCaseButton != null)
				deleteCaseButton.setDisable(busy);
			if (editButton != null)
				editButton.setDisable(busy);
			if (saveButton != null)
				saveButton.setDisable(busy);
			if (cancelButton != null)
				cancelButton.setDisable(busy);
			if (ovCaseNameEditor != null)
				ovCaseNameEditor.setDisable(busy);
			if (ovCaseNumberEditor != null)
				ovCaseNumberEditor.setDisable(busy);
			if (ovDescriptionEditor != null)
				ovDescriptionEditor.setDisable(busy);
			if (ovIncidentDateEditor != null)
				ovIncidentDateEditor.setDisable(busy);
			if (ovSolDateEditor != null)
				ovSolDateEditor.setDisable(busy);
			if (reloadRemoteButton != null)
				reloadRemoteButton.setDisable(busy);
			if (changeStatusButton != null)
				changeStatusButton.setDisable(busy);
			if (changeCallerButton != null)
				changeCallerButton.setDisable(busy);
			if (changeClientButton != null)
				changeClientButton.setDisable(busy);
			if (changePracticeAreaButton != null)
				changePracticeAreaButton.setDisable(busy);
			if (changeResponsibleAttorneyButton != null)
				changeResponsibleAttorneyButton.setDisable(busy);
			if (changeOpposingCounselButton != null)
				changeOpposingCounselButton.setDisable(busy);
			if (detailsEditButton != null)
				detailsEditButton.setDisable(busy);
			if (detailsSaveButton != null)
				detailsSaveButton.setDisable(busy);
			if (detailsCancelButton != null)
				detailsCancelButton.setDisable(busy);
		});
	}

	private void onDeleteCase() {
		if (caseDetailService == null || current == null || caseId == null) {
			showError("Case details are unavailable.");
			return;
		}
		if (!canDeleteCurrentCase()) {
			showError("Only admin and attorney users can delete cases.");
			return;
		}
		if (!confirmDeleteCase()) {
			return;
		}

		Integer tenantId = appState == null ? null : appState.getShaleClientId();
		if (tenantId == null || tenantId <= 0) {
			showDeleteFailure("Failed to delete case.");
			return;
		}

		setBusy(true);
		final long activeCaseId = caseId.longValue();
		new Thread(() -> {
			try {
				boolean deleted = caseDetailService.softDeleteCase(activeCaseId, tenantId);
				runOnFx(() -> {
					setBusy(false);
					if (!deleted) {
						showDeleteFailure("Case could not be deleted.");
						return;
					}
					clearError();
					publishCaseDeleted(activeCaseId);
					navigateAfterDelete();
				});
			} catch (RuntimeException ex) {
				runOnFx(() -> {
					setBusy(false);
					showDeleteFailure("Failed to delete case.");
				});
			}
		}, "case-delete-" + activeCaseId).start();
	}

	private boolean confirmDeleteCase() {
		Window owner = dialogOwner(deleteCaseButton);
		if (owner == null) {
			owner = dialogOwner(editButton);
		}
		String caseName = currentOverview == null ? null : safe(currentOverview.getCaseName());
		if (caseName == null || caseName.isBlank()) {
			caseName = current == null ? null : safe(current.getCaseName());
		}
		String detail = (caseName == null || caseName.isBlank())
				? "This will remove the case from normal views across Shale."
				: "\"" + caseName + "\" will be removed from normal views across Shale.";
		return AppDialogs.showConfirmation(
				owner,
				"Delete Case",
				"Delete this case?",
				detail,
				"Delete Case",
				AppDialogs.DialogActionKind.DANGER);
	}

	private void showDeleteFailure(String message) {
		showError(message);
		AppDialogs.showError(dialogOwner(deleteCaseButton), "Delete Case", message);
	}

	private Window dialogOwner(Button button) {
		if (button != null && button.getScene() != null) {
			return button.getScene().getWindow();
		}
		return null;
	}

	private void navigateAfterDelete() {
		if (onCaseDeleted != null) {
			onCaseDeleted.run();
		}
	}

	private void handleMissingCase() {
		current = null;
		currentOverview = null;
		caseParties = List.of();
		partiesLoadedOnce = false;
		renderPartiesSection();
		refreshDeleteAction();
		navigateAfterDelete();
	}

	private void refreshDeleteAction() {
		boolean showDelete = canDeleteCurrentCase() && !editMode && !detailsEditMode;
		setVisibleManaged(deleteCaseButton, showDelete);
	}

	private boolean canDeleteCurrentCase() {
		return current != null && caseDetailService != null && caseDetailService.canDeleteCase();
	}

	// ----------------------------
	// Change actions
	// ----------------------------

	private void onChangeResponsibleAttorney() {
		overviewPickerCoordinator.changeResponsibleAttorney();
	}

	// ----------------------------
	// Status / Caller / Client / PracticeArea pickers
	// ----------------------------

	private void onChangeStatus() {
		overviewPickerCoordinator.changePrimaryStatus();
	}

	private void onChangeCaller() {
		overviewPickerCoordinator.changeCaller();
	}

	private void onManageClients() {
		overviewPickerCoordinator.manageClients();
	}

	private void onChangePracticeArea() {
		overviewPickerCoordinator.changePracticeArea();
	}

	private void onDetailsChangeStatus() {
		overviewPickerCoordinator.changePrimaryStatusForDetails();
	}

	private void onDetailsChangePracticeArea() {
		overviewPickerCoordinator.changePracticeAreaForDetails();
	}

	private void onChangeOpposingCounsel() {
		overviewPickerCoordinator.changeOpposingCounsel();
	}

	// ----------------------------
	// Save pipeline
	// ----------------------------

	private void onSave() {
		saveCoordinator.save();
	}

	private void onSaveDetails() {
		detailsSaveCoordinator.save();
	}

	private void publishCaseFieldUpdated(long caseId, String field, Object newValueOrNull) {
		if (runtimeBridge == null || appState == null || appState.getShaleClientId() == null || appState.getUserId() == null)
			return;

		try {
			int clientId = appState.getShaleClientId();
			int userId = appState.getUserId();

			runtimeBridge.publishEntityFieldUpdated("Case", caseId, clientId, userId, field, newValueOrNull);
		} catch (Exception ex) {
			System.out.println("CaseUpdated publish skipped: " + ex.getMessage());
		}
	}

	private void publishCaseUpdateAdded(long caseId) {
		if (runtimeBridge == null || appState == null || appState.getShaleClientId() == null || appState.getUserId() == null)
			return;
		publishCaseFieldUpdated(caseId, "caseUpdateAdded", 1);
	}

	private void publishCaseDeleted(long caseId) {
		if (runtimeBridge == null || appState == null || appState.getShaleClientId() == null || appState.getUserId() == null)
			return;
		publishCaseFieldUpdated(caseId, "deleted", 1);
	}

	// ----------------------------
	// Live updates
	// ----------------------------

	private void subscribeLiveCaseUpdates() {
		liveUpdateHandler.subscribe();
	}


	private void refreshCurrentAfterRemoteUpdateAsync() {
		if (caseDao == null || caseId == null)
			return;
		final long id = caseId.longValue();

		new Thread(() ->
		{
			try {
				CaseDetailDto fresh = caseDao.getDetail(id);
				if (fresh == null) {
					runOnFx(this::handleMissingCase);
					return;
				}

				runOnFx(() ->
				{
					if (editMode) {
						showRemoteUpdateBanner();
						return;
					}
					current = fresh;
				});
			} catch (Exception ignored) {
			}
		}, "case-refresh-current-" + id).start();
	}

	private void refreshDetailsBaselineAfterRemoteAsync() {
		if (caseDao == null || caseId == null)
			return;
		final long activeCaseId = caseId.longValue();
		new Thread(() ->
		{
			CaseDetailDto detail = caseDao.getDetail(activeCaseId);
			CaseOverviewDto overview = caseDao.getOverview(activeCaseId);
			runOnFx(() ->
			{
				if (caseId == null || caseId.longValue() != activeCaseId || !detailsEditMode)
					return;
				if (detail == null || overview == null) {
					handleMissingCase();
					return;
				}
				if (overview != null)
					currentOverview = overview;
				current = detail;
				detailsBaseline = CaseDetailsDraft.from(detail, currentOverview);
			});
		}, "case-details-remote-baseline-" + activeCaseId).start();
	}

	private void applyLiveCaseName(String newName) {
		String safeName = safeText(newName).trim();
		if (safeName.isBlank())
			return;

		if (ovCaseNameValue != null)
			ovCaseNameValue.setText(safeName);

		String num = (current == null) ? "" : safeText(current.getCaseNumber());
		if (caseTitleLabel != null)
			caseTitleLabel.setText(num.isBlank() ? safeName : safeName + " — " + num);
	}

	private void applyLiveCaseNumber(String newNumber) {
		String safeNum = safeText(newNumber).trim();

		if (ovCaseNumberValue != null)
			ovCaseNumberValue.setText(safeNum.isBlank() ? "—" : safeNum);

		String name = (current == null) ? "" : safeText(current.getCaseName()).trim();
		if (caseTitleLabel != null) {
			if (!name.isBlank() && !safeNum.isBlank())
				caseTitleLabel.setText(name + " — " + safeNum);
			else if (!name.isBlank())
				caseTitleLabel.setText(name);
			else if (!safeNum.isBlank())
				caseTitleLabel.setText(safeNum);
		}
	}

	private void applyLiveCaseDescription(String newDescription) {
		if (ovDescriptionValue != null)
			ovDescriptionValue.setText(safeText(newDescription));
	}

	// ----------------------------
	// Team section
	// ----------------------------

	private void loadTeamSectionAsync() {
		teamCoordinator.loadTeamSectionAsync();
	}

	private void loadTeamSectionAsyncInternal() {
		// ✅ If editing and we have a draft, show it (don’t overwrite with DB)
		if (editMode && draftTeamAssignments != null) {
			renderTeamFromDraft();
			return;
		}

		if (caseDao == null || appState == null || caseId == null)
			return;

		final long activeCaseId = caseId.longValue();

		new Thread(() ->
		{
			try {
				List<CaseDao.CaseUserTeamRow> teamRows = caseDao.listCaseTeamRows(activeCaseId);

				runOnFx(() ->
				{
					// If they entered edit mode while this thread ran, don't overwrite draft
					if (editMode && draftTeamAssignments != null) {
						renderTeamFromDraft();
						return;
					}
					renderTeamCardsFromTeamRows(teamRows);
				});

			} catch (Exception ex) {
				runOnFx(() -> System.out.println("[TEAM] Failed to load team: " + ex.getMessage()));
			}
		}, "case-team-load-" + activeCaseId).start();
	}

	private void renderTeamCardsFromTeamRows(List<CaseDao.CaseUserTeamRow> rows) {
		teamCoordinator.renderTeamCardsFromTeamRows(rows);
	}

	private void renderTeamCardsFromTeamRowsInternal(List<CaseDao.CaseUserTeamRow> rows) {
		if (teamFlow == null)
			return;

		teamFlow.getChildren().clear();

		if (rows == null)
			rows = List.of();

		List<CaseDao.CaseUserTeamRow> filtered = rows.stream()
				.filter(r -> r != null && TEAM_ROLE_IDS.contains(r.roleId()))
				.sorted(java.util.Comparator
						.comparing((CaseDao.CaseUserTeamRow r) -> !(RoleSemantics.isResponsibleAttorneyRoleId(r.roleId()) && r.isPrimary()))
						.thenComparingInt(CaseDao.CaseUserTeamRow::roleId)
						.thenComparing(r -> safeText(r.displayName()), String.CASE_INSENSITIVE_ORDER))
				.toList();

		if (filtered.isEmpty()) {
			teamFlow.getChildren().add(new Label("—"));
			return;
		}

		if (userCardFactory == null) {
			userCardFactory = new UserCardFactory(onOpenUser == null ? id ->
			{
			} : onOpenUser);
		}

		for (var r : filtered) {
			Integer userId = r.userId();
			String name = safeText(r.displayName()).isBlank() ? "—" : r.displayName();
			String color = r.color();
			String initials = r.initials();

			UserCardModel model = new UserCardModel(userId, name, color, initials);
			var card = userCardFactory.create(model, Variant.MINI);

			Label role = new Label(roleLabel(r.roleId()));
			role.getStyleClass().add("muted");

			VBox wrap = new VBox(4, card, role);
			teamFlow.getChildren().add(wrap);
		}
	}

	private String roleLabel(int roleId) {
		return RoleSemantics.caseTeamRoleLabel(roleId);
	}

	@FXML
	private void onEditTeam() {
		teamCoordinator.onEditTeam();
	}

	private void onEditTeamInternal() {
		if (caseDao == null || appState == null || caseId == null) {
			showError("Team edit is unavailable.");
			return;
		}

		Integer tenantId = appState.getShaleClientId();
		if (tenantId == null || tenantId <= 0) {
			showError("No tenant is selected.");
			return;
		}

		// Only allow team edits while in edit mode (matches your "draft" behavior)
		if (!editMode) {
			showError("Click Edit before changing the team.");
			return;
		}

		setBusy(true);
		clearError();

		final long activeCaseId = caseId.longValue();
		final int tId = tenantId;

		new Thread(() ->
		{
			try {
				// Load once and cache for rendering draft team
				List<CaseDao.UserRow> allUsers = caseDao.listUsersForTenant(tId);
				java.util.Map<Integer, CaseDao.UserRow> map = new java.util.HashMap<>();
				for (var u : (allUsers == null ? List.<CaseDao.UserRow>of() : allUsers)) {
					if (u != null)
						map.put(u.id(), u);
				}

				// For the dialog: current assigned roles should come from DRAFT if present, else DB
				List<CaseDao.CaseUserRoleRow> assignedRoles;
				if (draftTeamAssignments != null) {
					assignedRoles = draftTeamAssignments.stream()
							.map(a -> new CaseDao.CaseUserRoleRow(a.userId(), a.roleId()))
							.toList();
				} else {
					assignedRoles = caseDao.listCaseUserRoles(activeCaseId);
				}

				// Attorneys filter (you already have this)
				java.util.Set<Integer> attorneyIds = caseDao.listAttorneyUserIdsForTenant(tId);

				runOnFx(() ->
				{
					setBusy(false);

					this.tenantUserById = map;

					Stage owner = (Stage) teamFlow.getScene().getWindow();

					TeamEditorDialog dlg = new TeamEditorDialog(
							owner,
							allUsers,
							assignedRoles,
							attorneyIds
					);

					dlg.showAndWaitForResult().ifPresent(res ->
					{
						// ✅ update draft only
						draftTeamAssignments = res.assignments().stream()
								.map(a -> new CaseDao.TeamAssignmentRow(a.userId(), a.roleId()))
								.toList();

						// ✅ render immediately from draft
						renderTeamFromDraft();

						// Optional: mark page dirty visually if you want (not required)
					});
				});

			} catch (Exception ex) {
				runOnFx(() ->
				{
					setBusy(false);
					showError("Failed to load team editor. " + ex.getMessage());
				});
			}
		}, "case-team-editor-load-" + activeCaseId).start();
	}

	private void renderTeamFromDraft() {
		teamCoordinator.renderTeamFromDraft();
	}

	private void renderTeamFromDraftInternal() {
		if (teamFlow == null)
			return;

		teamFlow.getChildren().clear();

		if (draftTeamAssignments == null || draftTeamAssignments.isEmpty()) {
			teamFlow.getChildren().add(new Label("—"));
			return;
		}

		if (tenantUserById == null)
			tenantUserById = java.util.Map.of();

		// Convert draft -> display rows using cached tenant users
		List<CaseDao.CaseUserTeamRow> rows = new java.util.ArrayList<>();
		for (var a : draftTeamAssignments) {
			if (a == null)
				continue;
			CaseDao.UserRow u = tenantUserById.get(a.userId());
			String name = (u == null) ? ("User #" + a.userId()) : u.displayName();
			String color = (u == null) ? null : u.color();
			String initials = null; // if you have initials in UserRow, use it; otherwise leave null

			boolean isPrimary = RoleSemantics.isResponsibleAttorneyRoleId(a.roleId());
			rows.add(new CaseDao.CaseUserTeamRow(a.userId(), name, color, initials, a.roleId(), isPrimary));
		}

		renderTeamCardsFromTeamRows(rows);
	}

	// ----------------------------
	// Case updates
	// ----------------------------

	private void loadCaseUpdatesAsync() {
		updatesPanelController.loadCaseUpdatesAsync();
		loadCaseTasksAsync();
	}

	private void loadCaseUpdatesAsyncInternal() {
		if (caseDao == null || caseId == null)
			return;
		final long activeCaseId = caseId.longValue();

		new Thread(() ->
		{
			try {
				List<CaseUpdateDto> updates = caseDao.listCaseUpdates(activeCaseId);
				runOnFx(() ->
				{
					if (caseId == null || caseId.longValue() != activeCaseId)
						return;
					renderCaseUpdates(updates);
				});
			} catch (Exception ex) {
				runOnFx(() -> showError("Failed to load case updates. " + ex.getMessage()));
			}
		}, "case-updates-load-" + activeCaseId).start();
	}

	private void renderCaseUpdates(List<CaseUpdateDto> updates) {
		updatesPanelController.renderCaseUpdates(updates);
	}

	private void renderCaseUpdatesInternal(List<CaseUpdateDto> updates) {
		if (caseUpdatesFeedBox == null)
			return;

		caseUpdatesFeedBox.getChildren().clear();
		List<CaseUpdateDto> safeUpdates = updates == null ? List.of() : List.copyOf(updates);
		caseUpdates = safeUpdates;
		if (editingCaseUpdateId != null
				&& safeUpdates.stream().noneMatch(u -> u != null && u.getId() == editingCaseUpdateId.longValue())) {
			editingCaseUpdateId = null;
			editingCaseUpdateDraftText = "";
			savingCaseUpdateEdit = false;
		}

		if (safeUpdates.isEmpty()) {
			Label empty = new Label("No updates yet.");
			empty.setWrapText(true);
			empty.setStyle("-fx-opacity: 0.7;");
			caseUpdatesFeedBox.getChildren().add(empty);
			if (caseUpdatesScrollPane != null)
				caseUpdatesScrollPane.setVvalue(0.0);
			return;
		}

		for (CaseUpdateDto dto : safeUpdates) {
			if (dto == null)
				continue;
			caseUpdatesFeedBox.getChildren().add(createCaseUpdateCard(dto));
		}

		if (caseUpdatesScrollPane != null)
			caseUpdatesScrollPane.setVvalue(0.0);
	}

	private void onSubmitCaseUpdate() {
		updatesPanelController.onSubmitCaseUpdate();
	}

	private void onSubmitCaseUpdateInternal() {
		if (caseDao == null || appState == null || caseId == null) {
			showError("Case updates are unavailable.");
			return;
		}
		if (caseUpdatesComposerArea == null || submitCaseUpdateButton == null) {
			showError("Case updates controls are unavailable.");
			return;
		}

		Integer shaleClientId = appState.getShaleClientId();
		if (shaleClientId == null || shaleClientId <= 0) {
			showError("No tenant is selected.");
			return;
		}

		String trimmedText = safeText(caseUpdatesComposerArea.getText()).trim();
		if (trimmedText.isBlank()) {
			showError("Update text is required.");
			return;
		}

		final long activeCaseId = caseId.longValue();
		final int activeClientId = shaleClientId;
		final Integer createdByUserId = appState.getUserId();

		submitCaseUpdateButton.setDisable(true);
		caseUpdatesComposerArea.setDisable(true);
		clearError();

		new Thread(() ->
		{
			try {
				caseDao.addCaseNote(activeCaseId, activeClientId, trimmedText, createdByUserId);
				runOnFx(() -> applyLastUpdatedLabel(LocalDateTime.now(ZoneOffset.UTC)));
				publishCaseUpdateAdded(activeCaseId);
				List<CaseUpdateDto> updates = caseDao.listCaseUpdates(activeCaseId);
				runOnFx(() ->
				{
					if (caseId == null || caseId.longValue() != activeCaseId)
						return;
					if (caseUpdatesComposerArea != null) {
						caseUpdatesComposerArea.clear();
						caseUpdatesComposerArea.setDisable(false);
					}
					renderCaseUpdates(updates);
					refreshLastUpdatedLabelAsync();
					if (submitCaseUpdateButton != null)
						submitCaseUpdateButton.setDisable(false);
				});
			} catch (Exception ex) {
				runOnFx(() ->
				{
					showError("Failed to save case update. " + ex.getMessage());
					if (caseUpdatesComposerArea != null)
						caseUpdatesComposerArea.setDisable(false);
					if (submitCaseUpdateButton != null)
						submitCaseUpdateButton.setDisable(false);
				});
			}
		}, "case-updates-submit-" + activeCaseId).start();
	}

	private Node createCaseUpdateCard(CaseUpdateDto dto) {
		return updatesPanelController.createCaseUpdateCard(dto);
	}

	private Node createCaseUpdateCardInternal(CaseUpdateDto dto) {
		Label authorLabel = new Label(safeAuthorName(dto));
		authorLabel.setStyle("-fx-font-weight: bold;");
		authorLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
		authorLabel.setMaxWidth(Double.MAX_VALUE);
		HBox.setHgrow(authorLabel, javafx.scene.layout.Priority.ALWAYS);

		VBox bodyBox;
		HBox rightActions = new HBox();
		rightActions.setAlignment(Pos.CENTER_RIGHT);
		if (!isEditingCaseUpdate(dto) && canEditCaseUpdate(dto)) {
			Button editButton = new Button("Edit");
			editButton.setDisable(savingCaseUpdateEdit);
			editButton.setOnAction(e -> startEditingCaseUpdate(dto));
			rightActions.getChildren().add(editButton);
		}

		HBox topRow = new HBox(8, authorLabel, rightActions);
		topRow.setAlignment(Pos.CENTER_LEFT);

		Label metadataLabel = new Label(buildCaseUpdateMetadata(dto));
		metadataLabel.setWrapText(true);
		metadataLabel.setStyle("-fx-opacity: 0.75; -fx-font-size: 11px;");

		if (isEditingCaseUpdate(dto)) {
			TextArea editArea = new TextArea(editingCaseUpdateDraftText);
			editArea.setWrapText(true);
			editArea.setPrefRowCount(4);
			editArea.setDisable(savingCaseUpdateEdit);
			editArea.textProperty().addListener((obs, oldText, newText) -> editingCaseUpdateDraftText = safeText(newText));

			Button saveButton = new Button("Save");
			saveButton.setDisable(savingCaseUpdateEdit);
			saveButton.setOnAction(e -> saveEditedCaseUpdate(dto));

			Button cancelButton = new Button("Cancel");
			cancelButton.setDisable(savingCaseUpdateEdit);
			cancelButton.setOnAction(e -> cancelEditingCaseUpdate());

			HBox editActions = new HBox(8, saveButton, cancelButton);
			editActions.setAlignment(Pos.CENTER_LEFT);
			bodyBox = new VBox(8, editArea, editActions);
		} else {
			Label noteLabel = new Label(safeText(dto.getNoteText()));
			noteLabel.setWrapText(true);
			bodyBox = new VBox(noteLabel);
		}

		VBox card = new VBox(4, topRow, metadataLabel, bodyBox);
		card.setPadding(new Insets(8, 10, 8, 10));
		card.setStyle("-fx-background-color: rgba(0,0,0,0.04); -fx-background-radius: 8;");
		return card;
	}

	private String buildCaseUpdateMetadata(CaseUpdateDto dto) {
		if (dto == null)
			return "";
		String createdText = "Created " + formatDateTime(dto.getCreatedAt());
		if (!isMeaningfullyEdited(dto)) {
			return createdText;
		}
		return createdText + " • Edited " + formatDateTime(dto.getUpdatedAt());
	}

	private boolean isMeaningfullyEdited(CaseUpdateDto dto) {
		if (dto == null || dto.getUpdatedAt() == null)
			return false;
		if (dto.getCreatedAt() == null)
			return true;
		return dto.getUpdatedAt().isAfter(dto.getCreatedAt().plusSeconds(1));
	}

	private boolean canEditCaseUpdate(CaseUpdateDto dto) {
		if (dto == null || appState == null)
			return false;
		Integer actorUserId = appState.getUserId();
		Integer createdByUserId = dto.getCreatedByUserId();
		return actorUserId != null && createdByUserId != null && actorUserId.intValue() == createdByUserId.intValue();
	}

	private boolean isEditingCaseUpdate(CaseUpdateDto dto) {
		return dto != null && editingCaseUpdateId != null && dto.getId() == editingCaseUpdateId.longValue();
	}

	private void startEditingCaseUpdate(CaseUpdateDto dto) {
		if (dto == null || !canEditCaseUpdate(dto))
			return;
		editingCaseUpdateId = dto.getId();
		editingCaseUpdateDraftText = safeText(dto.getNoteText());
		savingCaseUpdateEdit = false;
		renderCaseUpdates(caseUpdates);
	}

	private void cancelEditingCaseUpdate() {
		editingCaseUpdateId = null;
		editingCaseUpdateDraftText = "";
		savingCaseUpdateEdit = false;
		renderCaseUpdates(caseUpdates);
	}

	private void saveEditedCaseUpdate(CaseUpdateDto dto) {
		if (dto == null || caseDao == null || appState == null || caseId == null)
			return;

		Integer shaleClientId = appState.getShaleClientId();
		Integer actorUserId = appState.getUserId();
		if (shaleClientId == null || shaleClientId <= 0 || actorUserId == null || actorUserId <= 0) {
			showError("Unable to save note edit.");
			return;
		}

		String trimmedText = safeText(editingCaseUpdateDraftText).trim();
		if (trimmedText.isBlank()) {
			showError("Update text is required.");
			return;
		}

		final long activeCaseId = caseId.longValue();
		final long caseUpdateId = dto.getId();
		final int activeClientId = shaleClientId;
		final int activeActorUserId = actorUserId;
		savingCaseUpdateEdit = true;
		clearError();
		renderCaseUpdates(caseUpdates);

		new Thread(() ->
		{
			try {
				boolean updated = caseDao.updateCaseNote(caseUpdateId, activeCaseId, activeClientId, activeActorUserId, trimmedText);
				if (!updated) {
					runOnFx(() ->
					{
						savingCaseUpdateEdit = false;
						showError("Only the note creator can edit this update.");
						renderCaseUpdates(caseUpdates);
					});
					return;
				}
				runOnFx(() -> applyLastUpdatedLabel(LocalDateTime.now(ZoneOffset.UTC)));
				publishCaseUpdateAdded(activeCaseId);
				List<CaseUpdateDto> updates = caseDao.listCaseUpdates(activeCaseId);
				runOnFx(() ->
				{
					if (caseId == null || caseId.longValue() != activeCaseId)
						return;
					editingCaseUpdateId = null;
					editingCaseUpdateDraftText = "";
					savingCaseUpdateEdit = false;
					renderCaseUpdates(updates);
					refreshLastUpdatedLabelAsync();
				});
			} catch (Exception ex) {
				runOnFx(() ->
				{
					savingCaseUpdateEdit = false;
					showError("Failed to save case update. " + ex.getMessage());
					renderCaseUpdates(caseUpdates);
				});
			}
		}, "case-updates-edit-" + activeCaseId + "-" + caseUpdateId).start();
	}

	private static String safeAuthorName(CaseUpdateDto dto) {
		if (dto == null)
			return "Unknown";
		String name = safeText(dto.getCreatedByDisplayName()).trim();
		if (!name.isBlank())
			return name;
		if (dto.getCreatedByUserId() != null)
			return "User #" + dto.getCreatedByUserId();
		return "Unknown";
	}

	// ----------------------------
	// Card rendering
	// ----------------------------

	// ----------------------------
	// Cards
	// ----------------------------

	private void renderResponsibleAttorneyMini(Integer userId, String displayName, String userColorCss) {
		if (userCardFactory == null) {
			userCardFactory = new UserCardFactory(onOpenUser == null ? id ->
			{
			} : onOpenUser);
		}

		UserCardModel model = new UserCardModel(
				userId,
				(displayName == null || displayName.isBlank()) ? "—" : displayName,
				userColorCss,
				null
		);

		var headerCard = userCardFactory.create(model, Variant.MINI);
		var overviewCard = userCardFactory.create(model, Variant.MINI);

		if (assignedUserHost != null)
			assignedUserHost.getChildren().setAll(headerCard);
		if (ovResponsibleAttorneyHost != null)
			ovResponsibleAttorneyHost.getChildren().setAll(overviewCard);
	}

	private void renderPrimaryStatusMini(Integer statusId, String statusName, String statusColorCss) {
		if (statusCardFactory == null) {
			statusCardFactory = new StatusCardFactory(onOpenStatus == null ? id ->
			{
			} : onOpenStatus);
		}

		StatusCardModel model = new StatusCardModel(
				statusId,
				(statusName == null || statusName.isBlank()) ? "—" : statusName,
				null,
				statusColorCss
		);

		var headerCard = statusCardFactory.create(model, StatusCardFactory.Variant.MINI);
		var overviewCard = statusCardFactory.create(model, StatusCardFactory.Variant.MINI);

		if (statusHost != null)
			statusHost.getChildren().setAll(headerCard);
		if (ovCaseStatusHost != null)
			ovCaseStatusHost.getChildren().setAll(overviewCard);
	}

	private void renderPracticeAreaMini(Integer practiceAreaId, String name, String colorHex) {
		if (ovPracticeAreaHost == null)
			return;

		if (practiceAreaCardFactory == null) {
			practiceAreaCardFactory = new PracticeAreaCardFactory(onOpenPracticeArea == null ? id ->
			{
				System.out.println("PracticeAreaCard clicked practiceAreaId=" + id);
			} : onOpenPracticeArea);
		}

		PracticeAreaCardModel model = new PracticeAreaCardModel(
				practiceAreaId,
				(name == null || name.isBlank()) ? "—" : name,
				colorHex
		);

		ovPracticeAreaHost.getChildren().setAll(
				practiceAreaCardFactory.create(model, PracticeAreaCardFactory.Variant.MINI)
		);
	}


	private void renderDetailsStatusMini(Integer statusId, String statusName, String statusColorCss) {
		if (detCaseStatusHost == null)
			return;
		if (statusCardFactory == null) {
			statusCardFactory = new StatusCardFactory(onOpenStatus == null ? id -> { } : onOpenStatus);
		}
		StatusCardModel model = new StatusCardModel(statusId,
				(statusName == null || statusName.isBlank()) ? "—" : statusName,
				null,
				statusColorCss);
		detCaseStatusHost.getChildren().setAll(statusCardFactory.create(model, StatusCardFactory.Variant.MINI));
	}

	private void renderDetailsPracticeAreaMini(Integer practiceAreaId, String name, String colorHex) {
		if (detPracticeAreaHost == null)
			return;
		if (practiceAreaCardFactory == null) {
			practiceAreaCardFactory = new PracticeAreaCardFactory(onOpenPracticeArea == null ? id -> { } : onOpenPracticeArea);
		}
		PracticeAreaCardModel model = new PracticeAreaCardModel(practiceAreaId,
				(name == null || name.isBlank()) ? "—" : name,
				colorHex);
		detPracticeAreaHost.getChildren().setAll(practiceAreaCardFactory.create(model, PracticeAreaCardFactory.Variant.MINI));
	}

	private static Boolean parseNullableBooleanStorage(String raw) {
		String v = safeText(raw).trim();
		if (v.isBlank())
			return null;
		if ("1".equals(v) || "true".equalsIgnoreCase(v) || "yes".equalsIgnoreCase(v) || "y".equalsIgnoreCase(v))
			return Boolean.TRUE;
		if ("0".equals(v) || "false".equalsIgnoreCase(v) || "no".equalsIgnoreCase(v) || "n".equalsIgnoreCase(v))
			return Boolean.FALSE;
		return null;
	}

	private static String toNullableBooleanStorage(Boolean value) {
		if (value == null)
			return null;
		return value ? "1" : "0";
	}

	private static Boolean normalizeDetailsCheckboxBoolean(Boolean value) {
		return Boolean.TRUE.equals(value);
	}

	private static String normalizeDetailsCheckboxStorage(String raw) {
		return toNullableBooleanStorage(normalizeDetailsCheckboxBoolean(parseNullableBooleanStorage(raw)));
	}

	private static String normalizeCallerTimeInput(String value) {
		String trimmed = safeText(value).trim();
		if (trimmed.isBlank())
			return null;
		if (!trimmed.matches("^(?:[01]?\\d|2[0-3]):[0-5]\\d$"))
			throw new IllegalArgumentException("Time of Intake must be in HH:mm format.");
		String[] parts = trimmed.split(":");
		return String.format("%02d:%02d", Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
	}

	private static String normalizeCallerTimeDisplay(String raw) {
		String trimmed = safeText(raw).trim();
		if (trimmed.isBlank())
			return "";

		if (trimmed.matches("^(?:[01]?\\d|2[0-3]):[0-5]\\d$")) {
			String[] parts = trimmed.split(":");
			return String.format("%02d:%02d", Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
		}

		if (trimmed.matches("^(?:[01]?\\d|2[0-3]):[0-5]\\d:[0-5]\\d(?:\\.\\d+)?$")) {
			String[] parts = trimmed.split(":");
			return String.format("%02d:%02d", Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
		}

		return trimmed;
	}

	private void renderCallerMini(Integer contactId, String name) {
		if (ovCallerHost == null)
			return;
		ovCallerHost.getChildren().clear();

		if (contactCardFactory == null) {
			contactCardFactory = new ContactCardFactory(onOpenContact == null ? id ->
			{
			} : onOpenContact);
		}

		ovCallerHost.getChildren().setAll(contactCardFactory.createMini(contactId, safe(name)));
	}

	private void renderClientsMini(List<CaseOverviewDto.ContactSummary> clients) {
		if (ovClientHost == null)
			return;
		ovClientHost.getChildren().clear();

		if (contactCardFactory == null) {
			contactCardFactory = new ContactCardFactory(onOpenContact == null ? id ->
			{
			} : onOpenContact);
		}

		List<CaseOverviewDto.ContactSummary> safeClients = clients == null ? List.of() : clients.stream()
				.filter(Objects::nonNull)
				.toList();
		if (safeClients.isEmpty()) {
			ovClientHost.getChildren().setAll(contactCardFactory.createMini(null, "—"));
			return;
		}
		VBox list = new VBox(8);
		for (CaseOverviewDto.ContactSummary client : safeClients) {
			list.getChildren().add(contactCardFactory.createMini(client.contactId(), safe(client.displayName())));
		}
		ovClientHost.getChildren().setAll(list);
	}

	private void renderOpposingCounselMini(Integer contactId, String name) {
		if (ovOpposingCounselHost == null)
			return;
		ovOpposingCounselHost.getChildren().clear();

		if (contactCardFactory == null) {
			contactCardFactory = new ContactCardFactory(onOpenContact == null ? id ->
			{
			} : onOpenContact);
		}

		ovOpposingCounselHost.getChildren().setAll(contactCardFactory.createMini(contactId, safe(name)));
	}

	private void renderDetailsFromCurrent() {
		if (detailsEditMode && detailsDraft != null)
			return;
		detailsEditor.renderView(resolveDetailsViewModel());
	}

	private CaseDetailsDraft resolveDetailsViewModel() {
		if (detailsLocalViewOverride != null)
			return detailsLocalViewOverride.copy();
		return CaseDetailsDraft.from(current, currentOverview);
	}

	private static String boolLabel(Boolean value) {
		if (value == null)
			return "—";
		return value ? "Yes" : "No";
	}


	// ----------------------------
	// Utilities
	// ----------------------------

	// ----------------------------
	// UI helpers / errors
	// ----------------------------

	private void showRemoteUpdateBanner() {
		setVisibleManaged(remoteUpdateBanner, true);
		setVisibleManaged(reloadRemoteButton, true);
	}

	private void hideRemoteUpdateBanner() {
		setVisibleManaged(remoteUpdateBanner, false);
		setVisibleManaged(reloadRemoteButton, false);
	}

	private void clearError() {
		setErrorLabel(errorLabel, "");
		setErrorLabel(detailsErrorLabel, "");
	}

	private void showError(String message) {
		boolean detailsVisible = detailsScrollPane != null && detailsScrollPane.isVisible();
		if (detailsVisible) {
			setErrorLabel(detailsErrorLabel, message);
			setErrorLabel(errorLabel, "");
			return;
		}
		setErrorLabel(errorLabel, message);
		setErrorLabel(detailsErrorLabel, "");
	}

	private void setErrorLabel(Label target, String message) {
		if (target == null)
			return;
		target.setText(message == null ? "" : message);
		boolean visible = message != null && !message.isBlank();
		target.setVisible(visible);
		target.setManaged(visible);
	}

	private static void runOnFx(Runnable runnable) {
		if (Platform.isFxApplicationThread())
			runnable.run();
		else
			Platform.runLater(runnable);
	}

	private static void setPaneVisible(VBox pane, boolean visible) {
		if (pane == null)
			return;
		pane.setVisible(visible);
		pane.setManaged(visible);
	}

	private static void setVisibleManaged(javafx.scene.Node node, boolean visible) {
		if (node == null)
			return;
		node.setVisible(visible);
		node.setManaged(visible);
	}

	private static String formatDate(LocalDate d) {
		return d == null ? "—" : d.toString();
	}

	private static String safe(String s) {
		return (s == null || s.isBlank()) ? "—" : s;
	}

	private static String safeText(String s) {
		return s == null ? "" : s;
	}

	private static final DateTimeFormatter CASE_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

	private String formatDateTime(LocalDateTime value) {
		return UtcDateTimeDisplayFormatter.formatUtcToLocal(value, CASE_TIMESTAMP_FORMAT);
	}

	private static boolean hasPatchKey(String rawPatchJson, String key) {
		if (rawPatchJson == null || rawPatchJson.isBlank() || key == null || key.isBlank())
			return false;
		return rawPatchJson.contains("\"" + key + "\"");
	}

	private static boolean isPatchExplicitNull(String rawPatchJson, String key) {
		if (rawPatchJson == null || rawPatchJson.isBlank() || key == null || key.isBlank())
			return false;
		String needle = "\"" + key + "\"";
		int k = rawPatchJson.indexOf(needle);
		if (k < 0)
			return false;
		int colon = rawPatchJson.indexOf(':', k + needle.length());
		if (colon < 0)
			return false;
		int i = colon + 1;
		while (i < rawPatchJson.length() && Character.isWhitespace(rawPatchJson.charAt(i)))
			i++;
		return rawPatchJson.regionMatches(true, i, "null", 0, 4);
	}

	private static CaseOverviewDto copyOverviewWithDates(CaseOverviewDto base, LocalDate incidentDate, LocalDate solDate) {
		return new CaseOverviewDto(
				base.getCaseId(),
				base.getCaseNumber(),
				base.getCaseName(),
				base.getCaseStatus(),
				base.getPrimaryStatusId(),
				base.getPrimaryStatusColor(),
				base.getResponsibleAttorneyUserId(),
				base.getResponsibleAttorney(),
				base.getResponsibleAttorneyColor(),
				base.getPracticeAreaId(),
				base.getPracticeArea(),
				base.getPracticeAreaColor(),
				base.getIntakeDate(),
				incidentDate,
				solDate,
				base.getPrimaryCallerContactId(),
				base.getPrimaryClientContactId(),
				base.getPrimaryOpposingCounselContactId(),
				base.getCaller(),
				base.getClient(),
				base.getClients(),
				base.getOpposingCounsel(),
				base.getTeam(),
				base.getDescription()
		);
	}

	private static String extractPatchString(String rawPatchJson, String key) {
		if (rawPatchJson == null || rawPatchJson.isBlank() || key == null || key.isBlank())
			return null;

		String needle = "\"" + key + "\"";
		int k = rawPatchJson.indexOf(needle);
		if (k < 0)
			return null;

		int colon = rawPatchJson.indexOf(':', k + needle.length());
		if (colon < 0)
			return null;

		int firstQuote = rawPatchJson.indexOf('"', colon + 1);
		if (firstQuote < 0)
			return null;

		int secondQuote = rawPatchJson.indexOf('"', firstQuote + 1);
		if (secondQuote < 0)
			return null;

		return rawPatchJson.substring(firstQuote + 1, secondQuote);
	}

	private static LocalDate parsePatchedDate(String patchedValue) {
		if (patchedValue == null || patchedValue.isBlank())
			return null;
		try {
			return LocalDate.parse(patchedValue.trim());
		} catch (Exception ignored) {
			return null;
		}
	}

	private static Integer extractPatchInt(String rawPatchJson, String key) {
		if (rawPatchJson == null || rawPatchJson.isBlank() || key == null || key.isBlank())
			return null;

		String needle = "\"" + key + "\"";
		int k = rawPatchJson.indexOf(needle);
		if (k < 0)
			return null;

		int colon = rawPatchJson.indexOf(':', k + needle.length());
		if (colon < 0)
			return null;

		int i = colon + 1;
		while (i < rawPatchJson.length() && Character.isWhitespace(rawPatchJson.charAt(i)))
			i++;

		boolean quoted = i < rawPatchJson.length() && rawPatchJson.charAt(i) == '"';
		if (quoted)
			i++;

		int start = i;
		while (i < rawPatchJson.length() && Character.isDigit(rawPatchJson.charAt(i)))
			i++;

		if (i == start)
			return null;

		try {
			return Integer.parseInt(rawPatchJson.substring(start, i));
		} catch (Exception ignored) {
			return null;
		}
	}

	private static Optional<CaseDao.ContactRow> showSearchPickerDialog(
			String title,
			String headerText,
			String searchPrompt,
			List<CaseDao.ContactRow> items,
			CaseDao.ContactRow preselectOrNull) {

		javafx.scene.control.Dialog<CaseDao.ContactRow> dialog = new javafx.scene.control.Dialog<>();
		dialog.setTitle(title);
		dialog.setHeaderText(headerText);

		javafx.scene.control.ButtonType okType = new javafx.scene.control.ButtonType("OK", javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
		dialog.getDialogPane().getButtonTypes().addAll(okType, javafx.scene.control.ButtonType.CANCEL);

		javafx.scene.control.TextField searchField = new javafx.scene.control.TextField();
		searchField.setPromptText(searchPrompt);

		javafx.scene.control.ListView<CaseDao.ContactRow> listView = new javafx.scene.control.ListView<>();
		listView.setFixedCellSize(24);
		listView.setPrefHeight(420);

		listView.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
			@Override
			protected void updateItem(CaseDao.ContactRow item, boolean empty) {
				super.updateItem(item, empty);
				setText(empty || item == null ? null : (item.displayName() + "  (#" + item.id() + ")"));
			}
		});

		javafx.collections.ObservableList<CaseDao.ContactRow> all = javafx.collections.FXCollections.observableArrayList(items);
		javafx.collections.ObservableList<CaseDao.ContactRow> filtered = javafx.collections.FXCollections.observableArrayList(items);

		listView.setItems(filtered);

		if (preselectOrNull != null) {
			listView.getSelectionModel().select(preselectOrNull);
			listView.scrollTo(preselectOrNull);
		} else if (!filtered.isEmpty()) {
			listView.getSelectionModel().select(0);
		}

		searchField.textProperty().addListener((obs, oldV, newV) ->
		{
			String q = (newV == null ? "" : newV.trim().toLowerCase());

			filtered.setAll(all.filtered(r ->
			{
				String name = r == null ? "" : (r.displayName() == null ? "" : r.displayName());
				if (q.isEmpty())
					return true;
				return name.toLowerCase().contains(q) || String.valueOf(r.id()).contains(q);
			}));

			if (!filtered.isEmpty())
				listView.getSelectionModel().select(0);
		});

		listView.setOnMouseClicked(e ->
		{
			if (e.getClickCount() == 2) {
				CaseDao.ContactRow sel = listView.getSelectionModel().getSelectedItem();
				if (sel != null) {
					dialog.setResult(sel);
					dialog.close();
				}
			}
		});

		javafx.scene.Node okBtn = dialog.getDialogPane().lookupButton(okType);
		okBtn.setDisable(listView.getSelectionModel().getSelectedItem() == null);
		listView.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> okBtn.setDisable(n == null));

		javafx.scene.layout.VBox box = new javafx.scene.layout.VBox(10, searchField, listView);
		box.setPadding(new javafx.geometry.Insets(10));
		dialog.getDialogPane().setContent(box);

		dialog.setResultConverter(btn -> btn == okType ? listView.getSelectionModel().getSelectedItem() : null);

		javafx.application.Platform.runLater(searchField::requestFocus);
		return dialog.showAndWait();
	}

	private final class CaseOverviewRenderer {
		void applyOverview(CaseOverviewDto dto) {
			if (dto == null)
				return;
			currentOverview = dto;
			renderHeaderTitleFromOverview(dto);
			renderOverviewCards(dto);
			renderOverviewTextFields(dto);
			renderOverviewDates(dto, false);
			loadTeamSectionAsync();
		}

		void applyDetail(CaseDetailDto detail) {
			if (detail == null)
				return;
			if (!editMode && ovCaseNameValue != null)
				ovCaseNameValue.setText(safe(detail.getCaseName()));
			if (!editMode && ovCaseNumberValue != null)
				ovCaseNumberValue.setText(safeText(detail.getCaseNumber()));
			if (!editMode && ovDescriptionValue != null)
				ovDescriptionValue.setText(safeText(detail.getDescription()));
			if (statusLabel != null)
				statusLabel.setText("Status: " + safe(detail.getCaseStatus()));
			renderLastUpdated(detail.getUpdatedAt());
			renderHeaderTitleFromDetail(detail);
		}

		void applyOverviewEditSafe(CaseOverviewDto dto) {
			if (dto == null)
				return;
			currentOverview = dto;
			renderOverviewCards(dto);
			renderHeaderTitleFromOverview(dto);
			if (!editMode) {
				applyOverview(dto);
				return;
			}
			if (ovCaseNumberValue != null)
				ovCaseNumberValue.setText(safe(dto.getCaseNumber()));
			loadTeamSectionAsync();
			renderOverviewDates(dto, true);
		}

		private void renderOverviewCards(CaseOverviewDto dto) {
			renderResponsibleAttorney(dto);
			renderStatus(dto);
			renderContacts(dto);
			renderPracticeArea(dto);
		}

		private void renderResponsibleAttorney(CaseOverviewDto dto) {
			renderResponsibleAttorneyMini(dto.getResponsibleAttorneyUserId(), safe(dto.getResponsibleAttorney()),
					dto.getResponsibleAttorneyColor());
		}

		private void renderStatus(CaseOverviewDto dto) {
			Integer statusId = (editMode && draftPrimaryStatusId != null) ? draftPrimaryStatusId : dto.getPrimaryStatusId();
			renderPrimaryStatusMini(statusId, dto.getCaseStatus(), dto.getPrimaryStatusColor());
		}

		private void renderContacts(CaseOverviewDto dto) {
			String callerName = (editMode && draftPrimaryCallerName != null && !draftPrimaryCallerName.isBlank())
					? draftPrimaryCallerName
					: dto.getCaller();
			Integer callerId = (editMode && draftPrimaryCallerContactId != null)
					? draftPrimaryCallerContactId
					: dto.getPrimaryCallerContactId();
			renderCallerMini(callerId, callerName);

			List<CaseOverviewDto.ContactSummary> clients = (editMode && draftClientContacts != null)
					? draftClientContacts
					: dto.getClients();
			renderClientsMini(clients);

			String oppName = (editMode && draftPrimaryOpposingCounselName != null && !draftPrimaryOpposingCounselName.isBlank())
					? draftPrimaryOpposingCounselName
					: dto.getOpposingCounsel();
			Integer oppId = (editMode && draftPrimaryOpposingCounselContactId != null)
					? draftPrimaryOpposingCounselContactId
					: dto.getPrimaryOpposingCounselContactId();
			renderOpposingCounselMini(oppId, oppName);
		}

		private void renderPracticeArea(CaseOverviewDto dto) {
			Integer paId = (editMode && draftPracticeAreaId != null) ? draftPracticeAreaId : dto.getPracticeAreaId();
			String paName = (editMode && draftPracticeAreaName != null && !draftPracticeAreaName.isBlank())
					? draftPracticeAreaName
					: dto.getPracticeArea();
			String paColor = (editMode && draftPracticeAreaColor != null && !draftPracticeAreaColor.isBlank())
					? draftPracticeAreaColor
					: dto.getPracticeAreaColor();
			renderPracticeAreaMini(paId, paName, paColor);
		}

		private void renderOverviewTextFields(CaseOverviewDto dto) {
			if (ovCaseNameValue != null)
				ovCaseNameValue.setText(safe(dto.getCaseName()));
			if (ovCaseNumberValue != null)
				ovCaseNumberValue.setText(safe(dto.getCaseNumber()));
			if (ovDescriptionValue != null)
				ovDescriptionValue.setText(safeText(dto.getDescription()));
		}

		private void renderOverviewDates(CaseOverviewDto dto, boolean editSafeOnly) {
			if (ovIntakeDateValue != null)
				ovIntakeDateValue.setText(formatDate(dto.getIntakeDate()));
			if (ovIncidentDateValue != null)
				ovIncidentDateValue.setText(formatDate(dto.getIncidentDate()));
			if (ovSolDateValue != null)
				ovSolDateValue.setText(formatDate(dto.getSolDate()));
			if (!editSafeOnly) {
				if (ovIncidentDateEditor != null && !editMode)
					ovIncidentDateEditor.setValue(dto.getIncidentDate());
				if (ovSolDateEditor != null && !editMode)
					ovSolDateEditor.setValue(dto.getSolDate());
			}
		}

		private void renderHeaderTitleFromOverview(CaseOverviewDto dto) {
			if (caseTitleLabel == null)
				return;
			String name = safeText(dto.getCaseName()).trim();
			String num = safeText(dto.getCaseNumber()).trim();
			if (!name.isBlank() && !num.isBlank())
				caseTitleLabel.setText(name + " — " + num);
			else if (!name.isBlank())
				caseTitleLabel.setText(name);
			else if (!num.isBlank())
				caseTitleLabel.setText(num);
			else
				caseTitleLabel.setText("Case #" + dto.getCaseId());
		}

		private void renderHeaderTitleFromDetail(CaseDetailDto detail) {
			if (caseTitleLabel == null)
				return;
			String num = safeText(detail.getCaseNumber()).trim();
			String name = safeText(detail.getCaseName()).trim();
			if (!name.isBlank() && !num.isBlank())
				caseTitleLabel.setText(name + " — " + num);
			else if (!name.isBlank())
				caseTitleLabel.setText(name);
			else if (!num.isBlank())
				caseTitleLabel.setText(num);
		}

		private void renderLastUpdated(LocalDateTime updatedAt) {
			applyLastUpdatedLabel(updatedAt);
		}
	}

	private final class CaseOverviewEditor {
		void beginEdit() {
			snapshotDraftState();
			if (!ensureCurrentDetailReady())
				return;
			applyDraftStateToEditors();
			hideRemoteUpdateBanner();
			clearError();
			setEditMode(true);
			rerenderOverviewForDraft();
		}

		void cancelEdit() {
			clearDraftState();
			hideRemoteUpdateBanner();
			clearError();
			exitEditMode();
			restoreViewMode();
		}

		void reloadRemote() {
			handleRemoteReloadDuringEdit();
		}

		void setEditMode(boolean enabled) {
			editMode = enabled;

			setVisibleManaged(ovCaseNameValue, !enabled);
			setVisibleManaged(ovCaseNameEditor, enabled);
			setVisibleManaged(ovCaseNumberValue, !enabled);
			setVisibleManaged(ovCaseNumberEditor, enabled);

			setVisibleManaged(ovDescriptionValue, !enabled);
			setVisibleManaged(ovDescriptionEditor, enabled);

			setVisibleManaged(ovIncidentDateValue, !enabled);
			setVisibleManaged(ovIncidentDateEditor, enabled);
			setVisibleManaged(ovSolDateValue, !enabled);
			setVisibleManaged(ovSolDateEditor, enabled);

			setVisibleManaged(editButton, !enabled);
			setVisibleManaged(saveButton, enabled);
			setVisibleManaged(cancelButton, enabled);

			if (!enabled)
				hideRemoteUpdateBanner();

			setVisibleManaged(changeResponsibleAttorneyButton, enabled);
			setVisibleManaged(changeStatusButton, enabled);
			setVisibleManaged(changeCallerButton, enabled);
			setVisibleManaged(changeClientButton, enabled);
			setVisibleManaged(changePracticeAreaButton, enabled);
			setVisibleManaged(changeOpposingCounselButton, enabled);
			setVisibleManaged(btnEditTeam, enabled);
			refreshDeleteAction();
		}

		void clearDraftState() {
			draft = null;
			draftPrimaryStatusId = null;
			draftPrimaryCallerContactId = null;
			draftPrimaryCallerName = null;
			draftClientContacts = null;
			draftPracticeAreaId = null;
			draftPracticeAreaName = null;
			draftPracticeAreaColor = null;
			draftResponsibleAttorneyUserId = null;
			draftPrimaryOpposingCounselContactId = null;
			draftPrimaryOpposingCounselName = null;
			draftIncidentDate = null;
			draftSolDate = null;
			draftTeamAssignments = null;
		}

		private void snapshotDraftState() {
			draftPrimaryStatusId = (currentOverview == null ? null : currentOverview.getPrimaryStatusId());
			draftPrimaryCallerContactId = (currentOverview == null ? null : currentOverview.getPrimaryCallerContactId());
			draftPrimaryCallerName = (currentOverview == null ? null : currentOverview.getCaller());
			draftClientContacts = (currentOverview == null || currentOverview.getClients() == null)
					? List.of()
					: List.copyOf(currentOverview.getClients());
			draftPracticeAreaId = (currentOverview == null ? null : currentOverview.getPracticeAreaId());
			draftPracticeAreaName = (currentOverview == null ? null : currentOverview.getPracticeArea());
			draftPracticeAreaColor = (currentOverview == null ? null : currentOverview.getPracticeAreaColor());
			draftPrimaryOpposingCounselContactId = (currentOverview == null ? null : currentOverview.getPrimaryOpposingCounselContactId());
			draftPrimaryOpposingCounselName = (currentOverview == null ? null : currentOverview.getOpposingCounsel());
			draftIncidentDate = (currentOverview == null ? null : currentOverview.getIncidentDate());
			draftSolDate = (currentOverview == null ? null : currentOverview.getSolDate());
			if (ovIncidentDateEditor != null)
				ovIncidentDateEditor.setValue(draftIncidentDate);
			if (ovSolDateEditor != null)
				ovSolDateEditor.setValue(draftSolDate);
		}

		private boolean ensureCurrentDetailReady() {
			if (current != null)
				return true;
			showError("Case is still loading. Please try again.");
			return false;
		}

		private void applyDraftStateToEditors() {
			draft = new CaseEditModel(current.getCaseName(), current.getCaseNumber(), current.getDescription());
			if (ovCaseNameEditor != null)
				ovCaseNameEditor.setText(draft.caseName());
			if (ovCaseNumberEditor != null)
				ovCaseNumberEditor.setText(draft.caseNumber());
			if (ovDescriptionEditor != null)
				ovDescriptionEditor.setText(draft.description());
		}

		private void rerenderOverviewForDraft() {
			if (currentOverview != null)
				applyOverviewEditSafe(currentOverview);
		}

		private void exitEditMode() {
			setEditMode(false);
		}

		private void restoreViewMode() {
			if (currentOverview != null)
				applyOverviewEditSafe(currentOverview);
			applyDetail(current);
		}

		private void handleRemoteReloadDuringEdit() {
			clearDraftState();
			exitEditMode();
			hideRemoteUpdateBanner();
			clearError();
			reloadCurrentCaseForViewMode();
		}
	}

	private final class CaseOverviewSaveCoordinator {
		void save() {
			SaveRequest request = validatePreconditionsAndCapture();
			if (request == null)
				return;

			setBusy(true);
			clearError();

			new Thread(() -> runSaveWorker(request), "case-save-" + caseId).start();
		}

		private SaveRequest validatePreconditionsAndCapture() {
			if (caseDao == null) {
				showError("Case service is unavailable.");
				return null;
			}
			if (caseId == null) {
				showError("No case is selected.");
				return null;
			}
			if (current == null) {
				showError("Case is still loading. Please try again.");
				return null;
			}
			if (ovCaseNameEditor == null || ovDescriptionEditor == null || ovCaseNumberEditor == null) {
				showError("Edit fields are not available.");
				return null;
			}

			String name = safeText(ovCaseNameEditor.getText()).trim();
			String number = safeText(ovCaseNumberEditor.getText()).trim();
			String description = safeText(ovDescriptionEditor.getText());

			if (name.isEmpty()) {
				showError("Case Name is required.");
				return null;
			}

			draft = new CaseEditModel(name, number, description);
			CaseEditModel saveDraft = draft;

			SaveBaseline baseline = new SaveBaseline(
					safeText(current.getCaseName()).trim(),
					safeText(current.getDescription()),
					safeText(current.getCaseNumber()).trim(),
					currentOverview,
					current.getRowVer()
			);

			SaveDesiredValues desiredValues = captureRequestedValues();

			return new SaveRequest(
					caseId.longValue(),
					saveDraft,
					baseline,
					desiredValues,
					(appState == null ? null : appState.getShaleClientId()),
					(appState == null ? null : appState.getUserId())
			);
		}

		private SaveDesiredValues captureRequestedValues() {
			return new SaveDesiredValues(
					draftPrimaryStatusId,
					draftPrimaryCallerContactId,
					draftPrimaryCallerName,
					(draftClientContacts == null) ? null : List.copyOf(draftClientContacts),
					draftPracticeAreaId,
					draftResponsibleAttorneyUserId,
					draftPrimaryOpposingCounselContactId,
					draftPrimaryOpposingCounselName,
					(ovIncidentDateEditor == null ? null : ovIncidentDateEditor.getValue()),
					(ovSolDateEditor == null ? null : ovSolDateEditor.getValue()),
					(draftTeamAssignments == null) ? null : List.copyOf(draftTeamAssignments)
			);
		}

		private void runSaveWorker(SaveRequest request) {
			try {
				SaveComputation computation = computeChangeSet(request);
				CaseDetailDto updated = persistBaseCaseFields(request);
				if (updated == null) {
					handleConcurrentUpdate();
					return;
				}

				persistRelationshipChanges(request, computation);
				updated = populateLifecycleDateForSavedStatusIfMissing(
						request.saveCaseId(),
						resolveSavedPrimaryStatusId(request),
						request.tenantId(),
						updated);
				boolean teamChanged = persistTeamChanges(request);
				if (computation.statusChanged()) {
					CaseOverviewDto baseOverview = request.baseline().baseOverview();
					addStatusChangedTimelineEvent(
							request.saveCaseId(),
							request.tenantId(),
							request.userId(),
							baseOverview == null ? null : baseOverview.getPrimaryStatusId(),
							baseOverview == null ? null : baseOverview.getCaseStatus(),
							request.desired().desiredStatusId(),
							null
					);
				}
				if (computation.attyChanged()) {
					CaseOverviewDto baseOverview = request.baseline().baseOverview();
					addResponsibleAttorneyChangedTimelineEvent(
							request.saveCaseId(),
							request.tenantId(),
							request.userId(),
							baseOverview == null ? null : baseOverview.getResponsibleAttorneyUserId(),
							baseOverview == null ? null : baseOverview.getResponsibleAttorney(),
							request.desired().desiredResponsibleAttorneyUserId(),
							null
					);
				}
				CaseOverviewDto baseOverview = request.baseline().baseOverview();
				if (computation.callerChanged()) {
					addPrimaryContactChangedTimelineEvent(
							request.saveCaseId(),
							request.tenantId(),
							request.userId(),
							CaseDao.CaseTimelineEventTypes.CALLER_CHANGED,
							"Caller changed",
							baseOverview == null ? null : baseOverview.getPrimaryCallerContactId(),
							baseOverview == null ? null : baseOverview.getCaller(),
							request.desired().desiredCallerContactId(),
							request.desired().desiredCallerContactName()
					);
				}
				if (computation.clientChanged()) {
					addClientsChangedTimelineEvent(
							request.saveCaseId(),
							request.tenantId(),
							request.userId(),
							baseOverview == null ? List.of() : baseOverview.getClients(),
							request.desired().desiredClientContacts()
					);
				}
				if (computation.opposingCounselChanged()) {
					addPrimaryContactChangedTimelineEvent(
							request.saveCaseId(),
							request.tenantId(),
							request.userId(),
							CaseDao.CaseTimelineEventTypes.OPPOSING_COUNSEL_CHANGED,
							"Opposing counsel changed",
							baseOverview == null ? null : baseOverview.getPrimaryOpposingCounselContactId(),
							baseOverview == null ? null : baseOverview.getOpposingCounsel(),
							request.desired().desiredOpposingCounselContactId(),
							request.desired().desiredOpposingCounselContactName()
					);
				}
				if (computation.incidentChanged()) {
					addDateChangedTimelineEvent(
							request.saveCaseId(),
							request.tenantId(),
							request.userId(),
							CaseDao.CaseTimelineEventTypes.INCIDENT_DATE_CHANGED,
							"Incident date changed",
							baseOverview == null ? null : baseOverview.getIncidentDate(),
							request.desired().desiredIncidentDate()
					);
				}
				if (computation.solChanged()) {
					addDateChangedTimelineEvent(
							request.saveCaseId(),
							request.tenantId(),
							request.userId(),
							CaseDao.CaseTimelineEventTypes.SOL_DATE_CHANGED,
							"SOL date changed",
							baseOverview == null ? null : baseOverview.getSolDate(),
							request.desired().desiredSolDate()
					);
				}
				if (computation.practiceAreaChanged()) {
					addPracticeAreaChangedTimelineEvent(
							request.saveCaseId(),
							request.tenantId(),
							request.userId(),
							baseOverview == null ? null : baseOverview.getPracticeAreaId(),
							baseOverview == null ? null : baseOverview.getPracticeArea(),
							request.desired().desiredPracticeAreaId(),
							draftPracticeAreaName
					);
				}
				addTextIdentityChangedTimelineEvent(
						request.saveCaseId(),
						request.tenantId(),
						request.userId(),
						CaseDao.CaseTimelineEventTypes.CASE_NAME_CHANGED,
						"Case name changed",
						request.baseline().oldName(),
						request.saveDraft().caseName()
				);
				addTextIdentityChangedTimelineEvent(
						request.saveCaseId(),
						request.tenantId(),
						request.userId(),
						CaseDao.CaseTimelineEventTypes.CASE_NUMBER_CHANGED,
						"Case number changed",
						request.baseline().oldNumber(),
						request.saveDraft().caseNumber()
				);
				addDescriptionChangedTimelineEvent(
						request.saveCaseId(),
						request.tenantId(),
						request.userId(),
						request.baseline().oldDescription(),
						request.saveDraft().description()
				);
				if (teamChanged) {
					addTeamChangedTimelineEvent(
							request.saveCaseId(),
							request.tenantId(),
							request.userId()
					);
				}

				CaseDetailDto updatedForUi = updated;

				runOnFx(() -> finalizeSuccessfulSave(request, updatedForUi, computation, teamChanged));
			} catch (Exception ex) {
				runOnFx(() ->
				{
					showError("Failed to save case. " + ex.getMessage());
					setBusy(false);
				});
			}
		}

		private Integer resolveSavedPrimaryStatusId(SaveRequest request) {
			if (request.desired().desiredStatusId() != null)
				return request.desired().desiredStatusId();
			CaseOverviewDto baseOverview = request.baseline().baseOverview();
			return baseOverview == null ? null : baseOverview.getPrimaryStatusId();
		}

		private SaveComputation computeChangeSet(SaveRequest request) {
			CaseOverviewDto baseOverview = request.baseline().baseOverview();
			SaveDesiredValues desired = request.desired();

			LocalDate baseIncidentDate = baseOverview == null ? null : baseOverview.getIncidentDate();
			LocalDate baseSolDate = baseOverview == null ? null : baseOverview.getSolDate();

			boolean incidentChanged = !Objects.equals(desired.desiredIncidentDate(), baseIncidentDate);
			boolean solChanged = !Objects.equals(desired.desiredSolDate(), baseSolDate);

			Integer baseStatusId = baseOverview == null ? null : baseOverview.getPrimaryStatusId();
			boolean statusChanged = desired.desiredStatusId() != null && !desired.desiredStatusId().equals(baseStatusId);

			Integer baseCallerContactId = baseOverview == null ? null : baseOverview.getPrimaryCallerContactId();
			boolean callerChanged = desired.desiredCallerContactId() != null && !desired.desiredCallerContactId().equals(baseCallerContactId);

			java.util.Set<Integer> baseClientIds = toClientIdSet(baseOverview == null ? null : baseOverview.getClients());
			java.util.Set<Integer> desiredClientIds = toClientIdSet(desired.desiredClientContacts());
			boolean clientChanged = !baseClientIds.equals(desiredClientIds);

			Integer basePracticeAreaId = baseOverview == null ? null : baseOverview.getPracticeAreaId();
			boolean practiceAreaChanged = desired.desiredPracticeAreaId() != null && !desired.desiredPracticeAreaId().equals(basePracticeAreaId);

			Integer baseAttyId = baseOverview == null ? null : baseOverview.getResponsibleAttorneyUserId();
			boolean attyChanged = desired.desiredResponsibleAttorneyUserId() != null
					&& !desired.desiredResponsibleAttorneyUserId().equals(baseAttyId);

			Integer baseOpposingCounselContactId = baseOverview == null ? null : baseOverview.getPrimaryOpposingCounselContactId();
			boolean opposingCounselChanged = desired.desiredOpposingCounselContactId() != null
					&& !desired.desiredOpposingCounselContactId().equals(baseOpposingCounselContactId);

			return new SaveComputation(incidentChanged, solChanged, statusChanged, callerChanged, clientChanged,
					practiceAreaChanged, attyChanged, opposingCounselChanged);
		}

		private CaseDetailDto persistBaseCaseFields(SaveRequest request) {
			return caseDao.updateCase(
					request.saveCaseId(),
					request.saveDraft().caseName(),
					request.saveDraft().caseNumber(),
					request.saveDraft().description(),
					request.desired().desiredIncidentDate(),
					request.desired().desiredSolDate(),
					request.baseline().expectedRowVer()
			);
		}

		private void persistRelationshipChanges(SaveRequest request, SaveComputation computation) {
			if (computation.statusChanged())
				caseDao.setPrimaryStatus(request.saveCaseId(), request.desired().desiredStatusId(), null);

			if (computation.callerChanged()) {
				requireTenant(request.tenantId());
				caseDao.setPrimaryCasePartyCaller(
						request.saveCaseId(), request.tenantId(), request.desired().desiredCallerContactId(), request.userId(), null
				);
			}

			if (computation.clientChanged()) {
				requireTenant(request.tenantId());
				List<Integer> desiredClientIds = request.desired().desiredClientContacts() == null ? List.of()
						: request.desired().desiredClientContacts().stream()
						.map(CaseOverviewDto.ContactSummary::contactId)
						.filter(Objects::nonNull)
						.distinct()
						.toList();
				caseDao.syncRepresentedPartyContacts(
						request.saveCaseId(), request.tenantId(), desiredClientIds, null
				);
			}

			if (computation.practiceAreaChanged()) {
				requireTenant(request.tenantId());
				caseDao.setPracticeArea(request.saveCaseId(), request.tenantId(), request.desired().desiredPracticeAreaId());
			}

			if (computation.attyChanged())
				caseDao.setResponsibleAttorney(request.saveCaseId(), request.desired().desiredResponsibleAttorneyUserId());

			if (computation.opposingCounselChanged()) {
				requireTenant(request.tenantId());
				caseDao.setPrimaryCasePartyOpposingCounsel(
						request.saveCaseId(), request.tenantId(),
						request.desired().desiredOpposingCounselContactId(), request.userId(), null
				);
			}
		}

		private boolean persistTeamChanges(SaveRequest request) {
			if (request.desired().desiredTeamAssignments() == null)
				return false;

			java.util.Set<String> beforeTeam = normalizeTeamRoleRows(caseDao.listCaseUserRoles(request.saveCaseId()));
			java.util.Set<String> desiredTeam = normalizeTeamAssignments(request.desired().desiredTeamAssignments());
			boolean teamChanged = !beforeTeam.equals(desiredTeam);
			if (teamChanged)
				caseDao.replaceCaseTeamAssignments(request.saveCaseId(), request.desired().desiredTeamAssignments());
			return teamChanged;
		}

		private void finalizeSuccessfulSave(
				SaveRequest request,
				CaseDetailDto updated,
				SaveComputation computation,
				boolean teamChanged) {

			current = updated;

			setEditMode(false);
			draft = null;

			hideRemoteUpdateBanner();

			applyDetail(updated);
			clearError();
			setBusy(false);

			publishFieldUpdates(request, computation, teamChanged);

			clearDraftState();
			reloadCurrentCaseForViewMode();
		}

		private void publishFieldUpdates(
				SaveRequest request,
				SaveComputation computation,
				boolean teamChanged) {

			String newName = safeText(request.saveDraft().caseName()).trim();
			String newDesc = safeText(request.saveDraft().description());
			String newNum = safeText(request.saveDraft().caseNumber()).trim();

			if (!newName.equals(request.baseline().oldName()))
				publishCaseFieldUpdated(request.saveCaseId(), "name", newName);
			if (!newNum.equals(request.baseline().oldNumber()))
				publishCaseFieldUpdated(request.saveCaseId(), "caseNumber", newNum);
			if (!newDesc.equals(request.baseline().oldDescription()))
				publishCaseFieldUpdated(request.saveCaseId(), "description", newDesc);
			if (computation.incidentChanged())
				publishCaseFieldUpdated(request.saveCaseId(), "incidentDate",
						request.desired().desiredIncidentDate() == null ? null : request.desired().desiredIncidentDate().toString());
			if (computation.solChanged())
				publishCaseFieldUpdated(request.saveCaseId(), "solDate",
						request.desired().desiredSolDate() == null ? null : request.desired().desiredSolDate().toString());

			if (computation.statusChanged())
				publishCaseFieldUpdated(request.saveCaseId(), "primaryStatusId", request.desired().desiredStatusId());
			if (computation.callerChanged())
				publishCaseFieldUpdated(request.saveCaseId(), "primaryCallerContactId", request.desired().desiredCallerContactId());
			if (computation.clientChanged())
				publishCaseFieldUpdated(request.saveCaseId(), "clientContactsChanged", 1);
			if (computation.practiceAreaChanged())
				publishCaseFieldUpdated(request.saveCaseId(), "practiceAreaId", request.desired().desiredPracticeAreaId());
			if (computation.attyChanged())
				publishCaseFieldUpdated(request.saveCaseId(), "responsibleAttorneyUserId", request.desired().desiredResponsibleAttorneyUserId());
			if (computation.opposingCounselChanged())
				publishCaseFieldUpdated(request.saveCaseId(), "primaryOpposingCounselContactId", request.desired().desiredOpposingCounselContactId());

			if (teamChanged)
				publishCaseFieldUpdated(request.saveCaseId(), "teamChanged", 1);
		}


		private static java.util.Set<String> normalizeTeamRoleRows(List<CaseDao.CaseUserRoleRow> rows) {
			java.util.Set<String> out = new java.util.HashSet<>();
			if (rows == null)
				return out;
			for (CaseDao.CaseUserRoleRow r : rows) {
				if (r == null)
					continue;
				out.add(r.userId() + ":" + r.roleId());
			}
			return out;
		}

		private static java.util.Set<String> normalizeTeamAssignments(List<CaseDao.TeamAssignmentRow> rows) {
			java.util.Set<String> out = new java.util.HashSet<>();
			if (rows == null)
				return out;
			for (CaseDao.TeamAssignmentRow r : rows) {
				if (r == null)
					continue;
				out.add(r.userId() + ":" + r.roleId());
			}
			return out;
		}

		private void handleConcurrentUpdate() {
			runOnFx(() ->
			{
				showRemoteUpdateBanner();
				showError("This case was updated elsewhere. Reload and try again.");
				setBusy(false);
			});
		}

		private void requireTenant(Integer tenantId) {
			if (tenantId == null || tenantId <= 0)
				throw new RuntimeException("No tenant is selected.");
		}
	}

	private record SaveRequest(
			long saveCaseId,
			CaseEditModel saveDraft,
			SaveBaseline baseline,
			SaveDesiredValues desired,
			Integer tenantId,
			Integer userId) {
	}

	private record SaveBaseline(
			String oldName,
			String oldDescription,
			String oldNumber,
			CaseOverviewDto baseOverview,
			byte[] expectedRowVer) {
	}

	private record SaveDesiredValues(
			Integer desiredStatusId,
			Integer desiredCallerContactId,
			String desiredCallerContactName,
			List<CaseOverviewDto.ContactSummary> desiredClientContacts,
			Integer desiredPracticeAreaId,
			Integer desiredResponsibleAttorneyUserId,
			Integer desiredOpposingCounselContactId,
			String desiredOpposingCounselContactName,
			LocalDate desiredIncidentDate,
			LocalDate desiredSolDate,
			List<CaseDao.TeamAssignmentRow> desiredTeamAssignments) {
	}

	private record SaveComputation(
			boolean incidentChanged,
			boolean solChanged,
			boolean statusChanged,
			boolean callerChanged,
			boolean clientChanged,
			boolean practiceAreaChanged,
			boolean attyChanged,
			boolean opposingCounselChanged) {
	}


	private final class CaseOverviewPickerCoordinator {
		void changeResponsibleAttorney() {
			if (!requirePickerContext("Responsible attorney change is unavailable."))
				return;
			Integer tenantId = appState.getShaleClientId();
			if (!requireTenantSelected(tenantId))
				return;

			setBusy(true);
			clearError();

			final long activeCaseId = caseId.longValue();
			new Thread(() ->
			{
				try {
					List<CaseDao.UserRow> users = caseDao.listAttorneysForTenant(tenantId);
					java.util.Set<Integer> attorneyIds = caseDao.listAttorneyUserIdsForTenant(tenantId);
					List<CaseDao.UserRow> attorneyUsers = (users == null ? List.<CaseDao.UserRow>of() : users).stream()
							.filter(java.util.Objects::nonNull)
							.filter(u -> attorneyIds.contains(u.id()))
							.toList();
					runOnFx(() -> handleResponsibleAttorneyLoaded(attorneyUsers));
				} catch (Exception ex) {
					runOnFx(() ->
					{
						showError("Failed to load attorneys. " + ex.getMessage());
						setBusy(false);
					});
				}
			}, "case-atty-list-" + activeCaseId).start();
		}

		void changePrimaryStatus() {
			changePrimaryStatusInternal(false);
		}

		void changePrimaryStatusForDetails() {
			changePrimaryStatusInternal(true);
		}

		private void changePrimaryStatusInternal(boolean detailsMode) {
			if (!requirePickerContext("Status change is unavailable."))
				return;
			Integer tenantId = appState.getShaleClientId();
			if (!requireTenantSelected(tenantId))
				return;

			setBusy(true);
			clearError();

			new Thread(() ->
			{
				try {
					List<CaseDao.StatusRow> statuses = caseDao.listStatusesForTenant(tenantId);
					runOnFx(() -> handleStatusLoaded(statuses, detailsMode));
				} catch (Exception ex) {
					runOnFx(() ->
					{
						showError("Failed to load statuses. " + ex.getMessage());
						setBusy(false);
					});
				}
			}, "case-status-list-" + caseId).start();
		}

		void changeCaller() {
			changeContact(
					"Caller change is unavailable.",
					"Change Caller",
					"Select the primary caller",
					"case-caller-list-",
					() -> (editMode && draftPrimaryCallerContactId != null)
							? draftPrimaryCallerContactId
							: (currentOverview == null ? null : currentOverview.getPrimaryCallerContactId()),
					picked -> {
						draftPrimaryCallerContactId = picked.id();
						draftPrimaryCallerName = picked.displayName();
						renderCallerMini(draftPrimaryCallerContactId, draftPrimaryCallerName);
					});
		}

		void manageClients() {
			if (!requirePickerContext("Client change is unavailable."))
				return;
			Integer tenantId = appState.getShaleClientId();
			if (!requireTenantSelected(tenantId))
				return;
			setBusy(true);
			clearError();
			new Thread(() ->
			{
				try {
					List<CaseDao.ContactRow> contacts = caseDao.listContactsForTenant(tenantId);
					runOnFx(() -> handleClientsLoaded(contacts));
				} catch (Exception ex) {
					runOnFx(() ->
					{
						showError("Failed to load contacts. " + ex.getMessage());
						setBusy(false);
					});
				}
			}, "case-client-list-" + caseId).start();
		}

		void changePracticeArea() {
			changePracticeAreaInternal(false);
		}

		void changePracticeAreaForDetails() {
			changePracticeAreaInternal(true);
		}

		private void changePracticeAreaInternal(boolean detailsMode) {
			if (!requirePickerContext("Practice area change is unavailable."))
				return;
			Integer tenantId = appState.getShaleClientId();
			if (!requireTenantSelected(tenantId))
				return;

			setBusy(true);
			clearError();

			new Thread(() ->
			{
				try {
					List<CaseDao.PracticeAreaRow> areas = caseDao.listPracticeAreasForTenant(tenantId);
					runOnFx(() -> handlePracticeAreaLoaded(areas, detailsMode));
				} catch (Exception ex) {
					runOnFx(() ->
					{
						showError("Failed to load practice areas. " + ex.getMessage());
						setBusy(false);
					});
				}
			}, "case-practicearea-list-" + caseId).start();
		}

		void changeOpposingCounsel() {
			changeContact(
					"Opposing counsel change is unavailable.",
					"Change Opposing Counsel",
					"Select the primary opposing counsel",
					"case-oppcounsel-list-",
					() -> (editMode && draftPrimaryOpposingCounselContactId != null)
							? draftPrimaryOpposingCounselContactId
							: (currentOverview == null ? null : currentOverview.getPrimaryOpposingCounselContactId()),
					picked -> {
						draftPrimaryOpposingCounselContactId = picked.id();
						draftPrimaryOpposingCounselName = picked.displayName();
						renderOpposingCounselMini(draftPrimaryOpposingCounselContactId, draftPrimaryOpposingCounselName);
					});
		}

		private boolean requirePickerContext(String unavailableMessage) {
			if (caseDao == null || appState == null || caseId == null) {
				showError(unavailableMessage);
				return false;
			}
			return true;
		}

		private boolean requireTenantSelected(Integer tenantId) {
			if (tenantId == null || tenantId <= 0) {
				showError("No tenant is selected.");
				return false;
			}
			return true;
		}

		private void handleResponsibleAttorneyLoaded(List<CaseDao.UserRow> users) {
			setBusy(false);
			if (users == null || users.isEmpty()) {
				showError("No attorneys are configured for this tenant.");
				return;
			}

			Map<String, CaseDao.UserRow> labelToRow = new LinkedHashMap<>();
			Integer currentId = (editMode && draftResponsibleAttorneyUserId != null)
					? draftResponsibleAttorneyUserId
					: (currentOverview == null ? null : currentOverview.getResponsibleAttorneyUserId());
			String preselect = null;
			for (CaseDao.UserRow u : users) {
				String label = u.displayName();
				if (label == null || label.isBlank())
					continue;
				String key = label;
				if (labelToRow.containsKey(key))
					key = label + " (ID " + u.id() + ")";
				labelToRow.put(key, u);
				if (currentId != null && currentId.equals(u.id()))
					preselect = key;
			}
			if (labelToRow.isEmpty()) {
				showError("No attorneys are configured for this tenant.");
				return;
			}
			if (preselect == null)
				preselect = labelToRow.keySet().iterator().next();

			Optional<String> chosen = showChoiceDialog(
					"Change Responsible Attorney",
					"Select the responsible attorney",
					"Attorney:",
					preselect,
					labelToRow.keySet());
			if (chosen.isEmpty())
				return;
			CaseDao.UserRow picked = labelToRow.get(chosen.get());
			if (picked == null)
				return;

			draftResponsibleAttorneyUserId = picked.id();
			renderResponsibleAttorneyMini(picked.id(), picked.displayName(), picked.color());
		}

		private void handleStatusLoaded(List<CaseDao.StatusRow> statuses, boolean detailsMode) {
			setBusy(false);
			if (statuses == null || statuses.isEmpty()) {
				showError("No statuses are configured for this tenant.");
				return;
			}

			Map<String, CaseDao.StatusRow> labelToRow = new LinkedHashMap<>();
			String preselect = null;
			Integer currentId = detailsMode
					? ((detailsEditMode && detailsDraft != null && detailsDraft.primaryStatusId != null)
							? detailsDraft.primaryStatusId
							: (currentOverview == null ? null : currentOverview.getPrimaryStatusId()))
					: ((editMode && draftPrimaryStatusId != null)
							? draftPrimaryStatusId
							: (currentOverview == null ? null : currentOverview.getPrimaryStatusId()));
			for (CaseDao.StatusRow s : statuses) {
				String label = s.name() + (CaseDao.isTerminalStatus(s) ? " (Terminal)" : "");
				labelToRow.put(label, s);
				if (currentId != null && currentId == s.id())
					preselect = label;
			}
			if (preselect == null)
				preselect = labelToRow.keySet().iterator().next();

			Optional<String> chosen = showChoiceDialog(
					"Change Status",
					"Select the new primary status",
					"Status:",
					preselect,
					labelToRow.keySet());
			if (chosen.isEmpty())
				return;
			CaseDao.StatusRow picked = labelToRow.get(chosen.get());
			if (picked == null)
				return;

			if (detailsMode && detailsEditMode && detailsDraft != null) {
				detailsDraft.primaryStatusId = picked.id();
				detailsDraft.primaryStatusName = picked.name();
				detailsDraft.primaryStatusColor = picked.color();
				renderDetailsStatusMini(picked.id(), picked.name(), picked.color());
			} else {
				draftPrimaryStatusId = picked.id();
				renderPrimaryStatusMini(picked.id(), picked.name(), picked.color());
			}
		}

		private void handlePracticeAreaLoaded(List<CaseDao.PracticeAreaRow> areas, boolean detailsMode) {
			setBusy(false);
			if (areas == null || areas.isEmpty()) {
				showError("No practice areas are configured for this tenant.");
				return;
			}

			Map<String, CaseDao.PracticeAreaRow> labelToRow = new LinkedHashMap<>();
			String preselect = null;
			Integer currentId = detailsMode
					? ((detailsEditMode && detailsDraft != null && detailsDraft.practiceAreaId != null)
							? detailsDraft.practiceAreaId
							: (currentOverview == null ? null : currentOverview.getPracticeAreaId()))
					: ((editMode && draftPracticeAreaId != null)
							? draftPracticeAreaId
							: (currentOverview == null ? null : currentOverview.getPracticeAreaId()));
			for (CaseDao.PracticeAreaRow pa : areas) {
				String label = (pa.name() == null || pa.name().isBlank()) ? ("PracticeArea #" + pa.id()) : pa.name();
				labelToRow.put(label, pa);
				if (currentId != null && currentId == pa.id())
					preselect = label;
			}
			if (preselect == null)
				preselect = labelToRow.keySet().iterator().next();

			Optional<String> chosen = showChoiceDialog(
					"Change Practice Area",
					"Select the practice area",
					"Practice area:",
					preselect,
					labelToRow.keySet());
			if (chosen.isEmpty())
				return;
			CaseDao.PracticeAreaRow picked = labelToRow.get(chosen.get());
			if (picked == null)
				return;

			if (detailsMode && detailsEditMode && detailsDraft != null) {
				detailsDraft.practiceAreaId = picked.id();
				detailsDraft.practiceAreaName = picked.name();
				detailsDraft.practiceAreaColor = picked.color();
				renderDetailsPracticeAreaMini(picked.id(), picked.name(), picked.color());
			} else {
				draftPracticeAreaId = picked.id();
				draftPracticeAreaName = picked.name();
				draftPracticeAreaColor = picked.color();
				renderPracticeAreaMini(picked.id(), picked.name(), picked.color());
			}
		}

		private void changeContact(
				String unavailableMessage,
				String dialogTitle,
				String dialogHeader,
				String threadPrefix,
				java.util.function.Supplier<Integer> currentIdSupplier,
				java.util.function.Consumer<CaseDao.ContactRow> applySelection) {
			if (!requirePickerContext(unavailableMessage))
				return;
			Integer tenantId = appState.getShaleClientId();
			if (!requireTenantSelected(tenantId))
				return;

			setBusy(true);
			clearError();

			new Thread(() ->
			{
				try {
					List<CaseDao.ContactRow> contacts = caseDao.listContactsForTenant(tenantId);
					runOnFx(() -> handleContactLoaded(contacts, dialogTitle, dialogHeader, currentIdSupplier, applySelection));
				} catch (Exception ex) {
					runOnFx(() ->
					{
						showError("Failed to load contacts. " + ex.getMessage());
						setBusy(false);
					});
				}
			}, threadPrefix + caseId).start();
		}

		private void handleContactLoaded(
				List<CaseDao.ContactRow> contacts,
				String dialogTitle,
				String dialogHeader,
				java.util.function.Supplier<Integer> currentIdSupplier,
				java.util.function.Consumer<CaseDao.ContactRow> applySelection) {
			setBusy(false);
			if (contacts == null || contacts.isEmpty()) {
				showError("No contacts are configured for this tenant.");
				return;
			}

			List<CaseDao.ContactRow> cleaned = contacts.stream()
					.filter(c -> c != null && c.displayName() != null && !c.displayName().isBlank())
					.toList();
			if (cleaned.isEmpty()) {
				showError("No usable contacts found (all were blank).");
				return;
			}

			CaseDao.ContactRow preselectRow = findContactById(cleaned, currentIdSupplier.get());
			Optional<CaseDao.ContactRow> chosen = showSearchPickerDialog(
					dialogTitle,
					dialogHeader,
					"Search...",
					cleaned,
					preselectRow);
			if (chosen.isEmpty())
				return;
			applySelection.accept(chosen.get());
		}

		private CaseDao.ContactRow findContactById(List<CaseDao.ContactRow> contacts, Integer contactId) {
			if (contactId == null)
				return null;
			for (CaseDao.ContactRow c : contacts) {
				if (c.id() == contactId.intValue())
					return c;
			}
			return null;
		}

		private void handleClientsLoaded(List<CaseDao.ContactRow> contacts) {
			setBusy(false);
			if (contacts == null || contacts.isEmpty()) {
				showError("No contacts are configured for this tenant.");
				return;
			}
			List<CaseDao.ContactRow> cleaned = contacts.stream()
					.filter(c -> c != null && c.displayName() != null && !c.displayName().isBlank())
					.toList();
			if (cleaned.isEmpty()) {
				showError("No usable contacts found (all were blank).");
				return;
			}

			List<CaseOverviewDto.ContactSummary> initial = draftClientContacts != null
					? draftClientContacts
					: (currentOverview == null ? List.of() : currentOverview.getClients());
			if (initial == null) {
				initial = List.of();
			}
			List<CaseOverviewDto.ContactSummary> contactOnlyInitial = initial.stream()
					.filter(Objects::nonNull)
					.filter(client -> client.contactId() != null && client.contactId() > 0)
					.toList();
			Window owner = dialogOwner(changeClientButton);
			ClientAssignmentDialog dialog = new ClientAssignmentDialog(
					owner,
					cleaned,
					contactOnlyInitial,
					(firstName, lastName) -> {
						if (contactDao == null || appState == null || appState.getShaleClientId() == null || appState.getShaleClientId() <= 0)
							throw new IllegalStateException("Cannot create contact without an active tenant.");
						int createdId = contactDao.createContact(new ContactDao.CreateContactRequest(
								appState.getShaleClientId(),
								firstName,
								lastName,
								null,
								null,
								true));
						String displayName = ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).trim();
						if (displayName.isBlank())
							displayName = "Contact #" + createdId;
						return new CaseDao.ContactRow(createdId, displayName);
					});
			Optional<ClientAssignmentDialog.Result> result = dialog.showAndWait();
			if (result.isEmpty())
				return;
			draftClientContacts = result.get().assignedClients();
			renderClientsMini(draftClientContacts);
		}

		private Optional<String> showChoiceDialog(
				String title,
				String header,
				String content,
				String preselect,
				java.util.Collection<String> options) {
			ChoiceDialog<String> dialog = new ChoiceDialog<>(preselect, options);
			AppDialogs.applySecondaryWindowChrome(dialog);
			dialog.setTitle(title);
			dialog.setHeaderText(header);
			dialog.setContentText(content);
			return dialog.showAndWait();
		}
	}

	private final class CaseOverviewLiveUpdateHandler {
		void subscribe() {
			if (runtimeBridge == null)
				return;

			runtimeBridge.subscribeCaseUpdated(this::handleEvent);
		}

		private void handleEvent(UiRuntimeBridge.CaseUpdatedEvent event) {
			if (shouldIgnoreEvent(event))
				return;

			if (handleLegacyNameEvent(event))
				return;

			LivePatchData patch = parsePatch(event.rawPatchJson());

			if (handleCaseUpdateAdded(patch))
				return;

			if (handleEditModeConflict(patch))
				return;

			if (shouldReloadForStructuralPatch(patch)) {
				handleStructuralReload(patch);
				return;
			}

			if (handleInlineSimplePatch(patch))
				return;

			handleUnknownRemoteChange();
		}

		private boolean shouldIgnoreEvent(UiRuntimeBridge.CaseUpdatedEvent event) {
			if (event == null || caseId == null)
				return true;
			if (event.caseId() != caseId.intValue())
				return true;
			return isOwnEcho(event);
		}

		private boolean isOwnEcho(UiRuntimeBridge.CaseUpdatedEvent event) {
			String mine = runtimeBridge.getClientInstanceId();
			return mine != null && !mine.isBlank() && mine.equals(event.clientInstanceId());
		}

		private boolean handleLegacyNameEvent(UiRuntimeBridge.CaseUpdatedEvent event) {
			if (event.newName() == null)
				return false;
			runOnFx(() ->
			{
				applyLiveCaseName(event.newName());
				hideRemoteUpdateBanner();
				refreshCurrentAfterRemoteUpdateAsync();
			});
			return true;
		}

		private LivePatchData parsePatch(String rawPatch) {
			String patchedName = extractPatchString(rawPatch, "name");
			String patchedNumber = extractPatchString(rawPatch, "caseNumber");
			String patchedDescription = extractPatchString(rawPatch, "description");
			boolean incidentDatePatched = hasPatchKey(rawPatch, "incidentDate");
			boolean solDatePatched = hasPatchKey(rawPatch, "solDate");
			String patchedIncident = extractPatchString(rawPatch, "incidentDate");
			String patchedSol = extractPatchString(rawPatch, "solDate");
			Integer patchedPrimaryStatusId = extractPatchInt(rawPatch, "primaryStatusId");
			Integer patchedPrimaryCallerContactId = extractPatchInt(rawPatch, "primaryCallerContactId");
			boolean clientAssignmentsPatched = hasPatchKey(rawPatch, "clientContactsChanged")
					|| hasPatchKey(rawPatch, "primaryClientContactId");
			Integer patchedPracticeAreaId = extractPatchInt(rawPatch, "practiceAreaId");
			Integer patchedResponsibleAttorneyUserId = extractPatchInt(rawPatch, "responsibleAttorneyUserId");
			Integer patchedPrimaryOpposingCounselContactId = extractPatchInt(rawPatch, "primaryOpposingCounselContactId");
			Integer patchedTeamChanged = extractPatchInt(rawPatch, "teamChanged");
			boolean teamChanged = patchedTeamChanged != null && patchedTeamChanged.intValue() == 1;
			Integer patchedCaseUpdateAdded = extractPatchInt(rawPatch, "caseUpdateAdded");
			boolean caseUpdateAdded = patchedCaseUpdateAdded != null && patchedCaseUpdateAdded.intValue() == 1;
			Integer patchedDeleted = extractPatchInt(rawPatch, "deleted");
			boolean deleted = patchedDeleted != null && patchedDeleted.intValue() == 1;
			boolean detailsTouched = hasDetailsFieldPatch(rawPatch);

			return new LivePatchData(
				rawPatch,
				patchedName,
				patchedNumber,
				patchedDescription,
				incidentDatePatched,
				solDatePatched,
				patchedIncident,
				patchedSol,
				patchedPrimaryStatusId,
				patchedPrimaryCallerContactId,
				clientAssignmentsPatched,
				patchedPracticeAreaId,
				patchedResponsibleAttorneyUserId,
				patchedPrimaryOpposingCounselContactId,
				teamChanged,
				caseUpdateAdded,
				deleted,
				detailsTouched
			);
		}

		private boolean hasDetailsFieldPatch(String rawPatch) {
			if (rawPatch == null || rawPatch.isBlank())
				return false;
			String[] keys = {
				"callerDate", "callerTime", "acceptedDate", "closedDate", "deniedDate",
				"dateOfMedicalNegligence", "dateMedicalNegligenceWasDiscovered", "dateOfInjury",
				"statuteOfLimitations", "tortNoticeDeadline", "discoveryDeadline",
				"clientEstate", "officePrinterCode", "medicalRecordsReceived", "feeAgreementSigned",
				"dateFeeAgreementSigned", "acceptedChronology", "acceptedConsultantExpertSearch",
				"acceptedTestifyingExpertSearch", "acceptedMedicalLiterature", "acceptedDetail",
				"deniedChronology", "deniedDetail", "summary", "receivedUpdates"
			};
			for (String key : keys) {
				if (hasPatchKey(rawPatch, key))
					return true;
			}
			return false;
		}

		private boolean handleCaseUpdateAdded(LivePatchData patch) {
			if (!patch.caseUpdateAdded())
				return false;
			runOnFx(() ->
			{
				loadCaseUpdatesAsync();
		loadCaseTasksAsync();
				refreshLastUpdatedLabelAsync();
			});
			return true;
		}

		private boolean handleEditModeConflict(LivePatchData patch) {
			if (editMode) {
				runOnFx(() ->
				{
					showRemoteUpdateBanner();
				});
				return true;
			}
			if (detailsEditMode && patch.detailsTouched()) {
				runOnFx(() -> showRemoteUpdateBanner());
				refreshDetailsBaselineAfterRemoteAsync();
				return true;
			}
			return false;
		}

		private boolean shouldReloadForStructuralPatch(LivePatchData patch) {
			return patch.deleted()
					|| patch.patchedPrimaryStatusId() != null
					|| patch.patchedPrimaryCallerContactId() != null
					|| patch.clientAssignmentsPatched()
					|| patch.patchedPracticeAreaId() != null
					|| patch.patchedResponsibleAttorneyUserId() != null
					|| patch.patchedPrimaryOpposingCounselContactId() != null
					|| patch.teamChanged()
					|| patch.detailsTouched();
		}

		private void handleStructuralReload(LivePatchData patch) {
			runOnFx(() ->
			{
				reloadCurrentCaseForViewMode();
				if (patch.teamChanged())
					loadTeamSectionAsync();
				hideRemoteUpdateBanner();
			});
		}

		private boolean hasInlineSimplePatch(LivePatchData patch) {
			return patch.patchedName() != null || patch.patchedNumber() != null || patch.patchedDescription() != null
					|| patch.incidentDatePatched() || patch.solDatePatched();
		}

		private boolean handleInlineSimplePatch(LivePatchData patch) {
			if (!hasInlineSimplePatch(patch))
				return false;
			runOnFx(() -> applyInlineSimplePatch(patch));
			return true;
		}

		private void applyInlineSimplePatch(LivePatchData patch) {
			if (patch.patchedName() != null)
				applyLiveCaseName(patch.patchedName());
			if (patch.patchedNumber() != null)
				applyLiveCaseNumber(patch.patchedNumber());
			if (patch.patchedDescription() != null)
				applyLiveCaseDescription(patch.patchedDescription());

			LocalDate nextIncidentDate = null;
			LocalDate nextSolDate = null;
			boolean incidentApplied = false;
			boolean solApplied = false;

			if (patch.incidentDatePatched()) {
				if (patch.patchedIncident() != null) {
					LocalDate parsed = parsePatchedDate(patch.patchedIncident());
					if (parsed != null) {
						nextIncidentDate = parsed;
						incidentApplied = true;
					}
				} else if (isPatchExplicitNull(patch.rawPatch(), "incidentDate")) {
					nextIncidentDate = null;
					incidentApplied = true;
				}
			}

			if (patch.solDatePatched()) {
				if (patch.patchedSol() != null) {
					LocalDate parsed = parsePatchedDate(patch.patchedSol());
					if (parsed != null) {
						nextSolDate = parsed;
						solApplied = true;
					}
				} else if (isPatchExplicitNull(patch.rawPatch(), "solDate")) {
					nextSolDate = null;
					solApplied = true;
				}
			}

			if (incidentApplied && ovIncidentDateValue != null)
				ovIncidentDateValue.setText(formatDate(nextIncidentDate));
			if (solApplied && ovSolDateValue != null)
				ovSolDateValue.setText(formatDate(nextSolDate));

			if (incidentApplied || solApplied) {
				CaseOverviewDto base = currentOverview;
				if (base != null) {
					LocalDate mergedIncident = incidentApplied ? nextIncidentDate : base.getIncidentDate();
					LocalDate mergedSol = solApplied ? nextSolDate : base.getSolDate();
					currentOverview = copyOverviewWithDates(base, mergedIncident, mergedSol);
				}
			}

			hideRemoteUpdateBanner();
			refreshCurrentAfterRemoteUpdateAsync();
		}

		private void handleUnknownRemoteChange() {
			runOnFx(() ->
			{
				showRemoteUpdateBanner();
			});
		}
	}

	private record LivePatchData(
			String rawPatch,
			String patchedName,
			String patchedNumber,
			String patchedDescription,
			boolean incidentDatePatched,
			boolean solDatePatched,
			String patchedIncident,
			String patchedSol,
			Integer patchedPrimaryStatusId,
			Integer patchedPrimaryCallerContactId,
			boolean clientAssignmentsPatched,
			Integer patchedPracticeAreaId,
			Integer patchedResponsibleAttorneyUserId,
			Integer patchedPrimaryOpposingCounselContactId,
			boolean teamChanged,
			boolean caseUpdateAdded,
			boolean deleted,
			boolean detailsTouched) {
	}

	private final class CaseTeamCoordinator {
		void loadTeamSectionAsync() {
			loadTeamSectionAsyncInternal();
		}

		void renderTeamCardsFromTeamRows(List<CaseDao.CaseUserTeamRow> rows) {
			renderTeamCardsFromTeamRowsInternal(rows);
		}

		void onEditTeam() {
			onEditTeamInternal();
		}

		void renderTeamFromDraft() {
			renderTeamFromDraftInternal();
		}
	}

	private final class CaseUpdatesPanelController {
		void loadCaseUpdatesAsync() {
			loadCaseUpdatesAsyncInternal();
		}

		void renderCaseUpdates(List<CaseUpdateDto> updates) {
			renderCaseUpdatesInternal(updates);
		}

		void onSubmitCaseUpdate() {
			onSubmitCaseUpdateInternal();
		}

		Node createCaseUpdateCard(CaseUpdateDto dto) {
			return createCaseUpdateCardInternal(dto);
		}
	}

	private static final class CaseDetailsDraft {
		String name;
		String caseNumber;
		Integer primaryStatusId;
		String primaryStatusName;
		String primaryStatusColor;
		Integer practiceAreaId;
		String practiceAreaName;
		String practiceAreaColor;
		String description;

		LocalDate callerDate;
		String callerTime;
		LocalDate acceptedDate;
		LocalDate closedDate;
		LocalDate deniedDate;

		LocalDate dateOfMedicalNegligence;
		LocalDate dateMedicalNegligenceWasDiscovered;
		LocalDate dateOfInjury;
		LocalDate statuteOfLimitations;
		LocalDate tortNoticeDeadline;
		LocalDate discoveryDeadline;

		String clientEstate;
		String officePrinterCode;
		Boolean medicalRecordsReceived;
		Boolean feeAgreementSigned;
		LocalDate dateFeeAgreementSigned;

		Boolean acceptedChronology;
		Boolean acceptedConsultantExpertSearch;
		Boolean acceptedTestifyingExpertSearch;
		Boolean acceptedMedicalLiterature;
		String acceptedDetail;

		Boolean deniedChronology;
		String deniedDetail;
		String summary;
		Boolean receivedUpdates;

		static CaseDetailsDraft from(CaseDetailDto detail, CaseOverviewDto overview) {
			CaseDetailsDraft d = new CaseDetailsDraft();
			d.name = detail == null ? "" : safeText(detail.getCaseName());
			d.caseNumber = detail == null ? "" : safeText(detail.getCaseNumber());

			d.primaryStatusId = overview == null ? null : overview.getPrimaryStatusId();
			d.primaryStatusName = overview == null ? "" : safeText(overview.getCaseStatus());
			d.primaryStatusColor = overview == null ? null : overview.getPrimaryStatusColor();

			Integer practiceAreaId = (detail == null ? null : detail.getPracticeAreaId());
			if (practiceAreaId == null && overview != null)
				practiceAreaId = overview.getPracticeAreaId();
			d.practiceAreaId = practiceAreaId;
			d.practiceAreaName = overview == null ? "" : safeText(overview.getPracticeArea());
			d.practiceAreaColor = overview == null ? null : overview.getPracticeAreaColor();

			d.description = detail == null ? "" : safeText(detail.getDescription());
			d.callerDate = detail == null ? null : detail.getCallerDate();
			d.callerTime = detail == null ? "" : normalizeCallerTimeDisplay(detail.getCallerTime());
			d.acceptedDate = detail == null ? null : detail.getAcceptedDate();
			d.closedDate = detail == null ? null : detail.getClosedDate();
			d.deniedDate = detail == null ? null : detail.getDeniedDate();

			d.dateOfMedicalNegligence = detail == null ? null : detail.getDateOfMedicalNegligence();
			d.dateMedicalNegligenceWasDiscovered = detail == null ? null : detail.getDateMedicalNegligenceWasDiscovered();
			d.dateOfInjury = detail == null ? null : detail.getDateOfInjury();
			d.statuteOfLimitations = detail == null ? null : detail.getStatuteOfLimitations();
			d.tortNoticeDeadline = detail == null ? null : detail.getTortNoticeDeadline();
			d.discoveryDeadline = detail == null ? null : detail.getDiscoveryDeadline();

			d.clientEstate = detail == null ? "0" : normalizeDetailsCheckboxStorage(detail.getClientEstate());
			d.officePrinterCode = detail == null ? "" : safeText(detail.getOfficePrinterCode());
			d.medicalRecordsReceived = detail == null ? Boolean.FALSE : normalizeDetailsCheckboxBoolean(detail.getMedicalRecordsReceived());
			d.feeAgreementSigned = detail == null ? Boolean.FALSE : normalizeDetailsCheckboxBoolean(detail.getFeeAgreementSigned());
			d.dateFeeAgreementSigned = detail == null ? null : detail.getDateFeeAgreementSigned();

			d.acceptedChronology = detail == null ? Boolean.FALSE : normalizeDetailsCheckboxBoolean(detail.getAcceptedChronology());
			d.acceptedConsultantExpertSearch = detail == null ? Boolean.FALSE : normalizeDetailsCheckboxBoolean(detail.getAcceptedConsultantExpertSearch());
			d.acceptedTestifyingExpertSearch = detail == null ? Boolean.FALSE : normalizeDetailsCheckboxBoolean(detail.getAcceptedTestifyingExpertSearch());
			d.acceptedMedicalLiterature = detail == null ? Boolean.FALSE : normalizeDetailsCheckboxBoolean(detail.getAcceptedMedicalLiterature());
			d.acceptedDetail = detail == null ? "" : safeText(detail.getAcceptedDetail());

			d.deniedChronology = detail == null ? Boolean.FALSE : normalizeDetailsCheckboxBoolean(detail.getDeniedChronology());
			d.deniedDetail = detail == null ? "" : safeText(detail.getDeniedDetail());

			d.summary = detail == null ? "" : safeText(detail.getSummary());
			d.receivedUpdates = detail == null ? Boolean.FALSE : normalizeDetailsCheckboxBoolean(parseNullableBooleanStorage(detail.getReceivedUpdates()));
			return d;
		}

		CaseDetailsDraft copy() {
			CaseDetailsDraft c = new CaseDetailsDraft();
			c.name = name;
			c.caseNumber = caseNumber;
			c.primaryStatusId = primaryStatusId;
			c.primaryStatusName = primaryStatusName;
			c.primaryStatusColor = primaryStatusColor;
			c.practiceAreaId = practiceAreaId;
			c.practiceAreaName = practiceAreaName;
			c.practiceAreaColor = practiceAreaColor;
			c.description = description;
			c.callerDate = callerDate;
			c.callerTime = callerTime;
			c.acceptedDate = acceptedDate;
			c.closedDate = closedDate;
			c.deniedDate = deniedDate;
			c.dateOfMedicalNegligence = dateOfMedicalNegligence;
			c.dateMedicalNegligenceWasDiscovered = dateMedicalNegligenceWasDiscovered;
			c.dateOfInjury = dateOfInjury;
			c.statuteOfLimitations = statuteOfLimitations;
			c.tortNoticeDeadline = tortNoticeDeadline;
			c.discoveryDeadline = discoveryDeadline;
			c.clientEstate = clientEstate;
			c.officePrinterCode = officePrinterCode;
			c.medicalRecordsReceived = medicalRecordsReceived;
			c.feeAgreementSigned = feeAgreementSigned;
			c.dateFeeAgreementSigned = dateFeeAgreementSigned;
			c.acceptedChronology = acceptedChronology;
			c.acceptedConsultantExpertSearch = acceptedConsultantExpertSearch;
			c.acceptedTestifyingExpertSearch = acceptedTestifyingExpertSearch;
			c.acceptedMedicalLiterature = acceptedMedicalLiterature;
			c.acceptedDetail = acceptedDetail;
			c.deniedChronology = deniedChronology;
			c.deniedDetail = deniedDetail;
			c.summary = summary;
			c.receivedUpdates = receivedUpdates;
			return c;
		}
	}

	private final class CaseDetailsSaveCoordinator {
		void save() {
			if (caseDao == null || caseId == null || current == null) {
				showError("Case is still loading. Please try again.");
				return;
			}
			if (detailsDraft == null)
				return;

			detailsEditor.captureEditors(detailsDraft);
			DetailsSaveRequest request;
			try {
				request = buildSaveRequest(detailsDraft, current);
			} catch (IllegalArgumentException ex) {
				showError(ex.getMessage());
				return;
			}

			if (!request.hasChanges()) {
				detailsLocalViewOverride = null;
				detailsDraft = null;
				detailsBaseline = null;
				detailsEditor.setEditMode(false);
				renderDetailsFromCurrent();
				showError("No changes to save.");
				return;
			}

			setBusy(true);
			clearError();

			new Thread(() -> runSaveWorker(request), "case-details-save-" + caseId).start();
		}

		private void runSaveWorker(DetailsSaveRequest request) {
			try {
				CaseDetailDto updated = caseDao.updateCaseDetails(
						request.caseId(),
						request.name(),
						request.caseNumber(),
						request.practiceAreaId(),
						request.description(),
						request.callerDate(),
						request.callerTime(),
						request.acceptedDate(),
						request.closedDate(),
						request.deniedDate(),
						request.dateOfMedicalNegligence(),
						request.dateMedicalNegligenceWasDiscovered(),
						request.dateOfInjury(),
						request.statuteOfLimitations(),
						request.tortNoticeDeadline(),
						request.discoveryDeadline(),
						request.clientEstate(),
						request.officePrinterCode(),
						request.medicalRecordsReceived(),
						request.feeAgreementSigned(),
						request.dateFeeAgreementSigned(),
						request.acceptedChronology(),
						request.acceptedConsultantExpertSearch(),
						request.acceptedTestifyingExpertSearch(),
						request.acceptedMedicalLiterature(),
						request.acceptedDetail(),
						request.deniedChronology(),
						request.deniedDetail(),
						request.summary(),
						request.receivedUpdates(),
						request.expectedRowVer());

				if (updated != null && request.statusChanged() && request.primaryStatusId() != null)
					caseDao.setPrimaryStatus(request.caseId(), request.primaryStatusId(), null);
				if (updated != null && request.statusChanged() && request.primaryStatusId() != null) {
					addStatusChangedTimelineEvent(
							request.caseId(),
							(appState == null ? null : appState.getShaleClientId()),
							(appState == null ? null : appState.getUserId()),
							request.baselinePrimaryStatusId(),
							request.baselinePrimaryStatusName(),
							request.primaryStatusId(),
							request.primaryStatusName()
					);
				}
				if (updated != null) {
					addDateChangedTimelineEvent(
							request.caseId(),
							(appState == null ? null : appState.getShaleClientId()),
							(appState == null ? null : appState.getUserId()),
							CaseDao.CaseTimelineEventTypes.INTAKE_DATE_CHANGED,
							"Intake date changed",
							request.baseline().getCallerDate(),
							request.callerDate()
					);
					addTimeChangedTimelineEvent(
							request.caseId(),
							(appState == null ? null : appState.getShaleClientId()),
							(appState == null ? null : appState.getUserId()),
							CaseDao.CaseTimelineEventTypes.INTAKE_TIME_CHANGED,
							"Intake time changed",
							normalizeCallerTimeDisplay(request.baseline().getCallerTime()),
							request.callerTime()
					);
					addDateChangedTimelineEvent(
							request.caseId(),
							(appState == null ? null : appState.getShaleClientId()),
							(appState == null ? null : appState.getUserId()),
							CaseDao.CaseTimelineEventTypes.ACCEPTED_DATE_CHANGED,
							"Accepted date changed",
							request.baseline().getAcceptedDate(),
							request.acceptedDate()
					);
					addDateChangedTimelineEvent(
							request.caseId(),
							(appState == null ? null : appState.getShaleClientId()),
							(appState == null ? null : appState.getUserId()),
							CaseDao.CaseTimelineEventTypes.CLOSED_DATE_CHANGED,
							"Closed date changed",
							request.baseline().getClosedDate(),
							request.closedDate()
					);
					addDateChangedTimelineEvent(
							request.caseId(),
							(appState == null ? null : appState.getShaleClientId()),
							(appState == null ? null : appState.getUserId()),
							CaseDao.CaseTimelineEventTypes.DENIED_DATE_CHANGED,
							"Denied date changed",
							request.baseline().getDeniedDate(),
							request.deniedDate()
					);
					addDateChangedTimelineEvent(
							request.caseId(),
							(appState == null ? null : appState.getShaleClientId()),
							(appState == null ? null : appState.getUserId()),
							CaseDao.CaseTimelineEventTypes.MEDICAL_MALPRACTICE_DATE_CHANGED,
							"Date of medical negligence changed",
							request.baseline().getDateOfMedicalNegligence(),
							request.dateOfMedicalNegligence()
					);
					addDateChangedTimelineEvent(
							request.caseId(),
							(appState == null ? null : appState.getShaleClientId()),
							(appState == null ? null : appState.getUserId()),
							CaseDao.CaseTimelineEventTypes.MEDICAL_MALPRACTICE_DISCOVERY_DATE_CHANGED,
							"Medical negligence discovery date changed",
							request.baseline().getDateMedicalNegligenceWasDiscovered(),
							request.dateMedicalNegligenceWasDiscovered()
					);
					addDateChangedTimelineEvent(
							request.caseId(),
							(appState == null ? null : appState.getShaleClientId()),
							(appState == null ? null : appState.getUserId()),
							CaseDao.CaseTimelineEventTypes.INJURY_DATE_CHANGED,
							"Date of injury changed",
							request.baseline().getDateOfInjury(),
							request.dateOfInjury()
					);
					addDateChangedTimelineEvent(
							request.caseId(),
							(appState == null ? null : appState.getShaleClientId()),
							(appState == null ? null : appState.getUserId()),
							CaseDao.CaseTimelineEventTypes.STATUTE_OF_LIMITATIONS_CHANGED,
							"Statute of limitations changed",
							request.baseline().getStatuteOfLimitations(),
							request.statuteOfLimitations()
					);
					addDateChangedTimelineEvent(
							request.caseId(),
							(appState == null ? null : appState.getShaleClientId()),
							(appState == null ? null : appState.getUserId()),
							CaseDao.CaseTimelineEventTypes.TORT_NOTICE_DEADLINE_CHANGED,
							"Tort notice deadline changed",
							request.baseline().getTortNoticeDeadline(),
							request.tortNoticeDeadline()
					);
					addDateChangedTimelineEvent(
							request.caseId(),
							(appState == null ? null : appState.getShaleClientId()),
							(appState == null ? null : appState.getUserId()),
							CaseDao.CaseTimelineEventTypes.DISCOVERY_DEADLINE_CHANGED,
							"Discovery deadline changed",
							request.baseline().getDiscoveryDeadline(),
							request.discoveryDeadline()
					);
					addDateChangedTimelineEvent(
							request.caseId(),
							(appState == null ? null : appState.getShaleClientId()),
							(appState == null ? null : appState.getUserId()),
							CaseDao.CaseTimelineEventTypes.FEE_AGREEMENT_DATE_CHANGED,
							"Fee agreement date changed",
							request.baseline().getDateFeeAgreementSigned(),
							request.dateFeeAgreementSigned()
					);
					addBooleanChangedTimelineEvent(
							request.caseId(),
							(appState == null ? null : appState.getShaleClientId()),
							(appState == null ? null : appState.getUserId()),
							CaseDao.CaseTimelineEventTypes.ESTATE_CASE_CHANGED,
							"Estate case updated",
							parseNullableBooleanStorage(request.baseline().getClientEstate()),
							parseNullableBooleanStorage(request.clientEstate())
					);
					addBooleanChangedTimelineEvent(
							request.caseId(),
							(appState == null ? null : appState.getShaleClientId()),
							(appState == null ? null : appState.getUserId()),
							CaseDao.CaseTimelineEventTypes.MEDICAL_RECORDS_RECEIVED_CHANGED,
							"Medical records received updated",
							request.baseline().getMedicalRecordsReceived(),
							request.medicalRecordsReceived()
					);
					addBooleanChangedTimelineEvent(
							request.caseId(),
							(appState == null ? null : appState.getShaleClientId()),
							(appState == null ? null : appState.getUserId()),
							CaseDao.CaseTimelineEventTypes.FEE_AGREEMENT_SIGNED_CHANGED,
							"Fee agreement signed updated",
							request.baseline().getFeeAgreementSigned(),
							request.feeAgreementSigned()
					);
					addBooleanChangedTimelineEvent(
							request.caseId(),
							(appState == null ? null : appState.getShaleClientId()),
							(appState == null ? null : appState.getUserId()),
							CaseDao.CaseTimelineEventTypes.ACCEPTED_CHRONOLOGY_CHANGED,
							"Accepted chronology updated",
							request.baseline().getAcceptedChronology(),
							request.acceptedChronology()
					);
					addBooleanChangedTimelineEvent(
							request.caseId(),
							(appState == null ? null : appState.getShaleClientId()),
							(appState == null ? null : appState.getUserId()),
							CaseDao.CaseTimelineEventTypes.CONSULTANT_EXPERT_SEARCH_CHANGED,
							"Consultant expert search updated",
							request.baseline().getAcceptedConsultantExpertSearch(),
							request.acceptedConsultantExpertSearch()
					);
					addBooleanChangedTimelineEvent(
							request.caseId(),
							(appState == null ? null : appState.getShaleClientId()),
							(appState == null ? null : appState.getUserId()),
							CaseDao.CaseTimelineEventTypes.TESTIFYING_EXPERT_SEARCH_CHANGED,
							"Testifying expert search updated",
							request.baseline().getAcceptedTestifyingExpertSearch(),
							request.acceptedTestifyingExpertSearch()
					);
					addBooleanChangedTimelineEvent(
							request.caseId(),
							(appState == null ? null : appState.getShaleClientId()),
							(appState == null ? null : appState.getUserId()),
							CaseDao.CaseTimelineEventTypes.MEDICAL_LITERATURE_CHANGED,
							"Medical literature updated",
							request.baseline().getAcceptedMedicalLiterature(),
							request.acceptedMedicalLiterature()
					);
					addBooleanChangedTimelineEvent(
							request.caseId(),
							(appState == null ? null : appState.getShaleClientId()),
							(appState == null ? null : appState.getUserId()),
							CaseDao.CaseTimelineEventTypes.DENIED_CHRONOLOGY_CHANGED,
							"Denied chronology updated",
							request.baseline().getDeniedChronology(),
							request.deniedChronology()
					);
					addBooleanChangedTimelineEvent(
							request.caseId(),
							(appState == null ? null : appState.getShaleClientId()),
							(appState == null ? null : appState.getUserId()),
							CaseDao.CaseTimelineEventTypes.RECEIVED_UPDATES_CHANGED,
							"Received updates updated",
							parseNullableBooleanStorage(request.baseline().getReceivedUpdates()),
							parseNullableBooleanStorage(request.receivedUpdates())
					);
					addTextIdentityChangedTimelineEvent(
							request.caseId(),
							(appState == null ? null : appState.getShaleClientId()),
							(appState == null ? null : appState.getUserId()),
							CaseDao.CaseTimelineEventTypes.CASE_NAME_CHANGED,
							"Case name changed",
							request.baseline().getCaseName(),
							request.name()
					);
					addTextIdentityChangedTimelineEvent(
							request.caseId(),
							(appState == null ? null : appState.getShaleClientId()),
							(appState == null ? null : appState.getUserId()),
							CaseDao.CaseTimelineEventTypes.CASE_NUMBER_CHANGED,
							"Case number changed",
							request.baseline().getCaseNumber(),
							request.caseNumber()
					);
					addTextIdentityChangedTimelineEvent(
							request.caseId(),
							(appState == null ? null : appState.getShaleClientId()),
							(appState == null ? null : appState.getUserId()),
							CaseDao.CaseTimelineEventTypes.OFFICE_CASE_CODE_CHANGED,
							"Office case code changed",
							request.baseline().getOfficePrinterCode(),
							request.officePrinterCode()
					);
					addDescriptionChangedTimelineEvent(
							request.caseId(),
							(appState == null ? null : appState.getShaleClientId()),
							(appState == null ? null : appState.getUserId()),
							request.baseline().getDescription(),
							request.description()
					);
					addLongTextChangedTimelineEvent(
							request.caseId(),
							(appState == null ? null : appState.getShaleClientId()),
							(appState == null ? null : appState.getUserId()),
							CaseDao.CaseTimelineEventTypes.SUMMARY_UPDATED,
							"Summary updated",
							request.baseline().getSummary(),
							request.summary()
					);
					addLongTextChangedTimelineEvent(
							request.caseId(),
							(appState == null ? null : appState.getShaleClientId()),
							(appState == null ? null : appState.getUserId()),
							CaseDao.CaseTimelineEventTypes.ACCEPTED_DETAIL_UPDATED,
							"Accepted detail updated",
							request.baseline().getAcceptedDetail(),
							request.acceptedDetail()
					);
					addLongTextChangedTimelineEvent(
							request.caseId(),
							(appState == null ? null : appState.getShaleClientId()),
							(appState == null ? null : appState.getUserId()),
							CaseDao.CaseTimelineEventTypes.DENIED_DETAIL_UPDATED,
							"Denied detail updated",
							request.baseline().getDeniedDetail(),
							request.deniedDetail()
					);
					addPracticeAreaChangedTimelineEvent(
							request.caseId(),
							(appState == null ? null : appState.getShaleClientId()),
							(appState == null ? null : appState.getUserId()),
							request.baseline().getPracticeAreaId(),
							null,
							request.practiceAreaId(),
							null
					);
				}
				if (updated != null)
					currentOverview = caseDao.getOverview(request.caseId());

				runOnFx(() -> handleSaveResult(request, updated));
			} catch (Exception ex) {
				runOnFx(() -> {
					showError("Failed to save case details. " + ex.getMessage());
					setBusy(false);
				});
			}
		}

		private void handleSaveResult(DetailsSaveRequest request, CaseDetailDto updated) {
			if (updated == null) {
				showError("This case was updated elsewhere. Reload and try again.");
				setBusy(false);
				return;
			}
			current = updated;
			detailsLocalViewOverride = null;
			detailsDraft = null;
			detailsBaseline = null;
			detailsEditor.setEditMode(false);
			renderDetailsFromCurrent();
			applyDetail(updated);
			clearError();
			publishDetailsFieldUpdates(request);
			setBusy(false);
			reloadCurrentCaseForViewMode();
		}

		private void publishDetailsFieldUpdates(DetailsSaveRequest request) {
			CaseDetailDto baseline = request.baseline();
			publishIfChanged(request.caseId(), "name", normalizeRequired(baseline.getCaseName()), request.name());
			publishIfChanged(request.caseId(), "caseNumber", normalizeNullableText(baseline.getCaseNumber()), request.caseNumber());
			publishIfChanged(request.caseId(), "primaryStatusId", request.baselinePrimaryStatusId(), request.primaryStatusId());
			publishIfChanged(request.caseId(), "practiceAreaId", baseline.getPracticeAreaId(), request.practiceAreaId());
			publishIfChanged(request.caseId(), "description", normalizeNullableText(baseline.getDescription()), request.description());
			publishIfChanged(request.caseId(), "callerDate", baseline.getCallerDate(), request.callerDate());
			publishIfChanged(request.caseId(), "callerTime", normalizeCallerTimeInput(normalizeCallerTimeDisplay(baseline.getCallerTime())), request.callerTime());
			publishIfChanged(request.caseId(), "acceptedDate", baseline.getAcceptedDate(), request.acceptedDate());
			publishIfChanged(request.caseId(), "closedDate", baseline.getClosedDate(), request.closedDate());
			publishIfChanged(request.caseId(), "deniedDate", baseline.getDeniedDate(), request.deniedDate());
			publishIfChanged(request.caseId(), "dateOfMedicalNegligence", baseline.getDateOfMedicalNegligence(), request.dateOfMedicalNegligence());
			publishIfChanged(request.caseId(), "dateMedicalNegligenceWasDiscovered", baseline.getDateMedicalNegligenceWasDiscovered(), request.dateMedicalNegligenceWasDiscovered());
			publishIfChanged(request.caseId(), "dateOfInjury", baseline.getDateOfInjury(), request.dateOfInjury());
			publishIfChanged(request.caseId(), "statuteOfLimitations", baseline.getStatuteOfLimitations(), request.statuteOfLimitations());
			publishIfChanged(request.caseId(), "tortNoticeDeadline", baseline.getTortNoticeDeadline(), request.tortNoticeDeadline());
			publishIfChanged(request.caseId(), "discoveryDeadline", baseline.getDiscoveryDeadline(), request.discoveryDeadline());
			publishIfChanged(request.caseId(), "clientEstate", normalizeNullableText(baseline.getClientEstate()), request.clientEstate());
			publishIfChanged(request.caseId(), "officePrinterCode", normalizeNullableText(baseline.getOfficePrinterCode()), request.officePrinterCode());
			publishIfChanged(request.caseId(), "medicalRecordsReceived", baseline.getMedicalRecordsReceived(), request.medicalRecordsReceived());
			publishIfChanged(request.caseId(), "feeAgreementSigned", baseline.getFeeAgreementSigned(), request.feeAgreementSigned());
			publishIfChanged(request.caseId(), "dateFeeAgreementSigned", baseline.getDateFeeAgreementSigned(), request.dateFeeAgreementSigned());
			publishIfChanged(request.caseId(), "acceptedChronology", baseline.getAcceptedChronology(), request.acceptedChronology());
			publishIfChanged(request.caseId(), "acceptedConsultantExpertSearch", baseline.getAcceptedConsultantExpertSearch(), request.acceptedConsultantExpertSearch());
			publishIfChanged(request.caseId(), "acceptedTestifyingExpertSearch", baseline.getAcceptedTestifyingExpertSearch(), request.acceptedTestifyingExpertSearch());
			publishIfChanged(request.caseId(), "acceptedMedicalLiterature", baseline.getAcceptedMedicalLiterature(), request.acceptedMedicalLiterature());
			publishIfChanged(request.caseId(), "acceptedDetail", normalizeNullableText(baseline.getAcceptedDetail()), request.acceptedDetail());
			publishIfChanged(request.caseId(), "deniedChronology", baseline.getDeniedChronology(), request.deniedChronology());
			publishIfChanged(request.caseId(), "deniedDetail", normalizeNullableText(baseline.getDeniedDetail()), request.deniedDetail());
			publishIfChanged(request.caseId(), "summary", normalizeNullableText(baseline.getSummary()), request.summary());
			publishIfChanged(request.caseId(), "receivedUpdates", normalizeNullableText(baseline.getReceivedUpdates()), request.receivedUpdates());

			// Keep Overview inline listeners responsive for these two shared fields.
			publishIfChanged(request.caseId(), "incidentDate", baseline.getDateOfInjury(), request.dateOfInjury());
			publishIfChanged(request.caseId(), "solDate", baseline.getStatuteOfLimitations(), request.statuteOfLimitations());
		}

		private void publishIfChanged(long caseId, String field, Object before, Object after) {
			if (!Objects.equals(before, after))
				publishCaseFieldUpdated(caseId, field, after);
		}

		private DetailsSaveRequest buildSaveRequest(CaseDetailsDraft source, CaseDetailDto baseline) {
			String name = normalizeRequired(source.name);
			if (name.isBlank())
				throw new IllegalArgumentException("Case Name is required.");

			String caseNumber = normalizeNullableText(source.caseNumber);
			Integer practiceAreaId = source.practiceAreaId;
			String description = normalizeNullableText(source.description);
			String callerTime = normalizeCallerTimeInput(source.callerTime);
			String clientEstate = normalizeDetailsCheckboxStorage(source.clientEstate);
			String officePrinterCode = normalizeNullableText(source.officePrinterCode);
			Boolean medicalRecordsReceived = normalizeDetailsCheckboxBoolean(source.medicalRecordsReceived);
			Boolean feeAgreementSigned = normalizeDetailsCheckboxBoolean(source.feeAgreementSigned);
			Boolean acceptedChronology = normalizeDetailsCheckboxBoolean(source.acceptedChronology);
			Boolean acceptedConsultantExpertSearch = normalizeDetailsCheckboxBoolean(source.acceptedConsultantExpertSearch);
			Boolean acceptedTestifyingExpertSearch = normalizeDetailsCheckboxBoolean(source.acceptedTestifyingExpertSearch);
			Boolean acceptedMedicalLiterature = normalizeDetailsCheckboxBoolean(source.acceptedMedicalLiterature);
			Boolean deniedChronology = normalizeDetailsCheckboxBoolean(source.deniedChronology);
			String acceptedDetail = normalizeNullableText(source.acceptedDetail);
			String deniedDetail = normalizeNullableText(source.deniedDetail);
			String summary = normalizeNullableText(source.summary);
			String receivedUpdates = toNullableBooleanStorage(normalizeDetailsCheckboxBoolean(source.receivedUpdates));
			Boolean baselineMedicalRecordsReceived = normalizeDetailsCheckboxBoolean(baseline.getMedicalRecordsReceived());
			Boolean baselineFeeAgreementSigned = normalizeDetailsCheckboxBoolean(baseline.getFeeAgreementSigned());
			Boolean baselineAcceptedChronology = normalizeDetailsCheckboxBoolean(baseline.getAcceptedChronology());
			Boolean baselineAcceptedConsultantExpertSearch = normalizeDetailsCheckboxBoolean(baseline.getAcceptedConsultantExpertSearch());
			Boolean baselineAcceptedTestifyingExpertSearch = normalizeDetailsCheckboxBoolean(baseline.getAcceptedTestifyingExpertSearch());
			Boolean baselineAcceptedMedicalLiterature = normalizeDetailsCheckboxBoolean(baseline.getAcceptedMedicalLiterature());
			Boolean baselineDeniedChronology = normalizeDetailsCheckboxBoolean(baseline.getDeniedChronology());
			LifecycleDates lifecycleDates = withLifecycleAutopopulatedDates(
					source.primaryStatusId,
					(appState == null ? null : appState.getShaleClientId()),
					source.acceptedDate,
					source.closedDate,
					source.deniedDate);

			boolean statusChanged = !Objects.equals(source.primaryStatusId, currentOverview == null ? null : currentOverview.getPrimaryStatusId());
			boolean changed =
				statusChanged ||
				!Objects.equals(name, normalizeRequired(baseline.getCaseName())) ||
				!Objects.equals(caseNumber, normalizeNullableText(baseline.getCaseNumber())) ||
				!Objects.equals(practiceAreaId, baseline.getPracticeAreaId()) ||
				!Objects.equals(description, normalizeNullableText(baseline.getDescription())) ||
				!Objects.equals(source.callerDate, baseline.getCallerDate()) ||
				!Objects.equals(callerTime, normalizeCallerTimeInput(normalizeCallerTimeDisplay(baseline.getCallerTime()))) ||
				!Objects.equals(lifecycleDates.acceptedDate(), baseline.getAcceptedDate()) ||
				!Objects.equals(lifecycleDates.closedDate(), baseline.getClosedDate()) ||
				!Objects.equals(lifecycleDates.deniedDate(), baseline.getDeniedDate()) ||
				!Objects.equals(source.dateOfMedicalNegligence, baseline.getDateOfMedicalNegligence()) ||
				!Objects.equals(source.dateMedicalNegligenceWasDiscovered, baseline.getDateMedicalNegligenceWasDiscovered()) ||
				!Objects.equals(source.dateOfInjury, baseline.getDateOfInjury()) ||
				!Objects.equals(source.statuteOfLimitations, baseline.getStatuteOfLimitations()) ||
				!Objects.equals(source.tortNoticeDeadline, baseline.getTortNoticeDeadline()) ||
				!Objects.equals(source.discoveryDeadline, baseline.getDiscoveryDeadline()) ||
				!Objects.equals(clientEstate, normalizeDetailsCheckboxStorage(baseline.getClientEstate())) ||
				!Objects.equals(officePrinterCode, normalizeNullableText(baseline.getOfficePrinterCode())) ||
				!Objects.equals(medicalRecordsReceived, baselineMedicalRecordsReceived) ||
				!Objects.equals(feeAgreementSigned, baselineFeeAgreementSigned) ||
				!Objects.equals(source.dateFeeAgreementSigned, baseline.getDateFeeAgreementSigned()) ||
				!Objects.equals(acceptedChronology, baselineAcceptedChronology) ||
				!Objects.equals(acceptedConsultantExpertSearch, baselineAcceptedConsultantExpertSearch) ||
				!Objects.equals(acceptedTestifyingExpertSearch, baselineAcceptedTestifyingExpertSearch) ||
				!Objects.equals(acceptedMedicalLiterature, baselineAcceptedMedicalLiterature) ||
				!Objects.equals(acceptedDetail, normalizeNullableText(baseline.getAcceptedDetail())) ||
				!Objects.equals(deniedChronology, baselineDeniedChronology) ||
				!Objects.equals(deniedDetail, normalizeNullableText(baseline.getDeniedDetail())) ||
				!Objects.equals(summary, normalizeNullableText(baseline.getSummary())) ||
				!Objects.equals(receivedUpdates, normalizeDetailsCheckboxStorage(baseline.getReceivedUpdates()));

			return new DetailsSaveRequest(
				caseId.longValue(),
				currentOverview == null ? null : currentOverview.getPrimaryStatusId(),
				currentOverview == null ? null : currentOverview.getCaseStatus(),
				source.primaryStatusId,
				source.primaryStatusName,
				name,
				caseNumber,
				practiceAreaId,
				description,
				source.callerDate,
				callerTime,
				lifecycleDates.acceptedDate(),
				lifecycleDates.closedDate(),
				lifecycleDates.deniedDate(),
				source.dateOfMedicalNegligence,
				source.dateMedicalNegligenceWasDiscovered,
				source.dateOfInjury,
				source.statuteOfLimitations,
				source.tortNoticeDeadline,
				source.discoveryDeadline,
				clientEstate,
				officePrinterCode,
				medicalRecordsReceived,
				feeAgreementSigned,
				source.dateFeeAgreementSigned,
				acceptedChronology,
				acceptedConsultantExpertSearch,
				acceptedTestifyingExpertSearch,
				acceptedMedicalLiterature,
				acceptedDetail,
				deniedChronology,
				deniedDetail,
				summary,
				receivedUpdates,
				baseline.getRowVer(),
				baseline,
				statusChanged,
				changed);
		}


		private String normalizeNullableText(String value) {
			String trimmed = safeText(value).trim();
			return trimmed.isBlank() ? null : trimmed;
		}

		private String normalizeRequired(String value) {
			return safeText(value).trim();
		}
	}

	private CaseDetailDto populateLifecycleDateForSavedStatusIfMissing(
			long caseId,
			Integer savedStatusId,
			Integer tenantId,
			CaseDetailDto snapshot) {
		if (caseDao == null || savedStatusId == null || tenantId == null || tenantId <= 0)
			return snapshot;

		LifecycleDates before = new LifecycleDates(
				snapshot == null ? null : snapshot.getAcceptedDate(),
				snapshot == null ? null : snapshot.getClosedDate(),
				snapshot == null ? null : snapshot.getDeniedDate());
		LifecycleDates after = withLifecycleAutopopulatedDates(
				savedStatusId,
				tenantId,
				before.acceptedDate(),
				before.closedDate(),
				before.deniedDate());
		if (Objects.equals(before, after))
			return snapshot;

		caseDao.populateLifecycleDateIfNull(caseId, resolvePrimaryStatusLifecycleKey(savedStatusId, tenantId));
		return caseDao.getDetail(caseId);
	}

	private LifecycleDates withLifecycleAutopopulatedDates(
			Integer savedStatusId,
			Integer tenantId,
			LocalDate acceptedDate,
			LocalDate closedDate,
			LocalDate deniedDate) {
		String lifecycleKey = resolvePrimaryStatusLifecycleKey(savedStatusId, tenantId);
		LocalDate today = LocalDate.now();
		LocalDate effectiveAcceptedDate = acceptedDate;
		LocalDate effectiveClosedDate = closedDate;
		LocalDate effectiveDeniedDate = deniedDate;
		if (CaseDao.LIFECYCLE_KEY_ACCEPTED.equals(lifecycleKey) && effectiveAcceptedDate == null)
			effectiveAcceptedDate = today;
		if (CaseDao.LIFECYCLE_KEY_CLOSED.equals(lifecycleKey) && effectiveClosedDate == null)
			effectiveClosedDate = today;
		if (CaseDao.LIFECYCLE_KEY_DENIED.equals(lifecycleKey) && effectiveDeniedDate == null)
			effectiveDeniedDate = today;
		return new LifecycleDates(effectiveAcceptedDate, effectiveClosedDate, effectiveDeniedDate);
	}

	private String resolvePrimaryStatusLifecycleKey(Integer savedStatusId, Integer tenantId) {
		if (caseDao == null || savedStatusId == null || tenantId == null || tenantId <= 0)
			return null;
		return caseDao.findLifecycleKeyForStatus(tenantId, savedStatusId);
	}

	private void addStatusChangedTimelineEvent(
			long caseId,
			Integer tenantId,
			Integer actorUserId,
			Integer oldStatusId,
			String oldStatusName,
			Integer newStatusId,
			String newStatusName) {
		if (caseDao == null || tenantId == null || tenantId <= 0 || newStatusId == null)
			return;
		if (Objects.equals(oldStatusId, newStatusId))
			return;

		String oldLabel = resolveStatusLabel(oldStatusName, oldStatusId, tenantId);
		String newLabel = resolveStatusLabel(newStatusName, newStatusId, tenantId);
		String body = "from " + oldLabel + " to " + newLabel;

		caseDao.addCaseTimelineEvent(
				(int) caseId,
				tenantId,
				CaseDao.CaseTimelineEventTypes.STATUS_CHANGED,
				actorUserId,
				"Status changed",
				body
		);
	}

	private String resolveStatusLabel(String preferredName, Integer statusId, Integer tenantId) {
		String trimmed = safeText(preferredName).trim();
		if (!trimmed.isBlank())
			return trimmed;
		if (statusId == null)
			return "none";
		if (caseDao != null && tenantId != null && tenantId > 0) {
			List<CaseDao.StatusRow> statuses = caseDao.listStatusesForTenant(tenantId);
			if (statuses != null) {
				for (CaseDao.StatusRow status : statuses) {
					if (status == null || status.id() != statusId)
						continue;
					String name = safeText(status.name()).trim();
					if (!name.isBlank())
						return name;
				}
			}
		}
		return "Status #" + statusId;
	}

	private void addResponsibleAttorneyChangedTimelineEvent(
			long caseId,
			Integer tenantId,
			Integer actorUserId,
			Integer oldAttorneyUserId,
			String oldAttorneyDisplayName,
			Integer newAttorneyUserId,
			String newAttorneyDisplayName) {
		if (caseDao == null || tenantId == null || tenantId <= 0 || newAttorneyUserId == null)
			return;
		if (Objects.equals(oldAttorneyUserId, newAttorneyUserId))
			return;

		String oldLabel = resolveUserDisplayName(oldAttorneyDisplayName, oldAttorneyUserId, tenantId);
		String newLabel = resolveUserDisplayName(newAttorneyDisplayName, newAttorneyUserId, tenantId);
		String body = "from " + oldLabel + " to " + newLabel;

		caseDao.addCaseTimelineEvent(
				(int) caseId,
				tenantId,
				CaseDao.CaseTimelineEventTypes.RESPONSIBLE_ATTORNEY_CHANGED,
				actorUserId,
				"Responsible attorney changed",
				body
		);
	}

	private String resolveUserDisplayName(String preferredName, Integer userId, Integer tenantId) {
		String trimmed = safeText(preferredName).trim();
		if (!trimmed.isBlank())
			return trimmed;
		if (userId == null)
			return "none";
		if (caseDao != null && tenantId != null && tenantId > 0) {
			List<CaseDao.UserRow> users = caseDao.listUsersForTenant(tenantId);
			if (users != null) {
				for (CaseDao.UserRow user : users) {
					if (user == null || user.id() != userId)
						continue;
					String displayName = safeText(user.displayName()).trim();
					if (!displayName.isBlank())
						return displayName;
				}
			}
		}
		return "User #" + userId;
	}

	private void addPrimaryContactChangedTimelineEvent(
			long caseId,
			Integer tenantId,
			Integer actorUserId,
			String eventType,
			String title,
			Integer oldContactId,
			String oldContactName,
			Integer newContactId,
			String newContactName) {
		if (caseDao == null || tenantId == null || tenantId <= 0 || newContactId == null)
			return;
		if (Objects.equals(oldContactId, newContactId))
			return;

		String oldLabel = resolveContactDisplayName(oldContactName, oldContactId, caseId);
		String newLabel = resolveContactDisplayName(newContactName, newContactId, caseId);
		String body = "from " + oldLabel + " to " + newLabel;

		caseDao.addCaseTimelineEvent(
				(int) caseId,
				tenantId,
				eventType,
				actorUserId,
				title,
				body
		);
	}

	private void addClientsChangedTimelineEvent(
			long caseId,
			Integer tenantId,
			Integer actorUserId,
			List<CaseOverviewDto.ContactSummary> oldClients,
			List<CaseOverviewDto.ContactSummary> newClients) {
		if (caseDao == null || tenantId == null || tenantId <= 0)
			return;
		Map<Integer, String> oldById = toClientNameMap(oldClients);
		Map<Integer, String> newById = toClientNameMap(newClients);

		List<String> added = newById.entrySet().stream()
				.filter(e -> !oldById.containsKey(e.getKey()))
				.map(e -> resolveContactDisplayName(e.getValue(), e.getKey(), caseId))
				.toList();
		List<String> removed = oldById.entrySet().stream()
				.filter(e -> !newById.containsKey(e.getKey()))
				.map(e -> resolveContactDisplayName(e.getValue(), e.getKey(), caseId))
				.toList();
		if (added.isEmpty() && removed.isEmpty())
			return;

		StringBuilder body = new StringBuilder();
		if (!added.isEmpty())
			body.append("added: ").append(String.join(", ", added));
		if (!removed.isEmpty()) {
			if (body.length() > 0)
				body.append("; ");
			body.append("removed: ").append(String.join(", ", removed));
		}
		caseDao.addCaseTimelineEvent(
				(int) caseId,
				tenantId,
				CaseDao.CaseTimelineEventTypes.CLIENT_CHANGED,
				actorUserId,
				"Clients updated",
				body.toString()
		);
	}

	private Map<Integer, String> toClientNameMap(List<CaseOverviewDto.ContactSummary> clients) {
		Map<Integer, String> out = new LinkedHashMap<>();
		if (clients == null)
			return out;
		for (CaseOverviewDto.ContactSummary client : clients) {
			if (client == null || client.contactId() == null || client.contactId() <= 0 || out.containsKey(client.contactId()))
				continue;
			out.put(client.contactId(), safeText(client.displayName()));
		}
		return out;
	}

	private java.util.Set<Integer> toClientIdSet(List<CaseOverviewDto.ContactSummary> clients) {
		return new java.util.LinkedHashSet<>(toClientNameMap(clients).keySet());
	}

	private String resolveContactDisplayName(String preferredName, Integer contactId, long caseId) {
		String trimmed = safeText(preferredName).trim();
		if (!trimmed.isBlank())
			return trimmed;
		if (contactId == null)
			return "none";
		if (caseDao != null) {
			List<CaseDao.RelatedContactRow> contacts = caseDao.findRelatedContacts(caseId);
			if (contacts != null) {
				for (CaseDao.RelatedContactRow contact : contacts) {
					if (contact == null || contact.id() != contactId)
						continue;
					String displayName = safeText(contact.displayName()).trim();
					if (!displayName.isBlank())
						return displayName;
				}
			}
		}
		return "Contact #" + contactId;
	}

	private void addDateChangedTimelineEvent(
			long caseId,
			Integer tenantId,
			Integer actorUserId,
			String eventType,
			String title,
			LocalDate oldDate,
			LocalDate newDate) {
		if (caseDao == null || tenantId == null || tenantId <= 0)
			return;
		if (Objects.equals(oldDate, newDate))
			return;

		String oldLabel = resolveTimelineDateLabel(oldDate);
		String newLabel = resolveTimelineDateLabel(newDate);
		String body = "from " + oldLabel + " to " + newLabel;

		caseDao.addCaseTimelineEvent(
				(int) caseId,
				tenantId,
				eventType,
				actorUserId,
				title,
				body
		);
	}

	private void addTimeChangedTimelineEvent(
			long caseId,
			Integer tenantId,
			Integer actorUserId,
			String eventType,
			String title,
			String oldTime,
			String newTime) {
		if (caseDao == null || tenantId == null || tenantId <= 0)
			return;
		String normalizedOld = normalizeTimelineTextValue(oldTime);
		String normalizedNew = normalizeTimelineTextValue(newTime);
		if (Objects.equals(normalizedOld, normalizedNew))
			return;

		String oldLabel = normalizedOld == null ? "none" : normalizedOld;
		String newLabel = normalizedNew == null ? "none" : normalizedNew;
		String body = "from " + oldLabel + " to " + newLabel;

		caseDao.addCaseTimelineEvent(
				(int) caseId,
				tenantId,
				eventType,
				actorUserId,
				title,
				body
		);
	}

	private void addBooleanChangedTimelineEvent(
			long caseId,
			Integer tenantId,
			Integer actorUserId,
			String eventType,
			String title,
			Boolean oldValue,
			Boolean newValue) {
		if (caseDao == null || tenantId == null || tenantId <= 0)
			return;
		if (Objects.equals(oldValue, newValue) || newValue == null)
			return;

		caseDao.addCaseTimelineEvent(
				(int) caseId,
				tenantId,
				eventType,
				actorUserId,
				title,
				newValue ? "enabled" : "disabled"
		);
	}

	private String resolveTimelineDateLabel(LocalDate value) {
		return value == null ? "none" : value.toString();
	}

	private void addPracticeAreaChangedTimelineEvent(
			long caseId,
			Integer tenantId,
			Integer actorUserId,
			Integer oldPracticeAreaId,
			String oldPracticeAreaName,
			Integer newPracticeAreaId,
			String newPracticeAreaName) {
		if (caseDao == null || tenantId == null || tenantId <= 0)
			return;
		if (Objects.equals(oldPracticeAreaId, newPracticeAreaId))
			return;

		String oldLabel = resolvePracticeAreaLabel(oldPracticeAreaName, oldPracticeAreaId, tenantId);
		String newLabel = resolvePracticeAreaLabel(newPracticeAreaName, newPracticeAreaId, tenantId);
		String body = "from " + oldLabel + " to " + newLabel;

		caseDao.addCaseTimelineEvent(
				(int) caseId,
				tenantId,
				CaseDao.CaseTimelineEventTypes.PRACTICE_AREA_CHANGED,
				actorUserId,
				"Practice area changed",
				body
		);
	}

	private String resolvePracticeAreaLabel(String preferredName, Integer practiceAreaId, Integer tenantId) {
		String trimmed = safeText(preferredName).trim();
		if (!trimmed.isBlank())
			return trimmed;
		if (practiceAreaId == null)
			return "none";
		if (caseDao != null && tenantId != null && tenantId > 0) {
			List<CaseDao.PracticeAreaRow> areas = caseDao.listPracticeAreasForTenant(tenantId);
			if (areas != null) {
				for (CaseDao.PracticeAreaRow area : areas) {
					if (area == null || area.id() != practiceAreaId)
						continue;
					String name = safeText(area.name()).trim();
					if (!name.isBlank())
						return name;
				}
			}
		}
		return "Practice area #" + practiceAreaId;
	}

	private void addTextIdentityChangedTimelineEvent(
			long caseId,
			Integer tenantId,
			Integer actorUserId,
			String eventType,
			String title,
			String oldValue,
			String newValue) {
		if (caseDao == null || tenantId == null || tenantId <= 0)
			return;

		String normalizedOld = normalizeTimelineTextValue(oldValue);
		String normalizedNew = normalizeTimelineTextValue(newValue);
		if (Objects.equals(normalizedOld, normalizedNew))
			return;

		String oldLabel = normalizedOld == null ? "none" : normalizedOld;
		String newLabel = normalizedNew == null ? "none" : normalizedNew;
		String body = "from " + oldLabel + " to " + newLabel;

		caseDao.addCaseTimelineEvent(
				(int) caseId,
				tenantId,
				eventType,
				actorUserId,
				title,
				body
		);
	}

	private String normalizeTimelineTextValue(String value) {
		String trimmed = safeText(value).trim();
		return trimmed.isBlank() ? null : trimmed;
	}

	private void addTeamChangedTimelineEvent(
			long caseId,
			Integer tenantId,
			Integer actorUserId) {
		if (caseDao == null || tenantId == null || tenantId <= 0)
			return;
		caseDao.addCaseTimelineEvent(
				(int) caseId,
				tenantId,
				CaseDao.CaseTimelineEventTypes.TEAM_CHANGED,
				actorUserId,
				"Team changed",
				"updated assigned team"
		);
	}

	private void addDescriptionChangedTimelineEvent(
			long caseId,
			Integer tenantId,
			Integer actorUserId,
			String oldDescription,
			String newDescription) {
		addLongTextChangedTimelineEvent(
				caseId,
				tenantId,
				actorUserId,
				CaseDao.CaseTimelineEventTypes.DESCRIPTION_CHANGED,
				"Description updated",
				oldDescription,
				newDescription
		);
	}

	private void addLongTextChangedTimelineEvent(
			long caseId,
			Integer tenantId,
			Integer actorUserId,
			String eventType,
			String title,
			String oldValue,
			String newValue) {
		if (caseDao == null || tenantId == null || tenantId <= 0)
			return;
		String normalizedOld = normalizeTimelineTextValue(oldValue);
		String normalizedNew = normalizeTimelineTextValue(newValue);
		if (Objects.equals(normalizedOld, normalizedNew))
			return;

		caseDao.addCaseTimelineEvent(
				(int) caseId,
				tenantId,
				eventType,
				actorUserId,
				title,
				null
		);
	}

	private record LifecycleDates(LocalDate acceptedDate, LocalDate closedDate, LocalDate deniedDate) {
	}

	private record DetailsSaveRequest(
			long caseId,
			Integer baselinePrimaryStatusId,
			String baselinePrimaryStatusName,
			Integer primaryStatusId,
			String primaryStatusName,
			String name,
			String caseNumber,
			Integer practiceAreaId,
			String description,
			LocalDate callerDate,
			String callerTime,
			LocalDate acceptedDate,
			LocalDate closedDate,
			LocalDate deniedDate,
			LocalDate dateOfMedicalNegligence,
			LocalDate dateMedicalNegligenceWasDiscovered,
			LocalDate dateOfInjury,
			LocalDate statuteOfLimitations,
			LocalDate tortNoticeDeadline,
			LocalDate discoveryDeadline,
			String clientEstate,
			String officePrinterCode,
			Boolean medicalRecordsReceived,
			Boolean feeAgreementSigned,
			LocalDate dateFeeAgreementSigned,
			Boolean acceptedChronology,
			Boolean acceptedConsultantExpertSearch,
			Boolean acceptedTestifyingExpertSearch,
			Boolean acceptedMedicalLiterature,
			String acceptedDetail,
			Boolean deniedChronology,
			String deniedDetail,
			String summary,
			String receivedUpdates,
			byte[] expectedRowVer,
			CaseDetailDto baseline,
			boolean statusChanged,
			boolean hasChanges) {
	}

	private final class CaseDetailsEditor {
		void beginEdit() {
			CaseDetailsDraft base = resolveDetailsViewModel();
			detailsBaseline = base.copy();
			detailsDraft = base.copy();
			renderEditors(detailsDraft);
			setEditMode(true);
		}


		void cancelEdit() {
			CaseDetailsDraft restore = detailsBaseline != null ? detailsBaseline : resolveDetailsViewModel();
			renderView(restore);
			detailsDraft = null;
			detailsBaseline = null;
			setEditMode(false);
		}

		void setEditMode(boolean enabled) {
			detailsEditMode = enabled;
			setVisibleManaged(detailsEditButton, !enabled);
			setVisibleManaged(detailsSaveButton, enabled);
			setVisibleManaged(detailsCancelButton, enabled);

			toggleDetailField(detNameValue, detNameEditor, enabled);
			toggleDetailField(detCaseNumberValue, detCaseNumberEditor, enabled);
			setVisibleManaged(detCaseStatusValue, !enabled);
			setVisibleManaged(detCaseStatusEditorRow, enabled);
			setVisibleManaged(detPracticeAreaIdValue, !enabled);
			setVisibleManaged(detPracticeAreaEditorRow, enabled);
			toggleDetailField(detDescriptionValue, detDescriptionEditor, enabled);
			toggleDetailField(detCallerDateValue, detCallerDateEditor, enabled);
			toggleDetailField(detCallerTimeValue, detCallerTimeEditor, enabled);
			toggleDetailField(detAcceptedDateValue, detAcceptedDateEditor, enabled);
			toggleDetailField(detClosedDateValue, detClosedDateEditor, enabled);
			toggleDetailField(detDeniedDateValue, detDeniedDateEditor, enabled);
			toggleDetailField(detDateOfMedicalNegligenceValue, detDateOfMedicalNegligenceEditor, enabled);
			toggleDetailField(detDateMedicalNegligenceWasDiscoveredValue, detDateMedicalNegligenceWasDiscoveredEditor, enabled);
			toggleDetailField(detDateOfInjuryValue, detDateOfInjuryEditor, enabled);
			toggleDetailField(detStatuteOfLimitationsValue, detStatuteOfLimitationsEditor, enabled);
			toggleDetailField(detTortNoticeDeadlineValue, detTortNoticeDeadlineEditor, enabled);
			toggleDetailField(detDiscoveryDeadlineValue, detDiscoveryDeadlineEditor, enabled);
			toggleDetailField(detClientEstateValue, detClientEstateEditor, enabled);
			toggleDetailField(detOfficePrinterCodeValue, detOfficePrinterCodeEditor, enabled);
			toggleDetailField(detMedicalRecordsReceivedValue, detMedicalRecordsReceivedEditor, enabled);
			toggleDetailField(detFeeAgreementSignedValue, detFeeAgreementSignedEditor, enabled);
			toggleDetailField(detDateFeeAgreementSignedValue, detDateFeeAgreementSignedEditor, enabled);
			toggleDetailField(detAcceptedChronologyValue, detAcceptedChronologyEditor, enabled);
			toggleDetailField(detAcceptedConsultantExpertSearchValue, detAcceptedConsultantExpertSearchEditor, enabled);
			toggleDetailField(detAcceptedTestifyingExpertSearchValue, detAcceptedTestifyingExpertSearchEditor, enabled);
			toggleDetailField(detAcceptedMedicalLiteratureValue, detAcceptedMedicalLiteratureEditor, enabled);
			toggleDetailField(detAcceptedDetailValue, detAcceptedDetailEditor, enabled);
			toggleDetailField(detDeniedChronologyValue, detDeniedChronologyEditor, enabled);
			toggleDetailField(detDeniedDetailValue, detDeniedDetailEditor, enabled);
			toggleDetailField(detSummaryValue, detSummaryEditor, enabled);
			toggleDetailField(detReceivedUpdatesValue, detReceivedUpdatesEditor, enabled);
		}

		private void toggleDetailField(Label valueNode, javafx.scene.control.Control editorNode, boolean editEnabled) {
			setVisibleManaged(valueNode, !editEnabled);
			setVisibleManaged(editorNode, editEnabled);
		}

		private void renderNullableBoolean(CheckBox editor, Boolean value) {
			if (editor == null)
				return;
			editor.setAllowIndeterminate(false);
			editor.setIndeterminate(false);
			editor.setSelected(Boolean.TRUE.equals(value));
		}

		private Boolean captureNullableBoolean(CheckBox editor) {
			return editor != null && editor.isSelected();
		}

		void renderView(CaseDetailsDraft d) {
			if (d == null)
				return;

			if (detNameValue != null)
				detNameValue.setText(safe(d.name));
			if (detCaseNumberValue != null)
				detCaseNumberValue.setText(safe(d.caseNumber));
			if (detCaseStatusValue != null)
				detCaseStatusValue.setText(safe(d.primaryStatusName));
			renderDetailsStatusMini(d.primaryStatusId, d.primaryStatusName, d.primaryStatusColor);
			if (detPracticeAreaIdValue != null)
				detPracticeAreaIdValue.setText(safe(d.practiceAreaName));
			renderDetailsPracticeAreaMini(d.practiceAreaId, d.practiceAreaName, d.practiceAreaColor);
			if (detDescriptionValue != null)
				detDescriptionValue.setText(safe(d.description));
			if (detCallerDateValue != null)
				detCallerDateValue.setText(formatDate(d.callerDate));
			if (detCallerTimeValue != null)
				detCallerTimeValue.setText(safe(d.callerTime));
			if (detAcceptedDateValue != null)
				detAcceptedDateValue.setText(formatDate(d.acceptedDate));
			if (detClosedDateValue != null)
				detClosedDateValue.setText(formatDate(d.closedDate));
			if (detDeniedDateValue != null)
				detDeniedDateValue.setText(formatDate(d.deniedDate));
			if (detDateOfMedicalNegligenceValue != null)
				detDateOfMedicalNegligenceValue.setText(formatDate(d.dateOfMedicalNegligence));
			if (detDateMedicalNegligenceWasDiscoveredValue != null)
				detDateMedicalNegligenceWasDiscoveredValue.setText(formatDate(d.dateMedicalNegligenceWasDiscovered));
			if (detDateOfInjuryValue != null)
				detDateOfInjuryValue.setText(formatDate(d.dateOfInjury));
			if (detStatuteOfLimitationsValue != null)
				detStatuteOfLimitationsValue.setText(formatDate(d.statuteOfLimitations));
			if (detTortNoticeDeadlineValue != null)
				detTortNoticeDeadlineValue.setText(formatDate(d.tortNoticeDeadline));
			if (detDiscoveryDeadlineValue != null)
				detDiscoveryDeadlineValue.setText(formatDate(d.discoveryDeadline));
			if (detClientEstateValue != null)
				detClientEstateValue.setText(boolLabel(parseNullableBooleanStorage(d.clientEstate)));
			if (detOfficePrinterCodeValue != null)
				detOfficePrinterCodeValue.setText(safe(d.officePrinterCode));
			if (detMedicalRecordsReceivedValue != null)
				detMedicalRecordsReceivedValue.setText(boolLabel(d.medicalRecordsReceived));
			if (detFeeAgreementSignedValue != null)
				detFeeAgreementSignedValue.setText(boolLabel(d.feeAgreementSigned));
			if (detDateFeeAgreementSignedValue != null)
				detDateFeeAgreementSignedValue.setText(formatDate(d.dateFeeAgreementSigned));
			if (detAcceptedChronologyValue != null)
				detAcceptedChronologyValue.setText(boolLabel(d.acceptedChronology));
			if (detAcceptedConsultantExpertSearchValue != null)
				detAcceptedConsultantExpertSearchValue.setText(boolLabel(d.acceptedConsultantExpertSearch));
			if (detAcceptedTestifyingExpertSearchValue != null)
				detAcceptedTestifyingExpertSearchValue.setText(boolLabel(d.acceptedTestifyingExpertSearch));
			if (detAcceptedMedicalLiteratureValue != null)
				detAcceptedMedicalLiteratureValue.setText(boolLabel(d.acceptedMedicalLiterature));
			if (detAcceptedDetailValue != null)
				detAcceptedDetailValue.setText(safe(d.acceptedDetail));
			if (detDeniedChronologyValue != null)
				detDeniedChronologyValue.setText(boolLabel(d.deniedChronology));
			if (detDeniedDetailValue != null)
				detDeniedDetailValue.setText(safe(d.deniedDetail));
			if (detSummaryValue != null)
				detSummaryValue.setText(safe(d.summary));
			if (detReceivedUpdatesValue != null)
				detReceivedUpdatesValue.setText(boolLabel(d.receivedUpdates));
		}


		private void refreshFeeAgreementDateState() {
			if (detFeeAgreementSignedEditor == null || detDateFeeAgreementSignedEditor == null)
				return;
			boolean disable = !detFeeAgreementSignedEditor.isSelected();
			detDateFeeAgreementSignedEditor.setDisable(disable);
			if (disable)
				detDateFeeAgreementSignedEditor.setValue(null);
		}

		private void renderEditors(CaseDetailsDraft d) {
			if (d == null)
				return;

			if (detNameEditor != null)
				detNameEditor.setText(d.name);
			if (detCaseNumberEditor != null)
				detCaseNumberEditor.setText(d.caseNumber);
			renderDetailsStatusMini(d.primaryStatusId, d.primaryStatusName, d.primaryStatusColor);
			renderDetailsPracticeAreaMini(d.practiceAreaId, d.practiceAreaName, d.practiceAreaColor);
			if (detDescriptionEditor != null)
				detDescriptionEditor.setText(d.description);
			if (detCallerDateEditor != null)
				detCallerDateEditor.setValue(d.callerDate);
			if (detCallerTimeEditor != null)
				detCallerTimeEditor.setText(d.callerTime);
			if (detAcceptedDateEditor != null)
				detAcceptedDateEditor.setValue(d.acceptedDate);
			if (detClosedDateEditor != null)
				detClosedDateEditor.setValue(d.closedDate);
			if (detDeniedDateEditor != null)
				detDeniedDateEditor.setValue(d.deniedDate);
			if (detDateOfMedicalNegligenceEditor != null)
				detDateOfMedicalNegligenceEditor.setValue(d.dateOfMedicalNegligence);
			if (detDateMedicalNegligenceWasDiscoveredEditor != null)
				detDateMedicalNegligenceWasDiscoveredEditor.setValue(d.dateMedicalNegligenceWasDiscovered);
			if (detDateOfInjuryEditor != null)
				detDateOfInjuryEditor.setValue(d.dateOfInjury);
			if (detStatuteOfLimitationsEditor != null)
				detStatuteOfLimitationsEditor.setValue(d.statuteOfLimitations);
			if (detTortNoticeDeadlineEditor != null)
				detTortNoticeDeadlineEditor.setValue(d.tortNoticeDeadline);
			if (detDiscoveryDeadlineEditor != null)
				detDiscoveryDeadlineEditor.setValue(d.discoveryDeadline);
			renderNullableBoolean(detClientEstateEditor, parseNullableBooleanStorage(d.clientEstate));
			if (detOfficePrinterCodeEditor != null)
				detOfficePrinterCodeEditor.setText(d.officePrinterCode);
			renderNullableBoolean(detMedicalRecordsReceivedEditor, d.medicalRecordsReceived);
			renderNullableBoolean(detFeeAgreementSignedEditor, d.feeAgreementSigned);
			if (detDateFeeAgreementSignedEditor != null)
				detDateFeeAgreementSignedEditor.setValue(d.dateFeeAgreementSigned);
			if (detFeeAgreementSignedEditor != null)
				detFeeAgreementSignedEditor.setOnAction(e -> refreshFeeAgreementDateState());
			refreshFeeAgreementDateState();
			renderNullableBoolean(detAcceptedChronologyEditor, d.acceptedChronology);
			renderNullableBoolean(detAcceptedConsultantExpertSearchEditor, d.acceptedConsultantExpertSearch);
			renderNullableBoolean(detAcceptedTestifyingExpertSearchEditor, d.acceptedTestifyingExpertSearch);
			renderNullableBoolean(detAcceptedMedicalLiteratureEditor, d.acceptedMedicalLiterature);
			if (detAcceptedDetailEditor != null)
				detAcceptedDetailEditor.setText(d.acceptedDetail);
			renderNullableBoolean(detDeniedChronologyEditor, d.deniedChronology);
			if (detDeniedDetailEditor != null)
				detDeniedDetailEditor.setText(d.deniedDetail);
			if (detSummaryEditor != null)
				detSummaryEditor.setText(d.summary);
			renderNullableBoolean(detReceivedUpdatesEditor, d.receivedUpdates);
		}

		void captureEditors(CaseDetailsDraft d) {
			if (detNameEditor != null)
				d.name = safeText(detNameEditor.getText());
			if (detCaseNumberEditor != null)
				d.caseNumber = safeText(detCaseNumberEditor.getText());
			if (detDescriptionEditor != null)
				d.description = safeText(detDescriptionEditor.getText());
			if (detCallerDateEditor != null)
				d.callerDate = detCallerDateEditor.getValue();
			if (detCallerTimeEditor != null)
				d.callerTime = safeText(detCallerTimeEditor.getText());
			if (detAcceptedDateEditor != null)
				d.acceptedDate = detAcceptedDateEditor.getValue();
			if (detClosedDateEditor != null)
				d.closedDate = detClosedDateEditor.getValue();
			if (detDeniedDateEditor != null)
				d.deniedDate = detDeniedDateEditor.getValue();
			if (detDateOfMedicalNegligenceEditor != null)
				d.dateOfMedicalNegligence = detDateOfMedicalNegligenceEditor.getValue();
			if (detDateMedicalNegligenceWasDiscoveredEditor != null)
				d.dateMedicalNegligenceWasDiscovered = detDateMedicalNegligenceWasDiscoveredEditor.getValue();
			if (detDateOfInjuryEditor != null)
				d.dateOfInjury = detDateOfInjuryEditor.getValue();
			if (detStatuteOfLimitationsEditor != null)
				d.statuteOfLimitations = detStatuteOfLimitationsEditor.getValue();
			if (detTortNoticeDeadlineEditor != null)
				d.tortNoticeDeadline = detTortNoticeDeadlineEditor.getValue();
			if (detDiscoveryDeadlineEditor != null)
				d.discoveryDeadline = detDiscoveryDeadlineEditor.getValue();
			d.clientEstate = toNullableBooleanStorage(captureNullableBoolean(detClientEstateEditor));
			if (detOfficePrinterCodeEditor != null)
				d.officePrinterCode = safeText(detOfficePrinterCodeEditor.getText());
			d.medicalRecordsReceived = captureNullableBoolean(detMedicalRecordsReceivedEditor);
			d.feeAgreementSigned = captureNullableBoolean(detFeeAgreementSignedEditor);
			if (detDateFeeAgreementSignedEditor != null)
				d.dateFeeAgreementSigned = detDateFeeAgreementSignedEditor.getValue();
			if (Boolean.FALSE.equals(d.feeAgreementSigned))
				d.dateFeeAgreementSigned = null;
			d.acceptedChronology = captureNullableBoolean(detAcceptedChronologyEditor);
			d.acceptedConsultantExpertSearch = captureNullableBoolean(detAcceptedConsultantExpertSearchEditor);
			d.acceptedTestifyingExpertSearch = captureNullableBoolean(detAcceptedTestifyingExpertSearchEditor);
			d.acceptedMedicalLiterature = captureNullableBoolean(detAcceptedMedicalLiteratureEditor);
			if (detAcceptedDetailEditor != null)
				d.acceptedDetail = safeText(detAcceptedDetailEditor.getText());
			d.deniedChronology = captureNullableBoolean(detDeniedChronologyEditor);
			if (detDeniedDetailEditor != null)
				d.deniedDetail = safeText(detDeniedDetailEditor.getText());
			if (detSummaryEditor != null)
				d.summary = safeText(detSummaryEditor.getText());
			d.receivedUpdates = captureNullableBoolean(detReceivedUpdatesEditor);
		}
	}

	private record CaseEditModel(String caseName, String caseNumber, String description) {
	}

}
