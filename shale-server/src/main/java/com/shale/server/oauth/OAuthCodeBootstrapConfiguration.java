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
        var source=new com.microsoft.sqlserver.jdbc.SQLServerDataSource();
        source.setURL(env.getRequiredProperty("shale.ai.oauth.bootstrap.jdbc-url"));
        source.setUser(env.getRequiredProperty("shale.ai.oauth.bootstrap.username"));
        source.setPassword(env.getRequiredProperty("shale.ai.oauth.bootstrap.password"));
        source.setLoginTimeout(5);
        source.setSocketTimeout(10000);
        return () -> {
            try { return source.getConnection(); }
            catch(java.sql.SQLException unavailable) { throw new IllegalStateException("OAuth bootstrap unavailable."); }
        };
    }
}
