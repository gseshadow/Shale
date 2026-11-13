package com.shale.server.oauth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.junit.jupiter.api.Assertions.*;

/** Default-OFF; no partial or ineligible composition may advertise an operational CIMD server. */
class CimdConnectionConfigurationTest {
    private final ApplicationContextRunner runner=new ApplicationContextRunner().withUserConfiguration(CimdConnectionConfiguration.class);
    @Test void offRequiresNoNewServicesAndCreatesNoLifecycle() {
        runner.run(c->{assertNull(c.getStartupFailure());assertFalse(c.containsBean("cimdLifecycle"));});
        runner.withPropertyValues("shale.ai.cimd.enabled=false").run(c->{assertNull(c.getStartupFailure());assertFalse(c.containsBean("cimdMetadata"));});
    }
    @Test void incompleteProductionAndMissingControlsFailBeforeResolvingAdapters() {
        for(String profile:new String[]{"prod","azure"})runner.withInitializer(c->c.getEnvironment().setActiveProfiles(profile))
                .withPropertyValues("shale.ai.cimd.enabled=true").run(c->{
                    assertNotNull(c.getStartupFailure());
                    assertTrue(c.getStartupFailure().getMessage().contains("Complete OAuth/MCP composition"));
                });
        runner.withInitializer(c->c.getEnvironment().setActiveProfiles("test"))
                .withPropertyValues("shale.ai.cimd.enabled=true").run(c->{
                    assertNotNull(c.getStartupFailure());
                    assertTrue(c.getStartupFailure().getMessage().contains("Complete OAuth/MCP composition"));
                });
    }
    @Test void enablingAllFlagsStillRequiresExplicitCapabilityComposition() {
        runner.withInitializer(c->c.getEnvironment().setActiveProfiles("test")).withPropertyValues(
                "shale.ai.cimd.enabled=true","shale.ai.enabled=true","shale.ai.mcp.enabled=true",
                "shale.ai.oauth.composition-enabled=true","shale.ai.oauth.authorization-enabled=true","shale.ai.oauth.exchange-enabled=true")
                .run(c->assertNotNull(c.getStartupFailure(),"No auto-created credentials, identities or fallback sources"));
    }
}
