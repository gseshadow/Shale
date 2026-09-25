package com.shale.ui.component;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.shale.ui.component.factory.TaskCardFactory;
import com.shale.ui.testutil.JavaFxTestSupport;
import com.shale.ui.theme.ThemeManager;

import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Advisory rendered-geometry coverage for User View's production task-card variant. */
class UserAssignedTaskCardLayoutTest {
    private static final double USER_TASKS_VIEWPORT_WIDTH = 356;
    private static final double EPSILON = 0.5;

    @Test
    void assignedTaskSurfaceContainsActualRelatedMetadataVisualBounds() {
        JavaFxTestSupport.runAndWait(() -> {
            Fixture fixture = fixture(8);
            try {
                assertTrue(fixture.scroll.lookupAll(".scroll-bar").stream()
                                .filter(ScrollBar.class::isInstance).map(ScrollBar.class::cast)
                                .anyMatch(bar -> bar.getOrientation() == javafx.geometry.Orientation.VERTICAL
                                        && bar.isVisible()),
                        "The production-width fixture must exercise a visible vertical scrollbar.");

                for (TaskCard card : fixture.cards) {
                    Node metadata = card.lookup(".app-taskcard-user-assigned-metadata");
                    Node relatedCase = card.lookup(".task-related-case-card");
                    Node attorney = card.lookup(".case-card__attorney-mini-card");
                    assertNotNull(metadata, "The rendered User Assigned Tasks metadata block must be present.");
                    assertNotNull(relatedCase, "The rendered related-case card must be present.");
                    assertNotNull(attorney, "The embedded attorney ContactCard must be present.");

                    // USER_ASSIGNED_TASKS deliberately uses the My Tasks composition, not the
                    // compact two-column row. These nodes must therefore not be in its scene graph.
                    assertNull(card.lookup(".app-taskcard-compact-meta-row"));
                    assertNull(card.lookup(".app-taskcard-compact-meta-section"));

                    assertContained(card, metadata, "metadata block");
                    assertContained(card, relatedCase, "related-case card");
                    assertContained(card, attorney, "attorney pill");
                }
            } finally {
                fixture.themes.unregister(fixture.scene);
            }
        });
    }

    @Test
    void consecutiveSurfacesDoNotOverlapDescendantVisualBoundsAtProductionWidth() {
        JavaFxTestSupport.runAndWait(() -> {
            Fixture fixture = fixture(10);
            try {
                double previousSurfaceBottom = Double.NEGATIVE_INFINITY;
                double previousVisualBottom = Double.NEGATIVE_INFINITY;
                for (TaskCard card : fixture.cards) {
                    Bounds surface = card.localToScene(card.getLayoutBounds());
                    assertTrue(surface.getMinY() + EPSILON >= previousSurfaceBottom,
                            "Consecutive TaskCard surfaces must not overlap.");
                    assertTrue(surface.getMinY() + EPSILON >= previousVisualBottom,
                            "A preceding card's descendant visual edge must not intrude into the next surface.");
                    assertTrue(card.getWidth() <= fixture.cardsBox.getWidth() + EPSILON,
                            "Long content must fit the viewport width left by the vertical scrollbar.");

                    previousSurfaceBottom = surface.getMaxY();
                    previousVisualBottom = visualBottom(card);
                    assertTrue(previousVisualBottom <= previousSurfaceBottom + EPSILON,
                            "The TaskCard background must extend through every descendant visual edge.");
                }
            } finally {
                fixture.themes.unregister(fixture.scene);
            }
        });
    }

    @Test
    void optionalRelatedMetadataMayBeAbsentWithoutReservingAPhantomRow() {
        JavaFxTestSupport.runAndWait(() -> {
            TaskCard card = card(99, false);
            StackPane root = new StackPane(card);
            Scene scene = new Scene(root, USER_TASKS_VIEWPORT_WIDTH, 240);
            ThemeManager themes = new ThemeManager();
            try {
                themes.register(scene);
                root.applyCss();
                root.layout();
                assertNull(card.lookup(".task-related-case-card"),
                        "A task without related metadata must not render an empty embedded case card.");
                assertNotNull(card.lookup(".app-taskcard-user-assigned-metadata"),
                        "The due/status metadata block must remain managed when optional case metadata is absent.");
                assertTrue(visualBottom(card) <= card.localToScene(card.getLayoutBounds()).getMaxY() + EPSILON,
                        "Visible required metadata must remain inside the surface when optional metadata is absent.");
            } finally {
                themes.unregister(scene);
            }
        });
    }

