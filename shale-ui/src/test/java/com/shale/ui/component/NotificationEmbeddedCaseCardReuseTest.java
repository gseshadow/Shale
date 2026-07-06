package com.shale.ui.component;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class NotificationEmbeddedCaseCardReuseTest {

    @Test
    void overviewAndNotificationPreviewsUseSharedEmbeddedVariant() throws Exception {
        String notificationFactory = Files.readString(Path.of("src/main/java/com/shale/ui/component/factory/NotificationCardFactory.java"));
        String myShaleController = Files.readString(Path.of("src/main/java/com/shale/ui/controller/MyShaleController.java"));
        String caseCardFactory = Files.readString(Path.of("src/main/java/com/shale/ui/component/factory/CaseCardFactory.java"));

        assertTrue(myShaleController.contains("CaseCardFactory.Variant.EMBEDDED"),
                "My Shale Overview task lanes should use the explicit embedded case-card variant.");
        assertTrue(notificationFactory.contains("CaseCardFactory.Variant.EMBEDDED"),
                "Notification previews should use the same explicit embedded case-card variant.");
        assertTrue(caseCardFactory.contains("EMBEDDED"),
                "CaseCardFactory should expose an explicit embedded variant.");
        assertTrue(caseCardFactory.contains("case EMBEDDED -> card.applyEmbeddedMini();"),
                "The embedded variant should own the embedded mini behavior.");
        assertFalse(caseCardFactory.contains("createEmbeddedTaskCaseCard("),
                "Embedded rendering should not stay hidden behind a task-specific helper.");
        assertFalse(notificationFactory.contains("createEmbeddedTaskCaseCard("),
                "Notifications should not call a task-specific compatibility helper.");
    }

    @Test
    void embeddedVariantKeepsOverviewRootAndChildStyleClasses() throws Exception {
        String caseCard = Files.readString(Path.of("src/main/java/com/shale/ui/component/CaseCard.java"));
        String embeddedBlock = caseCard.substring(caseCard.indexOf("public void applyEmbeddedMini()"), caseCard.indexOf("public void applyTaskPreview()"));
        String buildBlock = caseCard.substring(caseCard.indexOf("private void buildUi()"), caseCard.indexOf("private void setPracticeAreaBarWidth"));

        assertTrue(buildBlock.contains("getStyleClass().addAll(\"case-card\", \"shale-entity-card\", \"shale-entity-card-clickable\")"),
                "Embedded cards should retain the CaseCard root style classes.");
        assertTrue(embeddedBlock.contains("getStyleClass().add(\"task-related-case-card\")"),
                "Embedded cards should retain the existing Overview task-related root marker used for click behavior.");
        assertTrue(buildBlock.contains("practiceAreaBar.getStyleClass().addAll(\"case-card__practice-area-bar\", \"shale-indicator-practice-area\")"),
                "Embedded cards should retain the left practice-area color-bar style classes.");
        assertTrue(buildBlock.contains("attorneyMiniCard.getStyleClass().add(\"case-card__attorney-mini-card\")"),
                "Embedded cards should retain the right attorney pill child style class.");
        assertTrue(embeddedBlock.contains("attorneyMiniCard.setManaged(true);")
                && embeddedBlock.contains("attorneyMiniCard.setVisible(true);"),
                "Embedded cards must not regress to the plain title-only MINI preview.");
        assertTrue(embeddedBlock.contains("headerRow.getChildren().setAll(titleLabel, headerSpacer, attorneyMiniCard);"),
                "Embedded cards should include the title and right-side attorney pill like Overview.");
    }

    @Test
    void miniRemainsSeparateFromEmbeddedVariant() throws Exception {
        String caseCard = Files.readString(Path.of("src/main/java/com/shale/ui/component/CaseCard.java"));
        String factory = Files.readString(Path.of("src/main/java/com/shale/ui/component/factory/CaseCardFactory.java"));
        String miniBlock = caseCard.substring(caseCard.indexOf("public void applyMini()"), caseCard.indexOf("public void applyEmbeddedMini()"));
        String embeddedBlock = caseCard.substring(caseCard.indexOf("public void applyEmbeddedMini()"), caseCard.indexOf("public void applyTaskPreview()"));

        assertTrue(factory.contains("case MINI -> card.applyMini();"),
                "MINI should still route to the standalone mini behavior.");
        assertTrue(factory.contains("case EMBEDDED -> card.applyEmbeddedMini();"),
                "EMBEDDED should route to the embedded mini behavior.");
        assertTrue(miniBlock.contains("attorneyMiniCard.setManaged(false);\n\t\tattorneyMiniCard.setVisible(false);"),
                "MINI should keep hiding the attorney mini card.");
        assertTrue(miniBlock.contains("headerRow.getChildren().setAll(titleLabel);"),
                "MINI should keep its title-only header structure.");
        assertTrue(embeddedBlock.contains("attorneyMiniCard.setManaged(true);"),
                "EMBEDDED should opt back into the attorney mini card.");
    }

    @Test
    void notificationEmbeddedCaseModelPassesSameVisualFieldsAsMyShaleTaskCards() throws Exception {
        String notificationFactory = Files.readString(Path.of("src/main/java/com/shale/ui/component/factory/NotificationCardFactory.java"));
        String myShaleController = Files.readString(Path.of("src/main/java/com/shale/ui/controller/MyShaleController.java"));
        String durableService = Files.readString(Path.of("src/main/java/com/shale/ui/notification/DurableNotificationService.java"));
        String appNotification = Files.readString(Path.of("src/main/java/com/shale/ui/notification/AppNotification.java"));

        assertTrue(myShaleController.contains("task.casePrimaryStatusName()")
                        && myShaleController.contains("task.casePrimaryStatusColor()")
                        && myShaleController.contains("task.casePracticeAreaColor()"),
                "My Shale task-card previews pass status and practice-area visual fields into TaskCardModel.");
        assertTrue(notificationFactory.contains("item.getCasePrimaryStatusName()")
                        && notificationFactory.contains("item.getCasePrimaryStatusColor()")
                        && notificationFactory.contains("item.getCasePracticeAreaColor()"),
                "Notification embedded case previews should pass the same visual fields into CaseCardModel.");
        assertTrue(durableService.contains("row.casePrimaryStatusName()")
                        && durableService.contains("row.casePrimaryStatusColor()")
                        && durableService.contains("row.casePracticeAreaColor()"),
                "Durable notification hydration should copy case visual fields onto AppNotification.");
        assertTrue(appNotification.contains("getCasePrimaryStatusName()")
                        && appNotification.contains("getCasePrimaryStatusColor()")
                        && appNotification.contains("getCasePracticeAreaColor()"),
                "AppNotification should expose hydrated case visual fields to the notification card factory.");
    }

    @Test
    void notificationPathDoesNotUseNotificationSpecificWidthOrMiniCardClasses() throws Exception {
        String notificationFactory = Files.readString(Path.of("src/main/java/com/shale/ui/component/factory/NotificationCardFactory.java"));
        String css = Files.readString(Path.of("src/main/resources/css/app.css"));

        assertFalse(notificationFactory.contains("EMBEDDED_CASE_CARD_WIDTH"),
                "Notification previews should not be fixed by hardcoded notification mini-card widths.");
        assertFalse(notificationFactory.contains("setPrefWidth(EMBEDDED_CASE_CARD_WIDTH)"),
                "Notification previews should rely on the shared card variant instead of width patching.");
        assertFalse(css.contains("-fx-pref-width: 195px"),
                "Notification right column should not squeeze embedded case cards into the old narrow pill layout.");
        assertFalse(css.contains("-fx-pref-width: 210px"),
                "Notification right column should not hardcode embedded case-card width.");
        assertFalse(notificationFactory.contains("notification-row-case-mini-card"));
        assertFalse(notificationFactory.contains("notification-row-case-mini"));
        assertFalse(css.contains(".notification-row-case-mini-card"));
        assertFalse(css.contains(".notification-row-case-mini"));
    }

    @Test
    void notificationRightAreaDoesNotShrinkEmbeddedCaseCardBelowComputedPreviewWidth() throws Exception {
        String notificationFactory = Files.readString(Path.of("src/main/java/com/shale/ui/component/factory/NotificationCardFactory.java"));
        String myShaleController = Files.readString(Path.of("src/main/java/com/shale/ui/controller/MyShaleController.java"));

        assertTrue(myShaleController.contains("headerTopRow.getChildren().add(caseCard);\n\t\tLabel inlineCountLabel"),
                "Overview places the embedded case card before any growable spacer, so the card receives its computed width.");
        assertTrue(notificationFactory.contains("rightArea.setMinWidth(Region.USE_PREF_SIZE);"),
                "Notification right-area must not advertise minWidth=0; HBox shrink would compress the embedded case card into a pill.");
        assertTrue(notificationFactory.contains("rightArea.setPrefWidth(Region.USE_COMPUTED_SIZE);"),
                "Notification right-area should derive its preferred width from the shared embedded card, not a hardcoded width.");
        assertTrue(notificationFactory.contains("HBox.setHgrow(mainArea, Priority.ALWAYS);"),
                "This documents the parent-chain pressure point: the growable main area can force HBox shrink on later siblings.");
        assertFalse(notificationFactory.contains("rightArea.setMinWidth(0);"),
                "The regression was the Notification right-area VBox minWidth=0, which allowed HBox to shrink the card to a tiny pill.");
    }

    @Test
    void notificationCenterTreatsSharedEmbeddedCaseCardAsInteractiveChild() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/component/dialog/NotificationCenterDialog.java"));

        assertTrue(source.contains("hasStyleClassInAncestorChain(node, \"task-related-case-card\")"),
                "Notification rows should preserve case-card click behavior by recognizing the shared embedded card marker as interactive.");
        assertFalse(source.contains("notification-row-case-mini-card"),
                "Notification row click handling should not depend on a notification-specific duplicate case-card style.");
        assertFalse(source.contains("notification-row-case-mini"),
                "Notification row click handling should not depend on a notification-specific duplicate case-card wrapper style.");
    }
}
