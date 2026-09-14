package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CaseDateTimelineWriterTest {
    @Test
    void overviewDateActionsUseEstablishedDescriptionsAndContext() throws Exception {
        List<List<Object>> rows = new ArrayList<>();
        Connection con = connection(rows, 1);
        LocalDateTime oldDate = LocalDateTime.of(2026, 9, 2, 0, 0);
        LocalDateTime newDate = LocalDateTime.of(2026, 9, 9, 0, 0);

        CaseDateDao.appendDateTimeline(con,42,7,9,CaseTimelineWriter.CASE_DATE_CREATED,"Hearing",newDate,null,null,null);
        CaseDateDao.appendDateTimeline(con,42,7,9,CaseTimelineWriter.CASE_DATE_UPDATED,"Hearing",newDate,null,oldDate,null);
        CaseDateDao.appendDateTimeline(con,42,7,9,CaseTimelineWriter.CASE_DATE_REMOVED,"Hearing",newDate,null,null,null);
        CaseDateDao.appendDateTimeline(con,42,7,9,CaseTimelineWriter.CASE_DATE_RESTORED,"Hearing",newDate,null,null,null);

        assertEquals(4, rows.size());
        assertEquals(List.of("added Hearing", "changed Hearing", "removed Hearing", "restored Hearing"),
                rows.stream().map(row -> (String) row.get(5)).toList());
        assertEquals("from Sep 2, 2026 to Sep 9, 2026", rows.get(1).get(6));
        for (List<Object> row : rows) {
            assertEquals(42L,row.get(0)); assertEquals(7,row.get(1)); assertEquals(9,row.get(4));
        }
    }

    @Test
    void failedTimelinePersistenceFailsTheOwningMutationClosed() {
        assertThrows(SQLException.class, () -> CaseDateDao.appendDateTimeline(connection(new ArrayList<>(),0),
                42,7,9,CaseTimelineWriter.CASE_DATE_CREATED,"Hearing",LocalDateTime.now(),null,null,null));
    }

    private static Connection connection(List<List<Object>> rows,int affected) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},
                (proxy,method,args)->method.getName().equals("prepareStatement")?statement(rows,affected):defaultValue(method.getReturnType()));
    }
    private static PreparedStatement statement(List<List<Object>> rows,int affected) {
        List<Object> values=new ArrayList<>();
        return (PreparedStatement) Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),new Class<?>[]{PreparedStatement.class},
                (proxy,method,args)->{if(method.getName().startsWith("set")){int i=(Integer)args[0];while(values.size()<i)values.add(null);values.set(i-1,args[1]);}
                    if(method.getName().equals("executeUpdate")){rows.add(new ArrayList<>(values));return affected;}return defaultValue(method.getReturnType());});
    }
    private static Object defaultValue(Class<?> type){if(!type.isPrimitive())return null;if(type==boolean.class)return false;if(type==int.class)return 0;if(type==long.class)return 0L;return null;}
}
