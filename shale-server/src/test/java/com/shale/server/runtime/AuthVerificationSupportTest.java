package com.shale.server.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static com.shale.server.runtime.AuthVerificationSupport.Phase.*;
import static com.shale.server.runtime.AuthVerificationSupport.Step.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Security boundary regressions: failure attribution, value-free diagnostics and exact cleanup identity. */
class AuthVerificationSupportTest {
    @Test void originalRotationAndIndependentCleanupFailureBothSurviveWithoutSensitiveCauses() {
        var bytes=new ByteArrayOutputStream();
        var support=new AuthVerificationSupport(new PrintStream(bytes));
        var failure=assertThrows(AssertionError.class,()->support.verify(()->{
            support.at(REMEMBERED_ROTATION,JTI_IDENTITY);
            throw new AssertionError("private assertion values");
        },()->{
            support.step(MARKER_IDENTITY);
            throw new IllegalStateException("private marker");
        }));
        assertEquals(REMEMBERED_ROTATION,support.primaryFailure().phase());
        assertEquals(JTI_IDENTITY,support.primaryFailure().step());
        assertEquals(CLEANUP,support.cleanupFailure().phase());
        assertEquals(MARKER_IDENTITY,support.cleanupFailure().step());
        assertEquals("AUTH_VERIFY FAIL remembered_rotation\n"
                +"AUTH_VERIFY DETAIL remembered_rotation jti_identity assertion\n"
                +"AUTH_VERIFY FAIL cleanup\nAUTH_VERIFY DETAIL cleanup marker_identity guard\n",
                bytes.toString(StandardCharsets.UTF_8).replace("\r\n","\n"));
        assertTrue(failure.getMessage().contains("primary=remembered_rotation jti_identity assertion"));
        assertTrue(failure.getMessage().contains("cleanup=cleanup marker_identity guard"));
        assertFalse(failure.getMessage().contains("private"));
        assertNull(failure.getCause());assertEquals(0,failure.getSuppressed().length);
    }

    @Test void cleanupOnlyAndPrimaryOnlyFailuresRemainFailuresAndCleanupAlwaysRuns() {
        for(boolean failBody:List.of(false,true)) {
            var support=new AuthVerificationSupport(new PrintStream(new ByteArrayOutputStream()));
            boolean[] cleaned={false};
            assertThrows(AssertionError.class,()->support.verify(()->{
                support.at(REMEMBERED_ROTATION,ROTATE);
                if(failBody)throw new SQLException("private", "23000", 2627);
            },()->{cleaned[0]=true;if(!failBody)throw new SQLTimeoutException("private");}));
            assertTrue(cleaned[0]);
            assertEquals(failBody,support.primaryFailure()!=null);
            assertEquals(!failBody,support.cleanupFailure()!=null);
        }
        var bytes=new ByteArrayOutputStream();
        var support=new AuthVerificationSupport(new PrintStream(bytes));
        assertDoesNotThrow(()->support.verify(()->{},()->{}));assertEquals(0,bytes.size());
    }

    @Test void closedCategoriesClassifyWrappedSqlWithoutReadingMessages() {
        for(Object[] test:List.of(
                new Object[]{new SQLException("private", "42000",229),AuthVerificationSupport.Category.SQL_PERMISSION},
                new Object[]{new SQLException("private", "23000",2627),AuthVerificationSupport.Category.SQL_CONSTRAINT},
                new Object[]{new SQLTimeoutException("private"),AuthVerificationSupport.Category.SQL_TIMEOUT},
                new Object[]{new SQLException("private", "08001"),AuthVerificationSupport.Category.SQL_CONNECTION},
                new Object[]{new SQLException("private"),AuthVerificationSupport.Category.SQL_OTHER})) {
            assertEquals(test[1],AuthVerificationSupport.category(new IllegalStateException("private",(Throwable)test[0])));
        }
        var unreadable=new RuntimeException(){@Override public String getMessage(){throw new AssertionError("must not inspect message");}};
        assertEquals(AuthVerificationSupport.Category.UNEXPECTED,AuthVerificationSupport.category(unreadable));
    }

    @Test void cleanupUuidIdentityAcceptsCaseOnlyAndStillRequiresExactlyOneCanonicalMatchingRow() throws Exception {
        UUID expected=UUID.fromString("abcdef12-abcd-4567-89ab-abcdef123456");
        for(String value:List.of(expected.toString(),expected.toString().toUpperCase(Locale.ROOT)))
            assertTrue(AuthVerificationSupport.exactlyOneUuid(rows(Collections.singletonList(value)),expected));
        for(List<String> values:List.of(Collections.<String>emptyList(),Collections.<String>singletonList(null),
                List.of(UUID.randomUUID().toString()),List.of("invalid"),List.of("1-1-1-1-1"),
                List.of(expected.toString(),expected.toString()),List.of(expected.toString(),UUID.randomUUID().toString())))
            assertFalse(AuthVerificationSupport.exactlyOneUuid(rows(values),expected),"missing, different, malformed or duplicate markers must retain the database");
        assertFalse(AuthVerificationSupport.sameUuid(null,expected.toString()));
    }
    @Test void recreatedOrMissingLoginIdentityMustNeverAuthorizeDrop() {
        assertTrue(AuthVerificationSupport.sameSid(new byte[]{1,2},new byte[]{1,2}));
        assertFalse(AuthVerificationSupport.sameSid(new byte[]{1,2},new byte[]{1,3}));
        assertFalse(AuthVerificationSupport.sameSid(null,new byte[]{1}));
        assertFalse(AuthVerificationSupport.sameSid(new byte[]{1},null));
        assertFalse(AuthVerificationSupport.sameSid(new byte[0],new byte[0]));
    }
    private ResultSet rows(List<String> values) {
        int[] index={-1};
        return (ResultSet)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{ResultSet.class},(p,m,a)->switch(m.getName()){
            case "next" -> ++index[0]<values.size();
            case "getString" -> values.get(index[0]);
            default -> throw new AssertionError("unexpected JDBC operation");
        });
    }
}
