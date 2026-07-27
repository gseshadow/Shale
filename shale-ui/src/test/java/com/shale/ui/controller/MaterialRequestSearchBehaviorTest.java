package com.shale.ui.controller;

import com.shale.core.dto.MaterialRequestSummaryDto;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class MaterialRequestSearchBehaviorTest {
    @Test void nullableOptionalFieldsNeverThrowAndBlankMatches() {
        var request = summary(null,null,null,null,null,null,null,null,null,null);
        assertDoesNotThrow(() -> CaseMaterialRequestsTabController.matchesSearch(request,"missing",List.of(),List.of()));
        assertFalse(CaseMaterialRequestsTabController.matchesSearch(request,"missing",List.of(),List.of()));
        assertTrue(CaseMaterialRequestsTabController.matchesSearch(request,"",List.of(),List.of()));
    }

    @Test void requestedFromValidityRequiresExactlyOneRealEntity() {
        assertFalse(CaseMaterialRequestsTabController.validRequestedFrom(null));
        var contact = new CaseMaterialRequestsTabController.RequestedFromSelection("contact",1L,"Display",new com.shale.ui.component.factory.ContactCardFactory.ContactCardModel(1,"Display",null,null,null),null);
        var organization = new CaseMaterialRequestsTabController.RequestedFromSelection("organization",2L,"Display",null,new com.shale.ui.component.factory.OrganizationCardFactory.OrganizationCardModel(2,"Display",null,null,null,null,null,null,null,null,null,null,null,null,null));
        var ambiguous = new CaseMaterialRequestsTabController.RequestedFromSelection("bad",3L,"Display",contact.contactModel(),organization.organizationModel());
        assertTrue(CaseMaterialRequestsTabController.validRequestedFrom(contact));
        assertTrue(CaseMaterialRequestsTabController.validRequestedFrom(organization));
        assertFalse(CaseMaterialRequestsTabController.validRequestedFrom(ambiguous));
    }

    @Test void everySupportedSummaryFieldMatchesIndependentlyAndNormalizationIsStable() {
        var request = summary("Unique Title","Contact Person","Organization Name","Legacy Source","Material Type","EMAIL","REQUESTED","Requested User","Assigned User","Distinct description");
        for (String query : List.of("title","contact","organization","legacy","material","email","requested","requested user","assigned","description"))
            assertTrue(CaseMaterialRequestsTabController.matchesSearch(request,CaseMaterialRequestsTabController.normalizeSearch("  "+query.toUpperCase()+"  "),List.of(),List.of()),query);
        assertEquals("pasted query",CaseMaterialRequestsTabController.normalizeSearch("  PASTED   QUERY "));
    }

    private static MaterialRequestSummaryDto summary(String title,String contact,String organization,String text,String type,String method,String status,String requestedBy,String assignedTo,String description) {
        return new MaterialRequestSummaryDto(1L,10,20L,3,type,null,null,title,4,requestedBy,null,5,assignedTo,null,6,contact,7,organization,text,method,LocalDateTime.now(),status,null,null,null,null,LocalDateTime.now(),new byte[]{1},description,false);
    }
}
