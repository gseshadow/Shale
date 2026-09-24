package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.shale.core.model.Organization;
import com.shale.core.service.OrganizationServicePort.AssignedOrganizationType;
import com.shale.core.service.OrganizationServicePort.OrganizationTypeDefinition;
import com.shale.core.service.OrganizationServicePort.OrganizationTypeOrigin;
import com.shale.core.service.OrganizationServicePort.OrganizationTypeProfile;
import com.shale.ui.component.ClassificationChipGroup;
import com.shale.ui.testutil.JavaFxTestSupport;
import com.shale.ui.theme.Theme;
import com.shale.ui.theme.ThemeManager;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Paint;

final class OrganizationProfilePhase3DPresentationTest {
    private static final Path FXML = Path.of("src/main/resources/fxml/organization.fxml");

    @Test
    void productionFxmlUsesSharedContractsAndOnePageScrollOwner() throws Exception {
        String view = Files.readString(FXML);
        for (String contract : List.of("shale-page-title", "shale-metadata-muted", "shale-section-card",
                "shale-section-title", "shale-property-row", "shale-property-row-label",
                "shale-property-row-value", "shale-error-message", "shale-empty-message")) {
            assertTrue(view.contains(contract), "Organization profile must use shared contract " + contract);
        }
        assertEquals(1, count(view, "<ScrollPane"), "the profile must have one page-level scroll owner");
        assertTrue(view.contains("<FlowPane fx:id=\"profileColumns\""),
                "profile columns must wrap instead of forcing horizontal page scrolling");
        assertFalse(view.contains("textFill=\"#") || view.contains("style=\"-fx-text-fill:"),
                "theme paint must not be embedded in Organization FXML");
    }

    @Test
    void authoritativePrimaryAloneDrivesComputedHeaderWashAndAllAssignmentsRemainVisible() {
        requireToolkit();
        JavaFxTestSupport.runAndWait(() -> {
            LoadedProfile profile = loadProfile();
            OrganizationTypeDefinition primary = definition(10, "Hospital", "#2266AA", true, false);
            OrganizationTypeDefinition secondary = definition(11, "Records", "#CC7722", true, false);
            OrganizationTypeDefinition historical = definition(12, "Former vendor", "#8844CC", false, false);

            apply(profile.controller(), organization(false, "A very long organization name that must remain wrapped"),
                    typeProfile(List.of(assigned(2, secondary, false, 0), assigned(1, primary, true, 4),
                            assigned(3, historical, false, 2))));
            Paint first = background(profile.header());
            ClassificationChipGroup chips = (ClassificationChipGroup) profile.root()
                    .lookup(".contact-classification-chip-group");
            assertEquals(3, chips.getChildren().size(), "active and historical assignments must remain visible");
            assertTrue(((Label) chips.getChildren().getFirst()).getText().contains("Primary"));
            assertTrue(((Label) chips.getChildren().get(2)).getText().contains("Inactive"),
                    "historical definitions need a non-color state cue");

            OrganizationTypeDefinition changedSecondary = definition(11, "Records", "#33AA55", true, false);
            apply(profile.controller(), organization(false, "Example"),
                    typeProfile(List.of(assigned(1, primary, true, 4), assigned(2, changedSecondary, false, 0))));
            assertEquals(first, background(profile.header()),
                    "a secondary type color must not replace the authoritative primary wash");

            apply(profile.controller(), organization(false, "Example"),
                    typeProfile(List.of(assigned(2, secondary, false, 0))));
            Paint noPrimary = background(profile.header());
            assertNotEquals(first, noPrimary, "absence of a primary assignment must use the theme fallback");
        });
    }

