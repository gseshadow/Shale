package com.shale.server.mcp;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import static org.junit.jupiter.api.Assertions.*;

class McpAdmissionDiagnosticsTest {
    @Test void disabledDiagnosticsDoNotReadTheClockOrLog() {
        capture(events -> {
            var attempt=new McpAdmissionDiagnostics(false,()->{throw new AssertionError("Disabled diagnostics changed deadline clock");}).begin("server/discover");
            attempt.accepted();attempt.rejected(new SecurityException("SENSITIVE"));assertTrue(events.list.isEmpty());
        });
    }
    @Test void emitsOnlyClosedPhasesTimesAndFailureCodes() {
        capture(events -> {
            var clock=new AtomicLong(1000000);var attempt=new McpAdmissionDiagnostics(true,clock::get).begin("tools/list");
            clock.addAndGet(123000000);attempt.accepted();
            attempt.rejected(new IllegalStateException("SENSITIVE_REQUEST_URL",new SQLTimeoutException("SENSITIVE_SQL","HY008",-2)));
            assertEquals(3,events.list.size());
            assertTrue(events.list.get(1).getFormattedMessage().contains("result=ACCEPTED method=TOOLS_LIST elapsedMillis=123"));
            assertTrue(events.list.get(2).getFormattedMessage().contains("reason=SQL_TIMEOUT verifierStage=NONE sqlCode=-2"));
            for(var event:events.list){assertFalse(event.getFormattedMessage().contains("SENSITIVE"));assertNull(event.getThrowableProxy());}
        });
    }
    @Test void rejectsMessageSpoofingAndUntrustedMethodValues() {
        capture(events -> {
            var attempt=new McpAdmissionDiagnostics(true,()->0).begin("SENSITIVE_METHOD\nforged");
            attempt.rejected(new IllegalStateException("CIMD verification failed closed; stage=CATALOG; sqlCode=59804"));
            String line=events.list.getLast().getFormattedMessage();
            assertTrue(line.contains("method=OTHER"));assertTrue(line.contains("verifierStage=NONE sqlCode=0"));assertFalse(line.contains("SENSITIVE"));
        });
    }
    @Test void recognizesClosedVerifierStageWhenItsOriginalStackMatches() {
        capture(events -> {
            var failure=new IllegalStateException("CIMD verification failed closed; stage=CATALOG; sqlCode=59804");
            failure.setStackTrace(new StackTraceElement[]{new StackTraceElement("com.shale.data.security.ai.AiCimdCaseData","ready","synthetic",1)});
            new McpAdmissionDiagnostics(true,()->0).begin(null).rejected(failure);
            assertTrue(events.list.getLast().getFormattedMessage().contains("method=NO_METHOD elapsedMillis=0 reason=VERIFIER verifierStage=CATALOG sqlCode=59804"));
        });
    }
    private void capture(java.util.function.Consumer<ListAppender<ILoggingEvent>> body) {
        var logger=(Logger)LoggerFactory.getLogger(McpAdmissionDiagnostics.class);var original=logger.getLevel();
        var appender=new ListAppender<ILoggingEvent>();appender.start();logger.addAppender(appender);logger.setLevel(Level.WARN);
        try {body.accept(appender);}finally{logger.detachAppender(appender);logger.setLevel(original);appender.stop();}
    }
}
