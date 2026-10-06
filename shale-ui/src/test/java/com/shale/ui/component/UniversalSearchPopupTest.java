package com.shale.ui.component;

import static org.junit.jupiter.api.Assertions.*;
import com.shale.ui.services.RecentSearchHistory;
import com.shale.ui.services.SearchService;
import com.shale.ui.services.SearchService.Suggestion;
import com.shale.ui.services.SearchService.SuggestionType;
import com.shale.ui.state.AppState;
import com.shale.ui.testutil.JavaFxTestSupport;
import com.shale.ui.theme.Theme;
import com.shale.ui.theme.ThemeManager;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** User-facing input/navigation contracts; no JavaFX skin or rendered-geometry assertions. */
class UniversalSearchPopupTest {
    @TempDir Path directory;

    @Test void unselectedEnterPreservesFullSearchAndRecentKeyboardSelectionRunsThatQuery() throws Exception {
        var history = new RecentSearchHistory(directory);
        history.record(new RecentSearchHistory.Scope(7, 11), "Recent Case");
        Fixture fixture = create(history);
        try {
            fixture.awaitChoices(1);
            JavaFxTestSupport.runAndWait(() -> {
                key(fixture.field, KeyCode.DOWN);
                key(fixture.field, KeyCode.ENTER);
            });
            assertTrue(fixture.full.await(5, TimeUnit.SECONDS));
            assertEquals("Recent Case", fixture.fullQuery);
            JavaFxTestSupport.runAndWait(() -> {
                fixture.field.setText("New query");
                key(fixture.field, KeyCode.ENTER);
                assertEquals("New query", fixture.fullQuery);
                assertFalse(fixture.window().isShowing(), "Submitting full search must close suggestions");
            });
        } finally { fixture.close(); }
    }

    @Test void selectedSuggestionAndMouseFocusTransferOpenOnceAndRecordOnlyCompletedQuery() throws Exception {
        var history = new RecentSearchHistory(directory);
        Fixture fixture = create(history);
        try {
            JavaFxTestSupport.runAndWait(() -> fixture.field.setText("Example"));
            fixture.awaitChoices(1);
            JavaFxTestSupport.runAndWait(() -> {
                var button = fixture.choice();
                button.fireEvent(mouse(MouseEvent.MOUSE_PRESSED));
                fixture.other.requestFocus();
                button.fire();
                button.fireEvent(mouse(MouseEvent.MOUSE_RELEASED));
            });
            assertTrue(fixture.opened.await(5, TimeUnit.SECONDS), "Click must survive TextField focus loss");
            assertEquals(42L, fixture.openedId);
            assertEquals(0, fixture.fullCount);
        } finally { fixture.close(); }
        // Disposal drains the completion-write executor.
        await(() -> history.load(new RecentSearchHistory.Scope(7, 11)).equals(List.of("Example")));
    }

    @Test void escapeOutsideClickAndIdentityChangeDismissAndNeverExposeOtherHistory() throws Exception {
        var history = new RecentSearchHistory(directory);
        history.record(new RecentSearchHistory.Scope(7, 11), "Private previous query");
        Fixture fixture = create(history);
        try {
            fixture.awaitChoices(1);
            JavaFxTestSupport.runAndWait(() -> {
                var popupRoot = (javafx.scene.Parent) fixture.window().getScene().getRoot().lookup(".universal-search-popup");
                popupRoot.applyCss();
                var name = (javafx.scene.control.Label) fixture.choice().getGraphic().lookup(".label");
                var lightText = name.getTextFill();
                ThemeManager.application().setActiveTheme(Theme.DARK);
                popupRoot.applyCss();
                assertNotEquals(lightText, name.getTextFill(), "The popup must resolve active theme tokens, not merely attach a stylesheet");
                assertTrue(fixture.choice().getStyleClass().contains("shale-control-navigation"));
                assertTrue(((javafx.scene.Parent) fixture.window().getScene().getRoot().lookup(".universal-search-popup")).getStylesheets().stream().anyMatch(v -> v.endsWith("dark.css")));
                key(fixture.field, KeyCode.ESCAPE);
                assertFalse(fixture.window().isShowing());
                fixture.field.fireEvent(mouse(MouseEvent.MOUSE_PRESSED));
            });
            fixture.awaitChoices(1);
            JavaFxTestSupport.runAndWait(() -> {
                fixture.other.fireEvent(mouse(MouseEvent.MOUSE_PRESSED));
                assertFalse(fixture.window().isShowing());
                fixture.field.requestFocus();
                fixture.field.fireEvent(mouse(MouseEvent.MOUSE_PRESSED));
            });
            fixture.awaitChoices(1);
            JavaFxTestSupport.runAndWait(() -> {
                fixture.state.setUserId(12);
                assertFalse(fixture.window().isShowing(), "User change must synchronously dismiss old history");
                assertEquals("", fixture.field.getText());
                fixture.popup.submitFullSearch();
                assertEquals(0, fixture.fullCount);
            });
            assertTrue(history.load(new RecentSearchHistory.Scope(7, 12)).isEmpty());
        } finally { fixture.close(); }
    }

