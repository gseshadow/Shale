package com.shale.desktop.live;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import com.shale.desktop.net.LiveBus;
import com.shale.ui.services.UiRuntimeBridge.CaseUpdatedEvent;
import com.shale.ui.services.UiRuntimeBridge.EntityUpdatedEvent;

public final class LiveEventDispatcher {

	private static final Gson GSON = new Gson();
	private static final java.lang.reflect.Type MAP_TYPE = new TypeToken<Map<String, Object>>() {
	}.getType();

	private final List<Consumer<String>> subscribers = new CopyOnWriteArrayList<>();

	// Case-specific subscribers (back-compat)
	private final List<Consumer<CaseUpdatedEvent>> caseUpdatedSubscribers = new CopyOnWriteArrayList<>();

	// Generic subscribers (recommended)
	private final List<Consumer<EntityUpdatedEvent>> entityUpdatedSubscribers = new CopyOnWriteArrayList<>();

	public void subscribe(Consumer<String> handler) {
		if (handler != null)
			subscribers.add(handler);
	}

	public void unsubscribe(Consumer<String> handler) {
		if (handler != null)
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
		if (handler != null)
			caseUpdatedSubscribers.add(handler);
	}

	public void unsubscribeCaseUpdated(Consumer<CaseUpdatedEvent> handler) {
		if (handler != null)
			caseUpdatedSubscribers.remove(handler);
	}

	public void subscribeEntityUpdated(Consumer<EntityUpdatedEvent> handler) {
		if (handler != null)
			entityUpdatedSubscribers.add(handler);
	}

	public void unsubscribeEntityUpdated(Consumer<EntityUpdatedEvent> handler) {
		if (handler != null)
			entityUpdatedSubscribers.remove(handler);
	}

	public void dispatch(LiveBus.Event event) {
		if (event == null)
			return;
		if (!"EntityUpdated".equals(event.type))
			return;
		if (event.entityType == null || event.entityType.isBlank())
			return;
		if (event.entityId == null)
			return;

		int schemaVersion = event.schemaVersion <= 0 ? 1 : event.schemaVersion;
		String eventId = event.eventId == null ? "" : event.eventId;
		String timestamp = event.timestamp == null ? "" : event.timestamp;

		int tenantId = event.shaleClientId == null ? 0 : event.shaleClientId;
		Map<String, Object> patchMap = parsePatch(event.patchRaw);

		// ---- Generic event (all entity types)
		EntityUpdatedEvent generic = new EntityUpdatedEvent(
				schemaVersion,
				eventId,
				event.entityType,
				event.entityId,
				tenantId,
				event.updatedByUserId,
				timestamp,
				event.patchRaw,
				patchMap
		);

		for (var handler : entityUpdatedSubscribers) {
			try {
				handler.accept(generic);
			} catch (Exception ignored) {
			}
		}

		// ---- Back-compat: CaseUpdatedEvent (kept so existing controllers keep working)
		if ("Case".equals(event.entityType)) {
			String newName = getString(patchMap, "name");
			CaseUpdatedEvent dto = new CaseUpdatedEvent(
					event.entityId.intValue(),
					tenantId,
					event.updatedByUserId,
					newName,
					event.patchRaw,
					event.clientInstanceId // NEW
			);

			for (var handler : caseUpdatedSubscribers) {
				try {
					handler.accept(dto);
				} catch (Exception ignored) {
				}
			}
		}
	}

	private static Map<String, Object> parsePatch(String rawPatch) {
		if (rawPatch == null || rawPatch.isBlank()) {
			return Map.of();
		}
		try {
			JsonElement el = GSON.fromJson(rawPatch, JsonElement.class);
			if (el == null || !el.isJsonObject()) {
				return Map.of();
			}
			Map<String, Object> map = GSON.fromJson(el, MAP_TYPE);
			return map == null ? Map.of() : map;
		} catch (Exception ignored) {
			return Map.of();
		}
	}

	private static String getString(Map<String, Object> patch, String key) {
		Object v = patch.get(key);
		return v == null ? null : String.valueOf(v);
	}
}