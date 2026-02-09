package com.shale.desktop.net;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class LiveBusClient implements WebSocket.Listener {
	public interface Handler {
		default void onOpen() {
		}

		default void onMessage(String text) {
		}

		default void onClosed(int statusCode, String reason) {
		}

		default void onError(Throwable error) {
		}
	}

	private final HttpClient http = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build();

	private final Handler handler;
	private volatile WebSocket socket;

	public LiveBusClient(Handler handler) {
		this.handler = handler;
	}

	/** Connects to the Azure Web PubSub client URL (wss://...) */
	public CompletableFuture<WebSocket> connect(String wssUrl) {
		return http.newWebSocketBuilder()
				.subprotocols("json.webpubsub.azure.v1") // REQUIRED for client actions
				.buildAsync(URI.create(wssUrl), this)
				.thenApply(ws ->
				{
					this.socket = ws;
					return ws;
				});
	}

	// --- WebSocket.Listener ---

	@Override
	public void onOpen(WebSocket ws) {
		ws.request(1); // start backpressure chain
		handler.onOpen();
	}

	@Override
	public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
		handler.onMessage(data.toString());
		ws.request(1);
		return null;
	}

	@Override
	public void onError(WebSocket ws, Throwable error) {
		handler.onError(error);
	}

	@Override
	public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
		handler.onClosed(statusCode, reason);
		return CompletableFuture.completedFuture(null);
	}

	// inside LiveBusClient
	public java.util.concurrent.CompletableFuture<Void> sendToGroup(String group, String jsonPayload, int ackId) {
		if (socket == null)
			return java.util.concurrent.CompletableFuture.failedFuture(
					new IllegalStateException("Socket not connected"));
		String frame = "{\"type\":\"sendToGroup\",\"group\":\"" + group + "\"," +
				"\"dataType\":\"json\",\"data\":" + jsonPayload + ",\"ackId\":" + ackId + "}";
		return socket.sendText(frame, true).thenAccept(v ->
		{
		});
	}

	public java.util.concurrent.CompletableFuture<Void> joinGroup(String group, int ackId) {
		if (socket == null)
			return java.util.concurrent.CompletableFuture.failedFuture(
					new IllegalStateException("Socket not connected"));
		String json = "{\"type\":\"joinGroup\",\"group\":\"" + group + "\",\"ackId\":" + ackId + "}";
		return socket.sendText(json, true).thenAccept(v ->
		{
			/* sent */ });
	}

	public java.util.concurrent.CompletableFuture<Void> leaveGroup(String group, int ackId) {
		if (socket == null)
			return java.util.concurrent.CompletableFuture.failedFuture(
					new IllegalStateException("Socket not connected"));
		String json = "{\"type\":\"leaveGroup\",\"group\":\"" + group + "\",\"ackId\":" + ackId + "}";
		return socket.sendText(json, true).thenAccept(v ->
		{
		});
	}

}
