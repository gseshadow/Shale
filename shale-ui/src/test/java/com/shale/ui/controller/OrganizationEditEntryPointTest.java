package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.parsers.DocumentBuilderFactory;

import com.shale.core.model.Organization;
import com.shale.ui.state.AppState;
import com.shale.ui.testutil.JavaFxTestSupport;

import javafx.scene.control.Button;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

final class OrganizationEditEntryPointTest {
    private static final Path FXML = Path.of("src/main/resources/fxml/organization.fxml");
    private static final Path CONTROLLER = Path.of("src/main/java/com/shale/ui/controller/OrganizationController.java");

    @Test
    void headerExposesAggregateEditBesideDestructiveDelete() throws Exception {
        var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(FXML.toFile());
        Element edit = button(document.getElementsByTagName("Button"), "editButton");
        Element delete = button(document.getElementsByTagName("Button"), "deleteOrganizationButton");

        assertNotNull(edit, "Organization View must declare its edit entry point");
        assertTrue("Edit Organization".equals(edit.getAttribute("text")), "edit action must have an explicit accessible label");
        assertNotNull(delete, "the existing destructive action must remain available");
        assertTrue(edit.getParentNode() == delete.getParentNode(), "edit and delete must share the existing header action row");
        assertTrue(precedes(edit, delete), "the ordinary edit action must precede the destructive delete action");

        String source = Files.readString(CONTROLLER);
        assertTrue(source.contains("ControlStyles.apply(editButton, ControlStyles.Purpose.SECONDARY)"),
                "edit must use the shared secondary semantic control");
        assertTrue(source.contains("ControlStyles.apply(deleteOrganizationButton, ControlStyles.Purpose.DANGER)"),
                "delete must retain destructive semantic styling");
        assertFalse(edit.hasAttribute("style"), "semantic button styling must not be duplicated inline");
    }

    @Test
    void editVisibilityUsesNormalOrganizationEditorAuthorizationRatherThanSettingsAdmin() {
        JavaFxTestSupport.runAndWait(() -> {
            OrganizationController controller = new OrganizationController();
            Button edit = new Button();
            Button delete = new Button();
            set(controller, "editButton", edit);
            set(controller, "deleteOrganizationButton", delete);
            set(controller, "currentOrganization", Organization.builder().id(12).shaleClientId(7).name("Example").build());

            AppState ordinaryEditor = new AppState();
            ordinaryEditor.setUserId(42);
            ordinaryEditor.setShaleClientId(7);
            ordinaryEditor.setAdmin(false);
            set(controller, "appState", ordinaryEditor);
            invoke(controller, "refreshAdminActions");
            assertTrue(edit.isVisible() && edit.isManaged(), "an authenticated non-admin Organization editor must see Edit");
            assertFalse(delete.isVisible() || delete.isManaged(), "non-admin users must not gain Delete");

            ordinaryEditor.setUserId(null);
            invoke(controller, "refreshAdminActions");
            assertFalse(edit.isVisible() || edit.isManaged(), "a user without an actor identity must not gain edit access");
        });
    }

    @Test
    void buttonIsWiredToTheExistingAggregateEditorLifecycle() throws Exception {
        String source = Files.readString(CONTROLLER);
        String initialize = method(source, "private void initialize()");
        String load = method(source, "private void loadOrganization()");
        String edit = method(source, "private void onEdit()");
        String save = method(source, "private void onSave()");
        String cancel = method(source, "private void onCancel()");

        assertTrue(initialize.contains("editButton.setOnAction(e -> onEdit())"), "FXML button must invoke the aggregate edit handler");
        assertTrue(edit.contains("!canEditOrganization()"), "direct UI handler invocation must retain its authorization guard");
        assertTrue(edit.contains("writeEditorsFromOrganization(currentOrganization)") && edit.contains("setEditMode(true)"),
                "the entry point must seed the existing aggregate editor rather than open a second implementation");
        assertTrue(load.contains("listEffectiveOrganizationTypes(tenantId)")
                        && load.contains("getOrganizationTypeProfile(organizationId,tenantId)")
                        && load.contains("OrganizationTypeAssignmentStage.forEdit(effectiveDefinitions,loadedProfile)"),
                "authoritative definitions and the complete assignment profile must seed the edit stage");
        assertTrue(save.contains("organizationService.updateOrganizationAggregate(command)"),
                "Save must delegate to the existing aggregate update operation");
        assertTrue(count(save, "updateOrganizationAggregate(") == 1, "one click must issue exactly one aggregate update");
        assertTrue(save.contains("Platform.runLater(this::loadOrganization)"), "successful Save must reload authoritative state");
        assertTrue(save.contains("publishOrganizationUpdated(currentOrganization.getId())"),
                "the live update remains after the successful aggregate call");
        assertTrue(cancel.contains("assignmentStage.discard()") && !cancel.contains("organizationService."),
                "Cancel must discard staged changes without mutation");
        assertTrue(count(source, "organizationService.updateOrganizationAggregate(") == 1,
                "only Save may mutate the aggregate, so closing the view cannot persist staged changes");
        assertFalse(initialize.contains("initializeInlineEditButtons()"),
                "the retired field-by-field mutation controls must remain unreachable");
    }

    @Test
    void viewDoesNotIntroduceDeferredPhaseTwoCPresentation() throws Exception {
        String fxml = Files.readString(FXML);
        assertTrue(count(fxml, "text=\"Organization Type\"") == 1,
                "the read-only compatibility Organization Type field remains the only type presentation");
        assertFalse(fxml.contains("classification-chip") || fxml.contains("OrganizationCard"),
                "this entry-point fix must not add Phase 2C chips, cards, header, or search presentation");
    }

    private static Element button(NodeList buttons, String id) {
        for (int i = 0; i < buttons.getLength(); i++) {
            Element element = (Element) buttons.item(i);
            if (id.equals(element.getAttribute("fx:id"))) return element;
        }
        return null;
    }

    private static boolean precedes(Element first, Element second) {
        for (var node = first.getNextSibling(); node != null; node = node.getNextSibling()) if (node == second) return true;
        return false;
    }

    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, signature + " must exist");
        int open = source.indexOf('{', start);
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            if (source.charAt(i) == '{') depth++;
            if (source.charAt(i) == '}' && --depth == 0) return source.substring(start, i + 1);
        }
        throw new AssertionError("Unclosed method " + signature);
    }

    private static int count(String text, String token) {
        int result = 0;
        for (int at = 0; (at = text.indexOf(token, at)) >= 0; at += token.length()) result++;
        return result;
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = OrganizationController.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void invoke(Object target, String name) throws Exception {
        Method method = OrganizationController.class.getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(target);
    }
}
