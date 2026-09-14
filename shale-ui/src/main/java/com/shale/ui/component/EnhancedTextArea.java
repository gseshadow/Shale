package com.shale.ui.component;

import com.shale.ui.component.dialog.AppDialogs;
import com.shale.ui.component.spellcheck.LocalSpellChecker;
import com.shale.ui.component.spellcheck.UserDictionarySession;
import com.shale.ui.component.richtext.NarrativeMarkdownCodec;
import com.shale.ui.util.ControlStyles;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.stage.Window;

import java.util.Optional;
import java.util.function.Consumer;

/** Reusable plain-text multiline editor with transactional pop-out editing and offline spelling assistance. */
public class EnhancedTextArea extends VBox {
    private static final ButtonType APPLY = new ButtonType("Apply", ButtonBar.ButtonData.APPLY);
    private final TextArea editor = new TextArea();
    private final Button expandButton = new Button();
    private final LocalSpellChecker spellChecker;
    private final StringProperty text = new SimpleStringProperty(this, "text", "");
    private final BooleanProperty editable = new javafx.beans.property.SimpleBooleanProperty(this, "editable", true);
    private boolean updatingProjection;

    public EnhancedTextArea() { this(UserDictionarySession.current().checker()); }

    EnhancedTextArea(LocalSpellChecker spellChecker) {
        this.spellChecker = spellChecker;
        getStyleClass().add("enhanced-text-area");
        editor.getStyleClass().add("enhanced-text-area-editor");
        ControlStyles.formControl(editor);
        editor.setWrapText(true);
        editor.setPrefRowCount(3);
        editor.setMaxWidth(Double.MAX_VALUE);
        ControlStyles.apply(expandButton, ControlStyles.Purpose.GHOST, ControlStyles.Size.SMALL);
        ControlStyles.iconOnly(expandButton);
        expandButton.getStyleClass().add("enhanced-text-area-expand");
        SVGPath expandIcon = new SVGPath();
        expandIcon.setContent("M 4 5 L 4 16 L 15 16 L 15 12 M 10 4 L 16 4 L 16 10 M 16 4 L 9 11");
        expandIcon.getStyleClass().add("enhanced-text-area-expand-icon");
        expandButton.setGraphic(expandIcon);
        expandButton.setTooltip(new Tooltip("Open expanded editor"));
        expandButton.setAccessibleText("Open expanded editor");
        expandButton.setMinSize(32, 32); expandButton.setPrefSize(32, 32); expandButton.setMaxSize(32, 32);
        expandButton.setOnAction(event -> showExpandedEditor());
        StackPane editorChrome = new StackPane(editor, expandButton);
        editorChrome.getStyleClass().add("enhanced-text-area-chrome");
        StackPane.setAlignment(expandButton, Pos.TOP_RIGHT);
        StackPane.setMargin(expandButton, new javafx.geometry.Insets(6, 7, 0, 0));
        VBox.setVgrow(editorChrome, Priority.ALWAYS);
        getChildren().add(editorChrome);
        text.addListener((obs, oldValue, newValue) -> updateInlineProjection());
        editor.textProperty().addListener((obs, oldValue, newValue) -> { if (!updatingProjection && editor.isEditable() && !java.util.Objects.equals(text.get(), newValue)) text.set(newValue); refreshSpellCheck(); });
        editor.setContextMenu(spellingMenu());
        expandableProperty().addListener((obs, oldValue, value) -> updateExpandVisibility());
        editableProperty().addListener((obs, oldValue, value) -> updateInlineProjection());
        disabledProperty().addListener((obs, oldValue, value) -> updateExpandVisibility());
        spellCheckEnabledProperty().addListener((obs, oldValue, value) -> refreshSpellCheck());
        updateExpandVisibility();
        updateInlineProjection();
    }

