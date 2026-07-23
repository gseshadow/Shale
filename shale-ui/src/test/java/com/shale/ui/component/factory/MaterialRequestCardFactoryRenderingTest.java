package com.shale.ui.component.factory;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.shale.core.dto.MaterialRequestSummaryDto;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

final class MaterialRequestCardFactoryRenderingTest {
    private static final AtomicBoolean TOOLKIT_STARTED = new AtomicBoolean();

    @BeforeAll
    static void startJavaFxToolkit() throws Exception {
        assumeTrue(hasDisplay(), "Material Request rendered card test requires a graphical display.");
        if (TOOLKIT_STARTED.compareAndSet(false, true)) {
            CountDownLatch latch = new CountDownLatch(1);
            Platform.startup(() -> {
                Platform.setImplicitExit(false);
                latch.countDown();
            });
            assertTrue(latch.await(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void renderedCardShowsMaterialRailAndDueGradientThroughTransparentBody() throws Exception {
        RenderedMaterialRequestCard rendered = render(summary(LocalDateTime.of(2026, 7, 25, 12, 0)));
        try {
            assertTrue(rendered.card().getStyle().contains("linear-gradient(to right"), rendered.card().getStyle());
            assertTrue(rendered.card().getStyle().contains(DueProximityStyles.DUE_WITHIN_ONE_WEEK_COLOR), rendered.card().getStyle());
            assertFalse(rendered.card().getStyle().contains("#2F80ED"), "Due gradient must not use the material type color.");

            assertTrue(rendered.rail().getStyle().contains("#2F80ED"), rendered.rail().getStyle());
            assertFalse(rendered.rail().getStyle().contains(DueProximityStyles.DUE_WITHIN_ONE_WEEK_COLOR),
                    "Material Type rail must not use due-proximity color.");
            assertEquals(7.0, rendered.rail().getPrefWidth(), 0.1);
            assertTrue(rendered.rail().getBoundsInParent().getMaxX() <= rendered.body().getBoundsInParent().getMinX() + 0.1,
                    "Body must start after the rail rather than covering it.");

            assertTrue(rendered.body().getStyle().contains("-fx-background-color: transparent"), rendered.body().getStyle());
            assertNotNull(rendered.card().getClip(), "The outer painted card should own the rounded clip.");
            assertTrue(rendered.userMiniCard().getStyle().contains("#7C3AED"), rendered.userMiniCard().getStyle());
            assertTrue(rendered.userMiniCard().getWidth() < rendered.card().getWidth() * 0.75,
                    "MINI user card should remain compact inside the wider request card.");
        } finally {
            runFxAndWait(rendered.stage()::close);
        }
    }

    @Test
    void distantDueDateStillRendersNeutralGradientInsteadOfFlatWhite() throws Exception {
        RenderedMaterialRequestCard rendered = render(summary(LocalDateTime.of(2026, 8, 22, 0, 0)));
        try {
            assertTrue(rendered.card().getStyle().contains("linear-gradient(to right"), rendered.card().getStyle());
            assertTrue(rendered.card().getStyle().contains("rgba(203,213,225"), rendered.card().getStyle());
            assertTrue(rendered.rail().getStyle().contains("#2F80ED"), rendered.rail().getStyle());
        } finally {
            runFxAndWait(rendered.stage()::close);
        }
    }

    private static RenderedMaterialRequestCard render(MaterialRequestSummaryDto summary) throws Exception {
        AtomicReference<RenderedMaterialRequestCard> ref = new AtomicReference<>();
        runFxAndWait(() -> {
            Node cardNode = new MaterialRequestCardFactory(id -> { }).create(summary, MaterialRequestCardFactory.Variant.LIST);
            HBox card = (HBox) cardNode;
            StackPane root = new StackPane(card);
            root.setStyle("-fx-background-color: #001122; -fx-padding: 24;");
            Scene scene = new Scene(root, 720, 360);
            scene.getStylesheets().add(MaterialRequestCardFactoryRenderingTest.class.getResource("/css/app.css").toExternalForm());
            Stage stage = new Stage();
            stage.setScene(scene);
            stage.show();
            root.applyCss();
            root.layout();
            Region rail = (Region) card.lookup(".material-request-card__material-type-rail");
            VBox body = (VBox) card.lookup(".material-request-card__body");
            Region userMiniCard = findFirstUserMiniCard(card);
            ref.set(new RenderedMaterialRequestCard(stage, card, rail, body, userMiniCard));
        });
        return ref.get();
    }

    private static Region findFirstUserMiniCard(Node root) {
        if (root instanceof com.shale.ui.component.UserCard userCard) return userCard;
        if (root instanceof javafx.scene.Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                Region found = findFirstUserMiniCard(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static MaterialRequestSummaryDto summary(LocalDateTime due) {
        return new MaterialRequestSummaryDto(
                1L, 10, 6502L, 3, "Medical records", null, "#2F80ED", "Test Medical Records Request",
                11, "Brian Downing", "#7C3AED", 11, "Brian Downing", "#7C3AED",
                null, null, 22, "Blue Cross Blue Shield", null, "Portal", LocalDateTime.of(2026, 7, 23, 9, 0),
                "REQUESTED", due, LocalDateTime.of(2026, 7, 30, 9, 0), null, LocalDateTime.of(2026, 7, 23, 9, 0),
                new byte[]{1});
    }

    private static void runFxAndWait(Runnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    private static boolean hasDisplay() {
        String display = System.getenv("DISPLAY");
        String wayland = System.getenv("WAYLAND_DISPLAY");
        return (display != null && !display.isBlank()) || (wayland != null && !wayland.isBlank());
    }

    private record RenderedMaterialRequestCard(Stage stage, HBox card, Region rail, VBox body, Region userMiniCard) {}
}
