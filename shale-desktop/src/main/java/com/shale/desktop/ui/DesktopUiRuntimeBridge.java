package com.shale.desktop.ui;

import java.util.function.Consumer;

import com.shale.data.runtime.RuntimeSessionService;
import com.shale.desktop.live.LiveEventDispatcher;
import com.shale.desktop.net.LiveBus;
import com.shale.desktop.net.NegotiateClient;
import com.shale.desktop.runtime.DesktopRuntimeSessionProvider;
import com.shale.ui.services.UiRuntimeBridge;

/**
 * Desktop-side implementation of UiRuntimeBridge. This is where login success initializes
 * runtime services (DB, RLS, live bus).
 */
public final class DesktopUiRuntimeBridge implements UiRuntimeBridge {

	private final LiveEventDispatcher dispatcher;
	private final DesktopRuntimeSessionProvider dbProvider;
	private final String negotiateEndpointUrl;

	private RuntimeSessionService runtimeSessionService;
	private volatile LiveBus liveBus;

	public DesktopUiRuntimeBridge(
			LiveEventDispatcher dispatcher,
			DesktopRuntimeSessionProvider dbProvider,
			String negotiateEndpointUrl) {

		this.dispatcher = dispatcher;
		this.dbProvider = dbProvider;
		this.negotiateEndpointUrl = negotiateEndpointUrl;
	}

	@Override
	public void onLoginSuccess(int userId, int shaleClientId, String email) {

		System.out.printf(
				"Login success: user=%d, client=%d, email=%s%n",
				userId, shaleClientId, email
		);

		runtimeSessionService.initialize(shaleClientId, userId);
		dbProvider.setRuntime(runtimeSessionService);

		tryConnectLiveBus(shaleClientId, userId);
	}

	private void tryConnectLiveBus(int shaleClientId, int userId) {
		if (negotiateEndpointUrl == null || negotiateEndpointUrl.isBlank()) {
			System.out.println("LiveBus disabled: negotiate endpoint is not configured.");
			return;
		}

		try {
			String base = negotiateEndpointUrl.trim();
			System.out.println("NEGOTIATE BASE URL: " + base);

			NegotiateClient negotiateClient = new NegotiateClient(base);

			LiveBus bus = new LiveBus(negotiateClient, shaleClientId, userId);
			bus.onEvent(dispatcher::dispatch);

			bus.connectAndJoin()
					.whenComplete((ok, ex) ->
					{
						if (ex != null) {
							System.out.println("LiveBus connect failed: " + ex.getMessage());
							return;
						}
						liveBus = bus;
						System.out.println("LiveBus connected.");
					});

		} catch (Exception ex) {
			System.out.println("LiveBus unavailable: " + ex.getMessage());
		}
	}

	@Override
	public void onLogout() {
		LiveBus bus = liveBus;
		liveBus = null;
		if (bus != null) {
			bus.shutdown();
		}

		dbProvider.clear();
		if (runtimeSessionService != null) {
			runtimeSessionService.clear();
		}

		System.out.println("Logout requested");
	}

	// --- Back-compat wrappers now route through the generic API ---

	@Override
	public void publishCaseUpdated(int caseId, int shaleClientId, int updatedByUserId) {
		publishEntityUpdated("Case", caseId, shaleClientId, updatedByUserId, null);
	}

	@Override
	public void publishCaseNameUpdated(int caseId, int shaleClientId, int updatedByUserId, String newName) {
		// Requires UiRuntimeBridge default method publishEntityFieldUpdated(...)
		publishEntityFieldUpdated("Case", caseId, shaleClientId, updatedByUserId, "name", newName);
	}

	@Override
	public void publishEntityUpdated(String entityType, long entityId,
			int shaleClientId, int updatedByUserId,
			String patchJsonOrNull) {

		LiveBus bus = liveBus;
		if (bus == null) {
			return;
		}

		bus.publishEntityUpdated(entityType, entityId, shaleClientId, updatedByUserId, patchJsonOrNull)
				.whenComplete((ok, ex) ->
				{
					if (ex != null) {
						System.out.println("[LIVE] publish failed: " + ex.getMessage());
						return;
					}
					System.out.println("[LIVE] publish ok");
				});
	}

	@Override
	public void subscribeCaseUpdated(Consumer<CaseUpdatedEvent> handler) {
		dispatcher.subscribeCaseUpdated(handler);
	}

	@Override
	public void unsubscribeCaseUpdated(Consumer<CaseUpdatedEvent> handler) {
		dispatcher.unsubscribeCaseUpdated(handler);
	}

	public void setRuntimeSessionService(RuntimeSessionService runtime) {
		this.runtimeSessionService = runtime;
	}

	@Override
	public String getClientInstanceId() {
		LiveBus bus = liveBus;
		return bus == null ? "" : bus.getClientInstanceId();
	}
}