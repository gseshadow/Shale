package com.shale.core.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.shale.core.dto.CaseDateDto;
import com.shale.core.dto.MigratedCaseDateProjectionDto;
import com.shale.core.dto.EffectiveCaseDateTypeDto;
import com.shale.core.model.CaseDateSemanticRole;
import com.shale.core.dto.CaseDateSemanticRoleMappingDto;
import com.shale.core.dto.CaseDetailDto;
import com.shale.core.dto.CaseStatusDto;
import com.shale.core.dto.CaseTeamRoleDefinitionDto;
import com.shale.core.dto.CaseTeamMembershipDto;
import com.shale.core.dto.CaseOverviewDto;
import com.shale.core.dto.CaseUpdateDto;
import com.shale.core.dto.CaseLinkDto;
import com.shale.core.dto.CaseLinkShareDto;
import com.shale.core.dto.ContactSharedCaseLinkDto;
import com.shale.core.dto.CaseLinkContactOptionDto;
import com.shale.core.dto.CasePartyEntityOptionDto;
import com.shale.core.dto.LinkTypeDto;
import com.shale.core.dto.PracticeAreaDto;
import com.shale.core.model.CaseDateAggregateCommand;
import com.shale.core.model.CaseDateAggregateResult;
import com.shale.core.model.CompatibilityCaseDateState;
import com.shale.core.model.MigratedCaseDateKey;
import java.util.Map;
import java.util.Collection;

/**
 * Shared case application boundary for future desktop/server adapters.
 *
 * <p>These signatures mirror the current CaseDao/CaseDetailService read,
 * note, and optimistic core-detail update capabilities without moving
 * implementations or exposing JavaFX types.</p>
 */
public interface CaseServicePort {

	Optional<CaseDetailDto> getCaseDetail(long caseId, int shaleClientId);
	default Optional<CaseDetailDto> getAuthoritativeCaseDetail(long caseId, int shaleClientId, int actorUserId) {
		throw new UnsupportedOperationException("Authoritative existing-case detail is unavailable.");
	}

	Optional<CaseOverviewDto> getCaseOverview(long caseId, int shaleClientId);

	List<CaseOverviewDto> searchCases(String query, int shaleClientId, int actorUserId, int limit);

	List<CaseOverviewDto> listAssignedCases(int assignedUserId, int shaleClientId, int limit);

	CaseDetailDto createCase(CreateCaseCommand command);

	List<CaseUpdateDto> listCaseUpdates(long caseId, int shaleClientId);

	List<CaseStatusDto> listCaseStatuses(int shaleClientId, boolean includeInactive);

	List<CaseStatusDto> listTenantCaseStatuses(int shaleClientId, boolean includeInactive);

	List<PracticeAreaDto> listPracticeAreas(int shaleClientId, boolean includeInactive);

	List<PracticeAreaDto> listTenantPracticeAreas(int shaleClientId, boolean includeInactive);

	List<LinkTypeDto> listLinkTypes(int shaleClientId, boolean includeInactive);

	List<EffectiveCaseDateTypeDto> listEffectiveCaseDateTypes(int shaleClientId, int actorUserId);

	default int resolveEffectiveCaseDateTypeId(int shaleClientId, int actorUserId, CaseDateSemanticRole role) {
		throw unsupportedCaseLinkOperation("resolveEffectiveCaseDateTypeId");
	}

	List<EffectiveCaseDateTypeDto> listCaseDateTypesForAdministration(int shaleClientId, int actorUserId);

	List<CaseDateSemanticRoleMappingDto> listCaseDateSemanticRoleMappings(int shaleClientId, int actorUserId);

	CaseDateSemanticRoleMappingDto saveCaseDateSemanticRoleMapping(SaveCaseDateSemanticRoleMappingCommand command);

	void resetCaseDateSemanticRoleMapping(ResetCaseDateSemanticRoleMappingCommand command);

	List<CaseDateDto> listCaseDatesForCase(long caseId, int shaleClientId, int actorUserId);

