package com.shale.data.security.ai;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.function.LongFunction;

/** Explicit dormant composition; owns no pools and registers no HTTP routes.
 * Complete predecessor/additive verification runs at startup and admission, never per lease.
 * Callers supply deadline-bound sources and the unchanged predecessor attestation.
 * All providers are trusted composition dependencies, never request-selected identities. */
public final class AiCimdCaseData implements AutoCloseable {
    private final LongFunction<AiCaseDataAttestation> predecessor;
    private final LongFunction<DataSource> data, catalog;
    private final String database, issuer, resource, verification;
    private boolean closed;

    public AiCimdCaseData(LongFunction<AiCaseDataAttestation> predecessor,
                          LongFunction<DataSource> data, LongFunction<DataSource> catalog,
                          String database, String issuer, String resource) {
        this.predecessor=Objects.requireNonNull(predecessor);
        this.data=Objects.requireNonNull(data); this.catalog=Objects.requireNonNull(catalog);
        this.database=Objects.requireNonNull(database);
        this.issuer=issuer; this.resource=resource;
        AiIntegrationSecurityService.configuredHttps(issuer);
        AiIntegrationSecurityService.configuredHttps(resource);
        try(var stream=getClass().getResourceAsStream("/ai/cimd-authorization-v1-catalog.sql")) {
            if(stream==null)throw unavailable();
            verification=new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch(Exception failure) { throw unavailable(); }
        ready(System.nanoTime()+10_000_000_000L);
    }

    /** Unchanged predecessor verification followed by the complete additive checks. */
    public synchronized void ready(long deadline) {
        if(closed)throw unavailable();
        String stage="PREDECESSOR";
        try {
            checkDeadline(deadline);
            predecessor.apply(deadline).verify();
            checkDeadline(deadline);
            stage="IDENTITY";
            try(Connection review=catalog.apply(deadline).getConnection();
                Connection caller=data.apply(deadline).getConnection()) {
                identity(caller,"AiCimdCaseRuntimeV1",deadline);
                identity(review,"AiCimdAuthorizationCatalogV1",deadline);
                if(AiCaseDataAttestation.platform(caller)!=AiCaseDataAttestation.platform(review))throw unavailable();
                if(!server(caller).equals(server(review)))throw unavailable();
                stage="REVIEWER";
                reviewerPermissions(review,deadline);
                byte[] callerSid=sid(caller),reviewSid=sid(review);
                if(Arrays.equals(callerSid,reviewSid))throw unavailable();
                stage="CATALOG";
                // Catalog checks include exact new role/member permissions and source/receipt/RLS
                // facts. There is no owner-context gate, signing certificate or receipt refresh.
                try(var statement=review.createStatement()) {
                    statement.setQueryTimeout(seconds(deadline));
                    boolean result=statement.execute(verification);
                    while(true) {
                        if(result)try(var rows=statement.getResultSet()){while(rows.next())checkDeadline(deadline);}
                        else if(statement.getUpdateCount()==-1)break;
                        result=statement.getMoreResults(Statement.CLOSE_CURRENT_RESULT);
                    }
                }
            }
            checkDeadline(deadline);
        } catch(Exception failure) {
            closed=true;
            int code=failure instanceof SQLException sql?sql.getErrorCode():0;
            throw new IllegalStateException("CIMD verification failed closed; stage="+stage+"; sqlCode="+code);
        }
    }

    public AiCaseDataRequest authenticate(String token,long deadline) {
        ready(deadline);
        return AiCaseDataAccess.authenticate(data.apply(deadline),token,issuer,resource,deadline,
                AiCaseDataAccess.Namespace.CIMD_V1);
    }

    public IntegrationCaseReader reader(AiCaseDataRequest request,long deadline) {
        synchronized(this) { if(closed)throw unavailable(); }
        if(request.namespace()!=AiCaseDataAccess.Namespace.CIMD_V1)throw unavailable();
        return new AiCaseDataAccess(data.apply(deadline),data,request);
    }

    private void identity(Connection c,String role,long deadline)throws SQLException {
        c.setNetworkTimeout(Runnable::run,millis(deadline));
        try(var s=c.createStatement()) {
            s.setQueryTimeout(seconds(deadline));
            try(var r=s.executeQuery("SELECT DB_NAME(),USER_NAME(),USER_ID(),@@TRANCOUNT,@@OPTIONS,SESSION_CONTEXT(N'ShaleClientId'),SESSION_CONTEXT(N'PrincipalUserId')")) {
                if(!r.next()||!database.equals(r.getString(1))||r.getInt(3)<=4||r.getInt(4)!=0||(r.getInt(5)&2)!=0||r.getObject(6)!=null||r.getObject(7)!=null)throw unavailable();
                String user=r.getString(2);
                try(var tokens=c.createStatement();var rows=tokens.executeQuery("SELECT name FROM sys.user_token")) {
                    var names=new HashSet<String>();while(rows.next())names.add(rows.getString(1));
                    if(!names.equals(Set.of(user,"public",role)))throw unavailable();
                }
            }
        }
    }
    private static void reviewerPermissions(Connection c,long deadline)throws SQLException {
        // No Case/control/credential visibility. The historical reviewer is not granted anything.
        String sql="""
            DECLARE @role int=DATABASE_PRINCIPAL_ID(N'AiCimdAuthorizationCatalogV1');
            IF NOT EXISTS(SELECT 1 FROM sys.database_principals WHERE principal_id=@role AND type='R' AND owning_principal_id=1)
             OR (SELECT COUNT(*) FROM sys.database_permissions WHERE grantee_principal_id=@role)<>3
             OR EXISTS(SELECT 1 FROM sys.database_permissions WHERE grantee_principal_id=@role AND NOT
               (state='G' AND grantor_principal_id=1 AND
                (class=0 AND permission_name='VIEW DEFINITION' OR class=1 AND minor_id=0 AND
                 (permission_name='SELECT' AND major_id=OBJECT_ID(N'sys.sql_expression_dependencies')
                  OR permission_name='EXECUTE' AND major_id=OBJECT_ID(N'dbo.AiCimdReadAuthorizationReceiptsV1')))))
             OR EXISTS(SELECT 1 FROM sys.database_permissions WHERE grantee_principal_id=USER_ID()
                       AND NOT(class=0 AND permission_name='CONNECT' AND state='G'))
             OR EXISTS(SELECT 1 FROM sys.schemas WHERE principal_id IN(USER_ID(),@role))
             OR EXISTS(SELECT 1 FROM sys.objects WHERE principal_id IN(USER_ID(),@role))
             OR EXISTS(SELECT 1 FROM sys.database_principals WHERE owning_principal_id IN(USER_ID(),@role))
             THROW 59520,'CIMD reviewer authority mismatch.',1;
            """;
        try(var s=c.createStatement()) { s.setQueryTimeout(seconds(deadline));s.execute(sql); }
        if(AiCaseDataAttestation.platform(c)!=AiCaseDataAttestation.Platform.AZURE_SQL_DATABASE) {
            try(var s=c.createStatement();var r=s.executeQuery("SELECT permission_name FROM sys.fn_my_permissions(NULL,'SERVER')")) {
                while(r.next())if(!Set.of("CONNECT SQL","VIEW ANY DATABASE","VIEW ANY DEFINITION","VIEW ANY SECURITY DEFINITION","VIEW ANY PERFORMANCE DEFINITION").contains(r.getString(1)))throw unavailable();
            }
        }
    }
    private static String server(Connection c)throws SQLException {
        try(var s=c.createStatement();var r=s.executeQuery("SELECT CONVERT(nvarchar(128),SERVERPROPERTY('ServerName'))")) {
            if(!r.next()||r.getString(1)==null)throw unavailable();return r.getString(1);
        }
    }
    private static byte[] sid(Connection c)throws SQLException {
        try(var s=c.createStatement();var r=s.executeQuery("SELECT sid FROM sys.database_principals WHERE principal_id=USER_ID()")) {
            if(!r.next())throw unavailable();byte[] sid=r.getBytes(1);
            if(sid==null||sid.length==0||r.next())throw unavailable();return sid;
        }
    }
    private static void checkDeadline(long deadline) { if(deadline-System.nanoTime()<=0||Thread.currentThread().isInterrupted())throw unavailable(); }
    private static int millis(long deadline) { checkDeadline(deadline);return (int)Math.max(1,Math.min(10_000,(deadline-System.nanoTime())/1_000_000)); }
    private static int seconds(long deadline) { return Math.max(1,Math.min(5,millis(deadline)/1000)); }
    @Override public synchronized void close() { closed=true; } // Sources belong to the composition root.
    private static IllegalStateException unavailable() { return new IllegalStateException("CIMD Case composition unavailable; verification required."); }
}
