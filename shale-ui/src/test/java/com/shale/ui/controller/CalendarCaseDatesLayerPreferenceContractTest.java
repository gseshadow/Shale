package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CalendarCaseDatesLayerPreferenceContractTest {
    private static String controller() throws Exception {
        return Files.readString(Path.of("src/main/java/com/shale/ui/controller/CalendarController.java"));
    }

    @Test void noPreferenceDefaultsCaseDatesVisible() throws Exception {
        String source = controller();
        assertTrue(source.contains("getBoolean(CASE_DATES_LAYER_PREFERENCE, true)"));
        assertTrue(source.contains("caseDatesLayerCheckBox.setSelected(true)"));
    }

    @Test void explicitHiddenPreferenceIsAppliedWithoutWritingOverIt() throws Exception {
        String source = controller();
        assertTrue(source.contains("caseDatesLayerCheckBox.setSelected(selected)"));
        assertTrue(source.contains("suppressLayerPreferenceWrite = true"));
        assertTrue(source.contains("!suppressLayerPreferenceWrite"));
    }

    @Test void userToggleIsPersistedAndRefreshDoesNotResetLayers() throws Exception {
        String source = controller();
        assertTrue(source.contains("putBoolean(CASE_DATES_LAYER_PREFERENCE, newValue)"));
        String load = source.substring(source.indexOf("private void loadCurrentRange"), source.indexOf("private void applyFiltersAndRender"));
        assertFalse(load.contains("setLayerDefaults()"));
        String live = source.substring(source.indexOf("private void handleEntityUpdated"), source.indexOf("private boolean rememberCaseDatesEvent"));
        assertFalse(live.contains("setLayerDefaults()"));
    }
}
