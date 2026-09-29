package com.shale.desktop.session;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import com.google.gson.Gson;
import com.shale.ui.services.UiRuntimeBridge;

/** Narrow Phase 8A self-session adapter. It never logs bearer or response data. */
public final class UserSessionManagementClient implements UiRuntimeBridge.UserSessionManagement {
    private final URI base; private final HttpClient http; private final DesktopServerSession session;
    private final Gson gson=new Gson();
    public UserSessionManagementClient(String apiBase,DesktopServerSession session){this(URI.create(apiBase.replaceAll("/+$","")+"/api/sessions"),HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build(),session);}
    UserSessionManagementClient(URI base,HttpClient http,DesktopServerSession session){this.base=base;this.http=http;this.session=session;}
    @Override public List<UiRuntimeBridge.UserSessionView> list(){
        HttpResponse<String> response=send(HttpRequest.newBuilder(base).GET());
        try { Raw[] rows=gson.fromJson(response.body(),Raw[].class);if(rows==null)return List.of();return Arrays.stream(rows).map(r->new UiRuntimeBridge.UserSessionView(UUID.fromString(r.sessionId),r.clientType,Instant.parse(r.issuedAt),Instant.parse(r.expiresAt),instant(r.lastRefreshedAt),instant(r.revokedAt),r.currentSession)).toList(); }
        catch(RuntimeException malformed){throw new SessionManagementException("Session information could not be read.",malformed);}
    }
    @Override public void revoke(UUID id){Objects.requireNonNull(id);send(HttpRequest.newBuilder(base.resolve("sessions/"+id+"/revoke")).POST(HttpRequest.BodyPublishers.noBody()));}
    @Override public void revokeOthers(){send(HttpRequest.newBuilder(base.resolve("sessions/revoke-others")).POST(HttpRequest.BodyPublishers.noBody()));}
    private HttpResponse<String> send(HttpRequest.Builder builder){String token=session.bearerToken().orElseThrow(()->new SessionManagementException("Session management is unavailable."));try{HttpResponse<String> r=http.send(builder.timeout(Duration.ofSeconds(8)).header("Authorization","Bearer "+token).build(),HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));if(r.statusCode()<200||r.statusCode()>=300)throw new SessionManagementException("Session management is temporarily unavailable.");return r;}catch(IOException e){throw new SessionManagementException("Session management is temporarily unavailable.",e);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new SessionManagementException("Session management is temporarily unavailable.",e);}}
    private static Instant instant(String value){return value==null?null:Instant.parse(value);}
    private record Raw(String sessionId,String clientType,String issuedAt,String expiresAt,String lastRefreshedAt,String revokedAt,boolean currentSession){}
    public static final class SessionManagementException extends RuntimeException {public SessionManagementException(String message){super(message);}public SessionManagementException(String message,Throwable cause){super(message,cause);}}
}
