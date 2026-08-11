package com.shale.ui.controller;

import com.shale.core.dto.CaseStatusReportRowDto;
import com.shale.core.dto.ReportCaseDetailRowDto;
import com.shale.core.dto.CaseStatusDto;
import com.shale.data.dao.CaseDao;
import com.shale.data.dao.CaseSummaryDao;
import com.shale.ui.component.StatisticCard;
import com.shale.ui.state.AppState;
import com.shale.ui.services.CaseExportService;
import com.shale.ui.export.CaseXlsxExporter;
import com.shale.ui.component.dialog.AppDialogs;
import com.shale.ui.util.ColorUtil;
import com.shale.ui.util.ControlStyles;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyLongWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.TableCell;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ButtonBar;
import javafx.scene.layout.BorderPane;

import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import javafx.stage.FileChooser;
import java.io.File;

public final class ReportsController {
    private static final DecimalFormat PERCENT_FORMAT = new DecimalFormat("0.0");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @FXML private DatePicker startDatePicker;
    @FXML private DatePicker endDatePicker;
    @FXML private MenuButton statusFilterMenuButton;
    @FXML private Button refreshButton;
    @FXML private Button showAllResultsButton;
    @FXML private Button exportButton;
    @FXML private Label statusLabel;
    @FXML private TableView<CaseStatusReportRowDto> statusReportTable;
    @FXML private TableColumn<CaseStatusReportRowDto, String> caseStatusColumn;
    @FXML private TableColumn<CaseStatusReportRowDto, Number> caseCountColumn;
    @FXML private PieChart statusPieChart;
    @FXML private StatisticCard totalCasesCard;
    @FXML private StatisticCard visibleStatusesCard;
    @FXML private StatisticCard largestStatusCard;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "reports-loader");
        t.setDaemon(true);
        return t;
    });
    private final Map<Integer, CaseStatusDto> availableStatusesById = new LinkedHashMap<>();
    private final Map<Integer, CheckMenuItem> statusMenuItemsById = new LinkedHashMap<>();
    private final Map<String, CaseStatusReportRowDto> reportRowsBySliceName = new LinkedHashMap<>();
    private AppState appState;
    private CaseDao caseDao;
    private CaseSummaryDao caseSummaryDao;
    private CaseExportService caseExportService;
    private final CaseXlsxExporter xlsxExporter = new CaseXlsxExporter();
    private boolean exportInProgress;
    private final AtomicLong loadGeneration = new AtomicLong();

    public void init(AppState appState, CaseDao caseDao, CaseSummaryDao caseSummaryDao, CaseExportService caseExportService) {
        this.appState = Objects.requireNonNull(appState, "appState");
        this.caseDao = Objects.requireNonNull(caseDao, "caseDao");
        this.caseSummaryDao = Objects.requireNonNull(caseSummaryDao, "caseSummaryDao");
        this.caseExportService = Objects.requireNonNull(caseExportService, "caseExportService");
        loadStatusesAndReport();
    }

    @FXML
    private void initialize() {
        ControlStyles.formControl(startDatePicker);
        ControlStyles.formControl(endDatePicker);
        ControlStyles.formControl(statusFilterMenuButton);
        ControlStyles.apply(refreshButton, ControlStyles.Purpose.PRIMARY, ControlStyles.Size.STANDARD);
        ControlStyles.apply(showAllResultsButton, ControlStyles.Purpose.SECONDARY, ControlStyles.Size.STANDARD);
        ControlStyles.apply(exportButton, ControlStyles.Purpose.SECONDARY, ControlStyles.Size.STANDARD);
        if (caseStatusColumn != null) {
            caseStatusColumn.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue().caseStatus()));
        }
        if (caseCountColumn != null) {
            caseCountColumn.setCellValueFactory(data -> new ReadOnlyLongWrapper(data.getValue().caseCount()));
        }
    }

    @FXML
    private void onRefresh() {
        refreshReport();
    }

    @FXML
    private void onShowAllResults() {
        if (startDatePicker != null) startDatePicker.setValue(null);
        if (endDatePicker != null) endDatePicker.setValue(null);
        selectAllStatuses(true);
        refreshReport();
    }

    private void loadStatusesAndReport() {
        if (appState == null || caseDao == null) {
            return;
        }
        Integer shaleClientId = appState.getShaleClientId();
        if (shaleClientId == null || shaleClientId <= 0) {
            setStatus("No tenant is selected.");
            return;
        }
        setLoading(true);
        long generation = loadGeneration.incrementAndGet();
        executor.submit(() -> {
            try {
                List<CaseStatusDto> statuses = caseDao.listCaseStatuses(shaleClientId, true);
                Platform.runLater(() -> {
                    if (!isCurrentLoad(generation, shaleClientId)) return;
                    applyAvailableStatuses(statuses);
                    refreshReport();
                });
            } catch (RuntimeException ex) {
                Platform.runLater(() -> {
                    if (!isCurrentLoad(generation, shaleClientId)) return;
                    clearReport();
                    setStatus("Unable to load case statuses. Please try again.");
                    setLoading(false);
                });
            }
        });
    }

    private void refreshReport() {
        if (appState == null || caseDao == null) {
            return;
        }
        Integer shaleClientId = appState.getShaleClientId();
        LocalDate startDate = startDatePicker == null ? null : startDatePicker.getValue();
        LocalDate endDate = endDatePicker == null ? null : endDatePicker.getValue();
        List<Integer> selectedStatusIds = selectedStatusIds();
        long generation = loadGeneration.incrementAndGet();
        if (shaleClientId == null || shaleClientId <= 0) {
            setStatus("No tenant is selected.");
            return;
        }
        if (selectedStatusIds.isEmpty()) {
            clearReport();
            setStatus("No case statuses selected.");
            updateStatusFilterText();
            setLoading(false);
            return;
        }
        setLoading(true);
        executor.submit(() -> {
            try {
                List<CaseStatusReportRowDto> rows = caseSummaryDao.listActiveStatusReport(shaleClientId, startDate, endDate, selectedStatusIds);
                Platform.runLater(() -> { if (isCurrentLoad(generation, shaleClientId)) applyRows(rows); });
            } catch (RuntimeException ex) {
                Platform.runLater(() -> {
                    if (!isCurrentLoad(generation, shaleClientId)) return;
                    clearReport();
                    setStatus("Unable to load report. Please try again.");
                });
            } finally {
                Platform.runLater(() -> { if (isCurrentLoad(generation, shaleClientId)) setLoading(false); });
            }
        });
    }

    private boolean isCurrentLoad(long generation, int tenantId) {
        return generation == loadGeneration.get() && appState != null
                && Objects.equals(appState.getShaleClientId(), tenantId);
    }

    private void applyAvailableStatuses(List<CaseStatusDto> statuses) {
        availableStatusesById.clear();
        statusMenuItemsById.clear();
        if (statusFilterMenuButton != null) {
            statusFilterMenuButton.getItems().clear();
            MenuItem selectAll = new MenuItem("Select all");
            selectAll.setOnAction(event -> selectAllStatuses(true));
            MenuItem clear = new MenuItem("Clear");
            clear.setOnAction(event -> selectAllStatuses(false));
            statusFilterMenuButton.getItems().addAll(selectAll, clear, new SeparatorMenuItem());
        }
        if (statuses != null) {
            for (CaseStatusDto status : statuses) {
                if (status == null || status.id() <= 0) continue;
                availableStatusesById.put(status.id(), status);
                CheckMenuItem item = new CheckMenuItem(status.name());
                item.setSelected(true);
                item.selectedProperty().addListener((obs, oldValue, newValue) -> updateStatusFilterText());
                statusMenuItemsById.put(status.id(), item);
                if (statusFilterMenuButton != null) statusFilterMenuButton.getItems().add(item);
            }
        }
        updateStatusFilterText();
    }

    private void applyRows(List<CaseStatusReportRowDto> rows) {
        List<CaseStatusReportRowDto> safeRows = rows == null ? List.of() : rows;
        statusReportTable.setItems(FXCollections.observableArrayList(safeRows));
        updateSummaryMetrics(safeRows);
        statusPieChart.getData().clear();
        reportRowsBySliceName.clear();
        long total = safeRows.stream().mapToLong(CaseStatusReportRowDto::caseCount).sum();
        if (safeRows.isEmpty()) {
            setStatus("No case statuses selected.");
            return;
        }
        if (total <= 0) {
            setStatus("No cases found for this filter.");
            return;
        }
        setStatus("");
        Map<String, String> colorsBySliceName = new LinkedHashMap<>();
        List<PieChart.Data> slices = new ArrayList<>();
        for (CaseStatusReportRowDto row : safeRows) {
            if (row.caseCount() <= 0) continue;
            double percentage = (row.caseCount() * 100.0) / total;
            String sliceName = row.caseStatus() + " " + PERCENT_FORMAT.format(percentage) + "%";
            String color = resolvedStatusColor(row.color());
            PieChart.Data slice = new PieChart.Data(sliceName, row.caseCount());
            if (color != null) {
                slice.nodeProperty().addListener((obs, oldNode, node) -> {
                    if (node != null) node.setStyle("-fx-pie-color: " + color + ";");
                });
            }
            slices.add(slice);
            colorsBySliceName.put(sliceName, color);
            reportRowsBySliceName.put(sliceName, row);
        }
        statusPieChart.setData(FXCollections.observableArrayList(slices));
        applyPieSliceColors(colorsBySliceName);
        attachPieSliceHandlers();
        Platform.runLater(() -> {
            applyPieSliceColors(colorsBySliceName);
            attachPieSliceHandlers();
        });
    }

    private void applyPieSliceColors(Map<String, String> colorsBySliceName) {
        for (PieChart.Data slice : statusPieChart.getData()) {
            String color = colorsBySliceName.get(slice.getName());
            if (color != null && slice.getNode() != null) {
                slice.getNode().setStyle("-fx-pie-color: " + color + ";");
            }
        }
        applyPieLegendColors(colorsBySliceName);
    }

    private void applyPieLegendColors(Map<String, String> colorsBySliceName) {
        if (statusPieChart == null || colorsBySliceName == null || colorsBySliceName.isEmpty()) return;
        for (Node legendItem : statusPieChart.lookupAll(".chart-legend-item")) {
            if (!(legendItem instanceof Labeled labeled)) continue;
            String color = colorsBySliceName.get(labeled.getText());
            Node symbol = labeled.getGraphic();
            if (color != null && symbol != null) {
                symbol.setStyle("-fx-background-color: " + color + ";");
            }
        }
    }

    private String resolvedStatusColor(String storedColor) {
        return ColorUtil.toCssBackgroundColor(storedColor);
    }

    private void attachPieSliceHandlers() {
        for (PieChart.Data slice : statusPieChart.getData()) {
            if (slice == null || slice.getNode() == null) continue;
            CaseStatusReportRowDto row = reportRowsBySliceName.get(slice.getName());
            if (row == null || row.caseCount() <= 0) continue;
            slice.getNode().setOnMouseClicked(event -> openCaseStatusCasesDialog(row));
        }
    }

    private void openCaseStatusCasesDialog(CaseStatusReportRowDto row) {
        if (row == null || row.caseCount() <= 0 || appState == null || caseDao == null) return;
        Integer shaleClientId = appState.getShaleClientId();
        if (shaleClientId == null || shaleClientId <= 0) {
            setStatus("No tenant is selected.");
            return;
        }
        LocalDate startDate = startDatePicker == null ? null : startDatePicker.getValue();
        LocalDate endDate = endDatePicker == null ? null : endDatePicker.getValue();
        CaseExportService.ReportCriteria criteria = new CaseExportService.ReportCriteria(shaleClientId, startDate, endDate, List.of(row.statusId()));
        long generation = loadGeneration.get();
        setLoading(true);
        executor.submit(() -> {
            try {
                List<ReportCaseDetailRowDto> rows = caseSummaryDao.listActiveStatusReportCases(
                        shaleClientId, row.statusId(), startDate, endDate).stream()
                        .map(CaseSummaryDao.ReportCaseRow::toDetailRow).toList();
                Platform.runLater(() -> { if (isCurrentLoad(generation, shaleClientId))
                    showCaseDetailsDialog(row.caseStatus(), startDate, endDate, rows, criteria); });
            } catch (RuntimeException ex) {
                Platform.runLater(() -> { if (isCurrentLoad(generation, shaleClientId))
                    setStatus("Unable to load cases for " + row.caseStatus() + ". Please try again."); });
            } finally {
                Platform.runLater(() -> { if (isCurrentLoad(generation, shaleClientId)) setLoading(false); });
            }
        });
    }

    private void showCaseDetailsDialog(String statusName, LocalDate startDate, LocalDate endDate, List<ReportCaseDetailRowDto> rows,
                                       CaseExportService.ReportCriteria criteria) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(statusName + " — " + dateRangeLabel(startDate, endDate));
        ButtonType exportType = new ButtonType("Export", ButtonBar.ButtonData.LEFT);
        dialog.getDialogPane().getButtonTypes().addAll(exportType, ButtonType.CLOSE);
        DialogPane pane = dialog.getDialogPane();
        pane.setPrefSize(1200, 650);
        pane.setMinSize(800, 420);
        pane.getStylesheets().add(Objects.requireNonNull(
                ReportsController.class.getResource("/css/app.css")).toExternalForm());
        dialog.setResizable(true);

        TableView<ReportCaseDetailRowDto> table = new TableView<>();
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No cases found for this status and date range."));
        addCaseDetailColumn(table, "Case Name", 180, ReportCaseDetailRowDto::caseName);
        addCaseDetailColumn(table, "Created At", 145, row -> formatDateTime(row.createdAt()));
        addCaseDetailColumn(table, "Intake Date", 110, row -> formatDate(row.intakeDate()));
        addCaseDetailColumn(table, "Denied Date", 110, row -> formatDate(row.deniedDate()));
        addCaseDetailColumn(table, "Closed Date", 110, row -> formatDate(row.closedDate()));
        addCaseDetailColumn(table, "Date of Injury", 120, row -> formatDate(row.dateOfInjury()));
        addCaseDetailColumn(table, "Description", 260, ReportCaseDetailRowDto::description);
        addCaseDetailColumn(table, "Statute of Limitations", 160, row -> formatDate(row.statuteOfLimitations()));
        addCaseDetailColumn(table, "Tort Notice Deadline", 160, row -> formatDate(row.tortNoticeDeadline()));
        addCaseDetailColumn(table, "Updated At", 145, row -> formatDateTime(row.updatedAt()));
        addCaseDetailColumn(table, "Responsible Attorney", 170, ReportCaseDetailRowDto::responsibleAttorney);
        table.setItems(FXCollections.observableArrayList(rows == null ? List.of() : rows));

        BorderPane content = new BorderPane(table);
        content.setPrefSize(1180, 600);
        pane.setContent(content);
        Button drillExport = (Button) pane.lookupButton(exportType);
        ControlStyles.apply(drillExport, ControlStyles.Purpose.SECONDARY, ControlStyles.Size.STANDARD);
        Button closeButton = (Button) pane.lookupButton(ButtonType.CLOSE);
        ControlStyles.apply(closeButton, ControlStyles.Purpose.SECONDARY, ControlStyles.Size.STANDARD);
        drillExport.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            event.consume();
            exportReport(criteria, statusName, statusName + " Cases", drillExport);
        });
        dialog.initOwner(statusPieChart == null || statusPieChart.getScene() == null ? null : statusPieChart.getScene().getWindow());
        dialog.showAndWait();
    }

    @FXML
    private void onExport() {
        if (appState == null) return;
        Integer tenantId = appState.getShaleClientId();
        if (tenantId == null || tenantId <= 0) { setStatus("No tenant is selected."); return; }
        exportReport(new CaseExportService.ReportCriteria(tenantId,
                startDatePicker == null ? null : startDatePicker.getValue(),
                endDatePicker == null ? null : endDatePicker.getValue(), selectedStatusIds()),
                "Case Status Report", "Case Status Report", exportButton);
    }

    private void exportReport(CaseExportService.ReportCriteria criteria, String fileLabel, String sheetName, Button button) {
        if (exportInProgress || criteria.statusIds().isEmpty()) return;
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Report to XLSX");
        chooser.setInitialFileName(safeFileName(fileLabel) + ".xlsx");
        chooser.getExtensionFilters().setAll(new FileChooser.ExtensionFilter("Excel Workbook (*.xlsx)", "*.xlsx"));
        File file = chooser.showSaveDialog(statusPieChart == null || statusPieChart.getScene() == null ? null : statusPieChart.getScene().getWindow());
        if (file == null) return;
        Map<Integer, String> statusNames = new LinkedHashMap<>();
        availableStatusesById.forEach((id, status) -> statusNames.put(id, status.name()));
        Map<Integer, String> namesSnapshot = Map.copyOf(statusNames);
        exportInProgress = true;
        if (button != null) button.setDisable(true);
        executor.submit(() -> {
            try {
                var rows = caseExportService.exportReport(criteria, namesSnapshot);
                xlsxExporter.writeReport(file.toPath(), sheetName, rows);
                Platform.runLater(() -> finishExport(button, () -> AppDialogs.showInfo(fileChooserOwner(), "Export Complete", "Report exported to:\n" + file.getAbsolutePath())));
            } catch (RuntimeException | java.io.IOException ex) {
                Platform.runLater(() -> finishExport(button, () -> AppDialogs.showError(fileChooserOwner(), "Export Failed", "Unable to export the report. Please try again.")));
            }
        });
    }

    private void finishExport(Button button, Runnable feedback) {
        exportInProgress = false;
        if (button != null) button.setDisable(false);
        feedback.run();
    }

    private javafx.stage.Window fileChooserOwner() {
        return statusPieChart == null || statusPieChart.getScene() == null ? null : statusPieChart.getScene().getWindow();
    }

    private String safeFileName(String value) {
        String safe = value == null ? "report" : value.replaceAll("[^A-Za-z0-9._ -]", "_").trim();
        return safe.isEmpty() ? "report" : safe;
    }

    private interface CaseDetailTextProvider {
        String value(ReportCaseDetailRowDto row);
    }

    private void addCaseDetailColumn(TableView<ReportCaseDetailRowDto> table, String title, double width, CaseDetailTextProvider provider) {
        TableColumn<ReportCaseDetailRowDto, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(data -> new ReadOnlyStringWrapper(provider.value(data.getValue())));
        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                String value = empty || item == null ? "" : item;
                setText(value);
                setTooltip(value.isBlank() ? null : new Tooltip(value));
            }
        });
        table.getColumns().add(column);
    }

    private String dateRangeLabel(LocalDate startDate, LocalDate endDate) {
        if (startDate == null && endDate == null) return "All dates";
        if (startDate != null && endDate != null) return startDate + " to " + endDate;
        if (startDate != null) return startDate + " onward";
        return "Through " + endDate;
    }

    private String formatDate(LocalDate value) {
        return value == null ? "" : value.toString();
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? "" : DATE_TIME_FORMAT.format(value);
    }

    private List<Integer> selectedStatusIds() {
        List<Integer> ids = new ArrayList<>();
        for (Map.Entry<Integer, CheckMenuItem> entry : statusMenuItemsById.entrySet()) {
            if (entry.getValue().isSelected()) ids.add(entry.getKey());
        }
        return ids;
    }

    private void selectAllStatuses(boolean selected) {
        for (CheckMenuItem item : statusMenuItemsById.values()) item.setSelected(selected);
        updateStatusFilterText();
    }

    private void updateStatusFilterText() {
        if (statusFilterMenuButton == null) return;
        int selected = selectedStatusIds().size();
        int total = availableStatusesById.size();
        statusFilterMenuButton.setText("Statuses (" + selected + "/" + total + ")");
    }

    private void clearReport() {
        statusReportTable.setItems(FXCollections.observableArrayList());
        statusPieChart.getData().clear();
        reportRowsBySliceName.clear();
        updateSummaryMetrics(List.of());
    }

    private void updateSummaryMetrics(List<CaseStatusReportRowDto> rows) {
        List<CaseStatusReportRowDto> safeRows = rows == null ? List.of() : rows;
        long total = safeRows.stream().mapToLong(CaseStatusReportRowDto::caseCount).sum();
        long nonZeroStatuses = safeRows.stream().filter(row -> row.caseCount() > 0).count();
        CaseStatusReportRowDto largest = safeRows.stream()
                .filter(row -> row.caseCount() > 0)
                .max((left, right) -> Long.compare(left.caseCount(), right.caseCount()))
                .orElse(null);
        if (totalCasesCard != null) {
            totalCasesCard.setTitle("Total cases");
            totalCasesCard.setValue(Long.toString(total));
            totalCasesCard.setSubtitle(dateRangeLabel(startDatePicker == null ? null : startDatePicker.getValue(), endDatePicker == null ? null : endDatePicker.getValue()));
        }
        if (visibleStatusesCard != null) {
            visibleStatusesCard.setTitle("Statuses shown");
            visibleStatusesCard.setValue(Long.toString(nonZeroStatuses));
            visibleStatusesCard.setSubtitle(selectedStatusIds().size() + " selected");
        }
        if (largestStatusCard != null) {
            largestStatusCard.setTitle("Largest status");
            largestStatusCard.setValue(largest == null ? "—" : Long.toString(largest.caseCount()));
            largestStatusCard.setSubtitle(largest == null ? "No cases in current filter" : largest.caseStatus());
        }
    }

    private void setLoading(boolean loading) {
        if (refreshButton != null) refreshButton.setDisable(loading);
        if (showAllResultsButton != null) showAllResultsButton.setDisable(loading);
        if (exportButton != null) exportButton.setDisable(loading || exportInProgress);
        if (loading) setStatus("Loading report…");
    }

    private void setStatus(String text) {
        if (statusLabel != null) statusLabel.setText(text == null ? "" : text);
    }
}
