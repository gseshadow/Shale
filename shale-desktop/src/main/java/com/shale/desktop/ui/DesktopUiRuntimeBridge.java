package com.shale.desktop.ui;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
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
	private volatile Integer lastUserId;
	private volatile Integer lastShaleClientId;

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
		lastUserId = userId;
		lastShaleClientId = shaleClientId;

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
			bus.onConnectivityChange(dispatcher::dispatchConnectivity);

			bus.connectAndJoin()
					.whenComplete((ok, ex) ->
					{
						if (ex != null) {
							System.out.println("LiveBus connect failed: " + ex.getMessage());
							dispatcher.dispatchConnectivity(false, "Connect failed");
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
		dispatcher.dispatchConnectivity(false, "Signed out");
		lastUserId = null;
		lastShaleClientId = null;

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
	public void publishOrganizationUpdated(int organizationId, int shaleClientId, int updatedByUserId) {
		publishEntityUpdated("Organization", organizationId, shaleClientId, updatedByUserId, null);
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
	public boolean openPath(Path path) {
		if (path == null) {
			return false;
		}
		try {
			java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
			if (!java.awt.Desktop.isDesktopSupported() || !desktop.isSupported(java.awt.Desktop.Action.BROWSE)) {
				return false;
			}
			desktop.browse(path.toUri());
			return true;
		} catch (IOException | UnsupportedOperationException | SecurityException ex) {
			System.err.println("Failed to open path: " + path + " error=" + ex.getMessage());
			return false;
		}
	}

	@Override
	public String getClientInstanceId() {
		LiveBus bus = liveBus;
		return bus == null ? "" : bus.getClientInstanceId();
	}

	@Override
	public void subscribeEntityUpdated(Consumer<EntityUpdatedEvent> handler) {
		dispatcher.subscribeEntityUpdated(handler);
	}

	@Override
	public void unsubscribeEntityUpdated(Consumer<EntityUpdatedEvent> handler) {
		dispatcher.unsubscribeEntityUpdated(handler);
	}

	@Override
	public void subscribeConnectivity(Consumer<ConnectivityEvent> handler) {
		dispatcher.subscribeConnectivity(handler);
	}

	@Override
	public void unsubscribeConnectivity(Consumer<ConnectivityEvent> handler) {
		dispatcher.unsubscribeConnectivity(handler);
	}

	@Override
	public Optional<Boolean> recheckConnectivity() {
		Integer shaleClientId = lastShaleClientId;
		Integer userId = lastUserId;
		if (shaleClientId == null || shaleClientId <= 0 || userId == null || userId <= 0) {
			return Optional.empty();
		}
		if (negotiateEndpointUrl == null || negotiateEndpointUrl.isBlank()) {
			return Optional.empty();
		}

		LiveBus reconnectBus = new LiveBus(new NegotiateClient(negotiateEndpointUrl.trim()), shaleClientId, userId);
		reconnectBus.onEvent(dispatcher::dispatch);
		reconnectBus.onConnectivityChange(dispatcher::dispatchConnectivity);
		try {
			reconnectBus.connectAndJoin().orTimeout(8, TimeUnit.SECONDS).join();
			LiveBus previous = liveBus;
			liveBus = reconnectBus;
			if (previous != null && previous != reconnectBus) {
				previous.shutdown();
			}
			dispatcher.dispatchConnectivity(true, "Reconnected");
			System.out.println("LiveBus connectivity recheck succeeded.");
			return Optional.of(true);
		} catch (RuntimeException ex) {
			reconnectBus.shutdown();
			dispatcher.dispatchConnectivity(false, "Reconnect failed");
			System.out.println("LiveBus connectivity recheck failed: " + ex.getMessage());
			return Optional.of(false);
		}
	}
}
