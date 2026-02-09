package com.shale.desktop.security;

import com.shale.core.model.User;

public final class SessionContext {
	private static final SessionContext INSTANCE = new SessionContext();
	private volatile User user;

	public SessionContext() {
	}

	public static SessionContext get() {
		return INSTANCE;
	}

	public boolean isAuthenticated() {
		return user != null;
	}

	public User user() {
		return user;
	}

	public int shaleClientId() {
		return user == null ? -1 : user.getShaleClientId();
	}

	public int userId() {
		return user == null ? -1 : user.getId();
	}

	public void setUser(User user) {
		this.user = user;
	}

	public void clear() {
		this.user = null;
	}
}