    public final StringProperty textProperty() { return text; }
    public final String getText() { return text.get(); }
    public final void setText(String text) { this.text.set(text == null ? "" : text); }
    public final StringProperty promptTextProperty() { return editor.promptTextProperty(); }
    public final String getPromptText() { return editor.getPromptText(); }
    public final void setPromptText(String text) { editor.setPromptText(text); }
    public final BooleanProperty editableProperty() { return editable; }
    public final boolean isEditable() { return editable.get(); }
    public final void setEditable(boolean editable) { this.editable.set(editable); }
    public final IntegerProperty prefRowCountProperty() { return editor.prefRowCountProperty(); }
    public final int getPrefRowCount() { return editor.getPrefRowCount(); }
    public final void setPrefRowCount(int rows) { editor.setPrefRowCount(rows); }

    private final StringProperty editorTitle = new SimpleStringProperty(this, "editorTitle", "");
    public final StringProperty editorTitleProperty() { return editorTitle; }
    public final String getEditorTitle() { return editorTitle.get(); }
    public final void setEditorTitle(String title) { editorTitle.set(title == null ? "" : title); }

    private final BooleanProperty expandable = new javafx.beans.property.SimpleBooleanProperty(this, "expandable", true);
    public final BooleanProperty expandableProperty() { return expandable; }
    public final boolean isExpandable() { return expandable.get(); }
    public final void setExpandable(boolean value) { expandable.set(value); }

    private final BooleanProperty spellCheckEnabled = new javafx.beans.property.SimpleBooleanProperty(this, "spellCheckEnabled", true);
    public final BooleanProperty spellCheckEnabledProperty() { return spellCheckEnabled; }
    public final boolean isSpellCheckEnabled() { return spellCheckEnabled.get(); }
    public final void setSpellCheckEnabled(boolean value) { spellCheckEnabled.set(value); }

    public ExpandedTextEdit createExpandedEdit() { return new ExpandedTextEdit(getText()); }
    public void applyExpandedEdit(ExpandedTextEdit edit) { if (edit != null && canExpand()) setText(edit.draft()); }
    /** Adds a session/user supplied term without altering the bundled dictionary. */
    public void addToCustomDictionary(String word) {
        try { UserDictionarySession.current().add(word); refreshSpellCheck(); }
        catch (RuntimeException ex) { AppDialogs.showError(getScene()==null?null:getScene().getWindow(), "Custom dictionary", "The word could not be saved. Check your connection and try again."); }
    }
    /** Ignores a term for this checker session. */
    public void ignoreSpelling(String word) { spellChecker.ignore(word); refreshSpellCheck(); }

    private void updateExpandVisibility() {
        boolean visible = isExpandable(); expandButton.setVisible(visible); expandButton.setManaged(visible);
        expandButton.setDisable(!isEditable() || isDisabled());
    }

