package com.shale.ui.component;

import com.shale.ui.component.richtext.NarrativeDocument;
import com.shale.ui.component.richtext.NarrativeMarkdownCodec;
import com.shale.ui.component.spellcheck.LocalSpellChecker;
import com.shale.ui.component.spellcheck.UserDictionarySession;
import com.shale.ui.component.dialog.AppDialogs;
import javafx.animation.PauseTransition;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.stage.Window;
import javafx.util.Duration;
import org.fxmisc.richtext.CharacterHit;
import org.fxmisc.richtext.InlineCssTextArea;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/** RichTextFX-backed draft surface. It never writes persistence itself. */
final class RichTextExpandedEditor extends VBox {
    private static final String SPELLING = "-rtfx-underline-color: #d32f2f; -rtfx-underline-width: 1.1; -rtfx-underline-dash-array: 1 2;";
    private final InlineCssTextArea area = new InlineCssTextArea();
    private final LocalSpellChecker checker;
    private final PauseTransition spellDelay = new PauseTransition(Duration.millis(350));
    private final boolean spellCheck;
    private final ContextMenu contextMenu = new ContextMenu();
    private final ChangeListener<Node> focusOwnerListener = (observable, oldOwner, newOwner) -> {
        if (newOwner != null && !isAncestorOf(newOwner)) contextMenu.hide();
    };
    private final ChangeListener<Boolean> windowFocusListener = (observable, wasFocused, isFocused) -> {
        if (!isFocused) contextMenu.hide();
    };
    private final ChangeListener<Window> sceneWindowListener = (observable, oldWindow, newWindow) -> observeWindow(newWindow);
    private Scene observedScene;
    private Window observedWindow;
    private List<LocalSpellChecker.Misspelling> misspellings = List.of();

    RichTextExpandedEditor(String markdown, LocalSpellChecker checker, boolean spellCheck) {
        this.checker = checker; this.spellCheck = spellCheck;
        getStyleClass().add("rich-text-expanded-editor");
        ToolBar toolbar = new ToolBar(); toolbar.getStyleClass().add("narrative-editor-toolbar");
        ToggleButton bold = formatToggle("B", "Bold", "Bold (Ctrl+B)", () -> toggleFormat(NarrativeDocument.Format.BOLD));
        ToggleButton italic = formatToggle("I", "Italic", "Italic (Ctrl+I)", () -> toggleFormat(NarrativeDocument.Format.ITALIC));
        ToggleButton underline = formatToggle("U", "Underline", "Underline (Ctrl+U)", () -> toggleFormat(NarrativeDocument.Format.UNDERLINE));
        ToggleButton bullets = iconToggle("M2 3h2v2H2V3m4 0h10v2H6V3M2 7h2v2H2V7m4 0h10v2H6V7m-4 4h2v2H2v-2m4 0h10v2H6v-2", "Bulleted list", () -> toggleList(false));
        ToggleButton numbers = iconToggle("M2 3h1v2H2v1h3V5H4V2H2v1m4 0h10v2H6V3M2 8h2v1H2v3h3v-1H3l2-2V7H2v1m4-1h10v2H6V7m-4 5h2v1H2v1h3v-5H2v1h2v1H2v1m4-1h10v2H6v-2", "Numbered list", () -> toggleList(true));
        toolbar.getItems().addAll(bold, italic, underline, new Separator(Orientation.VERTICAL), bullets, numbers);
        area.setWrapText(true); area.getStyleClass().add("narrative-editor-area");
        VBox.setVgrow(area, Priority.ALWAYS); getChildren().addAll(toolbar, area);
        load(NarrativeMarkdownCodec.decode(markdown));
        area.textProperty().addListener((o, old, value) -> { if (spellCheck) spellDelay.playFromStart(); });
        spellDelay.setOnFinished(e -> refreshSpelling());
        installKeys(); installContextMenu();
        sceneProperty().addListener((observable, oldScene, newScene) -> observeScene(newScene));
        Runnable state = () -> updateToolbarState(bold, italic, underline, bullets, numbers);
        area.caretPositionProperty().addListener((o, a, b) -> state.run());
        area.selectionProperty().addListener((o, a, b) -> state.run());
        state.run();
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
        misspellings = spellCheck ? checker.misspellingRanges(document.text()) : List.of();
        for (int i = 0; i < document.text().length(); i++)
            area.setStyle(i, i + 1, css(document.formatsAt(i), isMisspelled(i)));
        area.getUndoManager().forgetHistory();
    }

