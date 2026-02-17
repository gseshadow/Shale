package com.shale.ui.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.List;

import com.shale.data.dao.CaseDao;

import com.shale.core.dto.CaseOverviewDto;
import javafx.application.Platform;

public class CaseController {

	// Header
	@FXML
	private Label caseTitleLabel;
	@FXML
	private Label statusLabel;
	@FXML
	private Label assignedLabel;
	@FXML
	private Label lastUpdatedLabel;

	@FXML
	private Button addEntryButton;
	@FXML
	private Button addTaskButton;
	@FXML
	private Button backToCasesButton;

	// Left sections
	@FXML
	private ListView<String> sectionListView;

	// Center host panes
	@FXML
	private VBox overviewPane;
	@FXML
	private VBox tasksTabPane;
	@FXML
	private VBox genericPane;

	@FXML
	private Label contentTitleLabel; // used by overview pane header
	@FXML
	private Label genericTitleLabel;
	@FXML
	private TextArea placeholderTextArea;

	// Overview fields
	@FXML
	private Label ovCaseNameValue;
	@FXML
	private Label ovCaseNumberValue;
	@FXML
	private Label ovResponsibleAttorneyValue;
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
	private TextArea ovDescriptionArea;

	// Right tasks panel (overview-only)
	@FXML
	private VBox tasksPanel;
	@FXML
	private TextField taskSearchField;
	@FXML
	private ListView<String> taskListView;
	@FXML
	private Button newTaskInlineButton;

	// Tasks tab (center)
	@FXML
	private TextField tasksTabSearchField;
	@FXML
	private ListView<String> tasksTabListView;

	private CaseDao caseDao;
	private boolean overviewLoaded = false;

	// Context
	private Integer caseId;

	public void init(Integer caseId) {
		this.caseId = caseId;
		// init() may be called before FXML injection, so stay null-safe
		refreshHeader();
		refreshOverviewPlaceholders();
	}

	public void init(Integer caseId, CaseDao caseDao) {
		this.caseId = caseId;
		this.caseDao = caseDao;
		refreshHeader();
		// don’t load yet if you want it tab-driven; we’ll trigger when Overview is selected
	}

	@FXML
	private void initialize() {
		// Now injected
		refreshHeader();
		refreshOverviewPlaceholders();
		setupSections();
		setupOverviewTasksPanel();
	}

	private void setupSections() {
		if (sectionListView == null)
			return;

		sectionListView.getItems().setAll(
				"Overview",
				"Tasks",
				"Timeline",
				"Details",
				"People",
				"Organizations",
				"Documents"
		);
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

		// Ensure initial state is correct
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

		// Hide right-side tasks when user is on the Tasks tab
		setPaneVisible(tasksPanel, false);

		// Placeholder content for now
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

		// Hide right-side tasks for all non-overview sections
		setPaneVisible(tasksPanel, false);

		if (genericTitleLabel != null)
			genericTitleLabel.setText(sectionName);

		if (placeholderTextArea != null && (placeholderTextArea.getText() == null || placeholderTextArea.getText().isBlank())) {
			placeholderTextArea.setText(sectionName + " view is not implemented yet.");
		}
	}

	private void setupOverviewTasksPanel() {
		// Right panel shows top 5 upcoming/past due (placeholder for now)
		if (taskListView != null) {
			taskListView.getItems().setAll(
					"Past due: Call client (placeholder)",
					"Upcoming: Request records (placeholder)",
					"Upcoming: Review radiology (placeholder)",
					"Upcoming: Send HIPAA auth (placeholder)",
					"Upcoming: Draft demand outline (placeholder)"
			);
		}

		// If you want search to filter the right panel later, wire it here (placeholder)
		if (taskSearchField != null) {
			taskSearchField.setOnAction(e ->
			{
				// Later: filter taskListView based on query
			});
		}

		if (newTaskInlineButton != null) {
			newTaskInlineButton.setOnAction(e ->
			{
				// Later: open create-task UI
			});
		}
	}

	private void refreshHeader() {
		if (caseTitleLabel == null || caseId == null)
			return;

		// Placeholder header until real case data is injected
		caseTitleLabel.setText("Case #" + caseId + " (Placeholder)");

		if (statusLabel != null)
			statusLabel.setText("Status: —");
		if (assignedLabel != null)
			assignedLabel.setText("Assigned: —");
		if (lastUpdatedLabel != null)
			lastUpdatedLabel.setText("Last updated: —");
	}

