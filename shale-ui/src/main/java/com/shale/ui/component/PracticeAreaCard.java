package com.shale.ui.component;

import java.util.function.Consumer;

import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;

public class PracticeAreaCard extends HBox {

	private final Label nameLabel = new Label();
	private final StackPane dotHolder = new StackPane();

	private Integer practiceAreaId;
	private Consumer<Integer> onOpen;

	public PracticeAreaCard() {
		buildUiMiniDefaults();
		wireEvents();
	}

	public void setPracticeAreaId(Integer practiceAreaId) {
		this.practiceAreaId = practiceAreaId;
	}

	public void setOnOpen(Consumer<Integer> onOpen) {
		this.onOpen = onOpen;
	}

	public void setName(String name) {
		nameLabel.setText(name == null || name.isBlank() ? "—" : name);
	}

	/**
	 * Pass a CSS color like "#RRGGBB", "#RRGGBBAA", "rgba(...)" etc. If DB is "0xRRGGBBAA",
	 * convert to "#RRGGBBAA" before calling (ColorUtil can do this).
	 */
	public void setBackgroundCssColor(String css) {
		String bg = (css == null || css.isBlank()) ? "rgba(0,0,0,0.06)" : css;
		setStyle(("""
				-fx-background-color: %s;
				-fx-background-radius: 14;
				-fx-border-radius: 14;
				-fx-border-color: rgba(0,0,0,0.08);
				""").formatted(bg));
	}

	public void setDotCssColor(String css) {
		String fill = (css == null || css.isBlank()) ? "rgba(0,0,0,0.25)" : css;

		Circle c = new Circle(5.5);
		// Border helps “white” show up.
		c.setStyle(("""
				-fx-fill: %s;
				-fx-stroke: rgba(0,0,0,0.18);
				-fx-stroke-width: 1;
				""").formatted(fill));

		dotHolder.getChildren().setAll(c);
	}
	


	// --- Variants ---

	public void applyMini() {
		getChildren().clear();

		setPadding(new Insets(4, 10, 4, 10));
		setSpacing(6);

		nameLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: 600;");

		getChildren().addAll(dotHolder, nameLabel);
	}

	public void applyCompact() {
		getChildren().clear();

		setPadding(new Insets(8, 10, 8, 10));
		setSpacing(8);

		nameLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: 600;");

		getChildren().addAll(dotHolder, nameLabel);
	}

	public void applyFull() {
		getChildren().clear();

		setPadding(new Insets(10, 12, 10, 12));
		setSpacing(10);

		nameLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: 700;");

		getChildren().addAll(dotHolder, nameLabel);
	}

	private void buildUiMiniDefaults() {
		setCursor(Cursor.HAND);
		// Default dot so it doesn't render empty before setDotCssColor is called
		setDotCssColor("rgba(0,0,0,0.25)");
		applyMini();
	}

	private void wireEvents() {
		setOnMouseClicked(e ->
		{
			if (onOpen != null && practiceAreaId != null) {
				onOpen.accept(practiceAreaId);
			}
		});
	}

	public Node asNode() {
		return this;
	}
}