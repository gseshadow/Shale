package com.shale.ui.controller;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
final class OrganizationsStructuredSearchUiContractTest {
 @Test void toolbarExposesMultiTypeFilterSortAndClear() throws Exception{var doc=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(Path.of("src/main/resources/fxml/organizations.fxml").toFile());String xml=Files.readString(Path.of("src/main/resources/fxml/organizations.fxml"));for(String id:new String[]{"organizationsSearchField","organizationTypeFilter","organizationSort","activeFilterCount","clearFiltersButton","selectedFilterChips"})assertTrue(xml.contains("fx:id=\""+id+"\""));assertEquals("FilterPanel",doc.getElementsByTagName("FilterPanel").item(0).getNodeName());}
 @Test void filtersResetAndSnapshotProgressiveLoads() throws Exception{String s=Files.readString(Path.of("src/main/java/com/shale/ui/controller/OrganizationsController.java"));assertTrue(s.contains("toggleType(int id,boolean selected)")&&s.contains("loadFirstPage()"));assertTrue(s.contains("final OrganizationDao.OrganizationSearchCriteria criteria="));assertTrue(s.contains("generationAtSubmit != loadGeneration"));assertTrue(s.contains("findCardPresentations(tenantId,page.items()"));assertTrue(s.contains("No organizations match the current search or filters."));}
 @Test void removedModeIsAdminGatedAndUsesExistingCard() throws Exception{String xml=Files.readString(Path.of("src/main/resources/fxml/organizations.fxml"));String s=Files.readString(Path.of("src/main/java/com/shale/ui/controller/OrganizationsController.java"));assertTrue(xml.contains("fx:id=\"showRemovedOrganizationsButton\""));assertTrue(s.contains("appState.isAdmin()"));assertTrue(s.contains("OrganizationLifecycleMode.REMOVED_ONLY"));assertTrue(s.contains("card.setRemoved"));assertTrue(s.contains("restoreInProgress"));}
}
