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
import com.shale.server.runtime.RequestScopedDbSessionProvider;
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
    void requestSessionPlaceholderDoesNotOpenDbConnectionWithoutContext() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
                ShaleServerServiceConfiguration.class)) {
            DbSessionProvider provider = context.getBean(DbSessionProvider.class);

            assertThrows(ResponseStatusException.class, provider::requireConnection);
        }
    }
}
