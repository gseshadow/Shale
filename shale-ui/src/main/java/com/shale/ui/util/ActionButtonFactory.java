package com.shale.ui.util;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Button;

/**
 * Small factory for consistently constructing Shale action buttons.
 *
 * <p>The factory only applies existing style classes and optional action
 * handlers. It does not own layout, state, icons, or command behavior.</p>
 */
public final class ActionButtonFactory {

	public static final String BASE_STYLE_CLASS = "app-toolbar-button";
	public static final String PRIMARY_STYLE_CLASS = "app-toolbar-button-primary";
	public static final String NEUTRAL_STYLE_CLASS = "app-toolbar-button-neutral";
	public static final String DANGER_STYLE_CLASS = "app-toolbar-button-danger";
	public static final String COMPACT_STYLE_CLASS = "app-toolbar-button-compact";
	public static final String CARD_ACTION_STYLE_CLASS = "app-taskcard-action-button";

	private ActionButtonFactory() {
	}

	public static Button primary(String text, EventHandler<ActionEvent> handler) {
		return create(text, handler, PRIMARY_STYLE_CLASS);
	}

	public static Button neutral(String text, EventHandler<ActionEvent> handler) {
		return create(text, handler, NEUTRAL_STYLE_CLASS);
	}

	public static Button danger(String text, EventHandler<ActionEvent> handler) {
		return create(text, handler, DANGER_STYLE_CLASS);
	}

	public static Button compact(String text, EventHandler<ActionEvent> handler) {
		return create(text, handler, NEUTRAL_STYLE_CLASS, COMPACT_STYLE_CLASS);
	}

	public static Button cardAction(String text, EventHandler<ActionEvent> handler) {
		return create(text, handler, NEUTRAL_STYLE_CLASS, CARD_ACTION_STYLE_CLASS);
	}

	/** Explicit opt-in path; legacy factory methods retain their existing classes and appearance. */
	public static Button semantic(String text, EventHandler<ActionEvent> handler,
			ControlStyles.Purpose purpose, ControlStyles.Size size) {
		Button button = new Button(normalize(text));
		if (handler != null) button.setOnAction(handler);
		return ControlStyles.apply(button, purpose, size);
	}

	private static Button create(String text, EventHandler<ActionEvent> handler, String... styleClasses) {
		Button button = new Button(normalize(text));
		addStyleClass(button, BASE_STYLE_CLASS);
		if (styleClasses != null) {
			for (String styleClass : styleClasses) {
				addStyleClass(button, styleClass);
			}
		}
		if (handler != null) {
			button.setOnAction(handler);
		}
		return button;
	}

	private static void addStyleClass(Button button, String styleClass) {
		if (styleClass == null || styleClass.isBlank() || button.getStyleClass().contains(styleClass)) {
			return;
		}
		button.getStyleClass().add(styleClass);
	}

	private static String normalize(String text) {
		return text == null ? "" : text.trim();
	}
}
