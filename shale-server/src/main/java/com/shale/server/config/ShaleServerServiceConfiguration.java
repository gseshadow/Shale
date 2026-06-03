package com.shale.server.config;

import java.sql.Connection;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.shale.core.runtime.DbSessionProvider;
import com.shale.core.service.AuthServicePort;
import com.shale.core.service.CaseServicePort;
import com.shale.core.service.ContactServicePort;
import com.shale.core.service.NotificationServicePort;
import com.shale.core.service.TaskServicePort;
import com.shale.data.auth.AuthService;
import com.shale.data.dao.CaseDao;
import com.shale.data.dao.ContactDao;
import com.shale.data.dao.NotificationDao;
import com.shale.data.dao.TaskDao;
import com.shale.data.errors.AuthException;
import com.shale.data.service.adapter.AuthServiceAdapter;
import com.shale.data.service.adapter.CaseServiceAdapter;
import com.shale.data.service.adapter.ContactServiceAdapter;
import com.shale.data.service.adapter.NotificationServiceAdapter;
import com.shale.data.service.adapter.TaskServiceAdapter;
import com.shale.server.runtime.ServerRuntimeSessionState;

@Configuration
public class ShaleServerServiceConfiguration {

    @Bean
    ServerRuntimeSessionState serverRuntimeSessionState() {
        return new ServerRuntimeSessionState();
    }

    @Bean
    DbSessionProvider serverDbSessionProvider() {
        return new DisabledServerDbSessionProvider();
    }

    @Bean
    AuthService serverAuthService() {
        return (email, password) -> {
            throw new AuthException(ServerRuntimeSessionState.NOT_IMPLEMENTED_MESSAGE);
        };
    }

    @Bean
    AuthServicePort authServicePort(AuthService serverAuthService) {
        return new AuthServiceAdapter(serverAuthService);
    }

    @Bean
    CaseServicePort caseServicePort(DbSessionProvider serverDbSessionProvider) {
        return new CaseServiceAdapter(new CaseDao(serverDbSessionProvider));
    }

    @Bean
    TaskServicePort taskServicePort(DbSessionProvider serverDbSessionProvider) {
        return new TaskServiceAdapter(new TaskDao(serverDbSessionProvider));
    }

    @Bean
    ContactServicePort contactServicePort(DbSessionProvider serverDbSessionProvider) {
        return new ContactServiceAdapter(new ContactDao(serverDbSessionProvider));
    }

    @Bean
    NotificationServicePort notificationServicePort(DbSessionProvider serverDbSessionProvider) {
        return new NotificationServiceAdapter(new NotificationDao(serverDbSessionProvider));
    }

    private static final class DisabledServerDbSessionProvider implements DbSessionProvider {
        @Override
        public Connection requireConnection() {
            throw new UnsupportedOperationException(ServerRuntimeSessionState.NOT_IMPLEMENTED_MESSAGE);
        }
    }
}
