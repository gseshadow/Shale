package com.shale.ui.component.factory;

import com.shale.core.dto.MaterialRequestSummaryDto;
import com.shale.ui.util.ColorUtil;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Canonical display-only card factory for material request summaries.
 * Controllers supply fully hydrated summary DTOs and own all service calls.
 */
public final class MaterialRequestCardFactory {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a");
    static final String NEUTRAL_STATUS_COLOR = "#E2E8F0";

    public enum Variant { LIST }

    private final Consumer<Long> onOpenRequest;

    public MaterialRequestCardFactory() { this(null); }

    public MaterialRequestCardFactory(Consumer<Long> onOpenRequest) {
        this.onOpenRequest = onOpenRequest;
    }

    public Node create(MaterialRequestSummaryDto request, Variant variant) {
        Objects.requireNonNull(request, "request");
        if (variant != Variant.LIST) throw new IllegalArgumentException("Unsupported material request card variant: " + variant);

        String accent = ColorUtil.toCssBackgroundColor(request.materialTypeColor());
        VBox body = new VBox(7);
        body.setPadding(new Insets(10, 12, 10, 12));
        body.setMinWidth(0);
        HBox.setHgrow(body, Priority.ALWAYS);

        Label title = new Label(nvl(request.title(), nvl(request.materialTypeName(), "Material Request #" + request.id())));
        title.setWrapText(true);
        title.setMaxWidth(Double.MAX_VALUE);
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: 800; -fx-text-fill: #112542;");

        HBox header = new HBox(8, title, spacer(), materialTypePill(request.materialTypeName(), request.materialTypeColor()), statusPill(request.status(), null));
        header.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(title, Priority.ALWAYS);

        GridPane facts = new GridPane();
        facts.setHgap(16);
        facts.setVgap(5);
        addFact(facts, 0, "Requested From", source(request));
        addFact(facts, 1, "Requested By", request.requestedByDisplayName());
        addFact(facts, 2, "Assigned To", request.assignedToDisplayName());
        addFact(facts, 3, "Requested", fmt(request.requestedAt()));
        addFact(facts, 4, "Due", fmt(request.expectedResponseDate()));
        addFact(facts, 5, "Next Follow-up", fmt(request.nextFollowUpAt()));

        Label timing = dueIndicator(request);
        body.getChildren().add(header);
        if (timing != null) body.getChildren().add(timing);
        body.getChildren().add(facts);

        Region rail = new Region();
        rail.setMinWidth(7); rail.setPrefWidth(7); rail.setMaxWidth(7);
        rail.setStyle("-fx-background-color: " + accent + "; -fx-background-radius: 12 0 0 12;");
        HBox.setMargin(rail, new Insets(8, 0, 8, 8));

        HBox card = new HBox(0, rail, body);
        card.getStyleClass().addAll("shale-entity-card", "material-request-card");
        card.setMinWidth(0);
        card.setMaxWidth(Double.MAX_VALUE);
        card.setStyle("-fx-background-color: " + ColorUtil.toCssRgba(request.materialTypeColor(), 0.08) + "; -fx-background-radius: 12; -fx-border-radius: 12; -fx-border-color: rgba(15,23,42,0.12); -fx-border-width: 1; -fx-effect: dropshadow(gaussian, rgba(13,33,64,0.08), 8, 0.12, 0, 1);");
        if (onOpenRequest != null) {
            card.getStyleClass().add("shale-entity-card-clickable");
            card.setCursor(Cursor.HAND);
            card.setOnMouseClicked(e -> onOpenRequest.accept(request.id()));
        }
        return card;
    }

    static Label materialTypePill(String name, String color) {
        return StatusIndicatorFactory.createStatusPill(nvl(name, "Material"), color, StatusIndicatorFactory.PillSize.COMPACT);
    }

    static Label statusPill(String status, String configuredColor) {
        return StatusIndicatorFactory.createStatusPill(nvl(status, "Unknown"), nvl(configuredColor, NEUTRAL_STATUS_COLOR), StatusIndicatorFactory.PillSize.COMPACT);
    }

    private static void addFact(GridPane grid, int index, String label, String value) {
        if (value == null || value.isBlank()) return;
        VBox fact = new VBox(1);
        Label k = new Label(label);
        k.setStyle("-fx-font-size: 10px; -fx-font-weight: 800; -fx-text-fill: rgba(17,37,66,0.62);");
        Label v = new Label(value.trim());
        v.setWrapText(true);
        v.setStyle("-fx-font-size: 12px; -fx-text-fill: rgba(17,37,66,0.90);");
        fact.getChildren().addAll(k, v);
        grid.add(fact, index % 3, index / 3);
    }

    private static Label dueIndicator(MaterialRequestSummaryDto request) {
        LocalDate today = LocalDate.now();
        if (request.expectedResponseDate() != null && request.expectedResponseDate().toLocalDate().isBefore(today) && !terminal(request.status())) {
            return notice("Overdue since " + fmt(request.expectedResponseDate()));
        }
        if (request.nextFollowUpAt() != null && !request.nextFollowUpAt().toLocalDate().isAfter(today) && !terminal(request.status())) {
            return notice("Follow-up due " + fmt(request.nextFollowUpAt()));
        }
        return null;
    }

    private static Label notice(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setStyle("-fx-font-size: 11px; -fx-font-weight: 800; -fx-text-fill: #92400E;");
        return label;
    }

    private static Region spacer() { Region r = new Region(); HBox.setHgrow(r, Priority.ALWAYS); return r; }
    private static boolean terminal(String s) { String v = s == null ? "" : s.trim().toUpperCase(); return v.equals("CLOSED") || v.equals("CANCELLED"); }
    private static String fmt(LocalDateTime t) { return t == null ? null : (t.toLocalTime().equals(java.time.LocalTime.MIDNIGHT) ? DATE_FORMAT.format(t) : DATE_TIME_FORMAT.format(t)); }
    private static String source(MaterialRequestSummaryDto r) { return nvl(r.requestedFromContactDisplayName(), nvl(r.requestedFromOrganizationName(), r.requestedFromText())); }
    private static String nvl(String s, String fallback) { return s == null || s.isBlank() ? fallback : s.trim(); }
}