	/** Batch read boundary for list-style consumers of the nine migrated authoritative meanings. */
	default Map<Long, MigratedCaseDateProjectionDto> projectMigratedCaseDates(
			Collection<Long> caseIds, int shaleClientId, int actorUserId) {
		throw unsupportedCaseLinkOperation("projectMigratedCaseDates");
	}

	List<CaseDateDto> listDeletedCaseDatesForCase(long caseId, int shaleClientId, int actorUserId);

	Optional<CaseDateDto> getCaseDate(long caseDateId, int shaleClientId, int actorUserId);

	/** Authoritative snapshot used by the existing-case desktop compatibility-date editors. */
	default Map<MigratedCaseDateKey, CompatibilityCaseDateState> listMigratedCompatibilityStateForCase(
			long caseId, int shaleClientId, int actorUserId) { throw unsupportedCaseLinkOperation("listMigratedCompatibilityStateForCase"); }
	default CaseDateAggregateResult loadMigratedCompatibilityDateSnapshot(
			long caseId, int shaleClientId, int actorUserId) { throw unsupportedCaseLinkOperation("loadMigratedCompatibilityDateSnapshot"); }

	/** Atomic nine-slot mutation; this boundary never writes legacy dbo.Cases date columns. */
	default CaseDateAggregateResult mutateMigratedCompatibilityDates(CaseDateAggregateCommand command) {
		throw unsupportedCaseLinkOperation("mutateMigratedCompatibilityDates");
	}

	CaseDateDto createCaseDate(CreateCaseDateCommand command);

	CaseDateDto updateCaseDate(UpdateCaseDateCommand command);

	void deleteCaseDate(DeleteCaseDateCommand command);

	CaseDateDto restoreCaseDate(RestoreCaseDateCommand command);

	List<LinkTypeDto> listLinkTypesForAdministration(int shaleClientId, int actorUserId);

	LinkTypeDto createLinkType(LinkTypeCommand command);

	LinkTypeDto updateLinkType(LinkTypeCommand command);

	LinkTypeDto setLinkTypeActive(SetLinkTypeActiveCommand command);

	void resetLinkTypeOverride(ResetLinkTypeOverrideCommand command);

	List<CaseLinkDto> listCaseLinks(long caseId, int shaleClientId);

	Optional<CaseLinkDto> getPrimaryCaseLink(long caseId, int shaleClientId);

	List<ContactSharedCaseLinkDto> listCaseLinksSharedWithContact(int contactId, int shaleClientId);

	CaseLinkDto createCaseLink(CreateCaseLinkCommand command);

	default CaseLinkDto createCaseLinkWithShares(CreateCaseLinkWithSharesCommand command) { throw unsupportedCaseLinkOperation("createCaseLinkWithShares"); }

	CaseLinkDto updateCaseLink(UpdateCaseLinkCommand command);

	default CaseLinkDto updateCaseLinkWithShares(UpdateCaseLinkWithSharesCommand command) { throw unsupportedCaseLinkOperation("updateCaseLinkWithShares"); }

	CaseLinkDto setPrimaryCaseLink(SetPrimaryCaseLinkCommand command);

	List<CaseLinkDto> reorderCaseLinks(ReorderCaseLinksCommand command);

	void deleteCaseLink(DeleteCaseLinkCommand command);

	default List<CaseLinkContactOptionDto> searchCaseLinkShareContacts(int shaleClientId, String query, int limit) { throw unsupportedCaseLinkOperation("searchCaseLinkShareContacts"); }
	default List<CaseLinkContactOptionDto> listCaseLinkShareContacts(int shaleClientId) { throw unsupportedCaseLinkOperation("listCaseLinkShareContacts"); }
	default List<CaseLinkContactOptionDto> listCaseLinkShareCaseContacts(long caseId, int shaleClientId) { throw unsupportedCaseLinkOperation("listCaseLinkShareCaseContacts"); }
	default List<CasePartyEntityOptionDto> listRequestedFromCaseParties(long caseId, int shaleClientId) { throw unsupportedCaseLinkOperation("listRequestedFromCaseParties"); }

	default List<CaseLinkShareDto> listCaseLinkShares(long caseId, long caseLinkId, int shaleClientId) { throw unsupportedCaseLinkOperation("listCaseLinkShares"); }

