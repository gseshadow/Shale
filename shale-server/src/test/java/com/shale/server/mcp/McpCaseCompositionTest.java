package com.shale.server.mcp;

import com.shale.data.config.DataSources;
import com.shale.server.oauth.McpOAuthProperties;
import com.shale.server.oauth.McpOAuthSecurityConfiguration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.*;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.context.annotation.AnnotatedBeanDefinitionReader;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Wiring/policy checks only. Mock attestation is never authoritative SQL/client acceptance. */
class McpCaseCompositionTest {
    static final String GLOBAL="shale.ai.enabled", TRANSPORT="shale.ai.mcp.enabled",
            SEARCH="shale.ai.mcp.search-cases-enabled", OVERVIEW="shale.ai.mcp.get-case-overview-enabled";
    static final McpOAuthProperties PRODUCTION=new McpOAuthProperties("https://shale.example",McpOAuthProperties.Identity.PRODUCTION,false);
    static final McpOAuthProperties TEST=new McpOAuthProperties("https://shale.test",McpOAuthProperties.Identity.TEST,false);

    @Test void allProductionControlCombinationsUseTheSameHandlerConjunction() {
        for(String profile:List.of("azure","prod"))for(int mask=0;mask<32;mask++) {
            var env=environment(profile);
            String[] flags={GLOBAL,TRANSPORT,McpCaseCompositionPolicy.COMPOSITION,SEARCH,OVERVIEW};
            for(int bit=0;bit<flags.length;bit++)env.setProperty(flags[bit],Boolean.toString((mask&(1<<bit))!=0));
            var policy=new McpCaseCompositionPolicy(env,PRODUCTION);
            var data=mock(McpAiCaseData.class);
            boolean common=(mask&7)==7;
            if(common&&(mask&8)!=0)assertNotNull(new McpSearchConfiguration().mcpSearchHandler(policy,data));
            else assertThrows(IllegalStateException.class,()->new McpSearchConfiguration().mcpSearchHandler(policy,data));
            if(common&&(mask&16)!=0)assertNotNull(new McpOverviewConfiguration().mcpOverviewHandler(policy,data));
            else assertThrows(IllegalStateException.class,()->new McpOverviewConfiguration().mcpOverviewHandler(policy,data));
        }
    }

    @Test void productionControlMatrixUsesActualSpringHandlerRegistration() {
        for(String profile:List.of("azure","prod"))for(int mask=0;mask<32;mask++) {
            var properties=new HashMap<String,Object>();
            String[] flags={GLOBAL,TRANSPORT,McpCaseCompositionPolicy.COMPOSITION,SEARCH,OVERVIEW};
            for(int bit=0;bit<flags.length;bit++)properties.put(flags[bit],(mask&(1<<bit))!=0);
            properties.put("shale.ai.oauth.public-origin","https://shale.example");
            boolean transport=(mask&2)!=0,common=(mask&7)==7;
            if(transport&&!common) {
                assertThrows(RuntimeException.class,()->context(profile,properties,mock(McpAiCaseData.class)),"Incomplete production transport must refuse Spring composition");
            } else try(var context=context(profile,properties,mock(McpAiCaseData.class))) {
                assertEquals(common&&(mask&8)!=0, !context.getBeansOfType(McpSearchHandler.class).isEmpty());
                assertEquals(common&&(mask&16)!=0, !context.getBeansOfType(McpOverviewHandler.class).isEmpty());
                assertEquals(transport,context.containsBean("mcpServlet"));
                assertFalse(context.containsBean("mcpAccessAuthenticator"));
            }
        }
        try(var context=context("test",Map.of(GLOBAL,true,TRANSPORT,true,SEARCH,true,OVERVIEW,true,
                "shale.ai.oauth.public-origin","https://shale.test","shale.ai.oauth.identity","TEST",
                "shale.ai.oauth.bootstrap.jdbc-url","jdbc:sqlserver://127.0.0.1:1;databaseName=unused",
                "shale.ai.oauth.bootstrap.username","unused","shale.ai.oauth.bootstrap.password","unused"),mock(McpAiCaseData.class))) {
            assertEquals(1,context.getBeansOfType(McpSearchHandler.class).size());
            assertEquals(1,context.getBeansOfType(McpOverviewHandler.class).size());
        }
    }

    @Test void isolatedTestCompositionIsPreservedAndAmbiguousProfilesAreRejected() {
        var env=environment("test");
        env.setProperty(GLOBAL,"true");env.setProperty(TRANSPORT,"true");env.setProperty(SEARCH,"true");
        assertNotNull(new McpSearchConfiguration().mcpSearchHandler(new McpCaseCompositionPolicy(env,TEST),mock(McpAiCaseData.class)));
        assertThrows(IllegalStateException.class,()->new McpCaseCompositionPolicy(env,PRODUCTION).requireBoundary());
        for(String[] profiles:List.of(new String[]{"azure","test"},new String[]{"prod","dev"},new String[]{"prod","local"},new String[]{"local"})) {
            var mixed=environment(profiles);mixed.setProperty(McpCaseCompositionPolicy.COMPOSITION,"true");
            for(var identity:List.of(TEST,PRODUCTION))
                assertThrows(IllegalStateException.class,()->new McpCaseCompositionPolicy(mixed,identity).requireBoundary());
        }
        var production=environment("azure");production.setProperty(McpCaseCompositionPolicy.COMPOSITION,"true");
        assertThrows(IllegalStateException.class,()->new McpCaseCompositionPolicy(production,TEST).requireBoundary());
        assertThrows(IllegalStateException.class,()->new McpCaseCompositionPolicy(production,new McpOAuthProperties(null,null,false)).requireBoundary());
    }

