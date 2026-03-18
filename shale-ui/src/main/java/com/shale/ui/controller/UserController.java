package com.shale.ui.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

public final class UserController {

	@FXML
	private Label userStubTitleLabel;
	@FXML
	private Label userStubMessageLabel;

	public void init(int userId) {
		if (userStubTitleLabel != null) {
			userStubTitleLabel.setText("User #" + userId);
		}
		if (userStubMessageLabel != null) {
			userStubMessageLabel.setText("User profile view is not implemented yet. This stub route is ready for the full user view.");
		}
	}
}
