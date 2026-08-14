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
    private static String finalAuditConstraintSql() throws Exception {
        return Files.readString(Path.of("../docs/sql/2026-08-14_entity_action_audit_entity_type_constraint_case_status.sql"));
    }

    @Test void auditConstraintIsTransactionalTrustedIdempotentAndAcceptsEveryEmittedEntityType() throws Exception {
        String sql=finalAuditConstraintSql();
        assertTrue(sql.contains("BEGIN TRANSACTION")); assertTrue(sql.contains("XACT_ABORT ON"));
        assertTrue(sql.contains("Complete current vocabulary and every value known to have been allowed by deployed migrations"));
        assertTrue(sql.contains("INSERT @Allowed (Value)"));
        assertTrue(Pattern.compile("(?s)INSERT\\s+@Allowed\\s*\\(Value\\)\\s+SELECT\\s+e\\.Value\\s+FROM\\s+@Extracted")
                .matcher(sql).find());
        assertTrue(sql.contains("Existing EntityActionAuditLog rows contain an EntityType outside the resulting allowlist"));
        assertTrue(sql.contains("QUOTENAME(@AllowlistName)"));
        assertTrue(sql.contains("WITH CHECK ADD CONSTRAINT"));
        assertTrue(sql.contains("CHECK CONSTRAINT CK_EntityActionAuditLog_EntityType"));
        assertTrue(sql.contains("is_disabled = 0 AND is_not_trusted = 0) <> 1"));
        assertTrue(sql.contains("before_check.ObjectId <> @AllowlistObjectId"));
        assertTrue(sql.contains("IF XACT_STATE() <> 0 ROLLBACK TRANSACTION"));
        Set<String> emitted=Arrays.stream(EntityActionAuditEvent.EntityType.values()).map(Enum::name).collect(java.util.stream.Collectors.toSet());
        int valuesStart=sql.indexOf("INSERT @Allowed (Value) VALUES");
        int valuesEnd=sql.indexOf(';',valuesStart);
        Matcher m=Pattern.compile("\\('([A-Z_]+)'\\)").matcher(sql.substring(valuesStart,valuesEnd));Set<String> accepted=new java.util.HashSet<>();while(m.find())accepted.add(m.group(1));
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
