package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CaseMaterialsEditLookupMatchingTest {

    @Test
    void savedRequestMethodAndStatusKeysMatchEffectiveLookupsIgnoringCase() {
        assertTrue(CaseMaterialRequestsTabController.lookupValueMatches("email", "Email", "EMAIL"));
        assertTrue(CaseMaterialRequestsTabController.lookupValueMatches("partially_received", "Partially Received", "PARTIALLY_RECEIVED"));
    }

    @Test
    void legacyDisplayNamesStillMatchEffectiveLookups() {
        assertTrue(CaseMaterialRequestsTabController.lookupValueMatches("follow_up_due", "Follow Up Due", "Follow Up Due"));
        assertTrue(CaseMaterialRequestsTabController.lookupValueMatches("phone", "Phone", " phone "));
        assertFalse(CaseMaterialRequestsTabController.lookupValueMatches("email", "Email", "Fax"));
    }
}
