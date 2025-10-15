package dataStructures;

import java.io.Serializable;
import java.sql.Date;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import application.Main;
import connections.ConnectionResources;
import connections.Server;
import javafx.scene.paint.Color;

public class Case implements Serializable {

	/**
	 * 
	 */
	private boolean potential = true;
	private static final long serialVersionUID = -8557823655318053326L;
	private LocalTime callerTime;
	private LocalDate callerDate;
	private int callerId;
	private Contact caller;

	private LocalDate clientDOB;
	private boolean clientEstate = false;
	private int clientId;
	private Contact client;

	private String incidentPracticeAreaString = "";
	private LocalDate incidentMedNegOccurred;
	private LocalDate incidentMedNegDiscovered;
	private LocalDate incidentDateOfInjury;
	private LocalDate incidentStatuteOfLimitations;
	private LocalDate incidentTortNoticeDeadline;
	private LocalDate incidentDiscoveryDeadline;
	private int incidentId;
	private Incident incident;

	private String followUpQuestionsForPatient = "";
	private boolean followUpMeetingWithClient = false;
	private boolean followUpNurseReview = false;
	private boolean followUpExpertReview = false;

	private String receivedUpdates = "";

	private boolean accepted = false;
	private boolean acceptedChronology = false;
	private boolean acceptedConsultantExpertSearch = false;
	private boolean acceptedTestifyingExpertSearch = false;
	private boolean acceptedSupportiveMedicalLiterature = false;
	private LocalDate acceptedDate;
	private String acceptedDetail = "";

	private boolean denied = false;
	private boolean deniedChronology = false;
	private String deniedDetails = "";
	private LocalDate deniedDate;

	private boolean inTrial = false;
	private LocalDate trialDate;

	private LocalDate closedDate;
	private boolean closed = false;

	private String fileName = "";
	private String prefix = "";
	private String suffix = "";

	private boolean transferred = false;
	private boolean deleted = false;

	private int officeIntakePersonId;
	private User officeIntakePerson;
	private String officeResponsibleAttorneyName = "";
	private int officeResponsibleAttorneyId;
	private User officeResponsibleAttorney;
	private String officePrinterCode = "";

	private int casePracticeAreaId;
	private PracticeArea casePracticeArea;
	private int caseStatusId;
	private Status caseStatus;
	private String caseStatusString;
	private String caseName;
	private int _id;
	private int caseOpposingCounselId;
	private Contact caseOpposingCounsel;
	private int caseOrganizationId;
	private Organization caseOrganization;
	private int caseJudgeId;
	private Contact caseJudge;
	private String caseNumber = "";
	private boolean sameAsCaller = false;
	private boolean feeAgreementSigned = false;
	private LocalDate feeAgreementSignedDate;

	private ArrayList<Provider> providers = new ArrayList<>();
	private ArrayList<Facility> facilities = new ArrayList<>();
	private ArrayList<Contact> contacts = new ArrayList<>();

	public Case() {
	}

	public String getIncidentPotentialDefendants() {
		if (incident != null)
			return incident.getPotentialDefendants();
		return "";
	}

	public String getIncidentDescription() {
		if (incident == null)
			return "";
		return incident.getIncidentDescription();
	}

	public String getIncidentCaseStatus() {
		if (incident == null)
			return "";
		return incident.getIncidentCaseStatus();
	}

	public boolean isFollowUpExpertReview() {
		return followUpExpertReview;
	}

	public void setFollowUpExpertReview(boolean followUpExpertReview) {
		this.followUpExpertReview = followUpExpertReview;
	}

	public String getCallerNameFirst() {
		if (caller == null)
			return "";
		return caller.getName_first();
	}

	public String getCallerNameLast() {
		if (caller == null)
			return "";
		return caller.getName_last();
	}

	public String getCallerPhone() {
		if (caller == null)
			return "";
		return caller.getPhone_cell();
	}

	public String getCallerReferredFrom() {
		if (caller == null) {
			return "";
		}
		return caller.getReferredFrom();
	}

