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
		assertValidationPrecedesMutation(method(s, "public NewIntakeCreateResult createIntake"), "insertContact(con,");
		assertValidationPrecedesMutation(method(s, "public NewIntakeCreateResult mergeIntake"), "mergeRoleContact(con,");
        assertTrue(s.indexOf("insertConfiguredCaseDate(con") < s.indexOf("con.commit()"));
        assertTrue(s.contains("con.rollback()"));
    }

	private static void assertValidationPrecedesMutation(String method, String mutation) {
		String compact = method.replaceAll("\\s+", "");
		assertTrue(compact.indexOf("validateConfiguredIntakeDates(con,request)")
				< compact.indexOf(mutation));
	}

	private static String method(String source, String signature) {
		int start = source.indexOf(signature);
		assertTrue(start >= 0, "method signature not found: " + signature);
		int open = source.indexOf('{', start);
		int depth = 0;
		for (int i = open; i < source.length(); i++) {
			if (source.charAt(i) == '{') depth++;
			else if (source.charAt(i) == '}' && --depth == 0) return source.substring(start, i + 1);
		}
		throw new AssertionError("unterminated method: " + signature);
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
        assertTrue(s.contains("LocalDateTime.of(value.value(), request.intakeTime())"));
        assertTrue(s.contains("EndsAt,AllDay"));
        assertTrue(s.contains("?,NULL,?,SYSUTCDATETIME()"));
    }

    @Test void configuredModeDoesNotDualWriteAndMissingConfigurationUsesValidatedEffectiveTypes() throws Exception {
        String s=source();
        String insertCase=s.substring(s.indexOf("private long insertCase("), s.indexOf("/** Configured intake"));
        assertTrue(insertCase.contains("return insertConfiguredIntakeCase"));
        String configured=s.substring(s.indexOf("private static long insertConfiguredIntakeCase"), s.indexOf("private void insertCaseParty"));
        for (String legacy : new String[]{"CallerDate", "CallerTime", "DateOfMedicalNegligence", "DateMedicalNegligenceWasDiscovered", "DateOfInjury", "StatuteOfLimitations", "TortNoticeDeadline"})
            assertFalse(configured.contains(legacy), legacy);
        assertTrue(s.contains("if (currentId == 0)"));
        assertTrue(s.contains("fieldKeyForCaseDateType(value.caseDateTypeId()).equals(value.fieldKey())"));
        assertTrue(s.contains("validateEffectiveConfiguredDateType(con, request.shaleClientId(), value.caseDateTypeId())"));
        String validation=s.substring(s.indexOf("private List<ConfiguredDateValue> validateConfiguredIntakeDates"), s.indexOf("private static void validateEffectiveConfiguredDateType"));
        assertFalse(validation.contains("return List.of()"));
    }

    @Test void configuredIntakeHasNoPostCreateDateTransactionAndReportsCommittedDateCount() throws Exception {
        String s=source();
        String create=s.substring(s.indexOf("public NewIntakeCreateResult createIntake"), s.indexOf("public static final class IntakeConfigurationException"));
        assertEquals(1, create.split("insertConfiguredCaseDate\\(con, request, caseId, date, intakeTypeId\\)", -1).length - 1);
        assertFalse(create.contains("CalendarEvent"));
        assertEquals(1, create.split("db.requireConnection\\(\\)", -1).length - 1);
        assertEquals(1, create.split("con.commit\\(\\)", -1).length - 1);
        assertTrue(create.contains("configuredDates.size()"));
    }
}
