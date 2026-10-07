package com.shale.ui.state;

public final class AppState {
    private volatile long sessionRevision;
    private final java.util.concurrent.CopyOnWriteArrayList<Runnable> identityListeners =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    public long sessionRevision() { return sessionRevision; }
    public void addIdentityListener(Runnable listener) { identityListeners.add(listener); }
    public void removeIdentityListener(Runnable listener) { identityListeners.remove(listener); }
    private synchronized void identityChanged() {
        sessionRevision++;
        identityListeners.forEach(Runnable::run);
    }
	private volatile Integer userId;
	private volatile Integer shaleClientId;
	private volatile String userEmail;
	private volatile boolean admin;
	private volatile boolean attorney;

	public Integer getUserId() {
		return userId;
	}

	public void setUserId(Integer userId) {
		if (java.util.Objects.equals(this.userId, userId)) return;
		this.userId = userId;
        identityChanged();
	}

	public Integer getShaleClientId() {
		return shaleClientId;
	}

	public void setShaleClientId(Integer shaleClientId) {
		if (java.util.Objects.equals(this.shaleClientId, shaleClientId)) return;
		this.shaleClientId = shaleClientId;
        identityChanged();
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
		if (this.admin == admin) return;
		this.admin = admin;
        identityChanged();
	}

	public boolean isAttorney() {
		return attorney;
	}

	public void setAttorney(boolean attorney) {
		if (this.attorney == attorney) return;
		this.attorney = attorney;
        identityChanged();
	}

	public void clear() {
		userId = null;
		shaleClientId = null;
		userEmail = null;
		admin = false;
		attorney = false;
        identityChanged();
	}
}
