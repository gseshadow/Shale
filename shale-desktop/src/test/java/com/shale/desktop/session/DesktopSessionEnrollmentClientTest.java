package com.shale.desktop.session;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.net.ssl.SSLHandshakeException;

import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

class DesktopSessionEnrollmentClientTest {
	@Test void transportDiagnosticsDistinguishTimeoutConnectionAndTlsWithoutMessages() {
		assertEquals("REQUEST_TIMEOUT",DesktopSessionEnrollmentClient.transportFailureKind(new HttpTimeoutException("sensitive")));
		assertEquals("CONNECTION_FAILURE",DesktopSessionEnrollmentClient.transportFailureKind(new IOException("sensitive",new ConnectException("sensitive"))));
		assertEquals("TLS_FAILURE",DesktopSessionEnrollmentClient.transportFailureKind(new SSLHandshakeException("sensitive")));
		assertEquals("TRANSPORT_FAILURE",DesktopSessionEnrollmentClient.transportFailureKind(new IOException("sensitive")));
	}
	@Test void logoutDiagnosticsAreBoundedAndObserveEveryHttpResponse() throws Exception {
		String source=Files.readString(Path.of("src/main/java/com/shale/desktop/session/DesktopSessionEnrollmentClient.java"));
		assertEquals(1,count(source,"Desktop durable session logout response status={} elapsedMs={}.") ,"logout must report its HTTP outcome once");
		assertEquals(1,count(source,"Desktop durable session logout transport failure kind={} exceptionClass={} elapsedMs={}.") ,"logout transport diagnostics must use the bounded safe shape");
		org.junit.jupiter.api.Assertions.assertFalse(source.contains("logout transport failure {}"),"logout must never log unrestricted exception text");
	}
	@Test void httpFiveHundredRetainsTransientClassification() throws Exception {
		var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
		server.createContext("/api/auth/desktop-session",exchange->{exchange.sendResponseHeaders(500,-1);exchange.close();});
		server.start();
		try{
			var endpoint=URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/api/auth/desktop-session");
			var client=new DesktopSessionEnrollmentClient(endpoint,HttpClient.newHttpClient());
			var failure=org.junit.jupiter.api.Assertions.assertThrows(DesktopSessionEnrollmentClient.EnrollmentException.class,
					()->client.enrollRemembered("owner@test","password",44L,java.util.UUID.randomUUID()));
			assertEquals(DesktopSessionEnrollmentClient.Failure.TRANSIENT,failure.failure(),
					"HTTP 500 must remain retryable and must not become missing remember support");
		}finally{server.stop(0);}
	}
	private static int count(String value,String needle){int total=0,offset=0;while((offset=value.indexOf(needle,offset))>=0){total++;offset+=needle.length();}return total;}
}
