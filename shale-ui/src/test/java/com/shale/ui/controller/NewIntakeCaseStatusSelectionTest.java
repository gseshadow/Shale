package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import com.shale.data.dao.CaseDao;
import com.shale.ui.component.CaseStatusSelector;
import com.shale.ui.state.AppState;
import com.shale.ui.testutil.JavaFxTestSupport;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.DialogPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.Window;

class NewIntakeCaseStatusSelectionTest {
    private static final CaseDao.StatusRow CLOSED = status(1, "Closed", "closed");
    private static final CaseDao.StatusRow DEFAULT = status(2, "Duplicate", "intake");
    private static final CaseDao.StatusRow NEXT = status(3, "Duplicate", "accepted");
    @BeforeAll static void startToolkit() { JavaFxTestSupport.ensureToolkitStarted(); }

    @Test void defaultRemainsFirstNonterminalAndDoesNotOverwriteAnExistingChoice() {
        JavaFxTestSupport.runAndWait(() -> {
            var controller = fixture();
            controller.applyDefaultStatusIfAvailable(List.of(CLOSED, DEFAULT, NEXT));
            assertSame(DEFAULT, field(controller, "selectedStatus").get(controller));
            assertEquals(DEFAULT.id(), snapshotStatusId(controller));
            controller.applyStatusSelection(NEXT);
            controller.applyDefaultStatusIfAvailable(List.of(CLOSED, DEFAULT, NEXT));
            assertSame(NEXT, field(controller, "selectedStatus").get(controller),
                    "default initialization must never overwrite an explicit status choice");
        });
    }

    @Test void noNonterminalDefaultOrLoadFailureLeavesStatusRequired() {
        JavaFxTestSupport.runAndWait(() -> {
            var controller = fixture();
            controller.applyDefaultStatusIfAvailable(List.of(CLOSED));
            assertNull(field(controller, "selectedStatus").get(controller));
            assertTrue(requiredErrors(controller).contains("Status is required."));
            field(controller, "caseDao").set(controller, new CaseDao(() -> { throw new RuntimeException("unavailable"); }));
            invoke(controller, "preselectDefaultStatusIfAvailable");
            assertNull(field(controller, "selectedStatus").get(controller));
        });
    }

    @Test void cardPickerPropagatesTypedRowToModelSnapshotAndRequiredValidation() {
        JavaFxTestSupport.runAndWait(() -> {
            var controller = fixture();
            controller.applyDefaultStatusIfAvailable(List.of(DEFAULT, NEXT));
            Button change = (Button) field(controller, "selectStatusButton").get(controller);
            Stage owner = new Stage(); owner.setScene(change.getScene());
            owner.show();
            try {
                Platform.runLater(() -> {
                    var selector = (CaseStatusSelector<?>) pickerPane().lookup(".case-status-selector");
                    ((Button) selector.getChildren().get(1)).fire();
                });
                controller.selectStatusFromCandidates(List.of(DEFAULT, NEXT));
                assertSame(NEXT, field(controller, "selectedStatus").get(controller),
                        "same-named statuses must propagate the chosen DTO, not a name lookup");
                assertEquals(NEXT.id(), snapshotStatusId(controller));
                assertFalse(requiredErrors(controller).contains("Status is required."));
                StackPane host = (StackPane) field(controller, "statusHost").get(controller);
                assertTrue(host.getChildren().getFirst().getStyleClass().contains("shale-card-selected"));
                Platform.runLater(() -> ((Button) pickerPane().lookupButton(javafx.scene.control.ButtonType.CANCEL)).fire());
                controller.selectStatusFromCandidates(List.of(DEFAULT, NEXT));
                assertSame(NEXT, field(controller, "selectedStatus").get(controller), "Cancel must preserve status");
            } finally { owner.hide(); }
        });
    }

    private static NewIntakeController fixture() throws Exception {
        var loader = new FXMLLoader(NewIntakeCaseStatusSelectionTest.class.getResource("/fxml/new-intake.fxml"));
        javafx.scene.Parent root = loader.load(); new Scene(root); root.applyCss(); root.layout();
        NewIntakeController controller = loader.getController();
        var state = new AppState(); state.setShaleClientId(7);
        field(controller, "appState").set(controller, state);
        return controller;
    }
    private static CaseDao.StatusRow status(int id, String name, String lifecycle) {
        return new CaseDao.StatusRow(id, name, id, "#123456", lifecycle, lifecycle, true, false);
    }
    private static Field field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field;
    }
    private static Object invoke(Object target, String name) throws Exception {
        var method = target.getClass().getDeclaredMethod(name); method.setAccessible(true); return method.invoke(target);
    }
    private static Object snapshotStatusId(NewIntakeController controller) throws Exception {
        Object snapshot = invoke(controller, "captureCurrentSnapshot");
        return invoke(snapshot, "statusId");
    }
    @SuppressWarnings("unchecked")
    private static List<String> requiredErrors(NewIntakeController controller) throws Exception {
        return (List<String>) invoke(controller, "validateRequiredFields");
    }
    private static DialogPane pickerPane() {
        return Window.getWindows().stream().filter(Window::isShowing).map(Window::getScene)
                .filter(java.util.Objects::nonNull).map(Scene::getRoot).filter(DialogPane.class::isInstance)
                .map(DialogPane.class::cast).filter(pane -> pane.lookup(".case-status-selector") != null)
                .findFirst().orElseThrow();
    }
}
