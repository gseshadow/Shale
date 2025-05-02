package application;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

public class Intake extends FlowPane {

	private final int SPACING = 30;
	private final int TEXTAREAWIDTH = 350;
	private ArrayList<VBox> boxes = new ArrayList<>();
	private Potential potential;

	private VBox callerBox = new VBox();
	private TextField callerNameFirst = new TextField();
	private TextField callerNameLast = new TextField();
	private TextField callerPhone = new TextField();
	private DatePicker callerDate = new DatePicker();
	private LocalTime callerTime = LocalTime.now();
	private Label callerTimeString = new Label();

	private VBox clientBox = new VBox();;
	private TextField clientNameFirst = new TextField();
	private TextField clientNameLast = new TextField();
	private TextField clientAddress = new TextField();
	private TextField clientPhone = new TextField();
	private TextField clientEmail = new TextField();
	private DatePicker clientDOBPicker = new DatePicker();
	private LocalDate clientDOB = LocalDate.now();
	private Label clientAgeLabel = new Label();
	private int clientAge = 0;
	private TextArea incidentUpdates = new TextArea();

	private VBox incidentBox = new VBox();
	private DatePicker incidentDateMedNegOccurred = new DatePicker();
	private DatePicker incidentDateMedNegDiscovered = new DatePicker();
	private DatePicker incidentDateStatuteOfLimitations = new DatePicker();
	private TextField incidentDoctorsInvolved = new TextField();
	private TextField incidentFacilitiesInvolved = new TextField();
	private TextArea clientCondition = new TextArea();
	private TextField incidentMedicalBills = new TextField();
	private boolean incidentHaveMedRecords = false;
	private ChoiceBox<Boolean> incidentHaveMedRecordsBox = new ChoiceBox<>();
	private TextArea incidentSummary = new TextArea();
	private ChoiceBox<String> incidentPracticeAreaBox = new ChoiceBox<String>();

	private VBox followupBox = new VBox();;
	private TextArea followupQuestionsForPatient = new TextArea();
	private boolean followupMeetWithClient = false;
	private ChoiceBox<Boolean> followupMeetWithClientBox = new ChoiceBox<>();
	private boolean followupNurseReview = false;
	private ChoiceBox<Boolean> followupNurseReviewBox = new ChoiceBox<>();
	private boolean followupDoctorReview = false;
	private ChoiceBox<Boolean> followupDoctorReviewBox = new ChoiceBox<>();

	private VBox acceptedBox = new VBox();;
	private boolean acceptedChronology = false;
	private RadioButton acceptedChronologyButton = new RadioButton();
	private boolean acceptedSupportiveMedicalLiterature = false;
	private RadioButton acceptedSupportiveMedicalLiteratureButton = new RadioButton();
	private boolean acceptedConsultantExpertSearch = false;
	private RadioButton acceptedConsultantExpertSearchButton = new RadioButton();
	private boolean acceptedTestifyingExperSearch = false;
	private RadioButton acceptedTestifyingExperSearchButton = new RadioButton();
	private TextArea acceptedDetail = new TextArea();

	private VBox denialBox = new VBox();;
	private boolean denialChronology = false;
	private RadioButton denialChronologyButton = new RadioButton();
	private TextArea denialDetail = new TextArea();

