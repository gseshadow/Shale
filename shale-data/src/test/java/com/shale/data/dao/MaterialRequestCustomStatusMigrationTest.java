package com.shale.data.dao;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

final class MaterialRequestCustomStatusMigrationTest {
    private static final Path MIGRATION = migration();

    @Test void dropsLegacyStatusAndClosureChecksIdempotentlyWithoutReplacementAllowlist() throws Exception {
        String sql = Files.readString(MIGRATION);
        assertTrue(sql.contains("sys.check_constraints"));
        assertTrue(sql.contains("name = N'CK_MaterialRequests_Status'"));
        assertTrue(sql.contains("ALTER TABLE dbo.MaterialRequests\n    DROP CONSTRAINT CK_MaterialRequests_Status;"));
        assertTrue(sql.contains("name = N'CK_MaterialRequests_Closure'"));
        assertFalse(sql.matches("(?s).*ADD\\s+CONSTRAINT\\s+CK_MaterialRequests_Status.*"));
        assertFalse(sql.contains("'DRAFT','REQUESTED','FOLLOW_UP_DUE'"));
    }

    @Test void deterministicallyCanonicalizesLegacyJavaFxRequestStatusColors() throws Exception {
        String sql = Files.readString(MIGRATION);
        assertTrue(sql.contains("UPDATE dbo.RequestStatuses"));
        assertTrue(sql.contains("N'#' + SUBSTRING(Color, 3, 6)"));
        assertTrue(sql.contains("SUBSTRING(Color, 3, 8) NOT LIKE '%[^0-9A-Fa-f]%'"));
    }

    @Test void restoresDraftAsAVisibleBuiltInInsteadOfAHiddenLegacyValue() throws Exception {
        String sql = Files.readString(MIGRATION);
        assertTrue(sql.contains("LOWER(LTRIM(RTRIM(SystemKey))) = 'draft'"));
        assertTrue(sql.contains("(NULL, 'draft', N'Draft', '#64748B', 0, 1, 0"));
    }

    private static Path migration() {
        Path fromModule = Path.of("..", "docs", "sql", "2026-07-28_material_request_custom_statuses.sql");
        return Files.exists(fromModule) ? fromModule : Path.of("docs", "sql", "2026-07-28_material_request_custom_statuses.sql");
    }
}
