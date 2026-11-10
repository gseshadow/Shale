package com.shale.server.oauth;

import com.shale.core.security.ai.AiScope;
import com.shale.data.security.ai.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.server.authorization.*;
import static org.junit.jupiter.api.Assertions.*;

/** Adapter contract only: SQL durability is covered by the separate real JDBC suite. */
class CimdBrowserBindingTest {
    static final String CLIENT="https://vendor.test/Exact%2Fclient.json",CALLBACK="https://client.test/callback";
    final UUID integration=UUID.randomUUID(),tx=UUID.randomUUID(),web=UUID.randomUUID();
    final ShaleOAuthHuman.Identity human=new ShaleOAuthHuman.Identity(7,72,web);
    final Set<AiScope> scopes=AiScope.fromMask(3);
    final String nonce="n".repeat(43),challenge="A".repeat(43);
    final Instant consentDeadline=Instant.now().plusSeconds(600);
    final List<String> events=new ArrayList<>();
    final MockHttpServletRequest request=new MockHttpServletRequest();
    DurableOAuthAuthorization durable;
    @BeforeEach void setup(){
        SecurityContextHolder.getContext().setAuthentication(ShaleOAuthHuman.authentication(human));
        request.setParameter("client_id",CLIENT);request.setParameter("state",nonce);
        var client=new AiOAuthTransactionStore.Client(7,integration,CLIENT,"External application","PUBLIC",scopes,Instant.now().plusSeconds(3600),new AiIntegrationSecurityService.RowVersion(new byte[8]),"https://shale.test/oauth/mcp","https://shale.test/mcp",Set.of(CALLBACK),List.of());
        AiOAuthBrowserStore store=new AiOAuthBrowserStore(){
            @Override public boolean externalIdentity(){return true;}
            @Override public Optional<AiOAuthTransactionStore.Client> loadClient(String id){return CLIENT.equals(id)?Optional.of(client):Optional.empty();}
            @Override public void begin(UUID id,UUID session,String external,String redirect,String s256,String state,Set<AiScope> requested,String pending){
                assertEquals(tx,id);assertEquals(web,session);assertEquals(CLIENT,external);assertEquals(CALLBACK,redirect);assertEquals(challenge,s256);assertEquals("exact state",state);assertEquals(nonce,pending);events.add("begin");
            }
            @Override public Optional<AiOAuthTransactionStore.Intent> findIntent(String pending,UUID session){
                assertEquals(web,session);return nonce.equals(pending)?Optional.of(new AiOAuthTransactionStore.Intent(tx,7,72,integration,web,CLIENT,CALLBACK,challenge,"exact state",scopes,Instant.now(),consentDeadline)):Optional.empty();
            }
            @Override public void approve(UUID id,UUID session,Set<AiScope> accepted){assertEquals(tx,id);assertEquals(web,session);assertEquals(scopes,accepted);events.add("approve");}
            @Override public void complete(UUID id,UUID session,Set<AiScope> accepted,String code){assertEquals(tx,id);assertEquals(web,session);assertEquals(scopes,accepted);assertEquals(List.of("begin","approve"),events);events.add("complete");}
            @Override public void deny(UUID id,UUID session){assertEquals(tx,id);assertEquals(web,session);events.add("deny");}
        };
        durable=new DurableOAuthAuthorization(store,new McpOAuthProperties("https://shale.test",McpOAuthProperties.Identity.TEST,true),()->request);
    }
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    @Test void externalIdentitySurvivesFrameworkMappingIntentConsentAndCodeIssuance(){
        var registered=durable.findByClientId(CLIENT);assertEquals(CLIENT,registered.getClientId());assertTrue(registered.getId().startsWith("cimd:7:"));
        assertTrue(registered.getClientSettings().isRequireProofKey());assertTrue(registered.getClientSettings().isRequireAuthorizationConsent());
        var original=OAuth2AuthorizationRequest.authorizationCode().authorizationUri("https://shale.test/oauth/mcp/authorize").clientId(CLIENT).redirectUri(CALLBACK).state("exact state")
                .scopes(DurableOAuthAuthorization.wire(scopes)).additionalParameters(Map.of("code_challenge_method","S256","code_challenge",challenge)).build();
        var pending=OAuth2Authorization.withRegisteredClient(registered).id(tx.toString()).principalName(human.getName()).authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .attribute(OAuth2AuthorizationRequest.class.getName(),original).attribute("state",nonce).build();
        durable.save(pending);assertEquals(List.of("begin"),events,"Loading and beginning must not create consent/code.");
        var restored=durable.findByToken(nonce,new OAuth2TokenType("state"));
        assertEquals(consentDeadline.toString(),restored.getAttribute("consentExpiresAt"),"The browser must show the SQL-clipped deadline.");
        OAuth2AuthorizationRequest restoredRequest=restored.getAttribute(OAuth2AuthorizationRequest.class.getName());assertEquals(CLIENT,restoredRequest.getClientId());
        assertNull(durable.consents().findById(registered.getId(),human.getName()),"Prior consent never bypasses this explicit decision.");
        var consent=OAuth2AuthorizationConsent.withId(registered.getId(),human.getName());DurableOAuthAuthorization.wire(scopes).forEach(consent::scope);durable.consents().save(consent.build());
        var issued=OAuth2Authorization.from(pending).authorizedScopes(DurableOAuthAuthorization.wire(scopes)).token(new OAuth2AuthorizationCode("c".repeat(43),Instant.now(),Instant.now().plusSeconds(60))).build();durable.save(issued);
        assertEquals(List.of("begin","approve","complete"),events);
    }
    @Test void unauthenticatedAndForeignTenantAuthorityCannotLoadExternalClient(){
        SecurityContextHolder.clearContext();assertThrows(SecurityException.class,()->durable.findByClientId(CLIENT));
        SecurityContextHolder.getContext().setAuthentication(ShaleOAuthHuman.authentication(new ShaleOAuthHuman.Identity(8,82,UUID.randomUUID())));
        assertThrows(SecurityException.class,()->durable.findByClientId(CLIENT));assertTrue(events.isEmpty());
    }
    @Test void alteredExternalIdentityDoesNotNormalizeOrFallBackToManualClient(){
        assertNull(durable.findByClientId(CLIENT.replace("%2F","%2f")));assertNull(durable.findByClientId("manual-client"));assertTrue(events.isEmpty());
    }
}