	Intake(Potential potential) {
		this.setHgap(SPACING);
		this.setVgap(SPACING);
		this.setPadding(new Insets(5));
		this.setAlignment(Pos.TOP_CENTER);
		this.potential = potential;

		incidentHaveMedRecordsBox.setValue(false);
		followupMeetWithClientBox.setValue(false);
		followupNurseReviewBox.setValue(false);
		followupDoctorReviewBox.setValue(false);

		boxes.add(callerBox);
		{
			Label label = new Label("CALLER");
			label.setStyle("-fx-font-size: 24; -font-weight: bold");
			label.setUnderline(true);
			callerBox.getChildren().add(label);

			VBox categoryBox = new VBox();
			{
				categoryBox.setSpacing(5);
				categoryBox.setPadding(new Insets(5));
				categoryBox.setAlignment(Pos.TOP_LEFT);

				categoryBox.getChildren().add(new Label("First Name"));
				categoryBox.getChildren().add(callerNameFirst);

				categoryBox.getChildren().add(new Label("Last Name"));
				categoryBox.getChildren().add(callerNameLast);

				categoryBox.getChildren().add(new Label("Caller Phone Number"));
				categoryBox.getChildren().add(callerPhone);

				categoryBox.getChildren().add(new Label("Date / Time"));

				categoryBox.getChildren().add(callerDate);

				if (potential.getCallerTime() == null) {
					potential.setCallerTime(LocalTime.now());
				}
				categoryBox.getChildren().add(callerTimeString);
			}

			callerBox.getChildren().add(categoryBox);

		}

		boxes.add(clientBox);
		{
			Label label = new Label("CLIENT");
			label.setStyle("-fx-font-size: 24; -font-weight: bold");
			label.setUnderline(true);
			clientBox.getChildren().add(label);

			VBox categoryBox = new VBox();
			{
				categoryBox.setSpacing(5);
				categoryBox.setPadding(new Insets(5));
				categoryBox.setAlignment(Pos.TOP_LEFT);

				categoryBox.getChildren().add(new Label("First Name"));
				categoryBox.getChildren().add(clientNameFirst);

				categoryBox.getChildren().add(new Label("Last Name"));
				categoryBox.getChildren().add(clientNameLast);

				categoryBox.getChildren().add(new Label("Address"));
				categoryBox.getChildren().add(clientAddress);

				categoryBox.getChildren().add(new Label("Phone"));
				categoryBox.getChildren().add(clientPhone);

				categoryBox.getChildren().add(new Label("Email"));
				categoryBox.getChildren().add(clientEmail);

				categoryBox.getChildren().add(new Label("Date of Birth"));
				categoryBox.getChildren().add(clientDOBPicker);

				clientDOBPicker.setOnAction(new EventHandler<ActionEvent>() {

					@Override
					public void handle(ActionEvent arg0) {
						clientDOB = clientDOBPicker.getValue();
						clientAge = LocalDate.now().getYear() - clientDOBPicker.getValue().getYear();

						if (clientDOBPicker.getValue().getMonthValue() > LocalDate.now().getMonthValue()) {
							clientAge -= 1;
							if (clientAge < 0) {
								clientAge = 0;
							}
						}
						clientAgeLabel.setText("Age: " + String.valueOf(clientAge));

					}
				});

				categoryBox.getChildren().add(clientAgeLabel);

			}
			categoryBox.getChildren().add(new Label("Client Condition"));
			clientCondition.setWrapText(true);
			clientCondition.setPrefWidth(TEXTAREAWIDTH);
			categoryBox.getChildren().add(clientCondition);

			clientBox.getChildren().add(categoryBox);
		}

		boxes.add(incidentBox);
		{

			Label label = new Label("INCIDENT");
			label.setStyle("-fx-font-size: 24; -font-weight: bold");
			label.setUnderline(true);
			incidentBox.getChildren().add(label);

			VBox categoryBox = new VBox();
			{
				categoryBox.setSpacing(5);
				categoryBox.setPadding(new Insets(5));
				categoryBox.setAlignment(Pos.TOP_LEFT);

				categoryBox.getChildren().add(new Label("Practice Area"));
				categoryBox.getChildren().add(incidentPracticeAreaBox);

				categoryBox.getChildren().add(new Label("Date Medical Negligence Occurred"));
				categoryBox.getChildren().add(incidentDateMedNegOccurred);

				categoryBox.getChildren().add(new Label("Date Medical Negligence Discovered"));
				categoryBox.getChildren().add(incidentDateMedNegDiscovered);

				categoryBox.getChildren().add(new Label("Statute of Limitations"));
				categoryBox.getChildren().add(incidentDateStatuteOfLimitations);

				categoryBox.getChildren().add(new Label("Doctors Involved"));
				categoryBox.getChildren().add(incidentDoctorsInvolved);

				categoryBox.getChildren().add(new Label("Facilities Involved"));
				categoryBox.getChildren().add(incidentFacilitiesInvolved);

				ArrayList<Boolean> items = new ArrayList<>();
				items.add(true);
				items.add(false);
				ObservableList<Boolean> list = FXCollections.observableArrayList(items);
				incidentHaveMedRecordsBox.setItems(list);
				categoryBox.getChildren().add(new Label("In Posession of Medical Records"));
				categoryBox.getChildren().add(incidentHaveMedRecordsBox);

				categoryBox.getChildren().add(new Label("Summary"));
				incidentSummary.setWrapText(true);
				incidentSummary.setPrefWidth(TEXTAREAWIDTH);
				categoryBox.getChildren().add(incidentSummary);

				categoryBox.getChildren().add(new Label("Updates"));
				incidentUpdates.setWrapText(true);
				incidentUpdates.setPrefWidth(TEXTAREAWIDTH);
				categoryBox.getChildren().add(incidentUpdates);
			}
			incidentBox.getChildren().add(categoryBox);
		}
		boxes.add(followupBox);
		{
			Label label = new Label("FOLLOW UP");
			label.setStyle("-fx-font-size: 24; -font-weight: bold");
			label.setUnderline(true);
			followupBox.getChildren().add(label);

			VBox categoryBox = new VBox();
			{
				categoryBox.setSpacing(5);
				categoryBox.setPadding(new Insets(5));
				categoryBox.setAlignment(Pos.TOP_LEFT);

				categoryBox.getChildren().add(new Label("Questions for Patient"));
				categoryBox.getChildren().add(followupQuestionsForPatient);

				ArrayList<Boolean> items = new ArrayList<>();
				items.add(true);
				items.add(false);
				ObservableList<Boolean> mclist = FXCollections.observableArrayList(items);
				followupMeetWithClientBox.setItems(mclist);
				categoryBox.getChildren().add(new Label("Meeting with Client"));
				categoryBox.getChildren().add(followupMeetWithClientBox);

				ObservableList<Boolean> nrlist = FXCollections.observableArrayList(items);
				followupNurseReviewBox.setItems(nrlist);
				categoryBox.getChildren().add(new Label("Nurse Review"));
				categoryBox.getChildren().add(followupNurseReviewBox);

				ObservableList<Boolean> drlist = FXCollections.observableArrayList(items);
				followupDoctorReviewBox.setItems(drlist);
				categoryBox.getChildren().add(new Label("Doctor Review"));
				categoryBox.getChildren().add(followupDoctorReviewBox);
			}
			followupBox.getChildren().add(categoryBox);
		}

		if (potential != null && potential.isAccepted()) {
			boxes.add(acceptedBox);
			{
				Label label = new Label("ACCEPTED");
				label.setStyle("-fx-font-size: 24; -font-weight: bold");
				label.setUnderline(true);
				acceptedBox.getChildren().add(label);

				VBox categoryBox = new VBox();
				{
					categoryBox.setSpacing(5);
					categoryBox.setPadding(new Insets(5));
					categoryBox.setAlignment(Pos.TOP_LEFT);

					HBox a = new HBox();
					a.setSpacing(10);
					a.getChildren().add(acceptedChronologyButton);
					a.getChildren().add(new Label(":   Chronology"));
					categoryBox.getChildren().add(a);

					HBox b = new HBox();
					b.setSpacing(10);
					b.getChildren().add(acceptedConsultantExpertSearchButton);
					b.getChildren().add(new Label(":   Consultant Expert Search"));
					categoryBox.getChildren().add(b);

					HBox c = new HBox();
					c.setSpacing(10);
					c.getChildren().add(acceptedTestifyingExperSearchButton);
					c.getChildren().add(new Label(":   Testifying Expert Search"));
					categoryBox.getChildren().add(c);

					HBox d = new HBox();
					d.setSpacing(10);
					d.getChildren().add(acceptedSupportiveMedicalLiteratureButton);
					d.getChildren().add(new Label(":   Supportive Medical Literature"));
					categoryBox.getChildren().add(d);

					HBox e = new HBox();

					e.setSpacing(10);
					e.getChildren().add(new Label("Detail:"));
					e.getChildren().add(acceptedDetail);
					categoryBox.getChildren().add(e);
				}

				acceptedBox.getChildren().add(categoryBox);

			}
		}
		if (potential != null && potential.isRejected()) {
			boxes.add(denialBox);
			{

				Label label = new Label("DENIED");
				label.setStyle("-fx-font-size: 24; -font-weight: bold");
				label.setUnderline(true);
				denialBox.getChildren().add(label);

				VBox categoryBox = new VBox();
				{
					categoryBox.setSpacing(5);
					categoryBox.setPadding(new Insets(5));
					categoryBox.setAlignment(Pos.TOP_LEFT);

					HBox a = new HBox();
					a.setSpacing(5);
					a.getChildren().add(denialChronologyButton);
					a.getChildren().add(new Label(":   Chronology"));
					categoryBox.getChildren().add(a);

					categoryBox.getChildren().add(new Label("Details"));
					categoryBox.getChildren().add(denialDetail);
				}
				denialBox.getChildren().add(categoryBox);
			}
		}

		for (VBox box : boxes) {

			box.setPadding(new Insets(SPACING));
			box.setSpacing(SPACING);
			box.setBackground(new Background(new BackgroundFill(Color.LIGHTBLUE, new CornerRadii(5), new Insets(1))));
			box.setBorder(new Border(
					new BorderStroke(Color.BLACK, BorderStrokeStyle.SOLID, new CornerRadii(5), new BorderWidths(2))));
			this.getChildren().add(box);
		}

		incidentPracticeAreaBox.getItems().add("Medical Malpractice");
		incidentPracticeAreaBox.getItems().add("Personal Injury");

		populateData();

	}

