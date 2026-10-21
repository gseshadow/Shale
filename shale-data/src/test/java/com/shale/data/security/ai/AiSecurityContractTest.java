package com.shale.data.security.ai;

import com.shale.core.security.ai.AiScope;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.util.*;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class AiSecurityContractTest {
    @Test void startupDefaultsOffBeforeAnyDatabaseAccess() {
        var service = new AiIntegrationSecurityService(() -> { throw new AssertionError("Default off must not open SQL."); });
        assertThrows(SecurityException.class, () -> service.verifySession(UUID.randomUUID(), AiScope.CASES_SEARCH));
        assertThrows(SecurityException.class, () -> service.beginConsent(UUID.randomUUID(),AiScope.fromMask(1),1));
    }
    @Test void contextConstructionIsInternalImmutableAndLogSafe() {
        assertTrue(Modifier.isFinal(VerifiedIntegrationContext.class.getModifiers()));
        for (var c : VerifiedIntegrationContext.class.getDeclaredConstructors()) assertFalse(Modifier.isPublic(c.getModifiers()));
        for (var f : VerifiedIntegrationContext.class.getDeclaredFields()) assertTrue(Modifier.isFinal(f.getModifiers()));
        var context = new VerifiedIntegrationContext(7,72,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),AiScope.fromMask(1),AiScope.CASES_SEARCH);
        assertThrows(UnsupportedOperationException.class, () -> context.effectiveScopes().clear());
        assertEquals("VerifiedIntegrationContext[redacted]",context.toString());
        assertThrows(SecurityException.class, () -> new VerifiedIntegrationContext(7,72,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),AiScope.fromMask(1),AiScope.CASES_OVERVIEW_READ));
    }
    @Test void rowVersionCannotBeChangedAfterValidationAndNonceNeverLogs() {
        byte[] bytes = new byte[8];
        var version = new AiIntegrationSecurityService.RowVersion(bytes);
        bytes[0]=1; version.bytes()[0]=2;
        assertEquals(0,version.bytes()[0]);
        assertEquals("ConsentIntent[redacted]",new AiIntegrationSecurityService.ConsentIntent("secret-sentinel").toString());
    }
    @Test void newFoundationHasNoTransportOrCaseDependency() throws Exception {
        Path source=Path.of("src/main/java/com/shale/data/security/ai");
        for (Path file : Files.list(source).toList()) {
            String text=Files.readString(file);
            assertFalse(text.contains("com.shale.data.dao.Case"),file.toString());
            assertFalse(text.contains("@RequestMapping"),file.toString());
            assertFalse(text.contains("org.springframework.web"),file.toString());
        }
    }

    @Test void resultIsWithheldWhenProcedureCompletionOrLeaseCloseFails() {
        for (boolean closeFailure : new boolean[]{false,true}) {
            AtomicInteger row=new AtomicInteger();
            ResultSet rows=proxy(ResultSet.class,(name,args)->switch(name) {
                case "next" -> row.getAndIncrement()==0;
                case "getInt" -> 42;
                default -> null;
            });
            CallableStatement statement=proxy(CallableStatement.class,(name,args)->switch(name) {
                case "execute" -> true;
                case "getResultSet" -> rows;
                case "getMoreResults" -> { if(!closeFailure)throw new SQLException("Injected lost commit acknowledgement","08007"); yield false; }
                case "getUpdateCount" -> -1;
                default -> null;
            });
            Connection connection=proxy(Connection.class,(name,args)->switch(name) {
                case "getAutoCommit" -> true;
                case "prepareCall" -> statement;
                case "close" -> { if(closeFailure)throw new SQLException("Injected close failure"); yield null; }
                default -> null;
            });
            assertThrows(IllegalStateException.class,()->AiSql.call(()->connection,"AiVerifySessionV1",r->r.getInt(1)));
        }
    }
    private interface Behavior { Object apply(String method,Object[] args) throws Throwable; }
    private static <T>T proxy(Class<T> type,Behavior behavior) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(object,method,args)->behavior.apply(method.getName(),args)));
    }
}
