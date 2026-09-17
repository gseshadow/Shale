package com.shale.ui.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

final class Phase1BStyleVocabularyContractTest {
    private static final String COMPONENTS = "/css/foundation/content-components.css";
    private static final Pattern TOKEN = Pattern.compile("(?m)^\\s*(-shale-[a-z0-9-]+)\\s*:\\s*([^;]+);");
    private static final List<String> CANONICAL_TOKENS = List.of(
            "application-canvas", "application-chrome", "navigation", "navigation-hover", "navigation-selected",
            "content-plane", "section-surface", "card-surface", "card-hover", "elevated-surface", "input-surface",
            "surface-muted", "overlay-dialog", "text-primary", "text-secondary", "text-muted", "text-disabled",
            "text-on-dark", "text-on-primary", "text-link", "text-link-hover", "text-danger", "text-success",
            "text-warning", "border-subtle", "border-strong", "divider", "input-border", "input-hover-border",
            "border-focus", "danger-border", "border-selected", "action-primary-start", "action-primary-end",
            "action-primary-hover-start", "action-primary-hover-end", "action-primary-pressed",
            "action-secondary-background", "action-secondary-hover", "action-secondary-text", "action-danger-wash",
            "action-danger-hover", "action-danger-text", "icon-button-hover", "selected-control-background",
            "chip-neutral-background", "chip-neutral-border", "chip-neutral-text", "success-wash", "success-border",
            "success-text", "warning-wash", "warning-border", "warning-text", "danger-wash", "danger-text",
            "info-wash", "info-border", "info-text", "avatar-neutral-background", "avatar-neutral-text",
            "stage-inactive", "stage-complete", "stage-current-fallback", "update-card-surface", "update-card-border",
            "focus-ring", "selection", "validation-error", "card-shadow");

    @Test
    void canonicalThemeVocabularyHasExactLightDarkParity() throws Exception {
        Map<String, String> light = tokens(read(Theme.LIGHT.stylesheetResource()));
        Map<String, String> dark = tokens(read(Theme.DARK.stylesheetResource()));
        Set<String> lightCanonical = light.keySet().stream().filter(k -> k.startsWith("-shale-color-")).collect(java.util.stream.Collectors.toSet());
        Set<String> darkCanonical = dark.keySet().stream().filter(k -> k.startsWith("-shale-color-")).collect(java.util.stream.Collectors.toSet());
        assertEquals(lightCanonical, darkCanonical, "theme color roles must remain symmetric");
        for (String role : CANONICAL_TOKENS) {
            String prefix = role.equals("card-shadow") ? "-shale-color-" : "-shale-color-";
            assertTrue(light.containsKey(prefix + role), "light theme is missing canonical role " + role);
            assertTrue(dark.containsKey(prefix + role), "dark theme is missing canonical role " + role);
        }
        assertTrue(light.containsKey("-shale-opacity-disabled"));
        assertTrue(dark.containsKey("-shale-opacity-disabled"));
    }

    @Test
    void componentVocabularyIsOptInThemeDrivenAndRestrictsGradients() throws Exception {
        String css = read(COMPONENTS);
        for (String broad : List.of(".button {", ".label {", ".text-field {", ".combo-box {", ".scroll-pane {")) {
            assertFalse(css.contains(broad), "new vocabulary must not introduce broad selector " + broad);
        }
        assertFalse(Pattern.compile("#[0-9a-fA-F]{3,8}|rgba?\\(").matcher(css).find(),
                "component paint must use looked-up semantic colors");
        assertFalse(css.contains("--"), "JavaFX CSS must not use browser custom properties");
        Matcher gradient = Pattern.compile("linear-gradient\\([^;]+;").matcher(css);
        while (gradient.find()) {
            assertTrue(css.substring(0, gradient.start()).endsWith("-fx-background-color: ")
                    && ruleSelector(css, gradient.start()).contains("shale-stage-current"),
                    "only the current workflow stage may use a gradient in the content vocabulary");
        }
        for (String forbidden : List.of("case-overview", "contact-view", "organization-view", "a2")) {
            assertFalse(css.toLowerCase().contains(forbidden), "shared vocabulary must not contain page/design-version selector " + forbidden);
        }
    }