	private void populateData() {
		if (this.potential == null)
			this.potential = new Potential();
		callerNameFirst.setText(potential.getCallerNameFirst());
		callerNameLast.setText(potential.getCallerNameLast());
		callerPhone.setText(potential.getCallerPhone());

		if (potential.getCallerDate() == null) {
			callerDate.setValue(LocalDate.now());
		} else
			callerDate.setValue(potential.getCallerDate());

		String ampm = " AM";
		callerTime = potential.getCallerTime();
		int hour = callerTime.getHour();
		if (hour > 12) {
			ampm = " PM";
			hour -= 12;
		}
		callerTimeString.setText(hour + ":" + callerTime.getMinute() / 10 + (callerTime.getMinute() % 10) + ampm);

		clientNameFirst.setText(potential.getClientNameFirst());
		clientNameLast.setText(potential.getClientNameLast());
		clientAddress.setText(potential.getClientAddress());
		clientPhone.setText(potential.getClientPhone());
		clientEmail.setText(potential.getClientEmail());
		clientDOB = potential.getClientDOB();
		clientDOBPicker.setValue(potential.getClientDOB());
		clientCondition.setText(potential.getClientCondition());

		incidentPracticeAreaBox.getSelectionModel().select(potential.getPracticeArea());
		incidentDateMedNegOccurred.setValue(potential.getIncidentMedNegOccurred());
		incidentDateMedNegDiscovered.setValue(potential.getIncidentMedNegDiscovered());
		incidentDateStatuteOfLimitations.setValue(potential.getIncidentStatuteOfLimitations());
		incidentDoctorsInvolved.setText(potential.getIncidentDoctorsInvolved());
		incidentFacilitiesInvolved.setText(potential.getIncidentFacilitiesInvolved());
		incidentHaveMedRecords = potential.isIncidentMedRecsInHand();
		incidentHaveMedRecordsBox.setValue(potential.isIncidentMedRecsInHand());
		incidentSummary.setText(potential.getIncidentSummary());
		incidentUpdates.setText(potential.getIncidentUpdates());

		followupQuestionsForPatient.setText(potential.getFollowUpQuestionsForPatient());
		followupMeetWithClient = potential.isFollowUpMeetingWithClient();
		followupMeetWithClientBox.setValue(potential.isFollowUpMeetingWithClient());
		followupNurseReview = potential.isFollowUpNurseReview();
		followupNurseReviewBox.setValue(potential.isFollowUpNurseReview());
		followupDoctorReview = potential.isFollowUpDoctorReview();
		followupDoctorReviewBox.setValue(potential.isFollowUpDoctorReview());

		acceptedChronology = potential.isAcceptedChronology();
		acceptedChronologyButton.setSelected(potential.isAcceptedChronology());
		acceptedConsultantExpertSearch = potential.isAcceptedConsultantExpertSearch();
		acceptedConsultantExpertSearchButton.setSelected(potential.isAcceptedConsultantExpertSearch());
		acceptedTestifyingExperSearch = potential.isAcceptedTestifyingExpertSearch();
		acceptedTestifyingExperSearchButton.setSelected(potential.isAcceptedTestifyingExpertSearch());
		acceptedSupportiveMedicalLiterature = potential.isAcceptedSupportiveMedicalLiterature();
		acceptedSupportiveMedicalLiteratureButton.setSelected(potential.isAcceptedSupportiveMedicalLiterature());
		acceptedDetail.setText(potential.getAcceptedDetail());

		denialChronology = potential.isDeniedChronology();
		denialChronologyButton.setSelected(potential.isDeniedChronology());
		denialDetail.setText(potential.getDeniedDetails());

	}

