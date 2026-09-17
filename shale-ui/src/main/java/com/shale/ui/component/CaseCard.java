package com.shale.ui.component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.function.Consumer;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;

/**
 * CaseCard - reusable VBox "card" for rendering a case summary.
 *
 * Case card surface with a neutral main body, practice-area accent bar, compact
 * status chip, and responsible-attorney color dot.
 *
 * The host screen wires navigation via setOnOpen(...)
 */
public class CaseCard extends VBox {

	private static final double FULL_CARD_MIN_WIDTH = 280;
	private static final double FULL_CARD_PREF_WIDTH = 380;
	private static final double FULL_CARD_MAX_WIDTH = 420;

	private final Label titleLabel = new Label();
	private final Label intakeLabel = new Label();
	private final Label solLabel = new Label();
	private final Label tortNoticeLabel = new Label();
	private final Label statusLabel = new Label();
	private final Label nonEngagementLabel = new Label("Non-engagement sent");
	private final Region practiceAreaBar = new Region();
	private final ContactCard attorneyMiniCard = new ContactCard();

	private final HBox cardRow = new HBox(0);
	private final VBox bodyPane = new VBox(6);
	private final HBox headerRow = new HBox(8);
	private final HBox bottomRow = new HBox(8);
	private final HBox indicatorRow = new HBox(6);
	private final HBox attorneyRow = new HBox(0);
	private final VBox datesBox = new VBox(2);
	private final Region bodySpacer = new Region();
	private final Region bottomSpacer = new Region();
	private final Region headerSpacer = new Region();
	private final Region attorneySpacer = new Region();