	private void refreshOverviewPlaceholders() {
		// Once you inject a Case DTO/VM, this becomes setOverview(caseVm)

		if (ovCaseNameValue != null)
			ovCaseNameValue.setText(caseId == null ? "—" : "Case #" + caseId + " (Placeholder name)");
		if (ovCaseNumberValue != null)
			ovCaseNumberValue.setText(caseId == null ? "—" : String.valueOf(caseId));
		if (ovResponsibleAttorneyValue != null)
			ovResponsibleAttorneyValue.setText("—");
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

		if (ovDescriptionArea != null && (ovDescriptionArea.getText() == null || ovDescriptionArea.getText().isBlank())) {
			ovDescriptionArea.setText("");
		}
	}

	// When you have real data, call this method from SceneManager injection.
	@SuppressWarnings("unused")
	private void setOverview(
			String caseName,
			String caseNumber,
			String responsibleAttorney,
			String caseStatus,
			String description,
			String caller,
			String client,
			LocalDate intakeDate,
			LocalDate incidentDate,
			LocalDate solDate,
			String opposingCounsel,
			String practiceArea,
			List<String> teamUsers) {
		if (ovCaseNameValue != null)
			ovCaseNameValue.setText(safe(caseName));
		if (ovCaseNumberValue != null)
			ovCaseNumberValue.setText(safe(caseNumber));
		if (ovResponsibleAttorneyValue != null)
			ovResponsibleAttorneyValue.setText(safe(responsibleAttorney));
		if (ovCaseStatusValue != null)
			ovCaseStatusValue.setText(safe(caseStatus));

		if (ovCallerValue != null)
			ovCallerValue.setText(safe(caller));
		if (ovClientValue != null)
			ovClientValue.setText(safe(client));

		if (ovPracticeAreaValue != null)
			ovPracticeAreaValue.setText(safe(practiceArea));
		if (ovOpposingCounselValue != null)
			ovOpposingCounselValue.setText(safe(opposingCounsel));

		if (ovTeamValue != null)
			ovTeamValue.setText(teamUsers == null || teamUsers.isEmpty() ? "—" : String.join(", ", teamUsers));

		if (ovIntakeDateValue != null)
			ovIntakeDateValue.setText(formatDate(intakeDate));
		if (ovIncidentDateValue != null)
			ovIncidentDateValue.setText(formatDate(incidentDate));
		if (ovSolDateValue != null)
			ovSolDateValue.setText(formatDate(solDate));

		if (ovDescriptionArea != null)
			ovDescriptionArea.setText(safe(description));
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

	private void loadOverviewOnce() {
		if (overviewLoaded)
			return;
		if (caseDao == null || caseId == null)
			return;

		overviewLoaded = true;

		// Run DB work off the FX thread.
		new Thread(() ->
		{
			CaseOverviewDto dto = caseDao.getOverview(caseId.longValue());

			Platform.runLater(() ->
			{
				if (dto == null)
					return;
				applyOverview(dto);
			});
		}, "case-overview-loader-" + caseId).start();
	}

	private void applyOverview(CaseOverviewDto dto) {

		// Header
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

		if (assignedLabel != null)
			assignedLabel.setText("Assigned: " + safe(dto.getResponsibleAttorney()));

		// You can wire this once you add UpdatedAt to the DTO.
		if (lastUpdatedLabel != null)
			lastUpdatedLabel.setText("Last updated: —");

		// Overview grid values
		if (ovCaseNameValue != null)
			ovCaseNameValue.setText(safe(dto.getCaseName()));

		if (ovCaseNumberValue != null)
			ovCaseNumberValue.setText(safe(dto.getCaseNumber()));

		if (ovResponsibleAttorneyValue != null)
			ovResponsibleAttorneyValue.setText(safe(dto.getResponsibleAttorney()));

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

		if (ovDescriptionArea != null) {
			// For Overview, keep it read-friendly; editing can move to Details later.
			ovDescriptionArea.setText(dto.getDescription() == null ? "" : dto.getDescription());
		}
	}

}
