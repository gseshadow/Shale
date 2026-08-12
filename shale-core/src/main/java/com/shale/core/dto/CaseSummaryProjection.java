package com.shale.core.dto;

import java.time.LocalDateTime;

/**
 * Tenant-owned, PHI-minimized read model for displaying Case identity and its
 * current broadly reusable relationships.
 */
public record CaseSummaryProjection(
		long caseId,
		int shaleClientId,
		String caseNumber,
		String caseName,
		Integer primaryStatusId,
		String primaryStatusSystemKey,
		String primaryStatusLifecycleKey,
		String primaryStatusName,
		String primaryStatusColor,
		Integer practiceAreaId,
		String practiceAreaName,
		Integer responsibleAttorneyId,
		String responsibleAttorneyName,
		String responsibleAttorneyColor,
		Integer primaryLegalAssistantId,
		String primaryLegalAssistantName,
		String primaryLegalAssistantColor,
		LocalDateTime createdAt,
		LocalDateTime updatedAt,
		boolean deleted) {
}
