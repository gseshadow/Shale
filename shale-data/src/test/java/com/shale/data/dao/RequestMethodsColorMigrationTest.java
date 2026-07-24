package com.shale.data.dao;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

final class RequestMethodsColorMigrationTest {
    private static final Path MIGRATION = Path.of("../docs/sql/2026-07-24_request_methods_color.sql");

    @Test void migrationAddsNullableNvarchar20ColorAndSeedsBuiltInDefaults() throws Exception {
        String sql = Files.readString(Files.exists(MIGRATION) ? MIGRATION : Path.of("docs/sql/2026-07-24_request_methods_color.sql"));
        assertTrue(sql.contains("ALTER TABLE dbo.RequestMethods ADD Color nvarchar(20) NULL"));
        for (String key : new String[]{"email", "phone", "fax", "mail", "portal", "in_person", "other"}) {
            assertTrue(sql.contains("SystemKey = N'" + key + "' AND Color IS NULL"), key);
        }
        assertTrue(sql.contains("INCLUDE (SystemKey, Color)"));
        assertFalse(sql.contains("MaterialRequests.RequestMethodId"));
        assertFalse(sql.contains("ALTER TABLE dbo.MaterialRequests ALTER COLUMN RequestMethod"));
    }
}
