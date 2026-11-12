package com.shale.server.oauth;

import com.shale.server.oauth.cimd.CimdMetadataService;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.*;
import org.springframework.core.env.*;

/** Default-OFF composition; production requires explicit capabilities and live SQL verification. */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="shale.ai.cimd.enabled",havingValue="true")
public class CimdConnectionConfiguration {
    @Bean static org.springframework.beans.factory.config.BeanFactoryPostProcessor cimdRuntimeBoundary(Environment env) {
        return factory->{
            CimdRuntimeEligibility.require(env, false);
        };
    }
    @Bean(destroyMethod="close") @ConditionalOnMissingBean(CimdMetadataService.class)
    CimdMetadataService cimdMetadata(McpOAuthProperties identity) {
        return new CimdMetadataService(identity.issuer());
    }
    @Bean CimdOAuthLifecycle cimdLifecycle(CimdServices services,CimdMetadataService metadata,McpOAuthProperties identity,Environment env,org.springframework.beans.factory.ObjectProvider<DcrCapabilities> dcr) {
        return new CimdOAuthLifecycle(metadata,services.metadata(),services.resolver(),services.neutral(),services.issuer(),identity,java.time.Clock.systemUTC(),env.getProperty("shale.ai.dcr.enabled",Boolean.class,false)?dcr.getObject().metadata():null);
    }
    @Bean CimdApplicationAccess cimdApplications(CimdServices services,CimdMetadataService metadata,CimdOAuthLifecycle lifecycle) {
        return new CimdApplicationAccess(services,metadata,lifecycle);
    }
}
