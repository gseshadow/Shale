package com.shale.server.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

/** Dedicated non-tenant release-pipeline credential; tenant administrator tokens never satisfy it. */
@Component
public final class ReleaseControlPlaneAuthorizer {
	private final byte[] expected;
	private final String operatorId;
	public ReleaseControlPlaneAuthorizer(@Value("${SHALE_RELEASE_CONTROL_PLANE_TOKEN:}") String token,
			@Value("${SHALE_RELEASE_CONTROL_PLANE_OPERATOR:release-pipeline}") String operatorId) {
		this.expected = token.getBytes(StandardCharsets.UTF_8); this.operatorId = operatorId;
	}
	public String require(String supplied) {
		if (expected.length < 32) throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Release control-plane import is not configured.");
		byte[] actual = supplied == null ? new byte[0] : supplied.getBytes(StandardCharsets.UTF_8);
		if (!MessageDigest.isEqual(expected, actual)) throw new ResponseStatusException(UNAUTHORIZED, "Release control-plane authorization failed.");
		return operatorId;
	}
}
