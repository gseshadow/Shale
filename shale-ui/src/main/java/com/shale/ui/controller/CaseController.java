package com.shale.ui.controller;

import com.shale.ui.component.richtext.NarrativeMarkdownCodec;

import com.shale.ui.component.EnhancedTextArea;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.shale.core.dto.CasePartyDto;
import com.shale.core.dto.CaseDetailDto;
import com.shale.core.dto.CaseOverviewDto;
import com.shale.core.dto.CaseDateDto;
import com.shale.core.dto.EffectiveCaseDateTypeDto;
import com.shale.core.dto.CaseLinkDto;
import com.shale.core.dto.CaseLinkContactOptionDto;
import com.shale.core.dto.CaseLinkShareDto;
import com.shale.core.dto.LinkTypeDto;
import com.shale.core.service.CaseServicePort;
import com.shale.core.service.CaseServicePort.CreateCaseDateCommand;
import com.shale.core.service.CaseServicePort.DeleteCaseDateCommand;
import com.shale.core.service.CaseServicePort.RestoreCaseDateCommand;
import com.shale.core.service.CaseServicePort.UpdateCaseDateCommand;
import com.shale.core.service.MaterialRequestServicePort;
import com.shale.core.dto.CaseTimelineEventDto;
import com.shale.core.dto.CaseUpdateDto;
import com.shale.core.caseupdates.MedicalRecordRequestKeywordMatcher;
import com.shale.core.dto.CaseStatusHistoryDto;
import com.shale.core.dto.CaseTaskListItemDto;
import com.shale.core.dto.TaskDetailDto;
import com.shale.core.dto.TaskPriorityOptionDto;
import com.shale.core.dto.TaskStatusOptionDto;
import com.shale.core.model.CalendarEvent;
import com.shale.core.model.CalendarFeedCategory;
import com.shale.core.model.CalendarFeedClickTarget;
import com.shale.core.model.CalendarFeedItem;
import com.shale.core.model.CalendarFeedSourceFilter;
import com.shale.core.model.CaseDateAggregateResult;
import com.shale.core.model.CaseDateAggregateCommand;
import com.shale.core.model.CompatibilityCaseDateEditor;
import com.shale.core.model.CompatibilityCaseDateState;
import com.shale.core.model.MigratedCaseDateKey;
import com.shale.core.semantics.RoleSemantics;
import com.shale.data.dao.CalendarEventDao;
import com.shale.data.dao.CalendarEventTypeDao;
import com.shale.data.dao.CalendarFeedDao;
import com.shale.data.dao.CaseDao;
import com.shale.data.dao.CaseSummaryDao;
import com.shale.data.dao.ContactDao;
import com.shale.data.dao.OrganizationDao;
import com.shale.ui.component.ContactCard;
import com.shale.ui.component.OrganizationCard;
import com.shale.ui.component.StatusTimeline;
import com.shale.ui.component.UserSelectionField;
import com.shale.ui.component.factory.ContactCardFactory;
import com.shale.ui.document.CaseDocumentExportService;
import com.shale.ui.document.CaseDocumentFormat;
import com.shale.ui.document.CaseDocumentGenerationRequest;
import com.shale.ui.document.CaseDocumentService;
import com.shale.ui.document.CaseDocumentType;
import com.shale.ui.document.GeneratedDocument;
import com.shale.ui.component.factory.OrganizationCardFactory;
import com.shale.ui.component.factory.LinkTypeIndicatorFactory;
import com.shale.ui.component.ColorCodedComboBox;
import com.shale.ui.component.factory.CalendarEventCardFactory;
import com.shale.ui.component.factory.CaseLinkCardFactory;
import com.shale.ui.component.factory.PracticeAreaCardFactory;
import com.shale.ui.component.factory.PracticeAreaCardFactory.PracticeAreaCardModel;
import com.shale.ui.component.factory.PracticeAreaIndicatorFactory;
import com.shale.ui.component.factory.StatusCardFactory;
import com.shale.ui.component.factory.StatusIndicatorFactory;
import com.shale.ui.component.factory.StatusIndicatorFactory.PillSize;
import com.shale.ui.component.factory.StatusCardFactory.StatusCardModel;
import com.shale.ui.component.dialog.AppDialogs;
import com.shale.ui.component.dialog.AppDialogs.DialogAction;
import com.shale.ui.component.dialog.AppDialogs.DialogActionKind;
import com.shale.ui.component.dialog.ClientAssignmentDialog;
import com.shale.ui.component.dialog.ContactPickerDialog;
import com.shale.ui.component.dialog.CreateContactDialog;
import com.shale.ui.component.dialog.CaseDateOccurrenceDialog;
import com.shale.ui.component.dialog.NewCalendarEventDialog;
import com.shale.ui.component.dialog.NewTaskDialog;
import com.shale.ui.component.factory.UserCardFactory;
import com.shale.ui.component.factory.TaskCardFactory;
import com.shale.ui.component.factory.UserCardFactory.UserCardModel;
import com.shale.ui.component.factory.UserCardFactory.Variant;
import com.shale.ui.component.dialog.TeamEditorDialog;
import com.shale.ui.component.dialog.TaskDetailDialog;
import com.shale.ui.services.CalendarService;
import com.shale.ui.services.CaseDetailService;
import com.shale.ui.services.CaseTaskService;
import com.shale.ui.services.LiveUpdateEvents;
import com.shale.ui.services.PhiReadAuditService;
import com.shale.ui.services.UiRuntimeBridge;
import com.shale.ui.state.AppState;
import com.shale.ui.controller.support.PartyAddWorkflowDialog;
import com.shale.ui.controller.support.MedicalRecordsRequestedCaseUpdateSafeguard;
import com.shale.ui.util.ActionButtonFactory;
import com.shale.ui.util.AppSectionTabs;
import com.shale.ui.util.ColorUtil;
import com.shale.ui.util.ControlStyles;
import com.shale.ui.util.ExternalBrowserHelper;
import com.shale.core.util.CaseLinkUrlNormalizer;
import com.shale.ui.util.PerfLog;
import com.shale.ui.util.ReadOnlyTextDisplaySupport;
import com.shale.ui.util.UtcDateTimeDisplayFormatter;
import com.shale.ui.util.WindowSizingUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
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
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.Tooltip;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ScrollBar;
import javafx.scene.Node;
import javafx.beans.binding.Bindings;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * FXML controller for the Case scene and top-level coordinator for case view/edit flows.
 * <p>
 * Responsibility boundaries:
 * <ul>
 * <li>{@code CaseOverviewRenderer}: overview/detail render orchestration</li>
 * <li>{@code CaseOverviewEditor}: edit-mode lifecycle and draft-state transitions</li>
 * <li>{@code CaseOverviewSaveCoordinator}: save validation/persist/publish pipeline</li>
 * <li>{@code CaseOverviewLiveUpdateHandler}: remote case-update
 * subscription/branching</li>
 * <li>{@code CaseOverviewPickerCoordinator}: selectable overview relation pickers</li>
 * <li>{@code CaseTeamCoordinator}: team loading/edit/render</li>
 * <li>{@code CaseUpdatesPanelController}: case updates feed/compose/render</li>
 * </ul>
 */
public class CaseController {
	private static final Logger LOG = LoggerFactory.getLogger(CaseController.class);
	private static final String CASE_TASKS_SORT_DUE_ASC = "Due Date (Soonest)";
	private static final String CASE_TASKS_SORT_DUE_DESC = "Due Date (Latest)";
	private static final String CASE_TASKS_SORT_PRIORITY_ASC = "Priority (Low to High)";
	private static final String CASE_TASKS_SORT_PRIORITY_DESC = "Priority (High to Low)";
	private static final int CASE_CALENDAR_PAST_MONTHS = 12;
	private static final int CASE_CALENDAR_UPCOMING_MONTHS = 24;
	private static final DateTimeFormatter CASE_CALENDAR_DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy");

	// ----------------------------
	// FXML fields
	// ----------------------------

	@FXML
	private Label caseTitleLabel;
	@FXML
	private Label caseMetadataLabel;
	@FXML
	private StackPane intakeTakenByUserHost;
	@FXML
	private Label statusLabel;
	@FXML
	private StackPane statusHost;
	@FXML
	private StackPane assignedUserHost;
	@FXML
	private StackPane statusTimelineHost;
	@FXML
	private Label lastUpdatedLabel;
	@FXML
	private Button addTaskButton;
	@FXML
	private Button backToCasesButton;

	@FXML
	private HBox sectionTabsBar;
	@FXML
	private ScrollPane caseSectionNavigationScrollPane;
	@FXML
	private BorderPane caseRootPane;
	@FXML
	private ScrollPane overviewScrollPane;
	@FXML
	private VBox overviewPane;
	@FXML
	private VBox detailsSectionPane;
	@FXML
	private ScrollPane detailsScrollPane;
	@FXML
	private VBox detailsPane;
	@FXML
	private StackPane detailsUpdatesHost;
	@FXML
	private VBox tasksTabPane;
	@FXML
	private StackPane tasksUpdatesHost;
	@FXML
	private VBox caseCalendarTabPane;
	@FXML
	private VBox caseRequestsTabPane;
	@FXML
	private StackPane caseRequestsContentHost;
	@FXML
	private VBox caseDatesTabPane;
	@FXML
	private VBox caseDatesCardsBox;
	@FXML
	private VBox removedCaseDatesCardsBox;
	@FXML
	private Label caseDatesStatusLabel;
	@FXML
	private Label removedCaseDatesStatusLabel;
	@FXML
	private Button addCaseDateButton;
	@FXML
	private Button refreshCaseDatesButton;
	@FXML
	private Button showRemovedCaseDatesButton;
	@FXML
	private VBox caseLinksTabPane;
	@FXML
	private VBox caseLinksCardsBox;
	@FXML
	private Label caseLinksStatusLabel;
	@FXML
	private Button addCaseLinkButton;
	@FXML
	private ScrollPane caseCalendarScrollPane;
	@FXML
	private VBox caseCalendarAgendaBox;
	@FXML
	private Label caseCalendarStatusLabel;
	@FXML
	private CheckBox caseCalendarEventsLayerCheckBox;
	@FXML
	private CheckBox caseCalendarTasksLayerCheckBox;
	@FXML
	private CheckBox caseCalendarDeadlinesLayerCheckBox;
	@FXML
	private CheckBox caseCalendarCaseDatesLayerCheckBox;
	@FXML
	private Button caseCalendarNewEventButton;
	@FXML
	private Button caseCalendarNewTaskButton;
	@FXML
	private StackPane caseCalendarUpdatesHost;
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
	private StackPane partiesUpdatesHost;
	@FXML
	private StackPane timelineUpdatesHost;

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
	private StackPane ovPrimaryLegalAssistantHost;
	@FXML
	private Button changeResponsibleAttorneyButton;
	@FXML
	private Button changePrimaryLegalAssistantButton;

	@FXML
	private Button changeCallerButton;
	@FXML
	private StackPane ovCallerHost;
	@FXML
	private StackPane ovClientHost;
	@FXML
	private VBox ovPartiesBox;

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
	private Label ovDateOfMedicalNegligenceValue;
	@FXML
	private DatePicker ovDateOfMedicalNegligenceEditor;
	@FXML
	private Label ovSolDateValue;
	@FXML
	private DatePicker ovSolDateEditor;
	@FXML
	private Label ovTortNoticeDeadlineValue;
	@FXML
	private DatePicker ovTortNoticeDeadlineEditor;

	@FXML
	private Label ovDescriptionValue;
	@FXML
	private TextArea ovDescriptionEditor;
	@FXML
	private VBox ovPrimaryLinkBox;
	@FXML
	private Label ovPrimaryLinkStatusLabel;

	@FXML
	private Button deleteCaseButton;
	@FXML
	private Button editButton;
	@FXML
	private Button saveButton;
	@FXML
	private Button cancelButton;
	@FXML
	private Button editCaseNameButton;
	@FXML
	private Button editCaseNumberButton;
	@FXML
	private Button editDescriptionButton;
	@FXML
	private Button editIncidentDateButton;
	@FXML
	private Button editDateOfMedicalNegligenceButton;
	@FXML
	private Button editSolDateButton;
	@FXML
	private Button editTortNoticeDeadlineButton;
	@FXML
	private Button editPartiesButton;

	@FXML
	private MenuButton generateSummaryMenuButton;
	@FXML
	private MenuItem generateSummaryHtmlMenuItem;
	@FXML
	private MenuItem generateSummaryPdfMenuItem;
	@FXML
	private Label summaryGenerationStatusLabel;
	private long documentGeneration;

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
	private Button caseTasksShowCompletedButton;

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
	private TextField caseUpdatesSearchField;
	@FXML
	private VBox caseUpdatesPane;
	@FXML
	private ScrollPane caseUpdatesScrollPane;
	@FXML
	private VBox caseUpdatesFeedBox;

	@FXML
	private Label detNameValue;
	@FXML
	private TextField detNameEditor;
	@FXML
	private Label detCaseNumberValue;
	@FXML
	private TextField detCaseNumberEditor;
	@FXML
	private Label detCaseStatusValue;
	@FXML
	private StackPane detCaseStatusHost;
	@FXML
	private HBox detCaseStatusEditorRow;
	@FXML
	private Button detChangeStatusButton;
	@FXML
	private Label detPracticeAreaIdValue;
	@FXML
	private StackPane detPracticeAreaHost;
	@FXML
	private HBox detPracticeAreaEditorRow;
	@FXML
	private Button detChangePracticeAreaButton;
	@FXML
	private Label detDescriptionValue;
	@FXML
	private TextArea detDescriptionEditor;
	@FXML
	private Label detCallerDateValue;
	@FXML
	private DatePicker detCallerDateEditor;
	@FXML
	private Label detCallerTimeValue;
	@FXML
	private TextField detCallerTimeEditor;
	@FXML
	private Label detAcceptedDateValue;
	@FXML
	private DatePicker detAcceptedDateEditor;
	@FXML
	private Label detClosedDateValue;
	@FXML
	private DatePicker detClosedDateEditor;
	@FXML
	private Label detDeniedDateValue;
	@FXML
	private DatePicker detDeniedDateEditor;
	@FXML
	private Label detDateOfMedicalNegligenceValue;
	@FXML
	private DatePicker detDateOfMedicalNegligenceEditor;
	@FXML
	private Label detDateMedicalNegligenceWasDiscoveredValue;
	@FXML
	private DatePicker detDateMedicalNegligenceWasDiscoveredEditor;
	@FXML
	private Label detDateOfInjuryValue;
	@FXML
	private DatePicker detDateOfInjuryEditor;
	@FXML
	private Label detStatuteOfLimitationsValue;
	@FXML
	private DatePicker detStatuteOfLimitationsEditor;
	@FXML
	private Label detTortNoticeDeadlineValue;
	@FXML
	private DatePicker detTortNoticeDeadlineEditor;
	@FXML
	private Label detDiscoveryDeadlineValue;
	@FXML
	private DatePicker detDiscoveryDeadlineEditor;
	@FXML
	private Label detClientEstateValue;
	@FXML
	private CheckBox detClientEstateEditor;
	@FXML
	private Label detOfficePrinterCodeValue;
	@FXML
	private TextField detOfficePrinterCodeEditor;
	@FXML
	private Label detMedicalRecordsRequestedValue;
	@FXML
	private CheckBox detMedicalRecordsRequestedEditor;
	@FXML
	private Label detFeeAgreementSignedValue;
	@FXML
	private CheckBox detFeeAgreementSignedEditor;
	@FXML
	private Label detDateFeeAgreementSignedValue;
	@FXML
	private DatePicker detDateFeeAgreementSignedEditor;
	@FXML
	private Label detNonEngagementLetterSentValue;
	@FXML
	private CheckBox detNonEngagementLetterSentEditor;
	@FXML
	private Label detDateNonEngagementLetterSentValue;
	@FXML
	private DatePicker detDateNonEngagementLetterSentEditor;
	@FXML
	private Label detAcceptedChronologyValue;
	@FXML
	private CheckBox detAcceptedChronologyEditor;
	@FXML
	private Label detAcceptedConsultantExpertSearchValue;
	@FXML
	private CheckBox detAcceptedConsultantExpertSearchEditor;
	@FXML
	private Label detAcceptedTestifyingExpertSearchValue;
	@FXML
	private CheckBox detAcceptedTestifyingExpertSearchEditor;
	@FXML
	private Label detAcceptedMedicalLiteratureValue;
	@FXML
	private CheckBox detAcceptedMedicalLiteratureEditor;
	@FXML
	private Label detAcceptedDetailValue;
	@FXML
	private TextArea detAcceptedDetailEditor;
	@FXML
	private Label detDeniedChronologyValue;
	@FXML
	private CheckBox detDeniedChronologyEditor;
	@FXML
	private Label detDeniedDetailValue;
	@FXML
	private TextArea detDeniedDetailEditor;
	@FXML
	private Label detSummaryValue;
	@FXML
	private TextArea detSummaryEditor;
	@FXML
	private Label detReceivedUpdatesValue;
	@FXML
	private CheckBox detReceivedUpdatesEditor;

	// ----------------------------
	// Constants
	// ----------------------------

	private static final int ROLE_RESPONSIBLE_ATTORNEY = RoleSemantics.ROLE_RESPONSIBLE_ATTORNEY;
	private static final int ROLE_ATTORNEY = RoleSemantics.ROLE_ATTORNEY;
	private static final int ROLE_LEGAL_ASSISTANT = RoleSemantics.ROLE_LEGAL_ASSISTANT;

	private static final java.util.Set<Integer> TEAM_ROLE_IDS = java.util.Set.copyOf(RoleSemantics.CASE_TEAM_ROLE_IDS);

	private static final List<String> SECTIONS = List.of(
			"Overview",
			"Details",
			"Parties",
			"Tasks",
			"Calendar",
			"Dates",
			"Requests",
			"Links",
			"Timeline"
	);

	static List<String> sectionOrderForTesting() {
		return SECTIONS;
	}

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

	private CalendarService calendarService;
	private CalendarFeedDao calendarFeedDao;
	private CaseServicePort caseService;
	private MaterialRequestServicePort materialRequestService;
	private final CaseMaterialRequestsTabController caseMaterialRequestsTabController = new CaseMaterialRequestsTabController();
	private final CaseLinkCardFactory caseLinkCardFactory = new CaseLinkCardFactory();
	private final ExecutorService caseDateExecutor = Executors.newFixedThreadPool(2, new ThreadFactory() {
		private final java.util.concurrent.atomic.AtomicInteger sequence = new java.util.concurrent.atomic.AtomicInteger();
		@Override public Thread newThread(Runnable runnable) { Thread thread = new Thread(runnable, "case-dates-worker-" + sequence.incrementAndGet()); thread.setDaemon(true); return thread; }
	});
	private final AtomicBoolean caseDateMutationInFlight = new AtomicBoolean(false);
	private final AtomicBoolean remoteCaseDatesRefreshQueued = new AtomicBoolean(false);
	private final java.util.LinkedHashSet<String> receivedDateRefreshEventIds = new java.util.LinkedHashSet<>();
	private boolean caseDateEditorOpen;
	private boolean remoteCaseDatesRefreshDeferred;
	private List<CaseDateDto> caseDates = List.of();
	private List<CaseDateDto> removedCaseDates = List.of();
	private List<EffectiveCaseDateTypeDto> effectiveCaseDateTypes = List.of();
	private boolean caseDatesLoadedOnce;
	private boolean caseDatesStale = true;
	private boolean showRemovedCaseDates;
	private int caseDatesLoadGeneration;
	private CaseDateOccurrenceEditorLauncher caseDateOccurrenceEditorLauncher;
	private final Set<Integer> openingCaseCalendarEventIds = new HashSet<>();

	private final ExecutorService caseLinkExecutor = Executors.newFixedThreadPool(2, new ThreadFactory() {
		private final java.util.concurrent.atomic.AtomicInteger sequence = new java.util.concurrent.atomic.AtomicInteger();
		@Override public Thread newThread(Runnable runnable) {
			Thread thread = new Thread(runnable, "case-links-worker-" + sequence.incrementAndGet());
			thread.setDaemon(true);
			return thread;
		}
	});
	private final AtomicBoolean caseLinkMutationInFlight = new AtomicBoolean(false);
	private ExternalBrowserHelper externalBrowserHelper = new ExternalBrowserHelper();

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
	private final MedicalRecordRequestKeywordMatcher medicalRecordRequestKeywordMatcher = new MedicalRecordRequestKeywordMatcher();

	// ----------------------------
	// Controller state
	// ----------------------------

	private Integer caseId;
	private boolean overviewLoaded = false;
	private boolean editMode = false;
	private boolean detailsEditMode = false;

	private CaseDetailDto current;
	private CaseOverviewDto currentOverview;
	private boolean caseCalendarLoadedOnce;
	private boolean caseCalendarStale = true;
	private int caseCalendarLoadGeneration;
	private List<CalendarFeedItem> caseCalendarItems = List.of();
	private List<CaseLinkDto> caseLinks = List.of();
	private boolean caseLinksLoadedOnce;
	private boolean caseLinksStale = true;
	private int caseLinksLoadGeneration;
	private int caseLinkDialogLoadGeneration;
	private Optional<CaseLinkDto> overviewPrimaryLink = Optional.empty();
	private boolean overviewPrimaryLinkLoadedOnce;
	private boolean overviewPrimaryLinkStale = true;
	private int overviewPrimaryLinkLoadGeneration;
	private CalendarFeedSourceFilter caseCalendarSourceFilter = CalendarFeedSourceFilter.caseCalendarDefaults();
	private byte[] latestCaseRowVer;
	private byte[] overviewEditRowVer;
	private byte[] detailsEditRowVer;
	private final AuthoritativeCaseDateEditor compatibilityDates = new AuthoritativeCaseDateEditor();
	private int compatibilityDatesGeneration;
	private Runnable onCaseDeleted;
	private PhiReadAuditService phiReadAuditService;

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
	private boolean caseTasksLoadedOnce;
	private boolean caseTasksStale = true;
	private boolean showCompletedCaseTasks;
	private List<CaseUpdateDto> caseUpdates = List.of();
	private boolean caseUpdatesLoadedOnce;
	private boolean caseUpdatesStale = true;
	private boolean caseUpdatesLoading;
	private List<CaseTaskService.TaskActivityItem> caseTaskActivityEvents = List.of();
	@FXML
	private VBox caseTaskActivityPane;
	@FXML
	private ScrollPane caseTaskActivityScrollPane;
	@FXML
	private VBox caseTaskActivityFeedBox;
	@FXML
	private Label caseTaskActivityEmptyLabel;
	private Long editingCaseUpdateId;
	private String editingCaseUpdateDraftText = "";
	private boolean savingCaseUpdateEdit = false;

	private final Map<String, Button> sectionTabs = new LinkedHashMap<>();
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
	private long pageLoadStartNanos;
	private static volatile List<PartySideOption> cachedPartySideOptions;
	private static final Map<Integer, List<CaseDao.StatusRow>> statusesByTenantCache = new ConcurrentHashMap<>();
	private static final Map<Integer, List<CaseDao.PracticeAreaRow>> practiceAreasByTenantCache = new ConcurrentHashMap<>();

	private record PartyRoleOption(long id, String label) {
	}

	private record PartyEntityOption(String entityType, Long id, String label) {
	}

	private record PartySideOption(String label, String value) {
	}

	private record PartyEditorResult(String entityType, Long entityId, long partyRoleId, String side, boolean primary, String notes) {
	}

	private record PartyDialogData(
			List<CaseDao.PartyRoleRow> partyRoles,
			List<CaseDao.SelectableContactRow> contacts,
			List<CaseDao.SelectableOrganizationRow> organizations,
			List<PartySideOption> sideOptions,
			List<OrganizationDao.OrganizationTypeRow> organizationTypes
	) {
	}

	private record CallerPartySelection(Integer contactId, String displayName) {
	}

	private record OpposingCounselPartySelection(Integer contactId, String displayName) {
	}

	private enum PartyRenderMode {
		MANAGE,
		READ_ONLY_MINI
	}

	public void init(Integer caseId) {
		documentGeneration++;
		synchronized (receivedDateRefreshEventIds) { receivedDateRefreshEventIds.clear(); }
		remoteCaseDatesRefreshDeferred = false;
		compatibilityDates.invalidate();
		compatibilityDatesGeneration++;
		this.caseId = caseId;
		this.partiesLoadedOnce = false;
		this.caseTasksLoadedOnce = false;
		this.caseTasksStale = true;
		resetCaseCalendarState();
		resetCaseDatesState();
		resetCaseLinksState();
		resetMaterialRequestsState();
		resetOverviewPrimaryLinkState();
		this.caseUpdatesLoadedOnce = false;
		this.caseUpdatesStale = true;
		this.caseUpdatesLoading = false;
		this.caseTaskActivityEvents = List.of();
		renderCaseTaskActivity(List.of());
		refreshHeader();
		refreshOverviewPlaceholders();
	}

	public void init(Integer caseId, CaseDao caseDao,
			CaseSummaryDao caseSummaryDao, CaseDetailService caseDetailService, CaseTaskService caseTaskService, CalendarService calendarService, CalendarFeedDao calendarFeedDao, CaseServicePort caseService, OrganizationDao organizationDao, ContactDao contactDao,
			AppState appState, UiRuntimeBridge runtimeBridge, Runnable onCaseDeleted, PhiReadAuditService phiReadAuditService) {
		documentGeneration++;
		synchronized (receivedDateRefreshEventIds) { receivedDateRefreshEventIds.clear(); }
		remoteCaseDatesRefreshDeferred = false;
		compatibilityDates.invalidate();
		compatibilityDatesGeneration++;
		this.caseId = caseId;
		this.partiesLoadedOnce = false;
		this.caseTasksLoadedOnce = false;
		this.caseTasksStale = true;
		resetCaseCalendarState();
		resetCaseDatesState();
		resetCaseLinksState();
		resetMaterialRequestsState();
		resetOverviewPrimaryLinkState();
		this.caseUpdatesLoadedOnce = false;
		this.caseUpdatesStale = true;
		this.caseUpdatesLoading = false;
		this.caseTaskActivityEvents = List.of();
		renderCaseTaskActivity(List.of());
		this.caseDao = caseDao;
		this.caseDetailService = caseDetailService;
		this.caseTaskService = caseTaskService;
		this.calendarService = calendarService;
		this.calendarFeedDao = calendarFeedDao;
		this.caseService = caseService;
		this.caseDateOccurrenceEditorLauncher = caseService == null ? null : new CaseDateOccurrenceEditorLauncher(caseService, caseDateExecutor,
				() -> new CaseDateOccurrenceEditorLauncher.Context(appState == null || appState.getShaleClientId() == null ? 0 : appState.getShaleClientId(), appState == null || appState.getUserId() == null ? 0 : appState.getUserId(), this.caseId == null ? 0 : this.caseId, caseDatesTabPane != null && caseDatesTabPane.getScene() != null),
				this::caseDatesOwner, result -> { long savedCaseId = result.context().caseId(); refreshCaseDateViewsAfterLocalMutation(savedCaseId, isMigratedCaseDateSystemKey(result.date().typeSystemKey())); if (runtimeBridge != null) runtimeBridge.publishCaseDatesChanged(savedCaseId, result.context().tenantId(), result.context().actorId(), result.removed() ? LiveUpdateEvents.CHANGE_REMOVED : LiveUpdateEvents.CHANGE_UPDATED); loadCaseDatesAsync(); },
				this::showCaseDatesMessage, open -> { caseDateEditorOpen = open; if (!open) applyDeferredCaseDatesRefresh(); }, id -> this.onOpenCase.accept(id));
		this.organizationDao = organizationDao;
		this.contactDao = contactDao;
		this.appState = appState;
		this.runtimeBridge = runtimeBridge;
		this.caseDocumentService = (caseDao == null || caseSummaryDao == null || contactDao == null) ? null : new CaseDocumentService(caseDao, caseSummaryDao, contactDao);
		this.caseDocumentExportService = this.caseDocumentService == null ? null : new CaseDocumentExportService(this.caseDocumentService);
		this.onCaseDeleted = onCaseDeleted;
		this.phiReadAuditService = phiReadAuditService;
		this.pageLoadStartNanos = PerfLog.start();
		PerfLog.log("NAV", "start", "page=case_view caseId=" + caseId);
		PerfLog.log("CTRL", "start", "controller=CaseController page=case_view caseId=" + caseId);
		refreshHeader();
	}

	public void setMaterialRequestService(MaterialRequestServicePort materialRequestService) {
		this.materialRequestService = materialRequestService;
		caseMaterialRequestsTabController.init(materialRequestService, caseTaskService, caseService, appState, caseDao, contactDao, organizationDao, () -> caseId == null ? 0L : caseId.longValue(), this::caseRequestsOwner);
		caseMaterialRequestsTabController.setEntityNavigation(onOpenContact, onOpenOrganization, onOpenUser);
	}

	public void setOnOpenUser(Consumer<Integer> onOpenUser) {
		this.onOpenUser = onOpenUser;
		this.userCardFactory = new UserCardFactory(onOpenUser);
		caseMaterialRequestsTabController.setEntityNavigation(onOpenContact, onOpenOrganization, onOpenUser);
	}

	public void setOnOpenStatus(Consumer<Integer> onOpenStatus) {
		this.onOpenStatus = onOpenStatus;
		this.statusCardFactory = new StatusCardFactory(onOpenStatus);
	}

	public void setOnOpenContact(Consumer<Integer> onOpenContact) {
		this.onOpenContact = onOpenContact;
		this.contactCardFactory = new ContactCardFactory(onOpenContact);
		caseMaterialRequestsTabController.setEntityNavigation(onOpenContact, onOpenOrganization, onOpenUser);
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
		this.initialSectionName = resolveAvailableSection(resolved);
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
		caseMaterialRequestsTabController.setEntityNavigation(onOpenContact, onOpenOrganization, onOpenUser);
	}

