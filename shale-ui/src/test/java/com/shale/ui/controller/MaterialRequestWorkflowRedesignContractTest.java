package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;

final class MaterialRequestWorkflowRedesignContractTest {
  private static String read(String p){ try { return Files.readString(Path.of(p)); } catch(Exception e){ throw new RuntimeException(e);} }
  private static final String MAT = read("src/main/java/com/shale/ui/controller/CaseMaterialsTabController.java");
  private static final String REQ_FORM = MAT.substring(MAT.indexOf("final class MaterialRequestForm"), MAT.indexOf("final class MaterialItemForm"));

  @Test void newRequestNoLongerExposesPersistenceFields() {
    assertFalse(REQ_FORM.contains("Requested-from Contact ID"));
    assertFalse(REQ_FORM.contains("Requested-from Organization ID"));
    assertFalse(REQ_FORM.contains("Controlled free-text source"));
    assertFalse(REQ_FORM.contains("Assigned user selector"));
    assertFalse(REQ_FORM.contains("Relevant start"));
    assertFalse(REQ_FORM.contains("Relevant end"));
    assertTrue(REQ_FORM.contains("Requested From *"));
    assertTrue(REQ_FORM.contains("Generated Title"));
  }

  @Test void requestFormUsesUserFacingGroupsAndDefaults() {
    for (String group : new String[]{"Request", "Responsibility", "Schedule", "Request Details"}) assertTrue(REQ_FORM.contains("group(\"" + group + "\""));
    assertTrue(REQ_FORM.contains("plusMonths(1)"));
    assertTrue(REQ_FORM.contains("plusWeeks(2)"));
    assertTrue(REQ_FORM.contains("REQUESTED"));
    assertTrue(REQ_FORM.contains("colored-lookup-selector"));
    assertTrue(REQ_FORM.contains("mini-user-card"));
  }

  @Test void titleGenerationAndMutualExclusiveSourceAreExplicit() {
    assertTrue(REQ_FORM.contains("generatedTitle"));
    assertTrue(REQ_FORM.contains("replaceAll(\"(?i)"));
    assertTrue(REQ_FORM.contains("source.kind()==SourceKind.CONTACT?source.id():null"));
    assertTrue(REQ_FORM.contains("source.kind()==SourceKind.ORGANIZATION?source.id():null"));
  }
}
