package com.shale.ui.services;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import com.shale.core.dto.*;
import com.shale.core.dto.ApplicationPolicyView.ReleaseReference;
import com.shale.core.model.*;
import com.shale.core.service.ApplicationReleaseReadServicePort;

class ApplicationUpdatePolicyCoordinatorTest {
	@Test void serverAnchorIgnoresWallClockAndBecomesUnknownWhenStale(){Fixture f=new Fixture();f.refresh();assertEquals(ApplicationUpdatePolicyState.REQUIRED_BEFORE_DEADLINE,f.last().state());f.nanos.set(java.time.Duration.ofMinutes(16).toNanos());f.service.fail=true;f.refresh();assertEquals(ApplicationUpdatePolicyState.UNKNOWN,f.last().state());assertTrue(f.last().stale());}
	@Test void transientFailureUsesMarkedLastKnownAndRecoveryAppliesCorrection(){Fixture f=new Fixture();f.refresh();f.service.fail=true;f.refresh();assertEquals(ApplicationUpdatePolicyState.REQUIRED_BEFORE_DEADLINE,f.last().state());assertTrue(f.last().stale());f.service.fail=false;f.service.policy=Optional.of(policy(8,"1.0.100"));f.refresh();assertEquals(ApplicationUpdatePolicyState.CURRENT,f.last().state());}
	@Test void noCacheFailureIsUnknownAndNeverBlocks(){Fixture f=new Fixture();f.service.fail=true;f.refresh();assertEquals(ApplicationUpdatePolicyState.UNKNOWN,f.last().state());assertTrue(f.last().detail().contains("remains usable"));}
	@Test void freshBlockedAuthoritySurvivesTransientOutageButExpiresAfterFifteenMinutes(){Fixture f=new Fixture();f.service.policy=Optional.of(policy(9,"1.0.129"));f.nanos.set(java.time.Duration.ofDays(2).toNanos());f.refresh();assertEquals(ApplicationUpdatePolicyState.REQUIRED_DEADLINE_REACHED,f.last().state());f.service.fail=true;f.nanos.addAndGet(java.time.Duration.ofMinutes(14).toNanos());f.refresh();assertEquals(ApplicationUpdatePolicyState.REQUIRED_DEADLINE_REACHED,f.last().state());assertTrue(f.last().detail().contains("new work remains blocked"));f.nanos.addAndGet(java.time.Duration.ofMinutes(2).toNanos());f.refresh();assertEquals(ApplicationUpdatePolicyState.UNKNOWN,f.last().state());}
	private static ApplicationPolicyView policy(long revision,String allowed){Instant server=Instant.parse("2026-10-04T00:00:00Z");return new ApplicationPolicyView(1,ReleaseChannel.PRODUCTION,revision,ref("1.0.130"),ref("1.0.130"),ref(allowed),Instant.parse("2026-10-05T00:00:00Z"),ApplicationAccessMode.NORMAL,server,server,new byte[]{1});}
	private static ReleaseReference ref(String v){return new ReleaseReference(1,SemanticVersion.parse(v));}
	private static final class Fixture{final Service service=new Service();final AtomicLong nanos=new AtomicLong();final List<ApplicationUpdatePolicyCoordinator.Presentation> values=new ArrayList<>();final ApplicationUpdatePolicyCoordinator coordinator=new ApplicationUpdatePolicyCoordinator(service,()->"1.0.128",Runnable::run,Runnable::run,values::add,nanos::get);void refresh(){coordinator.refresh();}ApplicationUpdatePolicyCoordinator.Presentation last(){return values.get(values.size()-1);}}
	private static final class Service implements ApplicationReleaseReadServicePort{Optional<ApplicationPolicyView> policy=Optional.of(policy(7,"1.0.129"));boolean fail;public Optional<ApplicationPolicyView> findCurrentPolicy(ReleaseChannel c){if(fail)throw new IllegalStateException("offline");return policy;}public List<ApplicationReleaseView> listPublishedReleasesAfter(ReleaseChannel c,SemanticVersion v){return List.of();}public List<ApplicationReleaseItemView> listReleaseItems(long id){return List.of();}}
}
