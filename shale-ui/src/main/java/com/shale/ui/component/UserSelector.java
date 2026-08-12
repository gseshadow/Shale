package com.shale.ui.component;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import com.shale.ui.component.factory.UserCardFactory;
import com.shale.ui.component.factory.UserCardFactory.UserCardModel;
import com.shale.ui.util.ControlStyles;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Reusable single-selection control for choosing a Shale user supplied by the caller.
 *
 * @param <T> caller-owned user DTO type
 */
public class UserSelector<T> extends VBox {

    private static final double LIST_MIN_VIEWPORT_HEIGHT = 260;

    private final Function<T, Integer> userIdExtractor;
    private final Function<T, String> displayNameExtractor;
    private final Function<T, String> colorExtractor;
    private final ObservableList<T> candidates = FXCollections.observableArrayList();
    private final ObservableList<Integer> excludedUserIds = FXCollections.observableArrayList();
    private final ObjectProperty<T> selectedUser = new SimpleObjectProperty<>();
    private final StringProperty promptText = new SimpleStringProperty("Search users...");
    private final StringProperty emptyText = new SimpleStringProperty("No additional users available");
    private final BooleanProperty loading = new SimpleBooleanProperty(false);

    private final TextField searchField = new TextField();
    private final VBox list = new VBox(8);
    private final Label stateLabel = new Label();
    private final UserCardFactory cardFactory = new UserCardFactory(id -> { });

    public UserSelector(
            Function<T, Integer> userIdExtractor,
            Function<T, String> displayNameExtractor,
            Function<T, String> colorExtractor) {
        this.userIdExtractor = Objects.requireNonNull(userIdExtractor, "userIdExtractor");
        this.displayNameExtractor = Objects.requireNonNull(displayNameExtractor, "displayNameExtractor");
        this.colorExtractor = Objects.requireNonNull(colorExtractor, "colorExtractor");
        buildUi();
        wireState();
        renderUsers();
    }

    public ObservableList<T> getCandidates() { return candidates; }

    public void setCandidates(Collection<T> users) {
        selectedUser.set(null);
        candidates.setAll(users == null ? List.of() : users);
    }

    public ObservableList<Integer> getExcludedUserIds() { return excludedUserIds; }

    public void setExcludedUserIds(Collection<Integer> userIds) {
        excludedUserIds.setAll(userIds == null ? List.of() : userIds);
    }

    public ObjectProperty<T> selectedUserProperty() { return selectedUser; }

    public T getSelectedUser() { return selectedUser.get(); }

    public void setSelectedUser(T user) {
        if (user == null || isSelectable(user)) {
            selectedUser.set(user);
        }
    }

    public void clearSelection() { selectedUser.set(null); }

    public StringProperty promptTextProperty() { return promptText; }

    public String getPromptText() { return promptText.get(); }

    public void setPromptText(String promptText) { this.promptText.set(promptText); }

    public StringProperty emptyTextProperty() { return emptyText; }

    public String getEmptyText() { return emptyText.get(); }

    public void setEmptyText(String emptyText) { this.emptyText.set(emptyText); }

    public BooleanProperty loadingProperty() { return loading; }

    public boolean isLoading() { return loading.get(); }

    public void setLoading(boolean loading) { this.loading.set(loading); }

    /** Opts this selector's ordinary input into the shared form-control shell. */
    public void useSemanticFormControl() {
        ControlStyles.formControl(searchField);
    }

    private void buildUi() {
        getStyleClass().add("user-selector");
        setFillWidth(true);
        setSpacing(8);

        searchField.promptTextProperty().bind(promptText);
        list.setFillWidth(true);
        list.setPrefHeight(Region.USE_COMPUTED_SIZE);
        list.setMaxHeight(Region.USE_PREF_SIZE);
        stateLabel.getStyleClass().add("user-selector-empty");

        ScrollPane listScrollPane = new ScrollPane(list);
        listScrollPane.setFitToWidth(true);
        listScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        listScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        listScrollPane.setPannable(true);
        listScrollPane.getStyleClass().add("transparent-scroll");
        listScrollPane.setPrefViewportHeight(LIST_MIN_VIEWPORT_HEIGHT);
        listScrollPane.setMinViewportHeight(LIST_MIN_VIEWPORT_HEIGHT);
        listScrollPane.setPrefHeight(LIST_MIN_VIEWPORT_HEIGHT);
        listScrollPane.setMinHeight(LIST_MIN_VIEWPORT_HEIGHT);
        listScrollPane.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(listScrollPane, Priority.ALWAYS);

        getChildren().setAll(searchField, listScrollPane);
        setPadding(new Insets(0, 0, 0, 0));
    }

    private void wireState() {
        candidates.addListener((ListChangeListener<T>) change -> renderUsers());
        excludedUserIds.addListener((ListChangeListener<Integer>) change -> renderUsers());
        searchField.textProperty().addListener((obs, oldValue, newValue) -> renderUsers());
        loading.addListener((obs, oldValue, newValue) -> renderUsers());
        disabledProperty().addListener((obs, oldValue, newValue) -> renderUsers());
    }

    private void renderUsers() {
        list.getChildren().clear();
        if (isLoading()) {
            stateLabel.setText("Loading users…");
            list.getChildren().add(stateLabel);
            return;
        }
        String normalizedSearch = normalizeSearch(searchField.getText());
        List<T> filtered = candidates.stream()
                .filter(this::isSelectable)
                .filter(user -> matchesSearch(user, normalizedSearch))
                .toList();
        if (filtered.isEmpty()) {
            stateLabel.setText(getEmptyText());
            list.getChildren().add(stateLabel);
            return;
        }
        for (T user : filtered) {
            var card = cardFactory.create(new UserCardModel(userId(user), safe(displayName(user)), color(user), null), UserCardFactory.Variant.MINI);
            Button cardButton = new Button();
            cardButton.getStyleClass().addAll("app-dialog-button", "app-dialog-button-secondary", "user-selector-card-button");
            cardButton.setMaxWidth(Double.MAX_VALUE);
            cardButton.setDisable(isDisabled() || isLoading());
            cardButton.setGraphic(card);
            cardButton.setOnAction(e -> selectedUser.set(user));
            list.getChildren().add(cardButton);
        }
    }

    private boolean isSelectable(T user) {
        Integer userId = userId(user);
        return user != null && userId != null && userId > 0 && !excludedSet().contains(userId);
    }

    private Set<Integer> excludedSet() { return new HashSet<>(excludedUserIds); }

    private boolean matchesSearch(T user, String normalizedSearch) {
        if (normalizedSearch.isBlank()) return true;
        String displayName = safe(displayName(user)).trim().toLowerCase();
        return displayName.contains(normalizedSearch) || deriveInitials(displayName).contains(normalizedSearch);
    }

    private Integer userId(T user) { return user == null ? null : userIdExtractor.apply(user); }
    private String displayName(T user) { return user == null ? null : displayNameExtractor.apply(user); }
    private String color(T user) { return user == null ? null : colorExtractor.apply(user); }
    private static String safe(String text) { return text == null ? "" : text; }
    private static String normalizeSearch(String searchText) { return safe(searchText).trim().toLowerCase(); }

    private static String deriveInitials(String displayName) {
        if (displayName == null || displayName.isBlank()) return "";
        StringBuilder initials = new StringBuilder();
        for (String part : displayName.split("\\s+")) {
            if (!part.isBlank()) initials.append(part.charAt(0));
        }
        return initials.toString();
    }
}