	private Integer caseId;
	private Consumer<Integer> onOpen;
	private String statusColorCss = "#F1F5F9";
	private String statusName = "";
	private String attorneyColorCss;
	private String practiceAreaColorCss = "#CBD5E1";
	private String statusLabelBaseStyle = "-fx-font-size: 12px; -fx-font-weight: 800;";
	private LocalDate solDate;
	private LocalDate tortNoticeDeadline;

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
		getStyleClass().removeAll("case-card-full", "case-card-compact");
		getStyleClass().add("case-card-compact");
		setSpacing(2);
		setPadding(Insets.EMPTY);
		setMinWidth(0);
		setPrefWidth(210);
		setMaxWidth(210);
		bodyPane.setSpacing(0);
		bodyPane.setPadding(new Insets(5, 7, 5, 8));
		practiceAreaBar.setPrefWidth(3);
		HBox.setMargin(practiceAreaBar, new Insets(5, 0, 5, 6));
		datesBox.setManaged(false);
		datesBox.setVisible(false);
		attorneyMiniCard.setManaged(false);
		attorneyMiniCard.setVisible(false);
		bottomRow.setManaged(false);
		bottomRow.setVisible(false);
		bodySpacer.setManaged(false);
		bodySpacer.setVisible(false);
		titleLabel.getStyleClass().add("case-card__title-mini");
		titleLabel.setWrapText(false);
		titleLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
		titleLabel.setMinWidth(0);
		titleLabel.setMaxWidth(Double.MAX_VALUE);
		statusLabelBaseStyle = "-fx-font-size: 10px; -fx-font-weight: 800;";
		attorneyMiniCard.applyCompactMini();
		attorneyMiniCard.setMaxWidth(88);
		headerRow.getChildren().setAll(titleLabel);
		bodyPane.getChildren().setAll(headerRow);
		bottomRow.setSpacing(4);
		refreshSurfaceStyle();
	}

	public void applyEmbeddedMini() {
		applyMini();
		getStyleClass().add("task-related-case-card");
		attorneyMiniCard.setManaged(true);
		attorneyMiniCard.setVisible(true);
		headerRow.setAlignment(Pos.CENTER_LEFT);
		HBox.setHgrow(headerSpacer, Priority.ALWAYS);
		headerRow.getChildren().setAll(titleLabel, headerSpacer, attorneyMiniCard);
	}

	public void applyTaskPreview() {
		getStyleClass().removeAll("case-card-full", "case-card-compact");
		getStyleClass().add("case-card-compact");
		setSpacing(2);
		setPadding(Insets.EMPTY);
		setMinWidth(0);
		setPrefWidth(210);
		setMaxWidth(Double.MAX_VALUE);
		bodyPane.setSpacing(0);
		bodyPane.setPadding(new Insets(5, 8, 5, 8));
		bodyPane.setAlignment(Pos.CENTER_LEFT);
		practiceAreaBar.setPrefWidth(3);
		HBox.setMargin(practiceAreaBar, new Insets(5, 0, 5, 6));
		datesBox.setManaged(false);
		datesBox.setVisible(false);
		bodySpacer.setManaged(false);
		bodySpacer.setVisible(false);
		attorneyMiniCard.setManaged(false);
		attorneyMiniCard.setVisible(false);
		bottomRow.setManaged(false);
		bottomRow.setVisible(false);
		statusLabel.setManaged(false);
		statusLabel.setVisible(false);
		titleLabel.getStyleClass().add("case-card__title-mini");
		titleLabel.setWrapText(false);
		titleLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
		titleLabel.setMinWidth(0);
		titleLabel.setMaxWidth(Double.MAX_VALUE);
		headerRow.setAlignment(Pos.CENTER_LEFT);
		headerRow.getChildren().setAll(titleLabel);
		bodyPane.getChildren().setAll(headerRow);
		refreshSurfaceStyle();
	}

	public void applyCompact() {
		getStyleClass().removeAll("case-card-full", "case-card-compact");
		getStyleClass().add("case-card-full");
		setSpacing(6);
		setPadding(Insets.EMPTY);
		setMinWidth(FULL_CARD_MIN_WIDTH);
		setPrefWidth(FULL_CARD_PREF_WIDTH);
		setMaxWidth(FULL_CARD_MAX_WIDTH);
		bodyPane.setSpacing(5);
		bodyPane.setPadding(new Insets(12, 14, 11, 16));
		setPracticeAreaBarWidth(7);
		HBox.setMargin(practiceAreaBar, new Insets(8, 0, 8, 8));
		datesBox.setManaged(true);
		datesBox.setVisible(true);
		attorneyMiniCard.setManaged(true);
		attorneyMiniCard.setVisible(true);
		bottomRow.setManaged(true);
		bottomRow.setVisible(true);
		bodySpacer.setManaged(true);
		bodySpacer.setVisible(true);
		titleLabel.getStyleClass().remove("case-card__title-mini");
		statusLabelBaseStyle = "-fx-font-size: 12px; -fx-font-weight: 800;";
		statusLabel.setManaged(!statusName.isBlank());
		statusLabel.setVisible(!statusName.isBlank());
		bodyPane.setAlignment(Pos.TOP_LEFT);
		headerRow.setAlignment(Pos.TOP_LEFT);
		attorneyMiniCard.applySecondaryMini();
		headerRow.getChildren().setAll(titleLabel);
		attorneyRow.getChildren().setAll(attorneySpacer, attorneyMiniCard);
		indicatorRow.getChildren().setAll(nonEngagementLabel, statusLabel);
		bottomRow.getChildren().setAll(datesBox, bottomSpacer, indicatorRow);
		bodyPane.getChildren().setAll(headerRow, attorneyRow, bodySpacer, bottomRow);
		bottomRow.setSpacing(8);
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
		titleLabel.setAccessibleText("Case: " + text);
		titleLabel.setTooltip(new Tooltip(text));
	}

	public void setResponsibleAttorney(String responsibleAttorney) {
		boolean show = responsibleAttorney != null && !responsibleAttorney.isBlank();
		String text = show ? responsibleAttorney.trim() : "";
		attorneyMiniCard.setName(text);
		attorneyRow.setManaged(show);
		attorneyRow.setVisible(show);
	}

	public void setIntakeDate(LocalDate intakeDate) {
		boolean show = intakeDate != null;
		intakeLabel.setText(show ? "Intake: " + intakeDate : "");
		intakeLabel.setManaged(show);
		intakeLabel.setVisible(show);
	}

	public void setSolDate(LocalDate solDate) {
		this.solDate = solDate;
		boolean show = solDate != null;
		solLabel.setText(show ? "SOL: " + solDate : "");
		solLabel.setManaged(show);
		solLabel.setVisible(show);
		refreshSolStyle();
	}

	public void setTortNoticeDeadline(LocalDate tortNoticeDeadline) {
		this.tortNoticeDeadline = tortNoticeDeadline;
		boolean show = tortNoticeDeadline != null;
		tortNoticeLabel.setText(show ? "TCN: " + tortNoticeDeadline : "");
		tortNoticeLabel.setManaged(show);
		tortNoticeLabel.setVisible(show);
		refreshTortNoticeStyle();
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
		this.statusName = statusName == null ? "" : statusName.trim();
		boolean show = !this.statusName.isBlank();
		statusLabel.setText(this.statusName);
		statusLabel.setAccessibleText(show ? "Case status: " + this.statusName : "Case status unavailable");
		statusLabel.setManaged(show);
		statusLabel.setVisible(show);
		setLifecycleStyle("case-card-closed", normalizedStatusContains("closed", "inactive"));
		setLifecycleStyle("case-card-denied", normalizedStatusContains("denied", "declined", "rejected"));
		refreshSurfaceStyle();
	}

	public void setNonEngagementLetterSent(Boolean sent) {
		boolean show = Boolean.TRUE.equals(sent);
		nonEngagementLabel.setManaged(show);
		nonEngagementLabel.setVisible(show);
		setLifecycleStyle("case-card-non-engagement", show);
	}

	public void setStatusCssColor(String statusColorCss) {
		this.statusColorCss = normalizeColor(statusColorCss, "#F1F5F9");
		refreshSurfaceStyle();
	}

	public void setAttorneyDotCssColor(String attorneyColorCss) {
		this.attorneyColorCss = com.shale.ui.util.ColorUtil.toCssBackgroundColorOrNull(attorneyColorCss);
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
		getStyleClass().addAll("case-card", "case-card-neutral", "shale-entity-card", "shale-entity-card-clickable", "shale-interactive-card");
		practiceAreaBar.getStyleClass().addAll("case-card__practice-area-bar", "shale-indicator-practice-area");
		bodyPane.getStyleClass().add("case-card__body");
		bottomRow.getStyleClass().add("case-card__bottom-row");
		statusLabel.getStyleClass().addAll("case-card__status-label", "shale-status-pill", "shale-status-pill-compact");
		nonEngagementLabel.getStyleClass().addAll("case-card__non-engagement", "shale-semantic-chip", "shale-semantic-chip-warning");
		nonEngagementLabel.setManaged(false);
		nonEngagementLabel.setVisible(false);
		titleLabel.getStyleClass().add("case-card__title");
		intakeLabel.getStyleClass().addAll("case-card__date", "shale-metadata");
		solLabel.getStyleClass().addAll("case-card__deadline", "shale-metadata");
		tortNoticeLabel.getStyleClass().addAll("case-card__deadline", "shale-metadata");
		attorneyMiniCard.getStyleClass().add("case-card__attorney-mini-card");
		setBackgroundCssColor(null);
		refreshSolStyle();
		refreshTortNoticeStyle();
		tortNoticeLabel.setManaged(false);
		tortNoticeLabel.setVisible(false);

		datesBox.getChildren().setAll(intakeLabel, solLabel, tortNoticeLabel);

		VBox.setVgrow(bodySpacer, Priority.ALWAYS);
		HBox.setHgrow(bodyPane, Priority.ALWAYS);
		HBox.setHgrow(headerSpacer, Priority.ALWAYS);
		HBox.setHgrow(attorneySpacer, Priority.ALWAYS);
		HBox.setHgrow(bottomSpacer, Priority.ALWAYS);
		HBox.setHgrow(practiceAreaBar, Priority.NEVER);
		headerRow.setAlignment(Pos.TOP_LEFT);
		attorneyRow.setAlignment(Pos.CENTER_RIGHT);
		bottomRow.setAlignment(Pos.BOTTOM_LEFT);
		statusLabel.setWrapText(true);
		statusLabel.setMaxWidth(128);
		statusLabel.setAlignment(Pos.CENTER_RIGHT);
		datesBox.setMinWidth(118);
		datesBox.setFillWidth(true);
		titleLabel.setMinWidth(0);
		titleLabel.setMaxWidth(Double.MAX_VALUE);
		HBox.setHgrow(titleLabel, Priority.ALWAYS);
		headerRow.getChildren().setAll(titleLabel, headerSpacer, attorneyMiniCard);
		indicatorRow.setAlignment(Pos.CENTER_RIGHT);
		indicatorRow.getChildren().setAll(nonEngagementLabel, statusLabel);
		bottomRow.getChildren().setAll(datesBox, bottomSpacer, indicatorRow);
		bodyPane.getChildren().setAll(headerRow, bodySpacer, bottomRow);
		getChildren().setAll(cardRow);
		cardRow.getChildren().setAll(practiceAreaBar, bodyPane);
		setPracticeAreaBarWidth(7);
		HBox.setMargin(practiceAreaBar, new Insets(8, 0, 8, 8));

		// Nice UX: looks clickable
		setCursor(Cursor.HAND);
		setFocusTraversable(true);
		applyCompact();
	}

	private void setPracticeAreaBarWidth(double width) {
		practiceAreaBar.setMinWidth(width);
		practiceAreaBar.setPrefWidth(width);
		practiceAreaBar.setMaxWidth(width);
	}

	private void wireEvents() {
		setOnMouseClicked(e ->
		{
			if (e.getButton() == MouseButton.PRIMARY && e.isStillSincePress() && !isEmbeddedAction(e.getTarget())
					&& onOpen != null && caseId != null) {
				onOpen.accept(caseId);
				e.consume();
			}
		});
		setOnKeyPressed(e -> {
			if ((e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.SPACE)
					&& onOpen != null && caseId != null) {
				onOpen.accept(caseId);
				e.consume();
			}
		});
	}

	private boolean isEmbeddedAction(Object target) {
		Node node = target instanceof Node n ? n : null;
		while (node != null && node != this) {
			if (node instanceof javafx.scene.control.ButtonBase || node instanceof javafx.scene.control.TextInputControl
					|| node instanceof javafx.scene.control.ComboBoxBase<?> || node instanceof javafx.scene.control.Hyperlink) return true;
			node = node.getParent();
		}
		return false;
	}

	/*
	 * ----------------------------- Helper: if you want to embed the card as Node easily
	 * -----------------------------
	 */
	public Node asNode() {
		return this;
	}

	private void refreshSurfaceStyle() {
		setStyle("");
		practiceAreaBar.setStyle("""
				-fx-background-color: %s;
				-fx-background-radius: 999;
				""".formatted(practiceAreaColorCss));
		bodyPane.setStyle("-fx-background-color: transparent;");
		statusLabel.setStyle(StatusPillStyles.pillStyle(statusLabelBaseStyle, statusColorCss));
		attorneyMiniCard.setBackgroundCssColor(attorneyColorCss);
	}

	public static String normalizeColor(String dbColor, String fallback) {
		String normalized = com.shale.ui.util.ColorUtil.toCssBackgroundColorOrNull(dbColor);
		if (normalized != null)
			return normalized;
		normalized = com.shale.ui.util.ColorUtil.toCssBackgroundColorOrNull(fallback);
		return normalized == null ? "#F1F5F9" : normalized;
	}

	private void refreshSolStyle() {
		applyDeadlineState(solLabel, solDate);
	}

	private void refreshTortNoticeStyle() {
		applyDeadlineState(tortNoticeLabel, tortNoticeDeadline);
	}

	private static void applyDeadlineState(Label label, LocalDate deadline) {
		label.getStyleClass().removeAll("case-card__deadline-warning", "case-card__deadline-urgent");
		if (deadline == null) return;
		long days = ChronoUnit.DAYS.between(LocalDate.now(), deadline);
		if (days < 30) label.getStyleClass().add("case-card__deadline-urgent");
		else if (days <= 180) label.getStyleClass().add("case-card__deadline-warning");
	}

	private boolean normalizedStatusContains(String... terms) {
		String normalized = statusName.toLowerCase(java.util.Locale.ROOT);
		for (String term : terms) if (normalized.contains(term)) return true;
		return false;
	}

	private void setLifecycleStyle(String styleClass, boolean enabled) {
		getStyleClass().remove(styleClass);
		if (enabled) getStyleClass().add(styleClass);
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
