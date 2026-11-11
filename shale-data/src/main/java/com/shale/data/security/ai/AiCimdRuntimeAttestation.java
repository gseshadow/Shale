package com.shale.data.security.ai;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.ZoneId;
import java.util.*;

/** Independent complete catalog plus actual-session authority. No provisioning or receipt refresh. */
public final class AiCimdRuntimeAttestation {
    private final Map<AiCimdIdentity,DataSource> sources;
    private final Map<AiCimdIdentity,String> users;
    private final List<DataSource> forbidden;
    private final String database,issuer,resource;
    private final AiCaseDataAttestation.Contract contract;
    private final String catalogHash;

    public AiCimdRuntimeAttestation(Map<AiCimdIdentity,DataSource> sources, Map<AiCimdIdentity,String> users,
            List<DataSource> forbidden, String database, String issuer, String resource, AiCaseDataAttestation.Contract contract) {
        this.sources=Map.copyOf(sources); this.users=Map.copyOf(users); this.forbidden=List.copyOf(forbidden);
        this.database=Objects.requireNonNull(database); this.issuer=Objects.requireNonNull(issuer);
        this.resource=Objects.requireNonNull(resource); this.contract=Objects.requireNonNull(contract);
        if(!sources.keySet().equals(EnumSet.allOf(AiCimdIdentity.class)) || !users.keySet().equals(sources.keySet()) || forbidden.size()!=4) throw refused("CONFIGURATION");
        try(var stream=getClass().getResourceAsStream("/ai/cimd-runtime-v1-catalog.sha256")) {
            if(stream==null) throw refused("SOURCE"); catalogHash=new String(stream.readAllBytes(),StandardCharsets.US_ASCII).strip();
            if(!catalogHash.matches("[a-f0-9]{64}")) throw refused("SOURCE");
        } catch(Exception failure) { throw refused("SOURCE"); }
    }
    public void verify() {
        String stage="IDENTITY";
        var opened=new ArrayList<Connection>();
        try {
            var sessions=new EnumMap<AiCimdIdentity,Connection>(AiCimdIdentity.class);
            var identities=new EnumMap<AiCimdIdentity,Identity>(AiCimdIdentity.class);
            for(var role:AiCimdIdentity.values()) {
                Connection c=sources.get(role).getConnection(); opened.add(c); fresh(c);
                sessions.put(role,c); identities.put(role,identity(c));
            }
            Connection review=sessions.get(AiCimdIdentity.CATALOG);
            var catalog=identities.get(AiCimdIdentity.CATALOG);
            var platform=AiCaseDataAttestation.platform(review);
            require(review,"SELECT HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','VIEW DEFINITION')",1);
            if(platform==AiCaseDataAttestation.Platform.SQL_SERVER_OR_MI) {
                require(review,"SELECT HAS_PERMS_BY_NAME(NULL,NULL,'VIEW ANY DEFINITION')",1);
                require(review,"SELECT HAS_PERMS_BY_NAME(NULL,NULL,'VIEW ANY DATABASE')",1);
            }
            var seen=new HashSet<String>();
            for(var role:AiCimdIdentity.values()) {
                var id=identities.get(role); Connection c=sessions.get(role);
                if(!database.equals(id.database) || !catalog.server.equals(id.server) || AiCaseDataAttestation.platform(c)!=platform
                        || !users.get(role).equals(id.user) || !seen.add(HexFormat.of().formatHex(id.sid))) throw refused(stage);
                verifyIdentity(c,review,id,role,platform);
            }
            for(DataSource source:forbidden) try(Connection c=source.getConnection()) {
                var id=identity(c);
                if(AiCaseDataAttestation.platform(c)!=platform || !catalog.server.equals(id.server)
                        || !database.equals(id.database) || seen.contains(HexFormat.of().formatHex(id.sid))) throw refused(stage);
            }
            stage="PUBLIC"; AiCaseDataAttestation.publicAuthority(review);
            stage="CONTRACT";
            require(review,"SELECT COUNT(*) FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID('dbo.AiCaseDataInvocation') AND name='Shale.AiCaseData"+contract+".Source'",1);
            if(contract==AiCaseDataAttestation.Contract.V1) require(review,"SELECT COUNT(*) FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID('dbo.AiCaseDataInvocation') AND name IN('Shale.AiCaseDataV2','Shale.AiCaseDataV2.Source')",0);
            stage="SOURCE";
            // Hash and context are checked through independent complete metadata BEFORE entering owner context.
            try(var s=statement(review);var r=s.executeQuery("SELECT m.definition,m.execute_as_principal_id,m.uses_ansi_nulls,m.uses_quoted_identifier,COALESCE(o.principal_id,s.principal_id) FROM sys.sql_modules m JOIN sys.objects o ON o.object_id=m.object_id JOIN sys.schemas s ON s.schema_id=o.schema_id WHERE s.name='dbo' AND o.name='AiCimdAttestCatalogV1' AND o.type='P'")) {
                if(!r.next() || !catalogHash.equals(HexFormat.of().formatHex(AiSql.hash(r.getString(1).getBytes(StandardCharsets.UTF_16LE))))
                        || r.getInt(2)!=-2 || r.getInt(3)!=1 || r.getInt(4)!=1 || r.getInt(5)!=1 || r.next()) throw refused(stage);
            }
            require(review,"SELECT COUNT(*) FROM sys.crypt_properties WHERE major_id=OBJECT_ID('dbo.AiCimdAttestCatalogV1')",0);
            require(review,"SELECT COUNT(*) FROM sys.database_permissions WHERE class=1 AND major_id=OBJECT_ID('dbo.AiCimdAttestCatalogV1') AND NOT(minor_id=0 AND grantee_principal_id=DATABASE_PRINCIPAL_ID('AiCimdCatalogV1') AND grantor_principal_id=1 AND permission_name='EXECUTE' AND state='G')",0);
            stage="RECEIPTS_GRAPH_BINDING";
            String sqlZone=null;
            try(var s=statement(review)) {
                boolean more=s.execute("EXEC dbo.AiCimdAttestCatalogV1"); int bindings=0;
                while(true) {
                    if(more) try(var r=s.getResultSet()) {
                        // Unchanged predecessors emit bounded finding/count/receipt reports.
                        // Consume them privately; the fixed gate emits the unique binding last.
                        var columns=new ArrayList<String>();
                        for(int n=1;n<=r.getMetaData().getColumnCount();n++) columns.add(r.getMetaData().getColumnLabel(n));
                        if(!columns.equals(List.of("Issuer","Resource","Contract","JavaTimeZone","SqlTimeZone"))) {
                            if(bindings!=0) throw refused(stage);
                            while(r.next()) { }
                        } else {
                        if(++bindings!=1 || !r.next() || !issuer.equals(r.getString("Issuer")) || !resource.equals(r.getString("Resource"))
                                || !"case-read-v1".equals(r.getString("Contract"))
                                || !ZoneId.systemDefault().equals(ZoneId.of(r.getString("JavaTimeZone"))) || r.getString("SqlTimeZone")==null) throw refused(stage);
                        sqlZone=r.getString("SqlTimeZone");
                        if(r.next()) throw refused(stage);
                        }
                    } else if(s.getUpdateCount()==-1) break;
                    more=s.getMoreResults(Statement.CLOSE_CURRENT_RESULT);
                }
                if(bindings!=1) throw refused(stage);
            }
            stage="TIME_ZONE";
            // Preserve the established SQL/Java wall-time compatibility checks without
            // adding a direct SELECT grant on the immutable deployment table.
            for(int year=2025;year<=2028;year++) for(int month:List.of(1,3,4,7,10,11,12)) {
                var sample=java.time.LocalDate.of(year,month,15).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
                try(var p=review.prepareStatement("SELECT CONVERT(datetime2(7),(CONVERT(datetime2(7),?) AT TIME ZONE 'UTC') AT TIME ZONE ?)")) {
                    p.setQueryTimeout(5); com.shale.data.util.UtcJdbcTimestamp.set(p,1,sample); p.setString(2,sqlZone);
                    try(var r=p.executeQuery()) { if(!r.next() || !r.getTimestamp(1).toLocalDateTime().equals(java.time.LocalDateTime.ofInstant(sample,ZoneId.systemDefault())) || r.next()) throw refused(stage); }
                }
            }
            // Drain and close every session before readiness can escape.
            for(Connection c:opened) c.close(); opened.clear();
        } catch(Exception failure) { throw refused(stage); }
        finally { for(Connection c:opened) try { c.close(); } catch(SQLException ignored) { /* already refused */ } }
    }
    record Identity(byte[] sid,String database,String user,int id,String login,String server) {}
    static Identity identity(Connection c)throws SQLException {
        try(var s=statement(c);var r=s.executeQuery("SELECT sid,DB_NAME(),USER_NAME(),USER_ID(),ORIGINAL_LOGIN(),CONVERT(nvarchar(128),SERVERPROPERTY('ServerName')) FROM sys.database_principals WHERE principal_id=USER_ID()")) {
            if(!r.next()) throw refused("IDENTITY");
            var id=new Identity(r.getBytes(1),r.getString(2),r.getString(3),r.getInt(4),r.getString(5),r.getString(6));
            if(id.sid==null || id.sid.length==0 || id.server==null || id.id<=4 || r.next()) throw refused("IDENTITY");
            return id;
        }
    }
    /** Package-visible for real SQL tests of the authority boundary independently of private receipts. */
    static void verifyIdentity(Connection c,Connection review,Identity id,AiCimdIdentity role,AiCaseDataAttestation.Platform platform)throws SQLException {
        var names=new HashSet<String>();
        try(var s=statement(c);var r=s.executeQuery("SELECT name,type FROM sys.user_token")) {
            while(r.next()) { names.add(r.getString(1)); if(id.user.equals(r.getString(1)) && !"SQL USER".equals(r.getString(2))) throw refused("TOKENS"); }
        }
        if(!names.equals(Set.of(id.user,"public",role.role()))) throw refused("TOKENS");
        try(var p=review.prepareStatement("SELECT sid,type,authentication_type FROM sys.database_principals WHERE principal_id=? AND name=?")) {
            p.setInt(1,id.id); p.setString(2,id.user); p.setQueryTimeout(5);
            try(var r=p.executeQuery()) {
                if(!r.next() || !Arrays.equals(id.sid,r.getBytes(1)) || !"S".equals(r.getString(2)) || r.getInt(3)!=(platform==AiCaseDataAttestation.Platform.AZURE_SQL_DATABASE?2:1) || r.next()) throw refused("AUTHENTICATION");
            }
        }
        if(platform==AiCaseDataAttestation.Platform.AZURE_SQL_DATABASE) {
            if(!id.user.equals(id.login)) throw refused("AUTHENTICATION");
            require(c,"SELECT COUNT(*) FROM sys.dm_exec_sessions WHERE session_id=@@SPID AND authenticating_database_id=DB_ID() AND original_security_id=(SELECT sid FROM sys.database_principals WHERE principal_id=USER_ID())",1);
        } else {
            var logins=new HashSet<String>();
            try(var s=statement(c);var r=s.executeQuery("SELECT name FROM sys.login_token")) { while(r.next()) logins.add(r.getString(1)); }
            if(!logins.equals(Set.of(id.login,"public"))) throw refused("SERVER_TOKEN");
            require(c,"SELECT CASE WHEN SUSER_SID()=(SELECT sid FROM sys.database_principals WHERE principal_id=USER_ID()) AND SUSER_SNAME()=ORIGINAL_LOGIN() THEN 1 ELSE 0 END",1);
            permissions(c,null,"SERVER",role==AiCimdIdentity.CATALOG?Set.of("CONNECT SQL","VIEW ANY DATABASE","VIEW ANY DEFINITION","VIEW ANY SECURITY DEFINITION","VIEW ANY PERFORMANCE DEFINITION"):Set.of("CONNECT SQL","VIEW ANY DATABASE"));
            try(var p=review.prepareStatement("SELECT COUNT(*) FROM sys.server_permissions p JOIN sys.server_principals s ON s.principal_id=p.grantee_principal_id LEFT JOIN sys.endpoints e ON p.class=105 AND p.major_id=e.endpoint_id WHERE (s.sid=? OR s.name='public') AND p.state IN('G','W') AND NOT(p.class=100 AND p.state='G' AND (p.permission_name IN('CONNECT SQL','VIEW ANY DATABASE') OR ?=1 AND s.sid=? AND p.permission_name='VIEW ANY DEFINITION') OR s.name='public' AND p.class=105 AND p.state='G' AND p.permission_name='CONNECT' AND e.type=2 AND e.is_admin_endpoint=0 AND e.name IN('TSQL Local Machine','TSQL Named Pipes','TSQL Default TCP','TSQL Default VIA'))")) {
                p.setQueryTimeout(5); p.setBytes(1,id.sid); p.setInt(2,role==AiCimdIdentity.CATALOG?1:0); p.setBytes(3,id.sid);
                try(var r=p.executeQuery()) { if(!r.next() || r.getInt(1)!=0) throw refused("SERVER_GRANTS"); }
            }
            try(var s=statement(review);var dbs=s.executeQuery("SELECT name FROM sys.databases WHERE database_id>4 AND name<>DB_NAME()")) {
                while(dbs.next()) try(var p=c.prepareStatement("SELECT HAS_DBACCESS(?)")) {
                    p.setString(1,dbs.getString(1)); p.setQueryTimeout(5);
                    try(var r=p.executeQuery()) { if(!r.next() || r.getInt(1)!=0 || r.wasNull()) throw refused("CROSS_DATABASE"); }
                }
            }
        }
        Set<String> dbAllowed=new HashSet<>(Set.of("CONNECT","VIEW ANY COLUMN ENCRYPTION KEY DEFINITION","VIEW ANY COLUMN MASTER KEY DEFINITION"));
        if(role==AiCimdIdentity.CATALOG) dbAllowed.addAll(Set.of("VIEW DEFINITION","VIEW SECURITY DEFINITION","VIEW PERFORMANCE DEFINITION"));
        permissions(c,null,"DATABASE",dbAllowed);
        // A complete token excludes inherited/nested roles; check every explicit grant class,
        // ownership route and grant option, including grants not summarized by fn_my_permissions.
        String tokenIds=id.id+",DATABASE_PRINCIPAL_ID('"+role.role()+"'),DATABASE_PRINCIPAL_ID('public')";
        for(String sql:List.of(
                "SELECT COUNT(*) FROM sys.database_permissions WHERE grantee_principal_id="+id.id+" AND NOT(class=0 AND state='G' AND permission_name='CONNECT')",
                "SELECT COUNT(*) FROM sys.schemas WHERE principal_id IN("+tokenIds+")",
                "SELECT COUNT(*) FROM sys.objects WHERE principal_id IN("+tokenIds+")",
                "SELECT COUNT(*) FROM sys.database_principals WHERE owning_principal_id IN("+tokenIds+")",
                "SELECT COUNT(*) FROM sys.types WHERE is_user_defined=1 AND principal_id IN("+tokenIds+")",
                "SELECT COUNT(*) FROM sys.certificates WHERE principal_id IN("+tokenIds+")",
                "SELECT COUNT(*) FROM sys.asymmetric_keys WHERE principal_id IN("+tokenIds+")",
                "SELECT COUNT(*) FROM sys.assemblies WHERE principal_id IN("+tokenIds+")",
                "SELECT COUNT(*) FROM sys.database_permissions WHERE grantee_principal_id IN("+tokenIds+") AND state='W'")) require(review,sql,0);
        require(review,"SELECT COUNT(*) FROM sys.database_role_members WHERE role_principal_id=DATABASE_PRINCIPAL_ID('"+role.role()+"')",1);
        if(role==AiCimdIdentity.CATALOG) require(review,"SELECT COUNT(*) FROM sys.database_permissions WHERE grantee_principal_id=DATABASE_PRINCIPAL_ID('AiCimdCatalogV1') AND NOT(state='G' AND grantor_principal_id=1 AND (class=0 AND permission_name='VIEW DEFINITION' OR class=1 AND minor_id=0 AND major_id=OBJECT_ID('dbo.AiCimdAttestCatalogV1') AND permission_name='EXECUTE'))",0);
        String roots=role.roots().stream().map(AiCimdRuntimeAttestation::literal).collect(java.util.stream.Collectors.joining(","));
        require(review,"SELECT COUNT(*) FROM sys.database_permissions WHERE grantee_principal_id=DATABASE_PRINCIPAL_ID('"+role.role()+"') AND state IN('G','W') AND NOT(state='G' AND grantor_principal_id=1 AND (class=1 AND minor_id=0 AND permission_name='EXECUTE' AND OBJECT_SCHEMA_NAME(major_id)='dbo' AND OBJECT_NAME(major_id) IN("+roots+")"+(role==AiCimdIdentity.CATALOG?" OR class=0 AND permission_name='VIEW DEFINITION'":"")+"))",0);
        var executable=new HashSet<String>();
        try(var s=statement(review);var objects=s.executeQuery("SELECT s.name,o.name,o.object_id FROM sys.objects o JOIN sys.schemas s ON s.schema_id=o.schema_id WHERE o.is_ms_shipped=0 AND o.type IN('U','V','P','FN','IF','TF','SN','FS','FT','PC','X','AF','SO','SQ')")) {
            while(objects.next()) {
                String object=quote(objects.getString(1))+"."+quote(objects.getString(2));
                var allow=new HashSet<String>();
                if(role==AiCimdIdentity.CATALOG) allow.addAll(Set.of("VIEW DEFINITION","VIEW SECURITY DEFINITION","VIEW PERFORMANCE DEFINITION"));
                if("dbo".equals(objects.getString(1)) && role.roots().contains(objects.getString(2))) allow.add("EXECUTE");
                if(permissions(c,object,"OBJECT",allow).contains("EXECUTE")) executable.add(objects.getString(2));
                // Inventory is from the full reviewer, never the caller's filtered sys.columns.
                try(var p=review.prepareStatement("SELECT name FROM sys.columns WHERE object_id=?")) {
                    p.setInt(1,objects.getInt(3)); p.setQueryTimeout(5);
                    try(var columns=p.executeQuery()) {
                        var clauses=new ArrayList<String>();
                        while(columns.next()) for(String permission:List.of("SELECT","UPDATE","REFERENCES"))
                            clauses.add("HAS_PERMS_BY_NAME("+literal(object)+",'OBJECT','"+permission+"',"+literal(columns.getString(1))+",'COLUMN')=1");
                        if(!clauses.isEmpty()) require(c,"SELECT CASE WHEN "+String.join(" OR ",clauses)+" THEN 1 ELSE 0 END",0);
                    }
                }
            }
        }
        if(!executable.equals(role.roots())) throw refused("ROOTS");
    }
    private static Set<String> permissions(Connection c,String object,String scope,Set<String> allow)throws SQLException {
        var actual=new HashSet<String>();
        try(var p=c.prepareStatement("SELECT permission_name FROM sys.fn_my_permissions(?,?)")) {
            p.setString(1,object); p.setString(2,scope); p.setQueryTimeout(5);
            try(var r=p.executeQuery()) { while(r.next()) { String permission=r.getString(1); if(!allow.contains(permission)) throw refused("EFFECTIVE_PERMISSIONS"); actual.add(permission); } }
        } catch(SQLException invisible) { if(invisible.getErrorCode()!=15151 || !allow.isEmpty()) throw invisible; }
        return actual;
    }
    private static void fresh(Connection c)throws SQLException {
        if(!c.getAutoCommit()) throw refused("LEASE");
        require(c,"SELECT CASE WHEN @@TRANCOUNT=0 AND (@@OPTIONS & 2)=0 AND SESSION_CONTEXT(N'ShaleClientId') IS NULL AND SESSION_CONTEXT(N'PrincipalUserId') IS NULL THEN 1 ELSE 0 END",1);
    }
    static void require(Connection c,String sql,int expected)throws SQLException {
        try(var s=statement(c);var r=s.executeQuery(sql)) { if(!r.next() || r.getInt(1)!=expected || r.wasNull() || r.next()) throw refused("CATALOG"); }
    }
    static Statement statement(Connection c)throws SQLException { var s=c.createStatement(); s.setQueryTimeout(5); return s; }
    private static String quote(String s) { return "["+s.replace("]","]]")+"]"; }
    private static String literal(String s) { return "N'"+s.replace("'","''")+"'"; }
    private static IllegalStateException refused(String stage) { return new IllegalStateException("CIMD SQL attestation failed closed: "+stage); }
}