	public LocalTime getCallerTime() {
		return callerTime;
	}

	public void setCallerTime(Time callerTime) {
		if (callerTime != null)
			this.callerTime = callerTime.toLocalTime();
	}

	public void setCallerTime(LocalTime callerTime) {
		this.callerTime = callerTime;
	}

	public LocalDate getCallerDate() {
		return callerDate;
	}

	public void setCallerDate(Date callerDate) {
		if (callerDate != null)
			this.callerDate = callerDate.toLocalDate();
	}

	public void setCallerDate(LocalDate callerDate) {
		this.callerDate = callerDate;
	}

	public String getCallerDateString() {
		if (callerDate == null) {
			return "";
		} else
			return callerDate.toString();
	}

	public int getCallerId() {
		return callerId;
	}

	public void setCallerId(int callerId) {
		this.callerId = callerId;
	}

	public Contact getCaller() {
		return caller;
	}

	public void setCaller(Contact caller) {
		this.caller = caller;
	}

	public String getClientNameFirst() {
		if (client == null)
			return "";
		return client.getName_first();
	}

	public String getClientNameLast() {
		if (client == null)
			return "";
		return client.getName_last();
	}

	public String getClientAddress() {
		if (client == null)
			return "";
		return client.getAddress_home();
	}

	public String getClientPhone() {
		if (client == null)
			return "";
		return client.getPhone_cell();
	}

	public String getClientEmail() {
		if (client == null)
			return "";
		return client.getEmail_personal();
	}

	public Date getClientDOBSQL() {
		if (clientDOB != null) {
			return Date.valueOf(clientDOB);
		}
		return null;

	}

	public LocalDate getClientDOB() {
		if (client != null)
			return client.getDobLocalDateFormat();
		return null;
	}

	public void setClientDOB(Date clientDOB) {
		if (clientDOB != null)
			this.clientDOB = clientDOB.toLocalDate();
	}

	public void setClientDOB(LocalDate clientDOB) {
		this.clientDOB = clientDOB;
	}

	public boolean isClientDeceased() {
		if (client != null)
			return client.isDeceased();
		return false;
	}

	public boolean isClientEstate() {
		return clientEstate;
	}

	public void setClientEstate(boolean clientEstate) {
		this.clientEstate = clientEstate;
	}

	public int getClientId() {
		return clientId;
	}

	public void setClientId(int clientId) {
		this.clientId = clientId;
	}

	public Contact getClient() {
		return client;
	}

	public void setClient(Contact client) {
		this.client = client;
	}

	public String getIncidentUpdates() {
		if (incident == null)
			return "";
		return incident.getIncidentUpdates();
	}

	public String getPracticeArea() {

		if (incidentPracticeAreaString == null)
			return "";
		return incidentPracticeAreaString;
	}

	public void setPracticeArea(String practiceArea) {
		this.incidentPracticeAreaString = practiceArea;
	}

	public LocalDate getIncidentMedNegOccurred() {
		if (incident == null)
			return null;
		return incident.getDateMedNegOccurred();
	}

	public void setIncidentMedNegOccurred(Date incidentMedNegOccurred) {
		if (incidentMedNegOccurred != null)
			this.incidentMedNegOccurred = incidentMedNegOccurred.toLocalDate();
	}

	public void setIncidentMedNegOccurred(LocalDate incidentMedNegOccurred) {
		this.incidentMedNegOccurred = incidentMedNegOccurred;
	}

	public LocalDate getIncidentMedNegDiscovered() {
		if (incident == null)
			return null;
		return incident.getDateMedNegDiscovered();
	}

	public void setIncidentMedNegDiscovered(Date incidentMedNegDiscovered) {
		if (incidentMedNegDiscovered != null)
			this.incidentMedNegDiscovered = incidentMedNegDiscovered.toLocalDate();
	}

	public void setIncidentMedNegDiscovered(LocalDate incidentMedNegDiscovered) {
		this.incidentMedNegDiscovered = incidentMedNegDiscovered;
	}

