package com.shale.desktop.net;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class LiveBus {

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

	private final CopyOnWriteArrayList<Consumer<Event>> listeners = new CopyOnWriteArrayList<>();
	private volatile LiveBusClient wsClient;
	private volatile String groupName;

	public LiveBus(NegotiateClient negotiate, int shaleClientId, int userId) {
		this.negotiate = Objects.requireNonNull(negotiate);
		this.shaleClientId = shaleClientId;
		this.userId = userId;
	}

	public CompletableFuture<Void> connectAndJoin() {
		groupName = "tenant:" + shaleClientId;
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
		}).thenCompose(ws -> wsClient.joinGroup(groupName, 1)).thenAccept(v -> {
		});
	}

	public CompletableFuture<Void> publishCaseUpdated(int caseId, int tenantId, int updatedByUserId) {
		ensureReady();
		String payload = "{\"event\":\"CaseUpdated\",\"caseId\":" + caseId
				+ ",\"shaleClientId\":" + tenantId
				+ ",\"updatedByUserId\":" + updatedByUserId + "}";
		return wsClient.sendToGroup(groupName, payload, 2);
	}

	public void onEvent(Consumer<Event> listener) {
		listeners.add(listener);
	}

	public void shutdown() {
		// no-op for now
	}

	private void ensureReady() {
		if (wsClient == null || groupName == null) {
			throw new IllegalStateException("LiveBus not connected. Call connectAndJoin() first.");
		}
	}

	private void handleInbound(String text) {
		if (!(text.contains("\"type\":\"message\"") && text.contains("\"dataType\":\"json\"")))
			return;
		int i = text.indexOf("\"data\":");
		if (i < 0)
			return;
		String sub = text.substring(i + 7).trim();
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

		Event ev = new Event(type, by, caseId, tenantId, text);
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
}
