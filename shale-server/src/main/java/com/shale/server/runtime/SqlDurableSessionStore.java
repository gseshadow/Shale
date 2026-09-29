package com.shale.server.runtime;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import com.shale.core.dto.UserSessionView;
import com.shale.core.model.ClientType;
import com.shale.data.dao.UserSessionDao;
import com.shale.data.service.adapter.UserSessionServiceAdapter;

/** Opens a tenant-initialized runtime connection for every session operation; it never caches auth state. */
public final class SqlDurableSessionStore implements DurableSessionStore {
    private final RuntimeConnectionProvider connections;
    public SqlDurableSessionStore(RuntimeConnectionProvider connections) { this.connections=java.util.Objects.requireNonNull(connections); }
    private UserSessionServiceAdapter service(ServerPrincipal principal) {
        return new UserSessionServiceAdapter(new UserSessionDao(() -> {
			try { return connections.openConnection(principal); }
			catch (java.sql.SQLException e) { throw new IllegalStateException("Failed to open durable-session connection",e); }
		}));
    }
    @Override public UserSessionView create(ServerPrincipal p,ClientType c,Long i,UUID j,Instant e){return service(p).create(p.shaleClientId(),p.userId(),c,i,j,e);}
    @Override public Optional<UserSessionView> find(ServerPrincipal p,UUID s){return service(p).find(p.shaleClientId(),p.userId(),s);}
    @Override public UserSessionView rotate(ServerPrincipal p,UUID s,UUID x,UUID n,Instant e){return service(p).rotateAccessCredential(p.shaleClientId(),p.userId(),s,x,n,e);}
    @Override public UserSessionView revoke(ServerPrincipal p,UUID s,String r){return service(p).revoke(p.shaleClientId(),p.userId(),s,r);}
}
