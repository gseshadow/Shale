package com.shale.server.oauth.cimd;

import com.shale.server.oauth.CimdClientIdentifier;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import org.junit.jupiter.api.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CimdMetadataTest {
    static final CimdClientIdentifier ID=new CimdClientIdentifier("https://metadata.public.org/client.json");
    static byte[] bytes(String s) { return s.getBytes(StandardCharsets.UTF_8); }
    static String metadata(String extra) {
        return CimdFixture.publicMetadata(ID.value()).replace("}",extra+"}");
    }
    static CimdMetadata.Parsed parse(String extra) { return CimdMetadata.parse(ID,bytes(metadata(extra))); }

    @Test void publicLegacyPluralAndConfidentialProfilesPreserveStrictMethodsAndFilterGrants() throws Exception {
        assertEquals(Set.of("none"),parse("").methods(),"CIMD default must be public, never a secret default");
        assertEquals(Set.of("none"),parse(",\"token_endpoint_auth_method\":\"none\"").methods());
        var key=new RSAKeyGenerator(2048).keyID("current").algorithm(com.nimbusds.jose.JWSAlgorithm.RS256).generate();
        String jwks=",\"jwks\":{\"keys\":["+key.toPublicJWK().toJSONString()+"]}";
        var confidential=parse(",\"token_endpoint_auth_method\":\"private_key_jwt\""+jwks);
        assertEquals(Set.of("private_key_jwt"),confidential.methods());
        assertEquals(1,confidential.keys().size());
        var plural=parse(",\"token_endpoint_auth_method\":\"private_key_jwt\",\"token_endpoint_auth_methods_supported\":[\"none\",\"private_key_jwt\"],\"token_endpoint_auth_signing_alg\":\"RS256\",\"grant_types\":[\"authorization_code\",\"refresh_token\",\"extra\"],\"jwks_uri\":\"https://keys.public.org/keys.json\"");
        assertEquals(Set.of("none","private_key_jwt"),plural.methods());
        assertEquals(Set.of("authorization_code","refresh_token"),plural.grants());
        assertEquals("https://keys.public.org/keys.json",plural.keysUrl());
        for (String auth:List.of("client_secret_basic","client_secret_post","client_secret_jwt","password","unsupported"))
            assertThrows(IllegalArgumentException.class,()->parse(",\"token_endpoint_auth_method\":\""+auth+"\""),auth);
        for (String extra:List.of(",\"token_endpoint_auth_methods_supported\":[]", ",\"token_endpoint_auth_methods_supported\":[\"none\",\"client_secret_basic\"]",
                ",\"token_endpoint_auth_method\":\"private_key_jwt\"", ",\"token_endpoint_auth_method\":\"private_key_jwt\",\"token_endpoint_auth_methods_supported\":[\"none\"]",
                ",\"grant_types\":[\"password\"]", ",\"response_types\":[\"token\"]", ",\"code_challenge_methods_supported\":[\"plain\"]",
                ",\"token_endpoint_auth_signing_alg\":\"HS256\"", ",\"client_secret\":\"sentinel\"", ",\"dpop_bound_access_tokens\":true"))
            assertThrows(IllegalArgumentException.class,()->parse(extra),extra);
    }

    @Test void exactIdentityJsonTypesEncodingAndDisplayLimitsFailClosed() {
        for (String wrong:List.of(ID.value().toUpperCase(),ID.value()+"/","https://evil.public.org/client.json"))
            assertThrows(IllegalArgumentException.class,()->CimdMetadata.parse(ID,bytes(CimdFixture.publicMetadata(wrong))));
        for (String input:List.of("[]","null","{}","{\"client_id\":1}",metadata("")+" {}",metadata(",\"client_id\":\""+ID.value()+"\""),
                metadata("").replace("Synthetic","x".repeat(101)),metadata("").replace("Synthetic","evil\\u202ename"),
                metadata(",\"extension\":"+"[".repeat(9)+"0"+"]".repeat(9)),metadata(",\"extension\":\""+"x".repeat(2049)+"\""),
                metadata(",\"extension\":["+"0,".repeat(32)+"0]"),metadata(",\"redirect_uris\":\"https://callback.public.org/callback\"")))
            assertThrows(IllegalArgumentException.class,()->CimdMetadata.parse(ID,bytes(input)),"Malformed publication must not create a snapshot");
        byte[] malformed=bytes(metadata(",\"extension\":\"x\""));
        malformed[malformed.length-3]=(byte)0xff;
        assertThrows(IllegalArgumentException.class,()->CimdMetadata.parse(ID,malformed));
        assertThrows(IllegalArgumentException.class,()->CimdMetadata.json(new byte[32769]));
        assertDoesNotThrow(()->parse(",\"client_uri\":\"https://unrelated.public.org/\",\"unknown_info\":{\"x\":1}"));
    }

    @Test void callbacksHaveIndependentSixteenEntryLimitAndRetainExistingExactPolicy() {
        for (int count:List.of(10,16)) {
            String callbacks=java.util.stream.IntStream.range(0,count).mapToObj(i->"\"http://127.0.0.1:"+(3000+i)+"/callback\"").collect(java.util.stream.Collectors.joining(","));
            String document="{\"client_id\":\""+ID.value()+"\",\"redirect_uris\":["+callbacks+"]}";
            assertEquals(count,CimdMetadata.parse(ID,bytes(document)).redirects().size());
        }
        for (String callback:List.of("https://127.0.0.1/callback","https://192.168.1.2/callback","https://host.internal/callback","http://localhost:3333/callback",
                "https://callback.public.org/callback?x=1","https://callback.public.org/a/../callback","https://callback.public.org/%63allback","https://*.public.org/callback"))
            assertThrows(IllegalArgumentException.class,()->CimdMetadata.parse(ID,bytes(CimdFixture.publicMetadata(ID.value()).replace("https://callback.public.org/callback",callback))),callback);
        assertThrows(IllegalArgumentException.class,()->CimdMetadata.parse(ID,bytes(metadata("").replace(
                "[\"https://callback.public.org/callback\"]","[\"https://callback.public.org/callback\",\"https://callback.public.org/callback\"]"))));
        String seventeen=java.util.stream.IntStream.range(0,17).mapToObj(i->"\"http://127.0.0.1:"+(3000+i)+"/callback\"").collect(java.util.stream.Collectors.joining(","));
        assertThrows(IllegalArgumentException.class,()->CimdMetadata.parse(ID,bytes(metadata("").replace(
                "[\"https://callback.public.org/callback\"]","["+seventeen+"]"))));
    }

    @Test void numericProfileDiscardsOnlyPairedStrictLocalhostAliases() {
        String template = "{\"client_id\":\""+ID.value()+"\",\"redirect_uris\":[\"http://127.0.0.1:27890/callback\",\"%s\"]}";
        assertEquals(List.of("http://127.0.0.1:27890/callback"),
                CimdMetadata.parse(ID,bytes(template.formatted("http://localhost:27890/callback"))).redirects());
        for(String alias:List.of("http://localhost:27891/callback","http://localhost:27890/other",
                "http://localhost:27890/callback?x=1","http://localhost:27890/callback#x",
                "http://user@localhost:27890/callback","http://localhost:27890/a/../callback",
                "http://localhost:27890/%63allback","https://localhost:27890/callback"))
            assertThrows(IllegalArgumentException.class,()->CimdMetadata.parse(ID,bytes(template.formatted(alias))),alias);
        assertThrows(IllegalArgumentException.class,()->com.shale.server.oauth.OAuthRedirectPolicy.registered("http://localhost:27890/callback",false),
                "Discarding an advertised alias must never authorize that redirect");
    }

    @Test void keyProfileRejectsPrivateWeakAmbiguousOrRemoteFallbackMaterial() throws Exception {
        var key=new RSAKeyGenerator(2048).keyID("current").algorithm(com.nimbusds.jose.JWSAlgorithm.RS256).generate();
        String publicKey=key.toPublicJWK().toJSONString();
        for (String bad:List.of(key.toJSONString(),publicKey.replace("RS256","HS256"),publicKey.replace("current",""),
                publicKey.replace("}",",\"x5u\":\"https://evil.public.org/key\"}"),
                publicKey.replace("}",",\"key_ops\":[\"sign\"]}"),
                publicKey.replace(key.getModulus().toString(),"AQAB")))
            assertThrows(IllegalArgumentException.class,()->CimdMetadata.keys(CimdMetadata.json(bytes("{\"keys\":["+bad+"]}"))));
        assertThrows(IllegalArgumentException.class,()->CimdMetadata.keys(CimdMetadata.json(bytes("{\"keys\":["+publicKey+","+publicKey+"]}"))));
        assertThrows(IllegalArgumentException.class,()->CimdMetadata.keys(CimdMetadata.json(bytes("{\"keys\":[]}"))));
        String second=publicKey.replace("current","next");
        assertEquals(2,CimdMetadata.keys(CimdMetadata.json(bytes("{\"keys\":["+publicKey+","+second+"]}"))).size());
        String third=publicKey.replace("current","third");
        assertThrows(IllegalArgumentException.class,()->CimdMetadata.keys(CimdMetadata.json(bytes("{\"keys\":["+publicKey+","+second+","+third+"]}"))));
        assertThrows(IllegalArgumentException.class,()->parse(",\"token_endpoint_auth_method\":\"private_key_jwt\",\"jwks\":{\"keys\":["+publicKey+"]},\"jwks_uri\":\"https://keys.public.org/key\""));
    }

    @Test void addressPolicyRejectsMixedMappedReservedAndAlternativeIpSpellings() throws Exception {
        for (String ip:List.of("0.0.0.0","10.1.2.3","127.0.0.1","169.254.169.254","172.16.1.1","192.168.1.1","100.100.100.200",
                "168.63.129.16","192.0.0.9","198.18.0.1","192.0.2.1","198.51.100.1","203.0.113.1","224.0.0.1","240.0.0.1",
                "::","::1","::ffff:127.0.0.1","fc00::1","fe80::1","ff02::1","64:ff9b::a00:1","2002:a00:1::","2001:db8::1","2001::1"))
            assertFalse(CimdDestination.publicAddress(InetAddress.getByName(ip)),ip);
        for (String ip:List.of("93.184.215.14","2606:4700:4700::1111")) assertTrue(CimdDestination.publicAddress(InetAddress.getByName(ip)),ip);
        assertThrows(IllegalArgumentException.class,()->CimdDestination.approved(new InetAddress[]{InetAddress.getByName("93.184.215.14"),InetAddress.getByName("10.0.0.1")}));
        for (String host:List.of("127.1","2130706433","0x7f000001","[::1]","localhost","host.local","host.internal","host.public.org."))
            assertThrows(IllegalArgumentException.class,()->CimdDestination.url("https://"+host+"/client.json"),host);
    }
}
