package com.shale.desktop.ui;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.time.Instant;
import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.shale.data.runtime.RuntimeSessionService;
import com.shale.desktop.live.LiveEventDispatcher;
import com.shale.desktop.net.LiveBus;
import com.shale.desktop.net.NegotiateClient;
import com.shale.desktop.runtime.DesktopRuntimeSessionProvider;
import com.shale.desktop.identity.MachineIdentityResult;
import com.shale.desktop.instance.CurrentApplicationInstance;
import com.shale.desktop.instance.ApplicationInstanceHeartbeatLifecycle;
import com.shale.core.model.ClientType;
import com.shale.core.model.SemanticVersion;
import com.shale.core.service.ApplicationInstanceServicePort;
import com.shale.ui.services.AppVersionProvider;
import com.shale.ui.services.UiRuntimeBridge;
import com.shale.desktop.session.DesktopSessionEnrollmentLifecycle;
import com.shale.desktop.session.UserSessionManagementClient;
import com.shale.desktop.session.AdminSessionManagementClient;
import com.shale.desktop.update.AutomaticUpdatePreferenceService;

/**
 * Desktop-side implementation of UiRuntimeBridge. This is where login success initializes
 * runtime services (DB, RLS, live bus).
 */
public final class DesktopUiRuntimeBridge implements UiRuntimeBridge {

	private static final Logger log = LoggerFactory.getLogger(DesktopUiRuntimeBridge.class);

	private final LiveEventDispatcher dispatcher;
	private final DesktopRuntimeSessionProvider dbProvider;
	private final String negotiateEndpointUrl;
	private final MachineIdentityResult machineIdentity;
	private final ApplicationInstanceServicePort applicationInstances;
	private final CurrentApplicationInstance currentInstance = new CurrentApplicationInstance();
	private final ApplicationInstanceHeartbeatLifecycle heartbeat;
	private final DesktopSessionEnrollmentLifecycle serverSessions;
	private final UiRuntimeBridge.UserSessionManagement sessionManagement;
	private final UiRuntimeBridge.AdminSessionManagement adminSessionManagement;
	private final UiRuntimeBridge.WorkstationAutomaticUpdates workstationAutomaticUpdates;

	private RuntimeSessionService runtimeSessionService;
	private volatile LiveBus liveBus;
	private volatile Integer lastUserId;
	private volatile Integer lastShaleClientId;
	private final AtomicLong sessionGeneration = new AtomicLong();
	private volatile Runnable applicationPolicyRefreshHandler = () -> {};

	public DesktopUiRuntimeBridge(
			LiveEventDispatcher dispatcher,
			DesktopRuntimeSessionProvider dbProvider,
			String negotiateEndpointUrl) {
		this(dispatcher,dbProvider,negotiateEndpointUrl,null,null,null,Optional.empty());
	}

