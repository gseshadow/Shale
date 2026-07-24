package com.shale.ui.controller;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

final class RequestMethodEditorContractTest {
    private static final String SETTINGS = read("src/main/java/com/shale/ui/controller/SettingsController.java");
    private static final String MATERIALS = read("src/main/java/com/shale/ui/controller/CaseMaterialsTabController.java");
    private static final String CSS = read("src/main/resources/css/app.css");

    @Test void requestMethodUsesSharedColorPickerWithoutEditableSortOrder() {
        String dialog = method("showRequestLookupDialog");
        assertTrue(dialog.contains("new ColorPicker(dbColorToFx(existing==null?null:existing.color()))"));
        assertTrue(dialog.contains("grid.add(new Label(\"Color\")"));
        assertTrue(dialog.contains("if(kind!=RequestLookupKind.REQUEST_METHOD){grid.add(new Label(\"Sort Order\")"));
        assertTrue(dialog.contains("grid.add(active"));
        assertTrue(dialog.contains("new Label(\"System Key\")"));
    }

    @Test void settingsPreservesEditOrderAndLetsDaoAssignCreateOrder() {
        assertTrue(method("addRequestLookup").contains("input.systemKey(),null,null"));
        assertTrue(method("editRequestLookup").contains("row.systemKey(),row.sortOrder(),row.rowVer()"));
        assertTrue(SETTINGS.contains("safe(d.color())"));
    }

    @Test void requestMethodColorIsUsedByMaterialRequestSelectorsWhileValueContractStaysTextBased() {
        assertTrue(MATERIALS.contains("newLookupSelector(RequestMethodDto::name,RequestMethodDto::color)"));
        assertTrue(MATERIALS.contains("effective(requestMethod.systemKey(),requestMethod.name())"));
    }

    @Test void sharedDialogButtonBarClipsItsBackgroundToBothBottomCorners() {
        assertTrue(CSS.contains(".dialog-pane.secondary-window-shell > .button-bar"));
        assertTrue(CSS.contains("-fx-background-radius: 0 0 16 16;"));
        assertTrue(CSS.contains("-fx-border-radius: 0 0 16 16;"));
    }

    private static String method(String name) {
        int start = SETTINGS.indexOf(" " + name + "(");
        assertTrue(start >= 0, name);
        int brace = SETTINGS.indexOf('{', start);
        int depth = 0;
        for (int i = brace; i < SETTINGS.length(); i++) {
            if (SETTINGS.charAt(i) == '{') depth++;
            else if (SETTINGS.charAt(i) == '}' && --depth == 0) return SETTINGS.substring(start, i + 1);
        }
        throw new AssertionError(name);
    }

    private static String read(String path) {
        try { return Files.readString(Path.of(path)); }
        catch (Exception e) { throw new AssertionError(e); }
    }
}
