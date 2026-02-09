package com.shale.desktop.live;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class LiveBusClient {
	private final String clientUrl;
	private final LiveEventDispatcher dispatcher;

	private WebSocket ws;

	public LiveBusClient(String clientUrl, LiveEventDispatcher dispatcher) {
		this.clientUrl = clientUrl;
		this.dispatcher = dispatcher;
	}

	public void connect(int shaleClientId, int userId) {
		try {
			ws = HttpClient.newHttpClient()
					.newWebSocketBuilder()
					.buildAsync(URI.create(clientUrl), new ListenerImpl())
					.join();

			// Example: many hubs allow a "join group" message over the client protocol.
			// Your server-side permissions must allow this.
			String joinMsg = "{\"type\":\"joinGroup\",\"group\":\"tenant-" + shaleClientId + "\"}";
			ws.sendText(joinMsg, true);

		} catch (Exception ex) {
			throw new IllegalStateException("Live connect failed: " + ex.getMessage(), ex);
		}
	}

	public boolean isConnected() {
		return ws != null;
	}

	public CompletableFuture<WebSocket> send(String payloadJson) {
		if (ws == null)
			throw new IllegalStateException("WebSocket not connected");
		return ws.sendText(payloadJson, true);
	}

	public void close() {
		if (ws != null) {
			try {
				ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye").join();
			} catch (Exception ignored) {
			}
			ws = null;
		}
	}

	private final class ListenerImpl implements WebSocket.Listener {
		@Override
		public void onOpen(WebSocket webSocket) {
			webSocket.request(1);
		}

		@Override
		public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
			dispatcher.onMessageReceived(data.toString());
			webSocket.request(1);
			return null;
		}

		@Override
		public void onError(WebSocket webSocket, Throwable error) {
			dispatcher.onMessageReceived("[live] error: " + error.getMessage());
		}
	}
}
