package com.shale.ui.controller;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.shale.core.service.UserDictionaryServicePort.UserDictionaryWord;
import com.shale.core.util.DictionaryWordNormalizer;
import com.shale.ui.component.CommittedChangeTracker;
import com.shale.ui.component.spellcheck.UserDictionarySession;
import com.shale.ui.util.ActionButtonFactory;
import com.shale.ui.util.ControlStyles;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** User-dictionary-specific word-list presentation and mutation lifecycle. */
final class CustomDictionaryManagementPane {
    private final UserDictionarySession dictionary;
    private final Executor worker;
    private final CommittedChangeTracker changes;
    private final AtomicBoolean mutating = new AtomicBoolean();
    private final AtomicBoolean disposed = new AtomicBoolean();
    private final AtomicInteger generation = new AtomicInteger();
    private final TextField addWord = new TextField();
    private final TextField search = new TextField();
    private final Button addButton;
    private final Button refreshButton;
    private final Label status = new Label();
    private final Label count = new Label();
    private final ListView<UserDictionaryWord> words = new ListView<>();
    private final VBox root = new VBox(12);
    private List<UserDictionaryWord> authoritative = List.of();

    CustomDictionaryManagementPane(UserDictionarySession dictionary, Executor worker,
            CommittedChangeTracker changes) {
        this.dictionary = Objects.requireNonNull(dictionary, "dictionary");
        this.worker = Objects.requireNonNull(worker, "worker");
        this.changes = Objects.requireNonNull(changes, "changes");
        addButton = ActionButtonFactory.semantic("Add", event -> add(),
                ControlStyles.Purpose.PRIMARY, ControlStyles.Size.SMALL);
        refreshButton = ActionButtonFactory.semantic("Refresh", event -> load(),
                ControlStyles.Purpose.SECONDARY, ControlStyles.Size.SMALL);
        configureControls();
        load();
    }

    Node node() { return root; }
    boolean mutationInFlight() { return mutating.get(); }
    void dispose() { disposed.set(true); generation.incrementAndGet(); }

    private void configureControls() {
        root.setId("custom-dictionary-management-pane");
        addWord.setId("custom-dictionary-add-word");
        addWord.setPromptText("Add a word");
        addWord.setOnAction(event -> add());
        search.setId("custom-dictionary-search");
        search.setPromptText("Search words");
        ControlStyles.formControl(addWord);
        ControlStyles.formControl(search);
        addButton.setId("custom-dictionary-add");
        refreshButton.setId("custom-dictionary-refresh");
        status.setId("custom-dictionary-status");
        status.getStyleClass().add("search-summary-text");
        status.setWrapText(true);
        count.getStyleClass().add("search-summary-text");
        words.setId("custom-dictionary-list");
        words.setPlaceholder(new Label("No custom words yet."));
        words.setCellFactory(ignored -> new WordCell());
        search.textProperty().addListener((observable, oldValue, newValue) -> applyFilter());
        HBox addRow = new HBox(8, addWord, addButton);
        HBox.setHgrow(addWord, Priority.ALWAYS);
        HBox tools = new HBox(8, search, refreshButton, count);
        tools.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(search, Priority.ALWAYS);
        VBox.setVgrow(words, Priority.ALWAYS);
        root.getChildren().setAll(addRow, tools, status, words);
    }

    private void load() {
        int request = generation.incrementAndGet();
        setBusy(true);
        status.setText("Loading custom words…");
        worker.execute(() -> {
            try {
                requireWorkerThread();
                List<UserDictionaryWord> loaded = List.copyOf(dictionary.list());
                Platform.runLater(() -> applyLoad(request, loaded, null));
            } catch (RuntimeException failure) {
                Platform.runLater(() -> applyLoad(request, null, failure));
            }
        });
    }

