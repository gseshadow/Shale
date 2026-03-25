package com.shale.ui.services;

import java.util.List;
import java.util.Objects;

import com.shale.core.dto.CaseTaskListItemDto;
import com.shale.data.dao.TaskDao;

/**
 * Thin case-task service facade for UI.
 */
public final class CaseTaskService {

    private final TaskDao taskDao;

    public CaseTaskService(TaskDao taskDao) {
        this.taskDao = Objects.requireNonNull(taskDao, "taskDao");
    }

    public List<CaseTaskListItemDto> loadTasksForCase(long caseId, int shaleClientId) {
        return taskDao.listActiveTasksForCase(caseId, shaleClientId);
    }
}