    private void updateInlineProjection() {
        boolean formatted = NarrativeMarkdownCodec.containsFormatting(getText());
        String visible = formatted ? NarrativeMarkdownCodec.plainText(getText()) : getText();
        updatingProjection = true;
        try { if (!java.util.Objects.equals(editor.getText(), visible)) editor.setText(visible); }
        finally { updatingProjection = false; }
        editor.setEditable(isEditable() && !formatted);
        editor.setTooltip(formatted ? new Tooltip("Formatted narrative — open the expanded editor to edit") : null);
        editor.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("formatted-preview"), formatted);
        updateExpandVisibility();
    }

    private void showExpandedEditor() {
        if (!canExpand()) return;
        openEditor(getScene() == null ? null : getScene().getWindow(), expandedDialogTitle(), getText(),
                isSpellCheckEnabled(), this::setText);
    }

    /**
     * Opens the shared transactional narrative editor for a read-only/detail view.
     * The callback is invoked only after Apply; closing or cancelling never mutates the value.
     */
    public static void openEditor(Window owner, String title, String currentValue, Consumer<String> onApply) {
        openEditor(owner, title, currentValue, true, onApply);
    }

    static void openEditor(Window owner, String title, String currentValue, boolean spellCheck, Consumer<String> onApply) {
        Dialog<String> dialog = new Dialog<>();
        if (owner != null) dialog.initOwner(owner);
        String resolvedTitle = title == null || title.isBlank() ? "Edit text" : title;
        dialog.setTitle(resolvedTitle);
        AppDialogs.applySecondaryDialogShell(dialog, resolvedTitle);
        RichTextExpandedEditor expanded = new RichTextExpandedEditor(currentValue, UserDictionarySession.current().checker(), spellCheck);
        expanded.setPrefSize(760, 440);
        Label shortcutHint = new Label("Ctrl+Enter to Apply");
        shortcutHint.getStyleClass().add("enhanced-text-area-dialog-hint");
        shortcutHint.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(expanded, Priority.ALWAYS);
        VBox content = new VBox(8, expanded, shortcutHint);
        content.getStyleClass().add("enhanced-text-area-dialog-content");
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(APPLY, ButtonType.CANCEL);
        dialog.getDialogPane().setMinSize(720, 520);
        Button apply = (Button) dialog.getDialogPane().lookupButton(APPLY);
        ControlStyles.apply(apply, ControlStyles.Purpose.PRIMARY);
        ControlStyles.apply((Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL), ControlStyles.Purpose.SECONDARY);
        expanded.area().addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (new KeyCodeCombination(KeyCode.ENTER, KeyCombination.CONTROL_DOWN).match(event)) { apply.fire(); event.consume(); }
        });
        dialog.setResultConverter(button -> button == APPLY ? expanded.markdown() : null);
        dialog.setOnShown(event -> Platform.runLater(() -> expanded.area().requestFocus()));
        dialog.setOnHidden(event -> expanded.dispose());
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(onApply);
    }

    private ContextMenu spellingMenu() {
        ContextMenu menu = new ContextMenu();
        menu.setOnShowing(event -> {
            menu.getItems().clear();
            WordRange target = selectedOrCaretWord();
            if (isSpellCheckEnabled() && target != null && spellChecker.isMisspelled(target.word())) {
                for (String suggestion : spellChecker.suggestions(target.word(), 5)) {
                    MenuItem item = new MenuItem(suggestion);
                    item.setOnAction(e -> editor.replaceText(target.start(), target.end(), suggestion)); menu.getItems().add(item);
                }
                MenuItem ignore = new MenuItem("Ignore “" + target.word() + "”");
                ignore.setOnAction(e -> { spellChecker.ignore(target.word()); refreshSpellCheck(); });
                MenuItem add = new MenuItem("Add “" + target.word() + "” to dictionary");
                add.setOnAction(e -> addToCustomDictionary(target.word()));
                menu.getItems().addAll(ignore, add, new SeparatorMenuItem());
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
        var words = isSpellCheckEnabled() ? spellChecker.misspellings(editor.getText()) : java.util.List.<String>of();
        editor.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("has-spelling-issues"), !words.isEmpty());
    }

    public boolean canExpand() { return isExpandable() && isEditable() && !isDisabled(); }

    public String expandedDialogTitle() {
        String configured = getEditorTitle() == null ? "" : getEditorTitle().trim();
        return configured.isEmpty() ? "Edit text" : "Edit " + configured;
    }

    private WordRange selectedOrCaretWord() {
        if (!editor.getSelectedText().isBlank()) {
            return new WordRange(editor.getSelection().getStart(), editor.getSelection().getEnd(), editor.getSelectedText().trim());
        }
        return wordAt(editor.getText(), editor.getCaretPosition());
    }

    static WordRange wordAt(String text, int caret) {
        if (text == null || text.isEmpty()) return null;
        int position = Math.max(0, Math.min(caret, text.length()));
        if (position == text.length()) position--;
        if (position < 0 || !isWordCharacter(text.charAt(position))) return null;
        int start = position, end = position + 1;
        while (start > 0 && isWordCharacter(text.charAt(start - 1))) start--;
        while (end < text.length() && isWordCharacter(text.charAt(end))) end++;
        return new WordRange(start, end, text.substring(start, end));
    }

    private static boolean isWordCharacter(char value) {
        return Character.isLetter(value) || value == '\'' || value == '’' || value == '-';
    }

    record WordRange(int start, int end, String word) { }
}
