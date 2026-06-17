package com.shale.core.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ReportCaseDetailRowDto(
		int id,
		String caseName,
		LocalDateTime createdAt,
		LocalDate intakeDate,
		LocalDate deniedDate,
		LocalDate closedDate,
		LocalDate dateOfInjury,
		String description,
		LocalDate statuteOfLimitations,
		LocalDate tortNoticeDeadline,
		LocalDateTime updatedAt,
		String responsibleAttorney
) {
}
