package com.shale.ui.component;

final class CardSurfaceStyles {

    private static final String DEFAULT_CARD_SURFACE = "rgba(248,250,252,0.96)";
    private static final String CARD_BORDER = "rgba(74, 104, 138, 0.24)";
    private static final String CARD_EFFECT = "dropshadow(gaussian, rgba(7, 23, 44, 0.14), 18, 0.18, 0, 4)";

    private CardSurfaceStyles() {
    }

    static String cardContainerStyle(String backgroundCss) {
        String surface = (backgroundCss == null || backgroundCss.isBlank()) ? DEFAULT_CARD_SURFACE : backgroundCss;
        return """
                -fx-background-color: %s;
                -fx-background-radius: 14;
                -fx-border-radius: 14;
                -fx-border-color: %s;
                -fx-border-width: 1;
                -fx-effect: %s;
                """.formatted(surface, CARD_BORDER, CARD_EFFECT);
    }
}
