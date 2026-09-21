package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/** Protects the demonstrated Phase 6 unification gaps without fixing skin geometry. */
final class CaseOverviewPhase6SupportingComponentsTest {
    private static final String FXML = read("src/main/resources/fxml/case.fxml");
    private static final String CONTROLLER = read("src/main/java/com/shale/ui/controller/CaseController.java");
    private static final String COMPONENTS = read("src/main/resources/css/foundation/content-components.css");
    private static final String FORMS = read("src/main/resources/css/foundation/forms.css");

    @Test
    void updateSearchUsesSharedFieldAndAccessibleClearAction() {
        assertTrue(FXML.contains("styleClass=\"shale-search-field\""),
                "Updates search must opt into the shared search-field component");
        assertTrue(FORMS.contains(".text-field.shale-search-field:focused"),
                "The shared owner must retain a visible focus state");
        assertFalse(COMPONENTS.contains(".shale-update-search {"),
                "Case Overview must not duplicate shared search-field paint");
        assertTrue(CONTROLLER.contains("clearCaseUpdatesSearchButton.setAccessibleText(\"Clear case updates search\")")
                        && CONTROLLER.contains("clearCaseUpdatesSearchButton.setTooltip(new Tooltip(\"Clear case updates search\"))")
                        && CONTROLLER.contains("caseUpdatesSearchField.clear()")
                        && CONTROLLER.contains("caseUpdatesSearchField.requestFocus()"),
                "Clear must be a named, keyboard-native action that restores search focus");
    }

    @Test
    void emptyFilteredEmptyLoadingAndErrorRemainDistinctAndShared() {
        assertTrue(CONTROLLER.contains("\"No updates yet.\" : \"No updates match the current search.\""));
        for (String style : new String[] { "shale-empty-message", "shale-filtered-empty-message",
                "shale-loading-message", "shale-error-message" }) {
            assertTrue(CONTROLLER.contains(style), "Production should use shared state class " + style);
            assertTrue(COMPONENTS.contains("." + style), "Foundation should own state class " + style);
        }
        assertTrue(CONTROLLER.contains("Updates could not be loaded. Try refreshing the case."));
        assertFalse(CONTROLLER.contains("Updates could not be loaded. \" +"),
                "User-visible failure copy must not append internal exception details");
    }

    @Test
    void updateCardKeepsInteractionOnAuthorizedControlOnly() {
        assertFalse(COMPONENTS.contains(".shale-update-card:hover"),
                "A display-only update card must not advertise whole-card interaction");
        assertTrue(CONTROLLER.contains("if (canEditCaseUpdate(dto))"));
        assertTrue(CONTROLLER.contains("setAccessibleText(\"Edit update\")")
                        && CONTROLLER.contains("new Tooltip(\"Edit update\")"));
        assertTrue(CONTROLLER.contains("avatar.getStyleClass().addAll(\"shale-avatar\", \"shale-avatar-compact\")")
                        && CONTROLLER.contains("avatar.setAccessibleText(safeAuthorName(dto) + \" avatar\")"),
                "Updates must reuse the accessible shared avatar fallback");
    }

    private static String read(String path) {
        try {
            return Files.readString(Path.of(path));
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }
}
