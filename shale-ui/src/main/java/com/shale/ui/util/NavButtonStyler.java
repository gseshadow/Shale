package com.shale.ui.util;

import java.util.Collection;

import javafx.scene.control.Button;

public final class NavButtonStyler {

	public static final String NAV_BUTTON_STYLE_CLASS = "section-nav-button";
	public static final String NAV_BUTTON_ACTIVE_STYLE_CLASS = "section-nav-button-active";

	private NavButtonStyler() {
	}

	public static void applyBaseStyle(Button button) {
		if (button == null)
			return;

		if (!button.getStyleClass().contains(NAV_BUTTON_STYLE_CLASS)) {
			button.getStyleClass().add(NAV_BUTTON_STYLE_CLASS);
		}
	}

	public static void setActive(Button activeButton, Collection<Button> buttons) {
		if (buttons == null)
			return;

		for (Button button : buttons) {
			if (button == null)
				continue;

			applyBaseStyle(button);
			button.getStyleClass().remove(NAV_BUTTON_ACTIVE_STYLE_CLASS);
			if (button == activeButton) {
				button.getStyleClass().add(NAV_BUTTON_ACTIVE_STYLE_CLASS);
			}
		}
	}
}