	default CaseLinkShareDto addCaseLinkShare(AddCaseLinkShareCommand command) { throw unsupportedCaseLinkOperation("addCaseLinkShare"); }

	default CaseLinkShareDto updateCaseLinkShare(UpdateCaseLinkShareCommand command) { throw unsupportedCaseLinkOperation("updateCaseLinkShare"); }

	default void removeCaseLinkShare(RemoveCaseLinkShareCommand command) { throw unsupportedCaseLinkOperation("removeCaseLinkShare"); }

	PracticeAreaDto createPracticeArea(PracticeAreaCommand command);

	PracticeAreaDto updatePracticeArea(PracticeAreaCommand command);

	void deactivatePracticeArea(int shaleClientId, int practiceAreaId);

	CaseStatusDto createCaseStatus(CaseStatusCommand command);

	CaseStatusDto updateCaseStatus(CaseStatusCommand command);

	void removeCaseStatus(StatusLifecycleCommand command);

	CaseStatusDto restoreCaseStatus(StatusLifecycleCommand command);

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

	default List<CaseTeamRoleDefinitionDto> listCaseTeamRolesForAdministration(int shaleClientId, int actorUserId){ throw unsupportedCaseLinkOperation("listCaseTeamRolesForAdministration"); }
	default CaseTeamRoleDefinitionDto createCaseTeamRole(CaseTeamRoleCommand command){ throw unsupportedCaseLinkOperation("createCaseTeamRole"); }
	default CaseTeamRoleDefinitionDto updateCaseTeamRole(CaseTeamRoleCommand command){ throw unsupportedCaseLinkOperation("updateCaseTeamRole"); }
	default void removeCaseTeamRole(CaseTeamRoleLifecycleCommand command){ throw unsupportedCaseLinkOperation("removeCaseTeamRole"); }
	default void restoreCaseTeamRole(CaseTeamRoleLifecycleCommand command){ throw unsupportedCaseLinkOperation("restoreCaseTeamRole"); }
	default void resetCaseTeamRoleOverride(CaseTeamRoleLifecycleCommand command){ throw unsupportedCaseLinkOperation("resetCaseTeamRoleOverride"); }
	default List<CaseTeamMembershipDto> listCaseTeamMemberships(int tenantId, int actorUserId, long caseId) { throw unsupportedCaseLinkOperation("listCaseTeamMemberships"); }
	default CaseTeamMembershipDto addCaseTeamMember(CaseTeamMemberCommand command) { throw unsupportedCaseLinkOperation("addCaseTeamMember"); }
	default void removeCaseTeamMember(CaseTeamMemberLifecycleCommand command) { throw unsupportedCaseLinkOperation("removeCaseTeamMember"); }
	default void assignCaseTeamMemberRole(CaseTeamMemberRoleCommand command) { throw unsupportedCaseLinkOperation("assignCaseTeamMemberRole"); }
	default void removeCaseTeamMemberRole(CaseTeamMemberRoleLifecycleCommand command) { throw unsupportedCaseLinkOperation("removeCaseTeamMemberRole"); }

	record CaseTeamMemberCommand(int tenantId, int actorUserId, long caseId, int userId) {}
	record CaseTeamMemberLifecycleCommand(int tenantId, int actorUserId, long caseId, long membershipId, byte[] rowVer) {
		public CaseTeamMemberLifecycleCommand { rowVer=copyRowVer(rowVer); } @Override public byte[] rowVer(){return copyRowVer(rowVer);}
	}
	record CaseTeamMemberRoleCommand(int tenantId, int actorUserId, long caseId, long membershipId, int roleDefinitionId) {}
	record CaseTeamMemberRoleLifecycleCommand(int tenantId, int actorUserId, long caseId, long membershipId, long assignmentId, byte[] rowVer) {
		public CaseTeamMemberRoleLifecycleCommand { rowVer=copyRowVer(rowVer); } @Override public byte[] rowVer(){return copyRowVer(rowVer);}
	}

