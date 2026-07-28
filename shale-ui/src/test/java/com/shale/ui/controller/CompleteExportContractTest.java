package com.shale.ui.controller;

import com.shale.core.dto.ReportCaseDetailRowDto;
import com.shale.data.dao.CaseDao.CaseRow;
import com.shale.ui.export.CaseXlsxExporter;
import com.shale.ui.services.CaseExportService.ReportExportRow;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

final class CompleteExportContractTest {
    private static String read(String path) {
        try { return Files.readString(Path.of(path)); } catch (Exception ex) { throw new RuntimeException(ex); }
    }

    @Test void casesExportUsesSnapshotServiceAndNotRenderedOrLoadedRows() {
        String controller = read("src/main/java/com/shale/ui/controller/CasesController.java");
        assertTrue(controller.contains("new CaseExportService.CasesCriteria(tenantId, selectedSort()"));
        assertTrue(controller.contains("includeClosedDeniedInQuery(), normalizedSearchQuery(), new LinkedHashSet<>(selectedStatusIds)"));
        assertTrue(controller.contains("caseExportService.exportCases(criteria)"));
        assertFalse(controller.contains("currentExportRows()"));
        assertTrue(controller.contains("dbExec.submit"));
        assertTrue(controller.contains("LOG.error(\"Cases export failed"));
        assertTrue(controller.contains("finishExport(() -> showExportSuccess(file))"));
        assertTrue(controller.contains("finishExport(() -> showExportError(file))"));
    }

    @Test void daoExportWalksPastFirstHundredWithoutChangingUiPage() {
        String dao = read("../shale-data/src/main/java/com/shale/data/dao/CaseDao.java");
        String method = dao.substring(dao.indexOf("listCasesViewForExport"), dao.indexOf("findMyCasesPage", dao.indexOf("listCasesViewForExport")));
        assertTrue(method.contains("exportBatchSize = 500"));
        assertTrue(method.contains("rows.size() < total"));
        assertTrue(method.contains("findPageInternal(page, exportBatchSize"));
        assertFalse(method.contains("TOP (100)"));
        assertFalse(method.contains("pageSize = 100"));
    }

    @Test void reportsButtonsCriteriaAndStableDrillDownAreWired() {
        String fxml = read("src/main/resources/fxml/reports.fxml");
        String controller = read("src/main/java/com/shale/ui/controller/ReportsController.java");
        assertTrue(fxml.contains("fx:id=\"exportButton\""));
        assertTrue(fxml.contains("onAction=\"#onExport\""));
        assertTrue(controller.contains("new CaseExportService.ReportCriteria(shaleClientId, startDate, endDate, List.of(row.statusId()))"));
        assertTrue(controller.contains("ButtonBar.ButtonData.LEFT"));
        assertTrue(controller.contains("caseExportService.exportReport(criteria, namesSnapshot)"));
        assertTrue(controller.contains("button.setDisable(true)"));
        assertTrue(controller.contains("button.setDisable(false)"));
    }

    @Test void exportServiceEnforcesTenantUserAndBatchedPhiReadAudit() {
        String service = read("src/main/java/com/shale/ui/services/CaseExportService.java");
        assertTrue(service.contains("Objects.equals(currentTenant, tenantId)"));
        assertTrue(service.contains("userId == null || userId <= 0"));
        assertTrue(service.contains("throw new SecurityException"));
        assertTrue(service.contains("auditRead(\"Case.Export\""));
        assertTrue(service.contains("auditRead(\"Case.Report.Export\""));
        assertFalse(service.contains("for (CaseRow row"));
    }

    @Test void sharedXlsxExporterWritesHumanReadableAndTypedValues() throws Exception {
        Path file = Files.createTempFile("shale-report-export", ".xlsx");
        try {
            var detail = new ReportCaseDetailRowDto(1, "Readable Case", LocalDateTime.of(2026, 1, 2, 10, 30),
                    LocalDate.of(2026, 1, 1), null, null, LocalDate.of(2025, 12, 1), "Description",
                    LocalDate.of(2027, 1, 1), null, LocalDateTime.of(2026, 1, 3, 11, 45), "Alex Attorney");
            new CaseXlsxExporter().writeReport(file, "Case Status Report", List.of(new ReportExportRow("Open", detail)));
            try (var workbook = new XSSFWorkbook(Files.newInputStream(file))) {
                var sheet = workbook.getSheet("Case Status Report");
                assertNotNull(sheet);
                assertEquals("Case Name", sheet.getRow(0).getCell(0).getStringCellValue());
                assertEquals("Case Status", sheet.getRow(0).getCell(1).getStringCellValue());
                assertEquals("Open", sheet.getRow(1).getCell(1).getStringCellValue());
                assertEquals(CellType.NUMERIC, sheet.getRow(1).getCell(2).getCellType());
                assertEquals(CellType.NUMERIC, sheet.getRow(1).getCell(3).getCellType());
                assertEquals(1, sheet.getPaneInformation().getHorizontalSplitPosition());
                assertTrue(sheet.getCTWorksheet().isSetAutoFilter());
            }
        } finally { Files.deleteIfExists(file); }
    }

    @Test void casesXlsxHandlesMoreThanOneThousandRowsNullsLongTextAndDates() throws Exception {
        Path file = Files.createTempFile("shale-cases-export", ".xlsx");
        try {
            List<CaseRow> rows = new ArrayList<>();
            for (int i = 0; i < 1_127; i++) {
                String description = i == 417 ? "x".repeat(40_000) : (i % 2 == 0 ? null : "Description");
                rows.add(new CaseRow(i + 1L, "Case " + i,
                        i % 3 == 0 ? null : LocalDate.of(2026, 1, 1).plusDays(i),
                        LocalDate.of(2027, 12, 31), 1, null, null, null, null,
                        "Open", null, null, null, null, null, description,
                        i % 5 == 0 ? null : LocalDate.of(2025, 6, 15), null, null));
            }

            new CaseXlsxExporter().writeCases(file, rows);

            try (var workbook = new XSSFWorkbook(Files.newInputStream(file))) {
                var sheet = workbook.getSheet("Cases");
                assertEquals(1_127, sheet.getLastRowNum());
                assertEquals(1_127, sheet.getPhysicalNumberOfRows() - 1);
                assertEquals(32_767, sheet.getRow(418).getCell(6).getStringCellValue().length());
                assertEquals(CellType.NUMERIC, sheet.getRow(2).getCell(2).getCellType());
                assertEquals("", sheet.getRow(1).getCell(2).getStringCellValue());
            }
        } finally { Files.deleteIfExists(file); }
    }
}
