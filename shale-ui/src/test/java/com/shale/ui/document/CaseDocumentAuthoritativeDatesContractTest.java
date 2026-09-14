package com.shale.ui.document;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class CaseDocumentAuthoritativeDatesContractTest {
    @Test void modelCompositionConsumesOnlyDocumentProjectionDates() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/document/CaseDocumentService.java"));
        String method = source.substring(source.indexOf("public CaseDocumentModel buildCaseDocumentModel"),
                source.indexOf("private ContactDao.ContactDetailRow loadContact"));
        assertTrue(method.contains("documentCase.dateOfInjury()"));
        assertTrue(method.contains("documentCase.statuteOfLimitations()"));
        assertFalse(method.contains("overview.getIncidentDate()"));
        assertFalse(method.contains("overview.getSolDate()"));
    }

    @Test void caseControllerOverviewBaselinesDoNotConsumeMigratedScalars() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/controller/CaseController.java"));
        assertFalse(source.contains("getIntakeDate()"));
        assertFalse(source.contains("getIncidentDate()"));
        assertFalse(source.contains("getSolDate()"));
        assertFalse(source.contains("getTortNoticeDeadline()"));
        assertTrue(source.contains("authoritativeDate(MigratedCaseDateKey.DATE_OF_INJURY)"));
        assertTrue(source.contains("authoritativeDate(MigratedCaseDateKey.STATUTE_OF_LIMITATIONS)"));
    }
}
