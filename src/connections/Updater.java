package connections;

import static com.hivemq.client.mqtt.MqttGlobalPublishFilter.ALL;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.nio.CharBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.sql.Date;
import java.util.UUID;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;

import GUIElements.PotentialsBubbles;
import GUIElements.PotentialsList;
import GUIElements.TopBar;
import application.Main;
import dataStructures.Case;
import dataStructures.Contact;
import dataStructures.Facility;
import dataStructures.Incident;
import dataStructures.Organization;
import dataStructures.PracticeArea;
import dataStructures.Provider;
import dataStructures.Status;
import dataStructures.User;
import javafx.application.Platform;
import javafx.concurrent.Task;

public class Updater {

	final String host = "800f7c58ba184ccab4164e90696320cd.s2.eu.hivemq.cloud";
	final String username = "ShaleMQTT";
	final String password = "Bb}RCt1/ev\"Azbl4P'io~%df!PI<Kp(o";
	final String clienID = "ShaleClient" + UUID.randomUUID();
	final String topic = "Shale/coms/updates";
	final Mqtt5BlockingClient client;
	private boolean debug = false;

	public Updater() {

		if (Main.isGlobalDebug())
			debug = true;

		// create an MQTT client
//		client = MqttClient.builder().useMqttVersion5().identifier(clienID).serverHost(host).serverPort(8883).sslWithDefaultConfig().buildBlocking();
		client = MqttClient.builder().useMqttVersion5().serverHost(host).serverPort(8883).sslWithDefaultConfig().buildBlocking();

		// connect to HiveMQ Cloud with TLS and username/pw
		client.connectWith().simpleAuth().username(username).password(UTF_8.encode(password)).applySimpleAuth().send();

		if (debug) {
			System.out.println("Client Name: " + client.getConfig().getClientIdentifier());
			System.out.println("Connected successfully");

			try {
				Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nUpdater: Connected Successfully()".getBytes(), StandardOpenOption.APPEND);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		// subscribe to the topic "my/test/topic"
		client.subscribeWith().topicFilter(topic).send();

		// set a callback that is called when a message is received (using the async API
		// style)

		/* ***************************************************/
		/* FORMAT: */
		/* UserId#UpdateCode#ObjectId#Field#Value */
		/* ***************************************************/
		/* KEY for UpdateCode (object type): */
		/*
		 * /* 0 = Organization /* 1 = Facility /* 2 = Provider /* 3 = Contact /* 4 = PracticeArea
		 * /* 5 = Status /* 6 = Incident /* 7 = User /* 8 = Case /*
		 **************************************************/

		client.toAsync().publishes(ALL, publish ->
		{

			Task<Integer> t = new Task<Integer>() {
				@Override
				protected Integer call() throws Exception {
					CharBuffer s = UTF_8.decode(publish.getPayload().get());
					String output = "";
					String positionDef = "";
					int userId = 0;
					int objectTypeId = 0;
					int objectId = 0;
					String field = "";
					int position = 0;
					if (debug)
						System.out.println("MQTT SERVER MESSAGE:");
					for (char c : s.array()) {
						if (c != '#') {

							output += c;
						} else {
							switch (position) {
							case 0:
								positionDef = "User ID: ";
								userId = Integer.valueOf(output);
								break;
							case 1:
								positionDef = "Save Type: ";
								objectTypeId = Integer.valueOf(output);
								break;
							case 2:
								positionDef = "Object ID: ";
								objectId = Integer.valueOf(output);
								break;
							case 3:
								positionDef = "Field: ";
								field = output;
								break;
							}
							if (debug)
								System.out.print(positionDef);
							if (debug)
								System.out.println(output);
							output = "";
							position++;
						}
					}

					if (userId != Main.getCurrentUser().get_id()) {

						if (debug) {
							System.out.println("UPDATE FROM ANOTHER USER ->> MAKE A CHANGE");
							try {
								Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nUpdate from another user -> Make a change".getBytes(),
										StandardOpenOption.APPEND);
							} catch (IOException e) {
								e.printStackTrace();
							}
						}

						final int objTypeId = objectTypeId;
						final int objId = objectId;
						final String fld = field;

						/* ***************************************************/
						/* KEY for objectTypeId: */
						/* 0 = Organization */
						/* 1 = Facility */
						/* 2 = Provider */
						/* 3 = Contact */
						/* 4 = PracticeArea */
						/* 5 = Status */
						/* 6 = Incident */
						/* 7 = User */
						/* 8 = Case */
						/* **************************************************/

//						Platform.runLater(new Runnable() {
//
//							@Override
//							public void run() {
						switch (Integer.valueOf(objTypeId)) {
						case 0: // Organization
							if (debug) {
								System.out.println("Organization");
								try {
									Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nUpdater : Organization".getBytes(),
											StandardOpenOption.APPEND);
								} catch (IOException e) {
									e.printStackTrace();
								}
							}
							Organization o = Server.getOrganizationById(objId, ConnectionResources.getConnection());
							if (Main.getOrganizations().containsKey(objId)) {// Organization already exists in Main
								updateOrganization(objId, fld, o);
							} else {// new Organization pull from server
								Main.getOrganizations().put(o.get_id(), o);
							}
							break;
						case 1:// Facility
							if (debug) {
								System.out.println("Facility");
								try {
									Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nUpdater : Facility".getBytes(),
											StandardOpenOption.APPEND);
								} catch (IOException e) {
									e.printStackTrace();
								}
							}

							Facility f = Server.getFacilityById(objId, ConnectionResources.getConnection());
							if (Main.getFacilities().containsKey(objId)) {// Facility already exists in Main
								updateFacility(objId, fld, f);
							} else {// new Facility pull from server
								Main.getFacilities().put(f.get_id(), f);
							}
							break;
						case 2: // Provider
							if (debug) {
								System.out.println("Provider");
								try {
									Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nUpdater : Provider".getBytes(),
											StandardOpenOption.APPEND);
								} catch (IOException e) {
									e.printStackTrace();
								}
							}

							Provider p = Server.getProviderById(objId, ConnectionResources.getConnection());
							if (Main.getFacilities().containsKey(objId)) {// Provider already exists in Main
								updateProvider(objId, fld, p);
							} else {// new Provider pull from server
								Main.getProviders().put(p.get_id(), p);
							}
							break;
						case 3: // Contact
							if (debug) {
								System.out.println("Contact");
								try {
									Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nUpdater : Contact".getBytes(),
											StandardOpenOption.APPEND);
								} catch (IOException e) {
									e.printStackTrace();
								}
							}

							Contact c = Server.getContactById(objId, ConnectionResources.getConnection());
							if (Main.getContacts().containsKey(objId)) {// Contact already exists in Main
								updateContact(objId, fld, c);
							} else {// new Contact pull from server
								Main.getContacts().put(c.get_id(), c);
							}
							break;
						case 4: // PracticeArea
							if (debug) {
								System.out.println("PracticeArea");
								try {
									Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nUpdater : PracticeArea".getBytes(),
											StandardOpenOption.APPEND);
								} catch (IOException e) {
									e.printStackTrace();
								}
							}

							PracticeArea pa = Server.getPracticeAreaById(objId, ConnectionResources.getConnection());
							if (Main.getPracticeAreas().containsKey(objId)) {// PracticeArea already exists in Main
								updatePracticeArea(objId, fld, pa);
							} else {// new PracticeArea pull from server
								Main.getPracticeAreas().put(pa.get_id(), pa);
							}
							break;
						case 5: // Status
							if (debug) {
								System.out.println("Status");
								try {
									Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nUpdater : Status".getBytes(),
											StandardOpenOption.APPEND);
								} catch (IOException e) {
									e.printStackTrace();
								}
							}

							Status sc = Server.getStatusChoiceById(objId, ConnectionResources.getConnection());
							if (Main.getStatusChoices().containsKey(objId)) {// StatusChoice already exists in Main
								updateStatusChoice(objId, fld, sc);
							} else {// new StatusChoice pull from server
								Main.getStatusChoices().put(sc.get_id(), sc);
							}
							break;
						case 6: // Incident
							if (debug) {
								System.out.println("Incident");
								try {
									Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nUpdater : Incident".getBytes(),
											StandardOpenOption.APPEND);
								} catch (IOException e) {
									e.printStackTrace();
								}
							}

							Incident i = Server.getIncidentById(objId, ConnectionResources.getConnection());
							if (Main.getIncidents().containsKey(objId)) {// StatusChoice already exists in Main
								updateIncident(objId, fld, i);
							} else {// new StatusChoice pull from server
								Main.getIncidents().put(i.getId(), i);
							}
							break;
						case 7: // User
							if (debug) {
								System.out.println("User");
								try {
									Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nUpdater : User".getBytes(), StandardOpenOption.APPEND);
								} catch (IOException e) {
									e.printStackTrace();
								}
							}

							User u = Server.getUserById(objId, ConnectionResources.getConnection());
							if (Main.getUsers().containsKey(objId)) {// User already exists in Main
								updateUser(objId, fld, u);
							} else {// new User pull from server
								Main.getUsers().put(u.get_id(), u);
							}
							break;
						case 8: // Case
							if (debug) {
								System.out.println("Case");
								try {
									Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nUpdater : Case".getBytes(), StandardOpenOption.APPEND);
								} catch (IOException e) {
									e.printStackTrace();
								}
							}
							if (fld.equals("deleteCase")) {// remove case from main.getcases
								Main.getCases().remove(objId);
								updateCenter();
							} else if (Main.getCases().containsKey(objId)) {// Case already exists in Main
								Case cse = Server.getCaseById(objId, ConnectionResources.getConnection());
								if (Main.isGlobalDebug())
									System.out.println("Existing case...");
								updateCase(objId, fld, cse);
							} else {// new Case pull from server
								if (Main.isGlobalDebug())
									System.out.println("New case...");
								Case cse = Server.getCaseById(objId, ConnectionResources.getConnection());
								Main.addCase(cse.get_id(), cse);
								if (Main.getController().getCenterClass().equals(PotentialsBubbles.class)) {
									updateCenter();
								}
							}
							break;
						default:
							throw new IllegalArgumentException("Unexpected value: " + objTypeId);
						}
						if (debug) {
							System.out.println("OUTPUT FROM MQTT SERVER: " + userId + " : " + objectTypeId + " : " + objectId + " : " + field);
							try {
								Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nUpdater : Organization".getBytes(),
										StandardOpenOption.APPEND);
							} catch (IOException e) {
								e.printStackTrace();
							}
						}

					} else if (debug)
						System.out.println("SAME AS CURENT USER ->> DO NOT CHANGE");

					return null;
				}
			};
			new Thread(t).start();
		});

	}

	public void disconnect() {
		if (debug) {
			System.out.println("Disconnecting...");
			try {
				Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nUpdater : Disconnecting...".getBytes(), StandardOpenOption.APPEND);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		client.disconnect();
		if (debug) {
			System.out.println("Disconnected.");
			try {
				Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nUpdater : Disconnected".getBytes(), StandardOpenOption.APPEND);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public Mqtt5BlockingClient getClient() {
		return client;
	}

	private void updateOrganization(int id, String field, Organization source) {
		switch (field) {
		case "id":
			Main.getOrganizations().get(id).set_id(source.get_id());
			break;
		case "name":
			Main.getOrganizations().get(id).setName(source.getName());
			break;
		case "description":
			Main.getOrganizations().get(id).setDescription(source.getDescription());
			break;
		case "is_deleted":
			Main.getOrganizations().get(id).setIs_deleted(source.isIs_deleted());
			break;
		case "creator_user_id":
			Main.getOrganizations().get(id).setCreator_user_id(source.getCreator_user_id());
			break;
		}
	}

	private void updateFacility(int id, String field, Facility source) {

		// TODO update intake facilities involved once changed to use facility type
		// instead of string

		switch (field) {
		case "id":
			Main.getFacilities().get(id).set_id(source.get_id());
			break;
		case "name":
			Main.getFacilities().get(id).setName(source.getName());
			break;
		case "description":
			Main.getFacilities().get(id).setDescription(source.getDescription());
			break;
		case "is_deleted":
			Main.getFacilities().get(id).setDeleted(source.isDeleted());
			break;
		case "organization_id":
			Main.getFacilities().get(id).setOrganization_id(source.getOrganization_id());
			break;

		}
	}

	private void updateProvider(int id, String field, Provider source) {

		// TODO update intake providers involved once changed to use facility type
		// instead of string

		switch (field) {
		case "id":
			Main.getProviders().get(id).set_id(source.get_id());
			break;
		case "name":
			Main.getProviders().get(id).setName(source.getName());
			break;
		case "description":
			Main.getProviders().get(id).setDescription(source.getDescription());
			break;
		case "is_deleted":
			Main.getProviders().get(id).setDeleted(source.isDeleted());
			break;
		case "organization_id":
			Main.getProviders().get(id).setOrganization_id(source.getOrganization_id());
			break;

		}
	}

	private void updateContact(int id, String field, Contact source) {

		boolean client = false;
		boolean caller = false;

		if (Main.getCurrentIntake().getCase().getClientId() == source.get_id()) {
			client = true;
		}
		if (Main.getCurrentIntake().getCase().getCallerId() == source.get_id()) {
			caller = true;
		}

		switch (field) {
		case "id":
			Main.getContacts().get(id).set_id(source.get_id());
			break;
		case "dateOfBirth":
			Main.getContacts().get(id).setDate_of_birth(source.getDate_of_birth());
			if (client) {
				Main.getCurrentIntake().getClientDOBPicker().setValue(source.getDate_of_birth().toLocalDate());
			}
			break;
		case "description":
			Main.getContacts().get(id).setDescription(source.getDescription());
			break;
		case "condition":
			Main.getContacts().get(id).setCondition(source.getCondition());
			if (client) {
				Main.getCurrentIntake().getClientCondition().setText(source.getCondition());
			}
			break;
		case "nameFirst":
			Main.getContacts().get(id).setName_first(source.getName_first());
			if (client) {
				Main.getCurrentIntake().getClientNameFirst().setText(source.getName_first());
			}
			if (caller) {
				Main.getCurrentIntake().getCallerNameFirst().setText(source.getName_first());
			}
			break;
		case "nameLast":
			Main.getContacts().get(id).setName_last(source.getName_last());
			if (client) {
				Main.getCurrentIntake().getClientNameLast().setText(source.getName_last());
			}
			if (caller) {
				Main.getCurrentIntake().getCallerNameLast().setText(source.getName_last());
			}
			break;
		case "nameWork":
			Main.getContacts().get(id).setName_work(source.getName_work());
			break;
		case "phoneCell":
			Main.getContacts().get(id).setPhone_cell(source.getPhone_cell());
			if (client) {
				Main.getCurrentIntake().getClientPhone().setText(source.getPhone_cell());
			}
			if (caller) {
				Main.getCurrentIntake().getCallerPhone().setText(source.getPhone_cell());
			}
			break;
		case "phoneHome":
			Main.getContacts().get(id).setPhone_home(source.getPhone_home());
			break;
		case "phoneWork":
			Main.getContacts().get(id).setPhone_work(source.getPhone_work());
			break;
		case "addressHome":
			Main.getContacts().get(id).setAddress_home(source.getAddress_home());
			if (client) {
				Main.getCurrentIntake().getClientAddress().setText(source.getAddress_home());
			}
			break;
		case "addressWork":
			Main.getContacts().get(id).setAddress_work(source.getAddress_work());
			break;
		case "addressOther":
			Main.getContacts().get(id).setAddress_other(source.getAddress_other());
			break;
		case "emailPersonal":
			Main.getContacts().get(id).setEmail_personal(source.getEmail_personal());
			if (client) {
				Main.getCurrentIntake().getClientEmail().setText(source.getEmail_personal());
			}
			break;
		case "emailWork":
			Main.getContacts().get(id).setEmail_work(source.getEmail_work());
			break;
		case "emailOther":
			Main.getContacts().get(id).setEmail_other(source.getEmail_other());
			break;
		case "isClient":
			Main.getContacts().get(id).setIs_client(source.getIs_client());
			break;
		case "isExpert":
			Main.getContacts().get(id).setIs_expert(source.getIs_expert());
			break;
		case "isDeleted":
			Main.getContacts().get(id).setIs_deleted(source.isIs_deleted());
			break;
		case "organizationId":
			Main.getContacts().get(id).setOrganization_id(source.getOrganization_id());
			break;
		case "imageVersion":
			Main.getContacts().get(id).setImageVersion(source.getImageVersion());
			break;
		case "referredFrom":
			Main.getContacts().get(id).setReferredFrom(source.getReferredFrom());
			break;
		case "isDeceased":
			Main.getContacts().get(id).setDeceased(source.isDeceased());
			if (client) {
				Main.getCurrentIntake().getClientDeceased().setSelected(source.isDeceased());
			}
			break;
		}

	}

	private void updatePracticeArea(int id, String field, PracticeArea source) {

		switch (field) {
		case "id":
			Main.getPracticeAreas().get(id).set_id(source.get_id());
			break;
		case "practice_area":
			Main.getPracticeAreas().get(id).setPractice_area(source.getPracticeArea());
			break;
		case "is_deleted":
			Main.getPracticeAreas().get(id).setIs_deleted(source.isIs_deleted());
			break;
		case "organization_id":
			Main.getPracticeAreas().get(id).setOrganization_id(source.getOrganization_id());
			break;
		}
	}

	private void updateStatusChoice(int id, String field, Status source) {

		switch (field) {
		case "id":
			Main.getStatusChoices().get(id).set_id(source.get_id());
			break;
		case "status":
			Main.getStatusChoices().get(id).setStatus(source.getStatus());
			break;
		case "for_case":
			Main.getStatusChoices().get(id).setFor_case(source.isFor_case());
			break;
		case "is_deleted":
			Main.getStatusChoices().get(id).setDeleted(source.isDeleted());
			break;
		case "organization_id":
			Main.getStatusChoices().get(id).setOrganization_id(source.getOrganization_id());
			break;
		}
	}

	private void updateIncident(int id, String field, Incident source) {
		boolean change = false;
		if (Main.getCurrentIntake().getCase().getIncidentId() == source.getId()) {
			change = true;
		}

		switch (field) {
		case "id":
			Main.getIncidents().get(id).setId(source.getId());
			break;
		case "incidentMedNegOccurred":
			Main.getIncidents().get(id).setIncidentMedNegOccurred(Date.valueOf(source.getDateMedNegOccurred()));
			if (change) {
				Main.getCurrentIntake().getIncidentDateMedNegOccurred().setValue(source.getDateMedNegOccurred());
			}
			break;
		case "incidentMedNegDiscovered":
			Main.getIncidents().get(id).setDateMedNegDiscovered(Date.valueOf(source.getDateMedNegDiscovered()));
			if (change) {
				Main.getCurrentIntake().getIncidentDateMedNegDiscovered().setValue(source.getDateMedNegDiscovered());
			}
			break;
		case "incidentDateOfInjury":
			Main.getIncidents().get(id).setDateOfInjury(Date.valueOf(source.getDateOfInjury()));
			if (change) {
				Main.getCurrentIntake().getIncidentDateOfInjury().setValue(source.getDateOfInjury());
			}
			break;
		case "incidentStatuteOfLimitations":
			Main.getIncidents().get(id).setIncidentStatuteOfLimitations(Date.valueOf(source.getIncidentStatuteOfLimitations()));
			if (change) {
				Main.getCurrentIntake().getIncidentDateStatuteOfLimitations().setValue(source.getIncidentStatuteOfLimitations());
			}
			break;
		case "incidentTortNoticeDeadline":
			Main.getIncidents().get(id).setIncidentTortNoticeDeadline(Date.valueOf(source.getIncidentTortNoticeDeadline()));
			if (change) {
				Main.getCurrentIntake().getIncidentTortNoticeDeadline().setValue(source.getIncidentTortNoticeDeadline());
			}
			break;
		case "incidentDiscoveryDeadline":
			Main.getIncidents().get(id).setIncidentDiscoveryDeadline(Date.valueOf(source.getIncidentDiscoveryDeadline()));
			if (change) {
				Main.getCurrentIntake().getIncidentDiscoveryDeadline().setValue(source.getIncidentDiscoveryDeadline());
			}
			break;
		case "incidentMedRecsInHand":
			Main.getIncidents().get(id).setIncidentMedRecsInHand(source.isIncidentMedRecsInHand());
			if (change) {
				Main.getCurrentIntake().setIncidentHaveMedRecords(source.isIncidentMedRecsInHand());
			}
			break;
		case "incidentDescription":
			Main.getIncidents().get(id).setIncidentDescription(source.getIncidentDescription());
			if (change) {
				Main.getCurrentIntake().getIncidentDescription().setText(source.getIncidentDescription());
			}
			break;
		case "incidentCaseStatus":
			Main.getIncidents().get(id).setIncidentCaseStatus(source.getIncidentCaseStatus());
			if (change) {
				Main.getCurrentIntake().getIncidentCaseStatus().setText(source.getIncidentCaseStatus());
			}
			break;
		case "incidentSummary":
			Main.getIncidents().get(id).setIncidentSummary(source.getIncidentSummary());
			if (change) {
				Main.getCurrentIntake().getIncidentSummary().setText(source.getIncidentSummary());
			}
			break;
		case "incidentUpdate":
			Main.getIncidents().get(id).setIncidentUpdates(source.getIncidentUpdates());
			if (change) {
				Main.getCurrentIntake().getIncidentUpdates().setText(source.getIncidentUpdates());
			}
			break;
		case "caseId":
			Main.getIncidents().get(id).setIncidentCaseId(source.getIncidentCaseId());
			break;
		case "organizationId":
			Main.getIncidents().get(id).setIncidentOrganizationId(source.getIncidentOrganizationId());
			break;
		case "isDeleted":
			Main.getIncidents().get(id).setDeleted(source.isDeleted());
			break;
		case "incidentPotentialDefendants":
			Main.getIncidents().get(id).setPotentialDefendants(source.getPotentialDefendants());
			if (change) {
				Main.getCurrentIntake().getIncidentPotentialDefendants().setText(source.getPotentialDefendants());
			}
			break;
		case "incidentFacilitiesInvolved":
			Main.getIncidents().get(id).setFacilitiesInvolved(source.getFacilitiesInvolved());
			if (change) {
				Main.getCurrentIntake().getIncidentFacilitiesInvolved().setText(source.getFacilitiesInvolved());
			}
			break;

		}
	}

	private void updateUser(int id, String field, User source) {

		switch (field) {
		case "id":
			Main.getUsers().get(id).set_id(source.get_id());
			break;
		case "name_first":
			Main.getUsers().get(id).setNameFirst(source.getNameFirst());
			break;
		case "name_last":
			Main.getUsers().get(id).setNameLast(source.getNameLast());
			break;
		case "email":
			Main.getUsers().get(id).setEmail(source.getEmail());
			break;
		case "password":
			Main.getUsers().get(id).setPassword(source.getPassword());
			break;
		case "color":
			Main.getUsers().get(id).setColor(source.getColor());
			break;
		case "is_attorney":
			Main.getUsers().get(id).setIs_attorney(source.isIs_attorney());
			break;
		case "is_admin":
			Main.getUsers().get(id).setIs_admin(source.isIs_admin());
			break;
		case "is_deleted":
			Main.getUsers().get(id).setIs_deleted(source.isIs_deleted());
			break;
		case "default_organization":
			Main.getUsers().get(id).setDefault_organization(source.getDefault_organization());
			break;
//		case "organization_id":
//			Main.getUsers().get(id).setOrganizationId(source.getOrganizationId);
//			break;
		case "initials":
			Main.getUsers().get(id).setInitials(source.getInitials());
			break;
		}
	}

	private void updateCase(int id, String field, Case source) {

		boolean change = false;

		if (Main.getCurrentIntake() != null)
			if (Main.getCurrentIntake().getCase().get_id() == source.get_id()) {
				change = true;
			}

		switch (field) {
		case "id":
			Main.getCases().get(id).set_id(source.get_id());
			break;
		case "name":
			Main.getCases().get(id).setCaseName(source.getCaseName());
			if (change) {
				Main.getCurrentIntake().getCaseName().setText(source.getCaseName());
			} else
				updateCenter();
			break;
		case "callerTime":
			Main.getCases().get(id).setCallerTime(source.getCallerTimeSQL());
			if (change) {
				Main.getCurrentIntake().setCallerTime(source.getCallerTime());
			}
			break;
		case "callerDate":
			Main.getCases().get(id).setCallerDate(source.getCallerDateSQL());
			if (change) {
				Main.getCurrentIntake().getCallerDate().setValue(source.getCallerDate());
			}
			break;
		case "acceptedDate":
			Main.getCases().get(id).setAcceptedDate(source.getAcceptedDateSQL());
			break;
		case "closedDate":
			Main.getCases().get(id).setClosedDate(source.getClosedDateSQL());
			break;
		case "deniedDate":
			Main.getCases().get(id).setDeniedDate(source.getDeniedDateSQL());
			break;
		case "casePracticeAreaId":
			Main.getCases().get(id).setCasePracticeAreaId(source.getCasePracticeAreaId());
			if (change) {

				Platform.runLater(new Runnable() {

					@Override
					public void run() {
						Main.getCurrentIntake().changePracticeArea(Main.getPracticeAreas().get(source.getCasePracticeAreaId()));

					}
				});
			} else
				updateCenter();
			break;
		case "caseStatusId":
			Main.getCases().get(id).setCaseStatusId(source.getCaseStatusId());
			if (change) {

				Platform.runLater(new Runnable() {

					@Override
					public void run() {

						if (Main.getCurrentIntake() != null) {
							Main.getCurrentIntake().getCase().setCaseStatusId(source.getCaseStatusId());
							Main.getController().changeTop(new TopBar(source.getCaseStatusId(), Main.getCurrentIntake()));
						} else {
							Main.getController().changeCenter(new PotentialsBubbles());
						}
					}
				});
			} else
				updateCenter();

			break;
		case "clientId":
			Main.getCases().get(id).setClientId(source.getClientId());
			break;
		case "callerId":
			Main.getCases().get(id).setCallerId(source.getCallerId());
			break;
		case "caseOpposingCounselId":
			Main.getCases().get(id).setCaseOpposingCounselId(source.getCaseOpposingCounselId());
			break;
		case "isDeleted":
			Main.getCases().get(id).setDeleted(source.isDeleted());
			break;
		case "caseOrganizationId":
			Main.getCases().get(id).setCaseOrganizationId(source.getCaseOrganizationId());
			break;
		case "caseNumber":
			Main.getCases().get(id).setCaseNumber(source.getCaseNumber());
			if (change) {
				Main.getCurrentIntake().getCaseNumber().setText(source.getCaseNumber());
			}
			break;
		case "caseJudgeId":
			Main.getCases().get(id).setCaseJudgeId(source.getCaseJudgeId());
			break;
		case "trialDate":
			Main.getCases().get(id).setTrialDate(source.getTrialDateSQL());
			break;
		case "officeResponsibleAttorneyId":
			Main.getCases().get(id).setOfficeResponsibleAttorneyId(source.getOfficeResponsibleAttorneyId());
			if (change) {

				Platform.runLater(new Runnable() {

					@Override
					public void run() {
						Main.getCurrentIntake().changeResponsibleAttorney(Main.getUsers().get(source.getOfficeResponsibleAttorneyId()));
					}
				});

			}
			break;
		case "clientEstate":
			Main.getCases().get(id).setClientEstate(source.isClientEstate());
			if (change)

			{
				Main.getCurrentIntake().getClientEstate().setSelected(source.isClientEstate());
			}
			break;
		case "feeAgreementSigned":
			Main.getCases().get(id).setFeeAgreementSigned(source.isFeeAgreementSigned());
			Main.getCases().get(id).setFeeAgreementSignedDate(source.getFeeAgreementSignedDateSQL());
			if (change) {
				Platform.runLater(new Runnable() {

					@Override
					public void run() {

						if (source.isFeeAgreementSigned()) {
							Main.getCurrentIntake().getSigned().setText("Fee Agreement Signed");
							Main.getCurrentIntake().getSigned().setSelected(true);
						} else {
							Main.getCurrentIntake().getSigned().setText("Fee Agreement Unsigned");
							Main.getCurrentIntake().getSigned().setSelected(false);
						}
					}
				});

			}
			updateCenter();
			break;

		// TODO add fee agreement signed date for custom date change box

		case "followUpQuestionsForPatient":
			Main.getCases().get(id).setFollowUpQuestionsForPatient(source.getFollowUpQuestionsForPatient());
			if (change) {
				Main.getCurrentIntake().getFollowupQuestionsForPatient().setText(source.getFollowUpQuestionsForPatient());
			}
			break;
		case "followUpMeetWithClient":
			Main.getCases().get(id).setFollowUpMeetingWithClient(source.isFollowUpMeetingWithClient());
			if (change) {
				Main.getCurrentIntake().setFollowupMeetWithClient(source.isFollowUpMeetingWithClient());
			}
			break;
		case "followUpNurseReview":
			Main.getCases().get(id).setFollowUpNurseReview(source.isFollowUpNurseReview());
			if (change) {
				Main.getCurrentIntake().setFollowupNurseReview(source.isFollowUpNurseReview());
			}
			break;
		case "followUpExpertReview":
			Main.getCases().get(id).setFollowUpExpertReview(source.isFollowUpExpertReview());
			if (change) {
				Main.getCurrentIntake().setFollowupExpertReview(source.isFollowUpExpertReview());
			}
			break;
		case "followUpCaseTransferred":
			Main.getCases().get(id).setTransferred(source.isTransferred());
			if (change) {
				Main.getCurrentIntake().getFollowupTransferredButton().setSelected(source.isTransferred());
			}
			break;
		case "acceptedChronology":
			Main.getCases().get(id).setAcceptedChronology(source.isAcceptedChronology());
			if (change) {
				Main.getCurrentIntake().getAcceptedChronologyButton().setSelected(source.isAcceptedChronology());
			}
			break;
		case "acceptedConsultantExpertSearch":
			Main.getCases().get(id).setAcceptedConsultantExpertSearch(source.isAcceptedConsultantExpertSearch());
			if (change) {
				Main.getCurrentIntake().getAcceptedConsultantExpertSearchButton().setSelected(source.isAcceptedConsultantExpertSearch());
			}
			break;
		case "acceptedTestifyingExpertSearch":
			Main.getCases().get(id).setAcceptedTestifyingExpertSearch(source.isAcceptedTestifyingExpertSearch());
			if (change) {
				Main.getCurrentIntake().getAcceptedTestifyingExperSearchButton().setSelected(source.isAcceptedTestifyingExpertSearch());
			}
			break;
		case "acceptedMedicalLiterature":
			Main.getCases().get(id).setAcceptedSupportiveMedicalLiterature(source.isAcceptedSupportiveMedicalLiterature());
			if (change) {
				Main.getCurrentIntake().getAcceptedSupportiveMedicalLiteratureButton().setSelected(source.isAcceptedSupportiveMedicalLiterature());
			}
			break;
		case "acceptedDetail":
			Main.getCases().get(id).setAcceptedDetail(source.getAcceptedDetail());
			if (change) {
				Main.getCurrentIntake().getAcceptedDetail().setText(source.getAcceptedDetail());
			}
			break;
		case "deniedChronology":
			Main.getCases().get(id).setDeniedChronology(source.isDeniedChronology());
			if (change) {
				Main.getCurrentIntake().getDenialChronologyButton().setSelected(source.isDeniedChronology());
			}
			break;
		case "deniedDetail":
			Main.getCases().get(id).setDeniedDetails(source.getDeniedDetails());
			if (change) {
				Main.getCurrentIntake().getDenialDetail().setText(source.getDeniedDetails());
			}
			break;
		case "officeIntakePersonId":
			Main.getCases().get(id).setOfficeIntakePersonId(source.getOfficeIntakePersonId());
			updateCenter();
			break;
		case "incidentId":
			Main.getCases().get(id).setIncidentId(source.getIncidentId());
			break;
		case "officePrinterCode":
			Main.getCases().get(id).setOfficePrinterCode(source.getOfficePrinterCode());
			if (change) {
				Main.getCurrentIntake().getOfficePrinterCode().setText(source.getOfficePrinterCode());
			}
			break;
		case "sameAsCaller":
			Main.getCases().get(id).setSameAsCaller(source.isSameAsCaller());
			if (change) {
				Main.getCurrentIntake().getSameAsCaller().setSelected(source.isSameAsCaller());
			}
			break;

		}
	}

	/*
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 * 
	 */
	/*
	 * updateMessage(String updateMessage) message = userId # object type id # objectId
	 * 
	 */

	private void updateCenter() {
		Platform.runLater(new Runnable() {

			@Override
			public void run() {
				if (Main.getController().getCenterClass().equals(PotentialsBubbles.class)) {
					Main.getController().changeCenter(new PotentialsBubbles());

				} else if (Main.getController().getCenterClass().equals(PotentialsList.class)) {
					Main.getController().changeCenter(new PotentialsList());

				}

			}
		});
	}

	public void publish(String updateMessage) {
		/* ***************************************************/
		/* FORMAT: */
		/* UserId#UpdateCode#ObjectId#Field#Value */
		/* ***************************************************/
		if (debug) {
			System.out.println("**************************************");
			System.out.println("FROM SENDER: " + updateMessage);
			try {
				Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nUpdater.publish()".getBytes(), StandardOpenOption.APPEND);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		client.publishWith().topic(topic).payload(UTF_8.encode(updateMessage)).send();
	}
}
