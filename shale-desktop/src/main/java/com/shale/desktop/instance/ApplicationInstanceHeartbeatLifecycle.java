package com.shale.desktop.instance;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.shale.core.model.SemanticVersion;
import com.shale.core.service.ApplicationInstanceServicePort;
import com.shale.core.service.EndedApplicationInstanceException;

/** One bounded, non-overlapping, generation-safe periodic heartbeat per enrollment. */
public final class ApplicationInstanceHeartbeatLifecycle implements AutoCloseable {
	public static final long BASE_INTERVAL_SECONDS=60, JITTER_SECONDS=10;
	private static final Logger log=LoggerFactory.getLogger(ApplicationInstanceHeartbeatLifecycle.class);
	private final ApplicationInstanceServicePort service;
	private final ScheduledExecutorService scheduler;
	private final LongSupplier jitter;
	private final Supplier<String> version;
	private final AtomicLong generation=new AtomicLong();
	private final AtomicBoolean inFlight=new AtomicBoolean();
	private volatile ScheduledFuture<?> scheduled;
	private volatile Context context;
	private final AtomicBoolean outageLogged=new AtomicBoolean();

	public ApplicationInstanceHeartbeatLifecycle(ApplicationInstanceServicePort service){this(service,daemonScheduler(),()->ThreadLocalRandom.current().nextLong(-JITTER_SECONDS,JITTER_SECONDS+1),com.shale.ui.services.AppVersionProvider::currentVersion);}
	ApplicationInstanceHeartbeatLifecycle(ApplicationInstanceServicePort service,ScheduledExecutorService scheduler,LongSupplier jitter,Supplier<String> version){this.service=Objects.requireNonNull(service);this.scheduler=Objects.requireNonNull(scheduler);this.jitter=Objects.requireNonNull(jitter);this.version=Objects.requireNonNull(version);}

	public synchronized void start(int tenant,int user,long instanceId,Supplier<Optional<Instant>> activity){stop();long token=generation.incrementAndGet();context=new Context(token,tenant,user,instanceId,Objects.requireNonNull(activity));schedule(token);}
	public synchronized void stop(){generation.incrementAndGet();context=null;ScheduledFuture<?> task=scheduled;scheduled=null;if(task!=null)task.cancel(false);}
	private synchronized void schedule(long token){if(context==null||context.token()!=token)return;long delay=Math.max(1,BASE_INTERVAL_SECONDS+jitter.getAsLong());scheduled=scheduler.schedule(()->attempt(token),delay,TimeUnit.SECONDS);}
	private void attempt(long token){Context c=context;if(c==null||c.token()!=token||generation.get()!=token){return;}if(!inFlight.compareAndSet(false,true)){schedule(token);return;}try{service.heartbeat(c.tenant(),c.user(),c.instanceId(),SemanticVersion.parse(version.get()),c.activity().get().orElse(null));outageLogged.set(false);}catch(EndedApplicationInstanceException|SecurityException durable){if(isCurrent(token))stop();}catch(RuntimeException transientFailure){if(outageLogged.compareAndSet(false,true))log.warn("Application instance heartbeat temporarily unavailable ({}).",transientFailure.getClass().getSimpleName());}finally{inFlight.set(false);if(isCurrent(token))schedule(token);}}
	private boolean isCurrent(long token){Context c=context;return c!=null&&c.token()==token&&generation.get()==token;}
	@Override public void close(){stop();scheduler.shutdownNow();}
	private record Context(long token,int tenant,int user,long instanceId,Supplier<Optional<Instant>> activity){}
	private static ScheduledExecutorService daemonScheduler(){return Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"application-instance-heartbeat");t.setDaemon(true);return t;});}
}
