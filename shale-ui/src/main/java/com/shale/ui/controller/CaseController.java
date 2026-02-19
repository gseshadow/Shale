package com.shale.ui.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
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
	}

	private void subscribeLiveCaseUpdates() {
		if (runtimeBridge == null) {
			return;
		}
		runtimeBridge.subscribeCaseUpdated(event ->
		{
			if (caseId == null || event.caseId() != caseId.intValue()) {
				return;
			}
			Integer currentUserId = appState == null ? null : appState.getUserId();
			if (currentUserId != null && currentUserId.intValue() == event.updatedByUserId()) {
				return;
			}
			if (editMode) {
				runOnFx(() -> {
					remoteDirty = true;
					showRemoteUpdateBanner();
				});
				return;
			}
			reloadCurrentCaseForViewMode();
		});
	}

	private void reloadCurrentCaseForViewMode() {
		if (caseDao == null || caseId == null) {
			return;
		}
		final long activeCaseId = caseId.longValue();
		new Thread(() ->
		{
			CaseOverviewDto overview = caseDao.getOverview(activeCaseId);
			CaseDetailDto detail = caseDao.getDetail(activeCaseId);
			runOnFx(() ->
			{
				if (editMode) {
					return;
				}
				if (overview != null) {
					applyOverview(overview);
				}
				if (detail != null) {
					current = detail;
					applyDetail(detail);
				}
				remoteDirty = false;
				hideRemoteUpdateBanner();
				clearError();
				repaintViewLabels();
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
			tasksTabListView.getItems().setAll("Call client (placeholder)", "Request records (placeholder)", "Review radiology (placeholder)", "Draft demand (placeholder)", "Schedule depo (placeholder)");
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
			taskListView.getItems().setAll("Past due: Call client (placeholder)", "Upcoming: Request records (placeholder)", "Upcoming: Review radiology (placeholder)", "Upcoming: Send HIPAA auth (placeholder)", "Upcoming: Draft demand outline (placeholder)");
		}
		if (taskSearchField != null) {
			taskSearchField.setOnAction(e -> {
			});
		}
		if (newTaskInlineButton != null) {
			newTaskInlineButton.setOnAction(e -> {
			});
		}
	}

	private void refreshHeader() {
		if (caseTitleLabel == null || caseId == null)
			return;
		caseTitleLabel.setText("Case #" + caseId + " (Placeholder)");
		if (statusLabel != null)
			statusLabel.setText("Status: —");
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
		if (statusLabel != null)
			statusLabel.setText("Status: " + safe(dto.getCaseStatus()));
		renderResponsibleAttorneyMini(dto.getResponsibleAttorneyUserId(), safe(dto.getResponsibleAttorney()), dto.getResponsibleAttorneyColor());
		if (ovCaseNameValue != null)
			ovCaseNameValue.setText(safe(dto.getCaseName()));
		if (ovCaseNumberValue != null)
			ovCaseNumberValue.setText(safe(dto.getCaseNumber()));
		if (ovCaseStatusValue != null)
			ovCaseStatusValue.setText(safe(dto.getCaseStatus()));
		if (ovCallerValue != null)
			ovCallerValue.setText(safe(dto.getCaller()));
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
		setEditMode(false);
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
				CaseDetailDto updated = caseDao.updateCase(saveCaseId, saveDraft.caseName(), saveDraft.description(), expectedRowVer);
				runOnFx(() ->
				{
					if (updated == null) {
						remoteDirty = true;
						showRemoteUpdateBanner();
						showError("This case was updated elsewhere. Reload and try again.");
						setBusy(false);
						return;
					}
					current = updated;
					setEditMode(false);
					draft = null;
					remoteDirty = false;
					hideRemoteUpdateBanner();
					applyDetail(updated);
					clearError();
					setBusy(false);
					publishCaseUpdated(saveCaseId);
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

	private void publishCaseUpdated(long updatedCaseId) {
		if (runtimeBridge == null || appState == null || appState.getShaleClientId() == null || appState.getUserId() == null) {
			return;
		}
		try {
			System.out.println("[LIVE] publish CaseUpdated caseId=" + updatedCaseId + ", clientId=" + appState.getShaleClientId() + ", updatedBy=" + appState.getUserId());
			runtimeBridge.publishCaseUpdated((int) updatedCaseId, appState.getShaleClientId(), appState.getUserId());
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
}