    @Test
    void requiredComponentsAndStatesHaveOneAuthoritativeOwner() throws Exception {
        String components = read(COMPONENTS);
        String cards = read("/css/foundation/cards.css");
        String buttons = read("/css/foundation/buttons.css");
        String indicators = read("/css/foundation/indicators.css");
        String app = read("/css/app.css");
        for (String selector : List.of(".shale-page-title", ".app-section-tab", ".shale-property-row",
                ".shale-person-row", ".shale-avatar", ".shale-stage-tracker", ".shale-stage-current",
                ".shale-update-card", ".shale-update-search", ".shale-empty-message", ".shale-error-message")) {
            assertTrue(components.contains(selector), "missing shared component selector " + selector);
            assertFalse(app.contains(selector), "app.css must not compete with the component foundation for " + selector);
        }
        for (String selector : List.of(".shale-content-card", ".shale-interactive-card", ".shale-card-selected")) {
            assertTrue(cards.contains(selector), "cards.css owns " + selector);
        }
        for (String selector : List.of(".shale-inline-management-action", ".shale-update-edit-action")) {
            assertTrue(buttons.contains(selector), "buttons.css owns " + selector);
        }
        for (String selector : List.of(".metadata-chip", ".shale-semantic-chip", ".shale-semantic-chip-inactive")) {
            assertTrue(indicators.contains(selector), "indicators.css owns " + selector);
            assertFalse(app.contains(selector), "legacy entry point must not duplicate " + selector);
        }
    }

    @Test
    void coreSolidThemePairsMeetContrastTargets() throws Exception {
        for (Theme theme : Theme.values()) {
            Map<String, String> values = tokens(read(theme.stylesheetResource()));
            assertContrast(values, "-shale-color-text-primary", "-shale-color-card-surface", 4.5, theme);
            assertContrast(values, "-shale-color-text-muted", "-shale-color-card-surface", 4.5, theme);
            assertContrast(values, "-shale-color-text-on-primary", "-shale-color-action-primary-start", 3.0, theme);
            assertContrast(values, "-shale-color-border-focus", "-shale-color-card-surface", 3.0, theme);
        }
    }

    private static void assertContrast(Map<String, String> values, String foreground, String background,
            double target, Theme theme) {
        double ratio = contrast(hex(values.get(foreground)), hex(values.get(background)));
        assertTrue(ratio >= target, () -> theme + " " + foreground + " on " + background + " contrast was " + ratio);
    }

    private static String ruleSelector(String css, int declaration) {
        int close = css.lastIndexOf('}', declaration);
        int open = css.indexOf('{', close + 1);
        return css.substring(close + 1, open).trim();
    }

    private static Map<String, String> tokens(String css) {
        Map<String, String> values = new LinkedHashMap<>();
        Matcher matcher = TOKEN.matcher(css);
        while (matcher.find()) values.put(matcher.group(1), matcher.group(2).trim());
        return values;
    }

    private static int[] hex(String value) {
        assertNotNull(value);
        assertTrue(value.matches("#[0-9a-fA-F]{6}"), "contrast tokens must be solid #RRGGBB colors: " + value);
        return new int[] { Integer.parseInt(value.substring(1, 3), 16), Integer.parseInt(value.substring(3, 5), 16), Integer.parseInt(value.substring(5, 7), 16) };
    }

    private static double contrast(int[] a, int[] b) {
        double lighter = Math.max(luminance(a), luminance(b));
        double darker = Math.min(luminance(a), luminance(b));
        return (lighter + .05) / (darker + .05);
    }

    private static double luminance(int[] rgb) {
        double result = 0;
        double[] weights = { .2126, .7152, .0722 };
        for (int i = 0; i < 3; i++) {
            double channel = rgb[i] / 255d;
            result += weights[i] * (channel <= .04045 ? channel / 12.92 : Math.pow((channel + .055) / 1.055, 2.4));
        }
        return result;
    }

    private String read(String path) throws IOException {
        var resource = getClass().getResource(path);
        assertNotNull(resource, "Expected classpath resource " + path);
        try (var input = resource.openStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
