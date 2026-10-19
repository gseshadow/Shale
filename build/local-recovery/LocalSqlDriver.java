import java.sql.*;
import java.util.*;
import java.util.logging.Logger;
import com.microsoft.sqlserver.jdbc.SQLServerDriver;

/** Local-only TLS adapter: the historical DataSources class unconditionally supplies an
 * Azure hostname and trustServerCertificate=false. No production class is patched. */
public final class LocalSqlDriver implements Driver {
    private final String expected;
    private final SQLServerDriver delegate=new SQLServerDriver();
    private LocalSqlDriver(String expected){this.expected=expected;}
    public static void install(String expected)throws Exception{
        if(!expected.matches("jdbc:sqlserver://127\\.0\\.0\\.1:[0-9]+;databaseName=ShaleRetirementExercise_[a-f0-9]{32};encrypt=true;trustServerCertificate=true;loginTimeout=5;socketTimeout=20000"))throw new IllegalArgumentException("Exact LOCAL JDBC URL required");
        // Force discovery first, then remove alternate SQL Server URL fallbacks in this child.
        Class.forName(SQLServerDriver.class.getName());var drivers=Collections.list(DriverManager.getDrivers());
        for(Driver driver:drivers)if(driver instanceof SQLServerDriver||driver instanceof LocalSqlDriver)DriverManager.deregisterDriver(driver);
        DriverManager.registerDriver(new LocalSqlDriver(expected));
    }
    @Override public boolean acceptsURL(String url){return expected.equals(url);}
    @Override public Connection connect(String url,Properties properties)throws SQLException{
        if(!acceptsURL(url))return null;
        var local=new Properties();local.putAll(properties);local.setProperty("encrypt","true");local.setProperty("trustServerCertificate","true");local.setProperty("hostNameInCertificate","127.0.0.1");
        return delegate.connect(url,local);
    }
    @Override public DriverPropertyInfo[] getPropertyInfo(String url,Properties properties)throws SQLException{return delegate.getPropertyInfo(url,properties);}
    @Override public int getMajorVersion(){return delegate.getMajorVersion();}
    @Override public int getMinorVersion(){return delegate.getMinorVersion();}
    @Override public boolean jdbcCompliant(){return delegate.jdbcCompliant();}
    @Override public Logger getParentLogger(){return Logger.getLogger("local-sandbox-sql");}
}
