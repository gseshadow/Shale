package com.shale.ui.component;

import javafx.beans.DefaultProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Boring section container for titled JavaFX content areas.
 *
 * <p>The component intentionally uses existing Shale style classes and only
 * constructs layout. It does not own any screen behavior.</p>
 */
@DefaultProperty("children")
public class SectionRegion extends VBox {

	private static final double DEFAULT_SPACING = 12;
	private static final Insets DEFAULT_PADDING = new Insets(16, 16, 16, 16);

	private final HBox header = new HBox(12);
	private final VBox titleBlock = new VBox(2);
	private final Label titleLabel = new Label();
	private final Label subtitleLabel = new Label();
	private Node actions;

	public SectionRegion() {
		setSpacing(DEFAULT_SPACING);
		setPadding(DEFAULT_PADDING);
		getStyleClass().addAll("case-right-pane", "strong-panel");

		titleLabel.getStyleClass().add("search-section-title");
		subtitleLabel.getStyleClass().add("search-summary-text");
		subtitleLabel.setWrapText(true);
		setSubtitle(null);

		titleBlock.getChildren().addAll(titleLabel, subtitleLabel);
		titleBlock.setMinWidth(0);
		HBox.setHgrow(titleBlock, Priority.ALWAYS);

		header.setAlignment(Pos.CENTER_LEFT);
		header.getChildren().add(titleBlock);
		getChildren().add(header);
	}

	public static SectionRegion create(String title, Node content) {
		return create(title, null, null, content);
	}

	public static SectionRegion create(String title, String subtitle, Node content) {
		return create(title, subtitle, null, content);
	}

	public static SectionRegion create(String title, String subtitle, Node actions, Node content) {
		SectionRegion section = new SectionRegion();
		section.setTitle(title);
		section.setSubtitle(subtitle);
		section.setActions(actions);
		section.addContent(content);
		return section;
	}

	public void setTitle(String title) {
		titleLabel.setText(safe(title));
	}

	public String getTitle() {
		return titleLabel.getText();
	}

	public void setSubtitle(String subtitle) {
		String safeSubtitle = safe(subtitle);
		subtitleLabel.setText(safeSubtitle);
		boolean hasSubtitle = !safeSubtitle.isBlank();
		subtitleLabel.setVisible(hasSubtitle);
		subtitleLabel.setManaged(hasSubtitle);
	}

	public String getSubtitle() {
		return subtitleLabel.getText();
	}

	public void setActions(Node actions) {
		if (this.actions != null) {
			header.getChildren().remove(this.actions);
		}
		this.actions = actions;
		if (actions != null) {
			header.getChildren().add(actions);
		}
	}

	public Node getActions() {
		return actions;
	}

	public void addContent(Node content) {
		if (content != null) {
			getChildren().add(content);
		}
	}

	private static String safe(String value) {
		return value == null ? "" : value;
	}
}
