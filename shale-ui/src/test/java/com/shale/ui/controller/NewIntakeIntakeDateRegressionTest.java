package com.shale.ui.controller;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class NewIntakeIntakeDateRegressionTest {
    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test void caseSectionHasTimeButNoLegacyIntakeDateControl() throws Exception {
        String fxml = Files.readString(ROOT.resolve("shale-ui/src/main/resources/fxml/new-intake.fxml"));
        int caseStart = fxml.indexOf("text=\"Case\"");
        int partiesStart = fxml.indexOf("text=\"Parties\"", caseStart);
        String caseSection = fxml.substring(caseStart, partiesStart);
        assertFalse(caseSection.contains("Date of Intake"));
        assertFalse(caseSection.contains("dateOfIntakePicker"));
        assertTrue(caseSection.contains("Time of Intake"));
        assertTrue(caseSection.contains("timeOfIntakeField"));
    }

    @Test void controllerUsesSemanticResolutionAndOnlyConfiguredAggregateValues() throws Exception {
        String source = Files.readString(ROOT.resolve("shale-ui/src/main/java/com/shale/ui/controller/NewIntakeController.java"));
        assertTrue(source.contains("resolveEffectiveCaseDateTypeId(tenant, actor, CaseDateSemanticRole.INTAKE)"));
        assertFalse(source.contains("dateOfIntakePicker"));
        assertTrue(source.contains("configuredDateInputs.values().stream().map(input -> new CaseDao.ConfiguredDateValue("));
    }
}
