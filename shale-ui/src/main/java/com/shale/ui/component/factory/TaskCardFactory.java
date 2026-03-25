package com.shale.ui.component.factory;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.function.Consumer;

import com.shale.ui.component.TaskCard;

public final class TaskCardFactory {

    public enum Variant {
        FULL, COMPACT, MINI
    }

    public record TaskCardModel(
            long taskId,
            String title,
            String description,
            LocalDateTime dueAt,
            LocalDateTime completedAt,
            Integer assignedUserId,
            String assignedUserDisplayName,
            String assignedUserColor
    ) {
    }

    private final Consumer<Long> onOpenTask;
    private final Consumer<Long> onToggleCompleteTask;
    private final Consumer<Integer> onOpenUser;

    public TaskCardFactory(
            Consumer<Long> onOpenTask,
            Consumer<Long> onToggleCompleteTask,
            Consumer<Integer> onOpenUser) {
        this.onOpenTask = onOpenTask;
        this.onToggleCompleteTask = onToggleCompleteTask;
        this.onOpenUser = onOpenUser;
    }

    public TaskCard create(TaskCardModel model, Variant variant) {
        Objects.requireNonNull(model, "model");

        TaskCard card = new TaskCard();
        card.setTaskId(model.taskId());
        card.setOnOpen(onOpenTask);
        card.setOnToggleComplete(onToggleCompleteTask);
        card.setOnOpenAssigneeUser(onOpenUser);
        card.setTitle(model.title());
        card.setDueAt(model.dueAt());
        card.setDescriptionPreview(model.description());
        card.setCompleted(model.completedAt() != null);
        card.setAssignee(model.assignedUserId(), model.assignedUserDisplayName(), model.assignedUserColor());
        card.setBackgroundCssColor(null);

        switch (variant) {
            case FULL -> card.applyFull();
            case COMPACT -> card.applyCompact();
            case MINI -> card.applyMini();
        }

        return card;
    }
}
