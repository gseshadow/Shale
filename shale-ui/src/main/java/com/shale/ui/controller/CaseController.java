package com.shale.ui.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import com.shale.core.dto.CaseDetailDto;
import com.shale.core.dto.CaseOverviewDto;
import com.shale.data.dao.CaseDao;
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
import com.shale.ui.component.factory.StatusCardFactory;
import com.shale.ui.component.factory.StatusCardFactory.StatusCardModel;

public class CaseController {

	private static final int ROLE_CASECONTACT_CALLER = 2;

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
	private Label ovCaseNameValue;
	@FXML
	private TextField ovCaseNameEditor;
	@FXML
	private Label ovCaseNumberValue;
	@FXML
	private StackPane ovResponsibleAttorneyHost;
	@FXML
	private Label ovCaseStatusValue;
	@FXML
	private Label ovCallerValue;
	@FXML
	private Button changeCallerButton;
	@FXML
	private Label ovClientValue;
	@FXML
	private Label ovPracticeAreaValue;
	@FXML
	private Label ovOpposingCounselValue;
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

	private StatusCardFactory statusCardFactory;
	private Consumer<Integer> onOpenStatus;
	private CaseDao caseDao;
	private boolean overviewLoaded = false;
	private Consumer<Integer> onOpenUser;
	private UserCardFactory userCardFactory;
	private Integer caseId;
	private boolean editMode = false;
	private boolean remoteDirty = false;
	private CaseDetailDto current;
	private CaseEditModel draft;
	private AppState appState;
	private UiRuntimeBridge runtimeBridge;
	private CaseOverviewDto currentOverview;
	private Integer draftPrimaryStatusId; // only used in edit mode
	private Integer draftPrimaryCallerContactId; // only used in edit mode
	private String draftPrimaryCallerName; // only used in edit mode

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
		if (changeStatusButton != null) {
			changeStatusButton.setOnAction(e -> onChangeStatus());
		}
		if (changeCallerButton != null) {
			changeCallerButton.setOnAction(e -> onChangeCaller());
		}
	}

	private void subscribeLiveCaseUpdates() {
		if (runtimeBridge == null) {
			return;
		}

		runtimeBridge.subscribeCaseUpdated(event ->
		{
			if (caseId == null || event == null || event.caseId() != caseId.intValue()) {
				return;
			}

			// Ignore only my own echo (same machine instance)
			String mine = runtimeBridge.getClientInstanceId();
			if (mine != null && !mine.isBlank() && mine.equals(event.clientInstanceId())) {
				return;
			}

			// Legacy/newName-only event
			if (event.newName() != null) {
				runOnFx(() ->
				{
					applyLiveCaseName(event.newName());
					remoteDirty = false;
					hideRemoteUpdateBanner();

					// Refresh rowVer/current so next Save doesn't conflict
					refreshCurrentAfterRemoteUpdateAsync();
				});
				return;
			}

			// Patch-based event
			String rawPatch = event.rawPatchJson();
			String patchedName = extractPatchString(rawPatch, "name");
			String patchedDescription = extractPatchString(rawPatch, "description");
			Integer patchedPrimaryStatusId = extractPatchInt(rawPatch, "primaryStatusId");
			Integer patchedPrimaryCallerContactId = extractPatchInt(rawPatch, "primaryCallerContactId");

			System.out.println("[DEBUG LIVE] CASE listenerUserId=" + (appState == null ? null : appState.getUserId())
					+ " event.updatedByUserId=" + event.updatedByUserId()
					+ " caseId=" + event.caseId()
					+ " hasPatch=" + (rawPatch != null && !rawPatch.isBlank())
					+ " hasName=" + (patchedName != null)
					+ " hasDesc=" + (patchedDescription != null)
					+ " hasPrimaryStatusId=" + (patchedPrimaryStatusId != null)
					+ " hasPrimaryCallerContactId=" + (patchedPrimaryCallerContactId != null));

			// If we're editing, don't clobber local edits; show the banner.
			if (editMode) {
				runOnFx(() ->
				{
					remoteDirty = true;
					showRemoteUpdateBanner();
				});
				return;
			}

			// Status/caller change: easiest + most reliable is to reload overview/detail
			if (patchedPrimaryStatusId != null || patchedPrimaryCallerContactId != null) {
				runOnFx(() ->
				{
					reloadCurrentCaseForViewMode();
					remoteDirty = false;
					hideRemoteUpdateBanner();
				});
				return;
			}

			// Name/description patch: apply immediately, then refresh rowVer/current
			if (patchedName != null || patchedDescription != null) {
				runOnFx(() ->
				{
					if (patchedName != null) {
						applyLiveCaseName(patchedName);
					}
					if (patchedDescription != null) {
						applyLiveCaseDescription(patchedDescription);
					}

					remoteDirty = false;
					hideRemoteUpdateBanner();

					// Update current + rowVer from DB after remote change
					refreshCurrentAfterRemoteUpdateAsync();
				});
				return;
			}

			// Otherwise show banner (unknown patch keys or no patch)
			runOnFx(() ->
			{
				remoteDirty = true;
				showRemoteUpdateBanner();
			});
		});
	}

	private void refreshCurrentAfterRemoteUpdateAsync() {
		if (caseDao == null || caseId == null) {
			return;
		}
		final long id = caseId.longValue();

		new Thread(() ->
		{
			try {
				CaseDetailDto fresh = caseDao.getDetail(id);
				if (fresh == null) {
					return;
				}
				runOnFx(() ->
				{
					// If user started editing while this was loading, don't overwrite draft.
					if (editMode) {
						remoteDirty = true;
						showRemoteUpdateBanner();
						return;
					}

					// Update backing model so rowVer is current (prevents "updated elsewhere" on next save)
					current = fresh;

					// Optional: keep header "Last updated" fresh if you want:
					// applyDetail(fresh); // only if it won't visually jump in a bad way
				});
			} catch (Exception ignored) {
			}
		}, "case-refresh-current-" + id).start();
	}

	private void applyLiveCaseName(String newName) {
		String safeName = safeText(newName).trim();
		if (!safeName.isBlank()) {
			if (ovCaseNameValue != null) {
				ovCaseNameValue.setText(safeName);
			}
			if (current != null) {
				String num = safeText(current.getCaseNumber());
				if (caseTitleLabel != null) {
					caseTitleLabel.setText(num.isBlank() ? safeName : safeName + " — " + num);
				}
			}
		}
	}

	private void applyLiveCaseDescription(String newDescription) {
		String safeDesc = safeText(newDescription);
		if (ovDescriptionValue != null) {
			ovDescriptionValue.setText(safeDesc);
		}
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
				if (overview != null) {
					applyOverviewEditSafe(overview);
				}

				// Update backing model always; only paint detail when not editing
				if (detail != null) {
					current = detail;
					if (!editMode) {
						applyDetail(detail);
					}
				}

				remoteDirty = false;
				hideRemoteUpdateBanner();
				clearError(); // optional; note it also hides the banner in your current implementation
			});
		}, "case-view-sync-" + activeCaseId).start();
	}

	private void repaintViewLabels() {
		if (editMode || current == null) {
			return;
		}
		applyDetail(current);
	}

	private void showRemoteUpdateBanner() {
		setVisibleManaged(remoteUpdateBanner, true);
		setVisibleManaged(reloadRemoteButton, true);
	}

	private void hideRemoteUpdateBanner() {
		setVisibleManaged(remoteUpdateBanner, false);
		setVisibleManaged(reloadRemoteButton, false);
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

	private void onReloadRemote() {
		draft = null;
		remoteDirty = false;
		setEditMode(false);
		hideRemoteUpdateBanner();
		clearError();
		reloadCurrentCaseForViewMode();
	}

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
			tasksTabListView.getItems().setAll("Call client (placeholder)", "Request records (placeholder)", "Review radiology (placeholder)",
					"Draft demand (placeholder)", "Schedule depo (placeholder)");
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
			taskListView.getItems().setAll("Past due: Call client (placeholder)", "Upcoming: Request records (placeholder)",
					"Upcoming: Review radiology (placeholder)", "Upcoming: Send HIPAA auth (placeholder)", "Upcoming: Draft demand outline (placeholder)");
		}
		if (taskSearchField != null) {
			taskSearchField.setOnAction(e ->
			{
			});
		}
		if (newTaskInlineButton != null) {
			newTaskInlineButton.setOnAction(e ->
			{
			});
		}
	}

	private void refreshHeader() {
		if (caseTitleLabel == null || caseId == null)
			return;
		caseTitleLabel.setText("Case #" + caseId + " (Placeholder)");
		renderPrimaryStatusMini(null, "—", null);
		renderResponsibleAttorneyMini(null, "—", null);
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
		if (ovCallerValue != null)
			ovCallerValue.setText("—");
		if (ovClientValue != null)
			ovClientValue.setText("—");
		if (ovPracticeAreaValue != null)
			ovPracticeAreaValue.setText("—");
		if (ovOpposingCounselValue != null)
			ovOpposingCounselValue.setText("—");
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

	private void loadOverviewOnce() {
		if (overviewLoaded || caseDao == null || caseId == null)
			return;
		overviewLoaded = true;
		reloadCurrentCaseForViewMode();
	}

	private void applyOverview(CaseOverviewDto dto) {
		currentOverview = dto;
		if (caseTitleLabel != null) {
			String name = dto.getCaseName();
			String num = dto.getCaseNumber();
			String title;

			if (name != null && !name.isBlank() && num != null && !num.isBlank()) {
				title = name + " — " + num;
			} else if (name != null && !name.isBlank()) {
				title = name;
			} else if (num != null && !num.isBlank()) {
				title = num;
			} else {
				title = "Case #" + dto.getCaseId();
			}
			caseTitleLabel.setText(title);
		}
		renderResponsibleAttorneyMini(dto.getResponsibleAttorneyUserId(), safe(dto.getResponsibleAttorney()), dto.getResponsibleAttorneyColor());
		if (ovCaseNameValue != null)
			ovCaseNameValue.setText(safe(dto.getCaseName()));
		if (ovCaseNumberValue != null)
			ovCaseNumberValue.setText(safe(dto.getCaseNumber()));
		renderPrimaryStatusMini(
				dto.getPrimaryStatusId(), // you’ll add this to the DTO
				dto.getCaseStatus(), // name you already have
				dto.getPrimaryStatusColor() // you’ll add this to the DTO
		);
		if (ovCallerValue != null) {
			String callerName = (editMode && draftPrimaryCallerName != null && !draftPrimaryCallerName.isBlank())
					? draftPrimaryCallerName
					: dto.getCaller();
			ovCallerValue.setText(safe(callerName));
		}
		if (ovClientValue != null)
			ovClientValue.setText(safe(dto.getClient()));
		if (ovPracticeAreaValue != null)
			ovPracticeAreaValue.setText(safe(dto.getPracticeArea()));
		if (ovOpposingCounselValue != null)
			ovOpposingCounselValue.setText(safe(dto.getOpposingCounsel()));
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
			String num = safeText(detail.getCaseNumber());
			String name = safeText(detail.getCaseName());
			if (!name.isBlank() && !num.isBlank()) {
				caseTitleLabel.setText(name + " — " + num);
			} else if (!name.isBlank()) {
				caseTitleLabel.setText(name);
			} else if (!num.isBlank()) {
				caseTitleLabel.setText(num);
			}
		}
	}

	private String formatDateTime(LocalDateTime value) {
		return value == null ? "—" : value.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
	}

	private void onEdit() {
		draftPrimaryStatusId = (currentOverview == null ? null : currentOverview.getPrimaryStatusId());
		draftPrimaryCallerContactId = (currentOverview == null ? null : currentOverview.getPrimaryCallerContactId());
		draftPrimaryCallerName = (currentOverview == null ? null : currentOverview.getCaller());
		if (current == null) {
			showError("Case is still loading. Please try again.");
			return;
		}
		draft = new CaseEditModel(current.getCaseName(), current.getDescription());
		if (ovCaseNameEditor != null)
			ovCaseNameEditor.setText(draft.caseName());
		if (ovDescriptionEditor != null)
			ovDescriptionEditor.setText(draft.description());
		remoteDirty = false;
		hideRemoteUpdateBanner();
		clearError();
		setEditMode(true);
	}

	private void onCancel() {
		draft = null;
		remoteDirty = false;
		hideRemoteUpdateBanner();
		clearError();
		draftPrimaryStatusId = null;
		draftPrimaryCallerContactId = null;
		draftPrimaryCallerName = null;
		setEditMode(false);
		if (currentOverview != null)
			applyOverviewEditSafe(currentOverview);
		applyDetail(current);
	}

	private void onSave() {
		if (caseDao == null) {
			showError("Case service is unavailable.");
			return;
		}
		if (current == null) {
			showError("Case is still loading. Please try again.");
			return;
		}
		if (ovCaseNameEditor == null || ovDescriptionEditor == null) {
			return;
		}

		// Capture "before" values so we can publish per-field changes after save
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

		setBusy(true);
		clearError();

		new Thread(() ->
		{
			try {
				// 1) update name/description
				CaseDetailDto updated = caseDao.updateCase(saveCaseId, saveDraft.caseName(), saveDraft.description(), expectedRowVer);
				if (updated == null) {
					runOnFx(() ->
					{
						remoteDirty = true;
						showRemoteUpdateBanner();
						showError("This case was updated elsewhere. Reload and try again.");
						setBusy(false);
					});
					return;
				}

				// 2) status change (only if changed)
				Integer baseStatusId = currentOverview == null ? null : currentOverview.getPrimaryStatusId();
				Integer desiredStatusId = draftPrimaryStatusId;
				boolean statusChanged = desiredStatusId != null && !desiredStatusId.equals(baseStatusId);

				if (statusChanged) {
					caseDao.setPrimaryStatus(saveCaseId, desiredStatusId, null);
				}

				Integer baseCallerContactId = currentOverview == null ? null : currentOverview.getPrimaryCallerContactId();
				Integer desiredCallerContactId = draftPrimaryCallerContactId;
				boolean callerChanged = desiredCallerContactId != null && !desiredCallerContactId.equals(baseCallerContactId);

				if (callerChanged) {
					caseDao.setPrimaryCaseContact(
						saveCaseId,
						appState.getShaleClientId(),
						ROLE_CASECONTACT_CALLER,
						desiredCallerContactId,
						appState.getUserId(),
						null);
				}

				// 3) now update UI + publish on FX thread
				runOnFx(() ->
				{
					current = updated;
					setEditMode(false);
					draft = null;
					remoteDirty = false;
					hideRemoteUpdateBanner();
					applyDetail(updated);
					clearError();
					setBusy(false);

					// publish name/desc changes
					String newName = safeText(saveDraft.caseName()).trim();
					String newDesc = safeText(saveDraft.description());

					if (!newName.equals(oldName)) {
						publishCaseFieldUpdated(saveCaseId, "name", newName);
					}
					if (!newDesc.equals(oldDescription)) {
						publishCaseFieldUpdated(saveCaseId, "description", newDesc);
					}

					if (statusChanged) {
						publishCaseFieldUpdated(saveCaseId, "primaryStatusId", desiredStatusId);
					}
					if (callerChanged) {
						publishCaseFieldUpdated(saveCaseId, "primaryCallerContactId", desiredCallerContactId);
					}

					// ✅ clear draft after success
					draftPrimaryStatusId = null;
					draftPrimaryCallerContactId = null;
					draftPrimaryCallerName = null;

					// optional: refresh overview so currentOverview reflects new status id/color
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
		if (runtimeBridge == null || appState == null
				|| appState.getShaleClientId() == null
				|| appState.getUserId() == null) {
			return;
		}

		try {
			int clientId = appState.getShaleClientId();
			int userId = appState.getUserId();

			System.out.println("[LIVE] publish Case field update caseId=" + caseId
					+ " clientId=" + clientId
					+ " updatedBy=" + userId
					+ " field=" + field
					+ " hasValue=" + (newValueOrNull != null));

			runtimeBridge.publishEntityFieldUpdated(
					"Case",
					(int) caseId,
					clientId,
					userId,
					field,
					newValueOrNull
			);
		} catch (Exception ex) {
			System.out.println("CaseUpdated publish skipped: " + ex.getMessage());
		}
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
		if (!enabled) {
			hideRemoteUpdateBanner();
		}
		setVisibleManaged(changeStatusButton, enabled);
		setVisibleManaged(changeCallerButton, enabled);
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
			if (changeCallerButton != null)
				changeCallerButton.setDisable(busy);
		});
	}

	private static void runOnFx(Runnable runnable) {
		if (Platform.isFxApplicationThread()) {
			runnable.run();
		} else {
			Platform.runLater(runnable);
		}
	}

	private void clearError() {
		if (errorLabel != null) {
			errorLabel.setText("");
			errorLabel.setVisible(false);
			errorLabel.setManaged(false);
		}
		hideRemoteUpdateBanner();
	}

	private void showError(String message) {
		if (errorLabel != null) {
			errorLabel.setText(message == null ? "" : message);
			errorLabel.setVisible(message != null && !message.isBlank());
			errorLabel.setManaged(message != null && !message.isBlank());
		}
	}

	private void renderResponsibleAttorneyMini(Integer userId, String displayName, String userColorCss) {
		if (userCardFactory == null) {
			userCardFactory = new UserCardFactory(onOpenUser == null ? id ->
			{
			} : onOpenUser);
		}

		UserCardModel model = new UserCardModel(userId,
				(displayName == null || displayName.isBlank()) ? "—" : displayName,
				userColorCss,
				null);

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

		if (statusHost != null) {
			statusHost.getChildren().setAll(headerCard);
		}
		if (ovCaseStatusHost != null) {
			ovCaseStatusHost.getChildren().setAll(overviewCard);
		}
	}

	private static void setPaneVisible(VBox pane, boolean visible) {
		if (pane == null)
			return;
		pane.setVisible(visible);
		pane.setManaged(visible);
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

	private static void setVisibleManaged(javafx.scene.Node node, boolean visible) {
		if (node == null)
			return;
		node.setVisible(visible);
		node.setManaged(visible);
	}

	private record CaseEditModel(String caseName, String description) {
	}

	/**
	 * Minimal patch reader for {"name":"...","description":"..."} style patch. Supports
	 * string values; returns null if missing or malformed.
	 */
	private static String extractPatchString(String rawPatchJson, String key) {
		if (rawPatchJson == null || rawPatchJson.isBlank() || key == null || key.isBlank()) {
			return null;
		}
		// crude but effective for your current patch format
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

		// NOTE: this does not fully unescape JSON sequences; good enough for now
		return rawPatchJson.substring(firstQuote + 1, secondQuote);
	}

	public void setOnOpenStatus(Consumer<Integer> onOpenStatus) {
		this.onOpenStatus = onOpenStatus;
		this.statusCardFactory = new StatusCardFactory(onOpenStatus);
	}

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

					// Build labels for display
					java.util.Map<String, CaseDao.StatusRow> labelToRow = new java.util.LinkedHashMap<>();
					String preselect = null;

					Integer currentId = (editMode && draftPrimaryStatusId != null)
							? draftPrimaryStatusId
							: (currentOverview == null ? null : currentOverview.getPrimaryStatusId());

					for (CaseDao.StatusRow s : statuses) {
						String label = s.name() + (s.isClosed() ? " (Closed)" : "");
						labelToRow.put(label, s);
						if (currentId != null && currentId == s.id()) {
							preselect = label;
						}
					}

					if (preselect == null) {
						preselect = labelToRow.keySet().iterator().next();
					}

					ChoiceDialog<String> dialog = new ChoiceDialog<>(preselect, labelToRow.keySet());
					dialog.setTitle("Change Status");
					dialog.setHeaderText("Select the new primary status");
					dialog.setContentText("Status:");

					Optional<String> chosen = dialog.showAndWait();
					if (chosen.isEmpty()) {
						return; // cancelled
					}

					CaseDao.StatusRow picked = labelToRow.get(chosen.get());
					if (picked == null) {
						return;
					}

					// ✅ Edit-only: store draft + update cards immediately
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

					LinkedHashMap<String, CaseDao.ContactRow> labelToRow = new LinkedHashMap<>();
					String preselect = null;
					Integer currentId = (editMode && draftPrimaryCallerContactId != null)
							? draftPrimaryCallerContactId
							: (currentOverview == null ? null : currentOverview.getPrimaryCallerContactId());

					for (CaseDao.ContactRow c : contacts) {
						String label = c.displayName() + " (#" + c.id() + ")";
						labelToRow.put(label, c);
						if (currentId != null && currentId == c.id()) {
							preselect = label;
						}
					}

					if (preselect == null) {
						preselect = labelToRow.keySet().iterator().next();
					}

					ChoiceDialog<String> dialog = new ChoiceDialog<>(preselect, labelToRow.keySet());
					dialog.setTitle("Change Caller");
					dialog.setHeaderText("Select the primary caller");
					dialog.setContentText("Caller:");

					Optional<String> chosen = dialog.showAndWait();
					if (chosen.isEmpty()) {
						return;
					}

					CaseDao.ContactRow picked = labelToRow.get(chosen.get());
					if (picked == null) {
						return;
					}

					draftPrimaryCallerContactId = picked.id();
					draftPrimaryCallerName = picked.displayName();
					if (ovCallerValue != null) {
						ovCallerValue.setText(safe(draftPrimaryCallerName));
					}
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

	private String getCurrentStatusName() {
		// Use whatever your UI currently has as the displayed status.
		// Best is: store the latest CaseOverviewDto in a field and return dto.getCaseStatus().
		// For now, try reading from current detail first:
		if (current != null && current.getCaseStatus() != null)
			return current.getCaseStatus();
		return null;
	}

	private void applyOverviewEditSafe(CaseOverviewDto dto) {
		currentOverview = dto;
		// Always refresh these “safe” UI elements while editing:
		renderResponsibleAttorneyMini(dto.getResponsibleAttorneyUserId(), safe(dto.getResponsibleAttorney()), dto.getResponsibleAttorneyColor());
		Integer statusId = (editMode && draftPrimaryStatusId != null)
				? draftPrimaryStatusId
				: dto.getPrimaryStatusId();

		String statusName = dto.getCaseStatus();
		String statusColor = dto.getPrimaryStatusColor();

		// If you rendered from the chosen status row above, you can keep name/color in draft too.
		// Otherwise, just reload on save.
		renderPrimaryStatusMini(statusId, statusName, statusColor);

		// Header title is fine to refresh too:
		if (caseTitleLabel != null) {
			String name = dto.getCaseName();
			String num = dto.getCaseNumber();
			String title;
			if (name != null && !name.isBlank() && num != null && !num.isBlank())
				title = name + " — " + num;
			else if (name != null && !name.isBlank())
				title = name;
			else if (num != null && !num.isBlank())
				title = num;
			else
				title = "Case #" + dto.getCaseId();
			caseTitleLabel.setText(title);
		}

		// Only refresh the overview text labels if not editing
		if (!editMode) {
			applyOverview(dto);
			return;
		}

		// While editing, refresh only labels that are not being edited.
		// (You can expand this list as needed.)
		if (ovCaseNumberValue != null)
			ovCaseNumberValue.setText(safe(dto.getCaseNumber()));
		if (ovCallerValue != null) {
			String callerName = (editMode && draftPrimaryCallerName != null && !draftPrimaryCallerName.isBlank())
					? draftPrimaryCallerName
					: dto.getCaller();
			ovCallerValue.setText(safe(callerName));
		}
		if (ovClientValue != null)
			ovClientValue.setText(safe(dto.getClient()));
		if (ovPracticeAreaValue != null)
			ovPracticeAreaValue.setText(safe(dto.getPracticeArea()));
		if (ovOpposingCounselValue != null)
			ovOpposingCounselValue.setText(safe(dto.getOpposingCounsel()));
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

		// DO NOT touch ovCaseNameValue / ovDescriptionValue in edit mode (those are being edited)
	}

	private static Integer extractPatchInt(String rawPatchJson, String key) {
		if (rawPatchJson == null || rawPatchJson.isBlank() || key == null || key.isBlank()) {
			return null;
		}

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
}