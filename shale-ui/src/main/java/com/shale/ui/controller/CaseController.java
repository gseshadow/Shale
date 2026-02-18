package com.shale.ui.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;

import com.shale.core.dto.CaseOverviewDto;
import com.shale.data.dao.CaseDao;
import com.shale.ui.component.factory.UserCardFactory;
import com.shale.ui.component.factory.UserCardFactory.UserCardModel;
import com.shale.ui.component.factory.UserCardFactory.Variant;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class CaseController {

	// Header
	@FXML
	private Label caseTitleLabel;
	@FXML
	private Label statusLabel;

	// REPLACED: was Label assignedLabel
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

	// REPLACED: was Label ovResponsibleAttorneyValue
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

	// --- User card / navigation wiring ---
	private Consumer<Integer> onOpenUser;
	private UserCardFactory userCardFactory;

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

	/**
	 * Call this from SceneManager (or wherever you inject controller callbacks) so UserCards
	 * can navigate to a user profile view later.
	 */
	public void setOnOpenUser(Consumer<Integer> onOpenUser) {
		this.onOpenUser = onOpenUser;
		this.userCardFactory = new UserCardFactory(onOpenUser);
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

		// MINI user card placeholder
		renderResponsibleAttorneyMini(null, "—", null);

		if (lastUpdatedLabel != null)
			lastUpdatedLabel.setText("Last updated: —");
	}

	private void refreshOverviewPlaceholders() {
		// Once you inject a Case DTO/VM, this becomes setOverview(caseVm)

		if (ovCaseNameValue != null)
			ovCaseNameValue.setText(caseId == null ? "—" : "Case #" + caseId + " (Placeholder name)");
		if (ovCaseNumberValue != null)
			ovCaseNumberValue.setText(caseId == null ? "—" : String.valueOf(caseId));

		// MINI user card placeholder
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

		if (ovDescriptionArea != null && (ovDescriptionArea.getText() == null || ovDescriptionArea.getText().isBlank())) {
			ovDescriptionArea.setText("");
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

		// ✅ PROPER responsible attorney wiring

		Integer raUserId = dto.getResponsibleAttorneyUserId(); // <-- must exist
		String raName = safe(dto.getResponsibleAttorney());
		String raColor = dto.getResponsibleAttorneyColor(); // <-- must exist

		System.out.println("RA userId=" + raUserId + " name=" + raName);

		renderResponsibleAttorneyMini(raUserId, raName, raColor);

		// --- rest of your overview values ---
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

		if (ovDescriptionArea != null)
			ovDescriptionArea.setText(dto.getDescription() == null ? "" : dto.getDescription());
	}

	private void renderResponsibleAttorneyMini(Integer userId, String displayName, String userColorCss) {

		if (userCardFactory == null) {
			userCardFactory = new UserCardFactory(id ->
			{
			});
		}

		UserCardModel model = new UserCardModel(
				userId,
				(displayName == null || displayName.isBlank()) ? "—" : displayName,
				userColorCss,
				null
		);

		// ✅ IMPORTANT: create a separate Node for each host
		var headerCard = userCardFactory.create(model, Variant.MINI);
		var overviewCard = userCardFactory.create(model, Variant.MINI);

		if (assignedUserHost != null) {
			assignedUserHost.getChildren().setAll(headerCard);
		}
		if (ovResponsibleAttorneyHost != null) {
			ovResponsibleAttorneyHost.getChildren().setAll(overviewCard);
		}
	}

	private static Integer tryGetInt(Object target, String... methodNames) {
		for (String m : methodNames) {
			try {
				var method = target.getClass().getMethod(m);
				Object val = method.invoke(target);
				if (val instanceof Number n)
					return n.intValue();
			} catch (Exception ignored) {
			}
		}
		return null;
	}

	private static String tryGetString(Object target, String... methodNames) {
		for (String m : methodNames) {
			try {
				var method = target.getClass().getMethod(m);
				Object val = method.invoke(target);
				if (val != null)
					return val.toString();
			} catch (Exception ignored) {
			}
		}
		return null;
	}
}
