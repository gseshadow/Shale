package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

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
}
