package com.shale.ui.component.board;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Lightweight reusable shell for lane-based board UIs:
 * horizontal board row + vertically scrollable lanes with generic header/body slots.
 */
public final class LaneBoardLayout {

	private LaneBoardLayout() {
	}

	public static void configureBoardRow(HBox boardRow) {
		if (boardRow == null) {
			return;
		}
		boardRow.setFillHeight(true);
		boardRow.setMinHeight(0);
		boardRow.setPrefHeight(Region.USE_COMPUTED_SIZE);
		boardRow.setMaxHeight(Double.MAX_VALUE);
	}

	public static VBox createLane(Node header, Node bodyContent, LaneWidth width) {
		VBox lane = new VBox(8);
		lane.setMinWidth(width.minWidth());
		lane.setPrefWidth(width.prefWidth());
		lane.setMaxWidth(width.maxWidth());
		lane.setMinHeight(280);
		lane.setPrefHeight(Region.USE_COMPUTED_SIZE);
		lane.setMaxHeight(Double.MAX_VALUE);
		lane.setPadding(new Insets(8));
		lane.getStyleClass().addAll("strong-panel", "glass-panel");

		ScrollPane bodyScroll = new ScrollPane(bodyContent);
		bodyScroll.setFitToWidth(true);
		bodyScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
		bodyScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
		bodyScroll.getStyleClass().add("surface-scroll");
		VBox.setVgrow(bodyScroll, Priority.ALWAYS);
		bodyScroll.setMinHeight(200);
		bodyScroll.setPrefHeight(Region.USE_COMPUTED_SIZE);
		bodyScroll.setMaxHeight(Double.MAX_VALUE);

		lane.getChildren().addAll(header, bodyScroll);
		return lane;
	}

	public record LaneWidth(double minWidth, double prefWidth, double maxWidth) {
	}
}
