package com.shale.ui.controller;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MaterialRequestCardArchitectureTest {
    private static final Path CONTROLLER = Path.of("src/main/java/com/shale/ui/controller/CaseMaterialsTabController.java");
    private static final Path FACTORY = Path.of("src/main/java/com/shale/ui/component/factory/MaterialRequestCardFactory.java");
    private static final Path DAO = Path.of("../shale-data/src/main/java/com/shale/data/dao/MaterialRequestDao.java");

    @Test
    void repositoryHasExactlyOneCanonicalMaterialRequestCardFactoryAndRequestsTabUsesIt() throws Exception {
        assertTrue(Files.exists(FACTORY), "Repository inspection found no pre-existing reusable request card, so one canonical factory is introduced.");
        String controller = Files.readString(CONTROLLER);
        assertTrue(controller.contains("new MaterialRequestCardFactory(this::openDetail)"));
        assertTrue(controller.contains("requestCardFactory.create(r,MaterialRequestCardFactory.Variant.LIST,presentation.name(),presentation.color(),histories.getOrDefault(r.id(),List.of()),statuses)"));
        assertFalse(controller.contains("private Node card(MaterialRequestSummaryDto r)"));
        assertFalse(controller.contains("Requested / Due / Follow-up"));
        assertTrue(controller.contains("show(status,\"Loading material requests…\")"));
        assertTrue(controller.contains("No material requests yet."));
        assertTrue(controller.contains("showInlineError"));
        assertTrue(controller.contains("Button r=new Button(\"Retry\")"));
        assertTrue(controller.contains("svc.createMaterialRequest(cmd)"), "New Request save uses MaterialRequestServicePort create.");
    }

    @Test
    void summaryQueryHydratesMaterialTypeColorWithoutPerCardLookupOrMutations() throws Exception {
        String dao = Files.readString(DAO);
        String factory = Files.readString(FACTORY);
        assertTrue(dao.contains("mt.Color AS MaterialTypeColor"));
        assertTrue(dao.contains("WHERE mr.ShaleClientId=? AND mr.CaseId=? AND (?=1 OR mr.IsDeleted=0)"));
        assertFalse(factory.contains("MaterialRequestDao"));
        assertFalse(factory.contains("MaterialRequestService"));
        assertFalse(factory.contains("createMaterialRequest"));
        assertFalse(factory.contains("updateMaterialRequest"));
        assertFalse(factory.contains("deleteMaterialRequest"));
    }
}
