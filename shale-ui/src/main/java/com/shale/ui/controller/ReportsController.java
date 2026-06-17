package com.shale.ui.controller;

import com.shale.core.dto.CaseStatusReportRowDto;
import com.shale.core.dto.CaseStatusDto;
import com.shale.data.dao.CaseDao;
import com.shale.ui.state.AppState;
import com.shale.ui.util.ColorUtil;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyLongWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ReportsController {
    private static final DecimalFormat PERCENT_FORMAT = new DecimalFormat("0.0");

    @FXML private DatePicker startDatePicker;
    @FXML private DatePicker endDatePicker;
    @FXML private MenuButton statusFilterMenuButton;
    @FXML private Button refreshButton;
    @FXML private Button showAllResultsButton;
    @FXML private Label statusLabel;
    @FXML private TableView<CaseStatusReportRowDto> statusReportTable;
    @FXML private TableColumn<CaseStatusReportRowDto, String> caseStatusColumn;
    @FXML private TableColumn<CaseStatusReportRowDto, Number> caseCountColumn;
    @FXML private PieChart statusPieChart;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "reports-loader");
        t.setDaemon(true);
        return t;
    });
    private final Map<Integer, CaseStatusDto> availableStatusesById = new LinkedHashMap<>();
    private final Map<Integer, CheckMenuItem> statusMenuItemsById = new LinkedHashMap<>();
    private AppState appState;
    private CaseDao caseDao;

    public void init(AppState appState, CaseDao caseDao) {
        this.appState = Objects.requireNonNull(appState, "appState");
        this.caseDao = Objects.requireNonNull(caseDao, "caseDao");
        loadStatusesAndReport();
    }

    @FXML
    private void initialize() {
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
        executor.submit(() -> {
            try {
                List<CaseStatusDto> statuses = caseDao.listCaseStatuses(shaleClientId, true);
                Platform.runLater(() -> {
                    applyAvailableStatuses(statuses);
                    refreshReport();
                });
            } catch (RuntimeException ex) {
                Platform.runLater(() -> {
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
                List<CaseStatusReportRowDto> rows = caseDao.listCaseStatusReport(shaleClientId, startDate, endDate, selectedStatusIds);
                Platform.runLater(() -> applyRows(rows));
            } catch (RuntimeException ex) {
                Platform.runLater(() -> {
                    clearReport();
                    setStatus("Unable to load report. Please try again.");
                });
            } finally {
                Platform.runLater(() -> setLoading(false));
            }
        });
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
        statusPieChart.getData().clear();
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
            String color = ColorUtil.toCssBackgroundColorOrNull(row.color());
            PieChart.Data slice = new PieChart.Data(sliceName, row.caseCount());
            if (color != null) {
                slice.nodeProperty().addListener((obs, oldNode, node) -> {
                    if (node != null) node.setStyle("-fx-pie-color: " + color + ";");
                });
            }
            slices.add(slice);
            colorsBySliceName.put(sliceName, color);
        }
        statusPieChart.setData(FXCollections.observableArrayList(slices));
        applyPieSliceColors(colorsBySliceName);
        Platform.runLater(() -> applyPieSliceColors(colorsBySliceName));
    }

    private void applyPieSliceColors(Map<String, String> colorsBySliceName) {
        for (PieChart.Data slice : statusPieChart.getData()) {
            String color = colorsBySliceName.get(slice.getName());
            if (color != null && slice.getNode() != null) {
                slice.getNode().setStyle("-fx-pie-color: " + color + ";");
            }
        }
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
    }

    private void setLoading(boolean loading) {
        if (refreshButton != null) refreshButton.setDisable(loading);
        if (showAllResultsButton != null) showAllResultsButton.setDisable(loading);
        if (loading) setStatus("Loading report…");
    }

    private void setStatus(String text) {
        if (statusLabel != null) statusLabel.setText(text == null ? "" : text);
    }
}
