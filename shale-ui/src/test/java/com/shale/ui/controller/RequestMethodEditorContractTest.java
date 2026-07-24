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

    @Test void everyRequestLookupRowUsesTheSharedColoredNamePill() {
        String card = method("buildRequestLookupCard");
        String pill = "LinkTypeIndicatorFactory.createLinkTypePill(row.name(), row.color(), LinkTypeIndicatorFactory.PillSize.COMPACT)";

        assertTrue(card.contains("header.getChildren().addAll(dot, name, spacer, " + pill + ")"),
                "The common row header must place the shared name/color pill after its growing spacer.");
        assertFalse(card.contains("kind != RequestLookupKind.REQUEST_METHOD"),
                "Request Methods must not be excluded from the pill shared with Request Statuses.");
        assertTrue(card.contains("row.active() ? \"Active\" : \"Inactive\""));
        assertTrue(card.contains("metadataPill(row.scopeLabel())"));
        assertTrue(card.contains("metadataPill(\"System: \" + row.systemKey())"));
        assertTrue(card.contains("metadataPill(row.color())"));
        assertTrue(card.contains("row.global() ? \"Customize\" : \"Edit\""));
        assertTrue(card.contains("row.active() ? \"Deactivate\" : \"Activate\""));
        assertTrue(card.contains("row.custom() ? \"Remove\" : \"Reset to Default\""));
    }

    @Test void lookupNamePillRetainsSharedReadableTextAndInvalidColorFallback() {
        String indicator = read("src/main/java/com/shale/ui/component/factory/LinkTypeIndicatorFactory.java");
        String colorUtil = read("src/main/java/com/shale/ui/util/ColorUtil.java");

        assertTrue(indicator.contains("ColorUtil.toCssBackgroundColor(storedColor)"));
        assertTrue(indicator.contains("ColorUtil.readableTextColor(storedColor)"));
        assertTrue(colorUtil.contains("normalized == null ? \"rgba(0,0,0,0.06)\""));
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
