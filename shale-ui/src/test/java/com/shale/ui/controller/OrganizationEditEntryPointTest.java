package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

final class OrganizationEditEntryPointTest {
    private static final Path FXML = Path.of("src/main/resources/fxml/organization.fxml");
    private static final Path CONTROLLER = Path.of("src/main/java/com/shale/ui/controller/OrganizationController.java");
    private static final Path EDITOR = Path.of("src/main/java/com/shale/ui/controller/EditOrganizationDialog.java");
    private static final Path SHARED_EDITOR = Path.of("src/main/java/com/shale/ui/controller/OrganizationAggregateEditor.java");

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
    void buttonOpensDedicatedAggregateEditorAndViewHasNoInlineControls() throws Exception {
        String source = Files.readString(CONTROLLER);
        String fxml = Files.readString(FXML);
        String initialize = method(source, "private void initialize()");
        String edit = method(source, "private void onEdit()");

        assertTrue(initialize.contains("editButton.setOnAction(e -> onEdit())"), "FXML button must invoke the aggregate edit handler");
        assertTrue(edit.contains("!canEditOrganization()"), "direct UI handler invocation must retain its authorization guard");
        assertTrue(edit.contains("new EditOrganizationDialog") && edit.contains("editDialogOpen"),
                "Edit must open one dedicated modal and guard duplicate opening");
        assertFalse(fxml.contains("Editor\"") || fxml.contains("saveButton") || fxml.contains("cancelButton"),
                "the permanently read-only view must not retain hidden editors or inline Save/Cancel");
        assertFalse(source.contains("setEditMode") || source.contains("writeEditorsFromOrganization"),
                "the controller must not retain inline-edit state or field swapping");
        String applySuccess = method(source, "private void applySuccessfulAggregateSave(OrganizationAggregateResult result)");
        assertTrue(count(applySuccess, "loadOrganization()") == 1, "a successful Save must request one authoritative reload");
        assertFalse(source.contains("saveSingleOrganizationField") || source.contains("saveOrganizationSnapshot"),
                "retired field-by-field Organization mutation must be removed");
    }

    @Test
    void dedicatedEditorOwnsAuthoritativeAsyncModalLifecycle() throws Exception {
        String editor = Files.readString(EDITOR);
        assertTrue(editor.contains("Edit Organization") && editor.contains("Modality.WINDOW_MODAL") && editor.contains("dialog.initOwner(owner)"),
                "editor must use the Edit Contact-style owned window-modal shell");
        assertTrue(editor.contains("ScrollPane") && editor.contains("sizeModalStage") && editor.contains("dialog.setResizable(true)"),
                "editor content must remain bounded, scrollable, resizable, and screen-aware");
        String reload = method(editor, "private void reload()");
        assertTrue(reload.indexOf("executor.execute") < reload.indexOf("dao.findById(organizationId)")
                        && reload.contains("listEffectiveOrganizationTypes(tenant)")
                        && reload.contains("getOrganizationTypeProfile(organizationId,tenant)")
                        && reload.contains("findStructuredContactProfile(tenant,organizationId)")
                        && reload.contains("findOrganizationRowVer(organizationId,tenant)"),
                "the shared reload boundary must fetch parent, types, assignments, contacts, and RowVers on its worker executor");
        assertTrue(reload.contains("Platform.runLater(()->applyLoad(request,new LoadResult(")
                        && reload.contains("long request=++generation") && reload.contains("baseline=null")
                        && reload.contains("scroll.setContent(null)"),
                "each opening/reload must have a fresh generation and apply its authoritative aggregate on the FX thread");
        String applyLoad = method(editor, "private void applyLoad(long request,LoadResult loaded)");
        assertTrue(applyLoad.contains("request!=generation") && applyLoad.contains("!dialog.isShowing()")
                        && applyLoad.contains("OrganizationAggregateEditor.forEdit"),
                "stale or closed callbacks must not replace the currently displayed shared editor");
        assertTrue(count(editor, "service.updateOrganizationAggregate(command)") == 1,
                "Save must delegate exactly once to the atomic aggregate mutation");
        String shared = Files.readString(SHARED_EDITOR);
        assertTrue(shared.contains("new OrganizationServicePort.StructuredContactMutation(")
                        && count(shared, "OwnedContactCollection.exact(") == 4,
                "Save must own all four structured collections in one structured aggregate command");
        assertFalse(editor.contains("new OrganizationServicePort.LegacyContactMutation")
                        || editor.contains("value(phone)") || editor.contains("value(fax)")
                        || editor.contains("value(email)") || editor.contains("value(website)"),
                "Edit Organization must not submit legacy scalar contact controls or a legacy mutation");
        assertTrue(shared.contains("assignmentStage().isDirty()") && editor.contains("confirmDiscard()"),
                "dirty detection and close confirmation must cover shared assignment staging");
        assertTrue(shared.contains("phones.isDirty()") && shared.contains("emails.isDirty()")
                        && shared.contains("addresses.isDirty()") && shared.contains("websites.isDirty()"),
                "dirty detection must include every structured staged collection");
        assertTrue(editor.contains("Organization changed elsewhere. Authoritative values are being reloaded.") && editor.contains("reload();"),
                "a concurrency conflict must reload authoritative state before another save");
		assertFalse(editor.contains("phone.getText()") || editor.contains("email.getText()") || editor.contains("notes.getText(), failure"),
				"failure logging must not include staged contact values or notes");
		assertTrue(editor.contains("failure instanceof IllegalArgumentException"),
                "safe validation failures must remain user-facing without closing the editor");
    }

