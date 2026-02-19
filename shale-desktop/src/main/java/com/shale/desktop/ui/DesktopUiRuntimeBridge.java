package com.shale.desktop.ui;

import java.util.concurrent.CopyOnWriteArrayList;
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
	private final CopyOnWriteArrayList<Consumer<CaseUpdatedEvent>> caseUpdatedSubscribers = new CopyOnWriteArrayList<>();
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
			NegotiateClient negotiateClient = new NegotiateClient(negotiateEndpointUrl);
			LiveBus bus = new LiveBus(negotiateClient, shaleClientId, userId);
			bus.onEvent(event ->
			{
				if (!"CaseUpdated".equals(event.type) || event.caseId == null) {
					return;
				}
				int tenantId = event.shaleClientId == null ? shaleClientId : event.shaleClientId;
				CaseUpdatedEvent dto = new CaseUpdatedEvent(event.caseId, tenantId, event.updatedByUserId);
				for (var handler : caseUpdatedSubscribers) {
					try {
						handler.accept(dto);
					} catch (Exception ignored) {
					}
				}
			});
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
		// 1) stop live bus
		LiveBus bus = liveBus;
		liveBus = null;
		if (bus != null) {
			bus.shutdown();
		}

		// 2) clear runtime session wiring
		dbProvider.clear(); // <-- add this (implement clear() if you haven't)
		if (runtimeSessionService != null) {
			runtimeSessionService.clear(); // <-- add this (you implement clear() in RuntimeSessionService)
		}

		System.out.println("Logout requested");
	}

	@Override
	public void publishCaseUpdated(int caseId, int shaleClientId, int updatedByUserId) {
		LiveBus bus = liveBus;
		if (bus == null) {
			return;
		}
		bus.publishCaseUpdated(caseId, shaleClientId, updatedByUserId)
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
		if (handler != null) {
			caseUpdatedSubscribers.add(handler);
		}
	}

	@Override
	public void unsubscribeCaseUpdated(Consumer<CaseUpdatedEvent> handler) {
		if (handler != null) {
			caseUpdatedSubscribers.remove(handler);
		}
	}

	public void setRuntimeSessionService(RuntimeSessionService runtime) {
		this.runtimeSessionService = runtime;
	}
}
