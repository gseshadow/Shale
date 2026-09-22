package com.shale.ui.component.dialog;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.shale.ui.testutil.JavaFxTestSupport;

import javafx.scene.control.Button;
import javafx.scene.layout.FlowPane;

final class AppDialogsPhase8JTest {
    @Test void destructiveActionIsNotTheDefaultAndCancelRemainsSafe() {
        JavaFxTestSupport.runAndWait(() -> {
            FlowPane row = AppDialogs.createActionsRow(List.of(
                    AppDialogs.DialogAction.cancel("Cancel", false),
                    AppDialogs.DialogAction.of("Delete", true, AppDialogs.DialogActionKind.DANGER, false, false)),
                    ignored -> { });
            Button cancel = (Button) row.getChildren().get(0);
            Button delete = (Button) row.getChildren().get(1);
            assertTrue(cancel.isCancelButton(), "Escape must select cancellation");
            assertFalse(delete.isDefaultButton(), "Enter must not accidentally select a destructive action");
            assertTrue(delete.getStyleClass().contains("shale-control-danger"));
        });
    }

    @Test void dirtyChoiceUsesExactLabelsAndInvokesOneResultBoundary() {
        JavaFxTestSupport.runAndWait(() -> {
            AtomicInteger calls = new AtomicInteger();
            FlowPane row = AppDialogs.createActionsRow(List.of(
                    AppDialogs.DialogAction.of("Keep Editing", false, AppDialogs.DialogActionKind.SECONDARY, true, true),
                    AppDialogs.DialogAction.of("Discard", true, AppDialogs.DialogActionKind.DANGER, false, false)),
                    ignored -> calls.incrementAndGet());
            Button keep = (Button) row.getChildren().get(0);
            Button discard = (Button) row.getChildren().get(1);
            assertEquals("Keep Editing", keep.getText());
            assertEquals("Discard", discard.getText());
            assertTrue(keep.isDefaultButton());
            assertTrue(keep.isCancelButton());
            discard.fire();
            assertEquals(1, calls.get(), "one activation must cross the result boundary once");
        });
    }
}
