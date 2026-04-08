package com.shale.ui.navigation;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Optional;

public final class NavigationManager {

    private final Deque<AppRoute> backStack = new ArrayDeque<>();
    private AppRoute currentRoute;

    public Optional<AppRoute> currentRoute() {
        return Optional.ofNullable(currentRoute);
    }

    public boolean canGoBack() {
        return !backStack.isEmpty();
    }

    public boolean recordNavigation(AppRoute destination) {
        Objects.requireNonNull(destination, "destination");
        if (destination.equals(currentRoute)) {
            return false;
        }
        if (currentRoute != null) {
            backStack.push(currentRoute);
        }
        currentRoute = destination;
        return true;
    }

    public Optional<AppRoute> popBackDestination() {
        if (backStack.isEmpty()) {
            return Optional.empty();
        }
        AppRoute destination = backStack.pop();
        currentRoute = destination;
        return Optional.of(destination);
    }

    public void resetTo(AppRoute route) {
        Objects.requireNonNull(route, "route");
        backStack.clear();
        currentRoute = route;
    }
}
