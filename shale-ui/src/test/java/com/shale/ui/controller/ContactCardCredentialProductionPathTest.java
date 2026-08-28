package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import com.shale.core.service.ContactServicePort.ContactCardSummary;
import com.shale.ui.component.factory.ContactCardFactory;
import com.shale.ui.testutil.JavaFxTestSupport;
import javafx.scene.control.Label;
import org.junit.jupiter.api.Test;

class ContactCardCredentialProductionPathTest {
    @Test void directoryQueryUsesEffectiveHistoricalCredentialResolution() throws Exception {
        String dao = Files.readString(Path.of("../shale-data/src/main/java/com/shale/data/dao/ContactDao.java"));
        String method = dao.substring(dao.indexOf("public PagedResult<ContactCardSummaryRow> findDirectoryContactsPage"),
                dao.indexOf("public long countDirectoryContacts", dao.indexOf("public PagedResult<ContactCardSummaryRow> findDirectoryContactsPage")));
        assertTrue(method.contains("loadCredentialAbbreviations(con, shaleClientId"));
        assertTrue(method.indexOf("selected.stream().map(PageRow::id)") < method.indexOf("loadCredentialAbbreviations(con"));
        String projection = dao.substring(dao.indexOf("private static Map<Integer, List<String>> loadCredentialAbbreviations"),
                dao.indexOf("public long countDirectoryContacts", dao.indexOf("private static Map<Integer, List<String>> loadCredentialAbbreviations")));
        assertTrue(projection.contains("a.ContactId IN (%s)"));
        assertTrue(projection.contains("a.ShaleClientId=?"));
        assertTrue(projection.contains("a.IsDeleted=0"));
        assertTrue(projection.contains("d.ShaleClientId=a.ShaleClientId OR d.ShaleClientId IS NULL"));
        assertFalse(projection.contains("d.IsActive=1 AND d.IsDeleted=0"));
        assertTrue(projection.contains("ORDER BY a.ContactId,a.DisplayOrder,d.SortOrder,d.Name,d.Id,a.Id"));
        assertFalse(method.contains("findClassificationProfile"));
    }

    @Test void controllerPassesAdapterEffectiveNameToCardModel() {
        var summary = new ContactCardSummary(101, "Dr. Example Doctor M.D.", null, null, List.of("M.D."));
        assertEquals(1, summary.credentialAbbreviations().size(), "number received by ContactsController");
        var model = ContactsController.cardModel(summary);
        assertEquals("Dr. Example Doctor M.D.", model.displayName());
    }

    @Test void actualJavaFxCardNameLabelRendersEffectiveName() {
        JavaFxTestSupport.runAndWait(() -> {
            var card = new ContactCardFactory(id -> {}).create(
                    new ContactCardFactory.ContactCardModel(101, "Dr. Example Doctor M.D.", null, null, null),
                    ContactCardFactory.Variant.FULL);
            Label name = (Label) card.lookup("#contact-card-name-label");
            assertNotNull(name);
            assertEquals("Dr. Example Doctor M.D.", name.getText());
        });
    }

    @Test void directoryAndCaseOverviewProductionCardsRenderTheSameEffectiveName() throws Exception {
        String caseDao = Files.readString(Path.of("../shale-data/src/main/java/com/shale/data/dao/CaseDao.java"));
        String parties = caseDao.substring(caseDao.indexOf("public List<CasePartyDto> listCaseParties"),
                caseDao.indexOf("public long addCaseParty"));
        assertTrue(parties.contains("ContactCredentials"));
        assertTrue(parties.contains("CredentialDefinitions"));
        assertTrue(parties.contains("ContactNamePresentation.effectiveDisplayNameFromAbbreviations"));
        assertTrue(parties.contains("cd.ShaleClientId=cc.ShaleClientId OR cd.ShaleClientId IS NULL"));
        assertTrue(parties.contains("credentials.DisplayOrder, credentials.SortOrder"));

        JavaFxTestSupport.runAndWait(() -> {
            ContactCardFactory factory = new ContactCardFactory(id -> {});
            var directoryCard = factory.create(new ContactCardFactory.ContactCardModel(
                    101, "Example Doctor M.D.", null, null, null), ContactCardFactory.Variant.FULL);
            var overviewPartyCard = factory.create(new ContactCardFactory.ContactCardModel(
                    101, "Example Doctor M.D.", null, null, null), ContactCardFactory.Variant.COMPACT);
            assertEquals("Example Doctor M.D.", ((Label) directoryCard.lookup("#contact-card-name-label")).getText());
            assertEquals("Example Doctor M.D.", ((Label) overviewPartyCard.lookup("#contact-card-name-label")).getText());
        });
    }

    @Test void credentialSaveRefreshesDirectory() throws Exception {
        String cards = Files.readString(Path.of("src/main/java/com/shale/ui/controller/ContactsController.java"));
        String adapter = Files.readString(Path.of("../shale-data/src/main/java/com/shale/data/service/adapter/ContactServiceAdapter.java"));
        assertTrue(adapter.contains("ContactNamePresentation.effectiveDisplayNameFromAbbreviations"));
        assertTrue(cards.contains("LiveUpdateEvents.ENTITY_CONTACT"));
        assertTrue(cards.contains("Platform.runLater(this::loadFirstPage)"));
        String editor = Files.readString(Path.of("src/main/java/com/shale/ui/controller/ContactViewController.java"));
        assertTrue(editor.contains("publishEntityUpdated(LiveUpdateEvents.ENTITY_CONTACT"));
    }
}
