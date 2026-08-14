package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.shale.data.dao.CaseDao;
import com.shale.ui.testutil.JavaFxTestSupport;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.stage.Window;

class CaseOverviewMiniCardPickerCancellationTest {
    @BeforeAll
    static void startJavaFx() {
        JavaFxTestSupport.ensureToolkitStarted();
    }

    @Test
    void cancelButtonReturnsEmptyForEveryOverviewOptionTypeAndCanReopenRepeatedly() {
        JavaFxTestSupport.runAndWait(() -> {
            assertRepeatedCancel(new CaseDao.PracticeAreaRow(1, "Area", "#123456", "area"));
            assertRepeatedCancel(new CaseDao.StatusRow(2, "Open", 1, "#654321", "open", "open", true, false));
            assertRepeatedCancel(new CaseDao.UserRow(3, "Same Name", "#abcdef"));
            assertRepeatedCancel(new CaseDao.UserRow(4, "Same Name", "#fedcba"));
        });
    }

    @Test
    void windowCloseAndEscapeEquivalentDismissalReturnEmpty() {
        JavaFxTestSupport.runAndWait(() -> {
            CaseDao.PracticeAreaRow item = new CaseDao.PracticeAreaRow(1, "Area", null, null);
            scheduleDialogDismissal(false);
            assertTrue(open(List.of(item)).isEmpty());
            scheduleDialogDismissal(true);
            assertTrue(open(List.of(item)).isEmpty());
        });
    }

    @Test
    void rowActivationReturnsExactTypedItemAndDuplicateNamesRemainDistinct() {
        JavaFxTestSupport.runAndWait(() -> {
            CaseDao.UserRow first = new CaseDao.UserRow(10, "Duplicate", null);
            CaseDao.UserRow second = new CaseDao.UserRow(11, "Duplicate", null);
            Platform.runLater(() -> pickerRows().get(1).fire());
            Optional<CaseDao.UserRow> result = open(List.of(first, second));
            assertSame(second, result.orElseThrow());
            assertEquals(11, result.orElseThrow().id());
        });
    }

    private static <T> void assertRepeatedCancel(T item) {
        for (int cycle = 0; cycle < 3; cycle++) {
            scheduleCancelButton();
            Optional<T> result = open(List.of(item));
            assertTrue(result.isEmpty());
        }
    }

    private static <T> Optional<T> open(List<T> options) {
        AtomicInteger id = new AtomicInteger();
        return CaseController.showMiniCardPicker(null, "test", options,
                ignored -> id.incrementAndGet(), ignored -> new Label("MINI"));
    }

    private static void scheduleCancelButton() {
        Platform.runLater(() -> {
            DialogPane pane = openDialogPane();
            ((Button) pane.lookupButton(javafx.scene.control.ButtonType.CANCEL)).fire();
        });
    }

    private static void scheduleDialogDismissal(boolean escapeEquivalent) {
        Platform.runLater(() -> {
            Window window = openDialogPane().getScene().getWindow();
            if (escapeEquivalent) window.fireEvent(new javafx.scene.input.KeyEvent(
                    javafx.scene.input.KeyEvent.KEY_PRESSED, "", "", javafx.scene.input.KeyCode.ESCAPE,
                    false, false, false, false));
            else window.hide();
        });
    }

    private static List<Button> pickerRows() {
        return openDialogPane().lookupAll(".user-selector-card-button").stream()
                .filter(Button.class::isInstance).map(Button.class::cast).toList();
    }

    private static DialogPane openDialogPane() {
        return Window.getWindows().stream().filter(Window::isShowing)
                .map(Window::getScene).filter(java.util.Objects::nonNull)
                .map(scene -> scene.getRoot()).filter(DialogPane.class::isInstance)
                .map(DialogPane.class::cast).reduce((first, second) -> second).orElseThrow();
    }
}
