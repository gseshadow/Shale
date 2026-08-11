package com.shale.ui.services;

import com.shale.core.dto.ReportCaseDetailRowDto;
import com.shale.core.service.CaseServicePort;
import com.shale.data.dao.CaseDao;
import com.shale.data.dao.CaseSummaryDao;
import com.shale.data.dao.CaseSummaryDao.CaseGridRow;
import com.shale.ui.state.AppState;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Authorization/tenant boundary for complete case and report export reads. */
public final class CaseExportService {
    private final CaseDao caseDao;
    private final AppState appState;
    private final PhiReadAuditService phiReadAuditService;
    private final CaseServicePort caseService;
    private final CaseSummaryDao caseSummaryDao;

    public CaseExportService(CaseDao caseDao, CaseServicePort caseService, AppState appState, PhiReadAuditService phiReadAuditService) {
		this(caseDao, null, caseService, appState, phiReadAuditService);
	}

    public CaseExportService(CaseDao caseDao, CaseSummaryDao caseSummaryDao, CaseServicePort caseService,
            AppState appState, PhiReadAuditService phiReadAuditService) {
        this.caseDao = Objects.requireNonNull(caseDao, "caseDao");
		this.caseSummaryDao = caseSummaryDao;
        this.caseService = Objects.requireNonNull(caseService, "caseService");
        this.appState = Objects.requireNonNull(appState, "appState");
        this.phiReadAuditService = Objects.requireNonNull(phiReadAuditService, "phiReadAuditService");
    }

    public List<ExportCaseRow> exportCases(CasesCriteria criteria) {
        requireAuthorizedTenant(criteria.tenantId());
		if (caseSummaryDao == null) throw new IllegalStateException("Authoritative Cases export is unavailable.");
        List<ExportCaseRow> result = caseSummaryDao.listActiveGridForExport(criteria.tenantId(), criteria.order(),
				criteria.query(), criteria.statusMode(), criteria.statusIds()).stream().map(ExportCaseRow::from).toList();
        phiReadAuditService.auditRead("Case.Export", "Cases.Export", "Case", null);
        return result;
    }

    public List<ReportExportRow> exportReport(ReportCriteria criteria, Map<Integer, String> statusNames) {
        requireAuthorizedTenant(criteria.tenantId());
        List<ReportExportRow> rows = new ArrayList<>();
        for (Integer statusId : criteria.statusIds()) {
            String statusName = statusNames.getOrDefault(statusId, "Unknown");
            for (ReportCaseDetailRowDto detail : caseDao.listCaseStatusReportCases(
                    criteria.tenantId(), statusId, criteria.startDate(), criteria.endDate())) {
                rows.add(new ReportExportRow(statusName, detail));
            }
        }
        phiReadAuditService.auditRead("Case.Report.Export", "Reports.Export", "Case", null);
        return List.copyOf(rows);
    }

    private void requireAuthorizedTenant(int tenantId) {
        Integer currentTenant = appState.getShaleClientId();
        Integer userId = appState.getUserId();
        if (tenantId <= 0 || !Objects.equals(currentTenant, tenantId) || userId == null || userId <= 0) {
            throw new SecurityException("The export is not authorized.");
        }
    }

    public record CasesCriteria(int tenantId, CaseSummaryDao.GridOrder order, String query,
			CaseSummaryDao.GridStatusMode statusMode, Set<Integer> statusIds) {
        public CasesCriteria {
			order = Objects.requireNonNull(order, "order");
			statusMode = Objects.requireNonNull(statusMode, "statusMode");
            query = query == null ? "" : query.trim();
            statusIds = statusIds == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(statusIds));
			if (statusMode == CaseSummaryDao.GridStatusMode.SELECTED && statusIds.isEmpty())
				throw new IllegalArgumentException("SELECTED status mode requires status IDs");
        }
    }

    public record ReportCriteria(int tenantId, LocalDate startDate, LocalDate endDate, List<Integer> statusIds) {
        public ReportCriteria {
            statusIds = statusIds == null ? List.of() : List.copyOf(statusIds);
        }
    }


    public record ExportCaseRow(com.shale.core.dto.CaseSummaryProjection summary, LocalDate intakeDate,
			LocalDate dateOfIncident, LocalDate statuteOfLimitationsDate, LocalDate tortClaimsNoticeDeadline,
			String clientName, String opposingPartiesName, String latestCaseUpdate, String description) {
		static ExportCaseRow from(CaseGridRow row) {
			return new ExportCaseRow(row.summary(), row.intakeDate(), row.dateOfIncident(),
					row.statuteOfLimitationsDate(), row.tortClaimsNoticeDeadline(), row.clientName(),
					row.opposingPartiesName(), row.latestCaseUpdate(), row.description());
		}
    }

    public record ReportExportRow(String statusName, ReportCaseDetailRowDto detail) {}
}