	public TextField getCallerNameFirst() {
		return callerNameFirst;
	}

	public void setCallerNameFirst(TextField callerNameFirst) {
		this.callerNameFirst = callerNameFirst;
	}

	public TextField getCallerNameLast() {
		return callerNameLast;
	}

	public void setCallerNameLast(TextField callerNameLast) {
		this.callerNameLast = callerNameLast;
	}

	public DatePicker getCallerDate() {
		return callerDate;
	}

	public void setCallerDate(DatePicker callerDate) {
		this.callerDate = callerDate;
	}

	public LocalTime getCallerTime() {
		return callerTime;
	}

	public void setCallerTime(LocalTime callerTime) {
		this.callerTime = callerTime;
	}

	public TextField getClientNameFirst() {
		return clientNameFirst;
	}

	public void setClientNameFirst(TextField clientNameFirst) {
		this.clientNameFirst = clientNameFirst;
	}

	public TextField getClientNameLast() {
		return clientNameLast;
	}

	public void setClientNameLast(TextField clientNameLast) {
		this.clientNameLast = clientNameLast;
	}

	public TextField getClientAddress() {
		return clientAddress;
	}

	public void setClientAddress(TextField clientAddress) {
		this.clientAddress = clientAddress;
	}

	public TextField getClientPhone() {
		return clientPhone;
	}

