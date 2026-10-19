package com.shale.data.dao;

import java.sql.Connection;
import java.sql.SQLException;

/** Trusted, tenant-owned structural history; never accepts primary-history claims from a client. */
final class ContactPointHistory {
    private ContactPointHistory() {}
    static boolean wasPrimary(Connection connection,int tenant,String entityType,long id,String parentType,long parentId)throws SQLException {
        try(var statement=connection.prepareStatement("SELECT TOP(1) Metadata FROM dbo.EntityActionAuditLog WHERE ShaleClientId=? AND EntityType=? AND EntityId=? AND ParentEntityType=? AND ParentEntityId=? AND Action IN('CREATED','UPDATED','RESTORED','REORDERED') ORDER BY Id DESC")) {
            statement.setInt(1,tenant);statement.setString(2,entityType);statement.setLong(3,id);statement.setString(4,parentType);statement.setLong(5,parentId);
            try(var rows=statement.executeQuery()){return rows.next()&&"true".equalsIgnoreCase(EntityActionAuditDao.parseSafeMetadata(rows.getString(1)).get("PRIMARY"));}
        }
    }
}
