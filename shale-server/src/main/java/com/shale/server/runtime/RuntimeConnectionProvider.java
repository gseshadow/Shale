package com.shale.server.runtime;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Opens a runtime database connection initialized for the resolved server principal.
 */
public interface RuntimeConnectionProvider {
    Connection openConnection(ServerPrincipal principal) throws SQLException;
}