	public LocalDate getIncidentDateOfInjury() {
		if (incident == null)
			return null;
		return incident.getDateOfInjury();
	}

	public void setIncidentDateOfInjury(Date incidentDateOfInjury) {
		if (incidentDateOfInjury != null)
			this.incidentDateOfInjury = incidentDateOfInjury.toLocalDate();
	}

	public void setIncidentDateOfInjury(LocalDate incidentDateOfInjury) {
		this.incidentDateOfInjury = incidentDateOfInjury;
	}

	public LocalDate getIncidentStatuteOfLimitations() {
		if (incident == null)
			return null;
		return incident.getIncidentStatuteOfLimitations();
	}

	public void setIncidentStatuteOfLimitations(Date incidentStatuteOfLimitations) {
		if (incidentStatuteOfLimitations != null)
			this.incidentStatuteOfLimitations = incidentStatuteOfLimitations.toLocalDate();
	}

	public void setIncidentStatuteOfLimitations(LocalDate incidentStatuteOfLimitations) {
		this.incidentStatuteOfLimitations = incidentStatuteOfLimitations;
	}

	public String getIncidentStatuteOfLimitationsString() {
		if (incident != null) {
			return dateStringGenerator(incident.getIncidentStatuteOfLimitations());
		}
		return "";
	}

	public LocalDate getIncidentTortNoticeDeadline() {
		return incidentTortNoticeDeadline;
	}

	public void setIncidentTortNoticeDeadline(Date incidentTortNoticeDeadline) {
		if (incidentTortNoticeDeadline != null)
			this.incidentTortNoticeDeadline = incidentTortNoticeDeadline.toLocalDate();
	}

	public void setIncidentTortNoticeDeadline(LocalDate incidentTortNoticeDeadline) {
		this.incidentTortNoticeDeadline = incidentTortNoticeDeadline;
	}

	public LocalDate getIncidentDiscoveryDeadline() {
		return incidentDiscoveryDeadline;
	}

	public void setIncidentDiscoveryDeadline(Date incidentDiscoveryDeadline) {
		if (incidentDiscoveryDeadline != null)
			this.incidentDiscoveryDeadline = incidentDiscoveryDeadline.toLocalDate();
	}

	public void setIncidentDiscoveryDeadline(LocalDate incidentDiscoveryDeadline) {
		this.incidentDiscoveryDeadline = incidentDiscoveryDeadline;
	}

	public String getPotentialDefendants() {
		if (incident != null) {
			if (incident.getPotentialDefendants() == null)
				return "";
			return incident.getPotentialDefendants();
		} else
			return "";
	}

	public String getIncidentFacilitiesInvolved() {
		if (incident != null) {
			if (incident.getFacilitiesInvolved() == null)
				return "";
			return incident.getFacilitiesInvolved();
		} else
			return "";
	}

	public boolean isIncidentMedRecsInHand() {
		if (incident == null)
			return false;
		return incident.isIncidentMedRecsInHand();
	}

	public int getIncidentId() {
		return incidentId;
	}

	public void setIncidentId(int incidentId) {
		this.incidentId = incidentId;
	}

	public Incident getIncident() {
		return incident;
	}

	public void setIncident(Incident incident) {
		this.incident = incident;
	}

	public String getClientCondition() {
		if (client == null)
			return "";
		return client.getCondition();
	}

	public String getIncidentSummary() {
		if (incident == null)
			return "";
		return incident.getIncidentSummary();
	}

	public String getFollowUpQuestionsForPatient() {
		if (followUpQuestionsForPatient == null)
			return "";
		return followUpQuestionsForPatient;
	}

	public void setFollowUpQuestionsForPatient(String followUpQuestionsForPatient) {
		this.followUpQuestionsForPatient = followUpQuestionsForPatient;
	}

	public String getReceivedUpdates() {
		if (receivedUpdates == null)
			return "";
		return receivedUpdates;
	}

