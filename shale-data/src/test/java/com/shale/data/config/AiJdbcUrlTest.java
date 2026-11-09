package com.shale.data.config;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AiJdbcUrlTest {
    @Test void destinationOnlyUrlsPreserveNormalDriverHostnameVerification() {
        for (String url : List.of("jdbc:sqlserver://shale.database.windows.net:1433;databaseName=Shale",
                "jdbc:sqlserver://localhost;databaseName=synthetic;applicationName=Shale;",
                "jdbc:sqlserver://[::1]:1433;databaseName=test"))
            assertEquals(url, AiJdbcUrl.validate(url));
    }
    @Test void malformedDestinationsAndEveryNonDestinationSettingFailClosed() {
        for (String url : List.of("", "jdbc:mysql://host", "jdbc:sqlserver://", "jdbc:sqlserver://host:0",
                "jdbc:sqlserver://host:65536", "jdbc:sqlserver://user@host", "jdbc:sqlserver://host/path", "jdbc:sqlserver://[:::]",
                "jdbc:sqlserver://host;databaseName", "jdbc:sqlserver://host;databaseName={broken",
                "jdbc:sqlserver://host;databaseName=a;databaseName=b", "jdbc:sqlserver://host;databaseName=\nprivate"))
            assertSafeFailure(url);
        for (String key : List.of("encrypt", "trustServerCertificate", "hostNameInCertificate", "user", "userName",
                "password", "integratedSecurity", "authentication", "accessToken", "multipleActiveResultSets",
                "loginTimeout", "socketTimeout", "trustManagerClass", "serverCertificate", "socketFactoryClass",
                "trustStore", "trustStorePassword", "serverName", "database", "unknownFutureSetting")) {
            assertSafeFailure("jdbc:sqlserver://host;"+key+"=private");
            assertSafeFailure("jdbc:sqlserver://host; "+key.toUpperCase()+" =private");
        }
        assertThrows(IllegalStateException.class, () -> AiJdbcUrl.validate(null));
    }
    private static void assertSafeFailure(String url) {
        var failure=assertThrows(IllegalStateException.class,()->AiJdbcUrl.validate(url));
        assertFalse(failure.toString().contains(url.isEmpty()?"private":url));
        assertNull(failure.getCause());
    }
}
