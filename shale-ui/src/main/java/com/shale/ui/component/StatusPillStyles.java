package com.shale.ui.component;

import com.shale.ui.util.ColorUtil;

final class StatusPillStyles {

    private static final String SHARED_PILL_CHROME = """
            -fx-text-fill: %s;
            -fx-background-color: %s;
            -fx-background-radius: 999;
            -fx-border-color: rgba(7, 23, 44, 0.12);
            -fx-border-radius: 999;
            -fx-border-width: 1;
            -fx-padding: 3 8 3 8;
            """;

    private StatusPillStyles() {
    }

    static String pillStyle(String baseTextStyle, String colorCss) {
        String safeBase = baseTextStyle == null ? "" : baseTextStyle;
        String safeColor = colorCss == null || colorCss.isBlank() ? "#F1F5F9" : colorCss;
        return safeBase + "\n" + SHARED_PILL_CHROME.formatted(ColorUtil.readableTextColor(safeColor), safeColor);
    }
}