    private void applyLoad(int request, List<UserDictionaryWord> loaded, RuntimeException failure) {
        if (disposed.get() || request != generation.get()) return;
        setBusy(false);
        if (failure != null) {
            status.setText("Custom words could not be loaded. Choose Refresh to try again.");
            return;
        }
        authoritative = loaded;
        status.setText(loaded.isEmpty() ? "Add a word to begin your custom dictionary." : "");
        applyFilter();
    }

    private void applyFilter() {
        String query = search.getText() == null ? "" : search.getText().strip().toLowerCase(Locale.ROOT);
        FilteredList<UserDictionaryWord> filtered = new FilteredList<>(FXCollections.observableArrayList(authoritative),
                row -> query.isEmpty() || row.word().toLowerCase(Locale.ROOT).contains(query));
        words.setItems(filtered);
        count.setText(filtered.size() == authoritative.size() ? wordCount(authoritative.size())
                : filtered.size() + " of " + wordCount(authoritative.size()));
    }

    private void add() {
        if (mutating.get()) return;
        String snapshot = addWord.getText() == null ? "" : addWord.getText();
        String normalized = DictionaryWordNormalizer.normalize(snapshot);
        if (normalized.isBlank()) {
            status.setText("Enter a word to add.");
            addWord.requestFocus();
            return;
        }
        if (authoritative.stream().anyMatch(row -> row.normalizedWord().equals(normalized))) {
            status.setText("“" + snapshot.strip() + "” is already in your custom dictionary.");
            addWord.requestFocus();
            return;
        }
        mutate(() -> dictionary.add(snapshot), true);
    }

    private void remove(UserDictionaryWord selected) {
        if (selected == null || mutating.get()) return;
        long snapshotId = selected.id();
        String snapshotNormalized = selected.normalizedWord();
        UserDictionaryWord authoritativeRow = authoritative.stream()
                .filter(row -> row.id() == snapshotId && row.normalizedWord().equals(snapshotNormalized))
                .findFirst().orElse(null);
        if (authoritativeRow == null) {
            status.setText("That word is no longer available. Refresh and try again.");
            return;
        }
        mutate(() -> dictionary.remove(snapshotNormalized), false);
    }

    private void mutate(Runnable operation, boolean clearInput) {
        if (!mutating.compareAndSet(false, true)) return;
        setBusy(true);
        status.setText(clearInput ? "Adding…" : "Removing…");
        worker.execute(() -> {
            try {
                requireWorkerThread();
                operation.run();
                Platform.runLater(() -> {
                    if (disposed.get()) return;
                    mutating.set(false);
                    changes.markCommitted();
                    if (clearInput) addWord.clear();
                    load();
                });
            } catch (RuntimeException failure) {
                Platform.runLater(() -> {
                    if (disposed.get()) return;
                    mutating.set(false);
                    setBusy(false);
                    status.setText("The dictionary could not be changed. Check your connection and try again.");
                    if (clearInput) addWord.requestFocus();
                });
            }
        });
    }

    private void setBusy(boolean busy) {
        addWord.setDisable(busy);
        addButton.setDisable(busy);
        refreshButton.setDisable(busy);
        words.setDisable(busy);
    }

    private static void requireWorkerThread() {
        if (Platform.isFxApplicationThread())
            throw new IllegalStateException("Custom Dictionary service work must run off the JavaFX thread.");
    }

    private static String wordCount(int value) { return value + (value == 1 ? " word" : " words"); }

    private final class WordCell extends ListCell<UserDictionaryWord> {
        @Override protected void updateItem(UserDictionaryWord item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) { setText(null); setGraphic(null); return; }
            Label word = new Label(item.word());
            word.getStyleClass().add("app-dialog-field-label");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Button remove = ActionButtonFactory.semantic("Remove", event -> remove(item),
                    ControlStyles.Purpose.DANGER, ControlStyles.Size.SMALL);
            HBox row = new HBox(8, word, spacer, remove);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(4, 2, 4, 2));
            setText(null);
            setGraphic(row);
        }
    }
}
