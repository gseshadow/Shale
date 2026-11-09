package com.shale.server.oauth;

import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class OAuthCodeBootstrapConfigurationTest {
    static MockEnvironment settings() {
        var env=new MockEnvironment();
        env.setProperty("shale.ai.oauth.exchange-enabled","true");
        env.setProperty("shale.ai.oauth.bootstrap.jdbc-url","jdbc:sqlserver://private.database.windows.net;databaseName=private");
        env.setProperty("shale.ai.oauth.bootstrap.username","private-user");
        env.setProperty("shale.ai.oauth.bootstrap.password","private-password");
        return env;
    }
    @Test void validProductionSettingsForceTlsAndKeepDriverHostnameVerificationWithoutConnecting() throws Exception {
        var source=OAuthCodeBootstrapConfiguration.source(settings());
        assertEquals("true",source.getEncrypt());
        assertFalse(source.getTrustServerCertificate());
        assertTrue(source.getHostNameInCertificate()==null || source.getHostNameInCertificate().isEmpty());
        assertEquals(5,source.getLoginTimeout());assertEquals(10000,source.getSocketTimeout());
        assertEquals("private-user",source.getUser());
        // Exercise the pinned driver's actual property merge, without opening a socket.
        var field=SQLServerDataSource.class.getDeclaredField("connectionProps");field.setAccessible(true);
        var parse=Class.forName("com.microsoft.sqlserver.jdbc.Util").getDeclaredMethod("parseUrl",String.class,java.util.logging.Logger.class);parse.setAccessible(true);
        var merge=Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver").getDeclaredMethod("mergeURLAndSuppliedProperties",java.util.Properties.class,java.util.Properties.class);merge.setAccessible(true);
        var effective=(java.util.Properties)merge.invoke(null,parse.invoke(null,source.getURL(),java.util.logging.Logger.getAnonymousLogger()),field.get(source));
        assertEquals("true",effective.getProperty("encrypt"));assertEquals("false",effective.getProperty("trustServerCertificate"));
        assertFalse(effective.containsKey("hostNameInCertificate"));
    }
    @Test void unsafeOrIncompleteSettingsAbortStartupBeforeDatasourceAllocation(CapturedOutput output) {
        for(String suffix:List.of(";encrypt=false",";trustServerCertificate=true",";hostNameInCertificate=attacker",
                ";password=private-password",";trustManagerClass=private",";databaseName={private")) {
            var env=settings();env.setProperty("shale.ai.oauth.bootstrap.jdbc-url",env.getProperty("shale.ai.oauth.bootstrap.jdbc-url")+suffix);
            assertRejected(env);
        }
        for(String key:List.of("jdbc-url","username","password")) {
            var env=settings();env.setProperty("shale.ai.oauth.bootstrap."+key,"");assertRejected(env);
            var missing=new MockEnvironment();
            for(String other:List.of("jdbc-url","username","password"))
                if(!other.equals(key))missing.setProperty("shale.ai.oauth.bootstrap."+other,settings().getProperty("shale.ai.oauth.bootstrap."+other));
            assertRejected(missing);
        }
        assertFalse(output.getAll().contains("private"));assertFalse(output.getAll().contains("jdbc:sqlserver"));
    }
    private static void assertRejected(MockEnvironment env) {
        try(var construction=mockConstruction(SQLServerDataSource.class);
                var context=new AnnotationConfigApplicationContext()) {
            context.setEnvironment(env);context.register(OAuthCodeBootstrapConfiguration.class);
            // Missing switch must still exercise enabled validation in this test.
            env.setProperty("shale.ai.oauth.exchange-enabled","true");
            var failure=assertThrows(RuntimeException.class,context::refresh);
            assertTrue(construction.constructed().isEmpty(),"Invalid configuration must fail before allocating a source");
            var trace=new java.io.StringWriter();failure.printStackTrace(new java.io.PrintWriter(trace));
            assertFalse(trace.toString().contains("private"));assertFalse(trace.toString().contains("jdbc:sqlserver"));
        }
    }
    @Test void disabledOrAbsentSwitchNeverReadsBootstrapSettingsOrAllocatesSource() {
        for(String enabled:List.of("absent","false")) {
            var env=new MockEnvironment();
            if(!enabled.equals("absent"))env.setProperty("shale.ai.oauth.exchange-enabled",enabled);
            env.setProperty("shale.ai.oauth.bootstrap.jdbc-url","malformed-private");
            try(var construction=mockConstruction(SQLServerDataSource.class);var context=new AnnotationConfigApplicationContext()) {
                context.setEnvironment(env);context.register(OAuthCodeBootstrapConfiguration.class);context.refresh();
                assertFalse(context.containsBean("oauthCodeBootstrap"));assertTrue(construction.constructed().isEmpty());
            }
        }
    }
    @Test void bootstrapProtectsDriverLogsEvenWhenCaseCompositionIsOff() {
        var logger=java.util.logging.Logger.getLogger("com.microsoft.sqlserver.jdbc.privateProbe");
        var previous=logger.getLevel();var filter=logger.getFilter();var messages=new java.util.ArrayList<String>();
        var handler=new java.util.logging.Handler() {
            public void publish(java.util.logging.LogRecord record){messages.add(record.getMessage());}
            public void flush(){} public void close(){}
        };
        handler.setLevel(java.util.logging.Level.ALL);logger.addHandler(handler);
        try {
            logger.setLevel(java.util.logging.Level.ALL);
            OAuthCodeBootstrapConfiguration.source(settings());
            logger.severe("private-password jdbc:sqlserver://private");
            assertTrue(messages.isEmpty(),"Bootstrap must suppress driver logs before setting connection properties");
        } finally {logger.removeHandler(handler);logger.setLevel(previous);logger.setFilter(filter);}
    }
    @Test void driverFailuresAreSanitizedWithoutCredentialOrUrlLogs(CapturedOutput output) throws Exception {
        try(var construction=mockConstruction(SQLServerDataSource.class,(source,context)->
                when(source.getConnection()).thenThrow(new SQLException("private-password jdbc:sqlserver://private")))) {
            var provider=new OAuthCodeBootstrapConfiguration().oauthCodeBootstrap(settings());
            var failure=assertThrows(IllegalStateException.class,provider::requireConnection);
            assertEquals("OAuth bootstrap unavailable.",failure.getMessage());assertNull(failure.getCause());
        }
        assertFalse(output.getAll().contains("private"));assertFalse(output.getAll().contains("jdbc:sqlserver"));
    }
}
