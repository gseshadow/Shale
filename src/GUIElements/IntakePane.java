package GUIElements;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.sql.Date;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;

import application.Main;
import connections.ConnectionResources;
import connections.Server;
import dataStructures.Case;
import dataStructures.Contact;
import dataStructures.Facility;
import dataStructures.PracticeArea;
import dataStructures.User;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
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
import javafx.scene.text.Font;

public class IntakePane extends FlowPane {

	private final int SPACING = 30;
	private final int TEXTAREAWIDTH = 350;
	private ArrayList<VBox> boxes = new ArrayList<>();
	private Case currentCase;

	private VBox callerBox = new VBox();

	private TextField caseName = new TextField();
	private TextField caseNumber = new TextField();

	private TextField callerNameFirst = new TextField();
	private TextField callerNameLast = new TextField();
	private TextField callerPhone = new TextField();
	private TextField callerReferredFrom = new TextField();
	private DatePicker callerDate = new DatePicker();
	private LocalTime callerTime = LocalTime.now();
	private Label callerTimeString = new Label();

	private VBox clientBox = new VBox();;
	private CheckBox sameAsCaller = new CheckBox("Same as Caller");
	private Label clientNotification = new Label();
	private RadioButton clientEstate = new RadioButton(": Estate of");
	private TextField clientNameFirst = new TextField();
	private TextField clientNameLast = new TextField();
	private TextArea clientAddress = new TextArea();
	private TextField clientPhone = new TextField();
	private TextField clientEmail = new TextField();
	private DatePicker clientDOBPicker = new DatePicker();
	private Button clientDOBClear = new Button("X");
	private LocalDate clientDOB = LocalDate.now();
	private Label clientAgeLabel = new Label();
	private int clientAge = 0;
	private RadioButton clientDeceased = new RadioButton(": Deceased");
	private TextArea clientCondition = new TextArea();

	private VBox incidentBox = new VBox();
	private DatePicker incidentDateMedNegOccurred = new DatePicker();
	private Button incidentDateMedNegOccurredClear = new Button("X");
	private DatePicker incidentDateMedNegDiscovered = new DatePicker();
	private Button incidentDateMedNegDiscoveredClear = new Button("X");
	private DatePicker incidentDateOfInjury = new DatePicker();
	private Button incidentDateOfInjuryClear = new Button("X");
	private DatePicker incidentDateStatuteOfLimitations = new DatePicker();
	private Button incidentDateStatuteOfLimitationsClear = new Button("X");
	private TextField incidentPotentialDefendants = new TextField();
	private TextField incidentFacilitiesInvolved = new TextField();
	private TextField incidentMedicalBills = new TextField();
	private RadioButton incidentHaveMedRecordsButton = new RadioButton(": In Posession of Medical Records");
	private TextArea incidentSummary = new TextArea();
	private ChoiceBox<PracticeArea> incidentPracticeAreaBox = new ChoiceBox<PracticeArea>();
	private TextField incidentDescription = new TextField();
	private TextField incidentCaseStatus = new TextField();
	private TextField incidentSubmissionField = new TextField();
	private Button incidentSubmit = new Button("Submit");
	private TextArea incidentUpdates = new TextArea();

	/*
	 * TODO Not Yet Added
	 */
	private DatePicker incidentTortNoticeDeadline = new DatePicker();
//	private Button incidentTortNoticeDeadlineClear = new Button("X");
	private DatePicker incidentDiscoveryDeadline = new DatePicker();
//	private Button incidentDiscoveryDeadlineClear = new Button("X");
	/*
	 * 
	 */

	private VBox receivedBox = new VBox();
	private TextField receivedSubmissionField = new TextField();
	private Button receivedSubmit = new Button("Submit");
	private TextArea receivedUpdates = new TextArea();

	private VBox followupBox = new VBox();;
	private TextField followUpSubmissionField = new TextField();
	private Button followUpSubmit = new Button("Submit");
	private TextArea followupQuestionsForPatient = new TextArea();
	private RadioButton followupMeetWithClient = new RadioButton(": Meeting with Clients");
	private RadioButton followupNurseReview = new RadioButton(": Nurse Review");
	private RadioButton followupExpertReview = new RadioButton(": Expert Review");
	private RadioButton followupTransferredButton = new RadioButton(": Case Transferred");
	private ToggleButton signed = new ToggleButton();

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
	private boolean acceptedTransferEnable = false;
	private ArrayList<RadioButton> checklist = new ArrayList<>();

	private VBox denialBox = new VBox();;
	private boolean denialChronology = false;
	private RadioButton denialChronologyButton = new RadioButton();
	private TextArea denialDetail = new TextArea();

	private ChoiceBox<User> officeResponsibleAttorney = new ChoiceBox<>();
	private TextField officePrinterCode = new TextField();

	private boolean isNewCase = false;
	private boolean practiceAreaUpdate = true;
	private boolean responsibleAttorneyUpdate = true;
	private boolean debug = Main.isGlobalDebug();
	private IntakePane ip;
	private Label involved = new Label();

	public IntakePane(Case cse, boolean isNewCase) {

		currentCase = cse;
		this.setNewCase(isNewCase);
		ip = this;

		if (isNewCase) {
			/*
			 * Create new case on server and use as base case for intake
			 */
			Task<Integer> t = new Task<Integer>() {

				@Override
				protected Integer call() throws Exception {
					/*
					 * Build a new case and get id from server
					 */
					currentCase.setCallerTime(Time.valueOf(LocalTime.now()));
					currentCase.setCallerDate(Date.valueOf(LocalDate.now()));
					currentCase.setCaseOrganizationId(Main.getCurrentUser().getDefault_organization());
					currentCase.setOfficeIntakePersonId(Main.getCurrentUser().get_id());
					currentCase.setOfficeIntakePerson(Main.getCurrentUser());
					currentCase.setCaseStatusId(9);// Potential

					currentCase.set_id(Server.createCase(currentCase, ConnectionResources.getConnection()));

					Main.getUpdater().publish(Main.getCurrentUser().get_id() + "#8#" + currentCase.get_id() + "#newCase#");

					currentCase.setIncidentId(
							Server.createNewIncident(currentCase.getCaseOrganizationId(), currentCase.get_id(), ConnectionResources.getConnection()));
					Server.updateCaseIntField(currentCase.get_id(), "incidentId", currentCase.getIncidentId(), ConnectionResources.getConnection());

					currentCase.setClientId(
							Server.createNewContact(Main.getCurrentUser().getDefault_organization(), ConnectionResources.getConnection()));
					Server.updateCaseIntField(currentCase.get_id(), "clientId", currentCase.getClientId(), ConnectionResources.getConnection());

					currentCase.setCallerId(
							Server.createNewContact(Main.getCurrentUser().getDefault_organization(), ConnectionResources.getConnection()));
					Server.updateCaseIntField(currentCase.get_id(), "callerId", currentCase.getCallerId(), ConnectionResources.getConnection());

					return null;
				}
			};
			new Thread(t).start();
			t.setOnSucceeded(new EventHandler<WorkerStateEvent>() {

				@Override
				public void handle(WorkerStateEvent arg0) {

					Main.addCase(currentCase.get_id(), currentCase);
					Main.setCase(currentCase);
					Main.setCurrentCaseId(currentCase.get_id());
					Main.getController().changeCenter(ip);

					Task<Integer> tsk = new Task<Integer>() {

						@Override
						protected Integer call() throws Exception {
							Main.addIncident(currentCase.getIncidentId(),
									Server.getIncidentById(currentCase.getIncidentId(), ConnectionResources.getConnection()));
							currentCase.setIncident(Main.getIncidents().get(currentCase.getIncidentId()));

							Main.addContact(currentCase.getClientId(),
									Server.getContactById(currentCase.getClientId(), ConnectionResources.getConnection()));
							currentCase.setClient(Main.getContacts().get(currentCase.getClientId()));

							Main.addContact(currentCase.getCallerId(),
									Server.getContactById(currentCase.getCallerId(), ConnectionResources.getConnection()));
							currentCase.setCaller(Main.getContacts().get(currentCase.getCallerId()));

							return null;
						}
					};
					new Thread(tsk).start();
					loadIntake();
				}
			});

		} else {// Existing case change off of loading screen
			Main.setCase(currentCase);
			Main.setCurrentCaseId(currentCase.get_id());
			Main.getController().changeCenter(ip);
			loadIntake();

		}

	}

	private void loadIntake() {
		populateData();

		this.setHgap(SPACING);
		this.setVgap(SPACING);
		this.setPadding(new Insets(5));
		this.setAlignment(Pos.TOP_CENTER);

		// incidentPracticeAreaBox.getItems().add(null);
		for (PracticeArea p : Main.getPracticeAreas().values()) {
			incidentPracticeAreaBox.getItems().add(p);
		}

		boxes.add(callerBox);
		{

			Label caseLable = new Label("CASE");
			caseLable.setStyle("-fx-font-size: 24; -font-weight: bold");
			caseLable.setUnderline(true);
			callerBox.getChildren().add(caseLable);
			VBox caseBox = new VBox();
			{
				caseBox.setSpacing(5);
				caseBox.setPadding(new Insets(5));
				caseBox.setAlignment(Pos.TOP_LEFT);

				caseBox.getChildren().add(new Label("Case Name"));
				caseName.focusedProperty().addListener(new ChangeListener<Boolean>() {

					@Override
					public void changed(ObservableValue<? extends Boolean> arg0, Boolean oldValue, Boolean newValue) {

						if (newValue && debug) {
							System.out.println("caseName");
							try {
								Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nIntakePane : caseName".getBytes(),
										StandardOpenOption.APPEND);
							} catch (IOException e) {
								e.printStackTrace();
							}
						} else {

							updateCaseStringField("name", caseName.getText());
							currentCase.setCaseName(caseName.getText());
						}

					}
				});
				caseBox.getChildren().add(caseName);

				caseBox.getChildren().add(new Label("Case Number"));
				caseNumber.focusedProperty().addListener(new ChangeListener<Boolean>() {

					@Override
					public void changed(ObservableValue<? extends Boolean> arg0, Boolean oldValue, Boolean newValue) {

						if (newValue && debug) {
							System.out.println("caseNumber");

							try {
								Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nIntakePane : caseNumber".getBytes(),
										StandardOpenOption.APPEND);
							} catch (IOException e) {
								e.printStackTrace();
							}
						} else {

							updateCaseStringField("caseNumber", caseNumber.getText());
							currentCase.setCaseNumber(caseNumber.getText());
						}

					}
				});
				caseBox.getChildren().add(caseNumber);

			}
			callerBox.getChildren().add(caseBox);

