package com.shale.ui.component.dialog;

import com.shale.ui.util.ColorUtil;

/** Centralized, sanitized dynamic color treatment for Case Team people. */
final class CaseTeamCardStyles {
    static final String NEUTRAL_USER_COLOR = "64748B";

    private CaseTeamCardStyles() {}

    static String resolvedUserColor(String configuredColor) {
        String normalized = ColorUtil.normalizeStoredColor(configuredColor);
        return normalized == null ? NEUTRAL_USER_COLOR : normalized;
    }

    static String memberCardStyle(String configuredColor) {
        String color = resolvedUserColor(configuredColor);
        return "-fx-background-color: linear-gradient(to right, "
                + ColorUtil.toCssRgba(color, 0.24) + " 0%, "
                + ColorUtil.toCssRgba(color, 0.08) + " 24%, "
                + "rgba(255,255,255,0.94) 62%, rgba(255,255,255,0.98) 100%);";
    }

    static String accentStyle(String configuredColor) {
        return "-fx-background-color: " + ColorUtil.toCssBackgroundColor(resolvedUserColor(configuredColor)) + ";";
    }
}
