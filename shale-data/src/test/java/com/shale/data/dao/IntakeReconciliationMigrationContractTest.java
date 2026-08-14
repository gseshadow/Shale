package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class IntakeReconciliationMigrationContractTest {
    @Test void repairIsSemanticTenantSafeIdempotentAuditedAndValuePreserving() throws Exception {
        String sql = Files.readString(Path.of("../docs/sql/2026-08-14_reconcile_missing_intake_case_dates.sql"));
        assertTrue(sql.contains("SemanticRoleKey='INTAKE'"));
        assertTrue(sql.contains("m.ShaleClientId=c.ShaleClientId OR m.ShaleClientId IS NULL"));
        assertTrue(sql.contains("SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL"));
        assertTrue(sql.contains("NOT EXISTS(\n    SELECT 1 FROM dbo.CaseDates"));
        assertTrue(sql.contains("DATETIME2FROMPARTS"));
        assertTrue(sql.contains("CASE WHEN CallerTime IS NULL THEN 1 ELSE 0 END"));
        assertTrue(sql.contains("PreflightReconciliationCount"));
        assertTrue(sql.contains("ReconciliationCount"));
        assertTrue(sql.contains("CASE_DATES_INTAKE_RECONCILIATION"));
        assertTrue(sql.contains("INSERT dbo.AuditLog"));
        assertTrue(sql.contains("ROLLBACK TRANSACTION"));
        assertFalse(sql.matches("(?s).*CaseDateTypeId\\s*=\\s*12.*"));
        assertFalse(sql.contains("CalendarEvents"));
    }
}
