package com.shale.data.config;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DataSourcesAiCaseDataTest {
    @Test void missingAiConfigurationHasNoRuntimeOrBootstrapFallbackAndOffModeNeedsNone() {
        configured(false, () -> {
            try (var sources = new DataSources(new Config())) {
                assertNotNull(sources.auth());
                assertThrows(IllegalStateException.class, sources::aiCaseData);
                assertNotNull(sources.runtime());
            }
        });
    }
    @Test void poolRotationAndLifecycleAreIndependent() {
        configured(true, () -> {
            var sources = new DataSources(new Config());
            try {
                var ai = (HikariDataSource) sources.aiCaseData();
                var runtime = (HikariDataSource) sources.runtime();
                assertNotSame(runtime, ai); assertSame(ai, sources.aiCaseData());
                assertEquals("ai-case-data-pool", ai.getPoolName());
                assertEquals("synthetic-ai", ai.getUsername());
                assertEquals("true", ai.getDataSourceProperties().get("encrypt"));
                assertEquals("false", ai.getDataSourceProperties().get("trustServerCertificate"));
                sources.resetRuntime(); assertFalse(ai.isClosed());
                sources.resetAiCaseData(); assertTrue(ai.isClosed());
                var next = (HikariDataSource) sources.aiCaseData(); assertNotSame(ai, next);
                sources.close(); assertTrue(next.isClosed());
                assertThrows(IllegalStateException.class, sources::aiCaseData);
            } finally { sources.close(); }
        });
    }
    private static synchronized void configured(boolean ai, Runnable work) {
        var keys = List.of("SHALE_APP_DB_URL", "SHALE_APP_DB_USER", "SHALE_APP_DB_PASSWORD", "SHALE_RT_DB_URL", "SHALE_RT_DB_USER", "SHALE_RT_DB_PASSWORD", "shale.ai.data.jdbc-url", "shale.ai.data.username", "shale.ai.data.password");
        var before = new HashMap<String,String>(); keys.forEach(k -> before.put(k, System.getProperty(k)));
        try {
            for (String pool : List.of("APP", "RT")) {
                System.setProperty("SHALE_" + pool + "_DB_URL", "jdbc:sqlserver://127.0.0.1:1;databaseName=synthetic");
                System.setProperty("SHALE_" + pool + "_DB_USER", "synthetic-" + pool);
                System.setProperty("SHALE_" + pool + "_DB_PASSWORD", "synthetic");
            }
            for (String key : List.of("shale.ai.data.jdbc-url", "shale.ai.data.username", "shale.ai.data.password")) System.clearProperty(key);
            if (ai) {
                System.setProperty("shale.ai.data.jdbc-url", "jdbc:sqlserver://127.0.0.1:1;databaseName=synthetic");
                System.setProperty("shale.ai.data.username", "synthetic-ai");
                System.setProperty("shale.ai.data.password", "synthetic");
            }
            work.run();
        } finally { before.forEach((k,v) -> { if (v == null) System.clearProperty(k); else System.setProperty(k,v); }); }
    }
}
