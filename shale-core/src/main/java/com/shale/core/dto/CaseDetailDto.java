package com.shale.core.dto;

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
	private final LocalDateTime updatedAt;
	private final byte[] rowVer;

	public CaseDetailDto(long caseId,
			String caseNumber,
			String caseName,
			String description,
			String caseStatus,
			LocalDateTime updatedAt,
			byte[] rowVer) {
		this.caseId = caseId;
		this.caseNumber = caseNumber;
		this.caseName = caseName == null ? "" : caseName;
		this.description = description == null ? "" : description;
		this.caseStatus = caseStatus == null ? "" : caseStatus;
		this.updatedAt = updatedAt;
		this.rowVer = rowVer == null ? new byte[0] : Arrays.copyOf(rowVer, rowVer.length);
	}

	public long getCaseId() {
		return caseId;
	}

	public String getCaseNumber() {
		return caseNumber;
	}

	public String getCaseName() {
		return caseName;
	}

	public String getDescription() {
		return description;
	}

	public String getCaseStatus() {
		return caseStatus;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

	public byte[] getRowVer() {
		return Arrays.copyOf(rowVer, rowVer.length);
	}
}
