package com.shale.desktop.live;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import com.shale.desktop.net.LiveBus;
import com.shale.ui.services.UiRuntimeBridge.CaseUpdatedEvent;

public final class LiveEventDispatcher {
	private final List<Consumer<String>> subscribers = new CopyOnWriteArrayList<>();
	private final List<Consumer<CaseUpdatedEvent>> caseUpdatedSubscribers = new CopyOnWriteArrayList<>();

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

	public void subscribeCaseUpdated(Consumer<CaseUpdatedEvent> handler) {
		if (handler != null) {
			caseUpdatedSubscribers.add(handler);
		}
	}

	public void unsubscribeCaseUpdated(Consumer<CaseUpdatedEvent> handler) {
		if (handler != null) {
			caseUpdatedSubscribers.remove(handler);
		}
	}

	public void dispatch(LiveBus.Event event) {
		if (event == null || !"EntityUpdated".equals(event.type) || !"Case".equals(event.entityType) || event.entityId == null) {
			return;
		}
		String newName = extractPatchString(event.patchRaw, "name");
		int tenantId = event.shaleClientId == null ? 0 : event.shaleClientId;
		CaseUpdatedEvent dto = new CaseUpdatedEvent(event.entityId.intValue(), tenantId, event.updatedByUserId, newName, event.patchRaw);
		for (var handler : caseUpdatedSubscribers) {
			try {
				handler.accept(dto);
			} catch (Exception ignored) {
			}
		}
	}

	private static String extractPatchString(String patchRaw, String key) {
		if (patchRaw == null || patchRaw.isBlank()) {
			return null;
		}
		String pat = "\"" + key + "\"";
		int i = patchRaw.indexOf(pat);
		if (i < 0) {
			return null;
		}
		int q1 = patchRaw.indexOf('"', patchRaw.indexOf(':', i) + 1);
		if (q1 < 0) {
			return null;
		}
		int q2 = patchRaw.indexOf('"', q1 + 1);
		if (q2 < 0) {
			return null;
		}
		return patchRaw.substring(q1 + 1, q2);
	}
}