	record CaseTeamRoleCommand(Integer id, int tenantId, int actorUserId, String name, String description, String color, int sortOrder, boolean active, byte[] rowVer) {
		public CaseTeamRoleCommand { rowVer=copyRowVer(rowVer); } @Override public byte[] rowVer(){return copyRowVer(rowVer);}
	}
	record CaseTeamRoleLifecycleCommand(int tenantId, int actorUserId, int id, byte[] rowVer) {
		public CaseTeamRoleLifecycleCommand { rowVer=copyRowVer(rowVer); } @Override public byte[] rowVer(){return copyRowVer(rowVer);}
	}

	record CaseDateTypeCommand(Integer id, int shaleClientId, int actorUserId, String systemKey, String name, String description, String calendarCategory, String color, boolean supportsTime, Integer sortOrder, boolean active, byte[] expectedRowVer) { public CaseDateTypeCommand { expectedRowVer = copyRowVer(expectedRowVer); } @Override public byte[] expectedRowVer() { return copyRowVer(expectedRowVer); } }
	record SetCaseDateTypeActiveCommand(int shaleClientId, int actorUserId, int id, boolean active, byte[] expectedRowVer) { public SetCaseDateTypeActiveCommand { expectedRowVer = copyRowVer(expectedRowVer); } @Override public byte[] expectedRowVer() { return copyRowVer(expectedRowVer); } }
	record ResetCaseDateTypeOverrideCommand(int shaleClientId, int actorUserId, int id, byte[] expectedRowVer) { public ResetCaseDateTypeOverrideCommand { expectedRowVer = copyRowVer(expectedRowVer); } @Override public byte[] expectedRowVer() { return copyRowVer(expectedRowVer); } }

	EffectiveCaseDateTypeDto createCaseDateType(CaseDateTypeCommand command);
	EffectiveCaseDateTypeDto updateCaseDateType(CaseDateTypeCommand command);
	EffectiveCaseDateTypeDto setCaseDateTypeActive(SetCaseDateTypeActiveCommand command);
	void resetCaseDateTypeOverride(ResetCaseDateTypeOverrideCommand command);

	record SaveCaseDateSemanticRoleMappingCommand(int shaleClientId, int actorUserId, String roleKey, int caseDateTypeId, Long expectedMappingId, byte[] expectedRowVer) { public SaveCaseDateSemanticRoleMappingCommand { expectedRowVer=copyRowVer(expectedRowVer); } @Override public byte[] expectedRowVer(){return copyRowVer(expectedRowVer);} }
	record ResetCaseDateSemanticRoleMappingCommand(int shaleClientId, int actorUserId, String roleKey, long mappingId, byte[] expectedRowVer) { public ResetCaseDateSemanticRoleMappingCommand { expectedRowVer=copyRowVer(expectedRowVer); } @Override public byte[] expectedRowVer(){return copyRowVer(expectedRowVer);} }

	record CreateCaseDateCommand(int shaleClientId, int actorUserId, long caseId, int caseDateTypeId, String title, LocalDateTime startsAt, LocalDateTime endsAt, boolean allDay, String notes) {
	}

	record UpdateCaseDateCommand(int shaleClientId, int actorUserId, long caseId, long caseDateId, int caseDateTypeId, String title, LocalDateTime startsAt, LocalDateTime endsAt, boolean allDay, String notes, byte[] expectedRowVer) {
		public UpdateCaseDateCommand { expectedRowVer = copyRowVer(expectedRowVer); }
		@Override public byte[] expectedRowVer() { return copyRowVer(expectedRowVer); }
	}

	record DeleteCaseDateCommand(int shaleClientId, int actorUserId, long caseId, long caseDateId, byte[] expectedRowVer) {
		public DeleteCaseDateCommand { expectedRowVer = copyRowVer(expectedRowVer); }
		@Override public byte[] expectedRowVer() { return copyRowVer(expectedRowVer); }
	}

	record RestoreCaseDateCommand(int shaleClientId, int actorUserId, long caseId, long caseDateId, byte[] expectedRowVer) {
		public RestoreCaseDateCommand { expectedRowVer = copyRowVer(expectedRowVer); }
		@Override public byte[] expectedRowVer() { return copyRowVer(expectedRowVer); }
	}

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


