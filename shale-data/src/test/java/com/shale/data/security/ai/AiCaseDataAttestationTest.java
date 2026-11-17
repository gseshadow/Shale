package com.shale.data.security.ai;

import org.junit.jupiter.api.Test;
import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.*;

/** Routing/failure tests only. These doubles cannot establish Azure SQL permission semantics. */
class AiCaseDataAttestationTest {
    // Possessive repetition keeps inspection of large generated SQL batches off the Java call stack.
    private static final java.util.regex.Pattern REGISTRY_SERVER_CHECK=java.util.regex.Pattern.compile(
            "declare @registryservercheck nvarchar\\(max\\)=n'((?:[^']|'')*+)';\\s*exec\\(@registryservercheck\\);",
            java.util.regex.Pattern.DOTALL);

    @Test void registryPlatformInspectionHandlesLargeEscapedBatchesWithoutHidingFollowingSql() {
        for(String newline:List.of("\n","\r\n")) {
            String body=("SELECT N''escaped'';"+newline).repeat(4096)+"SELECT * FROM sys.server_principals;";
            String batch="declare @registryservercheck nvarchar(max)=n'"+body+"';"+newline+
                    "exec(@registryservercheck);"+newline+"SELECT * FROM sys.server_permissions;";
            var dynamic=REGISTRY_SERVER_CHECK.matcher(batch);
            assertTrue(dynamic.find(),"Large generated platform batches must remain inspectable without stack overflow.");
            assertEquals(body,dynamic.group(1),"Only the complete escaped dynamic string may be excluded from Azure inspection.");
            assertEquals(newline+"SELECT * FROM sys.server_permissions;",dynamic.replaceFirst(""),
                    "Unconditional server catalog access after platform dispatch must remain visible to inspection.");
        }
    }
    @Test void identityFailureDiagnosticsUseClosedSubstepsAndNeverRetainSensitiveCause() {
        List<SQLException> failures=List.of(new SQLTimeoutException("MCP deadline."),
                new SQLTimeoutException("PRIVATE_DRIVER_PASSWORD"),
                new SQLTransientConnectionException("PRIVATE_DRIVER_PASSWORD"),
                new SQLException("PRIVATE_DRIVER_PASSWORD", "private-state", 207));
        List<String> kinds=List.of("DEADLINE","QUERY_TIMEOUT","CONNECTION","SQL");
        for(int i=0;i<failures.size();i++) {
            var observed=new ArrayList<String>();SQLException problem=failures.get(i);
            DataSource broken=(DataSource)Proxy.newProxyInstance(DataSource.class.getClassLoader(),new Class[]{DataSource.class},(p,m,a)->{throw problem;});
            var failure=assertThrows(IllegalStateException.class,()->new AiCaseDataAttestation(
                    source(azure("private-ai",10,observed)),source(azure("private-verifier",11,observed)),
                    List.of(broken),"db","private-issuer","private-resource").verify());
            String message=failure.getMessage();
            assertTrue(message.contains("IDENTITY"),message);
            assertTrue(message.contains("step=AUTH_CONNECT kind="+kinds.get(i)),message);
            assertTrue(message.matches(".*elapsedMillis=[0-9]{1,6} interrupted=(true|false)\\]"),message);
            for(String secret:List.of("PRIVATE_DRIVER_PASSWORD","private-state","private-ai","private-verifier","private-issuer","private-resource"))
                assertFalse(message.contains(secret),"Attestation diagnostics must exclude private values");
            assertNull(failure.getCause(),"Driver exceptions can carry SQL/credentials and must remain unchained");
        }
    }
    @Test void platformFailuresIdentifyTheExactConnectionOrPropertyReadAndCloseAcquiredLeases() {
        for(String expected:List.of("CALLER_CONNECT","VERIFIER_CONNECT","CALLER_PLATFORM","VERIFIER_PLATFORM")) {
            var events=new ArrayList<String>();
            DataSource caller=platformSource("CALLER",expected,events);
            DataSource verifier=platformSource("VERIFIER",expected,events);
            var failure=assertThrows(IllegalStateException.class,()->new AiCaseDataAttestation(
                    caller,verifier,List.of(),"private-db","private-issuer","private-resource").verify());
            assertTrue(failure.getMessage().contains("PLATFORM (SQL 0) [step="+expected+" kind=CONNECTION origin=UNCLASSIFIED"),
                    failure.getMessage());
            List<String> acquired=events.stream().filter(e->e.endsWith("_OPEN")).map(e->e.replace("_OPEN","_CLOSE")).toList();
            var closed=events.stream().filter(e->e.endsWith("_CLOSE")).toList();
            var reversed=new ArrayList<>(acquired);Collections.reverse(reversed);
            assertEquals(reversed,closed,"Every acquired lease must close in reverse order, including verifier-open failure");
            assertNull(failure.getCause());
        }
    }
    @Test void connectionOriginDiagnosticsUseOnlyClosedCategoriesAcrossNestedAndNextExceptions() {
        List<Throwable> causes=List.of(new java.net.UnknownHostException("PRIVATE_HOST"),
                new javax.net.ssl.SSLHandshakeException("PRIVATE_CERTIFICATE"),
                new java.net.SocketTimeoutException("PRIVATE_ENDPOINT"),
                new java.net.ConnectException("PRIVATE_ENDPOINT"),new java.net.SocketException("PRIVATE_ENDPOINT"));
        List<String> origins=List.of("DNS","TLS","NETWORK_TIMEOUT","NETWORK_CONNECT","NETWORK_SOCKET");
        for(int i=0;i<causes.size();i++)for(boolean next:List.of(false,true)) {
            var driver=new SQLException("PRIVATE_DRIVER_PASSWORD","PRIVATE_STATE",0,causes.get(i));
            var pooled=new SQLTransientConnectionException("PRIVATE_POOL_AND_URL");
            if(next)pooled.setNextException(driver);else pooled.initCause(driver);
            DataSource broken=(DataSource)Proxy.newProxyInstance(DataSource.class.getClassLoader(),new Class[]{DataSource.class},(p,m,a)->{throw pooled;});
            var failure=assertThrows(IllegalStateException.class,()->new AiCaseDataAttestation(
                    broken,broken,List.of(),"PRIVATE_DATABASE","PRIVATE_ISSUER","PRIVATE_RESOURCE").verify());
            assertTrue(failure.getMessage().contains("step=CALLER_CONNECT kind=CONNECTION origin="+origins.get(i)),failure.getMessage());
            assertFalse(failure.getMessage().contains("PRIVATE_"),"Neither exception messages, states nor targets may escape");
            assertNull(failure.getCause());
        }
    }
    @Test void cyclicAndLongCauseGraphsRemainBoundedAndDoNotRetainPrivateCauses() {
        for(boolean cycle:List.of(false,true)) {
            var root=new SQLException("PRIVATE_ROOT");var tail=root;
            for(int i=0;i<(cycle?1:32);i++){var next=new SQLException("PRIVATE_NESTED");tail.initCause(next);tail=next;}
            tail.initCause(cycle?root:new java.net.UnknownHostException("PRIVATE_HOST"));
            DataSource broken=(DataSource)Proxy.newProxyInstance(DataSource.class.getClassLoader(),new Class[]{DataSource.class},(p,m,a)->{throw root;});
            var failure=assertTimeoutPreemptively(java.time.Duration.ofSeconds(2),()->assertThrows(IllegalStateException.class,
                    ()->new AiCaseDataAttestation(broken,broken,List.of(),"db","issuer","resource").verify()));
            assertTrue(failure.getMessage().contains("origin=UNCLASSIFIED"),failure.getMessage());
            assertFalse(failure.getMessage().contains("PRIVATE_"));assertNull(failure.getCause());
        }
    }
    private static DataSource platformSource(String role,String failedStep,List<String> events) {
        return (DataSource)Proxy.newProxyInstance(DataSource.class.getClassLoader(),new Class[]{DataSource.class},(p,m,a)->{
            if(!m.getName().equals("getConnection"))throw new UnsupportedOperationException();
            if(failedStep.equals(role+"_CONNECT"))throw new SQLTransientConnectionException("PRIVATE_DRIVER_PASSWORD");
            events.add(role+"_OPEN");
            Connection delegate=connection(q->{events.add(role+"_PLATFORM");return rows(5);});
            return Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class[]{Connection.class},(cp,cm,ca)->{
                if(cm.getName().equals("close")){events.add(role+"_CLOSE");return null;}
                if(cm.getName().equals("createStatement")&&failedStep.equals(role+"_PLATFORM"))
                    throw new SQLTransientConnectionException("PRIVATE_DRIVER_PASSWORD");
                try{return cm.invoke(delegate,ca);}catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}
            });
        });
    }
    @Test void laterStageFailuresDoNotClaimTheLastIdentitySubstep() {
        var observed=new ArrayList<String>();
        var failure=assertThrows(IllegalStateException.class,()->new AiCaseDataAttestation(
                source(azure("ai",10,observed)),source(azure("catalog",11,observed)),List.of(),"db","issuer","resource").verify());
        assertTrue(failure.getMessage().contains("CATALOG_RECEIPT"),failure.getMessage());
        assertTrue(failure.getMessage().contains("step=NONE kind=SQL"),failure.getMessage());
    }

    @Test void platformsMustBePositivelyIdentified()throws Exception {
        for(int edition:List.of(2,3,4,8))assertEquals(AiCaseDataAttestation.Platform.SQL_SERVER_OR_MI,AiCaseDataAttestation.platform(connection(q->rows(edition))));
        assertEquals(AiCaseDataAttestation.Platform.AZURE_SQL_DATABASE,AiCaseDataAttestation.platform(connection(q->rows(5))));
        for(Integer edition:Arrays.asList(null,0,1,6,9,11,12))
            assertThrows(IllegalStateException.class,()->AiCaseDataAttestation.platform(connection(q->rows(edition))),"Unknown engine must not inherit SQL Server behavior: "+edition);
        assertThrows(IllegalStateException.class,()->AiCaseDataAttestation.platform(connection(q->List.of())));
        assertThrows(IllegalStateException.class,()->AiCaseDataAttestation.platform(connection(q->List.of(new Object[]{5},new Object[]{5}))));
    }
    @Test void platformDetectionUsesOnlyTheDocumentedEnginePropertyOnEveryBranch()throws Exception {
        // Platform classification must not inherit the fixture's receipt/backup catalog needs.
        for(int edition:List.of(2,3,4,5,8)) {
            var queries=new ArrayList<String>();
            AiCaseDataAttestation.platform(connection(q->{
                queries.add(q);
                assertEquals("SELECT CONVERT(int,SERVERPROPERTY('EngineEdition'))",q,
                        "Platform detection must work without any system catalog, including recovery status");
                return rows(edition);
            }));
            assertEquals(1,queries.size(),"Platform detection must issue exactly one property read");
        }
    }
    @Test void platformSqlFailureIsPropagatedWithoutFallbackOrExtraQueries() {
        var queries=new ArrayList<String>();
        assertThrows(SQLException.class,()->AiCaseDataAttestation.platform(connection(q->{
            queries.add(q);throw new QueryFailure();
        })));
        assertEquals(List.of("SELECT CONVERT(int,SERVERPROPERTY('EngineEdition'))"),queries,
                "A failed property read must never fall back to SQL Server catalogs");
    }
    @Test void azureNeverProbesServerCatalogsEvenWhenCatalogAttestationFails() {
        var observed=new ArrayList<String>();
        Connection caller=azure("ai",10,observed),review=azure("catalog",11,observed);
        var failure=assertThrows(IllegalStateException.class,()->new AiCaseDataAttestation(source(caller),source(review),List.of(),"db","issuer","resource").verify());
        assertTrue(failure.getMessage().contains("CATALOG_RECEIPT"),failure.getMessage());
        assertTrue(observed.stream().anyMatch(q->q.contains("sys.external_data_sources")));
        for(String forbidden:List.of("sys.database_recovery_status","sys.server_permissions","sys.server_principals","sys.endpoints","sys.login_token","HAS_DBACCESS","IS_SRVROLEMEMBER","'SERVER'"))
            assertFalse(observed.stream().anyMatch(q->q.contains(forbidden)),forbidden);
    }
    @Test void v2AzureCatalogFailureDoesNotTryHistoricalReceipt() {
        var observed=new ArrayList<String>();
        var failure=assertThrows(IllegalStateException.class,()->new AiCaseDataAttestation(
                source(azure("ai",10,observed)),source(azure("catalog",11,observed)),List.of(),"db","issuer","resource",false,AiCaseDataAttestation.Contract.V2).verify());
        assertTrue(failure.getMessage().contains("CATALOG_RECEIPT"));
        assertTrue(observed.stream().anyMatch(q->q.contains("Shale.AiCaseDataV2")));
        assertFalse(observed.stream().anyMatch(q->q.contains("Shale.AiCaseDataV1")));
        for(String forbidden:List.of("sys.database_recovery_status","sys.server_permissions","sys.server_principals","sys.endpoints","sys.login_token","HAS_DBACCESS"))
            assertFalse(observed.stream().anyMatch(q->q.contains(forbidden)),forbidden);
    }
    @Test void azureRejectsLoginMappedAndExternalIdentitiesBeforeCatalogAcceptance() {
        for(int authentication:List.of(0,1,3,4)) {
            var observed=new ArrayList<String>();
            var failure=assertThrows(IllegalStateException.class,()->new AiCaseDataAttestation(
                    source(azure("ai",10,observed,authentication,"S")),source(azure("catalog",11,observed)),List.of(),"db","issuer","resource").verify());
            assertTrue(failure.getMessage().contains("IDENTITY"),failure.getMessage());
            assertFalse(observed.stream().anyMatch(q->q.contains("sys.external_data_sources")));
        }
        for(String type:List.of("E","X","U","G","C","K")) {
            var observed=new ArrayList<String>();
            assertThrows(IllegalStateException.class,()->new AiCaseDataAttestation(
                    source(azure("ai",10,observed,2,type)),source(azure("catalog",11,observed)),List.of(),"db","issuer","resource").verify());
        }
    }
    @Test void containedCatalogUserDoesNotSubstituteForActualSessionAuthentication() {
        for(Integer authenticated:Arrays.asList(null,0)) {
            var observed=new ArrayList<String>();
            var failure=assertThrows(IllegalStateException.class,()->new AiCaseDataAttestation(
                    source(azure("ai",10,observed,2,"S",authenticated)),source(azure("catalog",11,observed)),List.of(),"db","issuer","resource").verify());
            assertTrue(failure.getMessage().contains("IDENTITY"),failure.getMessage());
            assertFalse(observed.stream().anyMatch(q->q.contains("sys.external_data_sources")));
        }
    }
    @Test void visibilityMustBeCompleteBeforeExternalInventory() {
        for(Integer visible:Arrays.asList(null,0))assertThrows(IllegalStateException.class,()->AiCaseDataAttestation.azureIsolation(connection(q->rows(visible))));
    }
    @Test void everyExternalRouteAndAmbiguousResultFailsClosed()throws Exception {
        var queries=new ArrayList<String>();
        AiCaseDataAttestation.azureIsolation(connection(q->{queries.add(q);return rows(q.contains("HAS_PERMS")?1:0);}));
        assertEquals(6,queries.size());
        for(String fault:queries.subList(1,queries.size())) {
            assertThrows(IllegalStateException.class,()->AiCaseDataAttestation.azureIsolation(connection(q->rows(q.equals(fault)?1:q.contains("HAS_PERMS")?1:0))),fault);
            assertThrows(IllegalStateException.class,()->AiCaseDataAttestation.azureIsolation(connection(q->q.equals(fault)?rows(null):rows(q.contains("HAS_PERMS")?1:0))),fault);
        }
        assertThrows(SQLException.class,()->AiCaseDataAttestation.azureIsolation(connection(q->{throw new QueryFailure();})),"Missing catalog is not an empty pass");
    }
    @Test void publicFindingCannotBeIgnored()throws Exception {
        AiCaseDataAttestation.publicAuthority(connection(q->rows(0)));
        for(Integer finding:Arrays.asList(null,1,2))assertThrows(IllegalStateException.class,()->AiCaseDataAttestation.publicAuthority(connection(q->rows(finding))));
    }
    @Test void stage0AzureBatchIsReadOnlyBoundedAndExactlyDispatched()throws Exception {
        String azure=java.nio.file.Files.readString(java.nio.file.Path.of("../docs/sql/ai-integration/azure-sql-stage0-verify.sql"));
        String dispatcher=java.nio.file.Files.readString(java.nio.file.Path.of("../docs/sql/ai-integration/stage0-platform-verify.sql"));
        assertTrue(dispatcher.contains("EXEC sys.sp_executesql N'"+azure.replace("'","''")+"';"),"Dispatcher must execute the reviewed Azure verifier bytes");
        String executable=azure.replaceAll("(?s)/\\*.*?\\*/","");
        assertFalse(java.util.regex.Pattern.compile("(?i)\\b(INSERT|UPDATE|DELETE|CREATE|ALTER|DROP|MERGE|GRANT|DENY|REVOKE)\\b").matcher(executable).find(),"Stage 0 may not persist mutations");
        for(String surface:List.of("sys.database_recovery_status","sys.server_permissions","sys.server_principals","sys.endpoints","sys.login_token","HAS_DBACCESS"))assertFalse(azure.contains(surface),surface);
        assertTrue(azure.contains("sys.database_scoped_credentials"));
        assertTrue(azure.contains("'CONTROL'"));assertTrue(azure.contains("'STOP'"));
        assertFalse(executable.contains("SELECT *"));assertFalse(executable.contains("credential_identity"));assertFalse(executable.contains("location"));
    }
    @Test void azureMigrationAndPackagedVerificationSqlExcludeBoxedServerCatalogs()throws Exception {
        // Phase1R has separately tested dynamic platform dispatch; it is not a runtime detector.
        // This inventory covers SQL that Azure executes without such dispatch.
        var paths=new ArrayList<java.nio.file.Path>();
        try(var files=java.nio.file.Files.list(java.nio.file.Path.of("../docs/sql/ai-integration"))) {
            files.filter(p->p.toString().endsWith(".sql"))
                    .filter(p->!p.getFileName().toString().equals("stage0-platform-verify.sql"))
                    .forEach(paths::add);
        }
        paths.add(java.nio.file.Path.of("src/main/resources/ai/confinement-catalog.sql"));
        paths.add(java.nio.file.Path.of("src/main/resources/ai/confinement-v2-catalog.sql"));
        paths.add(java.nio.file.Path.of("src/main/resources/ai/registry-confinement-v1-catalog.sql"));
        paths.add(java.nio.file.Path.of("src/main/resources/ai/registry-external-v2-catalog.sql"));
        paths.add(java.nio.file.Path.of("src/main/resources/ai/external-authorization-v2-catalog.sql"));
        paths.add(java.nio.file.Path.of("src/main/resources/ai/registry-dcr-v3-catalog.sql"));
        paths.add(java.nio.file.Path.of("src/main/resources/ai/authorization-dcr-v3-catalog.sql"));
        assertTrue(paths.size()>10,"The Azure migration/verification inventory must not be empty");
        for(var path:paths) {
            String sql=java.nio.file.Files.readString(path).toLowerCase(Locale.ROOT);
            if(Set.of("registry-external-v2-catalog.sql","external-authorization-v2-catalog.sql","registry-dcr-v3-catalog.sql","authorization-dcr-v3-catalog.sql").contains(path.getFileName().toString())) {
                assertTrue(sql.contains("if @c1edition=5 begin"),"C1 retains explicit contained/boxed dispatch");
                var c1=java.util.regex.Pattern.compile("declare @c1servercheck nvarchar\\(max\\)=n'((?:[^']|'')*+)';\\s*exec\\(@c1servercheck\\);",java.util.regex.Pattern.DOTALL).matcher(sql);
                assertTrue(c1.find(),"C1 boxed catalog checks require a guarded dynamic batch");
                assertTrue(c1.group(1).contains("sys.server_principals"));
                sql=c1.replaceFirst("");
            }
            if(Set.of("registry-dcr-v3-catalog.sql","authorization-dcr-v3-catalog.sql").contains(path.getFileName().toString())) {
                assertTrue(sql.contains("if @c2identityedition=5 begin"),"C2 reuses contained/boxed dispatch");
                var endpoint=java.util.regex.Pattern.compile("declare @c2identityservercheck nvarchar\\(max\\)=n'((?:[^']|'')*+)';\\s*exec\\(@c2identityservercheck\\);",java.util.regex.Pattern.DOTALL).matcher(sql);
                assertTrue(endpoint.find(),"C2 endpoint boxed checks require guarded dynamic batch");
                assertTrue(endpoint.group(1).contains("sys.server_principals"));sql=endpoint.replaceFirst("");
            }
            if(Set.of("universal-pr3-confinement-verify.sql","registry-confinement-v1-catalog.sql","registry-external-v2-catalog.sql","registry-dcr-v3-catalog.sql").contains(path.getFileName().toString())) {
                // SQL Server metadata lives in a dynamic ELSE branch; Azure must never compile/issue it.
                assertTrue(sql.contains("if @registryedition=5 begin"));
                assertTrue(sql.contains("end else begin"));
                var dynamic=REGISTRY_SERVER_CHECK.matcher(sql);
                assertTrue(dynamic.find(),"Explicit platform dispatch required: "+path);
                assertTrue(dynamic.group(1).contains("sys.server_principals"));
                sql=dynamic.replaceFirst("");
            }
            for(String unsupported:List.of("sys.database_recovery_status","sys.server_permissions","sys.server_principals",
                    "sys.endpoints","sys.login_token","sys.master_files","sys.configurations","sys.linked_logins",
                    "sys.servers","sys.dm_xe_sessions","sys.dm_xe_session_targets","msdb.","master."))
                assertFalse(sql.contains(unsupported),path+": unsupported Azure catalog "+unsupported);
        }
    }
    private static Connection azure(String name,int id,List<String> observed) {return azure(name,id,observed,2,"S");}
    private static Connection azure(String name,int id,List<String> observed,int authentication,String type) {return azure(name,id,observed,authentication,type,1);}
    private static Connection azure(String name,int id,List<String> observed,int authentication,String type,Integer authenticated) {
        return connection(q->{
            observed.add(q);
            if(q.contains("EngineEdition"))return rows(5);
            if(q.contains("sys.dm_exec_sessions"))return rows(authenticated);
            if(q.startsWith("SELECT sid,ORIGINAL_LOGIN"))return List.<Object[]>of(new Object[]{new byte[]{(byte)id},name,"db",name,id,"logical-server"});
            if(q.startsWith("SELECT sid,type,authentication_type")) {
                int principal=Integer.parseInt(q.substring(q.indexOf("/*first=")+8,q.indexOf("*/")));
                return List.<Object[]>of(new Object[]{new byte[]{(byte)principal},type,authentication});
            }
            if(q.equals("SELECT name,type FROM sys.user_token"))return List.of(new Object[]{name,"SQL USER"},new Object[]{"public","ROLE"},new Object[]{"AiCaseDataCatalogVerifier","ROLE"});
            if(q.equals("SELECT principal_id,name,type FROM sys.user_token"))return List.of(new Object[]{id,name,"SQL USER"},new Object[]{0,"public","ROLE"},new Object[]{12,"AiCaseDataRuntime","ROLE"});
            if(q.contains("fn_my_permissions"))return List.of();
            return rows(q.contains("HAS_PERMS")?1:0);
        });
    }
    private static List<Object[]> rows(Integer value){return List.<Object[]>of(new Object[]{value});}
    private static class QueryFailure extends RuntimeException {}
    private static DataSource source(Connection connection) {
        return (DataSource)Proxy.newProxyInstance(DataSource.class.getClassLoader(),new Class[]{DataSource.class},(p,m,a)->{
            if(m.getName().equals("getConnection"))return connection;
            throw new UnsupportedOperationException(m.getName());
        });
    }
    private static Connection connection(Function<String,List<Object[]>> query) {
        return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class[]{Connection.class},(p,m,a)->{
            if(Set.of("close","setAutoCommit").contains(m.getName()))return null;
            if(m.getName().equals("createStatement")||m.getName().equals("prepareStatement")) {
                String prepared=a==null?null:(String)a[0];
                Class<?> kind=prepared==null?Statement.class:PreparedStatement.class;
                var bindings=new HashMap<Integer,Object>();
                return Proxy.newProxyInstance(kind.getClassLoader(),new Class[]{kind},(sp,sm,sa)->{
                    if(sm.getName().equals("execute")){query.apply((String)sa[0]);throw new SQLException("catalog unavailable",null,59100);}
                    if(sm.getName().equals("executeQuery")) {
                        List<Object[]> data;
                        try{data=query.apply(prepared==null?(String)sa[0]:prepared+" /*first="+bindings.get(1)+"*/");}catch(QueryFailure failure){throw new SQLException("catalog unavailable",null,208);}
                        int[] index={-1};boolean[] wasNull={false};
                        return Proxy.newProxyInstance(ResultSet.class.getClassLoader(),new Class[]{ResultSet.class},(rp,rm,ra)->{
                            if(rm.getName().equals("next"))return ++index[0]<data.size();
                            if(rm.getName().equals("close"))return null;
                            if(rm.getName().equals("wasNull"))return wasNull[0];
                            Object value=data.get(index[0])[(Integer)ra[0]-1];wasNull[0]=value==null;
                            return switch(rm.getName()){case "getInt"->value==null?0:((Number)value).intValue();case "getString"->(String)value;case "getBytes"->(byte[])value;default->throw new UnsupportedOperationException(rm.getName());};
                        });
                    }
                    if(sm.getName().startsWith("set")) {
                        if(sa!=null&&sa.length==2)bindings.put((Integer)sa[0],sa[1]);return null;
                    }
                    if(sm.getName().equals("close"))return null;
                    throw new UnsupportedOperationException(sm.getName());
                });
            }
            throw new UnsupportedOperationException(m.getName());
        });
    }
}
