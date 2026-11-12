package com.shale.server.oauth;

import com.shale.data.security.ai.*;
import com.shale.core.security.ai.AiScope;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Registration is anonymous metadata creation, never authority or caller-chosen identity. */
class DcrRegistrationEndpointTest {
    AiDcrApplicationStore store;AiDcrAdmissionStore admission;DcrRegistrationEndpoint endpoint;
    static final String ID="dcr."+"a".repeat(64);
    static final String PUBLIC="\"redirect_uris\":[\"http://localhost:3334/oauth/callback\"],\"token_endpoint_auth_method\":\"none\"";
    @BeforeEach void setup() {
        store=mock(AiDcrApplicationStore.class);admission=mock(AiDcrAdmissionStore.class);
        when(store.contract()).thenReturn(AiExternalContract.DCR_V3);
        when(admission.admit(any())).thenReturn(new AiDcrAdmissionStore.Admission(true,30));
        when(store.register(any(),any(),any(),anyInt(),anyInt())).thenReturn(new AiDcrApplicationStore.Registration(new DcrClientIdentifier(ID),Instant.ofEpochSecond(1234),
                new AiApplicationRegistryService.Application(UUID.randomUUID(),1,false,new AiIntegrationSecurityService.RowVersion(new byte[8]))));
        endpoint=new DcrRegistrationEndpoint(new DcrCapabilities(store,store,admission));
    }
    MockHttpServletResponse post(String body)throws Exception {
        var req=new MockHttpServletRequest("POST","/oauth/mcp/register");req.setRemoteAddr("127.0.0.1");req.setContentType("application/json");req.setContent(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var res=new MockHttpServletResponse();endpoint.handle(req,res);return res;
    }
    @Test void anonymousPublicRegistrationReturnsOnlyGeneratedIdentityAndAcceptedMetadata()throws Exception {
        var res=post("{"+PUBLIC+",\"tenant\":8,\"is_admin\":true}");
        assertEquals(201,res.getStatus());assertEquals("no-store",res.getHeader("Cache-Control"));
        var json=new tools.jackson.databind.ObjectMapper().readTree(res.getContentAsString());
        assertEquals(ID,json.path("client_id").asString());assertEquals(1234,json.path("client_id_issued_at").asInt());
        assertEquals("none",json.path("token_endpoint_auth_method").asString());assertFalse(json.has("tenant"));assertFalse(json.has("client_secret"));
        verify(store).register(eq("MCP application"),eq("[\"http://localhost:3334/oauth/callback\"]"),eq("[]"),eq(1),eq(3));
    }
    @Test void defaultSecretMethodAndUntrustedCredentialsAreRejectedWithoutRegistration()throws Exception {
        for(String body:List.of("{\"redirect_uris\":[\"https://client.test/cb\"]}","{"+PUBLIC+",\"client_id\":\"chosen\"}","{"+PUBLIC+",\"scope\":\"admin\"}","{"+PUBLIC+",\"grant_types\":[]}","{"+PUBLIC+",\"grant_types\":[\"client_credentials\"]}","{"+PUBLIC+",\"code_challenge_method\":\"plain\"}","{"+PUBLIC+",\"token_endpoint_auth_method\":\"private_key_jwt\"}")) {
            assertEquals(400,post(body).getStatus(),body);
        }
        verify(store,never()).register(any(),any(),any(),anyInt(),anyInt());
    }
    @Test void invalidRedirectErrorsAreStandardAndJsonIsStrictAndBounded()throws Exception {
        var bad=post("{\"redirect_uris\":[\"https://client.test/cb#fragment\"],\"token_endpoint_auth_method\":\"none\"}");
        assertEquals(400,bad.getStatus());assertTrue(bad.getContentAsString().contains("invalid_redirect_uri"));
        assertEquals(400,post("{"+PUBLIC+",\"client_name\":\"a\",\"client_name\":\"b\"}").getStatus());
        assertEquals(400,post("{"+PUBLIC+"} {}").getStatus());
        assertEquals(413,post("{"+PUBLIC+",\"extension\":\""+"x".repeat(32768)+"\"}").getStatus());
        verify(store,never()).register(any(),any(),any(),anyInt(),anyInt());
    }
    @Test void durableAdmissionRefusalIs429WithRetryAfterAndNoRegistration()throws Exception {
        when(admission.admit(any())).thenReturn(new AiDcrAdmissionStore.Admission(false,17));
        var res=post("{"+PUBLIC+"}");assertEquals(429,res.getStatus());assertEquals("17",res.getHeader("Retry-After"));verify(store,never()).register(any(),any(),any(),anyInt(),anyInt());
    }
    @Test void chunkedLengthCannotBypassBodyLimit()throws Exception {
        var req=new MockHttpServletRequest("POST","/oauth/mcp/register") { @Override public long getContentLengthLong(){return -1;} };
        req.setRemoteAddr("127.0.0.1");req.setContentType("application/json");req.setContent(new byte[32769]);
        var response=new MockHttpServletResponse();endpoint.handle(req,response);
        assertEquals(413,response.getStatus());verify(store,never()).register(any(),any(),any(),anyInt(),anyInt());
    }
    @Test void metadataCapabilityWithdrawsWhenSqlControlIsOffOrUnavailable() {
        when(admission.enabled()).thenReturn(false);assertFalse(endpoint.enabled());
        when(admission.enabled()).thenReturn(true);assertTrue(endpoint.enabled());
        when(admission.enabled()).thenThrow(new IllegalStateException("unavailable"));assertFalse(endpoint.enabled());
    }
    @Test void confidentialPublicRsaRegistrationDoesNotDowngrade()throws Exception {
        var key=new com.nimbusds.jose.jwk.gen.RSAKeyGenerator(2048).keyID("test-key").algorithm(com.nimbusds.jose.JWSAlgorithm.RS256).keyUse(com.nimbusds.jose.jwk.KeyUse.SIGNATURE).generate();
        String base="\"redirect_uris\":[\"https://client.test/callback\"],\"token_endpoint_auth_method\":\"private_key_jwt\",\"jwks\":";
        assertEquals(201,post("{"+base+new com.nimbusds.jose.jwk.JWKSet(key.toPublicJWK()).toString()+"}").getStatus());
        verify(store).register(any(),any(),any(),eq(2),eq(3));
        assertEquals(400,post("{"+base+new com.nimbusds.jose.jwk.JWKSet(key).toString(false)+"}").getStatus());
    }
}
