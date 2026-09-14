package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.shale.core.dto.CaseTaskListItemDto;

final class MyShaleCompletedFilterRegressionTest {
    private static final Path CONTROLLER = Path.of("src/main/java/com/shale/ui/controller/MyShaleController.java");
    private static final Path TASK_DAO = Path.of("../shale-data/src/main/java/com/shale/data/dao/TaskDao.java");

    @Test
    void showCompletedAddsCompletedTasksToDefaultViewAndTurningItOffRestoresIncompleteOnly() throws Exception {
        MyShaleController controller = new MyShaleController();
        Object allActive = staticField("ALL_ACTIVE_TASK_STATUSES_OPTION");
        CaseTaskListItemDto incomplete = task(101, null);
        CaseTaskListItemDto completed = task(102, LocalDateTime.of(2026, 9, 1, 12, 0));

        setShowCompleted(controller, false);
        assertTrue(matches(controller, incomplete, allActive), "The default view must retain incomplete tasks.");
        assertFalse(matches(controller, completed, allActive), "The default view must exclude completed tasks.");

        setShowCompleted(controller, true);
        assertTrue(matches(controller, incomplete, allActive), "Show Completed must not replace incomplete tasks.");
        assertTrue(matches(controller, completed, allActive), "Show Completed must make completed tasks visible.");

        setShowCompleted(controller, false);
        assertTrue(matches(controller, incomplete, allActive), "Turning the option off must retain incomplete tasks.");
        assertFalse(matches(controller, completed, allActive), "Turning the option off must exclude completed tasks again.");
    }

    @Test
    void explicitCompletedAndAllStatusFiltersKeepTheirExistingMeaning() throws Exception {
        MyShaleController controller = new MyShaleController();
        setShowCompleted(controller, true);
        CaseTaskListItemDto incomplete = task(201, null);
        CaseTaskListItemDto completed = task(202, LocalDateTime.of(2026, 9, 1, 12, 0));

        assertFalse(matches(controller, incomplete, staticField("COMPLETED_TASK_STATUS_OPTION")),
                "The explicit Completed filter must remain completed-only.");
        assertTrue(matches(controller, completed, staticField("COMPLETED_TASK_STATUS_OPTION")),
                "The explicit Completed filter must include completed tasks.");
        assertTrue(matches(controller, incomplete, staticField("ALL_TASK_STATUSES_OPTION")),
                "The explicit All filter must include incomplete tasks.");
        assertTrue(matches(controller, completed, staticField("ALL_TASK_STATUSES_OPTION")),
                "The explicit All filter must include completed tasks.");
    }

    @Test
    void toggleImmediatelyReloadsTheCurrentScopeWithIncludeCompleted() throws Exception {
        String source = Files.readString(CONTROLLER);
        String binding = source.substring(source.indexOf("if (myTasksShowCompletedButton != null)"),
                source.indexOf("if (myTasksBoardViewButton != null)"));
        assertTrue(containsInOrder(binding, "showCompletedMyTasks = !showCompletedMyTasks;",
                        "persistMyTasksShowCompletedPreference(showCompletedMyTasks);",
                        "updateMyTasksCompletionToggleLabel();", "refreshMyTasks();"),
                "The toggle must immediately reload using its newly selected state.");

        String refresh = source.substring(source.indexOf("private void refreshMyTasks(boolean force)"),
                source.indexOf("private void submitTaskMetadataLoad("));
        assertTrue(refresh.contains("final boolean includeCompleted = showCompletedMyTasks;"),
                "The reload must capture the current Show Completed state.");
        assertTrue(refresh.contains("final MyTasksSource source = myTasksSource;")
                        && refresh.contains("source == MyTasksSource.CREATED_BY_ME")
                        && refresh.contains("loadTasksCreatedByUser(tenantAtSubmit, userAtSubmit, sort, includeCompleted)")
                        && refresh.contains("loadMyTasks(tenantAtSubmit, userAtSubmit, sort, includeCompleted)"),
                "The reload must preserve the selected scope and pass includeCompleted to both paths.");
    }

    @Test
    void repositoryQueriesRetainOwnershipTenantDeletionAndSortPredicates() throws Exception {
        String source = Files.readString(TASK_DAO);
        String assigned = source.substring(source.indexOf("public List<CaseTaskListItemDto> listActiveTasksAssignedToUser"),
                source.indexOf("public List<CaseTaskListItemDto> listTasksCreatedByUserForBoard"));
        String created = source.substring(source.indexOf("public List<CaseTaskListItemDto> listTasksCreatedByUserForBoard"),
                source.indexOf("public List<AssignedUserTaskRow> listActiveTasksForAssigneeInTenant"));

        assertTrue(assigned.contains("WHERE t.ShaleClientId = ?") && assigned.contains("myAssignment.UserId = ?")
                        && assigned.contains("myAssignment.ShaleClientId = t.ShaleClientId")
                        && assigned.contains("AND ISNULL(t.IsDeleted, 0) = 0"),
                "Assigned-to-me loading must remain tenant/owner scoped and soft-delete filtered.");
        assertTrue(created.contains("WHERE t.ShaleClientId = ?") && created.contains("AND t.CreatedByUserId = ?")
                        && created.contains("AND ISNULL(t.IsDeleted, 0) = 0"),
                "Created-by-me loading must remain tenant/creator scoped and soft-delete filtered.");
        assertTrue(assigned.contains(".formatted(includeCompleted ? \"\" : \"AND t.CompletedAt IS NULL\", dueOrderSql)")
                        && created.contains(".formatted(includeCompleted ? \"\" : \"AND t.CompletedAt IS NULL\", dueOrderSql)"),
                "Both paths must exclude completed rows by default and include them only when requested.");
        assertTrue(assigned.contains("CASE WHEN t.CompletedAt IS NULL THEN 0 ELSE 1 END ASC")
                        && created.contains("CASE WHEN t.CompletedAt IS NULL THEN 0 ELSE 1 END ASC")
                        && assigned.contains("t.DueAt %s") && created.contains("t.DueAt %s"),
                "Including completed tasks must retain completion grouping and due-date sorting.");
    }

    private static Object staticField(String name) throws Exception {
        Field field = MyShaleController.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    private static void setShowCompleted(MyShaleController controller, boolean value) throws Exception {
        Field field = MyShaleController.class.getDeclaredField("showCompletedMyTasks");
        field.setAccessible(true);
        field.setBoolean(controller, value);
    }

    private static boolean matches(MyShaleController controller, CaseTaskListItemDto task, Object option) throws Exception {
        Method method = MyShaleController.class.getDeclaredMethod("matchesSelectedMyTaskStatus",
                CaseTaskListItemDto.class, option.getClass());
        method.setAccessible(true);
        return (boolean) method.invoke(controller, task, option);
    }

    private static CaseTaskListItemDto task(long id, LocalDateTime completedAt) {
        return new CaseTaskListItemDto(id, 7, 700, "Case", null, null, null, null, null, null,
                "Task " + id, null, "Open", null, 1, null, null, completedAt, 42, "Owner", null,
                42, "Creator", LocalDateTime.of(2026, 8, 1, 9, 0), LocalDateTime.of(2026, 8, 1, 9, 0), false);
    }

    private static boolean containsInOrder(String source, String... fragments) {
        int from = 0;
        for (String fragment : fragments) {
            int index = source.indexOf(fragment, from);
            if (index < 0) return false;
            from = index + fragment.length();
        }
        return true;
    }
}
