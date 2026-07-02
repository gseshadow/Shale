package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class CaseControllerNullableDateDialogTest {
    private static final Path SOURCE = Path.of("src/main/java/com/shale/ui/controller/CaseController.java");

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
}
