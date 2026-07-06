package com.shale.ui.component;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class NotificationEmbeddedCaseCardReuseTest {

    @Test
    void notificationAndOverviewUseSameEmbeddedTaskCaseCardFactoryHelper() throws Exception {
        String notificationFactory = Files.readString(Path.of("src/main/java/com/shale/ui/component/factory/NotificationCardFactory.java"));
        String myShaleController = Files.readString(Path.of("src/main/java/com/shale/ui/controller/MyShaleController.java"));
        String caseCardFactory = Files.readString(Path.of("src/main/java/com/shale/ui/component/factory/CaseCardFactory.java"));

        assertTrue(notificationFactory.contains("caseCardFactory.createEmbeddedTaskCaseCard("),
                "Notification case previews should use the same embedded task case-card helper as My Shale Overview.");
        assertTrue(myShaleController.contains("caseCardFactory.createEmbeddedTaskCaseCard("),
                "My Shale Overview task lane headers should use the shared embedded task case-card helper.");
        assertTrue(caseCardFactory.contains("new CaseCardModel(id, name, null, null, responsibleAttorney, responsibleAttorneyColor,"),
                "The shared helper should build the same compact embedded model used by the Overview path.");
        assertTrue(caseCardFactory.contains("Variant.MINI"),
                "The shared helper should keep the embedded case card on the CaseCardFactory MINI variant.");
        assertTrue(caseCardFactory.contains("card.getStyleClass().add(\"task-related-case-card\")"),
                "The shared helper should apply the task embedded case-card style class consistently.");
    }

    @Test
    void miniCaseCardStructureDoesNotRetainOldAttorneyPillChild() throws Exception {
        String caseCard = Files.readString(Path.of("src/main/java/com/shale/ui/component/CaseCard.java"));

        String miniBlock = caseCard.substring(caseCard.indexOf("public void applyMini()"), caseCard.indexOf("public void applyTaskPreview()"));

        assertTrue(miniBlock.contains("attorneyMiniCard.setManaged(false);\n\t\tattorneyMiniCard.setVisible(false);"),
                "MINI case cards should not manage or show the old right-side attorney pill.");
        assertTrue(miniBlock.contains("headerRow.getChildren().setAll(titleLabel);"),
                "MINI case cards should render the embedded task structure with only the case title in the header row.");
        assertFalse(miniBlock.contains("headerRow.getChildren().setAll(titleLabel, headerSpacer, attorneyMiniCard);"),
                "MINI case cards should not retain the old title + spacer + attorney pill layout.");
    }

    @Test
    void notificationPathDoesNotUseNotificationSpecificWidthOrMiniCardClasses() throws Exception {
        String notificationFactory = Files.readString(Path.of("src/main/java/com/shale/ui/component/factory/NotificationCardFactory.java"));
        String css = Files.readString(Path.of("src/main/resources/css/app.css"));

        assertFalse(notificationFactory.contains("EMBEDDED_CASE_CARD_WIDTH"),
                "Notification previews should not be fixed by hardcoded notification mini-card widths.");
        assertFalse(notificationFactory.contains("setPrefWidth(EMBEDDED_CASE_CARD_WIDTH)"),
                "Notification previews should rely on the shared card structure instead of width patching.");
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
