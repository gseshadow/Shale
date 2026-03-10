package com.shale.core.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;

/**
 * Detail DTO for a case used by the Case view edit workflow.
 */
public final class CaseDetailDto {

	private final long caseId;
	private final String caseNumber;
	private final String caseName;
	private final String description;
	private final String caseStatus;

	private final Integer practiceAreaId;

	private final LocalDate callerDate;
	private final String callerTime;
	private final LocalDate acceptedDate;
	private final LocalDate closedDate;
	private final LocalDate deniedDate;

	private final LocalDate dateOfMedicalNegligence;
	private final LocalDate dateMedicalNegligenceWasDiscovered;
	private final LocalDate dateOfInjury;
	private final LocalDate statuteOfLimitations;
	private final LocalDate tortNoticeDeadline;
	private final LocalDate discoveryDeadline;

	private final String clientEstate;
	private final String officePrinterCode;
	private final Boolean medicalRecordsReceived;
	private final Boolean feeAgreementSigned;
	private final LocalDate dateFeeAgreementSigned;

	private final Boolean acceptedChronology;
	private final Boolean acceptedConsultantExpertSearch;
	private final Boolean acceptedTestifyingExpertSearch;
	private final Boolean acceptedMedicalLiterature;
	private final String acceptedDetail;

	private final Boolean deniedChronology;
	private final String deniedDetail;

	private final String summary;
	private final String receivedUpdates;

	private final LocalDateTime updatedAt;
	private final byte[] rowVer;

	public CaseDetailDto(long caseId,
			String caseNumber,
			String caseName,
			String description,
			String caseStatus,
			Integer practiceAreaId,
			LocalDate callerDate,
			String callerTime,
			LocalDate acceptedDate,
			LocalDate closedDate,
			LocalDate deniedDate,
			LocalDate dateOfMedicalNegligence,
			LocalDate dateMedicalNegligenceWasDiscovered,
			LocalDate dateOfInjury,
			LocalDate statuteOfLimitations,
			LocalDate tortNoticeDeadline,
			LocalDate discoveryDeadline,
			String clientEstate,
			String officePrinterCode,
			Boolean medicalRecordsReceived,
			Boolean feeAgreementSigned,
			LocalDate dateFeeAgreementSigned,
			Boolean acceptedChronology,
			Boolean acceptedConsultantExpertSearch,
			Boolean acceptedTestifyingExpertSearch,
			Boolean acceptedMedicalLiterature,
			String acceptedDetail,
			Boolean deniedChronology,
			String deniedDetail,
			String summary,
			String receivedUpdates,
			LocalDateTime updatedAt,
			byte[] rowVer) {
		this.caseId = caseId;
		this.caseNumber = caseNumber;
		this.caseName = caseName == null ? "" : caseName;
		this.description = description == null ? "" : description;
		this.caseStatus = caseStatus == null ? "" : caseStatus;
		this.practiceAreaId = practiceAreaId;
		this.callerDate = callerDate;
		this.callerTime = callerTime == null ? "" : callerTime;
		this.acceptedDate = acceptedDate;
		this.closedDate = closedDate;
		this.deniedDate = deniedDate;
		this.dateOfMedicalNegligence = dateOfMedicalNegligence;
		this.dateMedicalNegligenceWasDiscovered = dateMedicalNegligenceWasDiscovered;
		this.dateOfInjury = dateOfInjury;
		this.statuteOfLimitations = statuteOfLimitations;
		this.tortNoticeDeadline = tortNoticeDeadline;
		this.discoveryDeadline = discoveryDeadline;
		this.clientEstate = clientEstate == null ? "" : clientEstate;
		this.officePrinterCode = officePrinterCode == null ? "" : officePrinterCode;
		this.medicalRecordsReceived = medicalRecordsReceived;
		this.feeAgreementSigned = feeAgreementSigned;
		this.dateFeeAgreementSigned = dateFeeAgreementSigned;
		this.acceptedChronology = acceptedChronology;
		this.acceptedConsultantExpertSearch = acceptedConsultantExpertSearch;
		this.acceptedTestifyingExpertSearch = acceptedTestifyingExpertSearch;
		this.acceptedMedicalLiterature = acceptedMedicalLiterature;
		this.acceptedDetail = acceptedDetail == null ? "" : acceptedDetail;
		this.deniedChronology = deniedChronology;
		this.deniedDetail = deniedDetail == null ? "" : deniedDetail;
		this.summary = summary == null ? "" : summary;
		this.receivedUpdates = receivedUpdates == null ? "" : receivedUpdates;
		this.updatedAt = updatedAt;
		this.rowVer = rowVer == null ? new byte[0] : Arrays.copyOf(rowVer, rowVer.length);
	}

	public long getCaseId() { return caseId; }
	public String getCaseNumber() { return caseNumber; }
	public String getCaseName() { return caseName; }
	public String getDescription() { return description; }
	public String getCaseStatus() { return caseStatus; }
	public Integer getPracticeAreaId() { return practiceAreaId; }
	public LocalDate getCallerDate() { return callerDate; }
	public String getCallerTime() { return callerTime; }
	public LocalDate getAcceptedDate() { return acceptedDate; }
	public LocalDate getClosedDate() { return closedDate; }
	public LocalDate getDeniedDate() { return deniedDate; }
	public LocalDate getDateOfMedicalNegligence() { return dateOfMedicalNegligence; }
	public LocalDate getDateMedicalNegligenceWasDiscovered() { return dateMedicalNegligenceWasDiscovered; }
	public LocalDate getDateOfInjury() { return dateOfInjury; }
	public LocalDate getStatuteOfLimitations() { return statuteOfLimitations; }
	public LocalDate getTortNoticeDeadline() { return tortNoticeDeadline; }
	public LocalDate getDiscoveryDeadline() { return discoveryDeadline; }
	public String getClientEstate() { return clientEstate; }
	public String getOfficePrinterCode() { return officePrinterCode; }
	public Boolean getMedicalRecordsReceived() { return medicalRecordsReceived; }
	public Boolean getFeeAgreementSigned() { return feeAgreementSigned; }
	public LocalDate getDateFeeAgreementSigned() { return dateFeeAgreementSigned; }
	public Boolean getAcceptedChronology() { return acceptedChronology; }
	public Boolean getAcceptedConsultantExpertSearch() { return acceptedConsultantExpertSearch; }
	public Boolean getAcceptedTestifyingExpertSearch() { return acceptedTestifyingExpertSearch; }
	public Boolean getAcceptedMedicalLiterature() { return acceptedMedicalLiterature; }
	public String getAcceptedDetail() { return acceptedDetail; }
	public Boolean getDeniedChronology() { return deniedChronology; }
	public String getDeniedDetail() { return deniedDetail; }
	public String getSummary() { return summary; }
	public String getReceivedUpdates() { return receivedUpdates; }
	public LocalDateTime getUpdatedAt() { return updatedAt; }

	public byte[] getRowVer() {
		return Arrays.copyOf(rowVer, rowVer.length);
	}
}
