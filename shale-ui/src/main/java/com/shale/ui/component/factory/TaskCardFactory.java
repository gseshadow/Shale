package com.shale.ui.component.factory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import com.shale.ui.component.TaskCard;
import com.shale.ui.privacy.PhiFieldRegistry;
import com.shale.ui.util.ColorUtil;

public final class TaskCardFactory {

    public enum Variant {
        FULL, MY_TASKS, COMPACT, COMPACT_FLUID, MINI
    }

    public record TaskCardModel(
            long taskId,
            Long caseId,
            String caseName,
            String casePrimaryStatusName,
            String casePrimaryStatusColor,
            String casePracticeAreaColor,
            String caseResponsibleAttorney,
            String caseResponsibleAttorneyColor,
            Boolean caseNonEngagementLetterSent,
            String title,
            String description,
            String createdByDisplayName,
            String taskStatusName,
            String taskStatusColorHex,
            String priorityColorHex,
            LocalDateTime dueAt,
            LocalDateTime completedAt,
            List<AssignedUserModel> assignedUsers
    ) {
    }

    public record AssignedUserModel(
            int userId,
            String displayName,
            String colorCss
    ) {
    }

    private final Consumer<Long> onOpenTask;
    private final Consumer<Long> onToggleCompleteTask;
    private final Consumer<Integer> onOpenCase;
    private final Consumer<Integer> onOpenUser;

    public TaskCardFactory(
            Consumer<Long> onOpenTask,
            Consumer<Long> onToggleCompleteTask,
            Consumer<Integer> onOpenCase,
            Consumer<Integer> onOpenUser) {
        this.onOpenTask = onOpenTask;
        this.onToggleCompleteTask = onToggleCompleteTask;
        this.onOpenCase = onOpenCase;
        this.onOpenUser = onOpenUser;
    }

    public TaskCard create(TaskCardModel model, Variant variant) {
        return create(model, variant, false);
    }

    public TaskCard create(TaskCardModel model, Variant variant, boolean allowPhiDescription) {
        Objects.requireNonNull(model, "model");

        TaskCard card = new TaskCard();
        boolean passiveSurface = variant != Variant.FULL && variant != Variant.MY_TASKS;
        boolean suppressTitleForPassiveSurface = passiveSurface
                && variant != Variant.COMPACT
                && variant != Variant.MINI
                && PhiFieldRegistry.isPhi("Tasks", "Title");
        String displayTitle = suppressTitleForPassiveSurface
                ? "Task #" + model.taskId()
                : model.title();
        String safeDescription = descriptionForCard(model, allowPhiDescription);
        card.setTaskId(model.taskId());
        card.setOnOpen(onOpenTask);
        card.setOnToggleComplete(onToggleCompleteTask);
        card.setOnOpenRelatedCase(onOpenCase);
        card.setOnOpenAssigneeUser(onOpenUser);
        card.setRelatedCase(
                model.caseId(),
                model.caseName(),
                model.casePrimaryStatusName(),
                model.casePrimaryStatusColor(),
                model.casePracticeAreaColor(),
                model.caseResponsibleAttorney(),
                model.caseResponsibleAttorneyColor(),
                model.caseNonEngagementLetterSent());
        card.setTitle(displayTitle);
        card.setDueAt(model.dueAt());
        card.setCreatedByDisplayName(model.createdByDisplayName());
        card.setDescriptionPreview(safeDescription);
        card.setTaskStatus(model.taskStatusName(), model.taskStatusColorHex());
        card.setCompleted(model.completedAt() != null);
        card.setBorderByDueState(model.dueAt(), model.completedAt());
        card.setAssignees(model.assignedUsers());
        card.setPriorityBackgroundColor(model.priorityColorHex());

        switch (variant) {
            case FULL -> card.applyFull();
            case MY_TASKS -> card.applyMyTasks();
            case COMPACT -> card.applyCompact();
            case COMPACT_FLUID -> card.applyCompactFluid();
            case MINI -> card.applyMini();
        }

        return card;
    }

    static String descriptionForCard(TaskCardModel model, boolean allowPhiDescription) {
        return (allowPhiDescription || !PhiFieldRegistry.isPhi("Tasks", "Description"))
                ? model.description()
                : null;
    }
}
