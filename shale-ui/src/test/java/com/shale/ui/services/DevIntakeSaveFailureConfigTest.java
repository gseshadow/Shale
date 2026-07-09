package com.shale.ui.services;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DevIntakeSaveFailureConfigTest {
	@Test
	void forceFlagWithoutDevGuard_isBlocked() {
		DevIntakeSaveFailureConfig.Resolution resolution = DevIntakeSaveFailureConfig.resolve(
				"true", null, null, null, null, null);

		assertTrue(resolution.forcePropertyDetected());
		assertFalse(resolution.enabled());
		assertFalse(resolution.devMode());
	}

	@Test
	void forceFlagWithDevProfile_isEnabled() {
		DevIntakeSaveFailureConfig.Resolution resolution = DevIntakeSaveFailureConfig.resolve(
				"true", "dev", null, null, null, null);

		assertTrue(resolution.forcePropertyDetected());
		assertTrue(resolution.profileDevMode());
		assertTrue(resolution.enabled());
	}

	@Test
	void forceFlagWithJavafxMavenLaunchMode_isEnabledForEclipseMavenRun() {
		DevIntakeSaveFailureConfig.Resolution resolution = DevIntakeSaveFailureConfig.resolve(
				"true", null, null, null, null, DevIntakeSaveFailureConfig.JAVAFX_MAVEN_PLUGIN_LAUNCH_MODE);

		assertTrue(resolution.forcePropertyDetected());
		assertTrue(resolution.javafxMavenRun());
		assertTrue(resolution.enabled());
	}

	@Test
	void disabledForceFlagStaysDisabledEvenInDev() {
		DevIntakeSaveFailureConfig.Resolution resolution = DevIntakeSaveFailureConfig.resolve(
				"false", "local", null, null, null, DevIntakeSaveFailureConfig.JAVAFX_MAVEN_PLUGIN_LAUNCH_MODE);

		assertFalse(resolution.forcePropertyDetected());
		assertTrue(resolution.devMode());
		assertFalse(resolution.enabled());
	}

	@Test
	void devTriggerCanForceOneIntakeSaveFailureAndReset() {
		withProperties(() -> {
			System.setProperty(DevIntakeSaveFailureConfig.SHALE_PROFILE_PROPERTY, "dev");
			DevIntakeSaveFailureConfig.clearNextIntakeSaveFailure();

			assertTrue(DevIntakeSaveFailureConfig.armNextIntakeSaveFailureFromDeveloperUi());
			assertTrue(DevIntakeSaveFailureConfig.isNextIntakeSaveFailureArmed());
			assertTrue(DevIntakeSaveFailureConfig.consumeNextIntakeSaveFailure());
			assertFalse(DevIntakeSaveFailureConfig.isNextIntakeSaveFailureArmed());
			assertFalse(DevIntakeSaveFailureConfig.consumeNextIntakeSaveFailure());
		});
	}

	@Test
	void productionModeCannotArmDevTrigger() {
		withProperties(() -> {
			System.setProperty(DevIntakeSaveFailureConfig.SHALE_PROFILE_PROPERTY, "prod");
			DevIntakeSaveFailureConfig.clearNextIntakeSaveFailure();

			assertFalse(DevIntakeSaveFailureConfig.armNextIntakeSaveFailureFromDeveloperUi());
			assertFalse(DevIntakeSaveFailureConfig.isNextIntakeSaveFailureArmed());
		});
	}

	@Test
	void forcedFailureThrowsBeforePrimarySaveSupplierRuns() {
		withProperties(() -> {
			System.setProperty(DevIntakeSaveFailureConfig.SHALE_PROFILE_PROPERTY, "local");
			DevIntakeSaveFailureConfig.clearNextIntakeSaveFailure();
			assertTrue(DevIntakeSaveFailureConfig.armNextIntakeSaveFailureFromDeveloperUi());
			AtomicBoolean primarySaveCalled = new AtomicBoolean(false);

			assertThrows(DevIntakeSaveFailureConfig.ForcedIntakeSaveFailureException.class,
					() -> DevIntakeSaveFailureConfig.runPrimaryIntakeSaveUnlessForced(() -> {
						primarySaveCalled.set(true);
						return "committed";
					}));

			assertFalse(primarySaveCalled.get());
			assertFalse(DevIntakeSaveFailureConfig.isNextIntakeSaveFailureArmed());
		});
	}

	private static void withProperties(Runnable testBody) {
		String oldForce = System.getProperty(DevIntakeSaveFailureConfig.FORCE_FAILURE_PROPERTY);
		String oldShaleProfile = System.getProperty(DevIntakeSaveFailureConfig.SHALE_PROFILE_PROPERTY);
		String oldAppProfile = System.getProperty(DevIntakeSaveFailureConfig.APP_PROFILE_PROPERTY);
		String oldAppEnv = System.getProperty(DevIntakeSaveFailureConfig.APP_ENV_PROPERTY);
		String oldDevEnabled = System.getProperty(DevIntakeSaveFailureConfig.DEV_ENABLED_PROPERTY);
		String oldLaunchMode = System.getProperty(DevIntakeSaveFailureConfig.LAUNCH_MODE_PROPERTY);
		try {
			System.clearProperty(DevIntakeSaveFailureConfig.FORCE_FAILURE_PROPERTY);
			System.clearProperty(DevIntakeSaveFailureConfig.SHALE_PROFILE_PROPERTY);
			System.clearProperty(DevIntakeSaveFailureConfig.APP_PROFILE_PROPERTY);
			System.clearProperty(DevIntakeSaveFailureConfig.APP_ENV_PROPERTY);
			System.clearProperty(DevIntakeSaveFailureConfig.DEV_ENABLED_PROPERTY);
			System.clearProperty(DevIntakeSaveFailureConfig.LAUNCH_MODE_PROPERTY);
			testBody.run();
		} finally {
			DevIntakeSaveFailureConfig.clearNextIntakeSaveFailure();
			restoreProperty(DevIntakeSaveFailureConfig.FORCE_FAILURE_PROPERTY, oldForce);
			restoreProperty(DevIntakeSaveFailureConfig.SHALE_PROFILE_PROPERTY, oldShaleProfile);
			restoreProperty(DevIntakeSaveFailureConfig.APP_PROFILE_PROPERTY, oldAppProfile);
			restoreProperty(DevIntakeSaveFailureConfig.APP_ENV_PROPERTY, oldAppEnv);
			restoreProperty(DevIntakeSaveFailureConfig.DEV_ENABLED_PROPERTY, oldDevEnabled);
			restoreProperty(DevIntakeSaveFailureConfig.LAUNCH_MODE_PROPERTY, oldLaunchMode);
		}
	}

	private static void restoreProperty(String key, String value) {
		if (value == null) {
			System.clearProperty(key);
		} else {
			System.setProperty(key, value);
		}
	}

}
