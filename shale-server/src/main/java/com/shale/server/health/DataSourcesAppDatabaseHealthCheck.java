package com.shale.server.health;

import java.sql.Connection;
import java.sql.Statement;

import com.shale.data.config.DataSources;

public final class DataSourcesAppDatabaseHealthCheck implements AppDatabaseHealthCheck {
    private final DataSources dataSources;

    public DataSourcesAppDatabaseHealthCheck(DataSources dataSources) {
        this.dataSources = java.util.Objects.requireNonNull(dataSources, "dataSources");
    }

    @Override
    public boolean isReady() {
        try (Connection connection = dataSources.auth().getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("SELECT 1");
            return true;
        } catch (RuntimeException | java.sql.SQLException ex) {
            return false;
        }
    }
}
