package com.shale.ui.component.factory;

import com.shale.core.dto.MaterialRequestSummaryDto;
import com.shale.ui.util.ColorUtil;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

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
    static final String CARD_RADIUS = DueProximityStyles.CARD_RADIUS;
    private static final double CARD_RADIUS_PX = 14.0;

    public enum Variant { LIST }

    private final Consumer<Long> onOpenRequest;
    private final Consumer<Integer> onOpenContact;
    private final Consumer<Integer> onOpenOrganization;
    private final Consumer<Integer> onOpenUser;
    private final ContactCardFactory contactCardFactory = new ContactCardFactory(id -> { });
    private final OrganizationCardFactory organizationCardFactory = new OrganizationCardFactory(id -> { });
    private final UserCardFactory userCardFactory = new UserCardFactory(id -> { });

    public MaterialRequestCardFactory() { this(null); }

    public MaterialRequestCardFactory(Consumer<Long> onOpenRequest) {
        this(onOpenRequest, null, null, null);
    }

    public MaterialRequestCardFactory(Consumer<Long> onOpenRequest, Consumer<Integer> onOpenContact, Consumer<Integer> onOpenOrganization, Consumer<Integer> onOpenUser) {
        this.onOpenRequest = onOpenRequest;
        this.onOpenContact = onOpenContact;
        this.onOpenOrganization = onOpenOrganization;
        this.onOpenUser = onOpenUser;
    }

    public Node create(MaterialRequestSummaryDto request, Variant variant) {
        return create(request, variant, request == null ? null : request.status(), null);
    }

    public Node create(MaterialRequestSummaryDto request, Variant variant, String configuredStatusColor) {
        return create(request, variant, request == null ? null : request.status(), configuredStatusColor);
    }

    public Node create(MaterialRequestSummaryDto request, Variant variant, String statusDisplayName, String configuredStatusColor) {
        Objects.requireNonNull(request, "request");
        if (variant != Variant.LIST) throw new IllegalArgumentException("Unsupported material request card variant: " + variant);

        String statusColor = resolvedStatusColor(configuredStatusColor);
        String materialTypeRailColor = ColorUtil.toCssBackgroundColor(request.materialTypeColor());
        VBox body = new VBox(7);
        body.getStyleClass().add("material-request-card__body");
        body.setPadding(new Insets(10, 12, 10, 12));
        body.setMinWidth(0);
        body.setStyle("-fx-background-color: transparent; -fx-background-radius: 0 " + CARD_RADIUS + " " + CARD_RADIUS + " 0;");
        HBox.setHgrow(body, Priority.ALWAYS);

        Label title = new Label(nvl(request.title(), nvl(request.materialTypeName(), "Material Request #" + request.id())));
        title.setWrapText(true);
        title.setMaxWidth(Double.MAX_VALUE);
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: 800; -fx-text-fill: #112542;");

        HBox header = new HBox(8, title, spacer(), materialTypePill(request.materialTypeName(), request.materialTypeColor()), statusPill(statusDisplayName, statusColor)); if(request.deleted()){Label deleted=new Label("Deleted");deleted.getStyleClass().add("shale-status-pill");deleted.setStyle("-fx-text-fill:#9b1c1c;-fx-background-color:#fee2e2;");header.getChildren().add(deleted);body.setOpacity(0.68);}
        header.setAlignment(Pos.TOP_LEFT);
        header.setMinWidth(0);
        HBox.setHgrow(title, Priority.ALWAYS);

        FlowPane facts = new FlowPane(18, 7);
        facts.getStyleClass().add("material-request-card__facts");
        facts.setAlignment(Pos.TOP_LEFT);
        facts.setColumnHalignment(javafx.geometry.HPos.LEFT);
        facts.setRowValignment(javafx.geometry.VPos.TOP);
        facts.setMinWidth(0);
        addEntityFact(facts, "Requested From", requestedFromNode(request));
        addEntityFact(facts, "Requested By", userNode(request.requestedByUserId(), request.requestedByDisplayName(), request.requestedByUserColor()));
        addEntityFact(facts, "Assigned To", userNode(request.assignedToUserId(), request.assignedToDisplayName(), request.assignedToUserColor()));
        addTextFact(facts, "Requested", fmt(request.requestedAt()));
        addTextFact(facts, "Due", fmt(request.expectedResponseDate()));
        addTextFact(facts, "Next Follow-up", fmt(request.nextFollowUpAt()));

        Label timing = dueIndicator(request);
        body.getChildren().add(header);
        if (!facts.getChildren().isEmpty()) body.getChildren().add(facts);
        if (timing != null) body.getChildren().add(timing);

        Region rail = new Region();
        rail.getStyleClass().add("material-request-card__material-type-rail");
        rail.setMinWidth(7); rail.setPrefWidth(7); rail.setMaxWidth(7);
        rail.setStyle("-fx-background-color: " + materialTypeRailColor + "; -fx-background-radius: " + CARD_RADIUS + " 0 0 " + CARD_RADIUS + ";");

        HBox card = new HBox(0, rail, body);
        card.getStyleClass().addAll("material-request-card", "material-request-list-card");
        card.setMinWidth(0);
        card.setMaxWidth(Double.MAX_VALUE);
        card.setMaxHeight(Region.USE_PREF_SIZE);
        installRoundedClip(card);
        applyCardStyle(card, statusColor, false);
        card.setOnMouseEntered(e -> applyCardStyle(card, statusColor, true));
        card.setOnMouseExited(e -> applyCardStyle(card, statusColor, false));
        if (onOpenRequest != null) {
            card.setCursor(Cursor.HAND);
            card.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY) onOpenRequest.accept(request.id());
            });
        }
        return card;
    }

    private static void applyCardStyle(HBox card, String statusColor, boolean hovered) {
        card.setStyle("-fx-background-color: " + statusWashCss(statusColor, hovered) + "; -fx-background-radius: " + CARD_RADIUS + "; -fx-border-radius: " + CARD_RADIUS + "; -fx-border-color: " + (hovered ? "rgba(74,104,138,0.34)" : "rgba(74,104,138,0.24)") + "; -fx-border-width: 1; -fx-effect: dropshadow(gaussian, " + (hovered ? "rgba(7,23,44,0.18), 24, 0.2, 0, 8" : "rgba(7,23,44,0.14), 18, 0.18, 0, 4") + ");");
    }

    static String statusWashCss(String configuredColor, boolean hovered) {
        String color = resolvedStatusColor(configuredColor);
        double leadingOpacity = hovered ? 0.20 : 0.15;
        double trailingOpacity = hovered ? 0.10 : 0.07;
        return "linear-gradient(to right, " + ColorUtil.toCssRgba(color, leadingOpacity) + " 0%, "
                + ColorUtil.toCssRgba(color, trailingOpacity) + " 30%, rgba(255,255,255,0.96) 68%, #FFFFFF 100%)";
    }

    private static String resolvedStatusColor(String configuredColor) {
        return ColorUtil.normalizeStoredColor(configuredColor) == null ? NEUTRAL_STATUS_COLOR : configuredColor;
    }

    private static void installRoundedClip(Region card) {
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(card.widthProperty());
        clip.heightProperty().bind(card.heightProperty());
        clip.setArcWidth(CARD_RADIUS_PX * 2.0);
        clip.setArcHeight(CARD_RADIUS_PX * 2.0);
        card.setClip(clip);
    }

    static Label materialTypePill(String name, String color) {
        return StatusIndicatorFactory.createStatusPill(nvl(name, "Material"), color, StatusIndicatorFactory.PillSize.COMPACT);
    }

    static Label statusPill(String status, String configuredColor) {
        return StatusIndicatorFactory.createStatusPill(nvl(status, "Unknown"), nvl(configuredColor, NEUTRAL_STATUS_COLOR), StatusIndicatorFactory.PillSize.COMPACT);
    }

    private void addEntityFact(FlowPane facts, String label, Node node) {
        if (node == null) return;
        VBox fact = factBox("material-request-card__entity-fact", 3);
        keepMiniCardCompact(node);
        fact.getChildren().addAll(key(label), node);
        facts.getChildren().add(fact);
    }

    private static void addTextFact(FlowPane facts, String label, String value) {
        if (value == null || value.isBlank()) return;
        VBox fact = factBox("material-request-card__date-fact", 1);
        Label v = valueLabel(value);
        fact.getChildren().addAll(key(label), v);
        facts.getChildren().add(fact);
    }

    private static VBox factBox(String styleClass, double spacing) {
        VBox fact = new VBox(spacing);
        fact.getStyleClass().add(styleClass);
        fact.setMinWidth(Region.USE_PREF_SIZE);
        fact.setPrefWidth(Region.USE_COMPUTED_SIZE);
        fact.setMaxWidth(Region.USE_PREF_SIZE);
        fact.setAlignment(Pos.TOP_LEFT);
        fact.setFillWidth(false);
        return fact;
    }

    private Node requestedFromNode(MaterialRequestSummaryDto r) {
        if (r.requestedFromContactId() != null && has(r.requestedFromContactDisplayName())) {
            Node card = contactCardFactory.create(new ContactCardFactory.ContactCardModel(r.requestedFromContactId(), r.requestedFromContactDisplayName(), null, null, null), ContactCardFactory.Variant.MINI);
            installEmbeddedNavigation(card, r.requestedFromContactId(), onOpenContact);
            return card;
        }
        if (r.requestedFromOrganizationId() != null && has(r.requestedFromOrganizationName())) {
            Node card = organizationCardFactory.create(new OrganizationCardFactory.OrganizationCardModel(r.requestedFromOrganizationId(), r.requestedFromOrganizationName(), null, null, null, null, null, null, null, null, null, null, null, null, null), OrganizationCardFactory.Variant.MINI);
            installEmbeddedNavigation(card, r.requestedFromOrganizationId(), onOpenOrganization);
            return card;
        }
        if (has(r.requestedFromText())) return valueLabel(r.requestedFromText());
        return null;
    }

    private Node userNode(Integer userId, String displayName, String color) {
        if (userId == null || !has(displayName)) return null;
        Node card = userCardFactory.create(new UserCardFactory.UserCardModel(userId, displayName, color, null), UserCardFactory.Variant.MINI);
        installEmbeddedNavigation(card, userId, onOpenUser);
        return card;
    }

    private static void installEmbeddedNavigation(Node node, Integer entityId, Consumer<Integer> onOpenEntity) {
        if (node == null || entityId == null || onOpenEntity == null) return;
        node.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                onOpenEntity.accept(entityId);
                e.consume();
            }
        });
    }

    private static void keepMiniCardCompact(Node node) {
        if (node instanceof Region region) {
            region.setMinWidth(Region.USE_PREF_SIZE);
            region.setPrefWidth(Region.USE_COMPUTED_SIZE);
            region.setMaxWidth(Region.USE_PREF_SIZE);
        }
        HBox.setHgrow(node, Priority.NEVER);
        VBox.setVgrow(node, Priority.NEVER);
        GridPane.setFillWidth(node, false);
        GridPane.setHgrow(node, Priority.NEVER);
    }

    private static Label key(String label) {
        Label k = new Label(label);
        k.setStyle("-fx-font-size: 10px; -fx-font-weight: 800; -fx-text-fill: rgba(17,37,66,0.62);");
        return k;
    }

    private static Label valueLabel(String value) {
        Label v = new Label(value.trim());
        v.setWrapText(true);
        v.setStyle("-fx-font-size: 12px; -fx-text-fill: rgba(17,37,66,0.90);");
        return v;
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
    private static boolean has(String s) { return s != null && !s.isBlank(); }
    private static String nvl(String s, String fallback) { return s == null || s.isBlank() ? fallback : s.trim(); }
}
