package com.shale.ui.component;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.shale.ui.component.factory.UserCardFactory;
import com.shale.ui.component.factory.UserCardFactory.UserCardModel;
import com.shale.ui.util.ControlStyles;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

/**
 * Compact selected-user field that composes the shared {@link UserSelector} in a caller-supplied picker shell.
 *
 * @param <T> caller-owned user DTO type
 */
public class UserSelectionField<T> extends HBox {

    private final Function<T, Integer> userIdExtractor;
    private final Function<T, String> displayNameExtractor;
    private final Function<T, String> colorExtractor;
    private final BiFunction<UserSelectionField<T>, List<T>, Optional<T>> picker;
    private final boolean clearable;
    private final ObservableList<T> candidates = FXCollections.observableArrayList();
    private final ObjectProperty<T> selectedUser = new SimpleObjectProperty<>();
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final Button addButton = secondary("Add");
    private final Button changeButton = secondary("Change");
    private final Button removeButton = secondary("Remove");
    private final UserCardFactory cardFactory = new UserCardFactory(id -> { });

    public UserSelectionField(
            Function<T, Integer> userIdExtractor,
            Function<T, String> displayNameExtractor,
            Function<T, String> colorExtractor,
            BiFunction<UserSelectionField<T>, List<T>, Optional<T>> picker,
            boolean clearable) {
        this.userIdExtractor = Objects.requireNonNull(userIdExtractor, "userIdExtractor");
        this.displayNameExtractor = Objects.requireNonNull(displayNameExtractor, "displayNameExtractor");
        this.colorExtractor = Objects.requireNonNull(colorExtractor, "colorExtractor");
        this.picker = Objects.requireNonNull(picker, "picker");
        this.clearable = clearable;
        buildUi();
        wireState();
        render();
    }

    public ObservableList<T> getCandidates() { return candidates; }

    public void setCandidates(Collection<T> users) { candidates.setAll(users == null ? List.of() : users); }

    public ObjectProperty<T> selectedUserProperty() { return selectedUser; }

    public T getSelectedUser() { return selectedUser.get(); }

    public void setSelectedUser(T user) { selectedUser.set(user); }

    public void clearSelection() { selectedUser.set(null); }

    public BooleanProperty loadingProperty() { return loading; }

    public boolean isLoading() { return loading.get(); }

    public void setLoading(boolean loading) { this.loading.set(loading); }

    /** Opts this field's actions into the unified controls without changing legacy callers. */
    public UserSelectionField<T> useUnifiedControlStyles() {
        addButton.getStyleClass().removeAll("app-dialog-button", "app-dialog-button-secondary");
        changeButton.getStyleClass().removeAll("app-dialog-button", "app-dialog-button-secondary");
        removeButton.getStyleClass().removeAll("app-dialog-button", "app-dialog-button-secondary", "app-dialog-small-action");
        ControlStyles.apply(addButton, ControlStyles.Purpose.SECONDARY, ControlStyles.Size.STANDARD);
        ControlStyles.apply(changeButton, ControlStyles.Purpose.SECONDARY, ControlStyles.Size.STANDARD);
        ControlStyles.apply(removeButton, ControlStyles.Purpose.GHOST, ControlStyles.Size.SMALL);
        return this;
    }

    private void buildUi() {
        getStyleClass().add("user-selection-field");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(8);
        setFillHeight(false);
        setMaxWidth(Double.MAX_VALUE);
        addButton.setAccessibleText("Add user");
        addButton.setOnAction(e -> openPicker());
        changeButton.setAccessibleText("Change selected user");
        changeButton.setOnAction(e -> openPicker());
        removeButton.getStyleClass().add("app-dialog-small-action");
        removeButton.setAccessibleText("Remove selected user");
        removeButton.setOnAction(e -> clearSelection());
    }

    private void wireState() {
        selectedUser.addListener((obs, oldValue, newValue) -> render());
        loading.addListener((obs, oldValue, newValue) -> render());
        disabledProperty().addListener((obs, oldValue, newValue) -> render());
    }

    private void render() {
        getChildren().clear();
        boolean unavailable = isDisabled() || isLoading();
        addButton.setDisable(unavailable);
        changeButton.setDisable(unavailable);
        removeButton.setDisable(unavailable);
        T selected = getSelectedUser();
        if (selected == null) {
            getChildren().setAll(addButton);
            return;
        }
        var card = cardFactory.create(new UserCardModel(userId(selected), safe(displayName(selected)), color(selected), null), UserCardFactory.Variant.MINI);
        card.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(card, Priority.ALWAYS);
        getChildren().addAll(card, changeButton);
        if (clearable) getChildren().add(removeButton);
    }

    private void openPicker() {
        if (isDisabled() || isLoading()) return;
        picker.apply(this, List.copyOf(candidates)).ifPresent(this::setSelectedUser);
    }

    private Integer userId(T user) { return user == null ? null : userIdExtractor.apply(user); }
    private String displayName(T user) { return user == null ? null : displayNameExtractor.apply(user); }
    private String color(T user) { return user == null ? null : colorExtractor.apply(user); }
    private static String safe(String text) { return text == null ? "" : text; }
    private static Button secondary(String text) { Button button = new Button(text); button.getStyleClass().addAll("app-dialog-button", "app-dialog-button-secondary"); return button; }
}
