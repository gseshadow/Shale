package com.shale.data.security.ai;

import com.shale.core.runtime.DbSessionProvider;
import org.junit.jupiter.api.Test;
import java.lang.reflect.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Adapter/namespace checks supplement the required hash-gated SQL acceptance. */
class AiDcrApplicationStoreTest {
    static final String ID="dcr."+"a".repeat(64),ISSUER="https://shale.test/oauth/mcp",RESOURCE="https://shale.test/mcp";
    @Test void generatedNamespaceCannotEnterManualOrCimdAdapters() {
        assertEquals(ID,new DcrClientIdentifier(ID).value());
        assertFalse(ID.matches("[A-Za-z0-9_-]{1,64}"));
        for(String bad:List.of("client", "https://vendor.test/client.json",ID.toUpperCase(),ID+"a",ID.substring(1),"dcr."+"g".repeat(64)))
            assertThrows(IllegalArgumentException.class,()->new DcrClientIdentifier(bad),bad);
        var store=new AiCimdOAuthStore(()->{fail("CIMD-only adapter must reject DCR before SQL");return null;},ISSUER,RESOURCE);
        assertThrows(SecurityException.class,()->store.resolve("x",ID,"CODE"));
        assertThrows(SecurityException.class,()->store.loadClient(ID,UUID.randomUUID()));
        assertThrows(SecurityException.class,()->store.revokeUnknown(ID,new byte[32],"none",null,null,null,null));
    }
    @Test void neutralRegistrationDelegatesWithoutTenantIdentityOrRequestedClientId() {
        var jdbc=new Jdbc();var store=new AiDcrApplicationStore(jdbc,ISSUER,RESOURCE);
        var registration=store.register("Synthetic DCR","[\"https://client.test/callback\"]","[]",1,3);
        assertEquals(ID,registration.identifier().value());
        assertEquals("{call dbo.AiDcrRegisterApplicationV2(?,?,?,?,?,?,?,?)}",jdbc.procedure);
        assertEquals(8,jdbc.bindings.size());assertEquals(ISSUER,jdbc.bindings.get(1));assertEquals(RESOURCE,jdbc.bindings.get(2));
        assertEquals(1,jdbc.bindings.get(6));assertEquals(3,jdbc.bindings.get(7));
        assertFalse(jdbc.bindings.containsValue(ID),"SQL generates the ID; no caller-selected alias");
        assertEquals("DcrRegistration[redacted]",registration.toString());
        for(var field:registration.getClass().getRecordComponents())assertFalse(field.getName().matches("(?i).*(tenant|grant|consent|credential|token).*"));
    }
    @Test void v2LookupAndAdministrativeMutationUseOnlyExplicitVersionedOperations() {
        var jdbc=new Jdbc();var store=new AiDcrApplicationStore(jdbc,ISSUER,RESOURCE);
        assertEquals(ID,store.prepare(new DcrClientIdentifier(ID)).identifier().value());
        assertEquals("{call dbo.AiDcrPrepareMetadataV2(?,?,?)}",jdbc.procedure);
        assertEquals(ID,jdbc.bindings.get(1));
        var oauth=new AiCimdOAuthStore(jdbc,ISSUER,RESOURCE,AiExternalContract.EXTERNAL_V2);
        oauth.resolve("a".repeat(43),ID,"CODE");
        assertEquals("{call dbo.AiExternalResolveCodeV2(?,?,?,?)}",jdbc.procedure);
        store.changeState(UUID.randomUUID(),true,new AiIntegrationSecurityService.RowVersion(new byte[8]));
        assertEquals("{call dbo.AiDcrChangeStateV2(?,?,?,?)}",jdbc.procedure);
        assertEquals(true,jdbc.bindings.get(2));
    }
    @Test void successorSelectionNeverFallsBackToDefaultPredecessor() {
        var jdbc=new Jdbc();
        var previous=new AiCaseDataAttestation(jdbc.source(),jdbc.source(),List.of(),"synthetic",ISSUER,RESOURCE);
        assertThrows(IllegalStateException.class,()->previous.requireRegistryContract(AiExternalContract.EXTERNAL_V2));
        var explicit=new AiCaseDataAttestation(jdbc.source(),jdbc.source(),List.of(),"synthetic",ISSUER,RESOURCE,false,AiCaseDataAttestation.Contract.V2,AiExternalContract.EXTERNAL_V2);
        explicit.requireRegistryContract(AiExternalContract.EXTERNAL_V2);
        assertEquals("AiExternalDataAuthenticateV2",AiCaseDataAccess.Namespace.EXTERNAL_V2.procedure("AiDataAuthenticateV1"));
        assertEquals("AiCimdDataAuthenticateV1",AiCaseDataAccess.Namespace.CIMD_V1.procedure("AiDataAuthenticateV1"));
    }
    static final class Jdbc implements DbSessionProvider {
        String procedure;final Map<Integer,Object> bindings=new HashMap<>();
        final UUID app=UUID.randomUUID();boolean row;
        final Map<String,Object> values=new HashMap<>();
        Jdbc(){values.putAll(Map.of("ClientId",ID,"ExternalClientId",ID,"ApplicationId",app.toString(),"Revision",1L,"ApplicationRevision",1L,"Disabled",false,"RowVer",new byte[8],"MethodMask",1,"ScopeMask",3));
            values.putAll(Map.of("IssuedAt",Timestamp.from(Instant.now()),"FreshUntil",Timestamp.from(Instant.now().plusSeconds(240)),"ClientName","Synthetic DCR","Redirects","[\"https://client.test/callback\"]","SigningKeys","[]","MetadataDigest",new byte[32],"ShaleClientId",7,"UserId",72));}
        javax.sql.DataSource source(){return proxy(javax.sql.DataSource.class,(p,m,a)->m.getName().equals("getConnection")?requireConnection():empty(m));}
        @Override public Connection requireConnection(){return proxy(Connection.class,(p,m,a)->switch(m.getName()){
            case "getAutoCommit"->true;
            case "prepareCall"->{procedure=(String)a[0];bindings.clear();yield statement();}
            default->empty(m);
        });}
        CallableStatement statement(){return proxy(CallableStatement.class,(p,m,a)->{
            if(m.getName().startsWith("set")&&a!=null&&a.length>=2&&a[0] instanceof Integer i){bindings.put(i,a[1]);return null;}
            return switch(m.getName()){
                case "execute"->{row=false;yield true;}
                case "getResultSet"->proxy(ResultSet.class,(r,rm,ra)->{
                    if(rm.getName().equals("next")){boolean next=!row;row=true;return next;}
                    if(ra!=null&&ra.length>0&&ra[0] instanceof String key){Object v=values.get(key);return switch(rm.getName()){
                        case "getString"->v==null?null:v.toString();case "getLong"->((Number)v).longValue();case "getInt"->((Number)v).intValue();default->v;};}
                    return empty(rm);
                });
                case "getUpdateCount"->-1;
                default->empty(m);
            };
        });}
        @SuppressWarnings("unchecked") static <T>T proxy(Class<T> type,InvocationHandler h){return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},h);}
        static Object empty(Method m){if(!m.getReturnType().isPrimitive()||m.getReturnType()==void.class)return null;if(m.getReturnType()==boolean.class)return false;if(m.getReturnType()==long.class)return 0L;return 0;}
    }
}