    @Test void attestationFailurePreventsEitherHandlerAndReadinessReturnsOnlyClosedState() {
        var env=environment("azure");
        for(String key:List.of(GLOBAL,TRANSPORT,SEARCH,OVERVIEW,McpCaseCompositionPolicy.COMPOSITION))env.setProperty(key,"true");
        var policy=new McpCaseCompositionPolicy(env,PRODUCTION);
        var data=mock(McpAiCaseData.class);
        doThrow(new IllegalStateException("private-sentinel SQL details")).when(data).ready(anyLong());
        assertThrows(IllegalStateException.class,()->new McpSearchConfiguration().mcpSearchHandler(policy,data));
        assertThrows(IllegalStateException.class,()->new McpOverviewConfiguration().mcpOverviewHandler(policy,data));
        var factory=new DefaultListableBeanFactory();factory.registerSingleton("boundary",data);
        var readiness=new McpCaseReadiness(policy,factory.getBeanProvider(McpAiCaseData.class));
        assertEquals("not-ready",readiness.state());
        doNothing().when(data).ready(anyLong());assertEquals("ready",readiness.state());
        doThrow(new IllegalStateException("stale catalog")).when(data).ready(anyLong());
        assertEquals("not-ready",readiness.state(),"A previous success cannot cache authority or readiness.");
    }

    @Test void productionOffModeNeverTouchesAnyDatasourceAndRetainsOrdinaryRoutes()throws Exception {
        for(String profile:List.of("azure","prod"))try(var context=context(profile,Map.of(),null)) {
            assertFalse(context.containsBean("mcpAiCaseData"));
            assertTrue(context.getBeansOfType(McpSearchHandler.class).isEmpty());
            assertTrue(context.getBeansOfType(McpOverviewHandler.class).isEmpty());
            assertFalse(context.containsBean("mcpServlet"));
            assertFalse(context.containsBean("oauthCodeBootstrap"));
            assertEquals("disabled",context.getBean(McpCaseReadiness.class).state());
            var security=context.getBean(FilterChainProxy.class);
            for(String route:List.of("/api/health","/api/auth/login","/api/auth/desktop-session","/api/v2/cases/search-page")) {
                var response=new MockHttpServletResponse();
                security.doFilter(new MockHttpServletRequest("GET",route),response,(r,s)->s.getWriter().write("ordinary"));
                assertEquals("ordinary",response.getContentAsString());
            }
            for(String route:List.of("/oauth/mcp/token","/oauth/mcp/authorize","/mcp")) {
                var response=new MockHttpServletResponse();
                security.doFilter(new MockHttpServletRequest("POST",route),response,(r,s)->fail("Disabled protocol reached application"));
                assertTrue(response.getStatus()>=400);
            }
        }
    }

