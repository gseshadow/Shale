package com.shale.ui.component;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Source-level layout contract; rendered pixel geometry remains advisory under the UI visual profile. */
final class CaseOverviewDateRowLayoutContractTest {
    private static final Path ROW = Path.of("src/main/java/com/shale/ui/component/CaseOverviewDateRow.java");
    private static final Path CONFIRMATION = Path.of("src/main/java/com/shale/ui/component/CaseDateConfirmationView.java");
    private static final Path CONTROLLER = Path.of("src/main/java/com/shale/ui/controller/CaseController.java");

    @Test
    void ordinaryMissingAndMultipleConfiguredDatesUseOneAlignedRowFactory() throws Exception {
        String controller = Files.readString(CONTROLLER);
        String render = method(controller, "private void renderConfiguredOverviewDates()", "private void openOverviewDate");
        assertAll(
                () -> assertTrue(render.contains("for(int index=0;index<overviewDateConfiguration.visibleDateTypes().size();index++)"),
                        "Every configured Incident & Deadlines type must use the same row layout."),
                () -> assertTrue(render.contains("value==null?\"—\":formatCaseDateOccurrence(value)"),
                        "Missing dates must retain a value slot rather than shifting the row action."),
                () -> assertTrue(render.contains("CaseOverviewDateRow.create"),
                        "Ordinary and confirmed date rows must share the responsive row factory."),
                () -> assertTrue(render.contains("confirmation.status()==CaseDateConfirmationDto.Status.NOT_REQUIRED?null"),
                        "An ordinary date must omit only the confirmation region, not the row or action."));
    }

    @Test
    void pendingConfirmationAndEditActionOccupyIndependentLayoutRegions() throws Exception {
        String row = Files.readString(ROW);
        String controller = Files.readString(CONTROLLER);
        assertAll(
                () -> assertTrue(row.contains("new HBox(10, accent, name, content, editAction)"),
                        "The edit action must remain a dedicated final row region outside confirmation content."),
                () -> assertTrue(row.contains("HBox.setHgrow(content, Priority.ALWAYS)"),
                        "Only the center content region should absorb horizontal resizing."),
                () -> assertTrue(row.contains("content.setMinWidth(0)"),
                        "The center region must shrink before the edit action is displaced."),
                () -> assertTrue(controller.contains("CaseDateConfirmationView.create(confirmation"),
                        "Pending confirmation interaction must remain wired to the existing shared control."),
                () -> assertTrue(controller.contains("ActionButtonFactory.semantic(\"✎\""),
                        "The configured date edit action must remain a semantic pencil button."));
    }

    @Test
    void longConfirmationTextEllipsizesOnOneLineInsteadOfCharacterWrapping() throws Exception {
        String confirmation = Files.readString(CONFIRMATION);
        assertAll(
                () -> assertTrue(confirmation.contains("state.setWrapText(false)"),
                        "Confirmation status must remain a single-line chip."),
                () -> assertTrue(confirmation.contains("state.setTextOverrun(OverrunStyle.ELLIPSIS)"),
                        "Long role names must degrade with an ellipsis."),
                () -> assertTrue(confirmation.contains("state.setTooltip(new Tooltip(message))"),
                        "Ellipsized confirmation text must remain available in a tooltip."),
                () -> assertTrue(confirmation.contains("state.setMinWidth(0)"),
                        "The status label must shrink cleanly without imposing a wide fixed row."),
                () -> assertFalse(confirmation.contains("state.setWrapText(true)"),
                        "Confirmation text must never return to character-by-character wrapping."));
    }

    private static String method(String source, String startToken, String endToken) {
        int start = source.indexOf(startToken);
        int end = source.indexOf(endToken, start);
        assertTrue(start >= 0 && end > start, "Expected controller method boundaries were not found.");
        return source.substring(start, end);
    }
}
