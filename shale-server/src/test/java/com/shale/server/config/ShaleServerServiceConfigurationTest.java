package com.shale.server.config;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.web.server.ResponseStatusException;

import com.shale.core.runtime.DbSessionProvider;
import com.shale.core.service.AuthServicePort;
import com.shale.core.service.ApplicationReleaseReadServicePort;
import com.shale.core.service.ApplicationReleaseImportServicePort;
import com.shale.core.service.CaseServicePort;
import com.shale.core.service.ContactServicePort;
import com.shale.core.service.NotificationServicePort;
import com.shale.core.service.TaskServicePort;
import com.shale.core.service.TaskPolicyConfigurationServicePort;
import com.shale.data.service.adapter.AuthServiceAdapter;
import com.shale.data.service.adapter.ApplicationReleaseReadServiceAdapter;
import com.shale.data.service.adapter.CaseServiceAdapter;
import com.shale.data.service.adapter.ContactServiceAdapter;
import com.shale.data.service.adapter.NotificationServiceAdapter;
import com.shale.data.service.adapter.TaskServiceAdapter;
import com.shale.data.service.adapter.TaskPolicyConfigurationServiceAdapter;
import com.shale.server.runtime.BearerTokenServerSessionResolver;
import com.shale.server.runtime.DevelopmentHeaderServerSessionResolver;
import com.shale.server.runtime.DesktopApplicationInstanceVerifier;
import com.shale.server.runtime.RequestScopedDbSessionProvider;
import com.shale.server.runtime.GlobalControlPlaneDbSessionProvider;
import com.shale.server.runtime.RuntimeConnectionProvider;
import com.shale.server.runtime.ServerRuntimeSessionState;
import org.springframework.test.util.ReflectionTestUtils;
import com.shale.server.runtime.ServerSessionResolver;
import com.shale.server.runtime.UnauthenticatedServerSessionResolver;

class ShaleServerServiceConfigurationTest {

    @Test
    void constructsSharedServicePortAdaptersWithoutDatabaseConnection() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
                ShaleServerServiceConfiguration.class)) {
            assertInstanceOf(AuthServiceAdapter.class, context.getBean(AuthServicePort.class));
            assertInstanceOf(CaseServiceAdapter.class, context.getBean(CaseServicePort.class));
            assertInstanceOf(TaskServiceAdapter.class, context.getBean(TaskServicePort.class));
            assertInstanceOf(TaskPolicyConfigurationServiceAdapter.class,
                    context.getBean(TaskPolicyConfigurationServicePort.class));
            assertInstanceOf(ContactServiceAdapter.class, context.getBean(ContactServicePort.class));
            assertInstanceOf(NotificationServiceAdapter.class, context.getBean(NotificationServicePort.class));
            assertInstanceOf(ApplicationReleaseReadServiceAdapter.class,
                    context.getBean(ApplicationReleaseReadServicePort.class));
            assertNotNull(context.getBean(ServerRuntimeSessionState.class));
            assertInstanceOf(UnauthenticatedServerSessionResolver.class, context.getBean(ServerSessionResolver.class));
            assertInstanceOf(RequestScopedDbSessionProvider.class, context.getBean(DbSessionProvider.class));
            assertInstanceOf(GlobalControlPlaneDbSessionProvider.class,
                    context.getBean("globalControlPlaneDbSessionProvider", DbSessionProvider.class));

            Object importAdapter = context.getBean(ApplicationReleaseImportServicePort.class);
            Object importDao = ReflectionTestUtils.getField(importAdapter, "dao");
            assertSame(context.getBean("globalControlPlaneDbSessionProvider", DbSessionProvider.class),
                    ReflectionTestUtils.getField(importDao, "db"),
                    "Only the global release importer must bypass tenant request session resolution.");

            Object readAdapter = context.getBean(ApplicationReleaseReadServicePort.class);
            Object readGateway = ReflectionTestUtils.getField(readAdapter, "gateway");
            Object readDao = ReflectionTestUtils.getField(readGateway, "dao");
            assertSame(context.getBean(DbSessionProvider.class), ReflectionTestUtils.getField(readDao, "db"),
                    "Ordinary authenticated services must retain the request-scoped provider.");
        }
    }

    @Test
    void localProfileEnablesDevelopmentHeaderResolver() {
        withDatabaseProperties(() -> {
            try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
                context.getEnvironment().setActiveProfiles("local");
                context.register(ShaleServerServiceConfiguration.class);
                context.refresh();

                assertInstanceOf(com.shale.server.runtime.CompositeServerSessionResolver.class, context.getBean(ServerSessionResolver.class));
				assertNotNull(context.getBean(DesktopApplicationInstanceVerifier.class));
            }
        });
    }

    @Test
    void azureProfileDisablesDevelopmentHeaderResolverAndEnablesRuntimeConnections() {
        withDatabaseProperties(() -> {
            try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
                context.getEnvironment().setActiveProfiles("azure");
                context.register(ShaleServerServiceConfiguration.class);
                context.refresh();

                assertInstanceOf(BearerTokenServerSessionResolver.class, context.getBean(ServerSessionResolver.class));
                assertInstanceOf(com.shale.server.runtime.RuntimeSessionServiceConnectionProvider.class, context.getBean(com.shale.server.runtime.RuntimeConnectionProvider.class));
            }
        });
    }

    @Test
    void requestSessionPlaceholderDoesNotOpenDbConnectionWithoutContext() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
                ShaleServerServiceConfiguration.class)) {
            DbSessionProvider provider = context.getBean(DbSessionProvider.class);

            assertThrows(ResponseStatusException.class, provider::requireConnection);
        }
    }

    private static void withDatabaseProperties(Runnable test) {
        System.setProperty("SHALE_APP_DB_URL", "jdbc:sqlserver://example.invalid:1433;databaseName=ShaleApp");
        System.setProperty("SHALE_APP_DB_USER", "app_user");
        System.setProperty("SHALE_APP_DB_PASSWORD", "app_password");
        System.setProperty("SHALE_RT_DB_URL", "jdbc:sqlserver://example.invalid:1433;databaseName=ShaleRuntime");
        System.setProperty("SHALE_RT_DB_USER", "rt_user");
        System.setProperty("SHALE_RT_DB_PASSWORD", "rt_password");
        System.setProperty("SHALE_AUTH_TOKEN_SECRET", "test-auth-token-secret-that-is-long-enough");
        System.setProperty("SHALE_AUTH_SESSION_BINDING_CUTOVER_AT", "2026-09-29T18:00:00Z");
        try {
            test.run();
        } finally {
            System.clearProperty("SHALE_APP_DB_URL");
            System.clearProperty("SHALE_APP_DB_USER");
            System.clearProperty("SHALE_APP_DB_PASSWORD");
            System.clearProperty("SHALE_RT_DB_URL");
            System.clearProperty("SHALE_RT_DB_USER");
            System.clearProperty("SHALE_RT_DB_PASSWORD");
            System.clearProperty("SHALE_AUTH_TOKEN_SECRET");
            System.clearProperty("SHALE_AUTH_SESSION_BINDING_CUTOVER_AT");
        }
    }
}
