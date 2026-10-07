package com.shale.data.runtime;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class RuntimeSessionServiceTimingTest {
    @Test void contextIsStampedBeforeReturningAndSetupFailureReleasesConnection() throws Exception {
        for (boolean fail : List.of(false, true)) {
            var operations = new ArrayList<String>();
            Connection connection = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class}, (p,m,a) -> {
                        if (m.getName().equals("close")) { operations.add("close"); return null; }
                        if (m.getName().equals("prepareStatement")) {
                            String sql=(String)a[0];
                            return Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(), new Class<?>[]{PreparedStatement.class},
                                    (sp,sm,sa) -> {
                                        if (sm.getName().equals("setInt")) operations.add(sql.contains("ShaleClientId") ? "tenant="+sa[1] : "actor="+sa[1]);
                                        if (sm.getName().equals("execute")) {
                                            if (fail && sql.contains("PrincipalUserId")) throw new SQLException("Unavailable");
                                            return false;
                                        }
                                        return null;
                                    });
                        }
                        return null;
                    });
            DataSource ds=(DataSource)Proxy.newProxyInstance(DataSource.class.getClassLoader(), new Class<?>[]{DataSource.class},
                    (p,m,a)->connection);
            var service = new RuntimeSessionService(ds);
            assertThrows(IllegalStateException.class, service::getConnection, "Never acquire before runtime initialization");
            service.initialize(7,11);
            if (fail) {
                assertThrows(SQLException.class, service::getConnection);
                assertEquals(List.of("tenant=7","actor=11","close"),operations,
                        "A connection with partial tenant setup must be released rather than leaked");
            } else {
                assertSame(connection, service.getConnection());
                assertEquals(List.of("tenant=7","actor=11"),operations);
            }
        }
    }
}
