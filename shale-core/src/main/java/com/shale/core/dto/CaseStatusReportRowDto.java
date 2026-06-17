package com.shale.core.dto;

public record CaseStatusReportRowDto(
		int statusId,
		String caseStatus,
		String systemKey,
		String lifecycleKey,
		String color,
		Integer sortOrder,
		long caseCount
) {
}
