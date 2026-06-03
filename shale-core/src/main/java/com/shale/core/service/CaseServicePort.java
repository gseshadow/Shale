package com.shale.core.service;

import java.util.List;
import java.util.Optional;

import com.shale.core.dto.CaseDetailDto;
import com.shale.core.dto.CaseOverviewDto;
import com.shale.core.dto.CaseUpdateDto;

/**
 * Shared case application boundary for future desktop/server adapters.
 *
 * <p>These signatures mirror the current CaseDao/CaseDetailService read and
 * note capabilities without moving implementations or exposing JavaFX types.</p>
 */
public interface CaseServicePort {

	Optional<CaseDetailDto> getCaseDetail(long caseId, int shaleClientId);

	Optional<CaseOverviewDto> getCaseOverview(long caseId, int shaleClientId);

	List<CaseOverviewDto> searchCases(String query, int shaleClientId, int limit);

	List<CaseUpdateDto> listCaseUpdates(long caseId, int shaleClientId);

	/**
	 * TODO: align this command with existing case update/note behavior before
	 * exposing server write endpoints.
	 */
	long addCaseNote(AddCaseNoteCommand command);

	/**
	 * TODO: replace this placeholder command with a complete shared update DTO
	 * before wiring shale-server write endpoints.
	 */
	CaseDetailDto updateCaseDetails(UpdateCaseDetailsCommand command);

	record AddCaseNoteCommand(
			long caseId,
			int shaleClientId,
			int actorUserId,
			String noteText) {
	}

	record UpdateCaseDetailsCommand(
			long caseId,
			int shaleClientId,
			int actorUserId,
			String caseName,
			String description,
			Integer statusId,
			Integer practiceAreaId) {
	}
}
