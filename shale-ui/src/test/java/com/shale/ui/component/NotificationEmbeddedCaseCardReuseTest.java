package com.shale.ui.component;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class NotificationEmbeddedCaseCardReuseTest {

    @Test
    void notificationCasePreviewUsesSameEmbeddedTaskCaseCardFactoryPathAsMyShaleOverview() throws Exception {
        String notificationFactory = Files.readString(Path.of("src/main/java/com/shale/ui/component/factory/NotificationCardFactory.java"));
        String myShaleController = Files.readString(Path.of("src/main/java/com/shale/ui/controller/MyShaleController.java"));
        String caseCardFactory = Files.readString(Path.of("src/main/java/com/shale/ui/component/factory/CaseCardFactory.java"));

        assertTrue(myShaleController.contains("caseCardFactory.createEmbeddedTaskCaseCard("),
                "My Shale Overview task lanes should use the shared embedded task case-card path.");
        assertTrue(notificationFactory.contains("caseCardFactory.createEmbeddedTaskCaseCard("),
                "Notification previews must copy the exact My Shale Overview embedded task case-card path.");
        assertTrue(caseCardFactory.contains("new CaseCardModel(id, name, null, null, responsibleAttorney, responsibleAttorneyColor,"),
                "The shared embedded path should keep the same compact model construction used by Overview task-lane headers.");
        assertTrue(caseCardFactory.contains("card.applyEmbeddedTaskMini();"),
                "The shared embedded path should apply the task-card embedded mini layout without changing MINI globally.");
        assertTrue(caseCardFactory.contains("card.getStyleClass().add(\"task-related-case-card\")"),
                "The shared embedded path should apply the Overview embedded mini root marker.");
    }

    @Test
    void embeddedTaskMiniKeepsOverviewRootAndChildStyleClasses() throws Exception {
        String caseCard = Files.readString(Path.of("src/main/java/com/shale/ui/component/CaseCard.java"));
        String factory = Files.readString(Path.of("src/main/java/com/shale/ui/component/factory/CaseCardFactory.java"));
        String embeddedBlock = caseCard.substring(caseCard.indexOf("public void applyEmbeddedTaskMini()"), caseCard.indexOf("public void applyTaskPreview()"));
        String buildBlock = caseCard.substring(caseCard.indexOf("private void buildUi()"), caseCard.indexOf("private void setPracticeAreaBarWidth"));

        assertTrue(buildBlock.contains("getStyleClass().addAll(\"case-card\", \"shale-entity-card\", \"shale-entity-card-clickable\")"),
                "Embedded cards should retain the CaseCard root style classes.");
        assertTrue(factory.contains("card.getStyleClass().add(\"task-related-case-card\")"),
                "Embedded task cards should retain the Overview task-related root style class.");
        assertTrue(buildBlock.contains("practiceAreaBar.getStyleClass().addAll(\"case-card__practice-area-bar\", \"shale-indicator-practice-area\")"),
                "Embedded cards should retain the left practice-area color-bar style classes.");
        assertTrue(buildBlock.contains("attorneyMiniCard.getStyleClass().add(\"case-card__attorney-mini-card\")"),
                "Embedded cards should retain the right attorney pill child style class.");
        assertTrue(embeddedBlock.contains("attorneyMiniCard.setManaged(true);")
                && embeddedBlock.contains("attorneyMiniCard.setVisible(true);"),
                "Embedded task cards must not regress to the old plain title-only pill.");
        assertTrue(embeddedBlock.contains("headerRow.getChildren().setAll(titleLabel, headerSpacer, attorneyMiniCard);"),
                "Embedded task cards should include the title and right-side attorney pill like Overview.");
    }

    @Test
    void globalMiniCaseCardBehaviorIsNotChangedForNotifications() throws Exception {
        String caseCard = Files.readString(Path.of("src/main/java/com/shale/ui/component/CaseCard.java"));
        String miniBlock = caseCard.substring(caseCard.indexOf("public void applyMini()"), caseCard.indexOf("public void applyEmbeddedTaskMini()"));

        assertTrue(miniBlock.contains("attorneyMiniCard.setManaged(false);\n\t\tattorneyMiniCard.setVisible(false);"),
                "Notification fixes should not globally change the existing MINI variant.");
        assertTrue(miniBlock.contains("headerRow.getChildren().setAll(titleLabel);"),
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
