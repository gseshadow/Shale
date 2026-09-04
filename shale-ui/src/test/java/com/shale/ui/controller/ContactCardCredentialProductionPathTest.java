package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import com.shale.core.service.ContactServicePort.ContactCardSummary;
import com.shale.ui.component.factory.ContactCardFactory;
import com.shale.ui.testutil.JavaFxTestSupport;
import javafx.scene.control.Label;
import com.shale.core.dto.CasePartyDto;
import com.shale.data.dao.ContactDao;
import org.junit.jupiter.api.Test;

class ContactCardCredentialProductionPathTest {
    @Test void directoryQueryUsesEffectiveHistoricalCredentialResolution() throws Exception {
        String dao = Files.readString(Path.of("../shale-data/src/main/java/com/shale/data/dao/ContactDao.java"));
        String method = dao.substring(dao.indexOf("public PagedResult<ContactCardSummaryRow> findDirectoryContactsPage"),
                dao.indexOf("public long countDirectoryContacts", dao.indexOf("public PagedResult<ContactCardSummaryRow> findDirectoryContactsPage")));
        assertTrue(method.contains("OFFSET ? ROWS FETCH NEXT ? ROWS ONLY"), "the directory page query must remain bounded");
        assertTrue(containsCode(method, "loadCredentialAbbreviations(con, shaleClientId, selected.stream().map(PageRow::id).toList())"),
                "credential enrichment must be one query bounded to IDs selected by the page query");
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
        var summary = new ContactCardSummary(101, "Dr. Example Doctor M.D.", null, null, List.of("M.D."),List.of());
        assertEquals(1, summary.credentialAbbreviations().size(), "number received by ContactsController");
        var model = ContactsController.cardModel(summary);
        assertEquals("Dr. Example Doctor M.D.", model.displayName());
    }

    @Test void actualJavaFxCardNameLabelRendersEffectiveName() {
        JavaFxTestSupport.runAndWait(() -> {
            var card = new ContactCardFactory(id -> {}).create(
                    new ContactCardFactory.ContactCardModel(101, "Dr. Example Doctor M.D.", null, null, null, java.util.List.of()),
                    ContactCardFactory.Variant.FULL);
            Label name = (Label) card.lookup("#contact-card-name-label");
            assertNotNull(name);
            assertEquals("Dr. Example Doctor M.D.", name.getText());
        });
    }

