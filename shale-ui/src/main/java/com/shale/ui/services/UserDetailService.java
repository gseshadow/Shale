package com.shale.ui.services;

import java.util.List;
import java.util.Objects;

import com.shale.data.dao.CaseDao;
import com.shale.data.dao.CaseDao.CaseRow;
import com.shale.data.dao.TaskDao;
import com.shale.data.dao.TaskDao.AssignedUserTaskRow;
import com.shale.data.dao.UserDao;
import com.shale.data.dao.UserDao.UserDetailRow;
import com.shale.data.dao.UserDao.UserProfileUpdateRequest;
import com.shale.data.dao.UserDao.UserRoleRow;
import com.shale.ui.util.PerfLog;

public final class UserDetailService {

	private static final int ASSIGNED_CASES_LIMIT = Integer.MAX_VALUE;

	private final UserDao userDao;
	private final CaseDao caseDao;
	private final TaskDao taskDao;

	public UserDetailService(UserDao userDao, CaseDao caseDao, TaskDao taskDao) {
		this.userDao = Objects.requireNonNull(userDao, "userDao");
		this.caseDao = Objects.requireNonNull(caseDao, "caseDao");
		this.taskDao = Objects.requireNonNull(taskDao, "taskDao");
	}

	public UserDetailRow loadUser(int userId, int shaleClientId) {
		long startNanos = PerfLog.start();
		PerfLog.log("DAO", "start", "method=findById page=user_view userId=" + userId + " organizationId=" + shaleClientId);
		UserDetailRow row = userDao.findById(userId, shaleClientId);
		PerfLog.logDone("DAO", "method=findById page=user_view userId=" + userId + " organizationId=" + shaleClientId + " rows=" + (row == null ? 0 : 1), startNanos);
		return row;
	}

	public List<UserRoleRow> loadAssignedRoles(int targetUserId, int shaleClientId) {
		long startNanos = PerfLog.start();
		PerfLog.log("DAO", "start", "method=listAssignedRoles page=user_view userId=" + targetUserId + " organizationId=" + shaleClientId);
		List<UserRoleRow> rows = userDao.listAssignedRoles(targetUserId, shaleClientId);
		PerfLog.logDone("DAO", "method=listAssignedRoles page=user_view userId=" + targetUserId + " organizationId=" + shaleClientId + " rows=" + (rows == null ? 0 : rows.size()), startNanos);
		return rows;
	}

	public List<UserRoleRow> loadAssignableRoles(int targetUserId, int shaleClientId) {
		long startNanos = PerfLog.start();
		PerfLog.log("DAO", "start", "method=listAssignableRoles page=user_view userId=" + targetUserId + " organizationId=" + shaleClientId);
		List<UserRoleRow> rows = userDao.listAssignableRoles(targetUserId, shaleClientId);
		PerfLog.logDone("DAO", "method=listAssignableRoles page=user_view userId=" + targetUserId + " organizationId=" + shaleClientId + " rows=" + (rows == null ? 0 : rows.size()), startNanos);
		return rows;
	}

	public boolean updateBasicProfile(UserProfileUpdateRequest request) {
		return userDao.updateBasicProfile(request);
	}

	public boolean addRoleToUser(int userId, int roleId, int shaleClientId) {
		return userDao.addRoleToUser(userId, roleId, shaleClientId);
	}

	public boolean removeRoleFromUser(int userId, int roleId, int shaleClientId) {
		return userDao.removeRoleFromUser(userId, roleId, shaleClientId);
	}

	public List<CaseRow> loadAssignedCases(int userId) {
		System.out.println("[TRACE ASSIGNED_CASES][UserDetailService.loadAssignedCases] "
				+ "daoMethod=listActiveCasesForUserTeamMember "
				+ "selectedUserId=" + userId
				+ " limit=" + ASSIGNED_CASES_LIMIT);
		List<CaseRow> rows = caseDao.listActiveCasesForUserTeamMember(userId, ASSIGNED_CASES_LIMIT);
		System.out.println("[TRACE ASSIGNED_CASES][UserDetailService.loadAssignedCases] "
				+ "selectedUserId=" + userId
				+ " serviceRowsReceived=" + (rows == null ? 0 : rows.size()));
		return rows;
	}

	public List<AssignedUserTaskRow> loadAssignedTasks(int shaleClientId, int userId) {
		long startNanos = PerfLog.start();
		PerfLog.log("DAO", "start", "method=listActiveTasksForAssigneeInTenant page=user_view userId=" + userId + " organizationId=" + shaleClientId);
		List<AssignedUserTaskRow> rows = taskDao.listActiveTasksForAssigneeInTenant(shaleClientId, userId);
		PerfLog.logDone("DAO", "method=listActiveTasksForAssigneeInTenant page=user_view userId=" + userId + " organizationId=" + shaleClientId + " rows=" + (rows == null ? 0 : rows.size()), startNanos);
		return rows;
	}
}
