package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.sql.Types;

import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetMetaDataImpl;
import javax.sql.rowset.RowSetProvider;

import com.shale.core.dto.TaskDetailDto;

import org.junit.jupiter.api.Test;

final class TaskDaoTaskDetailQueryTest {

    @Test
    void taskDetailUsesDirectCreatedByUserJoinForDisplayName() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/TaskDao.java"));
        String method = source.substring(
                source.indexOf("public TaskDetailDto findTaskDetail"),
                source.indexOf("public List<TaskAssignedUserRow> listAssignedUsersForTask"));

        assertTrue(method.contains("LEFT JOIN dbo.Users createdByUser"));
        assertTrue(method.contains("createdByUser.name_first"));
        assertTrue(method.contains("AS CreatedByDisplayName"));
        assertTrue(method.contains("current_status.CurrentStatusName AS CasePrimaryStatusName"));
        assertTrue(method.contains("current_status.PrimaryStatusColor AS CasePrimaryStatusColor"));
        assertTrue(method.contains("pa.Color AS CasePracticeAreaColor"));
        assertFalse(method.contains(") creator"),
                "Task detail must not use the prior creator OUTER APPLY alias for created-by display names");
    }

    @Test
    void taskDetailHydrationUsesSafeNullableTypeReaders() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/TaskDao.java"));
        String method = source.substring(
                source.indexOf("public TaskDetailDto findTaskDetail"),
                source.indexOf("public List<TaskAssignedUserRow> listAssignedUsersForTask"));

        assertTrue(method.contains("getNullableBoolean(rs, \"CaseNonEngagementLetterSent\")"));
        assertTrue(method.contains("getNullableInt(rs, \"StatusId\")"));
        assertTrue(method.contains("getNullableInt(rs, \"PriorityId\")"));
        assertTrue(method.contains("getNullableInt(rs, \"AssignedUserId\")"));
        assertFalse(method.contains("(Integer) rs.getObject"));
        assertFalse(method.contains("(Boolean) rs.getObject"));
    }

    @Test
    void taskDetailMapperReadsHydratedCaseCardColumnsFromResultSet() throws Exception {
        RowSetMetaDataImpl metadata = new RowSetMetaDataImpl();
        String[] columns = {
                "Id", "ShaleClientId", "CaseId", "CaseName", "CaseResponsibleAttorney",
                "CaseResponsibleAttorneyColor", "CaseNonEngagementLetterSent", "CasePrimaryStatusName",
                "CasePrimaryStatusColor", "CasePracticeAreaColor", "Title", "Description", "DueAt",
                "StatusId", "PriorityId", "CompletedAt", "AssignedUserId", "AssignedUserDisplayName",
                "AssignedUserColor", "CreatedByDisplayName"
        };
        metadata.setColumnCount(columns.length);
        for (int i = 0; i < columns.length; i++) {
            metadata.setColumnName(i + 1, columns[i]);
            metadata.setColumnLabel(i + 1, columns[i]);
            metadata.setColumnType(i + 1, Types.VARCHAR);
        }
        metadata.setColumnType(1, Types.BIGINT);
        metadata.setColumnType(2, Types.INTEGER);
        metadata.setColumnType(3, Types.BIGINT);
        metadata.setColumnType(7, Types.BOOLEAN);
        metadata.setColumnType(13, Types.TIMESTAMP);
        metadata.setColumnType(14, Types.INTEGER);
        metadata.setColumnType(15, Types.INTEGER);
        metadata.setColumnType(16, Types.TIMESTAMP);
        metadata.setColumnType(17, Types.INTEGER);

        CachedRowSet rowSet = RowSetProvider.newFactory().createCachedRowSet();
        rowSet.setMetaData(metadata);
        rowSet.moveToInsertRow();
        rowSet.updateLong("Id", 10L);
        rowSet.updateInt("ShaleClientId", 7);
        rowSet.updateLong("CaseId", 501L);
        rowSet.updateString("CaseName", "Smith v. Example");
        rowSet.updateString("CaseResponsibleAttorney", "Ada Attorney");
        rowSet.updateString("CaseResponsibleAttorneyColor", "#AA5500");
        rowSet.updateBoolean("CaseNonEngagementLetterSent", false);
        rowSet.updateString("CasePrimaryStatusName", "Open");
        rowSet.updateString("CasePrimaryStatusColor", "#22AA55");
        rowSet.updateString("CasePracticeAreaColor", "#004488");
        rowSet.updateString("Title", "Review records");
        rowSet.updateString("Description", "Read intake packet");
        rowSet.updateTimestamp("DueAt", Timestamp.valueOf("2026-01-02 12:00:00"));
        rowSet.updateInt("StatusId", 2);
        rowSet.updateInt("PriorityId", 1);
        rowSet.updateNull("CompletedAt");
        rowSet.updateInt("AssignedUserId", 31);
        rowSet.updateString("AssignedUserDisplayName", "Ada Attorney");
        rowSet.updateString("AssignedUserColor", "#AA5500");
        rowSet.updateString("CreatedByDisplayName", "Case Creator");
        rowSet.insertRow();
        rowSet.moveToCurrentRow();
        rowSet.beforeFirst();
        assertTrue(rowSet.next());

        TaskDetailDto detail = TaskDao.mapTaskDetail(rowSet);

        assertEquals("Open", detail.casePrimaryStatusName());
        assertEquals("#22AA55", detail.casePrimaryStatusColor());
        assertEquals("#004488", detail.casePracticeAreaColor());
    }

}
