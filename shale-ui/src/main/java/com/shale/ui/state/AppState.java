package com.shale.ui.state;

public final class AppState {
	private volatile Integer userId;
	private volatile Integer shaleClientId;
	private volatile String userEmail;

	public Integer getUserId() {
		return userId;
	}

	public void setUserId(Integer userId) {
		this.userId = userId;
	}

	public Integer getShaleClientId() {
		return shaleClientId;
	}

	public void setShaleClientId(Integer shaleClientId) {
		this.shaleClientId = shaleClientId;
	}

	public String getUserEmail() {
		return userEmail;
	}

	public void setUserEmail(String userEmail) {
		this.userEmail = userEmail;
	}

	public void clear() {
		userId = null;
		shaleClientId = null;
		userEmail = null;
	}
}
