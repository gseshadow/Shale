package com.shale.ui.component;

import com.shale.ui.util.ColorUtil;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

import java.util.List;

/**
 * Display-only status timeline shared by case history and compact card progress.
 * Data access and workflow interpretation deliberately remain with the caller.
 */
public final class StatusTimeline {
    public enum Variant { OVERVIEW, COMPACT_CARD }
    public enum State { COMPLETED, CURRENT, HISTORICAL, FUTURE }
    public record Item(String identity, String name, String color, State state, String tooltip) {}

    private StatusTimeline() {}

    public static ScrollPane create(List<Item> source, Variant variant) {
        List<Item> items = source == null ? List.of() : source;
        HBox row = new HBox(0);
        row.getStyleClass().addAll("status-timeline__row", "shale-stage-tracker");
        row.setAlignment(Pos.CENTER_LEFT);
        for (int i = 0; i < items.size(); i++) {
            row.getChildren().add(pill(items.get(i), variant));
            if (i + 1 < items.size()) row.getChildren().add(connector(items.get(i), variant));
        }

        ScrollPane scroll = new ScrollPane(row);
        scroll.getStyleClass().addAll("status-timeline", variant == Variant.COMPACT_CARD
                ? "status-timeline--compact" : "status-timeline--overview");
        scroll.setFitToHeight(true);
        scroll.setFitToWidth(true);
        scroll.setMinWidth(0);
        scroll.setMinViewportHeight(variant == Variant.COMPACT_CARD ? 30 : 48);
        scroll.setPrefViewportHeight(variant == Variant.COMPACT_CARD ? 32 : 52);
        scroll.setMaxHeight(variant == Variant.COMPACT_CARD ? 36 : 58);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPannable(true);
        if (variant == Variant.OVERVIEW && !items.isEmpty()) {
            // One post-layout reveal keeps the authoritative latest record practical
            // to find without animation, listeners, or a refresh feedback loop.
            Platform.runLater(() -> scroll.setHvalue(scroll.getHmax()));
        }
        // The timeline is read-only. Leave ordinary clicks unconsumed so an enclosing
        // selectable card receives them; ScrollPane still owns genuine pan gestures.
        return scroll;
    }

    private static Node pill(Item item, Variant variant) {
        boolean compact = variant == Variant.COMPACT_CARD;
        boolean completed = item.state() == State.COMPLETED;
        boolean current = item.state() == State.CURRENT;
        String storedColor = ColorUtil.normalizeStoredColor(item.color()) == null ? "#E2E8F0" : item.color();
        String name = item.name() == null || item.name().isBlank() ? "Unknown" : item.name().trim();

        Label check = new Label(current ? "●" : "✓");
        check.getStyleClass().add("status-timeline__check");
        check.setVisible(true);
        check.setManaged(true);
        check.setMinWidth(compact ? 10 : 14);
        check.setAlignment(Pos.CENTER);
        check.setStyle("-fx-text-fill: " + ColorUtil.toCssBackgroundColor(storedColor) + ";");

        Label label = new Label(name);
        label.getStyleClass().add("status-timeline__label");
        label.setMinHeight(compact ? 26 : 38);
        label.setMaxHeight(compact ? 26 : 38);
        label.setMinWidth(compact ? 44 : 72);
        label.setMaxWidth(compact ? 150 : 220);
        label.setAlignment(Pos.CENTER);
        label.setTextOverrun(OverrunStyle.ELLIPSIS);

        HBox pill = new HBox(compact ? 4 : 8, check, label);
        pill.getStyleClass().addAll("shale-stage-item", "status-timeline__pill",
                current ? "status-timeline__pill--current" : completed
                        ? "status-timeline__pill--completed" : item.state() == State.HISTORICAL
                        ? "status-timeline__pill--historical" : "status-timeline__pill--future");
        pill.setUserData(item);
        pill.setAlignment(Pos.CENTER);
        pill.setMinHeight(compact ? 26 : 38);
        pill.setMaxHeight(compact ? 26 : 38);
        pill.setMinWidth(compact ? 66 : 112);
        pill.setMaxWidth(compact ? 178 : 270);
        // Only the validated database color remains inline; theme-owned surface,
        // border, text, focus, and elevation are owned by the shared stage CSS.
        pill.setStyle("-shale-stage-data-color: " + ColorUtil.toCssBackgroundColor(storedColor) + ";");
        Tooltip.install(pill, new Tooltip(item.tooltip() == null || item.tooltip().isBlank() ? name : item.tooltip()));
        return pill;
    }

    private static Node connector(Item preceding, Variant variant) {
        boolean compact = variant == Variant.COMPACT_CARD;
        Region line = new Region();
        line.getStyleClass().addAll("status-timeline__connector-line", "shale-stage-connector",
                preceding.state() == State.COMPLETED ? "status-timeline__connector-line--completed" : "status-timeline__connector-line--future");
        line.setMinSize(compact ? 12 : 30, 2);
        line.setPrefSize(compact ? 12 : 30, 2);
        line.setMaxSize(compact ? 12 : 30, 2);
        StackPane connector = new StackPane(line);
        connector.getStyleClass().add("status-timeline__connector");
        connector.setMinSize(compact ? 16 : 38, compact ? 26 : 38);
        connector.setPrefSize(compact ? 16 : 38, compact ? 26 : 38);
        connector.setMaxHeight(compact ? 26 : 38);
        connector.setAlignment(Pos.CENTER);
        return connector;
    }
}
