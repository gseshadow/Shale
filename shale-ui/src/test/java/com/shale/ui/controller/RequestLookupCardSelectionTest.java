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

        assertTrue(card.contains("MouseButton.PRIMARY") && card.contains("!isActionControl(event.getTarget())"));
        assertTrue(card.contains("KeyCode.SPACE") && card.contains("KeyCode.ENTER"));
        assertTrue(card.contains("card.setFocusTraversable(true)"));
        assertTrue(select.contains("updateRequestLookupSelectionStyles(kind)"));
        assertFalse(select.contains("loadRequestLookupsAsync"), "selection must not replace cards with loading state");
        assertFalse(select.contains("remove(") || select.contains("clear(") || select.contains("setAll("));
        assertFalse(select.contains("editRequestLookup") || select.contains("toggleRequestLookup") || select.contains("resetRequestLookup"));
    }

    @Test void repeatedAndCrossCardSelectionOnlyUpdatesPseudoClasses() {
        String styles = method("updateRequestLookupSelectionStyles");

        assertTrue(styles.contains("for (Node node : container.getChildren())"));
        assertTrue(styles.contains("node.pseudoClassStateChanged(SELECTED_CARD"));
        assertFalse(styles.contains("getChildren().remove") || styles.contains("getChildren().clear") || styles.contains("getChildren().setAll"));
        assertTrue(CSS.contains(".shale-entity-card-selectable:selected"));
    }

    @Test void actionButtonsAreIsolatedFromBackgroundSelectionAndRemainExplicitMutationPaths() {
        String card = method("buildRequestLookupCard");
        String actionGuard = method("isActionControl");

        assertTrue(actionGuard.contains("node instanceof ButtonBase"));
        assertTrue(card.contains("edit.setOnAction") && card.contains("editRequestLookup(kind)"));
        assertTrue(card.contains("toggle.setOnAction") && card.contains("toggleRequestLookup(kind)"));
        assertTrue(card.contains("reset.setOnAction") && card.contains("resetRequestLookup(kind)"));
        assertTrue(card.split("e.consume\\(\\);", -1).length >= 4, "each explicit action must consume its event");
        assertTrue(method("resetRequestLookup").contains("AppDialogs.showConfirmation"));
    }

    @Test void sharedFixCoversAllThreeConfigurableLookupManagers() {
        String styles = method("updateRequestLookupSelectionStyles");
        assertTrue(styles.contains("case MATERIAL_TYPE -> materialTypeCardsContainer"));
        assertTrue(styles.contains("case REQUEST_METHOD -> requestMethodCardsContainer"));
        assertTrue(styles.contains("case REQUEST_STATUS -> requestStatusCardsContainer"));
    }

    @Test void realMutationsStillRefreshAndLoadsRejectStaleResultsWithoutClearingCurrentCards() {
        assertTrue(method("mutateRequestLookup").contains("loadRequestLookupsAsync()"));
        String load = method("loadRequestLookupsAsync");
        assertTrue(load.contains("final int generation = ++requestLookupLoadGeneration"));
        assertTrue(load.contains("if (generation != requestLookupLoadGeneration) return;"));
        assertTrue(load.contains("showRequestLookupLoadingIfEmpty"));
        assertFalse(load.contains("requestStatusCardsContainer.getChildren().setAll(loadingLabel(\"Loading request statuses"));
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
}
