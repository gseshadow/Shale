package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class NewIntakeAuthoritativeDateRegressionTest {
    @Test void creationResolvesIntakeSemanticallyAndWritesItsEditedDateTimeBeforeCommit() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseDao.java"));
        String create = source.substring(source.indexOf("public NewIntakeCreateResult createIntake"),
                source.indexOf("public static final class IntakeConfigurationException"));
        assertTrue(create.contains("requireConfiguredIntakeValue(con, request, configuredDates)"));
        assertTrue(source.contains("CaseDateSemanticRoleResolver.requireEffectiveTypeId("));
        assertTrue(source.contains("CaseDateSemanticRole.INTAKE"));
        assertTrue(source.contains("LocalDateTime.of(value.value(), request.intakeTime())"));
        assertTrue(create.indexOf("insertConfiguredCaseDate") < create.indexOf("con.commit()"));
        assertTrue(create.indexOf("auditCreatedCaseDate") < create.indexOf("con.commit()"));
        assertFalse(create.contains("CalendarEvent"));
        assertFalse(source.substring(source.indexOf("private static int requireConfiguredIntakeValue"),
                source.indexOf("private void ensureRequiredPartyRolesForTenant")).contains("12"));
    }

    @Test void casesGridImmediatelyProjectsAndOrdersTheNewAuthoritativeOccurrence() throws Exception {
        String summary = Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseSummaryDao.java"));
        assertTrue(summary.contains("MAX(CASE WHEN effective.SemanticRoleKey='INTAKE' THEN CAST(cd.StartsAt AS date) END) IntakeDate"));
        assertTrue(summary.contains("case INTAKE_NEWEST -> \"dates.IntakeDate DESC, c.Id DESC\""));
        assertTrue(summary.contains("case INTAKE_OLDEST -> \"dates.IntakeDate ASC, c.Id ASC\""));
        assertFalse(summary.contains("c.CallerDate"));
    }
}
