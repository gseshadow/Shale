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
}
