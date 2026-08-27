package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ContactCardCredentialProductionPathTest {
    @Test void directoryQueryIsBoundedTenantOwnedOrderedActiveCredentialProjection() throws Exception {
        String dao = Files.readString(Path.of("../shale-data/src/main/java/com/shale/data/dao/ContactDao.java"));
        String method = dao.substring(dao.indexOf("public PagedResult<ContactCardSummaryRow> findDirectoryContactsPage"),
                dao.indexOf("public long countDirectoryContacts", dao.indexOf("public PagedResult<ContactCardSummaryRow> findDirectoryContactsPage")));
        assertTrue(method.contains("credentialAbbreviationsExpression"));
        String projection = dao.substring(dao.indexOf("private static String credentialAbbreviationsExpression"),
                dao.indexOf("private static List<String> splitCredentialAbbreviations"));
        assertTrue(projection.contains("a.ContactId="));
        assertTrue(projection.contains("a.ShaleClientId="));
        assertTrue(projection.contains("a.IsDeleted=0"));
        assertTrue(projection.contains("d.IsActive=1 AND d.IsDeleted=0"));
        assertTrue(projection.contains("ORDER BY x.DisplayOrder"));
        assertFalse(method.contains("findClassificationProfile"));
    }

    @Test void cardRendererComposesEffectiveNameAndCredentialSaveRefreshesDirectory() throws Exception {
        String cards = Files.readString(Path.of("src/main/java/com/shale/ui/controller/ContactsController.java"));
        String renderer = cards.substring(cards.indexOf("private Node buildCard"), cards.indexOf("private Node buildLoadingMoreNode"));
        String adapter = Files.readString(Path.of("../shale-data/src/main/java/com/shale/data/service/adapter/ContactServiceAdapter.java"));
        assertTrue(adapter.contains("ContactNamePresentation.effectiveDisplayNameFromAbbreviations"));
        assertTrue(renderer.contains("row.displayName()"));
        assertFalse(renderer.contains("String displayName = safe(row.displayName())"));
        assertTrue(cards.contains("LiveUpdateEvents.ENTITY_CONTACT"));
        assertTrue(cards.contains("Platform.runLater(this::loadFirstPage)"));
        String editor = Files.readString(Path.of("src/main/java/com/shale/ui/controller/ContactViewController.java"));
        assertTrue(editor.contains("publishEntityUpdated(LiveUpdateEvents.ENTITY_CONTACT"));
    }
}
