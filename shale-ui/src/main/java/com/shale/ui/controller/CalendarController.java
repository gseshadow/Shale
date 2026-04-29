package com.shale.ui.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

public final class CalendarController {
    @FXML
    private Label calendarTitleLabel;

    @FXML
    private Label calendarSubtitleLabel;

    @FXML
    private Label calendarEmptyStateLabel;

    @FXML
    private void initialize() {
        if (calendarTitleLabel != null) {
            calendarTitleLabel.setText("Calendar");
        }
        if (calendarSubtitleLabel != null) {
            calendarSubtitleLabel.setText("Calendar events and case/task deadlines will appear here.");
        }
        if (calendarEmptyStateLabel != null) {
            calendarEmptyStateLabel.setText("No calendar items to display yet.");
        }
    }
}
