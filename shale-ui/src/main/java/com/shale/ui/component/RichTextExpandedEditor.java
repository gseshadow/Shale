package com.shale.ui.component;

import com.shale.ui.component.richtext.NarrativeDocument;
import com.shale.ui.component.richtext.NarrativeMarkdownCodec;
import com.shale.ui.component.spellcheck.LocalSpellChecker;
import com.shale.ui.util.ControlStyles;
import javafx.animation.PauseTransition;
import javafx.geometry.Orientation;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.fxmisc.richtext.InlineCssTextArea;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/** RichTextFX-backed draft surface. It never writes persistence itself. */
final class RichTextExpandedEditor extends VBox {
    private static final String SPELLING = "-rtfx-underline-color: #d32f2f; -rtfx-underline-width: 1.2; -rtfx-underline-dash-array: 2 2;";
    private final InlineCssTextArea area = new InlineCssTextArea();
    private final LocalSpellChecker checker;
    private final PauseTransition spellDelay = new PauseTransition(Duration.millis(350));
    private final boolean spellCheck;
    private List<LocalSpellChecker.Misspelling> misspellings = List.of();

    RichTextExpandedEditor(String markdown, LocalSpellChecker checker, boolean spellCheck) {
        this.checker = checker; this.spellCheck = spellCheck;
        getStyleClass().add("rich-text-expanded-editor");
        ToolBar toolbar = new ToolBar(); toolbar.getStyleClass().add("narrative-editor-toolbar");
        ToggleButton bold = toggle("Bold", "Ctrl+B", () -> toggleFormat(NarrativeDocument.Format.BOLD));
        ToggleButton italic = toggle("Italic", "Ctrl+I", () -> toggleFormat(NarrativeDocument.Format.ITALIC));
        ToggleButton underline = toggle("Underline", "Ctrl+U", () -> toggleFormat(NarrativeDocument.Format.UNDERLINE));
        Button bullets = button("• List", "Bulleted list", () -> toggleList(false));
        Button numbers = button("1. List", "Numbered list", () -> toggleList(true));
        Button undo = button("↶", "Undo (Ctrl+Z)", area::undo);
        Button redo = button("↷", "Redo (Ctrl+Y)", area::redo);
        toolbar.getItems().addAll(bold, italic, underline, new Separator(Orientation.VERTICAL), bullets, numbers,
                new Separator(Orientation.VERTICAL), undo, redo);
        area.setWrapText(true); area.getStyleClass().add("narrative-editor-area");
        VBox.setVgrow(area, Priority.ALWAYS); getChildren().addAll(toolbar, area);
        load(NarrativeMarkdownCodec.decode(markdown));
        area.textProperty().addListener((o, old, value) -> { if (spellCheck) spellDelay.playFromStart(); });
        spellDelay.setOnFinished(e -> refreshSpelling());
        installKeys(); installContextMenu(); refreshSpelling();
    }

    InlineCssTextArea area() { return area; }

    String markdown() {
        List<EnumSet<NarrativeDocument.Format>> formats = new ArrayList<>();
        for (int i = 0; i < area.getLength(); i++) {
            String css = area.getStyleOfChar(i);
            EnumSet<NarrativeDocument.Format> value = EnumSet.noneOf(NarrativeDocument.Format.class);
            if (css.contains("font-weight: bold")) value.add(NarrativeDocument.Format.BOLD);
            if (css.contains("font-style: italic")) value.add(NarrativeDocument.Format.ITALIC);
            if (css.contains("underline: true")) value.add(NarrativeDocument.Format.UNDERLINE);
            formats.add(value);
        }
        return NarrativeMarkdownCodec.encode(new NarrativeDocument(area.getText(), formats));
    }

    private void load(NarrativeDocument document) {
        area.replaceText(document.text());
        for (int i = 0; i < document.text().length(); i++) area.setStyle(i, i + 1, css(document.formatsAt(i), false));
        area.getUndoManager().forgetHistory();
    }

    private void toggleFormat(NarrativeDocument.Format format) {
        IndexRange selection = area.getSelection(); if (selection.getLength() == 0) return;
        boolean remove = true;
        for (int i = selection.getStart(); i < selection.getEnd(); i++) if (!formatsAt(i).contains(format)) { remove = false; break; }
        for (int i = selection.getStart(); i < selection.getEnd(); i++) {
            EnumSet<NarrativeDocument.Format> formats = formatsAt(i); if (remove) formats.remove(format); else formats.add(format);
            area.setStyle(i, i + 1, css(formats, isMisspelled(i)));
        }
    }

    private void toggleList(boolean ordered) {
        String text = area.getText(); IndexRange selected = area.getSelection();
        int start = text.lastIndexOf('\n', Math.max(0, selected.getStart() - 1)) + 1;
        int endBreak = text.indexOf('\n', selected.getEnd()); int end = endBreak < 0 ? text.length() : endBreak;
        String[] lines = text.substring(start, end).split("\\n", -1); boolean remove = true;
        for (String line : lines) if (!(ordered ? line.matches("\\d+\\. .*" ) : line.startsWith("• "))) remove = false;
        StringBuilder replacement = new StringBuilder(); int number = 1;
        for (String line : lines) {
            String body = line.replaceFirst("^(?:• |\\d+\\. )", "");
            if (!replacement.isEmpty()) replacement.append('\n');
            replacement.append(remove ? body : ordered ? number++ + ". " + body : "• " + body);
        }
        area.replaceText(start, end, replacement.toString()); area.selectRange(start, start + replacement.length());
    }

