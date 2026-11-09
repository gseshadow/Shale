package com.shale.server.oauth;

import com.shale.core.runtime.DbSessionProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

/** Dedicated resolver identity. Never reuses the general authentication or runtime pool. */
@Configuration(proxyBeanMethods=false)
class OAuthCodeBootstrapConfiguration {
    @Bean("oauthCodeBootstrap")
    @ConditionalOnProperty(name="shale.ai.oauth.exchange-enabled",havingValue="true")
    DbSessionProvider oauthCodeBootstrap(Environment env) {
        var source=source(env);
        return () -> {
            try { return source.getConnection(); }
            catch(java.sql.SQLException unavailable) { throw new IllegalStateException("OAuth bootstrap unavailable."); }
        };
    }
    static com.microsoft.sqlserver.jdbc.SQLServerDataSource source(Environment env) {
        // Resolve and validate everything before allocating a datasource or exchanging credentials.
        String url=com.shale.data.config.AiJdbcUrl.validate(required(env,"jdbc-url"));
        String username=required(env,"username"),password=required(env,"password");
        com.shale.server.mcp.McpPayloadLogFilter.installSqlProtection();
        var source=new com.microsoft.sqlserver.jdbc.SQLServerDataSource();
        source.setURL(url);
        source.setUser(username);
        source.setPassword(password);
        source.setEncrypt("true");
        source.setTrustServerCertificate(false);
        // No hostNameInCertificate override: the driver verifies the destination server name.
        source.setLoginTimeout(5);
        source.setSocketTimeout(10000);
        return source;
    }
    private static String required(Environment env,String suffix) {
        String value=env.getProperty("shale.ai.oauth.bootstrap."+suffix);
        if(value==null || value.isBlank())throw new IllegalStateException("Incomplete OAuth bootstrap configuration.");
        return value;
    }
}