    @Test
    void unexpectedPersistenceFailureLogsOneSafeWarningWithAttachedThrowable() {
        Logger logger = (Logger) LoggerFactory.getLogger(EditOrganizationDialog.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            RuntimeException failure = new IllegalStateException("persistence unavailable");

            EditOrganizationDialog.logPersistenceFailure(7,42,19,failure);

            assertEquals(1,appender.list.size(),"one failed aggregate mutation must emit exactly one warning");
            ILoggingEvent event=appender.list.getFirst();
            assertEquals(Level.WARN,event.getLevel());
            String message=event.getFormattedMessage();
            assertTrue(message.contains("operation=updateOrganizationAggregate"));
            assertTrue(message.contains("tenantId=7")&&message.contains("actorId=42")&&message.contains("organizationId=19"));
            assertTrue(message.contains("exceptionClass=java.lang.IllegalStateException"));
            assertNotNull(event.getThrowableProxy(),"the logging API must retain the failure stack trace");
            assertEquals(failure.getClass().getName(),event.getThrowableProxy().getClassName());
            assertEquals(failure.getMessage(),event.getThrowableProxy().getMessage(),
                    "the attached stack trace must belong to the injected persistence failure");
            for(String sensitive:new String[]{"phone","email","notes","rowver","sql","password","credential"})
                assertFalse(message.toLowerCase(java.util.Locale.ROOT).contains(sensitive),"structured values and persistence secrets must not enter log metadata: "+sensitive);
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void structuredEditorUsesContactCardSectionsAndRemovesLegacyScalarControls() throws Exception {
        String editor = Files.readString(SHARED_EDITOR);
        assertTrue(editor.contains("section(\"Organization Details\"")
                        && editor.contains("section(\"Contact Information\"")
                        && editor.contains("subsection(\"Phone Numbers\"")
                        && editor.contains("subsection(\"Email Addresses\"")
                        && editor.contains("subsection(\"Addresses\"")
                        && editor.contains("subsection(\"Websites\"")
                        && editor.contains("section(\"Organization Types\"")
                        && editor.contains("section(\"Notes\""),
                "the modal must expose the Contact-style structured section hierarchy");
        assertTrue(editor.contains("contact-point-card") && editor.contains("Show Removed")
                        && editor.contains("Move Up") && editor.contains("Make Primary"),
                "repeated methods must reuse Contact card, history, ordering, and primary language");
        assertFalse(editor.contains("TextField name = field(), phone")
                        || editor.contains("add(grid, row++, \"Phone\"")
                        || editor.contains("add(grid, row++, \"Address 1\""),
                "legacy scalar contact inputs must not remain hidden in the dialog");
        String lifecycle = Files.readString(EDITOR);
        assertTrue(lifecycle.contains("compatibilityConsistent()") && lifecycle.contains("saving is disabled"),
                "an inconsistent opening compatibility profile must prevent unsafe saving");
    }

    @Test
    void localPostCommitEventIsSuppressedButARealConcurrentEditorsEventIsNot() throws Exception {
        OrganizationController controller = new OrganizationController();
        AppState state = new AppState();state.setUserId(42);state.setShaleClientId(7);
        set(controller,"appState",state);set(controller,"organizationId",12);set(controller,"awaitingAuthoritativeReloadAfterLocalSave",true);
        var local = new com.shale.ui.services.UiRuntimeBridge.EntityUpdatedEvent(1,"local","Organization",12,7,42,"",null,java.util.Map.of(),"");
        var remote = new com.shale.ui.services.UiRuntimeBridge.EntityUpdatedEvent(1,"remote","Organization",12,7,99,"",null,java.util.Map.of(),"");
        assertTrue((boolean)invokeResult(controller,"shouldIgnoreLiveEvent",local),"the successful local publication must not create a concurrency banner");
        assertFalse((boolean)invokeResult(controller,"shouldIgnoreLiveEvent",remote),"a different actor's concurrent update must remain observable");
    }

    @Test
    void viewUsesCompletedStructuredPresentationWithoutLegacyTypeField() throws Exception {
        String fxml = Files.readString(FXML);
        String controller = Files.readString(CONTROLLER);
        assertTrue(fxml.contains("fx:id=\"organizationTypeChips\"") && fxml.contains("fx:id=\"phoneCards\"")
                        && fxml.contains("fx:id=\"emailCards\"") && fxml.contains("fx:id=\"addressCards\"")
                        && fxml.contains("fx:id=\"websiteCards\""),
                "the profile must host all completed structured type and contact-method presentations");
        assertFalse(fxml.contains("text=\"Organization Type\"") || fxml.contains("fx:id=\"organizationTypeValue\""),
                "the legacy compatibility-only single Organization Type field must not be rendered beside chips");
        assertTrue(controller.contains("Comparator.comparing(OrganizationServicePort.AssignedOrganizationType::primary).reversed()")
                        && controller.contains("new ClassificationChipGroup.Chip")
                        && controller.contains("p.fax()?null:\"Call\"")
                        && controller.contains("validEmail(e.emailAddress())?\"Email\":null")
                        && controller.contains("value.isBlank()?null:\"Open in Maps\"")
                        && controller.contains("safeWebsite(w.website())?\"Open Website\":null"),
                "active chips must be primary-first and structured cards must expose safe explicit actions without making Fax callable");
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

    private static Object invokeResult(Object target, String name, Object argument) throws Exception {
        Method method = OrganizationController.class.getDeclaredMethod(name, argument.getClass());
        method.setAccessible(true);
        return method.invoke(target, argument);
    }
}
