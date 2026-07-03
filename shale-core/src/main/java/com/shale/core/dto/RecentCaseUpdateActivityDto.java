package com.shale.core.dto;

import java.time.LocalDateTime;

public record RecentCaseUpdateActivityDto(
		long id,
		long caseId,
		String caseName,
		String noteText,
		LocalDateTime createdAt,
		Integer createdByUserId,
		String createdByDisplayName) {
	public RecentCaseUpdateActivityDto {
		caseName = caseName == null ? "" : caseName;
		noteText = noteText == null ? "" : noteText;
		createdByDisplayName = createdByDisplayName == null ? "" : createdByDisplayName;
	}
}
