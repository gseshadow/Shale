package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class CustomDictionaryManagementContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/shale/ui");

    @Test void settingsUsesOneOrdinaryUserRowAndDoesNotOwnOrEagerlyLoadTheWordList() throws Exception {
        String fxml = Files.readString(Path.of("src/main/resources/fxml/settings.fxml"));
        String settings = read("controller/SettingsController.java");

        assertTrue(fxml.contains("fx:id=\"customDictionaryRow\""));
        assertTrue(fxml.contains("description=\"Manage words accepted by Shale’s spelling tools.\""));
        assertTrue(settings.contains("bind(customDictionaryRow, this::onManageCustomDictionary)"));
        for (String obsolete : new String[] { "customDictionaryTable", "customDictionaryWordColumn",
                "removeCustomDictionaryWordButton", "customDictionaryStatusLabel" }) {
            assertFalse(fxml.contains(obsolete), "the compact Settings row must not retain " + obsolete);
            assertFalse(settings.contains(obsolete), "SettingsController must not retain " + obsolete);
        }
        assertFalse(settings.contains("loadCustomDictionary"),
                "the dictionary list must not load until the user selects Manage");
        assertTrue(settings.contains("new CustomDictionaryManagementLauncher"));
        assertFalse(method(settings, "onManageCustomDictionary").contains("isAdmin"),
                "a per-user dictionary must be available to authenticated non-admin users");
    }

    @Test void launcherReusesPresentationLifecycleWithoutDefinitionDtosOrDictionaryServiceLeakage() throws Exception {
        String launcher = read("controller/CustomDictionaryManagementLauncher.java");
        String pane = read("controller/CustomDictionaryManagementPane.java");
        assertTrue(launcher.contains("new DefinitionManagementSession()"));
        assertTrue(launcher.contains("session.show("));
        assertTrue(launcher.contains("new CustomDictionaryManagementPane"));
        assertTrue(pane.contains("UserDictionarySession"));
        assertTrue(pane.contains("Executor worker"));
        assertFalse(pane.contains("Executors.new"));
        assertFalse(pane.contains(".shutdown"));
        for (String unsupported : new String[] { "ColorPicker", "systemKey", "sortOrder", "setActive", "Edit" })
            assertFalse(pane.contains(unsupported), "word management must not expose definition behavior: " + unsupported);
    }

    @Test void panePreservesAsyncLifecycleAuthoritativeIdentityAndSuccessOnlyChangeTracking() throws Exception {
        String pane = read("controller/CustomDictionaryManagementPane.java");
        assertTrue(pane.contains("requireWorkerThread();"));
        assertTrue(pane.contains("String snapshot = addWord.getText()"),
                "JavaFX input must be snapshotted before worker execution");
        assertTrue(pane.contains("request != generation.get()") && pane.contains("disposed.get()"));
        assertTrue(pane.contains("List.copyOf(dictionary.list())"),
                "initial and post-mutation loads must be authoritative");
        assertTrue(pane.contains("row.id() == snapshotId") && pane.contains("snapshotNormalized"),
                "remove must retain the selected authoritative identity and normalized word");
        assertTrue(pane.indexOf("operation.run();") < pane.indexOf("changes.markCommitted();"),
                "failed persistence must not mark a committed change");
        assertTrue(pane.contains("if (clearInput) addWord.clear();"));
        assertTrue(pane.contains("if (clearInput) addWord.requestFocus();"),
                "failed adds should preserve the entered word");
        assertTrue(pane.contains("DictionaryWordNormalizer.normalize(snapshot)"));
        assertTrue(pane.contains("row.normalizedWord().equals(normalized)"),
                "duplicates must use the authoritative normalization identity");
    }

    @Test void existingQuickAddActionsRemainAndNoContextualManagerIsForcedIntoSuggestionRows() throws Exception {
        String compact = read("component/EnhancedTextArea.java");
        String expanded = read("component/RichTextExpandedEditor.java");
        assertTrue(compact.contains("Add “") && compact.contains("to dictionary"));
        assertTrue(compact.contains("UserDictionarySession.current().add(word)"));
        assertTrue(expanded.contains("new MenuItem(\"Add to dictionary\")"));
        assertFalse(compact.contains("CustomDictionaryManagementLauncher"));
        assertFalse(expanded.contains("CustomDictionaryManagementLauncher"));
    }

    private static String read(String relative) throws Exception { return Files.readString(MAIN.resolve(relative)); }

    private static String method(String source, String name) {
        int start = source.indexOf(name + "(");
        int brace = source.indexOf('{', start);
        int depth = 0;
        for (int i = brace; i < source.length(); i++) {
            if (source.charAt(i) == '{') depth++;
            else if (source.charAt(i) == '}' && --depth == 0) return source.substring(start, i + 1);
        }
        throw new AssertionError("Missing method " + name);
    }
}
