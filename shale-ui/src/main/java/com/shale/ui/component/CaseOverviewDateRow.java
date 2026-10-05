package com.shale.ui.component;

import java.util.Objects;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Responsive layout for one configurable Case Overview date and its independent edit action. */
public final class CaseOverviewDateRow {
    static final double LABEL_MIN_WIDTH = 112;
    static final double LABEL_PREF_WIDTH = 150;

    private CaseOverviewDateRow() { }

    public static HBox create(Region accent, String displayName, String displayValue,
            Node confirmation, Button editAction) {
        Objects.requireNonNull(accent, "accent");
        Objects.requireNonNull(editAction, "editAction");

        Label name = new Label(displayName);
        name.getStyleClass().add("shale-property-row-label");
        name.setWrapText(false);
        name.setTextOverrun(OverrunStyle.ELLIPSIS);
        name.setMinWidth(LABEL_MIN_WIDTH);
        name.setPrefWidth(LABEL_PREF_WIDTH);
        name.setMaxWidth(LABEL_PREF_WIDTH);
        name.setTooltip(new Tooltip(displayName));

        Label value = new Label(displayValue);
        value.getStyleClass().add("shale-property-row-value");
        value.setWrapText(false);
        value.setTextOverrun(OverrunStyle.ELLIPSIS);
        value.setMinWidth(0);
        value.setMaxWidth(Double.MAX_VALUE);

        VBox content = new VBox(4, value);
        content.getStyleClass().add("case-overview-date-content");
        content.setMinWidth(0);
        content.setMaxWidth(Double.MAX_VALUE);
        if (confirmation != null) content.getChildren().add(confirmation);
        HBox.setHgrow(content, Priority.ALWAYS);

        HBox row = new HBox(10, accent, name, content, editAction);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMinWidth(0);
        row.setMaxWidth(Double.MAX_VALUE);
        row.getStyleClass().addAll("case-overview-configured-date-row", "shale-property-row",
                "shale-property-row-compact");
        return row;
    }
}
