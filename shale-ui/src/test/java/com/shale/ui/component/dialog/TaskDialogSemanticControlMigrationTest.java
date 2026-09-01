package com.shale.ui.component.dialog;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

final class TaskDialogSemanticControlMigrationTest {
    private static final Path CREATE = Path.of("src/main/java/com/shale/ui/component/dialog/NewTaskDialog.java");
    private static final Path DETAIL = Path.of("src/main/java/com/shale/ui/component/dialog/TaskDetailDialog.java");
    private static final Path PICKER = Path.of("src/main/java/com/shale/ui/component/dialog/AssignedUserPickerDialog.java");
    private static final Path APP_CSS = Path.of("src/main/resources/css/app.css");

    @Test void classifiesEveryTaskDialogActionByBehavior() throws Exception {
        String create = Files.readString(CREATE);
        String detail = Files.readString(DETAIL);
        String picker = Files.readString(PICKER);

        assertSemantic(create, "addAssignedButton", "SECONDARY");
        assertSemantic(create, "cancelButton", "SECONDARY");
        assertSemantic(create, "createButton", "PRIMARY");
        assertSemantic(create, "removeButton", "GHOST");
        assertTrue(create.contains("removeButton, ControlStyles.Purpose.GHOST, ControlStyles.Size.SMALL"));

        assertSemantic(detail, "addAssignedUserButton", "SECONDARY");
        assertSemantic(detail, "addNoteButton", "PRIMARY");
        assertSemantic(detail, "deleteButton", "DANGER");
        assertSemantic(detail, "cancelButton", "SECONDARY");
        assertSemantic(detail, "completionToggleButton", "SECONDARY");
        assertSemantic(detail, "saveButton", "PRIMARY");
        assertSemantic(detail, "removeButton", "GHOST");
        assertSemantic(detail, "editButton", "GHOST");
        assertFalse(detail.contains("completionToggleButton, ControlStyles.Purpose.DANGER"));
        assertFalse(detail.contains("completionToggleButton, ControlStyles.Purpose.PRIMARY"),
                "Save remains the sole primary in the detail footer");
        assertSemantic(picker, "closeButton", "SECONDARY");
    }

    @Test void optsFieldsIntoFormShellAndConnectsRejectedSaveValidation() throws Exception {
        String create = Files.readString(CREATE);
        String detail = Files.readString(DETAIL);
        String picker = Files.readString(PICKER);

        for (String control : new String[] {"titleField", "dueDatePicker", "dueTimeField", "priorityComboBox"}) {
            assertTrue(create.contains("ControlStyles.formControl(" + control + ")"), control);
        }
        for (String control : new String[] {"titleField", "dueDatePicker", "dueTimeField",
                "statusCombo", "priorityCombo", "noteComposer", "editArea"}) {
            assertTrue(detail.contains("ControlStyles.formControl(" + control + ")"), control);
        }

        assertEnhancedDescriptionContract(create, "content");
        assertEnhancedDescriptionContract(detail, "formContent");
        assertTrue(create.contains("ControlStyles.setInvalid(titleField, true)"));
        assertTrue(create.contains("ControlStyles.setInvalid(dueTimeField, true)"));
        assertTrue(detail.contains("validationVisible[0] = true"));
        assertTrue(detail.contains("ControlStyles.setInvalid(titleField"));
        assertTrue(detail.contains("ControlStyles.setInvalid(statusCombo"));
        assertTrue(detail.contains("ControlStyles.setInvalid(priorityCombo"));
        assertTrue(picker.contains("selector.useSemanticFormControl()"));
    }

    @Test void preservesDialogKeyboardAndSpecializedRenderingContracts() throws Exception {
        String create = Files.readString(CREATE);
        String detail = Files.readString(DETAIL);
        String picker = Files.readString(PICKER);

        assertTrue(create.contains("createButton.setDefaultButton(true)"));
        assertTrue(create.contains("cancelButton.setCancelButton(true)"));
        assertTrue(detail.contains("saveButton.setDefaultButton(true)"));
        assertTrue(detail.contains("cancelButton.setCancelButton(true)"));
        assertTrue(picker.contains("closeButton.setCancelButton(true)"));
        assertTrue(detail.contains("applyColoredToolbarSelect(statusCombo"));
        assertTrue(detail.contains("applyColoredToolbarSelect(priorityCombo"));
        assertTrue(detail.contains("CaseCardFactory.Variant.EMBEDDED"));
        assertTrue(detail.contains("UserCardFactory.Variant.MINI"));
        assertTrue(detail.contains("busyMutationUi.register(addNoteButton)"));
    }

    @Test void introducesNoInlineActionColorsOrLegacyButtonFallbacks() throws Exception {
        for (Path source : new Path[] {CREATE, DETAIL, PICKER}) {
            String text = Files.readString(source);
            assertFalse(text.contains("getStyleClass().addAll(\"app-dialog-button\""), source.toString());
            assertFalse(text.matches("(?s).*\\b(?:create|save|cancel|delete|completionToggle|addNote|addAssigned|remove|edit|close)Button"
                    + "\\.setStyle\\(.*"), source.toString());
        }
    }

    @Test void newTaskBackgroundOwningFooterHasShellMatchingBottomRadii() throws Exception {
        String create = Files.readString(CREATE);
        String css = Files.readString(APP_CSS);

        assertTrue(create.contains("actions.getStyleClass().addAll(\"app-dialog-action-bar\", \"new-task-dialog-action-bar\")"));
        assertTrue(css.contains(".new-task-dialog-action-bar {"));
        int selector = css.indexOf(".new-task-dialog-action-bar {");
        int close = css.indexOf('}', selector);
        String rule = css.substring(selector, close);
        assertTrue(rule.contains("-fx-background-radius: 0 0 16 16;"));
        assertTrue(rule.contains("-fx-border-radius: 0 0 16 16;"));
    }

    private static void assertEnhancedDescriptionContract(String source, String formVariable) {
        assertTrue(source.contains("import com.shale.ui.component.EnhancedTextArea;"));
        assertTrue(source.contains("EnhancedTextArea descriptionArea = new EnhancedTextArea()"));
        assertTrue(source.contains("descriptionArea.setEditorTitle(\"Task Description\")"));
        assertVBoxInitializerContains(source, formVariable, "descriptionArea");
        assertTrue(source.contains("descriptionArea.getText()"), "Save should retain the enhanced description draft");
        assertFalse(source.matches("(?s).*\\bTextArea\\s+descriptionArea\\b.*"),
                "task description must not regress to raw TextArea");
        assertFalse(source.contains("ControlStyles.formControl(descriptionArea)"),
                "EnhancedTextArea owns semantic styling for its internal control");
    }

    private static void assertVBoxInitializerContains(String source, String variable, String child) {
        String declaration = "VBox " + variable + " = new VBox(";
        int start = source.indexOf(declaration);
        assertTrue(start >= 0, variable + " form shell should be declared");
        int end = source.indexOf(");", start);
        assertTrue(end > start, variable + " form shell initializer should be complete");
        assertTrue(source.substring(start, end).contains(child), child + " wrapper should be a child of " + variable);
    }

    private static void assertSemantic(String source, String variable, String purpose) {
        assertTrue(source.contains("ControlStyles.apply(" + variable + ", ControlStyles.Purpose." + purpose),
                variable + " should be " + purpose);
    }
}
