package com.shale.desktop.session;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import com.google.gson.Gson;
import com.shale.ui.services.UiRuntimeBridge;
import org.slf4j.Logger;import org.slf4j.LoggerFactory;

/** Narrow HTTP adapter for the existing Phase 8A tenant-admin session API. */
public final class AdminSessionManagementClient implements UiRuntimeBridge.AdminSessionManagement {
    private static final Logger log=LoggerFactory.getLogger(AdminSessionManagementClient.class);
    private final URI base; private final HttpClient http; private final DesktopServerSession session; private final Gson gson=new Gson();
    public AdminSessionManagementClient(String apiBase,DesktopServerSession session){this(URI.create(apiBase.replaceAll("/+$","")+"/api/admin/sessions"),HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build(),session);}
    AdminSessionManagementClient(URI base,HttpClient http,DesktopServerSession session){this.base=base;this.http=http;this.session=session;}
    @Override public UiRuntimeBridge.AdminSessionPage list(UiRuntimeBridge.AdminSessionFilter f){Objects.requireNonNull(f);StringJoiner q=new StringJoiner("&","?","");q.add("page="+f.page()).add("size="+f.size()).add("activeOnly="+f.activeOnly());if(f.userId()!=null)q.add("userId="+f.userId());if(f.clientType()!=null)q.add("clientType="+encode(f.clientType()));if(f.since()!=null)q.add("since="+encode(f.since().toString()));HttpResponse<String> response=send(HttpRequest.newBuilder(URI.create(base+q.toString())).GET(),true);try{RawPage page=gson.fromJson(response.body(),RawPage.class);List<UiRuntimeBridge.AdminSessionView> rows=page==null||page.items==null?List.of():Arrays.stream(page.items).map(AdminSessionManagementClient::view).toList();return new UiRuntimeBridge.AdminSessionPage(rows,page==null?f.page():page.page,page==null?f.size():page.size);}catch(RuntimeException malformed){log.warn("Administrative session list response parsing failed exceptionClass={}.",malformed.getClass().getSimpleName());throw new SessionManagementException("Administrative session information could not be read.",malformed);}}
    @Override public void revoke(UUID id){Objects.requireNonNull(id);send(HttpRequest.newBuilder(URI.create(base+"/"+id+"/revoke")).POST(HttpRequest.BodyPublishers.noBody()),false);}
    private HttpResponse<String> send(HttpRequest.Builder builder,boolean list){String token=session.bearerToken().orElseThrow(()->new SessionManagementException("Administrative session management is unavailable."));long started=System.nanoTime();try{HttpResponse<String> r=http.send(builder.timeout(Duration.ofSeconds(8)).header("Authorization","Bearer "+token).build(),HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));if(list)log.info("Administrative session list response status={} elapsedMs={}.",r.statusCode(),elapsedMillis(started));if(r.statusCode()==401||r.statusCode()==403)throw new AuthorizationException("Administrator access is unavailable.");if(r.statusCode()<200||r.statusCode()>=300)throw new SessionManagementException("Administrative session management is temporarily unavailable.");return r;}catch(IOException e){if(list)logTransportFailure(e,started);throw new SessionManagementException("Administrative session management is temporarily unavailable.",e);}catch(InterruptedException e){Thread.currentThread().interrupt();if(list)logTransportFailure(e,started);throw new SessionManagementException("Administrative session management is temporarily unavailable.",e);}}
    private static void logTransportFailure(Throwable failure,long started){log.warn("Administrative session list transport failure kind={} exceptionClass={} elapsedMs={}.",DesktopSessionEnrollmentClient.transportFailureKind(failure),failure.getClass().getSimpleName(),elapsedMillis(started));}
    private static long elapsedMillis(long started){return Math.max(0L,java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started));}
    private static UiRuntimeBridge.AdminSessionView view(Raw r){return new UiRuntimeBridge.AdminSessionView(UUID.fromString(r.sessionId),r.userId,r.userDisplayName,r.userEmail,r.clientType,Instant.parse(r.issuedAt),Instant.parse(r.expiresAt),instant(r.lastRefreshedAt),instant(r.revokedAt),r.revocationReason,r.currentSession);}
    private static Instant instant(String value){return value==null?null:Instant.parse(value);}private static String encode(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}
    private record RawPage(Raw[] items,int page,int size){} private record Raw(String sessionId,int userId,String userDisplayName,String userEmail,String clientType,String issuedAt,String expiresAt,String lastRefreshedAt,String revokedAt,String revocationReason,boolean currentSession){}
    public static class SessionManagementException extends RuntimeException{public SessionManagementException(String m){super(m);}public SessionManagementException(String m,Throwable c){super(m,c);}}
    public static final class AuthorizationException extends SessionManagementException{public AuthorizationException(String m){super(m);}}
}
