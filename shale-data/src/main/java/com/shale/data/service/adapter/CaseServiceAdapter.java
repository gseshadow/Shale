package com.shale.data.service.adapter;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.shale.core.dto.CaseDetailDto;
import com.shale.core.dto.CaseOverviewDto;
import com.shale.core.dto.CaseUpdateDto;
import com.shale.core.dto.CaseStatusDto;
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
	public CaseStatusDto createCaseStatus(CaseStatusCommand command) {
		Objects.requireNonNull(command, "command");
		return caseGateway.createCaseStatus(command.shaleClientId(), command.name(), command.description(), command.active(), command.sortOrder());
	}

	@Override
	public CaseStatusDto updateCaseStatus(CaseStatusCommand command) {
		Objects.requireNonNull(command, "command");
		if (command.id() == null) {
			throw new IllegalArgumentException("Status id is required.");
		}
		return caseGateway.updateCaseStatus(command.shaleClientId(), command.id(), command.name(), command.description(), command.active(), command.sortOrder());
	}

	@Override
	public void setCaseStatusActive(int shaleClientId, int statusId, boolean active) {
		caseGateway.setCaseStatusActive(shaleClientId, statusId, active);
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

		CaseStatusDto createCaseStatus(int shaleClientId, String name, String description, boolean active, Integer sortOrder);

		CaseStatusDto updateCaseStatus(int shaleClientId, int statusId, String name, String description, boolean active, Integer sortOrder);

		void setCaseStatusActive(int shaleClientId, int statusId, boolean active);

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
		public CaseStatusDto createCaseStatus(int shaleClientId, String name, String description, boolean active, Integer sortOrder) {
			return caseDao.createCaseStatus(shaleClientId, name, description, active, sortOrder);
		}

		@Override
		public CaseStatusDto updateCaseStatus(int shaleClientId, int statusId, String name, String description, boolean active, Integer sortOrder) {
			return caseDao.updateCaseStatus(shaleClientId, statusId, name, description, active, sortOrder);
		}

		@Override
		public void setCaseStatusActive(int shaleClientId, int statusId, boolean active) {
			caseDao.setCaseStatusActive(shaleClientId, statusId, active);
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
