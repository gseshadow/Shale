package com.shale.ui.component;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class NotificationEmbeddedCaseCardReuseTest {

    @Test
    void notificationCasePreviewUsesSameExistingMiniFactoryShapeAsMyShaleOverview() throws Exception {
        String notificationFactory = Files.readString(Path.of("src/main/java/com/shale/ui/component/factory/NotificationCardFactory.java"));
        String myShaleController = Files.readString(Path.of("src/main/java/com/shale/ui/controller/MyShaleController.java"));
        String caseCardFactory = Files.readString(Path.of("src/main/java/com/shale/ui/component/factory/CaseCardFactory.java"));

        assertFalse(caseCardFactory.contains("createEmbeddedTaskCaseCard"),
                "The notification fix should not invent a new embedded case-card helper when the existing MINI factory path is reusable.");
        assertTrue(notificationFactory.contains("new CaseCardFactory.CaseCardModel(\n\t\t\t\t\t\tcaseId,\n\t\t\t\t\t\tcaseName == null ? \"Case #\" + caseId : caseName,\n\t\t\t\t\t\tnull,\n\t\t\t\t\t\tnull,"),
                "Notification previews should use the same 7-field embedded case-card model shape used by My Shale Overview lane headers.");
        assertTrue(myShaleController.contains("new CaseCardModel(\n\t\t\t\t\t\tkey == null || key.caseId() == null ? 0L : key.caseId(),\n\t\t\t\t\t\tkey == null ? NO_CASE_COLUMN_TITLE : key.displayName(),\n\t\t\t\t\t\tnull,\n\t\t\t\t\t\tnull,"),
                "My Shale Overview lane headers should remain on the existing compact embedded case-card model shape.");
        assertTrue(notificationFactory.contains("CaseCardFactory.Variant.MINI"),
                "Notification previews should render through the existing CaseCardFactory MINI variant.");
        assertTrue(myShaleController.contains("CaseCardFactory.Variant.MINI"),
                "My Shale Overview lane headers should render through the existing CaseCardFactory MINI variant.");
        assertTrue(notificationFactory.contains("caseCard.getStyleClass().add(\"task-related-case-card\")"),
                "Notification previews should still mark the shared case card as an interactive task-related child.");
    }

    @Test
    void globalMiniCaseCardBehaviorIsNotChangedForNotifications() throws Exception {
        String caseCard = Files.readString(Path.of("src/main/java/com/shale/ui/component/CaseCard.java"));
        String miniBlock = caseCard.substring(caseCard.indexOf("public void applyMini()"), caseCard.indexOf("public void applyTaskPreview()"));

        assertTrue(miniBlock.contains("attorneyMiniCard.setManaged(true);\n\t\tattorneyMiniCard.setVisible(true);"),
                "Notification fixes should not globally remove the existing MINI attorney child from all MINI case cards.");
        assertTrue(miniBlock.contains("headerRow.getChildren().setAll(titleLabel, headerSpacer, attorneyMiniCard);"),
                "Notification fixes should not globally alter the existing MINI header structure.");
    }

    @Test
    void notificationPathDoesNotUseNotificationSpecificWidthOrMiniCardClasses() throws Exception {
        String notificationFactory = Files.readString(Path.of("src/main/java/com/shale/ui/component/factory/NotificationCardFactory.java"));
        String css = Files.readString(Path.of("src/main/resources/css/app.css"));

        assertFalse(notificationFactory.contains("EMBEDDED_CASE_CARD_WIDTH"),
                "Notification previews should not be fixed by hardcoded notification mini-card widths.");
        assertFalse(notificationFactory.contains("setPrefWidth(EMBEDDED_CASE_CARD_WIDTH)"),
                "Notification previews should rely on the shared card path instead of width patching.");
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
    void notificationCenterTreatsSharedEmbeddedCaseCardAsInteractiveChild() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/component/dialog/NotificationCenterDialog.java"));

        assertTrue(source.contains("hasStyleClassInAncestorChain(node, \"task-related-case-card\")"),
                "Notification rows should preserve case-card click behavior by recognizing the shared embedded card style as interactive.");
        assertFalse(source.contains("notification-row-case-mini-card"),
                "Notification row click handling should not depend on a notification-specific duplicate case-card style.");
        assertFalse(source.contains("notification-row-case-mini"),
                "Notification row click handling should not depend on a notification-specific duplicate case-card wrapper style.");
    }
}
