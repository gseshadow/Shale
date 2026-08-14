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
        assertTrue(sql.contains("CaseDateSemanticRoles WHERE RoleKey='INTAKE' AND IsProtected=1"));
        assertFalse(sql.contains("CaseDateSemanticRoles WHERE SemanticRoleKey"));
        assertFalse(sql.matches("(?s).*CaseDateSemanticRoles WHERE[^;]*(?:IsActive|IsDeleted).*"));
        assertTrue(sql.contains("ActorUserId,CaseDateTypeId,ScopeCount,"));
        assertTrue(sql.indexOf("ActorUserId,CaseDateTypeId,ScopeCount,") < sql.indexOf("#Candidates WHERE ScopeCount<>1"));
        assertTrue(sql.contains("CandidateOccurrenceFlags"));
        assertTrue(sql.contains("ExistingOccurrence"));
        assertFalse(sql.matches("(?s).*SUM\\s*\\(\\s*CASE\\s+WHEN\\s+(?:NOT\\s+)?EXISTS\\s*\\(.*"));
        assertTrue(sql.contains("m.ShaleClientId=c.ShaleClientId OR m.ShaleClientId IS NULL"));
        assertTrue(sql.contains("SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL"));
        assertTrue(sql.matches("(?s).*#Candidates\\s+x\\s+WHERE\\s+NOT\\s+EXISTS\\s*\\(\\s*"
                + "SELECT\\s+1\\s+FROM\\s+dbo\\.CaseDates\\s+cd.*"));
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
