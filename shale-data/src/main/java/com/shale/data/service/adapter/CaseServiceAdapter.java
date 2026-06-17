package com.shale.data.service.adapter;

import java.time.LocalDate;
import java.util.List;
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
	public List<CaseUpdateDto> listCaseUpdates(long caseId, int shaleClientId) {
		return caseGateway.listCaseUpdates(caseId);
	}

	@Override
	public List<CaseStatusDto> listCaseStatuses(int shaleClientId, boolean includeInactive) {
		return caseGateway.listCaseStatuses(shaleClientId, includeInactive);
	}

	@Override
	public List<PracticeAreaDto> listPracticeAreas(int shaleClientId, boolean includeInactive) {
		return caseGateway.listPracticeAreas(shaleClientId, includeInactive);
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
	public void reorderCaseStatuses(int shaleClientId, int firstStatusId, int secondStatusId) {
		caseGateway.reorderCaseStatuses(shaleClientId, firstStatusId, secondStatusId);
	}

	@Override
	public void addCaseNote(AddCaseNoteCommand command) {
		Objects.requireNonNull(command, "command");
		caseGateway.addCaseNote(command.caseId(), command.shaleClientId(), command.noteText(), command.actorUserId());
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
				command.expectedRowVer(),
				command.actorUserId());
	}

	interface CaseGateway {
		CaseDetailDto getDetail(long caseId);

		CaseOverviewDto getOverview(long caseId);

		List<CaseDao.CaseRow> searchCasesByName(String query);

		List<CaseUpdateDto> listCaseUpdates(long caseId);

		void addCaseNote(long caseId, int shaleClientId, String noteText, Integer createdByUserId);

		List<CaseStatusDto> listCaseStatuses(int shaleClientId, boolean includeInactive);

		List<PracticeAreaDto> listPracticeAreas(int shaleClientId, boolean includeInactive);

		PracticeAreaDto createPracticeArea(int shaleClientId, String name, String color, boolean active, String systemKey);

		PracticeAreaDto updatePracticeArea(int shaleClientId, int practiceAreaId, String name, String color, boolean active, String systemKey);

		void deactivatePracticeArea(int shaleClientId, int practiceAreaId);

		CaseStatusDto createCaseStatus(int shaleClientId, String name, boolean closed, Integer sortOrder, String color, String lifecycleKey, String systemKey);

		CaseStatusDto updateCaseStatus(int shaleClientId, int statusId, String name, boolean closed, Integer sortOrder, String color, String lifecycleKey, String systemKey);


		void reorderCaseStatuses(int shaleClientId, int firstStatusId, int secondStatusId);

		CaseDetailDto updateCase(long caseId, String name, String caseNumber, String description,
				LocalDate incidentDate, LocalDate solDate, byte[] expectedRowVer, Integer actorUserId);
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
		public List<CaseUpdateDto> listCaseUpdates(long caseId) {
			return caseDao.listCaseUpdates(caseId);
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
		public List<PracticeAreaDto> listPracticeAreas(int shaleClientId, boolean includeInactive) {
			return caseDao.listPracticeAreas(shaleClientId, includeInactive);
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
		public void reorderCaseStatuses(int shaleClientId, int firstStatusId, int secondStatusId) {
			caseDao.reorderCaseStatuses(shaleClientId, firstStatusId, secondStatusId);
		}

		@Override
		public CaseDetailDto updateCase(long caseId, String name, String caseNumber, String description,
				LocalDate incidentDate, LocalDate solDate, byte[] expectedRowVer, Integer actorUserId) {
			return caseDao.updateCase(caseId, name, caseNumber, description, incidentDate, solDate, expectedRowVer, actorUserId);
		}
	}
}