	public DesktopUiRuntimeBridge(LiveEventDispatcher dispatcher, DesktopRuntimeSessionProvider dbProvider,
			String negotiateEndpointUrl, MachineIdentityResult machineIdentity,
			ApplicationInstanceServicePort applicationInstances) {
		this(dispatcher,dbProvider,negotiateEndpointUrl,machineIdentity,applicationInstances,null,Optional.empty());
	}
	public DesktopUiRuntimeBridge(LiveEventDispatcher dispatcher, DesktopRuntimeSessionProvider dbProvider,
			String negotiateEndpointUrl, MachineIdentityResult machineIdentity,
			ApplicationInstanceServicePort applicationInstances,DesktopSessionEnrollmentLifecycle serverSessions) {
		this(dispatcher,dbProvider,negotiateEndpointUrl,machineIdentity,applicationInstances,serverSessions,Optional.empty());
	}
	public DesktopUiRuntimeBridge(LiveEventDispatcher dispatcher, DesktopRuntimeSessionProvider dbProvider,
			String negotiateEndpointUrl, MachineIdentityResult machineIdentity,
			ApplicationInstanceServicePort applicationInstances,DesktopSessionEnrollmentLifecycle serverSessions,
			Optional<URI> serverApiOrigin) {

		this.dispatcher = dispatcher;
		this.dbProvider = dbProvider;
		this.negotiateEndpointUrl = negotiateEndpointUrl;
		this.machineIdentity = machineIdentity;
		this.applicationInstances = applicationInstances;
		this.heartbeat = applicationInstances==null?null:new ApplicationInstanceHeartbeatLifecycle(applicationInstances);
		this.serverSessions=serverSessions;
		this.sessionManagement=serverSessions==null||serverApiOrigin.isEmpty()?null:new UserSessionManagementClient(serverApiOrigin.get().toString(),serverSessions.session());
		this.adminSessionManagement=serverSessions==null||serverApiOrigin.isEmpty()?null:new AdminSessionManagementClient(serverApiOrigin.get().toString(),serverSessions.session());
		AutomaticUpdatePreferenceService preferences = AutomaticUpdatePreferenceService.resolvePlatformDefault();
		this.workstationAutomaticUpdates = new UiRuntimeBridge.WorkstationAutomaticUpdates() {
			@Override public com.shale.core.update.WorkstationUpdatePreference read() { return preferences.current(); }
			@Override public ChangeResult change(boolean enabled, boolean authenticatedAdministrator) {
				return ChangeResult.valueOf(preferences.change(enabled, authenticatedAdministrator).name());
			}
		};
	}
	@Override public Optional<UiRuntimeBridge.WorkstationAutomaticUpdates> workstationAutomaticUpdates() {
		return Optional.of(workstationAutomaticUpdates);
	}
	@Override public Optional<UiRuntimeBridge.AdminSessionManagement> adminSessionManagement(){
		return serverSessions!=null&&serverSessions.state()==DesktopSessionEnrollmentLifecycle.State.ENROLLED?Optional.ofNullable(adminSessionManagement):Optional.empty();
	}

	@Override public Optional<UiRuntimeBridge.UserSessionManagement> userSessionManagement(){
		return serverSessions!=null&&serverSessions.state()==DesktopSessionEnrollmentLifecycle.State.ENROLLED?Optional.ofNullable(sessionManagement):Optional.empty();
	}

	@Override
	public void onLoginSuccess(int userId, int shaleClientId, String email) {
		long generation = sessionGeneration.incrementAndGet();

		log.info("Login success: userId={} tenantId={} emailConfigured={}", userId, shaleClientId, email != null && !email.isBlank());

		runtimeSessionService.initialize(shaleClientId, userId);
		dbProvider.setRuntime(runtimeSessionService);
		lastUserId = userId;
		lastShaleClientId = shaleClientId;
		enrollBestEffort(shaleClientId,userId);
		if(serverSessions!=null){
			Long instanceId=currentInstance.get().map(v->v.id()).orElse(null);
			java.util.concurrent.CompletableFuture.runAsync(()->serverSessions.enroll(instanceId))
					.whenComplete((ignored,failure)->{
						if(failure!=null){log.warn("Desktop durable session enrollment task failed: {}",failure.getClass().getSimpleName());return;}
						if(generation==sessionGeneration.get())serverSessions.startAcceleration(dispatcher,shaleClientId,generation,sessionGeneration::get,applicationPolicyRefreshHandler);
					});
		}

		tryConnectLiveBus(shaleClientId, userId, generation);
	}

