package com.shale.server.oauth;

import java.io.IOException;
import java.util.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Explicit bearer-only browser protocol surface. No ambient cookies => no second login/session system.
 * A random durable intent plus revalidated original WEB session protects approval from CSRF/substitution. */
final class OAuthHumanFilter extends OncePerRequestFilter {
    private final ShaleOAuthHuman humans;
    private final DurableOAuthAuthorization authorizations;
    private final CimdApplicationAccess applications;
    OAuthHumanFilter(ShaleOAuthHuman humans, DurableOAuthAuthorization authorizations) { this(humans,authorizations,null); }
    OAuthHumanFilter(ShaleOAuthHuman humans,DurableOAuthAuthorization authorizations,CimdApplicationAccess applications) { this.humans=humans; this.authorizations=authorizations;this.applications=applications; }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        if(request.getRequestURI().substring(request.getContextPath().length()).equals("/oauth/mcp/token") || OAuthRevocation.matches(request)) {
            chain.doFilter(request,response); return;
        }
        try {
            if(request.getContentLengthLong()>16384 || (request.getQueryString()!=null && request.getQueryString().length()>16384)) {
                response.setStatus(400); return;
            }
            var identity=humans.authenticate(request);
            SecurityContextHolder.getContext().setAuthentication(ShaleOAuthHuman.authentication(identity));
            String path=request.getRequestURI().substring(request.getContextPath().length());
            boolean onboarding=applications!=null&&path.equals("/oauth/mcp/onboarding"),decision=applications!=null&&path.equals("/oauth/mcp/approval");
            Set<String> allowed= onboarding?Set.of("client_id","scope"):decision?Set.of("client_id","scope","approval_id","approval_version","decision"):
                    "POST".equals(request.getMethod()) ? Set.of("client_id","state","scope") :
                    path.endsWith("/consent") ? Set.of("client_id","state","scope") : Set.of("client_id","response_type","redirect_uri","resource","scope","state","code_challenge","code_challenge_method");
            if(request.getParameterMap().size()>allowed.size()) throw new IllegalArgumentException();
            for(var entry:request.getParameterMap().entrySet()) {
                boolean consentScopes="POST".equals(request.getMethod()) && entry.getKey().equals("scope");
                if(!allowed.contains(entry.getKey()) || entry.getValue().length<1 || entry.getValue().length>(consentScopes ? 2 : 1)) throw new IllegalArgumentException();
                for(String value:entry.getValue()) {
                    if(value.length()>2048) throw new IllegalArgumentException();
                    if(consentScopes) com.shale.core.security.ai.AiScope.parse(value);
                }
                if(consentScopes && new HashSet<>(List.of(entry.getValue())).size()!=entry.getValue().length) throw new IllegalArgumentException();
            }
            if(onboarding||decision) {
                if(!"POST".equals(request.getMethod()))throw new IllegalArgumentException();
                var scopes=java.util.EnumSet.noneOf(com.shale.core.security.ai.AiScope.class);
                for(String value:Optional.ofNullable(request.getParameterValues("scope")).orElse(new String[0]))scopes.add(com.shale.core.security.ai.AiScope.parse(value));
                if(onboarding&&scopes.isEmpty())throw new IllegalArgumentException();
                Map<String,Object> result;
                if(onboarding)result=applications.request(request.getParameter("client_id"),scopes);
                else {
                    String version=request.getParameter("approval_version");
                    if(version==null||!version.matches("[0-9a-f]{16}"))throw new IllegalArgumentException();
                    result=applications.decide(request.getParameter("client_id"),UUID.fromString(request.getParameter("approval_id")),
                            HexFormat.of().parseHex(version),request.getParameter("decision"),scopes);
                }
                new org.springframework.http.converter.json.JacksonJsonHttpMessageConverter().write(result,org.springframework.http.MediaType.APPLICATION_JSON,
                        new org.springframework.http.server.ServletServerHttpResponse(response));
            } else if(path.equals("/oauth/mcp/consent")) {
                if(!"GET".equals(request.getMethod())) throw new IllegalArgumentException();
                var pending=authorizations.findByToken(request.getParameter("state"),new org.springframework.security.oauth2.server.authorization.OAuth2TokenType("state"));
                if(pending==null) throw new IllegalArgumentException();
                var original=(org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest)pending.getAttribute(org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest.class.getName());
                if(!original.getClientId().equals(request.getParameter("client_id"))) throw new IllegalArgumentException();
                new org.springframework.http.converter.json.JacksonJsonHttpMessageConverter().write(Map.of("client_id",original.getClientId(),
                        "client_name",authorizations.findByClientId(original.getClientId()).getClientName(),"expires_at",pending.getAttribute("consentExpiresAt"),
                        "scopes",original.getScopes(),"state",request.getParameter("state"),"tenant",identity.tenant(),
                        "user",identity.user(),"intent_expires_at",pending.getAttribute("intentExpiresAt"),
                        "notice","Approval delegates these Case read scopes to this client for one hour. Minimized Case names, numbers and staff identities may leave Shale."),
                        org.springframework.http.MediaType.APPLICATION_JSON,new org.springframework.http.server.ServletServerHttpResponse(response));
            } else chain.doFilter(request,response);
        } catch(SecurityException denied) { if(!response.isCommitted()) { response.resetBuffer(); response.setStatus(401); } }
        catch(IllegalArgumentException invalid) { if(!response.isCommitted()) { response.resetBuffer(); response.setStatus(400); } }
        catch(IllegalStateException unavailable) { if(!response.isCommitted()) { response.resetBuffer(); response.setStatus(503); } }
        finally { SecurityContextHolder.clearContext(); }
    }
}
