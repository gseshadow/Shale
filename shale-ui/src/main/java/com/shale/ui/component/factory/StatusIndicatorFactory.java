package com.shale.ui.component.factory;

import com.shale.ui.util.ColorUtil;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

/**
 * Shared factory for case status display indicators only. Status data and lifecycle
 * behavior remain owned by the existing services/DAOs.
 */
public final class StatusIndicatorFactory {

	public enum PillSize {
		COMPACT, DEFAULT, LARGE
	}

	private StatusIndicatorFactory() {
	}

	public static Node createStatusBadge(String statusName, String storedColor) {
		String display = displayName(statusName);
		Region dot = new Region();
		dot.getStyleClass().add("shale-status-badge-dot");
		dot.setStyle("-fx-background-color: " + ColorUtil.toCssBackgroundColor(storedColor) + ";");

		Label label = new Label(display);
		label.getStyleClass().add("shale-status-badge-label");
		label.setWrapText(true);

		HBox badge = new HBox(6, dot, label);
		badge.getStyleClass().add("shale-status-badge");
		badge.setAlignment(Pos.CENTER_LEFT);
		return badge;
	}

	public static Label createStatusPill(String statusName, String storedColor) {
		return createStatusPill(statusName, storedColor, PillSize.DEFAULT);
	}

	public static Label createStatusPill(String statusName, String storedColor, PillSize size) {
		String color = ColorUtil.toCssBackgroundColor(storedColor);
		Label pill = new Label(displayName(statusName));
		pill.getStyleClass().add("shale-status-pill");
		if (size == PillSize.COMPACT) {
			pill.getStyleClass().add("shale-status-pill-compact");
		} else if (size == PillSize.LARGE) {
			pill.getStyleClass().add("shale-status-pill-large");
		}
		pill.setStyle("-fx-background-color: " + color + "; -fx-text-fill: "
				+ ColorUtil.readableTextColor(storedColor) + ";");
		return pill;
	}

	private static String displayName(String statusName) {
		return statusName == null || statusName.isBlank() ? "—" : statusName.trim();
	}
}
