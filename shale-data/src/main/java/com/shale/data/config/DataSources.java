package com.shale.data.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;

public final class DataSources implements AutoCloseable {
    private final HikariDataSource auth;
    private final HikariDataSource runtime;

    public DataSources(Config cfg) {
        this.auth = pool(cfg.appJdbcUrl(), cfg.appUser(), cfg.appPass(), "auth-pool", cfg);
        this.runtime = pool(cfg.rtJdbcUrl(), cfg.rtUser(), cfg.rtPass(), "runtime-pool", cfg);
    }

    public DataSource auth()   { return auth; }
    public DataSource runtime(){ return runtime; }

    private static HikariDataSource pool(String url, String user, String pass, String name, Config cfg) {
        HikariConfig c = new HikariConfig();
        c.setJdbcUrl(url);
        c.setUsername(user);
        c.setPassword(pass);
        c.setPoolName(name);
        c.setMaximumPoolSize(cfg.maxPoolSize());
        c.setConnectionTimeout(cfg.connTimeoutMs());
        // SQL Server recommended props (encrypted channel)
        c.addDataSourceProperty("encrypt", "true");
        c.addDataSourceProperty("trustServerCertificate", "false");
        return new HikariDataSource(c);
    }

    @Override public void close() {
        if (auth != null) auth.close();
        if (runtime != null) runtime.close();
    }
}