	public void setClientPhone(TextField clientPhone) {
		this.clientPhone = clientPhone;
	}

	public TextField getClientEmail() {
		return clientEmail;
	}

	public void setClientEmail(TextField clientEmail) {
		this.clientEmail = clientEmail;
	}

	public LocalDate getClientDOB() {
		return clientDOB;
	}

	public void setClientDOB(LocalDate clientDOB) {
		this.clientDOB = clientDOB;
	}

	public Label getClientAgeLabel() {
		return clientAgeLabel;
	}

	public void setClientAgeLabel(Label clientAgeLabel) {
		this.clientAgeLabel = clientAgeLabel;
	}

	public int getClientAge() {
		return clientAge;
	}

	public void setClientAge(int clientAge) {
		this.clientAge = clientAge;
	}

	public DatePicker getIncidentDateMedNegOccurred() {
		return incidentDateMedNegOccurred;
	}

	public void setIncidentDateMedNegOccurred(DatePicker incidentDateMedNegOccurred) {
		this.incidentDateMedNegOccurred = incidentDateMedNegOccurred;
	}

	public DatePicker getIncidentDateMedNegDiscovered() {
		return incidentDateMedNegDiscovered;
	}

	public void setIncidentDateMedNegDiscovered(DatePicker incidentDateMedNegDiscovered) {
		this.incidentDateMedNegDiscovered = incidentDateMedNegDiscovered;
	}

