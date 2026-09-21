package com.shale.ui.theme;

/** The session-scoped application themes supported by the desktop UI. */
public enum Theme {
    LIGHT("/css/theme/light.css"),
    DARK("/css/theme/dark.css");

    private final String stylesheetResource;

    Theme(String stylesheetResource) {
        this.stylesheetResource = stylesheetResource;
    }

    public String stylesheetResource() {
        return stylesheetResource;
    }

    /** Resolves the stable persisted value without allowing malformed data to escape login. */
    public static Theme fromStoredValue(String value) {
        if (value == null) return LIGHT;
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return LIGHT;
        }
    }

    public String storedValue() {
        return name();
    }
}
