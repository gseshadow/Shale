package com.shale.ui.component;

import com.shale.ui.component.dialog.AppDialogs;
import com.shale.ui.component.spellcheck.LocalSpellChecker;
import com.shale.ui.component.spellcheck.ShaleDictionary;
import com.shale.ui.util.ControlStyles;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.Optional;

/** Reusable plain-text multiline editor with transactional pop-out editing and offline spelling assistance. */
public class EnhancedTextArea extends VBox {
    private static final ButtonType APPLY = new ButtonType("Apply", ButtonBar.ButtonData.APPLY);
    private final TextArea editor = new TextArea();
    private final Button expandButton = new Button("Expand");
    private final Label spellingStatus = new Label();
    private final LocalSpellChecker spellChecker;

    public EnhancedTextArea() { this(ShaleDictionary.create()); }

    EnhancedTextArea(LocalSpellChecker spellChecker) {
        this.spellChecker = spellChecker;
        getStyleClass().add("enhanced-text-area");
        editor.getStyleClass().add("enhanced-text-area-editor");
        ControlStyles.formControl(editor);
        editor.setWrapText(true);
        editor.setPrefRowCount(3);
        editor.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(editor, Priority.ALWAYS);
        ControlStyles.apply(expandButton, ControlStyles.Purpose.GHOST, ControlStyles.Size.SMALL);
        expandButton.getStyleClass().add("enhanced-text-area-expand");
        expandButton.setFocusTraversable(false);
        expandButton.setOnAction(event -> showExpandedEditor());
        HBox row = new HBox(editor, expandButton);
        row.getStyleClass().add("enhanced-text-area-row");
        HBox.setHgrow(editor, Priority.ALWAYS);
        spellingStatus.getStyleClass().add("enhanced-text-area-spelling");
        spellingStatus.setVisible(false); spellingStatus.setManaged(false);
        getChildren().addAll(row, spellingStatus);
        editor.textProperty().addListener((obs, oldValue, newValue) -> refreshSpellCheck());
        editor.setContextMenu(spellingMenu());
        expandableProperty().addListener((obs, oldValue, value) -> updateExpandVisibility());
        editableProperty().addListener((obs, oldValue, value) -> updateExpandVisibility());
        disabledProperty().addListener((obs, oldValue, value) -> updateExpandVisibility());
        spellCheckEnabledProperty().addListener((obs, oldValue, value) -> refreshSpellCheck());
        updateExpandVisibility();
    }

    public final StringProperty textProperty() { return editor.textProperty(); }
    public final String getText() { return editor.getText(); }
    public final void setText(String text) { editor.setText(text); }
    public final StringProperty promptTextProperty() { return editor.promptTextProperty(); }
    public final String getPromptText() { return editor.getPromptText(); }
    public final void setPromptText(String text) { editor.setPromptText(text); }
    public final BooleanProperty editableProperty() { return editor.editableProperty(); }
    public final boolean isEditable() { return editor.isEditable(); }
    public final void setEditable(boolean editable) { editor.setEditable(editable); }
    public final IntegerProperty prefRowCountProperty() { return editor.prefRowCountProperty(); }
    public final int getPrefRowCount() { return editor.getPrefRowCount(); }
    public final void setPrefRowCount(int rows) { editor.setPrefRowCount(rows); }

    private final BooleanProperty expandable = new javafx.beans.property.SimpleBooleanProperty(this, "expandable", true);
    public final BooleanProperty expandableProperty() { return expandable; }
    public final boolean isExpandable() { return expandable.get(); }
    public final void setExpandable(boolean value) { expandable.set(value); }

    private final BooleanProperty spellCheckEnabled = new javafx.beans.property.SimpleBooleanProperty(this, "spellCheckEnabled", true);
    public final BooleanProperty spellCheckEnabledProperty() { return spellCheckEnabled; }
    public final boolean isSpellCheckEnabled() { return spellCheckEnabled.get(); }
    public final void setSpellCheckEnabled(boolean value) { spellCheckEnabled.set(value); }