    @Test
    void invalidPrimaryUsesFallbackAndRemovedAndSparseStatesHaveTextCues() {
        requireToolkit();
        JavaFxTestSupport.runAndWait(() -> {
            LoadedProfile profile = loadProfile();
            apply(profile.controller(), organization(false, "Active"), typeProfile(List.of()));
            Paint missing = background(profile.header());
            apply(profile.controller(), organization(true, "Removed"),
                    typeProfile(List.of(assigned(1, definition(10, "Invalid", "not-a-color", true, false), true, 0))));
            assertEquals(missing, background(profile.header()), "invalid primary paint must use the canonical fallback");
            Label removed = (Label) profile.root().lookup("#removedStateLabel");
            assertTrue(removed.isVisible() && removed.getText().toLowerCase().contains("removed"));
            assertTrue(profile.header().getStyleClass().contains("organization-profile-header-removed"));
            VBox notes = (VBox) profile.root().lookup("#notesSection");
            assertFalse(notes.isVisible() || notes.isManaged(), "absent optional notes must be suppressed");
            ScrollPane scroll = (ScrollPane) profile.root().lookup("#profileScrollPane");
            assertEquals(ScrollPane.ScrollBarPolicy.NEVER, scroll.getHbarPolicy());
        });
    }

    private static LoadedProfile loadProfile() {
        try {
            FXMLLoader loader = new FXMLLoader(OrganizationProfilePhase3DPresentationTest.class.getResource("/fxml/organization.fxml"));
            Parent root = loader.load();
            Scene scene = new Scene(root, 1180, 760);
            ThemeManager themes = new ThemeManager();
            themes.register(scene);
            themes.setActiveTheme(Theme.DARK);
            root.applyCss();
            root.layout();
            return new LoadedProfile(root, loader.getController(), (VBox) root.lookup("#organizationHeaderSurface"));
        } catch (Exception failure) {
            throw new AssertionError("production Organization profile FXML must load through ThemeManager", failure);
        }
    }

    private static void apply(OrganizationController controller, Organization organization, OrganizationTypeProfile types) {
        try {
            set(controller, "currentOrganization", organization);
            set(controller, "currentTypeProfile", types);
            invoke(controller, "renderFromCurrent");
            Parent root = (Parent) ((VBox) get(controller, "organizationHeaderSurface")).getScene().getRoot();
            root.applyCss();
            root.layout();
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }

    private static Organization organization(boolean deleted, String name) {
        return Organization.builder().id(7).shaleClientId(3).name(name).notes(" ").deleted(deleted).build();
    }

    private static OrganizationTypeProfile typeProfile(List<AssignedOrganizationType> assignments) {
        return new OrganizationTypeProfile(7, 3, null, true, assignments);
    }

    private static AssignedOrganizationType assigned(long id, OrganizationTypeDefinition definition,
            boolean primary, int order) {
        return new AssignedOrganizationType(id, definition.organizationTypeId(), primary, order, definition, new byte[] {1});
    }

    private static OrganizationTypeDefinition definition(int id, String name, String color, boolean active,
            boolean deleted) {
        return new OrganizationTypeDefinition(id, 3, null, name, null, color, id, active, deleted,
                OrganizationTypeOrigin.TENANT, new byte[] {1});
    }

    private static Paint background(VBox header) {
        return header.getBackground().getFills().getLast().getFill();
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = OrganizationController.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object get(Object target, String name) throws Exception {
        Field field = OrganizationController.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void invoke(Object target, String name) throws Exception {
        Method method = OrganizationController.class.getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(target);
    }

    private static int count(String value, String token) {
        int result = 0;
        for (int at = 0; (at = value.indexOf(token, at)) >= 0; at += token.length()) result++;
        return result;
    }

    private static boolean hasDisplay() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return System.getenv("DISPLAY") != null || System.getenv("WAYLAND_DISPLAY") != null
                || os.contains("win") || os.contains("mac");
    }

    private static void requireToolkit() {
        assumeTrue(hasDisplay(), "Computed Organization profile presentation requires a graphical display.");
        JavaFxTestSupport.ensureToolkitStarted();
    }

    private record LoadedProfile(Parent root, OrganizationController controller, VBox header) { }
}
