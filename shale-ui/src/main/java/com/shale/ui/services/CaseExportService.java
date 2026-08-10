package com.shale.ui.services;

import com.shale.core.dto.ReportCaseDetailRowDto;
import com.shale.core.dto.MigratedCaseDateProjectionDto;
import com.shale.core.model.MigratedCaseDateKey;
import com.shale.core.service.CaseServicePort;
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
    private final CaseServicePort caseService;

    public CaseExportService(CaseDao caseDao, CaseServicePort caseService, AppState appState, PhiReadAuditService phiReadAuditService) {
        this.caseDao = Objects.requireNonNull(caseDao, "caseDao");
        this.caseService = Objects.requireNonNull(caseService, "caseService");
        this.appState = Objects.requireNonNull(appState, "appState");
        this.phiReadAuditService = Objects.requireNonNull(phiReadAuditService, "phiReadAuditService");
    }

    public List<ExportCaseRow> exportCases(CasesCriteria criteria) {
        requireAuthorizedTenant(criteria.tenantId());
        List<CaseRow> rows = caseDao.listCasesViewForExport(criteria.sort(), criteria.includeClosedDenied(),
                criteria.query(), criteria.statusIds());
        Integer actor = appState.getUserId();
        Map<Long, MigratedCaseDateProjectionDto> dates = caseService.projectMigratedCaseDates(
                rows.stream().map(CaseRow::id).toList(), criteria.tenantId(), actor);
        List<ExportCaseRow> result = rows.stream().map(row -> ExportCaseRow.from(row, dates.get(row.id()))).toList();
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


    public record ExportCaseRow(CaseRow base, LocalDate intakeDate, LocalDate dateOfIncident,
                                LocalDate statuteOfLimitationsDate, LocalDate tortClaimsNoticeDeadline) {
        static ExportCaseRow from(CaseRow row, MigratedCaseDateProjectionDto projection) {
            MigratedCaseDateProjectionDto p = projection == null ? MigratedCaseDateProjectionDto.empty(row.id()) : projection;
            return new ExportCaseRow(row, date(p, MigratedCaseDateKey.CALLER_DATE),
                    date(p, MigratedCaseDateKey.DATE_OF_INJURY), date(p, MigratedCaseDateKey.STATUTE_OF_LIMITATIONS),
                    date(p, MigratedCaseDateKey.TORT_NOTICE_DEADLINE));
        }
        private static LocalDate date(MigratedCaseDateProjectionDto p, MigratedCaseDateKey key) {
            var slot = p.date(key);
            return slot.present() ? slot.startsAt().toLocalDate() : null;
        }
    }

    public record ReportExportRow(String statusName, ReportCaseDetailRowDto detail) {}
}
