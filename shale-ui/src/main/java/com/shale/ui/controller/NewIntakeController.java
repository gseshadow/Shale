package com.shale.ui.controller;

import com.shale.data.dao.CaseDao;
import com.shale.ui.state.AppState;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class NewIntakeController {

	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

	@FXML private Label validationLabel;

	@FXML private TextField caseNameField;
	@FXML private DatePicker dateOfIntakePicker;
	@FXML private TextField timeOfIntakeField;
	@FXML private CheckBox estateCaseCheckBox;

	@FXML private TextField clientFirstNameField;
	@FXML private TextField clientLastNameField;
	@FXML private TextField clientAddressField;
	@FXML private TextField clientPhoneField;
	@FXML private TextField clientEmailField;
	@FXML private DatePicker clientDateOfBirthPicker;
	@FXML private CheckBox clientDeceasedCheckBox;
	@FXML private TextField clientConditionField;

	@FXML private CheckBox callerIsClientCheckBox;
	@FXML private Label callerReuseLabel;
	@FXML private GridPane callerFieldsGrid;
	@FXML private TextField callerFirstNameField;
	@FXML private TextField callerLastNameField;
	@FXML private TextField callerPhoneField;

	@FXML private TextField practiceAreaField;
	@FXML private Button selectPracticeAreaButton;
	@FXML private TextField statusField;
	@FXML private Button selectStatusButton;
	@FXML private TextArea descriptionArea;
	@FXML private TextArea summaryArea;
	@FXML private DatePicker dateMedicalNegligencePicker;
	@FXML private DatePicker dateMedicalNegligenceDiscoveredPicker;
	@FXML private DatePicker dateOfInjuryPicker;
	@FXML private DatePicker statuteOfLimitationsPicker;
	@FXML private DatePicker tortClaimsNoticePicker;

	@FXML private Button cancelButton;
	@FXML private Button createIntakeButton;

	private AppState appState;
	private CaseDao caseDao;
	private Stage stage;

	private boolean caseNameManuallyOverridden;
	private boolean updatingCaseNameProgrammatically;

	private CaseDao.PracticeAreaRow selectedPracticeArea;
	private CaseDao.StatusRow selectedStatus;

	public void init(AppState appState, CaseDao caseDao, Stage stage) {
		this.appState = appState;
		this.caseDao = caseDao;
		this.stage = stage;
	}

	@FXML
	private void initialize() {
		dateOfIntakePicker.setValue(LocalDate.now());
		timeOfIntakeField.setText(LocalTime.now().format(TIME_FORMAT));

		callerIsClientCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> applyCallerMode(Boolean.TRUE.equals(newVal)));
		applyCallerMode(false);

		clientFirstNameField.textProperty().addListener((obs, oldVal, newVal) -> autoGenerateCaseName());
		clientLastNameField.textProperty().addListener((obs, oldVal, newVal) -> autoGenerateCaseName());

		caseNameField.textProperty().addListener((obs, oldVal, newVal) -> {
			if (!updatingCaseNameProgrammatically) {
				caseNameManuallyOverridden = true;
			}
		});

		selectPracticeAreaButton.setOnAction(e -> onSelectPracticeArea());
		selectStatusButton.setOnAction(e -> onSelectStatus());

		Platform.runLater(this::autoGenerateCaseName);
	}

	private void autoGenerateCaseName() {
		if (caseNameManuallyOverridden) {
			return;
		}
		String generated = buildCaseName(clientFirstNameField.getText(), clientLastNameField.getText());
		updatingCaseNameProgrammatically = true;
		caseNameField.setText(generated);
		updatingCaseNameProgrammatically = false;
	}

	private String buildCaseName(String first, String last) {
		String cleanFirst = safeTrim(first);
		String cleanLast = safeTrim(last);
		if (cleanFirst.isEmpty() && cleanLast.isEmpty()) {
			return "";
		}
		if (cleanLast.isEmpty()) {
			return cleanFirst;
		}
		if (cleanFirst.isEmpty()) {
			return cleanLast;
		}
		return cleanLast + ", " + cleanFirst;
	}

	private void applyCallerMode(boolean callerIsClient) {
		callerFieldsGrid.setDisable(callerIsClient);
		callerReuseLabel.setVisible(callerIsClient);
		callerReuseLabel.setManaged(callerIsClient);
	}

	private void onSelectPracticeArea() {
		try {
			List<CaseDao.PracticeAreaRow> areas = caseDao.listPracticeAreasForTenant(requireClientId());
			if (areas.isEmpty()) {
				showValidation("No practice areas are available for this tenant.");
				return;
			}

			Map<String, CaseDao.PracticeAreaRow> labelToRow = new LinkedHashMap<>();
			for (CaseDao.PracticeAreaRow area : areas) {
				String label = area.name() == null || area.name().isBlank() ? "Practice Area #" + area.id() : area.name();
				labelToRow.put(label, area);
			}

			String preselect = selectedPracticeArea == null ? labelToRow.keySet().iterator().next() : safeTrim(selectedPracticeArea.name());
			ChoiceDialog<String> dialog = new ChoiceDialog<>(preselect, labelToRow.keySet());
			dialog.setTitle("Select Practice Area");
			dialog.setHeaderText("Select Practice Area");
			dialog.setContentText("Practice Area:");

			Optional<String> picked = dialog.showAndWait();
			if (picked.isPresent()) {
				selectedPracticeArea = labelToRow.get(picked.get());
				practiceAreaField.setText(picked.get());
				hideValidation();
			}
		} catch (RuntimeException ex) {
			showValidation("Unable to load practice areas.");
		}
	}

	private void onSelectStatus() {
		try {
			List<CaseDao.StatusRow> statuses = caseDao.listStatusesForTenant(requireClientId());
			if (statuses.isEmpty()) {
				showValidation("No statuses are available for this tenant.");
				return;
			}

			Map<String, CaseDao.StatusRow> labelToRow = new LinkedHashMap<>();
			for (CaseDao.StatusRow status : statuses) {
				String label = status.name() == null || status.name().isBlank() ? "Status #" + status.id() : status.name();
				labelToRow.put(label, status);
			}

			String preselect = selectedStatus == null ? labelToRow.keySet().iterator().next() : safeTrim(selectedStatus.name());
			ChoiceDialog<String> dialog = new ChoiceDialog<>(preselect, labelToRow.keySet());
			dialog.setTitle("Select Status");
			dialog.setHeaderText("Select Status");
			dialog.setContentText("Status:");

			Optional<String> picked = dialog.showAndWait();
			if (picked.isPresent()) {
				selectedStatus = labelToRow.get(picked.get());
				statusField.setText(picked.get());
				hideValidation();
			}
		} catch (RuntimeException ex) {
			showValidation("Unable to load statuses.");
		}
	}

	private int requireClientId() {
		Integer clientId = appState == null ? null : appState.getShaleClientId();
		if (clientId == null || clientId <= 0) {
			throw new RuntimeException("No tenant selected.");
		}
		return clientId;
	}

	@FXML
	private void onCreateIntake() {
		List<String> errors = validate();
		if (!errors.isEmpty()) {
			showValidation(errors.stream().collect(Collectors.joining("\n")));
			return;
		}

		String description = safeTrim(descriptionArea.getText());
		String summary = safeTrim(summaryArea.getText());
		System.out.println("[New Intake - Step 1 placeholder] validated draft: "
				+ " caseName=" + safeTrim(caseNameField.getText())
				+ ", client=" + safeTrim(clientLastNameField.getText()) + ", " + safeTrim(clientFirstNameField.getText())
				+ ", practiceAreaId=" + (selectedPracticeArea == null ? null : selectedPracticeArea.id())
				+ ", statusId=" + (selectedStatus == null ? null : selectedStatus.id())
				+ ", descriptionLen=" + description.length()
				+ ", summaryLen=" + summary.length());
		showValidation("Draft intake validated. Step 1 does not yet persist to the database.");
		validationLabel.setTextFill(javafx.scene.paint.Paint.valueOf("#157347"));
	}

	@FXML
	private void onCancel() {
		if (stage != null) {
			stage.close();
		}
	}

	private List<String> validate() {
		return java.util.stream.Stream.of(
				required(clientFirstNameField.getText(), "Client First Name is required."),
				required(clientLastNameField.getText(), "Client Last Name is required."),
				selectedPracticeArea == null ? "Practice Area is required." : null,
				selectedStatus == null ? "Status is required." : null,
				requireDescriptionOrSummary(),
				callerRequiredWhenNotClient(callerFirstNameField.getText(), "Caller First Name is required when Caller is Client is unchecked."),
				callerRequiredWhenNotClient(callerLastNameField.getText(), "Caller Last Name is required when Caller is Client is unchecked.")
		).filter(s -> s != null && !s.isBlank()).toList();
	}

	private String required(String value, String message) {
		return safeTrim(value).isEmpty() ? message : null;
	}

	private String requireDescriptionOrSummary() {
		if (!safeTrim(descriptionArea.getText()).isEmpty() || !safeTrim(summaryArea.getText()).isEmpty()) {
			return null;
		}
		return "Either Description or Summary is required.";
	}

	private String callerRequiredWhenNotClient(String value, String message) {
		if (callerIsClientCheckBox.isSelected()) {
			return null;
		}
		return safeTrim(value).isEmpty() ? message : null;
	}

	private void showValidation(String message) {
		validationLabel.setText(message);
		validationLabel.setTextFill(javafx.scene.paint.Paint.valueOf("#b42318"));
		validationLabel.setVisible(true);
		validationLabel.setManaged(true);
	}

	private void hideValidation() {
		validationLabel.setVisible(false);
		validationLabel.setManaged(false);
	}

	private static String safeTrim(String value) {
		return value == null ? "" : value.trim();
	}
}
