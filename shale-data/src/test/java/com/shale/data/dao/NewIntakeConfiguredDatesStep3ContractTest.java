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
        assertTrue(s.contains("submitted.keySet().equals(authoritative.keySet())"));
        assertTrue(s.contains("submitted.putIfAbsent(value.fieldKey(), value)"));
        assertTrue(s.contains("actual.caseDateTypeId()!=expected.caseDateTypeId()"));
        assertTrue(s.contains("actual.required()!=expected.required()"));
        assertTrue(s.contains("validateEffectiveConfiguredDateType"));
        assertTrue(s.contains("expected.required() && actual.value()==null"));
        assertTrue(s.contains("if(actual.value()!=null) result.add(actual)"));
        assertTrue(s.contains("value.value().atStartOfDay()"));
        assertTrue(s.contains("EndsAt,AllDay"));
        assertTrue(s.contains("?,NULL,1,SYSUTCDATETIME()"));
    }

    @Test void configuredModeDoesNotDualWriteAndMissingConfigurationRetainsLegacyValues() throws Exception {
        String s=source();
        assertTrue(s.contains("boolean configured = request.formConfigurationId() != 0"));
        assertEquals(5, s.split("configured \\? null : request\\.", -1).length - 1);
        assertTrue(s.contains("if (currentId == 0)"));
        assertTrue(s.contains("return List.of()"));
    }
}
