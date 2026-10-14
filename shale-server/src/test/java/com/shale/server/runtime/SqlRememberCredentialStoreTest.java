package com.shale.server.runtime;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class SqlRememberCredentialStoreTest {
    @Test void createUsesPrincipalScopedRuntimeConnectionAndBindsTheMigrationShape() {
        var openedFor=new AtomicReference<ServerPrincipal>();
        var bindings=new HashMap<Integer,Object>();
        var sql=new AtomicReference<String>();
        PreparedStatement statement=(PreparedStatement)Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{PreparedStatement.class},(proxy,method,args)->{
                    if(method.getName().startsWith("set")){bindings.put((Integer)args[0],args[1]);return null;}
                    if(method.getName().equals("executeUpdate"))return 1;
                    if(method.getName().equals("close"))return null;
                    return defaultValue(method.getReturnType());
                });
        Connection connection=(Connection)Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{Connection.class},(proxy,method,args)->{
                    if(method.getName().equals("prepareStatement")){sql.set((String)args[0]);return statement;}
                    if(method.getName().equals("close"))return null;
                    return defaultValue(method.getReturnType());
                });
        DataSource auth=(DataSource)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{DataSource.class},
                (proxy,method,args)->{throw new AssertionError("remembered credential creation must not use the context-free auth pool");});
        RuntimeConnectionProvider runtime=principal->{openedFor.set(principal);return connection;};
        var store=new SqlRememberCredentialStore(auth,runtime);
        var principal=new ServerPrincipal(9,7,"owner@test");
        UUID session=UUID.randomUUID(),installation=UUID.randomUUID();byte[] hash=new byte[32];
        Instant expiry=Instant.parse("2026-11-05T00:00:00Z");

        store.create(principal,session,installation,hash,expiry);

        assertSame(principal,openedFor.get(),"create must establish runtime tenant/user context from the authenticated principal");
        assertTrue(sql.get().contains("DesktopRememberCredentials(ShaleClientId,UserId,SessionId,InstallationId,CredentialHash,AbsoluteExpiresAt)"),
                "the insert must retain the deployed migration column order");
        assertEquals(7,bindings.get(1));assertEquals(9,bindings.get(2));assertEquals(session,bindings.get(3));
        assertEquals(installation,bindings.get(4));assertSame(hash,bindings.get(5));
        assertEquals(Timestamp.from(expiry),bindings.get(6));
    }

    @Test void restoreIdentityLookupUsesOnlyTheNarrowAuthModuleBeforeOpeningRuntimeContext() {
        UUID session=UUID.randomUUID(),installation=UUID.randomUUID();
        var next=new java.util.concurrent.atomic.AtomicBoolean(true);
        ResultSet rows=(ResultSet)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{ResultSet.class},
                (proxy,method,args)->switch(method.getName()){
                    case "next" -> next.getAndSet(false);
                    case "getInt" -> ((Integer)args[0])==1?7:9;
                    case "getString" -> ((Integer)args[0])==3?session.toString():"owner@test";
                    case "close" -> null;
                    default -> defaultValue(method.getReturnType());
                });
        CallableStatement call=(CallableStatement)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{CallableStatement.class},
                (proxy,method,args)->method.getName().equals("executeQuery")?rows:defaultValue(method.getReturnType()));
        var calledSql=new AtomicReference<String>();
        Connection authConnection=(Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},
                (proxy,method,args)->{
                    if(method.getName().equals("prepareCall")){calledSql.set((String)args[0]);return call;}
                    if(method.getName().equals("prepareStatement"))throw new AssertionError("auth lookup must not read credential tables directly");
                    return defaultValue(method.getReturnType());
                });
        DataSource auth=(DataSource)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{DataSource.class},
                (proxy,method,args)->method.getName().equals("getConnection")?authConnection:defaultValue(method.getReturnType()));
        var runtimePrincipal=new AtomicReference<ServerPrincipal>();
        RuntimeConnectionProvider runtime=principal->{runtimePrincipal.set(principal);throw new SQLException("stop after principal derivation");};
        var store=new SqlRememberCredentialStore(auth,runtime);

        var failure=assertThrows(IllegalStateException.class,()->store.rotate(new byte[32],installation,new byte[32],UUID.randomUUID(),Instant.now()));

        assertEquals("Failed to rotate remembered sign-in",failure.getMessage());
        assertEquals("{call dbo.ResolveDesktopRememberCredentialV2(?,?,?)}",calledSql.get(),"shale_app must execute only the narrow resolver module");
        assertEquals(7,runtimePrincipal.get().shaleClientId());assertEquals(9,runtimePrincipal.get().userId());
        assertEquals("owner@test",runtimePrincipal.get().email());
    }

    @Test void invalidOrAmbiguousResolverRowsNeverInitializeRuntimeAuthority() {
        UUID installation=UUID.randomUUID();
        for(Object[] row:List.of(new Object[]{null,9,UUID.randomUUID().toString()},
                new Object[]{0,9,UUID.randomUUID().toString()},new Object[]{7,null,UUID.randomUUID().toString()},
                new Object[]{7,-1,UUID.randomUUID().toString()},new Object[]{7,9,null},new Object[]{7,9,"invalid"})) {
            var store=new SqlRememberCredentialStore(lookupRows(List.<Object[]>of(row)),p->{throw new AssertionError("invalid candidate must not open runtime");});
            assertTrue(store.rotate(new byte[32],installation,new byte[32],UUID.randomUUID(),Instant.now()).isEmpty());
        }
        Object[] valid={7,9,UUID.randomUUID().toString()};
        var store=new SqlRememberCredentialStore(lookupRows(List.of(valid,valid)),p->{throw new AssertionError("ambiguous candidate must not open runtime");});
        assertTrue(store.rotate(new byte[32],installation,new byte[32],UUID.randomUUID(),Instant.now()).isEmpty());
    }

    @Test void malformedLookupInputsNeverBorrowAuthOrRuntime() {
        DataSource denied=(DataSource)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{DataSource.class},
                (p,m,a)->{throw new AssertionError("invalid hash must not borrow auth");});
        var store=new SqlRememberCredentialStore(denied,p->{throw new AssertionError("invalid hash must not borrow runtime");});
        for(byte[] hash:Arrays.asList(null,new byte[31],new byte[33]))
            assertTrue(store.rotate(hash,UUID.randomUUID(),new byte[32],UUID.randomUUID(),Instant.now()).isEmpty());
    }

    private DataSource lookupRows(List<Object[]> values) {
        int[] index={-1};boolean[] wasNull={false};
        ResultSet rows=(ResultSet)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{ResultSet.class},(p,m,a)->{
            if(m.getName().equals("next"))return ++index[0]<values.size();
            if(m.getName().equals("wasNull"))return wasNull[0];
            if(m.getName().equals("getInt")||m.getName().equals("getString")) {
                Object value=((Integer)a[0])==4?"owner@test":values.get(index[0])[(Integer)a[0]-1];wasNull[0]=value==null;
                return m.getName().equals("getInt")&&value==null?0:value;
            }
            return defaultValue(m.getReturnType());
        });
        CallableStatement call=(CallableStatement)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{CallableStatement.class},
                (p,m,a)->m.getName().equals("executeQuery")?rows:defaultValue(m.getReturnType()));
        Connection connection=(Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},
                (p,m,a)->m.getName().equals("prepareCall")?call:defaultValue(m.getReturnType()));
        return (DataSource)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{DataSource.class},
                (p,m,a)->m.getName().equals("getConnection")?connection:defaultValue(m.getReturnType()));
    }

    private static Object defaultValue(Class<?> type){
        if(!type.isPrimitive())return null;
        if(type==boolean.class)return false;if(type==byte.class)return (byte)0;if(type==short.class)return (short)0;
        if(type==int.class)return 0;if(type==long.class)return 0L;if(type==float.class)return 0F;
        if(type==double.class)return 0D;if(type==char.class)return '\0';return null;
    }
}
