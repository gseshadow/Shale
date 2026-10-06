package com.shale.server.runtime;

import java.sql.*;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

/** Uses the auth connection only to derive identity from the opaque hash, then performs writes under tenant context. */
public final class SqlRememberCredentialStore implements RememberCredentialStore {
    private final DataSource identityLookup;
    private final RuntimeConnectionProvider runtimeConnections;

    public SqlRememberCredentialStore(DataSource identityLookup,RuntimeConnectionProvider runtimeConnections){
        this.identityLookup=Objects.requireNonNull(identityLookup);
        this.runtimeConnections=Objects.requireNonNull(runtimeConnections);
    }

    @Override public void create(ServerPrincipal principal,UUID session,UUID installation,byte[] hash,Instant expires){
        try(Connection c=openRuntime(principal);PreparedStatement q=c.prepareStatement(
                "INSERT dbo.DesktopRememberCredentials(ShaleClientId,UserId,SessionId,InstallationId,CredentialHash,AbsoluteExpiresAt) VALUES(?,?,?,?,?,?)")){
            q.setInt(1,principal.shaleClientId());q.setInt(2,principal.userId());q.setObject(3,session);
            q.setObject(4,installation);q.setBytes(5,hash);q.setTimestamp(6,Timestamp.from(expires));q.executeUpdate();
        }catch(SQLException e){throw new IllegalStateException("Failed to create remembered sign-in",e);}
    }

    @Override public Optional<Record> rotate(byte[] oldHash,UUID installation,byte[] replacement,UUID replacementJti,Instant now){
        Optional<Lookup> candidate=lookup(oldHash,installation,now);
        if(candidate.isEmpty())return Optional.empty();
        Lookup found=candidate.get();
        try(Connection c=openRuntime(found.principal())){
            c.setAutoCommit(false);c.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            try(PreparedStatement q=c.prepareStatement("SELECT r.AbsoluteExpiresAt,u.Email,COALESCE(u.is_admin,0),COALESCE(u.is_attorney,0),s.CurrentAccessJti FROM dbo.DesktopRememberCredentials r WITH(UPDLOCK,HOLDLOCK) JOIN dbo.UserSessions s WITH(UPDLOCK,HOLDLOCK) ON s.SessionId=r.SessionId AND s.ShaleClientId=r.ShaleClientId AND s.UserId=r.UserId JOIN dbo.Users u ON u.Id=r.UserId AND u.ShaleClientId=r.ShaleClientId WHERE r.CredentialHash=? AND r.InstallationId=? AND r.ShaleClientId=? AND r.UserId=? AND r.SessionId=? AND r.ConsumedAt IS NULL AND r.AbsoluteExpiresAt>? AND s.RevokedAt IS NULL AND s.ExpiresAt>? AND COALESCE(u.is_deleted,0)=0 AND COALESCE(u.IsRemoved,0)=0")){
                q.setBytes(1,oldHash);q.setObject(2,installation);q.setInt(3,found.principal().shaleClientId());
                q.setInt(4,found.principal().userId());q.setObject(5,found.sessionId());
                q.setTimestamp(6,Timestamp.from(now));q.setTimestamp(7,Timestamp.from(now));
                try(ResultSet rs=q.executeQuery()){
                    if(!rs.next()){c.rollback();return Optional.empty();}
                    Instant deadline=rs.getTimestamp(1).toInstant();String email=rs.getString(2);
                    UUID expectedJti=UUID.fromString(rs.getString(5));
                    try(PreparedStatement s=c.prepareStatement("UPDATE dbo.UserSessions SET CurrentAccessJti=?,LastRefreshedAt=?,UpdatedAt=? WHERE SessionId=? AND ShaleClientId=? AND UserId=? AND CurrentAccessJti=? AND RevokedAt IS NULL AND ExpiresAt>?")){
                        s.setObject(1,replacementJti);s.setTimestamp(2,Timestamp.from(now));s.setTimestamp(3,Timestamp.from(now));
                        s.setObject(4,found.sessionId());s.setInt(5,found.principal().shaleClientId());s.setInt(6,found.principal().userId());
                        s.setObject(7,expectedJti);s.setTimestamp(8,Timestamp.from(now));
                        if(s.executeUpdate()!=1){c.rollback();return Optional.empty();}
                    }
                    try(PreparedStatement u=c.prepareStatement("UPDATE dbo.DesktopRememberCredentials SET CredentialHash=?,RotatedAt=?,UpdatedAt=? WHERE CredentialHash=? AND InstallationId=? AND ShaleClientId=? AND UserId=? AND SessionId=? AND ConsumedAt IS NULL")){
                        u.setBytes(1,replacement);u.setTimestamp(2,Timestamp.from(now));u.setTimestamp(3,Timestamp.from(now));
                        u.setBytes(4,oldHash);u.setObject(5,installation);u.setInt(6,found.principal().shaleClientId());
                        u.setInt(7,found.principal().userId());u.setObject(8,found.sessionId());
                        if(u.executeUpdate()!=1){c.rollback();return Optional.empty();}
                    }
                    boolean admin=rs.getBoolean(3),attorney=rs.getBoolean(4);c.commit();
                    var principal=new ServerPrincipal(found.principal().userId(),found.principal().shaleClientId(),email);
                    return Optional.of(new Record(principal,found.sessionId(),installation,deadline,admin,attorney));
                }
            }catch(Exception e){c.rollback();throw e;}
        }catch(SQLException e){throw new IllegalStateException("Failed to rotate remembered sign-in",e);}
    }

    @Override public void deleteForSession(ServerPrincipal principal,UUID sid){
        try(Connection c=openRuntime(principal);PreparedStatement q=c.prepareStatement(
                "DELETE dbo.DesktopRememberCredentials WHERE ShaleClientId=? AND UserId=? AND SessionId=?")){
            q.setInt(1,principal.shaleClientId());q.setInt(2,principal.userId());q.setObject(3,sid);q.executeUpdate();
        }catch(SQLException e){throw new IllegalStateException("Failed to clear remembered sign-in",e);}
    }

    private Optional<Lookup> lookup(byte[] hash,UUID installation,Instant now){
        try(Connection c=identityLookup.getConnection();PreparedStatement q=c.prepareStatement(
                "SELECT r.ShaleClientId,r.UserId,r.SessionId,u.Email FROM dbo.DesktopRememberCredentials r JOIN dbo.Users u ON u.Id=r.UserId AND u.ShaleClientId=r.ShaleClientId WHERE r.CredentialHash=? AND r.InstallationId=? AND r.ConsumedAt IS NULL AND r.AbsoluteExpiresAt>? AND COALESCE(u.is_deleted,0)=0 AND COALESCE(u.IsRemoved,0)=0")){
            q.setBytes(1,hash);q.setObject(2,installation);q.setTimestamp(3,Timestamp.from(now));
            try(ResultSet rs=q.executeQuery()){
                if(!rs.next())return Optional.empty();
                var principal=new ServerPrincipal(rs.getInt(2),rs.getInt(1),rs.getString(4));
                return Optional.of(new Lookup(principal,UUID.fromString(rs.getString(3))));
            }
        }catch(SQLException e){throw new IllegalStateException("Failed to locate remembered sign-in",e);}
    }

    private Connection openRuntime(ServerPrincipal principal)throws SQLException{
        return runtimeConnections.openConnection(principal);
    }

    private record Lookup(ServerPrincipal principal,UUID sessionId){}
}
