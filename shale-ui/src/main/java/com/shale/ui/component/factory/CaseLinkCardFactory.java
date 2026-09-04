package com.shale.ui.component.factory;

import java.util.Objects;
import java.util.function.Consumer;

import com.shale.core.dto.CaseLinkDto;
import com.shale.core.dto.CaseLinkShareDto;
import com.shale.ui.component.ContactCard;
import com.shale.ui.util.ActionButtonFactory;
import com.shale.ui.util.ColorUtil;
import com.shale.ui.util.ControlStyles;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Display-focused Case Link card factory; persistence remains in the Case controller/service port. */
public final class CaseLinkCardFactory {
    public enum Variant { FULL, COMPACT, MINI }

    public record Actions(Runnable open, Runnable edit, Runnable setPrimary, Runnable delete) {
        public Actions {
            open = open == null ? () -> {} : open;
            edit = edit == null ? () -> {} : edit;
            setPrimary = setPrimary == null ? () -> {} : setPrimary;
            delete = delete == null ? () -> {} : delete;
        }
    }

    public Node create(CaseLinkDto link, Variant variant, Actions actions) {
        return create(link, variant, actions, null, true);
    }

    public Node createReadOnly(CaseLinkDto link, Variant variant, Actions actions, Consumer<Integer> onOpenContact) {
        return create(link, variant, actions, onOpenContact, false);
    }

    public Node create(CaseLinkDto link, Variant variant, Actions actions, Consumer<Integer> onOpenContact) {
        return create(link, variant, actions, onOpenContact, true);
    }

