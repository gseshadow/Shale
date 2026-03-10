package com.shale.ui.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import com.shale.core.dto.CaseDetailDto;
import com.shale.core.dto.CaseOverviewDto;
import com.shale.core.dto.CaseUpdateDto;
import com.shale.data.dao.CaseDao;
import com.shale.ui.component.factory.ContactCardFactory;
import com.shale.ui.component.factory.PracticeAreaCardFactory;
import com.shale.ui.component.factory.PracticeAreaCardFactory.PracticeAreaCardModel;
import com.shale.ui.component.factory.StatusCardFactory;
import com.shale.ui.component.factory.StatusCardFactory.StatusCardModel;
import com.shale.ui.component.factory.UserCardFactory;
import com.shale.ui.component.factory.UserCardFactory.UserCardModel;
import com.shale.ui.component.factory.UserCardFactory.Variant;
import com.shale.ui.component.dialog.TeamEditorDialog;
import com.shale.ui.services.UiRuntimeBridge;
import com.shale.ui.state.AppState;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ScrollPane;
import javafx.scene.Node;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

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
	private Button editButton;
	@FXML
	private Button saveButton;
	@FXML
	private Button cancelButton;

	@FXML
	private Button detailsEditButton;
	@FXML
	private Button detailsSaveButton;
	@FXML
	private Button detailsCancelButton;

	@FXML
	private Label errorLabel;
	@FXML
	private HBox remoteUpdateBanner;
	@FXML
	private Button reloadRemoteButton;

	@FXML
	private VBox tasksPanel;
	@FXML
	private TextField taskSearchField;
	@FXML
	private ListView<String> taskListView;
	@FXML
	private Button newTaskInlineButton;

	@FXML
	private TextField tasksTabSearchField;
	@FXML
	private ListView<String> tasksTabListView;

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
	private ScrollPane caseUpdatesScrollPane;
	@FXML
	private VBox caseUpdatesFeedBox;

	@FXML private Label detNameValue;
	@FXML private TextField detNameEditor;
	@FXML private Label detCaseNumberValue;
	@FXML private TextField detCaseNumberEditor;
	@FXML private Label detPracticeAreaIdValue;
	@FXML private TextField detPracticeAreaIdEditor;
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
	@FXML private TextField detClientEstateEditor;
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
	@FXML private TextArea detReceivedUpdatesEditor;

	// ----------------------------
	// Constants
	// ----------------------------

	private static final int ROLE_CASECONTACT_CALLER = 2;
	private static final int ROLE_CASECONTACT_CLIENT = 1;
	private static final int ROLE_CASECONTACT_OPPOSING_COUNSEL = 6;
	private static final int ROLE_RESPONSIBLE_ATTORNEY = 4;
	private static final int ROLE_INTAKE_STAFF = 5;
	private static final int ROLE_ATTORNEY = 7;
	private static final int ROLE_LEGAL_ASSISTANT = 11;
	private static final int ROLE_PARALEGAL = 12;
	private static final int ROLE_LAW_CLERK = 13;
	private static final int ROLE_CO_COUNSEL = 14;

	private static final java.util.Set<Integer> TEAM_ROLE_IDS = java.util.Set.of(
			ROLE_RESPONSIBLE_ATTORNEY,
			ROLE_INTAKE_STAFF,
			ROLE_ATTORNEY,
			ROLE_LEGAL_ASSISTANT,
			ROLE_PARALEGAL,
			ROLE_LAW_CLERK,
			ROLE_CO_COUNSEL
	);

	private static final List<String> SECTIONS = List.of(
			"Overview",
			"Tasks",
			"Timeline",
			"Details",
			"People",
			"Organizations",
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

	private PracticeAreaCardFactory practiceAreaCardFactory;
	private Consumer<Integer> onOpenPracticeArea;

	private CaseDao caseDao;
	private AppState appState;
	private UiRuntimeBridge runtimeBridge;

	// ----------------------------
	// Controller state
	// ----------------------------

	private Integer caseId;
	private boolean overviewLoaded = false;
	private boolean editMode = false;
	private boolean detailsEditMode = false;

	private CaseDetailDto current;
	private CaseOverviewDto currentOverview;

	private CaseEditModel draft;

	// edit-mode drafts
	private Integer draftPrimaryStatusId;

	private Integer draftPrimaryCallerContactId;
	private String draftPrimaryCallerName;

	private Integer draftPrimaryClientContactId;
	private String draftPrimaryClientName;

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

	private final Map<String, Button> sectionButtons = new LinkedHashMap<>();

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

	public void init(Integer caseId) {
		this.caseId = caseId;
		refreshHeader();
		refreshOverviewPlaceholders();
	}

	public void init(Integer caseId, CaseDao caseDao, AppState appState, UiRuntimeBridge runtimeBridge) {
		this.caseId = caseId;
		this.caseDao = caseDao;
		this.appState = appState;
		this.runtimeBridge = runtimeBridge;
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

	/** Optional - if you don’t set this, card click will Sys.out for now */
	public void setOnOpenPracticeArea(Consumer<Integer> onOpenPracticeArea) {
		this.onOpenPracticeArea = onOpenPracticeArea;
		this.practiceAreaCardFactory = new PracticeAreaCardFactory(onOpenPracticeArea);
	}

	// ----------------------------
	// Initialization
	// ----------------------------

	@FXML
	private void initialize() {
		refreshHeader();
		refreshOverviewPlaceholders();
		setupSections();
		setupOverviewTasksPanel();
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
			changeClientButton.setOnAction(e -> onChangeClient());
		if (changePracticeAreaButton != null)
			changePracticeAreaButton.setOnAction(e -> onChangePracticeArea());
		if (changeOpposingCounselButton != null)
			changeOpposingCounselButton.setOnAction(e -> onChangeOpposingCounsel());
		if (btnEditTeam != null)
			btnEditTeam.setOnAction(e -> onEditTeam());
		if (submitCaseUpdateButton != null)
			submitCaseUpdateButton.setOnAction(e -> onSubmitCaseUpdate());
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
			button.setOnAction(e -> onSectionSelected(section));
			sectionButtons.put(section, button);
			sectionButtonsBox.getChildren().add(button);
		}

		onSectionSelected("Overview");
	}

	private Button createSectionButton(String section) {
		Button button = new Button(section);
		button.setMaxWidth(Double.MAX_VALUE);
		button.setAlignment(Pos.CENTER_LEFT);
		button.setStyle("-fx-padding: 8 10 8 10; -fx-background-radius: 8; -fx-background-color: rgba(0,0,0,0.06);");
		return button;
	}

	private void setupOverviewTasksPanel() {
		if (taskListView != null) {
			taskListView.getItems().setAll(
					"Past due: Call client (placeholder)",
					"Upcoming: Request records (placeholder)",
					"Upcoming: Review radiology (placeholder)",
					"Upcoming: Send HIPAA auth (placeholder)",
					"Upcoming: Draft demand outline (placeholder)"
			);
		}
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
		renderClientMini(null, "—");
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

	private void onSectionSelected(String sectionName) {
		if (sectionName == null)
			return;

		setActiveSectionButton(sectionName);
		switch (sectionName) {
		case "Overview" -> showOverview();
		case "Tasks" -> showTasksTab();
		case "Details" -> showDetails();
		default -> showGeneric(sectionName);
		}
	}

	private void setActiveSectionButton(String activeSection) {
		sectionButtons.forEach((section, button) ->
		{
			if (Objects.equals(section, activeSection)) {
				button.setStyle("-fx-padding: 8 10 8 10; -fx-background-radius: 8; -fx-background-color: rgba(0,0,0,0.12); -fx-font-weight: bold;");
			} else {
				button.setStyle("-fx-padding: 8 10 8 10; -fx-background-radius: 8; -fx-background-color: rgba(0,0,0,0.06);");
			}
		});
	}

	private void showOverview() {
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
		setPaneVisible(overviewPane, false);
		setVisibleManaged(detailsScrollPane, false);
		setPaneVisible(tasksTabPane, true);
		setPaneVisible(genericPane, false);
		setPaneVisible(tasksPanel, false);

		if (tasksTabListView != null && tasksTabListView.getItems().isEmpty()) {
			tasksTabListView.getItems().setAll(
					"Call client (placeholder)",
					"Request records (placeholder)",
					"Review radiology (placeholder)",
					"Draft demand (placeholder)",
					"Schedule depo (placeholder)"
			);
		}
	}

	private void showDetails() {
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
		setPaneVisible(overviewPane, false);
		setVisibleManaged(detailsScrollPane, false);
		setPaneVisible(tasksTabPane, false);
		setPaneVisible(genericPane, true);
		setPaneVisible(tasksPanel, false);

		if (genericTitleLabel != null)
			genericTitleLabel.setText(sectionName);

		if (placeholderTextArea != null && (placeholderTextArea.getText() == null || placeholderTextArea.getText().isBlank())) {
			placeholderTextArea.setText(sectionName + " view is not implemented yet.");
		}
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

		new Thread(() ->
		{
			CaseOverviewDto overview = caseDao.getOverview(activeCaseId);
			CaseDetailDto detail = caseDao.getDetail(activeCaseId);

			runOnFx(() ->
			{
				if (overview != null)
					applyOverviewEditSafe(overview);

				if (detail != null) {
					current = detail;
					detailsLocalViewOverride = null;
					renderDetailsFromCurrent();
					if (!editMode)
						applyDetail(detail);
					else
						applyLastUpdatedLabel(detail.getUpdatedAt());
				}

				hideRemoteUpdateBanner();
				clearError();
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

	private void onChangeClient() {
		overviewPickerCoordinator.changeClient();
	}

	private void onChangePracticeArea() {
		overviewPickerCoordinator.changePracticeArea();
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
				if (fresh == null)
					return;

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
				if (overview != null)
					currentOverview = overview;
				if (detail != null) {
					current = detail;
					detailsBaseline = CaseDetailsDraft.from(detail, currentOverview);
				}
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
						.comparing((CaseDao.CaseUserTeamRow r) -> !(r.roleId() == ROLE_RESPONSIBLE_ATTORNEY && r.isPrimary()))
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
		return switch (roleId) {
		case ROLE_RESPONSIBLE_ATTORNEY -> "Responsible Attorney";
		case ROLE_INTAKE_STAFF -> "Intake Staff";
		case ROLE_ATTORNEY -> "Attorney";
		case ROLE_LEGAL_ASSISTANT -> "Legal Assistant";
		case ROLE_PARALEGAL -> "Paralegal";
		case ROLE_LAW_CLERK -> "Law Clerk";
		case ROLE_CO_COUNSEL -> "Co-counsel";
		default -> "Role " + roleId;
		};
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

			boolean isPrimary = (a.roleId() == ROLE_RESPONSIBLE_ATTORNEY);
			rows.add(new CaseDao.CaseUserTeamRow(a.userId(), name, color, initials, a.roleId(), isPrimary));
		}

		renderTeamCardsFromTeamRows(rows);
	}

	// ----------------------------
	// Case updates
	// ----------------------------

	private void loadCaseUpdatesAsync() {
		updatesPanelController.loadCaseUpdatesAsync();
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
		List<CaseUpdateDto> safeUpdates = updates == null ? List.of() : updates;

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
				caseDao.addCaseUpdate(activeCaseId, activeClientId, trimmedText, createdByUserId);
				runOnFx(() -> applyLastUpdatedLabel(LocalDateTime.now()));
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

		Label timestampLabel = new Label(formatDateTime(dto.getCreatedAt()));
		timestampLabel.setStyle("-fx-opacity: 0.75;");

		Region spacer = new Region();
		HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

		HBox topRow = new HBox(8, authorLabel, spacer, timestampLabel);
		topRow.setAlignment(Pos.CENTER_LEFT);

		Label noteLabel = new Label(safeText(dto.getNoteText()));
		noteLabel.setWrapText(true);

		VBox card = new VBox(6, topRow, noteLabel);
		card.setPadding(new Insets(8, 10, 8, 10));
		card.setStyle("-fx-background-color: rgba(0,0,0,0.04); -fx-background-radius: 8;");
		return card;
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
				false,
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

	private void renderClientMini(Integer contactId, String name) {
		if (ovClientHost == null)
			return;
		ovClientHost.getChildren().clear();

		if (contactCardFactory == null) {
			contactCardFactory = new ContactCardFactory(onOpenContact == null ? id ->
			{
			} : onOpenContact);
		}

		ovClientHost.getChildren().setAll(contactCardFactory.createMini(contactId, safe(name)));
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
		if (errorLabel != null) {
			errorLabel.setText("");
			errorLabel.setVisible(false);
			errorLabel.setManaged(false);
		}
	}

	private void showError(String message) {
		if (errorLabel != null) {
			errorLabel.setText(message == null ? "" : message);
			errorLabel.setVisible(message != null && !message.isBlank());
			errorLabel.setManaged(message != null && !message.isBlank());
		}
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

	private String formatDateTime(LocalDateTime value) {
		return value == null ? "—" : value.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
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

			String clientName = (editMode && draftPrimaryClientName != null && !draftPrimaryClientName.isBlank())
					? draftPrimaryClientName
					: dto.getClient();
			Integer clientId = (editMode && draftPrimaryClientContactId != null)
					? draftPrimaryClientContactId
					: dto.getPrimaryClientContactId();
			renderClientMini(clientId, clientName);

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
		}

		void clearDraftState() {
			draft = null;
			draftPrimaryStatusId = null;
			draftPrimaryCallerContactId = null;
			draftPrimaryCallerName = null;
			draftPrimaryClientContactId = null;
			draftPrimaryClientName = null;
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
			draftPrimaryClientContactId = (currentOverview == null ? null : currentOverview.getPrimaryClientContactId());
			draftPrimaryClientName = (currentOverview == null ? null : currentOverview.getClient());
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
					draftPrimaryClientContactId,
					draftPracticeAreaId,
					draftResponsibleAttorneyUserId,
					draftPrimaryOpposingCounselContactId,
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
				boolean teamChanged = persistTeamChanges(request);

				CaseOverviewDto updatedOverview = caseDao.getOverview(request.saveCaseId());
				String importantChangesNote = buildImportantOverviewChangesNote(request, updatedOverview, teamChanged);
				persistImportantChangesNote(request, importantChangesNote);

				runOnFx(() -> finalizeSuccessfulSave(request, updated, computation, teamChanged, importantChangesNote));
			} catch (Exception ex) {
				runOnFx(() ->
				{
					showError("Failed to save case. " + ex.getMessage());
					setBusy(false);
				});
			}
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

			Integer baseClientContactId = baseOverview == null ? null : baseOverview.getPrimaryClientContactId();
			boolean clientChanged = desired.desiredClientContactId() != null && !desired.desiredClientContactId().equals(baseClientContactId);

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
				caseDao.setPrimaryCaseContact(
						request.saveCaseId(), request.tenantId(), ROLE_CASECONTACT_CALLER, request.desired().desiredCallerContactId(), request.userId(), null
				);
			}

			if (computation.clientChanged()) {
				requireTenant(request.tenantId());
				caseDao.setPrimaryCaseContact(
						request.saveCaseId(), request.tenantId(), ROLE_CASECONTACT_CLIENT, request.desired().desiredClientContactId(), request.userId(), null
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
				caseDao.setPrimaryCaseContact(
						request.saveCaseId(), request.tenantId(), ROLE_CASECONTACT_OPPOSING_COUNSEL,
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

		private void persistImportantChangesNote(SaveRequest request, String importantChangesNote) {
			if (importantChangesNote.isBlank())
				return;
			requireTenant(request.tenantId());
			caseDao.addCaseUpdate(request.saveCaseId(), request.tenantId(), importantChangesNote, request.userId());
		}

		private void finalizeSuccessfulSave(
				SaveRequest request,
				CaseDetailDto updated,
				SaveComputation computation,
				boolean teamChanged,
				String importantChangesNote) {

			current = updated;

			setEditMode(false);
			draft = null;

			hideRemoteUpdateBanner();

			applyDetail(updated);
			clearError();
			setBusy(false);

			publishFieldUpdates(request, computation, teamChanged, importantChangesNote);

			clearDraftState();
			reloadCurrentCaseForViewMode();
		}

		private void publishFieldUpdates(
				SaveRequest request,
				SaveComputation computation,
				boolean teamChanged,
				String importantChangesNote) {

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
				publishCaseFieldUpdated(request.saveCaseId(), "primaryClientContactId", request.desired().desiredClientContactId());
			if (computation.practiceAreaChanged())
				publishCaseFieldUpdated(request.saveCaseId(), "practiceAreaId", request.desired().desiredPracticeAreaId());
			if (computation.attyChanged())
				publishCaseFieldUpdated(request.saveCaseId(), "responsibleAttorneyUserId", request.desired().desiredResponsibleAttorneyUserId());
			if (computation.opposingCounselChanged())
				publishCaseFieldUpdated(request.saveCaseId(), "primaryOpposingCounselContactId", request.desired().desiredOpposingCounselContactId());

			if (teamChanged)
				publishCaseFieldUpdated(request.saveCaseId(), "teamChanged", 1);

			if (!importantChangesNote.isBlank())
				publishCaseUpdateAdded(request.saveCaseId());
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

		private static String buildOverviewChangeLine(String label, String oldValue, String newValue) {
			String oldSafe = normalizeUpdateValue(oldValue);
			String newSafe = normalizeUpdateValue(newValue);
			if (oldSafe.equals(newSafe))
				return "";
			return label + " changed: from " + oldSafe + " to " + newSafe;
		}

		private static String normalizeUpdateValue(String value) {
			String trimmed = safeText(value).trim().replaceAll("\\s+", " ");
			return trimmed.isBlank() ? "none" : trimmed;
		}

		private static String buildImportantOverviewChangesNote(SaveRequest request, CaseOverviewDto updatedOverview, boolean teamChanged) {
			java.util.List<String> lines = new java.util.ArrayList<>();
			addIfPresent(lines, buildOverviewChangeLine("Case name", request.baseline().oldName(), request.saveDraft().caseName()));
			addIfPresent(lines, buildOverviewChangeLine("Case number", request.baseline().oldNumber(), request.saveDraft().caseNumber()));
			if (!normalizeUpdateValue(request.baseline().oldDescription()).equals(normalizeUpdateValue(request.saveDraft().description())))
				addIfPresent(lines, "Description changed");

			addIfPresent(lines, buildImportantOverviewChangesNote(request.baseline().baseOverview(), updatedOverview, teamChanged));
			return String.join("\n", lines);
		}

		private static String buildImportantOverviewChangesNote(CaseOverviewDto before, CaseOverviewDto after, boolean teamChanged) {
			if (before == null || after == null)
				return "";

			java.util.List<String> lines = new java.util.ArrayList<>();
			addIfPresent(lines, buildOverviewChangeLine("Responsible attorney", before.getResponsibleAttorney(), after.getResponsibleAttorney()));
			addIfPresent(lines, buildOverviewChangeLine("Practice area", before.getPracticeArea(), after.getPracticeArea()));
			addIfPresent(lines, buildOverviewChangeLine("Status", before.getCaseStatus(), after.getCaseStatus()));
			addIfPresent(lines, buildOverviewChangeLine("Caller", before.getCaller(), after.getCaller()));
			addIfPresent(lines, buildOverviewChangeLine("Client", before.getClient(), after.getClient()));
			addIfPresent(lines, buildOverviewChangeLine("Opposing counsel", before.getOpposingCounsel(), after.getOpposingCounsel()));
			if (teamChanged) {
				String oldTeam = String.join(", ", before.getTeam());
				String newTeam = String.join(", ", after.getTeam());
				if (normalizeUpdateValue(oldTeam).equals(normalizeUpdateValue(newTeam)))
					newTeam = safeText(newTeam).trim() + " roles updated";
				addIfPresent(lines, buildOverviewChangeLine("Team", oldTeam, newTeam));
			}
			return String.join("\n", lines);
		}

		private static void addIfPresent(List<String> out, String line) {
			if (out == null)
				return;
			String text = safeText(line).trim();
			if (!text.isBlank())
				out.add(text);
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
			Integer desiredClientContactId,
			Integer desiredPracticeAreaId,
			Integer desiredResponsibleAttorneyUserId,
			Integer desiredOpposingCounselContactId,
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
					runOnFx(() -> handleResponsibleAttorneyLoaded(users));
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
					runOnFx(() -> handleStatusLoaded(statuses));
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

		void changeClient() {
			changeContact(
					"Client change is unavailable.",
					"Change Client",
					"Select the primary client",
					"case-client-list-",
					() -> (editMode && draftPrimaryClientContactId != null)
							? draftPrimaryClientContactId
							: (currentOverview == null ? null : currentOverview.getPrimaryClientContactId()),
					picked -> {
						draftPrimaryClientContactId = picked.id();
						draftPrimaryClientName = picked.displayName();
						renderClientMini(draftPrimaryClientContactId, draftPrimaryClientName);
					});
		}

		void changePracticeArea() {
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
					runOnFx(() -> handlePracticeAreaLoaded(areas));
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

		private void handleStatusLoaded(List<CaseDao.StatusRow> statuses) {
			setBusy(false);
			if (statuses == null || statuses.isEmpty()) {
				showError("No statuses are configured for this tenant.");
				return;
			}

			Map<String, CaseDao.StatusRow> labelToRow = new LinkedHashMap<>();
			String preselect = null;
			Integer currentId = (editMode && draftPrimaryStatusId != null)
					? draftPrimaryStatusId
					: (currentOverview == null ? null : currentOverview.getPrimaryStatusId());
			for (CaseDao.StatusRow s : statuses) {
				String label = s.name() + (s.isClosed() ? " (Closed)" : "");
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

			draftPrimaryStatusId = picked.id();
			renderPrimaryStatusMini(picked.id(), picked.name(), picked.color());
		}

		private void handlePracticeAreaLoaded(List<CaseDao.PracticeAreaRow> areas) {
			setBusy(false);
			if (areas == null || areas.isEmpty()) {
				showError("No practice areas are configured for this tenant.");
				return;
			}

			Map<String, CaseDao.PracticeAreaRow> labelToRow = new LinkedHashMap<>();
			String preselect = null;
			Integer currentId = (editMode && draftPracticeAreaId != null)
					? draftPracticeAreaId
					: (currentOverview == null ? null : currentOverview.getPracticeAreaId());
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

			draftPracticeAreaId = picked.id();
			draftPracticeAreaName = picked.name();
			draftPracticeAreaColor = picked.color();
			renderPracticeAreaMini(picked.id(), picked.name(), picked.color());
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

		private Optional<String> showChoiceDialog(
				String title,
				String header,
				String content,
				String preselect,
				java.util.Collection<String> options) {
			ChoiceDialog<String> dialog = new ChoiceDialog<>(preselect, options);
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
			Integer patchedPrimaryClientContactId = extractPatchInt(rawPatch, "primaryClientContactId");
			Integer patchedPracticeAreaId = extractPatchInt(rawPatch, "practiceAreaId");
			Integer patchedResponsibleAttorneyUserId = extractPatchInt(rawPatch, "responsibleAttorneyUserId");
			Integer patchedPrimaryOpposingCounselContactId = extractPatchInt(rawPatch, "primaryOpposingCounselContactId");
			Integer patchedTeamChanged = extractPatchInt(rawPatch, "teamChanged");
			boolean teamChanged = patchedTeamChanged != null && patchedTeamChanged.intValue() == 1;
			Integer patchedCaseUpdateAdded = extractPatchInt(rawPatch, "caseUpdateAdded");
			boolean caseUpdateAdded = patchedCaseUpdateAdded != null && patchedCaseUpdateAdded.intValue() == 1;
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
				patchedPrimaryClientContactId,
				patchedPracticeAreaId,
				patchedResponsibleAttorneyUserId,
				patchedPrimaryOpposingCounselContactId,
				teamChanged,
				caseUpdateAdded,
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
			return patch.patchedPrimaryStatusId() != null
					|| patch.patchedPrimaryCallerContactId() != null
					|| patch.patchedPrimaryClientContactId() != null
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
			Integer patchedPrimaryClientContactId,
			Integer patchedPracticeAreaId,
			Integer patchedResponsibleAttorneyUserId,
			Integer patchedPrimaryOpposingCounselContactId,
			boolean teamChanged,
			boolean caseUpdateAdded,
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
		String practiceAreaId;
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
		String receivedUpdates;

		static CaseDetailsDraft from(CaseDetailDto detail, CaseOverviewDto overview) {
			CaseDetailsDraft d = new CaseDetailsDraft();
			d.name = detail == null ? "" : safeText(detail.getCaseName());
			d.caseNumber = detail == null ? "" : safeText(detail.getCaseNumber());

			Integer practiceAreaId = (detail == null ? null : detail.getPracticeAreaId());
			if (practiceAreaId == null && overview != null)
				practiceAreaId = overview.getPracticeAreaId();
			d.practiceAreaId = practiceAreaId == null ? "" : String.valueOf(practiceAreaId);

			d.description = detail == null ? "" : safeText(detail.getDescription());
			d.callerDate = detail == null ? null : detail.getCallerDate();
			d.callerTime = detail == null ? "" : safeText(detail.getCallerTime());
			d.acceptedDate = detail == null ? null : detail.getAcceptedDate();
			d.closedDate = detail == null ? null : detail.getClosedDate();
			d.deniedDate = detail == null ? null : detail.getDeniedDate();

			d.dateOfMedicalNegligence = detail == null ? null : detail.getDateOfMedicalNegligence();
			d.dateMedicalNegligenceWasDiscovered = detail == null ? null : detail.getDateMedicalNegligenceWasDiscovered();
			d.dateOfInjury = detail == null ? null : detail.getDateOfInjury();
			d.statuteOfLimitations = detail == null ? null : detail.getStatuteOfLimitations();
			d.tortNoticeDeadline = detail == null ? null : detail.getTortNoticeDeadline();
			d.discoveryDeadline = detail == null ? null : detail.getDiscoveryDeadline();

			d.clientEstate = detail == null ? "" : safeText(detail.getClientEstate());
			d.officePrinterCode = detail == null ? "" : safeText(detail.getOfficePrinterCode());
			d.medicalRecordsReceived = detail == null ? null : detail.getMedicalRecordsReceived();
			d.feeAgreementSigned = detail == null ? null : detail.getFeeAgreementSigned();
			d.dateFeeAgreementSigned = detail == null ? null : detail.getDateFeeAgreementSigned();

			d.acceptedChronology = detail == null ? null : detail.getAcceptedChronology();
			d.acceptedConsultantExpertSearch = detail == null ? null : detail.getAcceptedConsultantExpertSearch();
			d.acceptedTestifyingExpertSearch = detail == null ? null : detail.getAcceptedTestifyingExpertSearch();
			d.acceptedMedicalLiterature = detail == null ? null : detail.getAcceptedMedicalLiterature();
			d.acceptedDetail = detail == null ? "" : safeText(detail.getAcceptedDetail());

			d.deniedChronology = detail == null ? null : detail.getDeniedChronology();
			d.deniedDetail = detail == null ? "" : safeText(detail.getDeniedDetail());

			d.summary = detail == null ? "" : safeText(detail.getSummary());
			d.receivedUpdates = detail == null ? "" : safeText(detail.getReceivedUpdates());
			return d;
		}

		CaseDetailsDraft copy() {
			CaseDetailsDraft c = new CaseDetailsDraft();
			c.name = name;
			c.caseNumber = caseNumber;
			c.practiceAreaId = practiceAreaId;
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

				String importantChangesNote = "";
				if (updated != null)
					importantChangesNote = buildImportantDetailsChangesNote(request, request.baseline(), updated);
				persistImportantDetailsChangesNote(request, importantChangesNote);

				String noteForUi = importantChangesNote;
				runOnFx(() -> handleSaveResult(request, updated, noteForUi));
			} catch (Exception ex) {
				runOnFx(() -> {
					showError("Failed to save case details. " + ex.getMessage());
					setBusy(false);
				});
			}
		}

		private void handleSaveResult(DetailsSaveRequest request, CaseDetailDto updated, String importantChangesNote) {
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
			publishDetailsFieldUpdates(request, importantChangesNote);
			setBusy(false);
			reloadCurrentCaseForViewMode();
		}

		private void persistImportantDetailsChangesNote(DetailsSaveRequest request, String importantChangesNote) {
			if (importantChangesNote.isBlank())
				return;
			Integer tenantId = (appState == null ? null : appState.getShaleClientId());
			if (tenantId == null || tenantId <= 0)
				return;
			caseDao.addCaseUpdate(request.caseId(), tenantId, importantChangesNote, (appState == null ? null : appState.getUserId()));
		}

		private void publishDetailsFieldUpdates(DetailsSaveRequest request, String importantChangesNote) {
			CaseDetailDto baseline = request.baseline();
			publishIfChanged(request.caseId(), "name", normalizeRequired(baseline.getCaseName()), request.name());
			publishIfChanged(request.caseId(), "caseNumber", normalizeNullableText(baseline.getCaseNumber()), request.caseNumber());
			publishIfChanged(request.caseId(), "practiceAreaId", baseline.getPracticeAreaId(), request.practiceAreaId());
			publishIfChanged(request.caseId(), "description", normalizeNullableText(baseline.getDescription()), request.description());
			publishIfChanged(request.caseId(), "callerDate", baseline.getCallerDate(), request.callerDate());
			publishIfChanged(request.caseId(), "callerTime", normalizeNullableText(baseline.getCallerTime()), request.callerTime());
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

			if (!importantChangesNote.isBlank())
				publishCaseUpdateAdded(request.caseId());
		}

		private void publishIfChanged(long caseId, String field, Object before, Object after) {
			if (!Objects.equals(before, after))
				publishCaseFieldUpdated(caseId, field, after);
		}

		private String buildImportantDetailsChangesNote(DetailsSaveRequest request, CaseDetailDto before, CaseDetailDto after) {
			if (before == null || after == null)
				return "";
			java.util.List<String> lines = new java.util.ArrayList<>();
			addDetailIfPresent(lines, buildDetailsChangeLine("Case name", before.getCaseName(), after.getCaseName()));
			addDetailIfPresent(lines, buildDetailsChangeLine("Case number", before.getCaseNumber(), after.getCaseNumber()));
			addDetailIfPresent(lines, buildDetailsChangeLine("Practice area", safePracticeArea(before), safePracticeArea(after)));
			if (!Objects.equals(normalizeNullableText(before.getDescription()), normalizeNullableText(after.getDescription())))
				addDetailIfPresent(lines, "Description changed");
			addDetailIfPresent(lines, buildDetailsChangeLine("Caller date", formatDate(before.getCallerDate()), formatDate(after.getCallerDate())));
			addDetailIfPresent(lines, buildDetailsChangeLine("Caller time", before.getCallerTime(), after.getCallerTime()));
			addDetailIfPresent(lines, buildDetailsChangeLine("Accepted date", formatDate(before.getAcceptedDate()), formatDate(after.getAcceptedDate())));
			addDetailIfPresent(lines, buildDetailsChangeLine("Closed date", formatDate(before.getClosedDate()), formatDate(after.getClosedDate())));
			addDetailIfPresent(lines, buildDetailsChangeLine("Denied date", formatDate(before.getDeniedDate()), formatDate(after.getDeniedDate())));
			addDetailIfPresent(lines, buildDetailsChangeLine("Date of medical negligence", formatDate(before.getDateOfMedicalNegligence()), formatDate(after.getDateOfMedicalNegligence())));
			addDetailIfPresent(lines, buildDetailsChangeLine("Date negligence discovered", formatDate(before.getDateMedicalNegligenceWasDiscovered()), formatDate(after.getDateMedicalNegligenceWasDiscovered())));
			addDetailIfPresent(lines, buildDetailsChangeLine("Date of injury", formatDate(before.getDateOfInjury()), formatDate(after.getDateOfInjury())));
			addDetailIfPresent(lines, buildDetailsChangeLine("Statute of limitations", formatDate(before.getStatuteOfLimitations()), formatDate(after.getStatuteOfLimitations())));
			addDetailIfPresent(lines, buildDetailsChangeLine("Tort notice deadline", formatDate(before.getTortNoticeDeadline()), formatDate(after.getTortNoticeDeadline())));
			addDetailIfPresent(lines, buildDetailsChangeLine("Discovery deadline", formatDate(before.getDiscoveryDeadline()), formatDate(after.getDiscoveryDeadline())));
			addDetailIfPresent(lines, buildDetailsChangeLine("Client estate", before.getClientEstate(), after.getClientEstate()));
			addDetailIfPresent(lines, buildDetailsChangeLine("Office printer code", before.getOfficePrinterCode(), after.getOfficePrinterCode()));
			addDetailIfPresent(lines, buildDetailsChangeLine("Medical records received", boolLabel(before.getMedicalRecordsReceived()), boolLabel(after.getMedicalRecordsReceived())));
			addDetailIfPresent(lines, buildDetailsChangeLine("Fee agreement signed", boolLabel(before.getFeeAgreementSigned()), boolLabel(after.getFeeAgreementSigned())));
			addDetailIfPresent(lines, buildDetailsChangeLine("Date fee agreement signed", formatDate(before.getDateFeeAgreementSigned()), formatDate(after.getDateFeeAgreementSigned())));
			addDetailIfPresent(lines, buildDetailsChangeLine("Accepted chronology", boolLabel(before.getAcceptedChronology()), boolLabel(after.getAcceptedChronology())));
			addDetailIfPresent(lines, buildDetailsChangeLine("Accepted consultant expert search", boolLabel(before.getAcceptedConsultantExpertSearch()), boolLabel(after.getAcceptedConsultantExpertSearch())));
			addDetailIfPresent(lines, buildDetailsChangeLine("Accepted testifying expert search", boolLabel(before.getAcceptedTestifyingExpertSearch()), boolLabel(after.getAcceptedTestifyingExpertSearch())));
			addDetailIfPresent(lines, buildDetailsChangeLine("Accepted medical literature", boolLabel(before.getAcceptedMedicalLiterature()), boolLabel(after.getAcceptedMedicalLiterature())));
			if (!Objects.equals(normalizeNullableText(before.getAcceptedDetail()), normalizeNullableText(after.getAcceptedDetail())))
				addDetailIfPresent(lines, "Accepted detail changed");
			addDetailIfPresent(lines, buildDetailsChangeLine("Denied chronology", boolLabel(before.getDeniedChronology()), boolLabel(after.getDeniedChronology())));
			if (!Objects.equals(normalizeNullableText(before.getDeniedDetail()), normalizeNullableText(after.getDeniedDetail())))
				addDetailIfPresent(lines, "Denied detail changed");
			if (!Objects.equals(normalizeNullableText(before.getSummary()), normalizeNullableText(after.getSummary())))
				addDetailIfPresent(lines, "Summary changed");
			if (!Objects.equals(normalizeNullableText(before.getReceivedUpdates()), normalizeNullableText(after.getReceivedUpdates())))
				addDetailIfPresent(lines, "Received updates changed");
			return String.join("\n", lines);
		}

		private String buildDetailsChangeLine(String label, String oldValue, String newValue) {
			String oldSafe = normalizeUpdateValue(oldValue);
			String newSafe = normalizeUpdateValue(newValue);
			if (oldSafe.equals(newSafe))
				return "";
			return label + " changed: from " + oldSafe + " to " + newSafe;
		}

		private String normalizeUpdateValue(String value) {
			String trimmed = safeText(value).trim().replaceAll("\\s+", " ");
			return trimmed.isBlank() ? "none" : trimmed;
		}

		private void addDetailIfPresent(List<String> out, String line) {
			if (out == null)
				return;
			String text = safeText(line).trim();
			if (!text.isBlank())
				out.add(text);
		}

		private String safePracticeArea(CaseDetailDto detail) {
			if (detail == null || detail.getPracticeAreaId() == null)
				return "none";
			return String.valueOf(detail.getPracticeAreaId());
		}

		private DetailsSaveRequest buildSaveRequest(CaseDetailsDraft source, CaseDetailDto baseline) {
			String name = normalizeRequired(source.name);
			if (name.isBlank())
				throw new IllegalArgumentException("Case Name is required.");

			String caseNumber = normalizeNullableText(source.caseNumber);
			Integer practiceAreaId = parseNullableInteger(source.practiceAreaId);
			String description = normalizeNullableText(source.description);
			String callerTime = normalizeNullableText(source.callerTime);
			String clientEstate = normalizeNullableText(source.clientEstate);
			String officePrinterCode = normalizeNullableText(source.officePrinterCode);
			String acceptedDetail = normalizeNullableText(source.acceptedDetail);
			String deniedDetail = normalizeNullableText(source.deniedDetail);
			String summary = normalizeNullableText(source.summary);
			String receivedUpdates = normalizeNullableText(source.receivedUpdates);

			boolean changed =
				!Objects.equals(name, normalizeRequired(baseline.getCaseName())) ||
				!Objects.equals(caseNumber, normalizeNullableText(baseline.getCaseNumber())) ||
				!Objects.equals(practiceAreaId, baseline.getPracticeAreaId()) ||
				!Objects.equals(description, normalizeNullableText(baseline.getDescription())) ||
				!Objects.equals(source.callerDate, baseline.getCallerDate()) ||
				!Objects.equals(callerTime, normalizeNullableText(baseline.getCallerTime())) ||
				!Objects.equals(source.acceptedDate, baseline.getAcceptedDate()) ||
				!Objects.equals(source.closedDate, baseline.getClosedDate()) ||
				!Objects.equals(source.deniedDate, baseline.getDeniedDate()) ||
				!Objects.equals(source.dateOfMedicalNegligence, baseline.getDateOfMedicalNegligence()) ||
				!Objects.equals(source.dateMedicalNegligenceWasDiscovered, baseline.getDateMedicalNegligenceWasDiscovered()) ||
				!Objects.equals(source.dateOfInjury, baseline.getDateOfInjury()) ||
				!Objects.equals(source.statuteOfLimitations, baseline.getStatuteOfLimitations()) ||
				!Objects.equals(source.tortNoticeDeadline, baseline.getTortNoticeDeadline()) ||
				!Objects.equals(source.discoveryDeadline, baseline.getDiscoveryDeadline()) ||
				!Objects.equals(clientEstate, normalizeNullableText(baseline.getClientEstate())) ||
				!Objects.equals(officePrinterCode, normalizeNullableText(baseline.getOfficePrinterCode())) ||
				!Objects.equals(source.medicalRecordsReceived, baseline.getMedicalRecordsReceived()) ||
				!Objects.equals(source.feeAgreementSigned, baseline.getFeeAgreementSigned()) ||
				!Objects.equals(source.dateFeeAgreementSigned, baseline.getDateFeeAgreementSigned()) ||
				!Objects.equals(source.acceptedChronology, baseline.getAcceptedChronology()) ||
				!Objects.equals(source.acceptedConsultantExpertSearch, baseline.getAcceptedConsultantExpertSearch()) ||
				!Objects.equals(source.acceptedTestifyingExpertSearch, baseline.getAcceptedTestifyingExpertSearch()) ||
				!Objects.equals(source.acceptedMedicalLiterature, baseline.getAcceptedMedicalLiterature()) ||
				!Objects.equals(acceptedDetail, normalizeNullableText(baseline.getAcceptedDetail())) ||
				!Objects.equals(source.deniedChronology, baseline.getDeniedChronology()) ||
				!Objects.equals(deniedDetail, normalizeNullableText(baseline.getDeniedDetail())) ||
				!Objects.equals(summary, normalizeNullableText(baseline.getSummary())) ||
				!Objects.equals(receivedUpdates, normalizeNullableText(baseline.getReceivedUpdates()));

			return new DetailsSaveRequest(
				caseId.longValue(),
				name,
				caseNumber,
				practiceAreaId,
				description,
				source.callerDate,
				callerTime,
				source.acceptedDate,
				source.closedDate,
				source.deniedDate,
				source.dateOfMedicalNegligence,
				source.dateMedicalNegligenceWasDiscovered,
				source.dateOfInjury,
				source.statuteOfLimitations,
				source.tortNoticeDeadline,
				source.discoveryDeadline,
				clientEstate,
				officePrinterCode,
				source.medicalRecordsReceived,
				source.feeAgreementSigned,
				source.dateFeeAgreementSigned,
				source.acceptedChronology,
				source.acceptedConsultantExpertSearch,
				source.acceptedTestifyingExpertSearch,
				source.acceptedMedicalLiterature,
				acceptedDetail,
				source.deniedChronology,
				deniedDetail,
				summary,
				receivedUpdates,
				baseline.getRowVer(),
				baseline,
				changed);
		}

		private Integer parseNullableInteger(String raw) {
			String normalized = normalizeNullableText(raw);
			if (normalized == null)
				return null;
			try {
				return Integer.valueOf(normalized);
			} catch (NumberFormatException ex) {
				throw new IllegalArgumentException("PracticeAreaId must be a whole number.");
			}
		}

		private String normalizeNullableText(String value) {
			String trimmed = safeText(value).trim();
			return trimmed.isBlank() ? null : trimmed;
		}

		private String normalizeRequired(String value) {
			return safeText(value).trim();
		}
	}

	private record DetailsSaveRequest(
			long caseId,
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
			toggleDetailField(detPracticeAreaIdValue, detPracticeAreaIdEditor, enabled);
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
			editor.setAllowIndeterminate(true);
			if (value == null) {
				editor.setIndeterminate(true);
				editor.setSelected(false);
				return;
			}
			editor.setIndeterminate(false);
			editor.setSelected(value);
		}

		private Boolean captureNullableBoolean(CheckBox editor) {
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
			if (detPracticeAreaIdValue != null)
				detPracticeAreaIdValue.setText(safe(d.practiceAreaId));
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
				detClientEstateValue.setText(safe(d.clientEstate));
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
				detReceivedUpdatesValue.setText(safe(d.receivedUpdates));
		}

		private void renderEditors(CaseDetailsDraft d) {
			if (d == null)
				return;

			if (detNameEditor != null)
				detNameEditor.setText(d.name);
			if (detCaseNumberEditor != null)
				detCaseNumberEditor.setText(d.caseNumber);
			if (detPracticeAreaIdEditor != null)
				detPracticeAreaIdEditor.setText(d.practiceAreaId);
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
			if (detClientEstateEditor != null)
				detClientEstateEditor.setText(d.clientEstate);
			if (detOfficePrinterCodeEditor != null)
				detOfficePrinterCodeEditor.setText(d.officePrinterCode);
			renderNullableBoolean(detMedicalRecordsReceivedEditor, d.medicalRecordsReceived);
			renderNullableBoolean(detFeeAgreementSignedEditor, d.feeAgreementSigned);
			if (detDateFeeAgreementSignedEditor != null)
				detDateFeeAgreementSignedEditor.setValue(d.dateFeeAgreementSigned);
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
			if (detReceivedUpdatesEditor != null)
				detReceivedUpdatesEditor.setText(d.receivedUpdates);
		}

		void captureEditors(CaseDetailsDraft d) {
			if (detNameEditor != null)
				d.name = safeText(detNameEditor.getText());
			if (detCaseNumberEditor != null)
				d.caseNumber = safeText(detCaseNumberEditor.getText());
			if (detPracticeAreaIdEditor != null)
				d.practiceAreaId = safeText(detPracticeAreaIdEditor.getText());
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
			if (detClientEstateEditor != null)
				d.clientEstate = safeText(detClientEstateEditor.getText());
			if (detOfficePrinterCodeEditor != null)
				d.officePrinterCode = safeText(detOfficePrinterCodeEditor.getText());
			d.medicalRecordsReceived = captureNullableBoolean(detMedicalRecordsReceivedEditor);
			d.feeAgreementSigned = captureNullableBoolean(detFeeAgreementSignedEditor);
			if (detDateFeeAgreementSignedEditor != null)
				d.dateFeeAgreementSigned = detDateFeeAgreementSignedEditor.getValue();
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
			if (detReceivedUpdatesEditor != null)
				d.receivedUpdates = safeText(detReceivedUpdatesEditor.getText());
		}
	}

	private record CaseEditModel(String caseName, String caseNumber, String description) {
	}

}
