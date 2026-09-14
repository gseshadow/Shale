package com.shale.ui.controller;

import org.junit.jupiter.api.Test;

import com.shale.core.service.MaterialRequestServicePort;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

final class RequestMethodEditorContractTest {
    private static final String SETTINGS = read("src/main/java/com/shale/ui/controller/SettingsController.java");
    private static final String MATERIALS = read("src/main/java/com/shale/ui/controller/CaseMaterialsTabController.java");
    private static final String CSS = read("src/main/resources/css/app.css");

    @Test void requestMethodUsesSharedColorPickerWithoutEditableSortOrder() {
        String dialog = method("showRequestLookupDialog");
        assertTrue(containsCode(dialog, "new ColorPicker(dbColorToFx(existing==null?null:existing.color()))"));
        assertTrue(containsCode(dialog, "grid.add(new Label(\"Color\")"));
        assertTrue(containsCode(dialog, "if(kind!=RequestLookupKind.REQUEST_METHOD){grid.add(new Label(\"Sort Order\")"));
        assertTrue(containsCode(dialog, "grid.add(active"));
        assertTrue(containsCode(dialog, "new Label(\"System Key\")"));
    }

    @Test void settingsPreservesEditOrderAndLetsDaoAssignCreateOrder() {
        MaterialRequestServicePort.RequestMethodCommand create = SettingsController.requestMethodCreateCommand(
                41, 73, "Email", "#123456", true, "email");
        assertNull(create.id());
        assertEquals(41, create.shaleClientId());
        assertEquals(73, create.actorUserId());
        assertEquals("Email", create.name());
        assertEquals("#123456", create.color());
        assertTrue(create.active());
        assertEquals("email", create.systemKey());
        assertNull(create.sortOrder(), "Create must leave append ordering to the DAO.");
        assertNull(create.expectedRowVer());

        byte[] rowVer = { 1, 2, 3 };
        MaterialRequestServicePort.RequestMethodCommand edit = SettingsController.requestMethodEditCommand(
                19, 41, 73, "Secure Email", "#654321", false, "email", 27, rowVer);
        assertEquals(19, edit.id());
        assertEquals(41, edit.shaleClientId());
        assertEquals(73, edit.actorUserId());
        assertEquals("Secure Email", edit.name());
        assertEquals("#654321", edit.color());
        assertFalse(edit.active());
        assertEquals("email", edit.systemKey());
        assertEquals(27, edit.sortOrder(), "Edit must retain the selected row's authoritative order.");
        assertSame(rowVer, edit.expectedRowVer());
        assertTrue(containsCode(method("showRequestLookupDialog"),
                "Integer sortOrder = kind == RequestLookupKind.REQUEST_METHOD ? null"),
                "The hidden Request Method form state must not invent a create order.");
        assertTrue(containsCode(SETTINGS, "safe(d.color())"));
    }

    @Test void requestMethodColorIsUsedByMaterialRequestSelectorsWhileValueContractStaysTextBased() {
        assertTrue(MATERIALS.contains("newLookupSelector(RequestMethodDto::name,RequestMethodDto::color,item->null)"));
        assertTrue(MATERIALS.contains("effective(requestMethod.systemKey(),requestMethod.name())"));
    }

    @Test void everyRequestLookupRowUsesTheSharedColoredNamePill() {
        String card = method("buildRequestLookupCard");
        String pill = "LinkTypeIndicatorFactory.createLinkTypePill(row.name(), row.color(), LinkTypeIndicatorFactory.PillSize.COMPACT)";

        assertTrue(card.replace(" ", "").contains(("header.getChildren().addAll(dot, name, spacer, " + pill + ")").replace(" ", "")),
                "The common row header must place the shared name/color pill after its growing spacer.");
        assertFalse(containsCode(card, "kind != RequestLookupKind.REQUEST_METHOD"),
                "Request Methods must not be excluded from the pill shared with Request Statuses.");
        assertTrue(containsCode(card, "row.active() ? \"Active\" : \"Inactive\""));
        assertTrue(containsCode(card, "metadataPill(row.scopeLabel())"));
        assertTrue(containsCode(card, "metadataPill(\"System: \" + row.systemKey())"));
        assertTrue(containsCode(card, "metadataPill(row.color())"));
        assertTrue(containsCode(card, "row.global() ? \"Customize\" : \"Edit\""));
        assertTrue(containsCode(card, "row.active() ? \"Deactivate\" : \"Activate\""));
        assertTrue(containsCode(card, "row.custom() ? \"Remove\" : \"Reset to Default\""));
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
        int start = -1;
        int searchFrom = 0;
        while ((searchFrom = SETTINGS.indexOf("private ", searchFrom)) >= 0) {
            int brace = SETTINGS.indexOf('{', searchFrom);
            int candidate = SETTINGS.indexOf(" " + name + "(", searchFrom);
            if (candidate >= 0 && candidate < brace) { start = candidate; break; }
            searchFrom += "private ".length();
        }
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

    private static boolean containsCode(String source, String expected) {
        return source.replaceAll("\\s+", "").contains(expected.replaceAll("\\s+", ""));
    }
}
