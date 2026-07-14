package com.shale.ui.component;

final class CardSurfaceStyles {

    /*
     * Foundation card/entity-card values mirrored by CSS in
     * css/foundation/colors.css, css/foundation/surfaces.css, and
     * css/foundation/cards.css. Keep exact values here so existing Java-built
     * cards preserve their current inline-style appearance.
     */
    private static final String DEFAULT_CARD_SURFACE = "rgba(248,250,252,0.96)";
    private static final String HOVER_CARD_SURFACE = "rgba(255,255,255,0.985)";
    private static final String CARD_BORDER = "rgba(74, 104, 138, 0.24)";
    private static final String HOVER_CARD_BORDER = "rgba(74, 104, 138, 0.34)";
    private static final String CARD_RADIUS = "14";
    private static final String CARD_BORDER_WIDTH = "1";
    private static final String CARD_EFFECT = "dropshadow(gaussian, rgba(7, 23, 44, 0.14), 18, 0.18, 0, 4)";
    private static final String HOVER_CARD_EFFECT = "dropshadow(gaussian, rgba(7, 23, 44, 0.18), 24, 0.2, 0, 8)";
    private static final String EMBEDDED_CARD_EFFECT = "dropshadow(gaussian, rgba(7, 23, 44, 0.08), 10, 0.12, 0, 2)";
    private static final String EMBEDDED_HOVER_CARD_EFFECT = "dropshadow(gaussian, rgba(7, 23, 44, 0.12), 14, 0.14, 0, 4)";

    private CardSurfaceStyles() {
    }

    static String cardContainerStyle(String backgroundCss) {
        return cardContainerStyle(backgroundCss, null, false);
    }

    static String cardContainerStyle(String backgroundCss, boolean hovered) {
        return cardContainerStyle(backgroundCss, null, hovered);
    }

    static String cardContainerStyle(String backgroundCss, String borderCss, boolean hovered) {
        return cardContainerStyle(backgroundCss, borderCss, hovered, false);
    }

    static String embeddedCardContainerStyle(String backgroundCss, boolean hovered) {
        return cardContainerStyle(backgroundCss, null, hovered, true);
    }

    private static String cardContainerStyle(String backgroundCss, String borderCss, boolean hovered, boolean embedded) {
        String surface = (backgroundCss == null || backgroundCss.isBlank())
                ? (hovered ? HOVER_CARD_SURFACE : DEFAULT_CARD_SURFACE)
                : backgroundCss;
        String border = (borderCss == null || borderCss.isBlank())
                ? (hovered ? HOVER_CARD_BORDER : CARD_BORDER)
                : borderCss;
        String effect = embedded
                ? (hovered ? EMBEDDED_HOVER_CARD_EFFECT : EMBEDDED_CARD_EFFECT)
                : (hovered ? HOVER_CARD_EFFECT : CARD_EFFECT);
        return """
                -fx-background-color: %s;
                -fx-background-radius: %s;
                -fx-border-radius: %s;
                -fx-border-color: %s;
                -fx-border-width: %s;
                -fx-effect: %s;
                """.formatted(surface, CARD_RADIUS, CARD_RADIUS, border, CARD_BORDER_WIDTH, effect);
    }
}
