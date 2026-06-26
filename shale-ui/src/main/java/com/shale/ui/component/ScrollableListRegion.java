package com.shale.ui.component;

import com.shale.ui.util.UiStateLabels;

import javafx.beans.DefaultProperty;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.StackPane;

/**
 * Small reusable container for scrollable list or grid content with optional
 * loading and empty state labels.
 *
 * <p>This component owns only the outer ScrollPane and standard state-label
 * visibility. It intentionally does not own data loading, filtering, item/card
 * creation, or business logic.</p>
 */
@DefaultProperty("content")
public class ScrollableListRegion extends StackPane {

	private final ScrollPane scrollPane = new ScrollPane();
	private Node content;
	private Label emptyLabel;
	private Label loadingLabel;

	public ScrollableListRegion() {
		scrollPane.setFitToWidth(true);
		scrollPane.setPannable(true);
		scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
		scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
		scrollPane.getStyleClass().add("surface-scroll");
		getChildren().add(scrollPane);
	}

	public static ScrollableListRegion create(Node content) {
		return create(content, null, null);
	}

	public static ScrollableListRegion create(Node content, Label emptyLabel) {
		return create(content, emptyLabel, null);
	}

	public static ScrollableListRegion create(Node content, Label emptyLabel, Label loadingLabel) {
		ScrollableListRegion region = new ScrollableListRegion();
		region.setContent(content);
		region.setEmptyLabel(emptyLabel);
		region.setLoadingLabel(loadingLabel);
		return region;
	}

	public Node getContent() {
		return content;
	}

	public void setContent(Node content) {
		this.content = content;
		scrollPane.setContent(content);
	}

	public Label getEmptyLabel() {
		return emptyLabel;
	}

	public void setEmptyLabel(Label emptyLabel) {
		if (this.emptyLabel != null) {
			getChildren().remove(this.emptyLabel);
		}
		this.emptyLabel = emptyLabel;
		if (emptyLabel != null) {
			UiStateLabels.hide(emptyLabel);
			getChildren().add(emptyLabel);
		}
	}

	public Label getLoadingLabel() {
		return loadingLabel;
	}

	public void setLoadingLabel(Label loadingLabel) {
		if (this.loadingLabel != null) {
			getChildren().remove(this.loadingLabel);
		}
		this.loadingLabel = loadingLabel;
		if (loadingLabel != null) {
			UiStateLabels.hide(loadingLabel);
			getChildren().add(loadingLabel);
		}
	}

	public ScrollPane getScrollPane() {
		return scrollPane;
	}

	public void showEmpty(boolean visible) {
		if (visible) {
			UiStateLabels.showEmpty(emptyLabel);
		} else {
			UiStateLabels.hide(emptyLabel);
		}
		setScrollVisible(!visible);
	}

	public void showLoading(boolean visible) {
		if (visible) {
			UiStateLabels.showLoading(loadingLabel);
			UiStateLabels.hide(emptyLabel);
			setScrollVisible(false);
		} else {
			UiStateLabels.hide(loadingLabel);
		}
	}

	private void setScrollVisible(boolean visible) {
		scrollPane.setVisible(visible);
		scrollPane.setManaged(visible);
	}
}
