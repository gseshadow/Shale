package com.shale.server.oauth;

import com.shale.server.oauth.cimd.CimdMetadataService;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.*;
import org.springframework.core.env.*;

/** Default-OFF development composition only; no production activation or datasource fallback. */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="shale.ai.cimd.enabled",havingValue="true")
public class CimdConnectionConfiguration {
    @Bean static org.springframework.beans.factory.config.BeanFactoryPostProcessor cimdDevelopmentBoundary(Environment env) {
        return factory->{
            if(!env.acceptsProfiles(Profiles.of("test","dev","local"))||env.acceptsProfiles(Profiles.of("prod","azure")))
                throw new IllegalStateException("CIMD connection activation is limited to explicit disposable development profiles.");
            for(String flag:java.util.List.of("shale.ai.enabled","shale.ai.mcp.enabled","shale.ai.oauth.composition-enabled",
                    "shale.ai.oauth.authorization-enabled","shale.ai.oauth.exchange-enabled"))
                if(!env.getProperty(flag,Boolean.class,false))throw new IllegalStateException("Complete OAuth/MCP composition required for CIMD.");
        };
    }
    @Bean(destroyMethod="close") @ConditionalOnMissingBean(CimdMetadataService.class)
    CimdMetadataService cimdMetadata(McpOAuthProperties identity) {
        if(identity.identity()==McpOAuthProperties.Identity.PRODUCTION)throw new IllegalStateException("Separate development CIMD identity required.");
        return new CimdMetadataService(identity.issuer());
    }
    @Bean CimdOAuthLifecycle cimdLifecycle(CimdServices services,CimdMetadataService metadata,McpOAuthProperties identity) {
        if(identity.identity()==McpOAuthProperties.Identity.PRODUCTION)throw new IllegalStateException("Separate development CIMD identity required.");
        return new CimdOAuthLifecycle(metadata,services.metadata(),services.resolver(),services.neutral(),services.issuer(),identity,java.time.Clock.systemUTC());
    }
    @Bean CimdApplicationAccess cimdApplications(CimdServices services,CimdMetadataService metadata,CimdOAuthLifecycle lifecycle) {
        return new CimdApplicationAccess(services,metadata,lifecycle);
    }
}
