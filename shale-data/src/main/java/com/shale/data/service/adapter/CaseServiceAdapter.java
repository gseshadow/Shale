package com.shale.data.service.adapter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.shale.core.dto.CaseDetailDto;
import com.shale.core.dto.CaseOverviewDto;
import com.shale.core.dto.CaseUpdateDto;
import com.shale.core.dto.CaseStatusDto;
import com.shale.core.dto.PracticeAreaDto;
import com.shale.core.service.CaseServicePort;
import com.shale.data.dao.CaseDao;

/**
 * Thin CaseServicePort adapter over existing CaseDao read operations.
 */
public final class CaseServiceAdapter implements CaseServicePort {

	private final CaseGateway caseGateway;

	public CaseServiceAdapter(CaseDao caseDao) {
		this(new DaoCaseGateway(caseDao));
	}

	CaseServiceAdapter(CaseGateway caseGateway) {
		this.caseGateway = Objects.requireNonNull(caseGateway, "caseGateway");
	}

	@Override
	public Optional<CaseDetailDto> getCaseDetail(long caseId, int shaleClientId) {
		return Optional.ofNullable(caseGateway.getDetail(caseId));
	}

	@Override
	public Optional<CaseOverviewDto> getCaseOverview(long caseId, int shaleClientId) {
		return Optional.ofNullable(caseGateway.getOverview(caseId));
	}

	@Override
	public List<CaseOverviewDto> searchCases(String query, int shaleClientId, int limit) {
		int resolvedLimit = limit <= 0 ? 25 : limit;
		return caseGateway.searchCasesByName(query).stream()
				.limit(resolvedLimit)
				.map(CaseDao.CaseRow::id)
				.map(caseGateway::getOverview)
				.filter(Objects::nonNull)
				.toList();
	}

	@Override
	public List<CaseOverviewDto> listAssignedCases(int assignedUserId, int shaleClientId, int limit) {
		int resolvedLimit = limit <= 0 ? 25 : limit;
		return caseGateway.listAssignedCasesForBoard(assignedUserId).stream()
				.limit(resolvedLimit)
				.map(CaseDao.CaseRow::id)
				.map(caseGateway::getOverview)
				.filter(Objects::nonNull)
				.toList();
	}

	@Override
	public List<CaseUpdateDto> listCaseUpdates(long caseId, int shaleClientId) {
		return caseGateway.listCaseUpdates(caseId, shaleClientId);
	}

