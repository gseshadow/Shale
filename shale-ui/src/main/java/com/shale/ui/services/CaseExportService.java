package com.shale.ui.services;

import com.shale.core.dto.ReportCaseDetailRowDto;
import com.shale.data.dao.CaseDao;
import com.shale.data.dao.CaseDao.CaseRow;
import com.shale.data.dao.CaseDao.CaseSort;
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

    public CaseExportService(CaseDao caseDao, AppState appState, PhiReadAuditService phiReadAuditService) {
        this.caseDao = Objects.requireNonNull(caseDao, "caseDao");
        this.appState = Objects.requireNonNull(appState, "appState");
        this.phiReadAuditService = Objects.requireNonNull(phiReadAuditService, "phiReadAuditService");
    }

    public List<CaseRow> exportCases(CasesCriteria criteria) {
        requireAuthorizedTenant(criteria.tenantId());
        List<CaseRow> rows = caseDao.listCasesViewForExport(criteria.sort(), criteria.includeClosedDenied(),
                criteria.query(), criteria.statusIds());
        phiReadAuditService.auditRead("Case.Export", "Cases.Export", "Case", null);
        return rows;
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

    public record CasesCriteria(int tenantId, CaseSort sort, boolean includeClosedDenied, String query,
                                Set<Integer> statusIds) {
        public CasesCriteria {
            sort = sort == null ? CaseSort.INTAKE_NEWEST : sort;
            query = query == null ? "" : query.trim();
            statusIds = statusIds == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(statusIds));
        }
    }

    public record ReportCriteria(int tenantId, LocalDate startDate, LocalDate endDate, List<Integer> statusIds) {
        public ReportCriteria {
            statusIds = statusIds == null ? List.of() : List.copyOf(statusIds);
        }
    }

    public record ReportExportRow(String statusName, ReportCaseDetailRowDto detail) {}
}
