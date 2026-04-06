package com.shale.core.semantics;

import java.util.List;
import java.util.Map;

public final class RoleSemantics {
	public static final int ROLE_ADMIN = 1;
	public static final int ROLE_RESPONSIBLE_ATTORNEY = 4;
	public static final int ROLE_PRELITIGATION_STAFF = 5;
	public static final int ROLE_ATTORNEY = 7;
	public static final int ROLE_LEGAL_ASSISTANT = 11;
	public static final int ROLE_PARALEGAL = 12;
	public static final int ROLE_LAW_CLERK = 13;
	public static final int ROLE_CO_COUNSEL = 14;

	public static final String FLAG_IS_ADMIN = "is_admin";
	public static final String FLAG_IS_ATTORNEY = "is_attorney";

	public static final List<Integer> CASE_TEAM_ROLE_IDS = List.of(
			ROLE_RESPONSIBLE_ATTORNEY,
			ROLE_PRELITIGATION_STAFF,
			ROLE_ATTORNEY,
			ROLE_LEGAL_ASSISTANT,
			ROLE_PARALEGAL,
			ROLE_LAW_CLERK,
			ROLE_CO_COUNSEL
	);

	public static final List<Integer> TEAM_EDITOR_ASSIGNABLE_ROLE_IDS = List.of(
			ROLE_ATTORNEY,
			ROLE_CO_COUNSEL,
			ROLE_LEGAL_ASSISTANT,
			ROLE_PARALEGAL,
			ROLE_LAW_CLERK,
			ROLE_PRELITIGATION_STAFF
	);

	private static final Map<Integer, String> CASE_TEAM_ROLE_LABELS = Map.of(
			ROLE_RESPONSIBLE_ATTORNEY, "Responsible Attorney",
			ROLE_PRELITIGATION_STAFF, "Prelitigation Staff",
			ROLE_ATTORNEY, "Attorney",
			ROLE_LEGAL_ASSISTANT, "Legal Assistant",
			ROLE_PARALEGAL, "Paralegal",
			ROLE_LAW_CLERK, "Law Clerk",
			ROLE_CO_COUNSEL, "Co-counsel"
	);

	private RoleSemantics() {
	}

	public static boolean isAdminRoleId(int roleId) {
		return roleId == ROLE_ADMIN;
	}

	public static boolean isAttorneyRoleId(int roleId) {
		return roleId == ROLE_ATTORNEY;
	}

	public static boolean isResponsibleAttorneyRoleId(int roleId) {
		return roleId == ROLE_RESPONSIBLE_ATTORNEY;
	}

	public static int normalizeCaseTeamRoleForSave(int roleId) {
		return isResponsibleAttorneyRoleId(roleId) ? ROLE_ATTORNEY : roleId;
	}

	public static String roleFlagColumn(int roleId) {
		return switch (roleId) {
		case ROLE_ADMIN -> FLAG_IS_ADMIN;
		case ROLE_ATTORNEY -> FLAG_IS_ATTORNEY;
		default -> throw new IllegalArgumentException("Unsupported role id: " + roleId);
		};
	}

	public static String roleLabel(int roleId) {
		return switch (roleId) {
		case ROLE_RESPONSIBLE_ATTORNEY -> "Responsible Attorney";
		case ROLE_ATTORNEY -> "Attorney";
		case ROLE_ADMIN -> "Admin";
		default -> "Role " + roleId;
		};
	}

	public static String caseTeamRoleLabel(int roleId) {
		String label = CASE_TEAM_ROLE_LABELS.get(roleId);
		return label == null ? "Role " + roleId : label;
	}
}
