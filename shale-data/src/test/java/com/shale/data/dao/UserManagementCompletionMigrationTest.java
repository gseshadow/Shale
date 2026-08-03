package com.shale.data.dao;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

final class UserManagementCompletionMigrationTest {
    private static String sql() throws Exception { return Files.readString(Path.of("../docs/sql/2026-08-03_users_management_completion.sql")); }

    @Test void auditConstraintIsTransactionalTrustedIdempotentAndAcceptsEveryEmittedEntityType() throws Exception {
        String sql=sql();
        assertTrue(sql.contains("BEGIN TRANSACTION")); assertTrue(sql.contains("XACT_ABORT ON"));
        assertTrue(sql.contains("@OldDefinition")); assertTrue(sql.contains("Preserve any older quoted values"));
        assertTrue(sql.contains("DROP CONSTRAINT CK_EntityActionAuditLog_EntityType"));
        assertTrue(sql.contains("WITH CHECK ADD CONSTRAINT CK_EntityActionAuditLog_EntityType"));
        assertTrue(sql.contains("is_not_trusted=0")); assertTrue(sql.contains("definition LIKE N'%USER%'"));
        Set<String> emitted=Arrays.stream(EntityActionAuditEvent.EntityType.values()).map(Enum::name).collect(java.util.stream.Collectors.toSet());
        Matcher m=Pattern.compile("\\(N'([A-Z_]+)'\\)").matcher(sql);Set<String> accepted=new java.util.HashSet<>();while(m.find())accepted.add(m.group(1));
        assertTrue(accepted.containsAll(emitted),"migration must seed every emitted EntityType: "+emitted);
    }

    @Test void removalSchemaIsCompleteAndUsesDynamicSqlAfterColumnAdds() throws Exception {
        String sql=sql();
        assertTrue(sql.contains("IsRemoved bit NOT NULL")); assertTrue(sql.contains("RemovedAt datetime2(7) NULL"));
        assertTrue(sql.contains("RemovedByUserId int NULL")); assertTrue(sql.contains("CK_Users_RemovalMetadata"));
        assertTrue(sql.contains("IsRemoved=1 AND is_deleted=1")); assertTrue(sql.contains("sp_executesql"));
        assertTrue(sql.contains("IX_Users_Tenant_Removed_Inactive_Name"));
    }
}
