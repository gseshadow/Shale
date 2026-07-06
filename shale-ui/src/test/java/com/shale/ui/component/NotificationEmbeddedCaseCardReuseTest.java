package com.shale.ui.component;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class NotificationEmbeddedCaseCardReuseTest {

    @Test
    void notificationCasePreviewsUseSharedOverviewEmbeddedCaseCardPath() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/component/factory/NotificationCardFactory.java"));

        assertTrue(source.contains("CaseCardFactory.Variant.MINI"),
                "Notification case previews should render through the shared CaseCardFactory MINI variant.");
        assertTrue(source.contains("miniCard.getStyleClass().add(\"task-related-case-card\")"),
                "Notification case previews should share the Overview task embedded case-card style class.");
        assertFalse(source.contains("notification-row-case-mini-card"),
                "Notification case previews should not use a notification-specific duplicate mini-card style.");
        assertFalse(source.contains("notification-row-case-mini"),
                "Notification case previews should not wrap the card in a notification-specific duplicate mini-card style.");
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

    @Test
    void notificationCssDoesNotOverrideSharedEmbeddedCaseCardSurface() throws Exception {
        String css = Files.readString(Path.of("src/main/resources/css/app.css"));

        assertFalse(css.contains(".notification-row-case-mini-card"),
                "Notification CSS should not null out or override the shared embedded case-card surface.");
        assertFalse(css.contains(".notification-row-case-mini"),
                "Notification CSS should not force notification-specific embedded case-card sizing.");
    }
}
