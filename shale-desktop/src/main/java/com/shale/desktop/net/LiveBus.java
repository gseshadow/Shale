package com.shale.desktop.net;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LiveBus {
	private static final int HTTP_TIMEOUT_SECS = 12;

	public static final class Event {
		public final int schemaVersion;
		public final String eventId;
		public final String timestamp;

		public final String type;
		public final String entityType;
		public final Long entityId;
		public final int updatedByUserId;
		public final Integer shaleClientId;
		public final String patchRaw;
		public final String raw;
		public final String clientInstanceId;

		public Event(int schemaVersion, String eventId, String timestamp,
				String type, String entityType, Long entityId,
				int updatedByUserId, Integer shaleClientId,
				String patchRaw, String clientInstanceId, String raw) {

			this.schemaVersion = schemaVersion;
			this.eventId = eventId;
			this.timestamp = timestamp;

			this.type = type;
			this.entityType = entityType;
			this.entityId = entityId;
			this.updatedByUserId = updatedByUserId;
			this.shaleClientId = shaleClientId;
			this.patchRaw = patchRaw;
			this.clientInstanceId = (clientInstanceId == null ? "" : clientInstanceId);
			this.raw = raw;
		}
	}

	private static final Pattern JSON_KEY_PATTERN = Pattern.compile("\\\"([^\\\"]+)\\\"\\s*:");

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

	private final String clientInstanceId = UUID.randomUUID().toString();

	public LiveBus(NegotiateClient negotiate, int shaleClientId, int userId) {
		this.negotiate = Objects.requireNonNull(negotiate);
		this.shaleClientId = shaleClientId;
		this.userId = userId;
		this.publishEndpointUrl = getConfig("LIVE_PUBLISH_ENDPOINT_URL");
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
		return publishEntityUpdated("Case", caseId, tenantId, updatedByUserId, null);
	}

	public CompletableFuture<Void> publishCaseNameUpdated(int caseId, int shaleClientId, int updatedByUserId, String newName) {
		String safeName = newName == null ? "" : escapeJson(newName);
		String patchJson = "{\"name\":\"" + safeName + "\"}";
		return publishEntityUpdated("Case", caseId, shaleClientId, updatedByUserId, patchJson);
	}

	public CompletableFuture<Void> publishEntityUpdated(String entityType, long entityId,
			int shaleClientId, int updatedByUserId,
			String patchJsonOrNull) {
		if (publishEndpointUrl == null || publishEndpointUrl.isBlank()) {
			return CompletableFuture.failedFuture(new IllegalStateException(
					"Missing LIVE_PUBLISH_ENDPOINT_URL for server-side publish endpoint"));
		}

		String body = "{\"eventId\":\"" + UUID.randomUUID() + "\""
				+ ",\"type\":\"EntityUpdated\""
				+ ",\"entityType\":\"" + escapeJson(entityType) + "\""
				+ ",\"entityId\":" + entityId
				+ ",\"shaleClientId\":" + shaleClientId
				+ ",\"updatedByUserId\":" + updatedByUserId
				+ ",\"clientInstanceId\":\"" + clientInstanceId + "\""
				+ ",\"timestamp\":\"" + Instant.now() + "\""
				+ (patchJsonOrNull == null || patchJsonOrNull.isBlank() ? "" : ",\"patch\":" + patchJsonOrNull)
				+ "}";
		System.out.println("[LIVE] LIVE_PUBLISH_ENDPOINT_URL:" + getConfig("LIVE_PUBLISH_ENDPOINT_URL"));
		System.out.println("[LIVE] server publish requested: entityType=" + entityType
				+ " entityId=" + entityId
				+ " clientId=" + shaleClientId
				+ " updatedBy=" + updatedByUserId);

		HttpRequest.Builder builder = HttpRequest.newBuilder()
				.uri(URI.create(publishEndpointUrl))
				.timeout(Duration.ofSeconds(HTTP_TIMEOUT_SECS))
				.header("Content-Type", "application/json")
				.header("Accept", "application/json, text/plain; q=0.8")
				.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));

		String functionKey = getConfig("FUNCTION_KEY");
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

		String type = null;
		String entityType = null;
		Long entityId = null;
		String patchRaw = null;
		Integer schemaVersionBoxed = extractInt(dataJson, "schemaVersion");
		int schemaVersion = (schemaVersionBoxed == null ? 1 : schemaVersionBoxed.intValue());
		String eventId = extractString(dataJson, "eventId");
		String timestamp = extractString(dataJson, "timestamp");
		String inboundClientInstanceId = extractString(dataJson, "clientInstanceId");

		if (inboundClientInstanceId == null)
			inboundClientInstanceId = "";
		if (eventId == null)
			eventId = "";
		if (timestamp == null)
			timestamp = "";

		String legacyType = extractString(dataJson, "event");
		if ("CaseUpdated".equals(legacyType)) {
			type = "EntityUpdated";
			entityType = "Case";
			Integer caseId = extractInt(dataJson, "caseId");
			if (caseId != null) {
				entityId = caseId.longValue();
			}
		} else {
			type = extractString(dataJson, "type");
			entityType = extractString(dataJson, "entityType");
			entityId = extractLong(dataJson, "entityId");
			patchRaw = extractObject(dataJson, "patch");
		}

		Integer tenantId = extractInt(dataJson, "shaleClientId");
		Integer by = extractInt(dataJson, "updatedByUserId");
		if (by == null)
			by = extractInt(dataJson, "by");
		if (type == null || by == null || entityType == null || entityId == null)
			return;

		System.out.println("[LIVE RX] type=" + type + " entityType=" + entityType + " entityId=" + entityId + " patchKeys=" + String.join(",", patchKeys(patchRaw)));
		Event ev = new Event(schemaVersion, eventId, timestamp,
				type, entityType, entityId, by, tenantId, patchRaw, inboundClientInstanceId, raw);
		for (var l : listeners)
			l.accept(ev);
	}

	private static String getConfig(String key) {
		String v = System.getProperty(key);
		if (v == null || v.isBlank()) {
			v = System.getenv(key);
		}
		return v;
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

	private static Long extractLong(String json, String key) {
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
			return Long.parseLong(json.substring(j, k));
		} catch (Exception e) {
			return null;
		}
	}

	private static String extractObject(String json, String key) {
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
		if (j >= json.length() || json.charAt(j) != '{')
			return null;

		int depth = 0;
		for (int k = j; k < json.length(); k++) {
			char ch = json.charAt(k);
			if (ch == '{')
				depth++;
			else if (ch == '}') {
				depth--;
				if (depth == 0) {
					return json.substring(j, k + 1);
				}
			}
		}
		return null;
	}

	private static List<String> patchKeys(String patchRaw) {
		if (patchRaw == null || patchRaw.isBlank())
			return List.of();
		List<String> keys = new ArrayList<>();
		Matcher m = JSON_KEY_PATTERN.matcher(patchRaw);
		while (m.find()) {
			keys.add(m.group(1));
		}
		return keys;
	}

	private static String escapeJson(String value) {
		return value
				.replace("\\", "\\\\")
				.replace("\"", "\\\"")
				.replace("\n", "\\n")
				.replace("\r", "\\r");
	}

	private static String preview(String text, int maxChars) {
		if (text == null)
			return "<null>";
		String compact = text.replaceAll("\\s+", " ");
		return compact.substring(0, Math.min(maxChars, compact.length()));
	}

	public String getClientInstanceId() {
		return clientInstanceId;
	}
}