	private TaskCardFactory buildTaskCardFactory(Consumer<Long> openTaskHandler) {
		Consumer<Long> resolvedOpenHandler = openTaskHandler == null ? taskId ->
		{
		} : openTaskHandler;
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
		setupCaseSectionNavigationHeight();
		setupRelatedEntitiesLayout();
		wireEditButtons();
		wireDetailsEditButtons();
		installDetailsInlineEditButtons();
		wireDetailsReadOnlyAutoSizing();
		setEditMode(false);
		detailsEditor.setEditMode(false);
		clearError();
		wireLiveRefreshLifecycle();

		if (changeResponsibleAttorneyButton != null)
			changeResponsibleAttorneyButton.setOnAction(e -> onEditResponsibleAttorneyField());
		if (changePrimaryLegalAssistantButton != null)
			changePrimaryLegalAssistantButton.setOnAction(e -> onEditPrimaryLegalAssistantField());
		if (editCaseNameButton != null)
			editCaseNameButton.setOnAction(e -> onEditCaseNameField());
		if (editCaseNumberButton != null)
			editCaseNumberButton.setOnAction(e -> onEditCaseNumberField());
		if (editDescriptionButton != null)
			editDescriptionButton.setOnAction(e -> onEditDescriptionField());
		if (editIncidentDateButton != null)
			editIncidentDateButton.setOnAction(e -> onEditIncidentDateField());
		if (editDateOfMedicalNegligenceButton != null)
			editDateOfMedicalNegligenceButton.setOnAction(e -> onEditDateOfMedicalNegligenceField());
		if (editSolDateButton != null)
			editSolDateButton.setOnAction(e -> onEditSolDateField());
		if (editTortNoticeDeadlineButton != null)
			editTortNoticeDeadlineButton.setOnAction(e -> onEditTortNoticeDeadlineField());
		if (editPartiesButton != null)
			editPartiesButton.setOnAction(e -> onSectionSelected("Parties", true));
		if (changeStatusButton != null)
			changeStatusButton.setOnAction(e -> onEditStatusField());
		if (changePracticeAreaButton != null)
			changePracticeAreaButton.setOnAction(e -> onEditPracticeAreaField());
		if (detChangeStatusButton != null)
			detChangeStatusButton.setOnAction(e -> onDetailsChangeStatus());
		if (detChangePracticeAreaButton != null)
			detChangePracticeAreaButton.setOnAction(e -> onDetailsChangePracticeArea());
		if (btnEditTeam != null)
			btnEditTeam.setOnAction(e -> onEditTeam());
		if (submitCaseUpdateButton != null)
			submitCaseUpdateButton.setOnAction(e -> onSubmitCaseUpdate());
		if (caseUpdatesSearchField != null) {
			caseUpdatesSearchField.textProperty().addListener((obs, oldText, newText) -> applyCaseUpdateFilter());
		}
		if (deleteCaseButton != null) {
			deleteCaseButton.setOnAction(e -> onDeleteCase());
			setVisibleManaged(deleteCaseButton, false);
		}
		if (addTaskButton != null) {
			addTaskButton.getStyleClass().removeAll(ActionButtonFactory.BASE_STYLE_CLASS, ActionButtonFactory.PRIMARY_STYLE_CLASS);
			ControlStyles.apply(addTaskButton, ControlStyles.Purpose.PRIMARY, ControlStyles.Size.STANDARD);
			addTaskButton.setOnAction(e -> onAddTask());
		}
		if (addCaseLinkButton != null) {
			addCaseLinkButton.getStyleClass().removeAll(ActionButtonFactory.BASE_STYLE_CLASS, ActionButtonFactory.PRIMARY_STYLE_CLASS);
			ControlStyles.apply(addCaseLinkButton, ControlStyles.Purpose.PRIMARY, ControlStyles.Size.STANDARD);
			addCaseLinkButton.setOnAction(e -> onAddCaseLink());
		}
		configureCaseCalendarControls();
		if (generateSummaryHtmlMenuItem != null)
			generateSummaryHtmlMenuItem.setOnAction(e -> onGenerateSummaryHtml());
		if (generateSummaryPdfMenuItem != null)
			generateSummaryPdfMenuItem.setOnAction(e -> onGenerateSummaryPdf());
		if (generateSummaryMenuButton != null && generateSummaryHtmlMenuItem == null && generateSummaryPdfMenuItem == null)
			generateSummaryMenuButton.setOnAction(e -> onGenerateSummaryHtml());
		if (caseTasksSortChoice != null) {
			ControlStyles.formControl(caseTasksSortChoice);
			caseTasksSortChoice.getItems().setAll(
					CASE_TASKS_SORT_DUE_ASC,
					CASE_TASKS_SORT_DUE_DESC,
					CASE_TASKS_SORT_PRIORITY_ASC,
					CASE_TASKS_SORT_PRIORITY_DESC);
			caseTasksSortChoice.getSelectionModel().select(CASE_TASKS_SORT_DUE_ASC);
			caseTasksSortChoice.getSelectionModel().selectedItemProperty()
					.addListener((obs, oldV, newV) -> refreshCaseTasks());
		}
		if (caseTasksShowCompletedButton != null) {
			caseTasksShowCompletedButton.getStyleClass().removeAll(ActionButtonFactory.BASE_STYLE_CLASS, ActionButtonFactory.NEUTRAL_STYLE_CLASS);
			ControlStyles.apply(caseTasksShowCompletedButton, ControlStyles.Purpose.SECONDARY, ControlStyles.Size.STANDARD);
			caseTasksShowCompletedButton.setOnAction(e ->
			{
				showCompletedCaseTasks = !showCompletedCaseTasks;
				updateCaseTaskCompletionToggleLabel();
				renderTasksSection();
			});
			updateCaseTaskCompletionToggleLabel();
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

	private void ensureStyleClass(javafx.scene.Node node, String styleClass) {
		if (node == null || styleClass == null || styleClass.isBlank()) {
			return;
		}
		if (!node.getStyleClass().contains(styleClass)) {
			node.getStyleClass().add(styleClass);
		}
	}

	private void configureAutoGrowingDetailArea(TextArea area, double minHeight) {
		if (area == null) {
			return;
		}
		ensureStyleClass(area, "detail-large-text");
		Text measurer = new Text();
		measurer.fontProperty().bind(area.fontProperty());
		Runnable recomputeHeight = () ->
		{
			String text = area.getText();
			measurer.setText((text == null || text.isEmpty()) ? " " : text);
			Insets insets = area.getInsets();
			double width = area.getWidth() > 0 ? area.getWidth() : area.prefWidth(-1);
			double contentWidth = Math.max(0, width - insets.getLeft() - insets.getRight() - 18);
			measurer.setWrappingWidth(contentWidth);
			double targetHeight = Math.max(
					minHeight,
					Math.ceil(measurer.getLayoutBounds().getHeight() + insets.getTop() + insets.getBottom() + 22));
			area.setMinHeight(targetHeight);
			area.setPrefHeight(targetHeight);
			area.setMaxHeight(targetHeight);
		};
		area.textProperty().addListener((obs, oldV, newV) -> recomputeHeight.run());
		area.widthProperty().addListener((obs, oldV, newV) -> recomputeHeight.run());
		area.fontProperty().addListener((obs, oldV, newV) -> recomputeHeight.run());
		Platform.runLater(recomputeHeight);
	}

	private void wireLiveRefreshLifecycle() {
		if (caseRootPane == null) {
			return;
		}
		caseRootPane.sceneProperty().addListener((obs, oldScene, newScene) ->
		{
			if (newScene == null) {
				unsubscribeLiveCaseUpdates();
			} else {
				subscribeLiveCaseUpdates();
			}
		});
		subscribeLiveCaseUpdates();
	}

	private void wireDetailsReadOnlyAutoSizing() {
		if (detDescriptionEditor != null) {
			detDescriptionEditor.textProperty().addListener((obs, oldV, newV) -> autoSizeReadOnlyDetailTextAreas());
			detDescriptionEditor.widthProperty().addListener((obs, oldV, newV) -> autoSizeReadOnlyDetailTextAreas());
		}
		if (detSummaryEditor != null) {
			detSummaryEditor.textProperty().addListener((obs, oldV, newV) -> autoSizeReadOnlyDetailTextAreas());
			detSummaryEditor.widthProperty().addListener((obs, oldV, newV) -> autoSizeReadOnlyDetailTextAreas());
		}
		Platform.runLater(this::autoSizeReadOnlyDetailTextAreas);
	}

	private void autoSizeReadOnlyDetailTextAreas() {
		autoSizeReadOnlyDetailTextArea(detDescriptionEditor, 2);
		autoSizeReadOnlyDetailTextArea(detSummaryEditor, 2);
	}

	private void autoSizeReadOnlyDetailTextArea(TextArea area, int minimumLines) {
		if (area == null || detailsEditMode) {
			return;
		}
		Text measurer = new Text();
		measurer.setFont(area.getFont());
		measurer.setText((area.getText() == null || area.getText().isEmpty()) ? " " : area.getText());
		Insets insets = area.getInsets();
		double width = area.getWidth() > 0 ? area.getWidth() : area.prefWidth(-1);
		double contentWidth = Math.max(0, width - insets.getLeft() - insets.getRight() - 18);
		measurer.setWrappingWidth(contentWidth);
		double lineHeight = Math.max(16, area.getFont().getSize() + 4);
		double minimumHeight = (lineHeight * minimumLines) + insets.getTop() + insets.getBottom() + 10;
		double computedHeight = Math.ceil(measurer.getLayoutBounds().getHeight() + insets.getTop() + insets.getBottom() + 10);
		double targetHeight = Math.max(minimumHeight, computedHeight);
		area.setMinHeight(targetHeight);
		area.setPrefHeight(targetHeight);
		area.setMaxHeight(targetHeight);
	}

	private void resetAutoSizedDetailTextArea(TextArea area) {
		if (area == null) {
			return;
		}
		area.setMinHeight(Region.USE_COMPUTED_SIZE);
		area.setPrefHeight(Region.USE_COMPUTED_SIZE);
		area.setMaxHeight(Region.USE_COMPUTED_SIZE);
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
		if (appState.getUserId() == null || appState.getUserId() <= 0) {
			showError("Unable to resolve authenticated user for summary generation.");
			return;
		}
		if (caseDocumentExportService == null) {
			showError("Summary generation service is unavailable.");
			return;
		}

		final int tenantId = appState.getShaleClientId();
		final int activeCaseId = caseId;
		final CaseDocumentGenerationRequest request = new CaseDocumentGenerationRequest(
				tenantId, appState.getUserId(), activeCaseId, CaseDocumentType.CASE_SUMMARY, format);
		final long generation = ++documentGeneration;
		final String loadingText = format == CaseDocumentFormat.PDF ? "Generating PDF summary..." : "Generating HTML summary...";
		setSummaryGenerationBusy(true, loadingText);

		Task<GeneratedDocument> task = new Task<>() {
			@Override
			protected GeneratedDocument call() throws Exception {
				System.out.println("[Document] Generating " + format + " CASE_SUMMARY for caseId=" + activeCaseId + " shaleClientId=" + tenantId);
				return caseDocumentExportService.exportCaseSummary(request);
			}
		};

		task.setOnSucceeded(event ->
		{
			if (!isCurrentDocumentRequest(request, generation)) return;
			setSummaryGenerationBusy(false, null);
			GeneratedDocument generated = task.getValue();
			try {
				boolean opened = runtimeBridge != null && runtimeBridge.openPath(generated.path());
				if (!opened) {
					throw new IllegalStateException("Unable to open generated summary preview.");
				}
				System.out.println("[Document] Generated case summary " + format);
			} catch (Exception ex) {
				System.err.println("[Document] Failed to open generated case summary " + format);
				showSummaryGenerationError("Could not open generated case summary.");
			}
		});

		task.setOnFailed(event ->
		{
			if (!isCurrentDocumentRequest(request, generation)) return;
			setSummaryGenerationBusy(false, null);
			System.err.println("[Document] Failed to generate case summary " + format);
			showSummaryGenerationError("Could not generate case summary " + format.name().toLowerCase() + ". Please try again.");
		});

		Thread worker = new Thread(task, "case-summary-export-" + format.name().toLowerCase() + "-" + activeCaseId);
		worker.setDaemon(true);
		worker.start();
	}

	private boolean isCurrentDocumentRequest(CaseDocumentGenerationRequest request, long generation) {
		return generation == documentGeneration && caseId != null && caseId == request.caseId()
				&& appState != null && Objects.equals(appState.getShaleClientId(), request.tenantId())
				&& Objects.equals(appState.getUserId(), request.authenticatedUserId());
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

		Runnable refreshWrapLength = () ->
		{
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
		if (detailsEditButton != null) {
			detailsEditButton.setOnAction(e -> detailsEditor.beginEdit());
			setVisibleManaged(detailsEditButton, false);
		}
		if (detailsSaveButton != null)
			detailsSaveButton.setOnAction(e -> onSaveDetails());
		if (detailsCancelButton != null)
			detailsCancelButton.setOnAction(e -> detailsEditor.cancelEdit());
	}

	private void installDetailsInlineEditButtons() {
		if (detailsPane == null)
			return;
		installDetailsInlineEditButtons(detailsPane);
	}

	private void installDetailsInlineEditButtons(javafx.scene.Parent parent) {
		for (Node child : parent.getChildrenUnmodifiable()) {
			if (child instanceof GridPane grid) {
				prepareDetailsGridForInlineEdits(grid);
			} else if (child instanceof javafx.scene.Parent nested) {
				installDetailsInlineEditButtons(nested);
			}
		}
	}

	private void prepareDetailsGridForInlineEdits(GridPane grid) {
		if (grid.getProperties().putIfAbsent("detailsInlineEditButtonsInstalled", Boolean.TRUE) != null)
			return;
		grid.getStyleClass().add("case-overview-rows");
		if (grid.getColumnConstraints().size() < 3) {
			ColumnConstraints actions = new ColumnConstraints();
			actions.setMinWidth(36);
			actions.setPrefWidth(36);
			actions.setMaxWidth(36);
			actions.setHalignment(javafx.geometry.HPos.RIGHT);
			grid.getColumnConstraints().add(actions);
		}
		Map<Integer, Label> labelsByRow = new LinkedHashMap<>();
		Map<Integer, Node> editableByRow = new LinkedHashMap<>();
		for (Node node : List.copyOf(grid.getChildren())) {
			Integer row = GridPane.getRowIndex(node);
			int rowIndex = row == null ? 0 : row;
			Integer col = GridPane.getColumnIndex(node);
			int colIndex = col == null ? 0 : col;
			if (node instanceof Label label && colIndex == 0) {
				label.getStyleClass().add("case-overview-row-label");
				labelsByRow.put(rowIndex, label);
			} else if (node instanceof Label label && colIndex == 1) {
				label.getStyleClass().add("case-overview-row-value");
			} else if (colIndex == 1 && isDetailsEditorNode(node)) {
				editableByRow.putIfAbsent(rowIndex, node);
			}
		}
		for (Map.Entry<Integer, Node> entry : editableByRow.entrySet()) {
			int row = entry.getKey();
			Node editor = entry.getValue();
			Button edit = createDetailsInlineEditButton(labelsByRow.get(row), editor);
			grid.add(edit, 2, row);
		}
	}

	private boolean isDetailsEditorNode(Node node) {
		return node instanceof TextInputControl
				|| node instanceof DatePicker
				|| node instanceof CheckBox
				|| node == detCaseStatusEditorRow
				|| node == detPracticeAreaEditorRow;
	}

	private Button createDetailsInlineEditButton(Label label, Node editor) {
		Button edit = new Button("✎");
		edit.getStyleClass().add("case-overview-edit-button");
		String field = label == null ? "field" : safeText(label.getText()).replace(":", "").trim();
		edit.setTooltip(new Tooltip("Edit " + (field.isBlank() ? "field" : field)));
		edit.setOnAction(e -> onEditDetailsField(field, editor, edit));
		return edit;
	}

	private void onEditDetailsField(String fieldLabel, Node editor, Button ownerButton) {
		CaseDetailsDraft base = resolveDetailsViewModel();
		if (base == null) {
			showError("Case details are still loading. Please try again.");
			return;
		}
		if (editor == detCaseStatusEditorRow) {
			onEditStatusField();
			return;
		}
		if (editor == detPracticeAreaEditorRow) {
			onEditPracticeAreaField();
			return;
		}
		if (editor == detNameEditor) {
			showDetailsTextFieldDialog("Edit Case Name", "Case Name", base.name, true, ownerButton,
					value -> saveSingleDetailsField(d -> d.name = value));
		} else if (editor == detCaseNumberEditor) {
			showDetailsTextFieldDialog("Edit Case Number", "Case Number", base.caseNumber, false, ownerButton,
					value -> saveSingleDetailsField(d -> d.caseNumber = value));
		} else if (editor == detDescriptionEditor) {
			showDetailsTextAreaDialog("Edit Description", "Description", base.description, ownerButton,
					value -> saveSingleDetailsField(d -> d.description = value));
		} else if (editor == detCallerDateEditor) {
			showDetailsDateDialog("Edit Caller Date", "Caller Date", authoritativeDate(MigratedCaseDateKey.CALLER_DATE), ownerButton,
					value -> saveAuthoritativeDate(MigratedCaseDateKey.CALLER_DATE, value));
		} else if (editor == detCallerTimeEditor) {
			CompatibilityCaseDateState intake = compatibilityDates.isLoaded() ? compatibilityDates.states().get(MigratedCaseDateKey.CALLER_DATE) : null;
			String time = intake == null || intake.startsAt() == null || intake.allDay() ? "" : intake.startsAt().toLocalTime().toString();
			showDetailsTextFieldDialog("Edit Caller Time", "Caller Time", time, false, ownerButton, this::saveAuthoritativeIntakeTime);
		} else if (editor == detAcceptedDateEditor) {
			showDetailsDateDialog("Edit Accepted Date", "Accepted Date", base.acceptedDate, ownerButton,
					value -> saveSingleDetailsField(d -> d.acceptedDate = value));
		} else if (editor == detClosedDateEditor) {
			showDetailsDateDialog("Edit Closed Date", "Closed Date", base.closedDate, ownerButton,
					value -> saveSingleDetailsField(d -> d.closedDate = value));
		} else if (editor == detDeniedDateEditor) {
			showDetailsDateDialog("Edit Denied Date", "Denied Date", base.deniedDate, ownerButton,
					value -> saveSingleDetailsField(d -> d.deniedDate = value));
		} else if (editor == detDateOfMedicalNegligenceEditor) {
			showDetailsDateDialog("Edit Date of Medical Negligence", "Date of Medical Negligence", authoritativeDate(MigratedCaseDateKey.DATE_OF_MEDICAL_NEGLIGENCE), ownerButton,
					value -> saveAuthoritativeDate(MigratedCaseDateKey.DATE_OF_MEDICAL_NEGLIGENCE, value));
		} else if (editor == detDateMedicalNegligenceWasDiscoveredEditor) {
			showDetailsDateDialog("Edit Date Medical Negligence Was Discovered", "Date Medical Negligence Was Discovered", authoritativeDate(MigratedCaseDateKey.DATE_MEDICAL_NEGLIGENCE_DISCOVERED), ownerButton,
					value -> saveAuthoritativeDate(MigratedCaseDateKey.DATE_MEDICAL_NEGLIGENCE_DISCOVERED, value));
		} else if (editor == detDateOfInjuryEditor) {
			showDetailsDateDialog("Edit Date of Injury", "Date of Injury", authoritativeDate(MigratedCaseDateKey.DATE_OF_INJURY), ownerButton,
					value -> saveAuthoritativeDate(MigratedCaseDateKey.DATE_OF_INJURY, value));
		} else if (editor == detStatuteOfLimitationsEditor) {
			showDetailsNullableDateDialog("Edit Statute of Limitations", "Statute of Limitations", authoritativeDate(MigratedCaseDateKey.STATUTE_OF_LIMITATIONS), ownerButton,
					value -> saveAuthoritativeDate(MigratedCaseDateKey.STATUTE_OF_LIMITATIONS, value));
		} else if (editor == detTortNoticeDeadlineEditor) {
			showDetailsNullableDateDialog("Edit Tort Notice Deadline", "Tort Notice Deadline", authoritativeDate(MigratedCaseDateKey.TORT_NOTICE_DEADLINE), ownerButton,
					value -> saveAuthoritativeDate(MigratedCaseDateKey.TORT_NOTICE_DEADLINE, value));
		} else if (editor == detDiscoveryDeadlineEditor) {
			showDetailsDateDialog("Edit Discovery Deadline", "Discovery Deadline", authoritativeDate(MigratedCaseDateKey.DISCOVERY_DEADLINE), ownerButton,
					value -> saveAuthoritativeDate(MigratedCaseDateKey.DISCOVERY_DEADLINE, value));
		} else if (editor == detDateFeeAgreementSignedEditor) {
			showDetailsDateDialog("Edit Date Fee Agreement Signed", "Date Fee Agreement Signed", authoritativeDate(MigratedCaseDateKey.DATE_FEE_AGREEMENT_SIGNED), ownerButton,
					value -> saveAuthoritativeDate(MigratedCaseDateKey.DATE_FEE_AGREEMENT_SIGNED, value));
		} else if (editor == detDateNonEngagementLetterSentEditor) {
			showDetailsDateDialog("Edit Date Non-Engagement Letter Sent", "Date Non-Engagement Letter Sent", authoritativeDate(MigratedCaseDateKey.DATE_NON_ENGAGEMENT_LETTER_SENT), ownerButton,
					value -> saveAuthoritativeDate(MigratedCaseDateKey.DATE_NON_ENGAGEMENT_LETTER_SENT, value));
		} else if (editor instanceof CheckBox checkBox) {
			showDetailsBooleanDialog("Edit " + fieldLabel, fieldLabel, checkBox.isSelected(), ownerButton,
					value -> saveSingleDetailsBooleanField(editor, value));
		} else if (editor instanceof TextArea) {
			showDetailsTextAreaDialog("Edit " + fieldLabel, fieldLabel, textFromDetailsDraft(base, editor), ownerButton,
					value -> saveSingleDetailsTextField(editor, value));
		} else if (editor instanceof TextInputControl) {
			showDetailsTextFieldDialog("Edit " + fieldLabel, fieldLabel, textFromDetailsDraft(base, editor), false, ownerButton,
					value -> saveSingleDetailsTextField(editor, value));
		} else if (editor instanceof DatePicker) {
			showDetailsDateDialog("Edit " + fieldLabel, fieldLabel, null, ownerButton,
					value -> saveSingleDetailsDateField(editor, value));
		}
	}

	// ----------------------------
	// Sections / placeholders
	// ----------------------------

	private void setupSections() {
		if (sectionTabsBar == null)
			return;

		sectionTabs.clear();
		sectionTabs.putAll(AppSectionTabs.buildTabs(
				sectionTabsBar,
				SECTIONS.stream()
						.map(section -> new AppSectionTabs.TabSpec<>(section, section))
						.toList(),
				section -> onSectionSelected(section, true)));

		onSectionSelected(initialSectionName, false);
	}

	private void setupCaseSectionNavigationHeight() {
		if (caseSectionNavigationScrollPane == null || sectionTabsBar == null)
			return;

		Runnable installSizing = () -> {
			Node horizontalBarNode = caseSectionNavigationScrollPane.lookup(".scroll-bar:horizontal");
			if (!(horizontalBarNode instanceof ScrollBar horizontalBar)
					|| caseSectionNavigationScrollPane.prefHeightProperty().isBound())
				return;

			caseSectionNavigationScrollPane.prefHeightProperty().bind(Bindings.createDoubleBinding(
					() -> sectionTabsBar.prefHeight(-1)
							+ caseSectionNavigationScrollPane.getInsets().getTop()
							+ caseSectionNavigationScrollPane.getInsets().getBottom()
							+ (horizontalBar.isVisible() ? horizontalBar.prefHeight(-1) : 0),
					sectionTabsBar.layoutBoundsProperty(),
					caseSectionNavigationScrollPane.insetsProperty(),
					horizontalBar.visibleProperty()));
		};

		caseSectionNavigationScrollPane.skinProperty().addListener((observable, oldSkin, newSkin) -> installSizing.run());
		if (caseSectionNavigationScrollPane.getSkin() != null)
			installSizing.run();
	}

	private void refreshHeader() {
		if (caseTitleLabel == null || caseId == null)
			return;

		caseTitleLabel.setText("Case #" + caseId + " (Placeholder)");
		refreshCaseMetadata(null);
		refreshIntakeTakenBy(null, null);
		renderPrimaryStatusMini(null, "—", null);
		renderResponsibleAttorneyMini(null, "—", null);
		renderPracticeAreaMini(null, "—", null);

		if (lastUpdatedLabel != null)
			lastUpdatedLabel.setText("Last updated: —");
	}

	private void refreshCaseMetadata(String caseNumberRaw) {
		if (caseMetadataLabel == null) {
			return;
		}
		String safeNumber = safeText(caseNumberRaw).trim();
		String idText = caseId == null ? "Case #—" : "Case #" + caseId;
		caseMetadataLabel.setText(safeNumber.isBlank() ? idText : idText + " • " + safeNumber);
	}

	private void refreshIntakeTakenBy(Integer userId, String displayName) {
		if (intakeTakenByUserHost == null)
			return;
		intakeTakenByUserHost.getChildren().setAll(createHeaderUserMini(userId, displayName, null));
	}

	private void refreshOverviewPlaceholders() {
		if (ovCaseNameValue != null)
			ovCaseNameValue.setText(caseId == null ? "—" : "Case #" + caseId + " (Placeholder name)");
		if (ovCaseNumberValue != null)
			ovCaseNumberValue.setText(caseId == null ? "—" : String.valueOf(caseId));

		renderResponsibleAttorneyMini(null, "—", null);

		if (ovCaseStatusValue != null)
			ovCaseStatusValue.setText("—");

		renderOverviewPartiesSection();
		renderPracticeAreaMini(null, "—", null);

		if (ovTeamValue != null)
			ovTeamValue.setText("—");

		if (ovIntakeDateValue != null)
			ovIntakeDateValue.setText(formatDate(null));
		if (ovIncidentDateValue != null)
			ovIncidentDateValue.setText(formatDate(null));
		if (ovIncidentDateEditor != null)
			ovIncidentDateEditor.setValue(null);
		if (ovDateOfMedicalNegligenceValue != null)
			ovDateOfMedicalNegligenceValue.setText(formatDate(null));
		if (ovDateOfMedicalNegligenceEditor != null)
			ovDateOfMedicalNegligenceEditor.setValue(null);
		if (ovSolDateValue != null)
			ovSolDateValue.setText(formatDate(null));
		if (ovSolDateEditor != null)
			ovSolDateEditor.setValue(null);
		if (ovTortNoticeDeadlineValue != null)
			ovTortNoticeDeadlineValue.setText(formatDate(null));
		if (ovTortNoticeDeadlineEditor != null)
			ovTortNoticeDeadlineEditor.setValue(null);

		if (ovDescriptionValue != null)
			ovDescriptionValue.setText("");
		renderCaseUpdates(List.of());
	}

	// ----------------------------
	// Section navigation
	// ----------------------------

	private void onSectionSelected(String sectionName, boolean userInitiated) {
		String selectedSection = resolveAvailableSection(sectionName);

		activeSectionName = selectedSection;
		setActiveSectionButton(selectedSection);
		switch (selectedSection) {
		case "Overview" -> showOverview();
		case "Parties" -> showParties();
		case "Tasks" -> showTasksTab();
		case "Calendar" -> showCalendarTab();
		case "Dates" -> showCaseDatesTab();
		case "Requests" -> showRequestsTab();
		case "Links" -> showLinksTab();
		case "Timeline" -> showTimeline();
		case "Details" -> showDetails();
		default -> showOverview();
		}

		if (userInitiated && onSectionNavigation != null) {
			onSectionNavigation.accept(toSectionKey(selectedSection));
		}
	}

	private static String resolveAvailableSection(String requestedSection) {
		return requestedSection != null && SECTIONS.contains(requestedSection) ? requestedSection : "Overview";
	}

	private static String toSectionKey(String sectionName) {
		if (sectionName == null) {
			return null;
		}
		return switch (sectionName) {
		case "Overview" -> "OVERVIEW";
		case "Tasks" -> "TASKS";
		case "Calendar" -> "CALENDAR";
		case "Dates" -> "DATES";
		case "Links" -> "LINKS";
		case "Timeline" -> "TIMELINE";
		case "Details" -> "DETAILS";
		case "Parties" -> "PARTIES";
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
		case "CALENDAR" -> "Calendar";
		case "DATES" -> "Dates";
		case "LINKS" -> "Links";
		case "TIMELINE" -> "Timeline";
		case "DETAILS" -> "Details";
		case "PARTIES" -> "Parties";
		case "REQUESTS" -> "Requests";
		default -> null;
		};
	}

	private void setActiveSectionButton(String activeSection) {
		Button activeButton = null;
		for (Map.Entry<String, Button> entry : sectionTabs.entrySet()) {
			if (Objects.equals(entry.getKey(), activeSection)) {
				activeButton = entry.getValue();
				break;
			}
		}

		AppSectionTabs.setActive(activeButton, sectionTabs.values());
	}

	private void loadStatusTimelineAsync() {
		if (caseDao == null || caseId == null || statusTimelineHost == null) {
			renderStatusTimeline(List.of());
			return;
		}
		final long activeCaseId = caseId.longValue();
		new Thread(() ->
		{
			try {
				List<CaseStatusHistoryDto> history = caseDao.listCaseStatusHistory(activeCaseId);
				runOnFx(() ->
				{
					if (caseId != null && caseId.longValue() == activeCaseId) {
						renderStatusTimeline(history);
					}
				});
			} catch (Exception ex) {
				runOnFx(() -> renderStatusTimeline(List.of()));
			}
		}, "case-status-timeline-" + activeCaseId).start();
	}

	private void renderStatusTimeline(List<CaseStatusHistoryDto> history) {
		if (statusTimelineHost == null) {
			return;
		}
		statusTimelineHost.getChildren().clear();
		List<CaseStatusHistoryDto> safeHistory = history == null ? List.of() : history;
		if (safeHistory.isEmpty()) {
			Label empty = new Label("No status history");
			empty.setStyle("-fx-opacity: 0.55; -fx-font-size: 11px;");
			statusTimelineHost.getChildren().add(empty);
			return;
		}

		List<StatusTimeline.Item> items = safeHistory.stream().map(item -> {
			String name = safeText(item.statusName()).isBlank() ? "Status #" + item.statusId() : safeText(item.statusName());
			StatusTimeline.State state = item.current() ? StatusTimeline.State.CURRENT
					: item.endDate() != null ? StatusTimeline.State.COMPLETED : StatusTimeline.State.FUTURE;
			return new StatusTimeline.Item(Integer.toString(item.statusId()), name, item.color(), state,
					buildStatusTimelineTooltip(item, name));
		}).toList();
		statusTimelineHost.getChildren().add(StatusTimeline.create(items, StatusTimeline.Variant.OVERVIEW));
	}

	private static String buildStatusTimelineTooltip(CaseStatusHistoryDto item, String name) {
		String entered = formatTimelineDateTime(item.effectiveDate());
		String exited = item.endDate() == null ? "Current" : formatTimelineDateTime(item.endDate());
		String duration = "—";
		if (item.effectiveDate() != null) {
			LocalDateTime end = item.endDate() == null ? LocalDateTime.now() : item.endDate();
			duration = Math.max(0, ChronoUnit.DAYS.between(item.effectiveDate().toLocalDate(), end.toLocalDate())) + " days";
		}
		return name + "\nEntered: " + entered + "\nExited: " + exited + "\nDuration: " + duration;
	}

	private static String formatTimelineDateTime(LocalDateTime value) {
		return value == null ? "—" : value.format(DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a"));
	}

	private void configureCaseCalendarControls() {
		setCaseCalendarLayerDefaults();
		List<CheckBox> layerBoxes = List.of(
				caseCalendarEventsLayerCheckBox,
				caseCalendarTasksLayerCheckBox,
				caseCalendarDeadlinesLayerCheckBox,
				caseCalendarCaseDatesLayerCheckBox).stream().filter(Objects::nonNull).toList();
		for (CheckBox box : layerBoxes) {
			box.selectedProperty().addListener((obs, oldValue, newValue) -> {
				updateCaseCalendarSourceFilterFromControls();
				renderCaseCalendarAgenda(false);
			});
		}
		if (caseCalendarNewEventButton != null) {
			ControlStyles.apply(caseCalendarNewEventButton, ControlStyles.Purpose.PRIMARY, ControlStyles.Size.STANDARD);
			caseCalendarNewEventButton.setOnAction(e -> onCaseCalendarNewEvent());
		}
		if (caseCalendarNewTaskButton != null) {
			ControlStyles.apply(caseCalendarNewTaskButton, ControlStyles.Purpose.SECONDARY, ControlStyles.Size.STANDARD);
			caseCalendarNewTaskButton.setOnAction(e -> onAddTask());
		}
		configureCaseDatesControls();
	}

	private void resetCaseCalendarState() {
		caseCalendarLoadedOnce = false;
		caseCalendarStale = true;
		caseCalendarItems = List.of();
		caseCalendarSourceFilter = CalendarFeedSourceFilter.caseCalendarDefaults();
		setCaseCalendarLayerDefaults();
		if (caseCalendarScrollPane != null) caseCalendarScrollPane.setVvalue(0.0);
	}

	private void setCaseCalendarLayerDefaults() {
		if (caseCalendarEventsLayerCheckBox != null) caseCalendarEventsLayerCheckBox.setSelected(true);
		if (caseCalendarTasksLayerCheckBox != null) caseCalendarTasksLayerCheckBox.setSelected(true);
		if (caseCalendarDeadlinesLayerCheckBox != null) caseCalendarDeadlinesLayerCheckBox.setSelected(true);
		if (caseCalendarCaseDatesLayerCheckBox != null) caseCalendarCaseDatesLayerCheckBox.setSelected(true);
		caseCalendarSourceFilter = CalendarFeedSourceFilter.caseCalendarDefaults();
	}

	private void updateCaseCalendarSourceFilterFromControls() {
		EnumSet<CalendarFeedCategory> enabled = EnumSet.noneOf(CalendarFeedCategory.class);
		if (caseCalendarEventsLayerCheckBox == null || caseCalendarEventsLayerCheckBox.isSelected()) enabled.add(CalendarFeedCategory.CALENDAR_EVENTS);
		if (caseCalendarTasksLayerCheckBox == null || caseCalendarTasksLayerCheckBox.isSelected()) enabled.add(CalendarFeedCategory.TASKS);
		if (caseCalendarDeadlinesLayerCheckBox == null || caseCalendarDeadlinesLayerCheckBox.isSelected()) enabled.add(CalendarFeedCategory.CASE_DEADLINES);
		if (caseCalendarCaseDatesLayerCheckBox == null || caseCalendarCaseDatesLayerCheckBox.isSelected()) enabled.add(CalendarFeedCategory.OTHER_CASE_DATES);
		caseCalendarSourceFilter = new CalendarFeedSourceFilter(enabled);
	}

	private void showCalendarTab() {
		attachCaseUpdatesPane(CaseUpdatesPlacement.RIGHT);
		activateCaseSectionRoot(caseCalendarTabPane);
		setPaneVisible(tasksPanel, false);
		if (!caseCalendarLoadedOnce || caseCalendarStale) {
			loadCaseCalendarAsync();
		} else {
			renderCaseCalendarAgenda(false);
		}
		loadCaseUpdatesAsync();
		auditCaseRead("Case.Calendar.Read", "Case.Calendar");
	}

	private void loadCaseCalendarAsync() {
		if (calendarService == null || appState == null || caseId == null) {
			showCaseCalendarMessage("Calendar is unavailable.");
			return;
		}
		Integer tenantId = appState.getShaleClientId();
		if (tenantId == null || tenantId <= 0) {
			showCaseCalendarMessage("Calendar is unavailable because no tenant is selected.");
			return;
		}
		final int activeCaseId = caseId;
		final int generation = ++caseCalendarLoadGeneration;
		LocalDate today = LocalDate.now();
		LocalDateTime start = today.minusMonths(CASE_CALENDAR_PAST_MONTHS).atStartOfDay();
		LocalDateTime end = today.plusMonths(CASE_CALENDAR_UPCOMING_MONTHS).plusDays(1).atStartOfDay();
		showCaseCalendarMessage("Loading calendar…");
		new Thread(() -> {
			try {
				List<CalendarFeedItem> items = calendarService.listCalendarFeedForCase(tenantId, start, end, activeCaseId);
				runOnFx(() -> {
					if (caseId == null || caseId != activeCaseId || generation != caseCalendarLoadGeneration) return;
					caseCalendarItems = items == null ? List.of() : items;
					caseCalendarLoadedOnce = true;
					caseCalendarStale = false;
					renderCaseCalendarAgenda(true);
				});
			} catch (RuntimeException ex) {
				runOnFx(() -> {
					if (generation == caseCalendarLoadGeneration) showCaseCalendarMessage("Failed to load case calendar. Click Calendar again to retry.");
				});
			}
		}, "case-calendar-load-" + activeCaseId).start();
	}

	private void renderCaseCalendarAgenda(boolean resetScroll) {
		if (caseCalendarAgendaBox == null) return;
		caseCalendarAgendaBox.getChildren().clear();
		if (!caseCalendarSourceFilter.hasAnyEnabled()) {
			showCaseCalendarMessage("No case calendar layers selected.");
			return;
		}
		List<CalendarFeedItem> visible = caseCalendarItems.stream().filter(caseCalendarSourceFilter::matches).toList();
		if (visible.isEmpty()) {
			showCaseCalendarMessage("No scheduled events, tasks, or case dates in this range.");
			return;
		}
		setVisibleManaged(caseCalendarStatusLabel, false);
		LocalDate today = LocalDate.now();
		List<CalendarFeedItem> upcoming = visible.stream().filter(i -> !itemDate(i).isBefore(today)).sorted(caseCalendarUpcomingComparator()).toList();
		List<CalendarFeedItem> past = visible.stream().filter(i -> itemDate(i).isBefore(today)).sorted(caseCalendarPastComparator()).toList();
		if (upcoming.isEmpty()) {
			caseCalendarAgendaBox.getChildren().add(sectionMessage("Upcoming", "No upcoming items."));
		} else {
			appendCaseCalendarSection("Upcoming", upcoming, false);
		}
		if (!past.isEmpty()) appendCaseCalendarSection("Past", past, true);
		if (resetScroll && caseCalendarScrollPane != null) caseCalendarScrollPane.setVvalue(0.0);
	}

	private void appendCaseCalendarSection(String title, List<CalendarFeedItem> items, boolean past) {
		Label section = new Label(title);
		section.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
		caseCalendarAgendaBox.getChildren().add(section);
		LocalDate current = null;
		for (CalendarFeedItem item : items) {
			LocalDate date = itemDate(item);
			if (!Objects.equals(current, date)) {
				current = date;
				Label heading = new Label(formatCaseCalendarDateHeading(date));
				heading.setStyle("-fx-font-weight: 700; -fx-opacity: 0.78;");
				caseCalendarAgendaBox.getChildren().add(heading);
			}
			caseCalendarAgendaBox.getChildren().add(createCaseCalendarRow(item, past));
		}
	}

	private Node createCaseCalendarRow(CalendarFeedItem item, boolean past) {
		Label time = new Label(item.allDay() ? "All day" : item.startsAt().format(DateTimeFormatter.ofPattern("h:mm a")));
		time.setMinWidth(72);
		Label title = new Label(safeText(item.title()).replaceFirst("\\s+—\\s+.*$", ""));
		title.setWrapText(true);
		title.setStyle("-fx-font-weight: 700;");
		Label meta = new Label(CalendarFeedCategory.classify(item).name().replace('_', ' ') + " • " + safeText(item.displayTypeName()));
		meta.setStyle("-fx-opacity: 0.68; -fx-font-size: 11px;");
		VBox text = new VBox(2, title, meta);
		HBox row = new HBox(10, time, text);
		row.setAlignment(Pos.CENTER_LEFT);
		row.setPadding(new Insets(8, 10, 8, 10));
		row.setStyle("-fx-background-color: rgba(255,255,255,0.86); -fx-background-radius: 10; -fx-border-color: rgba(31,41,55,0.12); -fx-border-radius: 10;" + (past ? " -fx-opacity: 0.78;" : ""));
		CalendarEventCardFactory.applyCalendarItemTooltip(row, item);
		configureCaseCalendarClick(row, item);
		return row;
	}

	private void configureCaseCalendarClick(Node row, CalendarFeedItem item) {
		CalendarFeedClickTarget target = CalendarFeedClickTarget.resolve(item);
		if (!target.actionable()) return;
		row.setCursor(Cursor.HAND);
		Runnable activate = () -> {
			switch (target.kind()) {
				case CALENDAR_EVENT -> openCaseCalendarEventEditor(Math.toIntExact(target.id()));
				case TASK -> openTask(target.id());
				case CASE -> AppDialogs.showInfo(caseCalendarOwner(), "Case Date", "This is a projected case date from the current case. Edit it from Overview or Details.");
				case CASE_DATES -> openAuthoritativeCaseDate(target.id());
				case NONE -> { }
			}
		};
		row.setFocusTraversable(true);
		row.setOnMouseClicked(e -> { if (e.getButton() == MouseButton.PRIMARY && e.isStillSincePress()) { activate.run(); e.consume(); } });
		row.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.SPACE) { activate.run(); e.consume(); } });
	}

	private Comparator<CalendarFeedItem> caseCalendarUpcomingComparator() {
		return Comparator.comparing((CalendarFeedItem i) -> itemDate(i))
				.thenComparing(i -> !i.allDay())
				.thenComparing(CalendarFeedItem::startsAt, Comparator.nullsLast(Comparator.naturalOrder()))
				.thenComparing(i -> safeText(i.key()));
	}

	private Comparator<CalendarFeedItem> caseCalendarPastComparator() {
		return Comparator.comparing((CalendarFeedItem i) -> itemDate(i), Comparator.reverseOrder())
				.thenComparing(i -> !i.allDay())
				.thenComparing(CalendarFeedItem::startsAt, Comparator.nullsLast(Comparator.naturalOrder()))
				.thenComparing(i -> safeText(i.key()));
	}

	private LocalDate itemDate(CalendarFeedItem item) {
		return item == null || item.startsAt() == null ? LocalDate.MAX : item.startsAt().toLocalDate();
	}

	private String formatCaseCalendarDateHeading(LocalDate date) {
		LocalDate today = LocalDate.now();
		if (date.equals(today)) return "Today — " + date.format(CASE_CALENDAR_DATE_FORMAT);
		if (date.equals(today.plusDays(1))) return "Tomorrow — " + date.format(CASE_CALENDAR_DATE_FORMAT);
		return date.getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault()) + " — " + date.format(CASE_CALENDAR_DATE_FORMAT);
	}

	private Node sectionMessage(String title, String message) {
		VBox box = new VBox(4);
		Label heading = new Label(title);
		heading.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
		Label body = new Label(message);
		body.setStyle("-fx-opacity: 0.72;");
		box.getChildren().addAll(heading, body);
		return box;
	}

	private void showCaseCalendarMessage(String message) {
		if (caseCalendarAgendaBox != null) caseCalendarAgendaBox.getChildren().clear();
		if (caseCalendarStatusLabel != null) {
			caseCalendarStatusLabel.setText(message);
			setVisibleManaged(caseCalendarStatusLabel, true);
		}
	}



	private void configureCaseDatesControls() {
		if (addCaseDateButton != null) { ControlStyles.apply(addCaseDateButton, ControlStyles.Purpose.PRIMARY, ControlStyles.Size.STANDARD); addCaseDateButton.setAccessibleText("Add case date"); addCaseDateButton.setOnAction(e -> openCaseDateDialog(null)); }
		if (refreshCaseDatesButton != null) { ControlStyles.apply(refreshCaseDatesButton, ControlStyles.Purpose.SECONDARY, ControlStyles.Size.STANDARD); refreshCaseDatesButton.setAccessibleText("Refresh case dates"); refreshCaseDatesButton.setOnAction(e -> loadCaseDatesAsync()); }
		if (showRemovedCaseDatesButton != null) { ControlStyles.apply(showRemovedCaseDatesButton, ControlStyles.Purpose.SECONDARY, ControlStyles.Size.SMALL); showRemovedCaseDatesButton.setAccessibleText("Show removed case dates"); showRemovedCaseDatesButton.setOnAction(e -> { showRemovedCaseDates = !showRemovedCaseDates; updateRemovedCaseDatesVisibility(); if (showRemovedCaseDates) loadCaseDatesAsync(); }); }
	}

	private void resetCaseDatesState() {
		caseDatesLoadedOnce = false; caseDatesStale = true; caseDates = List.of(); removedCaseDates = List.of(); effectiveCaseDateTypes = List.of(); caseDatesLoadGeneration++;
		if (caseDatesCardsBox != null) caseDatesCardsBox.getChildren().clear();
		if (removedCaseDatesCardsBox != null) removedCaseDatesCardsBox.getChildren().clear();
		showRemovedCaseDates = false; updateRemovedCaseDatesVisibility();
	}

	private void showCaseDatesTab() {
		attachCaseUpdatesPane(CaseUpdatesPlacement.RIGHT);
		activateCaseSectionRoot(caseDatesTabPane);
		setPaneVisible(tasksPanel, false);
		if (!caseDatesLoadedOnce || caseDatesStale) loadCaseDatesAsync(); else renderCaseDates(null);
		loadCaseUpdatesAsync();
	}

	public void openAuthoritativeCaseDate(long caseDateId) {
		showCaseDatesTab();
		if (caseDateOccurrenceEditorLauncher != null && caseId != null) caseDateOccurrenceEditorLauncher.open(caseId, caseDateId);
	}

	private void loadCaseDatesAsync() {
		if (caseService == null || appState == null || caseId == null) { showCaseDatesMessage("Case dates are unavailable."); return; }
		Integer tenantId = appState.getShaleClientId(); Integer actorId = appState.getUserId();
		if (tenantId == null || tenantId <= 0 || actorId == null || actorId <= 0) { showCaseDatesMessage("Case dates are unavailable because no active user is selected."); return; }
		final int activeCaseId = caseId; final int generation = ++caseDatesLoadGeneration;
		showCaseDatesMessage("Loading case dates…");
		caseDateExecutor.submit(() -> {
			long started = System.nanoTime();
			String operation = showRemovedCaseDates ? "caseDates.loadActiveAndRemoved" : "caseDates.loadActive";
			try {
				List<EffectiveCaseDateTypeDto> types = caseService.listEffectiveCaseDateTypes(tenantId, actorId);
				List<CaseDateDto> active = caseService.listCaseDatesForCase(activeCaseId, tenantId, actorId);
				List<CaseDateDto> removed = showRemovedCaseDates ? caseService.listDeletedCaseDatesForCase(activeCaseId, tenantId, actorId) : List.of();
				Platform.runLater(() -> {
					try {
						if (!isCaseDatesCurrent(activeCaseId, generation)) return;
						effectiveCaseDateTypes = types == null ? List.of() : List.copyOf(types);
						caseDates = sortCaseDates(active); removedCaseDates = sortCaseDates(removed); caseDatesLoadedOnce = true; caseDatesStale = false; renderCaseDates(null);
					} catch (RuntimeException uiEx) {
						logCaseDatesLoadFailure(operation + ".render", tenantId, actorId, activeCaseId, generation, started, uiEx);
						if (isCaseDatesCurrent(activeCaseId, generation)) renderCaseDatesFailure();
					}
				});
			} catch (Throwable ex) {
				logCaseDatesLoadFailure(operation, tenantId, actorId, activeCaseId, generation, started, ex);
				Platform.runLater(() -> { if (isCaseDatesCurrent(activeCaseId, generation)) renderCaseDatesFailure(); });
			}
		});
	}

