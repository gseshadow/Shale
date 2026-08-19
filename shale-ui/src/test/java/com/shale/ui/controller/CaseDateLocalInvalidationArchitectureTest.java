package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;

import com.shale.core.model.MigratedCaseDateKey;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CaseDateLocalInvalidationArchitectureTest {
    private static final Path CONTROLLER = Path.of("src/main/java/com/shale/ui/controller/CaseController.java");
    private static final Path DIALOG = Path.of("src/main/java/com/shale/ui/component/dialog/CaseDateOccurrenceDialog.java");

    @Test void everySharedMappingIsRenderedAndSavedThroughTheAuthoritativeContract() throws Exception {
        String source = Files.readString(CONTROLLER);
        assertEquals(9, MigratedCaseDateKey.values().length);
        for (MigratedCaseDateKey key : MigratedCaseDateKey.values()) {
            assertTrue(source.contains("MigratedCaseDateKey." + key.name()), key.name());
        }
        assertTrue(source.contains("MigratedCaseDateKey.require(systemKey)"));
        assertFalse(source.contains("SystemKeys"));
    }

    @Test void actualOverviewButtonsRouteToAggregateAndInitializeFromAuthoritativeSnapshot() throws Exception {
        String source = Files.readString(CONTROLLER);
        String[][] protectedButtons = {
                {"onEditIncidentDateField", "DATE_OF_INJURY"},
                {"onEditDateOfMedicalNegligenceField", "DATE_OF_MEDICAL_NEGLIGENCE"},
                {"onEditSolDateField", "STATUTE_OF_LIMITATIONS"},
                {"onEditTortNoticeDeadlineField", "TORT_NOTICE_DEADLINE"}
        };
        for (String[] button : protectedButtons) {
            String handler = method(source, button[0]);
            String key = "MigratedCaseDateKey." + button[1];
            assertTrue(handler.contains("authoritativeDate(" + key + ")"),
                    button[0] + " must read the authoritative snapshot");
            assertTrue(handler.contains("saveAuthoritativeDate(" + key + ", value)"),
                    button[0] + " must use the aggregate mutation path");
            assertFalse(handler.contains("saveCoreOverviewField"), button[0]);
            assertFalse(handler.contains("saveDetailDateOverviewField"), button[0]);
            assertFalse(handler.contains("currentOverview"), button[0]);
            assertFalse(handler.contains("current.get"), button[0]);
        }
    }

    @Test void generalHydrationCannotOverwriteAnyFixedDateControl() throws Exception {
        String source = Files.readString(CONTROLLER);
        String renderer = block(source, "private final class CaseOverviewRenderer");
        assertFalse(renderer.contains("getDateOfMedicalNegligence()"));
        String details = block(source, "private final class CaseDetailsEditor");
        for (String field : new String[] {"detCallerDateValue", "detDateOfMedicalNegligenceValue",
                "detDateMedicalNegligenceWasDiscoveredValue", "detDateOfInjuryValue",
                "detStatuteOfLimitationsValue", "detTortNoticeDeadlineValue", "detDiscoveryDeadlineValue",
                "detDateFeeAgreementSignedValue", "detDateNonEngagementLetterSentValue"}) {
            assertFalse(details.contains(field + ".setText"), field);
        }
        assertTrue(source.contains("renderCompatibilityDates()"));
    }

    @Test void unrelatedDesktopSavesUseNonMigratedDaoBoundaries() throws Exception {
        String source = Files.readString(CONTROLLER);
        assertTrue(source.contains("caseDao.updateCaseNonDate("));
        assertTrue(source.contains("caseDao.updateCaseDetailsNonMigrated("));
        assertFalse(source.contains("caseDao.updateCase("));
        assertFalse(source.contains("caseDao.updateCaseDetails("));
        String detailsEditor = block(source, "private final class CaseDetailsEditor");
        assertFalse(detailsEditor.contains("LocalDate.now()"), "workflow flags must not fabricate fixed dates");
    }

    @Test void fixedSuccessRefreshesDatesWithoutIssuingAnotherMutation() throws Exception {
        String source = Files.readString(CONTROLLER);
        String save = method(source, "saveAuthoritativeValues");
        assertTrue(save.contains("compatibilityDates.replace(result)"));
        assertTrue(save.contains("refreshCaseDateViewsAfterLocalMutation(activeCaseId, false)"));
        assertEquals(1, occurrences(save, "mutateMigratedCompatibilityDates(command)"));
        assertFalse(save.contains("loadMigratedCompatibilityDateSnapshot"));
    }

    @Test void genericMutationsReloadACompleteSnapshotOnlyForMappedTypes() throws Exception {
        String source = Files.readString(CONTROLLER);
        assertTrue(source.contains("compatibilityAffected = isMigratedCaseDateType(input.caseDateTypeId())"));
        assertTrue(source.contains("existing != null && isMigratedCaseDateSystemKey(existing.typeSystemKey())"));
        assertEquals(2, occurrences(source, "isMigratedCaseDateSystemKey(d.typeSystemKey())"), "remove and restore");
        String refresh = method(source, "refreshCaseDateViewsAfterLocalMutation");
        assertTrue(refresh.contains("loadCompatibilityDatesAsync(activeCaseId)"));
        assertFalse(refresh.contains("compatibilityDates.replace"), "generic mutations must not patch visible values/tokens");
    }

    @Test void serviceWorkIsOffFxAndResponsesHaveOrderingAndCaseGuards() throws Exception {
        String source = Files.readString(CONTROLLER);
        String dialog = Files.readString(DIALOG);
        assertTrue(source.contains("CompletableFuture.supplyAsync"));
        assertTrue(source.contains("caseDateExecutor"));
        assertTrue(dialog.contains("whenComplete"));
        assertTrue(dialog.contains("Platform.runLater"));
        assertTrue(source.contains("isCompatibilityDatesCurrent(activeCaseId, generation)"));
        assertTrue(source.contains("final int generation = ++compatibilityDatesGeneration"));
        assertTrue(source.contains("caseId.longValue() != activeCaseId"));
        assertTrue(source.contains("overviewScrollPane.getScene() != null"));
    }

    @Test void localRefreshDoesNotUseLegacyWritersOrWorkflowFabrication() throws Exception {
        String source = Files.readString(CONTROLLER);
        String refresh = method(source, "refreshCaseDateViewsAfterLocalMutation");
        assertFalse(refresh.contains("updateCase("));
        assertFalse(refresh.contains("updateCaseDetails("));
        assertFalse(refresh.contains("LocalDate.now"));
        assertFalse(refresh.contains("FeeAgreementSigned.setSelected"));
        assertFalse(refresh.contains("NonEngagementLetterSent.setSelected"));
    }

    private static String method(String source, String name) {
        return block(source, "private void " + name + "(");
    }

    private static String block(String source, String marker) {
        int declaration = source.indexOf(marker);
        assertTrue(declaration >= 0, "Missing declaration " + marker);
        int open = source.indexOf('{', declaration);
        assertTrue(open >= 0, "Missing body " + marker);
        int depth = 0;
        boolean quoted = false;
        boolean characterLiteral = false;
        boolean escaped = false;
        boolean lineComment = false;
        boolean blockComment = false;
        for (int i = open; i < source.length(); i++) {
            char character = source.charAt(i);
            char next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';
            if (lineComment) {
                if (character == '\n') lineComment = false;
                continue;
            }
            if (blockComment) {
                if (character == '*' && next == '/') { blockComment = false; i++; }
                continue;
            }
            if (quoted || characterLiteral) {
                if (escaped) escaped = false;
                else if (character == '\\') escaped = true;
                else if (quoted && character == '"') quoted = false;
                else if (characterLiteral && character == '\'') characterLiteral = false;
                continue;
            }
            if (character == '/' && next == '/') { lineComment = true; i++; continue; }
            if (character == '/' && next == '*') { blockComment = true; i++; continue; }
            if (character == '"') { quoted = true; continue; }
            if (character == '\'') { characterLiteral = true; continue; }
            if (character == '{') depth++;
            else if (character == '}' && --depth == 0) return source.substring(open, i + 1);
        }
        return fail("Unclosed body " + marker);
    }

    private static int occurrences(String text, String needle) {
        return (text.length() - text.replace(needle, "").length()) / needle.length();
    }
}
