package com.shale.data.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;

public final class DataSources implements AutoCloseable {

	private final Config cfg;

	private final HikariDataSource auth;
	private volatile HikariDataSource runtime; // lazy
    private HikariDataSource aiCaseData; // independent, lazy, guarded by this
    private boolean closed;

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

    /** Independently configured SQL identity. Absence fails; ordinary pools are never candidates. */
    public synchronized DataSource aiCaseData() {
        if (closed) throw new IllegalStateException("Datasources are closed.");
        if (aiCaseData == null) {
            // Resolve all required settings before constructing a pool; no partial initialization.
            String url = cfg.aiCaseDataJdbcUrl();
            // TLS, identity and timeout settings cannot be overridden by JDBC URL precedence.
            if (!url.startsWith("jdbc:sqlserver://") || java.util.regex.Pattern.compile(
                    "(?i);\\s*(encrypt|trustServerCertificate|hostNameInCertificate|user|userName|password|integratedSecurity|authentication|accessToken|multipleActiveResultSets|loginTimeout|socketTimeout)\\s*=")
                    .matcher(url).find()) throw new IllegalStateException("AI JDBC URL contains a protected connection setting.");
            String user = cfg.aiCaseDataUser();
            String password = cfg.aiCaseDataPassword();
            HikariConfig c = new HikariConfig();
            c.setJdbcUrl(url);
            c.setUsername(user);
            c.setPassword(password);
            c.setPoolName("ai-case-data-pool");
            c.setMinimumIdle(0);
            c.setMaximumPoolSize(10);
            c.setConnectionTimeout(3000);
            c.setValidationTimeout(1000);
            c.setInitializationFailTimeout(-1);
            c.setMaxLifetime(15 * 60 * 1000L);
            c.addDataSourceProperty("encrypt", "true");
            c.addDataSourceProperty("trustServerCertificate", "false");
            c.addDataSourceProperty("loginTimeout", "3");
            c.addDataSourceProperty("socketTimeout", "5000");
            c.addDataSourceProperty("applicationName", "ShaleAiCaseData");
            aiCaseData = new HikariDataSource(c);
        }
        return aiCaseData;
    }

    /** Rotation drains only AI connections. Enabled composition must reattest before readiness. */
    public synchronized void resetAiCaseData() {
        if (aiCaseData != null) { aiCaseData.close(); aiCaseData = null; }
    }

	@Override
	public synchronized void close() {
		closed = true;
        resetAiCaseData();
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