	public void setReceivedUpdates(String receivedUpdates) {
		this.receivedUpdates = receivedUpdates;
	}

	public boolean isFollowUpMeetingWithClient() {
		return followUpMeetingWithClient;
	}

	public void setFollowUpMeetingWithClient(boolean followUpMeetingWithClient) {
		this.followUpMeetingWithClient = followUpMeetingWithClient;
	}

	public boolean isFollowUpNurseReview() {
		return followUpNurseReview;
	}

	public void setFollowUpNurseReview(boolean followUpNurseReview) {
		this.followUpNurseReview = followUpNurseReview;
	}

	public boolean isFollowUpDoctorReview() {
		return followUpExpertReview;
	}

	public void setFollowUpDoctorReview(boolean followUpDoctorReview) {
		this.followUpExpertReview = followUpDoctorReview;
	}

	public boolean isAcceptedChronology() {
		return acceptedChronology;
	}

	public void setAcceptedChronology(boolean acceptedChronology) {
		this.acceptedChronology = acceptedChronology;
	}

	public boolean isAcceptedConsultantExpertSearch() {
		return acceptedConsultantExpertSearch;
	}

	public boolean isAcceptedTestifyingExpertSearch() {
		return acceptedTestifyingExpertSearch;
	}

	public void setAcceptedTestifyingExpertSearch(boolean acceptedTestifyingExpertSearch) {
		this.acceptedTestifyingExpertSearch = acceptedTestifyingExpertSearch;
	}

	public boolean isAcceptedSupportiveMedicalLiterature() {
		return acceptedSupportiveMedicalLiterature;
	}

	public void setAcceptedSupportiveMedicalLiterature(boolean acceptedSupportiveMedicalLiterature) {
		this.acceptedSupportiveMedicalLiterature = acceptedSupportiveMedicalLiterature;
	}

	public LocalDate getAcceptedDate() {
		return acceptedDate;
	}

	public void setAcceptedDate(Date acceptedDate) {
		if (acceptedDate != null)
			this.acceptedDate = acceptedDate.toLocalDate();
	}

	public void setAcceptedDate(LocalDate acceptedDate) {
		this.acceptedDate = acceptedDate;
	}

	public String getAcceptedDetail() {
		if (acceptedDetail == null)
			return "";
		return acceptedDetail;
	}

	public void setAcceptedDetail(String acceptedDetail) {
		this.acceptedDetail = acceptedDetail;
	}

	public boolean isDeniedChronology() {
		return deniedChronology;
	}

	public void setDeniedChronology(boolean deniedChronology) {
		this.deniedChronology = deniedChronology;
	}

	public String getDeniedDetails() {
		if (deniedDetails == null)
			return "";
		return deniedDetails;
	}

	public void setDeniedDetails(String deniedDetails) {
		this.deniedDetails = deniedDetails;
	}

	public Date getDeniedDateSQL() {
		if (deniedDate != null) {
			return Date.valueOf(deniedDate);
		}
		return null;

	}

	public LocalDate getDeniedDate() {
		return deniedDate;
	}

	public void setDeniedDate(Date deniedDate) {
		if (deniedDate != null)
			this.deniedDate = deniedDate.toLocalDate();
	}

	public void setDeniedDate(LocalDate deniedDate) {
		this.deniedDate = deniedDate;
	}

	public LocalDate getClosedDate() {
		return closedDate;
	}

	public void setClosedDate(Date closedDate) {
		if (closedDate != null)
			this.closedDate = closedDate.toLocalDate();
	}

	public void setClosedDate(LocalDate closedDate) {
		this.closedDate = closedDate;
	}

	public void setClosed(boolean closed) {
		this.closed = closed;
	}

	public boolean isClosed() {
		return this.closed;
	}

