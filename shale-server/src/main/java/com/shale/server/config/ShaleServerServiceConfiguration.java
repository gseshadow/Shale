package com.shale.server.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.shale.core.runtime.DbSessionProvider;
import com.shale.core.service.AuthServicePort;
import com.shale.core.service.CaseServicePort;
import com.shale.core.service.ContactServicePort;
import com.shale.core.service.NotificationServicePort;
import com.shale.core.service.TaskServicePort;
import com.shale.data.auth.AuthService;
import com.shale.data.config.Config;
import com.shale.data.config.DataSources;
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
import com.shale.server.runtime.DevelopmentHeaderServerSessionResolver;
import com.shale.server.runtime.RequestScopedDbSessionProvider;
import com.shale.server.runtime.RuntimeConnectionProvider;
import com.shale.server.runtime.RuntimeSessionServiceConnectionProvider;
import com.shale.server.runtime.ServerRuntimeSessionState;
import com.shale.server.runtime.ServerSessionResolver;
import com.shale.server.runtime.UnauthenticatedServerSessionResolver;

import jakarta.servlet.http.HttpServletRequest;

@Configuration
public class ShaleServerServiceConfiguration {

    @Bean
    @Profile("!dev")
    ServerSessionResolver serverSessionResolver() {
        return new UnauthenticatedServerSessionResolver();
    }

    /**
     * TEMPORARY development-only header resolver. This trusts request headers only
     * while the Spring dev profile is active and is not browser/mobile auth.
     */
    @Bean
    @Profile("dev")
    ServerSessionResolver developmentServerSessionResolver() {
        return new DevelopmentHeaderServerSessionResolver();
    }

    @Bean
    ServerRuntimeSessionState serverRuntimeSessionState(
            ServerSessionResolver serverSessionResolver,
            ObjectProvider<HttpServletRequest> currentRequest) {
        return new ServerRuntimeSessionState(serverSessionResolver, currentRequest);
    }

    @Bean
    DbSessionProvider serverDbSessionProvider(
            ServerSessionResolver serverSessionResolver,
            ObjectProvider<HttpServletRequest> currentRequest,
            RuntimeConnectionProvider runtimeConnectionProvider) {
        return new RequestScopedDbSessionProvider(serverSessionResolver, currentRequest, runtimeConnectionProvider);
    }

    @Bean
    @Profile("!dev")
    RuntimeConnectionProvider disabledRuntimeConnectionProvider() {
        return principal -> {
            throw new IllegalStateException(
                    "Runtime DB connections are disabled until the dev request-context simulation or real server auth is active.");
        };
    }

    @Bean
    @Profile("dev")
    DataSources serverDataSources() {
        return new DataSources(new Config());
    }

    @Bean
    @Profile("dev")
    RuntimeConnectionProvider runtimeConnectionProvider(DataSources serverDataSources) {
        return new RuntimeSessionServiceConnectionProvider(serverDataSources.runtime());
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
}
