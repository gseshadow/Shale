package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CaseDatesOccurrenceTitleMigrationTest {
    private static String sql() throws Exception {
        return Files.readString(Path.of("..", "docs", "sql", "2026-08-18_case_dates_occurrence_title.sql"));
    }

    @Test void migrationIsForwardOnlyIdempotentAndVerifiesNullableNvarchar255() throws Exception {
        String sql=sql();
        assertAll(
            () -> assertTrue(sql.contains("IF COL_LENGTH(N'dbo.CaseDates', N'Title') IS NULL")),
            () -> assertTrue(sql.contains("ADD Title nvarchar(255) NULL")),
            () -> assertTrue(sql.contains("c.max_length=510")),
            () -> assertTrue(sql.contains("c.is_nullable=1")),
            () -> assertTrue(sql.contains("THROW 55819")),
            () -> assertFalse(sql.toUpperCase().contains(" DEFAULT ")),
            () -> assertFalse(sql.toUpperCase().contains("UPDATE DBO.CASEDATES")));
    }
}