    private void installKeys() {
        area.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (new KeyCodeCombination(KeyCode.B, KeyCombination.CONTROL_DOWN).match(e)) { toggleFormat(NarrativeDocument.Format.BOLD); e.consume(); }
            else if (new KeyCodeCombination(KeyCode.I, KeyCombination.CONTROL_DOWN).match(e)) { toggleFormat(NarrativeDocument.Format.ITALIC); e.consume(); }
            else if (new KeyCodeCombination(KeyCode.U, KeyCombination.CONTROL_DOWN).match(e)) { toggleFormat(NarrativeDocument.Format.UNDERLINE); e.consume(); }
            else if (new KeyCodeCombination(KeyCode.Y, KeyCombination.CONTROL_DOWN).match(e)) { area.redo(); e.consume(); }
            else if (new KeyCodeCombination(KeyCode.Z, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN).match(e)) { area.redo(); e.consume(); }
            else if (e.getCode() == KeyCode.ENTER && continueList()) e.consume();
        });
    }

    private boolean continueList() {
        int caret = area.getCaretPosition(); String text = area.getText(); int start = text.lastIndexOf('\n', Math.max(0, caret - 1)) + 1;
        String line = text.substring(start, caret);
        if (line.equals("• ") || line.matches("\\d+\\. ")) { area.replaceText(start, caret, ""); return true; }
        if (line.startsWith("• ")) { area.insertText(caret, "\n• "); return true; }
        if (line.matches("\\d+\\. .*")) { int number = Integer.parseInt(line.substring(0, line.indexOf('.'))) + 1; area.insertText(caret, "\n" + number + ". "); return true; }
        return false;
    }

    private void refreshSpelling() {
        misspellings = spellCheck ? checker.misspellingRanges(area.getText()) : List.of();
        for (int i = 0; i < area.getLength(); i++) area.setStyle(i, i + 1, css(formatsAt(i), isMisspelled(i)));
    }

    private void installContextMenu() {
        ContextMenu menu = new ContextMenu(); area.setContextMenu(menu);
        menu.setOnShowing(e -> {
            menu.getItems().clear(); int caret = area.getCaretPosition();
            LocalSpellChecker.Misspelling hit = misspellings.stream().filter(m -> caret >= m.start() && caret <= m.end()).findFirst().orElse(null);
            if (hit != null) {
                for (String suggestion : checker.suggestions(hit.word(), 5)) { MenuItem item = new MenuItem(suggestion); item.setOnAction(x -> area.replaceText(hit.start(), hit.end(), suggestion)); menu.getItems().add(item); }
                MenuItem ignore = new MenuItem("Ignore “" + hit.word() + "”"); ignore.setOnAction(x -> { checker.ignore(hit.word()); refreshSpelling(); });
                MenuItem add = new MenuItem("Add “" + hit.word() + "” to dictionary"); add.setOnAction(x -> { checker.addToCustomDictionary(hit.word()); refreshSpelling(); });
                menu.getItems().addAll(ignore, add, new SeparatorMenuItem());
            }
            MenuItem cut = new MenuItem("Cut"); cut.setOnAction(x -> area.cut()); MenuItem copy = new MenuItem("Copy"); copy.setOnAction(x -> area.copy()); MenuItem paste = new MenuItem("Paste"); paste.setOnAction(x -> area.paste());
            menu.getItems().addAll(cut, copy, paste);
        });
    }

    private EnumSet<NarrativeDocument.Format> formatsAt(int i) {
        String css = area.getStyleOfChar(i); EnumSet<NarrativeDocument.Format> result = EnumSet.noneOf(NarrativeDocument.Format.class);
        if (css.contains("font-weight: bold")) result.add(NarrativeDocument.Format.BOLD); if (css.contains("font-style: italic")) result.add(NarrativeDocument.Format.ITALIC); if (css.contains("underline: true")) result.add(NarrativeDocument.Format.UNDERLINE); return result;
    }
    private boolean isMisspelled(int i) { return misspellings.stream().anyMatch(m -> i >= m.start() && i < m.end()); }
    private static String css(EnumSet<NarrativeDocument.Format> formats, boolean spelling) { return (formats.contains(NarrativeDocument.Format.BOLD) ? "-fx-font-weight: bold;" : "") + (formats.contains(NarrativeDocument.Format.ITALIC) ? "-fx-font-style: italic;" : "") + (formats.contains(NarrativeDocument.Format.UNDERLINE) ? "-fx-underline: true;" : "") + (spelling ? SPELLING : ""); }
    private static ToggleButton toggle(String label, String tooltip, Runnable action) { ToggleButton b = new ToggleButton(label); b.setTooltip(new Tooltip(tooltip)); b.setAccessibleText(label); b.setOnAction(e -> action.run()); b.getStyleClass().add("narrative-toolbar-button"); return b; }
    private static Button button(String label, String tooltip, Runnable action) { Button b = new Button(label); b.setTooltip(new Tooltip(tooltip)); b.setAccessibleText(tooltip); b.setOnAction(e -> action.run()); ControlStyles.apply(b, ControlStyles.Purpose.GHOST, ControlStyles.Size.SMALL); b.getStyleClass().add("narrative-toolbar-button"); return b; }
}
