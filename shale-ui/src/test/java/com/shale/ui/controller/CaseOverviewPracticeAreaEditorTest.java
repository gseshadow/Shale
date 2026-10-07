package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import com.shale.data.dao.CaseDao;
import com.shale.ui.component.PracticeAreaSelector;
import com.shale.ui.component.UserSelectionField;
import com.shale.ui.component.factory.PracticeAreaCardFactory;
import com.shale.ui.component.factory.PracticeAreaCardFactory.PracticeAreaCardModel;
import com.shale.ui.testutil.JavaFxTestSupport;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.stage.Window;

class CaseOverviewPracticeAreaEditorTest {
    private static final CaseDao.PracticeAreaRow CURRENT = new CaseDao.PracticeAreaRow(1, "Current", "#ffffff", null);
    private static final CaseDao.PracticeAreaRow NEXT = new CaseDao.PracticeAreaRow(2, "Next", "#123456", null);
    @BeforeAll static void startToolkit() { JavaFxTestSupport.ensureToolkitStarted(); }

    @Test void changeStagesTypedValueAndOuterCancelDiscardsIt() {
        JavaFxTestSupport.runAndWait(() -> {
            scheduleChange(false);
            assertTrue(openEditor().isEmpty(), "outer Cancel must not return a staged practice-area mutation");
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
            DialogPane outer = pane(".practice-area-edit-dialog");
            @SuppressWarnings("unchecked")
            var field = (UserSelectionField<CaseDao.PracticeAreaRow>) outer.lookup(".user-selection-field");
            assertSame(CURRENT, field.getSelectedUser());
            assertFalse(field.isDisabled(), "the active Change field must not inherit read-only/disabled styling");
            Platform.runLater(() -> {
                var picker = (PracticeAreaSelector<?>) pane(".practice-area-selector").lookup(".practice-area-selector");
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
    private static Optional<CaseDao.PracticeAreaRow> openEditor() throws Exception {
        var controller = new CaseController();
        var cards = new PracticeAreaCardFactory(ignored -> { });
        Function<CaseDao.PracticeAreaRow, Node> renderer = value -> cards.create(
                new PracticeAreaCardModel(value.id(), value.name(), value.color()), PracticeAreaCardFactory.Variant.MINI);
        var options = List.of(CURRENT, NEXT);
        BiFunction<Window, UserSelectionField<CaseDao.PracticeAreaRow>, Optional<CaseDao.PracticeAreaRow>> picker =
                (owner, field) -> PracticeAreaSelector.showPicker(owner, options, field.getSelectedUser(),
                        CaseDao.PracticeAreaRow::id, CaseDao.PracticeAreaRow::name, CaseDao.PracticeAreaRow::color);
        var method = CaseController.class.getDeclaredMethod("showCardChoiceFieldDialog", String.class,
                String.class, Object.class, List.class, Function.class, Function.class, boolean.class,
                Runnable.class, Button.class, BiFunction.class);
        method.setAccessible(true);
        return (Optional<CaseDao.PracticeAreaRow>) method.invoke(controller, "Edit Practice Area", "Practice Area",
                CURRENT, options, (Function<CaseDao.PracticeAreaRow, Integer>) CaseDao.PracticeAreaRow::id,
                renderer, false, null, null, picker);
    }

    private static DialogPane pane(String selector) {
        return Window.getWindows().stream().filter(Window::isShowing).map(Window::getScene)
                .filter(java.util.Objects::nonNull).map(scene -> scene.getRoot())
                .filter(DialogPane.class::isInstance).map(DialogPane.class::cast)
                .filter(pane -> pane.lookup(selector) != null).findFirst().orElseThrow();
    }
}
