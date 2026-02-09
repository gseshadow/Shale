package com.shale.desktop.runtime;

import com.shale.data.runtime.RuntimeSessionService;

public interface RuntimeSessionProvider {
	RuntimeSessionService requireRuntimeSession();
}
