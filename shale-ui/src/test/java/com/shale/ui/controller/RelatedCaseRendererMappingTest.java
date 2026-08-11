package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import com.shale.core.dto.CaseSummaryProjection;
import com.shale.data.dao.CaseSummaryDao.RelatedCaseRow;
import org.junit.jupiter.api.Test;

final class RelatedCaseRendererMappingTest {
    @Test void contactRendererMapsAuthoritativeStatusAndCaseId() {
        var model = ContactViewController.toRelatedCaseCardModel(relatedCase("Review", "#123456"));

        assertEquals(42L, model.id());
        assertEquals("Review", model.primaryStatusName());
        assertEquals("#123456", model.primaryStatusColor());
    }

    @Test void organizationRendererMapsAuthoritativeStatusAndCaseId() {
        var model = OrganizationController.toRelatedCaseCardModel(relatedCase("Open", "#ABCDEF"));

        assertEquals(42L, model.id());
        assertEquals("Open", model.primaryStatusName());
        assertEquals("#ABCDEF", model.primaryStatusColor());
    }

    @Test void bothRenderersPreserveCaseCardModelNoStatusNormalization() {
        var row = relatedCase(null, null);

        assertEquals("", ContactViewController.toRelatedCaseCardModel(row).primaryStatusName());
        assertEquals("", ContactViewController.toRelatedCaseCardModel(row).primaryStatusColor());
        assertEquals("", OrganizationController.toRelatedCaseCardModel(row).primaryStatusName());
        assertEquals("", OrganizationController.toRelatedCaseCardModel(row).primaryStatusColor());
    }

    @Test void bothRenderersContinueUsingFullCaseCards() throws Exception {
        String contact = Files.readString(Path.of("src/main/java/com/shale/ui/controller/ContactViewController.java"));
        String organization = Files.readString(Path.of("src/main/java/com/shale/ui/controller/OrganizationController.java"));

        assertTrue(contact.contains("caseCardFactory.create(toRelatedCaseCardModel(row), CaseCardFactory.Variant.FULL)"));
        assertTrue(organization.contains("caseCardFactory.create(toRelatedCaseCardModel(row), CaseCardFactory.Variant.FULL)"));
    }

    private static RelatedCaseRow relatedCase(String statusName, String statusColor) {
        var summary = new CaseSummaryProjection(42L, 7, "C-42", "Example", null, null, null,
                statusName, statusColor, null, null, 9, "Attorney", "#654321", null, null, null,
                LocalDateTime.MIN, LocalDateTime.MIN, false);
        return new RelatedCaseRow(100L, 2, summary, null, null, null, "#FEDCBA", false,
                "Client", "Plaintiff", true, null);
    }
}
