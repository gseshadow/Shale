package com.shale.server.oauth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.junit.jupiter.api.Assertions.*;

class DcrConnectionConfigurationTest {
    final ApplicationContextRunner runner=new ApplicationContextRunner().withUserConfiguration(DcrConnectionConfiguration.class);
    @Test void offCreatesNoCapabilitiesOrVerification() {
        runner.run(c->{assertNull(c.getStartupFailure());assertFalse(c.containsBean("dcrVerifiedDevelopmentComposition"));});
    }
    @Test void productionAndPartialCompositionAreRejected() {
        for(String profile:new String[]{"prod","azure"})runner.withInitializer(c->c.getEnvironment().setActiveProfiles(profile))
                .withPropertyValues("shale.ai.dcr.enabled=true").run(c->assertNotNull(c.getStartupFailure()));
        runner.withInitializer(c->c.getEnvironment().setActiveProfiles("test")).withPropertyValues("shale.ai.dcr.enabled=true")
                .run(c->assertNotNull(c.getStartupFailure()));
    }
    @Test void fullFlagsDoNotCreateRegistrarOrFallbackCredentials() {
        runner.withInitializer(c->c.getEnvironment().setActiveProfiles("test")).withPropertyValues("shale.ai.dcr.enabled=true","shale.ai.cimd.enabled=true","shale.ai.enabled=true","shale.ai.mcp.enabled=true","shale.ai.oauth.composition-enabled=true","shale.ai.oauth.authorization-enabled=true","shale.ai.oauth.exchange-enabled=true")
                .run(c->assertNotNull(c.getStartupFailure(),"Explicit capability providers and verified Case composition required"));
    }
}
