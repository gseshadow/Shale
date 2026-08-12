package com.shale.ui.component;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.shale.ui.testutil.JavaFxTestSupport;

import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.scene.shape.Circle;

class UserSelectionFieldTest {
    private record Item(int id, String name) { }

    @BeforeAll
    static void startJavaFx() {
        JavaFxTestSupport.ensureToolkitStarted();
    }

    @Test
    void regionRendererReceivesFlexibleMaximumWidth() {
        JavaFxTestSupport.runAndWait(() -> {
            Region rendered = new Region();
            UserSelectionField<Item> field = field(item -> rendered);

            field.setSelectedUser(new Item(1, "One"));

            assertSame(rendered, field.getChildren().getFirst());
            assertEquals(Double.MAX_VALUE, rendered.getMaxWidth());
        });
    }

    @Test
    void nonRegionRendererRemainsAValidExactCardNode() {
        JavaFxTestSupport.runAndWait(() -> {
            Circle rendered = new Circle(8);
            UserSelectionField<Item> field = field(item -> rendered);

            assertDoesNotThrow(() -> field.setSelectedUser(new Item(2, "Two")));
            assertSame(rendered, field.getChildren().getFirst());
        });
    }

    @Test
    void pickerCancellationPreservesSelectionRenderedCardAndChangeControl() {
        JavaFxTestSupport.runAndWait(() -> {
            Item original = new Item(7, "Original");
            Region rendered = new Region();
            java.util.concurrent.atomic.AtomicInteger opens = new java.util.concurrent.atomic.AtomicInteger();
            UserSelectionField<Item> field = new UserSelectionField<>(Item::id, Item::name, ignored -> null,
                    (owner, candidates) -> { opens.incrementAndGet(); return Optional.empty(); }, false,
                    item -> rendered);
            field.setSelectedUser(original);
            Node change = field.getChildren().get(1);

            for (int cycle = 0; cycle < 3; cycle++) ((javafx.scene.control.Button) change).fire();

            assertSame(original, field.getSelectedUser());
            assertSame(rendered, field.getChildren().getFirst());
            assertSame(change, field.getChildren().get(1));
            assertEquals(3, opens.get());
        });
    }

    private static UserSelectionField<Item> field(java.util.function.Function<Item, Node> renderer) {
        return new UserSelectionField<>(Item::id, Item::name, ignored -> null,
                (owner, candidates) -> Optional.empty(), false, renderer);
    }
}
