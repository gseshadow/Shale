package com.shale.ui.state;

public final class AppState {
	private volatile Integer userId;
	private volatile Integer shaleClientId;
	private volatile String userEmail;
	private volatile boolean admin;
	private volatile boolean attorney;

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

	public boolean isAdmin() {
		return admin;
	}

	public void setUserEmail(String userEmail) {
		this.userEmail = userEmail;
	}

	public void setAdmin(boolean admin) {
		this.admin = admin;
	}

	public boolean isAttorney() {
		return attorney;
	}

	public void setAttorney(boolean attorney) {
		this.attorney = attorney;
	}

	public void clear() {
		userId = null;
		shaleClientId = null;
		userEmail = null;
		admin = false;
		attorney = false;
	}
}
