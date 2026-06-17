package com.shale.server.config;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.web.server.ResponseStatusException;

import com.shale.core.runtime.DbSessionProvider;
import com.shale.core.service.AuthServicePort;
import com.shale.core.service.CaseServicePort;
import com.shale.core.service.ContactServicePort;
import com.shale.core.service.NotificationServicePort;
import com.shale.core.service.TaskServicePort;
import com.shale.data.service.adapter.AuthServiceAdapter;
import com.shale.data.service.adapter.CaseServiceAdapter;
import com.shale.data.service.adapter.ContactServiceAdapter;
import com.shale.data.service.adapter.NotificationServiceAdapter;
import com.shale.data.service.adapter.TaskServiceAdapter;
import com.shale.server.runtime.DevelopmentHeaderServerSessionResolver;
import com.shale.server.runtime.RequestScopedDbSessionProvider;
import com.shale.server.runtime.RuntimeConnectionProvider;
import com.shale.server.runtime.ServerRuntimeSessionState;
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
            assertInstanceOf(ContactServiceAdapter.class, context.getBean(ContactServicePort.class));
            assertInstanceOf(NotificationServiceAdapter.class, context.getBean(NotificationServicePort.class));
            assertNotNull(context.getBean(ServerRuntimeSessionState.class));
            assertInstanceOf(UnauthenticatedServerSessionResolver.class, context.getBean(ServerSessionResolver.class));
            assertInstanceOf(RequestScopedDbSessionProvider.class, context.getBean(DbSessionProvider.class));
        }
    }

    @Test
    void localProfileEnablesDevelopmentHeaderResolver() {
        withDatabaseProperties(() -> {
            try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
                context.getEnvironment().setActiveProfiles("local");
                context.register(ShaleServerServiceConfiguration.class);
                context.refresh();

                assertInstanceOf(DevelopmentHeaderServerSessionResolver.class, context.getBean(ServerSessionResolver.class));
            }
        });
    }

    @Test
    void azureProfileDisablesDevelopmentHeaderResolverAndRuntimeConnections() {
        withDatabaseProperties(() -> {
            try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
                context.getEnvironment().setActiveProfiles("azure");
                context.register(ShaleServerServiceConfiguration.class);
                context.refresh();

                assertInstanceOf(UnauthenticatedServerSessionResolver.class, context.getBean(ServerSessionResolver.class));
                RuntimeConnectionProvider provider = context.getBean(RuntimeConnectionProvider.class);
                assertThrows(IllegalStateException.class, () -> provider.openConnection(null));
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
        try {
            test.run();
        } finally {
            System.clearProperty("SHALE_APP_DB_URL");
            System.clearProperty("SHALE_APP_DB_USER");
            System.clearProperty("SHALE_APP_DB_PASSWORD");
            System.clearProperty("SHALE_RT_DB_URL");
            System.clearProperty("SHALE_RT_DB_USER");
            System.clearProperty("SHALE_RT_DB_PASSWORD");
        }
    }
}
