package com.shale.ui.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.shale.ui.component.factory.TaskCardFactory;
import com.shale.ui.testutil.JavaFxTestSupport;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

class UserAssignedTaskCardLayoutTest {

    @Test
    void assignedTaskVariantUsesComputedHeightWithAndWithoutOptionalMetadata() {
        JavaFxTestSupport.runAndWait(() -> {
            TaskCard withMetadata = card(1, true);
            TaskCard withoutMetadata = card(2, false);

            assertEquals(Region.USE_COMPUTED_SIZE, withMetadata.getPrefHeight(),
                    "Assigned-task cards must allow managed descendants to determine preferred height.");
            assertEquals(Region.USE_COMPUTED_SIZE, withMetadata.getMaxHeight(),
                    "Assigned-task cards must not cap the case/attorney row with a fixed maximum height.");
            assertTrue(withMetadata.prefHeight(304) > withoutMetadata.prefHeight(304),
                    "Visible related-case and attorney metadata must contribute to the card's computed height.");
            assertTrue(managedDescendantsFitPreferredHeight(withMetadata, 304),
                    "Every visible managed child must fit within the computed assigned-task card height.");
        });
    }

    @Test
    void narrowScrollPaneWithVerticalBarKeepsLongContentInsideConsecutiveCards() {
        JavaFxTestSupport.runAndWait(() -> {
            VBox cards = new VBox(10);
            for (int index = 0; index < 8; index++) cards.getChildren().add(card(index + 1, index % 2 == 0));
            ScrollPane scroll = new ScrollPane(new StackPane(cards));
            scroll.setFitToWidth(true);
            scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
            StackPane root = new StackPane(scroll);
            new Scene(root, 320, 360);
            root.applyCss();
            root.layout();

            double previousBottom = Double.NEGATIVE_INFINITY;
            for (Node node : cards.getChildren()) {
                TaskCard card = (TaskCard) node;
                assertTrue(card.getWidth() <= cards.getWidth() + 0.01,
                        "Long task/case/user names must shrink to the viewport left by the visible scrollbar.");
                assertTrue(card.getLayoutY() >= previousBottom,
                        "Consecutive assigned-task cards must not overlap.");
                assertTrue(managedDescendantsFitActualHeight(card),
                        "Related-case, attorney, and optional metadata must remain inside the laid-out card.");
                previousBottom = card.getLayoutY() + card.getHeight();
            }
        });
    }

    private static TaskCard card(long id, boolean withMetadata) {
        String longText = "A very long assigned task title that must ellipsize instead of widening the scrolling task column ";
        TaskCardFactory factory = new TaskCardFactory(task -> { }, task -> { }, caseId -> { }, userId -> { });
        return factory.create(new TaskCardFactory.TaskCardModel(
                id,
                withMetadata ? 42L : null,
                withMetadata ? "A very long related case name that must ellipsize within the available card width" : null,
                "In progress", "#2563EB", "#14B8A6",
                withMetadata ? "An exceptionally long responsible attorney display name" : null,
                "#7C3AED", false,
                longText + id, "Optional description " + id,
                withMetadata ? "A very long task creator display name" : null,
                "Open", "#DBEAFE", "#F59E0B", LocalDateTime.now().plusDays(1), null,
                withMetadata ? List.of(new TaskCardFactory.AssignedUserModel(7,
                        "A very long assigned user display name", "#7C3AED")) : List.of()),
                TaskCardFactory.Variant.USER_ASSIGNED_TASKS, true);
    }

    private static boolean managedDescendantsFitPreferredHeight(TaskCard card, double width) {
        card.resize(width, card.prefHeight(width));
        card.layout();
        return managedDescendantsFitActualHeight(card);
    }

    private static boolean managedDescendantsFitActualHeight(TaskCard card) {
        double cardBottom = card.getBoundsInLocal().getMaxY() + 0.01;
        return descendants(card).stream()
                .filter(Node::isManaged)
                .filter(Node::isVisible)
                .allMatch(node -> node.localToScene(node.getBoundsInLocal()).getMaxY()
                        <= card.localToScene(card.getBoundsInLocal()).getMinY() + cardBottom);
    }

    private static List<Node> descendants(Node root) {
        if (!(root instanceof javafx.scene.Parent parent)) return List.of();
        return parent.getChildrenUnmodifiable().stream()
                .flatMap(child -> java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(child), descendants(child).stream()))
                .toList();
    }
}
