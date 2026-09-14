package com.shale.data.dao;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class UserManagementCompletionMigrationTest {
    private static String sql() throws Exception {
        return AuditEntityTypeMigrationChain.read(AuditEntityTypeMigrationChain.USER_MANAGEMENT);
    }
    @Test void userManagementAuditConstraintMatchesItsHistoricalVocabularyAndSafetyContract() throws Exception {
        String sql=sql();
        assertTrue(sql.contains("BEGIN TRANSACTION")); assertTrue(sql.contains("XACT_ABORT ON"));
        assertTrue(sql.contains("WITH CHECK ADD CONSTRAINT"));
        assertTrue(sql.contains("CHECK CONSTRAINT CK_EntityActionAuditLog_EntityType"));
        assertTrue(sql.contains("is_disabled=0 AND is_not_trusted=0"));
        assertTrue(sql.contains("IF XACT_STATE() <> 0 ROLLBACK TRANSACTION"));
        assertEquals(AuditEntityTypeMigrationChain.USER_MANAGEMENT_SEEDED,
                AuditEntityTypeMigrationChain.historicalSeededAllowlist(sql),
                "the immutable User Management migration must contain only its deployment-era seed vocabulary");
    }

    @Test void removalSchemaIsCompleteAndUsesDynamicSqlAfterColumnAdds() throws Exception {
        String sql=sql();
        assertTrue(sql.contains("IsRemoved bit NOT NULL")); assertTrue(sql.contains("RemovedAt datetime2(7) NULL"));
        assertTrue(sql.contains("RemovedByUserId int NULL")); assertTrue(sql.contains("CK_Users_RemovalMetadata"));
        assertTrue(sql.contains("IsRemoved=1 AND is_deleted=1")); assertTrue(sql.contains("sp_executesql"));
        assertTrue(sql.contains("IX_Users_Tenant_Removed_Inactive_Name"));
    }
}
