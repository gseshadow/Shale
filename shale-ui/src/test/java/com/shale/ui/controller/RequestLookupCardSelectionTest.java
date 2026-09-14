package com.shale.ui.controller;

import com.shale.core.dto.RequestStatusDto;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class RequestLookupCardSelectionTest {
    private static final String SOURCE = read("src/main/java/com/shale/ui/controller/SettingsController.java");
    private static final String CSS = read("src/main/resources/css/foundation/cards.css");

    @Test void cardMouseAndKeyboardPathsOnlySelectAndRetainCards() {
        String card = method("buildRequestLookupCard");
        String select = method("selectRequestLookup");

        assertTrue(containsCode(card, "MouseButton.PRIMARY") && containsCode(card, "!isActionControl(event.getTarget())"));
        assertTrue(containsCode(card, "KeyCode.SPACE") && containsCode(card, "KeyCode.ENTER"));
        assertTrue(containsCode(card, "card.setFocusTraversable(true)"));
        assertTrue(containsCode(select, "updateRequestLookupSelectionStyles(kind)"));
        assertFalse(containsCode(select, "loadRequestLookupsAsync"), "selection must not replace cards with loading state");
        assertFalse(containsCode(select, "remove(") || containsCode(select, "clear(") || containsCode(select, "setAll("));
        assertFalse(containsCode(select, "editRequestLookup") || containsCode(select, "toggleRequestLookup") || containsCode(select, "resetRequestLookup"));
    }

    @Test void repeatedAndCrossCardSelectionOnlyUpdatesPseudoClasses() {
        String styles = method("updateRequestLookupSelectionStyles");

        assertTrue(containsCode(styles, "for (Node node : container.getChildren())"));
        assertTrue(containsCode(styles, "node.pseudoClassStateChanged(SELECTED_CARD"));
        assertFalse(containsCode(styles, "getChildren().remove") || containsCode(styles, "getChildren().clear") || containsCode(styles, "getChildren().setAll"));
        assertTrue(CSS.contains(".shale-entity-card-selectable:selected"));
    }

    @Test void actionButtonsAreIsolatedFromBackgroundSelectionAndRemainExplicitMutationPaths() {
        String card = method("buildRequestLookupCard");
        String actionGuard = method("isActionControl");

        assertTrue(actionGuard.contains("node instanceof ButtonBase"));
        assertTrue(containsCode(card, "edit.setOnAction") && containsCode(card, "editRequestLookup(kind)"));
        assertTrue(containsCode(card, "toggle.setOnAction") && containsCode(card, "toggleRequestLookup(kind)"));
        assertTrue(containsCode(card, "reset.setOnAction") && containsCode(card, "resetRequestLookup(kind)"));
        assertTrue(card.split("e.consume\\(\\);", -1).length >= 4, "each explicit action must consume its event");
        assertTrue(method("resetRequestLookup").contains("AppDialogs.showConfirmation"));
    }

    @Test void sharedFixCoversAllThreeConfigurableLookupManagers() {
        String styles = method("updateRequestLookupSelectionStyles");
        assertTrue(containsCode(styles, "case MATERIAL_TYPE -> materialTypeCardsContainer"));
        assertTrue(containsCode(styles, "case REQUEST_METHOD -> requestMethodCardsContainer"));
        assertTrue(containsCode(styles, "case REQUEST_STATUS -> requestStatusCardsContainer"));
    }

    @Test void realMutationsStillRefreshAndLoadsRejectStaleResultsWithoutClearingCurrentCards() {
        assertTrue(method("mutateRequestLookup").contains("loadRequestLookupsAsync()"));
        String load = method("loadRequestLookupsAsync");
        assertTrue(containsCode(load, "final int generation = ++requestLookupLoadGeneration"));
        assertTrue(containsCode(load, "if (generation != requestLookupLoadGeneration) return;"));
        assertTrue(containsCode(load, "showRequestLookupLoadingIfEmpty"));
        assertFalse(containsCode(load, "requestStatusCardsContainer.getChildren().setAll(loadingLabel(\"Loading request statuses"));
    }

    @Test void effectiveStatusFilteringRetainsGlobalOverrideAndCustomRowsWithoutMutatingDtos() {
        byte[] globalVersion = {1};
        byte[] overrideVersion = {2};
        byte[] customVersion = {3};
        RequestStatusDto global = new RequestStatusDto(1, null, "open", "Open", "#112233", 10, true, false, globalVersion);
        RequestStatusDto override = new RequestStatusDto(2, 7, "open", "Tenant Open", "#223344", 10, false, false, overrideVersion);
        RequestStatusDto custom = new RequestStatusDto(3, 7, null, "Waiting", "#334455", 20, true, false, customVersion);
        RequestStatusDto deletedCustom = new RequestStatusDto(4, 7, null, "Removed", "#445566", 30, true, true, new byte[]{4});

        List<RequestStatusDto> rows = SettingsController.buildRequestStatusRows(
                List.of(global, override, custom, deletedCustom), 7);

        assertEquals(List.of(override, custom), rows);
        assertFalse(rows.get(0).active(), "inactive tenant overrides remain administrable");
        assertSame(overrideVersion, override.rowVer());
        assertSame(customVersion, custom.rowVer());
        assertTrue(global.active());
        assertFalse(global.deleted());
    }

    @Test void requestStatusColorsRemainCanonicalRgb() {
        assertEquals("#28A745", SettingsController.fxColorToDb(javafx.scene.paint.Color.web("#28A745")));
    }

    private static String method(String name) {
        int candidate = -1;
        int searchFrom = 0;
        while ((searchFrom = SOURCE.indexOf("private ", searchFrom)) >= 0) {
            int brace = SOURCE.indexOf('{', searchFrom);
            int possible = SOURCE.indexOf(" " + name + "(", searchFrom);
            if (possible >= 0 && possible < brace) { candidate = possible; break; }
            searchFrom += "private ".length();
        }
        assertTrue(candidate >= 0, name);
        int brace = SOURCE.indexOf('{', candidate);
        int depth = 0;
        for (int i = brace; i < SOURCE.length(); i++) {
            if (SOURCE.charAt(i) == '{') depth++;
            else if (SOURCE.charAt(i) == '}' && --depth == 0) return SOURCE.substring(candidate, i + 1);
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
