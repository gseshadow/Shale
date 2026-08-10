package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class CasesMigratedDateCutoverContractTest {
    private static String read(String path) throws Exception { return Files.readString(Path.of(path)); }

    @Test void gridBoardAndCsvMapOnlyTheSharedProjection() throws Exception {
        String source = read("src/main/java/com/shale/ui/controller/CasesController.java");
        assertTrue(source.contains("caseService.projectMigratedCaseDates"));
        assertTrue(source.contains("projectDates(page.items())"));
        assertTrue(source.contains("MigratedCaseDateProjectionDto.empty(row.id())"));
        assertTrue(source.contains("MigratedCaseDateKey.CALLER_DATE"));
        assertTrue(source.contains("MigratedCaseDateKey.DATE_OF_INJURY"));
        assertTrue(source.contains("MigratedCaseDateKey.STATUTE_OF_LIMITATIONS"));
        assertTrue(source.contains("MigratedCaseDateKey.TORT_NOTICE_DEADLINE"));
        assertTrue(source.contains("toViewModel(CaseDao.CaseRow row, MigratedCaseDateProjectionDto projection)"));
        assertTrue(source.contains("slot.present() ? slot.startsAt().toLocalDate() : null"));
        assertTrue(source.contains("caseCardFactory.create(new CaseCardModel"));
    }

    @Test void caseDatesInvalidationIsTenantSafeDeduplicatedAndCoalesced() throws Exception {
        String source = read("src/main/java/com/shale/ui/controller/CasesController.java");
        assertTrue(source.contains("LiveUpdateEvents.ENTITY_CASE_DATES.equals(event.entityType())"));
        assertTrue(source.contains("event.shaleClientId() != tenant"));
        assertTrue(source.contains("mine.equals(event.clientInstanceId())"));
        assertTrue(source.contains("rememberCaseDatesEvent(event.eventId())"));
        assertTrue(source.contains("caseDatesRefreshQueued.compareAndSet(false, true)"));
        assertTrue(source.contains("loadFirstPage()"));
    }

    @Test void exportUsesOneBatchBoundaryAndRetainsFormattingGuards() throws Exception {
        String service = read("src/main/java/com/shale/ui/services/CaseExportService.java");
        String exporter = read("src/main/java/com/shale/ui/export/CaseXlsxExporter.java");
        assertTrue(service.contains("caseService.projectMigratedCaseDates"));
        assertTrue(service.contains("rows.stream().map(CaseRow::id).toList()"));
        assertFalse(service.contains("for (CaseRow row"));
        assertTrue(exporter.contains("ExportCaseRow"));
        assertTrue(exporter.contains("MAX_CELL_TEXT_LENGTH = 32_767"));
        assertTrue(exporter.contains("yyyy-mm-dd"));
        assertTrue(exporter.contains("Character.isHighSurrogate"));
        assertTrue(exporter.contains("Cases XLSX export failed at stage"));
    }
}
