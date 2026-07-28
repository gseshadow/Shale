package com.shale.ui.export;

import com.shale.data.dao.CaseDao.CaseRow;
import com.shale.ui.services.CaseExportService.ReportExportRow;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Shared XLSX formatting for complete case/report exports. */
public final class CaseXlsxExporter {
    /** Excel's documented limit for text stored in a cell. */
    static final int MAX_CELL_TEXT_LENGTH = 32_767;
    private static final String[] CASE_HEADERS = {"Case Name", "Client", "Intake Date / Caller Date", "Case Status",
            "Opposing Parties", "Latest Case Update", "Description", "Date of Incident", "Statute of Limitations",
            "Tort Claims Notice Deadline", "Responsible Attorney"};
    private static final String[] REPORT_HEADERS = {"Case Name", "Case Status", "Created At", "Intake Date", "Denied Date",
            "Closed Date", "Date of Injury", "Description", "Statute of Limitations", "Tort Notice Deadline",
            "Updated At", "Responsible Attorney"};

    public void writeCases(Path path, List<CaseRow> rows) throws IOException {
        write(path, "Cases", CASE_HEADERS, rows, (row, value) -> {
            value.accept(row.name()); value.accept(row.clientName()); value.accept(row.intakeDate());
            value.accept(row.primaryStatusName()); value.accept(row.opposingPartiesName()); value.accept(row.latestCaseUpdate());
            value.accept(row.description()); value.accept(row.dateOfIncident()); value.accept(row.statuteOfLimitationsDate());
            value.accept(row.tortClaimsNoticeDeadline()); value.accept(row.responsibleAttorneyName());
        });
    }

    public void writeReport(Path path, String sheetName, List<ReportExportRow> rows) throws IOException {
        write(path, sheetName, REPORT_HEADERS, rows, (row, value) -> {
            var d = row.detail();
            value.accept(d.caseName()); value.accept(row.statusName()); value.accept(d.createdAt()); value.accept(d.intakeDate());
            value.accept(d.deniedDate()); value.accept(d.closedDate()); value.accept(d.dateOfInjury()); value.accept(d.description());
            value.accept(d.statuteOfLimitations()); value.accept(d.tortNoticeDeadline()); value.accept(d.updatedAt());
            value.accept(d.responsibleAttorney());
        });
    }

    private <T> void write(Path path, String requestedSheetName, String[] headers, List<T> rows,
                           RowValues<T> rowValues) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet;
            try {
                sheet = workbook.createSheet(safeSheetName(requestedSheetName));
            } catch (RuntimeException ex) {
                throw exportFailure("workbook creation", -1, null, ex);
            }
            Font font = workbook.createFont(); font.setBold(true);
            CellStyle headerStyle = workbook.createCellStyle(); headerStyle.setFont(font);
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd"));
            CellStyle dateTimeStyle = workbook.createCellStyle();
            dateTimeStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd hh:mm"));
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) { header.createCell(i).setCellValue(headers[i]); header.getCell(i).setCellStyle(headerStyle); }
            int rowIndex = 1;
            for (T item : rows) {
                Row row = sheet.createRow(rowIndex++);
                int[] column = {0};
                try {
                    rowValues.add(item, value -> {
                        int columnIndex = column[0]++;
                        setCell(row, columnIndex, value, dateStyle, dateTimeStyle);
                    });
                } catch (RuntimeException ex) {
                    int failedColumn = Math.min(column[0], headers.length) - 1;
                    throw exportFailure("cell conversion", rowIndex - 1,
                            failedColumn >= 0 ? headers[failedColumn] : null, ex);
                }
            }
            try {
                sheet.createFreezePane(0, 1);
                sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, Math.max(0, rowIndex - 1), 0, headers.length - 1));
                for (int i = 0; i < headers.length; i++) {
                    sheet.autoSizeColumn(i);
                    sheet.setColumnWidth(i, Math.min(sheet.getColumnWidth(i) + 512, 15000));
                }
            } catch (RuntimeException ex) {
                throw exportFailure("worksheet finalization", -1, null, ex);
            }
            try (var output = Files.newOutputStream(path)) {
                workbook.write(output);
            } catch (IOException | RuntimeException ex) {
                throw exportFailure("file writing", -1, null, ex);
            }
        }
    }

    private void setCell(Row row, int index, Object value, CellStyle dateStyle, CellStyle dateTimeStyle) {
        var cell = row.createCell(index);
        if (value instanceof LocalDate date) { cell.setCellValue(date); cell.setCellStyle(dateStyle); }
        else if (value instanceof LocalDateTime dateTime) { cell.setCellValue(dateTime); cell.setCellStyle(dateTimeStyle); }
        else cell.setCellValue(safeCellText(value));
    }

    private String safeCellText(Object value) {
        if (value == null) return "";
        String text = value.toString();
        if (text.length() <= MAX_CELL_TEXT_LENGTH) return text;
        int end = MAX_CELL_TEXT_LENGTH;
        if (Character.isHighSurrogate(text.charAt(end - 1))) end--;
        return text.substring(0, end);
    }

    private IOException exportFailure(String stage, int dataRow, String column, Exception cause) {
        StringBuilder message = new StringBuilder("Cases XLSX export failed at stage ").append(stage);
        if (dataRow >= 1) message.append(", data row ").append(dataRow);
        if (column != null) message.append(", column ").append(column);
        return new IOException(message.toString(), cause);
    }

    private String safeSheetName(String value) {
        String safe = value == null ? "Report" : value.replaceAll("[\\\\/*?:\\[\\]]", " ").trim();
        return safe.isEmpty() ? "Report" : safe.substring(0, Math.min(31, safe.length()));
    }

    @FunctionalInterface private interface RowValues<T> { void add(T row, java.util.function.Consumer<Object> value); }
}
