package com.shale.ui.component.dialog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.shale.core.dto.CaseTaskListItemDto;
import com.shale.core.dto.TaskDetailDto;

final class TaskDetailCaseCardHydrationTest {

    @Test
    void taskDetailDtoAndMyShaleSummaryCarryEquivalentMiniCaseCardMetadata() {
        CaseTaskListItemDto myShale = new CaseTaskListItemDto(
                10L, 7, 501L, "Smith v. Example", "Open", "#22AA55", "#004488",
                "Ada Attorney", "#AA5500", false, "Review records", "Read intake packet",
                1, "#111111", LocalDateTime.of(2026, 1, 2, 12, 0), null,
                31, "Ada Attorney", "#AA5500", 42, "Case Creator",
                LocalDateTime.of(2026, 1, 1, 9, 0), LocalDateTime.of(2026, 1, 1, 10, 0), false);
        TaskDetailDto calendarLoadedDetail = new TaskDetailDto(
                10L, 7, 501L, "Smith v. Example", "Ada Attorney", "#AA5500", false,
                "Open", "#22AA55", "#004488", "Review records", "Read intake packet",
                LocalDateTime.of(2026, 1, 2, 12, 0), 2, 1, null,
                31, "Ada Attorney", "#AA5500", "Case Creator");

        TaskDetailDialog.TaskDetailModel myShaleModel = new TaskDetailDialog.TaskDetailModel(
                myShale.id(), myShale.caseId(), myShale.caseName(), myShale.caseResponsibleAttorney(),
                myShale.caseResponsibleAttorneyColor(), myShale.caseNonEngagementLetterSent(),
                myShale.casePrimaryStatusName(), myShale.casePrimaryStatusColor(), myShale.casePracticeAreaColor(),
                myShale.title(), myShale.description(), myShale.dueAt(), null, null,
                myShale.createdByDisplayName(), List.of(), List.of(), List.of(), false);
        TaskDetailDialog.TaskDetailModel calendarModel = new TaskDetailDialog.TaskDetailModel(
                calendarLoadedDetail.id(), calendarLoadedDetail.caseId(), calendarLoadedDetail.caseName(),
                calendarLoadedDetail.caseResponsibleAttorney(), calendarLoadedDetail.caseResponsibleAttorneyColor(),
                calendarLoadedDetail.caseNonEngagementLetterSent(), calendarLoadedDetail.casePrimaryStatusName(),
                calendarLoadedDetail.casePrimaryStatusColor(), calendarLoadedDetail.casePracticeAreaColor(),
                calendarLoadedDetail.title(), calendarLoadedDetail.description(), calendarLoadedDetail.dueAt(),
                calendarLoadedDetail.statusId(), calendarLoadedDetail.priorityId(), calendarLoadedDetail.createdByDisplayName(),
                List.of(), List.of(), List.of(), false);

        assertEquals(myShaleModel.casePracticeAreaColor(), calendarModel.casePracticeAreaColor());
        assertEquals(myShaleModel.casePrimaryStatusColor(), calendarModel.casePrimaryStatusColor());
        assertEquals(myShaleModel.caseResponsibleAttorneyColor(), calendarModel.caseResponsibleAttorneyColor());
        assertEquals(myShaleModel.casePrimaryStatusName(), calendarModel.casePrimaryStatusName());
        assertEquals(myShaleModel.caseName(), calendarModel.caseName());
    }

    @Test
    void calendarTaskDetailHydrationQueriesRealCaseCardVisualMetadata() throws Exception {
        String taskDao = Files.readString(Path.of("../shale-data/src/main/java/com/shale/data/dao/TaskDao.java"));
        String sceneManager = Files.readString(Path.of("src/main/java/com/shale/ui/navigation/SceneManager.java"));
        String dialog = Files.readString(Path.of("src/main/java/com/shale/ui/component/dialog/TaskDetailDialog.java"));

        assertTrue(taskDao.contains("current_status.CurrentStatusName AS CasePrimaryStatusName"));
        assertTrue(taskDao.contains("current_status.PrimaryStatusColor AS CasePrimaryStatusColor"));
        assertTrue(taskDao.contains("pa.Color AS CasePracticeAreaColor"));
        assertTrue(sceneManager.contains("initialDetail == null ? \"\" : initialDetail.casePrimaryStatusColor()"));
        assertTrue(sceneManager.contains("initialDetail == null ? \"\" : initialDetail.casePracticeAreaColor()"));
        assertTrue(dialog.contains("CaseCardFactory.Variant.EMBEDDED"));
        String taskDetailOpener = sceneManager.substring(sceneManager.indexOf("public void openTaskProfile(Long taskId, Runnable onTaskChanged)"));
        taskDetailOpener = taskDetailOpener.substring(0, taskDetailOpener.indexOf("private void showTaskDetailDialog"));
        assertFalse(taskDetailOpener.contains("CalendarFeed"), "Task-detail mini case card must not use Calendar feed colors.");
    }
}
