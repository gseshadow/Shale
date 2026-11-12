package com.shale.server.oauth.cimd;

import com.shale.server.oauth.CimdClientIdentifier;
import com.shale.server.oauth.OAuthRedirectPolicy;
import com.nimbusds.jose.jwk.RSAKey;
import tools.jackson.core.*;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;
import java.nio.*;
import java.net.URI;
import java.nio.charset.*;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.util.*;

/** Strict, bounded metadata profile. Parsed publication is evidence, never OAuth authority. */
final class CimdMetadata {
    private static final JsonMapper JSON=JsonMapper.builder(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(8).maxStringLength(2048)
                    .maxNameLength(100).maxNumberLength(20).build()).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    record Parsed(String name, List<String> redirects, Set<String> methods, Set<String> grants,
                  List<CimdMetadataService.SigningKey> keys, String keysUrl) {}

    static JsonNode json(byte[] bytes) {
        try {
            if (bytes.length==0 || bytes.length>CimdHttps.LIMIT) throw CimdDestination.invalid();
            String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            JsonNode node=JSON.readTree(text);
            if (!node.isObject()) throw CimdDestination.invalid();
            bounded(node);
            return node;
        } catch (Exception failure) { throw CimdDestination.invalid(); }
    }
    private static void bounded(JsonNode node) {
        if (node.isArray() && node.size()>32 || node.isObject() && node.size()>64) throw CimdDestination.invalid();
        for (JsonNode child:node) bounded(child);
    }
    static Parsed parse(CimdClientIdentifier id, byte[] bytes) {
        var n=json(bytes);
        if (!id.value().equals(string(n,"client_id",2048))) throw CimdDestination.invalid();
        String name=n.has("client_name")?string(n,"client_name",100):"";
        if (name.codePoints().anyMatch(c -> Character.isISOControl(c) || Character.getType(c)==Character.FORMAT ||
                c>=0x2028 && c<=0x202e || c>=0x2066 && c<=0x2069)) throw CimdDestination.invalid();
        // These declarations would alter authentication, sender constraints or callback semantics.
        // Unknown informational extensions remain bounded and ignored.
        for (String field:List.of("client_secret", "client_secret_expires_at", "request_uris", "request_object_signing_alg",
                "require_signed_request_object", "tls_client_auth_subject_dn", "tls_client_certificate_bound_access_tokens",
                "authorization_signed_response_alg", "authorization_encrypted_response_alg", "dpop_bound_access_tokens"))
            if (n.has(field)) throw CimdDestination.invalid();
        for (String field:n.propertyNames())
            if (field.startsWith("tls_client_auth_") || field.startsWith("request_object_") ||
                    field.startsWith("authorization_encrypted_") || field.startsWith("id_token_") ||
                    field.startsWith("backchannel_") || field.startsWith("frontchannel_") ||
                    Set.of("require_pushed_authorization_requests","subject_type","sector_identifier_uri",
                            "default_max_age","require_auth_time","default_acr_values").contains(field)) throw CimdDestination.invalid();
        Set<String> methods;
        if (n.has("token_endpoint_auth_methods_supported")) {
            methods=strings(n,"token_endpoint_auth_methods_supported",8);
            if (n.has("token_endpoint_auth_method") && !methods.contains(string(n,"token_endpoint_auth_method",100))) throw CimdDestination.invalid();
        } else methods=Set.of(n.has("token_endpoint_auth_method")?string(n,"token_endpoint_auth_method",100):"none");
        if (methods.stream().anyMatch(m -> m.contains("secret")) || Collections.disjoint(methods,Set.of("none","private_key_jwt"))) throw CimdDestination.invalid();
        methods=new HashSet<>(methods); methods.retainAll(Set.of("none","private_key_jwt"));
        boolean keyed=methods.contains("private_key_jwt");
        if (n.has("token_endpoint_auth_signing_alg")) {
            if (!keyed || !"RS256".equals(string(n,"token_endpoint_auth_signing_alg",100))) throw CimdDestination.invalid();
        }
        if (n.has("token_endpoint_auth_signing_alg_values_supported") &&
                (!keyed || !strings(n,"token_endpoint_auth_signing_alg_values_supported",8).equals(Set.of("RS256")))) throw CimdDestination.invalid();
        Set<String> grants=n.has("grant_types")?strings(n,"grant_types",16):Set.of("authorization_code");
        if (!grants.contains("authorization_code")) throw CimdDestination.invalid();
        grants=new HashSet<>(grants); grants.retainAll(Set.of("authorization_code","refresh_token"));
        if (n.has("response_types") && !strings(n,"response_types",8).contains("code")) throw CimdDestination.invalid();
        if (n.has("code_challenge_methods_supported") && !strings(n,"code_challenge_methods_supported",8).equals(Set.of("S256"))) throw CimdDestination.invalid();
        var redirects=new ArrayList<>(strings(n,"redirect_uris",16));
        var accepted=new ArrayList<String>();
        for (String redirect:redirects) {
            if (redirect.chars().anyMatch(c -> c<=32 || c>=127)) throw CimdDestination.invalid();
            // Some native publications include localhost aliases alongside numeric callbacks.
            // Shale implements the numeric profile only: ignore a strict paired alias, never
            // register/accept it or normalize an actual authorization redirect into another URI.
            URI alternative;
            try {alternative=URI.create(redirect);}catch(IllegalArgumentException invalid){throw CimdDestination.invalid();}
            if(!keyed&&"http".equals(alternative.getScheme())&&"localhost".equals(alternative.getHost())&&
                    alternative.getPort()>0&&alternative.getPort()<=65535&&alternative.getRawUserInfo()==null&&
                    alternative.getRawQuery()==null&&alternative.getRawFragment()==null&&!alternative.getRawPath().isEmpty()&&
                    alternative.normalize().equals(alternative)&&!redirect.contains("%")&&!redirect.contains("*")&&
                    redirects.contains("http://127.0.0.1:"+alternative.getPort()+alternative.getRawPath()))continue;
            var uri=OAuthRedirectPolicy.registered(redirect, keyed);
            if ("https".equals(uri.getScheme())) CimdDestination.url(redirect);
            accepted.add(redirect);
        }
        if(accepted.isEmpty())throw CimdDestination.invalid();
        boolean inline=n.has("jwks"), remote=n.has("jwks_uri");
        if (inline && remote || keyed && !inline && !remote || !keyed && (inline || remote)) throw CimdDestination.invalid();
        String url=remote?string(n,"jwks_uri",2048):null;
        if (url!=null) CimdDestination.url(url);
        var keys=inline?keys(n.get("jwks")):List.<CimdMetadataService.SigningKey>of();
        return new Parsed(name,List.copyOf(accepted),Set.copyOf(methods),Set.copyOf(grants),keys,url);
    }
    static List<CimdMetadataService.SigningKey> keys(JsonNode n) {
        if (!n.isObject() || n.size()!=1 || !n.has("keys") || !n.get("keys").isArray() || n.get("keys").isEmpty() || n.get("keys").size()>2) throw CimdDestination.invalid();
        var result=new ArrayList<CimdMetadataService.SigningKey>();
        var kids=new HashSet<String>();
        for (var k:n.get("keys")) {
            if (!k.isObject()) throw CimdDestination.invalid();
            // A closed public RSA profile excludes all private material, certificates and URL fallback.
            for (String field:k.propertyNames()) if (!Set.of("kty","kid","use","key_ops","alg","n","e").contains(field)) throw CimdDestination.invalid();
            String kid=string(k,"kid",100);
            if (!kid.matches("[A-Za-z0-9._-]{1,100}") || !kids.add(kid) ||
                    !"RSA".equals(string(k,"kty",10)) || !"RS256".equals(string(k,"alg",10)) ||
                    k.has("use") && !"sig".equals(string(k,"use",10)) ||
                    k.has("key_ops") && !strings(k,"key_ops",1).equals(Set.of("verify"))) throw CimdDestination.invalid();
            for (String field:List.of("n","e")) if (!string(k,field,1400).matches("[A-Za-z0-9_-]+")) throw CimdDestination.invalid();
            try {
                RSAPublicKey rsa=RSAKey.parse(k.toString()).toRSAPublicKey();
                if (rsa.getModulus().bitLength()<2048 || rsa.getModulus().bitLength()>4096 ||
                        !rsa.getModulus().testBit(0) || rsa.getPublicExponent().bitLength()>32 ||
                        rsa.getPublicExponent().intValue()<3 || !rsa.getPublicExponent().testBit(0)) throw CimdDestination.invalid();
                result.add(new CimdMetadataService.SigningKey(kid,rsa));
            } catch (Exception failure) { throw CimdDestination.invalid(); }
        }
        return List.copyOf(result);
    }
    static String string(JsonNode n,String field,int limit) {
        var v=n.get(field);
        if (v==null || !v.isString() || v.stringValue().isEmpty() || v.stringValue().length()>limit) throw CimdDestination.invalid();
        return v.stringValue();
    }
    private static Set<String> strings(JsonNode n,String field,int max) {
        var v=n.get(field);
        if (v==null || !v.isArray() || v.isEmpty() || v.size()>max) throw CimdDestination.invalid();
        var result=new LinkedHashSet<String>();
        for (var item:v) if (!item.isString() || item.stringValue().isEmpty() || item.stringValue().length()>2048 || !result.add(item.stringValue())) throw CimdDestination.invalid();
        return Collections.unmodifiableSet(result);
    }
    static String digest(byte[] metadata,List<CimdMetadataService.SigningKey> keys) {
        try {
            var sha=MessageDigest.getInstance("SHA-256"); sha.update(metadata);
            for (var key:keys) { sha.update(key.kid().getBytes(StandardCharsets.UTF_8)); sha.update(key.rsa().getEncoded()); }
            return HexFormat.of().formatHex(sha.digest());
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(); }
    }
}
