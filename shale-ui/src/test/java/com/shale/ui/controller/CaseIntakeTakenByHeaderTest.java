package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class CaseIntakeTakenByHeaderTest {
    @Test
    void intakeUserAppearsBetweenCaseNumberAndStatusAndMetadataCanWrap() throws Exception {
        String fxml = Files.readString(Path.of("src/main/resources/fxml/case.fxml"));
        int number = fxml.indexOf("fx:id=\"caseMetadataLabel\"");
        int intake = fxml.indexOf("fx:id=\"intakeTakenByLabel\"");
        int status = fxml.indexOf("fx:id=\"statusHost\"");

        assertTrue(fxml.contains("<FlowPane hgap=\"8.0\" vgap=\"4.0\""));
        assertTrue(number >= 0 && number < intake && intake < status);
        assertTrue(fxml.contains("text=\"Intake by: —\""));
    }

    @Test
    void detailRenderingResetsNameForEveryCaseAndUsesNullFallback() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/controller/CaseController.java"));
        assertTrue(source.contains("refreshIntakeTakenBy(detail.getIntakeTakenByDisplayName())"));
        assertTrue(source.contains("name.isBlank() ? \"—\" : name"));
    }
}
