package com.shale.server.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class GlobalControlPlaneDbSessionProviderTest {
    @Test
    void opensPlainConnectionWithoutTenantPrincipalOrSessionState() {
        Connection connection = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[] {Connection.class}, (proxy, method, args) -> null);
        AtomicInteger opens = new AtomicInteger();
        GlobalControlPlaneDbSessionProvider provider = new GlobalControlPlaneDbSessionProvider(() -> {
            opens.incrementAndGet();
            return connection;
        });

        assertSame(connection, provider.requireConnection(),
                "Global control-plane access must return the ordinary runtime connection directly.");
        assertEquals(1, opens.get(),
                "Global control-plane access must not detour through tenant session resolution.");
    }
}
