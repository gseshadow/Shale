package com.shale.core.dto;

import java.time.LocalDateTime;

/**
 * Status history entry for a case, backed by dbo.CaseStatuses joined to dbo.Statuses.
 */
public record CaseStatusHistoryDto(
		long caseStatusId,
		int statusId,
		String statusName,
		String color,
		String lifecycleKey,
		String systemKey,
		boolean closed,
		String notes,
		LocalDateTime effectiveDate,
		LocalDateTime endDate,
		LocalDateTime createdAt,
		LocalDateTime updatedAt,
		boolean primary) {

	public boolean current() {
		return primary || endDate == null;
	}
}
