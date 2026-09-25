package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Protects the desktop SOL/TCN read boundary from presentation or semantic-role coupling. */
final class CaseDeadlineIdentityCutoverContractTest {
    private static String source(String file) throws Exception {
        return Files.readString(Path.of("src/main/java/com/shale/data/dao/" + file));
    }

    @Test void summaryReadersUseOrdinarySystemFamiliesAndNotCardSelectionsOrDeadlineRoles() throws Exception {
        String sql = source("CaseSummaryDao.java");
        assertAll(
                () -> assertTrue(sql.contains("LOWER(LTRIM(RTRIM(t.SystemKey)))='statute_of_limitations'")),
                () -> assertTrue(sql.contains("LOWER(LTRIM(RTRIM(t.SystemKey)))='tort_notice_deadline'")),
                () -> assertTrue(sql.lines().filter(line -> line.contains("effective.SemanticRoleKey='STATUTE_OF_LIMITATIONS'")).count() == 2),
                () -> assertTrue(sql.lines().filter(line -> line.contains("effective.SemanticRoleKey='TORT_NOTICE_DEADLINE'")).count() == 2),
                () -> assertFalse(sql.contains("CaseDatePresentationSelections")),
                () -> assertFalse(sql.contains("CASE_CARD")));
    }

    @Test void effectiveOverlayMasksInactiveAndMissingFamiliesWhileHistoricalOccurrencesRemainReadable() throws Exception {
        String sql = source("CaseSummaryDao.java");
        assertAll(
                () -> assertTrue(sql.contains("candidate.IsDeleted=0")),
                () -> assertTrue(sql.contains("CASE WHEN candidate.ShaleClientId=c.ShaleClientId THEN 0 ELSE 1 END")),
                () -> assertTrue(sql.contains("effective_type.IsActive=1")),
                () -> assertTrue(sql.contains("stored_type.ShaleClientId=c.ShaleClientId OR stored_type.ShaleClientId IS NULL")
                        || sql.contains("t.ShaleClientId=c.ShaleClientId OR t.ShaleClientId IS NULL")),
                () -> assertFalse(sql.contains("stored_type.IsActive=1")),
                () -> assertFalse(sql.contains("t.IsActive=1")));
    }

    @Test void pagingSortUsesEarliestOccurrenceThenIdAndKeepsIntakeProtected() throws Exception {
        String sql = source("CaseDao.java");
        assertAll(
                () -> assertTrue(sql.contains("case STATUTE_SOONEST, STATUTE_LATEST -> \"statute_of_limitations\"")),
                () -> assertTrue(sql.contains("case TORT_NOTICE_SOONEST -> \"tort_notice_deadline\"")),
                () -> assertTrue(sql.contains("ORDER BY cd.StartsAt ASC,cd.Id ASC")),
                () -> assertTrue(sql.contains("effective_type.IsActive=1")),
                () -> assertTrue(sql.contains("CaseDateSemanticRole.INTAKE.persistedKey()")),
                () -> assertFalse(sql.contains("CaseDateSemanticRole.STATUTE_OF_LIMITATIONS.persistedKey()")),
                () -> assertFalse(sql.contains("CaseDateSemanticRole.TORT_NOTICE_DEADLINE.persistedKey()")),
                () -> assertFalse(sql.contains("CaseDatePresentationSelections")));
    }

    @Test void scalarSummaryDatesChooseTheEarliestValueAndRemainNullable() throws Exception {
        String sql = source("CaseSummaryDao.java");
        assertAll(
                () -> assertTrue(sql.contains("MIN(CASE WHEN LOWER(LTRIM(RTRIM(t.SystemKey)))='statute_of_limitations'")),
                () -> assertTrue(sql.contains("MIN(CASE WHEN LOWER(LTRIM(RTRIM(t.SystemKey)))='tort_notice_deadline'")),
                () -> assertTrue(sql.contains("cd.IsDeleted=0")));
    }
}
