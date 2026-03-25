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
            LocalDateTime completedAt
    ) {
    }

    private final Consumer<Long> onOpenTask;

    public TaskCardFactory(Consumer<Long> onOpenTask) {
        this.onOpenTask = onOpenTask;
    }

    public TaskCard create(TaskCardModel model, Variant variant) {
        Objects.requireNonNull(model, "model");

        TaskCard card = new TaskCard();
        card.setTaskId(model.taskId());
        card.setOnOpen(onOpenTask);
        card.setTitle(model.title());
        card.setDueAt(model.dueAt());
        card.setDescriptionPreview(model.description());
        card.setCompleted(model.completedAt() != null);
        card.setBackgroundCssColor(null);

        switch (variant) {
            case FULL -> card.applyFull();
            case COMPACT -> card.applyCompact();
            case MINI -> card.applyMini();
        }

        return card;
    }
}
