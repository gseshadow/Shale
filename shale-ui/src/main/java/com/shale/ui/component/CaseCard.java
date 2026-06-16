package com.shale.ui.component;

import java.time.LocalDate;
import java.util.function.Consumer;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

/**
 * CaseCard - reusable VBox "card" for rendering a case summary.
 *
 * Case card surface with a neutral main body, practice-area accent bar, compact
 * status chip, and responsible-attorney color dot.
 *
 * The host screen wires navigation via setOnOpen(...)
 */
public class CaseCard extends VBox {

	private final Label titleLabel = new Label();
	private final Label attorneyLabel = new Label();

	private final Label intakeLabel = new Label();
	private final Label solLabel = new Label();
	private final Label statusLabel = new Label();
	private final Region practiceAreaBar = new Region();
	private final Region attorneyDot = new Region();

	private final HBox cardRow = new HBox(0);
	private final VBox bodyPane = new VBox(6);
	private final HBox headerRow = new HBox(8);
	private final HBox indicatorRow = new HBox(6);
	private final VBox datesBox = new VBox(2);
	private final Region bodySpacer = new Region();
	private final Region headerSpacer = new Region();

	private Integer caseId;
	private Consumer<Integer> onOpen;
	private String statusColorCss = "#F1F5F9";
	private String attorneyColorCss = "#94A3B8";
	private String practiceAreaColorCss = "#CBD5E1";
	private String statusLabelBaseStyle = "-fx-font-size: 11px; -fx-font-weight: 700;";
	private boolean hovered;

	public CaseCard() {
		super(6);
		buildUi();
		wireEvents();
	}

	public CaseCard(int caseId) {
		this();
		setCaseId(caseId);
	}

	public void applyMini() {
		setSpacing(3);
		setPadding(Insets.EMPTY);
		setMinWidth(Region.USE_COMPUTED_SIZE);
		setPrefWidth(Region.USE_COMPUTED_SIZE);
		setMaxWidth(Region.USE_COMPUTED_SIZE);
		bodyPane.setPadding(new Insets(5, 9, 5, 9));
		practiceAreaBar.setPrefWidth(5);
		attorneyLabel.setManaged(false);
		attorneyLabel.setVisible(false);
		datesBox.setManaged(false);
		datesBox.setVisible(false);
		titleLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: 700; -fx-text-fill: #112542;");
		statusLabelBaseStyle = "-fx-font-size: 9px; -fx-font-weight: 700;";
		attorneyDot.setPrefSize(18, 18);
		indicatorRow.setSpacing(4);
		refreshSurfaceStyle();
	}

	public void applyCompact() {
		setSpacing(6);
		setPadding(Insets.EMPTY);
		setPrefWidth(280);
		bodyPane.setPadding(new Insets(10));
		practiceAreaBar.setPrefWidth(6);
		attorneyLabel.setManaged(true);
		attorneyLabel.setVisible(true);
		datesBox.setManaged(true);
		datesBox.setVisible(true);
		titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
		statusLabelBaseStyle = "-fx-font-size: 11px; -fx-font-weight: 700;";
		attorneyDot.setPrefSize(22, 22);
		indicatorRow.setSpacing(6);
		refreshSurfaceStyle();
	}

	public void applyFull() {
		applyCompact();
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
		// Kept for API compatibility. The case card main surface is intentionally
		// neutral now; callers should use setStatusCssColor and setAttorneyDotCssColor.
		refreshSurfaceStyle();
	}

	public void setStatus(String statusName) {
		statusLabel.setText((statusName == null || statusName.isBlank()) ? "—" : statusName.trim());
	}

	public void setStatusCssColor(String statusColorCss) {
		this.statusColorCss = normalizeColor(statusColorCss, "#F1F5F9");
		refreshSurfaceStyle();
	}

	public void setAttorneyDotCssColor(String attorneyColorCss) {
		this.attorneyColorCss = normalizeColor(attorneyColorCss, "#94A3B8");
		refreshSurfaceStyle();
	}

