package com.shale.desktop.session;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import javax.net.ssl.SSLException;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Narrow HTTP client for enrollment/logout; response bodies and bearer values are never logged. */
public class DesktopSessionEnrollmentClient {
	private static final Logger log=LoggerFactory.getLogger(DesktopSessionEnrollmentClient.class);
	public enum Validation { VALID, REVOKED, UNKNOWN }
    public enum Failure { ENDPOINT_UNAVAILABLE, TRANSIENT, SECURITY_REJECTED, MALFORMED_RESPONSE }
    public static final class EnrollmentException extends Exception {private final Failure failure;public EnrollmentException(Failure f){super(f.name());failure=f;}public EnrollmentException(Failure f,Throwable cause){super(f.name(),cause);failure=f;}public Failure failure(){return failure;}}
    private final URI endpoint;private final HttpClient http;private final Gson gson=new Gson();
    public DesktopSessionEnrollmentClient(String apiBaseUrl){this(URI.create(trim(apiBaseUrl)+"/api/auth/desktop-session"),HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build());}
    DesktopSessionEnrollmentClient(URI endpoint,HttpClient http){this.endpoint=endpoint;this.http=http;}
    public DesktopServerSession.Credential enroll(String email,String password,Long instanceId)throws EnrollmentException{
        String body=gson.toJson(new Request(email,password,instanceId));
        HttpRequest request=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(8)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body,StandardCharsets.UTF_8)).build();
        long started=System.nanoTime();
        try{
            HttpResponse<String> response=http.send(request,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));int status=response.statusCode();
            log.info("Desktop durable session enrollment response status={} elapsedMs={}.",status,elapsedMillis(started));
            if(status==404||status==501)throw new EnrollmentException(Failure.ENDPOINT_UNAVAILABLE);
            if(status==401||status==403)throw new EnrollmentException(Failure.SECURITY_REJECTED);
            if(status<200||status>=300)throw new EnrollmentException(status>=500?Failure.TRANSIENT:Failure.SECURITY_REJECTED);
            try{Response value=gson.fromJson(response.body(),Response.class);return new DesktopServerSession.Credential(value.accessToken,java.util.UUID.fromString(value.sessionId),java.util.UUID.fromString(value.currentJti),java.time.Instant.parse(value.expiresAt));}
            catch(RuntimeException bad){throw new EnrollmentException(Failure.MALFORMED_RESPONSE,bad);}
        }catch(EnrollmentException e){throw e;}catch(IOException e){logTransportFailure(e,started);throw new EnrollmentException(Failure.TRANSIENT,e);}catch(InterruptedException e){Thread.currentThread().interrupt();logTransportFailure(e,started);throw new EnrollmentException(Failure.TRANSIENT,e);}
    }
    public void logout(String token){if(token==null||token.isBlank())return;try{http.send(HttpRequest.newBuilder(endpoint.resolve("/api/auth/logout")).timeout(Duration.ofSeconds(4)).header("Authorization","Bearer "+token).POST(HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.discarding());}catch(IOException e){/* best effort; never log credential */}catch(InterruptedException e){Thread.currentThread().interrupt();}}
	public Validation validate(String token){if(token==null||token.isBlank())return Validation.REVOKED;try{var response=http.send(HttpRequest.newBuilder(endpoint.resolve("/api/sessions")).timeout(Duration.ofSeconds(6)).header("Authorization","Bearer "+token).GET().build(),HttpResponse.BodyHandlers.discarding());if(response.statusCode()==401||response.statusCode()==403)return Validation.REVOKED;return response.statusCode()/100==2?Validation.VALID:Validation.UNKNOWN;}catch(IOException e){return Validation.UNKNOWN;}catch(InterruptedException e){Thread.currentThread().interrupt();return Validation.UNKNOWN;}}
    private static String trim(String value){if(value==null||value.isBlank())throw new IllegalArgumentException("apiBaseUrl");return value.trim().replaceAll("/+$","");}
    static String transportFailureKind(Throwable failure){if(hasCause(failure,HttpTimeoutException.class))return "REQUEST_TIMEOUT";if(hasCause(failure,SSLException.class))return "TLS_FAILURE";if(hasCause(failure,ConnectException.class))return "CONNECTION_FAILURE";return "TRANSPORT_FAILURE";}
    private static void logTransportFailure(Throwable failure,long started){log.warn("Desktop durable session enrollment transport failure kind={} exceptionClass={} elapsedMs={}.",transportFailureKind(failure),failure.getClass().getSimpleName(),elapsedMillis(started));}
    private static boolean hasCause(Throwable failure,Class<? extends Throwable> type){for(Throwable current=failure;current!=null;current=current.getCause())if(type.isInstance(current))return true;return false;}
    private static long elapsedMillis(long started){return Math.max(0L,java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started));}
    private record Request(String email,String password,Long applicationInstanceId){}
    private record Response(String accessToken,String sessionId,String currentJti,String expiresAt){}
}
