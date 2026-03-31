package com.shale.core.dto;

import java.time.LocalDateTime;

public final class CaseUpdateDto {

	private final long id;
	private final long caseId;
	private final String noteText;
	private final LocalDateTime createdAt;
	private final LocalDateTime updatedAt;
	private final Integer createdByUserId;
	private final String createdByDisplayName;

	public CaseUpdateDto(long id,
			long caseId,
			String noteText,
			LocalDateTime createdAt,
			LocalDateTime updatedAt,
			Integer createdByUserId,
			String createdByDisplayName) {
		this.id = id;
		this.caseId = caseId;
		this.noteText = noteText == null ? "" : noteText;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
		this.createdByUserId = createdByUserId;
		this.createdByDisplayName = createdByDisplayName == null ? "" : createdByDisplayName;
	}

	public long getId() {
		return id;
	}

	public long getCaseId() {
		return caseId;
	}

	public String getNoteText() {
		return noteText;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

	public Integer getCreatedByUserId() {
		return createdByUserId;
	}

	public String getCreatedByDisplayName() {
		return createdByDisplayName;
	}
}
