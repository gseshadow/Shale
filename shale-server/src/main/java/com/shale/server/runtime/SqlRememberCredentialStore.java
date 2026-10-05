package com.shale.server.runtime;

import java.sql.*;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

/** Uses an auth connection because identity must be derived from the opaque hash before tenant context exists. */
public final class SqlRememberCredentialStore implements RememberCredentialStore {
    private final DataSource dataSource;
    public SqlRememberCredentialStore(DataSource dataSource){this.dataSource=java.util.Objects.requireNonNull(dataSource);}
    @Override public void create(ServerPrincipal p,UUID session,UUID installation,byte[] hash,Instant expires){
        try(Connection c=dataSource.getConnection();PreparedStatement q=c.prepareStatement("INSERT dbo.DesktopRememberCredentials(ShaleClientId,UserId,SessionId,InstallationId,CredentialHash,AbsoluteExpiresAt) VALUES(?,?,?,?,?,?)")){
            q.setInt(1,p.shaleClientId());q.setInt(2,p.userId());q.setObject(3,session);q.setObject(4,installation);q.setBytes(5,hash);q.setTimestamp(6,Timestamp.from(expires));q.executeUpdate();
        }catch(SQLException e){throw new IllegalStateException("Failed to create remembered sign-in",e);}
    }
    @Override public Optional<Record> rotate(byte[] oldHash,UUID installation,byte[] replacement,UUID replacementJti,Instant now){
        try(Connection c=dataSource.getConnection()){
            c.setAutoCommit(false);c.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            try(PreparedStatement q=c.prepareStatement("SELECT r.ShaleClientId,r.UserId,r.SessionId,r.AbsoluteExpiresAt,u.Email,COALESCE(u.is_admin,0),COALESCE(u.is_attorney,0),s.CurrentAccessJti FROM dbo.DesktopRememberCredentials r WITH(UPDLOCK,HOLDLOCK) JOIN dbo.UserSessions s WITH(UPDLOCK,HOLDLOCK) ON s.SessionId=r.SessionId AND s.ShaleClientId=r.ShaleClientId AND s.UserId=r.UserId JOIN dbo.Users u ON u.Id=r.UserId AND u.ShaleClientId=r.ShaleClientId WHERE r.CredentialHash=? AND r.InstallationId=? AND r.ConsumedAt IS NULL AND r.AbsoluteExpiresAt>? AND s.RevokedAt IS NULL AND s.ExpiresAt>? AND COALESCE(u.is_deleted,0)=0 AND COALESCE(u.IsRemoved,0)=0")){
                q.setBytes(1,oldHash);q.setObject(2,installation);q.setTimestamp(3,Timestamp.from(now));q.setTimestamp(4,Timestamp.from(now));
                try(ResultSet rs=q.executeQuery()){
                    if(!rs.next()){c.rollback();return Optional.empty();}
                    int tenant=rs.getInt(1),user=rs.getInt(2);UUID sid=UUID.fromString(rs.getString(3));Instant deadline=rs.getTimestamp(4).toInstant();String email=rs.getString(5);
                    UUID expectedJti=UUID.fromString(rs.getString(8));
                    try(PreparedStatement s=c.prepareStatement("UPDATE dbo.UserSessions SET CurrentAccessJti=?,LastRefreshedAt=?,UpdatedAt=? WHERE SessionId=? AND CurrentAccessJti=? AND RevokedAt IS NULL AND ExpiresAt>?")){s.setObject(1,replacementJti);s.setTimestamp(2,Timestamp.from(now));s.setTimestamp(3,Timestamp.from(now));s.setObject(4,sid);s.setObject(5,expectedJti);s.setTimestamp(6,Timestamp.from(now));if(s.executeUpdate()!=1){c.rollback();return Optional.empty();}}
                    try(PreparedStatement u=c.prepareStatement("UPDATE dbo.DesktopRememberCredentials SET CredentialHash=?,RotatedAt=?,UpdatedAt=? WHERE CredentialHash=? AND InstallationId=? AND ConsumedAt IS NULL")){
                        u.setBytes(1,replacement);u.setTimestamp(2,Timestamp.from(now));u.setTimestamp(3,Timestamp.from(now));u.setBytes(4,oldHash);u.setObject(5,installation);
                        if(u.executeUpdate()!=1){c.rollback();return Optional.empty();}
                    }
                    boolean admin=rs.getBoolean(6),attorney=rs.getBoolean(7);c.commit();return Optional.of(new Record(new ServerPrincipal(user,tenant,email),sid,installation,deadline,admin,attorney));
                }
            }catch(Exception e){c.rollback();throw e;}
        }catch(SQLException e){throw new IllegalStateException("Failed to rotate remembered sign-in",e);}
    }
    @Override public void deleteForSession(ServerPrincipal p,UUID sid){try(Connection c=dataSource.getConnection();PreparedStatement q=c.prepareStatement("DELETE dbo.DesktopRememberCredentials WHERE ShaleClientId=? AND UserId=? AND SessionId=?")){q.setInt(1,p.shaleClientId());q.setInt(2,p.userId());q.setObject(3,sid);q.executeUpdate();}catch(SQLException e){throw new IllegalStateException("Failed to clear remembered sign-in",e);}}
}
