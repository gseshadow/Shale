package com.shale.data.security.ai;

import org.junit.jupiter.api.Test;
import java.io.Serializable;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class ProofDigestTest {
    @Test void defensiveProofHasOnlyScopedJdbcExposureAndClearsEveryBinding() throws Exception {
        byte[] original = new byte[32]; Arrays.fill(original, (byte) 17);
        var proof = new ProofDigest(original); Arrays.fill(original, (byte) 99);
        var bound = new AtomicReference<byte[]>(); var clears = new AtomicInteger();
        var statement = statement(bound, clears, false);
        assertEquals(7, proof.withBinding(statement, 1, () -> {
            assertEquals(17, bound.get()[0]); return 7;
        }));
        assertArrayEquals(new byte[32], bound.get()); assertEquals(1, clears.get());
        assertThrows(SQLException.class, () -> proof.withBinding(statement, 1, () -> { throw new SQLException("synthetic"); }));
        assertArrayEquals(new byte[32], bound.get()); assertEquals(2, clears.get());
        // Cancellation does not close the retained proof before bounded accounting/release.
        Thread.currentThread().interrupt();
        try { proof.withBinding(statement, 1, () -> { assertEquals(17, bound.get()[0]); return null; }); }
        finally { Thread.interrupted(); }
        proof.close(); proof.close();
        assertThrows(IllegalStateException.class, () -> proof.withBinding(statement, 1, () -> null));
        assertEquals("ProofDigest[redacted]", proof.toString());
        assertFalse(Serializable.class.isAssignableFrom(ProofDigest.class));
        assertTrue(Arrays.stream(ProofDigest.class.getDeclaredMethods()).noneMatch(m -> m.getReturnType() == byte[].class));
        assertTrue(Arrays.stream(ProofDigest.class.getDeclaredMethods()).noneMatch(m -> Set.of("equals", "hashCode").contains(m.getName())));
    }
    @Test void transportCancellationCannotClearProofBeforeInvocationCleanupCompletes()throws Exception {
        var proof=new ProofDigest(new byte[32]);
        var request=new AiCaseDataRequest(IntegrationCaseReadTest.context(7,72),proof);
        var use=request.retain();request.close();
        var bound=new AtomicReference<byte[]>();var clears=new AtomicInteger();
        proof.withBinding(statement(bound,clears,false),1,()->null);
        use.close();use.close();
        assertThrows(IllegalStateException.class,()->proof.withBinding(statement(bound,clears,false),1,()->null));
        assertThrows(SecurityException.class,request::retain);
    }
    @Test void rejectedProofAndFailedBindingDoNotLeakOrSkipParameterCleanup() {
        for (int length : List.of(0, 31, 33, 64)) assertThrows(IllegalArgumentException.class, () -> new ProofDigest(new byte[length]));
        assertThrows(IllegalArgumentException.class, () -> new ProofDigest(null));
        var bound = new AtomicReference<byte[]>(); var clears = new AtomicInteger();
        try (var proof = new ProofDigest(new byte[32])) {
            assertThrows(SQLException.class, () -> proof.withBinding(statement(bound, clears, true), 1, () -> fail("Binding failed")));
            assertArrayEquals(new byte[32], bound.get()); assertEquals(1, clears.get());
        }
    }
    private static PreparedStatement statement(AtomicReference<byte[]> bound, AtomicInteger clears, boolean fail) {
        return (PreparedStatement) Proxy.newProxyInstance(ProofDigestTest.class.getClassLoader(), new Class<?>[]{PreparedStatement.class}, (p,m,a) -> {
            if (m.getName().equals("setBytes")) { bound.set((byte[]) a[1]); if (fail) throw new SQLException("synthetic"); }
            if (m.getName().equals("clearParameters")) clears.incrementAndGet();
            return null;
        });
    }
}
