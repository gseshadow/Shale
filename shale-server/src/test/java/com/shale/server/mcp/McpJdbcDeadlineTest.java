package com.shale.server.mcp;

import com.shale.data.config.AcceptanceVerificationBudget;

import javax.sql.DataSource;
import java.sql.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class McpJdbcDeadlineTest {
    @Test void failureAccountingRetainsItsIndependentShortLeaseAfterRequestExpiry()throws Exception {
        var source=mock(DataSource.class);var raw=mock(Connection.class);when(source.getConnection()).thenReturn(raw);
        when(raw.getNetworkTimeout()).thenReturn(12000);
        assertThrows(SQLTimeoutException.class,()->new McpJdbcDeadline(source,System.nanoTime()-1).getConnection());
        try(var audit=McpJdbcDeadline.failureAudit(source).getConnection()) {
            audit.getAutoCommit();verify(raw,atLeastOnce()).setNetworkTimeout(any(),intThat(t->t>0&&t<=3000));
        }
        verify(raw).setNetworkTimeout(any(),eq(12000));verify(raw).close();
    }
    @Test void expiredAdmissionNeverBorrowsAndActiveLeaseClipsTimeoutsAndRestoresOnClose()throws Exception {
        var source=mock(DataSource.class);
        assertThrows(SQLTimeoutException.class,()->new McpJdbcDeadline(source,System.nanoTime()-1).getConnection());
        verifyNoInteractions(source);
        var raw=mock(Connection.class);var call=mock(CallableStatement.class);
        when(source.getConnection()).thenReturn(raw);when(raw.prepareCall(anyString())).thenReturn(call);
        when(raw.getNetworkTimeout()).thenReturn(10000);
        try(var lease=new McpJdbcDeadline(source,System.nanoTime()+2_000_000_000L).getConnection()) {
            try(var statement=lease.prepareCall("{call bounded()}")) {
                statement.setQueryTimeout(5);statement.execute();
                verify(call).setQueryTimeout(intThat(t->t>=1 && t<=2));
                verify(raw,atLeastOnce()).setNetworkTimeout(any(),intThat(t->t>0 && t<=2000));
                assertThrows(SQLException.class,()->new McpJdbcDeadline(source,System.nanoTime()+1_000_000L).getConnection("other","secret"));
            }
        }
        verify(raw).setNetworkTimeout(any(),eq(10000));verify(raw).close();
    }
    @Test void longerAcceptanceMetadataReadsStillRespectShortToolAndFailureAuditDeadlines()throws Exception {
        try(var timing=mockStatic(AcceptanceVerificationBudget.class,CALLS_REAL_METHODS)) {
            timing.when(()->AcceptanceVerificationBudget.networkMillis(10000)).thenReturn(180000);
            timing.when(()->AcceptanceVerificationBudget.querySeconds(5)).thenReturn(180);
            var source=mock(DataSource.class);var raw=mock(Connection.class);var statement=mock(PreparedStatement.class);
            when(source.getConnection()).thenReturn(raw);when(raw.prepareStatement(anyString())).thenReturn(statement);
            try(var lease=new McpJdbcDeadline(source,System.nanoTime()+600_000_000_000L).getConnection();
                var query=lease.prepareStatement("metadata")) {
                query.setQueryTimeout(180);
                verify(statement).setQueryTimeout(180);
                verify(raw,atLeastOnce()).setNetworkTimeout(any(),eq(180000));
            }
            clearInvocations(raw,statement);
            try(var lease=new McpJdbcDeadline(source,System.nanoTime()+2_000_000_000L).getConnection();
                var query=lease.prepareStatement("short-tool")) {
                query.setQueryTimeout(180);
                verify(statement).setQueryTimeout(intThat(t->t>0&&t<=2));
                verify(raw,atLeastOnce()).setNetworkTimeout(any(),intThat(t->t>0&&t<=2000));
            }
            clearInvocations(raw);
            try(var audit=McpJdbcDeadline.failureAudit(source).getConnection()) {
                audit.getAutoCommit();
                verify(raw,atLeastOnce()).setNetworkTimeout(any(),intThat(t->t>0&&t<=3000));
            }
        }
    }
    @Test void requestPoolBorrowHasItsOwnDeadlineWithoutChangingOrdinaryPoolConfiguration()throws Exception {
        var source=mock(DataSource.class);var raw=mock(Connection.class);when(source.getConnection()).thenReturn(raw);
        var config=new com.zaxxer.hikari.HikariConfig();config.setDataSource(source);config.setMaximumPoolSize(1);config.setMinimumIdle(0);
        config.setConnectionTimeout(30000);config.setInitializationFailTimeout(-1);
        when(raw.isValid(anyInt())).thenReturn(true);
        try(var pool=new com.zaxxer.hikari.HikariDataSource(config);var occupied=pool.getConnection()) {
            long started=System.nanoTime();
            assertThrows(SQLException.class,()->new McpJdbcDeadline(pool,System.nanoTime()+250_000_000L).getConnection());
            assertTrue(System.nanoTime()-started<2_000_000_000L,"MCP borrow must not inherit the ordinary 30 second pool wait");
            assertEquals(30000,pool.getConnectionTimeout());
        }
    }
}
