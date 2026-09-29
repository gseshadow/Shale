package com.shale.data.service.adapter;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import com.shale.core.dto.UserSessionView;
import com.shale.core.model.ClientType;
import com.shale.core.service.UserSessionServicePort;
import com.shale.data.dao.UserSessionDao;

public final class UserSessionServiceAdapter implements UserSessionServicePort {
	private static final Set<String> REASONS=Set.of("USER_LOGOUT","ADMIN_REVOKED","SECURITY","CREDENTIAL_ROTATED","EXPIRED_REPLACEMENT");
	private final Gateway gateway; private final Clock clock;
	public UserSessionServiceAdapter(UserSessionDao dao){this(new DaoGateway(dao),Clock.systemUTC());}
	UserSessionServiceAdapter(Gateway gateway,Clock clock){this.gateway=Objects.requireNonNull(gateway);this.clock=Objects.requireNonNull(clock);}
	@Override public UserSessionView create(int t,int u,ClientType type,Long instance,UUID jti,Instant expiry){scope(t,u);Objects.requireNonNull(type);Objects.requireNonNull(jti);future(expiry);if(type!=ClientType.DESKTOP&&instance!=null)throw new IllegalArgumentException("Application instance is only valid for DESKTOP sessions.");return gateway.create(t,u,type,instance,UUID.randomUUID(),jti,expiry);}
	@Override public Optional<UserSessionView> find(int t,int u,UUID id){scope(t,u);return gateway.find(t,u,Objects.requireNonNull(id));}
	@Override public UserSessionView revoke(int t,int u,UUID id,String reason){scope(t,u);if(!REASONS.contains(reason))throw new IllegalArgumentException("Unsupported revocation reason.");return gateway.revoke(t,u,Objects.requireNonNull(id),reason);}
	@Override public UserSessionView rotateAccessCredential(int t,int u,UUID id,UUID expected,UUID replacement,Instant expiry){scope(t,u);future(expiry);if(expected.equals(replacement))throw new IllegalArgumentException("Replacement JTI must be new.");return gateway.rotate(t,u,Objects.requireNonNull(id),expected,replacement,expiry);}
	private void future(Instant value){if(value==null||!value.isAfter(clock.instant()))throw new IllegalArgumentException("Session expiry must be after server time.");}
	private static void scope(int t,int u){if(t<=0||u<=0)throw new SecurityException("An authenticated tenant user is required.");}
	interface Gateway{UserSessionView create(int t,int u,ClientType type,Long instance,UUID session,UUID jti,Instant expiry);Optional<UserSessionView> find(int t,int u,UUID session);UserSessionView revoke(int t,int u,UUID session,String reason);UserSessionView rotate(int t,int u,UUID session,UUID expected,UUID replacement,Instant expiry);}
	private record DaoGateway(UserSessionDao dao) implements Gateway{DaoGateway{Objects.requireNonNull(dao);}public UserSessionView create(int t,int u,ClientType type,Long i,UUID s,UUID j,Instant e){return dao.create(t,u,type,i,s,j,e);}public Optional<UserSessionView> find(int t,int u,UUID s){return dao.find(t,u,s);}public UserSessionView revoke(int t,int u,UUID s,String r){return dao.revoke(t,u,s,r);}public UserSessionView rotate(int t,int u,UUID s,UUID x,UUID n,Instant e){return dao.rotate(t,u,s,x,n,e);}}
}