	public void setPracticeAreaCssColor(String practiceAreaColorCss) {
		this.practiceAreaColorCss = normalizeColor(practiceAreaColorCss, "#CBD5E1");
		refreshSurfaceStyle();
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
		getStyleClass().add("case-card");
		practiceAreaBar.getStyleClass().add("case-card__practice-area-bar");
		bodyPane.getStyleClass().add("case-card__body");
		indicatorRow.getStyleClass().add("case-card__indicator-row");
		statusLabel.getStyleClass().add("case-card__status-label");
		attorneyDot.getStyleClass().add("case-card__attorney-dot");
		setBackgroundCssColor(null);
		// Title / attorney styles exactly like your snippet
		attorneyLabel.setStyle("-fx-font-size: 12px; -fx-opacity: 0.75;");

		intakeLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: rgba(17,37,66,0.72);");
		solLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: rgba(17,37,66,0.72);");

		datesBox.getChildren().setAll(intakeLabel, solLabel);

		VBox.setVgrow(bodySpacer, Priority.ALWAYS);
		HBox.setHgrow(bodyPane, Priority.ALWAYS);
		HBox.setHgrow(headerSpacer, Priority.ALWAYS);
		indicatorRow.setAlignment(Pos.CENTER_RIGHT);
		indicatorRow.getChildren().setAll(statusLabel, attorneyDot);
		statusLabel.setWrapText(true);
		statusLabel.setMaxWidth(96);
		headerRow.getChildren().setAll(titleLabel, headerSpacer, indicatorRow);
		bodyPane.getChildren().setAll(headerRow, attorneyLabel, bodySpacer, datesBox);
		getChildren().setAll(cardRow);
		cardRow.getChildren().setAll(practiceAreaBar, bodyPane);

		// Nice UX: looks clickable
		setCursor(Cursor.HAND);
		applyCompact();
	}

	private void wireEvents() {
		setOnMouseEntered(e -> {
			hovered = true;
			refreshSurfaceStyle();
		});
		setOnMouseExited(e -> {
			hovered = false;
			refreshSurfaceStyle();
		});
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

	private void refreshSurfaceStyle() {
		setStyle(CardSurfaceStyles.cardContainerStyle(null, hovered));
		practiceAreaBar.setStyle("""
				-fx-background-color: %s;
				-fx-background-radius: 14 0 0 14;
				""".formatted(practiceAreaColorCss));
		bodyPane.setStyle("-fx-background-color: transparent;");
		statusLabel.setStyle("""
				%s
				-fx-text-fill: %s;
				-fx-background-color: %s;
				-fx-background-radius: 999;
				-fx-border-color: rgba(7, 23, 44, 0.12);
				-fx-border-radius: 999;
				-fx-border-width: 1;
				-fx-padding: 3 8 3 8;
				""".formatted(statusLabelBaseStyle, readableTextColor(statusColorCss), statusColorCss));
		attorneyDot.setStyle("""
				-fx-background-color: %s;
				-fx-background-radius: 999;
				-fx-border-color: rgba(255,255,255,0.9);
				-fx-border-radius: 999;
				-fx-border-width: 2;
				-fx-effect: dropshadow(gaussian, rgba(7, 23, 44, 0.26), 8, 0.18, 0, 1);
				""".formatted(attorneyColorCss));
	}

	public static String normalizeColor(String dbColor, String fallback) {
		String normalized = com.shale.ui.util.ColorUtil.toCssBackgroundColorOrNull(dbColor);
		if (normalized != null)
			return normalized;
		normalized = com.shale.ui.util.ColorUtil.toCssBackgroundColorOrNull(fallback);
		return normalized == null ? "#F1F5F9" : normalized;
	}

	public static String readableTextColor(String backgroundColor) {
		try {
			Color color = com.shale.ui.util.ColorUtil.toFxColor(backgroundColor);
			double luminance = 0.2126 * color.getRed() + 0.7152 * color.getGreen() + 0.0722 * color.getBlue();
			return luminance < 0.55 ? "#FFFFFF" : "#112542";
		} catch (Exception ignored) {
			return "#112542";
		}
	}
}
