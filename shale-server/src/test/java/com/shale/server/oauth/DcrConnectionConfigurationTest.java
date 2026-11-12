package com.shale.server.oauth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.junit.jupiter.api.Assertions.*;

class DcrConnectionConfigurationTest {
    final ApplicationContextRunner runner=new ApplicationContextRunner().withUserConfiguration(DcrConnectionConfiguration.class);
    @Test void offCreatesNoCapabilitiesOrVerification() {
        runner.run(c->{assertNull(c.getStartupFailure());assertFalse(c.containsBean("dcrVerifiedComposition"));});
    }
    @Test void incompleteProductionAndPartialCompositionAreRejected() {
        for(String profile:new String[]{"prod","azure"})runner.withInitializer(c->c.getEnvironment().setActiveProfiles(profile))
                .withPropertyValues("shale.ai.dcr.enabled=true").run(c->assertNotNull(c.getStartupFailure()));
        runner.withInitializer(c->c.getEnvironment().setActiveProfiles("test")).withPropertyValues("shale.ai.dcr.enabled=true")
                .run(c->assertNotNull(c.getStartupFailure()));
    }
    @Test void fullFlagsDoNotCreateRegistrarOrFallbackCredentials() {
        runner.withInitializer(c->c.getEnvironment().setActiveProfiles("test")).withPropertyValues("shale.ai.dcr.enabled=true","shale.ai.cimd.enabled=true","shale.ai.enabled=true","shale.ai.mcp.enabled=true","shale.ai.oauth.composition-enabled=true","shale.ai.oauth.authorization-enabled=true","shale.ai.oauth.exchange-enabled=true")
                .run(c->assertNotNull(c.getStartupFailure(),"Explicit capability providers and verified Case composition required"));
    }
    @Test void eligibleDcrStillRequiresLiveCaseVerificationAndSqlControl() {
        var metadata=org.mockito.Mockito.mock(com.shale.data.security.ai.AiCimdOAuthStore.class);
        org.mockito.Mockito.when(metadata.contract()).thenReturn(com.shale.data.security.ai.AiExternalContract.DCR_V3);
        var services=org.mockito.Mockito.mock(CimdServices.class);
        org.mockito.Mockito.when(services.metadata()).thenReturn(metadata);
        org.mockito.Mockito.when(services.resolver()).thenReturn(metadata);
        org.mockito.Mockito.when(services.neutral()).thenReturn(metadata);
        var admission=org.mockito.Mockito.mock(com.shale.data.security.ai.AiDcrAdmissionStore.class);
        var registrar=org.mockito.Mockito.mock(com.shale.data.security.ai.AiDcrApplicationStore.class);
        org.mockito.Mockito.when(registrar.contract()).thenReturn(com.shale.data.security.ai.AiExternalContract.DCR_V3);
        var capabilities=new DcrCapabilities(registrar,registrar,admission);
        var cases=org.mockito.Mockito.mock(com.shale.data.security.ai.AiCimdCaseData.class);
        var identity=new McpOAuthProperties("https://shale.example",McpOAuthProperties.Identity.PRODUCTION,true);
        var config=new DcrConnectionConfiguration();
        assertThrows(IllegalStateException.class,()->config.dcrVerifiedComposition(capabilities,services,cases,identity));
        org.mockito.Mockito.verify(cases).ready(org.mockito.ArgumentMatchers.anyLong());
        org.mockito.Mockito.when(admission.enabled()).thenReturn(true);
        assertNotNull(config.dcrVerifiedComposition(capabilities,services,cases,identity));
        org.mockito.Mockito.doThrow(new IllegalStateException("Synthetic verification failure")).when(cases).ready(org.mockito.ArgumentMatchers.anyLong());
        assertThrows(IllegalStateException.class,()->config.dcrVerifiedComposition(capabilities,services,cases,identity));
        org.mockito.Mockito.when(metadata.contract()).thenReturn(com.shale.data.security.ai.AiExternalContract.CIMD_V1);
        assertThrows(IllegalStateException.class,()->config.dcrVerifiedComposition(capabilities,services,cases,identity));
    }
}
