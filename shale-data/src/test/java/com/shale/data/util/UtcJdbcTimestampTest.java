package com.shale.data.util;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class UtcJdbcTimestampTest {
    @Test void timestampBoundaryUsesFreshUtcCalendarsForEveryBindAndRead() throws Exception {
        var calendars=new ArrayList<Calendar>();
        Instant value=Instant.parse("2026-11-01T08:30:00.123456700Z");
        PreparedStatement statement=(PreparedStatement)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{PreparedStatement.class},(p,m,a)->{
            assertEquals("setTimestamp",m.getName());assertEquals(3,a.length,"writes require explicit timezone");
            assertEquals(Timestamp.from(value),a[1]);calendars.add((Calendar)a[2]);return null;
        });
        ResultSet rows=(ResultSet)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{ResultSet.class},(p,m,a)->{
            assertEquals("getTimestamp",m.getName());assertEquals(2,a.length,"reads require explicit timezone");
            calendars.add((Calendar)a[1]);return a[0].equals("nullable")?null:Timestamp.from(value);
        });
        UtcJdbcTimestamp.set(statement,1,value);UtcJdbcTimestamp.set(statement,2,value);
        assertEquals(value,UtcJdbcTimestamp.get(rows,1).toInstant());
        assertEquals(value,UtcJdbcTimestamp.get(rows,"ExpiresAt").toInstant());
        assertNull(UtcJdbcTimestamp.get(rows,"nullable"));
        for(int i=0;i<calendars.size();i++){
            assertEquals("UTC",calendars.get(i).getTimeZone().getID());
            for(int j=0;j<i;j++)assertNotSame(calendars.get(j),calendars.get(i),"mutable calendars must not be reused");
        }
    }
}
