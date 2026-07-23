package com.shale.ui.component;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class TaskCardHoverTooltipBehaviorTest {
    private final String taskCard = Files.readString(Path.of("src/main/java/com/shale/ui/component/TaskCard.java"));

    TaskCardHoverTooltipBehaviorTest() throws Exception {
    }

    @Test
    void taskCardsNoLongerResizeOnHover() {
        assertFalse(taskCard.contains("Timeline"), "Hover should not animate inline card expansion.");
        assertFalse(taskCard.contains("KeyFrame"), "Hover should not schedule delayed resizing keyframes.");
        assertFalse(taskCard.contains("hoverRevealTargetHeight"), "Hover should not compute an inline reveal height.");
        assertFalse(taskCard.contains("bodyPane.getChildren().setAll(titleLabel, hoverRevealPane)"));
    }

    @Test
    void hoverPopupContainsProminentTitleAndOptionalDescription() {
        assertTrue(taskCard.contains("static Tooltip buildTaskDetailsTooltip(String title, String description)"));
        assertTrue(taskCard.contains("titleNode.setStyle(\"-fx-font-size: 13px; -fx-font-weight: 800;\")"));
        assertTrue(taskCard.contains("descriptionNode.setStyle(\"-fx-font-size: 12px; -fx-line-spacing: 1px;\")"));
        assertTrue(taskCard.contains("descriptionNode.setWrapText(true)"));
        assertTrue(taskCard.contains("tooltip.setMaxWidth(TASK_DETAILS_TOOLTIP_MAX_WIDTH)"));
    }

    @Test
    void blankDescriptionLeavesPopupTitleOnly() {
        assertTrue(taskCard.contains("if (!normalizedDescription.isBlank())"));
        assertTrue(taskCard.contains("VBox content = new VBox(4, titleNode)"));
        assertTrue(taskCard.contains("normalizeTaskDetailsText(description)"));
    }

    @Test
    void hoverPopupIsCleanedUpWhenCardIsReused() {
        assertTrue(taskCard.contains("if (taskDetailsTooltip != null)"));
        assertTrue(taskCard.contains("Tooltip.uninstall(this, taskDetailsTooltip)"));
        assertTrue(taskCard.contains("taskDetailsTooltip = buildTaskDetailsTooltip"));
        assertTrue(taskCard.contains("Tooltip.install(this, taskDetailsTooltip)"));
    }
}
