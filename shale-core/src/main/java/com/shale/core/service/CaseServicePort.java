package com.shale.core.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.shale.core.dto.CaseDetailDto;
import com.shale.core.dto.CaseStatusDto;
import com.shale.core.dto.CaseOverviewDto;
import com.shale.core.dto.CaseUpdateDto;
import com.shale.core.dto.CaseLinkDto;
import com.shale.core.dto.CaseLinkShareDto;
import com.shale.core.dto.LinkTypeDto;
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

	CaseDetailDto createCase(CreateCaseCommand command);

	List<CaseUpdateDto> listCaseUpdates(long caseId, int shaleClientId);

	List<CaseStatusDto> listCaseStatuses(int shaleClientId, boolean includeInactive);

	List<CaseStatusDto> listTenantCaseStatuses(int shaleClientId, boolean includeInactive);

	List<PracticeAreaDto> listPracticeAreas(int shaleClientId, boolean includeInactive);

	List<PracticeAreaDto> listTenantPracticeAreas(int shaleClientId, boolean includeInactive);

	List<LinkTypeDto> listLinkTypes(int shaleClientId, boolean includeInactive);


	List<LinkTypeDto> listLinkTypesForAdministration(int shaleClientId, int actorUserId);

	LinkTypeDto createLinkType(LinkTypeCommand command);

	LinkTypeDto updateLinkType(LinkTypeCommand command);

	LinkTypeDto setLinkTypeActive(SetLinkTypeActiveCommand command);

	void resetLinkTypeOverride(ResetLinkTypeOverrideCommand command);

	List<CaseLinkDto> listCaseLinks(long caseId, int shaleClientId);

	Optional<CaseLinkDto> getPrimaryCaseLink(long caseId, int shaleClientId);

	CaseLinkDto createCaseLink(CreateCaseLinkCommand command);

	CaseLinkDto updateCaseLink(UpdateCaseLinkCommand command);

	CaseLinkDto setPrimaryCaseLink(SetPrimaryCaseLinkCommand command);

	List<CaseLinkDto> reorderCaseLinks(ReorderCaseLinksCommand command);

	void deleteCaseLink(DeleteCaseLinkCommand command);

	default List<CaseLinkShareDto> listCaseLinkShares(long caseId, long caseLinkId, int shaleClientId) { return List.of(); }

	default CaseLinkShareDto addCaseLinkShare(AddCaseLinkShareCommand command) { throw new UnsupportedOperationException(); }

	default CaseLinkShareDto updateCaseLinkShare(UpdateCaseLinkShareCommand command) { throw new UnsupportedOperationException(); }

	default void removeCaseLinkShare(RemoveCaseLinkShareCommand command) { throw new UnsupportedOperationException(); }

	PracticeAreaDto createPracticeArea(PracticeAreaCommand command);

	PracticeAreaDto updatePracticeArea(PracticeAreaCommand command);

	void deactivatePracticeArea(int shaleClientId, int practiceAreaId);

	CaseStatusDto createCaseStatus(CaseStatusCommand command);

	CaseStatusDto updateCaseStatus(CaseStatusCommand command);

	CaseDetailDto updateCaseCurrentStatus(UpdateCaseStatusCommand command);

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

	CaseDetailDto updateCaseAssignment(UpdateCaseAssignmentCommand command);

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

	record LinkTypeCommand(
			Integer id,
			int shaleClientId,
			int actorUserId,
			String name,
			String color,
			boolean active,
			String systemKey,
			byte[] expectedRowVer) {
		public LinkTypeCommand {
			expectedRowVer = copyRowVer(expectedRowVer);
		}

		@Override
		public byte[] expectedRowVer() {
			return copyRowVer(expectedRowVer);
		}
	}

	record SetLinkTypeActiveCommand(int shaleClientId, int actorUserId, int linkTypeId, boolean active, byte[] expectedRowVer) {
		public SetLinkTypeActiveCommand {
			expectedRowVer = copyRowVer(expectedRowVer);
		}

		@Override
		public byte[] expectedRowVer() {
			return copyRowVer(expectedRowVer);
		}
	}

	record ResetLinkTypeOverrideCommand(int shaleClientId, int actorUserId, int linkTypeId) {
	}

	record CreateCaseLinkCommand(int shaleClientId, int actorUserId, long caseId, int linkTypeId, String displayName,
			String url, String description, boolean primary, String notes, Integer sortOrder) {
	}

	record UpdateCaseLinkCommand(int shaleClientId, int actorUserId, long caseId, long caseLinkId, long externalLinkId,
			int linkTypeId, String displayName, String url, String description, Boolean primary, String notes, Integer sortOrder,
			byte[] expectedCaseLinkRowVer, byte[] expectedExternalLinkRowVer) {
		public UpdateCaseLinkCommand {
			expectedCaseLinkRowVer = copyRowVer(expectedCaseLinkRowVer);
			expectedExternalLinkRowVer = copyRowVer(expectedExternalLinkRowVer);
		}

		@Override
		public byte[] expectedCaseLinkRowVer() {
			return copyRowVer(expectedCaseLinkRowVer);
		}

		@Override
		public byte[] expectedExternalLinkRowVer() {
			return copyRowVer(expectedExternalLinkRowVer);
		}
	}

	record SetPrimaryCaseLinkCommand(int shaleClientId, int actorUserId, long caseId, long caseLinkId) {
	}

	record ReorderCaseLinksCommand(int shaleClientId, int actorUserId, long caseId, List<Long> orderedCaseLinkIds) {
	}


	record AddCaseLinkShareCommand(int shaleClientId, int actorUserId, long caseId, long caseLinkId, int contactId,
			LocalDateTime sharedAt, String notes) {
	}

	record UpdateCaseLinkShareCommand(int shaleClientId, int actorUserId, long caseId, long caseLinkId,
			long caseLinkShareId, int contactId, LocalDateTime sharedAt, String notes, byte[] expectedRowVer) {
		public UpdateCaseLinkShareCommand { expectedRowVer = copyRowVer(expectedRowVer); }
		@Override public byte[] expectedRowVer() { return copyRowVer(expectedRowVer); }
	}

	record RemoveCaseLinkShareCommand(int shaleClientId, int actorUserId, long caseId, long caseLinkId,
			long caseLinkShareId, byte[] expectedRowVer) {
		public RemoveCaseLinkShareCommand { expectedRowVer = copyRowVer(expectedRowVer); }
		@Override public byte[] expectedRowVer() { return copyRowVer(expectedRowVer); }
	}

	record DeleteCaseLinkCommand(int shaleClientId, int actorUserId, long caseId, long caseLinkId, byte[] expectedCaseLinkRowVer) {
		public DeleteCaseLinkCommand {
			expectedCaseLinkRowVer = copyRowVer(expectedCaseLinkRowVer);
		}

		@Override
		public byte[] expectedCaseLinkRowVer() {
			return copyRowVer(expectedCaseLinkRowVer);
		}
	}

	private static byte[] copyRowVer(byte[] rowVer) {
		return rowVer == null ? null : Arrays.copyOf(rowVer, rowVer.length);
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

	record UpdateCaseStatusCommand(
			long caseId,
			int shaleClientId,
			int actorUserId,
			int statusId) {
	}

	record UpdateCaseAssignmentCommand(
			long caseId,
			int shaleClientId,
			int actorUserId,
			int practiceAreaId,
			int responsibleAttorneyUserId) {
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

	record CreateCaseCommand(
			int shaleClientId,
			int actorUserId,
			String caseName,
			String caseNumber,
			int practiceAreaId,
			int responsibleAttorneyUserId,
			LocalDate callerDate,
			LocalDate dateOfInjury,
			LocalDate statuteOfLimitations,
			LocalDate tortNoticeDeadline,
			String summary,
			String description) {
	}
}
