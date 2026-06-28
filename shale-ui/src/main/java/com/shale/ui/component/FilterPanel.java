package com.shale.ui.component;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * Lightweight layout primitive for page-level filter controls and actions.
 *
 * <p>FilterPanel standardizes the common Shale toolbar shape without owning any
 * filtering or action behavior. Screens provide their own controls and handlers;
 * this component only arranges optional left and right content with a flexible
 * spacer between them.</p>
 */
public class FilterPanel extends HBox {

	private static final double DEFAULT_SPACING = 10;
	private static final Insets DEFAULT_PADDING = new Insets(14, 14, 14, 14);

	private final Region spacer = new Region();
	private Node left;
	private Node right;

	public FilterPanel() {
		setSpacing(DEFAULT_SPACING);
		setPadding(DEFAULT_PADDING);
		setAlignment(Pos.CENTER_LEFT);
		getStyleClass().addAll("shale-filter-panel", "cases-filter-bar", "strong-panel");
		HBox.setHgrow(spacer, Priority.ALWAYS);
		rebuildChildren();
	}

	public static FilterPanel create(Node left, Node right) {
		FilterPanel panel = new FilterPanel();
		panel.setLeft(left);
		panel.setRight(right);
		return panel;
	}

	public Node getLeft() {
		return left;
	}

	public void setLeft(Node left) {
		clearPanelHgrow(this.left);
		this.left = left;
		if (left != null) {
			allowHorizontalGrowth(left);
			HBox.setHgrow(left, Priority.ALWAYS);
		}
		rebuildChildren();
	}

	public Node getRight() {
		return right;
	}

	public void setRight(Node right) {
		this.right = right;
		rebuildChildren();
	}

	private void rebuildChildren() {
		getChildren().clear();
		if (left != null) {
			getChildren().add(left);
		}
		if (left != null && right != null) {
			getChildren().add(spacer);
		}
		if (right != null) {
			getChildren().add(right);
		}
	}

	private static void clearPanelHgrow(Node node) {
		if (node != null && HBox.getHgrow(node) == Priority.ALWAYS) {
			HBox.setHgrow(node, null);
		}
	}

	private static void allowHorizontalGrowth(Node node) {
		if (node instanceof Region region) {
			region.setMinWidth(0);
			region.setMaxWidth(Double.MAX_VALUE);
		}
	}
}
