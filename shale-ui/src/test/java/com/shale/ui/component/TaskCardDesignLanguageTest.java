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
    void priorityGradientComesFromConfiguredPriorityColorWithNeutralFallback() {
        assertTrue(taskCard.contains("private String priorityGradientCss(String storedColor)"));
        assertTrue(taskCard.contains("ColorUtil.toCssBackgroundColorOrNull(storedColor)"));
        assertTrue(taskCard.contains("linear-gradient(to right"));
        assertTrue(taskCard.contains("return null;"), "Invalid/missing colors should fall back to the shared neutral card surface.");
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
        assertTrue(factory.contains("card.setTaskStatus(model.taskStatusName(), model.taskStatusColorHex())"));
        assertTrue(taskCard.contains("CaseCard.normalizeColor(statusColor, \"#F1F5F9\")"));
        assertTrue(taskCard.contains("statusName == null || statusName.isBlank() ? \"—\""));
    }

    @Test
    void interactionsRemainIndependentFromDueAccent() {
        assertTrue(taskCard.contains("CardSurfaceStyles.cardContainerStyle(backgroundCss, hovered)"));
        assertTrue(taskCard.contains("setTranslateY(-1.5)"));
        assertFalse(taskCard.contains("hovered ? dueAccentCss"));
    }
}
