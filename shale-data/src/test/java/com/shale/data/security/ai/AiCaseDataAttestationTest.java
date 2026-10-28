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
    @Test void platformsMustBePositivelyIdentified()throws Exception {
        for(int edition:List.of(2,3,4,8))assertEquals(AiCaseDataAttestation.Platform.SQL_SERVER_OR_MI,AiCaseDataAttestation.platform(connection(q->rows(edition))));
        assertEquals(AiCaseDataAttestation.Platform.AZURE_SQL_DATABASE,AiCaseDataAttestation.platform(connection(q->rows(5))));
        for(Integer edition:Arrays.asList(null,0,1,6,9,11,12))
            assertThrows(IllegalStateException.class,()->AiCaseDataAttestation.platform(connection(q->rows(edition))),"Unknown engine must not inherit SQL Server behavior: "+edition);
        assertThrows(IllegalStateException.class,()->AiCaseDataAttestation.platform(connection(q->List.of())));
        assertThrows(IllegalStateException.class,()->AiCaseDataAttestation.platform(connection(q->List.of(new Object[]{5},new Object[]{5}))));
    }
    @Test void azureNeverProbesServerCatalogsEvenWhenCatalogAttestationFails() {
        var observed=new ArrayList<String>();
        Connection caller=azure("ai",10,observed),review=azure("catalog",11,observed);
        var failure=assertThrows(IllegalStateException.class,()->new AiCaseDataAttestation(source(caller),source(review),List.of(),"db","issuer","resource").verify());
        assertTrue(failure.getMessage().contains("CATALOG_RECEIPT"),failure.getMessage());
        assertTrue(observed.stream().anyMatch(q->q.contains("sys.external_data_sources")));
        for(String forbidden:List.of("sys.server_permissions","sys.server_principals","sys.endpoints","sys.login_token","HAS_DBACCESS","IS_SRVROLEMEMBER","'SERVER'"))
            assertFalse(observed.stream().anyMatch(q->q.contains(forbidden)),forbidden);
    }
    @Test void v2AzureCatalogFailureDoesNotTryHistoricalReceipt() {
        var observed=new ArrayList<String>();
        var failure=assertThrows(IllegalStateException.class,()->new AiCaseDataAttestation(
                source(azure("ai",10,observed)),source(azure("catalog",11,observed)),List.of(),"db","issuer","resource",false,AiCaseDataAttestation.Contract.V2).verify());
        assertTrue(failure.getMessage().contains("CATALOG_RECEIPT"));
        assertTrue(observed.stream().anyMatch(q->q.contains("Shale.AiCaseDataV2")));
        assertFalse(observed.stream().anyMatch(q->q.contains("Shale.AiCaseDataV1")));
        for(String forbidden:List.of("sys.server_permissions","sys.server_principals","sys.endpoints","sys.login_token","HAS_DBACCESS"))
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
        for(String surface:List.of("sys.server_permissions","sys.server_principals","sys.endpoints","sys.login_token","HAS_DBACCESS"))assertFalse(azure.contains(surface),surface);
        assertTrue(azure.contains("sys.database_scoped_credentials"));
        assertTrue(azure.contains("'CONTROL'"));assertTrue(azure.contains("'STOP'"));
        assertFalse(executable.contains("SELECT *"));assertFalse(executable.contains("credential_identity"));assertFalse(executable.contains("location"));
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
