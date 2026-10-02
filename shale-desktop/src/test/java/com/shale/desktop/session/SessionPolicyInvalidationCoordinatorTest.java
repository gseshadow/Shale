package com.shale.desktop.session;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;import java.util.*;import java.util.concurrent.Executor;import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import com.shale.desktop.live.LiveEventDispatcher;import com.shale.desktop.net.LiveBus;

class SessionPolicyInvalidationCoordinatorTest {
	@Test void matchingHintRevalidatesButOnlyAuthoritativeRevocationClearsBearer(){Fixture f=new Fixture();f.client.result=DesktopSessionEnrollmentClient.Validation.VALID;f.sessionEvent(7,f.sid);assertTrue(f.session.current().isPresent());assertEquals(1,f.client.validations);f.client.result=DesktopSessionEnrollmentClient.Validation.REVOKED;f.sessionEvent(7,f.sid);assertTrue(f.session.current().isEmpty());assertEquals(2,f.client.validations);f.close();}
	@Test void wrongTenantWrongSessionAndStaleGenerationAreIgnored(){Fixture f=new Fixture();f.sessionEvent(8,f.sid);f.sessionEvent(7,UUID.randomUUID());f.generation.incrementAndGet();f.sessionEvent(7,f.sid);assertEquals(0,f.client.validations);assertTrue(f.session.current().isPresent());f.close();}
	@Test void duplicateHintsCoalesceWhileValidationIsInFlight(){QueueExecutor queue=new QueueExecutor();Fixture f=new Fixture(queue);f.sessionEvent(7,f.sid);f.sessionEvent(7,f.sid);assertEquals(1,queue.tasks.size());queue.run();assertEquals(1,f.client.validations);f.close();}
	@Test void reconnectRevalidatesSessionAndRefreshesGlobalProductionPolicy(){Fixture f=new Fixture();f.dispatcher.dispatchConnectivity(true,"Reconnected");assertEquals(1,f.client.validations);assertEquals(1,f.policyRefreshes.get());f.close();}
	@Test void unknownEventsRemainIgnoredForOlderClientCompatibility(){Fixture f=new Fixture();f.dispatcher.dispatch(event("FUTURE_EVENT",7,null,null));assertEquals(0,f.client.validations);f.close();}
	private static LiveBus.Event event(String type,int tenant,UUID sid,String channel){return new LiveBus.Event(1,UUID.randomUUID().toString(),Instant.now().toString(),type,null,null,0,tenant,null,"","{}",sid==null?null:sid.toString(),channel);}
	static final class Fixture{final UUID sid=UUID.randomUUID();final DesktopServerSession session=new DesktopServerSession();final Client client=new Client();final LiveEventDispatcher dispatcher=new LiveEventDispatcher();final AtomicLong generation=new AtomicLong(1);final AtomicInteger policyRefreshes=new AtomicInteger();final SessionPolicyInvalidationCoordinator coordinator;Fixture(){this(Runnable::run);}Fixture(Executor executor){session.install(new DesktopServerSession.Credential("token",sid,UUID.randomUUID(),Instant.now().plusSeconds(60)));coordinator=new SessionPolicyInvalidationCoordinator(dispatcher,client,session,7,1,generation::get,executor,policyRefreshes::incrementAndGet);}void sessionEvent(int tenant,UUID sessionId){dispatcher.dispatch(event("SESSION_INVALIDATED",tenant,sessionId,null));}void close(){coordinator.close();}}
	static final class Client extends DesktopSessionEnrollmentClient{Validation result=Validation.UNKNOWN;int validations;Client(){super("https://example.test");}@Override public Validation validate(String token){validations++;return result;}}
	static final class QueueExecutor implements Executor{final List<Runnable> tasks=new ArrayList<>();public void execute(Runnable r){tasks.add(r);}void run(){tasks.remove(0).run();}}
}
