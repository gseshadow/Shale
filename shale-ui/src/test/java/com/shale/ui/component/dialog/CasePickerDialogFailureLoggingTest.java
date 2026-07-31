package com.shale.ui.component.dialog;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

final class CasePickerDialogFailureLoggingTest {
    @Test
    void wrappedFailureLogsOriginalStackAndEverySqlServerChainEntry() {
        Logger logger = (Logger) LoggerFactory.getLogger(CasePickerDialog.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            SQLException root = new SQLException("selector column failure", "S0001", 207);
            root.setNextException(new SQLException("secondary server detail", "01000", 8153));
            CompletionException wrapped = new CompletionException(root);

            CasePickerDialog.logLoadFailure(2, 19, wrapped);

            assertSame(root, CasePickerDialog.unwrapAsyncFailure(wrapped));
            assertEquals(2, appender.list.size());
            ILoggingEvent failure = appender.list.getFirst();
            assertEquals(Level.ERROR, failure.getLevel());
            assertTrue(failure.getFormattedMessage().contains("PERF DAO failed operation=calendar-case-selector"));
            assertTrue(failure.getFormattedMessage().contains("generation=2"));
            assertTrue(failure.getFormattedMessage().contains("exceptionClass=java.sql.SQLException"));
            assertNotNull(failure.getThrowableProxy(), "the wrapper and original cause stack trace are retained");
            assertEquals(CompletionException.class.getName(), failure.getThrowableProxy().getClassName());
            ILoggingEvent chained = appender.list.get(1);
            assertTrue(chained.getFormattedMessage().contains("chainIndex=1"));
            assertTrue(chained.getFormattedMessage().contains("sqlState=01000"));
            assertTrue(chained.getFormattedMessage().contains("vendorCode=8153"));
            assertNotNull(chained.getThrowableProxy());
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void performanceContextContainsNoSelectorInputsOrPhiFields() {
        Logger logger = (Logger) LoggerFactory.getLogger(CasePickerDialog.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            CasePickerDialog.logLoadFailure(1, 7, new IllegalStateException("database unavailable"));
            String message = appender.list.getFirst().getFormattedMessage();
            assertFalse(message.contains("caseId"));
            assertFalse(message.contains("caseName"));
            assertFalse(message.contains("search"));
            assertFalse(message.contains("tenantName"));
            assertFalse(message.contains("sqlParams"));
            assertFalse(message.contains("connectionString"));
        } finally {
            logger.detachAppender(appender);
        }
    }
}
