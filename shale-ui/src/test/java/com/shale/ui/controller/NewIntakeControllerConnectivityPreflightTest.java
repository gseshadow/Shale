package com.shale.ui.controller;

import com.shale.ui.services.UiRuntimeBridge;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
