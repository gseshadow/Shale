package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class CaseControllerNullableDateDialogTest {
    private static final Path SOURCE = Path.of("src/main/java/com/shale/ui/controller/CaseController.java");
    private static final Path FXML = Path.of("src/main/resources/fxml/case.fxml");

    @Test
    void statuteAndTortDateDialogsCanSaveNullValues() throws IOException {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("editor == detStatuteOfLimitationsEditor) {\n\t\t\tshowDetailsNullableDateDialog"),
                "Statute of Limitations should use the null-aware date dialog.");
        assertTrue(source.contains("editor == detTortNoticeDeadlineEditor) {\n\t\t\tshowDetailsNullableDateDialog"),
                "Tort Notice Deadline should use the null-aware date dialog.");
        assertTrue(source.contains("Dialog<Optional<LocalDate>> dialog = new Dialog<>()"),
                "The dialog result must distinguish Save with an empty date from Cancel.");
        assertTrue(source.contains("Optional.ofNullable(nullableDatePickerValue(picker))"),
                "Saving an empty DatePicker editor should produce a present dialog result containing an empty Optional.");
        assertTrue(source.contains("dialog.showAndWait().ifPresent(value -> onSave.accept(value.orElse(null)))"),
                "The saved empty Optional should be forwarded as null so the database date column can be cleared.");
        assertTrue(source.contains("d.statuteOfLimitations = nullableDatePickerValue(detStatuteOfLimitationsEditor);"),
                "The full details save path should capture a cleared SOL editor as null.");
        assertTrue(source.contains("d.tortNoticeDeadline = nullableDatePickerValue(detTortNoticeDeadlineEditor);"),
                "The full details save path should capture a cleared TCN editor as null.");
        assertTrue(source.contains("if (editorText == null || editorText.trim().isEmpty())\n\t\t\treturn null;"),
                "Blank DatePicker editor text must be treated as an explicit clear instead of preserving the previous value.");
    }

    @Test
    void overviewStatuteAndTortDialogsUseNullableDateCapture() throws IOException {
        String source = Files.readString(SOURCE);
        String fxml = Files.readString(FXML);

        assertTrue(source.contains("showNullableDateFieldDialog(\"Edit SOL Date\""),
                "Overview SOL single-field edit should use the null-aware date dialog.");
        assertTrue(source.contains("showNullableDateFieldDialog(\"Edit Tort Notice Deadline\""),
                "Overview TCN single-field edit should use the null-aware date dialog.");
        assertTrue(source.contains("saveCoreOverviewField(\"solDate\", null, null, value, null)"),
                "Overview SOL saves should forward the nullable dialog result to the update path.");
        assertTrue(source.contains("saveCoreOverviewField(\"tortNoticeDeadline\", null, null, null, value)"),
                "Overview TCN saves should forward the nullable dialog result to the update path.");
        assertTrue(source.contains("\"tortNoticeDeadline\".equals(field) ? tortNoticeDeadline : latest.getTortNoticeDeadline()"),
                "Overview TCN saves should send null when explicitly cleared and preserve the existing value for other fields.");
        assertTrue(source.contains("button == saveType ? Optional.ofNullable(nullableDatePickerValue(picker)) : null"),
                "Overview nullable dialog cancel should produce no result while save-empty produces a present empty Optional.");
        assertTrue(fxml.contains("fx:id=\"ovTortNoticeDeadlineEditor\""),
                "Overview should expose a Tort Notice Deadline DatePicker that participates in the overview edit path.");
    }

    @Test
    void overviewFullSaveCapturesBlankDateEditorsAsNull() throws IOException {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("(ovSolDateEditor == null ? null : nullableDatePickerValue(ovSolDateEditor))"),
                "Overview full-save SOL capture should treat a blank editor as null.");
        assertTrue(source.contains("(ovTortNoticeDeadlineEditor == null ? current.getTortNoticeDeadline() : nullableDatePickerValue(ovTortNoticeDeadlineEditor))"),
                "Overview full-save TCN capture should treat a blank editor as null while preserving the current value when the control is absent.");
        assertTrue(source.contains("request.desired().desiredTortNoticeDeadline()"),
                "Overview full-save should pass the captured TCN value to updateCase.");
        assertTrue(source.contains("boolean tortNoticeChanged = !Objects.equals(desired.desiredTortNoticeDeadline(), baseTortNoticeDeadline);"),
                "Overview full-save should detect cleared and newly selected TCN dates as changes.");
    }
}
