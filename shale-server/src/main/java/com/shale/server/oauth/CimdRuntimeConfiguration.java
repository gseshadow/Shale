package com.shale.server.oauth;

import com.shale.data.config.DataSources;
import com.shale.data.security.ai.*;
import com.shale.server.mcp.CimdCaseConnections;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

/** Production adapter composition over existing sources, stores and verifiers. All-OFF creates nothing. */
@Configuration(proxyBeanMethods = false)
@Profile({"azure", "prod"})
@ConditionalOnProperty(name = "shale.ai.cimd.enabled", havingValue = "true")
class CimdRuntimeConfiguration {
    @Bean static org.springframework.beans.factory.config.BeanFactoryPostProcessor cimdRuntimeSettings(Environment env) {
        return factory -> {
            CimdRuntimeEligibility.require(env, env.getProperty("shale.ai.dcr.enabled", Boolean.class, false));
            CimdRuntimeConnections.validate(env);
        };
    }

    @Bean CimdRuntimeConnections cimdRuntimeConnections(Environment env, DataSources ordinary) {
        var connections = new CimdRuntimeConnections(env);
        connections.verifyIsolation(CimdRuntimeConnections.roles(env), List.of(ordinary.aiCaseData(), ordinary.auth(),
                ordinary.runtime(), CimdRuntimeConnections.source(env, "shale.ai.data.verifier"),
                CimdRuntimeConnections.source(env, "shale.ai.oauth.bootstrap")));
        return connections;
    }

    @Bean CimdCaseConnections cimdCaseConnections(CimdRuntimeConnections connections) {
        return new CimdCaseConnections(CimdCaseConnections.bounded(connections.source("case")),
                CimdCaseConnections.bounded(connections.source("reviewer")));
    }

    @Bean CimdServices cimdServices(CimdRuntimeConnections c, McpOAuthProperties identity) {
        String issuer = identity.issuer(), resource = identity.resource();
        var contract = AiExternalContract.DCR_V3;
        return new CimdServices(new AiApplicationRegistryService(c.neutral("publisher"), issuer, resource),
                owner -> new AiCimdOnboardingStore(c.scoped("onboarding", owner), issuer, resource, contract),
                owner -> new AiTenantApplicationService(c.scoped("approval", owner)),
                owner -> new AiCimdOAuthStore(c.scoped("browser", owner), issuer, resource, contract),
                owner -> new AiCimdOAuthStore(c.scoped("issuer", owner), issuer, resource, contract),
                new AiCimdOAuthStore(c.neutral("metadata"), issuer, resource, contract),
                new AiCimdOAuthStore(c.neutral("resolver"), issuer, resource, contract),
                new AiCimdOAuthStore(c.neutral("neutral"), issuer, resource, contract));
    }

    @Bean @ConditionalOnProperty(name = "shale.ai.dcr.enabled", havingValue = "true")
    DcrCapabilities dcrCapabilities(CimdRuntimeConnections c, McpOAuthProperties identity) {
        return new DcrCapabilities(new AiDcrApplicationStore(c.neutral("dcr-endpoint"), identity.issuer(), identity.resource(), AiExternalContract.DCR_V3),
                new AiDcrApplicationStore(c.neutral("dcr-metadata"), identity.issuer(), identity.resource(), AiExternalContract.DCR_V3),
                new AiDcrAdmissionStore(c.neutral("dcr-endpoint")));
    }
}
