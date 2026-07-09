package com.shale.ui.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DevIntakeSaveFailureConfigTest {
	@Test
	void forceFlagWithoutDevGuard_isBlocked() {
		DevIntakeSaveFailureConfig.Resolution resolution = DevIntakeSaveFailureConfig.resolve(
				"true", null, null, null, null);

		assertTrue(resolution.forcePropertyDetected());
		assertFalse(resolution.enabled());
		assertFalse(resolution.devMode());
	}

	@Test
	void forceFlagWithDevProfile_isEnabled() {
		DevIntakeSaveFailureConfig.Resolution resolution = DevIntakeSaveFailureConfig.resolve(
				"true", "dev", null, null, null);

		assertTrue(resolution.forcePropertyDetected());
		assertTrue(resolution.profileDevMode());
		assertTrue(resolution.enabled());
	}

	@Test
	void forceFlagWithJavafxMavenLaunchMode_isEnabledForEclipseMavenRun() {
		DevIntakeSaveFailureConfig.Resolution resolution = DevIntakeSaveFailureConfig.resolve(
				"true", null, null, null, DevIntakeSaveFailureConfig.JAVAFX_MAVEN_PLUGIN_LAUNCH_MODE);

		assertTrue(resolution.forcePropertyDetected());
		assertTrue(resolution.javafxMavenRun());
		assertTrue(resolution.enabled());
	}

	@Test
	void disabledForceFlagStaysDisabledEvenInDev() {
		DevIntakeSaveFailureConfig.Resolution resolution = DevIntakeSaveFailureConfig.resolve(
				"false", "local", null, null, DevIntakeSaveFailureConfig.JAVAFX_MAVEN_PLUGIN_LAUNCH_MODE);

		assertFalse(resolution.forcePropertyDetected());
		assertTrue(resolution.devMode());
		assertFalse(resolution.enabled());
	}
}
