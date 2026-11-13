package com.shale.server.oauth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.env.*;

/** DCR stays OFF by default; enabled composition requires verified DCR_V3 capabilities. */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="shale.ai.dcr.enabled",havingValue="true")
public class DcrConnectionConfiguration {
    @Bean static org.springframework.beans.factory.config.BeanFactoryPostProcessor dcrRuntimeBoundary(Environment env) {
        return factory->{
            CimdRuntimeEligibility.require(env, true);
        };
    }
    @Bean Object dcrVerifiedComposition(DcrCapabilities capabilities,CimdServices services,
            com.shale.data.security.ai.AiCimdCaseData cases,McpOAuthProperties identity) {
        if(services.metadata().contract()!=com.shale.data.security.ai.AiExternalContract.DCR_V3
                ||services.resolver().contract()!=com.shale.data.security.ai.AiExternalContract.DCR_V3
                ||services.neutral().contract()!=com.shale.data.security.ai.AiExternalContract.DCR_V3)
            throw new IllegalStateException("Explicit DCR_V3 composition required.");
        cases.ready(System.nanoTime()+10_000_000_000L);
        if(!capabilities.admission().enabled())throw new IllegalStateException("Verified SQL DCR control required.");
        return new Object();
    }
}
