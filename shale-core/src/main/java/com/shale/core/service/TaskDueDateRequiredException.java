package com.shale.core.service;

/** Explicit business-rule failure returned by the authoritative task mutation boundary. */
public final class TaskDueDateRequiredException extends IllegalArgumentException {
    public TaskDueDateRequiredException() {
        super("Your firm requires all tasks to have a due date.");
    }
}
