package com.shale.ui.notification;

import com.shale.data.dao.TaskDao.TaskDueNotificationCandidate;

import java.util.List;

public final class AssignedUserTaskDueNotificationRecipientResolver implements TaskDueNotificationRecipientResolver {
	@Override
	public List<Integer> resolveTaskDueNotificationRecipients(TaskDueNotificationCandidate candidate) {
		if (candidate == null || candidate.assignedUserId() == null || candidate.assignedUserId() <= 0) {
			return List.of();
		}
		return List.of(candidate.assignedUserId());
	}
}
