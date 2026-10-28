package com.shale.data.security.ai;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.*;
import java.util.*;

/** Live read-only catalog/effective-authority attestation using separately governed catalog visibility.
 * Run before readiness, each request and any pool rebuild. No installer, grant or unchecked receipt file. */
public final class AiCaseDataAttestation {
    private static final Set<String> PUBLIC=Set.of("AiDataAuthenticateV1","AiDataSearchCasesV1","AiDataGetCaseOverviewV1","AiDataRecordCaseFailureV1","AiDataFinishCaseQuotaV1","AiDataAuthorizeCaseHandoffV1");
    private final DataSource ai,verifier;
    private final List<DataSource> forbidden;
    private final String database,issuer,resource;
    private final boolean productionInfrastructure;
    private final Properties definitions=new Properties();
    private final String catalog;
    private final Contract contract;
    public enum Contract { V1, V2 }
    public AiCaseDataAttestation(DataSource ai,DataSource verifier,List<DataSource> forbidden,String database,String issuer,String resource) {
        this(ai,verifier,forbidden,database,issuer,resource,false);
    }
    /** Production callers supply ordinary auth, ordinary runtime, then OAuth bootstrap in that order. */
    public AiCaseDataAttestation(DataSource ai,DataSource verifier,List<DataSource> forbidden,String database,String issuer,String resource,boolean productionInfrastructure) {
        this(ai,verifier,forbidden,database,issuer,resource,productionInfrastructure,Contract.V1);
    }
    /** Explicit contract selection. No failed version may fall back to another verifier. */
    public AiCaseDataAttestation(DataSource ai,DataSource verifier,List<DataSource> forbidden,String database,String issuer,String resource,boolean productionInfrastructure,Contract contract) {
        this.contract=Objects.requireNonNull(contract);
        this.ai=Objects.requireNonNull(ai);this.verifier=Objects.requireNonNull(verifier);this.forbidden=List.copyOf(forbidden);
        this.productionInfrastructure=productionInfrastructure;
        this.database=Objects.requireNonNull(database);this.issuer=Objects.requireNonNull(issuer);this.resource=Objects.requireNonNull(resource);
        String prefix=contract==Contract.V2?"/ai/confinement-v2":"/ai/confinement";
        try(var manifest=getClass().getResourceAsStream(prefix+"-modules.properties");var sql=getClass().getResourceAsStream(prefix+"-catalog.sql")) {
            if(manifest==null||sql==null)throw new IllegalStateException();definitions.load(manifest);catalog=new String(sql.readAllBytes(),StandardCharsets.UTF_8);
        } catch(Exception missing) { throw refused(); }
    }
    public void verify() {
        String stage="PLATFORM";
        try(Connection caller=ai.getConnection();Connection review=verifier.getConnection()) {
            Platform platform=platform(caller);
            if(platform(review)!=platform)throw refused();
            stage="IDENTITY";
            Identity identity=identity(caller,platform);
            if(!database.equals(identity.database())||identity.userId()==1||identity.sysadmin()!=0)throw refused();
            Identity catalogIdentity=identity(review,platform);
            if(!database.equals(catalogIdentity.database())||Arrays.equals(identity.sid(),catalogIdentity.sid())
                    ||platform==Platform.AZURE_SQL_DATABASE&&!identity.server().equals(catalogIdentity.server()))throw refused();
            var otherIdentities=new ArrayList<Identity>();
            for(DataSource source:forbidden)try(Connection other=source.getConnection()) {
                if(platform(other)!=platform)throw refused();
                Identity otherIdentity=identity(other,platform);
                if(platform==Platform.AZURE_SQL_DATABASE&&(!database.equals(otherIdentity.database())||!identity.server().equals(otherIdentity.server())))throw refused();
                if(Arrays.equals(identity.sid(),otherIdentity.sid()))throw refused();
                if(productionInfrastructure&&Arrays.equals(catalogIdentity.sid(),otherIdentity.sid()))throw refused();
                otherIdentities.add(otherIdentity);
            }
            // VIEW DEFINITION is a prerequisite, not an inference from an empty catalog.
            requireOne(review,"SELECT HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','VIEW DEFINITION')");
            if(platform==Platform.AZURE_SQL_DATABASE) {
                contained(caller,review,identity);
                contained(review,review,catalogIdentity);
                azureIsolation(review);
            }
            publicAuthority(review);
            if(productionInfrastructure) {
                if(otherIdentities.size()!=3)throw refused();
                for(int n=0;n<2;n++)if(Arrays.equals(otherIdentities.get(2).sid(),otherIdentities.get(n).sid()))throw refused();
            }
            if(productionInfrastructure||platform==Platform.AZURE_SQL_DATABASE) {
                if(catalogIdentity.userId()==1||catalogIdentity.sysadmin()!=0)throw refused();
                var catalogTokens=new HashSet<String>();
                try(var s=statement(review);var r=s.executeQuery("SELECT name,type FROM sys.user_token")) {
                    while(r.next()) {
                        String name=r.getString(1);catalogTokens.add(name);
                        if(name.equals(catalogIdentity.user())&&!r.getString(2).equals("SQL USER"))throw refused();
                    }
                }
                if(!catalogTokens.equals(Set.of(catalogIdentity.user(),"public","AiCaseDataCatalogVerifier")))throw refused();
                if(platform==Platform.SQL_SERVER_OR_MI)try(var s=statement(review);var r=s.executeQuery("SELECT name FROM sys.login_token")) {
                    while(r.next())if(!Set.of(catalogIdentity.login(),"public").contains(r.getString(1)))throw refused();
                }
                try(var p=review.prepareStatement("SELECT COUNT(*) FROM sys.database_permissions WHERE grantee_principal_id=? AND NOT(class=0 AND permission_name='CONNECT' AND state='G')")) {
                    p.setQueryTimeout(3);p.setInt(1,catalogIdentity.userId());
                    try(var r=p.executeQuery()){if(!r.next()||r.getInt(1)!=0)throw refused();}
                }
                if(platform==Platform.SQL_SERVER_OR_MI)try(var p=review.prepareStatement("SELECT COUNT(*) FROM sys.server_permissions p JOIN sys.server_principals s ON s.principal_id=p.grantee_principal_id WHERE s.sid=? AND p.state IN('G','W') AND NOT(p.class=100 AND p.state='G' AND p.permission_name IN('CONNECT SQL','VIEW ANY DATABASE','VIEW ANY DEFINITION'))")) {
                    p.setQueryTimeout(3);p.setBytes(1,catalogIdentity.sid());
                    try(var r=p.executeQuery()){if(!r.next()||r.getInt(1)!=0)throw refused();}
                }
                try(var p=review.prepareStatement("SELECT (SELECT COUNT(*) FROM sys.schemas WHERE principal_id IN(SELECT principal_id FROM sys.user_token))+(SELECT COUNT(*) FROM sys.objects WHERE principal_id IN(SELECT principal_id FROM sys.user_token))+(SELECT COUNT(*) FROM sys.database_principals WHERE owning_principal_id IN(SELECT principal_id FROM sys.user_token))+(SELECT COUNT(*) FROM sys.database_permissions WHERE grantee_principal_id IN(SELECT principal_id FROM sys.user_token) AND state='W')")) {
                    p.setQueryTimeout(3);
                    try(var r=p.executeQuery()){if(!r.next()||r.getInt(1)!=0)throw refused();}
                }
            }
            stage="TOKENS";
            var tokens=new ArrayList<Integer>();var names=new HashSet<String>();
            try(var s=statement(caller);var r=s.executeQuery("SELECT principal_id,name,type FROM sys.user_token")) {
                while(r.next()) {
                    String name=r.getString(2),type=r.getString(3);tokens.add(r.getInt(1));names.add(name);
                    if(!name.equals(identity.user())&&!Set.of("public","AiCaseDataRuntime").contains(name))throw refused();
                    if(name.equals(identity.user())&&!type.equals("SQL USER"))throw refused();
                }
            }
            if(!names.equals(Set.of(identity.user(),"public","AiCaseDataRuntime")))throw refused();
            if(platform==Platform.SQL_SERVER_OR_MI)try(var s=statement(caller);var r=s.executeQuery("SELECT name,type FROM sys.login_token")) {
                while(r.next())if(!r.getString(1).equals(identity.login())&&!r.getString(1).equals("public"))throw refused();
            }
            stage="DATABASE_AUTHORITY";
            allowed(caller,null,"DATABASE",Set.of("CONNECT","VIEW ANY COLUMN ENCRYPTION KEY DEFINITION","VIEW ANY COLUMN MASTER KEY DEFINITION"));
            if(platform==Platform.SQL_SERVER_OR_MI) {
                allowed(caller,null,"SERVER",Set.of("CONNECT SQL","VIEW ANY DATABASE"));
                stage="SERVER_PERMISSION_VISIBILITY";
                try(var s=statement(review);var r=s.executeQuery("SELECT HAS_PERMS_BY_NAME(NULL,NULL,'VIEW ANY DEFINITION')")){if(!r.next()||r.getInt(1)!=1)throw refused();}
                stage="SERVER_TOKEN_GRANTS";
                try(var p=review.prepareStatement("SELECT COUNT(*) FROM sys.server_permissions p JOIN sys.server_principals s ON s.principal_id=p.grantee_principal_id LEFT JOIN sys.endpoints e ON p.class=105 AND p.major_id=e.endpoint_id WHERE (s.sid=? OR s.name='public') AND p.state IN('G','W') AND NOT(p.class=100 AND p.state='G' AND p.permission_name IN('CONNECT SQL','VIEW ANY DATABASE') OR s.name='public' AND p.state='G' AND p.class=105 AND p.permission_name='CONNECT' AND e.is_admin_endpoint=0 AND e.type=2 AND e.name IN('TSQL Local Machine','TSQL Named Pipes','TSQL Default TCP','TSQL Default VIA'))")) {
                    p.setQueryTimeout(3);p.setBytes(1,identity.sid());try(var r=p.executeQuery()){if(!r.next()||r.getInt(1)!=0)throw refused();}
                }
                stage="PUBLIC_DDL_GRANTS";
                // Specific public impersonation/DDL grants need not appear as database-wide permissions.
                try(var s=statement(review);var r=s.executeQuery("SELECT COUNT(*) FROM sys.database_permissions WHERE grantee_principal_id=DATABASE_PRINCIPAL_ID('public') AND state IN('G','W') AND permission_name IN('IMPERSONATE','ALTER','CONTROL','TAKE OWNERSHIP')")){if(!r.next()||r.getInt(1)!=0)throw refused();}
                stage="CROSS_DATABASE";
                try(var s=statement(review);var r=s.executeQuery("SELECT HAS_PERMS_BY_NAME(NULL,NULL,'VIEW ANY DATABASE')")){if(!r.next()||r.getInt(1)!=1)throw refused();}
                try(var s=statement(review);var databases=s.executeQuery("SELECT name FROM sys.databases WHERE database_id>4 AND name<>DB_NAME()")) {
                    while(databases.next())try(var p=caller.prepareStatement("SELECT HAS_DBACCESS(?)")) {
                        p.setQueryTimeout(3);p.setString(1,databases.getString(1));try(var r=p.executeQuery()){if(!r.next()||r.getInt(1)!=0||r.wasNull())throw refused();}
                    }
                }
            }
            stage="CATALOG_RECEIPT";
            // Complete visibility belongs to the verifier, never to the AI data principal.
            try(var s=statement(review)) { boolean more=s.execute(catalog);while(true){if(more)try(var r=s.getResultSet()){while(r.next()){} }else if(s.getUpdateCount()==-1)break;more=s.getMoreResults(Statement.CLOSE_CURRENT_RESULT);} }
            stage="SOURCE_RECEIPT";
            try(var p=review.prepareStatement("SELECT CONVERT(varchar(64),CONVERT(varbinary(32),value),2) FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID(N'dbo.AiCaseDataInvocation') AND name=N'Shale.AiCaseData"+contract+".Source'")) {
                p.setQueryTimeout(3);try(var r=p.executeQuery()){if(!r.next()||!definitions.getProperty("Source").equalsIgnoreCase(r.getString(1))||r.next())throw refused();}
            }
            stage="MODULE_BODIES";
            for(String module:definitions.stringPropertyNames())if(!module.equals("Source")) {
                try(var p=review.prepareStatement("SELECT m.definition,m.execute_as_principal_id,COALESCE(o.principal_id,s.principal_id),s.name FROM sys.objects o JOIN sys.schemas s ON s.schema_id=o.schema_id JOIN sys.sql_modules m ON m.object_id=o.object_id WHERE s.name='dbo' AND o.name=?")) {
                    p.setQueryTimeout(3);p.setString(1,module);
                    try(var r=p.executeQuery()) {
                        if(!r.next()||!definitions.getProperty(module).equals(HexFormat.of().formatHex(AiSql.hash(r.getString(1).getBytes(StandardCharsets.UTF_16LE))))||r.getInt(3)!=1)throw refused();
                        int execution=r.getInt(2);boolean callerIdentity=r.wasNull();
                        if(module.equals("AiDataLocateAccessOwnerV1")?callerIdentity||execution!=-2:!callerIdentity)throw refused();
                        if(r.next())throw refused();
                    }
                }
            }
            stage="OWNERSHIP";
            for(int token:tokens)try(var p=review.prepareStatement("SELECT (SELECT COUNT(*) FROM sys.schemas WHERE principal_id=?)+(SELECT COUNT(*) FROM sys.objects WHERE principal_id=?)+(SELECT COUNT(*) FROM sys.database_principals WHERE owning_principal_id=?)+(SELECT COUNT(*) FROM sys.database_permissions WHERE grantee_principal_id=? AND state='W')")) {
                p.setQueryTimeout(3);for(int n=1;n<=4;n++)p.setInt(n,token);
                try(var r=p.executeQuery()){if(!r.next()||r.getInt(1)!=0)throw refused();}
            }
            // Reject positive grants to the login itself, including column exceptions to role DENY.
            // Complete metadata comes from the verifier; AI metadata visibility is intentionally limited.
            try(var p=review.prepareStatement("SELECT COUNT(*) FROM sys.database_permissions WHERE grantee_principal_id=? AND NOT(class=0 AND permission_name='CONNECT' AND state='G')")) {
                p.setQueryTimeout(3);p.setInt(1,identity.userId());
                try(var r=p.executeQuery()){if(!r.next()||r.getInt(1)!=0)throw refused();}
            }
            if(platform==Platform.AZURE_SQL_DATABASE) {
                allowed(review,null,"DATABASE",Set.of("CONNECT","VIEW DEFINITION","VIEW ANY COLUMN ENCRYPTION KEY DEFINITION","VIEW ANY COLUMN MASTER KEY DEFINITION"));
            }
            stage="OBJECT_AUTHORITY";
            var executable=new HashSet<String>();
            try(var s=statement(review);var r=s.executeQuery("SELECT s.name,o.name,o.type FROM sys.objects o JOIN sys.schemas s ON s.schema_id=o.schema_id WHERE o.is_ms_shipped=0 AND o.type IN('U','V','P','FN','IF','TF','SN','FS','FT','PC')")) {
                while(r.next()) {
                    String schema=r.getString(1),name=r.getString(2),object=quote(schema)+"."+quote(name);
                    Set<String> permitted=schema.equals("dbo")&&PUBLIC.contains(name)?Set.of("EXECUTE"):Set.of();
                    Set<String> actual=allowed(caller,object,"OBJECT",permitted);
                    if(actual.contains("EXECUTE"))executable.add(name);
                    if(platform==Platform.AZURE_SQL_DATABASE) {
                        Set<String> metadata=schema.equals("dbo")&&Set.of("AiCaseDataDeployment","AiDataUnicodeRange","AiDataUnicodeLower","AiDataWordTransition").contains(name)?Set.of("SELECT","VIEW DEFINITION"):Set.of("VIEW DEFINITION");
                        allowed(review,object,"OBJECT",metadata);
                    }
                    // SQL DENY at object level can have column-GRANT exceptions: check EVERY column.
                    try(var p=review.prepareStatement("SELECT c.name FROM sys.columns c JOIN sys.objects o ON o.object_id=c.object_id JOIN sys.schemas s ON s.schema_id=o.schema_id WHERE s.name=? AND o.name=?")) {
                        p.setQueryTimeout(3);p.setString(1,schema);p.setString(2,name);
                        try(var columns=p.executeQuery()) {while(columns.next())try(var check=caller.prepareStatement("SELECT HAS_PERMS_BY_NAME(?,'OBJECT','SELECT',?,'COLUMN'),HAS_PERMS_BY_NAME(?,'OBJECT','UPDATE',?,'COLUMN')")) {
                            check.setQueryTimeout(3);check.setString(1,object);check.setString(2,columns.getString(1));check.setString(3,object);check.setString(4,columns.getString(1));
                            try(var result=check.executeQuery()){if(!result.next()||result.getInt(1)==1||result.getInt(2)==1)throw refused();}
                        }}
                    }
                }
            }
            if(!executable.equals(PUBLIC))throw refused();
            stage="DEPLOYMENT_TIMEZONE";
            try(var s=statement(review);var r=s.executeQuery("SELECT Issuer,Resource,JavaTimeZone,SqlTimeZone FROM dbo.AiCaseDataDeployment WHERE Id=1")) {
                if(!r.next()||!issuer.equals(r.getString(1))||!resource.equals(r.getString(2))||!ZoneId.systemDefault().equals(ZoneId.of(r.getString(3))))throw refused();
                String javaZone=r.getString(3),sqlZone=r.getString(4);if(r.next())throw refused();
                // Pin SQL/Java wall-time mapping at both sides of every supported DST transition.
                ZoneId zone=ZoneId.of(javaZone);
                for(int year=2025;year<=2028;year++)for(int month:List.of(1,3,4,7,10,11,12)) {
                    Instant sample=LocalDate.of(year,month,15).atStartOfDay(ZoneOffset.UTC).toInstant();
                    try(var p=review.prepareStatement("SELECT CONVERT(datetime2(7),(CONVERT(datetime2(7),?) AT TIME ZONE 'UTC') AT TIME ZONE ?)")) {
                        p.setQueryTimeout(3);com.shale.data.util.UtcJdbcTimestamp.set(p,1,sample);p.setString(2,sqlZone);
                        try(var dates=p.executeQuery()){if(!dates.next()||!dates.getTimestamp(1).toLocalDateTime().equals(LocalDateTime.ofInstant(sample,zone)))throw refused();}
                    }
                }
            }
        } catch(Exception failure) { throw new IllegalStateException("AI Case datasource attestation failed closed: "+stage+(failure instanceof SQLException sql?" (SQL "+sql.getErrorCode()+")":"")); }
    }
    private record Identity(byte[] sid,String login,String database,String user,int userId,int sysadmin,String server) {}
    enum Platform { SQL_SERVER_OR_MI, AZURE_SQL_DATABASE }
    static Platform platform(Connection c)throws SQLException {
        try(var s=statement(c);var r=s.executeQuery("SELECT CONVERT(int,SERVERPROPERTY('EngineEdition'))")) {
            if(!r.next())throw refused();int edition=r.getInt(1);if(r.wasNull()||r.next())throw refused();
            return switch(edition) {case 2,3,4,8 -> Platform.SQL_SERVER_OR_MI;case 5 -> Platform.AZURE_SQL_DATABASE;default -> throw refused();};
        }
    }
    private static Identity identity(Connection c,Platform platform)throws SQLException {
        if(platform==Platform.AZURE_SQL_DATABASE) {
            try(var s=statement(c);var r=s.executeQuery("SELECT sid,ORIGINAL_LOGIN(),DB_NAME(),USER_NAME(),USER_ID(),CONVERT(nvarchar(128),SERVERPROPERTY('ServerName')) FROM sys.database_principals WHERE principal_id=USER_ID()")) {
                if(!r.next())throw refused();var result=new Identity(r.getBytes(1),r.getString(2),r.getString(3),r.getString(4),r.getInt(5),0,r.getString(6));
                if(result.sid()==null||result.sid().length==0||result.user()==null||result.login()==null||result.server()==null||result.server().isBlank()||r.next())throw refused();return result;
            }
        }
        try(var s=statement(c);var r=s.executeQuery("SELECT SUSER_SID(),ORIGINAL_LOGIN(),DB_NAME(),USER_NAME(),USER_ID(),IS_SRVROLEMEMBER('sysadmin')")) {
            if(!r.next())throw refused();var result=new Identity(r.getBytes(1),r.getString(2),r.getString(3),r.getString(4),r.getInt(5),r.getInt(6),null);if(r.wasNull()||result.sid()==null||r.next())throw refused();return result;
        }
    }
    // Both the authenticated session and the independent full catalog must agree. Names alone
    // cannot select the model: INSTANCE, EXTERNAL, group, certificate and WITHOUT LOGIN users fail.
    private static void contained(Connection session,Connection review,Identity identity)throws SQLException {
        if(identity.userId()<=4||!identity.user().equals(identity.login()))throw refused();
        // Prove how THIS session authenticated, not just how a same-named catalog user was
        // provisioned. Own-session visibility needs no VIEW DATABASE STATE/server role.
        requireOne(session,"SELECT COUNT(*) FROM sys.dm_exec_sessions WHERE session_id=@@SPID AND authenticating_database_id=DB_ID() AND original_security_id=(SELECT sid FROM sys.database_principals WHERE principal_id=USER_ID())");
        String sql="SELECT sid,type,authentication_type FROM sys.database_principals WHERE principal_id=? AND name=?";
        for(Connection c:List.of(session,review))try(var p=c.prepareStatement(sql)) {
            p.setQueryTimeout(3);p.setInt(1,identity.userId());p.setString(2,identity.user());
            try(var r=p.executeQuery()) {
                if(!r.next()||!Arrays.equals(identity.sid(),r.getBytes(1))||!"S".equals(r.getString(2))||r.getInt(3)!=2||r.wasNull()||r.next())throw refused();
            }
        }
    }
    static void azureIsolation(Connection review)throws SQLException {
        // VIEW DEFINITION covers these catalogs. Credentials have a different visibility rule:
        // never query their filtered catalog here and claim absence (see Stage 0 operator gate).
        requireOne(review,"SELECT HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','VIEW DEFINITION')");
        requireZero(review,"SELECT COUNT(*) FROM sys.external_data_sources");
        requireZero(review,"SELECT COUNT(*) FROM sys.external_tables");
        // Reject all synonyms, including unresolved names; do not parse identifier strings.
        requireZero(review,"SELECT COUNT(*) FROM sys.synonyms");
        requireZero(review,"SELECT COUNT(*) FROM sys.sql_expression_dependencies WHERE referenced_server_name IS NOT NULL OR referenced_database_name IS NOT NULL");
        requireZero(review,"SELECT COUNT(*) FROM sys.databases WHERE database_id=DB_ID() AND (is_trustworthy_on<>0 OR is_db_chaining_on<>0)");
    }
    static void publicAuthority(Connection review)throws SQLException {
        // Exact verifier role/own grants do not exclude authority inherited through public.
        // Keep ordinary system metadata/context facilities, but refuse application reads,
        // EXECUTEs, schema/database authority and every public grant option. Negative
        // system IDs include engine compatibility catalog views absent from sys.all_objects.
        try(var s=statement(review);var r=s.executeQuery("""
                SELECT COUNT(*) FROM sys.database_permissions p
                LEFT JOIN sys.all_objects o ON p.class=1 AND o.object_id=p.major_id
                WHERE p.grantee_principal_id=DATABASE_PRINCIPAL_ID('public')
                  AND (p.state='D' AND p.permission_name LIKE '%DEFINITION%'
                    OR p.state IN('G','W') AND (p.state='W' OR NOT(
                    p.class=0 AND p.permission_name IN('CONNECT','VIEW ANY COLUMN ENCRYPTION KEY DEFINITION','VIEW ANY COLUMN MASTER KEY DEFINITION')
                    OR p.class=1 AND p.permission_name IN('SELECT','EXECUTE','VIEW DEFINITION')
                      AND OBJECT_SCHEMA_NAME(p.major_id) IN('sys','INFORMATION_SCHEMA')
                      AND (COALESCE(o.is_ms_shipped,0)=1 OR p.major_id<0 AND p.permission_name='SELECT')
                    OR p.class IN(1,3) AND p.permission_name='VIEW DEFINITION')))
                """)) {
            if(!r.next()||r.getInt(1)!=0||r.wasNull()||r.next())throw refused();
        }
    }
    private static void requireOne(Connection c,String sql)throws SQLException {requireCount(c,sql,1);}
    private static void requireZero(Connection c,String sql)throws SQLException {requireCount(c,sql,0);}
    private static void requireCount(Connection c,String sql,int expected)throws SQLException {
        try(var s=statement(c);var r=s.executeQuery(sql)) {
            if(!r.next()||r.getInt(1)!=expected||r.wasNull()||r.next())throw refused();
        }
    }
    private static Set<String> allowed(Connection c,String object,String scope,Set<String> allow)throws SQLException {
        var actual=new HashSet<String>();
        try(var p=c.prepareStatement("SELECT permission_name FROM sys.fn_my_permissions(?,?)")) {
            p.setQueryTimeout(3);p.setString(1,object);p.setString(2,scope);
            try(var r=p.executeQuery()){while(r.next()){String permission=r.getString(1);if(!allow.contains(permission))throw refused();actual.add(permission);}}
        } catch(SQLException denied) {
            // Complete verifier inventory identifies the object; an invisible object has no effective permission.
            if(denied.getErrorCode()!=15151||!allow.isEmpty())throw denied;
        }
        return actual;
    }
    private static String quote(String name){return "["+name.replace("]","]]")+"]";}
    private static Statement statement(Connection c)throws SQLException {var s=c.createStatement();s.setQueryTimeout(3);return s;}
    private static IllegalStateException refused(){return new IllegalStateException("AI Case datasource attestation failed closed.");}
}
