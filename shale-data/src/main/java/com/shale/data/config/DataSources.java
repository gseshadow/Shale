package com.shale.data.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;

public final class DataSources implements AutoCloseable {

	private final Config cfg;

	private final HikariDataSource auth;
	private volatile HikariDataSource runtime; // lazy

	public DataSources(Config cfg) {
		this.cfg = cfg;
		this.auth = pool(
				cfg.appJdbcUrl(),
				cfg.appUser(),
				cfg.appPass(),
				"auth-pool",
				/* isAuth */ true
		);
	}

	public DataSource auth() {
		return auth;
	}

	/** Create runtime pool lazily (after login). */
	public DataSource runtime() {
		HikariDataSource ds = runtime;
		if (ds == null) {
			synchronized (this) {
				ds = runtime;
				if (ds == null) {
					runtime = ds = pool(
							cfg.rtJdbcUrl(),
							cfg.rtUser(),
							cfg.rtPass(),
							"runtime-pool",
							/* isAuth */ false
					);
				}
			}
		}
		return ds;
	}

	@Override
	public void close() {
		if (auth != null)
			auth.close();
		resetRuntime();
	}

	private HikariDataSource pool(String url, String user, String pass, String name, boolean isAuthPool) {
		HikariConfig c = new HikariConfig();

		c.setJdbcUrl(url);
		c.setUsername(user);
		c.setPassword(pass);
		c.setPoolName(name);

		// Desktop app: don't pre-fill a bunch of connections (this is what was hurting you)
		c.setMinimumIdle(0);

		// Auth pool should be tiny; runtime can be larger but still reasonable
		int max = isAuthPool ? 3 : Math.min(cfg.maxPoolSize(), 10);
		c.setMaximumPoolSize(max);

		// Be more forgiving over WAN / Azure transient issues
		c.setConnectionTimeout(Math.max(cfg.connTimeoutMs(), 30_000));
		c.setValidationTimeout(5_000);

		// Make validation explicit (helps prevent "connection is closed" surprises)
		c.setConnectionTestQuery("SELECT 1");

		// Don't fail app startup if Azure is briefly unreachable
		c.setInitializationFailTimeout(-1);

		// Reduce stale sockets / dead connections on flaky networks
		c.setMaxLifetime(15 * 60 * 1000L); // 15 min
		c.setKeepaliveTime(5 * 60 * 1000L); // 5 min

		// SQL Server TLS settings
		c.addDataSourceProperty("encrypt", "true");
		c.addDataSourceProperty("trustServerCertificate", "false");
		c.addDataSourceProperty("hostNameInCertificate", "*.database.windows.net");

		return new HikariDataSource(c);
	}

	public void resetRuntime() {
		synchronized (this) {
			if (runtime != null) {
				runtime.close();
				runtime = null; // <-- critical
			}
		}
	}

}
