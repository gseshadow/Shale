package com.shale.core.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.shale.core.dto.CaseDetailDto;
import com.shale.core.dto.CaseStatusDto;
import com.shale.core.dto.CaseOverviewDto;
import com.shale.core.dto.CaseUpdateDto;
import com.shale.core.dto.PracticeAreaDto;

/**
 * Shared case application boundary for future desktop/server adapters.
 *
 * <p>These signatures mirror the current CaseDao/CaseDetailService read,
 * note, and optimistic core-detail update capabilities without moving
 * implementations or exposing JavaFX types.</p>
 */
public interface CaseServicePort {

	Optional<CaseDetailDto> getCaseDetail(long caseId, int shaleClientId);

	Optional<CaseOverviewDto> getCaseOverview(long caseId, int shaleClientId);

	List<CaseOverviewDto> searchCases(String query, int shaleClientId, int limit);

	List<CaseOverviewDto> listAssignedCases(int assignedUserId, int shaleClientId, int limit);

	List<CaseUpdateDto> listCaseUpdates(long caseId, int shaleClientId);

	List<CaseStatusDto> listCaseStatuses(int shaleClientId, boolean includeInactive);

	List<CaseStatusDto> listTenantCaseStatuses(int shaleClientId, boolean includeInactive);

	List<PracticeAreaDto> listPracticeAreas(int shaleClientId, boolean includeInactive);

	List<PracticeAreaDto> listTenantPracticeAreas(int shaleClientId, boolean includeInactive);

	PracticeAreaDto createPracticeArea(PracticeAreaCommand command);

	PracticeAreaDto updatePracticeArea(PracticeAreaCommand command);

	void deactivatePracticeArea(int shaleClientId, int practiceAreaId);

	CaseStatusDto createCaseStatus(CaseStatusCommand command);

	CaseStatusDto updateCaseStatus(CaseStatusCommand command);


	void reorderCaseStatuses(int shaleClientId, int firstStatusId, int secondStatusId);

	/**
	 * Adds a user-authored case note using the existing CaseDao.addCaseNote
	 * behavior, which persists the note and touches the case but does not expose
	 * the inserted note id as part of the public DAO contract.
	 */
	void addCaseNote(AddCaseNoteCommand command);

	/**
	 * Updates only the core case fields currently supported by CaseDao.updateCase.
	 * Broader intake/status/practice-area writes require a later, complete
	 * contract around CaseDao.updateCaseDetails and its row-version semantics.
	 */
	CaseDetailDto updateCaseCoreDetails(UpdateCaseCoreDetailsCommand command);

	record AddCaseNoteCommand(
			long caseId,
			int shaleClientId,
			int actorUserId,
			String noteText) {
	}

	record PracticeAreaCommand(
			Integer id,
			int shaleClientId,
			String name,
			String color,
			boolean active,
			String systemKey) {
	}

	record CaseStatusCommand(
			Integer id,
			int shaleClientId,
			String name,
			boolean closed,
			Integer sortOrder,
			String color,
			String lifecycleKey,
			String systemKey) {
	}

	record UpdateCaseCoreDetailsCommand(
			long caseId,
			int shaleClientId,
			int actorUserId,
			String caseName,
			String caseNumber,
			String description,
			LocalDate dateOfInjury,
			LocalDate statuteOfLimitations,
			LocalDate tortNoticeDeadline,
			String summary,
			byte[] expectedRowVer) {
	}
}
