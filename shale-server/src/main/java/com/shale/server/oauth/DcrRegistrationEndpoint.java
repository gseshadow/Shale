package com.shale.server.oauth;

import com.nimbusds.jose.jwk.*;
import com.shale.core.security.ai.AiScope;
import com.shale.data.security.ai.AiDcrAdmissionStore;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import tools.jackson.databind.*;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;

/** RFC 7591 endpoint behind explicit runtime eligibility. No tenant identity or authority is accepted. */
final class DcrRegistrationEndpoint {
    static final int MAX_BYTES=32768;
    private final DcrApplicationRegistration registration;
    private final AiDcrAdmissionStore admission;
    private final ObjectMapper json=new ObjectMapper(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).streamReadConstraints(tools.jackson.core.StreamReadConstraints.builder().maxNestingDepth(8).maxStringLength(16384).maxNameLength(128).maxNumberLength(20).build()).build()).rebuild().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    DcrRegistrationEndpoint(DcrCapabilities capabilities) {
        registration=new DcrApplicationRegistration(capabilities.registrar());admission=capabilities.admission();
    }
    boolean enabled(){try{return admission.enabled();}catch(RuntimeException unavailable){return false;}}
    void handle(HttpServletRequest req,HttpServletResponse res)throws IOException {
        res.setHeader("Cache-Control","no-store");res.setHeader("Pragma","no-cache");
        res.setCharacterEncoding("UTF-8");res.setContentType("application/json");
        try {
            // Never trust caller-supplied X-Forwarded-For. The source budget uses the socket peer; Azure ingress/NAT acceptance is separate.
            byte[] peer=MessageDigest.getInstance("SHA-256").digest(Objects.requireNonNull(req.getRemoteAddr()).getBytes(StandardCharsets.UTF_8));
            var rate=admission.admit(peer);
            if(!rate.allowed()){error(res,429,"invalid_client_metadata",rate.retryAfter());return;}
            if(req.getContentLengthLong()>MAX_BYTES){error(res,413,"invalid_client_metadata",0);return;}
            if(req.getContentType()==null)throw invalid();
            var contentType=org.springframework.http.MediaType.parseMediaType(req.getContentType());
            if(!contentType.getType().equals("application")||!contentType.getSubtype().equals("json"))throw invalid();
            if(req.getHeader("Content-Encoding")!=null&&!req.getHeader("Content-Encoding").equalsIgnoreCase("identity"))throw invalid();
            byte[] body=req.getInputStream().readNBytes(MAX_BYTES+1);
            if(body.length>MAX_BYTES){error(res,413,"invalid_client_metadata",0);return;}
            JsonNode root;
            try { root=json.readTree(StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(body)).toString()); }
            catch(tools.jackson.core.JacksonException|java.nio.charset.CharacterCodingException bad){throw invalid();}
            if(root==null||!root.isObject()||root.size()>32)throw invalid();
            for(String forbidden:List.of("client_id","client_secret","registration_access_token","registration_client_uri","client_id_issued_at","client_secret_expires_at","software_statement","jwks_uri"))
                if(root.has(forbidden))throw invalid();
            String method=text(root,"token_endpoint_auth_method","client_secret_basic");
            if(!Set.of("none","private_key_jwt").contains(method))throw invalid();
            String name=text(root,"client_name","MCP application");
            var redirects=strings(root,"redirect_uris",null);
            if(redirects==null||redirects.isEmpty()||redirects.size()>5)throw redirectError();
            try{for(String uri:redirects)DcrApplicationRegistration.redirect(uri,method.equals("private_key_jwt"));}
            catch(IllegalArgumentException bad){throw redirectError();}
            var grants=strings(root,"grant_types",List.of("authorization_code"));
            if(!grants.contains("authorization_code")||!Set.of("authorization_code","refresh_token").containsAll(grants))throw invalid();
            var responses=strings(root,"response_types",List.of("code"));
            if(!responses.equals(List.of("code")))throw invalid();
            String scope=text(root,"scope","cases.search cases.overview.read");
            var scopes=new LinkedHashSet<AiScope>();
            for(String s:scope.split(" ",-1)) {
                AiScope selected=switch(s){case "cases.search"->AiScope.fromMask(1).iterator().next();case "cases.overview.read"->AiScope.fromMask(2).iterator().next();default->throw invalid();};
                if(!scopes.add(selected))throw invalid();
            }
            String app=text(root,"application_type",null);
            if(app!=null&&!Set.of("web","native").contains(app))throw invalid();
            if("web".equals(app)&&redirects.stream().anyMatch(u->!u.startsWith("https://")))throw redirectError();
            String algorithm=text(root,"token_endpoint_auth_signing_alg",null);
            if(algorithm!=null&&(!method.equals("private_key_jwt")||!algorithm.equals("RS256")))throw invalid();
            if(root.has("code_challenge_method")&&!text(root,"code_challenge_method","").equals("S256"))throw invalid();
            var keys=new ArrayList<RSAKey>();
            if(root.has("jwks")) {
                if(!method.equals("private_key_jwt")||!root.get("jwks").isObject())throw invalid();
                var parsed=JWKSet.parse(root.get("jwks").toString());
                for(JWK key:parsed.getKeys()){if(!(key instanceof RSAKey rsa))throw invalid();keys.add(rsa);}
            }
            var created=registration.register(name,redirects,scopes,method,keys);
            var result=new LinkedHashMap<String,Object>();
            result.put("client_id",created.identifier().value());result.put("client_id_issued_at",created.issuedAt().getEpochSecond());
            result.put("client_name",name);result.put("redirect_uris",redirects);result.put("grant_types",List.of("authorization_code","refresh_token"));
            result.put("response_types",responses);result.put("scope",scope);result.put("token_endpoint_auth_method",method);
            if(app!=null)result.put("application_type",app);
            if(method.equals("private_key_jwt")){result.put("jwks",JWKSet.parse(root.get("jwks").toString()).toJSONObject());result.put("token_endpoint_auth_signing_alg","RS256");}
            res.setStatus(201);res.getWriter().write(json.writeValueAsString(result));
        } catch(RegistrationError bad){error(res,400,bad.code,0);}
        catch(IllegalArgumentException|java.text.ParseException bad){error(res,400,"invalid_client_metadata",0);}
        catch(Exception unavailable){
            boolean limited=false;
            for(Throwable x=unavailable;x!=null;x=x.getCause())if(x instanceof java.sql.SQLException sql&&sql.getErrorCode()==59729)limited=true;
            error(res,limited?429:503,limited?"invalid_client_metadata":"temporarily_unavailable",limited?60:0);
        }
    }
    private static String text(JsonNode root,String key,String fallback) {
        if(!root.has(key))return fallback;
        if(!root.get(key).isString())throw invalid();return root.get(key).asString();
    }
    private static List<String> strings(JsonNode root,String key,List<String> fallback) {
        if(!root.has(key))return fallback;
        var n=root.get(key);if(!n.isArray()||n.size()>8)throw invalid();
        var result=new ArrayList<String>();for(var v:n){if(!v.isString()||result.contains(v.asString()))throw invalid();result.add(v.asString());}
        return result;
    }
    private static void error(HttpServletResponse res,int status,String code,int retry)throws IOException {
        res.setStatus(status);if(retry>0)res.setHeader("Retry-After",Integer.toString(retry));
        res.getWriter().write("{\"error\":\""+code+"\"}");
    }
    private static RegistrationError invalid(){return new RegistrationError("invalid_client_metadata");}
    private static RegistrationError redirectError(){return new RegistrationError("invalid_redirect_uri");}
    private static final class RegistrationError extends IllegalArgumentException {
        final String code;RegistrationError(String code){super(code);this.code=code;}
    }
}
