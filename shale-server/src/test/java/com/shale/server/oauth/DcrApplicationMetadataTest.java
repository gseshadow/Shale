package com.shale.server.oauth;

import com.shale.core.runtime.DbSessionProvider;
import com.shale.core.security.ai.AiScope;
import com.shale.data.security.ai.*;
import com.shale.server.oauth.cimd.CimdMetadataService;
import org.junit.jupiter.api.Test;
import java.lang.reflect.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Stored DCR metadata must never become a fetched CIMD document or a manual ID. */
class DcrApplicationMetadataTest {
    static final String ID="dcr."+"a".repeat(64);
    static final McpOAuthProperties PROPERTIES=new McpOAuthProperties("https://shale.test",McpOAuthProperties.Identity.TEST,true);
    @Test void namespaceSelectionPreservesCimdUrlValidationAndNeverAliasesDcr() {
        var dcr=new OAuthApplicationIdentifier(ID);
        assertEquals(OAuthApplicationIdentifier.Source.DCR,dcr.source());
        assertThrows(SecurityException.class,dcr::cimd);
        assertThrows(IllegalArgumentException.class,()->new CimdClientIdentifier(ID));
        assertThrows(IllegalArgumentException.class,()->new OAuthApplicationIdentifier("manual-client"));
        for(String bad:List.of("dcr."+"g".repeat(64),ID+"a","DCR."+"a".repeat(64),"https://client.test/../callback"))
            assertThrows(IllegalArgumentException.class,()->new OAuthApplicationIdentifier(bad),bad);
        String cimd="https://client.test/Exact%2Fidentity.json";
        assertEquals(cimd,new OAuthApplicationIdentifier(cimd).cimd().value());
        assertTrue(CimdOAuthProviders.external(ID));
        assertFalse(CimdOAuthProviders.external("manual-client"));
    }
    @Test void explicitV2ReadsStoredDcrMetadataAndDefaultCompositionRejectsIt() {
        var jdbc=new Stored();var store=new AiCimdOAuthStore(jdbc,PROPERTIES.issuer(),PROPERTIES.resource(),AiExternalContract.EXTERNAL_V2);
        try(var fetcher=new CimdMetadataService(PROPERTIES.issuer())) {
            var lifecycle=new CimdOAuthLifecycle(fetcher,store,store,store,owner->store,PROPERTIES,Clock.systemUTC(),
                    new AiDcrApplicationStore(jdbc,PROPERTIES.issuer(),PROPERTIES.resource()));
            var snapshot=lifecycle.prepare(new OAuthApplicationIdentifier(ID));
            assertEquals(ID,snapshot.identifier().value());assertEquals(Set.of("none"),snapshot.methods());
            assertEquals(List.of("http://127.0.0.1:8085/callback"),snapshot.redirects());
            assertEquals(List.of("{call dbo.AiDcrPrepareMetadataV2(?,?,?)}"),jdbc.calls,
                    "Only stored metadata is read; no publisher, resolver, tenant or CIMD fetch fallback");
            var cimdOnly=new CimdOAuthLifecycle(fetcher,store,store,store,owner->store,PROPERTIES,Clock.systemUTC());
            assertThrows(SecurityException.class,()->cimdOnly.prepare(new OAuthApplicationIdentifier(ID)));
            jdbc.external="dcr."+"b".repeat(64);
            assertThrows(SecurityException.class,()->lifecycle.prepare(new OAuthApplicationIdentifier(ID)),"Stored full identity must match");
        }
    }
    @Test void invalidRegistrationProfileNeverReachesTheDatabaseOrDowngradesConfidentialClients() {
        var service=new DcrApplicationRegistration(new AiDcrApplicationStore(()->{fail("Invalid metadata must fail before SQL");return null;},PROPERTIES.issuer(),PROPERTIES.resource()));
        for(String method:Arrays.asList(null,"client_secret_basic","client_secret_post","private_key_jwt"))
            assertThrows(IllegalArgumentException.class,()->service.register("App",List.of("https://client.test/callback"),AiScope.fromMask(3),method,List.of()));
        for(String uri:List.of("http://private.test/callback","https://client.test/callback?tenant=7","https://client.test/callback#fragment","https://client.test/../callback"))
            assertThrows(IllegalArgumentException.class,()->service.register("App",List.of(uri),AiScope.fromMask(3),"none",List.of()),uri);
        assertThrows(IllegalArgumentException.class,()->service.register("App",List.of("https://client.test/callback"),Set.of(),"none",List.of()));
    }
    @Test void supportedPublicRegistrationReturnsOnlySqlGeneratedApplicationIdentity() {
        var jdbc=new Stored();var service=new DcrApplicationRegistration(new AiDcrApplicationStore(jdbc,PROPERTIES.issuer(),PROPERTIES.resource()));
        assertEquals(ID,service.register("Synthetic",List.of("http://127.0.0.1:8085/callback"),AiScope.fromMask(1),"none",List.of()).identifier().value());
        assertEquals(List.of("{call dbo.AiDcrRegisterApplicationV2(?,?,?,?,?,?,?,?)}"),jdbc.calls);
    }
    @Test void confidentialRegistrationAcceptsOnlyValidatedPublicRs256Keys()throws Exception {
        var generator=java.security.KeyPairGenerator.getInstance("RSA");generator.initialize(2048);var pair=generator.generateKeyPair();
        var publicKey=new com.nimbusds.jose.jwk.RSAKey.Builder((java.security.interfaces.RSAPublicKey)pair.getPublic())
                .keyID("dcr-key").algorithm(com.nimbusds.jose.JWSAlgorithm.RS256).keyUse(com.nimbusds.jose.jwk.KeyUse.SIGNATURE).build();
        var jdbc=new Stored();var service=new DcrApplicationRegistration(new AiDcrApplicationStore(jdbc,PROPERTIES.issuer(),PROPERTIES.resource()));
        assertEquals(ID,service.register("Confidential",List.of("https://client.test/callback"),AiScope.fromMask(3),"private_key_jwt",List.of(publicKey)).identifier().value());
        assertThrows(IllegalArgumentException.class,()->service.register("Confidential",List.of("http://127.0.0.1:8085/callback"),AiScope.fromMask(3),"private_key_jwt",List.of(publicKey)));
        assertThrows(IllegalArgumentException.class,()->service.register("Confidential",List.of("https://client.test/callback"),AiScope.fromMask(3),"none",List.of(publicKey)));
        var privateKey=new com.nimbusds.jose.jwk.RSAKey.Builder(publicKey).privateKey((java.security.interfaces.RSAPrivateKey)pair.getPrivate()).build();
        assertThrows(IllegalArgumentException.class,()->service.register("Confidential",List.of("https://client.test/callback"),AiScope.fromMask(3),"private_key_jwt",List.of(privateKey)));
    }
    static final class Stored implements DbSessionProvider {
        final List<String> calls=new ArrayList<>();String external=ID;boolean next;
        @Override public Connection requireConnection(){return proxy(Connection.class,(p,m,a)->switch(m.getName()){
            case "getAutoCommit"->true;
            case "prepareCall"->{calls.add((String)a[0]);yield statement();}
            default->empty(m);
        });}
        CallableStatement statement(){return proxy(CallableStatement.class,(p,m,a)->switch(m.getName()){
            case "execute"->{next=true;yield true;}
            case "getResultSet"->proxy(ResultSet.class,(r,method,args)->{
                if(method.getName().equals("next")){boolean found=next;next=false;return found;}
                if(args!=null&&args.length>0&&args[0] instanceof String name)return switch(name){
                    case "ClientId","ExternalClientId"->external;
                    case "ApplicationId"->"00000000-0000-0000-0000-000000000001";
                    case "Revision","ApplicationRevision"->1L;
                    case "ClientName"->"Synthetic stored DCR";
                    case "Redirects"->"[\"http://127.0.0.1:8085/callback\"]";
                    case "SigningKeys"->"[]";
                    case "MethodMask","ScopeMask"->1;
                    case "MetadataDigest"->new byte[32];case "RowVer"->new byte[8];
                    case "FreshUntil","IssuedAt"->Timestamp.from(Instant.now().plusSeconds(240));
                    case "Disabled"->false;
                    default->throw new AssertionError(name);
                };
                return empty(method);
            });
            case "getUpdateCount"->-1;
            default->empty(m);
        });}
        @SuppressWarnings("unchecked") static <T>T proxy(Class<T> c,InvocationHandler h){return (T)Proxy.newProxyInstance(c.getClassLoader(),new Class[]{c},h);}
        static Object empty(Method m){if(!m.getReturnType().isPrimitive()||m.getReturnType()==void.class)return null;if(m.getReturnType()==boolean.class)return false;if(m.getReturnType()==long.class)return 0L;return 0;}
    }
}
