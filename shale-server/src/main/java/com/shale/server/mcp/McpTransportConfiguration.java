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
        return new JacksonMcpJsonMapper(JsonMapper.builder(factory).build());
    }
    static McpServer server(String version, JacksonMcpJsonMapper mapper) {
        return McpServer.builder().serverInfo(McpSchema.Implementation.builder("shale-mcp",version).build())
                .supportedVersions(List.of(McpGate.PROTOCOL)).jsonMapper(mapper).feature(new McpCatalog()).build();
    }
    @Bean @Order(0)
    SecurityFilterChain mcpTransportChain(HttpSecurity http, McpTransportProperties transport, McpOAuthProperties identity,
            Environment env, ObjectProvider<AiAccessTokenValidator> validators) throws Exception {
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
                token->validators.getObject().validate(token)),SecurityContextHolderFilter.class);
        return http.build();
    }
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(AiAccessTokenValidator.class)
    @ConditionalOnProperty(name="shale.ai.mcp.enabled",havingValue="true")
    AiAccessTokenValidator mcpAccessValidator(McpOAuthProperties identity, Environment env, ObjectProvider<DataSources> sources) {
        var source=new com.microsoft.sqlserver.jdbc.SQLServerDataSource();
        source.setURL(env.getRequiredProperty("shale.ai.oauth.bootstrap.jdbc-url"));
        source.setUser(env.getRequiredProperty("shale.ai.oauth.bootstrap.username"));
        source.setPassword(env.getRequiredProperty("shale.ai.oauth.bootstrap.password"));
        source.setLoginTimeout(2); source.setSocketTimeout(5000);
        return new AiAccessTokenValidator(()-> {
            try { return source.getConnection(); }
            catch(java.sql.SQLException unavailable) { throw new SecurityException("Integration authentication failed."); }
        },sources.getObject().runtime(),env.getProperty("shale.ai.enabled",Boolean.class,false),identity.issuer(),identity.resource());
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
        registration.setName("shaleMcp");registration.setAsyncSupported(false);registration.setLoadOnStartup(1);
        return registration;
    }
}
