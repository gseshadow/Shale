package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import com.shale.data.dao.CaseDao;
import com.shale.ui.component.CaseStatusSelector;
import com.shale.ui.component.UserSelectionField;
import com.shale.ui.component.factory.StatusCardFactory;
import com.shale.ui.component.factory.StatusCardFactory.StatusCardModel;
import com.shale.ui.testutil.JavaFxTestSupport;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.stage.Window;

class CaseOverviewCaseStatusEditorTest {
    private static final CaseDao.StatusRow CURRENT = new CaseDao.StatusRow(1, "Current", 0, "#ffffff", "intake", "intake", true, false);
    private static final CaseDao.StatusRow NEXT = new CaseDao.StatusRow(2, "Next", 1, "#123456", "accepted", "accepted", true, false);
    @BeforeAll static void startToolkit() { JavaFxTestSupport.ensureToolkitStarted(); }

    @Test void changeStagesTypedValueAndOuterCancelDiscardsIt() {
        JavaFxTestSupport.runAndWait(() -> {
            scheduleChange(false);
            assertTrue(openEditor().isEmpty(), "outer Cancel must not return a staged status mutation");
        });
    }

    @Test void changeThenSaveReturnsExactChosenRow() {
        JavaFxTestSupport.runAndWait(() -> {
            scheduleChange(true);
            assertSame(NEXT, openEditor().orElseThrow());
        });
    }

    private static void scheduleChange(boolean save) {
        Platform.runLater(() -> {
            DialogPane outer = pane(".case-status-edit-dialog");
            @SuppressWarnings("unchecked")
            var field = (UserSelectionField<CaseDao.StatusRow>) outer.lookup(".user-selection-field");
            assertSame(CURRENT, field.getSelectedUser());
            assertFalse(field.isDisabled(), "the active Change field must not inherit read-only/disabled styling");
            Platform.runLater(() -> {
                var picker = (CaseStatusSelector<?>) pane(".case-status-selector").lookup(".case-status-selector");
                ((Button) picker.getChildren().get(1)).fire();
            });
            ((Button) field.getChildren().get(1)).fire();
            assertSame(NEXT, field.getSelectedUser(), "Change must stage the exact picked row");
            var buttonType = save ? outer.getButtonTypes().stream().filter(type -> type.getText().equals("Save"))
                    .findFirst().orElseThrow() : ButtonType.CANCEL;
            Button button = (Button) outer.lookupButton(buttonType);
            assertFalse(button.isDisabled());
            assertTrue(button.getStyleClass().contains(save ? "shale-control-primary" : "shale-control-secondary"));
            button.fire();
        });
    }

    @SuppressWarnings("unchecked")
    private static Optional<CaseDao.StatusRow> openEditor() throws Exception {
        var controller = new CaseController();
        var cards = new StatusCardFactory(ignored -> { });
        Function<CaseDao.StatusRow, Node> renderer = value -> cards.create(
                new StatusCardModel(value.id(), value.name(), value.sortOrder(), value.color()), StatusCardFactory.Variant.MINI);
        var options = List.of(CURRENT, NEXT);
        BiFunction<Window, UserSelectionField<CaseDao.StatusRow>, Optional<CaseDao.StatusRow>> picker =
                (owner, field) -> CaseStatusSelector.showPicker(owner, options, field.getSelectedUser(),
                        CaseDao.StatusRow::id, CaseDao.StatusRow::name, CaseDao.StatusRow::color);
        var method = CaseController.class.getDeclaredMethod("showCardChoiceFieldDialog", String.class,
                String.class, Object.class, List.class, Function.class, Function.class, boolean.class,
                Runnable.class, Button.class, BiFunction.class);
        method.setAccessible(true);
        return (Optional<CaseDao.StatusRow>) method.invoke(controller, "Edit Case Status", "Case Status",
                CURRENT, options, (Function<CaseDao.StatusRow, Integer>) CaseDao.StatusRow::id,
                renderer, false, null, null, picker);
    }

    private static DialogPane pane(String selector) {
        return Window.getWindows().stream().filter(Window::isShowing).map(Window::getScene)
                .filter(java.util.Objects::nonNull).map(scene -> scene.getRoot())
                .filter(DialogPane.class::isInstance).map(DialogPane.class::cast)
                .filter(pane -> pane.lookup(selector) != null).findFirst().orElseThrow();
    }
}
