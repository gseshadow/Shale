package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import com.microsoft.sqlserver.jdbc.SQLServerException;
import com.shale.data.runtime.RuntimeSessionService;
import java.sql.SQLException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Opt-in, read-only execution of the production batch through the real Microsoft driver and SQL Server. */
class SuggestionDaoSqlServerTest {
    private static SQLServerDataSource source;
    private static RuntimeSessionService session;
    private static int tenant;

    @BeforeAll static void authorizedTestConnection() {
        String url = System.getenv("SHALE_SUGGESTION_TEST_JDBC_URL");
        assumeTrue(url != null && !url.isBlank(),
                "Runtime SQL verification requires an explicitly configured SQL Server test connection");
        source = new SQLServerDataSource();
        source.setURL(url);
        String username = System.getenv("SHALE_SUGGESTION_TEST_USERNAME");
        String password = System.getenv("SHALE_SUGGESTION_TEST_PASSWORD");
        if (username != null) source.setUser(username);
        if (password != null) source.setPassword(password);
        // Explicit setNString must work even when the driver default for setString is non-Unicode.
        source.setSendStringParametersAsUnicode(false);
        source.setLoginTimeout(10);
        tenant = Integer.parseInt(System.getenv("SHALE_SUGGESTION_TEST_TENANT_ID"));
        int user = Integer.parseInt(System.getenv("SHALE_SUGGESTION_TEST_USER_ID"));
        assertTrue(tenant > 0 && user > 0, "Use an authorized test tenant and principal");
        session = new RuntimeSessionService(source);
        session.initialize(tenant, user);
    }

    @Test void oldBoundedDeclarationFailsForOrdinaryShortInputThroughMicrosoftDriver() throws SQLException {
        try (var connection = source.getConnection()) {
            for (String variable : new String[]{"prefix", "contains"}) {
                try (var statement = connection.prepareStatement(
                        "DECLARE @" + variable + " nvarchar(4096)=?; SELECT @" + variable + ";")) {
                    statement.setNString(1, "a");
                    statement.setQueryTimeout(5);
                    var error = assertThrows(SQLServerException.class, statement::execute);
                    assertEquals(2717, error.getErrorCode(),
                            "The invalid declaration must fail independently of input length");
                }
            }
        }
    }

    @Test void productionBatchExecutesForShortUnicodeAndMaximumEscapedPattern() {
        var dao = new SuggestionDao(() -> {
            try { return session.getConnection(); }
            catch (SQLException ex) { throw new IllegalStateException("Test connection unavailable", ex); }
        });
        for (String input : new String[]{"résumé漢字%_[", "漢" + "%".repeat(1332) + "x"}) {
            var result = dao.search(tenant, input, true, 3, 18, null, null, () -> true, ignored -> { });
            assertFalse(result.failed(), "Every category must execute successfully on SQL Server");
            assertTrue(result.rows().size() <= 18, "The production total bound must remain enforced");
            for (var category : SuggestionDao.Category.values()) {
                assertTrue(result.rows().stream().filter(row -> row.category() == category).count() <= 3,
                        "The production per-category bound must remain enforced");
            }
        }
    }
}
