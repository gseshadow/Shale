package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

final class AuditLogViewerEntityTypeTest {
    @Test
    void rendersEveryActiveEntityTypeWithoutGenericFallback() {
        Map<String, String> active = Map.ofEntries(
                Map.entry("CASE", "Case"), Map.entry("CASE_STATUS", "Case Status"),
                Map.entry("LINK_TYPE", "Link Type"), Map.entry("CASE_LINK", "Case Link"),
                Map.entry("CASE_LINK_SHARE", "Shared Contact"), Map.entry("CASE_DATE", "Case Date"),
                Map.entry("CASE_DATE_TYPE", "Case Date Type"), Map.entry("CALENDAR_EVENT", "Calendar Event"),
                Map.entry("CASE_DATE_ROLE_MAPPING", "Case Date Role Mapping"),
                Map.entry("FORM_CONFIGURATION", "Form Configuration"), Map.entry("MATERIAL_TYPE", "Material Type"),
                Map.entry("MATERIAL_REQUEST", "Material Request"),
                Map.entry("MATERIAL_REQUEST_FOLLOW_UP", "Material Request Follow-up"),
                Map.entry("MATERIAL_ITEM", "Material Item"), Map.entry("USER", "User"));
        active.forEach((entityType, label) -> assertEquals(label, AuditLogViewerController.friendlyEntity(entityType)));
    }
}