    private Node create(CaseLinkDto link, Variant variant, Actions actions, Consumer<Integer> onOpenContact, boolean showManagementActions) {
        Objects.requireNonNull(link, "link");
        Objects.requireNonNull(variant, "variant");
        Actions safeActions = actions == null ? new Actions(null, null, null, null, java.util.List.of()) : actions;

        VBox card = new VBox();
        card.getStyleClass().addAll("shale-entity-card", "shale-entity-card-clickable", "case-link-card", "case-link-card-" + variant.name().toLowerCase());
        switch (variant) {
            case FULL -> card.getStyleClass().addAll("shale-entity-card-full", "shale-density-comfortable");
            case COMPACT -> card.getStyleClass().addAll("shale-entity-card-compact", "shale-density-compact", "case-overview-primary-link-card");
            case MINI -> card.getStyleClass().addAll("shale-entity-card-inline", "shale-density-dense");
        }
        applyTypeColorStyle(card, link.linkTypeColor(), variant);
        card.setFocusTraversable(true);
        card.setAccessibleText("Open link " + blankTo(link.displayName(), "Untitled link"));
        if (!blank(link.url())) Tooltip.install(card, new Tooltip(link.url().trim()));
        card.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            if (!event.isConsumed()) { safeActions.open().run(); event.consume(); }
        });
        card.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (!event.isConsumed() && event.getTarget() == card && (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE)) {
                safeActions.open().run(); event.consume();
            }
        });

        if (variant == Variant.COMPACT) {
            buildCompactCard(card, link, safeActions, onOpenContact, showManagementActions);
        } else {
            buildFullOrMiniCard(card, link, variant, safeActions, onOpenContact, showManagementActions);
        }
        return card;
    }

    private static void buildFullOrMiniCard(VBox card, CaseLinkDto link, Variant variant, Actions safeActions,
            Consumer<Integer> onOpenContact, boolean showManagementActions) {
        HBox header = new HBox(8); header.setAlignment(Pos.CENTER_LEFT);
        Label title = titleLabel(link, variant);
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        Label typePill = LinkTypeIndicatorFactory.createLinkTypePill(link.linkTypeName(), link.linkTypeColor(), LinkTypeIndicatorFactory.PillSize.COMPACT);
        header.getChildren().addAll(title, spacer, typePill);
        if (variant != Variant.MINI && link.primary()) header.getChildren().add(primaryBadge());
        card.getChildren().add(header);

        if (variant != Variant.MINI) {
            card.getChildren().add(descriptionLabel(link, false));
        }
        if (variant == Variant.FULL && !blank(link.notes())) {
            Label notes = new Label("Notes: " + link.notes().trim());
            notes.setWrapText(true);
            notes.getStyleClass().addAll("case-link-card-notes", "search-summary-text");
            card.getChildren().add(notes);
        }
        if (variant == Variant.FULL) addSharedWith(card, link, false, onOpenContact); // legacy: if (variant == Variant.FULL) addSharedWith(card, link, false)
        if (showManagementActions && variant == Variant.FULL) card.getChildren().add(fullFooter(link, safeActions));
    }

    private static void buildCompactCard(VBox card, CaseLinkDto link, Actions safeActions,
            Consumer<Integer> onOpenContact, boolean showManagementActions) {
        HBox header = new HBox(6); header.setAlignment(Pos.CENTER_LEFT);
        Label title = titleLabel(link, Variant.COMPACT);
        title.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(title, Priority.ALWAYS);
        Label typePill = LinkTypeIndicatorFactory.createLinkTypePill(link.linkTypeName(), link.linkTypeColor(), LinkTypeIndicatorFactory.PillSize.COMPACT);
        header.getChildren().addAll(title, typePill);
        if (link.primary()) header.getChildren().add(primaryBadge());
        card.getChildren().add(header);

        HBox summaryRow = new HBox(8); summaryRow.setAlignment(Pos.CENTER_LEFT);
        Label description = descriptionLabel(link, true);
        description.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(description, Priority.ALWAYS);
        summaryRow.getChildren().add(description);
        if (showManagementActions) {
            Button edit = isolated(semantic("Edit", ControlStyles.Purpose.GHOST, e -> safeActions.edit().run()));
            edit.getStyleClass().add("case-link-card-compact-edit");
            summaryRow.getChildren().add(edit);
        }
        card.getChildren().add(summaryRow);

        addSharedWith(card, link, true, onOpenContact); // legacy: if (variant == Variant.COMPACT) addSharedWith(card, link, true)
    }

    private static Label titleLabel(CaseLinkDto link, Variant variant) {
        Label title = new Label(blankTo(link.displayName(), "Untitled link"));
        title.getStyleClass().addAll("case-link-card-title", "case-link-card-title-" + variant.name().toLowerCase());
        title.setWrapText(variant != Variant.MINI);
        title.setTextOverrun(OverrunStyle.ELLIPSIS);
        return title;
    }

    private static Label descriptionLabel(CaseLinkDto link, boolean compact) {
        String text = blankTo(link.description(), compact ? "No description provided" : "No description");
        Label description = new Label(text);
        description.getStyleClass().addAll("case-link-card-description", blank(link.description()) ? "case-link-card-description-empty" : "search-summary-text");
        description.setWrapText(true);
        description.setTextOverrun(OverrunStyle.ELLIPSIS);
        if (compact) {
            description.getStyleClass().add("case-link-card-description-compact");
            description.setMinHeight(Region.USE_PREF_SIZE);
            description.setPrefHeight(Region.USE_COMPUTED_SIZE);
            Tooltip.install(description, new Tooltip(text));
        }
        return description;
    }


    private static void addSharedWith(VBox card, CaseLinkDto link, boolean compact, Consumer<Integer> onOpenContact) {
        if (link.shares() == null || link.shares().isEmpty()) return;
        Label label = new Label("Shared With");
        label.getStyleClass().addAll("case-link-card-shared-with-label", "search-summary-text");
        FlowPane flow = new FlowPane(compact ? 4 : 6, compact ? 4 : 6);
        flow.getStyleClass().add("case-link-card-shared-contact-flow");
        flow.setMaxWidth(Double.MAX_VALUE);
        flow.setPrefWrapLength(compact ? 320 : 560);
        if (compact) {
            flow.getStyleClass().add("case-link-card-shared-contact-flow-compact");
            flow.getChildren().add(label);
        }
        for (CaseLinkShareDto share : link.shares()) {
            ContactCard cardNode = embeddedShareCard(share, compact, onOpenContact);
            flow.getChildren().add(cardNode);
        }
        if (compact) {
            card.getChildren().add(flow);
            return;
        }
        VBox shared = new VBox(6);
        shared.getStyleClass().addAll("case-link-card-shared-with", "case-link-card-shared-with-full");
        shared.getChildren().addAll(label, flow);
        card.getChildren().add(shared);
    }

    private static ContactCard embeddedShareCard(CaseLinkShareDto share, boolean compact, Consumer<Integer> onOpenContact) {
        ContactCardFactory factory = new ContactCardFactory(onOpenContact == null ? id -> { } : onOpenContact);
        String role = share.contactUnavailable() ? "Unavailable" : null;
        ContactCard card = factory.create(new ContactCardFactory.ContactCardModel(
                share.contactId(),
                blankTo(share.contactDisplayName(), "Contact #" + share.contactId()),
                role, null, null, java.util.List.of()), ContactCardFactory.Variant.MINI);
        boolean navigable = onOpenContact != null && !share.contactUnavailable();
        card.setInteractive(navigable);
        if (!navigable) card.setMouseTransparent(true); // display-only fallback keeps cardNode.setMouseTransparent(true) behavior
        else {
            card.setFocusTraversable(true);
            card.addEventHandler(MouseEvent.MOUSE_CLICKED, MouseEvent::consume);
            card.addEventHandler(KeyEvent.KEY_PRESSED, event -> { if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE) event.consume(); });
        }
        card.getStyleClass().addAll("shale-entity-card-embedded", "case-link-embedded-contact-card");
        card.setMinWidth(96);
        card.setMaxWidth(compact ? 150 : 180);
        return card;
    }

    private static HBox fullFooter(CaseLinkDto link, Actions actions) {
        HBox footer = new HBox(6); footer.getStyleClass().add("case-link-card-footer"); footer.setAlignment(Pos.CENTER_LEFT);
        if (!link.primary()) footer.getChildren().add(isolated(semantic("Set Primary", ControlStyles.Purpose.SECONDARY, e -> actions.setPrimary().run())));
        footer.getChildren().add(isolated(semantic("Delete", ControlStyles.Purpose.DANGER, e -> actions.delete().run())));
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        footer.getChildren().addAll(spacer, isolated(semantic("Edit", ControlStyles.Purpose.GHOST, e -> actions.edit().run())));
        return footer;
    }

    private static Button semantic(String text, ControlStyles.Purpose purpose, javafx.event.EventHandler<javafx.event.ActionEvent> handler) {
        return ActionButtonFactory.semantic(text, handler, purpose, ControlStyles.Size.SMALL);
    }

    private static Button isolated(Button button) {
        button.addEventHandler(MouseEvent.MOUSE_CLICKED, MouseEvent::consume);
        button.addEventHandler(KeyEvent.KEY_PRESSED, event -> { /* parent card ignores child key targets */ });
        return button;
    }

    private static Label primaryBadge() { Label primary = new Label("Primary"); primary.getStyleClass().addAll("shale-status-pill", "shale-status-pill-small", "case-link-primary-badge"); return primary; }

    private static void applyTypeColorStyle(VBox card, String storedColor, Variant variant) {
        String accent = ColorUtil.toCssBackgroundColor(storedColor);
        String wash = ColorUtil.toCssRgba(storedColor, switch (variant) { case FULL -> 0.14; case COMPACT -> 0.10; case MINI -> 0.07; });
        card.setStyle("-shale-link-type-accent: " + accent + "; -shale-link-type-wash: " + wash + ";");
    }

    private static boolean blank(String s) { return s == null || s.trim().isEmpty(); }
    private static String blankTo(String s, String fallback) { return blank(s) ? fallback : s.trim(); }
}
