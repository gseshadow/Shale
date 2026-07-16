package com.shale.ui.component.factory;

import com.shale.ui.util.ColorUtil;

import javafx.scene.control.Label;

/** Shared Shale pill treatment for database-driven Case Link Type colors. */
public final class LinkTypeIndicatorFactory {
	public enum PillSize { COMPACT, DEFAULT }
	private LinkTypeIndicatorFactory() {}
	public static Label createLinkTypePill(String name, String storedColor) { return createLinkTypePill(name, storedColor, PillSize.DEFAULT); }
	public static Label createLinkTypePill(String name, String storedColor, PillSize size) {
		Label pill = new Label(name == null || name.isBlank() ? "—" : name.trim());
		pill.getStyleClass().addAll("shale-practice-area-pill", "shale-link-type-pill");
		if (size == PillSize.COMPACT) pill.getStyleClass().add("shale-practice-area-pill-compact");
		pill.setStyle("-fx-background-color: " + ColorUtil.toCssBackgroundColor(storedColor) + "; -fx-text-fill: " + ColorUtil.readableTextColor(storedColor) + ";");
		return pill;
	}
}
