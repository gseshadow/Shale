package com.shale.server.mcp;
import com.shale.data.security.ai.*;
import com.shale.server.oauth.*;
import com.shale.server.runtime.*;
import com.shale.server.auth.*;
import java.time.*;import java.util.*;import java.nio.file.*;
import org.springframework.context.annotation.*;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.beans.factory.ObjectProvider;
import jakarta.servlet.http.HttpServletRequest;
@SpringBootConfiguration
@EnableAutoConfiguration
@Import({McpOAuthSecurityConfiguration.class,McpTransportConfiguration.class,com.shale.server.controller.AuthController.class})
public class DcrConnectionServer {
 static final DcrConnectionFixture f=DcrConnectionFixture.CURRENT;
 @Bean RuntimeConnectionProvider connections(){return p->f.open("runtime",p.shaleClientId(),p.userId());}
 @Bean ShaleAuthTokenService tokens(){return new ShaleAuthTokenService(UUID.randomUUID()+UUID.randomUUID().toString(),3600,Clock.systemUTC());}
 @Bean TokenRevocationStore revocations(){return new InMemoryTokenRevocationStore();}
 @Bean DurableSessionStore sessions(RuntimeConnectionProvider c){return new SqlDurableSessionStore(c);}
 @Bean ServerAuthSessionService auth(ShaleAuthTokenService t,DurableSessionStore s,TokenRevocationStore r){return new ServerAuthSessionService(t,s,new DurableSessionTokenValidator(s),new LegacyTokenCompatibilityPolicy(Instant.EPOCH,3600,Clock.systemUTC()),r);}
 @Bean ServerRuntimeSessionState runtime(ShaleAuthTokenService t,ServerAuthSessionService a,ObjectProvider<HttpServletRequest> req){return new ServerRuntimeSessionState(new BearerTokenServerSessionResolver(t,a),req);}
 @Bean com.shale.core.service.AuthServicePort login(){return new com.shale.data.service.adapter.AuthServiceAdapter(new com.shale.data.auth.AuthServiceImpl(f.ds("auth"),new com.shale.data.auth.BCryptPasswordVerifier()));}
 @Bean CurrentUserProfileService profiles(ServerRuntimeSessionState s){return new UserDaoCurrentUserProfileService(new com.shale.data.dao.UserDao(()->{var p=s.requirePrincipal();return f.scoped("runtime",p.shaleClientId(),p.userId()).requireConnection();}));}
 @Bean(name="mcpAiCaseData") McpAiCaseData predecessor(McpOAuthProperties identity){return new McpAiCaseData(f.ds("ai"),f.ds("catalog"),List.of(f.ds("auth"),f.ds("runtime"),f.ds("bootstrap")),f.databaseName(),identity,AiCaseDataAttestation.Contract.V2,AiExternalContract.DCR_V3);}
 @Bean CimdCaseConnections external(){return new CimdCaseConnections(CimdCaseConnections.bounded(f.ds("v2case")),CimdCaseConnections.bounded(f.ds("cimdreview")));}
 @Bean com.shale.server.oauth.cimd.CimdMetadataService metadata(McpOAuthProperties p){return com.shale.server.oauth.cimd.LiveMetadata.service(p.issuer());}
 AiCimdOAuthStore neutral(String role){return new AiCimdOAuthStore(()->{try{return f.open(role,null,null);}catch(java.sql.SQLException e){throw new IllegalStateException(e);}},"https://shale.test/oauth/mcp","https://shale.test/mcp",AiExternalContract.DCR_V3);}
 AiCimdOAuthStore scoped(String role,AiOAuthExchangeStore.Owner o){return new AiCimdOAuthStore(f.scoped(role,o.tenantId(),o.userId()),"https://shale.test/oauth/mcp","https://shale.test/mcp",AiExternalContract.DCR_V3);}
 @Bean CimdServices services(){return new CimdServices(new AiApplicationRegistryService(()->{try{return f.open("publisher",null,null);}catch(java.sql.SQLException e){throw new IllegalStateException(e);}},"https://shale.test/oauth/mcp","https://shale.test/mcp"),o->new AiCimdOnboardingStore(f.scoped("v2onboarding",o.tenantId(),o.userId()),"https://shale.test/oauth/mcp","https://shale.test/mcp",AiExternalContract.DCR_V3),o->new AiTenantApplicationService(f.scoped("tenant",o.tenantId(),o.userId())),o->scoped("v2browser",o),o->scoped("v2issuer",o),neutral("v2metadata"),neutral("v2resolver"),neutral("v2neutral"));}
 @Bean DcrCapabilities dcr(){
  return new DcrCapabilities(new AiDcrApplicationStore(()->{try{return f.open("v3endpoint",null,null);}catch(java.sql.SQLException e){throw new IllegalStateException(e);}},"https://shale.test/oauth/mcp","https://shale.test/mcp",AiExternalContract.DCR_V3),
   new AiDcrApplicationStore(()->{try{return f.open("v2stored",null,null);}catch(java.sql.SQLException e){throw new IllegalStateException(e);}},"https://shale.test/oauth/mcp","https://shale.test/mcp",AiExternalContract.DCR_V3),
   new AiDcrAdmissionStore(()->{try{return f.open("v3endpoint",null,null);}catch(java.sql.SQLException e){throw new IllegalStateException(e);}}));
 }
 public static void start()throws Exception {
  var app=new SpringApplication(DcrConnectionServer.class);app.setAdditionalProfiles("test");app.setAllowBeanDefinitionOverriding(true);
  var p=new java.util.concurrent.ConcurrentHashMap<String,Object>();p.put("spring.main.allow-bean-definition-overriding",true);p.put("server.address","127.0.0.1");p.put("server.port","18080");p.put("shale.ai.data.database",f.databaseName());
  p.put("shale.ai.oauth.public-origin","https://shale.test");p.put("shale.ai.oauth.browser-origin","https://shale.test");p.put("shale.ai.oauth.identity","TEST");
  for(String flag:List.of("shale.ai.enabled","shale.ai.mcp.enabled","shale.ai.mcp.search-cases-enabled","shale.ai.mcp.get-case-overview-enabled","shale.ai.oauth.composition-enabled","shale.ai.oauth.authorization-enabled","shale.ai.oauth.exchange-enabled","shale.ai.cimd.enabled","shale.ai.dcr.enabled"))p.put(flag,true);
  p.put("shale.ai.cimd.metadata-advertisement-enabled",Boolean.parseBoolean(System.getenv().getOrDefault("SHALE_C2_CIMD_ADVERTISEMENT","true")));
  p.putAll(f.bootstrapProperties());p.put("shale.ai.mcp.allowed-origins","https://shale.test");p.put("logging.level.root","WARN");app.addInitializers(c->c.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("disposable",p)));
  try(var context=app.run()) {
   Files.writeString(Path.of("../work/pr-c2-harness/ready"),"ready");
   while(!Files.exists(Path.of("../work/pr-c2-harness/stop"))) {
    if(Files.exists(Path.of("../work/pr-c2-harness/advertise-cimd")))p.put("shale.ai.cimd.metadata-advertisement-enabled",true);
    Thread.sleep(500);
   }
  }
 }
}
