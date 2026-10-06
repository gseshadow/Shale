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

    private static Object defaultValue(Class<?> type){
        if(!type.isPrimitive())return null;
        if(type==boolean.class)return false;if(type==byte.class)return (byte)0;if(type==short.class)return (short)0;
        if(type==int.class)return 0;if(type==long.class)return 0L;if(type==float.class)return 0F;
        if(type==double.class)return 0D;if(type==char.class)return '\0';return null;
    }
}
