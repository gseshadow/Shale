package com.shale.ui.component;

final class CardSurfaceStyles {

    private static final String DEFAULT_CARD_SURFACE = "rgba(248,250,252,0.96)";
    private static final String HOVER_CARD_SURFACE = "rgba(255,255,255,0.985)";
    private static final String CARD_BORDER = "rgba(74, 104, 138, 0.24)";
    private static final String HOVER_CARD_BORDER = "rgba(74, 104, 138, 0.34)";
    private static final String CARD_EFFECT = "dropshadow(gaussian, rgba(7, 23, 44, 0.14), 18, 0.18, 0, 4)";
    private static final String HOVER_CARD_EFFECT = "dropshadow(gaussian, rgba(7, 23, 44, 0.18), 24, 0.2, 0, 8)";

    private CardSurfaceStyles() {
    }

    static String cardContainerStyle(String backgroundCss) {
        return cardContainerStyle(backgroundCss, null, false);
    }

    static String cardContainerStyle(String backgroundCss, boolean hovered) {
        return cardContainerStyle(backgroundCss, null, hovered);
    }

    static String cardContainerStyle(String backgroundCss, String borderCss, boolean hovered) {
        String surface = (backgroundCss == null || backgroundCss.isBlank())
                ? (hovered ? HOVER_CARD_SURFACE : DEFAULT_CARD_SURFACE)
                : backgroundCss;
        String border = (borderCss == null || borderCss.isBlank())
                ? (hovered ? HOVER_CARD_BORDER : CARD_BORDER)
                : borderCss;
        String effect = hovered ? HOVER_CARD_EFFECT : CARD_EFFECT;
        return """
                -fx-background-color: %s;
                -fx-background-radius: 14;
                -fx-border-radius: 14;
                -fx-border-color: %s;
                -fx-border-width: 1;
                -fx-effect: %s;
                """.formatted(surface, border, effect);
    }
}
