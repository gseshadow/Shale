package com.shale.ui.util;

import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;

/**
 * Factory for quiet, neutral metadata chips used for supplemental UI text.
 *
 * <p>Metadata chips intentionally do not represent semantic application state and
 * must remain separate from status or practice-area indicator components.</p>
 */
public final class MetadataChipFactory {
	private static final String BASE_STYLE_CLASS = "metadata-chip";
	private static final String SMALL_STYLE_CLASS = "metadata-chip-small";
	private static final String COMPACT_STYLE_CLASS = "metadata-chip-compact";
	private static final String EMPTY_TEXT = "—";

	private MetadataChipFactory() {
	}

	public static Label create(String text) {
		return build(text, null, COMPACT_STYLE_CLASS);
	}

	public static Label create(String text, String tooltip) {
		return build(text, tooltip, COMPACT_STYLE_CLASS);
	}

	public static Label small(String text) {
		return build(text, null, SMALL_STYLE_CLASS);
	}

	public static Label small(String text, String tooltip) {
		return build(text, tooltip, SMALL_STYLE_CLASS);
	}

	public static Label compact(String text) {
		return build(text, null, COMPACT_STYLE_CLASS);
	}

	public static Label compact(String text, String tooltip) {
		return build(text, tooltip, COMPACT_STYLE_CLASS);
	}

	private static Label build(String text, String tooltip, String sizeStyleClass) {
		Label label = new Label(normalize(text));
		label.getStyleClass().addAll(BASE_STYLE_CLASS, sizeStyleClass);
		String normalizedTooltip = normalizeTooltip(tooltip);
		if (!normalizedTooltip.isBlank()) {
			Tooltip.install(label, new Tooltip(normalizedTooltip));
		}
		return label;
	}

	private static String normalize(String text) {
		String normalized = text == null ? "" : text.trim();
		return normalized.isBlank() ? EMPTY_TEXT : normalized;
	}

	private static String normalizeTooltip(String tooltip) {
		return tooltip == null ? "" : tooltip.trim();
	}
}
