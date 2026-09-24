package com.shale.ui.theme;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.ObservableList;
import javafx.scene.Parent;
import javafx.scene.Scene;

/** Owns Shale author-stylesheet installation and the session theme state. */
public final class ThemeManager {
    public static final String BASE_STYLESHEET_RESOURCE = "/css/app.css";

    @FunctionalInterface
    interface ResourceResolver {
        URL resolve(String resource);
    }

    private static final ThemeManager APPLICATION = new ThemeManager(ThemeManager.class::getResource);

    private final ReadOnlyObjectWrapper<Theme> activeTheme = new ReadOnlyObjectWrapper<>(Theme.LIGHT);
    private final Map<Object, Boolean> targets = Collections.synchronizedMap(new WeakHashMap<>());
    private final ResourceResolver resourceResolver;
    private final String baseStylesheet;
    private final Map<Theme, String> themeStylesheets;

    public ThemeManager() {
        this(ThemeManager.class::getResource);
    }

    ThemeManager(ResourceResolver resourceResolver) {
        this.resourceResolver = Objects.requireNonNull(resourceResolver, "resourceResolver");
        baseStylesheet = resolveRequired(BASE_STYLESHEET_RESOURCE);
        themeStylesheets = Map.of(
                Theme.LIGHT, resolveRequired(Theme.LIGHT.stylesheetResource()),
                Theme.DARK, resolveRequired(Theme.DARK.stylesheetResource()));
    }

    /** The single manager used by production application composition and shared UI helpers. */
    public static ThemeManager application() {
        return APPLICATION;
    }

    public Theme getActiveTheme() {
        return activeTheme.get();
    }

    public ReadOnlyObjectProperty<Theme> activeThemeProperty() {
        return activeTheme.getReadOnlyProperty();
    }

    public void setActiveTheme(Theme theme) {
        requireFxThread();
        Theme requested = theme == null ? Theme.LIGHT : theme;
        if (requested == activeTheme.get()) return;
        activeTheme.set(requested);
        for (Object target : liveTargets()) install(target);
    }

    public void register(Scene scene) {
        registerTarget(Objects.requireNonNull(scene, "scene"));
    }

    /** Registers a DialogPane, popup content root, or other independently styled parent. */
    public void register(Parent parent) {
        registerTarget(Objects.requireNonNull(parent, "parent"));
    }

    public void unregister(Scene scene) {
        unregisterTarget(scene);
    }

    public void unregister(Parent parent) {
        unregisterTarget(parent);
    }

    private void registerTarget(Object target) {
        requireFxThread();
        targets.put(target, Boolean.TRUE);
        install(target);
    }

    private void unregisterTarget(Object target) {
        requireFxThread();
        if (target != null) targets.remove(target);
    }

    private List<Object> liveTargets() {
        synchronized (targets) {
            return new ArrayList<>(targets.keySet());
        }
    }

    private void install(Object target) {
        ObservableList<String> stylesheets = stylesheets(target);
        List<String> unrelated = stylesheets.stream()
                .filter(url -> !isShaleOwned(url))
                .toList();
        stylesheets.setAll(unrelated);
        stylesheets.add(baseStylesheet);
        stylesheets.add(themeStylesheets.get(activeTheme.get()));
    }

    private ObservableList<String> stylesheets(Object target) {
        if (target instanceof Scene scene) return scene.getStylesheets();
        if (target instanceof Parent parent) return parent.getStylesheets();
        throw new IllegalArgumentException("Unsupported theme target: " + target.getClass().getName());
    }

    private boolean isShaleOwned(String stylesheet) {
        return baseStylesheet.equals(stylesheet) || themeStylesheets.containsValue(stylesheet);
    }

    private String resolveRequired(String resource) {
        URL resolved = resourceResolver.resolve(resource);
        if (resolved == null) {
            throw new IllegalStateException("Required Shale stylesheet is missing from the classpath: " + resource);
        }
        return resolved.toExternalForm();
    }

    private static void requireFxThread() {
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalStateException("ThemeManager operations must run on the JavaFX Application Thread");
        }
    }
}
