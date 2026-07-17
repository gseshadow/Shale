package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class CaseLinkSharesPhase536VisualDefectsTest {
    private static final Path CONTACT_CARD = Path.of("src/main/java/com/shale/ui/component/ContactCard.java");
    private static final Path CASE_CONTROLLER = Path.of("src/main/java/com/shale/ui/controller/CaseController.java");
    private static final Path CARDS_CSS = Path.of("src/main/resources/css/foundation/cards.css");
    private static final Path APP_CSS = Path.of("src/main/resources/css/app.css");
    private static final Path APP_DIALOGS = Path.of("src/main/java/com/shale/ui/component/dialog/AppDialogs.java");
    private static final Path DESIGN_SYSTEM = Path.of("../architecture/design-system.md");

    @Test
    void miniContactCardNamesUsePrimaryTextToken() throws IOException {
        String source = Files.readString(CONTACT_CARD);
        String css = Files.readString(CARDS_CSS);

        assertTrue(source.contains("nameLabel.getStyleClass().addAll(\"contact-card-name\", \"contact-card-name-mini\")"));
        assertTrue(source.contains("nameLabel.getStyleClass().addAll(\"contact-card-name\", \"contact-card-name-compact-mini\")"));
        assertTrue(source.contains("nameLabel.getStyleClass().addAll(\"contact-card-name\", \"contact-card-name-secondary-mini\")"));
        assertTrue(source.contains("resetNameLabelVariantStyles()"));
        assertTrue(css.contains(".contact-card-name"));
        assertTrue(css.contains("-fx-text-fill: -shale-color-text-primary;"));
        assertTrue(css.contains(".contact-card-name-compact-mini"));
        assertTrue(css.contains(".contact-card-name-secondary-mini"));
    }

    @Test
    void variantSwitchingClearsMiniTextClassesAndInlineStyles() throws IOException {
        String source = Files.readString(CONTACT_CARD);

        assertTrue(source.contains("nameLabel.getStyleClass().removeAll(\"contact-card-name\", \"contact-card-name-mini\", \"contact-card-name-compact-mini\", \"contact-card-name-secondary-mini\")"));
        assertTrue(source.contains("nameLabel.setStyle(null);"));
        assertTrue(source.contains("nameLabel.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);"));
        assertTrue(source.contains("nameLabel.setTextOverrun(javafx.scene.control.OverrunStyle.CLIP);"));
    }

    @Test
    void unselectedSelectableContactDoesNotCreateEmptyCheckmarkBar() throws IOException {
        String source = Files.readString(CASE_CONTROLLER);
        String method = source.substring(source.indexOf("private Node createSelectableContactCard"), source.indexOf("private void toggleWorking"));

        assertTrue(method.contains("StackPane wrapper = new StackPane(card);"));
        assertTrue(method.contains("if (selected)"));
        assertTrue(method.contains("Label check = new Label(\"✓\")"));
        assertFalse(method.contains("new Label(selected ? \"✓\" : \"\")"));
        assertTrue(method.contains("StackPane.setMargin(check, new Insets(5, 5, 0, 0));"));
        assertTrue(method.contains("check.setAccessibleText(\"Selected\")"));
    }

    @Test
    void listCellReuseResetsSelectedGraphicAndAccessibilityState() throws IOException {
        String source = Files.readString(CASE_CONTROLLER);
        String cellFactory = source.substring(source.indexOf("allList.setCellFactory"), source.indexOf("allList.setOnMouseClicked"));

        assertTrue(cellFactory.contains("getStyleClass().removeAll(\"case-link-contact-cell-selected\")"));
        assertTrue(cellFactory.contains("setText(null)"));
        assertTrue(cellFactory.contains("setGraphic(null)"));
        assertTrue(cellFactory.contains("setAccessibleText(null)"));
        assertTrue(cellFactory.contains("if (selected) getStyleClass().add(\"case-link-contact-cell-selected\")"));
        assertTrue(cellFactory.contains("setAccessibleText((selected ? \"Selected \" : \"Not selected \")"));
    }

    @Test
    void caseLinkDialogsUseRoundedTransparentShellContract() throws IOException {
        String source = Files.readString(CASE_CONTROLLER);
        String css = Files.readString(APP_CSS);
        String dialogs = Files.readString(APP_DIALOGS);

        assertTrue(source.contains("dialog.getDialogPane().getStyleClass().add(\"case-link-dialog-shell\")"));
        assertTrue(css.contains(".dialog-pane.secondary-window-shell.case-link-dialog-shell"));
        assertTrue(css.contains("-fx-background-radius: 14;"));
        assertTrue(css.contains(".dialog-pane.secondary-window-shell.case-link-dialog-shell > .button-bar"));
        assertTrue(css.contains("-fx-background-radius: 0 0 14 14;"));
        assertTrue(dialogs.contains("newScene.setFill(Color.TRANSPARENT)"));
    }

    @Test
    void designSystemDocumentsPhase536VisualContract() throws IOException {
        String docs = Files.readString(DESIGN_SYSTEM);

        assertTrue(docs.contains("MINI Contact Cards use primary text"));
        assertTrue(docs.contains("Unselected selectable MINI Contact Cards show no indicator"));
        assertTrue(docs.contains("transparent secondary dialog shell"));
        assertTrue(docs.contains("fixed footer"));
    }
}
