package com.shale.ui.controller;

import com.shale.data.dao.ContactDao;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ContactController {

	@FXML private Label contactTitleLabel;
	@FXML private Label errorLabel;
	@FXML private Label nameValue;
	@FXML private Label emailValue;
	@FXML private Label phoneValue;
	@FXML private Label lastUpdatedValue;

	private Integer contactId;
	private ContactDao contactDao;
	private ContactDao.ContactRow currentContact;

	private final ExecutorService dbExec = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "contact-detail-loader");
		t.setDaemon(true);
		return t;
	});

	public void init(int contactId, ContactDao contactDao) {
		this.contactId = contactId;
		this.contactDao = contactDao;
		Platform.runLater(this::loadContact);
	}

	private void loadContact() {
		if (contactId == null || contactDao == null) {
			setError("Unable to load this contact.");
			return;
		}

		dbExec.submit(() -> {
			try {
				ContactDao.ContactRow loaded = contactDao.findById(contactId);
				Platform.runLater(() -> {
					if (loaded == null) {
						setError("Unable to load this contact.");
						return;
					}
					clearError();
					currentContact = loaded;
					renderFromCurrent();
				});
			} catch (Exception ex) {
				Platform.runLater(() -> setError("Unable to load this contact."));
			}
		});
	}

	private void renderFromCurrent() {
		if (currentContact == null) {
			return;
		}

		String displayName = currentContact.displayName();
		if (contactTitleLabel != null) {
			contactTitleLabel.setText(displayName);
		}
		if (nameValue != null) {
			nameValue.setText(displayName);
		}
		if (emailValue != null) {
			emailValue.setText(fallback(currentContact.email()));
		}
		if (phoneValue != null) {
			phoneValue.setText(fallback(currentContact.phone()));
		}
		if (lastUpdatedValue != null) {
			if (currentContact.updatedAt() == null) {
				lastUpdatedValue.setText("—");
			} else {
				lastUpdatedValue.setText(DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a")
						.withZone(ZoneId.systemDefault())
						.format(currentContact.updatedAt()));
			}
		}
	}

	private void setError(String message) {
		if (errorLabel != null) {
			errorLabel.setText(message);
			errorLabel.setVisible(true);
			errorLabel.setManaged(true);
		}
	}

	private void clearError() {
		if (errorLabel != null) {
			errorLabel.setText("");
			errorLabel.setVisible(false);
			errorLabel.setManaged(false);
		}
	}

	private static String fallback(String value) {
		if (value == null || value.isBlank()) {
			return "—";
		}
		return value;
	}
}
