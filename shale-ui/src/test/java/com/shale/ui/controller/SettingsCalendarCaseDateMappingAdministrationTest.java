package com.shale.ui.controller;

import com.shale.core.dto.EffectiveCaseDateTypeDto;
import com.shale.core.model.CalendarCaseDateTypeMapping;
import com.shale.core.model.CalendarEventType;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class SettingsCalendarCaseDateMappingAdministrationTest {
    private static final String SOURCE = read("src/main/java/com/shale/ui/controller/SettingsController.java");
    private static final String FXML = read("src/main/resources/fxml/settings.fxml");

    @Test void administratorSectionUsesEstablishedSettingsAndControlPatterns() {
        assertTrue(FXML.contains("calendarCaseDateMappingAdministrationSection"));
        assertTrue(SOURCE.contains("requireAdminLookupManagement(\"Calendar / Case Date Mappings\")"));
        assertTrue(SOURCE.contains("ActionButtonFactory.semantic"));
        assertTrue(SOURCE.contains("ColorCodedComboBox<CalendarEventType>"));
        assertTrue(SOURCE.contains("ColorCodedComboBox<EffectiveCaseDateTypeDto>"));
        assertTrue(SOURCE.contains("shale-entity-card-selectable"));
    }

    @Test void delegatesEveryMutationAndPropagatesImmutableIdentityAndRowVersion() {
        assertTrue(SOURCE.contains("calendarCaseDateMappingService.createMapping"));
        assertTrue(SOURCE.contains("calendarCaseDateMappingService.updateMapping"));
        assertTrue(SOURCE.contains("calendarCaseDateMappingService.setMappingActive"));
        assertTrue(SOURCE.contains("calendarCaseDateMappingService.deleteMapping"));
        assertTrue(SOURCE.contains("row.id(), input.eventTypeId()"));
        assertTrue(SOURCE.contains("row.rowVer()"));
        assertTrue(SOURCE.contains("loadCalendarCaseDateMappingsAsync(success"));
    }

    @Test void filtersLookupsToActiveGlobalOrCurrentTenant() {
        var globalEvent = event(1, null, true);
        var ownEvent = event(2, 7, true);
        var foreignEvent = event(3, 8, true);
        var inactiveEvent = event(4, null, false);
        assertEquals(List.of(globalEvent, ownEvent), SettingsController.eligibleCalendarEventTypes(List.of(globalEvent, ownEvent, foreignEvent, inactiveEvent), 7));

        var globalDate = date(1, null, true, false);
        var ownDate = date(2, 7, true, false);
        var foreignDate = date(3, 8, true, false);
        var deletedDate = date(4, 7, true, true);
        assertEquals(List.of(globalDate, ownDate), SettingsController.eligibleCaseDateTypes(List.of(globalDate, ownDate, foreignDate, deletedDate), 7));
    }

    @Test void detectsActiveOneToOneConflictsButAllowsInactiveHistoryAndSelfEdit() {
        var existing = mapping(11, 1, 2, true);
        assertNotNull(SettingsController.activeMappingConflict(List.of(existing), null, 1, 9, true));
        assertNotNull(SettingsController.activeMappingConflict(List.of(existing), null, 9, 2, true));
        assertNull(SettingsController.activeMappingConflict(List.of(existing), 11L, 1, 2, true));
        assertNull(SettingsController.activeMappingConflict(List.of(existing), null, 1, 2, false));
    }

    @Test void guardsDuplicateSubmissionStaleLoadsAndFriendlyErrors() {
        assertTrue(SOURCE.contains("if (calendarCaseDateMappingMutationRunning) return"));
        assertTrue(SOURCE.contains("if (generation != calendarCaseDateMappingLoadGeneration) return"));
        assertTrue(SOURCE.contains("if (calendarCaseDateMappings.isEmpty())"), "populated content remains during refresh");
        assertEquals("This mapping changed elsewhere. Refresh and try again.", SettingsController.mappingErrorMessage(new IllegalStateException("RowVer stale")));
        assertEquals("Only administrators can manage calendar/case-date mappings.", SettingsController.mappingErrorMessage(new SecurityException("not authorized")));
        assertEquals("This mapping is no longer available. Refresh and try again.", SettingsController.mappingErrorMessage(new IllegalArgumentException("not found")));
    }

    @Test void validatesDirectionAndRendersAllRequiredFields() {
        assertTrue(SOURCE.contains("Select at least one synchronization direction."));
        assertTrue(SOURCE.contains("Calendar Event Type: "));
        assertTrue(SOURCE.contains("Case Date Type: "));
        assertTrue(SOURCE.contains("Case Date → Calendar: "));
        assertTrue(SOURCE.contains("Calendar → Case Date: "));
        assertTrue(SOURCE.contains("row.active() ? \"Active\" : \"Inactive\""));
    }

    private static CalendarEventType event(int id, Integer tenant, boolean active) { return new CalendarEventType(id, tenant, "k" + id, "Event " + id, "#112233", id, active, LocalDateTime.MIN, null); }
    private static EffectiveCaseDateTypeDto date(int id, Integer tenant, boolean active, boolean deleted) { return new EffectiveCaseDateTypeDto(id, tenant, "d" + id, "Date " + id, null, "OTHER", "#112233", true, id, active, deleted, tenant == null ? EffectiveCaseDateTypeDto.Origin.GLOBAL : EffectiveCaseDateTypeDto.Origin.TENANT_CREATED, new byte[]{1}); }
    private static CalendarCaseDateTypeMapping mapping(long id, int event, int date, boolean active) { return new CalendarCaseDateTypeMapping(id, event, date, true, false, active, LocalDateTime.MIN, 1, null, null, new byte[]{1}); }
    private static String read(String path) { try { return Files.readString(Path.of(path)); } catch (Exception ex) { throw new AssertionError(ex); } }
}
