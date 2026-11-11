package com.shale.server.oauth;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

class CimdRuntimeConfigurationTest {
    @Test void absentAndExplicitOffNeedNoCredentialsOrDatabase() {
        for(String raw:new String[]{null,"false","FALSE"}) {
            try(var context=context(raw)) { assertDoesNotThrow(context::refresh); }
        }
    }
    @Test void startupRejectsActivationAndMalformedFlagsBeforeAnySingleton() {
        for(String raw:new String[]{"true","yes","1",""," false ","${unresolved}"}) {
            try(var context=context(raw)) { assertThrows(RuntimeException.class,context::refresh); }
        }
    }
    private static AnnotationConfigApplicationContext context(String raw) {
        var context=new AnnotationConfigApplicationContext(); var env=new MockEnvironment();
        if(raw!=null) env.setProperty("shale.ai.cimd.enabled",raw);
        context.setEnvironment(env); context.register(CimdRuntimeConfiguration.class); return context;
    }
}