    private void toggleFormat(NarrativeDocument.Format format) {
        IndexRange selection = area.getSelection(); if (selection.getLength() == 0) return;
        boolean remove = true;
        for (int i = selection.getStart(); i < selection.getEnd(); i++) if (!formatsAt(i).contains(format)) { remove = false; break; }
        StyleSpansBuilder<String> styles = new StyleSpansBuilder<>();
        for (int i = selection.getStart(); i < selection.getEnd(); i++) {
            EnumSet<NarrativeDocument.Format> formats = formatsAt(i); if (remove) formats.remove(format); else formats.add(format);
            styles.add(css(formats, isMisspelled(i)), 1);
        }
        area.setStyleSpans(selection.getStart(), styles.create());
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

    void refreshSpelling() {
        misspellings = spellCheck ? checker.misspellingRanges(area.getText()) : List.of();
        StyleSpansBuilder<String> styles = new StyleSpansBuilder<>();
        for (int i = 0; i < area.getLength(); i++) styles.add(css(formatsAt(i), isMisspelled(i)), 1);
        if (area.getLength() > 0) area.setStyleSpans(0, styles.create());
    }

    private void installContextMenu() {
        // RichTextFX consumes ordinary mouse events internally, so dismiss before its
        // handlers run and deliberately leave the event unconsumed for normal caret work.
        addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() == MouseButton.PRIMARY && contextMenu.isShowing()) contextMenu.hide();
        });
        // Observe the semantic request before RichTextFX handlers, then explicitly show the
        // sole editor menu rather than relying on GenericStyledArea to show a menu property.
        area.addEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, event -> {
            refreshSpelling();
            int position;
            if (event.isKeyboardTrigger()) position = area.getCaretPosition();
            else {
                Point2D local = area.screenToLocal(event.getScreenX(), event.getScreenY());
                CharacterHit pointerHit = area.hit(local.getX(), local.getY());
                position = pointerHit.getCharacterIndex().orElse(pointerHit.getInsertionIndex());
            }
            if (contextMenu.isShowing()) contextMenu.hide();
            populateContextMenu(contextMenu, misspellingAt(misspellings, position));
            contextMenu.show(area, event.getScreenX(), event.getScreenY());
            event.consume();
        });
    }

    private boolean isAncestorOf(Node node) {
        for (Node current = node; current != null; current = current.getParent())
            if (current == this) return true;
        return false;
    }

    private void observeScene(Scene scene) {
        if (observedScene != null) observedScene.focusOwnerProperty().removeListener(focusOwnerListener);
        if (observedScene != null) observedScene.windowProperty().removeListener(sceneWindowListener);
        observeWindow(null);
        observedScene = scene;
        if (observedScene != null) observedScene.focusOwnerProperty().addListener(focusOwnerListener);
        if (observedScene != null) observedScene.windowProperty().addListener(sceneWindowListener);
        observeWindow(scene == null ? null : scene.getWindow());
        if (scene == null) contextMenu.hide();
    }

    private void observeWindow(Window window) {
        if (observedWindow != null) observedWindow.focusedProperty().removeListener(windowFocusListener);
        observedWindow = window;
        if (observedWindow != null) observedWindow.focusedProperty().addListener(windowFocusListener);
    }

    /** Releases popup and timer state when the containing dialog is finished. */
    void dispose() {
        contextMenu.hide();
        spellDelay.stop();
        observeScene(null);
    }

    private void populateContextMenu(ContextMenu menu, LocalSpellChecker.Misspelling hit) {
        menu.getItems().clear();
        if (hit != null) {
            List<String> suggestions = checker.suggestions(hit.word(), 5);
            if (suggestions.isEmpty()) { MenuItem none = new MenuItem("No suggestions"); none.setDisable(true); menu.getItems().add(none); }
            else for (String suggestion : suggestions) { MenuItem item = new MenuItem(suggestion); item.setOnAction(x -> replaceMisspelling(hit, suggestion)); menu.getItems().add(item); }
            MenuItem ignore = new MenuItem("Ignore"); ignore.setOnAction(x -> { checker.ignore(hit.word()); refreshSpelling(); });
            MenuItem add = new MenuItem("Add to dictionary"); add.setOnAction(x -> persistWord(hit.word()));
            menu.getItems().addAll(new SeparatorMenuItem(), ignore, add, new SeparatorMenuItem());
        }
        MenuItem cut = new MenuItem("Cut"); cut.setDisable(area.getSelection().getLength() == 0); cut.setOnAction(x -> area.cut());
        MenuItem copy = new MenuItem("Copy"); copy.setDisable(area.getSelection().getLength() == 0); copy.setOnAction(x -> area.copy());
        MenuItem paste = new MenuItem("Paste"); paste.setDisable(!Clipboard.getSystemClipboard().hasString()); paste.setOnAction(x -> area.paste());
        MenuItem selectAll = new MenuItem("Select All"); selectAll.setDisable(area.getLength() == 0); selectAll.setOnAction(x -> area.selectAll());
        menu.getItems().addAll(cut, copy, paste, selectAll);
    }

    private void persistWord(String word) {
        try { UserDictionarySession.current().add(word); refreshSpelling(); }
        catch (RuntimeException ex) { AppDialogs.showError(getScene()==null?null:getScene().getWindow(), "Custom dictionary", "The word could not be saved. Check your connection and try again."); refreshSpelling(); }
    }

    private void replaceMisspelling(LocalSpellChecker.Misspelling misspelling, String suggestion) {
        EnumSet<NarrativeDocument.Format> formatting = formatsAt(misspelling.start());
        area.replaceText(misspelling.start(), misspelling.end(), suggestion);
        for (int i = misspelling.start(); i < misspelling.start() + suggestion.length(); i++)
            area.setStyle(i, i + 1, css(formatting, false));
        refreshSpelling();
    }

    static LocalSpellChecker.Misspelling misspellingAt(List<LocalSpellChecker.Misspelling> ranges, int characterIndex) {
        return ranges.stream().filter(m -> characterIndex >= m.start() && characterIndex < m.end()).findFirst().orElse(null);
    }

    private EnumSet<NarrativeDocument.Format> formatsAt(int i) {
        String css = area.getStyleOfChar(i); EnumSet<NarrativeDocument.Format> result = EnumSet.noneOf(NarrativeDocument.Format.class);
        if (css.contains("font-weight: bold")) result.add(NarrativeDocument.Format.BOLD); if (css.contains("font-style: italic")) result.add(NarrativeDocument.Format.ITALIC); if (css.contains("underline: true")) result.add(NarrativeDocument.Format.UNDERLINE); return result;
    }
    private boolean isMisspelled(int i) { return misspellings.stream().anyMatch(m -> i >= m.start() && i < m.end()); }
    private static String css(EnumSet<NarrativeDocument.Format> formats, boolean spelling) { return (formats.contains(NarrativeDocument.Format.BOLD) ? "-fx-font-weight: bold;" : "") + (formats.contains(NarrativeDocument.Format.ITALIC) ? "-fx-font-style: italic;" : "") + (formats.contains(NarrativeDocument.Format.UNDERLINE) ? "-fx-underline: true;" : "") + (spelling ? SPELLING : ""); }
    private void updateToolbarState(ToggleButton bold, ToggleButton italic, ToggleButton underline, ToggleButton bullets, ToggleButton numbers) {
        IndexRange selection = area.getSelection();
        int from = selection.getLength() == 0 ? Math.max(0, area.getCaretPosition() - 1) : selection.getStart();
        int to = selection.getLength() == 0 ? Math.min(area.getLength(), from + 1) : selection.getEnd();
        bold.setSelected(uniformFormat(NarrativeDocument.Format.BOLD, from, to));
        italic.setSelected(uniformFormat(NarrativeDocument.Format.ITALIC, from, to));
        underline.setSelected(uniformFormat(NarrativeDocument.Format.UNDERLINE, from, to));
        String line = currentLine(); bullets.setSelected(line.startsWith("• ")); numbers.setSelected(line.matches("\\d+\\. .*"));
    }

    private boolean uniformFormat(NarrativeDocument.Format format, int from, int to) {
        if (from >= to) return false;
        for (int i = from; i < to; i++) if (!formatsAt(i).contains(format)) return false;
        return true;
    }

    private String currentLine() {
        String text = area.getText(); int caret = area.getCaretPosition();
        int start = text.lastIndexOf('\n', Math.max(0, caret - 1)) + 1;
        int end = text.indexOf('\n', caret); return text.substring(start, end < 0 ? text.length() : end);
    }

    private ToggleButton formatToggle(String glyph, String accessible, String tooltip, Runnable action) {
        ToggleButton button = baseToggle(accessible, tooltip, action); button.setText(glyph);
        button.getStyleClass().add("narrative-format-" + accessible.toLowerCase()); return button;
    }
    private ToggleButton iconToggle(String path, String accessible, Runnable action) {
        ToggleButton button = baseToggle(accessible, accessible, action); button.setGraphic(icon(path)); return button;
    }
    private ToggleButton baseToggle(String accessible, String tooltip, Runnable action) {
        ToggleButton button = new ToggleButton(); button.setTooltip(new Tooltip(tooltip)); button.setAccessibleText(accessible);
        button.setOnAction(e -> { action.run(); area.requestFocus(); });
        button.getStyleClass().add("narrative-toolbar-button"); return button;
    }
    private static SVGPath icon(String content) { SVGPath icon = new SVGPath(); icon.setContent(content); icon.getStyleClass().add("narrative-toolbar-icon"); return icon; }
}
