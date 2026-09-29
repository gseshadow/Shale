package com.shale.data.service.adapter;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import com.shale.core.dto.ApplicationInstanceView;
import com.shale.core.model.ClientType;
import com.shale.core.model.SemanticVersion;
import com.shale.core.service.ApplicationInstanceServicePort;
import com.shale.data.dao.ApplicationInstanceDao;

public final class ApplicationInstanceServiceAdapter implements ApplicationInstanceServicePort {
	private final Gateway gateway;
	private final Clock clock;
	private static final Duration FUTURE_TOLERANCE=Duration.ofMinutes(5);
	public ApplicationInstanceServiceAdapter(ApplicationInstanceDao dao){this(new DaoGateway(dao),Clock.systemUTC());}
	ApplicationInstanceServiceAdapter(Gateway gateway){this(gateway,Clock.systemUTC());}
	ApplicationInstanceServiceAdapter(Gateway gateway,Clock clock){this.gateway=Objects.requireNonNull(gateway,"gateway");this.clock=Objects.requireNonNull(clock,"clock");}
	@Override public ApplicationInstanceView enroll(int t,int u,UUID m,ClientType c,SemanticVersion v){return gateway.enroll(t,u,m,c,v);}
	@Override public ApplicationInstanceView end(int t,int u,long id){return gateway.end(t,u,id);}
	@Override public ApplicationInstanceView heartbeat(int t,int u,long id,SemanticVersion v,Instant activity){if(activity!=null&&activity.isAfter(clock.instant().plus(FUTURE_TOLERANCE)))throw new IllegalArgumentException("lastHumanActivityAt exceeds the five-minute clock-skew tolerance.");return gateway.heartbeat(t,u,id,v,activity);}
	interface Gateway{ApplicationInstanceView enroll(int t,int u,UUID m,ClientType c,SemanticVersion v);ApplicationInstanceView end(int t,int u,long id);ApplicationInstanceView heartbeat(int t,int u,long id,SemanticVersion v,Instant activity);}
	private record DaoGateway(ApplicationInstanceDao dao) implements Gateway{DaoGateway{Objects.requireNonNull(dao,"dao");}public ApplicationInstanceView enroll(int t,int u,UUID m,ClientType c,SemanticVersion v){return dao.enroll(t,u,m,c,v);}public ApplicationInstanceView end(int t,int u,long id){return dao.end(t,u,id);}public ApplicationInstanceView heartbeat(int t,int u,long id,SemanticVersion v,Instant a){return dao.heartbeat(t,u,id,v,a);}}
}