	public DatePicker getIncidentDateStatuteOfLimitations() {
		return incidentDateStatuteOfLimitations;
	}

	public void setIncidentDateStatuteOfLimitations(DatePicker incidentDateStatuteOfLimitations) {
		this.incidentDateStatuteOfLimitations = incidentDateStatuteOfLimitations;
	}

	public TextField getIncidentDoctorsInvolved() {
		return incidentDoctorsInvolved;
	}

	public void setIncidentDoctorsInvolved(TextField incidentDoctorsInvolved) {
		this.incidentDoctorsInvolved = incidentDoctorsInvolved;
	}

	public TextField getIncidentFacilitiesInvolved() {
		return incidentFacilitiesInvolved;
	}

	public void setIncidentFacilitiesInvolved(TextField incidentFacilitiesInvolved) {
		this.incidentFacilitiesInvolved = incidentFacilitiesInvolved;
	}

	public TextArea getClientCondition() {
		return clientCondition;
	}

	public void setClientCondition(TextArea clientCondition) {
		this.clientCondition = clientCondition;
	}

	public TextField getIncidentMedicalBills() {
		return incidentMedicalBills;
	}

	public void setIncidentMedicalBills(TextField incidentMedicalBills) {
		this.incidentMedicalBills = incidentMedicalBills;
	}

	public boolean isIncidentHaveMedRecords() {
		return incidentHaveMedRecords;
	}

	public void setIncidentHaveMedRecords(boolean incidentHaveMedRecords) {
		this.incidentHaveMedRecords = incidentHaveMedRecords;
	}

	public TextArea getIncidentSummary() {
		return incidentSummary;
	}

	public void setIncidentSummary(TextArea incidentSummary) {
		this.incidentSummary = incidentSummary;
	}

	public TextArea getFollowupQuestionsForPatient() {
		return followupQuestionsForPatient;
	}

	public void setFollowupQuestionsForPatient(TextArea followupQuestionsForPatient) {
		this.followupQuestionsForPatient = followupQuestionsForPatient;
	}

	public boolean isFollowupMeetWithClient() {
		return followupMeetWithClient;
	}

	public void setFollowupMeetWithClient(boolean followupMeetWithClient) {
		this.followupMeetWithClient = followupMeetWithClient;
	}

	public boolean isFollowupNurseReview() {
		return followupNurseReview;
	}

	public void setFollowupNurseReview(boolean followupNurseReview) {
		this.followupNurseReview = followupNurseReview;
	}

	public boolean isFollowupDoctorReview() {
		return followupDoctorReview;
	}

	public void setFollowupDoctorReview(boolean followupDoctorReview) {
		this.followupDoctorReview = followupDoctorReview;
	}

	public boolean isAcceptedChronology() {
		return acceptedChronology;
	}

	public void setAcceptedChronology(boolean acceptedChronology) {
		this.acceptedChronology = acceptedChronology;
	}

	public boolean isAcceptedSupportiveMedicalLiterature() {
		return acceptedSupportiveMedicalLiterature;
	}

