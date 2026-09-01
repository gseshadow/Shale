package com.shale.ui.controller;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CalendarSemanticControlMigrationTest {
    @Test
    void mainCalendarUsesSemanticActionsFormsAndSegmentedViews() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/controller/CalendarController.java"));
        String fxml = Files.readString(Path.of("src/main/resources/fxml/calendar.fxml"));

        assertTrue(source.contains("ControlStyles.apply(newEventButton, ControlStyles.Purpose.PRIMARY"));
        assertTrue(source.contains("ControlStyles.apply(todayButton, ControlStyles.Purpose.SECONDARY"));
        assertTrue(source.contains("ControlStyles.apply(prevWeekButton, ControlStyles.Purpose.NAVIGATION, ControlStyles.Size.SMALL)"));
        assertTrue(source.contains("ControlStyles.apply(nextWeekButton, ControlStyles.Purpose.NAVIGATION, ControlStyles.Size.SMALL)"));
        assertTrue(source.contains("ControlStyles.apply(clearFiltersButton, ControlStyles.Purpose.GHOST"));
        assertTrue(source.contains("ControlStyles.formControl(searchTextField)"));
        assertTrue(source.contains("ControlStyles.formControl(caseFilterCombo)"));
        assertTrue(source.contains("ControlStyles.formControl(eventTypeFilterCombo)"));
        assertTrue(fxml.contains("shale-segmented-control"));
        assertTrue(fxml.contains("styleClass=\"shale-segment\""));
        assertTrue(fxml.contains("<FlowPane hgap=\"8\" vgap=\"8\""));
        assertFalse(fxml.contains("app-toolbar-button"));
        assertFalse(fxml.contains("shale-filter-combo"));
        assertFalse(fxml.contains("shale-search-field"));
    }

    @Test
    void eventEditorUsesOneAffirmativePurposeAndDangerOnlyForDelete() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/component/dialog/NewCalendarEventDialog.java"));
        assertTrue(source.contains("ControlStyles.apply(saveButton, ControlStyles.Purpose.PRIMARY)"));
        assertTrue(source.contains("ControlStyles.apply(deleteButton, ControlStyles.Purpose.DANGER)"));
        assertTrue(source.contains("ControlStyles.apply(cancelButton, ControlStyles.Purpose.SECONDARY)"));
        assertTrue(source.contains("ControlStyles.formControl(titleField)"));
        assertTrue(source.contains("ControlStyles.formControl(eventTypeComboBox)"));
        assertTrue(source.contains("ControlStyles.formControl(datePicker)"));
        assertTrue(source.contains("ControlStyles.formControl(startTimeCombo)"));
        assertTrue(source.contains("ControlStyles.formControl(amPmCombo)"));
        assertTrue(source.contains("ControlStyles.formControl(durationCombo)"));
        assertTrue(source.contains("import com.shale.ui.component.EnhancedTextArea;"));
        assertTrue(source.contains("EnhancedTextArea descriptionArea = new EnhancedTextArea()"));
        assertTrue(source.contains("descriptionArea.setEditorTitle(\"Event Description\")"));
        assertTrue(source.contains("content.getChildren().addAll(titleTypeRow,typeDateRow,timeRow,descriptionLabel,descriptionArea,"),
                "enhanced description should remain in the event form");
        assertTrue(source.contains("descriptionArea.getText()"), "event save should retain the enhanced description draft");
        assertFalse(source.matches("(?s).*\\bTextArea\\s+descriptionArea\\b.*"),
                "event description must not regress to a raw TextArea declaration");
        assertFalse(source.contains("ControlStyles.formControl(descriptionArea)"),
                "EnhancedTextArea owns semantic styling for its internal control");
        assertFalse(source.contains("ControlStyles.apply(saveButton, ControlStyles.Purpose.DANGER)"));
        assertFalse(source.contains("ControlStyles.apply(cancelButton, ControlStyles.Purpose.PRIMARY)"));
        assertTrue(source.lines()
                .filter(line -> line.contains("ControlStyles.Purpose.DANGER"))
                .allMatch(line -> line.contains("deleteButton")), "Delete should remain the sole destructive role");
        assertFalse(source.contains("app-dialog-button"));
        assertFalse(source.contains("-fx-text-fill: #b42318"));
    }

    @Test
    void caseCalendarOwnsExactlyOnePrimaryAction() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/controller/CaseController.java"));
        String fxml = Files.readString(Path.of("src/main/resources/fxml/case.fxml"));
        assertTrue(source.contains("ControlStyles.apply(caseCalendarNewEventButton, ControlStyles.Purpose.PRIMARY"));
        assertTrue(source.contains("ControlStyles.apply(caseCalendarNewTaskButton, ControlStyles.Purpose.SECONDARY"));
        assertTrue(fxml.contains("<Button fx:id=\"caseCalendarNewEventButton\" text=\"New Event\" />"));
        assertTrue(fxml.contains("<Button fx:id=\"caseCalendarNewTaskButton\" text=\"New Task\" />"));
    }

}
