package com.shale.server.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.shale.core.runtime.DbSessionProvider;
import com.shale.server.auth.CurrentUserProfileService;
import com.shale.server.auth.UserDaoCurrentUserProfileService;
import com.shale.core.service.AuthServicePort;
import com.shale.core.service.CaseServicePort;
import com.shale.core.service.ContactServicePort;
import com.shale.core.service.NotificationServicePort;
import com.shale.core.service.TaskServicePort;
import com.shale.data.auth.AuthService;
import com.shale.data.auth.AuthServiceImpl;
import com.shale.data.auth.BCryptPasswordVerifier;
import com.shale.data.config.Config;
import com.shale.data.config.DataSources;
import com.shale.data.dao.CaseDao;
import com.shale.data.dao.ContactDao;
import com.shale.data.dao.NotificationDao;
import com.shale.data.dao.TaskDao;
import com.shale.data.dao.UserDao;
import com.shale.data.errors.AuthException;
import com.shale.data.service.adapter.AuthServiceAdapter;
import com.shale.data.service.adapter.CaseServiceAdapter;
import com.shale.data.service.adapter.ContactServiceAdapter;
import com.shale.data.service.adapter.NotificationServiceAdapter;
import com.shale.data.service.adapter.TaskServiceAdapter;
import com.shale.server.health.AppDatabaseHealthCheck;
import com.shale.server.health.DataSourcesAppDatabaseHealthCheck;
import com.shale.server.runtime.BearerTokenServerSessionResolver;
import com.shale.server.runtime.CompositeServerSessionResolver;
import com.shale.server.runtime.DevelopmentHeaderServerSessionResolver;
import com.shale.server.runtime.RequestScopedDbSessionProvider;
import com.shale.server.runtime.RuntimeConnectionProvider;
import com.shale.server.runtime.RuntimeSessionServiceConnectionProvider;
import com.shale.server.runtime.ServerRuntimeSessionState;
import com.shale.server.runtime.ServerSessionResolver;
import com.shale.server.runtime.InMemoryTokenRevocationStore;
import com.shale.server.runtime.ShaleAuthTokenService;
import com.shale.server.runtime.TokenRevocationStore;
import com.shale.server.runtime.UnauthenticatedServerSessionResolver;

import jakarta.servlet.http.HttpServletRequest;

@Configuration
public class ShaleServerServiceConfiguration {

    @Bean
    @Profile({"prod", "azure"})
    ServerSessionResolver serverSessionResolver(ShaleAuthTokenService tokenService, TokenRevocationStore tokenRevocationStore) {
        return new BearerTokenServerSessionResolver(tokenService, tokenRevocationStore);
    }

    @Bean
    @Profile("!dev & !local & !prod & !azure")
    ServerSessionResolver unauthenticatedServerSessionResolver() {
        return new UnauthenticatedServerSessionResolver();
    }

    /**
     * TEMPORARY development-only header resolver. This trusts request headers only
     * while the Spring dev profile is active and is not browser/mobile auth.
     */
    @Bean
    @Profile({"dev", "local"})
    ServerSessionResolver developmentServerSessionResolver(ShaleAuthTokenService tokenService, TokenRevocationStore tokenRevocationStore) {
        return new CompositeServerSessionResolver(java.util.List.of(
                new BearerTokenServerSessionResolver(tokenService, tokenRevocationStore),
                new DevelopmentHeaderServerSessionResolver()));
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
    @Profile("!dev & !local & !prod & !azure")
    RuntimeConnectionProvider disabledRuntimeConnectionProvider() {
        return principal -> {
            throw new IllegalStateException(
                    "Runtime DB connections are disabled until the dev request-context simulation or real server auth is active.");
        };
    }

    @Bean
    @Profile({"dev", "local", "prod", "azure"})
    DataSources serverDataSources() {
        return new DataSources(new Config());
    }

    @Bean
    @Profile({"dev", "local", "prod", "azure"})
    AppDatabaseHealthCheck appDatabaseHealthCheck(DataSources serverDataSources) {
        return new DataSourcesAppDatabaseHealthCheck(serverDataSources);
    }

    @Bean
    @Profile({"dev", "local", "prod", "azure"})
    RuntimeConnectionProvider runtimeConnectionProvider(DataSources serverDataSources) {
        return new RuntimeSessionServiceConnectionProvider(serverDataSources.runtime());
    }

    @Bean
    TokenRevocationStore tokenRevocationStore() {
        return new InMemoryTokenRevocationStore();
    }

    @Bean
    @Profile({"dev", "local", "prod", "azure"})
    ShaleAuthTokenService shaleAuthTokenService() {
        return ShaleAuthTokenService.fromEnvironment();
    }

    @Bean
    @Profile("!dev & !local & !prod & !azure")
    ShaleAuthTokenService disabledShaleAuthTokenService() {
        return ShaleAuthTokenService.disabled();
    }

    @Bean
    @Profile({"dev", "local", "prod", "azure"})
    AuthService serverAuthService(DataSources serverDataSources) {
        return new AuthServiceImpl(serverDataSources, new BCryptPasswordVerifier());
    }

    @Bean
    @Profile("!dev & !local & !prod & !azure")
    AuthService disabledServerAuthService() {
        return (email, password) -> {
            throw new AuthException(ServerRuntimeSessionState.NOT_IMPLEMENTED_MESSAGE);
        };
    }

    @Bean
    AuthServicePort authServicePort(AuthService serverAuthService) {
        return new AuthServiceAdapter(serverAuthService);
    }

    @Bean
    CurrentUserProfileService currentUserProfileService(DbSessionProvider serverDbSessionProvider) {
        return new UserDaoCurrentUserProfileService(new UserDao(serverDbSessionProvider));
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