			Label callerLable = new Label("CALLER");
			callerLable.setStyle("-fx-font-size: 24; -font-weight: bold");
			callerLable.setUnderline(true);
			callerBox.getChildren().add(callerLable);
			VBox categoryBox = new VBox();
			{

				categoryBox.setSpacing(5);
				categoryBox.setPadding(new Insets(5));
				categoryBox.setAlignment(Pos.TOP_LEFT);

				/*
				 * 
				 */

				categoryBox.getChildren().add(new Label("First Name"));
				callerNameFirst.focusedProperty().addListener(new ChangeListener<Boolean>() {

					@Override
					public void changed(ObservableValue<? extends Boolean> arg0, Boolean oldValue, Boolean newValue) {

						if (newValue && debug) {
							System.out.println("callerNameFirst");
							try {
								Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nIntakePane : callerNameFirst".getBytes(),
										StandardOpenOption.APPEND);
							} catch (IOException e) {
								e.printStackTrace();
							}

						} else {

							checkCaller();
							updateContactStringField("nameFirst", callerNameFirst.getText(), currentCase.getCallerId());
							currentCase.getCaller().setName_first(callerNameFirst.getText());
						}

					}
				});
				categoryBox.getChildren().add(callerNameFirst);
				/*
				 * 
				 */
				categoryBox.getChildren().add(new Label("Last Name"));
				callerNameLast.focusedProperty().addListener(new ChangeListener<Boolean>() {

					@Override
					public void changed(ObservableValue<? extends Boolean> arg0, Boolean oldValue, Boolean newValue) {

						if (newValue && debug) {
							System.out.println("callerNameLast");
							try {
								Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nIntakePane : callerNameLast".getBytes(),
										StandardOpenOption.APPEND);
							} catch (IOException e) {
								e.printStackTrace();
							}
						} else {

							checkCaller();
							updateContactStringField("nameLast", callerNameLast.getText(), currentCase.getCallerId());
							currentCase.getCaller().setName_last(callerNameLast.getText());
						}

					}
				});
				categoryBox.getChildren().add(callerNameLast);
				/*
				 * 
				 */
				categoryBox.getChildren().add(new Label("Caller Phone Number"));
