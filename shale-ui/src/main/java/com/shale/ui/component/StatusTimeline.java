package com.shale.ui.component;

import com.shale.ui.util.ColorUtil;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
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
    public enum State { COMPLETED, CURRENT, FUTURE }
    public record Item(String identity, String name, String color, State state, String tooltip) {}

    private StatusTimeline() {}

    public static ScrollPane create(List<Item> source, Variant variant) {
        List<Item> items = source == null ? List.of() : source;
        HBox row = new HBox(0);
        row.getStyleClass().add("status-timeline__row");
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
        // A timeline is read-only. Consume its gestures so a containing card does not activate.
        scroll.addEventFilter(MouseEvent.MOUSE_CLICKED, MouseEvent::consume);
        return scroll;
    }

    private static Node pill(Item item, Variant variant) {
        boolean compact = variant == Variant.COMPACT_CARD;
        boolean completed = item.state() == State.COMPLETED;
        boolean current = item.state() == State.CURRENT;
        String storedColor = ColorUtil.normalizeStoredColor(item.color()) == null ? "#E2E8F0" : item.color();
        String background = ColorUtil.toCssBackgroundColor(storedColor);
        String textColor = ColorUtil.readableTextColor(storedColor);
        String name = item.name() == null || item.name().isBlank() ? "Unknown" : item.name().trim();

        Label check = new Label("✓");
        check.getStyleClass().add("status-timeline__check");
        check.setVisible(completed);
        check.setManaged(completed);
        check.setMinWidth(compact ? 10 : 14);
        check.setAlignment(Pos.CENTER);
        check.setStyle("-fx-text-fill: " + textColor + "; -fx-opacity: 0.82; -fx-font-size: "
                + (compact ? 10 : 13) + "px; -fx-font-weight: bold;");

        Label label = new Label(name);
        label.getStyleClass().add("status-timeline__label");
        label.setMinHeight(compact ? 26 : 38);
        label.setMaxHeight(compact ? 26 : 38);
        label.setMinWidth(compact ? 44 : 72);
        label.setMaxWidth(compact ? 150 : 220);
        label.setAlignment(Pos.CENTER);
        label.setTextOverrun(OverrunStyle.ELLIPSIS);
        label.setStyle("-fx-text-fill: " + textColor + "; -fx-font-size: " + (compact ? 11 : 13)
                + "px; -fx-font-weight: " + (current ? "bold" : "600") + ";");

        HBox pill = new HBox(compact ? 4 : 8, check, label);
        pill.getStyleClass().addAll("shale-status-pill", "status-timeline__pill",
                current ? "status-timeline__pill--current" : completed
                        ? "status-timeline__pill--completed" : "status-timeline__pill--future");
        pill.setUserData(item);
        pill.setAlignment(Pos.CENTER);
        pill.setMinHeight(compact ? 26 : 38);
        pill.setMaxHeight(compact ? 26 : 38);
        pill.setMinWidth(compact ? 66 : 112);
        pill.setMaxWidth(compact ? 178 : 270);
        double radius = compact ? 13 : 20;
        pill.setStyle("-fx-background-color: " + background + "; -fx-background-radius: " + radius + "; "
                + "-fx-padding: 0 " + (compact ? 9 : 18) + " 0 " + (completed ? (compact ? 7 : 14) : (compact ? 9 : 18)) + "; "
                + "-fx-border-color: " + (current ? "rgba(20,35,55,0.62)" : "rgba(0,0,0,0.14)") + "; "
                + "-fx-border-radius: " + radius + "; -fx-border-width: " + (current ? 1.8 : 0.8) + "; "
                + (current ? "-fx-effect: dropshadow(gaussian, rgba(31,41,55,0.26), " + (compact ? 6 : 10) + ", 0.2, 0, 1);" : "")
                + (item.state() == State.FUTURE ? "-fx-opacity: 0.52;" : ""));
        Tooltip.install(pill, new Tooltip(item.tooltip() == null || item.tooltip().isBlank() ? name : item.tooltip()));
        return pill;
    }

    private static Node connector(Item preceding, Variant variant) {
        boolean compact = variant == Variant.COMPACT_CARD;
        Region line = new Region();
        line.getStyleClass().addAll("status-timeline__connector-line",
                preceding.state() == State.COMPLETED ? "status-timeline__connector-line--completed" : "status-timeline__connector-line--future");
        line.setMinSize(compact ? 12 : 30, 2);
        line.setPrefSize(compact ? 12 : 30, 2);
        line.setMaxSize(compact ? 12 : 30, 2);
        line.setStyle("-fx-background-color: " + (preceding.state() == State.COMPLETED
                ? "rgba(91,103,124,0.62)" : "rgba(91,103,124,0.30)") + "; -fx-background-radius: 2;");
        StackPane connector = new StackPane(line);
        connector.getStyleClass().add("status-timeline__connector");
        connector.setMinSize(compact ? 16 : 38, compact ? 26 : 38);
        connector.setPrefSize(compact ? 16 : 38, compact ? 26 : 38);
        connector.setMaxHeight(compact ? 26 : 38);
        connector.setAlignment(Pos.CENTER);
        return connector;
    }
}
