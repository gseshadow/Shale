package com.shale.server.live;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.shale.core.model.ReleaseChannel;

/** Publishes the existing LiveBus JSON envelope to the configured Azure Function endpoint. */
public final class HttpInvalidationPublisher implements InvalidationPublisher {
	private static final Duration TIMEOUT = Duration.ofSeconds(8);
	private final URI endpoint;
	private final String functionKey;
	private final HttpClient http;

	public HttpInvalidationPublisher(String endpoint, String functionKey) {
		this(URI.create(Objects.requireNonNull(endpoint, "endpoint")), functionKey,
				HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
	}
	HttpInvalidationPublisher(URI endpoint, String functionKey, HttpClient http) {
		this.endpoint=endpoint;this.functionKey=functionKey;this.http=Objects.requireNonNull(http);
	}

	@Override public void sessionInvalidated(int tenant, UUID sessionId) {
		if(tenant<=0)throw new IllegalArgumentException("tenant");Objects.requireNonNull(sessionId);
		publish("{\"schemaVersion\":1,\"eventId\":\""+UUID.randomUUID()+"\",\"timestamp\":\""+Instant.now()
				+"\",\"type\":\"SESSION_INVALIDATED\",\"shaleClientId\":"+tenant+",\"sessionId\":\""+sessionId+"\"}");
	}
	@Override public void applicationPolicyChanged(ReleaseChannel channel) {
		Objects.requireNonNull(channel);
		publish("{\"schemaVersion\":1,\"eventId\":\""+UUID.randomUUID()+"\",\"timestamp\":\""+Instant.now()
				+"\",\"type\":\"APPLICATION_POLICY_CHANGED\",\"channel\":\""+channel.name()+"\"}");
	}
	private void publish(String json) {
		HttpRequest.Builder request=HttpRequest.newBuilder(endpoint).timeout(TIMEOUT).header("Content-Type","application/json")
				.POST(HttpRequest.BodyPublishers.ofString(json,StandardCharsets.UTF_8));
		if(functionKey!=null&&!functionKey.isBlank()&&(endpoint.getQuery()==null||!endpoint.getQuery().contains("code=")))request.header("x-functions-key",functionKey);
		try { HttpResponse<Void> response=http.send(request.build(),HttpResponse.BodyHandlers.discarding());
			if(response.statusCode()/100!=2)throw new IllegalStateException("Invalidation publish returned HTTP "+response.statusCode());
		} catch (InterruptedException e) { Thread.currentThread().interrupt();throw new IllegalStateException("Invalidation publish interrupted",e);
		} catch (java.io.IOException e) { throw new IllegalStateException("Invalidation publish unavailable",e); }
	}
}
