package com.shale.core.model;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class CalendarOverlaySelection {
    private final boolean sharedEnabled;
    private final Set<Integer> enabledUserIds;

    public CalendarOverlaySelection(boolean sharedEnabled, Set<Integer> enabledUserIds) {
        this.sharedEnabled = sharedEnabled;
        this.enabledUserIds = enabledUserIds == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(enabledUserIds));
    }

    public static CalendarOverlaySelection defaults(Integer currentUserId) {
        LinkedHashSet<Integer> users = new LinkedHashSet<>();
        if (currentUserId != null && currentUserId > 0) users.add(currentUserId);
        return new CalendarOverlaySelection(true, users);
    }

    public boolean sharedEnabled() { return sharedEnabled; }
    public Set<Integer> enabledUserIds() { return enabledUserIds; }
    public boolean hasAnyEnabled() { return sharedEnabled || !enabledUserIds.isEmpty(); }

    public boolean matches(CalendarFeedItem item) {
        if (!hasAnyEnabled() || item == null) return false;
        CalendarFeedCalendarOwner owner = CalendarOverlayClassifier.classify(item);
        return owner.sharedCalendar() ? sharedEnabled : enabledUserIds.contains(owner.userId());
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CalendarOverlaySelection that)) return false;
        return sharedEnabled == that.sharedEnabled && Objects.equals(enabledUserIds, that.enabledUserIds);
    }
    @Override public int hashCode() { return Objects.hash(sharedEnabled, enabledUserIds); }
}
