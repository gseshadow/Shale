package com.shale.ui.controller;

import com.shale.data.dao.ContactDao;
import com.shale.data.dao.ContactDao.ContactDetailRow;
import com.shale.ui.state.AppState;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ContactViewController {

    @FXML
    private Label contactNameLabel;
    @FXML
    private Label contactMetaLabel;
    @FXML
    private Label contactPlaceholderLabel;

    private int contactId;
    private ContactDao contactDao;
    private AppState appState;

    private final ExecutorService dbExec = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "contact-view-loader");
        t.setDaemon(true);
        return t;
    });

    public void init(int contactId, ContactDao contactDao, AppState appState) {
        this.contactId = contactId;
        this.contactDao = contactDao;
        this.appState = appState;
    }

    @FXML
    private void initialize() {
        Platform.runLater(this::loadContact);
    }

    private void loadContact() {
        Integer tenantId = appState == null ? null : appState.getShaleClientId();
        if (contactDao == null || tenantId == null || tenantId <= 0 || contactId <= 0) {
            renderUnavailable("Contact details are unavailable right now.");
            return;
        }

        dbExec.submit(() -> {
            try {
                ContactDetailRow row = contactDao.findById(contactId, tenantId);
                Platform.runLater(() -> renderContact(row));
            } catch (RuntimeException ex) {
                Platform.runLater(() -> renderUnavailable("Unable to load this contact yet."));
            }
        });
    }

    private void renderContact(ContactDetailRow row) {
        if (row == null) {
            renderUnavailable("This contact could not be found for the current tenant.");
            return;
        }

        contactNameLabel.setText(safe(row.displayName()).isBlank() ? "Contact" : row.displayName());
        String email = safe(row.email()).isBlank() ? "No email on file" : row.email();
        String phone = safe(row.phone()).isBlank() ? "No phone on file" : row.phone();
        contactMetaLabel.setText(email + " • " + phone);
        contactPlaceholderLabel.setText("Contact detail view is stubbed in for navigation. Editing, relationships, and richer profile sections are intentionally deferred for the next pass.");
    }

    private void renderUnavailable(String message) {
        contactNameLabel.setText("Contact");
        contactMetaLabel.setText("Contact #" + contactId);
        contactPlaceholderLabel.setText(message);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
