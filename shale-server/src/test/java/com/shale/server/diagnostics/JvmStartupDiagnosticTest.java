package com.shale.server.diagnostics;

import static org.junit.jupiter.api.Assertions.*;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class JvmStartupDiagnosticTest {
    @Test
    void reportsOnlyThreeAllowlistedFieldsPreservingZoneAndVersionSpelling() {
        assertEquals("SHALE_JVM_STARTUP zoneId=Etc/UTC javaVersion=21.0.13+6-LTS replicaId=abc_123-def",
                JvmStartupDiagnostic.format("Etc/UTC", "21.0.13+6-LTS", "abc_123-def"));
        for (String zone : new String[]{"America/Denver", "Asia/Kolkata", "UTC", "GMT+05:30", "-07:00"}) {
            assertTrue(JvmStartupDiagnostic.format(zone, "21.0.13", "abc").contains("zoneId=" + zone + " "),
                    "Diagnostic must preserve the exact getter result, including aliases and offsets");
        }
    }

    @Test
    void unavailableOrUnsafeFieldsNeverInjectLinesOrAdditionalFields() {
        for (String value : new String[]{null, "", " ", "abc\nforged", "abc\rforged", "abc\tforged",
                "abc javaVersion=forged", "secret=value", "\u001b[31m", "é", "a".repeat(129)}) {
            assertEquals("SHALE_JVM_STARTUP zoneId=unavailable javaVersion=unavailable replicaId=unavailable",
                    JvmStartupDiagnostic.format(value, value, value),
                    "Missing/unsafe metadata must be fully suppressed, never truncated or echoed");
        }
        assertTrue(JvmStartupDiagnostic.format("UTC", "21", "a".repeat(128)).endsWith("a".repeat(128)));
        assertTrue(JvmStartupDiagnostic.format("UTC", "21", "https://host/path").endsWith("replicaId=unavailable"));
    }

    @Test
    void emitsOneInfoEventFromCurrentJvmWithoutChangingItsZone() {
        Logger logger = (Logger) LoggerFactory.getLogger(JvmStartupDiagnostic.class);
        Level previous = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
        ZoneId before = ZoneId.systemDefault();
        try {
            var diagnostic = new JvmStartupDiagnostic();
            diagnostic.run(null);
            diagnostic.run(null);
            assertEquals(1, appender.list.size(), "Each startup runner must emit at most one diagnostic");
            ILoggingEvent event = appender.list.getFirst();
            assertEquals(Level.INFO, event.getLevel());
            assertNull(event.getThrowableProxy(), "Operational evidence must never include exceptions");
            assertEquals(JvmStartupDiagnostic.format(before.getId(), Runtime.version().toString(),
                    System.getenv("WEBSITE_INSTANCE_ID")), event.getFormattedMessage());
            assertEquals(before, ZoneId.systemDefault(), "Diagnostic must not change application time-zone behavior");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(previous);
        }
    }
}
