package com.shale.data.service.adapter;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.shale.core.dto.CaseDetailDto;
import com.shale.core.dto.CaseOverviewDto;
import com.shale.core.dto.CaseUpdateDto;
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
	public long addCaseNote(AddCaseNoteCommand command) {
		throw new UnsupportedOperationException(
				"TODO: CaseServiceAdapter.addCaseNote requires a port return type aligned with CaseDao.addCaseNote/addCaseUpdate.");
	}

	@Override
	public CaseDetailDto updateCaseDetails(UpdateCaseDetailsCommand command) {
		throw new UnsupportedOperationException(
				"TODO: CaseServiceAdapter.updateCaseDetails requires the full CaseDao update command/row-version contract.");
	}

	interface CaseGateway {
		CaseDetailDto getDetail(long caseId);

		CaseOverviewDto getOverview(long caseId);

		List<CaseDao.CaseRow> searchCasesByName(String query);

		List<CaseUpdateDto> listCaseUpdates(long caseId);
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
	}
}
