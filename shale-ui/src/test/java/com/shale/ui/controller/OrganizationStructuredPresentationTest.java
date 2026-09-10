package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;

final class OrganizationStructuredPresentationTest {
    private static String fxml() throws Exception{return Files.readString(Path.of("src/main/resources/fxml/organization.fxml"));}
    private static String controller() throws Exception{return Files.readString(Path.of("src/main/java/com/shale/ui/controller/OrganizationController.java"));}
    @Test void profileRemovesLegacyScalarRowsAndHostsStructuredGroups() throws Exception{
        String view=fxml();
        for(String id:List.of("nameValue","typeValue","phoneValue","faxValue","emailValue","websiteValue","address1Value","address2Value","cityValue","stateValue","postalCodeValue","countryValue"))assertFalse(view.contains("fx:id=\""+id+"\""),id);
        for(String id:List.of("organizationTypeChips","phoneCards","emailCards","addressCards","websiteCards"))assertTrue(view.contains("fx:id=\""+id+"\""),id);
        assertTrue(controller().contains("currentContactProfile.activePhones()"));
        assertFalse(controller().contains("phoneValue.setText"));
        assertTrue(controller().contains("new ContactMethodDisplayCard"));
        assertFalse(controller().contains("external-action-link"));
        assertTrue(controller().contains("p.fax()?null:\"Call\""));
    }
}
