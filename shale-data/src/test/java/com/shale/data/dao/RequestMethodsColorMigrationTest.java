package com.shale.data.dao;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

final class RequestMethodsColorMigrationTest {
    private static final Path MIGRATION = Path.of("../docs/sql/2026-07-24_request_methods_color.sql");

    @Test void migrationAddsTheNullableApplicationColorContractOnlyWhenAbsent() throws Exception {
        String sql = migration();
        assertTrue(sql.contains("IF COL_LENGTH(N'dbo.RequestMethods', N'Color') IS NULL"));
        assertTrue(sql.contains("ALTER TABLE dbo.RequestMethods ADD Color nvarchar(20) NULL;"));
        assertFalse(sql.contains("DROP TABLE"));
        assertFalse(sql.contains("CREATE TABLE dbo.RequestMethods"));
        assertFalse(sql.contains("DROP INDEX"));
    }

    @Test void rerunsOnlyFillMissingGlobalBuiltInDefaults() throws Exception {
        String sql = migration();
        String[][] defaults = {
                {"email", "#2563EB"}, {"phone", "#16A34A"}, {"fax", "#9333EA"},
                {"mail", "#D97706"}, {"portal", "#0891B2"},
                {"in_person", "#DB2777"}, {"other", "#64748B"}
        };
        for (String[] entry : defaults) {
            assertTrue(sql.contains("SET Color = N'" + entry[1] + "' WHERE ShaleClientId IS NULL AND SystemKey = N'" + entry[0] + "'"), entry[0]);
        }
        assertEquals(defaults.length, count(sql, "AND Color IS NULL;"));
        assertFalse(sql.contains("WHERE Name ="));
        assertFalse(sql.contains("UpdatedAt ="));
    }

    @Test void migrationPreservesLookupShapeAndUnrelatedColorImplementations() throws Exception {
        String sql = migration();
        assertFalse(sql.contains("ALTER TABLE dbo.RequestMethods ALTER COLUMN"));
        assertFalse(sql.contains("ALTER TABLE dbo.MaterialTypes"));
        assertFalse(sql.contains("UPDATE dbo.MaterialTypes"));
        assertFalse(sql.contains("ALTER TABLE dbo.RequestStatuses"));
        assertFalse(sql.contains("UPDATE dbo.RequestStatuses"));
        assertFalse(sql.contains("MaterialRequests.RequestMethodId"));
        assertFalse(sql.contains("ALTER TABLE dbo.MaterialRequests"));
        assertEquals(1, count(sql, "ALTER TABLE dbo.RequestMethods"));
        assertEquals(7, count(sql, "UPDATE dbo.RequestMethods SET Color ="));
    }

    @Test void productionDaoDoesNotConsumeRequestMethodColorInPhaseOne() throws Exception {
        String dao = Files.readString(Path.of("src/main/java/com/shale/data/dao/MaterialRequestDao.java"));
        assertFalse(dao.contains("SELECT Id,ShaleClientId,SystemKey,Name,Color,SortOrder,IsActive,IsDeleted,RowVer FROM dbo.RequestMethods"));
        assertFalse(dao.contains("INSERT dbo.RequestMethods(ShaleClientId,SystemKey,Name,Color"));
        assertFalse(dao.contains("UPDATE dbo.RequestMethods SET Name=?,Color=?"));
        assertTrue(dao.contains("SELECT Id,ShaleClientId,SystemKey,Name,SortOrder,IsActive,IsDeleted,RowVer FROM dbo.RequestMethods"));
    }

    private static String migration() throws Exception {
        return Files.readString(Files.exists(MIGRATION) ? MIGRATION : Path.of("docs/sql/2026-07-24_request_methods_color.sql"));
    }

    private static int count(String text, String needle) {
        int count = 0;
        for (int at = 0; (at = text.indexOf(needle, at)) >= 0; at += needle.length()) count++;
        return count;
    }
}
