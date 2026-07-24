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
import javafx.geometry.Insets;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
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

    @Test
    void cardUsesComputedContentHeightAndNormalGapBeforeDates() throws Exception {
        RenderedMaterialRequestCard rendered = render(summary(LocalDateTime.of(2026, 8, 22, 0, 0)));
        try {
            VBox entityFacts = (VBox) rendered.card().lookup(".material-request-card__entity-facts");
            GridPane dates = findFirst(rendered.card(), GridPane.class);
            assertNotNull(entityFacts);
            assertNotNull(dates);
            assertEquals(Priority.NEVER, VBox.getVgrow(rendered.userMiniCard()),
                    "MINI cards should not grow vertically to push date facts down.");
            assertNull(VBox.getVgrow(entityFacts), "Entity section must keep natural height.");
            assertNull(VBox.getVgrow(dates), "Date row must not be bottom-anchored by VBox grow.");
            assertEquals(Region.USE_PREF_SIZE, rendered.card().getMaxHeight(), 0.1,
                    "The card should refuse parent-provided spare height and use its computed content height.");

            double actualGap = dates.getBoundsInParent().getMinY() - entityFacts.getBoundsInParent().getMaxY();
            assertEquals(7.0, actualGap, 1.0,
                    "Date facts should follow the last entity section with the normal card body spacing.");
            assertTrue(dates.getBoundsInParent().getMaxY() <= rendered.body().getHeight() - rendered.body().getPadding().getBottom() + 0.5,
                    "Date row remains fully visible inside modest bottom padding.");
            assertTrue(rendered.card().getHeight() < rendered.stage().getScene().getHeight() - 80,
                    "A short request should not stretch to fill the available scene height.");
            assertEquals(rendered.card().getHeight(), rendered.card().getClip().getBoundsInLocal().getHeight(), 0.5,
                    "Rounded clip height tracks the final computed card height.");
        } finally {
            runFxAndWait(rendered.stage()::close);
        }
    }

    @Test
    void wrappedTitleGrowsOnlyItsOwnCardAndDoesNotResizeSiblingCards() throws Exception {
        AtomicReference<RenderedList> ref = new AtomicReference<>();
        runFxAndWait(() -> {
            HBox shortCard = (HBox) new MaterialRequestCardFactory(id -> { }).create(summary(LocalDateTime.of(2026, 8, 22, 0, 0)), MaterialRequestCardFactory.Variant.LIST);
            HBox wrappedCard = (HBox) new MaterialRequestCardFactory(id -> { }).create(summaryWithTitle("A very long material request title that should wrap onto multiple lines at narrower widths while preserving every field and growing naturally"), MaterialRequestCardFactory.Variant.LIST);
            VBox list = new VBox(10, shortCard, wrappedCard);
            list.setPadding(new Insets(8));
            list.setFillWidth(true);
            StackPane root = new StackPane(list);
            Scene scene = new Scene(root, 360, 520);
            scene.getStylesheets().add(MaterialRequestCardFactoryRenderingTest.class.getResource("/css/app.css").toExternalForm());
            Stage stage = new Stage();
            stage.setScene(scene);
            stage.show();
            root.applyCss();
            root.layout();
            ref.set(new RenderedList(stage, list, shortCard, wrappedCard));
        });
        RenderedList rendered = ref.get();
        try {
            assertTrue(rendered.second().getHeight() > rendered.first().getHeight(),
                    "Wrapped title should grow its own card naturally.");
            assertTrue(rendered.first().getHeight() < rendered.second().getHeight() - 8,
                    "Sibling cards must not inherit the tallest card height.");
        } finally {
            runFxAndWait(rendered.stage()::close);
        }
    }


    @Test
    void listInsetsExposeParentAroundMultipleRoundedCardsAndPreserveResponsiveWidth() throws Exception {
        AtomicReference<RenderedList> ref = new AtomicReference<>();
        runFxAndWait(() -> {
            HBox first = (HBox) new MaterialRequestCardFactory(id -> { }).create(summary(LocalDateTime.of(2026, 8, 22, 0, 0)), MaterialRequestCardFactory.Variant.LIST);
            HBox second = (HBox) new MaterialRequestCardFactory(id -> { }).create(summary(LocalDateTime.of(2026, 7, 25, 12, 0)), MaterialRequestCardFactory.Variant.LIST);
            VBox list = new VBox(10, first, second);
            list.setPadding(new Insets(8));
            list.setFillWidth(true);
            list.setStyle("-fx-background-color: #D9E2EC;");
            StackPane root = new StackPane(list);
            root.setStyle("-fx-background-color: #001122;");
            Scene scene = new Scene(root, 520, 420);
            scene.getStylesheets().add(MaterialRequestCardFactoryRenderingTest.class.getResource("/css/app.css").toExternalForm());
            Stage stage = new Stage();
            stage.setScene(scene);
            stage.show();
            root.applyCss();
            root.layout();
            ref.set(new RenderedList(stage, list, first, second));
        });
        RenderedList rendered = ref.get();
        try {
            Insets padding = rendered.list().getPadding();
            assertEquals(8.0, padding.getTop(), 0.1);
            assertEquals(8.0, padding.getRight(), 0.1);
            assertEquals(8.0, padding.getBottom(), 0.1);
            assertEquals(8.0, padding.getLeft(), 0.1);
            assertEquals(10.0, rendered.second().getBoundsInParent().getMinY() - rendered.first().getBoundsInParent().getMaxY(), 1.0,
                    "Only VBox spacing should separate multiple request cards, avoiding doubled margins.");
            assertEquals(rendered.list().getWidth() - padding.getLeft() - padding.getRight(), rendered.first().getWidth(), 1.0,
                    "Card should resize to the container width minus the intended external insets.");
            assertTrue(rendered.first().getBoundsInParent().getMinX() >= padding.getLeft() - 0.1);
            assertTrue(rendered.first().getBoundsInParent().getMinY() >= padding.getTop() - 0.1);
            assertSame(rendered.first().getParent(), rendered.list(), "No wrapper should be inserted between the list and the request card surface.");
            assertNotNull(rendered.first().getClip(), "Rounded card clip remains on the only card surface.");
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

    private static <T> T findFirst(Node root, Class<T> type) {
        if (type.isInstance(root)) return type.cast(root);
        if (root instanceof javafx.scene.Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                T found = findFirst(child, type);
                if (found != null) return found;
            }
        }
        return null;
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

    private static MaterialRequestSummaryDto summaryWithTitle(String title) {
        return new MaterialRequestSummaryDto(
                1L, 10, 6502L, 3, "Medical records", null, "#2F80ED", title,
                11, "Brian Downing", "#7C3AED", 11, "Brian Downing", "#7C3AED",
                null, null, 22, "Blue Cross Blue Shield", null, "Portal", LocalDateTime.of(2026, 7, 23, 9, 0),
                "REQUESTED", LocalDateTime.of(2026, 8, 22, 0, 0), LocalDateTime.of(2026, 7, 30, 9, 0), null, LocalDateTime.of(2026, 7, 23, 9, 0),
                new byte[]{1});
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
    private record RenderedList(Stage stage, VBox list, HBox first, HBox second) {}
}
