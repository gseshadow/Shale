package com.shale.server.mcp;

import com.shale.data.config.DataSources;
import com.shale.data.security.ai.AiAccessTokenValidator;
import com.shale.server.oauth.McpOAuthProperties;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.modern.McpSchema;
import io.modelcontextprotocol.modern.server.McpServer;
import io.modelcontextprotocol.modern.server.transport.HttpServletMcpTransport;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.savedrequest.NullRequestCache;
import tools.jackson.core.*;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.json.JsonMapper;
import java.util.List;
import java.util.Map;

@Configuration(proxyBeanMethods=false)
@EnableConfigurationProperties({McpTransportProperties.class,McpOAuthProperties.class})
public class McpTransportConfiguration {
    static JacksonMcpJsonMapper mapper() {
        McpPayloadLogFilter.install();
        var factory=JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(8).maxStringLength(8192).maxNumberLength(20).build()).build();
        return new JacksonMcpJsonMapper(JsonMapper.builder(factory).enable(tools.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build());
    }
    static McpServer server(String version, JacksonMcpJsonMapper mapper) {
        return McpServer.builder().serverInfo(McpSchema.Implementation.builder("shale-mcp",version).build())
                .supportedVersions(List.of(McpGate.PROTOCOL)).jsonMapper(mapper).feature(new McpCatalog()).build();
    }
    @Bean @Order(0)
    SecurityFilterChain mcpTransportChain(HttpSecurity http, McpTransportProperties transport, McpOAuthProperties identity,
            Environment env, ObjectProvider<AiAccessTokenValidator> validators, ObjectProvider<DeadlineAccess> bounded) throws Exception {
        transport.validate(identity);
        http.securityMatcher(request -> {
            String path=request.getRequestURI().substring(request.getContextPath().length());
            return path.equals("/mcp") || path.equals(com.shale.server.oauth.OAuthMetadataContract.PROTECTED_RESOURCE_PATH);
        });
        http.formLogin(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable).csrf(AbstractHttpConfigurer::disable).anonymous(AbstractHttpConfigurer::disable)
                .requestCache(c->c.requestCache(new NullRequestCache()))
                .securityContext(c->c.securityContextRepository(new NullSecurityContextRepository()))
                .sessionManagement(c->c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(c->c.anyRequest().permitAll());
        http.addFilterBefore(new McpGate(transport,identity,env.getProperty("shale.ai.enabled",Boolean.class,false),
                (token,deadline)->{var fixture=validators.getIfAvailable();return fixture!=null?fixture.validate(token):bounded.getObject().validate(token,deadline);},System::nanoTime),SecurityContextHolderFilter.class);
        return http.build();
    }
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean({AiAccessTokenValidator.class,DeadlineAccess.class})
    @ConditionalOnProperty(name="shale.ai.mcp.enabled",havingValue="true")
    DeadlineAccess mcpAccessAuthenticator(McpOAuthProperties identity, Environment env, ObjectProvider<DataSources> sources) {
        var source=new com.microsoft.sqlserver.jdbc.SQLServerDataSource();
        source.setURL(env.getRequiredProperty("shale.ai.oauth.bootstrap.jdbc-url"));
        source.setUser(env.getRequiredProperty("shale.ai.oauth.bootstrap.username"));
        source.setPassword(env.getRequiredProperty("shale.ai.oauth.bootstrap.password"));
        source.setLoginTimeout(2); source.setSocketTimeout(5000);
        return (token,deadline)-> {
            var bootstrap=new McpJdbcDeadline(source,deadline);
            var runtime=new McpJdbcDeadline(sources.getObject().runtime(),deadline);
            return new AiAccessTokenValidator(()-> {
                try { return bootstrap.getConnection(); }
                catch(java.sql.SQLException unavailable) { throw new SecurityException("Integration authentication failed."); }
            },runtime,env.getProperty("shale.ai.enabled",Boolean.class,false),identity.issuer(),identity.resource()).validate(token);
        };
    }
    @FunctionalInterface interface DeadlineAccess { com.shale.data.security.ai.VerifiedIntegrationContext validate(String token,long deadline); }
    @Bean
    @ConditionalOnProperty(name="shale.ai.mcp.enabled",havingValue="true")
    org.springframework.boot.web.servlet.FilterRegistrationBean<McpBodyFilter> mcpBodyAdmission() {
        var registration=new org.springframework.boot.web.servlet.FilterRegistrationBean<>(new McpBodyFilter());
        registration.addUrlPatterns("/mcp");registration.setDispatcherTypes(jakarta.servlet.DispatcherType.REQUEST,jakarta.servlet.DispatcherType.ASYNC);
        registration.setAsyncSupported(true);registration.setOrder(org.springframework.core.Ordered.HIGHEST_PRECEDENCE+10);
        return registration;
    }
    @Bean
    @ConditionalOnProperty(name="shale.ai.mcp.enabled",havingValue="true")
    ServletRegistrationBean<HttpServletMcpTransport> mcpServlet(ObjectProvider<BuildProperties> builds) {
        var build=builds.getIfAvailable();
        String version=build == null ? "development" : build.getVersion();
        var mapper=mapper();
        var servlet=HttpServletMcpTransport.builder(server(version,mapper)).jsonMapper(mapper).endpoint("/mcp")
                .maxRequestSize(8192).keepAliveInterval(null)
                // The namespace security gate uses exact configured Origin values before this servlet.
                .httpHeaderValidator(headers->{})
                .contextExtractor(request->McpTransportContext.create(Map.of(McpGate.AUTHORITY,request.getAttribute(McpGate.AUTHORITY))))
                .build();
        var registration=new ServletRegistrationBean<>(servlet,"/mcp");
        registration.setName("shaleMcp");registration.setAsyncSupported(true);registration.setLoadOnStartup(1);
        return registration;
    }
}
