package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

final class ContactSharedLinksSectionTest {
    private static final Path FXML = Path.of("src/main/resources/fxml/contact.fxml");
    private static final Path CONTROLLER = Path.of("src/main/java/com/shale/ui/controller/ContactViewController.java");

    @Test
    void relatedCasesAndSharedLinksAreSeparateSiblingSections() throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        Element root = factory.newDocumentBuilder().parse(FXML.toFile()).getDocumentElement();
        Element sidebar = findByFxId(root, "relatedSidebar");
        assertTrue(sidebar != null, "Expected relatedSidebar in contact.fxml");

        List<Element> sections = childElements(sidebar).stream()
                .filter(element -> hasStyleClass(element, "secondary-panel"))
                .toList();
        assertEquals(2, sections.size(), "relatedSidebar must contain exactly two sibling secondary panels");
        assertSection(sections.get(0), "Related Cases", "relatedCasesContainer");
        assertSection(sections.get(1), "Links Shared With This Contact", "sharedLinksContainer");
    }

    private static void assertSection(Element section, String heading, String containerId) {
        assertTrue(hasDescendantAttribute(section, "text", heading), "Missing heading: " + heading);
        assertTrue(findByFxId(section, containerId) != null, "Missing container: " + containerId);
    }

    private static Element findByFxId(Element root, String id) {
        if (id.equals(root.getAttributeNS("http://javafx.com/fxml/1", "id"))) return root;
        for (Element child : childElements(root)) {
            Element match = findByFxId(child, id);
            if (match != null) return match;
        }
        return null;
    }

    private static boolean hasDescendantAttribute(Element root, String attribute, String value) {
        if (value.equals(root.getAttribute(attribute))) return true;
        return childElements(root).stream().anyMatch(child -> hasDescendantAttribute(child, attribute, value));
    }

    private static boolean hasStyleClass(Element element, String styleClass) {
        return List.of(element.getAttribute("styleClass").split("\\s+")).contains(styleClass);
    }

    private static List<Element> childElements(Element parent) {
        List<Element> children = new ArrayList<>();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element) children.add(element);
        }
        return children;
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
