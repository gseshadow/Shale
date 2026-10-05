package com.shale.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class TaskDueDatePolicyTest {
    @Test void resolvesAllPersistedModesAndDefaultsSafely() {
        assertEquals(TaskDueDatePolicy.OPTIONAL, TaskDueDatePolicy.fromDatabase("OPTIONAL"));
        assertEquals(TaskDueDatePolicy.WARN, TaskDueDatePolicy.fromDatabase("WARN"));
        assertEquals(TaskDueDatePolicy.REQUIRED, TaskDueDatePolicy.fromDatabase("REQUIRED"));
        assertEquals(TaskDueDatePolicy.WARN, TaskDueDatePolicy.fromDatabase(null));
        assertEquals(TaskDueDatePolicy.WARN, TaskDueDatePolicy.fromDatabase("unexpected"));
    }
}
