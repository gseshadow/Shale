package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MaterialRequestDatesMigrationTest {
    @Test void migrationAddsIndependentNullableRequestedRangeWithoutDuplicatingExistingDates() throws Exception {
        String sql = Files.readString(Path.of("../docs/sql/2026-07-28_material_request_dates_and_creator_display.sql"));
        assertTrue(sql.contains("RequestedRangeStartDate date NULL"));
        assertTrue(sql.contains("RequestedRangeEndDate date NULL"));
        assertTrue(sql.contains("RequestedRangeStartDate <= RequestedRangeEndDate"));
        assertFalse(sql.contains("ADD RequestedAt"));
        assertFalse(sql.contains("ADD DueAt"));
        assertTrue(sql.contains("CreatedByUserId already exists"));
    }
}
