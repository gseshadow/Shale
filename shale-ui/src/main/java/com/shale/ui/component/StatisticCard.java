package com.shale.ui.component;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Small visual-only card for summary statistics on dashboards and reports.
 *
 * <p>The card owns no data loading, filtering, navigation, or chart behavior. It
 * only standardizes the label/value/subtitle shape so existing screens can keep
 * their calculations while sharing one metric-card primitive.</p>
 */
public final class StatisticCard extends VBox {

	private static final double DEFAULT_SPACING = 6;
	private static final String DEFAULT_VALUE = "—";

	private final Label titleLabel = new Label();
	private final Label valueLabel = new Label();
	private final Label subtitleLabel = new Label();
	private final HBox header = new HBox(8);
	private final Region headerSpacer = new Region();
	private Node metadata;

	public StatisticCard() {
		setSpacing(DEFAULT_SPACING);
		setMinWidth(0);
		getStyleClass().addAll("statistic-card", "strong-panel");

		titleLabel.getStyleClass().add("statistic-card-title");
		valueLabel.getStyleClass().add("statistic-card-value");
		subtitleLabel.getStyleClass().addAll("statistic-card-subtitle", "search-summary-text");
		subtitleLabel.setWrapText(true);

		header.setAlignment(Pos.CENTER_LEFT);
		HBox.setHgrow(headerSpacer, Priority.ALWAYS);
		header.getChildren().addAll(titleLabel, headerSpacer);

		getChildren().addAll(header, valueLabel, subtitleLabel);
		setSubtitle(null);
	}

	public static StatisticCard create(String title, String value) {
		return create(title, value, null, null);
	}

	public static StatisticCard create(String title, String value, String subtitle) {
		return create(title, value, subtitle, null);
	}

	public static StatisticCard create(String title, String value, String subtitle, Node metadata) {
		StatisticCard card = new StatisticCard();
		card.setTitle(title);
		card.setValue(value);
		card.setSubtitle(subtitle);
		card.setMetadata(metadata);
		return card;
	}

	public void setTitle(String title) {
		titleLabel.setText(safe(title));
	}

	public void setValue(String value) {
		String safeValue = safe(value);
		valueLabel.setText(safeValue.isBlank() ? DEFAULT_VALUE : safeValue);
	}

	public void setSubtitle(String subtitle) {
		String safeSubtitle = safe(subtitle);
		subtitleLabel.setText(safeSubtitle);
		boolean hasSubtitle = !safeSubtitle.isBlank();
		subtitleLabel.setVisible(hasSubtitle);
		subtitleLabel.setManaged(hasSubtitle);
	}

	public void setMetadata(Node metadata) {
		if (this.metadata != null) {
			header.getChildren().remove(this.metadata);
		}
		this.metadata = metadata;
		if (metadata != null) {
			header.getChildren().add(metadata);
		}
	}

	private static String safe(String value) {
		return value == null ? "" : value;
	}
}
