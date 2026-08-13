package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

class NewIntakeConfiguredDatesStep3ContractTest {
    private static String source() throws Exception {
        return Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseDao.java"));
    }

    @Test void configurationAndEveryStableFieldCrossTheAtomicBoundary() throws Exception {
        String s=source();
        assertTrue(s.contains("long formConfigurationId"));
        assertTrue(s.contains("byte[] formConfigurationRowVer"));
        assertTrue(s.contains("record ConfiguredDateValue(String fieldKey, int caseDateTypeId, boolean required, LocalDate value)"));
        assertTrue(s.indexOf("validateConfiguredIntakeDates(con, request)") < s.indexOf("insertContact(con,"));
        assertTrue(s.indexOf("insertConfiguredCaseDate(con") < s.indexOf("con.commit()"));
        assertTrue(s.contains("con.rollback()"));
    }

    @Test void exactSetRequiredEffectiveWinnerAndDateOnlyRulesAreEnforced() throws Exception {
        String s=source();
        String compact=s.replaceAll("\\s+","");
        assertTrue(s.contains("submitted.keySet().equals(authoritative.keySet())"));
        assertTrue(s.contains("submitted.putIfAbsent(value.fieldKey(), value)"));
        assertTrue(compact.contains("actual.caseDateTypeId()!=expected.caseDateTypeId()"));
        assertTrue(compact.contains("actual.required()!=expected.required()"));
        assertTrue(s.contains("validateEffectiveConfiguredDateType"));
        assertTrue(compact.contains("expected.required()&&actual.value()==null"));
        assertTrue(compact.contains("if(actual.value()!=null)result.add(actual)"));
        assertTrue(s.contains("value.value().atStartOfDay()"));
        assertTrue(s.contains("EndsAt,AllDay"));
        assertTrue(s.contains("?,NULL,1,SYSUTCDATETIME()"));
    }

    @Test void configuredModeDoesNotDualWriteAndMissingConfigurationFailsClosed() throws Exception {
        String s=source();
        String insertCase=s.substring(s.indexOf("private long insertCase("), s.indexOf("/** Configured intake"));
        assertTrue(insertCase.contains("return insertConfiguredIntakeCase"));
        String configured=s.substring(s.indexOf("private static long insertConfiguredIntakeCase"), s.indexOf("private void insertCaseParty"));
        for (String legacy : new String[]{"CallerDate", "CallerTime", "DateOfMedicalNegligence", "DateMedicalNegligenceWasDiscovered", "DateOfInjury", "StatuteOfLimitations", "TortNoticeDeadline"})
            assertFalse(configured.contains(legacy), legacy);
        assertTrue(s.contains("if (currentId == 0)"));
        assertTrue(s.contains("intake form configuration is unavailable"));
        String validation=s.substring(s.indexOf("private List<ConfiguredDateValue> validateConfiguredIntakeDates"), s.indexOf("private static void validateEffectiveConfiguredDateType"));
        assertFalse(validation.contains("return List.of()"));
    }

    @Test void configuredIntakeHasNoPostCreateDateTransactionAndReportsCommittedDateCount() throws Exception {
        String s=source();
        String create=s.substring(s.indexOf("public NewIntakeCreateResult createIntake"), s.indexOf("public static final class IntakeConfigurationException"));
        assertEquals(1, create.split("db.requireConnection\\(\\)", -1).length - 1);
        assertEquals(1, create.split("con.commit\\(\\)", -1).length - 1);
        assertTrue(create.contains("configuredDates.size()"));
    }
}