	private void tryConnectLiveBus(int shaleClientId, int userId, long generation) {
		if (negotiateEndpointUrl == null || negotiateEndpointUrl.isBlank()) {
			log.info("LiveBus disabled: negotiate endpoint is not configured.");
			return;
		}

		try {
			String base = negotiateEndpointUrl.trim();
			log.info("Live negotiate endpoint configured");

			NegotiateClient negotiateClient = new NegotiateClient(base);

			LiveBus bus = new LiveBus(negotiateClient, shaleClientId, userId);
			bus.onEvent(dispatcher::dispatch);
			bus.onConnectivityChange(dispatcher::dispatchConnectivity);

			bus.connectAndJoin()
					.whenComplete((ok, ex) ->
					{
						if (generation != sessionGeneration.get()) {
							bus.shutdown();
							return;
						}
						if (ex != null) {
							log.warn("LiveBus connect failed: {}", ex.getMessage());
							dispatcher.dispatchConnectivity(false, "Connect failed");
							return;
						}
						liveBus = bus;
						log.info("LiveBus connected.");
					});

		} catch (Exception ex) {
			log.warn("LiveBus unavailable: {}", ex.getMessage());
		}
	}

	@Override
	public void onLogout() {
		teardown(true);
	}
	private void teardown(boolean logicalLogout) {
		if(heartbeat!=null)heartbeat.stop();
		if(serverSessions!=null){if(logicalLogout)serverSessions.logout();else serverSessions.shutdown();}
		endBestEffort();
		sessionGeneration.incrementAndGet();
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

		log.info("Logout requested");
	}

	@Override public void onShutdown(){teardown(false);if(heartbeat!=null)heartbeat.close();}

	@Override public void startApplicationInstanceHeartbeat(Supplier<Optional<Instant>> activity){var enrolled=currentInstance.get();if(heartbeat==null||enrolled.isEmpty()||lastShaleClientId==null||lastUserId==null)return;heartbeat.start(lastShaleClientId,lastUserId,enrolled.get().id(),activity);}

	private void enrollBestEffort(int tenant,int user){
		currentInstance.clear();
		if(applicationInstances==null||machineIdentity==null){return;}
		if(!machineIdentity.isAvailable()){log.warn("Application instance enrollment skipped: machine identity unavailable ({})",machineIdentity.failure().orElse(null));return;}
		try{
			SemanticVersion version=SemanticVersion.parse(AppVersionProvider.currentVersion());
			currentInstance.set(applicationInstances.enroll(tenant,user,machineIdentity.machineId().orElseThrow(),ClientType.DESKTOP,version));
		}catch(RuntimeException ex){log.warn("Application instance enrollment unavailable: {}",ex.getClass().getSimpleName());}
	}

	private void endBestEffort(){
		var active=currentInstance.get(); currentInstance.clear();
		if(active.isEmpty()||applicationInstances==null||lastShaleClientId==null||lastUserId==null)return;
		int tenant=lastShaleClientId,user=lastUserId;long instanceId=active.get().id();
		var executor=java.util.concurrent.Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"shale-instance-end");t.setDaemon(true);return t;});
		try{java.util.concurrent.CompletableFuture.runAsync(()->applicationInstances.end(tenant,user,instanceId),executor).get(2,TimeUnit.SECONDS);}
		catch(Exception ex){log.warn("Application instance end unavailable: {}",ex.getClass().getSimpleName());}
		finally{executor.shutdownNow();}
	}

	public Optional<com.shale.core.dto.ApplicationInstanceView> currentApplicationInstance(){return currentInstance.get();}

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
						log.warn("Live publish failed: {}", ex.getMessage());
						return;
					}
					log.debug("Live publish ok");
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
			log.warn("Failed to open path: {}", path, ex);
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
	public void setApplicationPolicyRefreshHandler(Runnable handler) {
		applicationPolicyRefreshHandler=handler==null?()->{}:handler;
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
			log.info("LiveBus connectivity recheck succeeded.");
			return Optional.of(true);
		} catch (RuntimeException ex) {
			reconnectBus.shutdown();
			dispatcher.dispatchConnectivity(false, "Reconnect failed");
			log.warn("LiveBus connectivity recheck failed: {}", ex.getMessage());
			return Optional.of(false);
		}
	}
}
