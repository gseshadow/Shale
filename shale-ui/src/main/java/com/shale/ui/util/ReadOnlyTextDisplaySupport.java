package com.shale.ui.util;

import javafx.scene.control.TextInputControl;

public final class ReadOnlyTextDisplaySupport {

	private static final String READ_ONLY_STYLE_CLASS = "read-only-display-field";

	private ReadOnlyTextDisplaySupport() {
	}

	public static void apply(TextInputControl control, boolean editing) {
		if (control == null) {
			return;
		}
		control.setDisable(false);
		control.setMouseTransparent(false);
		control.setEditable(editing);
		if (editing) {
			control.getStyleClass().remove(READ_ONLY_STYLE_CLASS);
		} else if (!control.getStyleClass().contains(READ_ONLY_STYLE_CLASS)) {
			control.getStyleClass().add(READ_ONLY_STYLE_CLASS);
		}
	}
}
