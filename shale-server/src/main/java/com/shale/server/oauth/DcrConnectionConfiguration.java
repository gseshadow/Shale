package com.shale.server.oauth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.env.*;

/** DCR stays OFF by default and is prohibited in production profiles/identities. */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="shale.ai.dcr.enabled",havingValue="true")
public class DcrConnectionConfiguration {
    @Bean static org.springframework.beans.factory.config.BeanFactoryPostProcessor dcrDevelopmentBoundary(Environment env) {
        return factory->{
            if(!env.acceptsProfiles(Profiles.of("test","dev","local"))||env.acceptsProfiles(Profiles.of("prod","azure")))
                throw new IllegalStateException("DCR is limited to disposable development profiles.");
            for(String flag:java.util.List.of("shale.ai.enabled","shale.ai.mcp.enabled","shale.ai.cimd.enabled",
                    "shale.ai.oauth.composition-enabled","shale.ai.oauth.authorization-enabled","shale.ai.oauth.exchange-enabled"))
                if(!env.getProperty(flag,Boolean.class,false))throw new IllegalStateException("Complete verified OAuth/MCP required for DCR.");
        };
    }
    @Bean Object dcrVerifiedDevelopmentComposition(DcrCapabilities capabilities,CimdServices services,
            com.shale.data.security.ai.AiCimdCaseData cases,McpOAuthProperties identity) {
        if(identity.identity()==McpOAuthProperties.Identity.PRODUCTION||services.metadata().contract()!=com.shale.data.security.ai.AiExternalContract.DCR_V3
                ||services.resolver().contract()!=com.shale.data.security.ai.AiExternalContract.DCR_V3
                ||services.neutral().contract()!=com.shale.data.security.ai.AiExternalContract.DCR_V3)
            throw new IllegalStateException("Explicit C2 development composition required.");
        cases.ready(System.nanoTime()+10_000_000_000L);
        if(!capabilities.admission().enabled())throw new IllegalStateException("Verified SQL DCR control must be enabled in disposable development only.");
        return new Object();
    }
}
