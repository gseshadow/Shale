package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class CasesIntakeTakenByMigrationTest {
    @Test
    void migrationIsNullableIdempotentTenantSafeAndDoesNotBackfill() throws Exception {
        String sql = Files.readString(Path.of("../docs/sql/2026-07-30_cases_intake_taken_by_user.sql"));
        assertTrue(sql.contains("COL_LENGTH(N'dbo.Cases', N'IntakeTakenByUserId') IS NULL"));
        assertTrue(sql.contains("ADD IntakeTakenByUserId int NULL"));
        assertTrue(sql.contains("FOREIGN KEY (ShaleClientId, IntakeTakenByUserId)"));
        assertTrue(sql.contains("REFERENCES dbo.Users (ShaleClientId, Id)"));
        assertTrue(!sql.toUpperCase().contains("UPDATE DBO.CASES"));
    }
}
