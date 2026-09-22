package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.shale.ui.testutil.JavaFxTestSupport;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.ScrollPane;

final class EntityEditorWindowPresentationTest {
    @Test void organizationCreateFxmlSeparatesSharedRuntimeClasses() {
        JavaFxTestSupport.runAndWait(() -> {
            URL resource = getClass().getResource("/fxml/new-organization.fxml");
            Parent root = assertDoesNotThrow(() -> new FXMLLoader(resource).load());
            assertTrue(root.getStyleClass().contains("entity-editor-root"));
            assertTrue(root.getStyleClass().contains("contact-editor-surface"));
            assertFalse(root.getStyleClass().contains("entity-editor-root contact-editor-surface"));
            ScrollPane scroll = (ScrollPane) root.lookup("#editorScroll");
            assertNotNull(scroll);
            assertTrue(scroll.getStyleClass().containsAll(java.util.List.of("entity-editor-scroll", "contact-editor-section-scroll")));
            assertEquals(ScrollPane.ScrollBarPolicy.NEVER, scroll.getHbarPolicy(),
                    "aggregate editors must not expose page-level horizontal scrolling");
            assertTrue(root.lookup(".entity-editor-footer") != null, "the action footer must remain outside the scrolling form");
        });
    }

    @Test void sharedFoundationUsesSemanticPaintAndIsImportedOnlyThroughAppStylesheet() throws Exception {
        String app = Files.readString(Path.of("src/main/resources/css/app.css"));
        String css = Files.readString(Path.of("src/main/resources/css/foundation/entity-editor-windows.css"));
        assertEquals(1, count(app, "@import \"foundation/entity-editor-windows.css\";"));
        assertTrue(css.contains(".entity-editor-root") && css.contains(".entity-editor-section")
                && css.contains(".entity-editor-child-row") && css.contains(".entity-editor-footer"));
        assertFalse(css.matches("(?s).*(?:#[0-9a-fA-F]{3,8}|rgba?\\().*"),
                "the shared editor foundation must source paint exclusively from semantic theme tokens");
    }

    private static int count(String value, String token) {
        int count=0;
        for(int index=0;(index=value.indexOf(token,index))>=0;index+=token.length())count++;
        return count;
    }
}
