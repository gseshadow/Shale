package application;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalTime;

public class Potential implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -8557823655318053326L;
	private String callerNameFirst = "";
	private String callerNameLast = "";
	private String callerPhone = "";
	private LocalTime callerTime;
	private LocalDate callerDate;

	private String clientNameFirst = "";
	private String clientNameLast = "";
	private String clientAddress = "";
	private String clientPhone = "";
	private String clientEmail = "";
	private LocalDate clientDOB;
	private String clientCondition = "";

	private String incidentPracticeArea = "";
	private LocalDate incidentMedNegOccurred;
	private LocalDate incidentMedNegDiscovered;
	private LocalDate incidentStatuteOfLimitations;
	private String incidentDoctorsInvolved = "";
	private String incidentFacilitiesInvolved = "";
	private boolean incidentMedRecsInHand = false;
	private String incidentSummary = "";
	private String incidentUpdates = "";

	private String followUpQuestionsForPatient = "";
	private boolean followUpMeetingWithClient = false;
	private boolean followUpNurseReview = false;
	private boolean followUpDoctorReview = false;

	private boolean acceptedChronology = false;
	private boolean acceptedConsultantExpertSearch = false;
	private boolean acceptedTestifyingExpertSearch = false;
	private boolean acceptedSupportiveMedicalLiterature = false;
	private String acceptedDetail = "";

	private boolean deniedChronology = false;
	private String deniedDetails = "";

	private String fileName = "";
	private String prefix = "";
	private String suffix = "";

	private boolean accepted = false;
	private boolean rejected = false;

	public Potential() {

	}

	public Potential(String callerNameFirst, String callerNameLast, String callerPhone, LocalTime callerTime,
			LocalDate callerDate, String clientNameFirst, String clientNameLast, String clientAddress,
			String clientPhone, String clientEmail, LocalDate clientDOB, String incidentUpdates,
			String incidentPracticeArea, LocalDate incidentMedNegOccurred, LocalDate incidentMedNegDiscovered,
			LocalDate incidentStatuteOfLimitations, String incidentDoctorsInvolved, String incidentFacilitiesInvolved,
			boolean incidentMedRecsInHand, String clientCondition, String incidentSummary,
			String followUpQuestionsForPatient, boolean followUpMeetingWithClient, boolean followUpNurseReview,
			boolean followUpDoctorReview, boolean acceptedChronology, boolean acceptedCondultantExpertSearch,
			boolean acceptedTestifyingExpertSearch, boolean acceptedSupportiveMedicalLiterature, String acceptedDetail,
			boolean deniedChronology, String deniedDetails, String fileName, boolean accepted, boolean rejected) {
		super();
		this.callerNameFirst = callerNameFirst;
		this.callerNameLast = callerNameLast;
		this.callerPhone = callerPhone;
		this.callerTime = callerTime;
		this.callerDate = callerDate;
		this.clientNameFirst = clientNameFirst;
		this.clientNameLast = clientNameLast;
		this.clientAddress = clientAddress;
		this.clientPhone = clientPhone;
		this.clientEmail = clientEmail;
		this.clientDOB = clientDOB;
		this.incidentUpdates = incidentUpdates;
		this.incidentPracticeArea = incidentPracticeArea;
		this.incidentMedNegOccurred = incidentMedNegOccurred;
		this.incidentMedNegDiscovered = incidentMedNegDiscovered;
		this.incidentStatuteOfLimitations = incidentStatuteOfLimitations;
		this.incidentDoctorsInvolved = incidentDoctorsInvolved;
		this.incidentFacilitiesInvolved = incidentFacilitiesInvolved;
		this.incidentMedRecsInHand = incidentMedRecsInHand;
		this.clientCondition = clientCondition;
		this.incidentSummary = incidentSummary;
		this.followUpQuestionsForPatient = followUpQuestionsForPatient;
		this.followUpMeetingWithClient = followUpMeetingWithClient;
		this.followUpNurseReview = followUpNurseReview;
		this.followUpDoctorReview = followUpDoctorReview;
		this.acceptedChronology = acceptedChronology;
		this.acceptedConsultantExpertSearch = acceptedCondultantExpertSearch;
		this.acceptedTestifyingExpertSearch = acceptedTestifyingExpertSearch;
		this.acceptedSupportiveMedicalLiterature = acceptedSupportiveMedicalLiterature;
		this.acceptedDetail = acceptedDetail;
		this.deniedChronology = deniedChronology;
		this.deniedDetails = deniedDetails;
		this.fileName = fileName;
		this.accepted = accepted;
		this.rejected = rejected;
	}

	public String getCallerNameFirst() {
		if (callerNameFirst == null)
			return "";
		return callerNameFirst;
	}

	public void setCallerNameFirst(String callerNameFirst) {
		this.callerNameFirst = callerNameFirst;
	}

	public String getCallerNameLast() {
		if (callerNameLast == null)
			return "";
		return callerNameLast;
	}

	public void setCallerNameLast(String callerNameLast) {
		this.callerNameLast = callerNameLast;
	}

	public String getCallerPhone() {
		if (callerPhone == null)
			return "";
		return callerPhone;
	}

	public void setCallerPhone(String callerPhone) {
		this.callerPhone = callerPhone;
	}

	public LocalTime getCallerTime() {
		return callerTime;
	}

	public void setCallerTime(LocalTime callerTime) {
		this.callerTime = callerTime;
	}

	public LocalDate getCallerDate() {
		return callerDate;
	}

	public void setCallerDate(LocalDate callerDate) {
		this.callerDate = callerDate;
	}

	public String getClientNameFirst() {
		if (clientNameFirst == null)
			return "";
		return clientNameFirst;
	}

	public void setClientNameFirst(String clientNameFirst) {
		this.clientNameFirst = clientNameFirst;
	}

	public String getClientNameLast() {
		if (clientNameLast == null)
			return "";
		return clientNameLast;
	}

	public void setClientNameLast(String clientNameLast) {
		this.clientNameLast = clientNameLast;
	}

	public String getClientAddress() {
		if (clientAddress == null)
			return "";
		return clientAddress;
	}

	public void setClientAddress(String clientAddress) {
		this.clientAddress = clientAddress;
	}

	public String getClientPhone() {
		if (clientPhone == null)
			return "";
		return clientPhone;
	}

	public void setClientPhone(String clientPhone) {
		this.clientPhone = clientPhone;
	}

	public String getClientEmail() {
		if (clientEmail == null)
			return "";
		return clientEmail;
	}

	public void setClientEmail(String clientEmail) {
		this.clientEmail = clientEmail;
	}

	public LocalDate getClientDOB() {
		return clientDOB;
	}

	public void setClientDOB(LocalDate clientDOB) {
		this.clientDOB = clientDOB;
	}

	public String getIncidentUpdates() {
		if (incidentUpdates == null)
			return "";
		return incidentUpdates;
	}

	public void setIncidentUpdates(String incidentUpdates) {
		this.incidentUpdates = incidentUpdates;
	}

	public String getPracticeArea() {
		if (incidentPracticeArea == null)
			return "";
		return incidentPracticeArea;
	}

	public void setPracticeArea(String practiceArea) {
		this.incidentPracticeArea = practiceArea;
	}

	public LocalDate getIncidentMedNegOccurred() {
		return incidentMedNegOccurred;
	}

	public void setIncidentMedNegOccurred(LocalDate incidentMedNegOccurred) {
		this.incidentMedNegOccurred = incidentMedNegOccurred;
	}

	public LocalDate getIncidentMedNegDiscovered() {
		return incidentMedNegDiscovered;
	}

	public void setIncidentMedNegDiscovered(LocalDate incidentMedNegDiscovered) {
		this.incidentMedNegDiscovered = incidentMedNegDiscovered;
	}

	public LocalDate getIncidentStatuteOfLimitations() {
		return incidentStatuteOfLimitations;
	}

	public void setIncidentStatuteOfLimitations(LocalDate incidentStatuteOfLimitations) {
		this.incidentStatuteOfLimitations = incidentStatuteOfLimitations;
	}

	public String getIncidentDoctorsInvolved() {
		if (incidentDoctorsInvolved == null)
			return "";
		return incidentDoctorsInvolved;
	}

	public void setIncidentDoctorsInvolved(String incidentDoctorsInvolved) {
		this.incidentDoctorsInvolved = incidentDoctorsInvolved;
	}

	public String getIncidentFacilitiesInvolved() {
		if (incidentFacilitiesInvolved == null)
			return "";
		return incidentFacilitiesInvolved;
	}

	public void setIncidentFacilitiesInvolved(String incidentFacilitiesInvolved) {
		this.incidentFacilitiesInvolved = incidentFacilitiesInvolved;
	}

	public boolean isIncidentMedRecsInHand() {
		return incidentMedRecsInHand;
	}

	public void setIncidentMedRecsInHand(boolean incidentMedRecsInHand) {
		this.incidentMedRecsInHand = incidentMedRecsInHand;
	}

	public String getClientCondition() {
		if (clientCondition == null)
			return "";
		return clientCondition;
	}

	public void setClientCondition(String clientCondition) {
		this.clientCondition = clientCondition;
	}

	public String getIncidentSummary() {
		if (incidentSummary == null)
			return "";
		return incidentSummary;
	}

	public void setIncidentSummary(String incidentSummary) {
		this.incidentSummary = incidentSummary;
	}

	public String getFollowUpQuestionsForPatient() {
		if (followUpQuestionsForPatient == null)
			return "";
		return followUpQuestionsForPatient;
	}

	public void setFollowUpQuestionsForPatient(String followUpQuestionsForPatient) {
		this.followUpQuestionsForPatient = followUpQuestionsForPatient;
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
		return followUpDoctorReview;
	}

	public void setFollowUpDoctorReview(boolean followUpDoctorReview) {
		this.followUpDoctorReview = followUpDoctorReview;
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

	public void setAcceptedCondultantExpertSearch(boolean acceptedCondultantExpertSearch) {
		this.acceptedConsultantExpertSearch = acceptedCondultantExpertSearch;
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

	public boolean isRejected() {
		return rejected;
	}

	public void setRejected(boolean rejected) {
		this.rejected = rejected;
	}

	public int getClientAge() {
		int clientAge = 0;
		clientAge = LocalDate.now().getYear() - clientDOB.getYear();

		if (clientDOB.getMonthValue() > LocalDate.now().getMonthValue()) {
			clientAge -= 1;
			if (clientAge < 0) {
				clientAge = 0;
			}
		}

		return clientAge;
	}

}