    @Test void existingProductionCardRendersAllClassificationCategories() {
        JavaFxTestSupport.runAndWait(()->{
            var values=List.of(
                    new com.shale.core.service.ContactServicePort.ClassificationPresentation(com.shale.core.service.ContactServicePort.DefinitionCategory.CONTACT_TYPE,1,"Expert","#112233",0),
                    new com.shale.core.service.ContactServicePort.ClassificationPresentation(com.shale.core.service.ContactServicePort.DefinitionCategory.SPECIALTY,2,"Radiology","#445566",0),
                    new com.shale.core.service.ContactServicePort.ClassificationPresentation(com.shale.core.service.ContactServicePort.DefinitionCategory.CREDENTIAL,3,"M.D.","#778899",0));
            var card=new ContactCardFactory(id->{}).create(new ContactCardFactory.ContactCardModel(1,"Doctor M.D.",null,"doctor@example.test","555",values),ContactCardFactory.Variant.FULL);
            var chips=card.lookupAll(".contact-classification-chip").stream().map(n->((Label)n).getText()).toList();
            assertEquals(List.of("Expert","Radiology","M.D."),chips);
            assertEquals("Doctor M.D.",((Label)card.lookup("#contact-card-name-label")).getText());
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
                    101, "Example Doctor M.D.", null, null, null, java.util.List.of()), ContactCardFactory.Variant.FULL);
            var overviewPartyCard = factory.create(new ContactCardFactory.ContactCardModel(
                    101, "Example Doctor M.D.", null, null, null, java.util.List.of()), ContactCardFactory.Variant.COMPACT);
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

	@Test void searchAndCasePartyLoadingUseOneBoundedClassificationEnrichmentPerResultSet() throws Exception {
		String contactDao = Files.readString(Path.of("../shale-data/src/main/java/com/shale/data/dao/ContactDao.java"));
		String search = contactDao.substring(contactDao.indexOf("public List<DirectoryContactRow> searchContacts"),
				contactDao.indexOf("public DirectoryContactRow findDirectoryContactById"));
		assertTrue(search.contains("withCardClassifications(con, shaleClientId, selected)"));
		assertFalse(search.contains("findClassificationProfile"), "search must not load a full profile per card");
		String caseDao = Files.readString(Path.of("../shale-data/src/main/java/com/shale/data/dao/CaseDao.java"));
		String parties = caseDao.substring(caseDao.indexOf("public List<CasePartyDto> listCaseParties"),
				caseDao.indexOf("public long addCaseParty"));
		assertEquals(1, count(parties, "ContactDao.loadCardClassifications("),
				"the complete Case party set must use one classification query");
		assertTrue(parties.contains("distinct().toList()"));
	}

	@Test void searchAndCaseOverviewProductionMappingsRenderClassificationsAndReadablePhone() {
		var values = List.of(
				new com.shale.core.service.ContactServicePort.ClassificationPresentation(com.shale.core.service.ContactServicePort.DefinitionCategory.CONTACT_TYPE, 1, "Expert", "#112233", 0),
				new com.shale.core.service.ContactServicePort.ClassificationPresentation(com.shale.core.service.ContactServicePort.DefinitionCategory.SPECIALTY, 2, "Life Care Planning", "#445566", 0),
				new com.shale.core.service.ContactServicePort.ClassificationPresentation(com.shale.core.service.ContactServicePort.DefinitionCategory.CREDENTIAL, 3, "BSN", "#778899", 0));
		var searchRow = new ContactDao.DirectoryContactRow(7, "Mary", "Sussex",
				"Mary Sussex BSN, CNLCP, RN-BC, MBA", "mary@example.test", "(206) 730-4489",
				List.of("BSN", "CNLCP", "RN-BC", "MBA"), values.stream().map(value ->
						new ContactDao.ClassificationPresentationRow(value.category(), value.definitionId(), value.label(), value.color(), value.displayOrder())).toList());
		var party = new CasePartyDto(8, 9, 7L, null, 4, "Party", "party", "represented", true, "",
				null, null, "contact", searchRow.displayName(), searchRow.email(), searchRow.phone(),
				searchRow.credentialAbbreviations(), values);

		JavaFxTestSupport.runAndWait(() -> {
			var factory = new ContactCardFactory(id -> {});
			var searchCard = factory.create(SearchController.toContactCardModel(searchRow), ContactCardFactory.Variant.FULL);
			var partyCard = factory.create(CaseController.toContactCardModel(party), ContactCardFactory.Variant.COMPACT);
			for (var card : List.of(searchCard, partyCard)) {
				assertEquals(List.of("Expert", "Life Care Planning", "BSN"), card.lookupAll(".contact-classification-chip")
						.stream().map(node -> ((Label) node).getText()).toList());
				Label phone = (Label) card.lookup("#contact-card-phone-label");
				assertEquals("(206) 730-4489", phone.getText());
				assertFalse(phone.isWrapText(), "phone values must never wrap between arbitrary characters");
				assertEquals(javafx.scene.layout.Region.USE_PREF_SIZE, phone.getMinWidth());
			}
		});
	}
    private static boolean containsCode(String source, String expected) {
        return source.replaceAll("\\s+", "").contains(expected.replaceAll("\\s+", ""));
    }
	private static int count(String source, String value) {
		int count = 0;
		for (int at = 0; (at = source.indexOf(value, at)) >= 0; at += value.length()) count++;
		return count;
	}
}
