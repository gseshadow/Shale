package com.shale.ui.component;

import com.shale.ui.services.LatestSearchRunner;
import com.shale.ui.services.RecentSearchHistory;
import com.shale.ui.services.RecentSearchHistory.Scope;
import com.shale.ui.services.SearchService;
import com.shale.ui.services.SearchService.Suggestion;
import com.shale.ui.state.AppState;
import com.shale.ui.util.ActionButtonFactory;
import com.shale.ui.util.ControlStyles;
import com.shale.ui.component.dialog.AppDialogs;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.css.PseudoClass;
import javafx.event.EventHandler;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Window;
import javafx.util.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Session-owned shell companion. Entity navigation remains in SceneManager. */
public final class UniversalSearchPopup implements AutoCloseable {
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    private final TextField field;
    private final AppState state;
    private final Source search;
    private final RecentSearchHistory history;
    private final BooleanSupplier includeDeleted;
    private final Consumer<String> fullSearch;
    private final Consumer<Suggestion> openEntity;
    private final Popup popup = new Popup();
    private final VBox content = new VBox(4);
    private final ScrollPane scroll = new ScrollPane(content);
    private final VBox footer = new VBox(4);
    private final VBox root = new VBox(4, scroll, footer);
    private final PauseTransition debounce = new PauseTransition(Duration.millis(250));
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> daemon(r, "search-suggestions"));
    // Completion writes finish on disposal; history never depends on a mutable AppState inside a worker.
    private final ExecutorService historyWorker = Executors.newSingleThreadExecutor(r -> new Thread(r, "search-history"));
    private final LatestSearchRunner runner = new LatestSearchRunner(worker, Platform::runLater);
    private final List<Button> choices = new ArrayList<>();
    private int selection = -1;
    private boolean active;
    private boolean closed;
    private boolean popupMouseDown;
    private boolean suppressFocusRefresh;
    private Scene ownerScene;
    private Window ownerWindow;
    private final ChangeListener<String> textListener = (obs, oldValue, newValue) -> queryChanged();
    private final ChangeListener<Boolean> focusListener = (obs, oldValue, focused) -> {
        if (focused && !suppressFocusRefresh) refresh();
        else Platform.runLater(this::dismissIfFocusLeft);
    };
    private final ChangeListener<Boolean> windowFocusListener = (obs, oldValue, focused) -> {
        if (!focused) Platform.runLater(this::dismissIfFocusLeft);
    };
    private final ChangeListener<Scene> sceneListener = (obs, oldValue, scene) -> {
        dismiss();
        attachScene(scene);
    };
    private final EventHandler<KeyEvent> keys = this::keyPressed;
    private final EventHandler<MouseEvent> outsideClick = this::outsideClick;
    private final Runnable identityListener = this::identityChanged;

    public UniversalSearchPopup(TextField field, AppState state, SearchService search, RecentSearchHistory history,
            BooleanSupplier includeDeleted, Consumer<String> fullSearch, Consumer<Suggestion> openEntity) {
        this(field, state, new Source() {
            public SearchService.Suggestions suggest(int tenant, String query, boolean deleted, BooleanSupplier current) {
                return search.suggest(tenant, query, deleted, current);
            }
            public boolean available(int tenant, String query, Suggestion row, boolean deleted) {
                return search.suggestionAvailable(tenant, query, row, deleted);
            }
        }, history, includeDeleted, fullSearch, openEntity);
    }

    interface Source {
        SearchService.Suggestions suggest(int tenant, String query, boolean deleted, BooleanSupplier current);
        boolean available(int tenant, String query, Suggestion row, boolean deleted);
    }

    UniversalSearchPopup(TextField field, AppState state, Source search, RecentSearchHistory history,
            BooleanSupplier includeDeleted, Consumer<String> fullSearch, Consumer<Suggestion> openEntity) {
        this.field = field;
        this.state = state;
        this.search = search;
        this.history = history;
        this.includeDeleted = includeDeleted;
        this.fullSearch = fullSearch;
        this.openEntity = openEntity;
        // Independent popup parents need the token root marker as well as their feature class.
        root.getStyleClass().addAll("root", "universal-search-popup");
        scroll.getStyleClass().add("universal-search-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setMaxHeight(420);
        popup.getContent().add(root);
        popup.setAutoHide(true);
        popup.setConsumeAutoHidingEvents(false);
        popup.setHideOnEscape(false);
        popup.setOnShown(event -> TransientPopupSupport.register(root));
        popup.setOnHidden(event -> {
            TransientPopupSupport.unregister(root);
            active = false;
            debounce.stop();
            runner.invalidate();
            clearRows();
        });
        popup.getScene().addEventFilter(KeyEvent.KEY_PRESSED, keys);
        popup.getScene().addEventFilter(MouseEvent.MOUSE_PRESSED, event -> popupMouseDown = true);
        popup.getScene().addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
            popupMouseDown = false;
            Platform.runLater(this::dismissIfFocusLeft);
        });
        field.textProperty().addListener(textListener);
        field.focusedProperty().addListener(focusListener);
        field.sceneProperty().addListener(sceneListener);
        field.addEventFilter(KeyEvent.KEY_PRESSED, keys);
        state.addIdentityListener(identityListener);
        attachScene(field.getScene());
    }

    /** Search button and unselected Enter both use this unchanged full-results route. */
    public void submitFullSearch() { submitFullSearch(query()); }

    private void submitFullSearch(String query) {
        Scope scope = scope();
        if (closed || scope == null || query.isBlank()) return;
        record(scope, query);
        field.setText(query);
        suppressFocusRefresh = true;
        dismiss();
        fullSearch.accept(query);
    }

    private void queryChanged() { suppressFocusRefresh = false; refresh(); }
    private void outsideClick(MouseEvent event) {
        if (!descendant(event.getTarget(), field)) dismiss();
        else {
            suppressFocusRefresh = false;
            if (field.isFocused() && !popup.isShowing()) refresh();
        }
    }
    private void identityChanged() {
        runner.invalidate();
        Runnable clear = () -> {
            if (closed) return;
            dismiss();
            suppressFocusRefresh = true;
            field.clear();
            dismiss();
        };
        if (Platform.isFxApplicationThread()) clear.run(); else Platform.runLater(clear);
    }

    private void refresh() {
        long token = runner.invalidate();
        debounce.stop();
        if (closed || !field.isFocused() || scope() == null) { dismiss(); return; }
        active = true;
        long session = state.sessionRevision();
        Scope scope = scope();
        String query = query();
        clearRows();
        content.getChildren().add(label(query.isBlank() ? "Loading recent searches…" : "Loading suggestions…"));
        if (!query.isBlank()) addViewAll(query, scope, session);
        show();
        if (query.isBlank()) {
            runner.submit(token, () -> historyTask(() -> history.load(scope)),
                    values -> { if (current(scope, session, query)) renderHistory(values, scope, session); },
                    error -> { if (current(scope, session, query)) message("Recent searches are unavailable."); });
        } else {
            debounce.setOnFinished(event -> runner.submit(token,
                    () -> search.suggest(scope.tenantId(), query, includeDeleted.getAsBoolean(),
                            () -> runner.isCurrent(token) && identityCurrent(scope, session)),
                    values -> {
                        if (current(scope, session, query)) renderSuggestions(values, query, scope, session);
                    },
                    error -> {
                        if (current(scope, session, query)) {
                            message("Suggestions are unavailable. You can still search.");
                            addViewAll(query, scope, session);
                        }
                    }));
            debounce.playFromStart();
        }
    }

    private void renderSuggestions(SearchService.Suggestions result, String query, Scope scope, long session) {
        clearRows();
        SearchService.SuggestionType group = null;
        for (Suggestion row : result.rows()) {
            if (row.type() != group) {
                group = row.type();
                content.getChildren().add(heading(group.label()));
            }
            Button choice = choice(row.name(), row.detail(), () -> activate(row, query, scope, session));
            content.getChildren().add(choice);
        }
        if (result.rows().isEmpty()) content.getChildren().add(label(result.failed()
                ? "Suggestions are unavailable. You can still search." : "No matches."));
        else if (result.failed()) content.getChildren().add(label("Some suggestions could not be loaded."));
        addViewAll(query, scope, session);
        show();
    }

    private void renderHistory(List<String> values, Scope scope, long session) {
        clearRows();
        content.getChildren().add(heading("Recent searches"));
        for (String value : values) {
            Button choice = choice(value, "", () -> {
                if (current(scope, session, "")) submitFullSearch(value);
            });
            HBox.setHgrow(choice, Priority.ALWAYS);
            Button remove = ActionButtonFactory.semantic("Remove", event -> changeHistory(scope, session, value),
                    ControlStyles.Purpose.GHOST, ControlStyles.Size.SMALL);
            remove.setAccessibleText("Remove recent search " + value);
            content.getChildren().add(new HBox(4, choice, remove));
        }
        if (values.isEmpty()) content.getChildren().add(label("No recent searches."));
        else footer.getChildren().add(ActionButtonFactory.semantic("Clear history",
                event -> changeHistory(scope, session, null), ControlStyles.Purpose.GHOST, ControlStyles.Size.SMALL));
        show();
    }

    private void changeHistory(Scope scope, long session, String entry) {
        if (!identityCurrent(scope, session)) return;
        long token = runner.invalidate();
        runner.submit(token, () -> {
            return historyTask(() -> {
                if (entry == null) history.clear(scope); else history.remove(scope, entry);
                return history.load(scope);
            });
        }, values -> {
            if (current(scope, session, "")) renderHistory(values, scope, session);
        }, error -> {
            if (current(scope, session, "")) message("History could not be changed. Try again.");
        });
    }

    private void activate(Suggestion row, String query, Scope scope, long session) {
        if (!current(scope, session, query)) return;
        suppressFocusRefresh = true;
        dismiss();
        // Dismiss without stealing focus or cancelling the explicit activation request.
        long token = runner.invalidate();
        runner.submit(token, () -> search.available(scope.tenantId(), query, row, includeDeleted.getAsBoolean()),
                available -> {
                    if (!identityCurrent(scope, session)) return;
                    if (!available) {
                        AppDialogs.showError(ownerWindow, "Search", "This record is unavailable or no longer matches. Search again.");
                        return;
                    }
                    record(scope, query);
                    openEntity.accept(row);
                },
                error -> {
                    if (identityCurrent(scope, session))
                        AppDialogs.showError(ownerWindow, "Search", "Unable to open this record. You can still use full search.");
                });
    }

    private <T> T historyTask(java.util.concurrent.Callable<T> task) {
        try { return historyWorker.submit(task).get(); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException("History load cancelled"); }
        catch (java.util.concurrent.ExecutionException ex) { throw new IllegalStateException("History unavailable"); }
    }

    private void record(Scope scope, String query) {
        historyWorker.execute(() -> {
            try { history.record(scope, query); }
            catch (java.io.IOException | RuntimeException ex) {
                org.slf4j.LoggerFactory.getLogger(UniversalSearchPopup.class).warn("Recent search could not be saved.");
            }
        });
    }

    private void addViewAll(String query, Scope scope, long session) {
        Button all = ActionButtonFactory.semantic("View all results", event -> {
            if (current(scope, session, query)) submitFullSearch(query);
        }, ControlStyles.Purpose.NAVIGATION, ControlStyles.Size.SMALL);
        all.setMaxWidth(Double.MAX_VALUE);
        choices.add(all);
        footer.getChildren().add(all);
    }

    private Button choice(String name, String detail, Runnable action) {
        Button button = ActionButtonFactory.semantic("", event -> action.run(),
                ControlStyles.Purpose.NAVIGATION, ControlStyles.Size.SMALL);
        button.getStyleClass().add("universal-search-choice");
        VBox graphic = new VBox(2, label(name));
        if (!detail.isBlank()) {
            Label secondary = label(detail);
            secondary.getStyleClass().add("universal-search-detail");
            graphic.getChildren().add(secondary);
        }
        graphic.setMouseTransparent(true);
        graphic.setMaxWidth(Double.MAX_VALUE);
        button.setGraphic(graphic);
        button.setAccessibleText(name + (detail.isBlank() ? "" : ", " + detail));
        button.setMaxWidth(Double.MAX_VALUE);
        button.setMinWidth(0);
        choices.add(button);
        return button;
    }

    private void keyPressed(KeyEvent event) {
        if (closed) return;
        switch (event.getCode()) {
            case ESCAPE -> { dismiss(); event.consume(); }
            case DOWN, UP -> {
                if (!popup.isShowing()) refresh();
                if (choices.isEmpty()) return;
                if (selection < 0) selection = event.getCode() == javafx.scene.input.KeyCode.DOWN ? 0 : choices.size() - 1;
                else selection = Math.floorMod(selection + (event.getCode() == javafx.scene.input.KeyCode.DOWN ? 1 : -1), choices.size());
                for (int i = 0; i < choices.size(); i++) choices.get(i).pseudoClassStateChanged(SELECTED, i == selection);
                scrollToSelection();
                event.consume();
            }
            case ENTER -> {
                if (event.getTarget() != field && popup.getScene().getFocusOwner() instanceof Button button) {
                    button.fire();
                    event.consume();
                } else if (popup.isShowing() && selection >= 0 && selection < choices.size()) {
                    choices.get(selection).fire();
                    event.consume();
                }
                // Unselected Enter reaches the TextField's existing onAction handler.
            }
            default -> { }
        }
    }

    private void scrollToSelection() {
        Button button = choices.get(selection);
        if (!descendant(button, content)) return;
        Bounds bounds = button.localToScene(button.getBoundsInLocal());
        Bounds local = content.sceneToLocal(bounds);
        double range = content.getHeight() - scroll.getViewportBounds().getHeight();
        if (range <= 0) return;
        double top = scroll.getVvalue() * range;
        if (local.getMinY() < top) scroll.setVvalue(Math.max(0, local.getMinY() / range));
        else if (local.getMaxY() > top + scroll.getViewportBounds().getHeight())
            scroll.setVvalue(Math.min(1, (local.getMaxY() - scroll.getViewportBounds().getHeight()) / range));
    }

    private void message(String text) { clearRows(); content.getChildren().add(label(text)); }
    private void clearRows() { choices.clear(); selection = -1; content.getChildren().clear(); footer.getChildren().clear(); }
    private static Label label(String text) {
        Label label = new Label(text);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setMinWidth(0);
        return label;
    }
    private static Label heading(String text) {
        Label label = label(text);
        label.getStyleClass().add("universal-search-group");
        return label;
    }

    private void show() {
        Bounds anchor = field.localToScreen(field.getBoundsInLocal());
        if (anchor == null || ownerWindow == null || !ownerWindow.isShowing()) return;
        root.setPrefWidth(Math.min(560, Math.max(320, anchor.getWidth())));
        root.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        root.setMaxWidth(560);
        scroll.setPrefHeight(Math.min(420, Math.max(64, content.getChildren().size() * 48)));
        if (!popup.isShowing()) popup.show(field, anchor.getMinX(), anchor.getMaxY() + 4);
    }

    public void dismiss() {
        active = false;
        debounce.stop();
        runner.invalidate();
        popup.hide();
        clearRows();
    }

    private void dismissIfFocusLeft() {
        if (!closed && active && !popupMouseDown && !field.isFocused() && !popup.isFocused()) dismiss();
        if (!closed && active && ownerWindow != null && !popupMouseDown
                && !ownerWindow.isFocused() && !popup.isFocused()) dismiss();
    }

    private void attachScene(Scene scene) {
        if (ownerScene != null) ownerScene.removeEventFilter(MouseEvent.MOUSE_PRESSED, outsideClick);
        if (ownerWindow != null) ownerWindow.focusedProperty().removeListener(windowFocusListener);
        ownerScene = scene;
        ownerWindow = scene == null ? null : scene.getWindow();
        if (scene != null) {
            scene.addEventFilter(MouseEvent.MOUSE_PRESSED, outsideClick);
            if (ownerWindow != null) ownerWindow.focusedProperty().addListener(windowFocusListener);
            // Scene is attached to its window after FXML/controller initialization.
            Platform.runLater(() -> {
                if (closed || ownerScene != scene) return;
                if (ownerWindow == null) {
                    ownerWindow = scene.getWindow();
                    if (ownerWindow != null) ownerWindow.focusedProperty().addListener(windowFocusListener);
                }
            });
        }
    }

    private String query() { return Objects.requireNonNullElse(field.getText(), "").trim(); }
    private Scope scope() {
        Integer tenant = state.getShaleClientId(), user = state.getUserId();
        return tenant == null || user == null || tenant <= 0 || user <= 0 ? null : new Scope(tenant, user);
    }
    private boolean identityCurrent(Scope scope, long session) {
        return !closed && state.sessionRevision() == session && Objects.equals(scope, scope());
    }
    private boolean current(Scope scope, long session, String query) {
        return active && identityCurrent(scope, session) && query.equals(query());
    }
    private static boolean descendant(Object target, Node ancestor) {
        for (Node node = target instanceof Node n ? n : null; node != null; node = node.getParent())
            if (node == ancestor) return true;
        return false;
    }
    private static Thread daemon(Runnable runnable, String name) {
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(true);
        return thread;
    }

    @Override public void close() {
        if (closed) return;
        dismiss();
        closed = true;
        state.removeIdentityListener(identityListener);
        field.textProperty().removeListener(textListener);
        field.focusedProperty().removeListener(focusListener);
        field.sceneProperty().removeListener(sceneListener);
        field.removeEventFilter(KeyEvent.KEY_PRESSED, keys);
        attachScene(null);
        worker.shutdownNow();
        historyWorker.shutdown();
    }
}