    @Test void arrowSelectedLiveSuggestionOpensWithoutSubmittingFullSearch() throws Exception {
        Fixture fixture = create(new RecentSearchHistory(directory));
        try {
            JavaFxTestSupport.runAndWait(() -> fixture.field.setText("Example"));
            fixture.awaitChoices(1);
            JavaFxTestSupport.runAndWait(() -> {
                key(fixture.field, KeyCode.DOWN);
                key(fixture.field, KeyCode.ENTER);
            });
            assertTrue(fixture.opened.await(5, TimeUnit.SECONDS));
            assertEquals(42L, fixture.openedId);
            assertEquals(0, fixture.fullCount, "Selected Enter must consume the event before full-search onAction");
        } finally { fixture.close(); }
    }

    @Test void emptyAndFailedSuggestionsRetainViewAllAndPermitContinuedTyping() throws Exception {
        Fixture fixture = create(new RecentSearchHistory(directory));
        try {
            for (String query : List.of("none", "fail")) {
                JavaFxTestSupport.runAndWait(() -> fixture.field.setText(query));
                await(() -> JavaFxTestSupport.runAndWait(() -> fixture.window().getScene().getRoot().lookupAll(".label")
                        .stream().filter(n -> n instanceof javafx.scene.control.Label)
                        .map(n -> ((javafx.scene.control.Label)n).getText())
                        .anyMatch(text -> query.equals("none") ? text.equals("No matches.") : text.contains("unavailable"))));
                JavaFxTestSupport.runAndWait(() -> {
                    Button all = fixture.window().getScene().getRoot().lookupAll(".button").stream()
                            .map(n -> (Button)n).filter(b -> b.getText().equals("View all results")).findFirst().orElseThrow();
                    all.fire();
                    assertEquals(query, fixture.fullQuery);
                });
            }
        } finally { fixture.close(); }
    }

    private Fixture create(RecentSearchHistory history) {
        return JavaFxTestSupport.runAndWait(() -> new Fixture(history));
    }

    private static class Fixture {
        final AppState state = new AppState();
        final Stage stage = new Stage();
        final TextField field = new TextField();
        final Button other = new Button("Outside");
        final CountDownLatch full = new CountDownLatch(1), opened = new CountDownLatch(1);
        final UniversalSearchPopup popup;
        volatile String fullQuery;
        volatile long openedId;
        volatile int fullCount;
        private Window popupWindow;

        Fixture(RecentSearchHistory history) {
            state.setUserId(11); state.setShaleClientId(7);
            stage.setScene(new Scene(new VBox(field, other), 600, 400));
            ThemeManager.application().register(stage.getScene());
            popup = new UniversalSearchPopup(field, state, new UniversalSearchPopup.Source() {
                public SearchService.Suggestions suggest(int tenant, String query, boolean deleted, BooleanSupplier current) {
                    assertFalse(javafx.application.Platform.isFxApplicationThread(), "Search must run off the JavaFX thread");
                    if (query.equals("none")) return new SearchService.Suggestions(List.of(), false);
                    if (query.equals("fail")) throw new IllegalStateException("Provider offline");
                    return new SearchService.Suggestions(List.of(new Suggestion(SuggestionType.CASE, 42, "Example", "C-42", 0)), false);
                }
                public boolean available(int tenant, String query, Suggestion row, boolean deleted) {
                    assertFalse(javafx.application.Platform.isFxApplicationThread(), "Availability reload must run off the JavaFX thread");
                    return true;
                }
            }, history, () -> false, query -> { fullQuery = query; fullCount++; full.countDown(); },
                    row -> { openedId = row.id(); opened.countDown(); });
            field.setOnAction(event -> popup.submitFullSearch());
            stage.show();
            field.requestFocus();
        }

        Window window() {
            if (popupWindow == null) popupWindow = Window.getWindows().stream()
                    .filter(w -> w != stage && w.getScene() != null
                            && w.getScene().getRoot().lookup(".universal-search-popup") != null)
                    .findFirst().orElseThrow();
            return popupWindow;
        }

        Button choice() { return (Button) window().getScene().getRoot().lookup(".universal-search-choice"); }
        void awaitChoices(int count) throws Exception {
            await(() -> JavaFxTestSupport.runAndWait(() -> {
                window().getScene().getRoot().applyCss();
                return window().getScene().getRoot().lookupAll(".universal-search-choice").size() >= count;
            }));
        }
        void close() {
            JavaFxTestSupport.runAndWait(() -> {
                popup.close();
                ThemeManager.application().unregister(stage.getScene());
                ThemeManager.application().setActiveTheme(Theme.LIGHT);
                stage.close();
            });
        }
    }

    private static void key(TextField field, KeyCode code) {
        field.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false));
    }
    private static MouseEvent mouse(javafx.event.EventType<MouseEvent> type) {
        return new MouseEvent(type, 1, 1, 1, 1, MouseButton.PRIMARY, 1, false, false, false, false,
                true, false, false, false, false, true, null);
    }
    private static void await(BooleanSupplier condition) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < end) {
            try { if (condition.getAsBoolean()) return; } catch (java.util.NoSuchElementException pendingWindow) { }
            Thread.sleep(20);
        }
        fail("Search popup did not reach the expected input state within 5 seconds");
    }
}
