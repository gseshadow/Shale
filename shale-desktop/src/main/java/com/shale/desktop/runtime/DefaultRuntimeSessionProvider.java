package com.shale.desktop.runtime;

import com.shale.data.runtime.RuntimeSessionService;

import java.util.Objects;

public final class DefaultRuntimeSessionProvider implements RuntimeSessionProvider {
	private RuntimeSessionService runtime;

	public void set(RuntimeSessionService runtimeSessionService) {
		this.runtime = Objects.requireNonNull(runtimeSessionService);
	}

	@Override
	public RuntimeSessionService requireRuntimeSession() {
		if (runtime == null)
			throw new IllegalStateException("Not logged in.");
		return runtime;
	}
}
