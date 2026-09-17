package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.shale.core.dto.CaseStatusHistoryDto;
import com.shale.ui.component.StatusTimeline;

final class CaseHeaderPhase2BContractTest {
    private static final Path FXML = Path.of("src/main/resources/fxml/case.fxml");
    private static final Path COMPONENT_CSS = Path.of("src/main/resources/css/foundation/content-components.css");
    private static final Path LEGACY_CSS = Path.of("src/main/resources/css/app.css");

    @Test
    void headerOptsIntoCanonicalHierarchyAndResponsiveMetadataFlow() throws Exception {
        String fxml = Files.readString(FXML);

        assertTrue(fxml.contains("fx:id=\"caseTitleLabel\"") && fxml.contains("styleClass=\"shale-page-title\""),
                "The accessible case title must use the canonical page-title contract.");
        assertTrue(fxml.contains("fx:id=\"caseMetadataFlow\"") && fxml.contains("fx:id=\"caseClassificationFlow\""),
                "Text metadata and wrapping semantic classifications need explicit left-aligned hosts.");
        assertTrue(fxml.contains("alignment=\"TOP_LEFT\""));
        assertTrue(fxml.contains("fx:id=\"caseMetadataLabel\"") && fxml.contains("fx:id=\"intakeTakenByUserHost\"")
                && fxml.contains("fx:id=\"lastUpdatedLabel\""));
        assertTrue(fxml.contains("fx:id=\"headerPracticeAreaHost\"") && fxml.contains("fx:id=\"statusHost\"")
                && fxml.contains("fx:id=\"nonEngagementStateHost\""));
        assertFalse(fxml.contains("style=\"-fx-opacity: 0.55;\""),
                "Header separators must receive theme paint from their semantic class.");
    }

    @Test
    void chronologicalHistoryPreservesOrderRepetitionAndOnlyLatestCurrent() {
        LocalDateTime first = LocalDateTime.of(2026, 1, 1, 9, 0);
        List<StatusTimeline.Item> items = CaseController.toStatusTimelineItems(List.of(
                history(101, 7, "Review", "#336699", first, first.plusDays(1), false),
                history(102, 8, "Testing", "#8844CC", first.plusDays(1), null, true),
                history(103, 7, "Review", "#336699", first.plusDays(2), null, true)));

        assertEquals(List.of("Review", "Testing", "Review"), items.stream().map(StatusTimeline.Item::name).toList(),
                "Chronological records, including repeated statuses, must not be deduplicated.");
        assertEquals(List.of("101", "102", "103"), items.stream().map(StatusTimeline.Item::identity).toList(),
                "Presentation identity must follow history-row identity rather than status-definition identity.");
        assertEquals(List.of(StatusTimeline.State.HISTORICAL, StatusTimeline.State.HISTORICAL,
                StatusTimeline.State.CURRENT), items.stream().map(StatusTimeline.Item::state).toList(),
                "Only the latest authoritative open/primary history row receives current presentation.");
        assertEquals(List.of("#336699", "#8844CC", "#336699"),
                items.stream().map(StatusTimeline.Item::color).toList(),
                "Database-defined color identity must stay attached to each authoritative row.");
        assertTrue(CaseController.toStatusTimelineItems(List.of()).isEmpty(), "Empty history must render safely.");
    }

    @Test
    void migratedHistorySelectorsHaveOneThemeAwareOwner() throws Exception {
        String foundation = Files.readString(COMPONENT_CSS);
        String legacy = Files.readString(LEGACY_CSS);

        assertTrue(foundation.contains(".status-timeline__pill--historical"));
        assertTrue(foundation.contains("-shale-color-stage-inactive"));
        assertTrue(foundation.contains("-shale-color-border-focus"));
        assertFalse(legacy.contains(".status-timeline"),
                "The obsolete app.css status-history block must not compete with the foundation owner.");
    }

    private static CaseStatusHistoryDto history(long historyId, int statusId, String name, String color,
            LocalDateTime effective, LocalDateTime end, boolean primary) {
        return new CaseStatusHistoryDto(historyId, statusId, name, color, null, null, false, null,
                effective, end, effective, effective, primary);
    }
}
