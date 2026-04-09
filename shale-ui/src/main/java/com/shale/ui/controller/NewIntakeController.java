package com.shale.ui.controller;

import com.shale.data.dao.CaseDao;
import com.shale.ui.component.dialog.AppDialogs;
import com.shale.ui.component.factory.PracticeAreaCardFactory;
import com.shale.ui.component.factory.PracticeAreaCardFactory.PracticeAreaCardModel;
import com.shale.ui.component.factory.StatusCardFactory;
import com.shale.ui.component.factory.StatusCardFactory.StatusCardModel;
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
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class NewIntakeController {

	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
	private static final DateTimeFormatter TIME_PARSE_FORMAT = DateTimeFormatter.ofPattern("H:mm");

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
	@FXML private TextArea clientConditionArea;

	@FXML private CheckBox callerIsClientCheckBox;
	@FXML private Label callerReuseLabel;
	@FXML private Label callerFirstNameRequiredIndicator;
	@FXML private Label callerLastNameRequiredIndicator;
	@FXML private Label callerPhoneRequiredIndicator;
	@FXML private GridPane callerFieldsGrid;
	@FXML private TextField callerFirstNameField;
	@FXML private TextField callerLastNameField;
	@FXML private TextField callerPhoneField;
	@FXML private TextField callerAddressField;
	@FXML private TextField callerEmailField;

	@FXML private StackPane practiceAreaHost;
	@FXML private Button selectPracticeAreaButton;
	@FXML private StackPane statusHost;
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
	private Consumer<Integer> onCaseCreated;
	private boolean saving;

	private boolean caseNameManuallyOverridden;
	private boolean updatingCaseNameProgrammatically;

	private CaseDao.PracticeAreaRow selectedPracticeArea;
	private CaseDao.StatusRow selectedStatus;
	private PracticeAreaCardFactory practiceAreaCardFactory;
	private StatusCardFactory statusCardFactory;

	public void init(AppState appState, CaseDao caseDao, Stage stage, Consumer<Integer> onCaseCreated) {
		this.appState = appState;
		this.caseDao = caseDao;
		this.stage = stage;
		this.onCaseCreated = onCaseCreated;
		Platform.runLater(this::preselectDefaultStatusIfAvailable);
	}

	@FXML
	private void initialize() {
		dateOfIntakePicker.setValue(LocalDate.now());
		timeOfIntakeField.setText(LocalTime.now().format(TIME_FORMAT));

		callerIsClientCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
			applyCallerMode(Boolean.TRUE.equals(newVal));
			hideValidation();
		});
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
		renderPracticeAreaMini(null, "—", null);
		renderStatusMini(null, "—", null);

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
		boolean callerFieldsRequired = !callerIsClient;
		setRequiredIndicator(callerFirstNameRequiredIndicator, callerFieldsRequired);
		setRequiredIndicator(callerLastNameRequiredIndicator, callerFieldsRequired);
		setRequiredIndicator(callerPhoneRequiredIndicator, callerFieldsRequired);
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
			Optional<String> picked = showSecondaryChoiceDialog(
					"Change Practice Area",
					"Practice Area:",
					preselect,
					labelToRow.keySet());
			if (picked.isPresent()) {
				selectedPracticeArea = labelToRow.get(picked.get());
				renderPracticeAreaMini(selectedPracticeArea.id(), selectedPracticeArea.name(), selectedPracticeArea.color());
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
			Optional<String> picked = showSecondaryChoiceDialog(
					"Change Status",
					"Status:",
					preselect,
					labelToRow.keySet());
			if (picked.isPresent()) {
				selectedStatus = labelToRow.get(picked.get());
				renderStatusMini(selectedStatus.id(), selectedStatus.name(), selectedStatus.color());
				hideValidation();
			}
		} catch (RuntimeException ex) {
			showValidation("Unable to load statuses.");
		}
	}

	private Optional<String> showSecondaryChoiceDialog(
			String title,
			String content,
			String preselect,
			java.util.Collection<String> options) {
		ChoiceDialog<String> dialog = new ChoiceDialog<>(preselect, options);
		dialog.setTitle(title);
		dialog.setHeaderText(null);
		dialog.setContentText(content);
		AppDialogs.applySecondaryDialogShell(dialog, title);
		Window owner = stage == null ? null : stage;
		if (owner != null) {
			dialog.initOwner(owner);
		}
		return dialog.showAndWait();
	}

	private void preselectDefaultStatusIfAvailable() {
		if (selectedStatus != null || caseDao == null || appState == null) {
			return;
		}
		try {
			List<CaseDao.StatusRow> statuses = caseDao.listStatusesForTenant(requireClientId());
			Optional<CaseDao.StatusRow> defaultOpenStatus = statuses.stream()
					.filter(Objects::nonNull)
					.filter(status -> !CaseDao.isTerminalStatus(status))
					.findFirst();
			if (defaultOpenStatus.isPresent()) {
				selectedStatus = defaultOpenStatus.get();
				renderStatusMini(selectedStatus.id(), selectedStatus.name(), selectedStatus.color());
			}
		} catch (RuntimeException ignored) {
			// If statuses cannot be loaded at initialization time, keep existing fallback (unselected).
		}
	}

	private void renderPracticeAreaMini(Integer practiceAreaId, String name, String colorCss) {
		if (practiceAreaHost == null)
			return;
		if (practiceAreaCardFactory == null) {
			practiceAreaCardFactory = new PracticeAreaCardFactory(id -> {
			});
		}
		PracticeAreaCardModel model = new PracticeAreaCardModel(
				practiceAreaId,
				(name == null || name.isBlank()) ? "—" : name,
				colorCss
		);
		practiceAreaHost.getChildren().setAll(practiceAreaCardFactory.create(model, PracticeAreaCardFactory.Variant.MINI));
	}

	private void renderStatusMini(Integer statusId, String statusName, String statusColorCss) {
		if (statusHost == null)
			return;
		if (statusCardFactory == null) {
			statusCardFactory = new StatusCardFactory(id -> {
			});
		}
		StatusCardModel model = new StatusCardModel(
				statusId,
				(statusName == null || statusName.isBlank()) ? "—" : statusName,
				null,
				statusColorCss
		);
		statusHost.getChildren().setAll(statusCardFactory.create(model, StatusCardFactory.Variant.MINI));
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
		if (saving)
			return;

		List<String> errors = validate();
		if (!errors.isEmpty()) {
			showValidation(errors.stream().collect(Collectors.joining("\n")));
			return;
		}

		setSaving(true);
		try {
			CaseDao.NewIntakeCreateRequest request = new CaseDao.NewIntakeCreateRequest(
				requireClientId(),
				safeTrim(caseNameField.getText()),
				dateOfIntakePicker.getValue(),
				LocalTime.parse(safeTrim(timeOfIntakeField.getText()), TIME_PARSE_FORMAT),
				estateCaseCheckBox.isSelected(),
				selectedPracticeArea.id(),
				selectedStatus.id(),
				safeTrim(descriptionArea.getText()),
				safeTrim(summaryArea.getText()),
				dateMedicalNegligencePicker.getValue(),
				dateMedicalNegligenceDiscoveredPicker.getValue(),
				dateOfInjuryPicker.getValue(),
				statuteOfLimitationsPicker.getValue(),
				tortClaimsNoticePicker.getValue(),
				safeTrim(clientFirstNameField.getText()),
				safeTrim(clientLastNameField.getText()),
				safeTrim(clientAddressField.getText()),
				safeTrim(clientPhoneField.getText()),
				safeTrim(clientEmailField.getText()),
				clientDateOfBirthPicker.getValue(),
				clientDeceasedCheckBox.isSelected(),
				safeTrim(clientConditionArea.getText()),
				callerIsClientCheckBox.isSelected(),
				safeTrim(callerFirstNameField.getText()),
				safeTrim(callerLastNameField.getText()),
				safeTrim(callerPhoneField.getText()),
				safeTrim(callerAddressField.getText()),
				safeTrim(callerEmailField.getText()),
				appState == null ? null : appState.getUserId()
			);

			CaseDao.NewIntakeCreateResult result = caseDao.createIntake(request);
			showSuccess("Intake created successfully.");
			if (stage != null)
				stage.close();
			if (onCaseCreated != null)
				onCaseCreated.accept(Math.toIntExact(result.caseId()));
		} catch (RuntimeException ex) {
			showValidation("Create intake failed: " + firstMeaningfulMessage(ex));
		} finally {
			setSaving(false);
		}
	}

	@FXML
	private void onCancel() {
		if (stage != null) {
			stage.close();
		}
	}

	private void setSaving(boolean saving) {
		this.saving = saving;
		if (createIntakeButton != null)
			createIntakeButton.setDisable(saving);
		if (cancelButton != null)
			cancelButton.setDisable(saving);
		if (selectPracticeAreaButton != null)
			selectPracticeAreaButton.setDisable(saving);
		if (selectStatusButton != null)
			selectStatusButton.setDisable(saving);
	}

	private String firstMeaningfulMessage(Throwable throwable) {
		Throwable current = throwable;
		while (current != null) {
			String message = current.getMessage();
			if (message != null && !message.isBlank())
				return message;
			current = current.getCause();
		}
		return "Unexpected error";
	}

	private List<String> validate() {
		return java.util.stream.Stream.of(
				required(caseNameField.getText(), "Case Name is required."),
				requiredDate(dateOfIntakePicker.getValue(), "Date of Intake is required."),
				validateIntakeTime(),
				required(clientFirstNameField.getText(), "Client First Name is required."),
				required(clientLastNameField.getText(), "Client Last Name is required."),
				required(clientPhoneField.getText(), "Client Phone Number is required."),
				selectedPracticeArea == null ? "Practice Area is required." : null,
				selectedStatus == null ? "Status is required." : null,
				callerRequiredWhenNotClient(callerFirstNameField.getText(), "Caller First Name is required when Caller is Client is unchecked."),
				callerRequiredWhenNotClient(callerLastNameField.getText(), "Caller Last Name is required when Caller is Client is unchecked."),
				callerRequiredWhenNotClient(callerPhoneField.getText(), "Caller Phone Number is required when Caller is Client is unchecked.")
		).filter(s -> s != null && !s.isBlank()).toList();
	}

	private String required(String value, String message) {
		return safeTrim(value).isEmpty() ? message : null;
	}

	private String validateIntakeTime() {
		String value = safeTrim(timeOfIntakeField.getText());
		if (value.isEmpty()) {
			return "Time of Intake is required.";
		}
		try {
			LocalTime.parse(value, TIME_PARSE_FORMAT);
			return null;
		} catch (Exception e) {
			return "Time of Intake must use HH:mm format.";
		}
	}

	private String requiredDate(LocalDate value, String message) {
		return value == null ? message : null;
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

	private void showSuccess(String message) {
		validationLabel.setText(message);
		validationLabel.setTextFill(javafx.scene.paint.Paint.valueOf("#157347"));
		validationLabel.setVisible(true);
		validationLabel.setManaged(true);
	}

	private void hideValidation() {
		validationLabel.setVisible(false);
		validationLabel.setManaged(false);
	}

	private void setRequiredIndicator(Label indicator, boolean visible) {
		if (indicator == null) {
			return;
		}
		indicator.setVisible(visible);
		indicator.setManaged(visible);
	}

	private static String safeTrim(String value) {
		return value == null ? "" : value.trim();
	}
}
