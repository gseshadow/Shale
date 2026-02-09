package com.shale.desktop.ui;

import com.shale.data.runtime.RuntimeSessionService;
import com.shale.desktop.live.LiveEventDispatcher;
import com.shale.desktop.runtime.DesktopRuntimeSessionProvider;
import com.shale.ui.services.UiRuntimeBridge;

/**
 * Desktop-side implementation of UiRuntimeBridge. This is where login success initializes
 * runtime services (DB, RLS, live bus).
 */
public final class DesktopUiRuntimeBridge implements UiRuntimeBridge {

	private final LiveEventDispatcher dispatcher;
	private final DesktopRuntimeSessionProvider dbProvider;
	private RuntimeSessionService runtimeSessionService;

	public DesktopUiRuntimeBridge(
			LiveEventDispatcher dispatcher,
			DesktopRuntimeSessionProvider dbProvider) {

		this.dispatcher = dispatcher;
		this.dbProvider = dbProvider;
	}

	@Override
	public void onLoginSuccess(int userId, int shaleClientId, String email) {

		System.out.printf(
				"Login success: user=%d, client=%d, email=%s%n",
				userId, shaleClientId, email
		);

		// 🔑 THIS WAS MISSING
		runtimeSessionService.initialize(shaleClientId, userId);

		// Now allow DB access
		dbProvider.setRuntime(runtimeSessionService);
	}

	@Override
	public void onLogout() {
		// TODO:
		// - close runtime connections
		// - shut down live bus
		// - clear session context
		System.out.println("Logout requested");
	}

	public void setRuntimeSessionService(RuntimeSessionService runtime) {
		this.runtimeSessionService = runtime;
	}
}
