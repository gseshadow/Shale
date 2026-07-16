package com.shale.ui.component.factory;

import com.shale.core.dto.CaseLinkDto;
import com.shale.ui.util.ActionButtonFactory;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Display-focused Case Link card factory; persistence remains in the Case controller/service port. */
public final class CaseLinkCardFactory {
    public record Actions(Runnable open, Runnable edit, Runnable setPrimary, Runnable moveUp, Runnable moveDown, Runnable delete) {}

    public Node create(CaseLinkDto link, int index, int total, Actions actions) {
        VBox card = new VBox(7);
        card.getStyleClass().addAll("shale-entity-card", "shale-entity-card-compact", "shale-density-compact", "case-link-card");
        HBox header = new HBox(8); header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label(blankTo(link.displayName(), "Untitled link")); title.getStyleClass().add("app-dialog-field-label"); title.setWrapText(true);
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(title, spacer, LinkTypeIndicatorFactory.createLinkTypePill(link.linkTypeName(), link.linkTypeColor(), LinkTypeIndicatorFactory.PillSize.COMPACT));
        if (link.primary()) { Label primary = new Label("Primary"); primary.getStyleClass().addAll("shale-status-pill", "shale-status-pill-small"); header.getChildren().add(primary); }
        Label url = new Label(blankTo(link.url(), "—")); url.getStyleClass().add("search-summary-text"); url.setWrapText(true);
        card.getChildren().addAll(header, url);
        if (!blank(link.description())) { Label d = new Label(link.description().trim()); d.setWrapText(true); d.getStyleClass().add("search-summary-text"); card.getChildren().add(d); }
        if (!blank(link.notes())) { Label n = new Label("Notes: " + link.notes().trim()); n.setWrapText(true); n.getStyleClass().add("search-summary-text"); card.getChildren().add(n); }
        HBox buttons = new HBox(6); buttons.setAlignment(Pos.CENTER_LEFT);
        Button open = ActionButtonFactory.cardAction("Open Link", e -> actions.open().run());
        Button edit = ActionButtonFactory.cardAction("Edit", e -> actions.edit().run());
        Button primary = ActionButtonFactory.cardAction("Set Primary", e -> actions.setPrimary().run()); primary.setVisible(!link.primary()); primary.setManaged(!link.primary());
        Button up = ActionButtonFactory.cardAction("Move Up", e -> actions.moveUp().run()); up.setDisable(index <= 0);
        Button down = ActionButtonFactory.cardAction("Move Down", e -> actions.moveDown().run()); down.setDisable(index >= total - 1);
        Button del = ActionButtonFactory.danger("Delete", e -> actions.delete().run());
        buttons.getChildren().addAll(open, edit, primary, up, down, del);
        card.getChildren().add(buttons);
        return card;
    }
    private static boolean blank(String s) { return s == null || s.trim().isEmpty(); }
    private static String blankTo(String s, String fallback) { return blank(s) ? fallback : s.trim(); }
}