    @Test void readinessLogContainsOnlyClosedStateAndNeverDiagnosticDetails() {
        var env=environment("azure");
        var data=mock(McpAiCaseData.class);
        var factory=new DefaultListableBeanFactory();factory.registerSingleton("boundary",data);
        var readiness=new McpCaseReadiness(new McpCaseCompositionPolicy(env,PRODUCTION),factory.getBeanProvider(McpAiCaseData.class));
        var logger=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(McpCaseReadiness.class);
        var appender=new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();logger.addAppender(appender);
        try {
            readiness.report();verifyNoInteractions(data);
            env.setProperty(McpCaseCompositionPolicy.COMPOSITION,"true");readiness.report();
            doThrow(new IllegalStateException("SQL username/hash/proof private-sentinel")).when(data).ready(anyLong());
            readiness.report();
            assertEquals(List.of("AI_CASE_SQL_READINESS disabled","AI_CASE_SQL_READINESS ready","AI_CASE_SQL_READINESS not-ready"),
                    appender.list.stream().map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage).toList());
            assertTrue(appender.list.stream().allMatch(event->event.getThrowableProxy()==null));
            verify(data,never()).authenticate(anyString(),anyLong());
            verify(data,never()).reader(anyLong(),any());
        } finally {logger.detachAppender(appender);appender.stop();}
    }

    @Test void readinessOnlyHasNoOAuthOrMcpDisclosureSurface()throws Exception {
        var data=mock(McpAiCaseData.class);
        try(var context=context("azure",Map.of(McpCaseCompositionPolicy.COMPOSITION,"true",
                "shale.ai.oauth.public-origin","https://shale.example"),data)) {
            assertEquals("ready",context.getBean(McpCaseReadiness.class).state());
            verify(data,atLeastOnce()).ready(anyLong());
            verify(data,never()).authenticate(anyString(),anyLong());
            assertFalse(context.containsBean("mcpServlet"));
            assertFalse(context.containsBean("oauthCodeBootstrap"));
            assertTrue(context.getBeansOfType(McpSearchHandler.class).isEmpty());
            assertTrue(context.getBeansOfType(McpOverviewHandler.class).isEmpty());
            for(String path:List.of("/mcp","/oauth/mcp/token","/oauth/mcp/authorize")) {
                var response=new MockHttpServletResponse();
                context.getBean(FilterChainProxy.class).doFilter(new MockHttpServletRequest("POST",path),response,(r,s)->fail());
                assertTrue(response.getStatus()>=400);
            }
        }
    }

    @Test void enabledReadinessRequiresEveryDedicatedSettingBeforeAllocatingAiPool() {
        Map<String,String> complete=Map.ofEntries(
                Map.entry("shale.ai.data.contract-version","V2"),
                Map.entry("shale.ai.data.jdbc-url","jdbc:sqlserver://sql.example;databaseName=shale"),
                Map.entry("shale.ai.data.username","ai"),
                Map.entry("shale.ai.data.password","synthetic"),
                Map.entry("shale.ai.data.database","shale"),
                Map.entry("shale.ai.data.verifier.jdbc-url","jdbc:sqlserver://sql.example;databaseName=shale"),
                Map.entry("shale.ai.data.verifier.username","catalog"),
                Map.entry("shale.ai.data.verifier.password","synthetic"),
                Map.entry("shale.ai.oauth.bootstrap.jdbc-url","jdbc:sqlserver://sql.example;databaseName=shale"),
                Map.entry("shale.ai.oauth.bootstrap.username","bootstrap"),
                Map.entry("shale.ai.oauth.bootstrap.password","synthetic"));
        for(String missing:complete.keySet()) {
            var env=environment("azure");complete.forEach((key,value)->{if(!key.equals(missing))env.setProperty(key,value);});
            var pools=mock(DataSources.class);
            assertThrows(IllegalStateException.class,()->new McpAiCaseData(env,PRODUCTION,pools),missing);
            verifyNoInteractions(pools);
        }
    }

    @Test void contractSelectionIsExplicitAndUnknownVersionsFailClosed() {
        var env=environment("azure");
        assertThrows(IllegalStateException.class,()->McpAiCaseData.contract(env));
        for(String invalid:List.of("", "v2", "V3", "V2,V1")){
            env.setProperty("shale.ai.data.contract-version",invalid);
            assertThrows(RuntimeException.class,()->McpAiCaseData.contract(env));
        }
        for(String version:List.of("V1","V2")){
            env.setProperty("shale.ai.data.contract-version",version);
            assertEquals(version,McpAiCaseData.contract(env).name());
        }
    }
    @Test void productionTransportRequiresDedicatedCompositionAndNeverCreatesLegacyAuthenticator() {
        var env=environment("azure");env.setProperty(TRANSPORT,"true");
        assertThrows(IllegalStateException.class,()->new McpCaseCompositionPolicy(env,PRODUCTION).requireTransport());
        env.setProperty(McpCaseCompositionPolicy.COMPOSITION,"true");
        assertThrows(IllegalStateException.class,()->new McpCaseCompositionPolicy(env,PRODUCTION).requireTransport());
        env.setProperty(GLOBAL,"true");
        assertDoesNotThrow(()->new McpCaseCompositionPolicy(env,PRODUCTION).requireTransport());
        var properties=new HashMap<String,Object>();
        for(String key:List.of(GLOBAL,TRANSPORT,McpCaseCompositionPolicy.COMPOSITION))properties.put(key,"true");
        properties.put("shale.ai.oauth.public-origin","https://shale.example");
        properties.put(SEARCH,"true");properties.put(OVERVIEW,"true");
        try(var context=context("azure",properties,mock(McpAiCaseData.class))) {
            assertFalse(context.containsBean("mcpAccessAuthenticator"));
            assertEquals(1,context.getBeansOfType(McpSearchHandler.class).size());
            assertEquals(1,context.getBeansOfType(McpOverviewHandler.class).size());
        }
    }

    private static MockEnvironment environment(String... profiles) {
        var env=new MockEnvironment();env.setActiveProfiles(profiles);return env;
    }
    private static GenericWebApplicationContext context(String profile,Map<String,?> properties,McpAiCaseData boundary) {
        var context=new GenericWebApplicationContext();
        context.setServletContext(new MockServletContext());context.getEnvironment().setActiveProfiles(profile);
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("case-composition",new HashMap<>(properties)));
        if(boundary!=null)context.registerBean(McpAiCaseData.class,()->boundary);
        new AnnotatedBeanDefinitionReader(context).register(McpOAuthSecurityConfiguration.class,McpTransportConfiguration.class);
        try{context.refresh();return context;}catch(RuntimeException failure){context.close();throw failure;}
    }
}