	@Override
	public CaseDetailDto createCase(CreateCaseCommand command) {
		Objects.requireNonNull(command, "command");
		List<CaseStatusDto> statuses = listCaseStatuses(command.shaleClientId(), false);
		CaseStatusDto initialStatus = statuses.stream()
				.filter(Objects::nonNull)
				.filter(status -> !status.closed())
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("No active non-closed case status is available for this tenant."));
		long caseId = caseGateway.createBasicCase(command, initialStatus.id());
		return caseGateway.getDetail(caseId);
	}

	@Override
	public List<CaseStatusDto> listCaseStatuses(int shaleClientId, boolean includeInactive) {
		return caseGateway.listCaseStatuses(shaleClientId, includeInactive);
	}

	@Override
	public List<CaseStatusDto> listTenantCaseStatuses(int shaleClientId, boolean includeInactive) {
		return caseGateway.listTenantCaseStatuses(shaleClientId, includeInactive);
	}

	@Override
	public List<PracticeAreaDto> listPracticeAreas(int shaleClientId, boolean includeInactive) {
		return resolveEffectivePracticeAreas(caseGateway.listPracticeAreas(shaleClientId, includeInactive), shaleClientId);
	}

	static List<PracticeAreaDto> resolveEffectivePracticeAreas(List<PracticeAreaDto> rows, int shaleClientId) {
		if (rows == null || rows.isEmpty() || shaleClientId <= 0) {
			return List.of();
		}

		Map<String, PracticeAreaDto> keyed = new LinkedHashMap<>();
		List<PracticeAreaDto> unkeyed = new ArrayList<>();
		for (PracticeAreaDto area : rows) {
			if (area == null) {
				continue;
			}
			Integer areaTenantId = area.shaleClientId();
			if (areaTenantId != null && areaTenantId != shaleClientId) {
				continue;
			}

			String systemKey = normalizeSystemKey(area.systemKey());
			if (systemKey == null) {
				unkeyed.add(area);
				continue;
			}

			PracticeAreaDto existing = keyed.get(systemKey);
			boolean tenantRow = areaTenantId != null && areaTenantId == shaleClientId;
			boolean existingTenantRow = existing != null
					&& existing.shaleClientId() != null
					&& existing.shaleClientId() == shaleClientId;
			if (existing == null || (tenantRow && !existingTenantRow)) {
				keyed.put(systemKey, area);
			}
		}

		List<PracticeAreaDto> effective = new ArrayList<>(keyed.size() + unkeyed.size());
		effective.addAll(keyed.values());
		effective.addAll(unkeyed);
		effective.sort(Comparator
				.comparing((PracticeAreaDto area) -> area.name() == null ? "" : area.name(), String.CASE_INSENSITIVE_ORDER)
				.thenComparingInt(PracticeAreaDto::id));
		return List.copyOf(effective);
	}

	private static String normalizeSystemKey(String systemKey) {
		String normalized = systemKey == null ? "" : systemKey.trim().toLowerCase(Locale.ROOT);
		return normalized.isBlank() ? null : normalized;
	}

	@Override
	public List<PracticeAreaDto> listTenantPracticeAreas(int shaleClientId, boolean includeInactive) {
		return caseGateway.listTenantPracticeAreas(shaleClientId, includeInactive);
	}

	@Override
	public PracticeAreaDto createPracticeArea(PracticeAreaCommand command) {
		Objects.requireNonNull(command, "command");
		return caseGateway.createPracticeArea(command.shaleClientId(), command.name(), command.color(), command.active(), command.systemKey());
	}

	@Override
	public PracticeAreaDto updatePracticeArea(PracticeAreaCommand command) {
		Objects.requireNonNull(command, "command");
		if (command.id() == null) {
			throw new IllegalArgumentException("Practice area id is required.");
		}
		return caseGateway.updatePracticeArea(command.shaleClientId(), command.id(), command.name(), command.color(), command.active(), command.systemKey());
	}

	@Override
	public void deactivatePracticeArea(int shaleClientId, int practiceAreaId) {
		caseGateway.deactivatePracticeArea(shaleClientId, practiceAreaId);
	}

	@Override
	public CaseStatusDto createCaseStatus(CaseStatusCommand command) {
		Objects.requireNonNull(command, "command");
		return caseGateway.createCaseStatus(command.shaleClientId(), command.name(), command.closed(), command.sortOrder(), command.color(), command.lifecycleKey(), command.systemKey());
	}

	@Override
	public CaseStatusDto updateCaseStatus(CaseStatusCommand command) {
		Objects.requireNonNull(command, "command");
		if (command.id() == null) {
			throw new IllegalArgumentException("Status id is required.");
		}
		return caseGateway.updateCaseStatus(command.shaleClientId(), command.id(), command.name(), command.closed(), command.sortOrder(), command.color(), command.lifecycleKey(), command.systemKey());
	}


	@Override
	public CaseDetailDto updateCaseCurrentStatus(UpdateCaseStatusCommand command) {
		Objects.requireNonNull(command, "command");
		CaseDao.StatusRow status = caseGateway.findStatusForTenantById(command.shaleClientId(), command.statusId());
		if (status == null) {
			throw new IllegalArgumentException("Case status is not available for this tenant.");
		}
		caseGateway.setPrimaryStatus(command.caseId(), status.id(), null);
		caseGateway.populateLifecycleDateIfNull(command.caseId(), status.lifecycleKey());
		return caseGateway.getDetail(command.caseId());
	}

	@Override
	public void reorderCaseStatuses(int shaleClientId, int firstStatusId, int secondStatusId) {
		caseGateway.reorderCaseStatuses(shaleClientId, firstStatusId, secondStatusId);
	}

	@Override
	public void addCaseNote(AddCaseNoteCommand command) {
		Objects.requireNonNull(command, "command");
		caseGateway.addCaseNote(command.caseId(), command.shaleClientId(), command.noteText(), command.actorUserId());
	}

	@Override
	public CaseDetailDto updateCaseAssignment(UpdateCaseAssignmentCommand command) {
		Objects.requireNonNull(command, "command");
		caseGateway.updateCaseAssignment(command.caseId(), command.shaleClientId(), command.practiceAreaId(), command.responsibleAttorneyUserId());
		return caseGateway.getDetail(command.caseId());
	}

	@Override
	public CaseDetailDto updateCaseCoreDetails(UpdateCaseCoreDetailsCommand command) {
		Objects.requireNonNull(command, "command");
		return caseGateway.updateCase(
				command.caseId(),
				command.caseName(),
				command.caseNumber(),
				command.description(),
				command.dateOfInjury(),
				command.statuteOfLimitations(),
				command.tortNoticeDeadline(),
				command.summary(),
				command.expectedRowVer(),
				command.actorUserId());
	}

	interface CaseGateway {
		CaseDetailDto getDetail(long caseId);

		CaseOverviewDto getOverview(long caseId);

		List<CaseDao.CaseRow> searchCasesByName(String query);

		List<CaseDao.CaseRow> listAssignedCasesForBoard(int assignedUserId);

		List<CaseUpdateDto> listCaseUpdates(long caseId, int shaleClientId);

		void addCaseNote(long caseId, int shaleClientId, String noteText, Integer createdByUserId);

		List<CaseStatusDto> listCaseStatuses(int shaleClientId, boolean includeInactive);

		List<CaseStatusDto> listTenantCaseStatuses(int shaleClientId, boolean includeInactive);

		List<PracticeAreaDto> listPracticeAreas(int shaleClientId, boolean includeInactive);

		List<PracticeAreaDto> listTenantPracticeAreas(int shaleClientId, boolean includeInactive);

		PracticeAreaDto createPracticeArea(int shaleClientId, String name, String color, boolean active, String systemKey);

		PracticeAreaDto updatePracticeArea(int shaleClientId, int practiceAreaId, String name, String color, boolean active, String systemKey);

		void deactivatePracticeArea(int shaleClientId, int practiceAreaId);

		CaseStatusDto createCaseStatus(int shaleClientId, String name, boolean closed, Integer sortOrder, String color, String lifecycleKey, String systemKey);

		CaseStatusDto updateCaseStatus(int shaleClientId, int statusId, String name, boolean closed, Integer sortOrder, String color, String lifecycleKey, String systemKey);


		CaseDao.StatusRow findStatusForTenantById(int shaleClientId, int statusId);

		void setPrimaryStatus(long caseId, int statusId, String notes);

		void populateLifecycleDateIfNull(long caseId, String lifecycleKey);

		void reorderCaseStatuses(int shaleClientId, int firstStatusId, int secondStatusId);

		void updateCaseAssignment(long caseId, int shaleClientId, int practiceAreaId, int responsibleAttorneyUserId);

		CaseDetailDto updateCase(long caseId, String name, String caseNumber, String description,
				LocalDate incidentDate, LocalDate solDate, LocalDate tortNoticeDeadline, String summary,
				byte[] expectedRowVer, Integer actorUserId);

		long createBasicCase(CreateCaseCommand command, int statusId);
	}

	private record DaoCaseGateway(CaseDao caseDao) implements CaseGateway {
		private DaoCaseGateway {
			Objects.requireNonNull(caseDao, "caseDao");
		}

		@Override
		public CaseDetailDto getDetail(long caseId) {
			return caseDao.getDetail(caseId);
		}

		@Override
		public CaseOverviewDto getOverview(long caseId) {
			return caseDao.getOverview(caseId);
		}

		@Override
		public List<CaseDao.CaseRow> searchCasesByName(String query) {
			return caseDao.searchCasesByName(query);
		}

		@Override
		public List<CaseDao.CaseRow> listAssignedCasesForBoard(int assignedUserId) {
			return caseDao.listAssignedCasesForBoard(assignedUserId);
		}

		@Override
		public List<CaseUpdateDto> listCaseUpdates(long caseId, int shaleClientId) {
			return caseDao.listCaseUpdates(caseId, shaleClientId);
		}

		@Override
		public void addCaseNote(long caseId, int shaleClientId, String noteText, Integer createdByUserId) {
			caseDao.addCaseNote(caseId, shaleClientId, noteText, createdByUserId);
		}

		@Override
		public List<CaseStatusDto> listCaseStatuses(int shaleClientId, boolean includeInactive) {
			return caseDao.listCaseStatuses(shaleClientId, includeInactive);
		}

		@Override
		public List<CaseStatusDto> listTenantCaseStatuses(int shaleClientId, boolean includeInactive) {
			return caseDao.listTenantCaseStatuses(shaleClientId, includeInactive);
		}

		@Override
		public List<PracticeAreaDto> listPracticeAreas(int shaleClientId, boolean includeInactive) {
			return caseDao.listPracticeAreas(shaleClientId, includeInactive);
		}

		@Override
		public List<PracticeAreaDto> listTenantPracticeAreas(int shaleClientId, boolean includeInactive) {
			return caseDao.listTenantPracticeAreas(shaleClientId, includeInactive);
		}

		@Override
		public PracticeAreaDto createPracticeArea(int shaleClientId, String name, String color, boolean active, String systemKey) {
			return caseDao.createPracticeArea(shaleClientId, name, color, active, systemKey);
		}

		@Override
		public PracticeAreaDto updatePracticeArea(int shaleClientId, int practiceAreaId, String name, String color, boolean active, String systemKey) {
			return caseDao.updatePracticeArea(shaleClientId, practiceAreaId, name, color, active, systemKey);
		}

		@Override
		public void deactivatePracticeArea(int shaleClientId, int practiceAreaId) {
			caseDao.deactivatePracticeArea(shaleClientId, practiceAreaId);
		}

		@Override
		public CaseStatusDto createCaseStatus(int shaleClientId, String name, boolean closed, Integer sortOrder, String color, String lifecycleKey, String systemKey) {
			return caseDao.createCaseStatus(shaleClientId, name, closed, sortOrder, color, lifecycleKey, systemKey);
		}

		@Override
		public CaseStatusDto updateCaseStatus(int shaleClientId, int statusId, String name, boolean closed, Integer sortOrder, String color, String lifecycleKey, String systemKey) {
			return caseDao.updateCaseStatus(shaleClientId, statusId, name, closed, sortOrder, color, lifecycleKey, systemKey);
		}


		@Override
		public CaseDao.StatusRow findStatusForTenantById(int shaleClientId, int statusId) {
			return caseDao.findStatusForTenantById(shaleClientId, statusId);
		}

		@Override
		public void setPrimaryStatus(long caseId, int statusId, String notes) {
			caseDao.setPrimaryStatus(caseId, statusId, notes);
		}

		@Override
		public void populateLifecycleDateIfNull(long caseId, String lifecycleKey) {
			caseDao.populateLifecycleDateIfNull(caseId, lifecycleKey);
		}

		@Override
		public void reorderCaseStatuses(int shaleClientId, int firstStatusId, int secondStatusId) {
			caseDao.reorderCaseStatuses(shaleClientId, firstStatusId, secondStatusId);
		}

		@Override
		public void updateCaseAssignment(long caseId, int shaleClientId, int practiceAreaId, int responsibleAttorneyUserId) {
			caseDao.updateCaseAssignment(caseId, shaleClientId, practiceAreaId, responsibleAttorneyUserId);
		}

		@Override
		public CaseDetailDto updateCase(long caseId, String name, String caseNumber, String description,
				LocalDate incidentDate, LocalDate solDate, LocalDate tortNoticeDeadline, String summary,
				byte[] expectedRowVer, Integer actorUserId) {
			return caseDao.updateCase(caseId, name, caseNumber, description, incidentDate, solDate, tortNoticeDeadline, summary, expectedRowVer, actorUserId);
		}

		@Override
		public long createBasicCase(CreateCaseCommand command, int statusId) {
			return caseDao.createBasicCase(command.shaleClientId(), command.caseName(), command.caseNumber(),
					command.callerDate(), command.practiceAreaId(), command.responsibleAttorneyUserId(),
					statusId, command.description(), command.summary(), command.dateOfInjury(),
					command.statuteOfLimitations(), command.tortNoticeDeadline(), command.actorUserId());
		}
	}
}
