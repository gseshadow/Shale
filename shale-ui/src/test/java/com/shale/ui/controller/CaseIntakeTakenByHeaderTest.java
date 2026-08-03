package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import com.shale.ui.component.UserCard;
import com.shale.ui.testutil.JavaFxTestSupport;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

import org.junit.jupiter.api.Test;

final class CaseIntakeTakenByHeaderTest {
    @Test
    void intakeUserAppearsBetweenCaseNumberAndStatusAndMetadataCanWrap() throws Exception {
        String fxml = Files.readString(Path.of("src/main/resources/fxml/case.fxml"));
        int number = fxml.indexOf("fx:id=\"caseMetadataLabel\"");
        int intake = fxml.indexOf("fx:id=\"intakeTakenByUserHost\"");
        int status = fxml.indexOf("fx:id=\"statusHost\"");

        assertTrue(fxml.contains("<FlowPane hgap=\"8.0\" vgap=\"4.0\""));
        assertTrue(number >= 0 && number < intake && intake < status);
        assertTrue(fxml.contains("text=\"Intake by:\""));
        assertFalse(fxml.contains("fx:id=\"intakeTakenByLabel\""));
    }

    @Test
    void detailRenderingResetsNameForEveryCaseAndUsesNullFallback() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/controller/CaseController.java"));
        assertTrue(source.contains("refreshIntakeTakenBy(detail.getIntakeTakenByUserId(), detail.getIntakeTakenByDisplayName())"));
        assertTrue(source.contains("intakeTakenByUserHost.getChildren().setAll(createHeaderUserMini(userId, displayName, null))"));
        assertTrue(source.contains("userCardFactory.create(model, Variant.MINI)"));
        assertTrue(source.contains("renderResponsibleAttorneyMini"));
    }

    @Test
    void intakeUserUsesTheSameInteractiveMiniCardAsTheResponsibleAttorney() {
        JavaFxTestSupport.runAndWait(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/case.fxml"));
                Parent root = loader.load();
                CaseController controller = loader.getController();
                AtomicInteger openedUser = new AtomicInteger();
                controller.setOnOpenUser(openedUser::set);

                renderIntake(controller, 42, "Alex Morgan");
                StackPane intakeHost = field(controller, "intakeTakenByUserHost", StackPane.class);
                UserCard intakeCard = assertInstanceOf(UserCard.class, intakeHost.getChildren().getFirst());
                assertFalse(intakeCard.getStyle().isBlank(), "The shared MINI card surface must remain visible.");
                assertEquals("Alex Morgan", assertInstanceOf(Label.class, intakeCard.getChildren().getFirst()).getText());
                intakeCard.getOnMouseClicked().handle(null);
                assertEquals(42, openedUser.get());

                renderIntake(controller, null, null);
                UserCard fallback = assertInstanceOf(UserCard.class, intakeHost.getChildren().getFirst());
                assertEquals("—", assertInstanceOf(Label.class, fallback.getChildren().getFirst()).getText());
                assertEquals(1, intakeHost.getChildren().size());
                assertTrue(root.lookup("#intakeTakenByUserHost") == intakeHost);
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        });
    }

    private static void renderIntake(CaseController controller, Integer userId, String displayName) throws Exception {
        Method method = CaseController.class.getDeclaredMethod("refreshIntakeTakenBy", Integer.class, String.class);
        method.setAccessible(true);
        method.invoke(controller, userId, displayName);
    }

    private static <T> T field(Object owner, String name, Class<T> type) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return type.cast(field.get(owner));
    }
}