    public ExpandedTextEdit createExpandedEdit() { return new ExpandedTextEdit(getText()); }
    public void applyExpandedEdit(ExpandedTextEdit edit) { if (edit != null && isEditable()) setText(edit.draft()); }
    /** Adds a session/user supplied term without altering the bundled dictionary. */
    public void addToCustomDictionary(String word) { spellChecker.addToCustomDictionary(word); refreshSpellCheck(); }
    /** Ignores a term for this checker session. */
    public void ignoreSpelling(String word) { spellChecker.ignore(word); refreshSpellCheck(); }

    private void updateExpandVisibility() {
        boolean visible = isExpandable(); expandButton.setVisible(visible); expandButton.setManaged(visible);
        expandButton.setDisable(!isEditable() || isDisabled());
    }

    private void showExpandedEditor() {
        if (!isExpandable() || !isEditable() || isDisabled()) return;
        ExpandedTextEdit edit = createExpandedEdit();
        Dialog<String> dialog = new Dialog<>();
        Window owner = getScene() == null ? null : getScene().getWindow();
        if (owner != null) dialog.initOwner(owner);
        dialog.setTitle("Expanded text editor");
        AppDialogs.applySecondaryDialogShell(dialog, "Expanded text editor");
        TextArea expanded = new TextArea(edit.draft());
        expanded.setWrapText(true); expanded.setPrefRowCount(18); expanded.setPrefColumnCount(80);
        expanded.getStyleClass().addAll("enhanced-text-area-editor", "enhanced-text-area-dialog-editor");
        ControlStyles.formControl(expanded);
        dialog.getDialogPane().setContent(expanded);
        dialog.getDialogPane().getButtonTypes().addAll(APPLY, ButtonType.CANCEL);
        dialog.getDialogPane().setMinSize(720, 520);
        Button apply = (Button) dialog.getDialogPane().lookupButton(APPLY);
        ControlStyles.apply(apply, ControlStyles.Purpose.PRIMARY);
        ControlStyles.apply((Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL), ControlStyles.Purpose.SECONDARY);
        expanded.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (new KeyCodeCombination(KeyCode.ENTER, KeyCombination.CONTROL_DOWN).match(event)) { apply.fire(); event.consume(); }
        });
        dialog.setResultConverter(button -> button == APPLY ? expanded.getText() : null);
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(value -> { edit.setDraft(value); applyExpandedEdit(edit); });
    }

    private ContextMenu spellingMenu() {
        ContextMenu menu = new ContextMenu();
        menu.setOnShowing(event -> {
            menu.getItems().clear();
            String selected = editor.getSelectedText();
            if (isSpellCheckEnabled() && selected != null && !selected.isBlank() && spellChecker.isMisspelled(selected.trim())) {
                for (String suggestion : spellChecker.suggestions(selected.trim(), 5)) {
                    MenuItem item = new MenuItem(suggestion);
                    item.setOnAction(e -> editor.replaceSelection(suggestion)); menu.getItems().add(item);
                }
                MenuItem ignore = new MenuItem("Ignore “" + selected.trim() + "”");
                ignore.setOnAction(e -> { spellChecker.ignore(selected.trim()); refreshSpellCheck(); });
                menu.getItems().addAll(ignore, new SeparatorMenuItem());
            }
            MenuItem undo = item("Undo", editor::undo, editor.isUndoable());
            MenuItem redo = item("Redo", editor::redo, editor.isRedoable());
            MenuItem cut = item("Cut", editor::cut, editor.isEditable() && !editor.getSelectedText().isEmpty());
            MenuItem copy = item("Copy", editor::copy, !editor.getSelectedText().isEmpty());
            MenuItem paste = item("Paste", editor::paste, editor.isEditable() && Clipboard.getSystemClipboard().hasString());
            menu.getItems().addAll(undo, redo, new SeparatorMenuItem(), cut, copy, paste);
        });
        return menu;
    }

    private static MenuItem item(String label, Runnable action, boolean enabled) {
        MenuItem item = new MenuItem(label); item.setDisable(!enabled); item.setOnAction(event -> action.run()); return item;
    }

    private void refreshSpellCheck() {
        var words = isSpellCheckEnabled() ? spellChecker.misspellings(getText()) : java.util.List.<String>of();
        spellingStatus.setText(words.isEmpty() ? "" : "Check spelling: " + String.join(", ", words.stream().limit(5).toList()));
        spellingStatus.setVisible(!words.isEmpty()); spellingStatus.setManaged(!words.isEmpty());
    }
}