	public void setAcceptedSupportiveMedicalLiterature(boolean acceptedSupportiveMedicalLiterature) {
		this.acceptedSupportiveMedicalLiterature = acceptedSupportiveMedicalLiterature;
	}

	public boolean isAcceptedConsultantExpertSearch() {
		return acceptedConsultantExpertSearch;
	}

	public void setAcceptedConsultantExpertSearch(boolean acceptedConsultantExpertSearch) {
		this.acceptedConsultantExpertSearch = acceptedConsultantExpertSearch;
	}

	public boolean isAcceptedTestifyingExperSearch() {
		return acceptedTestifyingExperSearch;
	}

	public void setAcceptedTestifyingExperSearch(boolean acceptedTestifyingExperSearch) {
		this.acceptedTestifyingExperSearch = acceptedTestifyingExperSearch;
	}

	public TextArea getAcceptedDetail() {
		return acceptedDetail;
	}

	public void setAcceptedDetail(TextArea acceptedDetail) {
		this.acceptedDetail = acceptedDetail;
	}

	public boolean isDenialChronology() {
		return denialChronology;
	}

	public void setDenialChronology(boolean denialChronology) {
		this.denialChronology = denialChronology;
	}

	public TextArea getDenialDetail() {
		return denialDetail;
	}

	public void setDenialDetail(TextArea denialDetail) {
		this.denialDetail = denialDetail;
	}

	public Potential getPotential() {
		Potential temp = new Potential(callerNameFirst.getText(), callerNameLast.getText(), callerPhone.getText(),
				callerTime, callerDate.getValue(), clientNameFirst.getText(), clientNameLast.getText(),
				clientAddress.getText(), clientPhone.getText(), clientEmail.getText(), clientDOBPicker.getValue(),
				incidentUpdates.getText(), incidentPracticeAreaBox.getSelectionModel().getSelectedItem(),
				incidentDateMedNegOccurred.getValue(), incidentDateMedNegDiscovered.getValue(),
				incidentDateStatuteOfLimitations.getValue(), incidentDoctorsInvolved.getText(),
				incidentFacilitiesInvolved.getText(), incidentHaveMedRecordsBox.getValue(), clientCondition.getText(),
				incidentSummary.getText(), followupQuestionsForPatient.getText(), followupMeetWithClientBox.getValue(),
				followupNurseReviewBox.getValue(), followupDoctorReviewBox.getValue(),
				acceptedChronologyButton.isSelected(), acceptedConsultantExpertSearchButton.isSelected(),
				acceptedTestifyingExperSearchButton.isSelected(),
				acceptedSupportiveMedicalLiteratureButton.isSelected(), acceptedDetail.getText(),
				denialChronologyButton.isSelected(), denialDetail.getText(), potential.getFileName(),
				potential.isAccepted(), potential.isRejected());

		if (temp.isAccepted()) {
			temp.setPrefix(Main.getFileLocation() + "\\Accepted");
		} else if (temp.isRejected()) {
			temp.setPrefix(Main.getFileLocation() + "\\Rejected");
		} else
			temp.setPrefix(Main.getFileLocation() + "\\Potentials");

		Main.setPotential(temp);
		return temp;
	}

	public void setAccepted(boolean state) {
		potential.setRejected(false);
		potential.setAccepted(state);
		String location = Main.getFileLocation() + "\\Accepted\\" + potential.getClientNameLast() + ", "
				+ potential.getClientNameFirst();
		potential.setFileName(location);
	}

	public void setRejected(boolean state) {
		potential.setAccepted(false);
		potential.setRejected(state);
		String location = Main.getFileLocation() + "\\Rejected\\" + potential.getClientNameLast() + ", "
				+ potential.getClientNameFirst();
		potential.setFileName(location);
	}

	public void setFileName(String name) {
		potential.setFileName(name);
	}

	public void setPrefix(String pre) {
		potential.setPrefix(pre);
	}

}
