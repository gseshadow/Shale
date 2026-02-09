package com.shale.core.constants;

/** App-wide constants that multiple modules use. */
public final class AppConstants {
	private AppConstants() {
	}

	public static final String TENANT_GROUP_PREFIX = "tenant-";

	/** e.g., tenant-7 */
	public static String tenantGroup(int shaleClientId) {
		return TENANT_GROUP_PREFIX + shaleClientId;
	}
}
