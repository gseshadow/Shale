package com.shale.ui.notification;

import com.shale.data.dao.TaskDao.TaskDueNotificationCandidate;

import java.util.List;

public interface TaskDueNotificationRecipientResolver {
	List<Integer> resolveTaskDueNotificationRecipients(TaskDueNotificationCandidate candidate);
}
