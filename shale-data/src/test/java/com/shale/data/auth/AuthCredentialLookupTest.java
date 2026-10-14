package com.shale.data.auth;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import com.shale.data.errors.AuthException;

/** Synthetic JDBC tests protect the shared server/desktop credential contract; not SQL/RLS evidence. */
class AuthCredentialLookupTest {
    @Test void boundedModuleKeepsJavaBcryptAndSharedUserProjection() throws Exception {
        var f=new Fixture(List.of(valid()));
        var service=new AuthServiceImpl(f.source(),new BCryptPasswordVerifier());
        var user=service.login("owner@test", "synthetic-password");
        assertEquals(9,user.getId());assertEquals(7,user.getShaleClientId());
        assertEquals("owner@test",user.getEmail());assertTrue(user.isAdmin());
        assertEquals("{call dbo.LookupAuthenticationCredentialV1(?)}",f.sql);
        assertEquals(Map.of(1,"owner@test"),f.bindings,"only the exact identity, never password or tenant, is bound");
        assertEquals(5,f.timeout);assertTrue(f.closed);
        assertThrows(AuthException.class,()->service.login("owner@test","wrong"));
    }
    @Test void unavailableAmbiguousOrInvalidIdentityNeverReachesPasswordVerification() {
        for(String field:List.of("Id","ShaleClientId","is_deleted","IsRemoved")) {
            var row=valid();row.put(field,field.startsWith("is_")||field.equals("IsRemoved")?true:null);
            var f=new Fixture(List.of(row));
            var service=new AuthServiceImpl(f.source(),(p,h)->{throw new AssertionError("invalid identity must be rejected before BCrypt");});
            assertEquals("Invalid credentials.",assertThrows(AuthException.class,()->service.login("owner@test","password")).getMessage());
        }
        for(var rows:List.of(List.<Map<String,Object>>of(),List.of(valid(),valid()))) {
            var service=new AuthServiceImpl(new Fixture(rows).source(),(p,h)->{throw new AssertionError("missing/ambiguous identity must not be verified");});
            assertThrows(AuthException.class,()->service.login("owner@test","password"));
        }
    }
    @Test void nonpositiveTenantOrUserCannotBecomeTrustedRuntimeIdentity() {
        for(String field:List.of("Id","ShaleClientId"))for(int value:List.of(0,-1)) {
            var row=valid();row.put(field,value);
            assertThrows(AuthException.class,()->new AuthServiceImpl(new Fixture(List.of(row)).source(),(p,h)->true).login("owner@test","password"));
        }
    }
    @Test void absentAndMalformedHashFailSafely() {
        for(String hash:Arrays.asList(null,"malformed")) {
            var row=valid();row.put("pw_hash",hash);
            assertThrows(AuthException.class,()->new AuthServiceImpl(new Fixture(List.of(row)).source(),new BCryptPasswordVerifier()).login("owner@test","password"));
        }
    }
    @Test void invalidInputDoesNotBorrowAnAuthConnection() {
        DataSource denied=proxy(DataSource.class,(m,a)->{throw new AssertionError("invalid input must not borrow");});
        var service=new AuthServiceImpl(denied,(p,h)->true);
        for(String email:Arrays.asList(null," ","x".repeat(256)))assertThrows(AuthException.class,()->service.login(email,"password"));
        assertThrows(AuthException.class,()->service.login("owner@test",null));
    }
    @Test void databaseErrorsDoNotRetainDriverSecretsOrPrintDiagnostics() {
        var f=new Fixture(List.of());f.failure=new SQLException("synthetic-sensitive-driver-value","S0001",229);
        var error=assertThrows(AuthException.class,()->new AuthServiceImpl(f.source(),(p,h)->true).login("owner@test","password"));
        assertEquals("Authentication is temporarily unavailable.",error.getMessage());assertNull(error.getCause());
        assertTrue(f.closed,"failed module call must release auth resources");
    }
    private static Map<String,Object> valid() {
        var row=new HashMap<String,Object>();row.put("Id",9);row.put("ShaleClientId",7);row.put("email","owner@test");
        row.put("is_deleted",false);row.put("IsRemoved",false);row.put("name_first","Synthetic");row.put("name_last","Owner");row.put("is_admin",true);
        row.put("pw_hash",org.mindrot.jbcrypt.BCrypt.hashpw("synthetic-password",org.mindrot.jbcrypt.BCrypt.gensalt(4)));
        return row;
    }
    private static final class Fixture {
        final List<Map<String,Object>> rows;final Map<Integer,Object> bindings=new HashMap<>();String sql;int timeout;boolean closed;SQLException failure;
        Fixture(List<Map<String,Object>> rows){this.rows=rows;}
        DataSource source(){return proxy(DataSource.class,(m,a)->m.equals("getConnection")?connection():null);}
        Connection connection(){return proxy(Connection.class,(m,a)->{
            if(m.equals("prepareCall")){sql=(String)a[0];return call();}
            if(m.equals("prepareStatement"))throw new AssertionError("no direct pre-tenant Users query");
            if(m.equals("close"))closed=true;return null;
        });}
        CallableStatement call(){return proxy(CallableStatement.class,(m,a)->{
            if(m.equals("setString"))bindings.put((Integer)a[0],a[1]);
            if(m.equals("setQueryTimeout"))timeout=(Integer)a[0];
            if(m.equals("executeQuery")){if(failure!=null)throw failure;return result();}return null;
        });}
        ResultSet result(){int[] index={-1};boolean[] wasNull={false};return proxy(ResultSet.class,(m,a)->{
            if(m.equals("next"))return ++index[0]<rows.size();
            if(m.equals("wasNull"))return wasNull[0];
            if(m.startsWith("get")){Object value=rows.get(index[0]).get(a[0]);wasNull[0]=value==null;
                return switch(m){case "getInt"->value==null?0:value;case "getBoolean"->value==null?false:value;default->value;};}return null;
        });}
    }
    @FunctionalInterface private interface Invocation{Object call(String method,Object[] arguments)throws Throwable;}
    @SuppressWarnings("unchecked") private static <T>T proxy(Class<T> type,Invocation invocation){return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(p,m,a)->invocation.call(m.getName(),a));}
}
