package com.shale.ui.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import com.shale.core.dto.CaseDetailDto;
import com.shale.core.dto.CaseOverviewDto;
import com.shale.data.dao.CaseDao;
import com.shale.ui.component.factory.ContactCardFactory;
import com.shale.ui.component.factory.PracticeAreaCardFactory;
import com.shale.ui.component.factory.PracticeAreaCardFactory.PracticeAreaCardModel;
import com.shale.ui.component.factory.StatusCardFactory;
import com.shale.ui.component.factory.StatusCardFactory.StatusCardModel;
import com.shale.ui.component.factory.UserCardFactory;
import com.shale.ui.component.factory.UserCardFactory.UserCardModel;
import com.shale.ui.component.factory.UserCardFactory.Variant;
import com.shale.ui.services.UiRuntimeBridge;
import com.shale.ui.state.AppState;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class CaseController {

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
	private Button addEntryButton;
	@FXML
	private Button addTaskButton;
	@FXML
	private Button backToCasesButton;

	@FXML
	private ListView<String> sectionListView;
	@FXML
	private VBox overviewPane;
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
	private Label ovSolDateValue;

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

	private static final int ROLE_CASECONTACT_CALLER = 2;
	private static final int ROLE_CASECONTACT_CLIENT = 1;
	private static final int ROLE_CASECONTACT_OPPOSING_COUNSEL = 6;

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

	private Integer caseId;
	private boolean overviewLoaded = false;
	private boolean editMode = false;

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

	@FXML
	private void initialize() {
		refreshHeader();
		refreshOverviewPlaceholders();
		setupSections();
		setupOverviewTasksPanel();
		wireEditButtons();
		setEditMode(false);
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
	}

	// ----------------------------
	// Live updates
	// ----------------------------

	private void subscribeLiveCaseUpdates() {
		if (runtimeBridge == null)
			return;

		runtimeBridge.subscribeCaseUpdated(event ->
		{
			if (caseId == null || event == null || event.caseId() != caseId.intValue())
				return;

			String mine = runtimeBridge.getClientInstanceId();
			if (mine != null && !mine.isBlank() && mine.equals(event.clientInstanceId()))
				return;

			if (event.newName() != null) {
				runOnFx(() ->
				{
					applyLiveCaseName(event.newName());
					hideRemoteUpdateBanner();
					refreshCurrentAfterRemoteUpdateAsync();
				});
				return;
			}

			String rawPatch = event.rawPatchJson();
			String patchedName = extractPatchString(rawPatch, "name");
			String patchedDescription = extractPatchString(rawPatch, "description");
			Integer patchedPrimaryStatusId = extractPatchInt(rawPatch, "primaryStatusId");
			Integer patchedPrimaryCallerContactId = extractPatchInt(rawPatch, "primaryCallerContactId");
			Integer patchedPrimaryClientContactId = extractPatchInt(rawPatch, "primaryClientContactId");
			Integer patchedPracticeAreaId = extractPatchInt(rawPatch, "practiceAreaId");
			Integer patchedResponsibleAttorneyUserId = extractPatchInt(rawPatch, "responsibleAttorneyUserId");
			Integer patchedPrimaryOpposingCounselContactId = extractPatchInt(rawPatch, "primaryOpposingCounselContactId");

			if (editMode) {
				runOnFx(() ->
				{
					showRemoteUpdateBanner();
				});
				return;
			}

			if (patchedPrimaryStatusId != null
					|| patchedPrimaryCallerContactId != null
					|| patchedPrimaryClientContactId != null
					|| patchedPracticeAreaId != null
					|| patchedResponsibleAttorneyUserId != null
					|| patchedPrimaryOpposingCounselContactId != null) {
				runOnFx(() ->
				{
					reloadCurrentCaseForViewMode();
					hideRemoteUpdateBanner();
				});
				return;
			}

			if (patchedName != null || patchedDescription != null) {
				runOnFx(() ->
				{
					if (patchedName != null)
						applyLiveCaseName(patchedName);
					if (patchedDescription != null)
						applyLiveCaseDescription(patchedDescription);

					hideRemoteUpdateBanner();
					refreshCurrentAfterRemoteUpdateAsync();
				});
				return;
			}

			runOnFx(() ->
			{
				showRemoteUpdateBanner();
			});
		});
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

	private void applyLiveCaseDescription(String newDescription) {
		if (ovDescriptionValue != null)
			ovDescriptionValue.setText(safeText(newDescription));
	}

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
					if (!editMode)
						applyDetail(detail);
				}

				hideRemoteUpdateBanner();
				clearError();
			});
		}, "case-view-sync-" + activeCaseId).start();
	}

	private void applyOverview(CaseOverviewDto dto) {
		currentOverview = dto;

		if (caseTitleLabel != null) {
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

		renderResponsibleAttorneyMini(dto.getResponsibleAttorneyUserId(), safe(dto.getResponsibleAttorney()),
				dto.getResponsibleAttorneyColor());

		if (ovCaseNameValue != null)
			ovCaseNameValue.setText(safe(dto.getCaseName()));
		if (ovCaseNumberValue != null)
			ovCaseNumberValue.setText(safe(dto.getCaseNumber()));

		renderPrimaryStatusMini(dto.getPrimaryStatusId(), dto.getCaseStatus(), dto.getPrimaryStatusColor());

		// Caller/Client: ALWAYS render cards (even if id is null)
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

		// Practice Area: card (draft-aware)
		Integer paId = (editMode && draftPracticeAreaId != null) ? draftPracticeAreaId : dto.getPracticeAreaId();
		String paName = (editMode && draftPracticeAreaName != null && !draftPracticeAreaName.isBlank())
				? draftPracticeAreaName
				: dto.getPracticeArea();
		String paColor = (editMode && draftPracticeAreaColor != null && !draftPracticeAreaColor.isBlank())
				? draftPracticeAreaColor
				: dto.getPracticeAreaColor();
		renderPracticeAreaMini(paId, paName, paColor);

		if (ovTeamValue != null) {
			List<String> team = dto.getTeam();
			ovTeamValue.setText(team == null || team.isEmpty() ? "—" : String.join(", ", team));
		}

		if (ovIntakeDateValue != null)
			ovIntakeDateValue.setText(formatDate(dto.getIntakeDate()));
		if (ovIncidentDateValue != null)
			ovIncidentDateValue.setText(formatDate(dto.getIncidentDate()));
		if (ovSolDateValue != null)
			ovSolDateValue.setText(formatDate(dto.getSolDate()));

		if (ovDescriptionValue != null)
			ovDescriptionValue.setText(safeText(dto.getDescription()));
	}

	private void applyDetail(CaseDetailDto detail) {
		if (detail == null)
			return;

		if (!editMode && ovCaseNameValue != null)
			ovCaseNameValue.setText(safe(detail.getCaseName()));
		if (!editMode && ovDescriptionValue != null)
			ovDescriptionValue.setText(safeText(detail.getDescription()));

		if (statusLabel != null)
			statusLabel.setText("Status: " + safe(detail.getCaseStatus()));
		if (lastUpdatedLabel != null)
			lastUpdatedLabel.setText("Last updated: " + formatDateTime(detail.getUpdatedAt()));

		if (caseTitleLabel != null) {
			String num = safeText(detail.getCaseNumber()).trim();
			String name = safeText(detail.getCaseName()).trim();
			if (!name.isBlank() && !num.isBlank())
				caseTitleLabel.setText(name + " — " + num);
			else if (!name.isBlank())
				caseTitleLabel.setText(name);
			else if (!num.isBlank())
				caseTitleLabel.setText(num);
		}
	}

	private void applyOverviewEditSafe(CaseOverviewDto dto) {
		currentOverview = dto;

		// Always safe to refresh these while editing:
		renderResponsibleAttorneyMini(dto.getResponsibleAttorneyUserId(), safe(dto.getResponsibleAttorney()),
				dto.getResponsibleAttorneyColor());

		Integer statusId = (editMode && draftPrimaryStatusId != null) ? draftPrimaryStatusId : dto.getPrimaryStatusId();
		renderPrimaryStatusMini(statusId, dto.getCaseStatus(), dto.getPrimaryStatusColor());

		// Always render contact cards (draft if present, else dto)
		Integer callerId = (editMode && draftPrimaryCallerContactId != null) ? draftPrimaryCallerContactId
				: dto.getPrimaryCallerContactId();
		String callerName = (editMode && draftPrimaryCallerName != null && !draftPrimaryCallerName.isBlank())
				? draftPrimaryCallerName
				: dto.getCaller();
		renderCallerMini(callerId, callerName);

		Integer clientId = (editMode && draftPrimaryClientContactId != null) ? draftPrimaryClientContactId
				: dto.getPrimaryClientContactId();
		String clientName = (editMode && draftPrimaryClientName != null && !draftPrimaryClientName.isBlank())
				? draftPrimaryClientName
				: dto.getClient();
		renderClientMini(clientId, clientName);

		// Practice Area card (draft-aware)
		Integer paId = (editMode && draftPracticeAreaId != null) ? draftPracticeAreaId : dto.getPracticeAreaId();
		String paName = (editMode && draftPracticeAreaName != null && !draftPracticeAreaName.isBlank())
				? draftPracticeAreaName
				: dto.getPracticeArea();
		String paColor = (editMode && draftPracticeAreaColor != null && !draftPracticeAreaColor.isBlank())
				? draftPracticeAreaColor
				: dto.getPracticeAreaColor();
		renderPracticeAreaMini(paId, paName, paColor);

		String oppName = (editMode && draftPrimaryOpposingCounselName != null && !draftPrimaryOpposingCounselName.isBlank())
				? draftPrimaryOpposingCounselName
				: dto.getOpposingCounsel();

		Integer oppId = (editMode && draftPrimaryOpposingCounselContactId != null)
				? draftPrimaryOpposingCounselContactId
				: dto.getPrimaryOpposingCounselContactId();

		renderOpposingCounselMini(oppId, oppName);

		// Header title is OK to refresh
		if (caseTitleLabel != null) {
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

		// If NOT editing, apply everything
		if (!editMode) {
			applyOverview(dto);
			return;
		}

		// While editing: refresh only safe labels
		if (ovCaseNumberValue != null)
			ovCaseNumberValue.setText(safe(dto.getCaseNumber()));

		if (ovTeamValue != null) {
			List<String> team = dto.getTeam();
			ovTeamValue.setText(team == null || team.isEmpty() ? "—" : String.join(", ", team));
		}

		if (ovIntakeDateValue != null)
			ovIntakeDateValue.setText(formatDate(dto.getIntakeDate()));
		if (ovIncidentDateValue != null)
			ovIncidentDateValue.setText(formatDate(dto.getIncidentDate()));
		if (ovSolDateValue != null)
			ovSolDateValue.setText(formatDate(dto.getSolDate()));
	}

	// ----------------------------
	// Edit / Save / Cancel / Reload
	// ----------------------------

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

	private void onEdit() {
		// snapshot drafts from currentOverview (IMPORTANT: include client too)
		draftPrimaryStatusId = (currentOverview == null ? null : currentOverview.getPrimaryStatusId());

		draftPrimaryCallerContactId = (currentOverview == null ? null : currentOverview.getPrimaryCallerContactId());
		draftPrimaryCallerName = (currentOverview == null ? null : currentOverview.getCaller());

		draftPrimaryClientContactId = (currentOverview == null ? null : currentOverview.getPrimaryClientContactId());
		draftPrimaryClientName = (currentOverview == null ? null : currentOverview.getClient());

		// Practice Area drafts
		draftPracticeAreaId = (currentOverview == null ? null : currentOverview.getPracticeAreaId());
		draftPracticeAreaName = (currentOverview == null ? null : currentOverview.getPracticeArea());
		draftPracticeAreaColor = (currentOverview == null ? null : currentOverview.getPracticeAreaColor());

		// Opposing Counsel Drafts
		draftPrimaryOpposingCounselContactId = (currentOverview == null ? null : currentOverview.getPrimaryOpposingCounselContactId());
		draftPrimaryOpposingCounselName = (currentOverview == null ? null : currentOverview.getOpposingCounsel());

		if (current == null) {
			showError("Case is still loading. Please try again.");
			return;
		}

		draft = new CaseEditModel(current.getCaseName(), current.getDescription());

		if (ovCaseNameEditor != null)
			ovCaseNameEditor.setText(draft.caseName());
		if (ovDescriptionEditor != null)
			ovDescriptionEditor.setText(draft.description());

		hideRemoteUpdateBanner();
		clearError();

		setEditMode(true);

		// Force repaint of cards using draft values
		if (currentOverview != null)
			applyOverviewEditSafe(currentOverview);
	}

	private void onCancel() {
		draft = null;
		hideRemoteUpdateBanner();
		clearError();

		draftPrimaryStatusId = null;

		draftPrimaryCallerContactId = null;
		draftPrimaryCallerName = null;

		draftPrimaryClientContactId = null;
		draftPrimaryClientName = null;

		draftPracticeAreaId = null;
		draftPracticeAreaName = null;
		draftPracticeAreaColor = null;
		draftPrimaryOpposingCounselContactId = null;
		draftPrimaryOpposingCounselName = null;

		setEditMode(false);

		if (currentOverview != null)
			applyOverviewEditSafe(currentOverview);
		applyDetail(current);
	}

	private void onSave() {
		// Keep behavior obvious (helps if something silently returns)
		if (caseDao == null) {
			showError("Case service is unavailable.");
			return;
		}
		if (caseId == null) {
			showError("No case is selected.");
			return;
		}
		if (current == null) {
			showError("Case is still loading. Please try again.");
			return;
		}
		if (ovCaseNameEditor == null || ovDescriptionEditor == null) {
			showError("Edit fields are not available.");
			return;
		}

		final String oldName = safeText(current.getCaseName()).trim();
		final String oldDescription = safeText(current.getDescription());

		String name = safeText(ovCaseNameEditor.getText()).trim();
		String description = safeText(ovDescriptionEditor.getText());

		if (name.isEmpty()) {
			showError("Case Name is required.");
			return;
		}

		draft = new CaseEditModel(name, description);
		final CaseEditModel saveDraft = draft;
		final byte[] expectedRowVer = current.getRowVer();
		final long saveCaseId = caseId.longValue();

		final Integer tenantId = (appState == null ? null : appState.getShaleClientId());
		final Integer userId = (appState == null ? null : appState.getUserId());

		// ✅ SNAPSHOT draft selections NOW (before thread starts)
		final Integer desiredStatusId = draftPrimaryStatusId;
		final Integer desiredCallerContactId = draftPrimaryCallerContactId;
		final Integer desiredClientContactId = draftPrimaryClientContactId;
		final Integer desiredPracticeAreaId = draftPracticeAreaId;
		final Integer desiredResponsibleAttorneyUserId = draftResponsibleAttorneyUserId;
		final Integer desiredOpposingCounselContactId = draftPrimaryOpposingCounselContactId;

		setBusy(true);
		clearError();

		new Thread(() ->
		{
			try {
				CaseDetailDto updated = caseDao.updateCase(
						saveCaseId,
						saveDraft.caseName(),
						saveDraft.description(),
						expectedRowVer
				);

				if (updated == null) {
					runOnFx(() ->
					{
						showRemoteUpdateBanner();
						showError("This case was updated elsewhere. Reload and try again.");
						setBusy(false);
					});
					return;
				}

				// Use currentOverview as baseline (can be null)
				Integer baseStatusId = currentOverview == null ? null : currentOverview.getPrimaryStatusId();
				boolean statusChanged = desiredStatusId != null && !desiredStatusId.equals(baseStatusId);
				if (statusChanged) {
					caseDao.setPrimaryStatus(saveCaseId, desiredStatusId, null);
				}

				Integer baseCallerContactId = currentOverview == null ? null : currentOverview.getPrimaryCallerContactId();
				boolean callerChanged = desiredCallerContactId != null && !desiredCallerContactId.equals(baseCallerContactId);
				if (callerChanged) {
					if (tenantId == null || tenantId <= 0)
						throw new RuntimeException("No tenant is selected.");
					caseDao.setPrimaryCaseContact(
							saveCaseId, tenantId, ROLE_CASECONTACT_CALLER, desiredCallerContactId, userId, null
					);
				}

				Integer baseClientContactId = currentOverview == null ? null : currentOverview.getPrimaryClientContactId();
				boolean clientChanged = desiredClientContactId != null && !desiredClientContactId.equals(baseClientContactId);
				if (clientChanged) {
					if (tenantId == null || tenantId <= 0)
						throw new RuntimeException("No tenant is selected.");
					caseDao.setPrimaryCaseContact(
							saveCaseId, tenantId, ROLE_CASECONTACT_CLIENT, desiredClientContactId, userId, null
					);
				}

				Integer basePracticeAreaId = currentOverview == null ? null : currentOverview.getPracticeAreaId();
				boolean practiceAreaChanged = desiredPracticeAreaId != null && !desiredPracticeAreaId.equals(basePracticeAreaId);
				if (practiceAreaChanged) {
					if (tenantId == null || tenantId <= 0)
						throw new RuntimeException("No tenant is selected.");
					caseDao.setPracticeArea(saveCaseId, tenantId, desiredPracticeAreaId);
				}

				Integer baseAttyId = currentOverview == null ? null : currentOverview.getResponsibleAttorneyUserId();
				boolean attyChanged = desiredResponsibleAttorneyUserId != null
						&& !desiredResponsibleAttorneyUserId.equals(baseAttyId);
				if (attyChanged) {
					caseDao.setResponsibleAttorney(saveCaseId, desiredResponsibleAttorneyUserId);
				}

				Integer baseOpposingCounselContactId = currentOverview == null ? null : currentOverview.getPrimaryOpposingCounselContactId();
				boolean opposingCounselChanged = desiredOpposingCounselContactId != null
						&& !desiredOpposingCounselContactId.equals(baseOpposingCounselContactId);
				if (opposingCounselChanged) {
					if (tenantId == null || tenantId <= 0)
						throw new RuntimeException("No tenant is selected.");
					caseDao.setPrimaryCaseContact(
							saveCaseId, tenantId, ROLE_CASECONTACT_OPPOSING_COUNSEL, desiredOpposingCounselContactId, userId, null
					);
				}

				runOnFx(() ->
				{
					current = updated;

					setEditMode(false);
					draft = null;

					hideRemoteUpdateBanner();

					applyDetail(updated);
					clearError();
					setBusy(false);

					// publish diffs (optional)
					String newName = safeText(saveDraft.caseName()).trim();
					String newDesc = safeText(saveDraft.description());

					if (!newName.equals(oldName))
						publishCaseFieldUpdated(saveCaseId, "name", newName);
					if (!newDesc.equals(oldDescription))
						publishCaseFieldUpdated(saveCaseId, "description", newDesc);

					if (statusChanged)
						publishCaseFieldUpdated(saveCaseId, "primaryStatusId", desiredStatusId);
					if (callerChanged)
						publishCaseFieldUpdated(saveCaseId, "primaryCallerContactId", desiredCallerContactId);
					if (clientChanged)
						publishCaseFieldUpdated(saveCaseId, "primaryClientContactId", desiredClientContactId);
					if (practiceAreaChanged)
						publishCaseFieldUpdated(saveCaseId, "practiceAreaId", desiredPracticeAreaId);
					if (attyChanged)
						publishCaseFieldUpdated(saveCaseId, "responsibleAttorneyUserId", desiredResponsibleAttorneyUserId);
					if (opposingCounselChanged)
						publishCaseFieldUpdated(saveCaseId, "primaryOpposingCounselContactId", desiredOpposingCounselContactId);

					// clear drafts
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

					// refresh overview to pick up updated names/colors/ids
					reloadCurrentCaseForViewMode();
				});

			} catch (Exception ex) {
				runOnFx(() ->
				{
					showError("Failed to save case. " + ex.getMessage());
					setBusy(false);
				});
			}
		}, "case-save-" + caseId).start();
	}

	private void publishCaseFieldUpdated(long caseId, String field, Object newValueOrNull) {
		if (runtimeBridge == null || appState == null || appState.getShaleClientId() == null || appState.getUserId() == null)
			return;

		try {
			int clientId = appState.getShaleClientId();
			int userId = appState.getUserId();

			runtimeBridge.publishEntityFieldUpdated("Case", (int) caseId, clientId, userId, field, newValueOrNull);
		} catch (Exception ex) {
			System.out.println("CaseUpdated publish skipped: " + ex.getMessage());
		}
	}

	private void onReloadRemote() {
		draft = null;
		setEditMode(false);
		hideRemoteUpdateBanner();
		clearError();
		reloadCurrentCaseForViewMode();
	}

	private void setEditMode(boolean enabled) {
		this.editMode = enabled;

		setVisibleManaged(ovCaseNameValue, !enabled);
		setVisibleManaged(ovCaseNameEditor, enabled);

		setVisibleManaged(ovDescriptionValue, !enabled);
		setVisibleManaged(ovDescriptionEditor, enabled);

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
			if (ovDescriptionEditor != null)
				ovDescriptionEditor.setDisable(busy);
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
		});
	}

	// ----------------------------
	// Status / Caller / Client / PracticeArea pickers
	// ----------------------------

	private void onChangeStatus() {
		if (caseDao == null || appState == null || caseId == null) {
			showError("Status change is unavailable.");
			return;
		}
		Integer clientId = appState.getShaleClientId();
		if (clientId == null || clientId <= 0) {
			showError("No tenant is selected.");
			return;
		}

		setBusy(true);
		clearError();

		new Thread(() ->
		{
			try {
				List<CaseDao.StatusRow> statuses = caseDao.listStatusesForTenant(clientId);

				runOnFx(() ->
				{
					setBusy(false);

					if (statuses == null || statuses.isEmpty()) {
						showError("No statuses are configured for this tenant.");
						return;
					}

					java.util.Map<String, CaseDao.StatusRow> labelToRow = new java.util.LinkedHashMap<>();
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

					ChoiceDialog<String> dialog = new ChoiceDialog<>(preselect, labelToRow.keySet());
					dialog.setTitle("Change Status");
					dialog.setHeaderText("Select the new primary status");
					dialog.setContentText("Status:");

					Optional<String> chosen = dialog.showAndWait();
					if (chosen.isEmpty())
						return;

					CaseDao.StatusRow picked = labelToRow.get(chosen.get());
					if (picked == null)
						return;

					draftPrimaryStatusId = picked.id();
					renderPrimaryStatusMini(picked.id(), picked.name(), picked.color());
				});

			} catch (Exception ex) {
				runOnFx(() ->
				{
					showError("Failed to load statuses. " + ex.getMessage());
					setBusy(false);
				});
			}
		}, "case-status-list-" + caseId).start();
	}

	private void onChangePracticeArea() {
		if (caseDao == null || appState == null || caseId == null) {
			showError("Practice area change is unavailable.");
			return;
		}
		Integer tenantId = appState.getShaleClientId();
		if (tenantId == null || tenantId <= 0) {
			showError("No tenant is selected.");
			return;
		}

		setBusy(true);
		clearError();

		new Thread(() ->
		{
			try {
				List<CaseDao.PracticeAreaRow> areas = caseDao.listPracticeAreasForTenant(tenantId);

				runOnFx(() ->
				{
					setBusy(false);

					if (areas == null || areas.isEmpty()) {
						showError("No practice areas are configured for this tenant.");
						return;
					}

					java.util.Map<String, CaseDao.PracticeAreaRow> labelToRow = new java.util.LinkedHashMap<>();
					String preselect = null;

					Integer currentId = (editMode && draftPracticeAreaId != null)
							? draftPracticeAreaId
							: (currentOverview == null ? null : currentOverview.getPracticeAreaId());

					for (CaseDao.PracticeAreaRow pa : areas) {
						String label = (pa.name() == null || pa.name().isBlank())
								? ("PracticeArea #" + pa.id())
								: pa.name();
						labelToRow.put(label, pa);
						if (currentId != null && currentId == pa.id())
							preselect = label;
					}

					if (preselect == null)
						preselect = labelToRow.keySet().iterator().next();

					ChoiceDialog<String> dialog = new ChoiceDialog<>(preselect, labelToRow.keySet());
					dialog.setTitle("Change Practice Area");
					dialog.setHeaderText("Select the practice area");
					dialog.setContentText("Practice area:");

					Optional<String> chosen = dialog.showAndWait();
					if (chosen.isEmpty())
						return;

					CaseDao.PracticeAreaRow picked = labelToRow.get(chosen.get());
					if (picked == null)
						return;

					draftPracticeAreaId = picked.id();
					draftPracticeAreaName = picked.name();
					draftPracticeAreaColor = picked.color();

					renderPracticeAreaMini(picked.id(), picked.name(), picked.color());
				});

			} catch (Exception ex) {
				runOnFx(() ->
				{
					showError("Failed to load practice areas. " + ex.getMessage());
					setBusy(false);
				});
			}
		}, "case-practicearea-list-" + caseId).start();
	}

	private void onChangeCaller() {
		if (caseDao == null || appState == null || caseId == null) {
			showError("Caller change is unavailable.");
			return;
		}
		Integer clientId = appState.getShaleClientId();
		if (clientId == null || clientId <= 0) {
			showError("No tenant is selected.");
			return;
		}

		setBusy(true);
		clearError();

		new Thread(() ->
		{
			try {
				List<CaseDao.ContactRow> contacts = caseDao.listContactsForTenant(clientId);

				runOnFx(() ->
				{
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

					Integer currentId = (editMode && draftPrimaryCallerContactId != null)
							? draftPrimaryCallerContactId
							: (currentOverview == null ? null : currentOverview.getPrimaryCallerContactId());

					CaseDao.ContactRow preselectRow = null;
					if (currentId != null) {
						for (CaseDao.ContactRow c : cleaned) {
							if (c.id() == currentId.intValue()) {
								preselectRow = c;
								break;
							}
						}
					}

					Optional<CaseDao.ContactRow> chosen = showSearchPickerDialog(
							"Change Caller", "Select the primary caller", "Search...", cleaned, preselectRow);

					if (chosen.isEmpty())
						return;

					CaseDao.ContactRow picked = chosen.get();
					draftPrimaryCallerContactId = picked.id();
					draftPrimaryCallerName = picked.displayName();

					// update UI immediately
					renderCallerMini(draftPrimaryCallerContactId, draftPrimaryCallerName);
				});

			} catch (Exception ex) {
				runOnFx(() ->
				{
					showError("Failed to load contacts. " + ex.getMessage());
					setBusy(false);
				});
			}
		}, "case-caller-list-" + caseId).start();
	}

	private void onChangeClient() {
		if (caseDao == null || appState == null || caseId == null) {
			showError("Client change is unavailable.");
			return;
		}
		Integer tenantId = appState.getShaleClientId();
		if (tenantId == null || tenantId <= 0) {
			showError("No tenant is selected.");
			return;
		}

		setBusy(true);
		clearError();

		new Thread(() ->
		{
			try {
				List<CaseDao.ContactRow> contacts = caseDao.listContactsForTenant(tenantId);

				runOnFx(() ->
				{
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

					Integer currentId = (editMode && draftPrimaryClientContactId != null)
							? draftPrimaryClientContactId
							: (currentOverview == null ? null : currentOverview.getPrimaryClientContactId());

					CaseDao.ContactRow preselectRow = null;
					if (currentId != null) {
						for (CaseDao.ContactRow c : cleaned) {
							if (c.id() == currentId.intValue()) {
								preselectRow = c;
								break;
							}
						}
					}

					Optional<CaseDao.ContactRow> chosen = showSearchPickerDialog(
							"Change Client", "Select the primary client", "Search...", cleaned, preselectRow);

					if (chosen.isEmpty())
						return;

					CaseDao.ContactRow picked = chosen.get();
					draftPrimaryClientContactId = picked.id();
					draftPrimaryClientName = picked.displayName();

					// update UI immediately
					renderClientMini(draftPrimaryClientContactId, draftPrimaryClientName);
				});

			} catch (Exception ex) {
				runOnFx(() ->
				{
					showError("Failed to load contacts. " + ex.getMessage());
					setBusy(false);
				});
			}
		}, "case-client-list-" + caseId).start();
	}

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

	// ----------------------------
	// Sections / placeholders
	// ----------------------------

	private void setupSections() {
		if (sectionListView == null)
			return;

		sectionListView.getItems().setAll("Overview", "Tasks", "Timeline", "Details", "People", "Organizations", "Documents");
		sectionListView.getSelectionModel().select("Overview");

		sectionListView.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) ->
		{
			if (newV == null)
				return;
			switch (newV) {
			case "Overview" -> showOverview();
			case "Tasks" -> showTasksTab();
			default -> showGeneric(newV);
			}
		});

		showOverview();
	}

	private void showOverview() {
		setPaneVisible(overviewPane, true);
		setPaneVisible(tasksTabPane, false);
		setPaneVisible(genericPane, false);
		setPaneVisible(tasksPanel, true);
		if (contentTitleLabel != null)
			contentTitleLabel.setText("Overview");
		loadOverviewOnce();
	}

	private void showTasksTab() {
		setPaneVisible(overviewPane, false);
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

	private void showGeneric(String sectionName) {
		setPaneVisible(overviewPane, false);
		setPaneVisible(tasksTabPane, false);
		setPaneVisible(genericPane, true);
		setPaneVisible(tasksPanel, false);

		if (genericTitleLabel != null)
			genericTitleLabel.setText(sectionName);

		if (placeholderTextArea != null && (placeholderTextArea.getText() == null || placeholderTextArea.getText().isBlank())) {
			placeholderTextArea.setText(sectionName + " view is not implemented yet.");
		}
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
		if (ovSolDateValue != null)
			ovSolDateValue.setText(formatDate(null));

		if (ovDescriptionValue != null)
			ovDescriptionValue.setText("");
	}

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

	private record CaseEditModel(String caseName, String description) {
	}

	// ----------------------------
	// Patch parsing helpers
	// ----------------------------

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

	// ----------------------------
	// Search dialog (unchanged)
	// ----------------------------

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

	private void onChangeResponsibleAttorney() {
		if (caseDao == null || appState == null || caseId == null) {
			showError("Responsible attorney change is unavailable.");
			return;
		}

		Integer clientId = appState.getShaleClientId();
		if (clientId == null || clientId <= 0) {
			showError("No tenant is selected.");
			return;
		}

		setBusy(true);
		clearError();

		final long saveCaseId = caseId.longValue();

		new Thread(() ->
		{
			try {
				List<CaseDao.UserRow> users = caseDao.listAttorneysForTenant(clientId);

				runOnFx(() ->
				{
					setBusy(false);

					if (users == null || users.isEmpty()) {
						showError("No attorneys are configured for this tenant.");
						return;
					}

					var labelToRow = new java.util.LinkedHashMap<String, CaseDao.UserRow>();

					Integer currentId = (editMode && draftResponsibleAttorneyUserId != null)
							? draftResponsibleAttorneyUserId
							: (currentOverview == null ? null : currentOverview.getResponsibleAttorneyUserId());

					String preselect = null;

					for (var u : users) {
						String label = u.displayName();
						if (label == null || label.isBlank())
							continue;

						// avoid collisions if two users share same display name
						String key = label;
						if (labelToRow.containsKey(key)) {
							key = label + " (ID " + u.id() + ")";
						}

						labelToRow.put(key, u);

						if (currentId != null && currentId.equals(u.id())) {
							preselect = key;
						}
					}

					if (labelToRow.isEmpty()) {
						showError("No attorneys are configured for this tenant.");
						return;
					}

					if (preselect == null) {
						preselect = labelToRow.keySet().iterator().next();
					}

					ChoiceDialog<String> dialog = new ChoiceDialog<>(preselect, labelToRow.keySet());
					dialog.setTitle("Change Responsible Attorney");
					dialog.setHeaderText("Select the responsible attorney");
					dialog.setContentText("Attorney:");

					Optional<String> chosen = dialog.showAndWait();
					if (chosen.isEmpty())
						return;

					CaseDao.UserRow picked = labelToRow.get(chosen.get());
					if (picked == null)
						return;

					draftResponsibleAttorneyUserId = picked.id();

					// Update both header + overview card
					renderResponsibleAttorneyMini(
							picked.id(),
							picked.displayName(),
							picked.color()
					);
				});

			} catch (Exception ex) {
				runOnFx(() ->
				{
					showError("Failed to load attorneys. " + ex.getMessage());
					setBusy(false);
				});
			}
		}, "case-atty-list-" + saveCaseId).start();
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

	private void onChangeOpposingCounsel() {
		if (caseDao == null || appState == null || caseId == null) {
			showError("Opposing counsel change is unavailable.");
			return;
		}
		Integer tenantId = appState.getShaleClientId();
		if (tenantId == null || tenantId <= 0) {
			showError("No tenant is selected.");
			return;
		}

		setBusy(true);
		clearError();

		new Thread(() ->
		{
			try {
				List<CaseDao.ContactRow> contacts = caseDao.listContactsForTenant(tenantId);

				runOnFx(() ->
				{
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

					Integer currentId = (editMode && draftPrimaryOpposingCounselContactId != null)
							? draftPrimaryOpposingCounselContactId
							: (currentOverview == null ? null : currentOverview.getPrimaryOpposingCounselContactId());

					CaseDao.ContactRow preselectRow = null;
					if (currentId != null) {
						for (CaseDao.ContactRow c : cleaned) {
							if (c.id() == currentId.intValue()) {
								preselectRow = c;
								break;
							}
						}
					}

					Optional<CaseDao.ContactRow> chosen = showSearchPickerDialog(
							"Change Opposing Counsel",
							"Select the primary opposing counsel",
							"Search...",
							cleaned,
							preselectRow
					);

					if (chosen.isEmpty())
						return;

					CaseDao.ContactRow picked = chosen.get();
					draftPrimaryOpposingCounselContactId = picked.id();
					draftPrimaryOpposingCounselName = picked.displayName();

					renderOpposingCounselMini(draftPrimaryOpposingCounselContactId, draftPrimaryOpposingCounselName);
				});

			} catch (Exception ex) {
				runOnFx(() ->
				{
					showError("Failed to load contacts. " + ex.getMessage());
					setBusy(false);
				});
			}
		}, "case-oppcounsel-list-" + caseId).start();
	}
}