package com.shale.desktop.runtime;

import com.shale.core.runtime.DbSessionProvider;
import com.shale.data.runtime.RuntimeSessionService;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

public final class DesktopRuntimeSessionProvider implements DbSessionProvider {

	private RuntimeSessionService runtime;

	public synchronized void setRuntime(RuntimeSessionService runtime) {
		this.runtime = Objects.requireNonNull(runtime, "runtime");
	}

	public synchronized void clear() {
		this.runtime = null;
	}

	@Override
	public synchronized Connection requireConnection() {
		if (runtime == null) {
			throw new IllegalStateException("Database access requested before login");
		}
		try {
			return runtime.getConnection();
		} catch (SQLException e) {
			e.printStackTrace();
			return null;
		}
	}
}
