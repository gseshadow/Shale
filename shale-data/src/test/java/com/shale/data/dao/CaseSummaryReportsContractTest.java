package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.shale.core.dto.CaseSummaryProjection;

final class CaseSummaryReportsContractTest {
    private static String source() throws Exception {
        return Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseSummaryDao.java"));
    }

    @Test void reportDetailCompositionMapsTheRealProjectionWithoutAliases() {
        var summary = new CaseSummaryProjection(42, 7, "C-42", "Example", 9, "open", "active",
                "Open", "#123456", 3, "Malpractice", 11, "Alex Attorney", "#fff",
                12, "Lee Assistant", "#000", LocalDateTime.of(2026, 1, 2, 3, 4),
                LocalDateTime.of(2026, 2, 3, 4, 5), false);
        var row = new CaseSummaryDao.ReportCaseRow(summary, LocalDate.of(2026, 1, 1), null, null,
                LocalDate.of(2025, 1, 1), "description", LocalDate.of(2027, 1, 1), null).toDetailRow();
        assertAll(() -> assertEquals(42, row.id()), () -> assertEquals("Example", row.caseName()),
                () -> assertEquals("Alex Attorney", row.responsibleAttorney()),
                () -> assertEquals(LocalDate.of(2026, 1, 1), row.intakeDate()));
        assertEquals("Open", summary.primaryStatusName());
        assertEquals("#123456", summary.primaryStatusColor());
    }

    @Test void reportQueriesShareAuthoritativeTenantStatusRoleDateAndOrderingContracts() throws Exception {
        String dao = source();
        String report = dao.substring(dao.indexOf("listActiveStatusReportCases("),
                dao.indexOf("/** Active Cases related to a tenant Contact"));
        assertTrue(report.contains("verifyTenant(con, requestedTenantId)"));
        assertTrue(report.contains("verifyStatuses(con, requestedTenantId"));
        assertTrue(report.contains("statusApplySql()"));
        assertTrue(report.contains("RoleSemantics.ROLE_RESPONSIBLE_ATTORNEY"));
        assertTrue(report.contains("RoleSemantics.ROLE_LEGAL_ASSISTANT"));
        assertTrue(report.contains("dbo.CaseDates"));
        assertTrue(report.contains("dbo.CaseDateTypeSemanticRoleMappings"));
        assertTrue(report.contains("LOWER(LTRIM(RTRIM(family_type.SystemKey)))='statute_of_limitations'"));
        assertTrue(report.contains("LOWER(LTRIM(RTRIM(family_type.SystemKey)))='tort_notice_deadline'"));
        assertTrue(report.contains("ORDER BY family_date.StartsAt ASC,family_date.Id ASC"));
        assertTrue(report.contains("candidate.IsDeleted=0"));
        assertTrue(report.contains("effective_type.IsActive=1"));
        assertFalse(report.contains("effective.SemanticRoleKey='STATUTE_OF_LIMITATIONS'"));
        assertFalse(report.contains("effective.SemanticRoleKey='TORT_NOTICE_DEADLINE'"));
        assertFalse(report.contains("CaseDatePresentationSelections"));
        assertTrue(report.contains("ORDER BY dates.IntakeDate DESC,c.Id DESC"));
        assertFalse(report.contains("c.CallerDate"));
        assertFalse(report.contains("c.StatuteOfLimitations"));
        assertFalse(report.contains("cu.RoleId=4"));
    }
}
