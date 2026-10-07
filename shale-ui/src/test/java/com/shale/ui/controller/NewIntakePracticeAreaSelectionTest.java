package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import com.shale.data.dao.CaseDao;
import com.shale.ui.testutil.JavaFxTestSupport;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

class NewIntakePracticeAreaSelectionTest {
    @BeforeAll static void startToolkit() { JavaFxTestSupport.ensureToolkitStarted(); }

    @Test void typedCardSelectionPropagatesToExistingModelAndRequiredValidation() {
        JavaFxTestSupport.runAndWait(() -> {
            var controller = new NewIntakeController();
            var host = new StackPane();
            var validation = new Label("Practice Area is required.");
            field(controller, "practiceAreaHost").set(controller, host);
            field(controller, "validationLabel").set(controller, validation);
            var selected = new CaseDao.PracticeAreaRow(42, "Personal Injury", "#abcdef", "personal_injury");
            assertEquals(List.of("Practice Area is required."), errors(controller, List.of(selected)));

            controller.applyPracticeAreaSelection(selected);

            assertSame(selected, field(controller, "selectedPracticeArea").get(controller));
            assertTrue(host.getChildren().getFirst().getStyleClass().contains("shale-card-selected"));
            assertFalse(validation.isVisible());
            assertTrue(errors(controller, List.of(selected)).isEmpty());
            assertEquals(List.of("Practice Area is required."), errors(controller,
                    List.of(new CaseDao.PracticeAreaRow(43, selected.name(), selected.color(), null))),
                    "validation must use tenant candidate IDs rather than display names");
        });
    }

    private static Field field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field;
    }
    @SuppressWarnings("unchecked")
    private static List<String> errors(NewIntakeController controller, List<CaseDao.PracticeAreaRow> rows) throws Exception {
        Class<?> state = Class.forName(NewIntakeController.class.getName() + "$PracticeAreaValidationResult");
        var constructor = state.getDeclaredConstructor(List.class, boolean.class); constructor.setAccessible(true);
        var validate = NewIntakeController.class.getDeclaredMethod("validatePracticeAreaSelection", state);
        validate.setAccessible(true);
        return (List<String>) validate.invoke(controller, constructor.newInstance(rows, false));
    }
}
