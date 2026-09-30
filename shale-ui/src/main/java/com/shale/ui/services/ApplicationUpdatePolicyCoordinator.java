package com.shale.ui.services;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import com.shale.core.dto.ApplicationPolicyView;
import com.shale.core.model.ApplicationUpdatePolicyState;
import com.shale.core.model.ReleaseChannel;
import com.shale.core.model.SemanticVersion;
import com.shale.core.service.ApplicationReleaseReadServicePort;
import com.shale.core.service.ApplicationUpdatePolicyResolver;

/** Session-scoped, fail-open owner of authoritative update-policy presentation. */
public final class ApplicationUpdatePolicyCoordinator implements AutoCloseable {
	public record Presentation(ApplicationUpdatePolicyState state, String message, String detail,
			long revision, String targetVersion, boolean dismissible, boolean stale) {
		public boolean visible() { return state != ApplicationUpdatePolicyState.CURRENT; }
	}
	private static final Duration MAX_ANCHOR_AGE = Duration.ofMinutes(15);
	private static final DateTimeFormatter DEADLINE = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM);
	private final ApplicationReleaseReadServicePort policies;
	private final Supplier<String> versionSource;
	private final Executor worker;
	private final Consumer<Runnable> ui;
	private final Consumer<Presentation> presenter;
	private final LongSupplier monotonicNanos;
	private final ApplicationUpdatePolicyResolver resolver = new ApplicationUpdatePolicyResolver();
	private final AtomicBoolean inFlight = new AtomicBoolean();
	private final AtomicLong generation = new AtomicLong();
	private volatile boolean closed;
	private volatile Snapshot cache;

	public ApplicationUpdatePolicyCoordinator(ApplicationReleaseReadServicePort policies, Supplier<String> versionSource,
			Executor worker, Consumer<Runnable> ui, Consumer<Presentation> presenter) {
		this(policies, versionSource, worker, ui, presenter, System::nanoTime);
	}
	ApplicationUpdatePolicyCoordinator(ApplicationReleaseReadServicePort policies, Supplier<String> versionSource,
			Executor worker, Consumer<Runnable> ui, Consumer<Presentation> presenter, LongSupplier monotonicNanos) {
		this.policies=Objects.requireNonNull(policies);this.versionSource=Objects.requireNonNull(versionSource);
		this.worker=Objects.requireNonNull(worker);this.ui=Objects.requireNonNull(ui);this.presenter=Objects.requireNonNull(presenter);
		this.monotonicNanos=Objects.requireNonNull(monotonicNanos);
	}
	public void refresh() {
		if (closed || !inFlight.compareAndSet(false,true)) return;
		long requestedGeneration=generation.get();
		worker.execute(() -> { try {
			Optional<ApplicationPolicyView> read = policies.findCurrentPolicy(ReleaseChannel.PRODUCTION);
			if (closed||requestedGeneration!=generation.get()) return;
			if (read.isEmpty()) { cache=null; publish(new Presentation(ApplicationUpdatePolicyState.CURRENT,"","",0,"",false,false)); return; }
			ApplicationPolicyView policy=read.get();
			if (policy.serverTime()==null) throw new IllegalStateException("Policy response omitted authoritative server time");
			cache=new Snapshot(policy, policy.serverTime(), monotonicNanos.getAsLong());
			publish(evaluate(cache,false));
		} catch (RuntimeException failure) {
			Snapshot known=cache;
			publish(known==null ? unknown() : evaluate(known,true));
		} finally { inFlight.set(false); }});
	}
	/** Synchronous worker-thread refresh used by the in-session automatic evaluator. */
	public Presentation refreshForAutomaticEvaluation() {
		if (closed) return unknown();
		Optional<ApplicationPolicyView> read = policies.findCurrentPolicy(ReleaseChannel.PRODUCTION);
		if (read.isEmpty() || read.orElseThrow().serverTime() == null) return unknown();
		ApplicationPolicyView policy = read.orElseThrow();
		Snapshot fresh = new Snapshot(policy, policy.serverTime(), monotonicNanos.getAsLong());
		cache = fresh;
		return evaluate(fresh, false);
	}
	public void reset() { generation.incrementAndGet();cache=null;inFlight.set(false); }
	private Presentation evaluate(Snapshot snapshot, boolean outage) {
		long elapsed=Math.max(0,monotonicNanos.getAsLong()-snapshot.receivedNanos);
		boolean stale=elapsed>MAX_ANCHOR_AGE.toNanos();
		Instant now=stale?null:snapshot.serverTime.plusNanos(elapsed);
		SemanticVersion running;
		try { running=SemanticVersion.parse(versionSource.get()); } catch(RuntimeException malformed) { return unknown(); }
		ApplicationPolicyView p=snapshot.policy;
		ApplicationUpdatePolicyState state=resolver.resolve(running,ReleaseChannel.PRODUCTION,p,now);
		if(stale) state=ApplicationUpdatePolicyState.UNKNOWN;
		String target=p.minimumAllowed()!=null?p.minimumAllowed().version().toString():p.minimumRecommended()!=null?p.minimumRecommended().version().toString():"";
		String message=switch(state){
			case CURRENT -> "";
			case RECOMMENDED -> "A newer version of Shale is available.";
			case REQUIRED_BEFORE_DEADLINE -> "Shale must be updated by "+format(p.requiredUpdateDeadline())+".";
			case REQUIRED_DEADLINE_REACHED -> "This version of Shale requires an update.";
			case UNKNOWN -> "Update policy is temporarily unavailable.";
		};
		String detail=outage||stale?(state==ApplicationUpdatePolicyState.REQUIRED_DEADLINE_REACHED
				? "Showing fresh last-known policy while Shale reconnects. Existing work may be finished, but new work remains blocked."
				: "Showing last-known policy while Shale reconnects. Work remains available."):detail(state,p,now,target);
		return new Presentation(state,message,detail,p.revisionNumber(),target,
				state==ApplicationUpdatePolicyState.RECOMMENDED||state==ApplicationUpdatePolicyState.REQUIRED_BEFORE_DEADLINE,outage||stale);
	}
	private static String detail(ApplicationUpdatePolicyState state,ApplicationPolicyView p,Instant now,String target){
		if(state==ApplicationUpdatePolicyState.RECOMMENDED)return "Recommended version: "+target;
		if(state==ApplicationUpdatePolicyState.REQUIRED_BEFORE_DEADLINE){long days=Math.max(1,Duration.between(now,p.requiredUpdateDeadline()).toDays()+1);return days+" day"+(days==1?"":"s")+" remaining · Required version: "+target;}
		if(state==ApplicationUpdatePolicyState.REQUIRED_DEADLINE_REACHED)return "Required version: "+target+". Finish existing work; an update is required before starting new work.";
		return "";
	}
	private static String format(Instant instant){return DEADLINE.format(instant.atZone(ZoneId.systemDefault()));}
	private static Presentation unknown(){return new Presentation(ApplicationUpdatePolicyState.UNKNOWN,"Update policy is temporarily unavailable.","Shale remains usable while the policy is revalidated.",0,"",false,true);}
	private void publish(Presentation value){ui.accept(()->{if(!closed)presenter.accept(value);});}
	@Override public void close(){closed=true;generation.incrementAndGet();cache=null;}
	private record Snapshot(ApplicationPolicyView policy,Instant serverTime,long receivedNanos){}
}
