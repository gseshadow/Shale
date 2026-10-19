import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.Calendar;
import com.shale.data.util.UtcJdbcTimestamp;

/** Offline compatibility linkage/boundary check; no SQL, credentials, or application startup. */
public final class SessionUpdateChecks {
    public static void main(String[] args) throws Exception {
        if(Runtime.version().feature()!=21 || args.length!=1)throw new IllegalArgumentException("Explicit JDK 21 classes path required");
        Path expected=Path.of(args[0]).toRealPath();
        for(String name:new String[]{"com.shale.data.util.UtcJdbcTimestamp","com.shale.data.dao.UserSessionDao","com.shale.server.runtime.SqlRememberCredentialStore","com.shale.server.runtime.SessionManagementService"}){
            Class<?> type=Class.forName(name);
            Path actual=Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
            if(!actual.equals(expected))throw new AssertionError("Compatibility class precedence mismatch: "+name);
        }
        Instant now=Instant.parse("2026-11-01T08:30:00Z");
        PreparedStatement statement=(PreparedStatement)Proxy.newProxyInstance(SessionUpdateChecks.class.getClassLoader(),new Class<?>[]{PreparedStatement.class},(p,m,a)->{
            if(!m.getName().equals("setTimestamp")||a.length!=3||!((Calendar)a[2]).getTimeZone().getID().equals("UTC")||!((Timestamp)a[1]).toInstant().equals(now))throw new AssertionError("UTC bind missing");
            return null;
        });
        ResultSet rows=(ResultSet)Proxy.newProxyInstance(SessionUpdateChecks.class.getClassLoader(),new Class<?>[]{ResultSet.class},(p,m,a)->{
            if(!m.getName().equals("getTimestamp")||a.length!=2||!((Calendar)a[1]).getTimeZone().getID().equals("UTC"))throw new AssertionError("UTC read missing");
            return Timestamp.from(now);
        });
        UtcJdbcTimestamp.set(statement,1,now);
        if(!UtcJdbcTimestamp.get(rows,1).toInstant().equals(now)||!UtcJdbcTimestamp.get(rows,"ExpiresAt").toInstant().equals(now))throw new AssertionError("Instant changed");
        System.out.println("PASS JDK21 pinned compatibility linkage/class precedence and explicit UTC bind/read. JVM zone="+java.util.TimeZone.getDefault().getID()+". SQL/DPAPI/native Windows acceptance not implied.");
    }
}
