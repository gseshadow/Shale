package com.shale.ui.component.factory;

import com.shale.ui.util.ColorUtil;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

/**
 * Shared factory for practice-area display indicators only. Practice-area data,
 * tenant/global scope, persistence, and edit behavior remain owned by the
 * existing services/DAOs.
 */
public final class PracticeAreaIndicatorFactory {

	public enum PillSize {
		COMPACT, DEFAULT, LARGE
	}

	private PracticeAreaIndicatorFactory() {
	}

	public static Node createPracticeAreaBadge(String practiceAreaName, String storedColor) {
		Region dot = new Region();
		dot.getStyleClass().add("shale-practice-area-badge-dot");
		dot.setStyle("-fx-background-color: " + ColorUtil.toCssBackgroundColor(storedColor) + ";");

		Label label = new Label(displayName(practiceAreaName));
		label.getStyleClass().add("shale-practice-area-badge-label");
		label.setWrapText(true);

		HBox badge = new HBox(6, dot, label);
		badge.getStyleClass().add("shale-practice-area-badge");
		badge.setAlignment(Pos.CENTER_LEFT);
		return badge;
	}

	public static Label createPracticeAreaPill(String practiceAreaName, String storedColor) {
		return createPracticeAreaPill(practiceAreaName, storedColor, PillSize.DEFAULT);
	}

	public static Label createPracticeAreaPill(String practiceAreaName, String storedColor, PillSize size) {
		String color = ColorUtil.toCssBackgroundColor(storedColor);
		Label pill = new Label(displayName(practiceAreaName));
		pill.getStyleClass().add("shale-practice-area-pill");
		if (size == PillSize.COMPACT) {
			pill.getStyleClass().add("shale-practice-area-pill-compact");
		} else if (size == PillSize.LARGE) {
			pill.getStyleClass().add("shale-practice-area-pill-large");
		}
		pill.setStyle("-fx-background-color: " + color + "; -fx-text-fill: "
				+ ColorUtil.readableTextColor(storedColor) + ";");
		return pill;
	}

	private static String displayName(String practiceAreaName) {
		return practiceAreaName == null || practiceAreaName.isBlank() ? "—" : practiceAreaName.trim();
	}
}
