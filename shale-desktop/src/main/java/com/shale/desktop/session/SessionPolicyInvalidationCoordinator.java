package com.shale.desktop.session;

import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import com.shale.desktop.live.LiveEventDispatcher;
import com.shale.desktop.live.LiveEventDispatcher.ApplicationPolicyChangedEvent;
import com.shale.desktop.live.LiveEventDispatcher.SessionInvalidatedEvent;
import com.shale.ui.services.UiRuntimeBridge.ConnectivityEvent;

/** Turns best-effort LiveBus hints into bounded authoritative reads for one login generation. */
public final class SessionPolicyInvalidationCoordinator implements AutoCloseable {
	private final LiveEventDispatcher dispatcher;private final DesktopSessionEnrollmentClient client;
	private final DesktopServerSession session;private final Executor worker;private final Runnable policyRefresh;
	private final int tenant;private final long generation;private final java.util.function.LongSupplier currentGeneration;
	private final AtomicBoolean sessionInFlight=new AtomicBoolean(),policyInFlight=new AtomicBoolean();
	private final ScheduledExecutorService scheduler;private final Runnable confirmedRevocation;
	public static final long MAX_REVALIDATION_INTERVAL_SECONDS=60;
	private final Consumer<SessionInvalidatedEvent> sessions=this::onSession;
	private final Consumer<ApplicationPolicyChangedEvent> policies=this::onPolicy;
	private final Consumer<ConnectivityEvent> connectivity=this::onConnectivity;
	private volatile boolean closed;
	public SessionPolicyInvalidationCoordinator(LiveEventDispatcher dispatcher,DesktopSessionEnrollmentClient client,
			DesktopServerSession session,int tenant,long generation,java.util.function.LongSupplier currentGeneration,
			Executor worker,Runnable policyRefresh,Runnable confirmedRevocation){this(dispatcher,client,session,tenant,generation,currentGeneration,worker,policyRefresh,confirmedRevocation,daemonScheduler());}
	SessionPolicyInvalidationCoordinator(LiveEventDispatcher dispatcher,DesktopSessionEnrollmentClient client,
			DesktopServerSession session,int tenant,long generation,java.util.function.LongSupplier currentGeneration,
			Executor worker,Runnable policyRefresh,Runnable confirmedRevocation,ScheduledExecutorService scheduler){this.dispatcher=Objects.requireNonNull(dispatcher);this.client=Objects.requireNonNull(client);this.session=Objects.requireNonNull(session);this.tenant=tenant;this.generation=generation;this.currentGeneration=Objects.requireNonNull(currentGeneration);this.worker=Objects.requireNonNull(worker);this.policyRefresh=Objects.requireNonNull(policyRefresh);this.confirmedRevocation=Objects.requireNonNull(confirmedRevocation);this.scheduler=Objects.requireNonNull(scheduler);dispatcher.subscribeSessionInvalidated(sessions);dispatcher.subscribeApplicationPolicyChanged(policies);dispatcher.subscribeConnectivity(connectivity);scheduler.scheduleWithFixedDelay(this::revalidateSession,MAX_REVALIDATION_INTERVAL_SECONDS,MAX_REVALIDATION_INTERVAL_SECONDS,TimeUnit.SECONDS);}
	private void onSession(SessionInvalidatedEvent event){var current=session.current();if(closed||event.shaleClientId()!=tenant||current.isEmpty()||!current.get().sessionId().toString().equals(event.sessionId()))return;revalidateSession();}
	private void onPolicy(ApplicationPolicyChangedEvent event){if(!closed&&"PRODUCTION".equals(event.channel()))refreshPolicy();}
	private void onConnectivity(ConnectivityEvent event){if(!closed&&event.online()){revalidateSession();refreshPolicy();}}
	void revalidateSession(){if(!current()||!sessionInFlight.compareAndSet(false,true))return;worker.execute(()->{try{var credential=session.current();if(credential.isEmpty()||!current())return;if(client.validate(credential.get().accessToken())==DesktopSessionEnrollmentClient.Validation.REVOKED&&current()&&session.current().filter(v->v.sessionId().equals(credential.get().sessionId())).isPresent())confirmedRevocation.run();}finally{sessionInFlight.set(false);}});}
	private void refreshPolicy(){if(!current()||!policyInFlight.compareAndSet(false,true))return;worker.execute(()->{try{if(current())policyRefresh.run();}finally{policyInFlight.set(false);}});}
	private boolean current(){return !closed&&generation==currentGeneration.getAsLong();}
	@Override public void close(){closed=true;scheduler.shutdownNow();dispatcher.unsubscribeSessionInvalidated(sessions);dispatcher.unsubscribeApplicationPolicyChanged(policies);dispatcher.unsubscribeConnectivity(connectivity);}
	private static ScheduledExecutorService daemonScheduler(){return Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"durable-session-revalidation");t.setDaemon(true);return t;});}
}
