package com.shale.data.security.ai;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

/** Read-only independent control-plane identity verification. No endpoint, pool composition or grants.
 * Future registry pool activation/rotation must run this as well as ordinary Case readiness.
 * Providers must independently enforce certificate-validated TLS. No credentials or data are returned. */
public final class AiRegistryIdentityAttestation {
    private final DataSource publisher,tenant,catalog;
    private final String database,verification;
    public AiRegistryIdentityAttestation(DataSource publisher,DataSource tenant,DataSource catalog,String database) {
        this.publisher=Objects.requireNonNull(publisher);this.tenant=Objects.requireNonNull(tenant);
        this.catalog=Objects.requireNonNull(catalog);this.database=Objects.requireNonNull(database);
        try(var stream=getClass().getResourceAsStream("/ai/registry-confinement-v1-catalog.sql")) {
            if(stream==null)throw new IllegalStateException();verification=new String(stream.readAllBytes(),StandardCharsets.UTF_8);
        }catch(Exception missing){throw refused();}
    }
    public void verify() {
        try(Connection review=catalog.getConnection();Connection pub=publisher.getConnection();Connection member=tenant.getConnection()) {
            var platform=AiCaseDataAttestation.platform(review);
            if(AiCaseDataAttestation.platform(pub)!=platform||AiCaseDataAttestation.platform(member)!=platform)throw refused();
            Identity reviewer=identity(review),p=identity(pub),t=identity(member);
            if(!database.equals(reviewer.database)||!database.equals(p.database)||!database.equals(t.database)
                    ||!reviewer.server.equals(p.server)||!reviewer.server.equals(t.server)||reviewer.id<=4
                    ||Arrays.equals(p.sid,t.sid)||Arrays.equals(p.sid,reviewer.sid)||Arrays.equals(t.sid,reviewer.sid))throw refused();
            if(count(review,"SELECT HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','VIEW DEFINITION')")!=1)throw refused();
            // Presence is mandatory here. A legacy-only Case deployment is not registry readiness.
            if(count(review,"SELECT COUNT(*) FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID('dbo.AiCaseDataInvocation') AND name='Shale.RegistryConfinementV1.Required'")!=1)throw refused();
            try(var s=statement(review);var r=s.executeQuery("SELECT name FROM sys.user_token")) {
                var tokens=new HashSet<String>();while(r.next())tokens.add(r.getString(1));
                if(!tokens.equals(Set.of(reviewer.user,"public","AiCaseDataCatalogVerifier")))throw refused();
            }
            execute(review,verification);
            verifyIdentity(pub,review,p,"AiApplicationPublisher",Set.of("AiPublishApplicationV1"),platform);
            verifyIdentity(member,review,t,"AiApplicationTenant",Set.of("AiRequestApplicationV1","AiDecideApplicationV1"),platform);
        }catch(Exception failure){throw refused();}
    }
    private void verifyIdentity(Connection caller,Connection review,Identity identity,String role,Set<String> roots,AiCaseDataAttestation.Platform platform)throws SQLException {
        var names=new HashSet<String>();
        try(var s=statement(caller);var rows=s.executeQuery("SELECT name,type FROM sys.user_token")) {
            while(rows.next()){String name=rows.getString(1);names.add(name);if(name.equals(identity.user)&&!rows.getString(2).equals("SQL USER"))throw refused();}
        }
        if(!names.equals(Set.of(identity.user,"public",role))||identity.id<=4)throw refused();
        try(var check=review.prepareStatement("SELECT sid,type,authentication_type FROM sys.database_principals WHERE principal_id=? AND name=?")) {
            check.setQueryTimeout(3);check.setInt(1,identity.id);check.setString(2,identity.user);
            try(var r=check.executeQuery()){if(!r.next()||!Arrays.equals(identity.sid,r.getBytes(1))||!"S".equals(r.getString(2))
                    ||r.getInt(3)!=(platform==AiCaseDataAttestation.Platform.AZURE_SQL_DATABASE?2:1)||r.next())throw refused();}
        }
        if(platform==AiCaseDataAttestation.Platform.AZURE_SQL_DATABASE) {
            if(!identity.user.equals(identity.login)||count(caller,"SELECT COUNT(*) FROM sys.dm_exec_sessions WHERE session_id=@@SPID AND authenticating_database_id=DB_ID() AND original_security_id=(SELECT sid FROM sys.database_principals WHERE principal_id=USER_ID())")!=1)throw refused();
        } else {
            try(var s=statement(caller);var rows=s.executeQuery("SELECT name FROM sys.login_token")) {
                while(rows.next())if(!Set.of(identity.login,"public").contains(rows.getString(1)))throw refused();
            }
            permissions(caller,null,"SERVER",Set.of("CONNECT SQL","VIEW ANY DATABASE"));
            try(var s=statement(review);var dbs=s.executeQuery("SELECT name FROM sys.databases WHERE database_id>4 AND name<>DB_NAME()")) {
                while(dbs.next())try(var p=caller.prepareStatement("SELECT HAS_DBACCESS(?)")) {
                    p.setString(1,dbs.getString(1));p.setQueryTimeout(3);
                    try(var r=p.executeQuery()){if(!r.next()||r.getInt(1)!=0||r.wasNull())throw refused();}
                }
            }
        }
        permissions(caller,null,"DATABASE",Set.of("CONNECT","VIEW ANY COLUMN ENCRYPTION KEY DEFINITION","VIEW ANY COLUMN MASTER KEY DEFINITION"));
        var executable=new HashSet<String>();
        try(var s=statement(review);var objects=s.executeQuery("SELECT s.name,o.name FROM sys.objects o JOIN sys.schemas s ON s.schema_id=o.schema_id WHERE o.is_ms_shipped=0 AND o.type IN('U','V','P','FN','IF','TF','SN','FS','FT','PC')")) {
            while(objects.next()) {
                String schema=objects.getString(1),name=objects.getString(2),object=quote(schema)+"."+quote(name);
                Set<String> allow=schema.equals("dbo")&&roots.contains(name)?Set.of("EXECUTE"):Set.of();
                if(permissions(caller,object,"OBJECT",allow).contains("EXECUTE"))executable.add(name);
                // Use the independent catalog, never the caller's permission-filtered column inventory.
                try(var p=review.prepareStatement("SELECT c.name FROM sys.columns c WHERE c.object_id=OBJECT_ID(?)")) {
                    p.setString(1,object);p.setQueryTimeout(3);
                    try(var columns=p.executeQuery()){while(columns.next())try(var check=caller.prepareStatement("SELECT HAS_PERMS_BY_NAME(?,'OBJECT','SELECT',?,'COLUMN'),HAS_PERMS_BY_NAME(?,'OBJECT','UPDATE',?,'COLUMN')")) {
                        check.setQueryTimeout(3);check.setString(1,object);check.setString(2,columns.getString(1));check.setString(3,object);check.setString(4,columns.getString(1));
                        try(var r=check.executeQuery()){if(!r.next()||r.getInt(1)==1||r.getInt(2)==1)throw refused();}
                    }}
                }
            }
        }
        if(!executable.equals(roots))throw refused();
    }
    private record Identity(byte[] sid,String database,String user,int id,String login,String server) {}
    private static Identity identity(Connection c)throws SQLException {
        try(var s=statement(c);var r=s.executeQuery("SELECT sid,DB_NAME(),USER_NAME(),USER_ID(),ORIGINAL_LOGIN(),CONVERT(nvarchar(128),SERVERPROPERTY('ServerName')) FROM sys.database_principals WHERE principal_id=USER_ID()")) {
            if(!r.next())throw refused();var id=new Identity(r.getBytes(1),r.getString(2),r.getString(3),r.getInt(4),r.getString(5),r.getString(6));
            if(id.sid==null||id.sid.length==0||id.server==null||r.next())throw refused();return id;
        }
    }
    private static Set<String> permissions(Connection c,String object,String scope,Set<String> allow)throws SQLException {
        var found=new HashSet<String>();
        try(var p=c.prepareStatement("SELECT permission_name FROM sys.fn_my_permissions(?,?)")) {
            p.setString(1,object);p.setString(2,scope);p.setQueryTimeout(3);
            try(var r=p.executeQuery()){while(r.next()){String value=r.getString(1);if(!allow.contains(value))throw refused();found.add(value);}}
        }catch(SQLException denied){if(denied.getErrorCode()!=15151||!allow.isEmpty())throw denied;}
        return found;
    }
    private static void execute(Connection c,String sql)throws SQLException {
        try(var s=statement(c)){boolean more=s.execute(sql);while(true){if(more)try(var r=s.getResultSet()){while(r.next()){} }else if(s.getUpdateCount()==-1)break;more=s.getMoreResults(Statement.CLOSE_CURRENT_RESULT);}}
    }
    private static int count(Connection c,String sql)throws SQLException {try(var s=statement(c);var r=s.executeQuery(sql)){if(!r.next())throw refused();return r.getInt(1);}}
    private static Statement statement(Connection c)throws SQLException {var s=c.createStatement();s.setQueryTimeout(3);return s;}
    private static String quote(String s){return "["+s.replace("]","]]")+"]";}
    private static IllegalStateException refused(){return new IllegalStateException("Registry SQL identity attestation failed closed.");}
}