	private boolean isCaseDatesCurrent(int activeCaseId, int generation) { return caseId != null && caseId == activeCaseId && generation == caseDatesLoadGeneration && caseDatesTabPane != null && caseDatesTabPane.getScene() != null; }
	private void logCaseDatesLoadFailure(String operation, int tenantId, int actorId, int activeCaseId, int generation, long startedNanos, Throwable ex) { long elapsedMs = Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000L); LOG.error("Case dates load failed operation={} tenantId={} actorId={} caseId={} generation={} elapsedMs={}", operation, tenantId, actorId, activeCaseId, generation, elapsedMs, ex); }

	private List<CaseDateDto> sortCaseDates(List<CaseDateDto> dates) {
		return (dates == null ? List.<CaseDateDto>of() : dates).stream().sorted(Comparator.comparing(CaseDateDto::startsAt, Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(CaseDateDto::endsAt, Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(d -> safeText(d.typeName())).thenComparingLong(CaseDateDto::id)).toList();
	}

	private void renderCaseDates(String message) {
		if (caseDatesCardsBox == null) return; caseDatesCardsBox.getChildren().clear();
		if (message != null && !message.isBlank()) showCaseDatesMessage(message); else setVisibleManaged(caseDatesStatusLabel, false);
		if (caseDates.isEmpty()) showCaseDatesMessage("No case dates have been added yet."); else for (CaseDateDto date : caseDates) caseDatesCardsBox.getChildren().add(createCaseDateCard(date, false));
		updateRemovedCaseDatesVisibility();
		if (showRemovedCaseDates && removedCaseDatesCardsBox != null) { removedCaseDatesCardsBox.getChildren().clear(); if (removedCaseDates.isEmpty()) showRemovedCaseDatesMessage("No removed case dates."); else { setVisibleManaged(removedCaseDatesStatusLabel, false); for (CaseDateDto date : removedCaseDates) removedCaseDatesCardsBox.getChildren().add(createCaseDateCard(date, true)); } }
	}

	private Node createCaseDateCard(CaseDateDto date, boolean removed) {
		Label title = new Label(safe(date.displayTitle())); title.setStyle("-fx-font-weight: 700; -fx-font-size: 13px;");
		Label when = new Label(formatCaseDateOccurrence(date)); when.setStyle("-fx-opacity: 0.78;");
		VBox text = new VBox(3, title, when);
		if (isHistoricalCaseDateType(date)) { Label h = new Label("Historical/inactive type"); h.setStyle("-fx-opacity: 0.65; -fx-font-size: 11px;"); text.getChildren().add(h); }
		if (!removed && !safeText(date.notes()).isBlank()) { Label n = new Label(date.notes()); n.setWrapText(true); n.setStyle("-fx-opacity: 0.75;"); text.getChildren().add(n); }
		Button edit = ActionButtonFactory.semantic("Edit", e -> openCaseDateDialog(date), ControlStyles.Purpose.GHOST, ControlStyles.Size.SMALL); edit.setAccessibleText("Edit case date");
		Button remove = ActionButtonFactory.semantic("Remove", e -> onRemoveCaseDate(date), ControlStyles.Purpose.DANGER, ControlStyles.Size.SMALL); remove.setAccessibleText("Remove case date");
		Button restore = ActionButtonFactory.semantic("Restore", e -> onRestoreCaseDate(date), ControlStyles.Purpose.SECONDARY, ControlStyles.Size.SMALL); restore.setAccessibleText("Restore case date");
		HBox actions = removed ? new HBox(6, restore) : new HBox(6, edit, remove); actions.setAlignment(Pos.CENTER_RIGHT);
		Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS); HBox row = new HBox(12, text, spacer, actions); row.setAlignment(Pos.CENTER_LEFT); row.setPadding(new Insets(10)); row.setStyle("-fx-background-color: rgba(248,250,252,0.96); -fx-background-radius: 12; -fx-border-color: rgba(74,104,138,0.24); -fx-border-radius: 12;" + (removed ? " -fx-opacity: 0.72;" : "")); return row;
	}

	private String formatCaseDateOccurrence(CaseDateDto d) { if (d == null || d.startsAt() == null) return "—"; DateTimeFormatter df = DateTimeFormatter.ofPattern("MMM d, yyyy"); DateTimeFormatter dtf = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a"); if (d.allDay()) { String s = d.startsAt().toLocalDate().format(df); return d.endsAt() == null ? s : s + " – " + d.endsAt().toLocalDate().format(df); } String s = d.startsAt().format(dtf); return d.endsAt() == null ? s : s + " – " + d.endsAt().format(dtf); }
	private boolean isHistoricalCaseDateType(CaseDateDto d) { return d != null && effectiveCaseDateTypes.stream().noneMatch(t -> t.id() == d.caseDateTypeId()); }
	private void showCaseDatesMessage(String message) { if (caseDatesStatusLabel != null) { caseDatesStatusLabel.setText(message); setVisibleManaged(caseDatesStatusLabel, true); } }
	private void showRemovedCaseDatesMessage(String message) { if (removedCaseDatesStatusLabel != null) { removedCaseDatesStatusLabel.setText(message); setVisibleManaged(removedCaseDatesStatusLabel, true); } }
	private void renderCaseDatesFailure() { if (caseDatesCardsBox != null) caseDatesCardsBox.getChildren().clear(); Button retry = ActionButtonFactory.semantic("Retry", e -> loadCaseDatesAsync(), ControlStyles.Purpose.SECONDARY, ControlStyles.Size.STANDARD); retry.setAccessibleText("Retry loading case dates"); VBox box = new VBox(8, new Label("Failed to load case dates."), retry); caseDatesCardsBox.getChildren().setAll(box); setVisibleManaged(caseDatesStatusLabel, false); }
	private void updateRemovedCaseDatesVisibility() { if (showRemovedCaseDatesButton != null) { showRemovedCaseDatesButton.setText(showRemovedCaseDates ? "Hide Removed" : "Show Removed"); showRemovedCaseDatesButton.setAccessibleText(showRemovedCaseDates ? "Hide removed case dates" : "Show removed case dates"); } setVisibleManaged(removedCaseDatesCardsBox, showRemovedCaseDates); setVisibleManaged(removedCaseDatesStatusLabel, showRemovedCaseDates && removedCaseDates.isEmpty()); }
	private void openCaseDateDialog(CaseDateDto existing) { if (caseService == null || appState == null || caseId == null || currentOverview == null || currentOverview.getCaseId() != caseId) return; if (effectiveCaseDateTypes.isEmpty()) loadCaseDatesAsync(); caseDateEditorOpen = true; try { CaseDateOccurrenceDialog.show(caseDatesOwner(), existing == null ? "Add Date" : "Edit Date", effectiveCaseDateTypes, existing, CaseDateOccurrenceEditorLauncher.toCaseCardModel(currentOverview), id -> this.onOpenCase.accept(id), input -> saveCaseDate(existing, input), existing == null ? null : () -> removeCaseDateFromDialog(existing), this::loadCaseDatesAsync); } finally { caseDateEditorOpen = false; applyDeferredCaseDatesRefresh(); } }
	private java.util.concurrent.CompletionStage<String> saveCaseDate(CaseDateDto existing, CaseDateOccurrenceDialog.Input input) {
		Integer tenantId = appState.getShaleClientId(), actorId = appState.getUserId();
		if (tenantId == null || actorId == null || caseId == null)
			return java.util.concurrent.CompletableFuture.completedFuture("Save is unavailable.");
		if (caseDateMutationInFlight.getAndSet(true))
			return java.util.concurrent.CompletableFuture.completedFuture("Save is already in progress.");
		final long activeCaseId = caseId.longValue();
		final boolean compatibilityAffected = isMigratedCaseDateType(input.caseDateTypeId())
				|| (existing != null && isMigratedCaseDateSystemKey(existing.typeSystemKey()));
		return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
			try {
				if (existing == null) caseService.createCaseDate(createCaseDateCommand(tenantId, actorId, activeCaseId, input));
				else caseService.updateCaseDate(updateCaseDateCommand(tenantId, actorId, activeCaseId, existing, input));
				Platform.runLater(() -> { refreshCaseDateViewsAfterLocalMutation(activeCaseId, compatibilityAffected); publishCaseDatesChanged(activeCaseId, existing == null ? LiveUpdateEvents.CHANGE_CREATED : LiveUpdateEvents.CHANGE_UPDATED); });
				return null;
			} catch (RuntimeException ex) { return rootMessage(ex); }
			finally { caseDateMutationInFlight.set(false); Platform.runLater(this::applyDeferredCaseDatesRefresh); }
		}, caseDateExecutor);
	}
	static CreateCaseDateCommand createCaseDateCommand(int tenantId,int actorId,long caseId,CaseDateOccurrenceDialog.Input input){ return new CreateCaseDateCommand(tenantId,actorId,caseId,input.caseDateTypeId(),input.title(),input.startsAt(),input.endsAt(),input.allDay(),input.notes()); }
	static UpdateCaseDateCommand updateCaseDateCommand(int tenantId,int actorId,long caseId,CaseDateDto existing,CaseDateOccurrenceDialog.Input input){ return new UpdateCaseDateCommand(tenantId,actorId,caseId,existing.id(),input.caseDateTypeId(),input.title(),input.startsAt(),input.endsAt(),input.allDay(),input.notes(),existing.rowVer()); }
	private java.util.concurrent.CompletionStage<String> removeCaseDateFromDialog(CaseDateDto existing) {
		Integer tenantId=appState.getShaleClientId(), actorId=appState.getUserId();
		if(tenantId==null||actorId==null||caseId==null)return java.util.concurrent.CompletableFuture.completedFuture("Remove is unavailable.");
		if(caseDateMutationInFlight.getAndSet(true))return java.util.concurrent.CompletableFuture.completedFuture("A Case Date change is already in progress.");
		final long activeCaseId=caseId.longValue();
		return java.util.concurrent.CompletableFuture.supplyAsync(()->{try{
			caseService.deleteCaseDate(new DeleteCaseDateCommand(tenantId,actorId,activeCaseId,existing.id(),existing.rowVer()));
			Platform.runLater(()->{refreshCaseDateViewsAfterLocalMutation(activeCaseId,isMigratedCaseDateSystemKey(existing.typeSystemKey()));publishCaseDatesChanged(activeCaseId,LiveUpdateEvents.CHANGE_REMOVED);loadCaseDatesAsync();});
			return null;
		}catch(RuntimeException ex){return rootMessage(ex);}finally{caseDateMutationInFlight.set(false);Platform.runLater(this::applyDeferredCaseDatesRefresh);}},caseDateExecutor);
	}

	private void onRemoveCaseDate(CaseDateDto d) {
		if (d == null || caseService == null || appState == null || caseId == null) return;
		if (!AppDialogs.showConfirmation(caseDatesOwner(), "Remove Date", "Remove this case date?", "The date will be removed from active case dates and can be restored later.", "Remove", DialogActionKind.DANGER)) return;
		Integer tenantId = appState.getShaleClientId(), actorId = appState.getUserId();
		if (tenantId == null || actorId == null || caseDateMutationInFlight.getAndSet(true)) return;
		final long activeCaseId = caseId.longValue();
		caseDateExecutor.submit(() -> { try {
			caseService.deleteCaseDate(new DeleteCaseDateCommand(tenantId, actorId, activeCaseId, d.id(), d.rowVer()));
			Platform.runLater(() -> { refreshCaseDateViewsAfterLocalMutation(activeCaseId, isMigratedCaseDateSystemKey(d.typeSystemKey())); publishCaseDatesChanged(activeCaseId, LiveUpdateEvents.CHANGE_REMOVED); });
		} catch (RuntimeException ex) { Platform.runLater(() -> AppDialogs.showError(caseDatesOwner(), "Remove Date", rootMessage(ex))); }
		finally { caseDateMutationInFlight.set(false); Platform.runLater(this::applyDeferredCaseDatesRefresh); } });
	}

	private void onRestoreCaseDate(CaseDateDto d) {
		if (d == null || caseService == null || appState == null || caseId == null) return;
		Integer tenantId = appState.getShaleClientId(), actorId = appState.getUserId();
		if (tenantId == null || actorId == null || caseDateMutationInFlight.getAndSet(true)) return;
		final long activeCaseId = caseId.longValue();
		caseDateExecutor.submit(() -> { try {
			caseService.restoreCaseDate(new RestoreCaseDateCommand(tenantId, actorId, activeCaseId, d.id(), d.rowVer()));
			Platform.runLater(() -> { refreshCaseDateViewsAfterLocalMutation(activeCaseId, isMigratedCaseDateSystemKey(d.typeSystemKey())); publishCaseDatesChanged(activeCaseId, LiveUpdateEvents.CHANGE_ADDED); });
		} catch (RuntimeException ex) { Platform.runLater(() -> AppDialogs.showError(caseDatesOwner(), "Restore Date", rootMessage(ex))); }
		finally { caseDateMutationInFlight.set(false); Platform.runLater(this::applyDeferredCaseDatesRefresh); } });
	}
	private Window caseDatesOwner() { return caseDatesTabPane != null && caseDatesTabPane.getScene() != null ? caseDatesTabPane.getScene().getWindow() : taskDialogOwner(); }


	private void resetCaseLinksState() {
		caseLinksLoadedOnce = false;
		caseLinksStale = true;
		caseLinks = List.of();
		caseLinksLoadGeneration++;
		if (caseLinksCardsBox != null) caseLinksCardsBox.getChildren().clear();
	}

	private void resetOverviewPrimaryLinkState() {
		overviewPrimaryLink = Optional.empty();
		overviewPrimaryLinkLoadedOnce = false;
		overviewPrimaryLinkStale = true;
		overviewPrimaryLinkLoadGeneration++;
		renderOverviewPrimaryLinkLoading();
	}

	private void invalidateOverviewPrimaryLinkAfterCaseLinkMutation() {
		overviewPrimaryLinkStale = true;
		overviewPrimaryLinkLoadedOnce = false;
		if ("Overview".equals(activeSectionName)) loadOverviewPrimaryLinkIfNeeded();
	}

	private void loadOverviewPrimaryLinkIfNeeded() {
		if (overviewPrimaryLinkLoadedOnce && !overviewPrimaryLinkStale) {
			renderOverviewPrimaryLinkState();
			return;
		}
		if (caseService == null || appState == null || caseId == null) {
			renderOverviewPrimaryLinkFailure("Primary Link is unavailable.");
			return;
		}
		Integer tenantId = appState.getShaleClientId();
		if (tenantId == null || tenantId <= 0) {
			renderOverviewPrimaryLinkFailure("Primary Link is unavailable because no tenant is selected.");
			return;
		}
		final int activeCaseId = caseId;
		final int generation = ++overviewPrimaryLinkLoadGeneration;
		renderOverviewPrimaryLinkLoading();
		new Thread(() -> {
			try {
				Optional<CaseLinkDto> primary = caseService.getPrimaryCaseLink(activeCaseId, tenantId);
				Platform.runLater(() -> {
					if (caseId == null || caseId != activeCaseId || generation != overviewPrimaryLinkLoadGeneration) return;
					overviewPrimaryLink = primary == null ? Optional.empty() : primary;
					overviewPrimaryLinkLoadedOnce = true;
					overviewPrimaryLinkStale = false;
					renderOverviewPrimaryLinkState();
				});
			} catch (RuntimeException ex) {
				Platform.runLater(() -> {
					if (caseId == null || caseId != activeCaseId || generation != overviewPrimaryLinkLoadGeneration) return;
					renderOverviewPrimaryLinkFailure("Failed to load primary link. " + rootMessage(ex));
				});
			}
		}, "case-overview-primary-link-load-" + activeCaseId).start();
	}

	private void renderOverviewPrimaryLinkLoading() {
		if (ovPrimaryLinkBox != null) ovPrimaryLinkBox.getChildren().clear();
		showOverviewPrimaryLinkMessage("Loading primary link…");
	}

	private void renderOverviewPrimaryLinkFailure(String message) {
		if (ovPrimaryLinkBox != null) ovPrimaryLinkBox.getChildren().clear();
		showOverviewPrimaryLinkMessage(message);
	}

	private void renderOverviewPrimaryLinkState() {
		if (ovPrimaryLinkBox == null) return;
		ovPrimaryLinkBox.getChildren().clear();
		setVisibleManaged(ovPrimaryLinkStatusLabel, false);
		if (overviewPrimaryLink.isEmpty()) {
			Label empty = new Label("No primary link has been selected for this case.");
			empty.setWrapText(true);
			empty.getStyleClass().add("case-overview-row-value");
			ovPrimaryLinkBox.getChildren().add(empty);
			return;
		}
		CaseLinkDto link = overviewPrimaryLink.get();
		ovPrimaryLinkBox.getChildren().add(caseLinkCardFactory.create(link, CaseLinkCardFactory.Variant.COMPACT, new CaseLinkCardFactory.Actions(
				() -> onOpenOverviewPrimaryLink(link), () -> onEditCaseLink(link), null, null), onOpenContact));
	}

	private void showOverviewPrimaryLinkMessage(String message) {
		if (ovPrimaryLinkStatusLabel != null) {
			ovPrimaryLinkStatusLabel.setText(message == null ? "" : message);
			setVisibleManaged(ovPrimaryLinkStatusLabel, message != null && !message.isBlank());
		}
	}

	private void onOpenOverviewPrimaryLink(CaseLinkDto link) {
		try { externalBrowserHelper.openHttpOrHttps(link.url()); }
		catch (RuntimeException ex) { AppDialogs.showError(caseOverviewOwner(), "Open Link", rootMessage(ex)); }
	}

	private void navigateToCaseLinksForManagement() {
		onSectionSelected("Links", true);
	}

	private void resetMaterialRequestsState() {
		if (caseRequestsContentHost != null) {
			caseRequestsContentHost.getChildren().clear();
		}
	}

	private void showRequestsTab() {
		showRequestsSurface();
		if (caseRequestsContentHost != null && caseRequestsContentHost.getChildren().isEmpty() && materialRequestService != null) {
			caseRequestsContentHost.getChildren().setAll(caseMaterialRequestsTabController.view());
		}
		if (materialRequestService != null) caseMaterialRequestsTabController.load();
		loadCaseUpdatesAsync();
	}

	private void showRequestsSurface() {
		attachCaseUpdatesPane(CaseUpdatesPlacement.RIGHT);
		activateCaseSectionRoot(caseRequestsTabPane);
		setPaneVisible(tasksPanel, false);
	}

	private Window caseRequestsOwner() { return caseRequestsTabPane != null && caseRequestsTabPane.getScene() != null ? caseRequestsTabPane.getScene().getWindow() : taskDialogOwner(); }

	private void showLinksTab() {
		attachCaseUpdatesPane(CaseUpdatesPlacement.RIGHT);
		setPaneVisible(overviewScrollPane, false);
		setPaneVisible(detailsSectionPane, false);
		setPaneVisible(tasksTabPane, false);
		setPaneVisible(caseCalendarTabPane, false);
		setPaneVisible(caseDatesTabPane, false);
		setPaneVisible(caseRequestsTabPane, false);
		setPaneVisible(caseLinksTabPane, true);
		setPaneVisible(genericPane, false);
		setPaneVisible(tasksPanel, false);
		if (!caseLinksLoadedOnce || caseLinksStale) loadCaseLinksAsync(null); else renderCaseLinks(null);
		loadCaseUpdatesAsync();
	}

	private void loadCaseLinksAsync(String successMessage) {
		if (caseService == null || appState == null || caseId == null) { showCaseLinksMessage("Links are unavailable."); return; }
		Integer tenantId = appState.getShaleClientId();
		if (tenantId == null || tenantId <= 0) { showCaseLinksMessage("Links are unavailable because no tenant is selected."); return; }
		final int activeCaseId = caseId;
		final int generation = ++caseLinksLoadGeneration;
		final long started = System.nanoTime();
		showCaseLinksMessage("Loading links…");
		caseLinkExecutor.submit(() -> {
			try {
				List<CaseLinkDto> links = caseService.listCaseLinks(activeCaseId, tenantId);
				List<CaseLinkDto> safeLinks = links == null ? List.of() : List.copyOf(links);
				Platform.runLater(() -> {
					if (caseId == null || caseId != activeCaseId || generation != caseLinksLoadGeneration) {
						LOG.info("Case Links reload stale op=list tenantId={} caseId={} requestId={} rows={} elapsedMs={}", tenantId, activeCaseId, generation, safeLinks.size(), elapsedMs(started));
						return;
					}
					caseLinks = safeLinks;
					caseLinksLoadedOnce = true;
					caseLinksStale = false;
					LOG.info("Case Links reload success op=list tenantId={} caseId={} requestId={} rows={} elapsedMs={}", tenantId, activeCaseId, generation, safeLinks.size(), elapsedMs(started));
					renderCaseLinks(successMessage);
				});
			} catch (RuntimeException ex) {
				LOG.warn("Case Links reload failure op=list tenantId={} caseId={} requestId={} elapsedMs={}", tenantId, activeCaseId, generation, elapsedMs(started), ex);
				Platform.runLater(() -> { if (generation == caseLinksLoadGeneration && caseId != null && caseId == activeCaseId) showCaseLinksMessage("Failed to load links. " + rootMessage(ex)); });
			}
		});
	}

	private void renderCaseLinks(String message) {
		if (caseLinksCardsBox == null) return;
		caseLinksCardsBox.getChildren().clear();
		if (message != null && !message.isBlank()) showCaseLinksMessage(message); else setVisibleManaged(caseLinksStatusLabel, false);
		if (caseLinks.isEmpty()) { showCaseLinksMessage("No links have been added to this case yet."); return; }
		groupCaseLinksByType(caseLinks).forEach((group, links) -> {
			VBox section = new VBox(8);
			section.getStyleClass().add("case-link-type-group");
			HBox heading = new HBox(8);
			heading.setAlignment(Pos.CENTER_LEFT);
			heading.getStyleClass().add("case-link-type-group-heading");
			heading.getChildren().add(LinkTypeIndicatorFactory.createLinkTypePill(group.name(), group.color(), LinkTypeIndicatorFactory.PillSize.DEFAULT));
			Label count = new Label(links.size() + (links.size() == 1 ? " link" : " links"));
			count.getStyleClass().add("search-summary-text");
			heading.getChildren().add(count);
			section.getChildren().add(heading);
			for (CaseLinkDto link : links) {
				section.getChildren().add(caseLinkCardFactory.create(link, CaseLinkCardFactory.Variant.FULL, new CaseLinkCardFactory.Actions(
						() -> onOpenCaseLink(link), () -> onEditCaseLink(link), () -> onSetPrimaryCaseLink(link), () -> onDeleteCaseLink(link)), onOpenContact));
			}
			caseLinksCardsBox.getChildren().add(section);
		});
	}

	private LinkedHashMap<LinkTypeGroup, List<CaseLinkDto>> groupCaseLinksByType(List<CaseLinkDto> links) {
		return links.stream()
				.collect(Collectors.groupingBy(
						link -> new LinkTypeGroup(link.linkTypeId(), blankTo(link.linkTypeName(), "Link Type"), link.linkTypeColor()),
						() -> new java.util.TreeMap<>(Comparator.comparing(LinkTypeGroup::name, String.CASE_INSENSITIVE_ORDER).thenComparingInt(LinkTypeGroup::id)),
						Collectors.collectingAndThen(Collectors.toList(), list -> list.stream()
								.sorted(Comparator.comparing(CaseLinkDto::primary).reversed()
										.thenComparingInt(CaseLinkDto::sortOrder)
										.thenComparing(CaseLinkDto::displayName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
										.thenComparingLong(CaseLinkDto::caseLinkId))
								.toList())))
				.entrySet().stream()
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
	}

	private record LinkTypeGroup(int id, String name, String color) {}

	private void showCaseLinksMessage(String message) {
		if (caseLinksStatusLabel != null) { caseLinksStatusLabel.setText(message == null ? "" : message); setVisibleManaged(caseLinksStatusLabel, message != null && !message.isBlank()); }
	}

	private void onOpenCaseLink(CaseLinkDto link) {
		try { externalBrowserHelper.openHttpOrHttps(link.url()); }
		catch (RuntimeException ex) { AppDialogs.showError(caseLinksOwner(), "Open Link", rootMessage(ex)); }
	}

	private void onAddCaseLink() {
		loadLinkTypesForDialog(null, types -> showCaseLinkDialog(null, types).ifPresent(input -> {
			final int tenantId = requireTenantId();
			final int actorId = requireActorUserId();
			final int activeCaseId = caseId;
			runCaseLinkMutation("create", "Link added.", activeCaseId, null,
					shareLiveChangesForCreate(input.shareAdds()),
					() -> caseService.createCaseLinkWithShares(new CaseServicePort.CreateCaseLinkWithSharesCommand(tenantId, actorId, activeCaseId, input.linkType().id(), input.displayName(), input.url(), input.description(), input.primary(), input.notes(), null, input.shareAdds())));
		}));
	}

	private void onEditCaseLink(CaseLinkDto link) {
		loadLinkTypesForDialog(link, types -> showCaseLinkDialog(link, types).ifPresent(input -> {
			final int tenantId = requireTenantId();
			final int actorId = requireActorUserId();
			final int activeCaseId = caseId;
			runCaseLinkMutation("update", "Link updated.", activeCaseId, link.caseLinkId(),
					shareLiveChangesForUpdate(input.shareAdds(), input.shareUpdates(), input.shareRemovals(), link.shares()),
					() -> caseService.updateCaseLinkWithShares(new CaseServicePort.UpdateCaseLinkWithSharesCommand(tenantId, actorId, activeCaseId, link.caseLinkId(), link.externalLinkId(), input.linkType().id(), input.displayName(), input.url(), input.description(), null, input.notes(), null, link.caseLinkRowVer(), link.externalLinkRowVer(), input.shareAdds(), input.shareUpdates(), input.shareRemovals())));
		}));
	}

	private void onSetPrimaryCaseLink(CaseLinkDto link) {
		final int tenantId = requireTenantId();
		final int actorId = requireActorUserId();
		final int activeCaseId = caseId;
		runCaseLinkMutation("set-primary", "Primary link updated.", activeCaseId, link.caseLinkId(), CaseLinkShareLiveChanges.NONE, () -> caseService.setPrimaryCaseLink(new CaseServicePort.SetPrimaryCaseLinkCommand(tenantId, actorId, activeCaseId, link.caseLinkId())));
	}

	private void onDeleteCaseLink(CaseLinkDto link) {
		boolean ok = AppDialogs.showConfirmation(caseLinksOwner(), "Delete Link", "Remove this link from the case?", "The link will be removed from this case. Shared external-link records are left to the service/DAO to manage safely.", "Delete", DialogActionKind.DANGER);
		if (!ok) return;
		final int tenantId = requireTenantId();
		final int actorId = requireActorUserId();
		final int activeCaseId = caseId;
		runCaseLinkMutation("delete", "Link deleted.", activeCaseId, link.caseLinkId(), CaseLinkShareLiveChanges.NONE, () -> { caseService.deleteCaseLink(new CaseServicePort.DeleteCaseLinkCommand(tenantId, actorId, activeCaseId, link.caseLinkId(), link.caseLinkRowVer())); return null; });
	}

	private void onMoveCaseLink(int index, int delta) {
		int target = index + delta;
		if (index < 0 || target < 0 || index >= caseLinks.size() || target >= caseLinks.size()) return;
		List<Long> ids = new ArrayList<>(caseLinks.stream().map(CaseLinkDto::caseLinkId).toList());
		java.util.Collections.swap(ids, index, target);
		if (ids.size() != caseLinks.size() || ids.stream().distinct().count() != ids.size()) { showCaseLinksMessage("Cannot reorder links because the active link list is invalid."); return; }
		final int tenantId = requireTenantId();
		final int actorId = requireActorUserId();
		final int activeCaseId = caseId;
		runCaseLinkMutation("reorder", "Links reordered.", activeCaseId, null, CaseLinkShareLiveChanges.NONE, () -> caseService.reorderCaseLinks(new CaseServicePort.ReorderCaseLinksCommand(tenantId, actorId, activeCaseId, ids)));
	}

	private void publishCaseLinkLiveInvalidations(String operation, long caseId, Long caseLinkIdForLog, Object result, int tenantId, int actorId, CaseLinkShareLiveChanges shareChanges) {
		if (runtimeBridge == null || tenantId <= 0 || actorId <= 0) return;
		Long caseLinkId = resolvedCaseLinkId(caseLinkIdForLog, result);
		Long externalLinkId = result instanceof CaseLinkDto dto ? dto.externalLinkId() : null;
		Integer linkTypeId = result instanceof CaseLinkDto dto ? dto.linkTypeId() : null;
		String change = switch (operation == null ? "" : operation) {
			case "create" -> LiveUpdateEvents.CHANGE_CREATED;
			case "update" -> LiveUpdateEvents.CHANGE_UPDATED;
			case "set-primary" -> LiveUpdateEvents.CHANGE_PRIMARY_CHANGED;
			case "delete" -> LiveUpdateEvents.CHANGE_DELETED;
			case "reorder" -> LiveUpdateEvents.CHANGE_REORDERED;
			default -> LiveUpdateEvents.CHANGE_UPDATED;
		};
		runtimeBridge.publishCaseLinkChanged(caseId, caseLinkId, externalLinkId, linkTypeId, tenantId, actorId, change);
		publishCaseLinkShareLiveInvalidations(caseId, caseLinkId, result, tenantId, actorId, shareChanges);
		runtimeBridge.publishEntityAuditActivityAdded(null, tenantId, actorId);
	}

	private void publishCaseLinkShareLiveInvalidations(long caseId, Long fallbackCaseLinkId, Object result, int tenantId, int actorId, CaseLinkShareLiveChanges shareChanges) {
		if (runtimeBridge == null || shareChanges == null || shareChanges.isEmpty()) return;
		List<CaseLinkShareDto> committedShares = result instanceof CaseLinkDto dto && dto.shares() != null ? dto.shares() : List.of();
		long caseLinkId = fallbackCaseLinkId == null ? 0L : fallbackCaseLinkId;
		for (CaseLinkShareLiveChange add : shareChanges.added()) {
			Long shareId = committedShares.stream().filter(s -> s.contactId() == add.contactId()).map(CaseLinkShareDto::caseLinkShareId).findFirst().orElse(null);
			runtimeBridge.publishCaseLinkShareChanged(caseId, caseLinkId, shareId, add.contactId(), tenantId, actorId, LiveUpdateEvents.CHANGE_ADDED);
		}
		for (CaseLinkShareLiveChange update : shareChanges.updated()) {
			runtimeBridge.publishCaseLinkShareChanged(caseId, caseLinkId, update.caseLinkShareId(), update.contactId(), tenantId, actorId, LiveUpdateEvents.CHANGE_UPDATED);
		}
		for (CaseLinkShareLiveChange removal : shareChanges.removed()) {
			runtimeBridge.publishCaseLinkShareChanged(caseId, caseLinkId, removal.caseLinkShareId(), removal.contactId(), tenantId, actorId, LiveUpdateEvents.CHANGE_REMOVED);
		}
	}

	private void runCaseLinkMutation(String operation, String successMessage, int activeCaseId, Long caseLinkIdForLog, CaseLinkShareLiveChanges shareChanges, java.util.concurrent.Callable<?> action) {
		if (!caseLinkMutationInFlight.compareAndSet(false, true)) {
			LOG.info("Case Link mutation duplicate blocked op={} tenantId={} actorId={} caseId={} caseLinkId={}", operation, safeTenantId(), safeActorUserId(), activeCaseId, caseLinkIdForLog);
			showCaseLinksMessage("Saving link changes…");
			return;
		}
		setCaseLinkControlsDisabled(true);
		final int tenantId = safeTenantId();
		final int actorId = safeActorUserId();
		final int requestId = ++caseLinksLoadGeneration;
		final long started = System.nanoTime();
		LOG.info("Case Link mutation start op={} tenantId={} actorId={} caseId={} caseLinkId={} requestId={}", operation, tenantId, actorId, activeCaseId, caseLinkIdForLog, requestId);
		caseLinkExecutor.submit(() -> {
			try {
				Object result = action.call();
				publishCaseLinkLiveInvalidations(operation, activeCaseId, caseLinkIdForLog, result, tenantId, actorId, shareChanges);
				List<CaseLinkDto> reloaded = caseService.listCaseLinks(activeCaseId, tenantId);
				List<CaseLinkDto> safeReloaded = reloaded == null ? List.of() : List.copyOf(reloaded);
				Platform.runLater(() -> {
					caseLinkMutationInFlight.set(false);
					setCaseLinkControlsDisabled(false);
					caseLinksStale = true;
					invalidateOverviewPrimaryLinkAfterCaseLinkMutation();
					if (caseId == null || caseId != activeCaseId || requestId != caseLinksLoadGeneration) {
						LOG.info("Case Link mutation reload rejected stale op={} tenantId={} actorId={} caseId={} caseLinkId={} requestId={} rows={} elapsedMs={}", operation, tenantId, actorId, activeCaseId, resolvedCaseLinkId(caseLinkIdForLog, result), requestId, safeReloaded.size(), elapsedMs(started));
						return;
					}
					caseLinks = safeReloaded;
					caseLinksLoadedOnce = true;
					caseLinksStale = false;
					LOG.info("Case Link mutation success op={} tenantId={} actorId={} caseId={} caseLinkId={} requestId={} rows={} elapsedMs={}", operation, tenantId, actorId, activeCaseId, resolvedCaseLinkId(caseLinkIdForLog, result), requestId, safeReloaded.size(), elapsedMs(started));
					renderCaseLinks(successMessage);
				});
			} catch (Exception ex) {
				boolean primaryConflict = isPrimaryCaseLinkConflict(ex);
				List<CaseLinkDto> conflictReload = null;
				if (primaryConflict) {
					try {
						List<CaseLinkDto> reloaded = caseService.listCaseLinks(activeCaseId, tenantId);
						conflictReload = reloaded == null ? List.of() : List.copyOf(reloaded);
					} catch (Exception reloadEx) {
						LOG.warn("Case Link mutation conflict reload failed op={} tenantId={} actorId={} caseId={} caseLinkId={} requestId={}", operation, tenantId, actorId, activeCaseId, caseLinkIdForLog, requestId, reloadEx);
					}
				}
				List<CaseLinkDto> safeConflictReload = conflictReload;
				LOG.warn("Case Link mutation failure op={} tenantId={} actorId={} caseId={} caseLinkId={} requestId={} primaryConflict={} elapsedMs={}", operation, tenantId, actorId, activeCaseId, caseLinkIdForLog, requestId, primaryConflict, elapsedMs(started), ex);
				Platform.runLater(() -> {
					caseLinkMutationInFlight.set(false);
					setCaseLinkControlsDisabled(false);
					if (caseId != null && caseId == activeCaseId) {
						if (primaryConflict && safeConflictReload != null) {
							caseLinks = safeConflictReload;
							caseLinksLoadedOnce = true;
							caseLinksStale = false;
							invalidateOverviewPrimaryLinkAfterCaseLinkMutation();
							renderCaseLinks(null);
						} else {
							renderCaseLinks(null);
						}
						AppDialogs.showError(caseLinksOwner(), "Case Links", caseLinkUserMessage(ex));
					}
				});
			}
		});
	}

	private void loadLinkTypesForDialog(CaseLinkDto currentLink, Consumer<List<LinkTypeDto>> onLoaded) {
		if (caseService == null || caseId == null) return;
		final int activeCaseId = caseId;
		final int tenantId = requireTenantId();
		final int requestId = ++caseLinkDialogLoadGeneration;
		setCaseLinkControlsDisabled(true);
		showCaseLinksMessage("Loading Link Types…");
		caseLinkExecutor.submit(() -> {
			try {
				List<LinkTypeDto> active = caseService.listLinkTypes(tenantId, false);
				if (currentLink != null && active.stream().noneMatch(t -> t.id() == currentLink.linkTypeId())) {
					LinkTypeDto unavailable = new LinkTypeDto(currentLink.linkTypeId(), null, currentLink.linkTypeName() + " (unavailable)", currentLink.linkTypeColor(), false, false, currentLink.linkTypeSystemKey(), null);
					List<LinkTypeDto> copy = new ArrayList<>(); copy.add(unavailable); copy.addAll(active); active = copy;
				}
				List<LinkTypeDto> result = List.copyOf(active);
				Platform.runLater(() -> {
					setCaseLinkControlsDisabled(false);
					if (caseId == null || caseId != activeCaseId || requestId != caseLinkDialogLoadGeneration) {
						LOG.info("Case Link dialog Link Type load rejected stale tenantId={} caseId={} requestId={} rows={}", tenantId, activeCaseId, requestId, result.size());
						return;
					}
					setVisibleManaged(caseLinksStatusLabel, false);
					if (result.isEmpty()) { AppDialogs.showInfo(caseLinksOwner(), "Add Link", "No active Link Types are available for this tenant."); return; }
					onLoaded.accept(result);
				});
			} catch (RuntimeException ex) {
				LOG.warn("Case Link dialog Link Type load failure tenantId={} caseId={} requestId={}", tenantId, activeCaseId, requestId, ex);
				Platform.runLater(() -> { setCaseLinkControlsDisabled(false); if (caseId != null && caseId == activeCaseId && requestId == caseLinkDialogLoadGeneration) AppDialogs.showError(caseLinksOwner(), "Case Links", rootMessage(ex)); });
			}
		});
	}

	private static void styleCaseLinkDialogButtons(Dialog<?> dialog, ButtonType affirmative, String affirmativeText) {
		Node affirmativeNode = dialog.getDialogPane().lookupButton(affirmative);
		if (affirmativeNode instanceof Button button) {
			button.setText(affirmativeText);
			ControlStyles.apply(button, ControlStyles.Purpose.PRIMARY, ControlStyles.Size.STANDARD);
		}
		Node cancelNode = dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
		if (cancelNode instanceof Button button) ControlStyles.apply(button, ControlStyles.Purpose.SECONDARY, ControlStyles.Size.STANDARD);
	}

	private static Button caseLinkAction(String text, ControlStyles.Purpose purpose, ControlStyles.Size size, javafx.event.EventHandler<javafx.event.ActionEvent> handler) {
		return ActionButtonFactory.semantic(text, handler, purpose, size);
	}

	private static boolean invalidCaseLinkUrl(String text) {
		if (blank(text) || text.trim().length() > 2048) return true;
		try { CaseLinkUrlNormalizer.normalize(text); return false; }
		catch (RuntimeException invalid) { return true; }
	}

	private Optional<CaseLinkInput> showCaseLinkDialog(CaseLinkDto existing, List<LinkTypeDto> linkTypes) {
		Dialog<CaseLinkInput> dialog = new Dialog<>(); String title = existing == null ? "Add Link" : "Edit Link"; dialog.setTitle(title); if (caseLinksOwner() != null) dialog.initOwner(caseLinksOwner()); AppDialogs.applySecondaryDialogShell(dialog, title); dialog.getDialogPane().getStyleClass().add("case-link-dialog-shell"); dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL); styleCaseLinkDialogButtons(dialog, ButtonType.OK, existing == null ? "Create" : "Save");
		ColorCodedComboBox<LinkTypeDto> type = new ColorCodedComboBox<>(LinkTypeDto::name, LinkTypeDto::color); type.getItems().setAll(linkTypes);
		TextField name = new TextField(existing == null ? "" : safeText(existing.displayName())); TextField url = new TextField(existing == null ? "" : safeText(existing.url())); TextArea description = new TextArea(existing == null ? "" : safeText(existing.description())); description.setPrefRowCount(3); TextArea notes = new TextArea(existing == null ? "" : safeText(existing.notes())); notes.setPrefRowCount(3); ControlStyles.formControl(type); ControlStyles.formControl(name); ControlStyles.formControl(url); ControlStyles.formControl(description); ControlStyles.formControl(notes); CheckBox primary = new CheckBox("Make primary"); primary.setSelected(existing != null && existing.primary());
		SharedWithEditor sharedWithEditor = new SharedWithEditor(existing);
		VBox sharedWithBox = sharedWithEditor.root();
		if (existing != null) type.getSelectionModel().select(linkTypes.stream().filter(t -> t.id() == existing.linkTypeId()).findFirst().orElse(null)); else if (!linkTypes.isEmpty()) type.getSelectionModel().selectFirst();
		Label error = new Label(); error.setTextFill(Color.web("#b42318")); error.setVisible(false); error.setManaged(false);
		GridPane grid = new GridPane(); grid.setHgap(10); grid.setVgap(8); grid.addRow(0, new Label("Link Type"), type); grid.addRow(1, new Label("Display Name"), name); grid.addRow(2, new Label("URL"), url); grid.addRow(3, new Label("Description"), description); grid.addRow(4, new Label("Notes"), notes); grid.add(primary, 1, 5); grid.add(sharedWithBox, 0, 6, 2, 1); grid.add(error, 0, 7, 2, 1);
		ColumnConstraints labels = new ColumnConstraints(); labels.setMinWidth(110); ColumnConstraints fields = new ColumnConstraints(); fields.setHgrow(Priority.ALWAYS); grid.getColumnConstraints().setAll(labels, fields); grid.setMaxWidth(Double.MAX_VALUE);
		grid.getStyleClass().addAll("case-link-dialog-form", "shale-card-surface");
		grid.getChildren().stream().filter(n -> n instanceof Label).forEach(n -> n.getStyleClass().add("case-link-dialog-label"));
		sharedWithBox.getStyleClass().addAll("case-link-dialog-section", "shale-embedded-card-surface");
		ScrollPane formScroll = screenSafeDialogScrollPane(grid); formScroll.getStyleClass().add("case-link-dialog-scroll"); formScroll.setPrefViewportWidth(720); dialog.getDialogPane().setContent(formScroll); dialog.setResizable(true); Runnable resizeCaseLinkDialog = () -> applyContentSizedDialogBounds(dialog, caseLinksOwner(), formScroll, grid, 780, 560, 420); sharedWithEditor.setOnSummaryChanged(resizeCaseLinkDialog); applyContentSizedDialogBounds(dialog, caseLinksOwner(), formScroll, grid, 780, 560, 420);
		AtomicBoolean validationVisible = new AtomicBoolean(false);
		Runnable updateInvalid = () -> {
			boolean show = validationVisible.get();
			ControlStyles.setInvalid(type, show && (type.getValue() == null || !type.getValue().active()));
			ControlStyles.setInvalid(name, show && (blank(name.getText()) || name.getText().trim().length() > 255));
			ControlStyles.setInvalid(url, show && invalidCaseLinkUrl(url.getText()));
			ControlStyles.setInvalid(description, show && description.getText() != null && description.getText().trim().length() > 2048);
			ControlStyles.setInvalid(notes, show && notes.getText() != null && notes.getText().trim().length() > 2000);
		};
		type.valueProperty().addListener((o,a,b) -> updateInvalid.run()); name.textProperty().addListener((o,a,b) -> updateInvalid.run()); url.textProperty().addListener((o,a,b) -> updateInvalid.run()); description.textProperty().addListener((o,a,b) -> updateInvalid.run()); notes.textProperty().addListener((o,a,b) -> updateInvalid.run());
		final CaseLinkInput[] validated = new CaseLinkInput[1];
		Node ok = dialog.getDialogPane().lookupButton(ButtonType.OK);
		ok.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
			try { validated[0] = validateCaseLinkDialogInput(type.getValue(), name.getText(), url.getText(), description.getText(), primary.isSelected(), notes.getText(), sharedWithEditor.shareAdds(), sharedWithEditor.shareUpdates(), sharedWithEditor.shareRemovals()); url.setText(validated[0].url()); validationVisible.set(false); updateInvalid.run(); error.setText(""); error.setVisible(false); error.setManaged(false); }
			catch (RuntimeException ex) { validated[0] = null; validationVisible.set(true); updateInvalid.run(); error.setText(rootMessage(ex)); error.setVisible(true); error.setManaged(true); LOG.info("Case Link dialog validation blocked save tenantId={} actorId={} caseId={} reason={}", safeTenantId(), safeActorUserId(), caseId, rootMessage(ex)); focusFirstInvalidCaseLinkField(type, name, url, description, notes); scrollFocusedNodeIntoView(formScroll); event.consume(); }
		});
		dialog.setResultConverter(button -> button == ButtonType.OK ? validated[0] : null);
		return dialog.showAndWait();
	}

	private static ScrollPane screenSafeDialogScrollPane(Node content) {
		ScrollPane scroll = new ScrollPane(content);
		scroll.setFitToWidth(true);
		scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
		scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
		scroll.getStyleClass().add("transparent-scroll");
		scroll.getStyleClass().add("case-link-styled-scroll");
		return scroll;
	}

	private static ScrollPane boundedVerticalScrollPane(Node content, double minHeight, double prefHeight) {
		ScrollPane scroll = screenSafeDialogScrollPane(content);
		scroll.setMinHeight(minHeight);
		scroll.setPrefHeight(prefHeight);
		scroll.setMaxHeight(prefHeight);
		return scroll;
	}

	private static ScrollPane adaptiveContactScrollPane(FlowPane content, double maxHeight) {
		ScrollPane scroll = screenSafeDialogScrollPane(content);
		scroll.getStyleClass().add("case-link-adaptive-contact-scroll");
		scroll.setMinHeight(Region.USE_PREF_SIZE);
		scroll.setMaxHeight(maxHeight);
		Runnable update = () -> {
			double h = Math.min(maxHeight, Math.max(42, content.prefHeight(Math.max(160, content.getWidth())) + 12));
			scroll.setPrefHeight(h);
			scroll.setVbarPolicy(h >= maxHeight - 1 ? ScrollPane.ScrollBarPolicy.AS_NEEDED : ScrollPane.ScrollBarPolicy.NEVER);
		};
		content.layoutBoundsProperty().addListener((obs, oldV, newV) -> update.run());
		content.widthProperty().addListener((obs, oldV, newV) -> update.run());
		Platform.runLater(update);
		return scroll;
	}

	private static void applyContentSizedDialogBounds(Dialog<?> dialog, Window owner, ScrollPane scrollPane, Node content, double prefWidth, double minWidth, double minHeight) {
		dialog.setResizable(true);
		Runnable resize = () -> {
			Window window = dialog.getDialogPane().getScene() == null ? null : dialog.getDialogPane().getScene().getWindow();
			if (window instanceof Stage stage) {
				WindowSizingUtil.sizeContentModalStage(stage, owner, dialog.getDialogPane(), scrollPane, content, prefWidth, minWidth, minHeight);
			}
		};
		dialog.setOnShown(event -> Platform.runLater(resize));
		if (dialog.getDialogPane().getScene() != null) Platform.runLater(resize);
	}

	private static void applyScreenSafeDialogBounds(Dialog<?> dialog, Window owner, double prefWidth, double prefHeight, double minWidth, double minHeight) {
		dialog.setResizable(true);
		dialog.setOnShown(event -> {
			Window window = dialog.getDialogPane().getScene() == null ? null : dialog.getDialogPane().getScene().getWindow();
			if (window instanceof Stage stage) {
				WindowSizingUtil.sizeModalStage(stage, owner, prefWidth, prefHeight, minWidth, minHeight);
				WindowSizingUtil.constrainToVisualBounds(stage, owner);
			}
		});
	}

	private static void scrollFocusedNodeIntoView(ScrollPane scroll) {
		if (scroll != null) scroll.setVvalue(1.0);
	}

	private final class SharedWithEditor {
		private final VBox root = new VBox(8);
		private final FlowPane cards = new FlowPane(8, 8);
		private final List<StagedShare> staged = new ArrayList<>();
		private Runnable onSummaryChanged = () -> { };
		SharedWithEditor(CaseLinkDto existing) { root.getStyleClass().add("case-link-shared-with-section"); for (CaseLinkShareDto share : existing == null || existing.shares() == null ? List.<CaseLinkShareDto>of() : existing.shares()) staged.add(StagedShare.persisted(share)); renderSummary(); }
		VBox root() { return root; }
		void setOnSummaryChanged(Runnable onSummaryChanged) { this.onSummaryChanged = onSummaryChanged == null ? () -> { } : onSummaryChanged; Platform.runLater(this.onSummaryChanged); }
		List<CaseServicePort.CaseLinkShareDraft> shareAdds() { return staged.stream().filter(s -> !s.removed && s.shareId <= 0).map(s -> new CaseServicePort.CaseLinkShareDraft(s.contactId, s.sharedAt, s.notes)).toList(); }
		List<CaseServicePort.CaseLinkShareUpdate> shareUpdates() { return staged.stream().filter(s -> !s.removed && s.shareId > 0 && s.changedFromOriginal()).map(s -> new CaseServicePort.CaseLinkShareUpdate(s.shareId, s.contactId, s.sharedAt, s.notes, s.rowVer == null ? null : s.rowVer.clone())).toList(); }
		List<CaseServicePort.CaseLinkShareRemoval> shareRemovals() { return staged.stream().filter(s -> s.removed && s.shareId > 0).map(s -> new CaseServicePort.CaseLinkShareRemoval(s.shareId, s.rowVer == null ? null : s.rowVer.clone())).toList(); }
		private List<StagedShare> activeShares() { return staged.stream().filter(s -> !s.removed).sorted(Comparator.comparing((StagedShare s) -> safeText(s.displayName), String.CASE_INSENSITIVE_ORDER).thenComparingInt(s -> s.contactId)).toList(); }
		private void renderSummary() {
			root.getChildren().clear();
			List<StagedShare> active = activeShares();
			if (active.isEmpty()) {
				Button share = caseLinkAction("Share Link", ControlStyles.Purpose.SECONDARY, ControlStyles.Size.SMALL, e -> openShareModal());
				share.setAccessibleText("Share Link");
				root.getChildren().add(share);
				root.requestLayout();
				Platform.runLater(onSummaryChanged);
				return;
			}
			Label heading = new Label("Shared With");
			heading.getStyleClass().add("section-heading");
			cards.getChildren().clear();
			cards.getStyleClass().add("case-link-shared-contact-flow");
			cards.setPrefWrapLength(520);
			cards.setMaxWidth(Double.MAX_VALUE);
			for (StagedShare share : active) cards.getChildren().add(createShareContactCard(share));
			ScrollPane cardsScroll = adaptiveContactScrollPane(cards, 176);
			cardsScroll.getStyleClass().add("case-link-shared-with-summary");
			cardsScroll.setAccessibleText("Shared With Contact Cards");
			Button edit = caseLinkAction("Edit Shared With", ControlStyles.Purpose.SECONDARY, ControlStyles.Size.SMALL, e -> openShareModal());
			edit.setAccessibleText("Edit Shared With");
			root.getChildren().addAll(heading, cardsScroll, edit);
			root.requestLayout();
			Platform.runLater(onSummaryChanged);
		}
		private Node createShareContactCard(StagedShare share) {
			ContactCardFactory factory = new ContactCardFactory(id -> { });
			String role = (share.unavailable ? "Unavailable · " : "") + "Shared " + share.sharedAt.format(DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a"));
			ContactCard card = factory.create(new ContactCardFactory.ContactCardModel(share.contactId, share.displayName, role, null, null), ContactCardFactory.Variant.MINI);
			card.setInteractive(false);
			card.setSuppressPlaceholderLines(true);
			card.getStyleClass().addAll("shale-entity-card-embedded", "case-link-embedded-contact-card");
			card.setMinWidth(120);
			card.setPrefWidth(Region.USE_COMPUTED_SIZE);
			card.setMaxWidth(220);
			card.setAccessibleText(role + " " + share.displayName);
			return card;
		}
		private void openShareModal() { showShareSelectionDialog(activeShares()).ifPresent(applied -> { staged.clear(); staged.addAll(applied.stream().map(StagedShare::copy).toList()); renderSummary(); }); }
		private Optional<List<StagedShare>> showShareSelectionDialog(List<StagedShare> parentShares) {
			Dialog<List<StagedShare>> dialog = new Dialog<>(); String title = parentShares == null || parentShares.isEmpty() ? "Share Link" : "Edit Shared With"; dialog.setTitle(title); Window modalOwner = root.getScene() == null ? caseLinksOwner() : root.getScene().getWindow(); if (modalOwner != null) dialog.initOwner(modalOwner); AppDialogs.applySecondaryDialogShell(dialog, title); dialog.getDialogPane().getStyleClass().add("case-link-dialog-shell"); dialog.getDialogPane().getButtonTypes().setAll(ButtonType.APPLY, ButtonType.CANCEL); styleCaseLinkDialogButtons(dialog, ButtonType.APPLY, "Apply"); dialog.setResizable(true);
			Map<Integer, StagedShare> working = new LinkedHashMap<>(); for (StagedShare s : parentShares == null ? List.<StagedShare>of() : parentShares) working.put(s.contactId, StagedShare.copy(s));
			VBox selectedBox = new VBox(6); Label selectedHeading = new Label(); selectedHeading.getStyleClass().add("section-heading"); FlowPane caseRows = new FlowPane(8, 8); caseRows.getStyleClass().add("case-link-share-selection-flow"); caseRows.setPrefWrapLength(700); Label caseState = new Label("Loading Case Contacts..."); caseState.getStyleClass().add("search-summary-text"); caseRows.getChildren().add(caseState); AtomicReference<List<CaseLinkContactOptionDto>> caseOptions = new AtomicReference<>(List.of());
			ScrollPane selectedScroll = boundedVerticalScrollPane(selectedBox, 96, 176); ScrollPane caseContactsScroll = boundedVerticalScrollPane(caseRows, 84, 150); caseContactsScroll.getStyleClass().add("case-link-case-contacts-scroll");
			TextField search = ControlStyles.formControl(new TextField()); search.setPromptText("Search all Contacts..."); Button clear = caseLinkAction("Clear", ControlStyles.Purpose.GHOST, ControlStyles.Size.SMALL, e -> search.clear()); HBox searchRow = new HBox(6, search, clear); HBox.setHgrow(search, Priority.ALWAYS);
			javafx.collections.ObservableList<CaseLinkContactOptionDto> allOptions = javafx.collections.FXCollections.observableArrayList(); javafx.collections.transformation.FilteredList<CaseLinkContactOptionDto> filtered = new javafx.collections.transformation.FilteredList<>(allOptions, o -> true); javafx.scene.control.ListView<CaseLinkContactOptionDto> allList = new javafx.scene.control.ListView<>(filtered); allList.setPrefHeight(240); allList.setPlaceholder(new Label("Loading Contacts..."));
			final Runnable[] refresh = new Runnable[1]; refresh[0] = () -> { selectedBox.getChildren().clear(); List<StagedShare> active = working.values().stream().filter(s -> !s.removed).sorted(Comparator.comparing((StagedShare s) -> safeText(s.displayName), String.CASE_INSENSITIVE_ORDER).thenComparingInt(s -> s.contactId)).toList(); selectedHeading.setText("Selected Contacts (" + active.size() + ")"); if (active.isEmpty()) { Label empty = new Label("No Contacts selected."); empty.getStyleClass().add("search-summary-text"); selectedBox.getChildren().add(empty); } else for (StagedShare share : active) { Button details = caseLinkAction("Details", ControlStyles.Purpose.GHOST, ControlStyles.Size.SMALL, e -> showShareDetailsDialog(share.displayName, share.sharedAt, share.notes).ifPresent(d -> { share.sharedAt = d.sharedAt(); share.notes = d.notes(); share.dirty = true; refresh[0].run(); })); Button remove = caseLinkAction(share.shareId > 0 ? "Unshare" : "Remove", ControlStyles.Purpose.GHOST, ControlStyles.Size.SMALL, e -> { if (share.shareId > 0) { boolean ok = AppDialogs.showConfirmation(dialog.getDialogPane().getScene().getWindow(), "Unshare Contact", "Unshare this contact from the link?", "The Contact record is not deleted. The unshare is staged until the parent Link dialog is saved.", "Unshare", DialogActionKind.DANGER); if (!ok) return; share.removed = true; } else working.remove(share.contactId); refresh[0].run(); }); FlowPane actions = new FlowPane(6, 6, details, remove); actions.setPrefWrapLength(170); HBox row = new HBox(8, createShareContactCard(share), actions); row.getStyleClass().add("case-link-selected-contact-row"); row.setAlignment(Pos.CENTER_LEFT); row.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(row.getChildren().get(0), Priority.ALWAYS); selectedBox.getChildren().add(row); } renderCaseContactOptions(caseRows, caseOptions.get(), working, refresh[0]); allList.refresh(); };
			allList.setCellFactory(lv -> new javafx.scene.control.ListCell<>() { @Override protected void updateItem(CaseLinkContactOptionDto item, boolean empty) { super.updateItem(item, empty); getStyleClass().removeAll("case-link-contact-cell-selected"); setText(null); setGraphic(null); setAccessibleText(null); if (empty || item == null) return; boolean selected = working.containsKey(item.contactId()) && !working.get(item.contactId()).removed; setGraphic(createSelectableContactCard(item, selected, () -> { toggleWorking(working, item); refresh[0].run(); }, true)); if (selected) getStyleClass().add("case-link-contact-cell-selected"); setAccessibleText((selected ? "Selected " : "Not selected ") + item.displayName()); } });
			allList.setOnMouseClicked(e -> { CaseLinkContactOptionDto o = allList.getSelectionModel().getSelectedItem(); if (o != null) { toggleWorking(working, o); refresh[0].run(); } }); allList.setOnKeyPressed(e -> { if (e.getCode() == javafx.scene.input.KeyCode.SPACE || e.getCode() == javafx.scene.input.KeyCode.ENTER) { CaseLinkContactOptionDto o = allList.getSelectionModel().getSelectedItem(); if (o != null) { toggleWorking(working, o); refresh[0].run(); e.consume(); } } }); search.textProperty().addListener((obs, oldV, newV) -> { String q = safeText(newV).toLowerCase(Locale.ROOT); filtered.setPredicate(o -> q.isBlank() || safeText(o.displayName()).toLowerCase(Locale.ROOT).contains(q)); allList.setPlaceholder(new Label(q.isBlank() ? "No available Contacts." : "No Contacts match this search.")); });
			Label allContactsHeading = new Label("All Contacts"); allContactsHeading.getStyleClass().add("section-heading"); VBox allContactsSection = new VBox(8, allContactsHeading, searchRow, allList); allContactsSection.getStyleClass().addAll("case-link-dialog-section", "shale-embedded-card-surface"); VBox.setVgrow(allList, Priority.ALWAYS); Label caseContactsHeading = new Label("Case Contacts"); caseContactsHeading.getStyleClass().add("section-heading"); VBox selectedSection = new VBox(8, selectedHeading, selectedScroll); selectedSection.getStyleClass().addAll("case-link-dialog-section", "shale-embedded-card-surface"); VBox caseContactsSection = new VBox(8, caseContactsHeading, caseContactsScroll); caseContactsSection.getStyleClass().addAll("case-link-dialog-section", "shale-embedded-card-surface"); VBox content = new VBox(12, selectedSection, caseContactsSection, allContactsSection); content.getStyleClass().add("case-link-share-modal-form"); content.setPadding(new Insets(16)); content.setPrefWidth(760); content.setPrefHeight(620); VBox.setVgrow(allContactsSection, Priority.ALWAYS); dialog.getDialogPane().setContent(content); applyScreenSafeDialogBounds(dialog, modalOwner, 820, 700, 600, 460); refresh[0].run();
			AtomicBoolean open = new AtomicBoolean(true); dialog.setOnHidden(e -> open.set(false)); final int caseIdSnapshot = CaseController.this.caseId == null ? -1 : CaseController.this.caseId; final int tenantId = safeTenantId(); final int generation = ++caseLinkDialogLoadGeneration;
			caseLinkExecutor.submit(() -> { long started = System.nanoTime(); try { List<CaseLinkContactOptionDto> caseContacts = caseService == null ? List.of() : caseService.listCaseLinkShareCaseContacts(caseIdSnapshot, tenantId); Platform.runLater(() -> { if (!open.get() || generation != caseLinkDialogLoadGeneration || CaseController.this.caseId == null || caseIdSnapshot != CaseController.this.caseId) { LOG.info("Case Link share modal stale result op=list-case-contacts tenantId={} caseId={} requestId={} rows={} elapsedMs={}", tenantId, caseIdSnapshot, generation, caseContacts.size(), elapsedMs(started)); return; } LOG.info("Case Link share modal load success op=list-case-contacts tenantId={} caseId={} requestId={} rows={} elapsedMs={}", tenantId, caseIdSnapshot, generation, caseContacts.size(), elapsedMs(started)); caseOptions.set(List.copyOf(caseContacts)); renderCaseContactOptions(caseRows, caseOptions.get(), working, refresh[0]); }); } catch (RuntimeException ex) { LOG.warn("Case Link share modal load failure op=list-case-contacts tenantId={} caseId={} requestId={} elapsedMs={}", tenantId, caseIdSnapshot, generation, elapsedMs(started), ex); Platform.runLater(() -> { if (open.get() && generation == caseLinkDialogLoadGeneration) caseRows.getChildren().setAll(new Label("Unable to load Case Contacts.")); }); } });
			caseLinkExecutor.submit(() -> { long started = System.nanoTime(); try { List<CaseLinkContactOptionDto> all = caseService == null ? List.of() : caseService.listCaseLinkShareContacts(tenantId); Platform.runLater(() -> { if (!open.get() || generation != caseLinkDialogLoadGeneration || CaseController.this.caseId == null || caseIdSnapshot != CaseController.this.caseId) { LOG.info("Case Link share modal stale result op=list-all-contacts tenantId={} caseId={} requestId={} rows={} elapsedMs={}", tenantId, caseIdSnapshot, generation, all.size(), elapsedMs(started)); return; } LOG.info("Case Link share modal load success op=list-all-contacts tenantId={} caseId={} requestId={} rows={} elapsedMs={}", tenantId, caseIdSnapshot, generation, all.size(), elapsedMs(started)); allOptions.setAll(all); allList.setPlaceholder(new Label(all.isEmpty() ? "No available Contacts." : "No Contacts match this search.")); }); } catch (RuntimeException ex) { LOG.warn("Case Link share modal load failure op=list-all-contacts tenantId={} caseId={} requestId={} elapsedMs={}", tenantId, caseIdSnapshot, generation, elapsedMs(started), ex); Platform.runLater(() -> { if (open.get() && generation == caseLinkDialogLoadGeneration) allList.setPlaceholder(new Label("Unable to load All Contacts.")); }); } });
			dialog.setResultConverter(button -> button == ButtonType.APPLY ? working.values().stream().map(StagedShare::copy).toList() : null); return dialog.showAndWait();
		}
		private void renderCaseContactOptions(FlowPane caseRows, List<CaseLinkContactOptionDto> options, Map<Integer, StagedShare> working, Runnable refresh) {
			caseRows.getChildren().clear();
			if (options == null || options.isEmpty()) {
				Label empty = new Label("No available Case Contacts.");
				empty.getStyleClass().add("search-summary-text");
				caseRows.getChildren().add(empty);
				return;
			}
			for (CaseLinkContactOptionDto option : options) {
				boolean selected = working.containsKey(option.contactId()) && !working.get(option.contactId()).removed;
				caseRows.getChildren().add(createSelectableContactCard(option, selected, () -> { toggleWorking(working, option); refresh.run(); }, false));
			}
		}
		private Node createSelectableContactCard(CaseLinkContactOptionDto option, boolean selected, Runnable toggle, boolean cell) {
			ContactCardFactory factory = new ContactCardFactory(id -> { });
			ContactCard card = factory.create(new ContactCardFactory.ContactCardModel(option.contactId(), option.displayName(), null, null, null), ContactCardFactory.Variant.MINI);
			card.setInteractive(false);
			card.getStyleClass().addAll("shale-entity-card-selectable", "case-link-selectable-contact-card");
			if (selected) card.getStyleClass().add("case-link-selectable-contact-card-selected");
			card.setAccessibleText((selected ? "Selected " : "Not selected ") + option.displayName());
			StackPane wrapper = new StackPane(card);
			wrapper.getStyleClass().add("case-link-selectable-contact-wrapper");
			if (selected) {
				Label check = new Label("✓");
				check.getStyleClass().add("case-link-selection-checkmark");
				check.setMouseTransparent(true);
				check.setAccessibleText("Selected");
				wrapper.getChildren().add(check);
				wrapper.getStyleClass().add("case-link-selectable-contact-wrapper-selected");
				StackPane.setAlignment(check, Pos.TOP_RIGHT);
				StackPane.setMargin(check, new Insets(5, 5, 0, 0));
			}
			wrapper.setFocusTraversable(!cell);
			wrapper.setAccessibleText((selected ? "Selected " : "Not selected ") + option.displayName());
			wrapper.setOnMouseClicked(e -> { toggle.run(); e.consume(); });
			wrapper.setOnKeyPressed(e -> { if (e.getCode() == javafx.scene.input.KeyCode.SPACE || e.getCode() == javafx.scene.input.KeyCode.ENTER) { toggle.run(); e.consume(); } });
			return wrapper;
		}
		private void toggleWorking(Map<Integer, StagedShare> working, CaseLinkContactOptionDto option) { StagedShare existing = working.get(option.contactId()); if (existing != null && !existing.removed) { if (existing.shareId > 0) existing.removed = true; else working.remove(option.contactId()); return; } if (existing != null) { existing.removed = false; return; } working.put(option.contactId(), StagedShare.newShare(option.contactId(), option.displayName(), LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES), null)); }
	}

	private record ShareDetails(LocalDateTime sharedAt, String notes) {}

	private Optional<ShareDetails> showShareDetailsDialog(String contactName, LocalDateTime initialSharedAt, String initialNotes) {
		Dialog<ShareDetails> dialog = new Dialog<>(); dialog.setTitle("Share Details"); AppDialogs.applySecondaryDialogShell(dialog, "Share Details"); dialog.getDialogPane().getStyleClass().add("case-link-dialog-shell"); dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL); styleCaseLinkDialogButtons(dialog, ButtonType.OK, "Save");
		Label contact = new Label("Contact: " + blankTo(contactName, "Selected contact"));
		DatePicker date = new DatePicker((initialSharedAt == null ? LocalDateTime.now() : initialSharedAt).toLocalDate());
		TextField time = new TextField((initialSharedAt == null ? LocalDateTime.now() : initialSharedAt).toLocalTime().truncatedTo(ChronoUnit.MINUTES).toString());
		TextArea notes = new TextArea(safeText(initialNotes)); notes.setPrefRowCount(3); notes.setPromptText("Share Notes (not Link Notes)"); ControlStyles.formControl(date); ControlStyles.formControl(time); ControlStyles.formControl(notes);
		Label error = new Label(); error.setTextFill(Color.web("#b42318")); error.setVisible(false); error.setManaged(false);
		GridPane grid = new GridPane(); grid.setHgap(8); grid.setVgap(8); grid.add(contact, 0, 0, 2, 1); grid.addRow(1, new Label("Shared Date"), date); grid.addRow(2, new Label("Shared Time"), time); grid.addRow(3, new Label("Share Notes"), notes); grid.add(error, 0, 4, 2, 1); dialog.getDialogPane().setContent(grid);
		AtomicBoolean validationVisible = new AtomicBoolean(false);
		Runnable updateInvalid = () -> {
			boolean show = validationVisible.get();
			ControlStyles.setInvalid(date, show && date.getValue() == null);
			boolean invalidTime;
			try { java.time.LocalTime.parse(safeText(time.getText()).trim()); invalidTime = false; } catch (RuntimeException invalid) { invalidTime = true; }
			ControlStyles.setInvalid(time, show && invalidTime);
			ControlStyles.setInvalid(notes, show && notes.getText() != null && notes.getText().trim().length() > 500);
		};
		date.valueProperty().addListener((o,a,b) -> updateInvalid.run()); time.textProperty().addListener((o,a,b) -> updateInvalid.run()); notes.textProperty().addListener((o,a,b) -> updateInvalid.run());
		final ShareDetails[] result = new ShareDetails[1];
		dialog.getDialogPane().lookupButton(ButtonType.OK).addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
			try { LocalDate d = date.getValue(); if (d == null) throw new IllegalArgumentException("Shared date is required."); java.time.LocalTime t = java.time.LocalTime.parse(time.getText().trim()); String n = trimLimit(notes.getText(), "Share Notes", 500, false); result[0] = new ShareDetails(LocalDateTime.of(d, t), n); validationVisible.set(false); updateInvalid.run(); error.setVisible(false); error.setManaged(false); }
			catch (RuntimeException ex) { result[0] = null; validationVisible.set(true); updateInvalid.run(); error.setText(rootMessage(ex)); error.setVisible(true); error.setManaged(true); event.consume(); }
		});
		dialog.setResultConverter(button -> button == ButtonType.OK ? result[0] : null);
		return dialog.showAndWait();
	}

	private static final class StagedShare {
		long shareId; int contactId; String displayName; boolean unavailable; LocalDateTime sharedAt; String notes; byte[] rowVer; boolean dirty; boolean removed;
		static StagedShare persisted(CaseLinkShareDto dto) { StagedShare s = new StagedShare(); s.shareId = dto.caseLinkShareId(); s.contactId = dto.contactId(); s.displayName = blankTo(dto.contactDisplayName(), "Contact #" + dto.contactId()); s.unavailable = dto.contactUnavailable(); s.sharedAt = dto.sharedAt() == null ? LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES) : dto.sharedAt(); s.notes = dto.notes(); s.rowVer = dto.rowVer() == null ? null : dto.rowVer().clone(); s.originalSharedAt = s.sharedAt; s.originalNotes = s.notes; return s; }
		static StagedShare newShare(int contactId, String displayName, LocalDateTime sharedAt, String notes) { StagedShare s = new StagedShare(); s.contactId = contactId; s.displayName = blankTo(displayName, "Contact #" + contactId); s.sharedAt = sharedAt; s.notes = notes; s.dirty = true; return s; }
		static StagedShare copy(StagedShare src) { StagedShare s = new StagedShare(); s.shareId = src.shareId; s.contactId = src.contactId; s.displayName = src.displayName; s.unavailable = src.unavailable; s.sharedAt = src.sharedAt; s.notes = src.notes; s.rowVer = src.rowVer == null ? null : src.rowVer.clone(); s.dirty = src.dirty; s.removed = src.removed; s.originalSharedAt = src.originalSharedAt; s.originalNotes = src.originalNotes; return s; }
		LocalDateTime originalSharedAt; String originalNotes;
		boolean changedFromOriginal() { return dirty || !Objects.equals(sharedAt, originalSharedAt) || !Objects.equals(safeText(notes), safeText(originalNotes)); }
	}

	private static CaseLinkShareLiveChanges shareLiveChangesForCreate(List<CaseServicePort.CaseLinkShareDraft> adds) {
		return new CaseLinkShareLiveChanges(
				(adds == null ? List.<CaseServicePort.CaseLinkShareDraft>of() : adds).stream()
						.map(add -> new CaseLinkShareLiveChange(null, add.contactId()))
						.toList(),
				List.of(),
				List.of());
	}

	private static CaseLinkShareLiveChanges shareLiveChangesForUpdate(
			List<CaseServicePort.CaseLinkShareDraft> adds,
			List<CaseServicePort.CaseLinkShareUpdate> updates,
			List<CaseServicePort.CaseLinkShareRemoval> removals,
			List<CaseLinkShareDto> originalShares) {
		Map<Long, Integer> originalContactByShareId = (originalShares == null ? List.<CaseLinkShareDto>of() : originalShares).stream()
				.collect(Collectors.toMap(CaseLinkShareDto::caseLinkShareId, CaseLinkShareDto::contactId, (a, b) -> a));
		return new CaseLinkShareLiveChanges(
				(adds == null ? List.<CaseServicePort.CaseLinkShareDraft>of() : adds).stream()
						.map(add -> new CaseLinkShareLiveChange(null, add.contactId()))
						.toList(),
				(updates == null ? List.<CaseServicePort.CaseLinkShareUpdate>of() : updates).stream()
						.map(update -> new CaseLinkShareLiveChange(update.caseLinkShareId(), update.contactId()))
						.toList(),
				(removals == null ? List.<CaseServicePort.CaseLinkShareRemoval>of() : removals).stream()
						.map(removal -> new CaseLinkShareLiveChange(removal.caseLinkShareId(), originalContactByShareId.get(removal.caseLinkShareId())))
						.filter(change -> change.contactId() != null)
						.toList());
	}

	private record CaseLinkShareLiveChange(Long caseLinkShareId, Integer contactId) {}

	private record CaseLinkShareLiveChanges(List<CaseLinkShareLiveChange> added, List<CaseLinkShareLiveChange> updated, List<CaseLinkShareLiveChange> removed) {
		private static final CaseLinkShareLiveChanges NONE = new CaseLinkShareLiveChanges(List.of(), List.of(), List.of());
		private CaseLinkShareLiveChanges {
			added = added == null ? List.of() : List.copyOf(added);
			updated = updated == null ? List.of() : List.copyOf(updated);
			removed = removed == null ? List.of() : List.copyOf(removed);
		}
		private boolean isEmpty() { return added.isEmpty() && updated.isEmpty() && removed.isEmpty(); }
	}

	static CaseLinkInput validateCaseLinkDialogInput(LinkTypeDto selected, String name, String url, String description, boolean primary, String notes, List<CaseServicePort.CaseLinkShareDraft> shareAdds, List<CaseServicePort.CaseLinkShareUpdate> shareUpdates, List<CaseServicePort.CaseLinkShareRemoval> shareRemovals) {
		if (selected == null) throw new IllegalArgumentException("Link Type is required.");
		if (!selected.active()) throw new IllegalArgumentException("Select an active Link Type before saving.");
		String display = trimLimit(name, "Display name", 255, true);
		String rawUrl = trimLimit(url, "URL", 2048, true);
		String cleanUrl = CaseLinkUrlNormalizer.normalize(rawUrl);
		String desc = trimLimit(description, "Description", 2048, false);
		String note = trimLimit(notes, "Notes", 2000, false);
		return new CaseLinkInput(selected, display, cleanUrl, desc, primary, note, shareAdds == null ? List.of() : List.copyOf(shareAdds), shareUpdates == null ? List.of() : List.copyOf(shareUpdates), shareRemovals == null ? List.of() : List.copyOf(shareRemovals));
	}

	static CaseLinkInput validateCaseLinkDialogInput(LinkTypeDto selected, String name, String url, String description, boolean primary, String notes) { return validateCaseLinkDialogInput(selected, name, url, description, primary, notes, List.of(), List.of(), List.of()); }

	private void focusFirstInvalidCaseLinkField(ComboBox<LinkTypeDto> type, TextField name, TextField url, TextArea description, TextArea notes) {
		if (type.getValue() == null || !type.getValue().active()) type.requestFocus();
		else if (blank(name.getText()) || name.getText().trim().length() > 255) name.requestFocus();
		else if (blank(url.getText()) || url.getText().trim().length() > 2048) url.requestFocus();
		else if (description != null && description.getText() != null && description.getText().trim().length() > 2048) description.requestFocus();
		else if (notes != null && notes.getText() != null && notes.getText().trim().length() > 2000) notes.requestFocus();
	}

	private void setCaseLinkControlsDisabled(boolean disabled) { if (addCaseLinkButton != null) addCaseLinkButton.setDisable(disabled); }
	private int safeTenantId() { Integer id = appState == null ? null : appState.getShaleClientId(); return id == null ? -1 : id; }
	private int safeActorUserId() { Integer id = appState == null ? null : appState.getUserId(); return id == null ? -1 : id; }
	private static long elapsedMs(long started) { return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started); }
	private static Long resolvedCaseLinkId(Long fallback, Object result) { return result instanceof CaseLinkDto dto ? dto.caseLinkId() : fallback; }

	private static boolean isPrimaryCaseLinkConflict(Throwable ex) {
		for (Throwable cur = ex; cur != null; cur = cur.getCause()) {
			String message = cur.getMessage();
			if (message != null && message.contains("Primary Link changed while you were saving")) return true;
		}
		return false;
	}

	private static String caseLinkUserMessage(Throwable ex) {
		String message = ex == null ? null : ex.getMessage();
		return message == null || message.isBlank() ? rootMessage(ex) : message;
	}

	private static String rootMessage(Throwable ex) { Throwable cur = ex; while (cur != null && cur.getCause() != null) cur = cur.getCause(); String msg = cur == null ? null : cur.getMessage(); return msg == null || msg.isBlank() ? "Unexpected error." : msg; }
	private static boolean blank(String text) { return text == null || text.trim().isEmpty(); }
	private static String blankTo(String text, String fallback) { return blank(text) ? fallback : text.trim(); }
	private static String trimLimit(String value, String label, int max, boolean required) { String out = value == null ? "" : value.trim(); if (required && out.isBlank()) throw new IllegalArgumentException(label + " is required."); if (out.length() > max) throw new IllegalArgumentException(label + " must be " + max + " characters or fewer."); return out.isBlank() ? null : out; }
	private Window caseLinksOwner() { return caseLinksTabPane != null && caseLinksTabPane.getScene() != null ? caseLinksTabPane.getScene().getWindow() : taskDialogOwner(); }
	private int requireTenantId() { Integer id = appState == null ? null : appState.getShaleClientId(); if (id == null || id <= 0) throw new IllegalStateException("No tenant is selected."); return id; }
	private int requireActorUserId() { Integer id = appState == null ? null : appState.getUserId(); if (id == null || id <= 0) throw new IllegalStateException("No active user is selected."); return id; }
	record CaseLinkInput(LinkTypeDto linkType, String displayName, String url, String description, boolean primary, String notes, List<CaseServicePort.CaseLinkShareDraft> shareAdds, List<CaseServicePort.CaseLinkShareUpdate> shareUpdates, List<CaseServicePort.CaseLinkShareRemoval> shareRemovals) {}

	private void refreshCaseCalendar() {
		caseCalendarStale = true;
		if (isSectionActive("Calendar")) {
			loadCaseCalendarAsync();
		}
	}

	private void onCaseCalendarNewEvent() {
		if (calendarService == null || appState == null || caseId == null) return;
		Integer tenantId = appState.getShaleClientId();
		if (tenantId == null || tenantId <= 0) return;
		new Thread(() -> {
			try {
				var eventTypes = calendarService.listEffectiveEventTypes(tenantId);
				List<NewCalendarEventDialog.CaseOption> caseOptions = caseOptionForCurrentCase();
				runOnFx(() -> {
					Optional<NewCalendarEventDialog.CreateCalendarEventInput> input = NewCalendarEventDialog.showAndWait(
							caseCalendarOwner(), eventTypes, LocalDate.now(), caseOptions, List.of(), caseOptions.isEmpty() ? null : caseOptions.getFirst());
					input.ifPresent(value -> {
						LocalDateTime startsAt = value.allDay() ? value.date().atStartOfDay() : value.date().atTime(value.startTime());
						LocalDateTime endsAt = value.allDay() ? null : startsAt.plusMinutes(value.durationMinutes());
						CalendarEvent event = new CalendarEvent(null, tenantId, value.calendarEventTypeId(), caseId, null, value.title(), value.description(), startsAt, endsAt, value.allDay(), "MANUAL", null, null, value.assignedToUserId(), false, false, appState.getUserId(), null, null);
						new Thread(() -> {
							calendarService.createEvent(event);
							runOnFx(this::refreshCaseCalendar);
						}, "case-calendar-create-event-" + caseId).start();
					});
				});
			} catch (RuntimeException ex) {
				runOnFx(() -> showCaseCalendarMessage("Unable to open the event dialog."));
			}
		}, "case-calendar-new-event-" + caseId).start();
	}

	private void openCaseCalendarEventEditor(int eventId) {
		if (calendarService == null || appState == null) return;
		Integer tenantId = appState.getShaleClientId();
		if (tenantId == null || tenantId <= 0 || !openingCaseCalendarEventIds.add(eventId)) return;
		final Integer expectedCaseId = caseId;
		new Thread(() -> {
			try {
				CalendarEvent event = calendarService.getEventById(eventId, tenantId);
				if (event == null) { runOnFx(() -> openingCaseCalendarEventIds.remove(eventId)); return; }
				var types = calendarService.listEffectiveEventTypes(tenantId);
				var initial = new NewCalendarEventDialog.CreateCalendarEventInput(event.title(), event.calendarEventTypeId(), event.startsAt().toLocalDate(), event.allDay(), event.allDay() ? null : event.startsAt().toLocalTime(), 60, event.description(), event.caseId(), event.assignedToUserId());
				runOnFx(() -> {
					openingCaseCalendarEventIds.remove(eventId);
					if (!Objects.equals(caseId, expectedCaseId) || !Objects.equals(appState.getShaleClientId(), tenantId)) return;
					NewCalendarEventDialog.showEditDialog(caseCalendarOwner(), types, initial, input -> {
					LocalDateTime startsAt = input.allDay() ? input.date().atStartOfDay() : input.date().atTime(input.startTime());
					LocalDateTime endsAt = input.allDay() ? null : startsAt.plusMinutes(input.durationMinutes());
					calendarService.updateEvent(new CalendarEvent(event.calendarEventId(), event.shaleClientId(), input.calendarEventTypeId(), input.caseId(), event.taskId(), input.title(), input.description(), startsAt, endsAt, input.allDay(), event.sourceType(), event.sourceField(), event.sourceId(), input.assignedToUserId(), event.completed(), event.cancelled(), appState.getUserId(), event.createdAt(), event.updatedAt()));
					refreshCaseCalendar();
					return null;
				}, () -> {
					calendarService.deleteCalendarEvent(event.calendarEventId(), tenantId);
					refreshCaseCalendar();
					return null;
				}, null, null, caseOptionForCurrentCase(), List.of());
				});
			} catch (RuntimeException ex) {
				runOnFx(() -> { openingCaseCalendarEventIds.remove(eventId); if (Objects.equals(caseId, expectedCaseId)) showCaseCalendarMessage("Unable to open this event."); });
			}
		}, "case-calendar-open-event-" + eventId).start();
	}

	private List<NewCalendarEventDialog.CaseOption> caseOptionForCurrentCase() {
		String name = caseTitleLabel == null ? "Case #" + caseId : caseTitleLabel.getText();
		if (currentOverview != null && !safeText(currentOverview.getCaseName()).isBlank()) name = currentOverview.getCaseName();
		return List.of(new NewCalendarEventDialog.CaseOption(caseId, name, null, null, false));
	}

	private Window caseCalendarOwner() {
		if (caseCalendarTabPane != null && caseCalendarTabPane.getScene() != null) return caseCalendarTabPane.getScene().getWindow();
		return taskDialogOwner();
	}

	private Window caseOverviewOwner() {
		if (overviewScrollPane != null && overviewScrollPane.getScene() != null) return overviewScrollPane.getScene().getWindow();
		return taskDialogOwner();
	}

	private void showOverview() {
		attachCaseUpdatesPane(CaseUpdatesPlacement.RIGHT);
		setPaneVisible(overviewScrollPane, true);
		setPaneVisible(detailsSectionPane, false);
		setPaneVisible(tasksTabPane, false);
		setPaneVisible(caseCalendarTabPane, false);
		setPaneVisible(caseDatesTabPane, false);
		setPaneVisible(caseRequestsTabPane, false);
		setPaneVisible(caseLinksTabPane, false);
		setPaneVisible(genericPane, false);
		setPaneVisible(tasksPanel, true);
		if (contentTitleLabel != null)
			contentTitleLabel.setText("Overview");
		loadCaseUpdatesAsync();
		loadOverviewOnce();
		loadOverviewPrimaryLinkIfNeeded();
		auditCaseRead("Case.Overview.Read", "Case.Overview");
	}

	private void showTasksTab() {
		attachCaseUpdatesPane(CaseUpdatesPlacement.RIGHT);
		setPaneVisible(overviewScrollPane, false);
		setPaneVisible(detailsSectionPane, false);
		setPaneVisible(tasksTabPane, true);
		setPaneVisible(caseCalendarTabPane, false);
		setPaneVisible(caseDatesTabPane, false);
		setPaneVisible(caseRequestsTabPane, false);
		setPaneVisible(caseLinksTabPane, false);
		setPaneVisible(genericPane, false);
		setPaneVisible(tasksPanel, false);
		if (shouldReloadTasksForTabOpen()) {
			loadCaseTasksAsync();
		}
		renderTasksSection();
		loadCaseUpdatesAsync();
	}

	private boolean shouldReloadTasksForTabOpen() {
		return !caseTasksLoadedOnce || caseTasksStale;
	}

	private void showDetails() {
		attachCaseUpdatesPane(CaseUpdatesPlacement.RIGHT);
		setPaneVisible(overviewScrollPane, false);
		setPaneVisible(detailsSectionPane, true);
		setPaneVisible(tasksTabPane, false);
		setPaneVisible(caseCalendarTabPane, false);
		setPaneVisible(caseDatesTabPane, false);
		setPaneVisible(caseRequestsTabPane, false);
		setPaneVisible(caseLinksTabPane, false);
		setPaneVisible(genericPane, false);
		setPaneVisible(tasksPanel, false);
		if (contentTitleLabel != null)
			contentTitleLabel.setText("Details");
		renderDetailsFromCurrent();
		loadCaseUpdatesAsync();
		auditCaseRead("Case.Detail.Read", "Case.Detail");
	}

	private void showGeneric(String sectionName) {
		setUpdatesPaneVisible(false);
		setPaneVisible(overviewScrollPane, false);
		setPaneVisible(detailsSectionPane, false);
		setPaneVisible(tasksTabPane, false);
		setPaneVisible(caseCalendarTabPane, false);
		setPaneVisible(caseDatesTabPane, false);
		setPaneVisible(caseRequestsTabPane, false);
		setPaneVisible(caseLinksTabPane, false);
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
		attachCaseUpdatesPane(CaseUpdatesPlacement.RIGHT);
		setPaneVisible(overviewScrollPane, false);
		setPaneVisible(detailsSectionPane, false);
		setPaneVisible(tasksTabPane, false);
		setPaneVisible(caseCalendarTabPane, false);
		setPaneVisible(caseDatesTabPane, false);
		setPaneVisible(caseRequestsTabPane, false);
		setPaneVisible(caseLinksTabPane, false);
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
		showTimelineLoadingState();
		auditCaseRead("Case.Timeline.Read", "Case.Timeline");
		loadCaseUpdatesAsync();
		loadCaseTimelineEventsAsync();
	}

	private void showParties() {
		attachCaseUpdatesPane(CaseUpdatesPlacement.RIGHT);
		setPaneVisible(overviewScrollPane, false);
		setPaneVisible(detailsSectionPane, false);
		setPaneVisible(tasksTabPane, false);
		setPaneVisible(caseCalendarTabPane, false);
		setPaneVisible(caseDatesTabPane, false);
		setPaneVisible(caseRequestsTabPane, false);
		setPaneVisible(caseLinksTabPane, false);
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
		loadCaseUpdatesAsync();
		if (caseDao != null && caseId != null && (!partiesLoadedOnce || caseParties == null || caseParties.isEmpty())) {
			refreshPartiesSectionAsync();
		}
	}

	private void auditCaseRead(String fieldName, String screenName) {
		if (phiReadAuditService == null || caseId == null || caseId <= 0) {
			return;
		}
		phiReadAuditService.auditRead(fieldName, screenName, "Case", caseId.longValue());
	}

	private void showTimelineLoadingState() {
		if (timelineListBox != null) {
			timelineListBox.getChildren().clear();
		}
		if (timelineEmptyLabel != null) {
			timelineEmptyLabel.setText("Loading timeline…");
		}
		setVisibleManaged(timelineEmptyLabel, true);
		if (timelineScrollPane != null) {
			timelineScrollPane.setVvalue(0.0);
		}
	}

	private void loadCaseTimelineEventsAsync() {
		if (caseDao == null || caseId == null) {
			renderTimelineEvents(List.of());
			return;
		}

		final int activeCaseId = caseId;
		new Thread(() ->
		{
			try {
				List<CaseTimelineEventDto> events = caseDao.listCaseTimelineEvents(activeCaseId);
				runOnFx(() ->
				{
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
			if (timelineEmptyLabel != null) {
				timelineEmptyLabel.setText("No timeline events yet.");
			}
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
		Label titleLabel = new Label(timelineDescription(event));
		titleLabel.setStyle("-fx-font-weight: bold;");
		titleLabel.setWrapText(true);

		Label actorLabel = new Label(safeText(event.getEventType()).isBlank() ? "Historical event" : friendlyTimelineType(event.getEventType()));
		actorLabel.setStyle("-fx-opacity: 0.85;");

		Label timestampLabel = new Label(formatDateTime(event.getOccurredAt()));
		timestampLabel.setStyle("-fx-opacity: 0.75;");

		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		HBox metaRow = new HBox(8, actorLabel, spacer, timestampLabel);
		metaRow.setAlignment(Pos.CENTER_LEFT);

		VBox content = new VBox(6, titleLabel, metaRow);
		VBox card = new VBox(content);
		card.setPadding(new Insets(10, 12, 10, 12));
		card.getStyleClass().addAll("secondary-panel", "shale-entity-card", "shale-entity-card-embedded");
		return card;
	}

	static String timelineDescription(CaseTimelineEventDto event) {
		if (event == null) return "Case activity recorded.";
		String actor = safeText(event.getActorDisplayName()).trim();
		if (actor.isBlank()) actor = "System";
		String title = safeText(event.getTitle()).trim();
		String body = safeText(event.getBody()).trim();
		if (title.isBlank()) title = friendlyTimelineType(event.getEventType());
		String phrase = switch (safeText(event.getEventType())) {
			case CaseDao.CaseTimelineEventTypes.STATUS_CHANGED -> "changed Status";
			case CaseDao.CaseTimelineEventTypes.RESPONSIBLE_ATTORNEY_CHANGED -> "changed Responsible Attorney";
			case CaseDao.CaseTimelineEventTypes.CASE_DELETED -> "deleted the Case";
			case CaseDao.CaseTimelineEventTypes.CASE_RESTORED -> "restored the Case";
			default -> title;
		};
		return actor + " " + phrase + (body.isBlank() ? "." : " " + body + ".");
	}

	private static String friendlyTimelineType(String eventType) {
		String value = safeText(eventType).trim();
		if (value.isBlank()) return "Case activity";
		String normalized = value.toLowerCase(Locale.ROOT).replace('_', ' ');
		return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
	}

	private void renderPartiesSection() {
		if (timelineListBox == null)
			return;

		if (!isSectionActive("Parties")) {
			return;
		}

		timelineListBox.getChildren().clear();
		List<CasePartyDto> safeParties = caseParties == null ? List.of() : caseParties;
		if (safeParties.isEmpty()) {
			if (timelineEmptyLabel != null)
				timelineEmptyLabel.setText("No parties yet.");
			setVisibleManaged(timelineEmptyLabel, true);
			return;
		}

		setVisibleManaged(timelineEmptyLabel, false);

		renderPartyGroups(timelineListBox, safeParties, PartyRenderMode.MANAGE, 300, false);
	}

	private void renderOverviewPartiesSection() {
		if (ovPartiesBox == null)
			return;
		ovPartiesBox.getChildren().clear();
		List<CasePartyDto> safeParties = caseParties == null ? List.of() : caseParties;
		if (safeParties.isEmpty()) {
			ovPartiesBox.getChildren().add(createOverviewPartyRow("—", "No parties added."));
			return;
		}

		Map<String, List<CasePartyDto>> grouped = safeParties.stream()
				.filter(Objects::nonNull)
				.collect(Collectors.groupingBy(
						p -> normalizedPartySideKey(p.getSide()),
						LinkedHashMap::new,
						Collectors.toList()));
		Map<String, String> sideLabelsByKey = loadPartySideLabelMap();
		for (String sideKey : List.of("represented", "opposing", "neutral", "unclassified")) {
			List<CasePartyDto> group = grouped.get(sideKey);
			if (group == null || group.isEmpty()) {
				continue;
			}
			Label heading = new Label(toPartySideLabel(sideLabelsByKey, sideKey));
			heading.getStyleClass().add("case-overview-party-side");
			ovPartiesBox.getChildren().add(heading);
			group.stream()
					.sorted((a, b) ->
					{
						int primaryCompare = Boolean.compare(b.isPrimary(), a.isPrimary());
						if (primaryCompare != 0)
							return primaryCompare;
						return safeText(a.getDisplayName()).compareToIgnoreCase(safeText(b.getDisplayName()));
					})
					.map(this::createOverviewPartyRow)
					.forEach(ovPartiesBox.getChildren()::add);
		}
	}

	private Node createOverviewPartyRow(String role, String name) {
		return createOverviewPartyRowContent(role, new Label(safeText(name).isBlank() ? "—" : name));
	}

	private Node createOverviewPartyRow(CasePartyDto party) {
		String role = formatOverviewPartyRelationshipMeta(
				toPartyRoleLabel(party.getPartyRoleName(), party.getPartyRoleId()),
				party.isPrimary());
		Node card = createPartyEntityCard(party, 300, PartyRenderMode.READ_ONLY_MINI);
		return createOverviewPartyRowContent(role, card);
	}

	private Node createOverviewPartyRowContent(String role, Node valueNode) {
		Label roleLabel = new Label(safeText(role).isBlank() ? "—" : role.replace(" · ", " • "));
		roleLabel.getStyleClass().add("case-overview-party-role");
		roleLabel.setMinWidth(170);
		roleLabel.setPrefWidth(170);
		if (valueNode instanceof Label label) {
			label.getStyleClass().add("case-overview-party-name");
			label.setWrapText(true);
		}
		HBox row = new HBox(14, roleLabel, valueNode);
		row.getStyleClass().add("case-overview-party-row");
		HBox.setHgrow(valueNode, Priority.ALWAYS);
		return row;
	}

	private void renderPartyGroups(VBox target, List<CasePartyDto> parties, PartyRenderMode mode, double entityCardWidth, boolean compactHeadings) {
		Map<String, List<CasePartyDto>> grouped = parties.stream()
				.filter(Objects::nonNull)
				.collect(Collectors.groupingBy(
						p -> normalizedPartySideKey(p.getSide()),
						LinkedHashMap::new,
						Collectors.toList()));
		Map<String, String> sideLabelsByKey = loadPartySideLabelMap();
		target.setSpacing(mode == PartyRenderMode.MANAGE ? 6 : 8);
		List<String> sideOrder = List.of("represented", "opposing", "neutral", "unclassified");
		for (String sideKey : sideOrder) {
			List<CasePartyDto> group = grouped.get(sideKey);
			if (group == null || group.isEmpty()) {
				continue;
			}
			Label heading = new Label(toPartySideLabel(sideLabelsByKey, sideKey));
			heading.setStyle(compactHeadings
					? "-fx-font-size: 13px; -fx-font-weight: bold; -fx-opacity: 0.9;"
					: "-fx-font-size: 14px; -fx-font-weight: bold; -fx-opacity: 0.92;");
			target.getChildren().add(heading);

			List<CasePartyDto> sorted = group.stream()
					.sorted((a, b) ->
					{
						int primaryCompare = Boolean.compare(b.isPrimary(), a.isPrimary());
						if (primaryCompare != 0)
							return primaryCompare;
						return safeText(a.getDisplayName()).compareToIgnoreCase(safeText(b.getDisplayName()));
					})
					.toList();

			for (CasePartyDto party : sorted) {
				target.getChildren().add(createPartyCard(party, sideLabelsByKey, mode, entityCardWidth));
			}
		}
	}

	private Node createPartyCard(CasePartyDto party, Map<String, String> sideLabelsByKey, PartyRenderMode mode, double entityCardWidth) {
		String roleLabel = toPartyRoleLabel(party.getPartyRoleName(), party.getPartyRoleId());
		String sideLabel = toPartySideLabel(sideLabelsByKey, normalizedPartySideKey(party.getSide()));
		String notes = safeText(party.getNotes()).trim();
		Node summaryCard = createPartyEntityCard(party, entityCardWidth, mode);

		String metadata = (mode == PartyRenderMode.READ_ONLY_MINI)
				? formatOverviewPartyRelationshipMeta(roleLabel, party.isPrimary())
				: formatPartyRelationshipMeta(roleLabel, sideLabel, party.isPrimary());
		Label metaLabel = new Label(metadata);
		metaLabel.setStyle(mode == PartyRenderMode.READ_ONLY_MINI
				? "-fx-opacity: 0.72; -fx-font-size: 11px;"
				: "-fx-opacity: 0.86;");
		metaLabel.setWrapText(true);

		VBox content = new VBox(mode == PartyRenderMode.READ_ONLY_MINI ? 2 : 4, summaryCard, metaLabel);
		content.setAlignment(Pos.TOP_LEFT);
		if (!notes.isBlank()) {
			Label notesLabel = new Label(notes);
			notesLabel.setWrapText(true);
			notesLabel.setStyle(mode == PartyRenderMode.READ_ONLY_MINI
					? "-fx-opacity: 0.68; -fx-font-size: 11px;"
					: "-fx-opacity: 0.9;");
			content.getChildren().add(notesLabel);
		}

		VBox card = new VBox(mode == PartyRenderMode.READ_ONLY_MINI ? 2 : 4, content);
		if (mode == PartyRenderMode.MANAGE) {
			Button editButton = new Button("Edit");
			editButton.getStyleClass().addAll("app-toolbar-button", "app-toolbar-button-neutral", "app-toolbar-button-compact");
			editButton.setOnAction(e -> onEditParty(party));

			Button removeButton = new Button("Remove");
			removeButton.getStyleClass().addAll("app-toolbar-button", "app-toolbar-button-danger", "app-toolbar-button-compact");
			removeButton.setOnAction(e -> onRemoveParty(party));

			VBox actions = new VBox(6, editButton, removeButton);
			actions.setAlignment(Pos.TOP_RIGHT);

			HBox row = new HBox(10, content, actions);
			row.setAlignment(Pos.TOP_LEFT);
			HBox.setHgrow(content, Priority.ALWAYS);

			card.getChildren().setAll(row);
			card.setPadding(new Insets(8, 10, 8, 10));
			card.getStyleClass().addAll("secondary-panel", "shale-entity-card", "shale-entity-card-embedded");
		} else {
			card.setPadding(new Insets(1, 0, 1, 0));
		}
		return card;
	}

	private Node createPartyEntityCard(CasePartyDto party, double partiesCardWidth, PartyRenderMode mode) {
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
					party.getPhone(),
					party.getEmail(),
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
			OrganizationCardFactory.Variant variant = OrganizationCardFactory.Variant.COMPACT;
			OrganizationCard card = factory.create(model, variant);
			card.setSuppressPlaceholderLines(true);
			card.setMinWidth(partiesCardWidth);
			card.setPrefWidth(partiesCardWidth);
			card.setMaxWidth(partiesCardWidth);
			return card;
		}

		if ("contact".equals(entityType) && party.getContactId() != null) {
			ContactCardFactory factory = contactCardFactory != null
					? contactCardFactory
					: new ContactCardFactory(onOpenContact == null ? id ->
					{
					} : onOpenContact);
			ContactCardFactory.ContactCardModel model = new ContactCardFactory.ContactCardModel(
					party.getContactId().intValue(),
					safeText(party.getDisplayName()),
					null,
					party.getEmail(),
					party.getPhone()
			);
			ContactCardFactory.Variant variant = ContactCardFactory.Variant.COMPACT;
			ContactCard card = factory.create(model, variant);
			card.setSuppressPlaceholderLines(true);
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

	private String formatOverviewPartyRelationshipMeta(String roleLabel, boolean primary) {
		return primary ? roleLabel + " · Primary" : roleLabel;
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
		List<PartySideOption> cached = cachedPartySideOptions;
		if (cached != null && !cached.isEmpty()) {
			return cached;
		}
		if (caseDao == null) {
			List<PartySideOption> defaults = defaultPartySideOptions();
			cachedPartySideOptions = defaults;
			return defaults;
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
				List<PartySideOption> defaults = defaultPartySideOptions();
				cachedPartySideOptions = defaults;
				return defaults;
			}
			out.add(new PartySideOption("Unaffiliated", null));
			List<PartySideOption> loaded = List.copyOf(out);
			cachedPartySideOptions = loaded;
			return loaded;
		} catch (Exception ignored) {
			List<PartySideOption> defaults = defaultPartySideOptions();
			cachedPartySideOptions = defaults;
			return defaults;
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

	private List<CaseDao.StatusRow> statusesForTenantCached(int tenantId) {
		if (tenantId <= 0 || caseDao == null) {
			return List.of();
		}
		return statusesByTenantCache.computeIfAbsent(tenantId, key ->
		{
			List<CaseDao.StatusRow> statuses = caseDao.listStatusesForTenant(key);
			return statuses == null ? List.of() : List.copyOf(statuses);
		});
	}

	private List<CaseDao.PracticeAreaRow> practiceAreasForTenantCached(int tenantId) {
		if (tenantId <= 0 || caseDao == null) {
			return List.of();
		}
		return practiceAreasByTenantCache.computeIfAbsent(tenantId, key ->
		{
			List<CaseDao.PracticeAreaRow> areas = caseDao.listPracticeAreasForTenant(key);
			return areas == null ? List.of() : List.copyOf(areas);
		});
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
		final long activeCaseId = caseId.longValue();
		setPartyDialogLoading(true);
		loadPartyDialogDataAsync(activeCaseId, data ->
		{
			setPartyDialogLoading(false);
			PartyAddWorkflowDialog.AddPartyDraft draft = showAddPartyWizardDialog(data);
			if (draft == null)
				return;
			new Thread(() ->
			{
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
		});
	}

	private void onEditParty(CasePartyDto party) {
		if (party == null || caseDao == null || caseId == null)
			return;
		final long activeCaseId = caseId.longValue();
		setPartyDialogLoading(true);
		loadPartyDialogDataAsync(activeCaseId, data ->
		{
			setPartyDialogLoading(false);
			PartyEditorResult result = showPartyEditorDialog(party, data);
			if (result == null)
				return;
			new Thread(() ->
			{
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
		});
	}

	private void setPartyDialogLoading(boolean loading) {
		if (addOrganizationButton == null) {
			return;
		}
		addOrganizationButton.setDisable(loading);
		if (loading && isSectionActive("Parties")) {
			addOrganizationButton.setText("Loading…");
		} else if (isSectionActive("Parties")) {
			addOrganizationButton.setText("Add Party");
		}
	}

	private void loadPartyDialogDataAsync(long activeCaseId, Consumer<PartyDialogData> onLoaded) {
		new Thread(() ->
		{
			try {
				PartyDialogData data = loadPartyDialogData(activeCaseId);
				runOnFx(() ->
				{
					if (caseId == null || caseId.longValue() != activeCaseId) {
						return;
					}
					if (onLoaded != null) {
						onLoaded.accept(data);
					}
				});
			} catch (Exception ex) {
				runOnFx(() ->
				{
					setPartyDialogLoading(false);
					showError("Failed to load party options. " + ex.getMessage());
				});
			}
		}, "case-party-dialog-load-" + activeCaseId).start();
	}

	private PartyDialogData loadPartyDialogData(long activeCaseId) {
		List<CaseDao.PartyRoleRow> partyRoles = caseDao.listPartyRoles();
		List<CaseDao.SelectableContactRow> contacts = caseDao.findLinkableContacts(activeCaseId);
		List<CaseDao.SelectableOrganizationRow> organizations = caseDao.findLinkableOrganizations(activeCaseId);
		List<PartySideOption> sideOptions = loadPartySideOptions();
		List<OrganizationDao.OrganizationTypeRow> organizationTypes = organizationDao == null ? List.of() : organizationDao.findOrganizationTypes();
		return new PartyDialogData(
				partyRoles == null ? List.of() : partyRoles,
				contacts == null ? List.of() : contacts,
				organizations == null ? List.of() : organizations,
				sideOptions == null ? List.of() : sideOptions,
				organizationTypes == null ? List.of() : organizationTypes);
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
		new Thread(() ->
		{
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
		new Thread(() ->
		{
			try {
				List<CasePartyDto> refreshed = caseDao.listCaseParties(activeCaseId);
				runOnFx(() ->
				{
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

	private PartyEditorResult showPartyEditorDialog(CasePartyDto existing, PartyDialogData data) {
		if (existing == null) {
			PartyAddWorkflowDialog.AddPartyDraft draft = showAddPartyWizardDialog(data);
			if (draft == null || draft.entityId() == null) {
				return null;
			}
			return new PartyEditorResult(draft.entityType(), draft.entityId(), draft.partyRoleId(), draft.side(), draft.primary(), draft.notes());
		}

		if (appState == null || appState.getShaleClientId() <= 0 || caseId == null || caseId <= 0) {
			showError("Unable to edit parties without an active client/case context.");
			return null;
		}
		List<CaseDao.PartyRoleRow> partyRoles = data == null ? List.of() : data.partyRoles();
		List<CaseDao.SelectableContactRow> contacts = data == null ? List.of() : data.contacts();
		List<CaseDao.SelectableOrganizationRow> organizations = data == null ? List.of() : data.organizations();

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
		sideChoice.getItems().addAll(data == null ? List.of() : data.sideOptions());
		sideChoice.setConverter(new javafx.util.StringConverter<>() {
			@Override
			public String toString(PartySideOption object) {
				return object == null ? "" : object.label;
			}

			@Override
			public PartySideOption fromString(String string) {
				return null;
			}
		});

		CheckBox primaryCheck = new CheckBox("Primary");
		TextArea notesArea = new TextArea();
		notesArea.setPrefRowCount(3);
		notesArea.setWrapText(true);

		partyRoles.stream()
				.map(r -> new PartyRoleOption(r.id(), toPartyRoleLabel(r.name(), r.id())))
				.forEach(roleChoice.getItems()::add);
		roleChoice.setConverter(new javafx.util.StringConverter<>() {
			@Override
			public String toString(PartyRoleOption object) {
				return object == null ? "" : object.label;
			}

			@Override
			public PartyRoleOption fromString(String string) {
				return null;
			}
		});
		entityChoice.setConverter(new javafx.util.StringConverter<>() {
			@Override
			public String toString(PartyEntityOption object) {
				return object == null ? "" : object.label;
			}

			@Override
			public PartyEntityOption fromString(String string) {
				return null;
			}
		});

		Runnable loadEntities = () ->
		{
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
				.ifPresentOrElse(entityChoice::setValue, () ->
				{
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
			@Override
			public String toString(PartySideOption object) {
				return object == null ? "" : object.label;
			}

			@Override
			public PartySideOption fromString(String string) {
				return null;
			}
		});

		dialog.setResultConverter(button ->
		{
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

	private PartyAddWorkflowDialog.AddPartyDraft showAddPartyWizardDialog(PartyDialogData data) {
		if (appState == null || appState.getShaleClientId() <= 0 || caseId == null || caseId <= 0) {
			showError("Unable to add parties without an active client/case context.");
			return null;
		}
		List<CaseDao.PartyRoleRow> partyRoles = data == null ? List.of() : data.partyRoles();
		List<CaseDao.SelectableContactRow> contacts = data == null ? List.of() : data.contacts();
		List<CaseDao.SelectableOrganizationRow> organizations = data == null ? List.of() : data.organizations();
		List<OrganizationDao.OrganizationTypeRow> organizationTypes = data == null ? List.of() : data.organizationTypes();
		List<PartyAddWorkflowDialog.PartySideOption> sideOptions = (data == null ? List.<PartySideOption>of() : data.sideOptions()).stream()
				.map(o -> new PartyAddWorkflowDialog.PartySideOption(o.label(), o.value()))
				.toList();
		return PartyAddWorkflowDialog.show(organizationDialogOwner(), partyRoles, contacts, organizations, organizationTypes, sideOptions);
	}

	private Long createEntityForNewPartyDraft(PartyAddWorkflowDialog.AddPartyDraft draft) {
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
					null,
					null,
					safeText(draft.contactFirstName()),
					safeText(draft.contactLastName()),
					null,
					null,
					null,
					null,
					null,
					false,
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

		new Thread(() ->
		{
			try {
				long taskLoadStartNanos = PerfLog.start();
				PerfLog.log("DAO", "start", "method=loadTasksForCase page=case_view caseId=" + activeCaseId);
				List<CaseTaskListItemDto> tasks = caseTaskService.loadTasksForCase(
						activeCaseId,
						shaleClientId,
						selectedCaseTaskSort());
				PerfLog.logDone("DAO", "method=loadTasksForCase page=case_view caseId=" + activeCaseId + " rows=" + (tasks == null ? 0 : tasks.size()), taskLoadStartNanos);
				List<Long> taskIds = (tasks == null ? List.<CaseTaskListItemDto>of() : tasks).stream()
						.map(CaseTaskListItemDto::id)
						.toList();
				long assignedUsersLoadStartNanos = PerfLog.start();
				PerfLog.log("DAO", "start", "method=loadAssignedUsersForTasks page=case_view caseId=" + activeCaseId);
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
				PerfLog.logDone("DAO", "method=loadAssignedUsersForTasks page=case_view caseId=" + activeCaseId + " rows=" + assignedByTask.size(), assignedUsersLoadStartNanos);
				runOnFx(() ->
				{
					if (caseId == null || caseId.longValue() != activeCaseId) {
						return;
					}
					caseTasks = tasks == null ? List.of() : tasks;
					caseTaskAssignedUsers = assignedByTask;
					caseTasksLoadedOnce = true;
					caseTasksStale = false;
					renderTasksSection();
				});
			} catch (Exception ex) {
				runOnFx(() ->
				{
					caseTasks = List.of();
					caseTaskAssignedUsers = java.util.Map.of();
					caseTasksLoadedOnce = true;
					caseTasksStale = false;
					renderTasksSection();
				});
				System.err.println("Case tasks load failed for caseId=" + activeCaseId + ": " + ex.getMessage());
			}
		}, "case-load-tasks-" + activeCaseId).start();
	}

	private void setUpdatesPaneVisible(boolean showOnRight) {
		attachCaseUpdatesPane(showOnRight ? CaseUpdatesPlacement.RIGHT : CaseUpdatesPlacement.HIDDEN);
	}

	private void attachCaseUpdatesPane(CaseUpdatesPlacement placement) {
		if (caseRootPane == null || caseUpdatesPane == null) {
			return;
		}
		if (placement == CaseUpdatesPlacement.HIDDEN) {
			caseRootPane.setRight(null);
			caseUpdatesPane.setManaged(false);
			caseUpdatesPane.setVisible(false);
			return;
		}
		caseUpdatesPane.setManaged(true);
		caseUpdatesPane.setVisible(true);
		caseUpdatesPane.setMaxWidth(Region.USE_COMPUTED_SIZE);
		caseUpdatesPane.setPrefWidth(320.0);
		VBox.setVgrow(caseUpdatesPane, Priority.ALWAYS);
		if (caseRootPane.getRight() != caseUpdatesPane) {
			caseRootPane.setRight(caseUpdatesPane);
		}
	}

	private void renderTasksSection() {
		if (tasksTabFlow == null || tasksTabEmptyLabel == null) {
			return;
		}
		long renderStartNanos = PerfLog.start();
		PerfLog.log("RENDER", "start", "panel=tasks page=case_view caseId=" + caseId);

		tasksTabFlow.getChildren().clear();
		List<CaseTaskListItemDto> visibleTasks = visibleCaseTasks();
		if (visibleTasks.isEmpty()) {
			setVisibleManaged(tasksTabEmptyLabel, true);
			tasksTabEmptyLabel.setText(showCompletedCaseTasks
					? "No tasks for this case yet."
					: "No incomplete tasks for this case.");
			PerfLog.logDone("RENDER", "panel=tasks page=case_view caseId=" + caseId + " childCount=0", renderStartNanos);
			return;
		}

		TaskCardFactory factory = taskCardFactory != null
				? taskCardFactory
				: buildTaskCardFactory(this::openTask);

		for (CaseTaskListItemDto task : visibleTasks) {
			TaskCardFactory.TaskCardModel model = new TaskCardFactory.TaskCardModel(
					task.id(),
					task.caseId(),
					task.caseName(),
					task.casePrimaryStatusName(),
					task.casePrimaryStatusColor(),
					task.casePracticeAreaColor(),
					task.caseResponsibleAttorney(),
					task.caseResponsibleAttorneyColor(),
					task.caseNonEngagementLetterSent(),
					task.title(),
					task.description(),
					task.createdByDisplayName(),
					task.taskStatusName(),
					task.taskStatusColorHex(),
					task.priorityColorHex(),
					task.dueAt(),
					task.completedAt(),
					caseTaskAssignedUsers.getOrDefault(task.id(), List.of()));
			tasksTabFlow.getChildren().add(factory.create(model, TaskCardFactory.Variant.COMPACT, true));
		}

		setVisibleManaged(tasksTabEmptyLabel, false);
		PerfLog.logDone("RENDER", "panel=tasks page=case_view caseId=" + caseId + " childCount=" + tasksTabFlow.getChildren().size(), renderStartNanos);
	}

	private List<CaseTaskListItemDto> visibleCaseTasks() {
		if (caseTasks == null || caseTasks.isEmpty()) {
			return List.of();
		}
		if (showCompletedCaseTasks) {
			return caseTasks;
		}
		return caseTasks.stream()
				.filter(task -> task.completedAt() == null)
				.toList();
	}

	private void updateCaseTaskCompletionToggleLabel() {
		if (caseTasksShowCompletedButton == null) {
			return;
		}
		caseTasksShowCompletedButton.setText(showCompletedCaseTasks ? "Hide Completed" : "Show Completed");
	}

	private boolean isSectionActive(String sectionName) {
		return Objects.equals(activeSectionName, sectionName);
	}

	private boolean isCaseUpdatesSectionActive() {
		return isSectionActive("Overview")
				|| isSectionActive("Details")
				|| isSectionActive("Parties")
				|| isSectionActive("Tasks")
				|| isSectionActive("Timeline");
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

		final int activeCaseId = caseId;
		new Thread(() ->
		{
			List<TaskPriorityOptionDto> priorities;
			try {
				priorities = caseTaskService.loadActivePriorities(shaleClientId);
			} catch (Exception ex) {
				logTaskActionException("load-priorities", ex);
				runOnFx(() -> showTaskActionError("Unable to load priorities right now."));
				return;
			}

			List<CaseTaskService.AssignableUserOption> assignableUsers;
			try {
				assignableUsers = caseTaskService.loadAssignableUsers(shaleClientId);
			} catch (Exception ex) {
				logTaskActionException("load-assignees", ex);
				runOnFx(() -> showTaskActionError("Unable to load users right now."));
				return;
			}

			runOnFx(() ->
			{
				Optional<NewTaskDialog.CreateTaskInput> input = NewTaskDialog.showAndWait(
						taskDialogOwner(),
						priorities,
						assignableUsers);
				if (input.isEmpty()) {
					return;
				}

				CaseTaskService.CreateTaskRequest request = new CaseTaskService.CreateTaskRequest(
						shaleClientId,
						(long) activeCaseId,
						input.get().title(),
						input.get().description(),
						input.get().dueAt(),
						input.get().priorityId(),
						input.get().assignedUserIds(),
						currentUserId);

				new Thread(() ->
				{
					try {
						caseTaskService.createTask(request);
						runOnFx(() -> { refreshCaseTasks(); refreshCaseCalendar(); });
					} catch (Exception ex) {
						logTaskActionException("create", ex);
						runOnFx(() -> showTaskActionError("Failed to create task for this case. " + rootCauseMessage(ex)));
					}
				}, "case-create-task-" + activeCaseId).start();
			});
		}, "case-add-task-prereq-" + activeCaseId).start();
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
		new Thread(() ->
		{
			try {
				if (currentlyCompleted) {
					caseTaskService.uncompleteTask(taskId, shaleClientId, appState.getUserId());
				} else {
					caseTaskService.completeTask(taskId, shaleClientId, appState.getUserId());
				}
				runOnFx(() -> { refreshCaseTasks(); refreshCaseCalendar(); });
			} catch (Exception ex) {
				logTaskActionException("toggle-complete", ex);
				runOnFx(() -> showTaskActionError("Failed to update task completion. " + rootCauseMessage(ex)));
			}
		}, "case-toggle-task-" + taskId).start();
	}

	private void showTaskDetailPopup(Long taskId) {
		long clickReceivedAt = PerfLog.start();
		PerfLog.log("TASK_DETAIL_TIMING", "click_received", "context=CASE_TASKS taskId=" + taskId);
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
			PerfLog.log("TASK_DETAIL_TIMING", "open_skipped_in_flight", "context=CASE_TASKS taskId=" + taskId);
			return;
		}
		Optional<CaseTaskListItemDto> summary = findCaseTaskById(taskId);
		TaskDetailDialog.TaskDetailModel model = new TaskDetailDialog.TaskDetailModel(
				taskId,
				summary.map(CaseTaskListItemDto::caseId).orElse(0L),
				summary.map(CaseTaskListItemDto::caseName).orElse(""),
				summary.map(CaseTaskListItemDto::caseResponsibleAttorney).orElse(""),
				summary.map(CaseTaskListItemDto::caseResponsibleAttorneyColor).orElse(""),
				summary.map(CaseTaskListItemDto::caseNonEngagementLetterSent).orElse(null),
				summary.map(CaseTaskListItemDto::casePrimaryStatusName).orElse(""),
				summary.map(CaseTaskListItemDto::casePrimaryStatusColor).orElse(""),
				summary.map(CaseTaskListItemDto::casePracticeAreaColor).orElse(""),
				summary.map(CaseTaskListItemDto::title).orElse(""),
				summary.map(CaseTaskListItemDto::description).orElse(""),
				summary.map(CaseTaskListItemDto::dueAt).orElse(null),
				null,
				null,
				summary.map(CaseTaskListItemDto::createdByDisplayName).orElse(""),
				List.of(),
				List.of(),
				List.of(),
				summary.map(item -> item.completedAt() != null).orElse(false));
		PerfLog.logElapsed("TASK_DETAIL_TIMING", "shell_stage_created", "context=CASE_TASKS taskId=" + taskId, PerfLog.elapsedMs(clickReceivedAt));
		try {
			auditTaskRead(taskId);
			Optional<TaskDetailDialog.TaskDetailResult> result = TaskDetailDialog.showAndWait(
					"CASE_TASKS",
					clickReceivedAt,
					taskDialogOwner(),
					model,
					List.of(),
					List.of(),
					id ->
					{
						TaskDetailDto detail = caseTaskService.loadTaskDetail(id, shaleClientId);
						List<TaskStatusOptionDto> statuses = caseTaskService.loadActiveTaskStatuses(shaleClientId);
						List<TaskPriorityOptionDto> priorities = caseTaskService.loadActivePriorities(shaleClientId);
						if (detail == null) {
							throw new IllegalStateException("Task was not found or may have been deleted.");
						}
						return new TaskDetailDialog.CoreTaskHydration(detail, statuses, priorities);
					},
					id -> caseTaskService.loadAssignableUsersForTask(id, shaleClientId),
					id -> caseTaskService.loadAssignedUsersForTask(id, shaleClientId).stream()
							.map(member -> new TaskDetailDialog.AssignedTeamMember(
									member.userId(),
									member.displayName(),
									member.color()))
							.toList(),
					id -> caseTaskService.loadTaskActivity(id, shaleClientId).stream()
							.map(item -> new TaskDetailDialog.TaskActivityEntry(
									item.title(),
									item.body(),
									item.actorDisplayName(),
									item.occurredAt()))
							.toList(),
					id -> caseTaskService.loadTaskNotes(id, shaleClientId).stream()
							.map(note -> new TaskDetailDialog.TaskNoteEntry(
									note.id(),
									note.userId(),
									note.userDisplayName(),
									note.body(),
									note.createdAt(),
									note.updatedAt(),
									note.userId() == currentUserId))
							.toList(),
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
							caseTaskService.removeTaskAssignment(model.taskId(), shaleClientId, userId, currentUserId);
							return caseTaskService.loadAssignedUsersForTask(model.taskId(), shaleClientId).stream()
									.map(member -> new TaskDetailDialog.AssignedTeamMember(
											member.userId(),
											member.displayName(),
											member.color()))
									.toList();
						}
					},
					new TaskDetailDialog.NotesEditor() {
						@Override
						public List<TaskDetailDialog.TaskNoteEntry> addAndReload(String body) {
							caseTaskService.addTaskNote(model.taskId(), shaleClientId, currentUserId, body);
							return caseTaskService.loadTaskNotes(model.taskId(), shaleClientId).stream()
									.map(note -> new TaskDetailDialog.TaskNoteEntry(
											note.id(),
											note.userId(),
											note.userDisplayName(),
											note.body(),
											note.createdAt(),
											note.updatedAt(),
											note.userId() == currentUserId))
									.toList();
						}

						@Override
						public List<TaskDetailDialog.TaskNoteEntry> editAndReload(long noteId, String body) {
							caseTaskService.updateTaskNote(noteId, shaleClientId, currentUserId, body);
							return caseTaskService.loadTaskNotes(model.taskId(), shaleClientId).stream()
									.map(note -> new TaskDetailDialog.TaskNoteEntry(
											note.id(),
											note.userId(),
											note.userDisplayName(),
											note.body(),
											note.createdAt(),
											note.updatedAt(),
											note.userId() == currentUserId))
									.toList();
						}
					},
					onOpenUser,
					onOpenCase);
			if (result.isEmpty()) {
				return;
			}
			TaskDetailDialog.TaskDetailResult action = result.get();
			if (action.action() == TaskDetailDialog.TaskDetailAction.DELETE) {
				deleteTaskFromDetail(taskId, shaleClientId, currentUserId);
				return;
			}
			TaskDetailDialog.SaveTaskPayload payload = action.payload();
			if (payload == null) {
				return;
			}
			saveTaskFromDetail(taskId, shaleClientId, currentUserId, payload);
		} catch (Exception ex) {
			logTaskActionException("load-detail", ex);
			showTaskActionError("Failed to load task details. " + rootCauseMessage(ex));
		} finally {
			taskDetailDialogInFlight.set(false);
		}
	}

	private void auditTaskRead(Long taskId) {
		if (phiReadAuditService == null || taskId == null || taskId <= 0) {
			return;
		}
		phiReadAuditService.auditRead("Task.Detail.Read", "Task.Detail", "Task", taskId);
		phiReadAuditService.auditRead("Task.Activity.Read", "Task.Activity", "Task", taskId);
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
				payload.statusId(),
				payload.priorityId(),
				payload.completed(),
				currentUserId);

		new Thread(() ->
		{
			try {
				caseTaskService.updateTask(request);
				runOnFx(() -> { refreshCaseTasks(); refreshCaseCalendar(); });
			} catch (Exception ex) {
				logTaskActionException("save-detail", ex);
				runOnFx(() -> showTaskActionError("Failed to save task. " + rootCauseMessage(ex)));
			}
		}, "case-task-save-detail-" + taskId).start();
	}

	private void deleteTaskFromDetail(long taskId, int shaleClientId, int currentUserId) {
		new Thread(() ->
		{
			try {
				caseTaskService.deleteTask(taskId, shaleClientId, currentUserId);
				runOnFx(() -> { refreshCaseTasks(); refreshCaseCalendar(); });
			} catch (Exception ex) {
				logTaskActionException("delete-detail", ex);
				runOnFx(() -> showTaskActionError("Failed to delete task. " + rootCauseMessage(ex)));
			}
		}, "case-task-delete-detail-" + taskId).start();
	}

	private void refreshCaseTasks() {
		caseTasksStale = true;
		loadCaseTasksAsync();
		loadCaseTaskActivityAsync();
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
		loadCompatibilityDatesAsync(activeCaseId);
		caseUpdatesStale = true;
		loadCaseUpdatesAsync();
		loadCaseTasksAsync();
		loadCaseTaskActivityAsync();

		new Thread(() ->
		{
			long overviewStartNanos = PerfLog.start();
			PerfLog.log("DAO", "start", "method=getOverview page=case_view caseId=" + activeCaseId);
			CaseOverviewDto overview = caseDao.getOverview(activeCaseId);
			PerfLog.logDone("DAO", "method=getOverview page=case_view caseId=" + activeCaseId + " rows=" + (overview == null ? 0 : 1), overviewStartNanos);
			long detailStartNanos = PerfLog.start();
			PerfLog.log("DAO", "start", "method=getDetail page=case_view caseId=" + activeCaseId);
			CaseDetailDto detail = caseDao.getDetail(activeCaseId);
			PerfLog.logDone("DAO", "method=getDetail page=case_view caseId=" + activeCaseId + " rows=" + (detail == null ? 0 : 1), detailStartNanos);
			List<CasePartyDto> loadedParties = List.of();
			boolean partiesLoadSucceeded = false;
			try {
				long partiesStartNanos = PerfLog.start();
				PerfLog.log("DAO", "start", "method=listCaseParties page=case_view caseId=" + activeCaseId);
				loadedParties = caseDao.listCaseParties(activeCaseId);
				PerfLog.logDone("DAO", "method=listCaseParties page=case_view caseId=" + activeCaseId + " rows=" + (loadedParties == null ? 0 : loadedParties.size()),
						partiesStartNanos);
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

				applyCurrentDetailSnapshot(detail);
				detailsLocalViewOverride = null;
				renderDetailsFromCurrent();
				if (!editMode)
					applyDetail(detail);
				else
					applyLastUpdatedLabel(detail.getUpdatedAt());
				loadStatusTimelineAsync();

				hideRemoteUpdateBanner();
				clearError();
				refreshDeleteAction();
				PerfLog.logDone("NAV", "ready page=case_view caseId=" + activeCaseId, pageLoadStartNanos);
			});
		}, "case-view-sync-" + activeCaseId).start();
	}

	private void loadCompatibilityDatesAsync(long activeCaseId) {
		if (caseService == null || appState == null || appState.getShaleClientId() == null || appState.getUserId() == null) return;
		final int generation = ++compatibilityDatesGeneration;
		final int tenant = appState.getShaleClientId();
		final int actor = appState.getUserId();
		new Thread(() -> {
			try {
				CaseDateAggregateResult loaded = caseService.loadMigratedCompatibilityDateSnapshot(activeCaseId, tenant, actor);
				runOnFx(() -> {
					if (!isCompatibilityDatesCurrent(activeCaseId, generation)) return;
					compatibilityDates.replace(loaded);
					latestCaseRowVer = loaded.caseRowVer();
					renderCompatibilityDates();
				});
			} catch (RuntimeException ex) {
				runOnFx(() -> { if (isCompatibilityDatesCurrent(activeCaseId, generation))
					showError("Authoritative Case Dates could not be loaded. Reload before editing dates."); });
			}
		}, "case-compatibility-dates-load-" + activeCaseId).start();
	}

	private boolean isCompatibilityDatesCurrent(long activeCaseId, int generation) {
		return caseId != null && caseId.longValue() == activeCaseId
				&& generation == compatibilityDatesGeneration
				&& overviewScrollPane != null && overviewScrollPane.getScene() != null;
	}

	/**
	 * The only local invalidation path between the generic Dates list and the fixed
	 * compatibility controls.  A generic mutation never synthesizes an editor state:
	 * migrated types force a complete aggregate reload so all row versions and absence
	 * witnesses move forward together.  Fixed aggregate saves already return that
	 * coherent snapshot, so only the generic list is invalidated in that direction.
	 */
	private void refreshCaseDateViewsAfterLocalMutation(long activeCaseId, boolean compatibilityAffected) {
		if (caseId == null || caseId.longValue() != activeCaseId) return;
		caseDatesStale = true;
		if ("Dates".equals(activeSectionName)) loadCaseDatesAsync();
		if (compatibilityAffected) loadCompatibilityDatesAsync(activeCaseId);
	}

	/** Publish only after the service/DAO transaction has returned successfully. */
	private void publishCaseDatesChanged(long activeCaseId, String change) {
		if (runtimeBridge == null || appState == null || appState.getShaleClientId() == null || appState.getUserId() == null) return;
		runtimeBridge.publishCaseDatesChanged(activeCaseId, appState.getShaleClientId(), appState.getUserId(), change);
	}

	private void applyDeferredCaseDatesRefresh() {
		if (!remoteCaseDatesRefreshDeferred || caseDateEditorOpen || caseDateMutationInFlight.get() || compatibilityDates.isSaving()) return;
		remoteCaseDatesRefreshDeferred = false;
		queueRemoteCaseDatesRefresh();
	}

	private void queueRemoteCaseDatesRefresh() {
		if (!remoteCaseDatesRefreshQueued.compareAndSet(false, true)) return;
		runOnFx(() -> {
			remoteCaseDatesRefreshQueued.set(false);
			if (caseId == null) return;
			if (caseDateEditorOpen || caseDateMutationInFlight.get() || compatibilityDates.isSaving()) {
				remoteCaseDatesRefreshDeferred = true;
				return;
			}
			long activeCaseId = caseId.longValue();
			caseDatesStale = true;
			loadCaseDatesAsync();
			loadCompatibilityDatesAsync(activeCaseId);
		});
	}

	private boolean rememberCaseDateEvent(String eventId) {
		if (eventId == null || eventId.isBlank()) return true;
		synchronized (receivedDateRefreshEventIds) {
			if (!receivedDateRefreshEventIds.add(eventId)) return false;
			while (receivedDateRefreshEventIds.size() > 256) receivedDateRefreshEventIds.remove(receivedDateRefreshEventIds.iterator().next());
			return true;
		}
	}

	private boolean isMigratedCaseDateType(int typeId) {
		return effectiveCaseDateTypes.stream().filter(type -> type.id() == typeId).findFirst()
				.map(EffectiveCaseDateTypeDto::systemKey).map(this::isMigratedCaseDateSystemKey).orElse(false);
	}

	private boolean isMigratedCaseDateSystemKey(String systemKey) {
		try { MigratedCaseDateKey.require(systemKey); return true; }
		catch (IllegalArgumentException ignored) { return false; }
	}

	private void renderCompatibilityDates() {
		if (!compatibilityDates.isLoaded()) return;
		Map<MigratedCaseDateKey, CompatibilityCaseDateState> s = compatibilityDates.states();
		setCompatibilityDate(detCallerDateValue, detCallerDateEditor, s.get(MigratedCaseDateKey.CALLER_DATE));
		CompatibilityCaseDateState intake = s.get(MigratedCaseDateKey.CALLER_DATE);
		String intakeTime = intake.startsAt() == null || intake.allDay() ? "" : intake.startsAt().toLocalTime().toString();
		if (detCallerTimeValue != null) detCallerTimeValue.setText(intakeTime.isBlank() ? "—" : intakeTime);
		if (detCallerTimeEditor != null) detCallerTimeEditor.setText(intakeTime);
		setCompatibilityDate(detDateOfInjuryValue, detDateOfInjuryEditor, s.get(MigratedCaseDateKey.DATE_OF_INJURY));
		setCompatibilityDate(detDateOfMedicalNegligenceValue, detDateOfMedicalNegligenceEditor, s.get(MigratedCaseDateKey.DATE_OF_MEDICAL_NEGLIGENCE));
		setCompatibilityDate(detDateMedicalNegligenceWasDiscoveredValue, detDateMedicalNegligenceWasDiscoveredEditor, s.get(MigratedCaseDateKey.DATE_MEDICAL_NEGLIGENCE_DISCOVERED));
		setCompatibilityDate(detStatuteOfLimitationsValue, detStatuteOfLimitationsEditor, s.get(MigratedCaseDateKey.STATUTE_OF_LIMITATIONS));
		setCompatibilityDate(detTortNoticeDeadlineValue, detTortNoticeDeadlineEditor, s.get(MigratedCaseDateKey.TORT_NOTICE_DEADLINE));
		setCompatibilityDate(detDiscoveryDeadlineValue, detDiscoveryDeadlineEditor, s.get(MigratedCaseDateKey.DISCOVERY_DEADLINE));
		setCompatibilityDate(detDateFeeAgreementSignedValue, detDateFeeAgreementSignedEditor, s.get(MigratedCaseDateKey.DATE_FEE_AGREEMENT_SIGNED));
		setCompatibilityDate(detDateNonEngagementLetterSentValue, detDateNonEngagementLetterSentEditor, s.get(MigratedCaseDateKey.DATE_NON_ENGAGEMENT_LETTER_SENT));
		setCompatibilityDate(ovIncidentDateValue, ovIncidentDateEditor, s.get(MigratedCaseDateKey.DATE_OF_INJURY));
		setCompatibilityDate(ovDateOfMedicalNegligenceValue, ovDateOfMedicalNegligenceEditor, s.get(MigratedCaseDateKey.DATE_OF_MEDICAL_NEGLIGENCE));
		setCompatibilityDate(ovSolDateValue, ovSolDateEditor, s.get(MigratedCaseDateKey.STATUTE_OF_LIMITATIONS));
		setCompatibilityDate(ovTortNoticeDeadlineValue, ovTortNoticeDeadlineEditor, s.get(MigratedCaseDateKey.TORT_NOTICE_DEADLINE));
		if (ovIntakeDateValue != null) ovIntakeDateValue.setText(formatDate(intake.startsAt() == null ? null : intake.startsAt().toLocalDate()));
	}

	private void setCompatibilityDate(Label label, DatePicker picker, CompatibilityCaseDateState state) {
		LocalDate date = state == null || state.startsAt() == null ? null : state.startsAt().toLocalDate();
		if (label != null) label.setText(formatDate(date));
		if (picker != null) picker.setValue(date);
	}

	private void saveAuthoritativeDate(MigratedCaseDateKey key, LocalDate date) {
		if (caseId == null || appState == null || caseService == null || !compatibilityDates.isLoaded()) {
			showError("Reload authoritative Case Dates before editing."); return;
		}
		if (compatibilityDates.hasConflict(key)) {
			showError("This protected Case Date has conflicting active occurrences. Resolve them in Dates, then reload."); return;
		}
		Map<MigratedCaseDateKey, CompatibilityCaseDateEditor.EditedValue> values =
				new java.util.EnumMap<>(AuthoritativeCaseDateEditor.values(compatibilityDates.states()));
		CompatibilityCaseDateState old = compatibilityDates.states().get(key);
		LocalDateTime start = null;
		if (date != null) {
			if (key.supportsTime() && old.startsAt() != null && !old.allDay()) start = LocalDateTime.of(date, old.startsAt().toLocalTime());
			else start = date.atStartOfDay();
		}
		values.put(key, new CompatibilityCaseDateEditor.EditedValue(start, date == null ? null : old.endsAt(), key.supportsTime() ? old.allDay() : true));
		saveAuthoritativeValues(values);
	}

	private void saveAuthoritativeIntakeTime(String value) {
		if (!compatibilityDates.isLoaded()) { showError("Reload authoritative Case Dates before editing."); return; }
		CompatibilityCaseDateState old = compatibilityDates.states().get(MigratedCaseDateKey.CALLER_DATE);
		if (old == null || old.startsAt() == null) { showError("Set Caller Date before Caller Time."); return; }
		final java.time.LocalTime time;
		try { time = java.time.LocalTime.parse(safeText(value).trim()); }
		catch (RuntimeException ex) { showError("Caller Time must be a valid time such as 9:30."); return; }
		Map<MigratedCaseDateKey, CompatibilityCaseDateEditor.EditedValue> values =
				new java.util.EnumMap<>(AuthoritativeCaseDateEditor.values(compatibilityDates.states()));
		values.put(MigratedCaseDateKey.CALLER_DATE, new CompatibilityCaseDateEditor.EditedValue(
				LocalDateTime.of(old.startsAt().toLocalDate(), time), old.endsAt(), false));
		saveAuthoritativeValues(values);
	}

	private void saveAuthoritativeValues(Map<MigratedCaseDateKey, CompatibilityCaseDateEditor.EditedValue> values) {
		if (caseId == null || appState == null || caseService == null || !compatibilityDates.isLoaded()) {
			showError("Reload authoritative Case Dates before editing."); return;
		}
		CaseDateAggregateCommand command;
		try { command = compatibilityDates.beginSave(appState.getShaleClientId(), appState.getUserId(), caseId, values); }
		catch (RuntimeException ex) { showError(ex.getMessage()); return; }
		if (command == null) { renderCompatibilityDates(); return; }
		final long activeCaseId = caseId;
		// A save is itself a newer authoritative request. Invalidate any older load so
		// an out-of-order response cannot overwrite the returned aggregate snapshot.
		final int generation = ++compatibilityDatesGeneration;
		setBusy(true);
		new Thread(() -> {
			try {
				CaseDateAggregateResult result = caseService.mutateMigratedCompatibilityDates(command);
				runOnFx(() -> {
					if (!isCompatibilityDatesCurrent(activeCaseId, generation)) return;
					compatibilityDates.replace(result); latestCaseRowVer = result.caseRowVer(); renderCompatibilityDates(); setBusy(false);
					refreshCaseDateViewsAfterLocalMutation(activeCaseId, false);
					publishCaseDatesChanged(activeCaseId, LiveUpdateEvents.CHANGE_UPDATED);
					applyDeferredCaseDatesRefresh();
				});
			} catch (RuntimeException ex) {
				runOnFx(() -> {
					if (!isCompatibilityDatesCurrent(activeCaseId, generation)) return;
					compatibilityDates.failedSave(); setBusy(false);
					showError("Case Dates changed elsewhere or are inconsistent. Reloaded authoritative dates; review your change before saving again.");
					compatibilityDates.invalidate(); loadCompatibilityDatesAsync(activeCaseId);
					applyDeferredCaseDatesRefresh();
				});
			}
		}, "case-compatibility-dates-save-" + activeCaseId).start();
	}

	private void refreshOverviewAndDetailsAfterStructuralPatchAsync() {
		if (caseDao == null || caseId == null) {
			return;
		}
		final long activeCaseId = caseId.longValue();
		new Thread(() ->
		{
			try {
				CaseOverviewDto overview = caseDao.getOverview(activeCaseId);
				CaseDetailDto detail = caseDao.getDetail(activeCaseId);
				runOnFx(() ->
				{
					if (caseId == null || caseId.longValue() != activeCaseId) {
						return;
					}
					if (overview != null) {
						currentOverview = applyCallerFromCaseParties(overview, caseParties);
						applyOverviewEditSafe(currentOverview);
					}
					if (detail != null) {
						applyCurrentDetailSnapshot(detail);
						detailsLocalViewOverride = null;
						renderDetailsFromCurrent();
						if (!editMode) {
							applyDetail(detail);
						} else {
							applyLastUpdatedLabel(detail.getUpdatedAt());
						}
						refreshDeleteAction();
					}
					loadStatusTimelineAsync();
					refreshLastUpdatedLabelAsync();
				});
			} catch (Exception ex) {
				runOnFx(() -> reloadCurrentCaseForViewMode());
			}
		}, "case-structural-refresh-" + activeCaseId).start();
	}

	private void loadCaseTaskActivityAsync() {
		if (caseTaskService == null || appState == null || caseId == null || caseId <= 0) {
			renderCaseTaskActivity(List.of());
			return;
		}
		Integer shaleClientId = appState.getShaleClientId();
		if (shaleClientId == null || shaleClientId <= 0) {
			renderCaseTaskActivity(List.of());
			return;
		}
		final int activeCaseId = caseId;
		new Thread(() ->
		{
			try {
				List<CaseTaskService.TaskActivityItem> events = caseTaskService.loadCaseTaskActivity(activeCaseId, shaleClientId);
				runOnFx(() ->
				{
					if (caseId == null || caseId != activeCaseId) {
						return;
					}
					renderCaseTaskActivity(events);
				});
			} catch (Exception ex) {
				runOnFx(() -> showError("Failed to load task activity. " + ex.getMessage()));
			}
		}, "case-task-activity-load-" + activeCaseId).start();
	}

	private void renderCaseTaskActivity(List<CaseTaskService.TaskActivityItem> events) {
		if (caseTaskActivityFeedBox == null) {
			return;
		}
		caseTaskActivityFeedBox.getChildren().clear();
		caseTaskActivityEvents = events == null ? List.of() : List.copyOf(events);
		if (caseTaskActivityEvents.isEmpty()) {
			setVisibleManaged(caseTaskActivityEmptyLabel, true);
			if (caseTaskActivityScrollPane != null) {
				caseTaskActivityScrollPane.setVvalue(0.0);
			}
			return;
		}
		setVisibleManaged(caseTaskActivityEmptyLabel, false);
		for (CaseTaskService.TaskActivityItem event : caseTaskActivityEvents) {
			if (event == null) {
				continue;
			}
			caseTaskActivityFeedBox.getChildren().add(createCaseTaskActivityCard(event));
		}
		if (caseTaskActivityScrollPane != null) {
			caseTaskActivityScrollPane.setVvalue(0.0);
		}
	}

	private Node createCaseTaskActivityCard(CaseTaskService.TaskActivityItem event) {
		String taskTitle = safeText(event.taskTitle()).trim();
		if (taskTitle.isBlank()) {
			taskTitle = "Task #" + event.taskId();
		}
		Hyperlink taskLink = new Hyperlink(taskTitle);
		taskLink.setOnAction(e -> openTask(event.taskId()));
		taskLink.setWrapText(true);

		Label eventTitle = new Label(safeText(event.title()).trim().isBlank() ? "Activity event" : safeText(event.title()).trim());
		eventTitle.setStyle("-fx-font-weight: bold;");
		eventTitle.setWrapText(true);

		VBox content = new VBox(6, taskLink, eventTitle);
		String body = safeText(event.body()).trim();
		if (!body.isBlank()) {
			Label bodyLabel = new Label(body);
			bodyLabel.setWrapText(true);
			content.getChildren().add(bodyLabel);
		}

		String actor = safeText(event.actorDisplayName()).trim();
		if (actor.isBlank()) {
			actor = "System";
		}
		Label metaLabel = new Label(actor + " · " + formatDateTime(event.occurredAt()));
		metaLabel.setStyle("-fx-opacity: 0.75;");
		content.getChildren().add(metaLabel);

		VBox card = new VBox(content);
		card.setPadding(new Insets(10, 12, 10, 12));
		card.getStyleClass().addAll("secondary-panel", "shale-entity-card", "shale-entity-card-embedded");
		return card;
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
				overview.getPrimaryLegalAssistantUserId(),
				overview.getPrimaryLegalAssistant(),
				overview.getPrimaryLegalAssistantColor(),
				overview.getPracticeAreaId(),
				overview.getPracticeArea(),
				overview.getPracticeAreaColor(),
				null,
				null,
				null,
				null,
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
		return normalizedKey.equals(partySystemKey);
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

	// Thin wrapper keeps controller as FXML entrypoint while render ownership stays in
	// renderer.
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
			if (ovDateOfMedicalNegligenceEditor != null)
				ovDateOfMedicalNegligenceEditor.setDisable(busy);
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
			if (changePrimaryLegalAssistantButton != null)
				changePrimaryLegalAssistantButton.setDisable(busy);
			if (editCaseNameButton != null)
				editCaseNameButton.setDisable(busy);
			if (editCaseNumberButton != null)
				editCaseNumberButton.setDisable(busy);
			if (editDescriptionButton != null)
				editDescriptionButton.setDisable(busy);
			if (editIncidentDateButton != null)
				editIncidentDateButton.setDisable(busy);
			if (editDateOfMedicalNegligenceButton != null)
				editDateOfMedicalNegligenceButton.setDisable(busy);
			if (editSolDateButton != null)
				editSolDateButton.setDisable(busy);
			if (editPartiesButton != null)
				editPartiesButton.setDisable(busy);
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
		new Thread(() ->
		{
			try {
				boolean deleted = caseDetailService.softDeleteCase(activeCaseId, tenantId);
				runOnFx(() ->
				{
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
				runOnFx(() ->
				{
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
		boolean showDelete = current != null && caseDetailService != null && !editMode && !detailsEditMode;
		setVisibleManaged(deleteCaseButton, showDelete);
	}

	// ----------------------------
	// Change actions
	// ----------------------------

	private void onEditCaseNameField() {
		showTextFieldDialog("Edit Case Name", "Case name", currentOverview == null ? "" : currentOverview.getCaseName(), true,
				value -> saveCoreOverviewField("name", value));
	}

	private void onEditCaseNumberField() {
		showTextFieldDialog("Edit Case Number", "Case number", currentOverview == null ? "" : currentOverview.getCaseNumber(), false, value -> saveCoreOverviewField("caseNumber",
				value));
	}

	private void onEditDescriptionField() {
		showTextAreaDialog("Edit Description", "Description / summary notes", currentOverview == null ? "" : currentOverview.getDescription(), value -> saveCoreOverviewField(
				"description", value));
	}

	private void onEditIncidentDateField() {
		showDateFieldDialog("Edit Incident Date", "Incident date", authoritativeDate(MigratedCaseDateKey.DATE_OF_INJURY),
				value -> saveAuthoritativeDate(MigratedCaseDateKey.DATE_OF_INJURY, value));
	}

	private void onEditDateOfMedicalNegligenceField() {
		showDateFieldDialog("Edit Date of Medical Negligence", "Date of medical negligence", authoritativeDate(MigratedCaseDateKey.DATE_OF_MEDICAL_NEGLIGENCE),
				value -> saveAuthoritativeDate(MigratedCaseDateKey.DATE_OF_MEDICAL_NEGLIGENCE, value));
	}

	private void onEditSolDateField() {
		showNullableDateFieldDialog("Edit SOL Date", "SOL date", authoritativeDate(MigratedCaseDateKey.STATUTE_OF_LIMITATIONS), editSolDateButton,
				value -> saveAuthoritativeDate(MigratedCaseDateKey.STATUTE_OF_LIMITATIONS, value));
	}

	private void onEditTortNoticeDeadlineField() {
		showNullableDateFieldDialog("Edit Tort Notice Deadline", "Tort notice deadline", authoritativeDate(MigratedCaseDateKey.TORT_NOTICE_DEADLINE),
				editTortNoticeDeadlineButton, value -> saveAuthoritativeDate(MigratedCaseDateKey.TORT_NOTICE_DEADLINE, value));
	}

	private LocalDate authoritativeDate(MigratedCaseDateKey key) {
		if (!compatibilityDates.isLoaded()) return null;
		CompatibilityCaseDateState state = compatibilityDates.states().get(key);
		return state == null || state.startsAt() == null ? null : state.startsAt().toLocalDate();
	}

	private void showTextFieldDialog(String title, String label, String currentValue, boolean required, Consumer<String> onSave) {
		Dialog<String> dialog = new Dialog<>();
		AppDialogs.applySecondaryDialogShell(dialog, title);
		dialog.initOwner(dialogOwner(editCaseNameButton));
		ButtonType saveType = new ButtonType("Save", ButtonData.OK_DONE);
		dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);
		TextField field = new TextField(safeText(currentValue));
		Label error = new Label(required ? "Required" : "");
		error.setTextFill(Color.web("#b42318"));
		error.setVisible(false);
		error.setManaged(false);
		VBox box = new VBox(8, new Label(label), new Label("Current: " + (safeText(currentValue).isBlank() ? "—" : safeText(currentValue))), field, error);
		dialog.getDialogPane().setContent(box);
		Node save = dialog.getDialogPane().lookupButton(saveType);
		save.addEventFilter(javafx.event.ActionEvent.ACTION, e ->
		{
			if (required && safeText(field.getText()).trim().isBlank()) {
				error.setText(label + " is required.");
				error.setVisible(true);
				error.setManaged(true);
				e.consume();
			}
		});
		dialog.setResultConverter(button -> button == saveType ? field.getText() : null);
		dialog.showAndWait().ifPresent(onSave);
	}

	private void showTextAreaDialog(String title, String label, String currentValue, Consumer<String> onSave) {
		EnhancedTextArea.openEditor(dialogOwner(editDescriptionButton), title, safeText(currentValue), onSave);
	}

	private void showDateFieldDialog(String title, String label, LocalDate currentValue, Consumer<LocalDate> onSave) {
		Dialog<LocalDate> dialog = new Dialog<>();
		AppDialogs.applySecondaryDialogShell(dialog, title);
		dialog.initOwner(dialogOwner(editIncidentDateButton));
		ButtonType saveType = new ButtonType("Save", ButtonData.OK_DONE);
		dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);
		DatePicker picker = new DatePicker(currentValue);
		dialog.getDialogPane().setContent(new VBox(8, new Label(label), new Label("Current: " + formatDate(currentValue)), picker));
		dialog.setResultConverter(button -> button == saveType ? picker.getValue() : null);
		dialog.showAndWait().ifPresent(onSave);
	}

	private void showNullableDateFieldDialog(String title, String label, LocalDate currentValue, Button ownerButton, Consumer<LocalDate> onSave) {
		Dialog<Optional<LocalDate>> dialog = new Dialog<>();
		AppDialogs.applySecondaryDialogShell(dialog, title);
		dialog.initOwner(dialogOwner(ownerButton));
		ButtonType saveType = new ButtonType("Save", ButtonData.OK_DONE);
		dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);
		DatePicker picker = new DatePicker(currentValue);
		dialog.getDialogPane().setContent(new VBox(8, new Label(label), new Label("Current: " + formatDate(currentValue)), picker));
		dialog.setResultConverter(button -> button == saveType ? Optional.ofNullable(nullableDatePickerValue(picker)) : null);
		caseDateEditorOpen = true;
		try { dialog.showAndWait().ifPresent(value -> onSave.accept(value.orElse(null))); }
		finally { caseDateEditorOpen = false; applyDeferredCaseDatesRefresh(); }
	}

	private void showDetailsTextFieldDialog(String title, String label, String currentValue, boolean required, Button ownerButton, Consumer<String> onSave) {
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
		save.addEventFilter(javafx.event.ActionEvent.ACTION, e ->
		{
			if (required && safeText(field.getText()).trim().isBlank()) {
				error.setText(label + " is required.");
				error.setVisible(true);
				error.setManaged(true);
				e.consume();
			}
		});
		installUnsavedDetailsDialogConfirmation(dialog, ButtonType.CANCEL, () -> !Objects.equals(safeText(currentValue), safeText(field.getText())));
		dialog.setResultConverter(button -> button == saveType ? field.getText() : null);
		dialog.showAndWait().ifPresent(onSave);
	}

	private void showDetailsTextAreaDialog(String title, String label, String currentValue, Button ownerButton, Consumer<String> onSave) {
		EnhancedTextArea.openEditor(dialogOwner(ownerButton), title, safeText(currentValue), onSave);
	}

	private void showDetailsDateDialog(String title, String label, LocalDate currentValue, Button ownerButton, Consumer<LocalDate> onSave) {
		Dialog<LocalDate> dialog = new Dialog<>();
		AppDialogs.applySecondaryDialogShell(dialog, title);
		dialog.initOwner(dialogOwner(ownerButton));
		ButtonType saveType = new ButtonType("Save", ButtonData.OK_DONE);
		dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);
		DatePicker picker = new DatePicker(currentValue);
		dialog.getDialogPane().setContent(new VBox(8, new Label(label), new Label("Current: " + formatDate(currentValue)), picker));
		installUnsavedDetailsDialogConfirmation(dialog, ButtonType.CANCEL, () -> !Objects.equals(currentValue, picker.getValue()));
		dialog.setResultConverter(button -> button == saveType ? picker.getValue() : null);
		caseDateEditorOpen = true;
		try { dialog.showAndWait().ifPresent(onSave); }
		finally { caseDateEditorOpen = false; applyDeferredCaseDatesRefresh(); }
	}

	private void showDetailsNullableDateDialog(String title, String label, LocalDate currentValue, Button ownerButton, Consumer<LocalDate> onSave) {
		Dialog<Optional<LocalDate>> dialog = new Dialog<>();
		AppDialogs.applySecondaryDialogShell(dialog, title);
		dialog.initOwner(dialogOwner(ownerButton));
		ButtonType saveType = new ButtonType("Save", ButtonData.OK_DONE);
		dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);
		DatePicker picker = new DatePicker(currentValue);
		dialog.getDialogPane().setContent(new VBox(8, new Label(label), new Label("Current: " + formatDate(currentValue)), picker));
		installUnsavedDetailsDialogConfirmation(dialog, ButtonType.CANCEL, () -> !Objects.equals(currentValue, picker.getValue()));
		dialog.setResultConverter(button -> button == saveType ? Optional.ofNullable(nullableDatePickerValue(picker)) : null);
		caseDateEditorOpen = true;
		try { dialog.showAndWait().ifPresent(value -> onSave.accept(value.orElse(null))); }
		finally { caseDateEditorOpen = false; applyDeferredCaseDatesRefresh(); }
	}

	static LocalDate nullableDatePickerValue(DatePicker picker) {
		if (picker == null)
			return null;
		String editorText = picker.getEditor() == null ? null : picker.getEditor().getText();
		if (editorText == null || editorText.trim().isEmpty())
			return null;
		return picker.getValue();
	}

	private void showDetailsBooleanDialog(String title, String label, boolean currentValue, Button ownerButton, Consumer<Boolean> onSave) {
		Dialog<Boolean> dialog = new Dialog<>();
		AppDialogs.applySecondaryDialogShell(dialog, title);
		dialog.initOwner(dialogOwner(ownerButton));
		ButtonType saveType = new ButtonType("Save", ButtonData.OK_DONE);
		dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);
		CheckBox box = new CheckBox(label);
		box.setSelected(currentValue);
		dialog.getDialogPane().setContent(new VBox(8, new Label("Current: " + boolLabel(currentValue)), box));
		installUnsavedDetailsDialogConfirmation(dialog, ButtonType.CANCEL, () -> currentValue != box.isSelected());
		dialog.setResultConverter(button -> button == saveType ? box.isSelected() : null);
		dialog.showAndWait().ifPresent(onSave);
	}

	private void installUnsavedDetailsDialogConfirmation(Dialog<?> dialog, ButtonType cancelType, java.util.function.BooleanSupplier hasChanges) {
		Node cancel = dialog.getDialogPane().lookupButton(cancelType);
		if (cancel == null)
			return;
		cancel.addEventFilter(javafx.event.ActionEvent.ACTION, e ->
		{
			if (hasChanges == null || !hasChanges.getAsBoolean())
				return;
			boolean confirmed = AppDialogs.showConfirmation(
					dialog.getOwner(),
					"Discard Changes?",
					"Discard unsaved changes?",
					"Canceling will discard the changes in this field.",
					"Discard Changes",
					AppDialogs.DialogActionKind.DANGER);
			if (!confirmed)
				e.consume();
		});
	}

	private String displayCurrentValue(String value) {
		String safe = safeText(value);
		return safe.isBlank() ? "—" : safe;
	}

	private void saveSingleDetailsField(Consumer<CaseDetailsDraft> mutator) {
		CaseDetailsDraft draft = CaseDetailsDraft.from(current, currentOverview);
		if (mutator != null)
			mutator.accept(draft);
		detailsDraft = draft;
		detailsBaseline = CaseDetailsDraft.from(current, currentOverview);
		detailsEditRowVer = cloneRowVer(latestCaseRowVer != null ? latestCaseRowVer : (current == null ? null : current.getRowVer()));
		detailsEditor.renderEditors(draft);
		detailsSaveCoordinator.save();
	}

	private String textFromDetailsDraft(CaseDetailsDraft d, Node editor) {
		if (editor == detOfficePrinterCodeEditor)
			return d.officePrinterCode;
		if (editor == detAcceptedDetailEditor)
			return d.acceptedDetail;
		if (editor == detDeniedDetailEditor)
			return d.deniedDetail;
		if (editor == detSummaryEditor)
			return d.summary;
		return "";
	}

	private void saveSingleDetailsTextField(Node editor, String value) {
		saveSingleDetailsField(d ->
		{
			if (editor == detOfficePrinterCodeEditor)
				d.officePrinterCode = value;
			else if (editor == detAcceptedDetailEditor)
				d.acceptedDetail = value;
			else if (editor == detDeniedDetailEditor)
				d.deniedDetail = value;
			else if (editor == detSummaryEditor)
				d.summary = value;
		});
	}

	private void saveSingleDetailsDateField(Node editor, LocalDate value) {
		saveSingleDetailsField(d ->
		{
			if (editor == detDateFeeAgreementSignedEditor)
				d.dateFeeAgreementSigned = value;
			else if (editor == detDateNonEngagementLetterSentEditor)
				d.dateNonEngagementLetterSent = value;
		});
	}

	private void saveSingleDetailsBooleanField(Node editor, Boolean value) {
		saveSingleDetailsField(d ->
		{
			if (editor == detClientEstateEditor)
				d.clientEstate = toNullableBooleanStorage(value);
			else if (editor == detMedicalRecordsRequestedEditor)
				d.medicalRecordsRequested = value;
			else if (editor == detFeeAgreementSignedEditor)
				d.feeAgreementSigned = value;
			else if (editor == detNonEngagementLetterSentEditor)
				d.nonEngagementLetterSent = value;
			else if (editor == detAcceptedChronologyEditor)
				d.acceptedChronology = value;
			else if (editor == detAcceptedConsultantExpertSearchEditor)
				d.acceptedConsultantExpertSearch = value;
			else if (editor == detAcceptedTestifyingExpertSearchEditor)
				d.acceptedTestifyingExpertSearch = value;
			else if (editor == detAcceptedMedicalLiteratureEditor)
				d.acceptedMedicalLiterature = value;
			else if (editor == detDeniedChronologyEditor)
				d.deniedChronology = value;
			else if (editor == detReceivedUpdatesEditor)
				d.receivedUpdates = value;
		});
	}

	private void saveCoreOverviewField(String field, String textValue) {
		if (caseDao == null || caseId == null || current == null) {
			showError("Case is still loading. Please try again.");
			return;
		}
		long activeCaseId = caseId.longValue();
		setBusy(true);
		clearError();
		new Thread(() ->
		{
			try {
				CaseDetailDto latest = caseDao.getDetail(activeCaseId);
				if (latest == null || latest.getRowVer() == null || latest.getRowVer().length == 0) {
					throw new IllegalStateException("Could not load the latest case version.");
				}
				String name = "name".equals(field) ? safeText(textValue).trim() : latest.getCaseName();
				String number = "caseNumber".equals(field) ? safeText(textValue).trim() : latest.getCaseNumber();
				String description = "description".equals(field) ? safeText(textValue) : latest.getDescription();
				CaseDetailDto updated = caseDao.updateCaseNonDate(activeCaseId, name, number, description,
						latest.getSummary(), latest.getRowVer(), appState == null ? null
								: appState.getUserId());
				if (updated == null) {
					runOnFx(() ->
					{
						setBusy(false);
						showError("This case was updated elsewhere. Reload and try again.");
						reloadCurrentCaseForViewMode();
					});
					return;
				}
				runOnFx(() ->
				{
					applyCurrentDetailSnapshot(updated);
					applyDetail(updated);
					compatibilityDates.invalidate();
					setBusy(false);
					publishCaseFieldUpdated(activeCaseId, field, switch (field) {
					case "name" -> name;
					case "caseNumber" -> number;
					case "description" -> description;
					default -> null;
					});
					reloadCurrentCaseForViewMode();
				});
			} catch (Exception ex) {
				runOnFx(() ->
				{
					setBusy(false);
					showError("Failed to save " + field + ". " + ex.getMessage());
				});
			}
		}, "case-field-save-" + activeCaseId + "-" + field).start();
	}

	private void onEditStatusField() {
		if (!ensureTenantAndCaseForFieldDialog("status")) return;
		List<CaseDao.StatusRow> options = statusesForTenantCached(appState.getShaleClientId());
		CaseDao.StatusRow currentValue = currentOverview == null ? null : options.stream()
				.filter(v -> Objects.equals(v.id(), currentOverview.getPrimaryStatusId())).findFirst()
				.orElse(new CaseDao.StatusRow(currentOverview.getPrimaryStatusId(), currentOverview.getCaseStatus(), 0,
						currentOverview.getPrimaryStatusColor(), null, null, true, false));
		StatusCardFactory cards = new StatusCardFactory(id -> { });
		showCardChoiceFieldDialog("Edit Case Status", "Case Status", currentValue, options,
				CaseDao.StatusRow::id, v -> cards.create(new StatusCardModel(v.id(), v.name(), v.sortOrder(), v.color()), StatusCardFactory.Variant.MINI),
				false, null, changeStatusButton).ifPresent(v -> saveStatusField(v.id()));
	}

	private void onEditPracticeAreaField() {
		if (!ensureTenantAndCaseForFieldDialog("practice area")) return;
		List<CaseDao.PracticeAreaRow> options = practiceAreasForTenantCached(appState.getShaleClientId());
		CaseDao.PracticeAreaRow currentValue = currentOverview == null ? null : options.stream()
				.filter(v -> Objects.equals(v.id(), currentOverview.getPracticeAreaId())).findFirst()
				.orElse(new CaseDao.PracticeAreaRow(currentOverview.getPracticeAreaId(), currentOverview.getPracticeArea(),
						currentOverview.getPracticeAreaColor(), null));
		PracticeAreaCardFactory cards = new PracticeAreaCardFactory(id -> { });
		showCardChoiceFieldDialog("Edit Practice Area", "Practice Area", currentValue, options,
				CaseDao.PracticeAreaRow::id, v -> cards.create(new PracticeAreaCardModel(v.id(), v.name(), v.color()), PracticeAreaCardFactory.Variant.MINI),
				false, null, changePracticeAreaButton).ifPresent(v -> savePracticeAreaField(v.id()));
	}

	private void onEditResponsibleAttorneyField() {
		if (!ensureTenantAndCaseForFieldDialog("responsible attorney")) return;
		// Responsible Attorney is the one Case Overview user editor whose candidates
		// must come from the authoritative Users.is_attorney eligibility query.
		List<CaseDao.UserRow> eligibleAttorneys = caseDao.listAttorneysForTenant(appState.getShaleClientId());
		CaseDao.UserRow currentValue = currentOverview == null ? null : resolveResponsibleAttorneySelection(
				currentOverview.getResponsibleAttorneyUserId(), currentOverview.getResponsibleAttorney(),
				currentOverview.getResponsibleAttorneyColor(), eligibleAttorneys);
		showUserCardChoice("Edit Responsible Attorney", "Responsible Attorney", currentValue, eligibleAttorneys, false,
				null, changeResponsibleAttorneyButton).ifPresent(v -> saveResponsibleAttorneyField(v.id()));
	}

	static CaseDao.UserRow resolveResponsibleAttorneySelection(Integer currentUserId, String displayName, String color,
			List<CaseDao.UserRow> eligibleAttorneys) {
		if (currentUserId == null) return null;
		return eligibleAttorneys.stream()
				.filter(v -> Objects.equals(v.id(), currentUserId))
				.findFirst()
				.orElse(new CaseDao.UserRow(currentUserId, displayName, color));
	}

	private void onEditPrimaryLegalAssistantField() {
		if (!ensureTenantAndCaseForFieldDialog("primary legal assistant")) return;
		List<CaseDao.UserRow> options = caseDao.listUsersForTenant(appState.getShaleClientId());
		CaseDao.UserRow currentValue = currentOverview == null || currentOverview.getPrimaryLegalAssistantUserId() == null ? null : options.stream()
				.filter(v -> Objects.equals(v.id(), currentOverview.getPrimaryLegalAssistantUserId())).findFirst()
				.orElse(new CaseDao.UserRow(currentOverview.getPrimaryLegalAssistantUserId(), currentOverview.getPrimaryLegalAssistant(),
						currentOverview.getPrimaryLegalAssistantColor()));
		Optional<CaseDao.UserRow> selected = showUserCardChoice("Edit Primary Legal Assistant", "Primary Legal Assistant",
				currentValue, options, true, this::removePrimaryLegalAssistantField, changePrimaryLegalAssistantButton);
		if (selected.isPresent()) savePrimaryLegalAssistantField(selected.get().id());
	}

	private Optional<CaseDao.UserRow> showUserCardChoice(String title, String label, CaseDao.UserRow currentValue,
			List<CaseDao.UserRow> options, boolean clearable, Runnable removeAction, Button ownerButton) {
		UserCardFactory cards = new UserCardFactory(id -> { });
		return showCardChoiceFieldDialog(title, label, currentValue, options, CaseDao.UserRow::id,
				v -> cards.create(new UserCardModel(v.id(), v.displayName(), v.color(), null), Variant.MINI), clearable, removeAction, ownerButton);
	}

	/** Shared Case Overview editor shell; persistence deliberately remains in each caller. */
	private <T> Optional<T> showCardChoiceFieldDialog(String title, String fieldLabel, T currentValue, List<T> options,
			Function<T, Integer> identity, Function<T, Node> miniCardRenderer, boolean clearable, Runnable removeAction, Button ownerButton) {
		Dialog<T> dialog = new Dialog<>();
		AppDialogs.applySecondaryDialogShell(dialog, title);
		dialog.initOwner(dialogOwner(ownerButton));
		ButtonType removeType = new ButtonType("Remove", ButtonData.LEFT);
		ButtonType saveType = new ButtonType("Save", ButtonData.OK_DONE);
		if (clearable && currentValue != null) dialog.getDialogPane().getButtonTypes().add(removeType);
		dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, saveType);
		UserSelectionField<T> selector = new UserSelectionField<>(identity, Object::toString, ignored -> null,
				(field, candidates) -> showMiniCardPicker(dialog.getDialogPane().getScene().getWindow(), fieldLabel, candidates,
						identity, miniCardRenderer), clearable, miniCardRenderer).useUnifiedControlStyles();
		selector.setCandidates(options);
		selector.setSelectedUser(currentValue);
		selector.setMaxWidth(Double.MAX_VALUE);
		Node currentCard = currentValue == null ? emptyCurrentValue("No " + fieldLabel.toLowerCase(Locale.ROOT) + " assigned")
				: miniCardRenderer.apply(currentValue);
		VBox content = new VBox(10, new Label("Current " + fieldLabel), currentCard,
				new Label("New " + fieldLabel), selector);
		content.getStyleClass().add("field-edit-dialog-body");
		content.setMinWidth(420);
		dialog.getDialogPane().setContent(content);
		Node save = dialog.getDialogPane().lookupButton(saveType);
		if (save instanceof Button button) ControlStyles.apply(button, ControlStyles.Purpose.PRIMARY, ControlStyles.Size.STANDARD);
		if (save != null) save.disableProperty().bind(selector.selectedUserProperty().isNull());
		Node cancel = dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
		if (cancel instanceof Button button) ControlStyles.apply(button, ControlStyles.Purpose.SECONDARY, ControlStyles.Size.STANDARD);
		Node remove = dialog.getDialogPane().lookupButton(removeType);
		if (remove instanceof Button button) ControlStyles.apply(button, ControlStyles.Purpose.GHOST, ControlStyles.Size.STANDARD);
		dialog.setResultConverter(button -> {
			if (button == removeType && removeAction != null) removeAction.run();
			return button == saveType ? selector.getSelectedUser() : null;
		});
		return dialog.showAndWait();
	}

	private static Node emptyCurrentValue(String text) {
		Label empty = new Label(text);
		empty.getStyleClass().add("field-edit-current-value");
		return empty;
	}

	static <T> Optional<T> showMiniCardPicker(Window owner, String label, List<T> options,
			Function<T, Integer> identity, Function<T, Node> renderer) {
		Dialog<T> picker = new Dialog<>();
		AppDialogs.applySecondaryDialogShell(picker, "Select " + label);
		picker.initOwner(owner);
		picker.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
		// Dialog's default converter returns the pressed ButtonType. With Dialog<T>
		// that value survives erasure and contaminates the typed Optional. All shell
		// dismissal paths are cancellation; card activation sets the typed result
		// explicitly below before closing.
		picker.setResultConverter(buttonType -> null);
		VBox list = new VBox(8);
		for (T option : options) {
			Button row = new Button();
			ControlStyles.apply(row, ControlStyles.Purpose.SECONDARY, ControlStyles.Size.STANDARD);
			row.getStyleClass().add("user-selector-card-button");
			row.setMaxWidth(Double.MAX_VALUE);
			row.setGraphic(renderer.apply(option));
			row.setAccessibleText("Select " + label + " " + identity.apply(option));
			row.setOnAction(event -> { picker.setResult(option); picker.close(); });
			list.getChildren().add(row);
		}
		ScrollPane scroll = new ScrollPane(list);
		scroll.setFitToWidth(true); scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
		scroll.setPrefViewportHeight(Math.min(360, Math.max(120, options.size() * 58)));
		picker.getDialogPane().setContent(scroll);
		return picker.showAndWait();
	}

	private boolean ensureTenantAndCaseForFieldDialog(String fieldLabel) {
		if (caseDao == null || caseId == null || appState == null || appState.getShaleClientId() == null || appState.getShaleClientId() <= 0) {
			showError("Unable to edit " + fieldLabel + " without an active tenant/case context.");
			return false;
		}
		return true;
	}

	private void saveStatusField(int statusId) {
		long activeCaseId = caseId.longValue();
		setBusy(true);
		new Thread(() ->
		{
			try {
				Integer oldStatusId = currentOverview == null ? null : currentOverview.getPrimaryStatusId();
				String oldStatusName = currentOverview == null ? null : currentOverview.getCaseStatus();
				caseDao.setPrimaryStatus(activeCaseId, statusId, null);
				CaseDetailDto latest = caseDao.getDetail(activeCaseId);
				addStatusChangedTimelineEvent(activeCaseId, appState.getShaleClientId(), appState.getUserId(), oldStatusId, oldStatusName, statusId, null);
				runOnFx(() ->
				{
					applyCurrentDetailSnapshot(latest);
					applyDetail(latest);
					setBusy(false);
					publishCaseFieldUpdated(activeCaseId, "primaryStatusId", statusId);
					reloadCurrentCaseForViewMode();
				});
			} catch (Exception ex) {
				runOnFx(() ->
				{
					setBusy(false);
					showError("Failed to save status. " + ex.getMessage());
				});
			}
		}, "case-status-field-save-" + activeCaseId).start();
	}

	private void savePracticeAreaField(int practiceAreaId) {
		long activeCaseId = caseId.longValue();
		int tenantId = appState.getShaleClientId();
		setBusy(true);
		new Thread(() ->
		{
			try {
				Integer oldId = currentOverview == null ? null : currentOverview.getPracticeAreaId();
				String oldName = currentOverview == null ? null : currentOverview.getPracticeArea();
				caseDao.setPracticeArea(activeCaseId, tenantId, practiceAreaId);
				addPracticeAreaChangedTimelineEvent(activeCaseId, tenantId, appState.getUserId(), oldId, oldName, practiceAreaId, null);
				runOnFx(() ->
				{
					setBusy(false);
					publishCaseFieldUpdated(activeCaseId, "practiceAreaId", practiceAreaId);
					reloadCurrentCaseForViewMode();
				});
			} catch (Exception ex) {
				runOnFx(() ->
				{
					setBusy(false);
					showError("Failed to save practice area. " + ex.getMessage());
				});
			}
		}, "case-practice-area-field-save-" + activeCaseId).start();
	}

	private void saveResponsibleAttorneyField(int userId) {
		long activeCaseId = caseId.longValue();
		setBusy(true);
		new Thread(() ->
		{
			try {
				Integer oldId = currentOverview == null ? null : currentOverview.getResponsibleAttorneyUserId();
				String oldName = currentOverview == null ? null : currentOverview.getResponsibleAttorney();
				caseDao.setResponsibleAttorney(activeCaseId, userId);
				addResponsibleAttorneyChangedTimelineEvent(activeCaseId, appState.getShaleClientId(), appState.getUserId(), oldId, oldName, userId, null);
				runOnFx(() ->
				{
					setBusy(false);
					publishCaseFieldUpdated(activeCaseId, "responsibleAttorneyUserId", userId);
					reloadCurrentCaseForViewMode();
				});
			} catch (Exception ex) {
				runOnFx(() ->
				{
					setBusy(false);
					showError("Failed to save responsible attorney. " + ex.getMessage());
				});
			}
		}, "case-responsible-attorney-field-save-" + activeCaseId).start();
	}

	private void removePrimaryLegalAssistantField() {
		long activeCaseId = caseId.longValue();
		setBusy(true);
		new Thread(() ->
		{
			try {
				caseDao.removePrimaryLegalAssistant(activeCaseId, appState.getShaleClientId());
				addTeamChangedTimelineEvent(activeCaseId, appState.getShaleClientId(), appState.getUserId());
				runOnFx(() ->
				{
					setBusy(false);
					publishCaseFieldUpdated(activeCaseId, "primaryLegalAssistantUserId", null);
					reloadCurrentCaseForViewMode();
				});
			} catch (Exception ex) {
				runOnFx(() ->
				{
					setBusy(false);
					showError("Failed to remove primary legal assistant. " + ex.getMessage());
				});
			}
		}, "case-primary-legal-assistant-field-remove-" + activeCaseId).start();
	}

	private void savePrimaryLegalAssistantField(int userId) {
		long activeCaseId = caseId.longValue();
		setBusy(true);
		new Thread(() ->
		{
			try {
				caseDao.setPrimaryLegalAssistant(activeCaseId, appState.getShaleClientId(), userId);
				addTeamChangedTimelineEvent(activeCaseId, appState.getShaleClientId(), appState.getUserId());
				runOnFx(() ->
				{
					setBusy(false);
					publishCaseFieldUpdated(activeCaseId, "primaryLegalAssistantUserId", userId);
					reloadCurrentCaseForViewMode();
				});
			} catch (Exception ex) {
				runOnFx(() ->
				{
					setBusy(false);
					showError("Failed to save primary legal assistant. " + ex.getMessage());
				});
			}
		}, "case-primary-legal-assistant-field-save-" + activeCaseId).start();
	}

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

	private void unsubscribeLiveCaseUpdates() {
		liveUpdateHandler.unsubscribe();
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
					applyCurrentDetailSnapshot(fresh);
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
				applyCurrentDetailSnapshot(detail);
				detailsBaseline = CaseDetailsDraft.from(detail, currentOverview);
				detailsEditRowVer = cloneRowVer(detail.getRowVer());
			});
		}, "case-details-remote-baseline-" + activeCaseId).start();
	}

	private void applyCurrentDetailSnapshot(CaseDetailDto detail) {
		if (detail == null)
			return;
		current = detail;
		latestCaseRowVer = cloneRowVer(detail.getRowVer());
	}

	private static byte[] cloneRowVer(byte[] token) {
		return token == null ? null : java.util.Arrays.copyOf(token, token.length);
	}

	private static String rowVerHex(byte[] token) {
		if (token == null || token.length == 0)
			return "(null)";
		StringBuilder sb = new StringBuilder(token.length * 2);
		for (byte b : token)
			sb.append(String.format("%02x", b));
		return "0x" + sb;
	}

	private void logCaseConcurrencyConflict(String sourcePath, long caseId, byte[] loadedToken, byte[] submittedToken) {
		byte[] dbToken = null;
		try {
			CaseDetailDto dbDetail = caseDao == null ? null : caseDao.getDetail(caseId);
			dbToken = (dbDetail == null ? null : dbDetail.getRowVer());
		} catch (Exception ex) {
			System.err.println("Concurrency debug lookup failed for caseId=" + caseId + ", source=" + sourcePath + ": " + ex.getMessage());
		}
		System.err.println("Case concurrency conflict"
				+ " source=" + sourcePath
				+ " caseId=" + caseId
				+ " loadedToken=" + rowVerHex(loadedToken)
				+ " submittedToken=" + rowVerHex(submittedToken)
				+ " dbToken=" + rowVerHex(dbToken));
	}

	private void applyLiveCaseName(String newName) {
		String safeName = safeText(newName).trim();
		if (safeName.isBlank())
			return;

		if (ovCaseNameValue != null)
			ovCaseNameValue.setText(safeName);

		String num = (current == null) ? "" : safeText(current.getCaseNumber());
		if (caseTitleLabel != null)
			caseTitleLabel.setText(safeName);
		refreshCaseMetadata(num);
	}

	private void applyLiveCaseNumber(String newNumber) {
		String safeNum = safeText(newNumber).trim();

		if (ovCaseNumberValue != null)
			ovCaseNumberValue.setText(safeNum.isBlank() ? "—" : safeNum);

		String name = (current == null) ? "" : safeText(current.getCaseName()).trim();
		if (caseTitleLabel != null && !name.isBlank())
			caseTitleLabel.setText(name);
		refreshCaseMetadata(safeNum);
	}

	private void applyLiveCaseDescription(String newDescription) {
		if (ovDescriptionValue != null)
			ovDescriptionValue.setText(NarrativeMarkdownCodec.plainText(safeText(newDescription)));
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
				long teamLoadStartNanos = PerfLog.start();
				PerfLog.log("DAO", "start", "method=listCaseTeamRows page=case_view caseId=" + activeCaseId);
				List<CaseDao.CaseUserTeamRow> teamRows = caseDao.listCaseTeamRows(activeCaseId);
				PerfLog.logDone("DAO", "method=listCaseTeamRows page=case_view caseId=" + activeCaseId + " rows=" + (teamRows == null ? 0 : teamRows.size()), teamLoadStartNanos);

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
		long renderStartNanos = PerfLog.start();
		PerfLog.log("RENDER", "start", "panel=team page=case_view caseId=" + caseId);

		teamFlow.getChildren().clear();

		if (rows == null)
			rows = List.of();

		List<CaseDao.CaseUserTeamRow> filtered = deduplicatePracticeTeamRowsForDisplay(rows);

		if (filtered.isEmpty()) {
			teamFlow.getChildren().add(new Label("—"));
			PerfLog.logDone("RENDER", "panel=team page=case_view caseId=" + caseId + " childCount=1", renderStartNanos);
			return;
		}

		for (var r : filtered) {
			String name = safeText(r.displayName()).isBlank() ? "—" : r.displayName();
			UserCardModel model = new UserCardModel(r.userId(), name, r.color(), r.initials());
			Node card = userCardFactory.create(model, Variant.COMPACT);
			Tooltip.install(card, new Tooltip(roleLabel(r.roleId())));
			teamFlow.getChildren().add(card);
		}
		PerfLog.logDone("RENDER", "panel=team page=case_view caseId=" + caseId + " childCount=" + teamFlow.getChildren().size(), renderStartNanos);
	}

	private List<CaseDao.CaseUserTeamRow> deduplicatePracticeTeamRowsForDisplay(List<CaseDao.CaseUserTeamRow> rows) {
		if (rows == null || rows.isEmpty())
			return List.of();
		List<CaseDao.CaseUserTeamRow> ordered = rows.stream()
				.filter(r -> r != null && TEAM_ROLE_IDS.contains(r.roleId()))
				.sorted(java.util.Comparator
						.comparing((CaseDao.CaseUserTeamRow r) -> !isPrimaryResponsibleAttorney(r))
						.thenComparing(r -> !isPrimaryLegalAssistant(r))
						.thenComparingInt(CaseDao.CaseUserTeamRow::roleId)
						.thenComparing(r -> safeText(r.displayName()), String.CASE_INSENSITIVE_ORDER)
						.thenComparingInt(CaseDao.CaseUserTeamRow::userId))
				.toList();
		java.util.LinkedHashMap<Integer, CaseDao.CaseUserTeamRow> byUserId = new java.util.LinkedHashMap<>();
		for (CaseDao.CaseUserTeamRow row : ordered) {
			byUserId.putIfAbsent(row.userId(), row);
		}
		return new ArrayList<>(byUserId.values());
	}

	private static boolean isPrimaryResponsibleAttorney(CaseDao.CaseUserTeamRow row) {
		return row != null && RoleSemantics.isResponsibleAttorneyRoleId(row.roleId()) && row.isPrimary();
	}

	private static boolean isPrimaryLegalAssistant(CaseDao.CaseUserTeamRow row) {
		return row != null && row.roleId() == ROLE_LEGAL_ASSISTANT && row.isPrimary();
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

					dlg.showAndWaitForResult().ifPresent(res -> saveTeamAssignments(activeCaseId, res.assignments()));
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

	private void saveTeamAssignments(long activeCaseId, List<TeamEditorDialog.TeamAssignment> assignments) {
		setBusy(true);
		new Thread(() ->
		{
			try {
				List<CaseDao.TeamAssignmentRow> desired = (assignments == null ? List.<TeamEditorDialog.TeamAssignment>of() : assignments).stream()
						.map(a -> new CaseDao.TeamAssignmentRow(a.userId(), a.roleId()))
						.toList();
				caseDao.replaceCaseTeamAssignments(activeCaseId, desired);
				addTeamChangedTimelineEvent(activeCaseId, appState.getShaleClientId(), appState.getUserId());
				runOnFx(() ->
				{
					setBusy(false);
					clearError();
					publishCaseFieldUpdated(activeCaseId, "teamChanged", 1);
					reloadCurrentCaseForViewMode();
				});
			} catch (Exception ex) {
				runOnFx(() ->
				{
					setBusy(false);
					showError("Failed to save team. " + ex.getMessage());
				});
			}
		}, "case-team-save-" + activeCaseId).start();
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
		if (!isCaseUpdatesSectionActive()) {
			caseUpdatesStale = true;
			return;
		}
		if (caseUpdatesLoading) {
			return;
		}
		if (caseUpdatesLoadedOnce && !caseUpdatesStale) {
			return;
		}
		caseUpdatesLoading = true;
		updatesPanelController.loadCaseUpdatesAsync();
	}

	private void loadCaseUpdatesAsyncInternal() {
		if (caseDao == null || caseId == null)
			return;
		final long activeCaseId = caseId.longValue();

		new Thread(() ->
		{
			try {
				long updatesLoadStartNanos = PerfLog.start();
				PerfLog.log("DAO", "start", "method=listCaseUpdates page=case_view caseId=" + activeCaseId);
				List<CaseUpdateDto> updates = caseDao.listCaseUpdates(activeCaseId);
				PerfLog.logDone("DAO", "method=listCaseUpdates page=case_view caseId=" + activeCaseId + " rows=" + (updates == null ? 0 : updates.size()), updatesLoadStartNanos);
				runOnFx(() ->
				{
					caseUpdatesLoading = false;
					if (caseId == null || caseId.longValue() != activeCaseId)
						return;
					caseUpdatesLoadedOnce = true;
					caseUpdatesStale = false;
					renderCaseUpdates(updates);
				});
			} catch (Exception ex) {
				runOnFx(() ->
				{
					caseUpdatesLoading = false;
					caseUpdatesStale = true;
					showError("Failed to load case updates. " + ex.getMessage());
				});
			}
		}, "case-updates-load-" + activeCaseId).start();
	}

	private void renderCaseUpdates(List<CaseUpdateDto> updates) {
		updatesPanelController.renderCaseUpdates(updates);
	}

	private void renderCaseUpdatesInternal(List<CaseUpdateDto> updates) {
		List<CaseUpdateDto> safeUpdates = updates == null ? List.of() : List.copyOf(updates);
		caseUpdates = safeUpdates;
		if (editingCaseUpdateId != null
				&& safeUpdates.stream().noneMatch(u -> u != null && u.getId() == editingCaseUpdateId.longValue())) {
			editingCaseUpdateId = null;
			editingCaseUpdateDraftText = "";
			savingCaseUpdateEdit = false;
		}
		applyCaseUpdateFilterInternal();
	}

	private void applyCaseUpdateFilter() {
		updatesPanelController.applyCaseUpdateFilter();
	}

	private void applyCaseUpdateFilterInternal() {
		if (caseUpdatesFeedBox == null)
			return;
		long renderStartNanos = PerfLog.start();
		PerfLog.log("RENDER", "start", "panel=case_updates page=case_view caseId=" + caseId);

		caseUpdatesFeedBox.getChildren().clear();
		String searchQuery = safeText(caseUpdatesSearchField == null ? null : caseUpdatesSearchField.getText())
				.trim()
				.toLowerCase(java.util.Locale.ROOT);
		List<CaseUpdateDto> visibleUpdates = caseUpdates == null ? List.of()
				: caseUpdates.stream()
						.filter(dto -> caseUpdateMatchesSearch(dto, searchQuery))
						.toList();

		if (visibleUpdates.isEmpty()) {
			Label empty = new Label(searchQuery.isBlank() ? "No updates yet." : "No updates found.");
			empty.setWrapText(true);
			empty.setStyle("-fx-opacity: 0.7;");
			caseUpdatesFeedBox.getChildren().add(empty);
			if (caseUpdatesScrollPane != null)
				caseUpdatesScrollPane.setVvalue(0.0);
			PerfLog.logDone("RENDER", "panel=case_updates page=case_view caseId=" + caseId + " childCount=" + caseUpdatesFeedBox.getChildren().size(), renderStartNanos);
			return;
		}

		for (CaseUpdateDto dto : visibleUpdates) {
			if (dto == null)
				continue;
			caseUpdatesFeedBox.getChildren().add(createCaseUpdateCard(dto));
		}

		if (caseUpdatesScrollPane != null)
			caseUpdatesScrollPane.setVvalue(0.0);
		PerfLog.logDone("RENDER", "panel=case_updates page=case_view caseId=" + caseId + " childCount=" + caseUpdatesFeedBox.getChildren().size(), renderStartNanos);
	}

	private boolean caseUpdateMatchesSearch(CaseUpdateDto dto, String searchQuery) {
		if (dto == null) {
			return false;
		}
		if (searchQuery == null || searchQuery.isBlank()) {
			return true;
		}
		String noteText = safeText(dto.getNoteText()).toLowerCase(java.util.Locale.ROOT);
		if (noteText.contains(searchQuery)) {
			return true;
		}
		String authorText = safeAuthorName(dto).toLowerCase(java.util.Locale.ROOT);
		if (authorText.contains(searchQuery)) {
			return true;
		}
		String metadataText = safeText(buildCaseUpdateMetadata(dto)).toLowerCase(java.util.Locale.ROOT);
		return metadataText.contains(searchQuery);
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
					caseUpdatesLoadedOnce = true;
					caseUpdatesStale = false;
					renderCaseUpdates(updates);
					refreshLastUpdatedLabelAsync();
					if (submitCaseUpdateButton != null)
						submitCaseUpdateButton.setDisable(false);
					handleMedicalRecordsRequestedSafeguardAfterSavedUpdate(activeCaseId, activeClientId, trimmedText);
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

	private void handleMedicalRecordsRequestedSafeguardAfterSavedUpdate(long activeCaseId, int activeClientId, String savedNoteText) {
		if (caseDao == null || caseId == null || caseId.longValue() != activeCaseId) {
			return;
		}
		boolean alreadyRequested = current != null && Boolean.TRUE.equals(current.getMedicalRecordsRequested());
		MedicalRecordsRequestedCaseUpdateSafeguard safeguard = new MedicalRecordsRequestedCaseUpdateSafeguard(
				medicalRecordRequestKeywordMatcher,
				this::confirmMarkMedicalRecordsRequested,
				(caseIdToUpdate, clientId) -> caseDao.markMedicalRecordsRequested(caseIdToUpdate, clientId));
		try {
			boolean updated = safeguard.handleSavedCaseUpdate(activeCaseId, activeClientId, savedNoteText, alreadyRequested);
			if (updated) {
				reloadCurrentCaseForViewMode();
			}
		} catch (Exception ex) {
			showError("Failed to mark medical records requested. " + ex.getMessage());
		}
	}

	private boolean confirmMarkMedicalRecordsRequested() {
		return AppDialogs.showChoice(
				caseRootPane == null || caseRootPane.getScene() == null ? null : caseRootPane.getScene().getWindow(),
				"Medical Records Requested",
				"Medical Records Requested",
				"This update appears to mention medical records. Would you like to mark Medical Records Requested as true?",
				List.of(
						DialogAction.of("Yes", true, DialogActionKind.PRIMARY, true, false),
						DialogAction.cancel("No", false)))
				.orElse(false);
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
		card.setPadding(new Insets(10, 12, 10, 12));
		card.getStyleClass().addAll("secondary-panel", "shale-entity-card", "shale-entity-card-embedded");
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
					caseUpdatesLoadedOnce = true;
					caseUpdatesStale = false;
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
		UserCardModel model = new UserCardModel(
				userId,
				(displayName == null || displayName.isBlank()) ? "—" : displayName,
				userColorCss,
				null
		);

		var headerCard = createHeaderUserMini(userId, displayName, userColorCss);

		if (assignedUserHost != null)
			assignedUserHost.getChildren().setAll(headerCard);
		if (ovResponsibleAttorneyHost != null) {
			ensureUserCardFactory();
			ovResponsibleAttorneyHost.getChildren().setAll(userCardFactory.create(model, Variant.COMPACT));
		}
	}

	private Node createHeaderUserMini(Integer userId, String displayName, String userColorCss) {
		ensureUserCardFactory();
		UserCardModel model = new UserCardModel(
				userId,
				(displayName == null || displayName.isBlank()) ? "—" : displayName,
				userColorCss,
				null
		);
		return userCardFactory.create(model, Variant.MINI);
	}

	private void ensureUserCardFactory() {
		if (userCardFactory == null) {
			userCardFactory = new UserCardFactory(onOpenUser == null ? id -> {
			} : onOpenUser);
		}
	}

	private void renderPrimaryLegalAssistantMini(Integer userId, String displayName, String userColorCss) {
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

		if (ovPrimaryLegalAssistantHost != null)
			ovPrimaryLegalAssistantHost.getChildren().setAll(userCardFactory.create(model, Variant.COMPACT));
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

		var headerBadge = StatusIndicatorFactory.createStatusBadge(statusName, statusColorCss);

		if (statusHost != null)
			statusHost.getChildren().setAll(headerBadge);
		if (ovCaseStatusHost != null)
			ovCaseStatusHost.getChildren().setAll(StatusIndicatorFactory.createStatusPill(statusName, statusColorCss, PillSize.LARGE));
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

		ovPracticeAreaHost.getChildren().setAll(PracticeAreaIndicatorFactory.createPracticeAreaPill(name, colorHex, PracticeAreaIndicatorFactory.PillSize.LARGE));
	}

	private Node createOverviewInlineValue(String value, String colorCss) {
		String display = safeText(value).trim();
		Label label = new Label(display.isBlank() ? "—" : display);
		label.getStyleClass().add("case-overview-row-value");
		label.setWrapText(true);
		if (safeText(colorCss).isBlank()) {
			return label;
		}
		Region dot = new Region();
		dot.setMinSize(9, 9);
		dot.setPrefSize(9, 9);
		dot.setMaxSize(9, 9);
		dot.setStyle("-fx-background-radius: 999; -fx-background-color: " + ColorUtil.toCssBackgroundColor(colorCss) + ";");
		HBox row = new HBox(6, dot, label);
		row.setAlignment(Pos.CENTER_LEFT);
		row.getStyleClass().add("case-overview-row-value-host");
		return row;
	}

	private void renderDetailsStatusMini(Integer statusId, String statusName, String statusColorCss) {
		if (detCaseStatusHost == null)
			return;
		if (statusCardFactory == null) {
			statusCardFactory = new StatusCardFactory(onOpenStatus == null ? id ->
			{
			} : onOpenStatus);
		}
		detCaseStatusHost.getChildren().setAll(StatusIndicatorFactory.createStatusBadge(statusName, statusColorCss));
	}

	private void renderDetailsPracticeAreaMini(Integer practiceAreaId, String name, String colorHex) {
		if (detPracticeAreaHost == null)
			return;
		if (practiceAreaCardFactory == null) {
			practiceAreaCardFactory = new PracticeAreaCardFactory(onOpenPracticeArea == null ? id ->
			{
			} : onOpenPracticeArea);
		}
		PracticeAreaCardModel model = new PracticeAreaCardModel(practiceAreaId,
				(name == null || name.isBlank()) ? "—" : name,
				colorHex);
		detPracticeAreaHost.getChildren().setAll(PracticeAreaIndicatorFactory.createPracticeAreaPill(name, colorHex, PracticeAreaIndicatorFactory.PillSize.COMPACT));
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

		List<CaseOverviewDto.ContactSummary> safeClients = clients == null ? List.of()
				: clients.stream()
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
		boolean detailsVisible = detailsSectionPane != null && detailsSectionPane.isVisible();
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

	private static void setPaneVisible(Node pane, boolean visible) {
		if (pane == null)
			return;
		pane.setVisible(visible);
		pane.setManaged(visible);
		pane.setMouseTransparent(!visible);
	}

	private void activateCaseSectionRoot(Node activeRoot) {
		if (!Platform.isFxApplicationThread())
			throw new IllegalStateException("Case sections must be activated on the JavaFX application thread.");
		for (Node root : new Node[] {overviewScrollPane, detailsSectionPane, tasksTabPane, caseCalendarTabPane,
				caseDatesTabPane, caseRequestsTabPane, caseLinksTabPane, genericPane}) {
			if (root != null) setPaneVisible(root, root == activeRoot);
		}
		if (activeRoot != caseRequestsTabPane) caseMaterialRequestsTabController.deactivate();
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
				ovDescriptionValue.setText(NarrativeMarkdownCodec.plainText(safeText(detail.getDescription())));
			if (statusLabel != null)
				statusLabel.setText("Status: " + safe(detail.getCaseStatus()));
			renderLastUpdated(detail.getUpdatedAt());
			renderHeaderTitleFromDetail(detail);
		}

		void applyOverviewEditSafe(CaseOverviewDto dto) {
			if (dto == null)
				return;
			if (!editMode) {
				applyOverview(dto);
				return;
			}
			currentOverview = dto;
			renderOverviewCards(dto);
			renderHeaderTitleFromOverview(dto);
			if (ovCaseNumberValue != null)
				ovCaseNumberValue.setText(safe(dto.getCaseNumber()));
			loadTeamSectionAsync();
			renderOverviewDates(dto, true);
		}

		private void renderOverviewCards(CaseOverviewDto dto) {
			renderResponsibleAttorney(dto);
			renderStatus(dto);
			renderOverviewPartiesSection();
			renderPracticeArea(dto);
		}

		private void renderResponsibleAttorney(CaseOverviewDto dto) {
			renderResponsibleAttorneyMini(dto.getResponsibleAttorneyUserId(), safe(dto.getResponsibleAttorney()),
					dto.getResponsibleAttorneyColor());
			renderPrimaryLegalAssistantMini(dto.getPrimaryLegalAssistantUserId(), safe(dto.getPrimaryLegalAssistant()),
					dto.getPrimaryLegalAssistantColor());
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
			if (ovCaseNameEditor != null && !editMode)
				ovCaseNameEditor.setText(safe(dto.getCaseName()));
			if (ovCaseNumberValue != null)
				ovCaseNumberValue.setText(safe(dto.getCaseNumber()));
			if (ovCaseNumberEditor != null && !editMode)
				ovCaseNumberEditor.setText(safe(dto.getCaseNumber()));
			if (ovDescriptionValue != null)
				ovDescriptionValue.setText(NarrativeMarkdownCodec.plainText(safeText(dto.getDescription())));
			if (ovDescriptionEditor != null && !editMode)
				ovDescriptionEditor.setText(NarrativeMarkdownCodec.plainText(safeText(dto.getDescription())));
		}

		private void renderOverviewDates(CaseOverviewDto dto, boolean editSafeOnly) {
			// The general overview/detail DTOs still carry deferred compatibility values,
			// but existing-case fixed controls are rendered only by renderCompatibilityDates().
		}

		private void renderHeaderTitleFromOverview(CaseOverviewDto dto) {
			if (caseTitleLabel == null)
				return;
			String name = safeText(dto.getCaseName()).trim();
			if (!name.isBlank())
				caseTitleLabel.setText(name);
			else
				caseTitleLabel.setText("Case #" + dto.getCaseId());
			refreshCaseMetadata(safeText(dto.getCaseNumber()).trim());
		}

		private void renderHeaderTitleFromDetail(CaseDetailDto detail) {
			if (caseTitleLabel == null)
				return;
			String num = safeText(detail.getCaseNumber()).trim();
			String name = safeText(detail.getCaseName()).trim();
			if (!name.isBlank())
				caseTitleLabel.setText(name);
			else if (caseId != null)
				caseTitleLabel.setText("Case #" + caseId);
			refreshCaseMetadata(num);
			refreshIntakeTakenBy(detail.getIntakeTakenByUserId(), detail.getIntakeTakenByDisplayName());
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
			overviewEditRowVer = cloneRowVer(latestCaseRowVer != null ? latestCaseRowVer : current.getRowVer());
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

			setVisibleManaged(ovCaseNameValue, true);
			setVisibleManaged(ovCaseNameEditor, false);
			setVisibleManaged(ovCaseNumberValue, true);
			setVisibleManaged(ovCaseNumberEditor, false);

			setVisibleManaged(ovDescriptionValue, true);
			setVisibleManaged(ovDescriptionEditor, false);

			setVisibleManaged(ovIncidentDateValue, true);
			setVisibleManaged(ovIncidentDateEditor, false);
			setVisibleManaged(ovDateOfMedicalNegligenceValue, true);
			setVisibleManaged(ovDateOfMedicalNegligenceEditor, false);
			setVisibleManaged(ovSolDateValue, true);
			setVisibleManaged(ovSolDateEditor, false);
			setVisibleManaged(ovTortNoticeDeadlineValue, true);
			setVisibleManaged(ovTortNoticeDeadlineEditor, false);

			setVisibleManaged(editButton, false);
			setVisibleManaged(saveButton, false);
			setVisibleManaged(cancelButton, false);
			setVisibleManaged(editCaseNameButton, true);
			setVisibleManaged(editCaseNumberButton, true);
			setVisibleManaged(editDescriptionButton, true);
			setVisibleManaged(editIncidentDateButton, true);
			setVisibleManaged(editDateOfMedicalNegligenceButton, true);
			setVisibleManaged(editSolDateButton, true);
			setVisibleManaged(editTortNoticeDeadlineButton, true);
			setVisibleManaged(editPartiesButton, true);

			if (!enabled)
				hideRemoteUpdateBanner();

			setVisibleManaged(changeResponsibleAttorneyButton, true);
			setVisibleManaged(changePrimaryLegalAssistantButton, true);
			setVisibleManaged(changeStatusButton, true);
			setVisibleManaged(changeCallerButton, false);
			setVisibleManaged(changeClientButton, false);
			setVisibleManaged(changePracticeAreaButton, true);
			setVisibleManaged(changeOpposingCounselButton, false);
			setVisibleManaged(btnEditTeam, true);
			refreshDeleteAction();
		}

		void clearDraftState() {
			draft = null;
			overviewEditRowVer = null;
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
			if (compatibilityDates.isLoaded()) renderCompatibilityDates();
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
			draftIncidentDate = authoritativeDate(MigratedCaseDateKey.DATE_OF_INJURY);
			draftSolDate = authoritativeDate(MigratedCaseDateKey.STATUTE_OF_LIMITATIONS);
			renderCompatibilityDates();
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
			byte[] expectedRowVer = cloneRowVer(overviewEditRowVer != null ? overviewEditRowVer
					: (latestCaseRowVer != null ? latestCaseRowVer : current.getRowVer()));
			if (expectedRowVer == null || expectedRowVer.length == 0) {
				showError("Case concurrency token is missing. Reload and try again.");
				return null;
			}

			SaveBaseline baseline = new SaveBaseline(
					safeText(current.getCaseName()).trim(),
					safeText(current.getDescription()),
					safeText(current.getCaseNumber()).trim(),
					currentOverview,
					authoritativeDate(MigratedCaseDateKey.DATE_OF_INJURY),
					authoritativeDate(MigratedCaseDateKey.STATUTE_OF_LIMITATIONS),
					authoritativeDate(MigratedCaseDateKey.TORT_NOTICE_DEADLINE),
					current.getSummary(),
					expectedRowVer
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
					(ovSolDateEditor == null ? null : nullableDatePickerValue(ovSolDateEditor)),
					(ovTortNoticeDeadlineEditor == null ? authoritativeDate(MigratedCaseDateKey.TORT_NOTICE_DEADLINE) : nullableDatePickerValue(ovTortNoticeDeadlineEditor)),
					(draftTeamAssignments == null) ? null : List.copyOf(draftTeamAssignments)
			);
		}

		private void runSaveWorker(SaveRequest request) {
			try {
				SaveComputation computation = computeChangeSet(request);
				CaseDetailDto updated = persistBaseCaseFields(request);
				if (updated == null) {
					logCaseConcurrencyConflict(
							"CaseOverviewSaveCoordinator.runSaveWorker",
							request.saveCaseId(),
							request.baseline().expectedRowVer(),
							request.baseline().expectedRowVer());
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
				// Authoritative Case Date mutations append their own single transaction-bound event.
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

				CaseDetailDto latestDetail = caseDao.getDetail(request.saveCaseId());
				CaseDetailDto updatedForUi = latestDetail != null ? latestDetail : updated;

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

			LocalDate baseIncidentDate = request.baseline().incidentDate();
			LocalDate baseSolDate = request.baseline().solDate();
			LocalDate baseTortNoticeDeadline = request.baseline().tortNoticeDeadline();

			boolean incidentChanged = !Objects.equals(desired.desiredIncidentDate(), baseIncidentDate);
			boolean solChanged = !Objects.equals(desired.desiredSolDate(), baseSolDate);
			boolean tortNoticeChanged = !Objects.equals(desired.desiredTortNoticeDeadline(), baseTortNoticeDeadline);

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

			return new SaveComputation(incidentChanged, solChanged, tortNoticeChanged, statusChanged, callerChanged, clientChanged,
					practiceAreaChanged, attyChanged, opposingCounselChanged);
		}

		private CaseDetailDto persistBaseCaseFields(SaveRequest request) {
			return caseDao.updateCaseNonDate(
					request.saveCaseId(),
					request.saveDraft().caseName(),
					request.saveDraft().caseNumber(),
					request.saveDraft().description(),
					request.baseline().summary(),
					request.baseline().expectedRowVer(),
					request.userId()
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

			applyCurrentDetailSnapshot(updated);

			setEditMode(false);
			draft = null;

			hideRemoteUpdateBanner();

			applyDetail(updated);
			clearError();
			setBusy(false);

			publishFieldUpdates(request, computation, teamChanged);

			clearDraftState();
			compatibilityDates.invalidate();
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
			if (computation.tortNoticeChanged())
				publishCaseFieldUpdated(request.saveCaseId(), "tortNoticeDeadline",
						request.desired().desiredTortNoticeDeadline() == null ? null : request.desired().desiredTortNoticeDeadline().toString());

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
			Integer userId
	) {
	}

	private record SaveBaseline(
			String oldName,
			String oldDescription,
			String oldNumber,
			CaseOverviewDto baseOverview,
			LocalDate incidentDate,
			LocalDate solDate,
			LocalDate tortNoticeDeadline,
			String summary,
			byte[] expectedRowVer
	) {
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
			LocalDate desiredTortNoticeDeadline,
			List<CaseDao.TeamAssignmentRow> desiredTeamAssignments
	) {
	}

	private record SaveComputation(
			boolean incidentChanged,
			boolean solChanged,
			boolean tortNoticeChanged,
			boolean statusChanged,
			boolean callerChanged,
			boolean clientChanged,
			boolean practiceAreaChanged,
			boolean attyChanged,
			boolean opposingCounselChanged
	) {
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
					List<CaseDao.StatusRow> statuses = statusesForTenantCached(tenantId);
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
					picked ->
					{
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
					List<CaseDao.PracticeAreaRow> areas = practiceAreasForTenantCached(tenantId);
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
					picked ->
					{
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
					(firstName, lastName) ->
					{
						if (contactDao == null || appState == null || appState.getShaleClientId() == null || appState.getShaleClientId() <= 0)
							throw new IllegalStateException("Cannot create contact without an active tenant.");
						int createdId = contactDao.createContact(new ContactDao.CreateContactRequest(
								appState.getShaleClientId(),
								null,
								null,
								firstName,
								lastName,
								null,
								null,
								null,
								null,
								null,
								false,
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
		private final Consumer<UiRuntimeBridge.CaseUpdatedEvent> eventHandler = this::handleEvent;
		private final Consumer<UiRuntimeBridge.EntityUpdatedEvent> entityEventHandler = this::handleEntityEvent;
		private final Consumer<UiRuntimeBridge.ConnectivityEvent> connectivityEventHandler = this::handleConnectivityEvent;
		private boolean subscribed;

		void subscribe() {
			if (runtimeBridge == null || subscribed)
				return;

			runtimeBridge.subscribeCaseUpdated(eventHandler);
			runtimeBridge.subscribeEntityUpdated(entityEventHandler);
			runtimeBridge.subscribeConnectivity(connectivityEventHandler);
			subscribed = true;
		}

		void unsubscribe() {
			if (runtimeBridge == null || !subscribed) {
				return;
			}
			runtimeBridge.unsubscribeCaseUpdated(eventHandler);
			runtimeBridge.unsubscribeEntityUpdated(entityEventHandler);
			runtimeBridge.unsubscribeConnectivity(connectivityEventHandler);
			subscribed = false;
		}

		private void handleConnectivityEvent(UiRuntimeBridge.ConnectivityEvent event) {
			if (event != null && event.online() && caseId != null) queueRemoteCaseDatesRefresh();
		}

		private void handleEntityEvent(UiRuntimeBridge.EntityUpdatedEvent event) {
			if (event == null || appState == null || caseId == null) return;
			Integer tenantId = appState.getShaleClientId();
			if (tenantId == null || event.shaleClientId() != tenantId) return;
			String entityType = event.entityType();
			if (LiveUpdateEvents.ENTITY_CASE_DATES.equals(entityType)) {
				if (event.entityId() != caseId.longValue() || !rememberCaseDateEvent(event.eventId())) return;
				if (runtimeBridge != null) {
					String mine = runtimeBridge.getClientInstanceId();
					if (mine != null && !mine.isBlank() && mine.equals(event.clientInstanceId())) return;
				}
				queueRemoteCaseDatesRefresh();
				return;
			}
			if (!LiveUpdateEvents.ENTITY_CASE_LINK.equals(entityType)
					&& !LiveUpdateEvents.ENTITY_CASE_LINK_SHARE.equals(entityType)
					&& !LiveUpdateEvents.ENTITY_LINK_TYPE.equals(entityType)) return;
			long eventCaseId = getPatchLong(event, "caseId", -1L);
			if ((LiveUpdateEvents.ENTITY_CASE_LINK.equals(entityType) || LiveUpdateEvents.ENTITY_CASE_LINK_SHARE.equals(entityType))
					&& eventCaseId != caseId.longValue()) return;
			if (runtimeBridge != null) {
				String mine = runtimeBridge.getClientInstanceId();
				if (mine != null && !mine.isBlank() && mine.equals(event.clientInstanceId())) return;
			}
			runOnFx(() -> {
				caseLinksStale = true;
				invalidateOverviewPrimaryLinkAfterCaseLinkMutation();
				if (caseLinksTabPane != null && caseLinksTabPane.isVisible()) loadCaseLinksAsync(null);
			});
		}

		private long getPatchLong(UiRuntimeBridge.EntityUpdatedEvent event, String key, long fallback) {
			Object value = event.patch() == null ? null : event.patch().get(key);
			if (value instanceof Number number) return number.longValue();
			try { return value == null ? fallback : Long.parseLong(String.valueOf(value)); } catch (NumberFormatException ex) { return fallback; }
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
					"clientEstate", "officePrinterCode", "medicalRecordsRequested", "feeAgreementSigned",
					"dateFeeAgreementSigned", "nonEngagementLetterSent", "dateNonEngagementLetterSent",
					"acceptedChronology", "acceptedConsultantExpertSearch",
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
				// Keep ownership explicit: case updates and tasks refresh are separate.
				caseUpdatesStale = true;
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
				if (patch == null || patch.deleted()) {
					reloadCurrentCaseForViewMode();
					hideRemoteUpdateBanner();
					return;
				}

				boolean partyRelated = patch.patchedPrimaryCallerContactId() != null
						|| patch.clientAssignmentsPatched()
						|| patch.patchedPrimaryOpposingCounselContactId() != null;
				boolean teamRelated = patch.teamChanged();
				boolean overviewOrDetailsRelated = patch.patchedPrimaryStatusId() != null
						|| patch.patchedPracticeAreaId() != null
						|| patch.patchedResponsibleAttorneyUserId() != null
						|| patch.detailsTouched()
						|| partyRelated;

				if (partyRelated) {
					refreshPartiesSectionAsync();
				}
				if (teamRelated) {
					loadTeamSectionAsync();
				}
				if (overviewOrDetailsRelated) {
					refreshOverviewAndDetailsAfterStructuralPatchAsync();
				}
				if (!partyRelated && !teamRelated && !overviewOrDetailsRelated) {
					reloadCurrentCaseForViewMode();
				}
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
			boolean detailsTouched
	) {
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

		void applyCaseUpdateFilter() {
			applyCaseUpdateFilterInternal();
		}

		Node createCaseUpdateCard(CaseUpdateDto dto) {
			return createCaseUpdateCardInternal(dto);
		}
	}

	private enum CaseUpdatesPlacement {
		RIGHT,
		HIDDEN
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
		Boolean medicalRecordsRequested;
		Boolean feeAgreementSigned;
		LocalDate dateFeeAgreementSigned;
		Boolean nonEngagementLetterSent;
		LocalDate dateNonEngagementLetterSent;

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
			d.acceptedDate = detail == null ? null : detail.getAcceptedDate();
			d.closedDate = detail == null ? null : detail.getClosedDate();
			d.deniedDate = detail == null ? null : detail.getDeniedDate();


			d.clientEstate = detail == null ? "0" : normalizeDetailsCheckboxStorage(detail.getClientEstate());
			d.officePrinterCode = detail == null ? "" : safeText(detail.getOfficePrinterCode());
			d.medicalRecordsRequested = detail == null ? Boolean.FALSE : normalizeDetailsCheckboxBoolean(detail.getMedicalRecordsRequested());
			System.out.println("Case details load: feeAgreementSigned rawLoaded=" + (detail == null ? null : detail.getFeeAgreementSigned()));
			d.feeAgreementSigned = detail == null ? Boolean.FALSE : normalizeDetailsCheckboxBoolean(detail.getFeeAgreementSigned());
			d.nonEngagementLetterSent = detail == null ? Boolean.FALSE : normalizeDetailsCheckboxBoolean(detail.getNonEngagementLetterSent());

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
			c.medicalRecordsRequested = medicalRecordsRequested;
			c.feeAgreementSigned = feeAgreementSigned;
			c.dateFeeAgreementSigned = dateFeeAgreementSigned;
			c.nonEngagementLetterSent = nonEngagementLetterSent;
			c.dateNonEngagementLetterSent = dateNonEngagementLetterSent;
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
			byte[] expectedRowVer = cloneRowVer(detailsEditRowVer != null ? detailsEditRowVer
					: (latestCaseRowVer != null ? latestCaseRowVer : (current == null ? null : current.getRowVer())));
			if (expectedRowVer == null || expectedRowVer.length == 0) {
				showError("Case concurrency token is missing. Reload and try again.");
				return;
			}
			DetailsSaveRequest request;
			try {
				request = buildSaveRequest(detailsDraft, current, expectedRowVer);
			} catch (IllegalArgumentException ex) {
				showError(ex.getMessage());
				return;
			}

			if (!request.hasChanges()) {
				detailsLocalViewOverride = null;
				detailsDraft = null;
				detailsBaseline = null;
				detailsEditRowVer = null;
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
				CaseDetailDto updated = caseDao.updateCaseDetailsNonMigrated(
						request.caseId(),
						appState.getShaleClientId(),
						request.name(),
						request.caseNumber(),
						request.practiceAreaId(),
						request.description(),
						request.acceptedDate(),
						request.closedDate(),
						request.deniedDate(),
						request.clientEstate(),
						request.officePrinterCode(),
						request.medicalRecordsRequested(),
						request.feeAgreementSigned(),
						request.nonEngagementLetterSent(),
						request.acceptedChronology(),
						request.acceptedConsultantExpertSearch(),
						request.acceptedTestifyingExpertSearch(),
						request.acceptedMedicalLiterature(),
						request.acceptedDetail(),
						request.deniedChronology(),
						request.deniedDetail(),
						request.summary(),
						request.receivedUpdates(),
						request.expectedRowVer(),
						(appState == null ? null : appState.getUserId()));
				if (updated == null) {
					logCaseConcurrencyConflict(
							"CaseDetailsSaveCoordinator.runSaveWorker",
							request.caseId(),
							request.expectedRowVer(),
							request.expectedRowVer());
				}

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
				// The DAO compares authoritative snapshots and appends Details timeline rows
				// on the mutation connection, so controller refreshes cannot duplicate them.
				if (updated != null)
					currentOverview = caseDao.getOverview(request.caseId());
				if (updated != null) {
					CaseDetailDto latestDetail = caseDao.getDetail(request.caseId());
					if (latestDetail != null)
						updated = latestDetail;
				}

				final CaseDetailDto finalUpdated = updated;
				runOnFx(() -> handleSaveResult(request, finalUpdated));
			} catch (Exception ex) {
				runOnFx(() ->
				{
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
			applyCurrentDetailSnapshot(updated);
			detailsLocalViewOverride = null;
			detailsDraft = null;
			detailsBaseline = null;
			detailsEditRowVer = null;
			detailsEditor.setEditMode(false);
			renderDetailsFromCurrent();
			applyDetail(updated);
			clearError();
			publishDetailsFieldUpdates(request);
			setBusy(false);
			compatibilityDates.invalidate();
			reloadCurrentCaseForViewMode();
		}

		private void publishDetailsFieldUpdates(DetailsSaveRequest request) {
			CaseDetailDto baseline = request.baseline();
			publishIfChanged(request.caseId(), "name", normalizeRequired(baseline.getCaseName()), request.name());
			publishIfChanged(request.caseId(), "caseNumber", normalizeNullableText(baseline.getCaseNumber()), request.caseNumber());
			publishIfChanged(request.caseId(), "primaryStatusId", request.baselinePrimaryStatusId(), request.primaryStatusId());
			publishIfChanged(request.caseId(), "practiceAreaId", baseline.getPracticeAreaId(), request.practiceAreaId());
			publishIfChanged(request.caseId(), "description", normalizeNullableText(baseline.getDescription()), request.description());
			publishIfChanged(request.caseId(), "acceptedDate", baseline.getAcceptedDate(), request.acceptedDate());
			publishIfChanged(request.caseId(), "closedDate", baseline.getClosedDate(), request.closedDate());
			publishIfChanged(request.caseId(), "deniedDate", baseline.getDeniedDate(), request.deniedDate());
			publishIfChanged(request.caseId(), "clientEstate", normalizeNullableText(baseline.getClientEstate()), request.clientEstate());
			publishIfChanged(request.caseId(), "officePrinterCode", normalizeNullableText(baseline.getOfficePrinterCode()), request.officePrinterCode());
			publishIfChanged(request.caseId(), "medicalRecordsRequested", baseline.getMedicalRecordsRequested(), request.medicalRecordsRequested());
			publishIfChanged(request.caseId(), "feeAgreementSigned", baseline.getFeeAgreementSigned(), request.feeAgreementSigned());
			publishIfChanged(request.caseId(), "nonEngagementLetterSent", baseline.getNonEngagementLetterSent(), request.nonEngagementLetterSent());
			publishIfChanged(request.caseId(), "acceptedChronology", baseline.getAcceptedChronology(), request.acceptedChronology());
			publishIfChanged(request.caseId(), "acceptedConsultantExpertSearch", baseline.getAcceptedConsultantExpertSearch(), request.acceptedConsultantExpertSearch());
			publishIfChanged(request.caseId(), "acceptedTestifyingExpertSearch", baseline.getAcceptedTestifyingExpertSearch(), request.acceptedTestifyingExpertSearch());
			publishIfChanged(request.caseId(), "acceptedMedicalLiterature", baseline.getAcceptedMedicalLiterature(), request.acceptedMedicalLiterature());
			publishIfChanged(request.caseId(), "acceptedDetail", normalizeNullableText(baseline.getAcceptedDetail()), request.acceptedDetail());
			publishIfChanged(request.caseId(), "deniedChronology", baseline.getDeniedChronology(), request.deniedChronology());
			publishIfChanged(request.caseId(), "deniedDetail", normalizeNullableText(baseline.getDeniedDetail()), request.deniedDetail());
			publishIfChanged(request.caseId(), "summary", normalizeNullableText(baseline.getSummary()), request.summary());
			publishIfChanged(request.caseId(), "receivedUpdates", normalizeNullableText(baseline.getReceivedUpdates()), request.receivedUpdates());

		}

		private void publishIfChanged(long caseId, String field, Object before, Object after) {
			if (!Objects.equals(before, after))
				publishCaseFieldUpdated(caseId, field, after);
		}

		private DetailsSaveRequest buildSaveRequest(CaseDetailsDraft source, CaseDetailDto baseline, byte[] expectedRowVer) {
			String name = normalizeRequired(source.name);
			if (name.isBlank())
				throw new IllegalArgumentException("Case Name is required.");

			String caseNumber = normalizeNullableText(source.caseNumber);
			Integer practiceAreaId = source.practiceAreaId;
			String description = normalizeNullableText(source.description);
			String callerTime = normalizeCallerTimeInput(source.callerTime);
			String clientEstate = normalizeDetailsCheckboxStorage(source.clientEstate);
			String officePrinterCode = normalizeNullableText(source.officePrinterCode);
			Boolean medicalRecordsRequested = normalizeDetailsCheckboxBoolean(source.medicalRecordsRequested);
			Boolean feeAgreementSigned = normalizeDetailsCheckboxBoolean(source.feeAgreementSigned);
			LocalDate rawDateFeeAgreementSigned = source.dateFeeAgreementSigned;
			LocalDate dateFeeAgreementSigned = rawDateFeeAgreementSigned;
			Boolean nonEngagementLetterSent = normalizeDetailsCheckboxBoolean(source.nonEngagementLetterSent);
			LocalDate rawDateNonEngagementLetterSent = source.dateNonEngagementLetterSent;
			LocalDate dateNonEngagementLetterSent = rawDateNonEngagementLetterSent;
			Boolean acceptedChronology = normalizeDetailsCheckboxBoolean(source.acceptedChronology);
			Boolean acceptedConsultantExpertSearch = normalizeDetailsCheckboxBoolean(source.acceptedConsultantExpertSearch);
			Boolean acceptedTestifyingExpertSearch = normalizeDetailsCheckboxBoolean(source.acceptedTestifyingExpertSearch);
			Boolean acceptedMedicalLiterature = normalizeDetailsCheckboxBoolean(source.acceptedMedicalLiterature);
			Boolean deniedChronology = normalizeDetailsCheckboxBoolean(source.deniedChronology);
			String acceptedDetail = normalizeNullableText(source.acceptedDetail);
			String deniedDetail = normalizeNullableText(source.deniedDetail);
			String summary = normalizeNullableText(source.summary);
			String receivedUpdates = toNullableBooleanStorage(normalizeDetailsCheckboxBoolean(source.receivedUpdates));
			Boolean baselineMedicalRecordsRequested = normalizeDetailsCheckboxBoolean(baseline.getMedicalRecordsRequested());
			Boolean baselineFeeAgreementSigned = baseline.getFeeAgreementSigned();
			Boolean baselineNonEngagementLetterSent = baseline.getNonEngagementLetterSent();
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
			boolean changed = statusChanged ||
					!Objects.equals(name, normalizeRequired(baseline.getCaseName())) ||
					!Objects.equals(caseNumber, normalizeNullableText(baseline.getCaseNumber())) ||
					!Objects.equals(practiceAreaId, baseline.getPracticeAreaId()) ||
					!Objects.equals(description, normalizeNullableText(baseline.getDescription())) ||
					!Objects.equals(lifecycleDates.acceptedDate(), baseline.getAcceptedDate()) ||
					!Objects.equals(lifecycleDates.closedDate(), baseline.getClosedDate()) ||
					!Objects.equals(lifecycleDates.deniedDate(), baseline.getDeniedDate()) ||
					!Objects.equals(clientEstate, normalizeDetailsCheckboxStorage(baseline.getClientEstate())) ||
					!Objects.equals(officePrinterCode, normalizeNullableText(baseline.getOfficePrinterCode())) ||
					!Objects.equals(medicalRecordsRequested, baselineMedicalRecordsRequested) ||
					!Objects.equals(feeAgreementSigned, baselineFeeAgreementSigned) ||
					!Objects.equals(nonEngagementLetterSent, baselineNonEngagementLetterSent) ||
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
					medicalRecordsRequested,
					feeAgreementSigned,
					dateFeeAgreementSigned,
					nonEngagementLetterSent,
					dateNonEngagementLetterSent,
					acceptedChronology,
					acceptedConsultantExpertSearch,
					acceptedTestifyingExpertSearch,
					acceptedMedicalLiterature,
					acceptedDetail,
					deniedChronology,
					deniedDetail,
					summary,
					receivedUpdates,
					expectedRowVer,
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
			List<CaseDao.StatusRow> statuses = statusesForTenantCached(tenantId);
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
			List<CaseDao.PracticeAreaRow> areas = practiceAreasForTenantCached(tenantId);
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
			Boolean medicalRecordsRequested,
			Boolean feeAgreementSigned,
			LocalDate dateFeeAgreementSigned,
			Boolean nonEngagementLetterSent,
			LocalDate dateNonEngagementLetterSent,
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
			boolean hasChanges
	) {
	}

	private final class CaseDetailsEditor {
		void beginEdit() {
			CaseDetailsDraft base = resolveDetailsViewModel();
			detailsBaseline = base.copy();
			detailsDraft = base.copy();
			detailsEditRowVer = cloneRowVer(latestCaseRowVer != null ? latestCaseRowVer : (current == null ? null : current.getRowVer()));
			renderEditors(detailsDraft);
			setEditMode(true);
		}

		void cancelEdit() {
			CaseDetailsDraft restore = detailsBaseline != null ? detailsBaseline : resolveDetailsViewModel();
			renderView(restore);
			detailsDraft = null;
			detailsBaseline = null;
			detailsEditRowVer = null;
			setEditMode(false);
		}

		void setEditMode(boolean enabled) {
			detailsEditMode = enabled;
			setVisibleManaged(detailsEditButton, false);
			setVisibleManaged(detailsSaveButton, enabled);
			setVisibleManaged(detailsCancelButton, enabled);

			toggleDetailField(detNameValue, detNameEditor, enabled);
			toggleDetailField(detCaseNumberValue, detCaseNumberEditor, enabled);
			setVisibleManaged(detCaseStatusValue, !enabled);
			setVisibleManaged(detCaseStatusEditorRow, enabled);
			setVisibleManaged(detPracticeAreaIdValue, !enabled);
			setVisibleManaged(detPracticeAreaEditorRow, enabled);
			toggleDetailTextDisplayField(detDescriptionValue, detDescriptionEditor, enabled);
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
			toggleDetailField(detMedicalRecordsRequestedValue, detMedicalRecordsRequestedEditor, enabled);
			toggleDetailField(detFeeAgreementSignedValue, detFeeAgreementSignedEditor, enabled);
			toggleDetailField(detDateFeeAgreementSignedValue, detDateFeeAgreementSignedEditor, enabled);
			toggleDetailField(detNonEngagementLetterSentValue, detNonEngagementLetterSentEditor, enabled);
			toggleDetailField(detDateNonEngagementLetterSentValue, detDateNonEngagementLetterSentEditor, enabled);
			toggleDetailField(detAcceptedChronologyValue, detAcceptedChronologyEditor, enabled);
			toggleDetailField(detAcceptedConsultantExpertSearchValue, detAcceptedConsultantExpertSearchEditor, enabled);
			toggleDetailField(detAcceptedTestifyingExpertSearchValue, detAcceptedTestifyingExpertSearchEditor, enabled);
			toggleDetailField(detAcceptedMedicalLiteratureValue, detAcceptedMedicalLiteratureEditor, enabled);
			toggleDetailField(detAcceptedDetailValue, detAcceptedDetailEditor, enabled);
			toggleDetailField(detDeniedChronologyValue, detDeniedChronologyEditor, enabled);
			toggleDetailField(detDeniedDetailValue, detDeniedDetailEditor, enabled);
			toggleDetailTextDisplayField(detSummaryValue, detSummaryEditor, enabled);
			toggleDetailField(detReceivedUpdatesValue, detReceivedUpdatesEditor, enabled);
		}

		private void toggleDetailTextDisplayField(Label valueNode, TextArea editorNode, boolean editEnabled) {
			setVisibleManaged(valueNode, !editEnabled);
			setVisibleManaged(editorNode, editEnabled);
			if (editEnabled) {
				ReadOnlyTextDisplaySupport.apply(editorNode, true);
			}
		}

		private void toggleDetailField(Label valueNode, javafx.scene.control.Control editorNode, boolean editEnabled) {
			if (editorNode instanceof TextInputControl textInput) {
				setVisibleManaged(valueNode, false);
				setVisibleManaged(editorNode, true);
				ReadOnlyTextDisplaySupport.apply(textInput, editEnabled);
				return;
			}
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

		private void renderTriStateBoolean(CheckBox editor, Boolean value) {
			if (editor == null)
				return;
			editor.setAllowIndeterminate(true);
			if (value == null) {
				editor.setSelected(false);
				editor.setIndeterminate(true);
				return;
			}
			editor.setIndeterminate(false);
			editor.setSelected(Boolean.TRUE.equals(value));
		}

		private Boolean captureTriStateBoolean(CheckBox editor) {
			if (editor == null)
				return null;
			if (editor.isIndeterminate())
				return null;
			return editor.isSelected();
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
				detDescriptionValue.setText(NarrativeMarkdownCodec.plainText(safe(d.description)));
			if (detAcceptedDateValue != null)
				detAcceptedDateValue.setText(formatDate(d.acceptedDate));
			if (detClosedDateValue != null)
				detClosedDateValue.setText(formatDate(d.closedDate));
			if (detDeniedDateValue != null)
				detDeniedDateValue.setText(formatDate(d.deniedDate));
			if (detClientEstateValue != null)
				detClientEstateValue.setText(boolLabel(parseNullableBooleanStorage(d.clientEstate)));
			if (detOfficePrinterCodeValue != null)
				detOfficePrinterCodeValue.setText(safe(d.officePrinterCode));
			if (detMedicalRecordsRequestedValue != null)
				detMedicalRecordsRequestedValue.setText(boolLabel(d.medicalRecordsRequested));
			if (detFeeAgreementSignedValue != null)
				detFeeAgreementSignedValue.setText(boolLabel(Boolean.TRUE.equals(d.feeAgreementSigned)));
			if (detNonEngagementLetterSentValue != null)
				detNonEngagementLetterSentValue.setText(boolLabel(Boolean.TRUE.equals(d.nonEngagementLetterSent)));
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
				detSummaryValue.setText(NarrativeMarkdownCodec.plainText(safe(d.summary)));
			if (detReceivedUpdatesValue != null)
				detReceivedUpdatesValue.setText(boolLabel(d.receivedUpdates));
			if (!detailsEditMode)
				renderEditors(d);
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
			if (detAcceptedDateEditor != null)
				detAcceptedDateEditor.setValue(d.acceptedDate);
			if (detClosedDateEditor != null)
				detClosedDateEditor.setValue(d.closedDate);
			if (detDeniedDateEditor != null)
				detDeniedDateEditor.setValue(d.deniedDate);
			renderNullableBoolean(detClientEstateEditor, parseNullableBooleanStorage(d.clientEstate));
			if (detOfficePrinterCodeEditor != null)
				detOfficePrinterCodeEditor.setText(d.officePrinterCode);
			renderNullableBoolean(detMedicalRecordsRequestedEditor, d.medicalRecordsRequested);
			if (detFeeAgreementSignedEditor != null) {
				detFeeAgreementSignedEditor.setAllowIndeterminate(false);
				detFeeAgreementSignedEditor.setIndeterminate(false);
				detFeeAgreementSignedEditor.setSelected(Boolean.TRUE.equals(d.feeAgreementSigned));
			}
			if (detNonEngagementLetterSentEditor != null) {
				detNonEngagementLetterSentEditor.setAllowIndeterminate(false);
				detNonEngagementLetterSentEditor.setIndeterminate(false);
				detNonEngagementLetterSentEditor.setSelected(Boolean.TRUE.equals(d.nonEngagementLetterSent));
			}
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
			if (detAcceptedDateEditor != null)
				d.acceptedDate = detAcceptedDateEditor.getValue();
			if (detClosedDateEditor != null)
				d.closedDate = detClosedDateEditor.getValue();
			if (detDeniedDateEditor != null)
				d.deniedDate = detDeniedDateEditor.getValue();
			d.clientEstate = toNullableBooleanStorage(captureNullableBoolean(detClientEstateEditor));
			if (detOfficePrinterCodeEditor != null)
				d.officePrinterCode = safeText(detOfficePrinterCodeEditor.getText());
			d.medicalRecordsRequested = captureNullableBoolean(detMedicalRecordsRequestedEditor);
			d.feeAgreementSigned = detFeeAgreementSignedEditor != null && detFeeAgreementSignedEditor.isSelected();
			d.nonEngagementLetterSent = detNonEngagementLetterSentEditor != null && detNonEngagementLetterSentEditor.isSelected();
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
