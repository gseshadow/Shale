package com.shale.server.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;

@ExtendWith(OutputCaptureExtension.class)
class RequestScopedDbSessionProviderTest {

    @Test
    void missingHeadersBlockBeforeOpeningRuntimeConnection() {
        RecordingRuntimeConnectionProvider runtimeConnectionProvider = new RecordingRuntimeConnectionProvider();
        RequestScopedDbSessionProvider provider = new RequestScopedDbSessionProvider(
                new DevelopmentHeaderServerSessionResolver(),
                fixedRequest(new MockHttpServletRequest()),
                runtimeConnectionProvider);

        ResponseStatusException error = assertThrows(ResponseStatusException.class, provider::requireConnection);

        assertEquals(401, error.getStatusCode().value());
        assertEquals(0, runtimeConnectionProvider.calls);
    }

    @Test
    void validHeadersOpenInitializedConnectionForResolvedPrincipal() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(DevelopmentHeaderServerSessionResolver.USER_ID_HEADER, "19");
        request.addHeader(DevelopmentHeaderServerSessionResolver.TENANT_ID_HEADER, "29");
        RecordingRuntimeConnectionProvider runtimeConnectionProvider = new RecordingRuntimeConnectionProvider();
        RequestScopedDbSessionProvider provider = new RequestScopedDbSessionProvider(
                new DevelopmentHeaderServerSessionResolver(),
                fixedRequest(request),
                runtimeConnectionProvider);

        Connection connection = provider.requireConnection();

        assertSame(runtimeConnectionProvider.connection, connection);
        assertEquals(1, runtimeConnectionProvider.calls);
        assertEquals(19, runtimeConnectionProvider.principal.userId());
        assertEquals(29, runtimeConnectionProvider.principal.shaleClientId());
    }

    @Test
    void requestScopedProviderLogsSanitizedRootCauseWhenRuntimeConnectionFails(CapturedOutput output) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(DevelopmentHeaderServerSessionResolver.USER_ID_HEADER, "19");
        request.addHeader(DevelopmentHeaderServerSessionResolver.TENANT_ID_HEADER, "29");
        RequestScopedDbSessionProvider provider = new RequestScopedDbSessionProvider(
                new DevelopmentHeaderServerSessionResolver(),
                fixedRequest(request),
                principal -> {
                    SQLException root = new SQLException(
                            "Login failed for jdbc:sqlserver://db.example.test:1433;database=shale;password=secret123");
                    throw new SQLException("outer connection failure", root);
                });

        ResponseStatusException error = assertThrows(ResponseStatusException.class, provider::requireConnection);

        assertEquals(503, error.getStatusCode().value());
        assertEquals("Unable to open request-scoped runtime database connection.", error.getReason());
        assertTrue(output.toString().contains("java.sql.SQLException"));
        assertTrue(output.toString().contains("Login failed for jdbc:<redacted>"));
        assertTrue(!output.toString().contains("secret123"));
        assertTrue(!output.toString().contains("db.example.test"));
    }

    @Test
    void runtimeSessionProviderLogsSanitizedRootCauseWhenInitializationFails(CapturedOutput output) throws Exception {
        DataSource dataSource = (DataSource) Proxy.newProxyInstance(
                DataSource.class.getClassLoader(),
                new Class<?>[] {DataSource.class},
                (proxy, method, args) -> {
                    if ("getConnection".equals(method.getName())) {
                        throw new SQLException("Could not connect using jdbc:sqlserver://runtime.example.test;pwd=topsecret");
                    }
                    return null;
                });
        RuntimeSessionServiceConnectionProvider provider = new RuntimeSessionServiceConnectionProvider(dataSource);

        SQLException error = assertThrows(SQLException.class, () -> provider.openConnection(new ServerPrincipal(11, 13, null)));

        assertTrue(error.getMessage().contains("jdbc:sqlserver://runtime.example.test"));
        assertTrue(output.toString().contains("Runtime session service connection initialization failed"));
        assertTrue(output.toString().contains("java.sql.SQLException"));
        assertTrue(output.toString().contains("jdbc:<redacted>"));
        assertTrue(!output.toString().contains("runtime.example.test"));
        assertTrue(!output.toString().contains("topsecret"));
    }

    @Test
    void runtimeSessionProviderUsesDesktopSessionContextInitializationSql() throws Exception {
        RecordingDataSource dataSource = new RecordingDataSource();
        RuntimeSessionServiceConnectionProvider provider = new RuntimeSessionServiceConnectionProvider(dataSource.proxy());

        Connection connection = provider.openConnection(new ServerPrincipal(11, 13, null));

        assertSame(dataSource.connectionProxy, connection);
        assertEquals(List.of(
                "EXEC sys.sp_set_session_context @key=N'ShaleClientId', @value=?",
                "EXEC sys.sp_set_session_context @key=N'PrincipalUserId', @value=?"), dataSource.sql);
        assertEquals(List.of(13, 11), dataSource.values);
        assertEquals(2, dataSource.executeCount);
    }

    private static ObjectProvider<HttpServletRequest> fixedRequest(HttpServletRequest request) {
        return new ObjectProvider<>() {
            @Override
            public HttpServletRequest getObject(Object... args) {
                return request;
            }

            @Override
            public HttpServletRequest getIfAvailable() {
                return request;
            }

            @Override
            public HttpServletRequest getIfUnique() {
                return request;
            }

            @Override
            public HttpServletRequest getObject() {
                return request;
            }
        };
    }

    private static final class RecordingRuntimeConnectionProvider implements RuntimeConnectionProvider {
        private final Connection connection = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                (proxy, method, args) -> null);
        private int calls;
        private ServerPrincipal principal;

        @Override
        public Connection openConnection(ServerPrincipal principal) {
            this.calls++;
            this.principal = principal;
            return connection;
        }
    }

    private static final class RecordingDataSource {
        private final List<String> sql = new ArrayList<>();
        private final List<Integer> values = new ArrayList<>();
        private int executeCount;
        private final Connection connectionProxy = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                (proxy, method, args) -> {
                    if ("prepareStatement".equals(method.getName())) {
                        sql.add((String) args[0]);
                        return preparedStatementProxy();
                    }
                    if ("isClosed".equals(method.getName())) {
                        return false;
                    }
                    if ("toString".equals(method.getName())) {
                        return "recording-connection";
                    }
                    return null;
                });

        private DataSource proxy() {
            return (DataSource) Proxy.newProxyInstance(
                    DataSource.class.getClassLoader(),
                    new Class<?>[] {DataSource.class},
                    (proxy, method, args) -> {
                        if ("getConnection".equals(method.getName())) {
                            return connectionProxy;
                        }
                        return null;
                    });
        }

        private PreparedStatement preparedStatementProxy() {
            return (PreparedStatement) Proxy.newProxyInstance(
                    PreparedStatement.class.getClassLoader(),
                    new Class<?>[] {PreparedStatement.class},
                    (proxy, method, args) -> {
                        if ("setInt".equals(method.getName())) {
                            values.add((Integer) args[1]);
                            return null;
                        }
                        if ("execute".equals(method.getName())) {
                            executeCount++;
                            return true;
                        }
                        if ("close".equals(method.getName())) {
                            return null;
                        }
                        return null;
                    });
        }
    }
}
