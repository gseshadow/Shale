package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.shale.core.dto.CaseStatusDto;

import javafx.scene.paint.Color;

final class SettingsControllerLifecycleTest {

    @Test
    void initializeLoadsCaseStatusesWhenServiceWasInjectedBeforeFxmlInjection() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/controller/SettingsController.java"));
        String initialize = source.substring(source.indexOf("\t@FXML\n\tprivate void initialize()"),
                source.indexOf("\n\tpublic void init", source.indexOf("\t@FXML\n\tprivate void initialize()")));

        assertTrue(initialize.contains("if (caseService != null)"),
                "SceneManager injects SettingsController dependencies through the controller factory before FXML initialize(); initialize must load statuses after TableView injection.");
        assertTrue(initialize.contains("loadCaseStatuses();"),
                "SettingsController.initialize() should populate Settings > Case Statuses when service injection already happened.");
    }

    @Test
    void statusColorRoundTripsDatabaseHexFormat() {
        Color color = SettingsController.dbColorToFx("0x28A745FF");

        assertEquals(0x28 / 255.0, color.getRed(), 0.0001);
        assertEquals(0xA7 / 255.0, color.getGreen(), 0.0001);
        assertEquals(0x45 / 255.0, color.getBlue(), 0.0001);
        assertEquals(1.0, color.getOpacity(), 0.0001);
        assertEquals("0x28A745FF", SettingsController.fxColorToDb(color));
    }

    @Test
    void colorConversionUsesSafeDefaultForBlankValues() {
        assertEquals("0x6C757DFF", SettingsController.fxColorToDb(SettingsController.dbColorToFx("")));
    }

    @Test
    void protectedStatusKeysArePreservedOnEditAndOmittedForNewStatuses() {
        CaseStatusDto existing = new CaseStatusDto(7, "Accepted", true, 20, "0x28A745FF", "accepted", "accepted", 7);

        assertEquals("accepted", SettingsController.lifecycleKeyForSave(existing));
        assertEquals("accepted", SettingsController.systemKeyForSave(existing));
        assertNull(SettingsController.lifecycleKeyForSave(null));
        assertNull(SettingsController.systemKeyForSave(null));
    }

}
