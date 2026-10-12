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

    @Test void administratorCanOpenExistingManagerWithoutCommittingSelection() {
        JavaFxTestSupport.runAndWait(() -> {
            Platform.runLater(() -> {
                DialogPane outer = pane(".practice-area-edit-dialog");
                @SuppressWarnings("unchecked")
                var field = (UserSelectionField<CaseDao.PracticeAreaRow>) outer.lookup(".user-selection-field");
                field.setSelectedUser(NEXT);
                Button manage = (Button) outer.lookup("#managePracticeAreasButton");
                assertNotNull(manage, "Management belongs inside the field editor");
                assertTrue(manage.isVisible());
                assertTrue(manage.getStyleClass().contains("shale-control-secondary"));
                assertTrue(manage.getStyleClass().contains("shale-control-small"));
                manage.fire();
                DialogPane manager = pane(".management-window");
                assertSame(outer.getScene().getWindow(), ((javafx.stage.Stage) manager.getScene().getWindow()).getOwner(),
                        "Existing management window must be owned by the editor");
                ((Button) manager.lookupButton(ButtonType.CLOSE)).fire();
                assertTrue(outer.getScene().getWindow().isShowing(), "Closing management must leave selection staged");
                assertSame(NEXT, field.getSelectedUser(), "Definition management must not discard or save the staged choice");
                ((Button) outer.lookupButton(ButtonType.CANCEL)).fire();
            });
            assertTrue(openEditor(true).isEmpty());
        });
    }

    private static void scheduleChange(boolean save) {
        Platform.runLater(() -> {
            DialogPane outer = pane(".practice-area-edit-dialog");
            @SuppressWarnings("unchecked")
            var field = (UserSelectionField<CaseDao.PracticeAreaRow>) outer.lookup(".user-selection-field");
            assertSame(CURRENT, field.getSelectedUser());
            Button manage = (Button) outer.lookup("#managePracticeAreasButton");
            assertNotNull(manage);
            assertFalse(manage.isVisible(), "Ordinary users must not gain definition management");
            assertFalse(manage.isManaged());
            assertFalse(manage.isFocusTraversable());
            assertNull(manage.getOnAction());
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

    private static Optional<CaseDao.PracticeAreaRow> openEditor() throws Exception {
        return openEditor(false);
    }

    @SuppressWarnings("unchecked")
    private static Optional<CaseDao.PracticeAreaRow> openEditor(boolean administrator) throws Exception {
        var controller = new CaseController();
        var state = new com.shale.ui.state.AppState();
        state.setAdmin(administrator); state.setShaleClientId(7); state.setUserId(11);
        com.shale.core.service.CaseServicePort service = (com.shale.core.service.CaseServicePort) java.lang.reflect.Proxy.newProxyInstance(
                CaseOverviewPracticeAreaEditorTest.class.getClassLoader(), new Class<?>[] { com.shale.core.service.CaseServicePort.class },
                (proxy, method, args) -> method.getReturnType() == List.class ? List.of() : null);
        set(controller, "appState", state); set(controller, "caseId", 42); set(controller, "caseService", service);
        set(controller, "practiceAreaManagementLauncher",
                new PracticeAreaManagementLauncher(service, task -> { Thread worker = new Thread(task); worker.setDaemon(true); worker.start(); }));
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

    private static void set(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }

    private static DialogPane pane(String selector) {
        return Window.getWindows().stream().filter(Window::isShowing).map(Window::getScene)
                .filter(java.util.Objects::nonNull).map(scene -> scene.getRoot())
                .filter(DialogPane.class::isInstance).map(DialogPane.class::cast)
                .filter(pane -> pane.lookup(selector) != null).findFirst().orElseThrow();
    }
}
