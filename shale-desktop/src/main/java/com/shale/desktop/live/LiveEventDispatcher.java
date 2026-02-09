package com.shale.desktop.live;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class LiveEventDispatcher {
	private final List<Consumer<String>> subscribers = new CopyOnWriteArrayList<>();

	public void subscribe(Consumer<String> handler) {
		subscribers.add(handler);
	}

	public void unsubscribe(Consumer<String> handler) {
		subscribers.remove(handler);
	}

	public void onMessageReceived(String message) {
		for (var h : subscribers) {
			try {
				h.accept(message);
			} catch (Exception ignored) {
			}
		}
	}
}
