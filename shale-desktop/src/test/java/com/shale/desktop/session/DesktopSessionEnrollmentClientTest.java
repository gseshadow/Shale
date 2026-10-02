package com.shale.desktop.session;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;

import javax.net.ssl.SSLHandshakeException;

import org.junit.jupiter.api.Test;

class DesktopSessionEnrollmentClientTest {
	@Test void transportDiagnosticsDistinguishTimeoutConnectionAndTlsWithoutMessages() {
		assertEquals("REQUEST_TIMEOUT",DesktopSessionEnrollmentClient.transportFailureKind(new HttpTimeoutException("sensitive")));
		assertEquals("CONNECTION_FAILURE",DesktopSessionEnrollmentClient.transportFailureKind(new IOException("sensitive",new ConnectException("sensitive"))));
		assertEquals("TLS_FAILURE",DesktopSessionEnrollmentClient.transportFailureKind(new SSLHandshakeException("sensitive")));
		assertEquals("TRANSPORT_FAILURE",DesktopSessionEnrollmentClient.transportFailureKind(new IOException("sensitive")));
	}
}
