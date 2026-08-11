package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import com.shale.ui.controller.support.CaseListUiSupport.StatusFilterOption;
import com.shale.data.dao.CaseSummaryDao.GridStatusMode;
import org.junit.jupiter.api.Test;

final class CasesMigratedDateCutoverContractTest {
    private static String read(String path) throws Exception { return Files.readString(Path.of(path)); }

    @Test void statusSelectionIsTranslatedToAnExplicitDaoMode() {
        List<StatusFilterOption> options = List.of(new StatusFilterOption(10, "Open", false),
                new StatusFilterOption(20, "Closed", true));
        assertEquals(GridStatusMode.UNRESTRICTED, CasesController.statusMode(Set.of(10, 20), options));
        assertEquals(GridStatusMode.SELECTED, CasesController.statusMode(Set.of(10), options));
        assertEquals(GridStatusMode.NO_STATUS, CasesController.statusMode(Set.of(), options));
    }

    @Test void gridBoardAndCsvMapOnlyTheSharedProjection() throws Exception {
        String source = read("src/main/java/com/shale/ui/controller/CasesController.java");
        assertTrue(source.contains("caseSummaryDao.findActiveGridPage"));
        assertTrue(source.contains("toViewModel(CaseGridRow row)"));
        assertTrue(source.contains("var summary = row.summary()"));
        assertFalse(source.contains("projectMigratedCaseDates"));
        assertFalse(source.contains("MigratedCaseDateKey"));
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
		String dao = read("../shale-data/src/main/java/com/shale/data/dao/CaseSummaryDao.java");
        String exporter = read("src/main/java/com/shale/ui/export/CaseXlsxExporter.java");
		assertTrue(service.contains("caseSummaryDao.listActiveGridForExport"));
		assertFalse(service.contains("projectMigratedCaseDates"));
		assertTrue(dao.contains("EXPORT_BATCH_SIZE = 500"));
		assertTrue(dao.contains("findActiveGridPage(requestedTenantId"));
        assertTrue(exporter.contains("ExportCaseRow"));
        assertTrue(exporter.contains("MAX_CELL_TEXT_LENGTH = 32_767"));
        assertTrue(exporter.contains("yyyy-mm-dd"));
        assertTrue(exporter.contains("Character.isHighSurrogate"));
        assertTrue(exporter.contains("Cases XLSX export failed at stage"));
    }

    @Test void loadTimingWrapsExistingBoundariesWithoutSchedulingWork() throws Exception {
        String source = read("src/main/java/com/shale/ui/controller/CasesController.java");
        assertTrue(source.contains("boundary=defaults-finalized"));
        assertTrue(source.contains("boundary=status-filter-load-pending"));
        assertTrue(source.contains("boundary=background-dao-start"));
        assertTrue(source.contains("boundary=dao-complete"));
        assertTrue(source.contains("phase=projection-hydration"));
        assertTrue(source.contains("phase=projection-merge-dto-map"));
        assertTrue(source.contains("boundary=page-applied-rendered"));
        assertTrue(source.indexOf("generationAtSubmit != loadGeneration")
                < source.indexOf("loaded.addAll(newItems)"), "stale loads must still be rejected before apply");
        assertFalse(source.contains("PerfLog.log(\"CTRL\", \"start\", \"search="));
    }

	@Test void initialLoadWaitsForStatusOptionsAndThenRunsExactlyFromCompletionBoundary() throws Exception {
		String source = read("src/main/java/com/shale/ui/controller/CasesController.java");
		String startup = source.substring(source.indexOf("Platform.runLater(() ->"),
				source.indexOf("if (casesFlow != null)", source.indexOf("Platform.runLater(() ->")));
		assertFalse(startup.contains("loadFirstPage()"), "startup must not query before status modes are known");
		String reload = source.substring(source.indexOf("private void reloadStatusFilterOptionsAndThen"),
				source.indexOf("private boolean matchesSelectedStatus"));
		assertEquals(2, reload.split("onLoaded\\.run\\(\\);", -1).length - 1,
				"both unavailable and successfully loaded status-option paths must release the initial load");
	}
}
