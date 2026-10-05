package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Contract for the Case Details Incident & Deadlines value/status/action hierarchy. */
final class CaseDetailsIncidentDeadlinesLayoutContractTest {
    private static final Path CONTROLLER = Path.of("src/main/java/com/shale/ui/controller/CaseController.java");
    private static final Path FXML = Path.of("src/main/resources/fxml/case.fxml");
    private static final Path CONFIRMATION = Path.of("src/main/java/com/shale/ui/component/CaseDateConfirmationView.java");

    @Test
    void confirmationIsStackedBeneathTheDateInsteadOfUsingTheActionColumn() throws Exception {
        String source = Files.readString(CONTROLLER);
        String renderer = method(source, "private void setCompatibilityDate", "private void saveAuthoritativeDate");

        assertAll(
                () -> assertTrue(renderer.contains("VBox valueStack = compatibilityDateValueStack(label)"),
                        "Compatibility dates must resolve a dedicated value/status stack."),
                () -> assertTrue(renderer.contains("new VBox(4, label)"),
                        "The date must be the first line in a compact vertical value stack."),
                () -> assertTrue(renderer.contains("valueStack.getChildren().add(marker)"),
                        "Confirmation status must be added beneath the date value."),
                () -> assertTrue(renderer.contains("valueStack.getChildren().removeIf"),
                        "A row without confirmation must keep the same stack without a stale status line."),
                () -> assertFalse(renderer.contains("parent.add(marker,2"),
                        "Confirmation status must never occupy the edit-action column."));
    }

    @Test
    void confirmationRemainsReadableAndEditActionKeepsItsOwnColumn() throws Exception {
        String source = Files.readString(CONTROLLER);
        String confirmation = Files.readString(CONFIRMATION);
        String inlineActions = method(source, "private void prepareDetailsGridForInlineEdits", "private boolean isDetailsEditorNode");

        assertAll(
                () -> assertTrue(confirmation.contains("state.setWrapText(false)"),
                        "Confirmation text must remain horizontal."),
                () -> assertTrue(confirmation.contains("state.setTextOverrun(OverrunStyle.ELLIPSIS)"),
                        "Unusually long confirmation text must ellipsize rather than character-wrap."),
                () -> assertTrue(confirmation.contains("state.setTooltip(new Tooltip(message))"),
                        "The complete confirmation status must remain available by tooltip."),
                () -> assertTrue(inlineActions.contains("grid.add(edit, 2, row)"),
                        "Every editable Details row must retain a dedicated trailing action column."),
                () -> assertTrue(inlineActions.contains("actions.setMinWidth(36)"),
                        "The action column must reserve enough width for the pencil button."),
                () -> assertTrue(source.contains("edit.setOnAction(e -> onEditDetailsField"),
                        "The visible pencil must retain its edit interaction."));
    }

    @Test
    void MissingAndMultipleIncidentDeadlineValuesKeepTheSameAlignment() throws Exception {
        String fxml = Files.readString(FXML);
        int start = fxml.indexOf("<Label text=\"Incident &amp; Deadlines\"");
        int end = fxml.indexOf("<Label text=\"Notes\"", start);
        String section = fxml.substring(start, end);

        assertAll(
                () -> assertTrue(section.contains("fx:id=\"detDateOfMedicalNegligenceValue\" text=\"—\""),
                        "A missing date must retain the shared value-column placeholder."),
                () -> assertTrue(section.contains("fx:id=\"detStatuteOfLimitationsValue\" text=\"—\""),
                        "Statute of Limitations must use the shared value column."),
                () -> assertTrue(section.contains("fx:id=\"detTortNoticeDeadlineValue\" text=\"—\""),
                        "Multiple configured deadline rows must retain the same alignment."),
                () -> assertTrue(section.contains("<ColumnConstraints hgrow=\"ALWAYS\""),
                        "The value/status column must absorb horizontal resizing."));
    }

    private static String method(String source, String startToken, String endToken) {
        int start = source.indexOf(startToken);
        int end = source.indexOf(endToken, start);
        assertTrue(start >= 0 && end > start, "Expected controller method boundaries were not found.");
        return source.substring(start, end);
    }
}
