package com.shale.ui.services;

import java.util.Locale;

/**
 * Resolves the developer-only forced New Intake save failure switch.
 *
 * This intentionally requires both the explicit failure flag and a developer
 * launch signal so packaged production builds cannot enable the switch by
 * accidentally carrying the flag.
 */
public final class DevIntakeSaveFailureConfig {
	public static final String FORCE_FAILURE_PROPERTY = "shale.dev.forceIntakeSaveFailure";
	public static final String SHALE_PROFILE_PROPERTY = "shale.profile";
	public static final String APP_PROFILE_PROPERTY = "app.profile";
	public static final String DEV_ENABLED_PROPERTY = "shale.dev.enabled";
	public static final String LAUNCH_MODE_PROPERTY = "shale.launchMode";
	public static final String JAVAFX_MAVEN_PLUGIN_LAUNCH_MODE = "javafx-maven-plugin";

	private DevIntakeSaveFailureConfig() {
	}

	public static Resolution resolveFromSystemProperties() {
		String forceValue = System.getProperty(FORCE_FAILURE_PROPERTY);
		String shaleProfile = System.getProperty(SHALE_PROFILE_PROPERTY);
		String appProfile = System.getProperty(APP_PROFILE_PROPERTY);
		String devEnabledValue = System.getProperty(DEV_ENABLED_PROPERTY);
		String launchMode = System.getProperty(LAUNCH_MODE_PROPERTY);
		return resolve(forceValue, shaleProfile, appProfile, devEnabledValue, launchMode);
	}

	static Resolution resolve(
			String forceValue,
			String shaleProfile,
			String appProfile,
			String devEnabledValue,
			String launchMode) {
		boolean forceDetected = Boolean.parseBoolean(blankToFalse(forceValue));
		String activeProfile = firstNonBlank(shaleProfile, appProfile, "");
		boolean profileDevMode = isDevProfile(activeProfile);
		boolean explicitDevMode = Boolean.parseBoolean(blankToFalse(devEnabledValue));
		boolean javafxMavenRun = JAVAFX_MAVEN_PLUGIN_LAUNCH_MODE.equalsIgnoreCase(safeTrim(launchMode));
		boolean devMode = profileDevMode || explicitDevMode || javafxMavenRun;
		boolean enabled = forceDetected && devMode;
		String reason;
		if (enabled) {
			reason = "enabled: force flag detected and developer/local launch guard is active";
		} else if (forceDetected) {
			reason = "blocked: force flag detected but developer/local launch guard is inactive";
		} else {
			reason = "disabled: force flag was not detected";
		}
		return new Resolution(forceDetected, enabled, activeProfile, devMode, profileDevMode, explicitDevMode, javafxMavenRun, reason);
	}

	public static void logStartupResolution() {
		Resolution resolution = resolveFromSystemProperties();
		System.out.println("[DevIntakeSaveFailure] startup activeProfile=" + printable(resolution.activeProfile())
				+ " devMode=" + resolution.devMode()
				+ " profileDevMode=" + resolution.profileDevMode()
				+ " explicitDevMode=" + resolution.explicitDevMode()
				+ " javafxMavenRun=" + resolution.javafxMavenRun()
				+ " forcePropertyDetected=" + resolution.forcePropertyDetected()
				+ " forcedIntakeFailureEnabled=" + resolution.enabled()
				+ " reason=\"" + resolution.reason() + "\"");
	}

	private static boolean isDevProfile(String profile) {
		String normalized = safeTrim(profile).toLowerCase(Locale.ROOT);
		return normalized.equals("dev") || normalized.equals("local") || normalized.equals("development");
	}

	private static String firstNonBlank(String... values) {
		if (values == null) return "";
		for (String value : values) {
			String trimmed = safeTrim(value);
			if (!trimmed.isBlank()) return trimmed;
		}
		return "";
	}

	private static String blankToFalse(String value) {
		String trimmed = safeTrim(value);
		return trimmed.isBlank() ? "false" : trimmed;
	}

	private static String printable(String value) {
		String trimmed = safeTrim(value);
		return trimmed.isBlank() ? "<none>" : trimmed;
	}

	private static String safeTrim(String value) {
		return value == null ? "" : value.trim();
	}

	public record Resolution(
			boolean forcePropertyDetected,
			boolean enabled,
			String activeProfile,
			boolean devMode,
			boolean profileDevMode,
			boolean explicitDevMode,
			boolean javafxMavenRun,
			String reason) {
	}
}
