package com.shale.core.dto;

/**
 * Minimal, immutable case projection used by selectors.
 *
 * <p>Case name and responsible-attorney presentation are the only case data
 * rendered by the calendar event editor. Keeping this contract separate from
 * the Cases screen DTO prevents selector reads from hydrating narrative,
 * party, status, timeline, and deadline data.</p>
 */
public record CaseSelectionOptionDto(
		long caseId,
		String displayName,
		String responsibleAttorneyName,
		String responsibleAttorneyColor,
		Boolean nonEngagementLetterSent) {
}
