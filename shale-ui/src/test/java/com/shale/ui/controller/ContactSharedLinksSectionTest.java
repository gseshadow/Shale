package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

final class ContactSharedLinksSectionTest {
    private static final Path FXML = Path.of("src/main/resources/fxml/contact.fxml");
    private static final Path CONTROLLER = Path.of("src/main/java/com/shale/ui/controller/ContactViewController.java");

    @Test
    void relatedCasesAndSharedLinksAreSeparateSiblingSections() throws Exception {
        String fxml = Files.readString(FXML);
        int column = fxml.indexOf("<VBox minWidth=\"430\" prefWidth=\"460\" maxWidth=\"520\" spacing=\"16\" GridPane.columnIndex=\"1\">");
        String side = fxml.substring(column, fxml.indexOf("</GridPane>", column));

        int relatedSection = side.indexOf("<VBox spacing=\"8\" styleClass=\"secondary-panel\"");
        int relatedHeading = side.indexOf("Related Cases", relatedSection);
        int relatedContainer = side.indexOf("fx:id=\"relatedCasesContainer\"", relatedHeading);
        int closeRelated = side.indexOf("</VBox>", relatedContainer);
        int sharedSection = side.indexOf("<VBox spacing=\"8\" styleClass=\"secondary-panel\"", closeRelated);
        int sharedHeading = side.indexOf("Links Shared With This Contact", sharedSection);
        int sharedContainer = side.indexOf("fx:id=\"sharedLinksContainer\"", sharedHeading);

        assertTrue(relatedSection >= 0 && relatedHeading > relatedSection && relatedContainer > relatedHeading);
        assertTrue(sharedSection > closeRelated && sharedHeading > sharedSection && sharedContainer > sharedHeading);
    }

    @Test
    void controllerDistinguishesSuccessEmptyFailureAndStaleSharedLinkLoads() throws Exception {
        String source = Files.readString(CONTROLLER);

        assertTrue(source.contains("renderSharedLinksEmpty()"));
        assertTrue(source.contains("renderSharedLinksFailure()"));
        assertTrue(source.contains("generation != sharedLinksLoadGeneration || contactId != requestedContactId"));
        assertTrue(source.contains("caseService.listCaseLinksSharedWithContact(requestedContactId, tenantId)"));
        assertTrue(source.contains("operation=contacts.sharedLinks.success"));
        assertTrue(source.contains("operation=contacts.sharedLinks.failure"));
    }

    @Test
    void initReloadsAfterControllerDependenciesAreInjected() throws Exception {
        String source = Files.readString(CONTROLLER);

        assertTrue(source.contains("private boolean initialized;"));
        assertInitReloadsAfterDependenciesAreInjected(source);
    }

    @Test
    void initLifecycleInspectionIsStableAcrossLineEndings() throws Exception {
        String source = Files.readString(CONTROLLER);
        String lf = normalizeLineEndings(source);
        String crlf = lf.replace("\n", "\r\n");

        String originalBody = extractMethodBody(source, "init");
        assertEquals(originalBody, extractMethodBody(lf, "init"));
        assertEquals(originalBody, extractMethodBody(crlf, "init"));

        assertInitReloadsAfterDependenciesAreInjected(source);
        assertInitReloadsAfterDependenciesAreInjected(lf);
        assertInitReloadsAfterDependenciesAreInjected(crlf);
    }

    private static void assertInitReloadsAfterDependenciesAreInjected(String source) {
        String initBody = compactWhitespace(extractMethodBody(source, "init"));

        assertTrue(initBody.contains("this.contactId = contactId;"));
        assertTrue(initBody.contains("this.contactDetailService = contactDetailService;"));
        assertTrue(initBody.contains("this.appState = appState;"));
        assertTrue(initBody.contains("this.onOpenCase = onOpenCase;"));
        assertTrue(initBody.contains("this.onContactDeleted = onContactDeleted;"));
        assertTrue(initBody.contains("this.caseService = caseService;"));
        assertTrue(initBody.contains("this.onOpenContact = onOpenContact;"));
        assertTrue(initBody.contains("this.phiReadAuditService = phiReadAuditService;"));
        assertTrue(initBody.contains("this.caseCardFactory = new CaseCardFactory(onOpenCase);"));
        assertTrue(initBody.contains("auditContactRead();"));
        assertTrue(initBody.contains("if (initialized) { resetSharedLinksState(); loadContact(); loadSharedLinks(); }"));

        assertTrue(initBody.indexOf("this.contactId = contactId;") < initBody.indexOf("if (initialized)"));
        assertTrue(initBody.indexOf("this.appState = appState;") < initBody.indexOf("if (initialized)"));
        assertTrue(initBody.indexOf("this.caseService = caseService;") < initBody.indexOf("if (initialized)"));
        assertTrue(initBody.indexOf("resetSharedLinksState();") < initBody.indexOf("loadSharedLinks();"));
        assertTrue(initBody.indexOf("loadContact();") < initBody.indexOf("loadSharedLinks();"));
    }

    private static String extractMethodBody(String source, String methodName) {
        String normalized = normalizeLineEndings(source);
        Pattern signature = Pattern.compile("public\\s+void\\s+" + Pattern.quote(methodName) + "\\s*\\(");
        Matcher matcher = signature.matcher(normalized);
        int bodyStart = -1;
        while (matcher.find()) {
            int openParen = matcher.end() - 1;
            int closeParen = findMatching(normalized, openParen, '(', ')');
            if (closeParen < 0) {
                continue;
            }
            int openBrace = nextNonWhitespace(normalized, closeParen + 1);
            if (openBrace >= 0 && normalized.charAt(openBrace) == '{') {
                bodyStart = openBrace;
            }
        }
        assertTrue(bodyStart >= 0, "Expected to find public void " + methodName + "(...) method body");

        int bodyEnd = findMatching(normalized, bodyStart, '{', '}');
        assertTrue(bodyEnd > bodyStart, "Expected balanced braces for public void " + methodName + "(...)");
        return normalized.substring(bodyStart + 1, bodyEnd);
    }

    private static int findMatching(String source, int openIndex, char open, char close) {
        int depth = 0;
        for (int i = openIndex; i < source.length(); i++) {
            char current = source.charAt(i);
            if (current == open) {
                depth++;
            } else if (current == close) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static int nextNonWhitespace(String source, int start) {
        for (int i = start; i < source.length(); i++) {
            if (!Character.isWhitespace(source.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private static String normalizeLineEndings(String source) {
        return source.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static String compactWhitespace(String source) {
        return normalizeLineEndings(source).replace('\t', ' ').replaceAll("\\s+", " ").trim();
    }
}
