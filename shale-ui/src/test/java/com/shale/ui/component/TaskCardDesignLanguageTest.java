package com.shale.ui.component;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class TaskCardDesignLanguageTest {
    private final String taskCard = Files.readString(Path.of("src/main/java/com/shale/ui/component/TaskCard.java"));
    private final String factory = Files.readString(Path.of("src/main/java/com/shale/ui/component/factory/TaskCardFactory.java"));

    TaskCardDesignLanguageTest() throws Exception {
    }

    @Test
    void priorityGradientComesFromConfiguredPriorityColorWithNeutralFallback() throws Exception {
        assertTrue(taskCard.contains("private String priorityGradientCss(String storedColor)"));
        assertTrue(taskCard.contains("ColorUtil.toCssBackgroundColorOrNull(storedColor)"));
        String sharedGradient = Files.readString(Path.of("src/main/java/com/shale/ui/component/EntityCardGradientStyles.java"));
        assertTrue(sharedGradient.contains("#F8FAFC 30%"));
        assertTrue(sharedGradient.contains("tintStop(cssColor, 0.72) + \" 88%"));
        assertTrue(sharedGradient.contains("cssColor + \" 98%"));
        assertTrue(taskCard.contains("EntityCardGradientStyles.caseStrengthGradient(css, false)"));
        assertTrue(taskCard.contains("css == null ? null"), "Invalid/missing colors should fall back to the shared neutral card surface.");
        assertFalse(taskCard.contains("case \"High\""));
        assertFalse(taskCard.contains("case \"Medium\""));
        assertFalse(taskCard.contains("case \"Low\""));
    }

    @Test
    void dueDateStateDrivesLeftAccentBarNotCardBorder() {
        assertTrue(taskCard.contains("dueAccentBar"));
        assertTrue(taskCard.contains("setBorderByDueState(LocalDateTime dueAt, LocalDateTime completedAt)"));
        assertTrue(taskCard.contains("dueAt.isBefore(now)"));
        assertTrue(taskCard.contains("now.plusDays(1)"));
        assertTrue(taskCard.contains("now.plusWeeks(1)"));
        assertTrue(taskCard.contains("now.plusWeeks(2)"));
        assertFalse(taskCard.contains("cardContainerStyle(backgroundCss, borderCss"));
    }

    @Test
    void headerContainsTitleAndNonShrinkingStatusPill() {
        assertTrue(taskCard.contains("statusPill"));
        assertTrue(taskCard.contains("fullHeaderRow = new HBox(8, fullHeaderText, fullHeaderSpacer, statusPill"));
        assertTrue(taskCard.contains("compactTitleRow = new HBox(8, compactTitleBlock, compactHeaderSpacer, statusPill"));
        assertTrue(taskCard.contains("statusPill.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE)"));
        assertTrue(taskCard.contains("setTextOverrun(OverrunStyle.ELLIPSIS)"));
    }

    @Test
    void statusPillUsesActualModelStatusAndNeutralFallback() {
        assertTrue(factory.contains("String taskStatusName"));
        assertTrue(factory.contains("String taskStatusColorHex"));
        assertTrue(factory.contains("TaskStatusPresentation status = resolveTaskStatusPresentation("));
        assertTrue(factory.contains("card.setTaskStatus(status.name(), status.colorHex())"));
        assertTrue(taskCard.contains("CaseCard.normalizeColor(statusColor, \"#F1F5F9\")"));
        assertTrue(taskCard.contains("statusName == null || statusName.isBlank() ? \"—\""));
    }

    @Test
    void interactionsRemainIndependentFromDueAccent() {
        assertTrue(taskCard.contains("CardSurfaceStyles.cardContainerStyle(backgroundCss, hovered)"));
        assertTrue(taskCard.contains("setTranslateY(-1.5)"));
        assertFalse(taskCard.contains("hovered ? dueAccentCss"));
    }
	@Test
	void hoverRevealUsesSharedTaskCardAnimationAndConditionalContent() {
		assertTrue(taskCard.contains("HOVER_REVEAL_DURATION = Duration.millis(180)"));
		assertTrue(taskCard.contains("setHoverRevealExpanded(true)"));
		assertTrue(taskCard.contains("setHoverRevealExpanded(false)"));
		assertTrue(taskCard.contains("hoverRevealHasContent"));
		assertTrue(taskCard.contains("hoverDescriptionSection.setManaged(!hoverText.isBlank())"));
		assertTrue(taskCard.contains("hoverAssigneesSection.setManaged(!hoverAssigneesRow.getChildren().isEmpty())"));
		assertTrue(taskCard.contains("buildHoverDescriptionPreview(fullText)"));
		assertTrue(taskCard.contains("hoverRevealTargetHeight()"));
		assertTrue(taskCard.contains("hoverRevealMaximumHeight(availableWidth)"));
	}

	@Test
	void compactVariantsCanReceiveDescriptionForHoverReveal() {
		assertTrue(factory.contains("descriptionForCard(model, allowPhiDescription)"));
		assertFalse(factory.contains("variant == Variant.FULL || variant == Variant.MY_TASKS"),
				"Compact task cards should not lose descriptions before the shared hover reveal can render them.");
	}

}
