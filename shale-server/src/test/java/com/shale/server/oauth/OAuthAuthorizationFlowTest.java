package com.shale.server.oauth;

import com.shale.core.dto.UserSessionView;
import com.shale.core.model.ClientType;
import com.shale.data.security.ai.*;
import com.shale.server.runtime.*;
import java.net.*;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.*;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.web.context.request.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import static com.shale.core.security.ai.AiScope.*;
import static org.junit.jupiter.api.Assertions.*;

/** SYNTHETIC SQL plus actual maintained servlet filters. Durable WEB store is a test identity fixture;
 * ordinary production WEB/DESKTOP authority is separately covered by existing auth regressions. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OAuthAuthorizationFlowTest {
    static final String ISSUER="https://shale.test/oauth/mcp",RESOURCE="https://shale.test/mcp",REDIRECT="https://client.test/callback",VERIFIER="v".repeat(43);
    static ShaleAuthTokenService tokens;
    static ShaleOAuthHumanTest.FixtureSessions sessions;
    static RuntimeConnectionProvider runtime;
    AnnotationConfigWebApplicationContext context;
    String port,password,db,login,secret,bootstrapLogin,bootstrapSecret,webToken,client,otherClient;
    UUID sid;
    Connection owner(String database)throws Exception {return DriverManager.getConnection(url(database),"sa",password);}
    String url(String database){return "jdbc:sqlserver://127.0.0.1:"+port+";databaseName="+database+";encrypt=true;trustServerCertificate=true;loginTimeout=5;socketTimeout=10000";}
    static void sql(Connection c,String text)throws SQLException {try(var s=c.createStatement()){s.setQueryTimeout(10);s.execute(text);}}
    @BeforeAll void setup()throws Exception {
        Assumptions.assumeTrue("LOCAL_DISPOSABLE_AI".equals(System.getenv("SHALE_AI_SQL_OPT_IN")),"Disposable loopback SQL opt-in required; skip is not evidence");
        port=System.getenv("SHALE_AI_SQL_PORT");password=System.getenv("SHALE_AI_SQL_PASSWORD");assertNotNull(port);assertTrue(port.matches("[0-9]{4,5}"));
        db="AiOAuth_"+UUID.randomUUID().toString().replace("-","");login=db+"_runtime";secret=UUID.randomUUID()+"aB9!";bootstrapLogin=db+"_bootstrap";bootstrapSecret=UUID.randomUUID()+"aB9!";
        try(var c=owner("master")){sql(c,"CREATE DATABASE ["+db+"]");sql(c,"CREATE LOGIN ["+login+"] WITH PASSWORD='"+secret+"',CHECK_POLICY=OFF");}
        try(var c=owner("master")){sql(c,"CREATE LOGIN ["+bootstrapLogin+"] WITH PASSWORD='"+bootstrapSecret+"',CHECK_POLICY=OFF");}
        try(var c=owner(db)) {
            for(String batch:Files.readString(Path.of("../shale-data/src/test/resources/ai/synthetic-prerequisites.sql")).split("(?im)^GO\\s*$"))if(!batch.isBlank())sql(c,batch);
            sql(c,Files.readString(Path.of("../docs/sql/ai-integration/phase3b1.sql")));
            sql(c,"CREATE TABLE dbo.UserSessions(SessionId uniqueidentifier PRIMARY KEY,ShaleClientId int,UserId int,ClientType varchar(16),ExpiresAt datetime2(7),RevokedAt datetime2(7))");
            sql(c,Files.readString(Path.of("../docs/sql/ai-integration/phase3b2b.sql")));
            sql(c,Files.readString(Path.of("../docs/sql/ai-integration/phase3b2c1.sql")));
            sql(c,Files.readString(Path.of("../docs/sql/ai-integration/phase3b2c2.sql")));
            sql(c,Files.readString(Path.of("../docs/sql/ai-integration/revocation.sql")));
            sql(c,Files.readString(Path.of("../docs/sql/ai-integration/phase3b2d.sql")));
            for(String batch:Files.readString(Path.of("../shale-data/src/test/resources/ai/synthetic-case-read.sql")).split("(?im)^GO\\s*$"))
                if(!batch.isBlank())sql(c,batch);
            sql(c,Files.readString(Path.of("../docs/sql/ai-integration/phase3b3.sql")));
            sql(c,"CREATE USER ["+bootstrapLogin+"] FOR LOGIN ["+bootstrapLogin+"]; ALTER ROLE AiIntegrationBootstrap ADD MEMBER ["+bootstrapLogin+"]");
            sql(c,"CREATE USER ["+login+"] FOR LOGIN ["+login+"]; ALTER ROLE AiIntegrationRuntime ADD MEMBER ["+login+"]; ALTER ROLE AiCapabilityOperator ADD MEMBER ["+login+"]");
        }
        runtime=p->{Connection c=DriverManager.getConnection(url(db),login,secret);sql(c,"EXEC sys.sp_set_session_context @key=N'ShaleClientId',@value="+p.shaleClientId()+"; EXEC sys.sp_set_session_context @key=N'PrincipalUserId',@value="+p.userId());return c;};
        new AiProductCapabilityService(()->{try{return DriverManager.getConnection(url(db),login,secret);}catch(SQLException e){throw new IllegalStateException(e);}})
                .setEnabled(true,new AiProductCapabilityService(()->{try{return DriverManager.getConnection(url(db),login,secret);}catch(SQLException e){throw new IllegalStateException(e);}}).inspect().rowVersion());
        var controls=new AiIntegrationSecurityService(()->{try{return runtime.openConnection(new ServerPrincipal(71,7,null));}catch(SQLException e){throw new IllegalStateException(e);}},true,ISSUER,RESOURCE);
        controls.setTenantEnabled(true,null);
        client=controls.register("Synthetic client",false,Set.of(CASES_SEARCH,CASES_OVERVIEW_READ),1,List.of(URI.create(REDIRECT))).clientId();
        otherClient=controls.register("Other synthetic client",false,Set.of(CASES_SEARCH),1,List.of(URI.create(REDIRECT))).clientId();
        tokens=new ShaleAuthTokenService("synthetic-oauth-flow-secret-at-least-32-bytes",3600,Clock.systemUTC());
        var principal=new ServerPrincipal(72,7,null);var prepared=tokens.prepare(principal);sid=UUID.randomUUID();webToken=tokens.issueBound(prepared,sid);
        sessions=new ShaleOAuthHumanTest.FixtureSessions();sessions.current=new UserSessionView(1,sid,ClientType.WEB,null,prepared.tokenId(),Instant.now(),Instant.now().plusSeconds(3600),null,null,null);
        try(var c=owner(db)){sql(c,"INSERT dbo.UserSessions VALUES('"+sid+"',7,72,'WEB',DATEADD(hour,2,SYSUTCDATETIME()),NULL)");}
        context=new AnnotationConfigWebApplicationContext();context.setServletContext(new MockServletContext());context.getEnvironment().setActiveProfiles("test");
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("oauth",Map.of("shale.ai.oauth.public-origin","https://shale.test","shale.ai.oauth.identity","TEST","shale.ai.oauth.composition-enabled","true","shale.ai.oauth.authorization-enabled","true","shale.ai.oauth.exchange-enabled","true","shale.ai.oauth.bootstrap.jdbc-url",url(db),"shale.ai.oauth.bootstrap.username",bootstrapLogin,"shale.ai.oauth.bootstrap.password",bootstrapSecret)));
        context.register(McpOAuthSecurityConfiguration.class,Fixtures.class);context.refresh();
    }
    @Configuration static class Fixtures {
        @Bean ShaleAuthTokenService tokens(){return tokens;}
        @Bean DurableSessionStore sessions(){return sessions;}
        @Bean RuntimeConnectionProvider runtime(){return runtime;}
        @Bean ServerAuthSessionService auth(){return new ServerAuthSessionService(tokens,sessions,new DurableSessionTokenValidator(sessions),new LegacyTokenCompatibilityPolicy(Instant.EPOCH,3600,Clock.systemUTC()),new InMemoryTokenRevocationStore());}
    }
    @AfterAll void cleanup()throws Exception {
        if(context!=null)context.close();
        if(db!=null)try(var c=owner("master")){sql(c,"ALTER DATABASE ["+db+"] SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE ["+db+"]");sql(c,"DROP LOGIN ["+login+"]");sql(c,"DROP LOGIN ["+bootstrapLogin+"]");}
    }
    MockHttpServletRequest authorization()throws Exception {
        var r=new MockHttpServletRequest("GET","/oauth/mcp/authorize");r.addParameter("response_type","code");r.addParameter("client_id",client);r.addParameter("redirect_uri",REDIRECT);r.addParameter("resource",RESOURCE);r.addParameter("scope","cases.search cases.overview.read");r.addParameter("state","exact + & = é");r.addParameter("code_challenge_method","S256");
        r.addParameter("code_challenge",Base64.getUrlEncoder().withoutPadding().encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(VERIFIER.getBytes(java.nio.charset.StandardCharsets.US_ASCII))));return r;
    }
    MockHttpServletResponse send(MockHttpServletRequest r)throws Exception {
        r.setServletPath(r.getRequestURI());
        if(r.getMethod().equals("GET")) {
            var encoded=new ArrayList<String>();r.getParameterMap().forEach((k,values)->{for(var value:values)encoded.add(URLEncoder.encode(k,java.nio.charset.StandardCharsets.UTF_8)+"="+URLEncoder.encode(value,java.nio.charset.StandardCharsets.UTF_8));});
            r.setQueryString(String.join("&",encoded));
        } else r.setContentType("application/x-www-form-urlencoded");
        if(r.getAttribute("skipAuthorization")==null)r.addHeader("Authorization","Bearer "+webToken);r.addHeader("Host","attacker.invalid");r.addHeader("X-Forwarded-Host","attacker.invalid");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(r));
        try {var response=new MockHttpServletResponse();context.getBean(FilterChainProxy.class).doFilter(r,response,(q,s)->fail("OAuth request escaped filters"));return response;}
        finally {RequestContextHolder.resetRequestAttributes();}
    }
    static Map<String,String> query(String location){var result=new HashMap<String,String>();for(String entry:URI.create(location).getRawQuery().split("&")){var kv=entry.split("=",2);result.put(URLDecoder.decode(kv[0],java.nio.charset.StandardCharsets.UTF_8),URLDecoder.decode(kv[1],java.nio.charset.StandardCharsets.UTF_8));}return result;}
    MockHttpServletRequest approve(String state){var r=new MockHttpServletRequest("POST","/oauth/mcp/authorize");r.addParameter("client_id",client);r.addParameter("state",state);r.addParameter("scope","cases.search");return r;}
    @Test void maintainedAuthorizationConsentAndCodeFlowPreservesStateAndIssuerAndNeverIssuesTokens()throws Exception {
        var begun=send(authorization());assertEquals(302,begun.getStatus(),"AS must begin explicit consent");var consent=query(begun.getRedirectedUrl());
        String nonce=consent.get("state");assertNotEquals("exact + & = é",nonce);
        var inspect=new MockHttpServletRequest("GET","/oauth/mcp/consent");inspect.addParameter("state",nonce);inspect.addParameter("client_id",client);
        assertEquals(200,send(inspect).getStatus());
        var response=send(approve(nonce));assertEquals(302,response.getStatus(),"AS must issue a code after durable self-consent");
        assertTrue(response.getRedirectedUrl().startsWith(REDIRECT+"?"));var result=query(response.getRedirectedUrl());
        assertEquals("exact + & = é",result.get("state"));assertEquals(ISSUER,result.get("iss"));assertTrue(result.get("code").matches("[A-Za-z0-9_-]{43}"));
        assertEquals(400,send(approve(nonce)).getStatus(),"Reused intent must fail");
        var store=new AiOAuthTransactionStore(()->{try{return runtime.openConnection(new ServerPrincipal(72,7,null));}catch(SQLException e){throw new IllegalStateException(e);}},true,ISSUER,RESOURCE);
        assertEquals(Set.of(CASES_SEARCH),store.consume(result.get("code"),sid,client,REDIRECT,VERIFIER,ISSUER,RESOURCE).scopes());
        var token=new MockHttpServletRequest("POST","/oauth/mcp/token");token.addParameter("grant_type","authorization_code");token.addParameter("code",result.get("code"));
        var blocked=send(token);assertEquals(400,blocked.getStatus());assertFalse(blocked.getContentAsString().contains("access_token"));
    }
    String freshCode()throws Exception {return query(send(approve(query(send(authorization()).getRedirectedUrl()).get("state"))).getRedirectedUrl()).get("code");}
    MockHttpServletRequest exchangeRequest(String code) {
        var r=new MockHttpServletRequest("POST","/oauth/mcp/token");r.setAttribute("skipAuthorization",true);
        Map.of("grant_type","authorization_code","client_id",client,"code",code,"redirect_uri",REDIRECT,"resource",RESOURCE,"code_verifier",VERIFIER).forEach(r::setParameter);
        return r;
    }
    @Test void maintainedPublicTokenEndpointIssuesOnlyBoundedOpaqueCredentials()throws Exception {
        String code=freshCode();
        for(String[] bad:List.of(new String[]{"code_verifier","w".repeat(43)},new String[]{"redirect_uri",REDIRECT+"/"},new String[]{"resource",RESOURCE+"/"},new String[]{"client_id",otherClient},new String[]{"client_secret","secret"})) {
            var r=exchangeRequest(code);r.setParameter(bad[0],bad[1]);var denied=send(r);assertTrue(denied.getStatus()>=400);assertFalse(denied.getContentAsString().contains("access_token"));
        }
        var success=send(exchangeRequest(code));assertEquals(200,success.getStatus(),success.getContentAsString());
        var body=new tools.jackson.databind.ObjectMapper().readTree(success.getContentAsString());
        var fields=new HashSet<String>();body.propertyNames().forEach(fields::add);
        assertEquals(Set.of("access_token","refresh_token","token_type","expires_in","scope"),fields);
        assertEquals("Bearer",body.path("token_type").asText());assertTrue(body.path("expires_in").asInt()<=300);
        assertTrue(body.path("access_token").asText().matches("[A-Za-z0-9_-]{43}"));assertTrue(body.path("refresh_token").asText().matches("[A-Za-z0-9_-]{43}"));
        var replay=send(exchangeRequest(code));assertTrue(replay.getStatus()>=400);assertFalse(replay.getContentAsString().contains("access_token"));
        try(var c=owner(db);var stmt=c.createStatement();var rows=stmt.executeQuery("SELECT (SELECT COUNT(*) FROM dbo.AiOAuthCredential)+(SELECT COUNT(*) FROM dbo.AiDelegatedSession)")){assertTrue(rows.next());assertTrue(rows.getInt(1)>0);}
        assertNull(success.getHeader("Authorization"));assertEquals(0,success.getCookies().length);
    }
    @Test void enabledTokenNamespaceRejectsEveryOtherGrantOrdinaryAuthAndMisleadingMetadata()throws Exception {
        String code=freshCode();
        for(String grant:List.of("password","implicit","client_credentials","urn:ietf:params:oauth:grant-type:token-exchange","urn:ietf:params:oauth:grant-type:device_code","extension")) {
            var r=exchangeRequest(code);r.setParameter("grant_type",grant);var denied=send(r);assertEquals(400,denied.getStatus());assertTrue(denied.getContentAsString().contains("unsupported_grant_type"));
        }
        for(String auth:List.of("Bearer "+webToken,"Bearer "+"r".repeat(43),"Basic YTpi")) {
            var r=exchangeRequest(code);r.addHeader("Authorization",auth);assertEquals(400,send(r).getStatus());
        }
        var metadata=send(new MockHttpServletRequest("GET",OAuthMetadataContract.AUTHORIZATION_SERVER_PATH));
        assertEquals(200,metadata.getStatus());
        var claims=new tools.jackson.databind.ObjectMapper().readTree(metadata.getContentAsString());
        assertEquals(ISSUER+"/token",claims.path("token_endpoint").asText());
        assertEquals(2,claims.path("grant_types_supported").size());assertEquals(2,claims.path("token_endpoint_auth_methods_supported").size());
        var valid=send(exchangeRequest(code));assertEquals(200,valid.getStatus());
    }
    @Test void maintainedConfidentialEndpointRequiresPinnedJwtAndPkceAndRejectsReplayAcrossCodes()throws Exception {
        var controls=new AiIntegrationSecurityService(()->{try{return runtime.openConnection(new ServerPrincipal(71,7,null));}catch(SQLException e){throw new IllegalStateException(e);}},true,ISSUER,RESOURCE);
        var g=java.security.KeyPairGenerator.getInstance("RSA");g.initialize(2048);var pair=g.generateKeyPair();
        var reg=controls.register("Synthetic confidential",true,Set.of(CASES_SEARCH,CASES_OVERVIEW_READ),1,List.of(URI.create(REDIRECT)));
        var key=controls.rotateClientKey(reg.integrationId(),(java.security.interfaces.RSAPublicKey)pair.getPublic(),1,reg.rowVersion());
        String original=client;client=reg.clientId();
        try {
            String first=freshCode(),second=freshCode();var now=Instant.now().minusSeconds(1);
            var jwt=new com.nimbusds.jwt.SignedJWT(new com.nimbusds.jose.JWSHeader.Builder(com.nimbusds.jose.JWSAlgorithm.RS256).keyID(key.credentialId().toString()).build(),
                    new com.nimbusds.jwt.JWTClaimsSet.Builder().issuer(client).subject(client).audience(ISSUER+"/token").issueTime(java.util.Date.from(now)).expirationTime(java.util.Date.from(now.plusSeconds(60))).jwtID(UUID.randomUUID().toString()).build());
            jwt.sign(new com.nimbusds.jose.crypto.RSASSASigner(pair.getPrivate()));String assertion=jwt.serialize();
            var missing=send(exchangeRequest(first));assertTrue(missing.getStatus()>=400);assertFalse(missing.getContentAsString().contains("access_token"));
            var wrong=exchangeRequest(first);wrong.setParameter("client_assertion_type","urn:ietf:params:oauth:client-assertion-type:jwt-bearer");wrong.setParameter("client_assertion",assertion);wrong.setParameter("code_verifier","w".repeat(43));
            assertFalse(send(wrong).getContentAsString().contains("access_token"));
            var valid=exchangeRequest(first);valid.setParameter("client_assertion_type","urn:ietf:params:oauth:client-assertion-type:jwt-bearer");valid.setParameter("client_assertion",assertion);
            var result=send(valid);assertEquals(200,result.getStatus(),result.getContentAsString());assertTrue(result.getContentAsString().contains("access_token"));
            var replay=exchangeRequest(second);replay.setParameter("client_assertion_type","urn:ietf:params:oauth:client-assertion-type:jwt-bearer");replay.setParameter("client_assertion",assertion);
            assertFalse(send(replay).getContentAsString().contains("access_token"));
            var mapper=new tools.jackson.databind.ObjectMapper();String refresh=mapper.readTree(result.getContentAsString()).path("refresh_token").asText();
            assertTrue(send(refreshRequest(refresh)).getStatus()>=400,"Refresh possession cannot bypass confidential client authentication");
            var invalid=refreshRequest(refresh);authenticate(invalid,signedAssertion(key.credentialId(),g.generateKeyPair().getPrivate()));
            assertTrue(send(invalid).getStatus()>=400,"A wrong assertion signature must fail before refresh consumption");
            var usedJti=refreshRequest(refresh);authenticate(usedJti,assertion);assertTrue(send(usedJti).getStatus()>=400,"Initial JTI remains shared replay authority");
            var rotate=refreshRequest(refresh);authenticate(rotate,signedAssertion(key.credentialId(),pair.getPrivate()));
            var rotated=send(rotate);assertEquals(200,rotated.getStatus(),rotated.getContentAsString());
            String replacement=mapper.readTree(rotated.getContentAsString()).path("refresh_token").asText();
            var reuse=refreshRequest(refresh);authenticate(reuse,signedAssertion(key.credentialId(),pair.getPrivate()));assertEquals(400,send(reuse).getStatus());
            var descendant=refreshRequest(replacement);authenticate(descendant,signedAssertion(key.credentialId(),pair.getPrivate()));assertEquals(400,send(descendant).getStatus());
        } finally {client=original;}
    }
    @Test void approvalRejectsCrossClientAndAllBindingSubstitutionsAndAllowsBothExplicitScopes()throws Exception {
        String nonce=query(send(authorization()).getRedirectedUrl()).get("state");
        for(String[] bad:List.of(new String[]{"client_id",otherClient},new String[]{"redirect_uri","https://evil.test/callback"},
                new String[]{"resource",RESOURCE+"/"},new String[]{"user_id","73"},new String[]{"integration_id",UUID.randomUUID().toString()},
                new String[]{"code_challenge","x".repeat(43)},new String[]{"issuer",ISSUER+"/"},new String[]{"scope","admin"})) {
            var r=approve(nonce);r.setParameter(bad[0],bad[1]);var result=send(r);assertEquals(400,result.getStatus(),bad[0]);assertNull(result.getRedirectedUrl());
        }
        var valid=approve(nonce);valid.setParameter("scope","cases.search","cases.overview.read");assertEquals(302,send(valid).getStatus());
        String denied=query(send(authorization()).getRedirectedUrl()).get("state");var decline=approve(denied);decline.removeParameter("scope");var declined=send(decline);assertEquals(302,declined.getStatus());
        var error=query(declined.getRedirectedUrl());assertEquals("access_denied",error.get("error"));assertEquals("exact + & = é",error.get("state"));assertEquals(ISSUER,error.get("iss"));
        assertEquals(400,send(approve(denied)).getStatus(),"Denied intent cannot be reused");
    }
    @Test void noSessionOrSpringCookieOrDesktopCredentialCanAuthorize()throws Exception {
        var absent=authorization();absent.setAttribute("skipAuthorization",true);
        absent.getSession().setAttribute("SPRING_SECURITY_CONTEXT",new org.springframework.security.core.context.SecurityContextImpl(
                org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated("other-user","",List.of())));
        assertEquals(401,send(absent).getStatus());
        var remembered=authorization();remembered.setAttribute("skipAuthorization",true);remembered.addHeader("Authorization","Bearer "+"x".repeat(43));assertEquals(401,send(remembered).getStatus());
        var basic=authorization();basic.setAttribute("skipAuthorization",true);basic.addHeader("Authorization","Basic YTpi");assertEquals(401,send(basic).getStatus());
    }
    @Test void hostileAuthorizationAndConsentParametersAreRejectedWithoutRedirect()throws Exception {
        for(String[] bad:List.of(new String[]{"response_type","token"},new String[]{"resource",RESOURCE+"/"},new String[]{"redirect_uri","https://evil.test/callback"},
                new String[]{"code_challenge_method","plain"},new String[]{"scope","admin"},new String[]{"state",""},new String[]{"user_id","73"},new String[]{"issuer","https://evil.test"})) {
            var r=authorization();r.setParameter(bad[0],bad[1]);var response=send(r);assertEquals(400,response.getStatus(),bad[0]);assertNull(response.getRedirectedUrl());
        }
        for(String missing:List.of("resource","state","scope","code_challenge","code_challenge_method")){var r=authorization();r.removeParameter(missing);assertEquals(400,send(r).getStatus(),missing);}
        var multiple=authorization();multiple.setParameter("resource",RESOURCE,RESOURCE);assertEquals(400,send(multiple).getStatus());
        assertEquals(400,send(approve("a".repeat(43))).getStatus(),"Unbound CSRF state must fail");
        var substituted=authorization();substituted.setParameter("client_id","unknown");assertEquals(400,send(substituted).getStatus());
    }
    private String signedAssertion(UUID key,java.security.PrivateKey signing)throws Exception {
        return signedAssertion(key,signing,"/token",UUID.randomUUID().toString());
    }
    private String signedAssertion(UUID key,java.security.PrivateKey signing,String endpoint,String jti)throws Exception {
        var now=Instant.now().minusSeconds(1);
        var jwt=new com.nimbusds.jwt.SignedJWT(new com.nimbusds.jose.JWSHeader.Builder(com.nimbusds.jose.JWSAlgorithm.RS256).keyID(key.toString()).build(),
                new com.nimbusds.jwt.JWTClaimsSet.Builder().issuer(client).subject(client).audience(ISSUER+endpoint)
                        .issueTime(java.util.Date.from(now)).expirationTime(java.util.Date.from(now.plusSeconds(60))).jwtID(jti).build());
        jwt.sign(new com.nimbusds.jose.crypto.RSASSASigner(signing));return jwt.serialize();
    }
    private void authenticate(MockHttpServletRequest request,String assertion) {
        request.setParameter("client_assertion_type","urn:ietf:params:oauth:client-assertion-type:jwt-bearer");request.setParameter("client_assertion",assertion);
    }
    MockHttpServletRequest refreshRequest(String refresh) {
        var r=new MockHttpServletRequest("POST","/oauth/mcp/token");r.setAttribute("skipAuthorization",true);
        Map.of("grant_type","refresh_token","client_id",client,"refresh_token",refresh,"resource",RESOURCE).forEach(r::setParameter);return r;
    }
    @Test void maintainedPublicRefreshRotatesAndLostResponseRetryRevokesReplacement()throws Exception {
        var mapper=new tools.jackson.databind.ObjectMapper();
        var initial=send(exchangeRequest(freshCode()));assertEquals(200,initial.getStatus(),initial.getContentAsString());
        var first=mapper.readTree(initial.getContentAsString());String old=first.path("refresh_token").asText();
        var rotated=send(refreshRequest(old));assertEquals(200,rotated.getStatus(),rotated.getContentAsString());
        var second=mapper.readTree(rotated.getContentAsString());assertNotEquals(old,second.path("refresh_token").asText());
        assertEquals(400,send(refreshRequest(old)).getStatus());
        assertEquals(400,send(refreshRequest(second.path("refresh_token").asText())).getStatus());
        for(String token:List.of(first.path("access_token").asText(),second.path("access_token").asText())) {
            assertTrue(tokens.verifyToken(token).isEmpty(),"Ordinary Shale JWT authority must reject the opaque AI token");
            var request=new MockHttpServletRequest("GET","/mcp");request.addHeader("Authorization","Bearer "+token);
            var response=new MockHttpServletResponse();var escaped=new java.util.concurrent.atomic.AtomicBoolean();
            context.getBean(FilterChainProxy.class).doFilter(request,response,(q,res)->escaped.set(true));
            assertTrue(escaped.get(),"No bearer/MCP security surface may accept these credentials");
        }
    }

    MockHttpServletRequest revokeRequest(String token) {
        var r=new MockHttpServletRequest("POST","/oauth/mcp/revoke");r.setAttribute("skipAuthorization",true);
        r.setParameter("client_id",client);r.setParameter("token",token);return r;
    }
    @Test void publicRevocationIsEmpty200ForBothKindsUnknownMalformedForeignAndRepeated()throws Exception {
        var mapper=new tools.jackson.databind.ObjectMapper();
        for(String kind:List.of("access_token","refresh_token")) {
            var first=mapper.readTree(send(exchangeRequest(freshCode())).getContentAsString());
            var second=mapper.readTree(send(refreshRequest(first.path("refresh_token").asText())).getContentAsString());
            String credential=first.path(kind).asText();
            for(String presented:List.of("unknown","?not-an-opaque-token",credential,credential)) {
                var r=revokeRequest(presented);r.setParameter("token_type_hint",kind.equals("access_token")?"refresh_token":"future-hint");
                var result=send(r);assertEquals(200,result.getStatus(),result.getContentAsString());assertEquals("",result.getContentAsString());assertEquals("no-store",result.getHeader("Cache-Control"));
            }
            assertEquals(400,send(refreshRequest(second.path("refresh_token").asText())).getStatus(),"All descendants are revoked");
        }
        var live=mapper.readTree(send(exchangeRequest(freshCode())).getContentAsString());
        var foreign=revokeRequest(live.path("access_token").asText());foreign.setParameter("client_id",otherClient);
        var response=send(foreign);assertEquals(200,response.getStatus());assertEquals("",response.getContentAsString());
        assertEquals(200,send(refreshRequest(live.path("refresh_token").asText())).getStatus(),"Foreign client cannot revoke");
    }
    @Test void revocationRejectsSecretsCallerAuthorityAndOrdinarySessionAuthentication()throws Exception {
        for(String field:List.of("client_secret","tenant_id","user_id","integration_id")) {
            var r=revokeRequest("unknown");r.setParameter(field,"sentinel");assertEquals(400,send(r).getStatus());
        }
        for(String header:List.of("Basic YTpi","Bearer "+webToken,"Bearer "+"a".repeat(43))) {
            var r=revokeRequest("unknown");r.addHeader("Authorization",header);assertEquals(400,send(r).getStatus());
        }
        var missing=revokeRequest("unknown");missing.removeParameter("client_id");assertEquals(400,send(missing).getStatus());
    }
    @Test void confidentialRevocationRequiresEndpointAudienceAndFreshSharedJtiEvenForUnknown()throws Exception {
        var controls=new AiIntegrationSecurityService(()->{try{return runtime.openConnection(new ServerPrincipal(71,7,null));}catch(SQLException e){throw new IllegalStateException(e);}},true,ISSUER,RESOURCE);
        var g=java.security.KeyPairGenerator.getInstance("RSA");g.initialize(2048);var pair=g.generateKeyPair();
        var reg=controls.register("Synthetic revocation",true,Set.of(CASES_SEARCH,CASES_OVERVIEW_READ),1,List.of(URI.create(REDIRECT)));
        var key=controls.rotateClientKey(reg.integrationId(),(java.security.interfaces.RSAPublicKey)pair.getPublic(),1,reg.rowVersion());
        String original=client;client=reg.clientId();
        try {
            String jti=UUID.randomUUID().toString();
            var issue=exchangeRequest(freshCode());authenticate(issue,signedAssertion(key.credentialId(),pair.getPrivate(),"/token",jti));
            var issued=send(issue);assertEquals(200,issued.getStatus(),issued.getContentAsString());
            var body=new tools.jackson.databind.ObjectMapper().readTree(issued.getContentAsString());String access=body.path("access_token").asText();
            assertTrue(send(revokeRequest("unknown")).getStatus()>=400,"Confidential client always needs proof");
            var wrongAudience=revokeRequest(access);authenticate(wrongAudience,signedAssertion(key.credentialId(),pair.getPrivate()));assertTrue(send(wrongAudience).getStatus()>=400);
            var badSignature=revokeRequest(access);authenticate(badSignature,signedAssertion(key.credentialId(),g.generateKeyPair().getPrivate(),"/revoke",UUID.randomUUID().toString()));assertTrue(send(badSignature).getStatus()>=400);
            var shared=revokeRequest(access);authenticate(shared,signedAssertion(key.credentialId(),pair.getPrivate(),"/revoke",jti));assertEquals(401,send(shared).getStatus(),"Issuance JTI cannot be reused with a newly signed revoke audience");
            String assertion=signedAssertion(key.credentialId(),pair.getPrivate(),"/revoke",UUID.randomUUID().toString());
            var unknown=revokeRequest("unknown");authenticate(unknown,assertion);assertEquals(200,send(unknown).getStatus());
            var replay=revokeRequest(access);authenticate(replay,assertion);assertEquals(401,send(replay).getStatus(),"Unknown revocation still consumes shared JTI");
            var valid=revokeRequest(access);authenticate(valid,signedAssertion(key.credentialId(),pair.getPrivate(),"/revoke",UUID.randomUUID().toString()));
            var response=send(valid);assertEquals(200,response.getStatus(),response.getContentAsString());assertEquals("",response.getContentAsString());
            var refresh=refreshRequest(body.path("refresh_token").asText());authenticate(refresh,signedAssertion(key.credentialId(),pair.getPrivate()));assertEquals(400,send(refresh).getStatus());
            var nextIssue=exchangeRequest(freshCode());authenticate(nextIssue,signedAssertion(key.credentialId(),pair.getPrivate()));
            var nextResponse=send(nextIssue);assertEquals(200,nextResponse.getStatus());
            String nextRefresh=new tools.jackson.databind.ObjectMapper().readTree(nextResponse.getContentAsString()).path("refresh_token").asText();
            var revokeRefresh=revokeRequest(nextRefresh);authenticate(revokeRefresh,signedAssertion(key.credentialId(),pair.getPrivate(),"/revoke",UUID.randomUUID().toString()));
            assertEquals(200,send(revokeRefresh).getStatus());
            var deniedRefresh=refreshRequest(nextRefresh);authenticate(deniedRefresh,signedAssertion(key.credentialId(),pair.getPrivate()));assertEquals(400,send(deniedRefresh).getStatus());
            controls.revokeClientKey(reg.integrationId(),key.credentialId(),key.rowVersion());
            var revokedKey=revokeRequest("unknown");authenticate(revokedKey,signedAssertion(key.credentialId(),pair.getPrivate(),"/revoke",UUID.randomUUID().toString()));assertTrue(send(revokedKey).getStatus()>=400);
        } finally {client=original;}
    }
    @Test void revocationAuditFailureWithholdsSuccessAndRollsBackFamily()throws Exception {
        var mapper=new tools.jackson.databind.ObjectMapper();var body=mapper.readTree(send(exchangeRequest(freshCode())).getContentAsString());
        try(var c=owner(db)){sql(c,"CREATE TRIGGER dbo.FailRevokeAudit ON dbo.EntityActionAuditLog AFTER INSERT AS IF EXISTS(SELECT 1 FROM inserted WHERE EntityType='AI_DELEGATED_SESSION' AND Action='DEACTIVATED') THROW 58999,'Injected revoke audit fault',1");}
        try {assertEquals(500,send(revokeRequest(body.path("access_token").asText())).getStatus());}
        finally {try(var c=owner(db)){sql(c,"DROP TRIGGER dbo.FailRevokeAudit");}}
        assertEquals(200,send(refreshRequest(body.path("refresh_token").asText())).getStatus(),"Audit failure must roll back family revocation");
    }


    static AiAccessTokenValidator mcpValidator;
    @Configuration static class McpFixtures {
        @Bean AiAccessTokenValidator mcpValidator() { return mcpValidator; }
    }
    @Test void mountedMcpUsesRealAccessGateRejectsOtherKindsAndCreatesNoCaseAudit(@org.junit.jupiter.api.io.TempDir Path temp)throws Exception {
        var issued=new tools.jackson.databind.ObjectMapper().readTree(send(exchangeRequest(freshCode())).getContentAsString());
        String access=issued.path("access_token").asText(),refresh=issued.path("refresh_token").asText();
        assertEquals(43,access.length());
        var rest=new MockHttpServletRequest("GET","/api/cases/search");rest.addHeader("Authorization","Bearer "+access);
        var rejection=assertThrows(org.springframework.web.server.ResponseStatusException.class,()->new BearerTokenServerSessionResolver(tokens,new InMemoryTokenRevocationStore()).resolve(rest));
        assertEquals(401,rejection.getStatusCode().value(),"A live ACCESS credential must fail ordinary REST authority");
        var source=new com.microsoft.sqlserver.jdbc.SQLServerDataSource();source.setURL(url(db));source.setUser(login);source.setPassword(secret);
        mcpValidator=new AiAccessTokenValidator(()->{try{return DriverManager.getConnection(url(db),bootstrapLogin,bootstrapSecret);}catch(SQLException e){throw new IllegalStateException(e);}},source,true,ISSUER,RESOURCE);
        try(var mcp=new AnnotationConfigWebApplicationContext()) {
            mcp.setServletContext(new MockServletContext());mcp.getEnvironment().setActiveProfiles("test");
            mcp.getEnvironment().getPropertySources().addFirst(new MapPropertySource("mcp",Map.of(
                    "shale.ai.mcp.enabled",true,"shale.ai.enabled",true,"shale.ai.oauth.public-origin","https://shale.test","shale.ai.oauth.identity","TEST")));
            mcp.register(McpFixtures.class,McpOAuthSecurityConfiguration.class,com.shale.server.mcp.McpTransportConfiguration.class);mcp.refresh();
            var sdk=(jakarta.servlet.Servlet)mcp.getBean("mcpServlet",org.springframework.boot.web.servlet.ServletRegistrationBean.class).getServlet();
            for(String method:List.of("server/discover","tools/list","unknown/method")) {
                var response=mcpSend(mcp,sdk,access,method);assertEquals(method.equals("unknown/method")?404:200,response.getStatus(),response.getContentAsString());
                assertFalse(response.getContentAsString().contains("Synthetic"));
            }
            for(String token:List.of(refresh,freshCode(),webToken,"a".repeat(43),"malformed.jwt.like"))
                assertTrue(mcpSend(mcp,sdk,token,"server/discover").getStatus()>=400,"Only ACCESS credentials may authenticate MCP");
            if("true".equals(System.getenv("SHALE_MCP_EXTERNAL_CLIENTS"))) {
                var bootstrapSource=new com.microsoft.sqlserver.jdbc.SQLServerDataSource();bootstrapSource.setURL(url(db));
                bootstrapSource.setUser(bootstrapLogin);bootstrapSource.setPassword(bootstrapSecret);bootstrapSource.setLoginTimeout(2);bootstrapSource.setSocketTimeout(5000);
                com.shale.server.mcp.McpClientInteropFixture.verifyClients(bootstrapSource,source,ISSUER,RESOURCE,access,temp);
            }
            var controls=new AiIntegrationSecurityService(()->{try{return runtime.openConnection(new ServerPrincipal(71,7,null));}catch(SQLException e){throw new IllegalStateException(e);}},true,ISSUER,RESOURCE);
            var policy=controls.inspectTenantPolicy();controls.setTenantEnabled(false,policy.rowVersion());
            try {assertEquals(401,mcpSend(mcp,sdk,access,"server/discover").getStatus());}
            finally {controls.setTenantEnabled(true,controls.inspectTenantPolicy().rowVersion());}
            try(var c=owner(db);var q=c.createStatement();var rows=q.executeQuery("SELECT (SELECT COUNT(*) FROM dbo.IntegrationAccessAuditLog WHERE Outcome='ALLOWED_READ'),(SELECT COUNT(*) FROM dbo.AuditLog)")) {
                assertTrue(rows.next());assertEquals(0,rows.getInt(1));assertEquals(0,rows.getInt(2));
            }
        } finally {mcpValidator=null;}
    }
    MockHttpServletResponse mcpSend(AnnotationConfigWebApplicationContext context,jakarta.servlet.Servlet sdk,String token,String method)throws Exception {
        var request=new MockHttpServletRequest("POST","/mcp");request.setServletPath("/mcp");request.setContentType("application/json");
        request.addHeader("Accept","application/json");request.addHeader("Authorization","Bearer "+token);
        request.addHeader("MCP-Protocol-Version","2026-07-28");request.addHeader("Mcp-Method",method);
        request.setContent(io.modelcontextprotocol.json.McpJsonDefaults.getMapper().writeValueAsBytes(new io.modelcontextprotocol.modern.JsonRpc.JSONRPCRequest(method,"sql",Map.of("_meta",Map.of(
                io.modelcontextprotocol.modern.McpSchema.MetaKeys.PROTOCOL_VERSION,"2026-07-28",io.modelcontextprotocol.modern.McpSchema.MetaKeys.CLIENT_CAPABILITIES,Map.of())))));
        var response=new MockHttpServletResponse();context.getBean(FilterChainProxy.class).doFilter(request,response,sdk::service);
        assertNull(request.getSession(false));assertNull(response.getHeader("Mcp-Session-Id"));return response;
    }
}
