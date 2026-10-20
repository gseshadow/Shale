package com.shale.core.dto;

import java.time.LocalDateTime;

public final class CaseUpdateDto {

	private final byte[] rowVersion;
	private final boolean deleted;
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
        this(id, caseId, noteText, createdAt, updatedAt, createdByUserId, createdByDisplayName, null, false);
    }

    public CaseUpdateDto(long id, long caseId, String noteText, LocalDateTime createdAt,
            LocalDateTime updatedAt, Integer createdByUserId, String createdByDisplayName,
            byte[] rowVersion, boolean deleted) {
        this.rowVersion = rowVersion == null ? null : rowVersion.clone();
        this.deleted = deleted;
		this.id = id;
		this.caseId = caseId;
		this.noteText = noteText == null ? "" : noteText;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
		this.createdByUserId = createdByUserId;
		this.createdByDisplayName = createdByDisplayName == null ? "" : createdByDisplayName;
	}

    public byte[] rowVersion() { return rowVersion == null ? null : rowVersion.clone(); }
    public boolean deleted() { return deleted; }

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
