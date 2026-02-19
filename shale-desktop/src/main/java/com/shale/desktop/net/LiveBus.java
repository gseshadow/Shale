package com.shale.desktop.net;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class LiveBus {
	private static final int HTTP_TIMEOUT_SECS = 12;

	public static final class Event {
		public final String type;
		public final int updatedByUserId;
		public final Integer caseId;
		public final Integer shaleClientId;
		public final String raw;

		public Event(String type, int updatedByUserId, Integer caseId, Integer shaleClientId, String raw) {
			this.type = type;
			this.updatedByUserId = updatedByUserId;
			this.caseId = caseId;
			this.shaleClientId = shaleClientId;
			this.raw = raw;
		}
	}

	private final NegotiateClient negotiate;
	private final int shaleClientId;
	private final int userId;
	private final String publishEndpointUrl;
	private final HttpClient http = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(HTTP_TIMEOUT_SECS))
			.build();

	private final CopyOnWriteArrayList<Consumer<Event>> listeners = new CopyOnWriteArrayList<>();
	private volatile LiveBusClient wsClient;
	private volatile String groupName;
	private volatile String connectionId;

	public LiveBus(NegotiateClient negotiate, int shaleClientId, int userId) {
		this.negotiate = Objects.requireNonNull(negotiate);
		this.shaleClientId = shaleClientId;
		this.userId = userId;
		this.publishEndpointUrl = System.getenv("LIVE_PUBLISH_ENDPOINT_URL");
	}

	public CompletableFuture<Void> connectAndJoin() {
		groupName = "client-" + shaleClientId;
		return CompletableFuture.supplyAsync(() ->
		{
			try {
				return negotiate.negotiateForTenant(shaleClientId, userId);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}).thenCompose(wss ->
		{
			wsClient = new LiveBusClient(new LiveBusClient.Handler() {
				@Override
				public void onMessage(String text) {
					handleInbound(text);
				}
			});
			return wsClient.connect(wss);
		}).thenCompose(ws -> wsClient.joinGroup(groupName, 1)
				.thenRun(() -> System.out.println("[LIVE] group joined: " + groupName))
				.exceptionally(ex ->
				{
					System.out.println("[LIVE] join group failed: " + ex.getMessage());
					throw new RuntimeException(ex);
				}));
	}

	public CompletableFuture<Void> publishCaseUpdated(int caseId, int tenantId, int updatedByUserId) {
		if (publishEndpointUrl == null || publishEndpointUrl.isBlank()) {
			return CompletableFuture.failedFuture(new IllegalStateException(
					"Missing LIVE_PUBLISH_ENDPOINT_URL for server-side publish endpoint"));
		}

		String body = "{\"caseId\":" + caseId
				+ ",\"shaleClientId\":" + tenantId
				+ ",\"updatedByUserId\":" + updatedByUserId + "}";
		System.out.println("[LIVE] server publish requested: caseId=" + caseId
				+ " clientId=" + tenantId
				+ " updatedBy=" + updatedByUserId);

		HttpRequest.Builder builder = HttpRequest.newBuilder()
				.uri(URI.create(publishEndpointUrl))
				.timeout(Duration.ofSeconds(HTTP_TIMEOUT_SECS))
				.header("Content-Type", "application/json")
				.header("Accept", "application/json, text/plain; q=0.8")
				.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));

		String functionKey = System.getenv("FUNCTION_KEY");
		if (functionKey != null && !functionKey.isBlank() && !publishEndpointUrl.contains("code=")) {
			builder.header("x-functions-key", functionKey);
		}

		return http.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
				.thenAccept(response ->
				{
					if (response.statusCode() / 100 != 2) {
						throw new IllegalStateException("Live publish failed: HTTP " + response.statusCode()
								+ " body=" + preview(response.body(), 200));
					}
				});
	}

	public void onEvent(Consumer<Event> listener) {
		listeners.add(listener);
	}

	public void shutdown() {
		// no-op for now
	}

	private void handleInbound(String text) {
		String raw = text == null ? "" : text;
		String preview = raw.length() > 200 ? raw.substring(0, 200) : raw;
		System.out.println("[LIVE RX RAW] " + preview);

		if (raw.contains("\"type\":\"connected\"")) {
			String connId = extractString(raw, "connectionId");
			if (connId != null && !connId.isBlank()) {
				connectionId = connId;
			}
			System.out.println("[LIVE] client connected: userId=" + userId + ", clientId=" + shaleClientId + ", connectionId=" + (connectionId == null ? "?" : connectionId));
			return;
		}

		if (!(raw.contains("\"type\":\"message\"") && raw.contains("\"dataType\":\"json\"")))
			return;
		int i = raw.indexOf("\"data\":");
		if (i < 0)
			return;
		String sub = raw.substring(i + 7).trim();
		String dataJson = extractFirstJsonObject(sub);
		if (dataJson == null)
			return;

		String type = extractString(dataJson, "event");
		Integer caseId = extractInt(dataJson, "caseId");
		Integer tenantId = extractInt(dataJson, "shaleClientId");
		Integer by = extractInt(dataJson, "updatedByUserId");
		if (by == null)
			by = extractInt(dataJson, "by");
		if (type == null || by == null)
			return;

		System.out.println("[LIVE RX] type=" + type + " caseId=" + caseId + " clientId=" + tenantId + " updatedBy=" + by);
		Event ev = new Event(type, by, caseId, tenantId, raw);
		for (var l : listeners)
			l.accept(ev);
	}

	private static String extractFirstJsonObject(String s) {
		int depth = 0, start = -1;
		for (int idx = 0; idx < s.length(); idx++) {
			char c = s.charAt(idx);
			if (c == '{') {
				if (depth++ == 0)
					start = idx;
			} else if (c == '}' && --depth == 0 && start >= 0)
				return s.substring(start, idx + 1);
		}
		return null;
	}

	private static String extractString(String json, String key) {
		String pat = "\"" + key + "\"";
		int i = json.indexOf(pat);
		if (i < 0)
			return null;
		int q1 = json.indexOf('"', json.indexOf(':', i) + 1);
		if (q1 < 0)
			return null;
		int q2 = json.indexOf('"', q1 + 1);
		if (q2 < 0)
			return null;
		return json.substring(q1 + 1, q2);
	}

	private static Integer extractInt(String json, String key) {
		String pat = "\"" + key + "\"";
		int i = json.indexOf(pat);
		if (i < 0)
			return null;
		int c = json.indexOf(':', i);
		if (c < 0)
			return null;
		int j = c + 1;
		while (j < json.length() && Character.isWhitespace(json.charAt(j)))
			j++;
		int k = j;
		while (k < json.length() && "-0123456789".indexOf(json.charAt(k)) >= 0)
			k++;
		if (k == j)
			return null;
		try {
			return Integer.parseInt(json.substring(j, k));
		} catch (Exception e) {
			return null;
		}
	}

	private static String preview(String text, int maxChars) {
		if (text == null)
			return "<null>";
		String compact = text.replaceAll("\\s+", " ");
		return compact.substring(0, Math.min(maxChars, compact.length()));
	}
}
