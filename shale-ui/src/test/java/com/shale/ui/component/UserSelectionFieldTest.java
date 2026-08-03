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

    private static UserSelectionField<Item> field(java.util.function.Function<Item, Node> renderer) {
        return new UserSelectionField<>(Item::id, Item::name, ignored -> null,
                (owner, candidates) -> Optional.empty(), false, renderer);
    }
}
