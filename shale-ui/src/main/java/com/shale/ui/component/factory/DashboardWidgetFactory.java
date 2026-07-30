package com.shale.ui.component.factory;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import com.shale.ui.util.ControlStyles;

/**
 * Builds reusable dashboard widgets with a consistent Shale card shell.
 */
public final class DashboardWidgetFactory {

	private DashboardWidgetFactory() {
	}

	public static VBox placeholder(String title, String emptyStateText) {
		return widget(title, null, null, emptyState(emptyStateText), false, false);
	}

	public static VBox widget(
			String title,
			String badgeText,
			Runnable viewAllAction,
			Node content,
			boolean loading,
			boolean empty) {
		VBox widget = new VBox(10);
		widget.getStyleClass().addAll("dashboard-widget", "shale-card-surface", "shale-entity-card-compact");
		widget.setFillWidth(true);
		widget.setMaxWidth(Double.MAX_VALUE);

		HBox header = new HBox(8);
		header.setAlignment(Pos.CENTER_LEFT);
		header.getStyleClass().add("dashboard-widget-header");

		Label titleLabel = new Label(title == null ? "" : title);
		titleLabel.getStyleClass().add("dashboard-widget-title");

		header.getChildren().add(titleLabel);
		if (badgeText != null && !badgeText.isBlank()) {
			Label badge = new Label(badgeText.trim());
			badge.getStyleClass().addAll("dashboard-widget-badge", "badge");
			header.getChildren().add(badge);
		}
		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		header.getChildren().add(spacer);
		if (viewAllAction != null) {
			Button viewAll = new Button("View All");
			viewAll.getStyleClass().add("dashboard-widget-view-all");
			ControlStyles.apply(viewAll, ControlStyles.Purpose.NAVIGATION, ControlStyles.Size.SMALL);
			viewAll.setOnAction(event -> viewAllAction.run());
			header.getChildren().add(viewAll);
		}

		StackPane contentPane = new StackPane();
		contentPane.getStyleClass().add("dashboard-widget-content");
		contentPane.setMaxWidth(Double.MAX_VALUE);
		Node stateNode = loading
				? stateLabel("Loading…", "dashboard-widget-loading", "shale-loading-placeholder")
				: (empty || content == null ? emptyState("Nothing to show yet.") : content);
		contentPane.getChildren().setAll(stateNode);

		widget.getChildren().addAll(header, contentPane);
		return widget;
	}

	public static Label emptyState(String text) {
		return stateLabel(text, "dashboard-widget-empty", "shale-empty-state");
	}

	public static Label errorState(String text) {
		return stateLabel(text, "dashboard-widget-error", "shale-empty-state");
	}

	private static Label stateLabel(String text, String... styleClasses) {
		Label label = new Label(text == null ? "" : text);
		label.getStyleClass().addAll(styleClasses);
		label.setWrapText(true);
		return label;
	}
}
