package connections;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;

import application.Main;
import dataStructures.Case;
import dataStructures.Contact;
import dataStructures.Facility;
import dataStructures.Incident;
import dataStructures.LogEntry;
import dataStructures.Organization;
import dataStructures.PracticeArea;
import dataStructures.Provider;
import dataStructures.Status;
import dataStructures.User;

public class Server {
	private static PreparedStatement ps;
	private static ResultSet rs;
	private static boolean showSaveData = Main.isGlobalDebug();

	/*
	 * Download from Database
	 */

	public static HashMap<Integer, Organization> getOrganizations(int organization_id, Connection conn) {

		HashMap<Integer, Organization> organizations = new HashMap<>();
		try {
			ps = conn.prepareStatement("SELECT * FROM [dbo].[Organization] WHERE id = ?");
			ps.setInt(1, organization_id);
			rs = ps.executeQuery();

			while (rs.next()) {
				Organization o = new Organization(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getBoolean(4), rs.getInt(5));
				organizations.put(o.get_id(), o);
			}
			conn.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return organizations;
	}

	public static Organization getOrganizationById(int id, Connection conn) {
		Organization obj = new Organization();
		try {
			ps = conn.prepareStatement("SELECT * from [dbo].[Organization] WHERE id = ?");

			ps.setInt(1, id);
			rs = ps.executeQuery();

			while (rs.next()) {
				int i = 0;
				obj.set_id(rs.getInt(i += 1));
				obj.setName(rs.getString(i += 1));
				obj.setDescription(rs.getString(i += 1));
				obj.setIs_deleted(rs.getBoolean(i += 1));
				obj.setCreator_user_id(rs.getInt(i += 1));
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return obj;
	}

	public static HashMap<Integer, Facility> getFacilities(int organization_id, Connection conn) {
		HashMap<Integer, Facility> facilities = new HashMap<>();
		try {
			ps = conn.prepareStatement("SELECT * from [dbo].[Facility] WHERE organization_id = ? AND is_deleted = 0");

			ps.setInt(1, organization_id);
			rs = ps.executeQuery();

			while (rs.next()) {
				Facility f = new Facility(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getBoolean(4), rs.getInt(5), rs.getString(6), rs.getString(7), rs.getString(8));
				facilities.put(f.get_id(), f);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}

		return facilities;
	}

	public static Facility getFacilityById(int id, Connection conn) {
		Facility obj = new Facility();
		try {
			ps = conn.prepareStatement("SELECT * from [dbo].[Facility] WHERE id = ?");

			ps.setInt(1, id);
			rs = ps.executeQuery();

			while (rs.next()) {
				int i = 0;
				obj.set_id(rs.getInt(i += 1));
				obj.setName(rs.getString(i += 1));
				obj.setDescription(rs.getString(i += 1));
				obj.setDeleted(rs.getBoolean(i += 1));
				obj.setOrganization_id(rs.getInt(i += 1));
				obj.setPhone(rs.getString(i += 1));
				obj.setAcronym(rs.getString(i += 1));
				obj.setFax(rs.getString(i += 1));

			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return obj;
	}

	public static ArrayList<Integer> getFacilitiesByCaseId(int case_id, Connection conn) {
		ArrayList<Integer> ids = new ArrayList<>();
		try {
			ps = conn.prepareStatement("SELECT * from [dbo].[Case_Facility] WHERE case_id = ? AND is_deleted = 0");

			ps.setInt(1, case_id);
			rs = ps.executeQuery();

			while (rs.next()) {
				ids.add(rs.getInt(3));
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return ids;
	}

	public static HashMap<Integer, Provider> getProviders(int organization_id, Connection conn) {

		HashMap<Integer, Provider> providers = new HashMap<>();
		try {
			ps = conn.prepareStatement("SELECT * from [dbo].[Provider] WHERE organization_id = ? AND is_deleted = 0");

			ps.setInt(1, organization_id);
			rs = ps.executeQuery();

			while (rs.next()) {
				Provider p = new Provider(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getBoolean(4), rs.getInt(5));
				providers.put(p.get_id(), p);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}

		return providers;
	}

	public static Provider getProviderById(int id, Connection conn) {
		Provider obj = new Provider();
		try {
			ps = conn.prepareStatement("SELECT * from [dbo].[Provider] WHERE id = ?");

			ps.setInt(1, id);
			rs = ps.executeQuery();

			while (rs.next()) {
				int i = 0;
				obj.set_id(rs.getInt(i += 1));
				obj.setName(rs.getString(i += 1));
				obj.setDescription(rs.getString(i += 1));
				obj.setDeleted(rs.getBoolean(i += 1));
				obj.setOrganization_id(rs.getInt(i += 1));
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return obj;
	}

	public static HashMap<Integer, Contact> getContacts(int organization_id, Connection conn) {
		HashMap<Integer, Contact> contacts = new HashMap<>();
		contacts.clear();
		try {
			ps = conn.prepareStatement("SELECT * from [dbo].[Contact] WHERE organizationId = ? AND isDeleted = 0");

			ps.setInt(1, organization_id);
			rs = ps.executeQuery();

			while (rs.next()) {
				Contact c = new Contact();
				int i = 0;
				c.set_id(rs.getInt(i += 1));
				c.setDate_of_birth(rs.getDate(i += 1));
				c.setDescription(rs.getString(i += 1));
				c.setCondition(rs.getString(i += 1));
				c.setNotes(rs.getString(i += 1));
				c.setName_first(rs.getString(i += 1));
				c.setName_last(rs.getString(i += 1));
				c.setName_work(rs.getString(i += 1));
				c.setPhone_cell(rs.getString(i += 1));
				c.setPhone_work(rs.getString(i += 1));
				c.setPhone_home(rs.getString(i += 1));
				c.setAddress_home(rs.getString(i += 1));
				c.setAddress_work(rs.getString(i += 1));
				c.setAddress_other(rs.getString(i += 1));
				c.setEmail_personal(rs.getString(i += 1));
				c.setEmail_work(rs.getString(i += 1));
				c.setEmail_other(rs.getString(i += 1));
				c.setIs_client(rs.getBoolean(i += 1));
				c.setIs_expert(rs.getBoolean(i += 1));
				c.setIs_deleted(rs.getBoolean(i += 1));
				c.setOrganization_id(rs.getInt(i += 1));
				c.setImageVersion(rs.getInt(i += 1));
				c.setReferredFrom(rs.getString(i += 1));
				c.setDeceased(rs.getBoolean(i += 1));

				contacts.put(c.get_id(), c);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return contacts;
	}

	public static Contact getContactById(int contactId, Connection conn) {
		Contact c = new Contact();
		try {
			ps = conn.prepareStatement("SELECT * from [dbo].[Contact] WHERE id = ?");

			ps.setInt(1, contactId);
			rs = ps.executeQuery();

			while (rs.next()) {
				int i = 0;
				c.set_id(rs.getInt(i += 1));
				c.setDate_of_birth(rs.getDate(i += 1));
				c.setDescription(rs.getString(i += 1));
				c.setCondition(rs.getString(i += 1));
				c.setNotes(rs.getString(i += 1));
				c.setName_first(rs.getString(i += 1));
				c.setName_last(rs.getString(i += 1));
				c.setName_work(rs.getString(i += 1));
				c.setPhone_cell(rs.getString(i += 1));
				c.setPhone_work(rs.getString(i += 1));
				c.setPhone_home(rs.getString(i += 1));
				c.setAddress_home(rs.getString(i += 1));
				c.setAddress_work(rs.getString(i += 1));
				c.setAddress_other(rs.getString(i += 1));
				c.setEmail_personal(rs.getString(i += 1));
				c.setEmail_work(rs.getString(i += 1));
				c.setEmail_other(rs.getString(i += 1));
				c.setIs_client(rs.getBoolean(i += 1));
				c.setIs_expert(rs.getBoolean(i += 1));
				c.setIs_deleted(rs.getBoolean(i += 1));
				c.setOrganization_id(rs.getInt(i += 1));
				c.setImageVersion(rs.getInt(i += 1));
				c.setReferredFrom(rs.getString(i += 1));
				c.setDeceased(rs.getBoolean(i += 1));

			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return c;
	}

	public static HashMap<Integer, PracticeArea> getPracticeAreas(int organization_id, Connection conn) {
		HashMap<Integer, PracticeArea> practiceAreas = new HashMap<>();
		try {
			ps = conn.prepareStatement("SELECT * from [dbo].[Practice_Area] WHERE organization_id = ? AND is_deleted = 0");

			ps.setInt(1, organization_id);
			rs = ps.executeQuery();
			while (rs.next()) {
				PracticeArea p = new PracticeArea(rs.getInt(1), rs.getString(2), rs.getBoolean(3), rs.getInt(4));
				practiceAreas.put(p.get_id(), p);
			}
			conn.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}

		return practiceAreas;
	}

	public static PracticeArea getPracticeAreaById(int id, Connection conn) {
		PracticeArea obj = new PracticeArea();
		try {
			ps = conn.prepareStatement("SELECT * from [dbo].[Practice_Area] WHERE id = ?");

			ps.setInt(1, id);
			rs = ps.executeQuery();

			while (rs.next()) {
				int i = 0;
				obj.set_id(rs.getInt(i += 1));
				obj.setPractice_area(rs.getString(i += 1));
				obj.setIs_deleted(rs.getBoolean(i += 1));
				obj.setOrganization_id(rs.getInt(i += 1));
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return obj;
	}

	public static HashMap<Integer, Status> getStatusChoices(Connection conn) {

		HashMap<Integer, Status> statusChoices = new HashMap<>();

		try {
			ps = conn.prepareStatement("SELECT * FROM [dbo].[Status] WHERE is_deleted = 0");

			rs = ps.executeQuery();

			while (rs.next()) {
				Status temp = new Status(rs.getInt(1), rs.getString(2), rs.getBoolean(3), rs.getBoolean(4), rs.getInt(5));
				if (!temp.isDeleted())
					statusChoices.put(temp.get_id(), temp);
			}
			conn.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return statusChoices;
	}

	public static Status getStatusChoiceById(int id, Connection conn) {
		Status obj = new Status();
		try {
			ps = conn.prepareStatement("SELECT * from [dbo].[Status] WHERE id = ?");

			ps.setInt(1, id);
			rs = ps.executeQuery();

			while (rs.next()) {
				int i = 0;
				obj.set_id(rs.getInt(i += 1));
				obj.setStatus(rs.getString(i += 1));
				obj.setFor_case(rs.getBoolean(i += 1));
				obj.setDeleted(rs.getBoolean(i += 1));
				obj.setOrganization_id(rs.getInt(i += 1));
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return obj;
	}

	public static HashMap<Integer, Incident> getIncidents(int organizationId, Connection conn) {
		HashMap<Integer, Incident> incidents = new HashMap<>();

		try {
			ps = conn.prepareStatement("SELECT * FROM [dbo].[Incident]  " + "WHERE organizationId = ? AND isDeleted = 0");
			ps.setInt(1, organizationId);
			rs = ps.executeQuery();
			while (rs.next()) {
				int i = 0;
				Incident incident = new Incident();

				incident.setId(rs.getInt(i += 1));
				incident.setIncidentMedNegOccurred(rs.getDate(i += 1));
				incident.setDateMedNegDiscovered(rs.getDate(i += 1));
				incident.setDateOfInjury(rs.getDate(i += 1));
				incident.setIncidentStatuteOfLimitations(rs.getDate(i += 1));
				incident.setIncidentTortNoticeDeadline(rs.getDate(i += 1));
				incident.setIncidentDiscoveryDeadline(rs.getDate(i += 1));
				incident.setIncidentMedRecsInHand(rs.getBoolean(i += 1));
				incident.setIncidentDescription(rs.getString(i += 1));
				incident.setIncidentCaseStatus(rs.getString(i += 1));
				incident.setIncidentSummary(rs.getString(i += 1));
				incident.setIncidentUpdates(rs.getString(i += 1));
				incident.setIncidentCaseId(rs.getInt(i += 1));
				incident.setIncidentOrganizationId(rs.getInt(i += 1));
				incident.setDeleted(rs.getBoolean(i += 1));
				incident.setPotentialDefendants(rs.getString(i += 1));
				incident.setFacilitiesInvolved(rs.getString(i += 1));

				incidents.put(incident.getId(), incident);
			}
			conn.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}

		return incidents;
	}

	public static Incident getIncidentById(int incidentId, Connection conn) {
		Incident incident = new Incident();
		try {
			ps = conn.prepareStatement("SELECT * FROM [dbo].[Incident]  " + "WHERE id = ?");
			ps.setInt(1, incidentId);
			rs = ps.executeQuery();
			while (rs.next()) {
				int i = 0;

				incident.setId(rs.getInt(i += 1));
				incident.setIncidentMedNegOccurred(rs.getDate(i += 1));
				incident.setDateMedNegDiscovered(rs.getDate(i += 1));
				incident.setDateOfInjury(rs.getDate(i += 1));
				incident.setIncidentStatuteOfLimitations(rs.getDate(i += 1));
				incident.setIncidentTortNoticeDeadline(rs.getDate(i += 1));
				incident.setIncidentDiscoveryDeadline(rs.getDate(i += 1));
				incident.setIncidentMedRecsInHand(rs.getBoolean(i += 1));
				incident.setIncidentDescription(rs.getString(i += 1));
				incident.setIncidentCaseStatus(rs.getString(i += 1));
				incident.setIncidentSummary(rs.getString(i += 1));
				incident.setIncidentUpdates(rs.getString(i += 1));
				incident.setIncidentCaseId(rs.getInt(i += 1));
				incident.setIncidentOrganizationId(rs.getInt(i += 1));
				incident.setDeleted(rs.getBoolean(i += 1));
				incident.setPotentialDefendants(rs.getString(i += 1));
				incident.setFacilitiesInvolved(rs.getString(i += 1));

			}
			conn.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}

		return incident;
	}

	public static HashMap<Integer, User> getUsersByOrganization(int organization_id, Connection conn) {
		HashMap<Integer, User> users = new HashMap<>();

		try {
			ps = conn.prepareStatement("SELECT * FROM [dbo].[User] WHERE default_organization = ? AND is_deleted = 0 ORDER BY name_last ASC");
			ps.setInt(1, organization_id);
			rs = ps.executeQuery();
			while (rs.next()) {
				User tempUser = new User();
				int i = 0;
				tempUser.set_id(rs.getInt(i += 1));
				tempUser.setNameFirst(rs.getString(i += 1));
				tempUser.setNameLast(rs.getString(i += 1));
				tempUser.setEmail(rs.getString(i += 1));
				tempUser.setPassword(rs.getString(i += 1));
				tempUser.setColor(rs.getString(i += 1));
				tempUser.setIs_attorney(rs.getBoolean(i += 1));
				tempUser.setIs_admin(rs.getBoolean(i += 1));
				tempUser.setIs_deleted(rs.getBoolean(i += 1));
				tempUser.setDefault_organization(rs.getInt(i += 1));
				i += 1;
				tempUser.setInitials(rs.getString(i += 1));

				users.put(tempUser.get_id(), tempUser);
			}
			conn.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}

		return users;
	}

	public static User getUserById(int id, Connection conn) {
		User user = new User();

		try {
			ps = conn.prepareStatement("SELECT * FROM [dbo].[User] WHERE id = ?");
			ps.setInt(1, id);
			rs = ps.executeQuery();
			while (rs.next()) {
				User tempUser = new User();
				int i = 0;
				tempUser.set_id(rs.getInt(i += 1));
				tempUser.setNameFirst(rs.getString(i += 1));
				tempUser.setNameLast(rs.getString(i += 1));
				tempUser.setEmail(rs.getString(i += 1));
				tempUser.setPassword(rs.getString(i += 1));
				tempUser.setColor(rs.getString(i += 1));
				tempUser.setIs_attorney(rs.getBoolean(i += 1));
				tempUser.setIs_admin(rs.getBoolean(i += 1));
				tempUser.setIs_deleted(rs.getBoolean(i += 1));
				tempUser.setDefault_organization(rs.getInt(i += 1));
				i += 1;// Iterate past organization id
				tempUser.setInitials(rs.getString(i += 1));

			}
			conn.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}

		return user;
	}

	public static HashMap<Integer, Case> getCasesByOrganization(int organization_id, Connection conn) {
		HashMap<Integer, Case> cases = new HashMap<Integer, Case>();

		try {
			// Load all cases
			ps = conn.prepareStatement("SELECT * FROM [dbo].[Case] WHERE isDeleted = 0 AND caseOrganizationId = ? ORDER BY name ASC");
			ps.setInt(1, organization_id);
			rs = ps.executeQuery();

			while (rs.next()) {
				Case c = new Case();
				int i = 0;
				c.set_id(rs.getInt(i += 1));
				c.setCaseName(rs.getString(i += 1));
				c.setCallerTime(rs.getTime(i += 1));
				c.setCallerDate(rs.getDate(i += 1));
				c.setAcceptedDate(rs.getDate(i += 1));
				c.setClosedDate(rs.getDate(i += 1));
				c.setDeniedDate(rs.getDate(i += 1));
				c.setCasePracticeAreaId(rs.getInt(i += 1));
				c.setCaseStatusId(rs.getInt(i += 1));
				c.setClientId(rs.getInt(i += 1));
				c.setCallerId(rs.getInt(i += 1));
				c.setCaseOpposingCounselId(i += 1);
				c.setDeleted(rs.getBoolean(i += 1));
				c.setCaseOrganizationId(rs.getInt(i += 1));
				c.setCaseNumber(rs.getString(i += 1));
				c.setCaseJudgeId(rs.getInt(i += 1));
				c.setTrialDate(rs.getDate(i += 1));
				c.setOfficeResponsibleAttorneyId(rs.getInt(i += 1));
				c.setClientEstate(rs.getBoolean(i += 1));
				c.setFollowUpQuestionsForPatient(rs.getString(i += 1));
				c.setFollowUpMeetingWithClient(rs.getBoolean(i += 1));
				c.setFollowUpNurseReview(rs.getBoolean(i += 1));
				c.setFollowUpExpertReview(rs.getBoolean(i += 1));
				c.setTransferred(rs.getBoolean(i += 1));
				c.setAcceptedChronology(rs.getBoolean(i += 1));
				c.setAcceptedConsultantExpertSearch(rs.getBoolean(i += 1));
				c.setAcceptedTestifyingExpertSearch(rs.getBoolean(i += 1));
				c.setAcceptedSupportiveMedicalLiterature(rs.getBoolean(i += 1));
				c.setAcceptedDetail(rs.getString(i += 1));
				c.setDeniedChronology(rs.getBoolean(i += 1));
				c.setDeniedDetails(rs.getString(i += 1));
				c.setOfficeIntakePersonId(rs.getInt(i += 1));
				c.setIncidentId(rs.getInt(i += 1));
				c.setOfficePrinterCode(rs.getString(i += 1));
				c.setSameAsCaller(rs.getBoolean(i += 1));
				c.setFeeAgreementSigned(rs.getBoolean(i += 1));
				c.setFeeAgreementSignedDate(rs.getDate(i += 1));
				c.setReceivedUpdates(rs.getString(i += 1));

				/*
				 * 
				 */
				// c.setOfficeIntakePerson(Main.getUsers().get(c.getOfficeIntakePersonId()));

				cases.put(c.get_id(), c);

			}
		} catch (SQLException e) {
			e.printStackTrace();

		}
		return cases;
	}

	public static Case getCaseById(int id, Connection conn) {
		Case c = new Case();
		try {
			// Load all cases
			ps = conn.prepareStatement("SELECT * FROM [dbo].[Case] WHERE id = ?");
			ps.setInt(1, id);
			rs = ps.executeQuery();

			while (rs.next()) {
				int i = 0;
				c.set_id(rs.getInt(i += 1));
				c.setCaseName(rs.getString(i += 1));
				c.setCallerTime(rs.getTime(i += 1));
				c.setCallerDate(rs.getDate(i += 1));
				c.setAcceptedDate(rs.getDate(i += 1));
				c.setClosedDate(rs.getDate(i += 1));
				c.setDeniedDate(rs.getDate(i += 1));
				c.setCasePracticeAreaId(rs.getInt(i += 1));
				c.setCaseStatusId(rs.getInt(i += 1));
				c.setClientId(rs.getInt(i += 1));
				c.setCallerId(rs.getInt(i += 1));
				c.setCaseOpposingCounselId(i += 1);
				c.setDeleted(rs.getBoolean(i += 1));
				c.setCaseOrganizationId(rs.getInt(i += 1));
				c.setCaseNumber(rs.getString(i += 1));
				c.setCaseJudgeId(rs.getInt(i += 1));
				c.setTrialDate(rs.getDate(i += 1));
				c.setOfficeResponsibleAttorneyId(rs.getInt(i += 1));
				c.setClientEstate(rs.getBoolean(i += 1));
				c.setFollowUpQuestionsForPatient(rs.getString(i += 1));
				c.setFollowUpMeetingWithClient(rs.getBoolean(i += 1));
				c.setFollowUpNurseReview(rs.getBoolean(i += 1));
				c.setFollowUpExpertReview(rs.getBoolean(i += 1));
				c.setTransferred(rs.getBoolean(i += 1));
				c.setAcceptedChronology(rs.getBoolean(i += 1));
				c.setAcceptedConsultantExpertSearch(rs.getBoolean(i += 1));
				c.setAcceptedTestifyingExpertSearch(rs.getBoolean(i += 1));
				c.setAcceptedSupportiveMedicalLiterature(rs.getBoolean(i += 1));
				c.setAcceptedDetail(rs.getString(i += 1));
				c.setDeniedChronology(rs.getBoolean(i += 1));
				c.setDeniedDetails(rs.getString(i += 1));
				c.setOfficeIntakePersonId(rs.getInt(i += 1));
				c.setIncidentId(rs.getInt(i += 1));
				c.setOfficePrinterCode(rs.getString(i += 1));
				c.setSameAsCaller(rs.getBoolean(i += 1));
				c.setFeeAgreementSigned(rs.getBoolean(i += 1));
				c.setFeeAgreementSignedDate(rs.getDate(i += 1));
				c.setReceivedUpdates(rs.getString(i += 1));
			}
		} catch (SQLException e) {
			e.printStackTrace();

		}
		return c;
	}

	/*
	 * Upload to Database
	 */

	public static int createCase(Case selectedCase, Connection conn) {

		if (showSaveData) {
			System.out.println("SERVER.CreateCase()");
			try {
				Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nServer.CreateCase()".getBytes(), StandardOpenOption.APPEND);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		int id = 0;
		try {
			ps = conn.prepareStatement("INSERT INTO [dbo].[Case]" + "(name, " + "callerTime, " + "callerDate, " + "acceptedDate," + "closedDate,"
					+ "deniedDate," + "casePracticeAreaId," + "caseStatusId," + "clientId," + "callerId, " + "caseOpposingCounselId, " + "isDeleted, "
					+ "caseOrganizationId, " + "caseNumber, " + "caseJudgeId, " + "trialDate, " + "officeResponsibleAttorneyId, " + "clientEstate, "
					+ "followUpQuestionsForPatient, " + "followUpMeetWithClient, " + "followUpNurseReview," + "followUpExpertReview,"
					+ "followUpCaseTransferred," + "acceptedChronology," + "acceptedConsultantExpertSearch," + "acceptedTestifyingExpertSearch,"
					+ "acceptedMedicalLiterature," + "acceptedDetail," + "deniedChronology," + "deniedDetail," + "officeIntakePersonId,"
					+ "incidentId," + "officePrinterCode," + "feeAgreementSigned," + "dateFeeAgreementSigned)"
					+ "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");

			int i = 0;
			ps.setString(i += 1, selectedCase.getCaseName());
			ps.setTime(i += 1, selectedCase.getCallerTimeSQL());
			ps.setDate(i += 1, selectedCase.getCallerDateSQL());
			ps.setDate(i += 1, selectedCase.getAcceptedDateSQL());
			ps.setDate(i += 1, selectedCase.getClosedDateSQL());
			ps.setDate(i += 1, selectedCase.getDeniedDateSQL());

			ps.setInt(i += 1, selectedCase.getCasePracticeAreaId());
			ps.setInt(i += 1, selectedCase.getCaseStatusId());
			ps.setInt(i += 1, selectedCase.getClientId());
			ps.setInt(i += 1, selectedCase.getCallerId());
			ps.setInt(i += 1, selectedCase.getCaseOpposingCounselId());
			ps.setBoolean(i += 1, false);
			ps.setInt(i += 1, selectedCase.getCaseOrganizationId());
			ps.setString(i += 1, selectedCase.getCaseNumber());

			ps.setInt(i += 1, selectedCase.getCaseJudgeId());
			ps.setDate(i += 1, selectedCase.getTrialDateSQL());
			ps.setInt(i += 1, selectedCase.getOfficeResponsibleAttorneyId());
			ps.setBoolean(i += 1, selectedCase.isClientEstate());
			ps.setString(i += 1, selectedCase.getFollowUpQuestionsForPatient());
			ps.setBoolean(i += 1, selectedCase.isFollowUpMeetingWithClient());

			ps.setBoolean(i += 1, selectedCase.isFollowUpNurseReview());
			ps.setBoolean(i += 1, selectedCase.isFollowUpExpertReview());
			ps.setBoolean(i += 1, selectedCase.isTransferred());
			ps.setBoolean(i += 1, selectedCase.isAcceptedChronology());
			ps.setBoolean(i += 1, selectedCase.isAcceptedConsultantExpertSearch());

			ps.setBoolean(i += 1, selectedCase.isAcceptedTestifyingExpertSearch());
			ps.setBoolean(i += 1, selectedCase.isAcceptedSupportiveMedicalLiterature());
			ps.setString(i += 1, selectedCase.getAcceptedDetail());
			ps.setBoolean(i += 1, selectedCase.isDeniedChronology());
			ps.setString(i += 1, selectedCase.getDeniedDetails());
			ps.setInt(i += 1, selectedCase.getOfficeIntakePersonId());

			ps.setInt(i += 1, selectedCase.getIncidentId());
			ps.setString(i += 1, selectedCase.getOfficePrinterCode());
			ps.setBoolean(i += 1, selectedCase.isFeeAgreementSigned());
			ps.setDate(i += 1, selectedCase.getFeeAgreementSignedDateSQL());

			ps.execute();

			ps = conn.prepareStatement("SELECT IDENT_CURRENT ('[dbo].[Case]') as ID");
			rs = ps.executeQuery();
			rs.next();
			id = rs.getInt(1);

			conn.close();

			return id;
		} catch (SQLException e) {
			e.printStackTrace();
		}

		return id;
	}

	public static void deleteCase(int id, Connection conn) {
		// Main.getUpdater().publish();//TODO publish update
		if (showSaveData) {
			System.out.println("SERVER.DeleteCase()");
			System.out.println();

			try {
				Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nServer.deleteCase()".getBytes(), StandardOpenOption.APPEND);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		try {

			ps = conn.prepareStatement("UPDATE[dbo].[Incident] SET isDeleted = 1 WHERE caseId= ? ");
			ps.setInt(1, id);
			ps.execute();

			ps = conn.prepareStatement("UPDATE[dbo].[Case] SET isDeleted = 1 WHERE id = ? ");
			ps.setInt(1, id);
			ps.execute();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static int createNewContact(int organizationId, Connection conn) {
		// Main.getUpdater().publish();//TODO publish update
		int id = 0;
		try {
			String statement = "INSERT INTO [dbo].[Contact] (organizationId, isDeleted) VALUES (?, 0)";

			if (showSaveData) {
				System.out.println(statement);
				System.out.println();

				try {
					Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nServer.createNewContact()".getBytes(), StandardOpenOption.APPEND);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			ps = conn.prepareStatement(statement);

			ps.setInt(1, organizationId);
			ps.execute();

			ps = conn.prepareStatement("SELECT IDENT_CURRENT ('[dbo].[Contact]') as ID");
			rs = ps.executeQuery();
			rs.next();
			id = rs.getInt(1);

			conn.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return id;
	}

	public static void deleteContact(int id, Connection conn) {
		// Main.getUpdater().publish();//TODO publish update
		if (showSaveData) {
			System.out.println("SERVER.DeleteContact()");
			System.out.println();

			try {
				Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nServer.deleteContact()".getBytes(), StandardOpenOption.APPEND);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		try {

			ps = conn.prepareStatement("UPDATE[dbo].[Contact] SET isDeleted = 1 WHERE id = ? ");
			ps.setInt(1, id);
			ps.execute();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void updateContactStringField(int id, String field, String value, Connection conn) {
		try {

			Main.getUpdater().publish(Main.getCurrentUser().get_id() + "#3#" + id + "#" + field + "#");
			value = SQLCorrect.getString(value);
			String statement = "UPDATE [dbo].[Contact] SET " + field + " = '" + value + "' WHERE id = " + id;
			if (showSaveData) {
				System.out.println(statement);
				System.out.println();

				try {
					Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nServer.updateContactStringField()".getBytes(),
							StandardOpenOption.APPEND);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			ps = conn.prepareStatement(statement);

			ps.execute();
			/**
			 * Submit info to audit log
			 */
			auditLogString(3, id, field, value);

			conn.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public static void updateContactBooleanField(int id, String field, boolean value, Connection conn) {
		try {

			Main.getUpdater().publish(Main.getCurrentUser().get_id() + "#3#" + id + "#" + field + "#");

			String statement = "UPDATE [dbo].[Contact] SET " + field + " = '" + value + "' WHERE id = " + id;
			if (showSaveData) {
				System.out.println(statement);
				System.out.println();

				try {
					Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nServer.updateContactBooleanField()".getBytes(),
							StandardOpenOption.APPEND);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			ps = conn.prepareStatement(statement);

			ps.execute();
			/**
			 * Submit info to audit log
			 */
			auditLogBoolean(3, id, field, value);

			conn.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public static void updateContactDateField(int id, String field, Date value, Connection conn) {
		try {
			Main.getUpdater().publish(Main.getCurrentUser().get_id() + "#3#" + id + "#" + field + "#");

			String statement = "UPDATE [dbo].[Contact] SET " + field + " = '" + value + "' WHERE id = " + id;
			if (value == null) {
				statement = "UPDATE [dbo].[Contact] SET " + field + " = NULL WHERE id = " + id;
			}

			if (showSaveData) {
				System.out.println(statement);
				System.out.println();

				try {
					Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nServer.updateContactDateField()".getBytes(),
							StandardOpenOption.APPEND);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			ps = conn.prepareStatement(statement);

			ps.execute();
			/**
			 * Submit info to audit log
			 */
			auditLogDate(3, id, field, value);

			conn.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public static void updateCaseIntField(int id, String field, int value, Connection conn) {
		try {
			Main.getUpdater().publish(Main.getCurrentUser().get_id() + "#8#" + id + "#" + field + "#");
			String statement = "UPDATE [dbo].[Case] SET " + field + " = '" + value + "' WHERE id = " + id;
			if (showSaveData) {
				System.out.println(statement);
				System.out.println();

				try {
					Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nServer.updateCaseIntField()".getBytes(), StandardOpenOption.APPEND);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			ps = conn.prepareStatement(statement);

			ps.execute();
			/**
			 * Submit info to audit log
			 */
			auditLogInt(8, id, field, value);

			conn.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public static void updateCaseDateField(int id, String field, Date value, Connection conn) {
		try {

			Main.getUpdater().publish(Main.getCurrentUser().get_id() + "#8#" + id + "#" + field + "#");
			String statement = "UPDATE [dbo].[Case] SET " + field + " = '" + value + "' WHERE id = " + id;

			if (value == null) {
				statement = "UPDATE [dbo].[Case] SET " + field + " = NULL WHERE id = " + id;
			}

			if (showSaveData) {
				System.out.println(statement);
				System.out.println();

				try {
					Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nServer.updateCaseDateField()".getBytes(), StandardOpenOption.APPEND);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			ps = conn.prepareStatement(statement);

			ps.execute();
			/**
			 * Submit info to audit log
			 */
			auditLogDate(8, id, field, value);

			conn.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public static void updateCaseBooelanField(int id, String field, boolean value, Connection conn) {
		try {

			Main.getUpdater().publish(Main.getCurrentUser().get_id() + "#8#" + id + "#" + field + "#");

			String statement = "UPDATE [dbo].[Case] SET " + field + " = '" + value + "' WHERE id = " + id;
			if (showSaveData) {
				System.out.println(statement);
				System.out.println();

				try {
					Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nServer.updateCaseBooleanField()".getBytes(),
							StandardOpenOption.APPEND);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			ps = conn.prepareStatement(statement);

			ps.execute();
			/**
			 * Submit info to audit log
			 */
			auditLogBoolean(8, id, field, value);

			conn.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public static void updateCasePracticeAreaField(int caseId, int practiceAreaId, Connection conn) {
		try {

			Main.getUpdater().publish(Main.getCurrentUser().get_id() + "#8#" + caseId + "#casePracticeAreaId#" + practiceAreaId + "#");

			String statement = "UPDATE [dbo].[Case] SET casePracticeAreaId = '" + practiceAreaId + "' WHERE id = " + caseId;
			if (showSaveData) {
				System.out.println(statement);
				System.out.println();

				try {
					Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nServer.updateCasePracticeAreaField()".getBytes(),
							StandardOpenOption.APPEND);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			ps = conn.prepareStatement(statement);

			ps.execute();

			/**
			 * Submit info to audit log
			 */

			statement = "INSERT INTO [dbo].[AuditLog] (userId, objectTypeId, objectId, fieldName, intValue) "
					+ "VALUES (" + Main.getCurrentUser().get_id() + ",8," + caseId + ", casePracticeAreaId," + practiceAreaId + ")";
			ps = conn.prepareStatement(statement);
			ps.execute();

			conn.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public static void updateCaseStringField(int id, String field, String value, Connection conn) {
		try {
			value = SQLCorrect.getString(value);
			Main.getUpdater().publish(Main.getCurrentUser().get_id() + "#8#" + id + "#" + field + "#");
			String statement = "UPDATE [dbo].[Case] SET " + field + " = '" + value + "' WHERE id = " + id;

			if (showSaveData) {
				System.out.println(statement);
				System.out.println();

				try {
					Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nServer.updateCaseStringField()".getBytes(), StandardOpenOption.APPEND);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			ps = conn.prepareStatement(statement);

			ps.execute();
			/**
			 * Submit info to audit log
			 */
			auditLogString(8, id, field, value);

			conn.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public static int createNewIncident(int organizationId, int caseId, Connection conn) {
		// Main.getUpdater().publish();//TODO publish update
		int id = 0;
		try {
			String statement = "INSERT INTO [dbo].[Incident] (organizationId, caseId, isDeleted) VALUES (" + organizationId + ", " + caseId + ", 0)";
			if (showSaveData) {
				System.out.println(statement);
				System.out.println();

				try {
					Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nServer.createNewIncident()".getBytes(), StandardOpenOption.APPEND);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			ps = conn.prepareStatement(statement);

			ps.execute();

			ps = conn.prepareStatement("SELECT IDENT_CURRENT ('[dbo].[Incident]') as ID");
			rs = ps.executeQuery();
			rs.next();
			id = rs.getInt(1);

			conn.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return id;
	}

	public static void updateIncidentDateField(int id, String field, Date value, Connection conn) {
		try {

			Main.getUpdater().publish(Main.getCurrentUser().get_id() + "#6#" + id + "#" + field + "#");

			String statement = "UPDATE [dbo].[Incident] SET " + field + " = '" + value + "' WHERE id = " + id;
			if (value == null) {
				statement = "UPDATE [dbo].[Incident] SET " + field + " = NULL WHERE id = " + id;
			}

			if (showSaveData) {
				System.out.println(statement);
				System.out.println();

				try {
					Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nServer.updateIncidentDateField()".getBytes(),
							StandardOpenOption.APPEND);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			ps = conn.prepareStatement(statement);

			ps.execute();
			/**
			 * Submit info to audit log
			 */
			auditLogDate(6, id, field, value);

			conn.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public static void updateIncidentStringField(int id, String field, String value, Connection conn) {
		value = SQLCorrect.getString(value);
		try {

			Main.getUpdater().publish(Main.getCurrentUser().get_id() + "#6#" + id + "#" + field + "#");

			String statement = "UPDATE [dbo].[Incident] SET " + field + " = '" + value + "' WHERE id = " + id;

			if (showSaveData) {
				System.out.println(statement);
				System.out.println();

				try {
					Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nServer.updateIncidentStringField()".getBytes(),
							StandardOpenOption.APPEND);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			ps = conn.prepareStatement(statement);

			ps.execute();
			auditLogString(6, id, field, value);

			conn.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public static void updateIncidentBooleanField(int id, String field, boolean value, Connection conn) {
		try {

			Main.getUpdater().publish(Main.getCurrentUser().get_id() + "#6#" + id + "#" + field + "#");

			String statement = "UPDATE [dbo].[Incident] SET " + field + " = '" + value + "' WHERE id = " + id;

			if (showSaveData) {
				System.out.println(statement);
				System.out.println();

				try {
					Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nServer.updateIncidentBooleanField()".getBytes(),
							StandardOpenOption.APPEND);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			ps = conn.prepareStatement(statement);

			ps.execute();
			auditLogBoolean(6, id, field, value);

			conn.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public static void updateUserStringField(int id, String field, String value, Connection conn) {
		try {

			Main.getUpdater().publish(Main.getCurrentUser().get_id() + "#7#" + id + "#" + field + "#");// TODO need to update user name everywhere

			String statement = "UPDATE [dbo].[User] SET " + field + " = '" + value + "' WHERE id = " + id;

			if (showSaveData) {
				System.out.println(statement);
				System.out.println();

				try {
					Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nServer.updateUserStringField()".getBytes(), StandardOpenOption.APPEND);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			ps = conn.prepareStatement(statement);

			ps.execute();
			auditLogString(7, id, field, value);
			conn.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public static int createNewFacility(int organizationId, Connection conn) {
		// Main.getUpdater().publish();//TODO publish update
		int id = 0;
		try {
			String statement = "INSERT INTO [dbo].[Facility] (organization_id, is_deleted, name) VALUES (?, 0, 'NewFacility')";

			if (showSaveData) {
				System.out.println(statement);
				System.out.println();

				try {
					Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nServer.createNewFacility()".getBytes(), StandardOpenOption.APPEND);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			ps = conn.prepareStatement(statement);

			ps.setInt(1, organizationId);
			ps.execute();

			ps = conn.prepareStatement("SELECT IDENT_CURRENT ('[dbo].[Facility]') as ID");
			rs = ps.executeQuery();
			rs.next();
			id = rs.getInt(1);

			conn.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return id;
	}

	public static void updateFacilityStringField(int id, String field, String value, Connection conn) {
		value = SQLCorrect.getString(value);
		try {

//			Main.getUpdater().publish(Main.getCurrentUser().get_id() + "#6#" + id + "#" + field + "#");
			// TODO change updater to use facility and change numbers here to match

			String statement = "UPDATE [dbo].[Facility] SET " + field + " = '" + value + "' WHERE id = " + id;

			if (showSaveData) {
				System.out.println(statement);
				System.out.println();

				try {
					Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nServer.updateFacilityStringField()".getBytes(),
							StandardOpenOption.APPEND);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			ps = conn.prepareStatement(statement);

			ps.execute();
			auditLogString(1, id, field, value);
			conn.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public static void deleteFacility(int id, Connection conn) {
		// Main.getUpdater().publish();//TODO publish update
		if (showSaveData) {
			System.out.println("SERVER.DeleteFacility()");
			System.out.println();

			try {
				Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nServer.deleteFacility()".getBytes(), StandardOpenOption.APPEND);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		try {

			ps = conn.prepareStatement("UPDATE[dbo].[Facility] SET isDeleted = 1 WHERE id= ? ");
			ps.setInt(1, id);
			ps.execute();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static int createNewCaseFacility(int case_id, int facility_id, Connection conn) {
		// Main.getUpdater().publish();//TODO publish update
		int id = 0;
		try {
			String statement = "INSERT INTO [dbo].[Case_Facility] (case_id, facility_id, is_deleted) VALUES (?,?,0)";

			if (showSaveData) {
				System.out.println(statement);
				System.out.println();

				try {
					Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nServer.createNewCaseFacility()".getBytes(), StandardOpenOption.APPEND);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			ps = conn.prepareStatement(statement);

			ps.setInt(1, case_id);
			ps.setInt(2, facility_id);
			ps.execute();

			ps = conn.prepareStatement("SELECT IDENT_CURRENT ('[dbo].[Case_Facility]') as ID");
			rs = ps.executeQuery();
			rs.next();
			id = rs.getInt(1);

			conn.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return id;
	}

	public static void deleteCaseFacility(int case_id, int facility_id, Connection conn) {
		// Main.getUpdater().publish();//TODO publish update
		if (showSaveData) {
			System.out.println("SERVER.DeleteCaseFacility()");
			System.out.println();

			try {
				Files.write(Paths.get(Main.getLog().getAbsolutePath()), "\nServer.deleteCaseFacility()".getBytes(), StandardOpenOption.APPEND);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		try {

			ps = conn.prepareStatement("DELETE FROM [dbo].[Case_Facility] WHERE case_id = ? AND facility_id = ? ");
			ps.setInt(1, case_id);
			ps.setInt(2, facility_id);
			ps.execute();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/*
	 * Update Checking
	 */
	public static double getUpdate(double version, Connection conn) {
		/*
		 * checks currentVersion from server against version from local if different, return false
		 * true return false
		 */
		double currentVersion = 0.0;
		try {
			ps = conn.prepareStatement("SELECT currentVersion from [dbo].[Versions]");
			rs = ps.executeQuery();
			rs.next();
			currentVersion = rs.getDouble(1);
			return currentVersion;
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return 0.0;

	}

	private static void auditLogBoolean(int objectTypeId, int objectId, String fieldName, boolean booleanValue) {

		int bool = 0;
		if (booleanValue)
			bool = 1;
		String statement = "INSERT INTO [dbo].[AuditLog] (userId, objectTypeId, objectId, fieldName, booleanValue, entryDate, fieldCode) "
				+ "VALUES (" + Main.getCurrentUser().get_id() + "," + objectTypeId + "," + objectId + ", '" + fieldName + "'," + bool + ", '" + new Timestamp(System
						.currentTimeMillis()) + "', " + 1 + ")";
		Connection conn = ConnectionResources.getConnection();
		try {
			ps = conn.prepareStatement(statement);
			ps.execute();
			conn.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}

	}

	private static void auditLogInt(int objectTypeId, int objectId, String fieldName, int intValue) {
		String statement = "INSERT INTO [dbo].[AuditLog] (userId, objectTypeId, objectId, fieldName, intValue, entryDate, fieldCode) "
				+ "VALUES (" + Main.getCurrentUser().get_id() + "," + objectTypeId + "," + objectId + ", '" + fieldName + "'," + intValue + ", '" + new Timestamp(System
						.currentTimeMillis()) + "', " + 2 + ")";
		Connection conn = ConnectionResources.getConnection();
		try {
			ps = conn.prepareStatement(statement);
			ps.execute();
			conn.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}

	}

	private static void auditLogDate(int objectTypeId, int objectId, String fieldName, Date dateValue) {
		String statement = "INSERT INTO [dbo].[AuditLog] (userId, objectTypeId, objectId, fieldName, dateValue, entryDate, fieldCode) "
				+ "VALUES (" + Main.getCurrentUser().get_id() + "," + objectTypeId + "," + objectId + ", '" + fieldName + "','" + dateValue + "', '" + new Timestamp(System
						.currentTimeMillis()) + "', " + 3 + ")";
		Connection conn = ConnectionResources.getConnection();
		try {
			ps = conn.prepareStatement(statement);
			ps.execute();
			conn.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}

	}

	private static void auditLogString(int objectTypeId, int objectId, String fieldName, String stringValue) {

		String statement = "INSERT INTO [dbo].[AuditLog] (userId, objectTypeId, objectId, fieldName, stringValue, entryDate, fieldCode) "
				+ "VALUES (" + Main.getCurrentUser().get_id() + "," + objectTypeId + "," + objectId + ", '" + fieldName + "','" + stringValue + "', '" + new Timestamp(System
						.currentTimeMillis()) + "', " + 4 + ")";
		Connection conn = ConnectionResources.getConnection();
		try {
			ps = conn.prepareStatement(statement);
			ps.execute();
			conn.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}

	}

	public static ArrayList<LogEntry> getAuditSearchResults(int userId, int caseId, int clientId) {
		boolean usr = false;
		boolean cse = false;
		boolean cli = false;
		if (userId != 0)
			usr = true;
		if (caseId != 0)
			cse = true;
		if (clientId != 0)
			cli = true;

		String statement = "";
		if (usr && cse && cli) {
			statement = "SELECT * FROM [dbo].[auditLog] WHERE userId =" + userId + " AND caseId =" + caseId + " AND clientId =" + clientId + " ORDER BY entryDate ASC";
		} else if (usr && cli) {
			statement = "SELECT * FROM [dbo].[auditLog] WHERE userId =" + userId + " AND clientId =" + clientId + " ORDER BY entryDate ASC";
		} else if (usr && cse) {
			statement = "SELECT * FROM [dbo].[auditLog] WHERE userId =" + userId + " AND caseId =" + caseId + " ORDER BY entryDate ASC";
		} else if (cse && cli) {
			statement = "SELECT * FROM [dbo].[auditLog] WHERE caseId =" + caseId + " AND clientId =" + clientId + " ORDER BY entryDate ASC";
		} else if (usr) {
			statement = "SELECT * FROM [dbo].[auditLog] WHERE userId =" + userId + " ORDER BY entryDate ASC";
		} else if (cse) {
			statement = "SELECT * FROM [dbo].[auditLog] WHERE caseId =" + caseId + " ORDER BY entryDate ASC";
		} else if (cli) {
			statement = "SELECT * FROM [dbo].[auditLog] WHERE clientId =" + clientId + " ORDER BY entryDate ASC";
		}
		ArrayList<LogEntry> logs = new ArrayList<LogEntry>();
		Connection conn = ConnectionResources.getConnection();
		try {
			ps = conn.prepareStatement(statement);
			rs = ps.executeQuery();
			while (rs.next()) {

				int i = 1;
				LogEntry tempEntry = new LogEntry(rs.getInt(i += 1), rs.getInt(i += 1), rs.getInt(i += 1), rs.getString(i += 1), rs.getString(i += 1), rs.getDate(i += 1), rs
						.getBoolean(i += 1), rs
								.getInt(i += 1), rs.getString(i += 1), rs.getInt(i += 1));
				logs.add(tempEntry);
			}

			conn.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return logs;
	}
}
