package com.shale.ui.component;

import com.shale.core.dto.CaseDateConfirmationDto;
import com.shale.ui.util.ActionButtonFactory;
import com.shale.ui.util.ControlStyles;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

/** Shared compact presentation and action for an authoritative Case Date confirmation. */
public final class CaseDateConfirmationView {
    private static final DateTimeFormatter CONFIRMED_AT = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a");
    private CaseDateConfirmationView() { }

    public static Node create(CaseDateConfirmationDto value, String requiredRoleName,
            boolean eligible, Runnable confirmAction) {
        Objects.requireNonNull(value, "value");
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("case-date-confirmation");
        if (value.status() == CaseDateConfirmationDto.Status.NOT_REQUIRED) return row;
        Label state = new Label(message(value, requiredRoleName));
        state.setWrapText(true);
        state.getStyleClass().addAll("shale-semantic-chip", "metadata-chip-compact",
                value.status() == CaseDateConfirmationDto.Status.PENDING
                        ? "shale-semantic-chip-warning" : "shale-semantic-chip-success");
        HBox.setHgrow(state, Priority.ALWAYS);
        row.getChildren().add(state);
        if (value.status() == CaseDateConfirmationDto.Status.PENDING && eligible && confirmAction != null) {
            Button confirm = ActionButtonFactory.semantic("Confirm", e -> confirmAction.run(),
                    ControlStyles.Purpose.PRIMARY, ControlStyles.Size.SMALL);
            confirm.setAccessibleText("Confirm this case date");
            row.getChildren().add(confirm);
        }
        return row;
    }

    static String message(CaseDateConfirmationDto value, String roleName) {
        if (value.status() == CaseDateConfirmationDto.Status.PENDING)
            return "Confirmation needed — " + safeRole(roleName);
        if (value.status() == CaseDateConfirmationDto.Status.CONFIRMED)
            return "Confirmed by " + (value.confirmedByDisplayName() == null ? "a firm member" : value.confirmedByDisplayName())
                    + " on " + value.confirmedAt().format(CONFIRMED_AT);
        return "";
    }

    private static String safeRole(String roleName) {
        return roleName == null || roleName.isBlank() ? "required firm-wide role" : roleName;
    }
}
