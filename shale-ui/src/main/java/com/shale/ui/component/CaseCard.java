package com.shale.ui.component;

import java.time.LocalDate;
import java.util.function.Consumer;

import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

/**
 * CaseCard - reusable VBox "card" for rendering a case summary.
 *
 * Designed to match your current VBox card style exactly: - background color driven by
 * responsible attorney color (CSS string) - rounded corners - light border - drop shadow
 * - title, responsible attorney, intake + SOL dates
 *
 * The host screen wires navigation via setOnOpen(...)
 */
public class CaseCard extends VBox {

	private final Label titleLabel = new Label();
	private final Label attorneyLabel = new Label();

	private final Label intakeLabel = new Label();
	private final Label solLabel = new Label();

	private final HBox datesRow = new HBox(10);
	private final Region spacer = new Region();

	private Integer caseId;
	private Consumer<Integer> onOpen;

	public CaseCard() {
		super(6);
		buildUi();
		wireEvents();
	}

	public CaseCard(int caseId) {
		this();
		setCaseId(caseId);
	}

	/*
	 * ----------------------------- Data setters -----------------------------
	 */

	public void setCaseId(Integer caseId) {
		this.caseId = caseId;
	}

	public Integer getCaseId() {
		return caseId;
	}

	public void setOnOpen(Consumer<Integer> onOpen) {
		this.onOpen = onOpen;
	}

	public void setTitle(String name) {
		String text = (name == null || name.isBlank()) ? "(no name)" : name;
		titleLabel.setText(text);
	}

	public void setResponsibleAttorney(String responsibleAttorney) {
		String text = (responsibleAttorney == null || responsibleAttorney.isBlank()) ? "" : responsibleAttorney;
		attorneyLabel.setText(text);
	}

	public void setIntakeDate(LocalDate intakeDate) {
		intakeLabel.setText("Intake: " + (intakeDate == null ? "" : intakeDate.toString()));
	}

	public void setSolDate(LocalDate solDate) {
		solLabel.setText("SOL: " + (solDate == null ? "" : solDate.toString()));
	}

	/**
	 * Set the card background color using a CSS color string produced by your existing
	 * toCssBackgroundColor(...) Example values: "#RRGGBB", "rgba(...)",
	 * "linear-gradient(...)" (if you ever want)
	 */
	public void setBackgroundCssColor(String backgroundColorCss) {
		String bg = (backgroundColorCss == null || backgroundColorCss.isBlank()) ? "white" : backgroundColorCss;

		setStyle("""
				    -fx-background-color: %s;
				    -fx-background-radius: 14;
				    -fx-border-radius: 14;
				    -fx-border-color: #e5e5e5;
				    -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 10, 0.2, 0, 2);
				""".formatted(bg));
	}

	/**
	 * Convenience: if you ever want to set a plain Color directly (not required). This does
	 * NOT use your gradient/tint logic; it just sets a solid background.
	 */
	public void setBackgroundColor(Color color) {
		if (color == null)
			return;
		BackgroundFill fill = new BackgroundFill(color, new CornerRadii(14), Insets.EMPTY);
		setBackground(new Background(fill));
	}

	/**
	 * Convenience: apply everything from your VM in one call. (Keeps this class UI-only; VM
	 * is just a data bag.)
	 */
	public void setFromVm(Object vm,
			int id,
			String name,
			String responsibleAttorney,
			LocalDate intakeDate,
			LocalDate solDate,
			String backgroundCss) {
		setCaseId(id);
		setTitle(name);
		setResponsibleAttorney(responsibleAttorney);
		setIntakeDate(intakeDate);
		setSolDate(solDate);
		setBackgroundCssColor(backgroundCss);
	}

	/*
	 * ----------------------------- UI build / events -----------------------------
	 */

	private void buildUi() {
		setPadding(new Insets(10));
		setPrefWidth(280);

		// Title / attorney styles exactly like your snippet
		titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
		attorneyLabel.setStyle("-fx-font-size: 12px; -fx-opacity: 0.75;");

		intakeLabel.setStyle("-fx-font-size: 12px;");
		solLabel.setStyle("-fx-font-size: 12px;");

		HBox.setHgrow(spacer, Priority.ALWAYS);
		datesRow.getChildren().setAll(intakeLabel, spacer, solLabel);

		getChildren().setAll(titleLabel, attorneyLabel, datesRow);

		// Nice UX: looks clickable
		setCursor(Cursor.HAND);
	}

	private void wireEvents() {
		setOnMouseClicked(e ->
		{
			if (onOpen != null && caseId != null) {
				onOpen.accept(caseId);
			}
		});
	}

	/*
	 * ----------------------------- Helper: if you want to embed the card as Node easily
	 * -----------------------------
	 */
	public Node asNode() {
		return this;
	}
}
