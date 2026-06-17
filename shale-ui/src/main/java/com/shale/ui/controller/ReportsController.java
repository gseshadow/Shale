package com.shale.ui.controller;

import com.shale.core.dto.CaseStatusReportRowDto;
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
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ReportsController {
    @FXML private DatePicker startDatePicker;
    @FXML private DatePicker endDatePicker;
    @FXML private Button refreshButton;
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
    private AppState appState;
    private CaseDao caseDao;

    public void init(AppState appState, CaseDao caseDao) {
        this.appState = Objects.requireNonNull(appState, "appState");
        this.caseDao = Objects.requireNonNull(caseDao, "caseDao");
        refreshReport();
    }

    @FXML
    private void initialize() {
        if (startDatePicker != null && startDatePicker.getValue() == null) {
            startDatePicker.setValue(LocalDate.now().minusMonths(12));
        }
        if (endDatePicker != null && endDatePicker.getValue() == null) {
            endDatePicker.setValue(LocalDate.now());
        }
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

    private void refreshReport() {
        if (appState == null || caseDao == null) {
            return;
        }
        Integer shaleClientId = appState.getShaleClientId();
        LocalDate startDate = startDatePicker == null ? null : startDatePicker.getValue();
        LocalDate endDate = endDatePicker == null ? null : endDatePicker.getValue();
        if (shaleClientId == null || shaleClientId <= 0 || startDate == null || endDate == null) {
            setStatus("Choose a valid date range.");
            return;
        }
        setLoading(true);
        executor.submit(() -> {
            try {
                List<CaseStatusReportRowDto> rows = caseDao.listCaseStatusReport(shaleClientId, startDate, endDate);
                Platform.runLater(() -> applyRows(rows));
            } catch (RuntimeException ex) {
                Platform.runLater(() -> {
                    applyRows(List.of());
                    setStatus("Unable to load report. Please try again.");
                });
            } finally {
                Platform.runLater(() -> setLoading(false));
            }
        });
    }

    private void applyRows(List<CaseStatusReportRowDto> rows) {
        statusReportTable.setItems(FXCollections.observableArrayList(rows));
        statusPieChart.getData().clear();
        if (rows == null || rows.isEmpty()) {
            setStatus("No cases found for this date range");
            return;
        }
        setStatus("");
        for (CaseStatusReportRowDto row : rows) {
            PieChart.Data slice = new PieChart.Data(row.caseStatus(), row.caseCount());
            statusPieChart.getData().add(slice);
            String color = ColorUtil.toCssBackgroundColorOrNull(row.color());
            if (color != null) {
                slice.nodeProperty().addListener((obs, oldNode, node) -> {
                    if (node != null) node.setStyle("-fx-pie-color: " + color + ";");
                });
            }
        }
    }

    private void setLoading(boolean loading) {
        if (refreshButton != null) refreshButton.setDisable(loading);
        if (loading) setStatus("Loading report…");
    }

    private void setStatus(String text) {
        if (statusLabel != null) statusLabel.setText(text == null ? "" : text);
    }
}
