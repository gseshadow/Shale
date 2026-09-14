package com.shale.ui.component;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

final class OrganizationCardMiniStyleTest {
    private static final Path ORGANIZATION_CARD = Path.of("src/main/java/com/shale/ui/component/OrganizationCard.java");
    private static final Path CARDS_CSS = Path.of("src/main/resources/css/foundation/cards.css");
    private static final Path REQUESTED_FROM_DIALOG = Path.of("src/main/java/com/shale/ui/controller/support/RequestedFromWorkflowDialog.java");
    private static final Path APP_CSS = Path.of("src/main/resources/css/app.css");
    private static final Path CASE_MATERIALS = Path.of("src/main/java/com/shale/ui/controller/CaseMaterialsTabController.java");
    private static final Path CASE_CONTROLLER = Path.of("src/main/java/com/shale/ui/controller/CaseController.java");

    @Test
    void miniOrganizationNameUsesSharedPrimaryTextStyleInsteadOfInheritedWhiteText() throws IOException {
        String source = Files.readString(ORGANIZATION_CARD);
        String css = Files.readString(CARDS_CSS);
        String miniBlock = source.substring(source.indexOf("public void applyMini()"), source.indexOf("public void applyCompact()"));

        assertTrue(miniBlock.contains("resetNameLabelVariantStyles()"));
        assertTrue(Pattern.compile("nameLabel\\.getStyleClass\\(\\)\\.addAll\\(\\s*\"organization-card-name\"\\s*,\\s*\"organization-card-name-mini\"\\s*\\)")
                .matcher(miniBlock).find());
        assertTrue(miniBlock.contains("nameLabel.setStyle(null);"));
        assertFalse(miniBlock.contains("-fx-text-fill: white"));
        assertFalse(miniBlock.contains("-fx-text-fill: #fff"));
        assertTrue(cssRuleContains(css, ".organization-card-name", "-fx-text-fill", "-shale-color-text-primary"));
        assertTrue(cssRuleContains(css, ".organization-card-name-mini", "-fx-font-size", "12px"));
        assertTrue(cssRuleContains(css, ".organization-card-name-mini", "-fx-font-weight", "600"));
    }

    @Test
    void requestedFromDoesNotWorkAroundOrganizationMiniTextLocally() throws IOException {
        String source = Files.readString(REQUESTED_FROM_DIALOG);
        String resultCardBlock = source.substring(source.indexOf("OrganizationCardFactory organizationCards"), source.indexOf("TextField first"));

        assertTrue(resultCardBlock.contains("OrganizationCardFactory.Variant.MINI"));
        assertTrue(resultCardBlock.contains("card.getStyleClass().add(\"requested-from-result-card\")"));
        assertFalse(resultCardBlock.contains("organization-card-name"));
        assertFalse(resultCardBlock.contains("-fx-text-fill"));
    }

    @Test
    void requestedFromSelectionHoverAndFocusStylesDoNotOverrideMiniOrganizationNameText() throws IOException {
        String css = Files.readString(APP_CSS);

        assertTrue(cssHasSelector(css, ".requested-from-results .list-cell:filled:hover"));
        assertTrue(cssHasSelector(css, ".requested-from-results .list-cell:filled:selected"));
        assertTrue(cssHasSelector(css, ".requested-from-results .list-cell:filled:focused"));
        assertFalse(requestedFromStateRules(css).stream().anyMatch(rule ->
                        rule.selector().contains(".organization-card-name")
                                || rule.declarations().contains("-fx-text-fill")),
                "requested-from cell state rules may style the cell chrome, not descendant Organization name text");
    }

    @Test
    void fullAndCompactRetainExistingInlineTitleColorsAndClearMiniClasses() throws IOException {
        String source = Files.readString(ORGANIZATION_CARD);
        String compactBlock = source.substring(source.indexOf("public void applyCompact()"), source.indexOf("public void applyFull()"));
        String fullBlock = source.substring(source.indexOf("public void applyFull()"), source.indexOf("public Node asNode()"));

        assertTrue(compactBlock.contains("resetNameLabelVariantStyles()"));
        assertTrue(compactBlock.contains("nameLabel.setStyle(\"-fx-font-size: 14px; -fx-font-weight: 700; -fx-text-fill: #112542;\")"));
        assertTrue(fullBlock.contains("resetNameLabelVariantStyles()"));
        assertTrue(fullBlock.contains("nameLabel.setStyle(\"-fx-font-size: 15px; -fx-font-weight: 700; -fx-text-fill: #112542;\")"));
    }

    @Test
    void knownMiniOrganizationConsumersUseSharedFactoryVariant() throws IOException {
        String requestedFrom = Files.readString(REQUESTED_FROM_DIALOG);
        String caseMaterials = Files.readString(CASE_MATERIALS);
        String caseController = Files.readString(CASE_CONTROLLER);

        assertTrue(requestedFrom.contains("OrganizationCardFactory.Variant.MINI"));
        assertTrue(caseMaterials.contains("OrganizationCardFactory.Variant.MINI"));
        assertFalse(caseController.contains("OrganizationCardFactory.Variant.MINI"));
    }

    private static boolean cssRuleContains(String css, String selector, String property, String value) {
        Pattern rule = Pattern.compile("([^{}]+)\\{([^}]*)}");
        var matcher = rule.matcher(css);
        while (matcher.find()) {
            boolean selectorMatches = Pattern.compile("(?:^|,)\\s*" + Pattern.quote(selector) + "\\s*(?:,|$)")
                    .matcher(matcher.group(1)).find();
            if (selectorMatches && Pattern.compile(Pattern.quote(property) + "\\s*:\\s*" + Pattern.quote(value) + "\\s*;")
                    .matcher(matcher.group(2)).find()) {
                return true;
            }
        }
        return false;
    }

    private static boolean cssHasSelector(String css, String selector) {
        return cssRules(css).stream().anyMatch(rule -> Pattern.compile("(?:^|,)\\s*" + Pattern.quote(selector) + "\\s*(?:,|$)")
                .matcher(rule.selector()).find());
    }

    private static java.util.List<CssRule> requestedFromStateRules(String css) {
        return cssRules(css).stream().filter(rule -> rule.selector().contains("requested-from")
                && (rule.selector().contains(":hover") || rule.selector().contains(":selected")
                        || rule.selector().contains(":focused"))).toList();
    }

    private static java.util.List<CssRule> cssRules(String css) {
        Pattern rulePattern = Pattern.compile("([^{}]+)\\{([^}]*)}");
        var matcher = rulePattern.matcher(css);
        var rules = new java.util.ArrayList<CssRule>();
        while (matcher.find()) rules.add(new CssRule(matcher.group(1), matcher.group(2)));
        return rules;
    }

    private record CssRule(String selector, String declarations) { }
}
