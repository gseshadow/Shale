package com.shale.ui.util;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;

public final class AppSectionTabs {

	public static final String TAB_BUTTON_STYLE_CLASS = "app-section-tab";
	public static final String TAB_BUTTON_ACTIVE_STYLE_CLASS = "app-section-tab-active";

	private AppSectionTabs() {
	}

	public static <T> Map<T, Button> buildTabs(HBox host, List<TabSpec<T>> tabs, Consumer<T> onSelect) {
		Map<T, Button> buttons = new LinkedHashMap<>();
		if (host == null) {
			return buttons;
		}
		host.getChildren().clear();
		host.setAlignment(Pos.CENTER_LEFT);
		for (TabSpec<T> tab : tabs) {
			if (tab == null) {
				continue;
			}
			Button button = new Button(tab.label());
			button.setMnemonicParsing(false);
			applyBaseStyle(button);
			button.setOnAction(e -> {
				if (onSelect != null) {
					onSelect.accept(tab.key());
				}
			});
			buttons.put(tab.key(), button);
			host.getChildren().add(button);
		}
		return buttons;
	}

	public static void applyBaseStyle(Button button) {
		if (button == null) {
			return;
		}
		if (!button.getStyleClass().contains(TAB_BUTTON_STYLE_CLASS)) {
			button.getStyleClass().add(TAB_BUTTON_STYLE_CLASS);
		}
	}

	public static void setActive(Button activeButton, Collection<Button> buttons) {
		if (buttons == null) {
			return;
		}
		for (Button button : buttons) {
			if (button == null) {
				continue;
			}
			applyBaseStyle(button);
			button.getStyleClass().remove(TAB_BUTTON_ACTIVE_STYLE_CLASS);
			if (button == activeButton) {
				button.getStyleClass().add(TAB_BUTTON_ACTIVE_STYLE_CLASS);
			}
		}
	}

	public record TabSpec<T>(T key, String label) {
	}
}
