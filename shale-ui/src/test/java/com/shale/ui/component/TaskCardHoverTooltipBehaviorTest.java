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
    void hoverPopupUsesCaseStatusStyleTextPopupWithTitleAndOptionalDescription() {
        assertTrue(taskCard.contains("buildTaskDetailsPopup(String title, String description)"));
        assertTrue(taskCard.contains("heading.setWrapText(true)"));
        assertTrue(taskCard.contains("task-hover-popup"));
        assertTrue(taskCard.contains("normalizedTitle + \"\\n\\n\" + displayedDescription"));
    }

    @Test
    void hoverPopupUsesNaturalContentHeightWithoutFixedVerticalReservations() {
        assertFalse(taskCard.contains("content.setMinHeight"));
        assertFalse(taskCard.contains("content.setPrefHeight"));
        assertFalse(taskCard.contains("content.setMaxHeight"));
        assertFalse(taskCard.contains("tooltip.setPrefHeight"));
        assertFalse(taskCard.contains("tooltip.setMaxHeight"));
        assertFalse(taskCard.contains("VBox.setVgrow"));
    }

    @Test
    void longHoverDescriptionsRemainCompleteAndUseBoundedVerticalScrolling() {
        assertTrue(taskCard.contains("Label details = new Label(normalizedDescription)"));
        assertTrue(taskCard.contains("scroll.setMaxHeight(260)"));
        assertTrue(taskCard.contains("ScrollBarPolicy.NEVER"));
        assertTrue(taskCard.contains("ScrollBarPolicy.AS_NEEDED"));
    }

    @Test
    void blankDescriptionLeavesPopupTitleOnly() {
        assertTrue(taskCard.contains("displayedDescription.isBlank() ? normalizedTitle"));
        assertTrue(taskCard.contains("normalizeTaskDetailsText(description)"));
    }

    @Test
    void hoverPopupWaitsBeforeShowingAndCanBeCanceled() {
        assertTrue(taskCard.contains("TASK_DETAILS_POPUP_SHOW_DELAY = Duration.millis(400)"));
        assertTrue(taskCard.contains("taskDetailsPopupShowDelay.playFromStart()"));
        assertTrue(taskCard.contains("cancelTaskDetailsPopupShow()"));
        assertTrue(taskCard.contains("taskDetailsPopupShowDelay.stop()"));
        assertFalse(taskCard.contains("Duration.seconds(5)"));
        assertFalse(taskCard.contains("Duration.millis(5000)"));
    }

    @Test
    void hoverPopupAnchorsToCardWithoutChangingCardGeometry() {
        assertTrue(taskCard.contains("localToScreen(getBoundsInLocal())"));
        assertTrue(taskCard.contains("cardBounds.getMaxX() + TASK_DETAILS_POPUP_CURSOR_OFFSET"));
    }

    @Test
    void hoverPopupCorrectsOnlyForScreenEdgesAfterInitialCursorPlacement() {
        assertTrue(taskCard.contains("correctTaskDetailsPopupForScreenEdges(cardBounds)"));
        assertTrue(taskCard.contains("Screen.getScreensForRectangle(anchor.getMinX()"));
        assertTrue(taskCard.contains("popupWindow.setX"));
        assertTrue(taskCard.contains("popupWindow.setY"));
    }

    @Test
    void hoverPopupRemainsOpenBeyondDefaultTooltipTimeout() {
        assertTrue(taskCard.contains("Popup"));
        assertFalse(taskCard.contains("Duration.seconds(5)"));
        assertFalse(taskCard.contains("Duration.millis(5000)"));
    }

    @Test
    void hoverPopupUsesGracefulExitDelayToAvoidCardPopupFlicker() {
        assertTrue(taskCard.contains("TASK_DETAILS_TOOLTIP_HIDE_DELAY = Duration.millis(120)"));
        assertTrue(taskCard.contains("taskDetailsPopupHideDelay.setOnFinished"));
    }

    @Test
    void hoverPopupIsCleanedUpWhenCardIsReused() {
        assertTrue(taskCard.contains("if (taskDetailsPopup != null)"));
        assertTrue(taskCard.contains("hideTaskDetailsPopup()"));
        assertTrue(taskCard.contains("taskDetailsPopup = buildTaskDetailsPopup"));
        assertTrue(taskCard.contains("showTaskDetailsPopup()"));
    }
}
