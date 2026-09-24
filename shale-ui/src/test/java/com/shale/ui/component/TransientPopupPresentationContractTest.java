package com.shale.ui.component;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TransientPopupPresentationContractTest {
    private static String read(String path) throws Exception { return Files.readString(Path.of(path)); }

    @Test void popupStylesAreScopedAndImportedThroughTheCanonicalThemeEntryPoint() throws Exception {
        String css = read("src/main/resources/css/foundation/transient-popups.css");
        String app = read("src/main/resources/css/app.css");
        assertTrue(app.contains("@import \"foundation/transient-popups.css\";"));
        assertTrue(css.contains(".shale-context-menu"));
        assertTrue(css.contains(".rich-text-context-menu"));
        assertTrue(css.contains(".task-hover-popup"));
        assertFalse(css.lines().anyMatch(line -> line.matches("\\s*\\.(?:context-menu|menu-item|tooltip|popup|label|separator)\\s*\\{.*")),
                "Popup rules must not leak through an unscoped global selector.");
        assertFalse(css.contains("#"), "Popup paint must use semantic looked-up tokens rather than hex colors.");
    }

    @Test void EveryApplicationOwnedContextMenuOptsIntoSharedLifecycle() throws Exception {
        String compact = read("src/main/java/com/shale/ui/component/EnhancedTextArea.java");
        String rich = read("src/main/java/com/shale/ui/component/RichTextExpandedEditor.java");
        String cases = read("src/main/java/com/shale/ui/controller/CasesController.java");
        assertTrue(compact.contains("TransientPopupSupport.style(menu, \"rich-text-context-menu\")"));
        assertTrue(rich.contains("TransientPopupSupport.style(contextMenu, \"rich-text-context-menu\")"));
        assertTrue(cases.contains("TransientPopupSupport.style(menu)"));
    }
}
