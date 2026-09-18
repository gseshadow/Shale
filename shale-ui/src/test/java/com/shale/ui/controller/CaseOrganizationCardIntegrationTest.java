package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class CaseOrganizationCardIntegrationTest {
    @Test void caseOverviewAndPartiesUseOneBulkHydratedSharedFactoryPath() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/controller/CaseController.java"));
        String renderer = source.substring(source.indexOf("private Node createPartyEntityCard("),
                source.indexOf("static ContactCardFactory.ContactCardModel toContactCardModel"));
        assertTrue(renderer.contains("OrganizationCardFactory.Variant.COMPACT"));
        assertTrue(renderer.contains("factory.create(model,"));
        assertTrue(renderer.contains("casePartyOrganizationPresentations.get"));
        assertFalse(renderer.contains("new OrganizationCard("));

        String hydration = source.substring(source.indexOf("loadCasePartyOrganizationPresentations("),
                source.indexOf("private PartyEditorResult showPartyEditorDialog"));
        assertTrue(hydration.contains("findCardPresentations"),
                "Case party cards must use the directory's bounded authoritative presentation projection");
        assertTrue(hydration.contains("distinct().toList()"),
                "Organization ids must be de-duplicated before the bulk projection query");
        assertFalse(hydration.contains("findStructuredContactProfile"),
                "Case rendering must not introduce per-card structured-profile queries");
    }
}
