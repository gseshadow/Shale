package com.shale.core.runtime;

import java.sql.Connection;

public interface DbSessionProvider {
	Connection requireConnection();
}
