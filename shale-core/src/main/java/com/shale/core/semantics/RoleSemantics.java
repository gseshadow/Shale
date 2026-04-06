package com.shale.core.semantics;

public final class RoleSemantics {
	public static final int ROLE_ADMIN = 1;
	public static final int ROLE_RESPONSIBLE_ATTORNEY = 4;
	public static final int ROLE_ATTORNEY = 7;

	public static final String FLAG_IS_ADMIN = "is_admin";
	public static final String FLAG_IS_ATTORNEY = "is_attorney";

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
}
