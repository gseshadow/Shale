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
// If you add EntityUpdatedEvent to UiRuntimeBridge, import it too:
// import com.shale.ui.services.UiRuntimeBridge.EntityUpdatedEvent;

public final class LiveEventDispatcher {
    private static final Gson GSON = new Gson();
    private static final java.lang.reflect.Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();

    private final List<Consumer<String>> subscribers = new CopyOnWriteArrayList<>();

    // Current (case-specific) subscribers
    private final List<Consumer<CaseUpdatedEvent>> caseUpdatedSubscribers = new CopyOnWriteArrayList<>();

    // Recommended (generic) subscribers — add once you add EntityUpdatedEvent to UiRuntimeBridge
    // private final List<Consumer<EntityUpdatedEvent>> entityUpdatedSubscribers = new CopyOnWriteArrayList<>();

    public void subscribe(Consumer<String> handler) { subscribers.add(handler); }
    public void unsubscribe(Consumer<String> handler) { subscribers.remove(handler); }

    public void onMessageReceived(String message) {
        for (var h : subscribers) {
            try { h.accept(message); } catch (Exception ignored) {}
        }
    }

    public void subscribeCaseUpdated(Consumer<CaseUpdatedEvent> handler) {
        if (handler != null) caseUpdatedSubscribers.add(handler);
    }

    public void unsubscribeCaseUpdated(Consumer<CaseUpdatedEvent> handler) {
        if (handler != null) caseUpdatedSubscribers.remove(handler);
    }

    // If/when you add generic subscription
    // public void subscribeEntityUpdated(Consumer<EntityUpdatedEvent> handler) {
    //     if (handler != null) entityUpdatedSubscribers.add(handler);
    // }
    //
    // public void unsubscribeEntityUpdated(Consumer<EntityUpdatedEvent> handler) {
    //     if (handler != null) entityUpdatedSubscribers.remove(handler);
    // }

    public void dispatch(LiveBus.Event event) {
        if (event == null || !"EntityUpdated".equals(event.type) || event.entityType == null || event.entityId == null) {
            return;
        }

        int tenantId = event.shaleClientId == null ? 0 : event.shaleClientId;
        Map<String, Object> patchMap = parsePatch(event.patchRaw);

        // --- Recommended: publish generic event (once you add EntityUpdatedEvent)
        // EntityUpdatedEvent generic = new EntityUpdatedEvent(
        //     event.entityType,
        //     event.entityId,
        //     tenantId,
        //     event.updatedByUserId,
        //     event.patchRaw,
        //     patchMap
        // );
        // for (var handler : entityUpdatedSubscribers) {
        //     try { handler.accept(generic); } catch (Exception ignored) {}
        // }

        // --- Back-compat: case-specific event (kept for now)
        if ("Case".equals(event.entityType)) {
            String newName = getString(patchMap, "name"); // safe, no manual parsing
            CaseUpdatedEvent dto = new CaseUpdatedEvent(
                    event.entityId.intValue(),
                    tenantId,
                    event.updatedByUserId,
                    newName,
                    event.patchRaw
            );
            for (var handler : caseUpdatedSubscribers) {
                try { handler.accept(dto); } catch (Exception ignored) {}
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
            return GSON.fromJson(el, MAP_TYPE);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private static String getString(Map<String, Object> patch, String key) {
        Object v = patch.get(key);
        return v == null ? null : String.valueOf(v);
    }
}