package com.shale.ui.component.dialog;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SharedTimeDurationControlContractTest {
    @Test void bothEditorsUseTheSameEditableLabeledControlAndDisableItForAllDay() throws Exception {
        String shared = source("component/TimeDurationInput.java");
        String wizard = source("component/dialog/NewEventWizard.java");
        String occurrence = source("component/dialog/CaseDateOccurrenceDialog.java");
        assertTrue(shared.contains("startTime.setEditable(true)"));
        assertTrue(shared.contains("labeled(\"Start Time\""));
        assertTrue(shared.contains("labeled(\"Hours\""));
        assertTrue(shared.contains("labeled(\"Minutes\""));
        assertTrue(shared.contains("setAccessibleText(\"Duration Hours\")"));
        assertTrue(shared.contains("setAccessibleText(\"Duration Minutes\")"));
        assertTrue(shared.contains("IntStream.rangeClosed(0, 23)"));
        assertTrue(shared.contains("IntStream.rangeClosed(0, 59)"));
        assertTrue(shared.contains("hours.setValue(1)"));
        assertTrue(shared.contains("minutes.setValue(0)"));
        assertTrue(shared.contains("total == 0"));
        assertTrue(wizard.contains("new TimeDurationInput()"));
        assertTrue(occurrence.contains("new TimeDurationInput()"));
        assertTrue(wizard.contains("timing.setTimedControlsDisabled(disabled)"));
        assertTrue(occurrence.contains("timing.setTimedControlsDisabled(!timed)"));
        assertFalse(wizard.contains("new TextField(\"9:00\")"));
        assertFalse(occurrence.contains("TextField endTime"));
    }

    @Test void focusOrderIsStartTimeThenHoursThenMinutesAndLabelsAreAssociated() throws Exception {
        String shared = source("component/TimeDurationInput.java");
        assertTrue(shared.contains("getChildren().setAll(timeBox, hoursBox, minutesBox)"));
        assertTrue(shared.contains("label.setLabelFor(control)"));
        assertTrue(shared.contains("startTime.setAccessibleText(\"Start Time\")"));
    }

    @Test void authorityAndPersistenceRoutingRemainUnchanged() throws Exception {
        String wizard = source("component/dialog/NewEventWizard.java");
        String occurrence = source("component/dialog/CaseDateOccurrenceDialog.java");
        assertTrue(wizard.contains("selectedCase==null?SourceKind.GENERAL_EVENT:SourceKind.CASE_EVENT"));
        assertTrue(wizard.contains("new GeneralEventInput"));
        assertTrue(wizard.contains("new CaseDateInput"));
        assertTrue(occurrence.contains("existing.rowVer") || !occurrence.contains("rowVer"),
                "RowVer remains owned by the launcher/controller command boundary");
        assertTrue(occurrence.contains("TypeChoice.historical(existing)"));
    }

    private static String source(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/com/shale/ui/" + relative));
    }
}
