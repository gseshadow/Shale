package com.shale.ui.component;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class UserSelectorSourceContractTest {

    @Test
    void userSelectorRetainsCallerDtoIdentityAndExcludesByStableUserId() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/component/UserSelector.java"));

        assertTrue(source.contains("ObjectProperty<T> selectedUser"),
                "The selected value should retain the caller-owned user DTO, not a display string.");
        assertTrue(source.contains("Function<T, Integer> userIdExtractor"),
                "Stable numeric user IDs should be extracted separately from display names.");
        assertTrue(source.contains("ObservableList<Integer> excludedUserIds"),
                "Generic exclusions should be represented as user IDs, not task-specific assignee concepts.");
        assertTrue(source.contains("!excludedSet().contains(userId)"),
                "Excluded users must not be selectable.");
        assertFalse(source.contains("CaseTaskService"),
                "The reusable component must not depend on the Task Detail service boundary or DAO-specific types.");
    }

    @Test
    void userSelectorPreservesTaskDetailPickerPresentationConcepts() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/component/UserSelector.java"));

        assertTrue(source.contains("UserCardFactory"), "User cards should continue using the shared user-card factory.");
        assertTrue(source.contains("UserCardFactory.Variant.MINI"), "The Add Assignee picker used mini user cards.");
        assertTrue(source.contains("Search users..."), "The existing search prompt should remain the default.");
        assertTrue(source.contains("deriveInitials"), "Initials search behavior should be preserved.");
        assertTrue(source.contains("No additional users available"), "The existing empty state should remain available.");
        assertTrue(source.contains("Loading users…"), "The component should expose a loading state.");
    }
}