	record CaseLinkShareDraft(int contactId, LocalDateTime sharedAt, String notes) {
	}

	record CaseLinkShareUpdate(long caseLinkShareId, int contactId, LocalDateTime sharedAt, String notes, byte[] expectedRowVer) {
		public CaseLinkShareUpdate { expectedRowVer = copyRowVer(expectedRowVer); }
		@Override public byte[] expectedRowVer() { return copyRowVer(expectedRowVer); }
	}

	record CaseLinkShareRemoval(long caseLinkShareId, byte[] expectedRowVer) {
		public CaseLinkShareRemoval { expectedRowVer = copyRowVer(expectedRowVer); }
		@Override public byte[] expectedRowVer() { return copyRowVer(expectedRowVer); }
	}

	record CreateCaseLinkWithSharesCommand(int shaleClientId, int actorUserId, long caseId, int linkTypeId, String displayName,
			String url, String description, boolean primary, String notes, Integer sortOrder, List<CaseLinkShareDraft> shares) {
		public CreateCaseLinkWithSharesCommand { shares = shares == null ? List.of() : List.copyOf(shares); }
		@Override public List<CaseLinkShareDraft> shares() { return shares == null ? List.of() : List.copyOf(shares); }
	}

	record UpdateCaseLinkWithSharesCommand(int shaleClientId, int actorUserId, long caseId, long caseLinkId, long externalLinkId,
			int linkTypeId, String displayName, String url, String description, Boolean primary, String notes, Integer sortOrder,
			byte[] expectedCaseLinkRowVer, byte[] expectedExternalLinkRowVer, List<CaseLinkShareDraft> shareAdds,
			List<CaseLinkShareUpdate> shareUpdates, List<CaseLinkShareRemoval> shareRemovals) {
		public UpdateCaseLinkWithSharesCommand {
			expectedCaseLinkRowVer = copyRowVer(expectedCaseLinkRowVer); expectedExternalLinkRowVer = copyRowVer(expectedExternalLinkRowVer);
			shareAdds = shareAdds == null ? List.of() : List.copyOf(shareAdds); shareUpdates = shareUpdates == null ? List.of() : List.copyOf(shareUpdates); shareRemovals = shareRemovals == null ? List.of() : List.copyOf(shareRemovals);
		}
		@Override public byte[] expectedCaseLinkRowVer() { return copyRowVer(expectedCaseLinkRowVer); }
		@Override public byte[] expectedExternalLinkRowVer() { return copyRowVer(expectedExternalLinkRowVer); }
		@Override public List<CaseLinkShareDraft> shareAdds() { return shareAdds == null ? List.of() : List.copyOf(shareAdds); }
		@Override public List<CaseLinkShareUpdate> shareUpdates() { return shareUpdates == null ? List.of() : List.copyOf(shareUpdates); }
		@Override public List<CaseLinkShareRemoval> shareRemovals() { return shareRemovals == null ? List.of() : List.copyOf(shareRemovals); }
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

	private static UnsupportedOperationException unsupportedCaseLinkOperation(String methodName) {
		return new UnsupportedOperationException(methodName + " requires an explicit CaseServicePort implementation; missing Case Link delegation must not mimic an empty or successful result.");
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

	record StatusLifecycleCommand(int shaleClientId, int actorUserId, int statusId) { }

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
			String summary,
			byte[] expectedRowVer,
			com.shale.core.model.CaseDateAggregateCommand caseDates) {
	}

	record CreateCaseCommand(
			int shaleClientId,
			int actorUserId,
			String caseName,
			String caseNumber,
			int practiceAreaId,
			int responsibleAttorneyUserId,
			List<CreateMappedCaseDate> caseDates,
			String summary,
			String description) {
		public CreateCaseCommand { caseDates = caseDates == null ? List.of() : List.copyOf(caseDates); }
	}

	/** Stable identity and lossless value for an authoritative mapped Case Date. */
	record CreateMappedCaseDate(String systemKey, Integer caseDateTypeId,
			java.time.LocalDateTime startsAt, java.time.LocalDateTime endsAt, boolean allDay) {}
}