//				char[] chars = callerPhone.getText().toCharArray();
				callerPhone.focusedProperty().addListener(new ChangeListener<Boolean>() {

					@Override
					public void changed(ObservableValue<? extends Boolean> arg0, Boolean oldValue, Boolean newValue) {

						if (newValue && debug) {
							System.out.println("callerPhone");
							try {
								Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nIntakePane : callerPhone".getBytes(),
										StandardOpenOption.APPEND);
							} catch (IOException e) {
								e.printStackTrace();
							}
						} else if (newValue != oldValue) {
							System.out.println("HERE " + callerPhone.getText().length());
							checkCaller();
							updateContactStringField("phoneCell", callerPhone.getText(), currentCase.getCallerId());
							currentCase.getCaller().setPhone_cell(callerPhone.getText());
						}

					}
				});
				categoryBox.getChildren().add(callerPhone);
				/*
				 * 
				 */
				categoryBox.getChildren().add(new Label("Referred From"));
				callerReferredFrom.focusedProperty().addListener(new ChangeListener<Boolean>() {

					@Override
					public void changed(ObservableValue<? extends Boolean> arg0, Boolean oldValue, Boolean newValue) {

						if (newValue && debug) {
							System.out.println("callerReferredFrom");
							try {
								Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nIntakePane : callerReferredFrom".getBytes(),
										StandardOpenOption.APPEND);
							} catch (IOException e) {
								e.printStackTrace();
							}
						} else {

							checkCaller();
							updateContactStringField("referredFrom", callerReferredFrom.getText(), currentCase.getCallerId());
							currentCase.getCaller().setReferredFrom(callerReferredFrom.getText());
						}

					}
				});
				categoryBox.getChildren().add(callerReferredFrom);
				/*
				 * 
				 */
				categoryBox.getChildren().add(new Label("Date / Time"));
				callerDate.focusedProperty().addListener(new ChangeListener<Boolean>() {

					@Override
					public void changed(ObservableValue<? extends Boolean> arg0, Boolean oldValue, Boolean newValue) {

						if (newValue && debug) {
							System.out.println("callerDate");
							try {
								Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nIntakePane : callerDate".getBytes(),
										StandardOpenOption.APPEND);
							} catch (IOException e) {
								e.printStackTrace();
							}
						} else {

							updateCaseDateField("callerDate", callerDate.getValue());
							currentCase.setCallerDate(callerDate.getValue());
						}

					}
				});
				categoryBox.getChildren().add(callerDate);

				if (currentCase.getCallerTime() == null) {
					currentCase.setCallerTime(LocalTime.now());
				}
				categoryBox.getChildren().add(callerTimeString);
			}

			callerBox.getChildren().add(categoryBox);

		}

		boxes.add(clientBox);
		{
			{
				HBox topBox = new HBox();
				topBox.setSpacing(10);
				topBox.setAlignment(Pos.CENTER_LEFT);

				Label label = new Label("CLIENT");
				label.setStyle("-fx-font-size: 24; -font-weight: bold");
				label.setUnderline(true);

				topBox.getChildren().add(label);
				sameAsCaller.setSelected(currentCase.isSameAsCaller());

				sameAsCaller.setOnAction(new EventHandler<ActionEvent>() {

					@Override
					public void handle(ActionEvent arg0) {
						if (sameAsCaller.isSelected()) {

							/*
							 * delete current client and id from case and server
							 */
							int removeId = currentCase.getClientId();
							Server.deleteContact(removeId, ConnectionResources.getConnection());

							/*
							 * change client name fields to match caller fields and rename case
							 */
							if (clientNameFirst.getText().equals("")) {
								clientNameFirst.setText(callerNameFirst.getText());
								currentCase.getClient().setName_first(clientNameFirst.getText());
							} else if (callerNameFirst.getText().equals("")) {
								callerNameFirst.setText(clientNameFirst.getText());
								currentCase.getCaller().setName_first(callerNameFirst.getText());
							}
							if (clientNameLast.getText().equals("")) {
								clientNameLast.setText(callerNameLast.getText());
								currentCase.getClient().setName_last(clientNameLast.getText());
							} else if (callerNameLast.getText().equals("")) {
								callerNameLast.setText(clientNameLast.getText());
								currentCase.getCaller().setName_last(callerNameLast.getText());
							}
							if (clientPhone.getText().equals("")) {
								clientPhone.setText(callerPhone.getText());
								currentCase.getClient().setPhone_cell(clientPhone.getText());
							} else if (callerPhone.getText().equals("")) {
								callerPhone.setText(clientPhone.getText());
								currentCase.getCaller().setPhone_cell(callerPhone.getText());
							}

							setCaseName();

							/*
							 * update case information to match new client
							 */
							currentCase.setClientId(currentCase.getCallerId());
							currentCase.setClient(Main.getContacts().get(currentCase.getClientId()));
							updateCaseBooleanField("sameAsCaller", true);
							updateCaseIntField("clientId", currentCase.getClientId());
							currentCase.setSameAsCaller(true);

						} else {
							/*
							 * create new contact and set new id as case client id
							 */
							currentCase.setClientId(
									Server.createNewContact(Main.getCurrentUser().getDefault_organization(), ConnectionResources.getConnection()));
							Server.updateCaseIntField(currentCase.get_id(), "clientId", currentCase.getClientId(),
									ConnectionResources.getConnection());

							Contact c = Server.getContactById(currentCase.getClientId(), ConnectionResources.getConnection());
							Main.addContact(currentCase.getClientId(), c);

							updateCaseBooleanField("sameAsCaller", false);
							currentCase.setSameAsCaller(false);
							currentCase.setClient(Main.getContacts().get(currentCase.getClientId()));

							currentCase.getClient().setName_first(clientNameFirst.getText());
							currentCase.getClient().setName_last(clientNameLast.getText());
							currentCase.getClient().setPhone_cell(clientPhone.getText());

						}

					}
				});
				topBox.getChildren().add(sameAsCaller);

				clientBox.getChildren().add(topBox);

			}

			VBox categoryBox = new VBox();
			{
				categoryBox.setSpacing(5);
				categoryBox.setPadding(new Insets(5));
				categoryBox.setAlignment(Pos.TOP_LEFT);

				clientEstate.setContentDisplay(ContentDisplay.LEFT);
				clientEstate.setOnAction(new EventHandler<ActionEvent>() {

					@Override
					public void handle(ActionEvent arg0) {
						setCaseName();
						updateCaseBooleanField("clientEstate", clientEstate.isSelected());
						currentCase.setClientEstate(clientEstate.isSelected());
					}
				});
				categoryBox.getChildren().add(clientEstate);

				categoryBox.getChildren().add(new Label("First Name"));
				clientNameFirst.focusedProperty().addListener(new ChangeListener<Boolean>() {

					@Override
					public void changed(ObservableValue<? extends Boolean> arg0, Boolean oldValue, Boolean newValue) {

						if (newValue && debug) {
							System.out.println("clientNameFirst");
							try {
								Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nIntakePane : clientNameFirst".getBytes(),
										StandardOpenOption.APPEND);
							} catch (IOException e) {
								e.printStackTrace();
							}
						} else {

							checkClient();
							updateContactStringField("nameFirst", clientNameFirst.getText(), currentCase.getClientId());
							setCaseName();
							currentCase.getClient().setName_first(clientNameFirst.getText());
						}

					}
				});
				categoryBox.getChildren().add(clientNameFirst);

				categoryBox.getChildren().add(new Label("Last Name"));
				clientNameLast.focusedProperty().addListener(new ChangeListener<Boolean>() {

					@Override
					public void changed(ObservableValue<? extends Boolean> arg0, Boolean oldValue, Boolean newValue) {

						if (newValue && debug) {
							System.out.println("clientNameLast");
							try {
								Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nIntakePane : clientNameLast".getBytes(),
										StandardOpenOption.APPEND);
							} catch (IOException e) {
								e.printStackTrace();
							}
						} else {

							checkClient();
							updateContactStringField("nameLast", clientNameLast.getText(), currentCase.getClientId());
							setCaseName();
							currentCase.getClient().setName_last(clientNameLast.getText());

						}

					}
				});
				categoryBox.getChildren().add(clientNameLast);

				categoryBox.getChildren().add(new Label("Address"));

				clientAddress.setMaxHeight(50);
				clientAddress.setWrapText(true);

				clientAddress.focusedProperty().addListener(new ChangeListener<Boolean>() {

					@Override
					public void changed(ObservableValue<? extends Boolean> arg0, Boolean oldValue, Boolean newValue) {

						if (newValue && debug) {
							System.out.println("clientAddress");
							try {
								Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nIntakePane : clientAddress".getBytes(),
										StandardOpenOption.APPEND);
							} catch (IOException e) {
								e.printStackTrace();
							}
						} else {

							checkClient();
							updateContactStringField("addressHome", clientAddress.getText(), currentCase.getClientId());
							currentCase.getClient().setAddress_home(clientAddress.getText());
						}

					}
				});
				categoryBox.getChildren().add(clientAddress);

				categoryBox.getChildren().add(new Label("Phone"));
				clientPhone.focusedProperty().addListener(new ChangeListener<Boolean>() {

					@Override
					public void changed(ObservableValue<? extends Boolean> arg0, Boolean oldValue, Boolean newValue) {

						if (newValue && debug) {
							System.out.println("clientPhone");
							try {
								Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nIntakePane : clientPhone".getBytes(),
										StandardOpenOption.APPEND);
							} catch (IOException e) {
								e.printStackTrace();
							}
						} else {

							checkClient();
							updateContactStringField("phoneCell", clientPhone.getText(), currentCase.getClientId());
							currentCase.getClient().setPhone_cell(clientPhone.getText());
						}

					}
				});
				categoryBox.getChildren().add(clientPhone);

				categoryBox.getChildren().add(new Label("Email"));
				clientEmail.focusedProperty().addListener(new ChangeListener<Boolean>() {

					@Override
					public void changed(ObservableValue<? extends Boolean> arg0, Boolean oldValue, Boolean newValue) {

						if (newValue && debug) {
							System.out.println("clientEmail");
							try {
								Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nIntakePane : clientEmail".getBytes(),
										StandardOpenOption.APPEND);
							} catch (IOException e) {
								e.printStackTrace();
							}
						} else {

							checkClient();
							updateContactStringField("emailPersonal", clientEmail.getText(), currentCase.getClientId());
							currentCase.getClient().setEmail_personal(clientEmail.getText());
						}

					}
				});
				categoryBox.getChildren().add(clientEmail);

				{
					categoryBox.getChildren().add(new Label("Date of Birth"));
					HBox dob = new HBox();
					dob.setAlignment(Pos.CENTER_LEFT);
					dob.setSpacing(5);
					dob.getChildren().add(clientDOBPicker);
					clientDOBClear.setOnAction(new EventHandler<ActionEvent>() {

						@Override
						public void handle(ActionEvent arg0) {
							clientDOBPicker.setValue(null);
							clientAgeLabel.setText("");
						}
					});
					dob.getChildren().add(clientDOBClear);
					categoryBox.getChildren().add(dob);
				}
				clientDOBPicker.setOnAction(new EventHandler<ActionEvent>() {

					@Override
					public void handle(ActionEvent arg0) {
						if (clientDOBPicker.getValue() != null) {
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
						checkClient();
						updateContactDateField("dateOfBirth", clientDOBPicker.getValue(), currentCase.getClientId());
						currentCase.getClient().setDate_of_birth(Date.valueOf(clientDOBPicker.getValue()));
					}
				});

				clientDOBPicker.focusedProperty().addListener(new ChangeListener<Boolean>() {

					@Override
					public void changed(ObservableValue<? extends Boolean> arg0, Boolean oldValue, Boolean newValue) {

						if (newValue && debug) {
							System.out.println("clientDOBPicker");
							try {
								Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nIntakePane : clientDOBPicker".getBytes(),
										StandardOpenOption.APPEND);
							} catch (IOException e) {
								e.printStackTrace();
							}
						} else {

							checkClient();
							updateContactDateField("dateOfBirth", clientDOBPicker.getValue(), currentCase.getClientId());
							if (clientDOBPicker.getValue() != null)
								currentCase.getClient().setDate_of_birth(Date.valueOf(clientDOBPicker.getValue()));
							else
								currentCase.getClient().setDate_of_birth(null);

						}

					}
				});

				categoryBox.getChildren().add(clientAgeLabel);

			}
			categoryBox.getChildren().add(new Label("Client Condition"));
			clientDeceased.setOnAction(new EventHandler<ActionEvent>() {

				@Override
				public void handle(ActionEvent arg0) {
					checkClient();
					updateContactBooleanField("isDeceased", clientDeceased.isSelected(), currentCase.getClientId());
					currentCase.getClient().setDeceased(clientDeceased.isSelected());

				}
			});
			categoryBox.getChildren().add(clientDeceased);

			clientCondition.setWrapText(true);
			clientCondition.setPrefWidth(TEXTAREAWIDTH);
			clientCondition.focusedProperty().addListener(new ChangeListener<Boolean>() {

				@Override
				public void changed(ObservableValue<? extends Boolean> arg0, Boolean oldValue, Boolean newValue) {

					if (newValue && debug) {
						System.out.println("clientCondition");
						try {
							Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nIntakePane : clientCondition".getBytes(),
									StandardOpenOption.APPEND);
						} catch (IOException e) {
							e.printStackTrace();
						}
					} else {

						checkClient();
						updateContactStringField("condition", clientCondition.getText(), currentCase.getClientId());
						currentCase.getClient().setCondition(clientCondition.getText());
					}

				}
			});
			categoryBox.getChildren().add(clientCondition);

			clientBox.getChildren().add(categoryBox);
		}

		boxes.add(incidentBox);

		{

			Label label = new Label("INCIDENT");
			label.setStyle("-fx-font-size: 24; -font-weight: bold");
			label.setUnderline(true);
			incidentBox.getChildren().add(label);

			VBox incidentVBox = new VBox();
			incidentVBox.setSpacing(5);
			incidentVBox.setPadding(new Insets(5));
			incidentVBox.setAlignment(Pos.TOP_LEFT);

			VBox updateBox = new VBox();
			updateBox.setSpacing(5);
			updateBox.setAlignment(Pos.TOP_LEFT);

			VBox summaryVBox = new VBox();
			summaryVBox.setSpacing(5);
			summaryVBox.setPadding(new Insets(5));
			summaryVBox.setAlignment(Pos.TOP_LEFT);

			HBox updatesBox = new HBox();
			updatesBox.setSpacing(5);
			updatesBox.setAlignment(Pos.TOP_LEFT);

			HBox columnBox = new HBox();
			columnBox.setSpacing(5);
			columnBox.setPadding(new Insets(5));
			columnBox.setAlignment(Pos.TOP_LEFT);

			VBox column1Box = new VBox();
			column1Box.setSpacing(5);
			column1Box.setPadding(new Insets(5));
			column1Box.setAlignment(Pos.TOP_LEFT);

			VBox column2Box = new VBox();
			column2Box.setSpacing(5);
			column2Box.setPadding(new Insets(5));
			column2Box.setAlignment(Pos.TOP_LEFT);

			/*
			 * Setup Column1Box
			 */
			{

				column1Box.getChildren().add(new Label("Practice Area"));

				column1Box.getChildren().add(incidentPracticeAreaBox);
				incidentPracticeAreaBox.setOnAction(new EventHandler<ActionEvent>() {

					@Override
					public void handle(ActionEvent arg0) {
						if (practiceAreaUpdate) {
							updateCaseIntField("casePracticeAreaId", incidentPracticeAreaBox.getValue().get_id());
							currentCase.setCasePracticeAreaId(incidentPracticeAreaBox.getValue().get_id());
						} else {
							practiceAreaUpdate = true;
						}
					}

				});

				column1Box.getChildren().add(new Label("Date Medical Negligence Occurred"));
				incidentDateMedNegOccurred.focusedProperty().addListener(new ChangeListener<Boolean>() {

					@Override
					public void changed(ObservableValue<? extends Boolean> arg0, Boolean oldValue, Boolean newValue) {

						if (newValue && debug) {
							System.out.println("incidentDateMedNegOccurred");
							try {
								Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nIntakePane : incidentDateMedNegOccurred".getBytes(),
										StandardOpenOption.APPEND);
							} catch (IOException e) {
								e.printStackTrace();
							}
						} else {

							checkIncident();
							updateIncidentDateField("incidentMedNegOccurred", incidentDateMedNegOccurred.getValue(), currentCase.getIncidentId());
							if (incidentDateMedNegOccurred.getValue() != null)
								currentCase.getIncident().setIncidentMedNegOccurred(Date.valueOf(incidentDateMedNegOccurred.getValue()));
							else
								currentCase.getIncident().setIncidentMedNegOccurred(null);
						}

					}
				});
				incidentDateMedNegOccurred.setOnAction(new EventHandler<ActionEvent>() {

					@Override
					public void handle(ActionEvent arg0) {
						checkIncident();
						updateIncidentDateField("incidentMedNegOccurred", incidentDateMedNegOccurred.getValue(), currentCase.getIncidentId());
						if (incidentDateMedNegOccurred.getValue() != null)
							currentCase.getIncident().setIncidentMedNegOccurred(Date.valueOf(incidentDateMedNegOccurred.getValue()));
						else
							currentCase.getIncident().setIncidentMedNegOccurred(null);
					}
				});
				column1Box.getChildren().add(incidentDateMedNegOccurred);

				{
					HBox dateHBox = new HBox();
					dateHBox.setAlignment(Pos.CENTER_LEFT);
					dateHBox.setSpacing(5);
					dateHBox.getChildren().add(incidentDateMedNegOccurred);
					incidentDateMedNegOccurredClear.setOnAction(new EventHandler<ActionEvent>() {

						@Override
						public void handle(ActionEvent arg0) {
							incidentDateMedNegOccurred.setValue(null);
						}
					});
					dateHBox.getChildren().add(incidentDateMedNegOccurredClear);
					column1Box.getChildren().add(dateHBox);
				}
				column1Box.getChildren().add(new Label("Date Medical Negligence Discovered"));
				incidentDateMedNegDiscovered.focusedProperty().addListener(new ChangeListener<Boolean>() {

					@Override
					public void changed(ObservableValue<? extends Boolean> arg0, Boolean oldValue, Boolean newValue) {

						if (newValue && debug) {
							System.out.println("incidentDateMedNegDiscovered");
							try {
								Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nIntakePane : incidentDateMedNegDiscovered".getBytes(),
										StandardOpenOption.APPEND);
							} catch (IOException e) {
								e.printStackTrace();
							}
						} else {

							checkIncident();
							updateIncidentDateField("incidentMedNegDiscovered", incidentDateMedNegDiscovered.getValue(), currentCase.getIncidentId());

							if (incidentDateMedNegDiscovered.getValue() != null)
								currentCase.getIncident().setDateMedNegDiscovered(Date.valueOf(incidentDateMedNegDiscovered.getValue()));
							else
								currentCase.getIncident().setDateMedNegDiscovered(null);
						}

					}
				});
				incidentDateMedNegDiscovered.setOnAction(new EventHandler<ActionEvent>() {

					@Override
					public void handle(ActionEvent arg0) {
						checkIncident();
						updateIncidentDateField("incidentMedNegDiscovered", incidentDateMedNegDiscovered.getValue(), currentCase.getIncidentId());
						if (incidentDateMedNegDiscovered.getValue() != null)
							currentCase.getIncident().setDateMedNegDiscovered(Date.valueOf(incidentDateMedNegDiscovered.getValue()));
						else
							currentCase.getIncident().setDateMedNegDiscovered(null);

					}
				});
				column1Box.getChildren().add(incidentDateMedNegDiscovered);
				{
					HBox dateHBox = new HBox();
					dateHBox.setAlignment(Pos.CENTER_LEFT);
					dateHBox.setSpacing(5);
					dateHBox.getChildren().add(incidentDateMedNegDiscovered);
					incidentDateMedNegDiscoveredClear.setOnAction(new EventHandler<ActionEvent>() {

						@Override
						public void handle(ActionEvent arg0) {
							incidentDateMedNegDiscovered.setValue(null);

						}
					});
					dateHBox.getChildren().add(incidentDateMedNegDiscoveredClear);
					column1Box.getChildren().add(dateHBox);
				}

				column1Box.getChildren().add(new Label("Date Of Injury"));

				incidentDateOfInjury.focusedProperty().addListener(new ChangeListener<Boolean>() {

					@Override
					public void changed(ObservableValue<? extends Boolean> arg0, Boolean oldValue, Boolean newValue) {

						if (newValue && debug) {
							System.out.println("incidentDateOfInjury");
							try {
								Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nIntakePane : incidentDateOfInjury".getBytes(),
										StandardOpenOption.APPEND);
							} catch (IOException e) {
								e.printStackTrace();
							}
						} else {

							checkIncident();
							updateIncidentDateField("incidentDateOfInjury", incidentDateOfInjury.getValue(), currentCase.getIncidentId());
							if (incidentDateOfInjury.getValue() != null)
								currentCase.getIncident().setDateOfInjury(Date.valueOf(incidentDateOfInjury.getValue()));
							else
								currentCase.getIncident().setDateOfInjury(null);
						}

					}
				});
				incidentDateOfInjury.setOnAction(new EventHandler<ActionEvent>() {

					@Override
					public void handle(ActionEvent arg0) {
						checkIncident();
						updateIncidentDateField("incidentDateOfInjury", incidentDateOfInjury.getValue(), currentCase.getIncidentId());
						if (incidentDateOfInjury.getValue() != null)
							currentCase.getIncident().setDateOfInjury(Date.valueOf(incidentDateOfInjury.getValue()));
						else
							currentCase.getIncident().setDateOfInjury(null);
					}
				});

				column1Box.getChildren().add(incidentDateOfInjury);
				{
					HBox dateHBox = new HBox();
					dateHBox.setAlignment(Pos.CENTER_LEFT);
					dateHBox.setSpacing(5);
					dateHBox.getChildren().add(incidentDateOfInjury);
					incidentDateOfInjuryClear.setOnAction(new EventHandler<ActionEvent>() {

						@Override
						public void handle(ActionEvent arg0) {
							incidentDateOfInjury.setValue(null);

						}
					});
					dateHBox.getChildren().add(incidentDateOfInjuryClear);
					column1Box.getChildren().add(dateHBox);
				}

				column1Box.getChildren().add(new Label("Statute of Limitations"));

				incidentDateStatuteOfLimitations.focusedProperty().addListener(new ChangeListener<Boolean>() {

					@Override
					public void changed(ObservableValue<? extends Boolean> arg0, Boolean oldValue, Boolean newValue) {

						if (newValue && debug) {
							System.out.println("incidentDateStatuteOfLimitations");
							try {
								Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nIntakePane : incidentDateStatureOfLimitations".getBytes(),
										StandardOpenOption.APPEND);
							} catch (IOException e) {
								e.printStackTrace();
							}
						} else {

							checkIncident();
							updateIncidentDateField("incidentStatuteOfLimitations", incidentDateStatuteOfLimitations.getValue(),
									currentCase.getIncidentId());
							if (incidentDateStatuteOfLimitations.getValue() != null)
								currentCase.getIncident().setIncidentStatuteOfLimitations(Date.valueOf(incidentDateStatuteOfLimitations.getValue()));
							else
								currentCase.getIncident().setIncidentStatuteOfLimitations(null);
						}

					}
				});
				incidentDateStatuteOfLimitations.setOnAction(new EventHandler<ActionEvent>() {

					@Override
					public void handle(ActionEvent arg0) {
						checkIncident();
						updateIncidentDateField("incidentStatuteOfLimitations", incidentDateStatuteOfLimitations.getValue(),
								currentCase.getIncidentId());
						if (incidentDateStatuteOfLimitations.getValue() != null)
							currentCase.getIncident().setIncidentStatuteOfLimitations(Date.valueOf(incidentDateStatuteOfLimitations.getValue()));
						else
							currentCase.getIncident().setIncidentStatuteOfLimitations(null);

					}
				});

				column1Box.getChildren().add(incidentDateStatuteOfLimitations);
				{
					HBox dateHBox = new HBox();
					dateHBox.setAlignment(Pos.CENTER_LEFT);
					dateHBox.setSpacing(5);
					dateHBox.getChildren().add(incidentDateStatuteOfLimitations);
					incidentDateStatuteOfLimitationsClear.setOnAction(new EventHandler<ActionEvent>() {

						@Override
						public void handle(ActionEvent arg0) {
							incidentDateStatuteOfLimitations.setValue(null);

						}
					});
					dateHBox.getChildren().add(incidentDateStatuteOfLimitationsClear);
					column1Box.getChildren().add(dateHBox);
				}

				column1Box.getChildren().add(new Label("Potential Defendants"));

				incidentPotentialDefendants.focusedProperty().addListener(new ChangeListener<Boolean>() {

					@Override
					public void changed(ObservableValue<? extends Boolean> arg0, Boolean oldValue, Boolean newValue) {

						if (newValue && debug) {
							System.out.println("incidentPotentialDefendants");
							try {
								Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nIntakePane : incidentPotentialDefendants".getBytes(),
										StandardOpenOption.APPEND);
							} catch (IOException e) {
								e.printStackTrace();
							}
						} else {

							checkIncident();
							updateIncidentStringField("incidentPotentialDefendants", incidentPotentialDefendants.getText());
							currentCase.getIncident().setPotentialDefendants(incidentPotentialDefendants.getText());
						}

					}
				});

				column1Box.getChildren().add(incidentPotentialDefendants);

				column1Box.getChildren().add(new Label("Facilities Involved"));

				incidentFacilitiesInvolved.focusedProperty().addListener(new ChangeListener<Boolean>() {

					@Override
					public void changed(ObservableValue<? extends Boolean> arg0, Boolean oldValue, Boolean newValue) {

						if (newValue && debug) {
							System.out.println("incidentFacilitiesInvolved");
							try {
								Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nIntakePane : incidentFacilitiesInvolved".getBytes(),
										StandardOpenOption.APPEND);
							} catch (IOException e) {
								e.printStackTrace();
							}
						} else {

							checkIncident();
							updateIncidentStringField("incidentFacilitiesInvolved", incidentFacilitiesInvolved.getText());
							currentCase.getIncident().setFacilitiesInvolved(incidentFacilitiesInvolved.getText());
						}

					}
				});
				column1Box.getChildren().add(incidentFacilitiesInvolved);

				Task<Integer> t = new Task<Integer>() {

					@Override
					protected Integer call() throws Exception {
						if (currentCase.getFacilities() != null)
							for (Facility f : currentCase.getFacilities()) {
								involved.setText(f.getName() + "\n" + involved.getText());
							}
						return null;
					}
				};
				Thread r = new Thread(t);
				Platform.runLater(r);

				column1Box.getChildren().add(involved);

				Button selectFacilitiesButton = new Button("Select Facilities");
				selectFacilitiesButton.setOnAction(new EventHandler<ActionEvent>() {

					@Override
					public void handle(ActionEvent arg0) {
						involved.setText("");
						new MultiPicker(1, Server.getFacilitiesByCaseId(currentCase.get_id(), ConnectionResources.getConnection()), ip);
					}
				});

				column1Box.getChildren().add(selectFacilitiesButton);

				incidentHaveMedRecordsButton.setOnAction(new EventHandler<ActionEvent>() {

					@Override
					public void handle(ActionEvent arg0) {
						checkIncident();
						updateIncidentBooleanField("incidentMedRecsInHand", incidentHaveMedRecordsButton.isSelected());
						currentCase.getIncident().setIncidentMedRecsInHand(incidentHaveMedRecordsButton.isSelected());

					}
				});

				column1Box.getChildren().add(incidentHaveMedRecordsButton);

			}
			columnBox.getChildren().add(column1Box);

			/*
			 * Setup column2Box
			 */
			{
				column2Box.getChildren().add(new Label("Description"));

				incidentDescription.focusedProperty().addListener(new ChangeListener<Boolean>() {

					@Override
					public void changed(ObservableValue<? extends Boolean> arg0, Boolean oldValue, Boolean newValue) {

						if (newValue && debug) {
							System.out.println("incidentDescription");
							try {
								Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nIntakePane : incidentDescription".getBytes(),
										StandardOpenOption.APPEND);
							} catch (IOException e) {
								e.printStackTrace();
							}
						} else {

							checkIncident();
							updateIncidentStringField("incidentDescription", incidentDescription.getText());
							currentCase.getIncident().setIncidentDescription(incidentDescription.getText());

						}

					}
				});

				column2Box.getChildren().add(incidentDescription);

				column2Box.getChildren().add(new Label("Case Status"));

				incidentCaseStatus.focusedProperty().addListener(new ChangeListener<Boolean>() {

					@Override
					public void changed(ObservableValue<? extends Boolean> arg0, Boolean oldValue, Boolean newValue) {

						if (newValue && debug) {
							System.out.println("incidentCaseStatus");
							try {
								Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nIntakePane : incidentCaseStatus".getBytes(),
										StandardOpenOption.APPEND);
							} catch (IOException e) {
								e.printStackTrace();
							}
						} else {

							checkIncident();
							updateIncidentStringField("incidentCaseStatus", incidentCaseStatus.getText());
							currentCase.getIncident().setIncidentCaseStatus(incidentCaseStatus.getText());

						}

					}
				});

				column2Box.getChildren().add(incidentCaseStatus);

				column2Box.getChildren().add(new Label("Updates"));
				/*
				 * Setup Submission box
				 */
				{

					updatesBox.getChildren().add(incidentSubmissionField);

					incidentSubmissionField.setPrefWidth(425);
					incidentSubmissionField.setPromptText("Type updates here");
					incidentSubmissionField.setOnKeyReleased(new EventHandler<KeyEvent>() {

						@Override
						public void handle(KeyEvent arg0) {
							if (arg0.getCode() == KeyCode.ENTER && !incidentSubmissionField.getText().equals("")) {
								if (!incidentSubmissionField.getText().equals("")) {
									submitEntry(incidentUpdates, incidentSubmissionField);
									checkIncident();
									updateIncidentStringField("incidentUpdate", incidentUpdates.getText());
									currentCase.getIncident().setIncidentUpdates(incidentUpdates.getText());
								}
							}
						}

					});

					incidentSubmit.setOnAction(new EventHandler<ActionEvent>() {

						@Override
						public void handle(ActionEvent arg0) {
							if (!incidentSubmissionField.getText().equals("")) {
								submitEntry(incidentUpdates, incidentSubmissionField);
								updateIncidentStringField("incidentUpdate", incidentUpdates.getText());
								currentCase.getIncident().setIncidentUpdates(incidentUpdates.getText());
							}
						}
					});

					updatesBox.getChildren().add(incidentSubmit);
					column2Box.getChildren().add(updatesBox);

					incidentUpdates.setWrapText(true);
					incidentUpdates.setPrefWidth(TEXTAREAWIDTH);

					incidentUpdates.focusedProperty().addListener(new ChangeListener<Boolean>() {

						@Override
						public void changed(ObservableValue<? extends Boolean> arg0, Boolean oldValue, Boolean newValue) {

							if (newValue && debug) {
								System.out.println("incidentUpdates");
								try {
									Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nIntakePane : incidentUpdates".getBytes(),
											StandardOpenOption.APPEND);
								} catch (IOException e) {
									e.printStackTrace();
								}
							} else {

								checkIncident();
								updateIncidentStringField("incidentUpdate", incidentUpdates.getText());
								currentCase.getIncident().setIncidentUpdates(incidentUpdates.getText());
							}

						}
					});
					updateBox.getChildren().add(incidentUpdates);

				}
				column2Box.getChildren().add(updateBox);
				column2Box.getChildren().add(new Label());

				VBox officeVBox = new VBox();
				officeVBox.setSpacing(5);
				officeVBox.setPadding(new Insets(10));
				officeVBox.setAlignment(Pos.TOP_LEFT);

				HBox officeBox = new HBox();
				officeBox.setAlignment(Pos.CENTER_LEFT);
				officeBox.setSpacing(5);

				officeBox.getChildren().add(new Label("Resonsible Attorney:"));

				officeResponsibleAttorney.setOnAction(new EventHandler<ActionEvent>() {

					@Override
					public void handle(ActionEvent arg0) {

						if (responsibleAttorneyUpdate) {
							updateCaseIntField("officeResponsibleAttorneyId",
									officeResponsibleAttorney.getSelectionModel().getSelectedItem().get_id());
							currentCase.setOfficeResponsibleAttorneyId(officeResponsibleAttorney.getSelectionModel().getSelectedItem().get_id());
						} else {
							responsibleAttorneyUpdate = true;
						}
					}
				});

				officeBox.getChildren().add(officeResponsibleAttorney);

				officeBox.getChildren().add(new Label(" | "));

				officeBox.getChildren().add(new Label("Case Code"));

				officePrinterCode.focusedProperty().addListener(new ChangeListener<Boolean>() {

					@Override
					public void changed(ObservableValue<? extends Boolean> arg0, Boolean oldValue, Boolean newValue) {

						if (newValue && debug) {
							System.out.println("officePrinterCode");
							try {
								Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nIntakePane : officePrinterCode".getBytes(),
										StandardOpenOption.APPEND);
							} catch (IOException e) {
								e.printStackTrace();
							}
						} else {

							checkIncident();
							updateCaseStringField("officePrinterCode", officePrinterCode.getText());
							currentCase.setOfficePrinterCode(officePrinterCode.getText());
						}

					}
				});

				officeBox.getChildren().add(officePrinterCode);

				officeVBox.setBorder(
						new Border(new BorderStroke(Color.BLACK, BorderStrokeStyle.SOLID, new CornerRadii(3), new BorderWidths(1), new Insets(-5))));
				officeVBox.getChildren().add(new Label("Office"));
				officeVBox.getChildren().add(officeBox);

				column2Box.getChildren().add(officeVBox);
			}
			summaryVBox.getChildren().add(new Label("Summary"));
			incidentSummary.setWrapText(true);
			incidentSummary.setPrefWidth(TEXTAREAWIDTH * 2);
			incidentSummary.setPrefHeight(400);
			incidentSummary.setFont(new Font(18));

			incidentSummary.focusedProperty().addListener(new ChangeListener<Boolean>() {

				@Override
				public void changed(ObservableValue<? extends Boolean> arg0, Boolean oldValue, Boolean newValue) {

					if (newValue && debug) {
						System.out.println("incidentSummary");
						try {
							Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nIntakePane : incidentSummary".getBytes(),
									StandardOpenOption.APPEND);
						} catch (IOException e) {
							e.printStackTrace();
						}
					} else {

						checkIncident();
						updateIncidentStringField("incidentSummary", incidentSummary.getText());
						currentCase.getIncident().setIncidentSummary(incidentSummary.getText());
					}

				}
			});

			summaryVBox.getChildren().add(incidentSummary);

			columnBox.getChildren().add(column2Box);
			incidentVBox.getChildren().add(columnBox);
			incidentVBox.getChildren().add(summaryVBox);

			incidentBox.getChildren().add(incidentVBox);
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

				HBox questionBox = new HBox();
				questionBox.setSpacing(5);
				questionBox.setAlignment(Pos.TOP_LEFT);

				followUpSubmissionField.setPrefWidth(425);
				followUpSubmissionField.setPromptText("Type new question here");
				followUpSubmissionField.setOnKeyReleased(new EventHandler<KeyEvent>() {

					@Override
					public void handle(KeyEvent arg0) {
						if (arg0.getCode() == KeyCode.ENTER && !followUpSubmissionField.getText().equals("")) {
							{
								submitEntry(followupQuestionsForPatient, followUpSubmissionField);
								updateCaseStringField("followUpQuestionsForPatient", followupQuestionsForPatient.getText());
								currentCase.setFollowUpQuestionsForPatient(followupQuestionsForPatient.getText());
							}
						}
					}

				});

				questionBox.getChildren().add(followUpSubmissionField);

				followUpSubmit.setOnAction(new EventHandler<ActionEvent>() {

					@Override
					public void handle(ActionEvent arg0) {
						if (!followUpSubmissionField.getText().equals("")) {
							submitEntry(followupQuestionsForPatient, followUpSubmissionField);
							updateCaseStringField("followUpQuestionsForPatient", followupQuestionsForPatient.getText());
							currentCase.setFollowUpQuestionsForPatient(followupQuestionsForPatient.getText());
						}
					}
				});
				questionBox.getChildren().add(followUpSubmit);
				categoryBox.getChildren().add(questionBox);

				followupQuestionsForPatient.focusedProperty().addListener(new ChangeListener<Boolean>() {

					@Override
					public void changed(ObservableValue<? extends Boolean> arg0, Boolean oldValue, Boolean newValue) {

						if (newValue && debug) {
							System.out.println("followupQuestionsForPatient");
							try {
								Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nIntakePane : followupQuestionsForPatient".getBytes(),
										StandardOpenOption.APPEND);
							} catch (IOException e) {
								e.printStackTrace();
							}
						} else {

							updateCaseStringField("followUpQuestionsForPatient", followupQuestionsForPatient.getText());
							currentCase.setFollowUpQuestionsForPatient(followupQuestionsForPatient.getText());
						}

					}
				});

				categoryBox.getChildren().add(followupQuestionsForPatient);

				followupMeetWithClient.setOnAction(new EventHandler<ActionEvent>() {

					@Override
					public void handle(ActionEvent arg0) {
						updateCaseBooleanField("followUpMeetWithClient", followupMeetWithClient.isSelected());
						currentCase.setFollowUpMeetingWithClient(followupMeetWithClient.isSelected());

					}
				});
				categoryBox.getChildren().add(followupMeetWithClient);

				followupNurseReview.setOnAction(new EventHandler<ActionEvent>() {

					@Override
					public void handle(ActionEvent arg0) {
						updateCaseBooleanField("followUpNurseReview", followupNurseReview.isSelected());
						currentCase.setFollowUpNurseReview(followupNurseReview.isSelected());

					}
				});
				categoryBox.getChildren().add(followupNurseReview);

				followupExpertReview.setOnAction(new EventHandler<ActionEvent>() {

					@Override
					public void handle(ActionEvent arg0) {
						updateCaseBooleanField("followUpExpertReview", followupExpertReview.isSelected());
						currentCase.setFollowUpExpertReview(followupExpertReview.isSelected());

					}
				});
				categoryBox.getChildren().add(followupExpertReview);

			}
			followupBox.getChildren().add(categoryBox);
			/*
			 * 
			 */

			if (currentCase.isFeeAgreementSigned()) {
				signed.setText("Fee Agreement Signed");
				signed.setSelected(true);
			} else {
				signed.setText("Fee Agreement Unsigned");
				signed.setSelected(false);
			}

			signed.setOnAction(new EventHandler<ActionEvent>() {

				@Override
				public void handle(ActionEvent arg0) {
					if (signed.isSelected()) {
						signed.setText("Fee Agreement Signed");
						currentCase.setFeeAgreementSigned(true);
						updateCaseDateField("dateFeeAgreementSigned", LocalDate.now());
					} else {
						signed.setText("Fee Agreement Unsigned");
						currentCase.setFeeAgreementSigned(false);
						updateCaseDateField("dateFeeAgreementSigned", null);
					}
					updateCaseBooleanField("feeAgreementSigned", currentCase.isFeeAgreementSigned());
				}
			});
			followupBox.getChildren().add(signed);
			/*
			 * 
			 */
		}

		boxes.add(receivedBox);
		{
			Label label = new Label("Documents and Media");
			label.setStyle("-fx-font-size: 24; -font-weight: bold");
			label.setUnderline(true);
			receivedBox.getChildren().add(label);

			VBox categoryBox = new VBox();
			{
				categoryBox.setSpacing(5);
				categoryBox.setPadding(new Insets(5));
				categoryBox.setAlignment(Pos.TOP_LEFT);

				categoryBox.getChildren().add(new Label("Description of received media"));

				HBox questionBox = new HBox();
				questionBox.setSpacing(5);
				questionBox.setAlignment(Pos.TOP_LEFT);

				receivedSubmissionField.setPrefWidth(425);
				receivedSubmissionField.setPromptText("Enter information here...");
				receivedSubmissionField.setOnKeyReleased(new EventHandler<KeyEvent>() {

					@Override
					public void handle(KeyEvent arg0) {
						if (arg0.getCode() == KeyCode.ENTER && !receivedSubmissionField.getText().equals("")) {
							{
								submitEntry(receivedUpdates, receivedSubmissionField);
								updateCaseStringField("receivedUpdates", receivedUpdates.getText());
								currentCase.setReceivedUpdates(receivedUpdates.getText());
							}
						}
					}

				});

				questionBox.getChildren().add(receivedUpdates);

				receivedSubmit.setOnAction(new EventHandler<ActionEvent>() {

					@Override
					public void handle(ActionEvent arg0) {
						if (!receivedUpdates.getText().equals("")) {
							submitEntry(receivedUpdates, receivedSubmissionField);
							updateCaseStringField("receivedUpdates", receivedUpdates.getText());
							currentCase.setReceivedUpdates(receivedUpdates.getText());
						}
					}
				});
				questionBox.getChildren().add(receivedSubmissionField);
				questionBox.getChildren().add(receivedSubmit);
				categoryBox.getChildren().add(questionBox);

				receivedUpdates.focusedProperty().addListener(new ChangeListener<Boolean>() {

					@Override
					public void changed(ObservableValue<? extends Boolean> arg0, Boolean oldValue, Boolean newValue) {

						if (newValue && debug) {
							System.out.println("receivedUpdates");
							try {
								Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nIntakePane : receivedUpdates".getBytes(),
										StandardOpenOption.APPEND);
							} catch (IOException e) {
								e.printStackTrace();
							}
						} else {

							updateCaseStringField("receivedUpdates", receivedUpdates.getText());
							currentCase.setReceivedUpdates(receivedUpdates.getText());
						}

					}
				});

				categoryBox.getChildren().add(receivedUpdates);

			}

			receivedBox.getChildren().add(categoryBox);
			/*
			* 
			*/
		}

		if (currentCase != null && currentCase.getCaseStatusId() == 13) {

			checklist.add(acceptedChronologyButton);
			checklist.add(acceptedSupportiveMedicalLiteratureButton);
			checklist.add(acceptedConsultantExpertSearchButton);
			checklist.add(acceptedTestifyingExperSearchButton);

			Button transfer = new Button("Transfer Case");
			transfer.setDisable(acceptedTransferCheck());
			IntakePane intake = this;
			transfer.setOnAction(new EventHandler<ActionEvent>() {

				@Override
				public void handle(ActionEvent arg0) {
					Case cse = getCase();
					cse.setPotential(false);
					cse.setAccepted(true);
					cse.setTransferred(true);
					cse.setDenied(false);
					cse.setClosed(false);
					cse.setCaseStatusString("Active");
					Task<Integer> t = new Task<Integer>() {

						@Override
						protected Integer call() throws Exception {
							Server.updateCaseIntField(cse.get_id(), "caseStatusId", 17, ConnectionResources.getConnection());
							return null;
						}
					};
					new Thread(t).start();

					Main.getCases().get(getCase().get_id()).setCaseStatusId(17);
					Main.getController().changeCenter(new IntakePane(Main.getCurrentCase(), false));
					Main.getController().changeTop(new TopBar(17, intake));

				}
			});

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

					acceptedChronologyButton.setOnAction(new EventHandler<ActionEvent>() {

						@Override
						public void handle(ActionEvent arg0) {
							transfer.setDisable(acceptedTransferCheck());

							updateCaseBooleanField("acceptedChronology", acceptedChronologyButton.isSelected());
							currentCase.setAcceptedChronology(acceptedChronologyButton.isSelected());

						}

					});
					a.getChildren().add(acceptedChronologyButton);
					a.getChildren().add(new Label(":   Chronology"));
					categoryBox.getChildren().add(a);

					HBox b = new HBox();
					b.setSpacing(10);

					acceptedConsultantExpertSearchButton.setOnAction(new EventHandler<ActionEvent>() {

						@Override
						public void handle(ActionEvent arg0) {
							transfer.setDisable(acceptedTransferCheck());
							updateCaseBooleanField("acceptedConsultantExpertSearch", acceptedConsultantExpertSearchButton.isSelected());
							currentCase.setAcceptedConsultantExpertSearch(acceptedConsultantExpertSearchButton.isSelected());

						}
					});
					b.getChildren().add(acceptedConsultantExpertSearchButton);
					b.getChildren().add(new Label(":   Consultant Expert Search"));
					categoryBox.getChildren().add(b);

					HBox c = new HBox();
					c.setSpacing(10);

					acceptedTestifyingExperSearchButton.setOnAction(new EventHandler<ActionEvent>() {

						@Override
						public void handle(ActionEvent arg0) {
							transfer.setDisable(acceptedTransferCheck());
							updateCaseBooleanField("acceptedTestifyingExpertSearch", acceptedTestifyingExperSearchButton.isSelected());
							currentCase.setAcceptedTestifyingExpertSearch(acceptedTestifyingExperSearchButton.isSelected());

						}
					});
					c.getChildren().add(acceptedTestifyingExperSearchButton);
					c.getChildren().add(new Label(":   Testifying Expert Search"));
					categoryBox.getChildren().add(c);

					HBox d = new HBox();
					d.setSpacing(10);

					acceptedSupportiveMedicalLiteratureButton.setOnAction(new EventHandler<ActionEvent>() {

						@Override
						public void handle(ActionEvent arg0) {
							transfer.setDisable(acceptedTransferCheck());
							updateCaseBooleanField("acceptedMedicalLiterature", acceptedSupportiveMedicalLiteratureButton.isSelected());
							currentCase.setAcceptedSupportiveMedicalLiterature(acceptedSupportiveMedicalLiteratureButton.isSelected());

						}
					});
					d.getChildren().add(acceptedSupportiveMedicalLiteratureButton);
					d.getChildren().add(new Label(":   Supportive Medical Literature"));
					categoryBox.getChildren().add(d);

					HBox e = new HBox();
					e.setSpacing(10);

					acceptedDetail.focusedProperty().addListener(new ChangeListener<Boolean>() {

						@Override
						public void changed(ObservableValue<? extends Boolean> arg0, Boolean oldValue, Boolean newValue) {

							if (newValue && debug) {
								System.out.println("acceptedDetail");
								try {
									Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nIntakePane : acceptedDetail".getBytes(),
											StandardOpenOption.APPEND);
								} catch (IOException e) {
									e.printStackTrace();
								}
							} else {

								updateCaseStringField("acceptedDetail", acceptedDetail.getText());
								currentCase.setAcceptedDetail(acceptedDetail.getText());
							}

						}
					});
					e.getChildren().add(new Label("Detail:"));
					e.getChildren().add(acceptedDetail);
					categoryBox.getChildren().add(e);
				}

				acceptedBox.getChildren().add(categoryBox);

				acceptedBox.getChildren().add(transfer);
			}
		}
		if (currentCase != null && currentCase.getCaseStatusId() == 14) {
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

					denialChronologyButton.setOnAction(new EventHandler<ActionEvent>() {

						@Override
						public void handle(ActionEvent arg0) {
							updateCaseBooleanField("deniedChronology", denialChronologyButton.isSelected());
							currentCase.setDeniedChronology(denialChronologyButton.isSelected());

						}
					});
					a.getChildren().add(denialChronologyButton);
					a.getChildren().add(new Label(":   Chronology"));
					categoryBox.getChildren().add(a);

					categoryBox.getChildren().add(new Label("Details"));

					denialDetail.focusedProperty().addListener(new ChangeListener<Boolean>() {

						@Override
						public void changed(ObservableValue<? extends Boolean> arg0, Boolean oldValue, Boolean newValue) {

							if (newValue && debug) {
								System.out.println("denialDetail");
								try {
									Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nIntakePane : denialDetail".getBytes(),
											StandardOpenOption.APPEND);
								} catch (IOException e) {
									e.printStackTrace();
								}
							} else {

								updateCaseStringField("deniedDetail", denialDetail.getText());
								currentCase.setDeniedDetails(denialDetail.getText());
							}

						}
					});
					categoryBox.getChildren().add(denialDetail);
				}
				denialBox.getChildren().add(categoryBox);
			}
		}

		for (VBox box : boxes) {

			box.setPadding(new Insets(SPACING));
			box.setSpacing(SPACING);
			box.setBackground(new Background(new BackgroundFill(Color.LIGHTBLUE, new CornerRadii(5), new Insets(1))));
			box.setBorder(new Border(new BorderStroke(Color.BLACK, BorderStrokeStyle.SOLID, new CornerRadii(5), new BorderWidths(2))));
			this.getChildren().add(box);
		}

		for (User attorney : Main.getUsers().values()) {
			if (attorney.isIs_attorney())
				officeResponsibleAttorney.getItems().add(attorney);
		}

		Main.setIntake(this);
	}

	private void populateData() {

		caseName.setText(currentCase.getCaseName());

		caseNumber.setText(currentCase.getCaseNumber());

		callerNameFirst.setText(currentCase.getCallerNameFirst());

		callerNameLast.setText(currentCase.getCallerNameLast());

		callerPhone.setText(currentCase.getCallerPhone());

		callerReferredFrom.setText(currentCase.getCallerReferredFrom());

		if (currentCase.getCallerDate() == null) {
			callerDate.setValue(LocalDate.now());
		} else
			callerDate.setValue(currentCase.getCallerDate());

		String ampm = " AM";
		callerTime = currentCase.getCallerTime();
		int hour = callerTime.getHour();
		if (hour > 12) {
			ampm = " PM";
			hour -= 12;
		}
		callerTimeString.setText(hour + ":" + callerTime.getMinute() / 10 + (callerTime.getMinute() % 10) + ampm);

		clientEstate.setSelected(currentCase.isClientEstate());
		clientNameFirst.setText(currentCase.getClientNameFirst());
		clientNameLast.setText(currentCase.getClientNameLast());
		clientAddress.setText(currentCase.getClientAddress());
		clientPhone.setText(currentCase.getClientPhone());
		clientEmail.setText(currentCase.getClientEmail());
		clientDOB = currentCase.getClientDOB();
		clientDOBPicker.setValue(currentCase.getClientDOB());
		clientDeceased.setSelected(currentCase.isClientDeceased());
		clientCondition.setText(currentCase.getClientCondition());

		incidentPracticeAreaBox.getSelectionModel().select(currentCase.getCasePracticeArea());
		officeResponsibleAttorney.getSelectionModel().select(currentCase.getOfficeResponsibleAttorney());
		officePrinterCode.setText(currentCase.getOfficePrinterCode());
		incidentDateMedNegOccurred.setValue(currentCase.getIncidentMedNegOccurred());
		incidentDateMedNegDiscovered.setValue(currentCase.getIncidentMedNegDiscovered());
		incidentDateOfInjury.setValue(currentCase.getIncidentDateOfInjury());
		incidentDateStatuteOfLimitations.setValue(currentCase.getIncidentStatuteOfLimitations());
		incidentPotentialDefendants.setText(currentCase.getPotentialDefendants());
		incidentFacilitiesInvolved.setText(currentCase.getIncidentFacilitiesInvolved());
		incidentHaveMedRecordsButton.setSelected(currentCase.isIncidentMedRecsInHand());
		incidentDescription.setText(currentCase.getIncidentDescription());
		incidentCaseStatus.setText(currentCase.getIncidentCaseStatus());
		incidentSummary.setText(currentCase.getIncidentSummary());
		incidentUpdates.setText(currentCase.getIncidentUpdates());

		followupQuestionsForPatient.setText(currentCase.getFollowUpQuestionsForPatient());
		followupMeetWithClient.setSelected(currentCase.isFollowUpMeetingWithClient());
		followupNurseReview.setSelected(currentCase.isFollowUpNurseReview());
		followupExpertReview.setSelected(currentCase.isFollowUpDoctorReview());
		followupTransferredButton.setSelected(currentCase.isTransferred());

		receivedUpdates.setText(currentCase.getReceivedUpdates());

		acceptedChronology = currentCase.isAcceptedChronology();
		acceptedChronologyButton.setSelected(currentCase.isAcceptedChronology());
		acceptedConsultantExpertSearch = currentCase.isAcceptedConsultantExpertSearch();
		acceptedConsultantExpertSearchButton.setSelected(currentCase.isAcceptedConsultantExpertSearch());
		acceptedTestifyingExperSearch = currentCase.isAcceptedTestifyingExpertSearch();
		acceptedTestifyingExperSearchButton.setSelected(currentCase.isAcceptedTestifyingExpertSearch());
		acceptedSupportiveMedicalLiterature = currentCase.isAcceptedSupportiveMedicalLiterature();
		acceptedSupportiveMedicalLiteratureButton.setSelected(currentCase.isAcceptedSupportiveMedicalLiterature());
		followupTransferredButton.setSelected(currentCase.isTransferred());
		acceptedDetail.setText(currentCase.getAcceptedDetail());

		denialChronology = currentCase.isDeniedChronology();
		denialChronologyButton.setSelected(currentCase.isDeniedChronology());
		denialDetail.setText(currentCase.getDeniedDetails());

	}

	private void checkClient() {
		Task<Integer> t = new Task<Integer>() {

			@Override
			protected Integer call() throws Exception {

				if (currentCase.getClientId() == 0) {
					currentCase.setClientId(
							Server.createNewContact(Main.getCurrentUser().getDefault_organization(), ConnectionResources.getConnection()));
					Server.updateCaseIntField(currentCase.get_id(), "clientId", currentCase.getClientId(), ConnectionResources.getConnection());
				}
				return null;
			}
		};
		new Thread(t).start();
	}

	private void checkCaller() {
		Task<Integer> t = new Task<Integer>() {

			@Override
			protected Integer call() throws Exception {
				if (currentCase.getCallerId() == 0) {
					currentCase.setCallerId(
							Server.createNewContact(Main.getCurrentUser().getDefault_organization(), ConnectionResources.getConnection()));
					Server.updateCaseIntField(currentCase.get_id(), "callerId", currentCase.getCallerId(), ConnectionResources.getConnection());
				}
				return null;
			}
		};
		new Thread(t).start();

	}

	private void checkIncident() {
		Task<Integer> t = new Task<Integer>() {

			@Override
			protected Integer call() throws Exception {

				if (currentCase.getIncidentId() == 0) {
					currentCase.setIncidentId(
							Server.createNewIncident(currentCase.getCaseOrganizationId(), currentCase.get_id(), ConnectionResources.getConnection()));
					Server.updateCaseIntField(currentCase.get_id(), "incidentId", currentCase.getIncidentId(), ConnectionResources.getConnection());

					Main.addIncident(currentCase.getIncidentId(),
							Server.getIncidentById(currentCase.getIncidentId(), ConnectionResources.getConnection()));
					currentCase.setIncident(Main.getIncidents().get(currentCase.getIncidentId()));
				}
				return null;
			}
		};
		new Thread(t).start();
	}

	private void updateContactStringField(String field, String value, int contactId) {
		Task<Integer> t = new Task<Integer>() {

			@Override
			protected Integer call() throws Exception {

				Server.updateContactStringField(contactId, field, value, ConnectionResources.getConnection());
				return null;
			}
		};
		new Thread(t).start();
	}

	private void updateContactDateField(String field, LocalDate value, int contactId) {

		Task<Integer> t = new Task<Integer>() {

			@Override
			protected Integer call() throws Exception {

//				updateContactDateField("dateOfBirth", clientDOBPicker.getValue(), currentCase.getClientId());

				Date date = null;
				if (value != null) {
					date = Date.valueOf(value);
				}
				Server.updateContactDateField(contactId, field, date, ConnectionResources.getConnection());
				return null;
			}
		};
		new Thread(t).start();
	}

	private void updateContactBooleanField(String field, boolean value, int contactId) {
		Task<Integer> t = new Task<Integer>() {

			@Override
			protected Integer call() throws Exception {

				Server.updateContactBooleanField(contactId, field, value, ConnectionResources.getConnection());
				return null;
			}
		};
		new Thread(t).start();
	}

	private void updateCaseDateField(String field, LocalDate value) {

		Task<Integer> t = new Task<Integer>() {

			@Override
			protected Integer call() throws Exception {
				Date date = null;
				if (value != null) {
					date = Date.valueOf(value);
				}
				Server.updateCaseDateField(currentCase.get_id(), field, date, ConnectionResources.getConnection());
				return null;
			}
		};
		new Thread(t).start();
	}

	private void updateCaseBooleanField(String field, boolean value) {

		Task<Integer> t = new Task<Integer>() {

			@Override
			protected Integer call() throws Exception {
				Server.updateCaseBooelanField(currentCase.get_id(), field, value, ConnectionResources.getConnection());
				return null;
			}
		};
		new Thread(t).start();
	}

	private void updateCaseIntField(String field, int value) {
		Task<Integer> t = new Task<Integer>() {

			@Override
			protected Integer call() throws Exception {
				Server.updateCaseIntField(currentCase.get_id(), field, value, ConnectionResources.getConnection());
				return null;
			}
		};
		new Thread(t).start();

	}

	private void updateCaseStringField(String field, String value) {
		Task<Integer> t = new Task<Integer>() {

			@Override
			protected Integer call() throws Exception {
				Server.updateCaseStringField(currentCase.get_id(), field, value, ConnectionResources.getConnection());
				return null;
			}
		};
		new Thread(t).start();

	}

	private void updateIncidentDateField(String field, LocalDate value, int id) {

		Task<Integer> t = new Task<Integer>() {

			@Override
			protected Integer call() throws Exception {
				Date date = null;
				if (value != null) {
					date = Date.valueOf(value);
				}
				Server.updateIncidentDateField(id, field, date, ConnectionResources.getConnection());
				return null;
			}
		};
		new Thread(t).start();
	}

	private void updateIncidentStringField(String field, String value) {
		Task<Integer> t = new Task<Integer>() {

			@Override
			protected Integer call() throws Exception {
				Server.updateIncidentStringField(currentCase.getIncidentId(), field, value, ConnectionResources.getConnection());
				return null;
			}
		};
		new Thread(t).start();

	}

	private void updateIncidentBooleanField(String field, boolean value) {
		Task<Integer> t = new Task<Integer>() {

			@Override
			protected Integer call() throws Exception {
				Server.updateIncidentBooleanField(currentCase.getIncidentId(), field, value, ConnectionResources.getConnection());
				return null;
			}
		};
		new Thread(t).start();

	}

	public TextField getCaseName() {
		return caseName;
	}

	public void setCaseName(TextField caseName) {
		this.caseName = caseName;
	}

	public TextField getCaseNumber() {
		return caseNumber;
	}

	public void setCaseNumber(TextField caseNumber) {
		this.caseNumber = caseNumber;
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

	public TextField getCallerPhone() {
		return callerPhone;
	}

	public void setCallerPhone(TextField callerPhone) {
		this.callerPhone = callerPhone;
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

	public RadioButton getClientEstate() {
		return clientEstate;
	}

	public void setClientEstate(RadioButton clientEstate) {
		this.clientEstate = clientEstate;
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

	public TextArea getClientAddress() {
		return clientAddress;
	}

	public void setClientAddress(TextArea clientAddress) {
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
		clientDOBPicker.setValue(clientDOB);
	}

	public DatePicker getClientDOBPicker() {
		return clientDOBPicker;
	}

	public void setClientDOBPicker(DatePicker clientDOBPicker) {
		this.clientDOBPicker = clientDOBPicker;
	}

	public RadioButton getClientDeceased() {
		return clientDeceased;
	}

	public void setClientDeceased(RadioButton clientDeceased) {
		this.clientDeceased = clientDeceased;
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

	public ChoiceBox<PracticeArea> getIncidentPracticeAreaBox() {
		return incidentPracticeAreaBox;
	}

	public void setIncidentPracticeAreaBox(ChoiceBox<PracticeArea> incidentPracticeAreaBox) {
		this.incidentPracticeAreaBox = incidentPracticeAreaBox;
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

	public DatePicker getIncidentDateOfInjury() {
		return incidentDateOfInjury;
	}

	public void setIncidentDateOfInjury(DatePicker incidentDateOfInjury) {
		this.incidentDateOfInjury = incidentDateOfInjury;
	}

	public DatePicker getIncidentDateStatuteOfLimitations() {
		return incidentDateStatuteOfLimitations;
	}

	public void setIncidentDateStatuteOfLimitations(DatePicker incidentDateStatuteOfLimitations) {
		this.incidentDateStatuteOfLimitations = incidentDateStatuteOfLimitations;
	}

	public DatePicker getIncidentTortNoticeDeadline() {
		return incidentTortNoticeDeadline;
	}

	public void setIncidentTortNoticeDeadline(DatePicker incidentTortNoticeDeadline) {
		this.incidentTortNoticeDeadline = incidentTortNoticeDeadline;
	}

	public DatePicker getIncidentDiscoveryDeadline() {
		return incidentDiscoveryDeadline;
	}

	public void setIncidentDiscoveryDeadline(DatePicker incidentDiscoveryDeadline) {
		this.incidentDiscoveryDeadline = incidentDiscoveryDeadline;
	}

	public TextField getIncidentPotentialDefendants() {
		return incidentPotentialDefendants;
	}

	public void setIncidentPotentialDefendants(TextField incidentPotentialDefendants) {
		this.incidentPotentialDefendants = incidentPotentialDefendants;
	}

	public TextField getIncidentFacilitiesInvolved() {
		return incidentFacilitiesInvolved;
	}

	public void setIncidentFacilitiesInvolved(TextField incidentFacilitiesInvolved) {
		this.incidentFacilitiesInvolved = incidentFacilitiesInvolved;
	}

	public TextField getIncidentDescription() {
		return incidentDescription;
	}

	public void setIncidentDescription(TextField incidentDescription) {
		this.incidentDescription = incidentDescription;
	}

	public TextField getIncidentCaseStatus() {
		return incidentCaseStatus;
	}

	public void setIncidentCaseStatus(TextField incidentCaseStatus) {
		this.incidentCaseStatus = incidentCaseStatus;
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
		return incidentHaveMedRecordsButton.isSelected();
	}

	public void setIncidentHaveMedRecords(boolean incidentHaveMedRecords) {
		this.incidentHaveMedRecordsButton.setSelected(incidentHaveMedRecords);
	}

	public TextArea getIncidentSummary() {
		return incidentSummary;
	}

	public void setIncidentSummary(TextArea incidentSummary) {
		this.incidentSummary = incidentSummary;
	}

	public TextArea getIncidentUpdates() {
		return incidentUpdates;
	}

	public void setIncidentUpdates(TextArea incidentUpdates) {
		this.incidentUpdates = incidentUpdates;
	}

	public ChoiceBox<User> getOfficeResponsibleAttorney() {
		return officeResponsibleAttorney;
	}

	public void setOfficeResponsibleAttorney(ChoiceBox<User> officeResponsibleAttorney) {
		this.officeResponsibleAttorney = officeResponsibleAttorney;
	}

	public TextArea getFollowupQuestionsForPatient() {
		return followupQuestionsForPatient;
	}

	public void setFollowupQuestionsForPatient(TextArea followupQuestionsForPatient) {
		this.followupQuestionsForPatient = followupQuestionsForPatient;
	}

	public boolean isFollowupMeetWithClient() {
		return followupMeetWithClient.isSelected();
	}

	public void setFollowupMeetWithClient(boolean followupMeetWithClient) {
		this.followupMeetWithClient.setSelected(followupMeetWithClient);
	}

	public boolean isFollowupNurseReview() {
		return followupNurseReview.isSelected();
	}

	public void setFollowupNurseReview(boolean followupNurseReview) {
		this.followupNurseReview.setSelected(followupNurseReview);
	}

	public boolean isFollowupDoctorReview() {
		return followupExpertReview.isSelected();
	}

	public void setFollowupExpertReview(boolean followupExpertReview) {
		this.followupExpertReview.setSelected(followupExpertReview);
	}

	public RadioButton getFollowupTransferredButton() {
		return followupTransferredButton;
	}

	public void setFollowupTransferredButton(RadioButton followupTransferredButton) {
		this.followupTransferredButton = followupTransferredButton;
	}

	public ToggleButton getSigned() {
		return signed;
	}

	public void setSigned(ToggleButton signed) {
		this.signed = signed;
	}

	public boolean isAcceptedChronology() {
		return acceptedChronology;
	}

	public void setAcceptedChronology(boolean acceptedChronology) {
		this.acceptedChronology = acceptedChronology;
	}

	public RadioButton getAcceptedChronologyButton() {
		return acceptedChronologyButton;
	}

	public RadioButton getAcceptedSupportiveMedicalLiteratureButton() {
		return acceptedSupportiveMedicalLiteratureButton;
	}

	public void setAcceptedSupportiveMedicalLiteratureButton(RadioButton acceptedSupportiveMedicalLiteratureButton) {
		this.acceptedSupportiveMedicalLiteratureButton = acceptedSupportiveMedicalLiteratureButton;
	}

	public RadioButton getAcceptedConsultantExpertSearchButton() {
		return acceptedConsultantExpertSearchButton;
	}

	public void setAcceptedConsultantExpertSearchButton(RadioButton acceptedConsultantExpertSearchButton) {
		this.acceptedConsultantExpertSearchButton = acceptedConsultantExpertSearchButton;
	}

	public RadioButton getAcceptedTestifyingExperSearchButton() {
		return acceptedTestifyingExperSearchButton;
	}

	public void setAcceptedTestifyingExperSearchButton(RadioButton acceptedTestifyingExperSearchButton) {
		this.acceptedTestifyingExperSearchButton = acceptedTestifyingExperSearchButton;
	}

	public void setAcceptedChronologyButton(RadioButton acceptedChronologyButton) {
		this.acceptedChronologyButton = acceptedChronologyButton;
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

	public RadioButton getDenialChronologyButton() {
		return denialChronologyButton;
	}

	public void setDenialChronologyButton(RadioButton denialChronologyButton) {
		this.denialChronologyButton = denialChronologyButton;
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

	public Case getCase() {
		Main.setCase(currentCase);
		return currentCase;
	}

	public void setAccepted(boolean state) {
		currentCase.setDenied(false);
		currentCase.setAccepted(state);
		String location = Main.getFileLocation() + System.getProperty("file.separator") + "Accepted" + System.getProperty("file.separator")
				+ currentCase.getClientNameLast() + ", " + currentCase.getClientNameFirst();
		currentCase.setFileName(location);
	}

	public void setRejected(boolean state) {
		currentCase.setAccepted(false);
		currentCase.setDenied(state);
		String location = Main.getFileLocation() + System.getProperty("file.separator") + "Rejected" + System.getProperty("file.separator")
				+ currentCase.getClientNameLast() + ", " + currentCase.getClientNameFirst();
		currentCase.setFileName(location);
	}

	public void setFileName(String name) {
		currentCase.setFileName(name);
	}

	public void setPrefix(String pre) {
		currentCase.setPrefix(pre);
	}

	private void submitEntry(TextArea area, TextField field) {
		String ampm = " AM";
		LocalTime entryTime = LocalTime.now();
		int hour = entryTime.getHour();
		if (hour > 12) {
			ampm = " PM";
			hour -= 12;
		}
		String time = hour + ":" + entryTime.getMinute() / 10 + (entryTime.getMinute() % 10) + ampm;

		String s = Main.getCurrentUser().getNameFull() + " | " + time + " | " + LocalDate.now().toString() + "\n   -- " + field.getText() + "\n\n"
				+ area.getText();
		field.setText("");
		area.setText(s);

	}

	public void setNotification(String s) {
		clientNotification.setText(s);
	}

	public boolean isNewCase() {
		return isNewCase;
	}

	public void setNewCase(boolean isNewCase) {
		this.isNewCase = isNewCase;
	}

	private void setCaseName() {
		if (clientEstate.isSelected()) {
			caseName.setText(clientNameLast.getText() + ", " + clientNameFirst.getText() + " Estate of");
		} else
			caseName.setText(clientNameLast.getText() + ", " + clientNameFirst.getText());
		updateCaseStringField("name", caseName.getText());
		currentCase.setCaseName(caseName.getText());
	}

	public CheckBox getSameAsCaller() {
		return sameAsCaller;
	}

	public void setSameAsCaller(CheckBox sameAsCaller) {
		this.sameAsCaller = sameAsCaller;
	}

	public TextField getOfficePrinterCode() {
		return officePrinterCode;
	}

	public void setOfficePrinterCode(TextField officePrinterCode) {
		this.officePrinterCode = officePrinterCode;
	}

	public void changePracticeArea(PracticeArea pa) {
		practiceAreaUpdate = false;
		incidentPracticeAreaBox.getSelectionModel().select(pa);
	}

	public void changeResponsibleAttorney(User attorney) {
		responsibleAttorneyUpdate = false;
		officeResponsibleAttorney.getSelectionModel().select(attorney);
	}

	private boolean acceptedTransferCheck() {
		acceptedTransferEnable = true;
		for (RadioButton rb : checklist)
			if (!rb.isSelected())
				acceptedTransferEnable = false;
		return !acceptedTransferEnable;
	}

	public void updateFacilities() {
		for (Facility f : currentCase.getFacilities()) {
			involved.setText(f.getName() + ", " + involved.getText());
		}
	}
}
