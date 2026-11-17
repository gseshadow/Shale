package com.shale.data.security.ai;

import com.shale.data.config.AcceptanceVerificationBudget;
import java.sql.*;
import java.util.*;

/** Check every independently inventoried column under the caller, without one network request per column. */
final class AiColumnPermissionAttestation {
    private static final int BATCH_SIZE=256;
    private AiColumnPermissionAttestation() {}
    static void verify(Connection caller,String object,ResultSet columns)throws SQLException {
        var seen=new HashSet<String>();
        var batch=new ArrayList<String>(BATCH_SIZE);
        while(columns.next()) {
            String name=columns.getString(1);
            if(name==null||!seen.add(name))throw refused();
            batch.add(name);
            if(batch.size()==BATCH_SIZE){check(caller,object,batch);batch.clear();}
        }
        if(!batch.isEmpty())check(caller,object,batch);
    }
    private static void check(Connection caller,String object,List<String> columns)throws SQLException {
        // Only placeholder count is generated; object/column names remain bound Unicode values.
        String sql="SELECT v.ColumnName,HAS_PERMS_BY_NAME(?,'OBJECT','SELECT',v.ColumnName,'COLUMN'),"
                +"HAS_PERMS_BY_NAME(?,'OBJECT','UPDATE',v.ColumnName,'COLUMN') FROM (VALUES "
                +String.join(",",Collections.nCopies(columns.size(),"(?)"))+") AS v(ColumnName)";
        try(var query=caller.prepareStatement(sql)) {
            query.setQueryTimeout(AcceptanceVerificationBudget.querySeconds(3));
            query.setString(1,object);query.setString(2,object);
            for(int i=0;i<columns.size();i++)query.setString(i+3,columns.get(i));
            var remaining=new HashSet<>(columns);
            try(var rows=query.executeQuery()) {
                while(rows.next()) {
                    if(!remaining.remove(rows.getString(1))||rows.getInt(2)==1||rows.getInt(3)==1)throw refused();
                }
            }
            if(!remaining.isEmpty())throw refused();
        }
    }
    private static IllegalStateException refused(){return new IllegalStateException("Column authority verification failed closed.");}
}
