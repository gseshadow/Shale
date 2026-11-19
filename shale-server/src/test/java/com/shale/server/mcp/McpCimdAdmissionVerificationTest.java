package com.shale.server.mcp;
import com.shale.data.security.ai.*;

import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.concurrent.atomic.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Scripted transport only: fresh SQL credential resolution, never a claim of native catalog validity. */
class McpCimdAdmissionVerificationTest {
    @Test void externalAdmissionUsesLiveCredentialResolutionWithoutRepeatingCatalogInspection()throws Exception {
        var queries=new AtomicInteger();var drift=new AtomicBoolean();
        var caller=source(false,queries,drift);var reviewer=source(true,queries,drift);
        var predecessor=mock(AiCaseDataAttestation.class);
        try(var access=mockStatic(AiCaseDataAccess.class);
            var data=new AiCimdCaseData(d->predecessor,d->caller,d->reviewer,"db","https://shale.test/oauth/mcp","https://shale.test/mcp",AiExternalContract.DCR_V3)) {
            int startupQueries=queries.get();assertTrue(startupQueries>0);verify(predecessor).verify();
            long deadline=System.nanoTime()+10_000_000_000L;String token="a".repeat(43);var proof=mock(AiCaseDataRequest.class);
            access.when(()->AiCaseDataAccess.authenticate(caller,token,"https://shale.test/oauth/mcp","https://shale.test/mcp",deadline,AiCaseDataAccess.Namespace.EXTERNAL_V2))
                .thenReturn(proof).thenThrow(new SecurityException("Synthetic expired/revoked token"));
            assertSame(proof,data.authenticate(token,deadline));
            assertThrows(SecurityException.class,()->data.authenticate(token,deadline));
            assertEquals(startupQueries,queries.get(),"An RPC must not inspect system catalogs");
            verify(predecessor,times(1)).verify();
            access.verify(()->AiCaseDataAccess.authenticate(caller,token,"https://shale.test/oauth/mcp","https://shale.test/mcp",deadline,AiCaseDataAccess.Namespace.EXTERNAL_V2),times(2));
            data.requireInstallationReady();
            drift.set(true);assertThrows(IllegalStateException.class,()->data.ready(deadline));
            drift.set(false);assertThrows(AiInstallationReadiness.Unavailable.class,()->data.authenticate(token,deadline));
            assertThrows(IllegalStateException.class,()->data.ready(deadline),"Repair must not revive a withdrawn instance");
        }
    }
    private static DataSource source(boolean review,AtomicInteger queries,AtomicBoolean drift)throws Exception {
        var data=mock(DataSource.class);var connection=mock(Connection.class);
        when(data.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenAnswer(call->{
            var statement=mock(Statement.class);
            when(statement.getUpdateCount()).thenReturn(-1);
            when(statement.execute(anyString())).thenAnswer(query->{
                queries.incrementAndGet();if(drift.get()&&((String)query.getArgument(0)).length()>10000)throw new SQLException("PRIVATE_SENTINEL","private",59804);return false;
            });
            when(statement.executeQuery(anyString())).thenAnswer(query->{
                queries.incrementAndGet();String sql=query.getArgument(0);
                if(sql.contains("EngineEdition"))return rows(new Object[]{5});
                if(sql.startsWith("SELECT DB_NAME"))return rows(new Object[]{"db",review?"reviewer":"caller",10,0,0,null,null});
                if(sql.contains("sys.user_token"))return rows(new Object[]{review?"reviewer":"caller"},new Object[]{"public"},new Object[]{review?"AiCimdAuthorizationCatalogV1":"AiExternalCaseRuntimeV2"});
                if(sql.contains("ServerName"))return rows(new Object[]{"synthetic-server"});
                if(sql.startsWith("SELECT sid"))return rows(new Object[]{new byte[]{(byte)(review?2:1)}});
                throw new AssertionError("Unexpected metadata query in scripted verifier");
            });return statement;
        });return data;
    }
    private static ResultSet rows(Object[]... values) {
        int[] position={-1};boolean[] wasNull={false};
        return (ResultSet)Proxy.newProxyInstance(ResultSet.class.getClassLoader(),new Class<?>[]{ResultSet.class},(proxy,method,args)->{
            if(method.getName().equals("next"))return ++position[0]<values.length;
            if(method.getName().equals("wasNull"))return wasNull[0];
            if(method.getName().startsWith("get")&&args!=null&&args[0] instanceof Integer index) {
                Object value=values[position[0]][index-1];wasNull[0]=value==null;
                return switch(method.getName()){case "getInt"->value==null?0:((Number)value).intValue();case "getString"->value==null?null:value.toString();default->value;};
            }
            return null;
        });
    }
}
