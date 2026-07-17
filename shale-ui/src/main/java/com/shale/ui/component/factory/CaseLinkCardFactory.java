package com.shale.ui.component.factory;

import java.util.Objects;

import com.shale.core.dto.CaseLinkDto;
import com.shale.ui.util.ActionButtonFactory;
import com.shale.ui.util.ColorUtil;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
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
        Objects.requireNonNull(link, "link");
        Objects.requireNonNull(variant, "variant");
        Actions safeActions = actions == null ? new Actions(null, null, null, null) : actions;

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

        HBox header = new HBox(8); header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label(blankTo(link.displayName(), "Untitled link"));
        title.getStyleClass().addAll("case-link-card-title", "case-link-card-title-" + variant.name().toLowerCase());
        title.setWrapText(variant != Variant.MINI);
        title.setTextOverrun(OverrunStyle.ELLIPSIS);
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        Label typePill = LinkTypeIndicatorFactory.createLinkTypePill(link.linkTypeName(), link.linkTypeColor(), LinkTypeIndicatorFactory.PillSize.COMPACT);
        header.getChildren().addAll(title, spacer, typePill);
        if (variant != Variant.MINI && link.primary()) header.getChildren().add(primaryBadge());
        card.getChildren().add(header);

        if (variant != Variant.MINI) {
            Label description = new Label(blankTo(link.description(), "No description"));
            description.getStyleClass().addAll("case-link-card-description", blank(link.description()) ? "case-link-card-description-empty" : "search-summary-text");
            description.setWrapText(true);
            card.getChildren().add(description);
        }
        if (variant == Variant.FULL && !blank(link.notes())) {
            Label notes = new Label("Notes: " + link.notes().trim());
            notes.setWrapText(true);
            notes.getStyleClass().addAll("case-link-card-notes", "search-summary-text");
            card.getChildren().add(notes);
        }
        if (variant == Variant.FULL) card.getChildren().add(fullFooter(link, safeActions));
        if (variant == Variant.COMPACT) card.getChildren().add(compactFooter(safeActions));
        return card;
    }

    private static HBox fullFooter(CaseLinkDto link, Actions actions) {
        HBox footer = new HBox(6); footer.getStyleClass().add("case-link-card-footer"); footer.setAlignment(Pos.CENTER_LEFT);
        if (!link.primary()) footer.getChildren().add(isolated(ActionButtonFactory.cardAction("Set Primary", e -> actions.setPrimary().run())));
        footer.getChildren().add(isolated(ActionButtonFactory.danger("Delete", e -> actions.delete().run())));
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        footer.getChildren().addAll(spacer, isolated(ActionButtonFactory.cardAction("Edit", e -> actions.edit().run())));
        return footer;
    }

    private static HBox compactFooter(Actions actions) {
        HBox footer = new HBox(6); footer.getStyleClass().add("case-link-card-footer"); footer.setAlignment(Pos.CENTER_RIGHT);
        footer.getChildren().add(isolated(ActionButtonFactory.cardAction("Edit", e -> actions.edit().run())));
        return footer;
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
