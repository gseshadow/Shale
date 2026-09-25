package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/** Protects the set-based CASE_CARD reader wiring for every desktop card collection. */
final class CaseCardPresentationReaderCutoverTest {
    private static String source(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/com/shale/ui/", relative));
    }

    @Test
    void everyCollectionLoadsCaseCardSelectionsInOneBatchAndPassesProjectionToCards() throws Exception {
        String search=source("services/SearchService.java");
        String contacts=source("controller/ContactViewController.java");
        String organizations=source("controller/OrganizationController.java");
        String users=source("controller/UserController.java");
        String myShale=source("controller/MyShaleController.java");
        String tasks=source("component/factory/TaskCardFactory.java");
        assertAll(
                () -> assertTrue(search.contains("resolveCardDates(cardCaseIds"), "Search must batch active, deleted, and Task Case ids"),
                () -> assertTrue(contacts.contains("resolveCaseDatePresentations(loadedRelatedCases.stream()"), "Contact related Cases must batch"),
                () -> assertTrue(organizations.contains("resolveCardDates(loadedRelatedCases.stream()"), "Organization related Cases must batch"),
                () -> assertTrue(users.contains("loadAssignedCaseCardDates") && users.contains("assignedTaskCaseDates"), "Team/User Cases and Tasks must batch"),
                () -> assertTrue(myShale.contains("resolveCardDates(tasks.stream()") && myShale.contains("resolveCardDates((rows"), "My Shale boards must batch"),
                () -> assertTrue(tasks.contains("model.casePresentationDates()"), "Task embedded Case cards must receive the batch projection"));
    }

    @Test
    void cardsHaveNoFixedDateFallbackAndExplicitEmptyRemainsEmpty() throws Exception {
        String factory=source("component/factory/CaseCardFactory.java");
        assertAll(
                () -> assertFalse(factory.contains("LocalDate intakeDate")),
                () -> assertFalse(factory.contains("LocalDate solDate")),
                () -> assertFalse(factory.contains("setIntakeDate")),
                () -> assertTrue(factory.contains("Objects.requireNonNull(presentationDates"),
                        "all callers must explicitly supply the CASE_CARD result, including an empty list"));
    }

    @Test
    void overviewEditorUsesTheResolvedOccurrenceIdEvenForSystemFamilies() throws Exception {
        String controller=source("controller/CaseController.java");
        assertTrue(controller.contains("filter(d->d.id()==selected.caseDateId())"));
        assertTrue(controller.contains("openOverviewDate(type,value)"));
        assertTrue(controller.contains("if(value!=null){openCaseDateDialog(value);return;}"));
    }
}