    private static Fixture fixture(int count) {
        VBox cardsBox = new VBox(10);
        List<TaskCard> cards = java.util.stream.LongStream.rangeClosed(1, count)
                .mapToObj(UserAssignedTaskCardLayoutTest::card).toList();
        cardsBox.getChildren().setAll(cards);
        cardsBox.setFillWidth(true);
        cardsBox.setMaxWidth(Double.MAX_VALUE);

        StackPane content = new StackPane(cardsBox);
        ScrollPane scroll = new ScrollPane(content);
        scroll.getStyleClass().add("surface-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        StackPane root = new StackPane(scroll);
        Scene scene = new Scene(root, USER_TASKS_VIEWPORT_WIDTH, 360);
        ThemeManager themes = new ThemeManager();
        themes.register(scene);
        root.applyCss();
        root.layout();
        // ScrollPane skins can resize the viewport/content during their first pulse.
        root.applyCss();
        root.layout();
        return new Fixture(scene, themes, scroll, cardsBox, cards);
    }

    private static TaskCard card(long id) {
        return card(id, true);
    }

    private static TaskCard card(long id, boolean withRelatedMetadata) {
        TaskCardFactory factory = new TaskCardFactory(task -> { }, task -> { }, caseId -> { }, userId -> { });
        return factory.create(new TaskCardFactory.TaskCardModel(
                id, withRelatedMetadata ? 42L : null,
                withRelatedMetadata
                        ? "A very long related case name that must ellipsize inside the narrow User View task column"
                        : null,
                "In progress", "#2563EB", "#14B8A6",
                withRelatedMetadata ? "An exceptionally long responsible attorney display name" : null,
                "#7C3AED", false,
                "A very long assigned task title that must ellipsize instead of widening the scrolling column " + id,
                "Optional description " + id, "A very long task creator display name",
                "Open", "#DBEAFE", "#F59E0B", LocalDateTime.now().plusDays(1), null,
                List.of(new TaskCardFactory.AssignedUserModel(7,
                        "A very long assigned user display name", "#7C3AED"))),
                TaskCardFactory.Variant.USER_ASSIGNED_TASKS, true);
    }

    private static void assertContained(TaskCard card, Node child, String description) {
        Bounds surfaceLayout = card.localToScene(card.getLayoutBounds());
        Bounds childLayout = child.localToScene(child.getLayoutBounds());
        Bounds childInParent = child.getBoundsInParent();
        Bounds childVisual = child.localToScene(child.getBoundsInLocal());
        assertTrue(childLayout.getMaxY() <= surfaceLayout.getMaxY() + EPSILON,
                description + " layoutBounds must remain inside the TaskCard surface; boundsInParent=" + childInParent);
        assertTrue(childVisual.getMaxY() <= surfaceLayout.getMaxY() + EPSILON,
                description + " visual/effect bounds must remain inside the TaskCard surface; boundsInParent=" + childInParent);
    }

    private static double visualBottom(Parent root) {
        return descendants(root).stream().filter(Node::isManaged).filter(Node::isVisible)
                .mapToDouble(node -> node.localToScene(node.getBoundsInLocal()).getMaxY())
                .max().orElse(root.localToScene(root.getLayoutBounds()).getMaxY());
    }

    private static List<Node> descendants(Parent root) {
        return root.getChildrenUnmodifiable().stream()
                .flatMap(child -> java.util.stream.Stream.concat(java.util.stream.Stream.of(child),
                        child instanceof Parent parent ? descendants(parent).stream() : java.util.stream.Stream.empty()))
                .toList();
    }

    private record Fixture(Scene scene, ThemeManager themes, ScrollPane scroll,
                           VBox cardsBox, List<TaskCard> cards) { }
}
