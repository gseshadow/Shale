package com.shale.ui.controller;

import com.shale.ui.component.dialog.AppDialogs;
import com.shale.ui.services.UiRuntimeBridge;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.sql.SQLTransientConnectionException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class NewIntakeControllerConnectivityPreflightTest {

	@Test
	void cachedOfflineFreshOnline_allowsCreateAndPromotesKnownOnlineState() throws Exception {
		NewIntakeController controller = new NewIntakeController();
		setField(controller, "knownOnlineState", Boolean.FALSE);
		setField(controller, "runtimeBridge", bridgeReturning(Optional.of(true)));

		boolean blocked = invokeShouldBlockCreateForOfflinePreflight(controller);

		assertFalse(blocked);
		assertEquals(Boolean.TRUE, getField(controller, "knownOnlineState"));
	}

	@Test
	void cachedOfflineFreshOffline_blocksCreate() throws Exception {
		NewIntakeController controller = new NewIntakeController();
		setField(controller, "knownOnlineState", Boolean.FALSE);
		setField(controller, "runtimeBridge", bridgeReturning(Optional.of(false)));

		boolean blocked = invokeShouldBlockCreateForOfflinePreflight(controller);

		assertTrue(blocked);
	}

	@Test
	void retryPath_firstOfflineSecondOnline_changesFromBlockedToAllowed() throws Exception {
		NewIntakeController controller = new NewIntakeController();
		setField(controller, "knownOnlineState", Boolean.FALSE);
		AtomicInteger attempts = new AtomicInteger(0);
		setField(controller, "runtimeBridge", new UiRuntimeBridge() {
			@Override
			public void onLoginSuccess(int userId, int shaleClientId, String email) {
			}

			@Override
			public void onLogout() {
			}

			@Override
			public Optional<Boolean> recheckConnectivity() {
				int n = attempts.getAndIncrement();
				return n == 0 ? Optional.of(false) : Optional.of(true);
			}
		});

		boolean firstBlocked = invokeShouldBlockCreateForOfflinePreflight(controller);
		boolean secondBlocked = invokeShouldBlockCreateForOfflinePreflight(controller);

		assertTrue(firstBlocked);
		assertFalse(secondBlocked);
	}

	@Test
	void cachedOfflineRecheckUnavailable_doesNotHardBlock() throws Exception {
		NewIntakeController controller = new NewIntakeController();
		setField(controller, "knownOnlineState", Boolean.FALSE);
		setField(controller, "runtimeBridge", bridgeReturning(Optional.empty()));

		boolean blocked = invokeShouldBlockCreateForOfflinePreflight(controller);

		assertFalse(blocked);
	}

	@Test
	void sqlTransientConnectionException_isConnectivityFailure() throws Exception {
		NewIntakeController controller = new NewIntakeController();

		boolean connectivityFailure = invokeIsConnectivityFailure(controller,
				new RuntimeException("save failed", new SQLTransientConnectionException("Network timeout", "08S01")));

		assertTrue(connectivityFailure);
	}

	@Test
	void practiceAreaConnectivityFailureUsesRecoveryMessageNotConfiguredMessage() throws Exception {
		NewIntakeController controller = new NewIntakeController();
		Object preflightResult = newPracticeAreaValidationResult(List.of(), true);

		List<String> errors = invokeValidatePracticeAreaSelection(controller, preflightResult);

		assertEquals(1, errors.size());
		assertTrue(errors.getFirst().contains("Shale could not connect to the database to verify practice areas"));
		assertFalse(errors.getFirst().contains("No tenant practice areas are configured"));
	}

	@Test
	void recoveryDialogActionsIncludeLocalBackupAndCopyTextForPreflightFailures() throws Exception {
		NewIntakeController controller = new NewIntakeController();

		List<?> actions = invokeRecoveryDialogActions(controller);
		List<String> labels = actions.stream().map(NewIntakeControllerConnectivityPreflightTest::dialogActionText).toList();

		assertEquals(List.of("Try Again", "Save Local Backup", "Copy Intake Text", "Keep Editing"), labels);
	}

	@Test
	void practiceAreaPreflightTimeoutNormalizesToRuntimeException() throws Exception {
		NewIntakeController controller = new NewIntakeController();

		RuntimeException normalized = invokeNormalizePreflightException(controller,
				new CompletionException(new TimeoutException("slow network")));

		assertInstanceOf(TimeoutException.class, normalized.getCause());
		assertTrue(normalized.getMessage().contains("timed out"));
	}

	private static UiRuntimeBridge bridgeReturning(Optional<Boolean> result) {
		return new UiRuntimeBridge() {
			@Override
			public void onLoginSuccess(int userId, int shaleClientId, String email) {
			}

			@Override
			public void onLogout() {
			}

			@Override
			public Optional<Boolean> recheckConnectivity() {
				return result;
			}
		};
	}

	private static boolean invokeShouldBlockCreateForOfflinePreflight(NewIntakeController controller) throws Exception {
		Method method = NewIntakeController.class.getDeclaredMethod("shouldBlockCreateForOfflinePreflight");
		method.setAccessible(true);
		return (boolean) method.invoke(controller);
	}

	private static boolean invokeIsConnectivityFailure(NewIntakeController controller, Throwable throwable) throws Exception {
		Method method = NewIntakeController.class.getDeclaredMethod("isConnectivityFailure", Throwable.class);
		method.setAccessible(true);
		return (boolean) method.invoke(controller, throwable);
	}

	@SuppressWarnings("unchecked")
	private static List<String> invokeValidatePracticeAreaSelection(NewIntakeController controller, Object state) throws Exception {
		Method method = NewIntakeController.class.getDeclaredMethod("validatePracticeAreaSelection", state.getClass());
		method.setAccessible(true);
		return (List<String>) method.invoke(controller, state);
	}

	@SuppressWarnings("unchecked")
	private static List<AppDialogs.DialogAction<?>> invokeRecoveryDialogActions(NewIntakeController controller) throws Exception {
		Method method = NewIntakeController.class.getDeclaredMethod("recoveryDialogActions");
		method.setAccessible(true);
		return (List<AppDialogs.DialogAction<?>>) method.invoke(controller);
	}

	private static RuntimeException invokeNormalizePreflightException(NewIntakeController controller, Throwable throwable) throws Exception {
		Method method = NewIntakeController.class.getDeclaredMethod("normalizePreflightException", Throwable.class);
		method.setAccessible(true);
		return (RuntimeException) method.invoke(controller, throwable);
	}

	private static Object newPracticeAreaValidationResult(List<?> practiceAreas, boolean unverifiedDueToConnectivity) throws Exception {
		Class<?> nestedType = Class.forName("com.shale.ui.controller.NewIntakeController$PracticeAreaValidationResult");
		Constructor<?> constructor = nestedType.getDeclaredConstructor(List.class, boolean.class);
		constructor.setAccessible(true);
		return constructor.newInstance(practiceAreas, unverifiedDueToConnectivity);
	}

	private static String dialogActionText(Object action) {
		try {
			Method method = action.getClass().getDeclaredMethod("text");
			method.setAccessible(true);
			return (String) method.invoke(action);
		} catch (ReflectiveOperationException ex) {
			throw new AssertionError(ex);
		}
	}

	private static void restoreProperty(String key, String value) {
		if (value == null) {
			System.clearProperty(key);
		} else {
			System.setProperty(key, value);
		}
	}

	private static void setField(Object target, String fieldName, Object value) throws Exception {
		Field field = target.getClass().getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(target, value);
	}

	private static Object getField(Object target, String fieldName) throws Exception {
		Field field = target.getClass().getDeclaredField(fieldName);
		field.setAccessible(true);
		return field.get(target);
	}
}