	public String getFileName() {
		if (fileName == null)
			return "";
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public String getPrefix() {
		return prefix;
	}

	public void setPrefix(String prefix) {
		this.prefix = prefix;
	}

	public String getSuffix() {
		return suffix;
	}

	public void setSuffix(String suffix) {
		this.suffix = suffix;
	}

	public boolean isAccepted() {
		return accepted;
	}

	public void setAccepted(boolean accepted) {
		this.accepted = accepted;
	}

	public boolean isDenied() {
		return denied;
	}

	public void setDenied(boolean denied) {
		this.denied = denied;
	}

	public int getClientAge() {
		int clientAge = 0;
		if (client != null) {
			clientAge = LocalDate.now().getYear() - client.getDate_of_birth().toLocalDate().getYear();

			if (client.getDate_of_birth().toLocalDate().getMonthValue() > LocalDate.now().getMonthValue()) {
				clientAge -= 1;
				if (clientAge < 0) {
					clientAge = 0;
				}
			}
		}

		return clientAge;
	}

	public String getIncidentPracticeArea() {
		return incidentPracticeAreaString;
	}

	public void setIncidentPracticeArea(String incidentPracticeArea) {
		this.incidentPracticeAreaString = incidentPracticeArea;
	}

	public void setAcceptedConsultantExpertSearch(boolean acceptedConsultantExpertSearch) {
		this.acceptedConsultantExpertSearch = acceptedConsultantExpertSearch;
	}

	public boolean isTransferred() {
		return transferred;
	}

	public void setTransferred(boolean transferred) {
		this.transferred = transferred;
	}

	public String getOfficeIntakePersonName() {
		if (officeIntakePerson == null) {
			return "";
		}
		return officeIntakePerson.getNameFull();
	}

	public int getOfficeIntakePersonId() {
		return officeIntakePersonId;
	}

	public void setOfficeIntakePersonId(int officeIntakePersonId) {
		this.officeIntakePersonId = officeIntakePersonId;
	}

	public User getOfficeIntakePerson() {
		if (officeIntakePerson == null) {
			officeIntakePerson = Main.getUsers().get(officeIntakePersonId);
		}
		return officeIntakePerson;
	}

	public void setOfficeIntakePerson(User officeIntakePerson) {
		this.officeIntakePerson = officeIntakePerson;
	}

	public String getOfficeResponsibleAttorneyName() {
		if (officeResponsibleAttorneyName == null) {
			return "";
		}
		return officeResponsibleAttorneyName;
	}

	public void setOfficeResponsibleAttorneyName(String officeResponsibleAttorneyName) {
		this.officeResponsibleAttorneyName = officeResponsibleAttorneyName;
	}

	public int getOfficeResponsibleAttorneyId() {
		return officeResponsibleAttorneyId;
	}

	public void setOfficeResponsibleAttorneyId(int officeResponsibleAttorneyId) {
		this.officeResponsibleAttorneyId = officeResponsibleAttorneyId;
	}

	public User getOfficeResponsibleAttorney() {
		return officeResponsibleAttorney;
	}

	public void setOfficeResponsibleAttorney(User officeResponsibleAttorney) {
		this.officeResponsibleAttorney = officeResponsibleAttorney;
	}

	public String getOfficePrinterCode() {

		if (officePrinterCode == null) {
			return "";
		}
		return officePrinterCode;
	}

	public void setOfficePrinterCode(String officePrinterCode) {
		this.officePrinterCode = officePrinterCode;
	}

	@Override
	public String toString() {
		return getCaseName();

	}

	public int getCasePracticeAreaId() {
		return casePracticeAreaId;
	}

	public void setCasePracticeAreaId(int casePracticeAreaId) {
		this.casePracticeAreaId = casePracticeAreaId;
	}

	public String getCaseStatusString() {
		if (caseStatusString == null)
			return "";
		return caseStatusString;
	}

	public void setCaseStatusString(String status) {
		caseStatusString = status;
	}

	public Status getCaseStatus() {
		if (caseStatus == null)
			return Main.getStatusChoices().get(7);
		return caseStatus;
	}

	public void setCaseStatus(Status caseStatus) {
		this.caseStatus = caseStatus;
	}

	public int get_id() {
		return _id;
	}

	public void set_id(int _id) {
		this._id = _id;
	}

	public String getCaseName() {
		if (caseName != null)
			return caseName;
		return getClientNameLast() + ", " + getClientNameFirst();
	}

	public void setCaseName(String caseName) {
		this.caseName = caseName;
	}

	public PracticeArea getCasePracticeArea() {
		if (casePracticeArea == null) {
			casePracticeArea = new PracticeArea();
		}
		return casePracticeArea;
	}

	public void setCasePracticeArea(PracticeArea casePracticeArea) {
		this.casePracticeArea = casePracticeArea;
	}

	public int getCaseStatusId() {
		return caseStatusId;
	}

	public void setCaseStatusId(int caseStatusId) {
		this.caseStatusId = caseStatusId;
	}

	public int getCaseOpposingCounselId() {
		return caseOpposingCounselId;
	}

	public void setCaseOpposingCounselId(int caseOpposingCounselId) {
		this.caseOpposingCounselId = caseOpposingCounselId;
	}

	public int getCaseOrganizationId() {
		return caseOrganizationId;
	}

	public void setCaseOrganizationId(int caseOrganizationId) {
		this.caseOrganizationId = caseOrganizationId;
	}

	public Organization getCaseOrganization() {
		return caseOrganization;
	}

	public void setCaseOrganization(Organization caseOrganization) {
		this.caseOrganization = caseOrganization;
	}

	public Contact getCaseOpposingCounsel() {
		return caseOpposingCounsel;
	}

	public void setCaseOpposingCounsel(Contact caseOpposingCounsel) {
		this.caseOpposingCounsel = caseOpposingCounsel;
	}

	public int getCaseJudgeId() {
		return caseJudgeId;
	}

	public void setCaseJudgeId(int caseJudgeId) {
		this.caseJudgeId = caseJudgeId;
	}

	public Contact getCaseJudge() {
		return caseJudge;
	}

	public void setCaseJudge(Contact caseJudge) {
		this.caseJudge = caseJudge;
	}

	public String getCaseNumber() {
		if (caseNumber == null)
			return "";
		return caseNumber;
	}

	public void setCaseNumber(String caseNumber) {
		this.caseNumber = caseNumber;
	}

	public boolean isDeleted() {
		return deleted;
	}

	public boolean isInTrial() {
		return inTrial;
	}

	public LocalDate getTrialDate() {
		return trialDate;
	}

	public void setTrialDate(Date trialDate) {
		if (trialDate != null)
			this.trialDate = trialDate.toLocalDate();
	}

	public void setTrialDate(LocalDate trialDate) {
		this.trialDate = trialDate;
	}

	public void setInTrial(boolean inTrial) {
		this.inTrial = inTrial;
	}

	public void setDeleted(boolean deleted) {
		this.deleted = deleted;
	}

	public ArrayList<Provider> getProviders() {
		return providers;
	}

	public void setProviders(ArrayList<Provider> providers) {
		this.providers = providers;
	}

	public ArrayList<Integer> getFacilityIds() {
		return Server.getFacilitiesByCaseId(_id, ConnectionResources.getConnection());
	}

	public ArrayList<Facility> getFacilities() {
		facilities.clear();
		for (int i : getFacilityIds()) {
			facilities.add(Main.getFacilities().get(i));
		}

		return facilities;
	}

	public void setFacilities(ArrayList<Facility> facilities) {
		this.facilities = facilities;
	}

	public ArrayList<Contact> getContacts() {
		return contacts;
	}

	public void setContacts(ArrayList<Contact> contacts) {
		this.contacts = contacts;
	}

	public Time getCallerTimeSQL() {
		if (callerTime != null) {
			return Time.valueOf(callerTime);
		}
		return null;
	}

	public Date getIncidentDateOfInjurySQL() {
		if (incidentDateOfInjury != null) {
			return Date.valueOf(incidentDateOfInjury);
		}
		return null;

	}

	public Date getIncidentMedNegOccurredSQL() {
		if (incidentMedNegOccurred != null) {
			return Date.valueOf(incidentMedNegOccurred);
		}
		return null;

	}

	public Date getIncidentMedNegDiscoveredSQL() {
		if (incidentMedNegDiscovered != null) {
			return Date.valueOf(incidentMedNegDiscovered);
		}
		return null;

	}

	public Date getAcceptedDateSQL() {
		if (acceptedDate != null) {
			return Date.valueOf(acceptedDate);
		}
		return null;

	}

	public Date getClosedDateSQL() {
		if (closedDate != null) {
			return Date.valueOf(closedDate);
		}
		return null;

	}

	public Date getTrialDateSQL() {
		if (trialDate != null) {
			return Date.valueOf(trialDate);
		}
		return null;

	}

	public Date getIncidentStatuteOfLimitationsSQL() {
		if (incidentStatuteOfLimitations != null) {
			return Date.valueOf(incidentStatuteOfLimitations);
		}
		return null;

	}

	public Date getIncidentTortNoticeDeadlineSQL() {
		if (incidentTortNoticeDeadline != null) {
			return Date.valueOf(incidentTortNoticeDeadline);
		}
		return null;

	}

	public Date getIncidentDiscoveryDeadlineSQL() {
		if (incidentDiscoveryDeadline != null) {
			return Date.valueOf(incidentDiscoveryDeadline);
		}
		return null;

	}

	public Date getCallerDateSQL() {
		if (callerDate != null) {
			return Date.valueOf(callerDate);
		}
		return null;

	}

	private String dateStringGenerator(LocalDate date) {
		if (date == null)
			return "";
		else {
			String s = date.getMonthValue() + "/" + date.getDayOfMonth() + "/" + date.getYear();
			return s;
		}
	}

	public boolean isSameAsCaller() {
		return sameAsCaller;
	}

	public void setSameAsCaller(boolean sameAsCaller) {
		this.sameAsCaller = sameAsCaller;
	}

	public boolean isPotential() {
		return potential;
	}

	public void setPotential(boolean potential) {
		this.potential = potential;
	}

	public boolean isFeeAgreementSigned() {
		return feeAgreementSigned;
	}

	public void setFeeAgreementSigned(boolean caseFeeAgreementSigned) {
		this.feeAgreementSigned = caseFeeAgreementSigned;
	}

	public void setFeeAgreementSignedDate(Date feeAgreementSignedDate) {
		if (feeAgreementSignedDate != null)
			this.feeAgreementSignedDate = feeAgreementSignedDate.toLocalDate();
	}

	public Date getFeeAgreementSignedDate() {
		if (feeAgreementSignedDate != null) {
			return Date.valueOf(feeAgreementSignedDate);
		}
		return null;
	}

	public Date getFeeAgreementSignedDateSQL() {
		if (feeAgreementSignedDate != null) {
			return Date.valueOf(feeAgreementSignedDate);
		}
		return null;

	}

	public Color getBorderColor() {
		if (incident == null) {
			if (incidentId != 0) {
				incident = Main.getIncidents().get(incidentId);
			}
		}
		if (getIncidentStatuteOfLimitations() != null) {
			if (LocalDate.now().isAfter(incident.getIncidentStatuteOfLimitations())) {
				return Color.DARKRED;
			} else if (LocalDate.now().isAfter(incident.getIncidentStatuteOfLimitations().minusMonths(1))) {// Within 1 months
				return Color.RED;
			} else if (LocalDate.now().isAfter(incident.getIncidentStatuteOfLimitations().minusMonths(3))) {// Within 3 months
				return Color.ORANGERED;
			} else if (LocalDate.now().isAfter(incident.getIncidentStatuteOfLimitations().minusMonths(6))) {// Within 6 months
				return Color.YELLOW;
			} else
				return Color.BLACK;

		}
		return Color.BLACK;
	}

}
