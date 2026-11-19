package com.shale.data.security.ai;

import com.shale.data.config.AcceptanceVerificationBudget;
import java.sql.*;
import java.util.*;
import java.util.function.Function;

/** Fresh, independent inventory and caller-identity checks with bounded transport batches. */
final class AiObjectAuthorityAttestation {
    private static final int OBJECT_BATCH=64, COLUMN_BATCH=256;
    private static final Set<String> TYPES=Set.of("U","V","P","FN","IF","TF","SN","FS","FT","PC");
    private static final Set<String> METADATA_READS=Set.of("AiCaseDataDeployment","AiDataUnicodeRange","AiDataUnicodeLower","AiDataWordTransition");
    private record ObjectColumns(String schema,String name,String type,List<String> columns) {
        String qualified(){return quote(schema)+"."+quote(name);}
    }
    private record Column(String object,String name) {}
    private AiObjectAuthorityAttestation() {}

    static void verify(Connection caller,Connection review,Set<String> roots,boolean checkReviewer)throws SQLException {
        var inventory=inventory(review);
        var permissions=permissions(caller,inventory,o->o.schema.equals("dbo")&&roots.contains(o.name)?Set.of("EXECUTE"):Set.of());
        var executable=new HashSet<String>();
        for(var object:inventory)if(permissions.get(object.qualified()).contains("EXECUTE"))executable.add(object.name);
        if(!executable.equals(roots))throw refused();
        if(checkReviewer)permissions(review,inventory,o->o.schema.equals("dbo")&&METADATA_READS.contains(o.name)?Set.of("SELECT","VIEW DEFINITION"):Set.of("VIEW DEFINITION"));
        var batch=new ArrayList<Column>(COLUMN_BATCH);
        for(var object:inventory)for(String column:object.columns) {
            batch.add(new Column(object.qualified(),column));
            if(batch.size()==COLUMN_BATCH){columns(caller,batch);batch.clear();}
        }
        if(!batch.isEmpty())columns(caller,batch);
    }

    private static List<ObjectColumns> inventory(Connection review)throws SQLException {
        var objects=new LinkedHashMap<String,ObjectColumns>();
        var seen=new HashMap<String,Set<String>>();
        var empty=new HashSet<String>();
        // LEFT JOIN retains objects with no columns; caller-filtered visibility is never used.
        String sql="SELECT s.name,o.name,RTRIM(o.type),c.name FROM sys.objects o JOIN sys.schemas s ON s.schema_id=o.schema_id "
                +"LEFT JOIN sys.columns c ON c.object_id=o.object_id WHERE o.is_ms_shipped=0 AND o.type IN('U','V','P','FN','IF','TF','SN','FS','FT','PC')";
        try(var statement=review.createStatement()) {
            statement.setQueryTimeout(AcceptanceVerificationBudget.querySeconds(3));
            try(var rows=statement.executeQuery(sql)) {
                while(rows.next()) {
                    String schema=rows.getString(1),name=rows.getString(2),type=rows.getString(3),column=rows.getString(4);
                    if(schema==null||name==null||type==null||!TYPES.contains(type))throw refused();
                    String key=quote(schema)+"."+quote(name);
                    var object=objects.computeIfAbsent(key,k->new ObjectColumns(schema,name,type,new ArrayList<>()));
                    if(!object.type.equals(type))throw refused();
                    var names=seen.computeIfAbsent(key,k->new HashSet<>());
                    if(column==null) {if(!names.isEmpty()||!empty.add(key))throw refused();}
                    else {if(empty.contains(key)||!names.add(column))throw refused();object.columns.add(column);}
                }
            }
        }
        return List.copyOf(objects.values());
    }

    private static Map<String,Set<String>> permissions(Connection identity,List<ObjectColumns> inventory,
                                                       Function<ObjectColumns,Set<String>> expected)throws SQLException {
        var actual=new HashMap<String,Set<String>>();
        for(int start=0;start<inventory.size();start+=OBJECT_BATCH) {
            var batch=inventory.subList(start,Math.min(start+OBJECT_BATCH,inventory.size()));
            var sql=new StringBuilder();
            for(int index=0;index<batch.size();index++) {
                // 15151 is isolated to this object. A batch error must never imply no grants elsewhere.
                sql.append("BEGIN TRY SELECT ").append(index)
                   .append(" AS ObjectOrdinal,p.permission_name,CAST(0 AS int) AS Invisible FROM (VALUES(0)) AS v(Anchor) OUTER APPLY sys.fn_my_permissions(?,'OBJECT') p; END TRY BEGIN CATCH IF ERROR_NUMBER()<>15151 THROW; SELECT ")
                   .append(index).append(" AS ObjectOrdinal,CAST(NULL AS nvarchar(128)) AS permission_name,CAST(1 AS int) AS Invisible; END CATCH; ");
            }
            try(var query=identity.prepareStatement(sql.toString())) {
                query.setQueryTimeout(AcceptanceVerificationBudget.querySeconds(3));
                for(int i=0;i<batch.size();i++)query.setString(i+1,batch.get(i).qualified());
                boolean more=query.execute();
                for(int index=0;index<batch.size();index++) {
                    if(!more)throw refused();
                    var object=batch.get(index);var allow=expected.apply(object);var found=new HashSet<String>();
                    boolean sentinel=false;int count=0;
                    try(var rows=query.getResultSet()) {
                        while(rows.next()) {
                            int ordinal=rows.getInt(1);if(rows.wasNull()||ordinal!=index)throw refused();
                            String permission=rows.getString(2);
                            int invisible=rows.getInt(3);if(rows.wasNull()||invisible<0||invisible>1)throw refused();
                            count++;
                            if(permission==null) {
                                if(count!=1||invisible==1&&!allow.isEmpty())throw refused();sentinel=true;
                            } else {
                                boolean implied=allow.contains("VIEW DEFINITION")&&Set.of("VIEW SECURITY DEFINITION","VIEW PERFORMANCE DEFINITION").contains(permission);
                                if(sentinel||invisible!=0||!allow.contains(permission)&&!implied)throw refused();
                                found.add(permission);
                            }
                        }
                    }
                    if(count==0||sentinel&&count!=1)throw refused();
                    actual.put(object.qualified(),Set.copyOf(found));
                    more=query.getMoreResults(Statement.CLOSE_CURRENT_RESULT);
                }
                if(more||query.getUpdateCount()!=-1)throw refused();
            }
        }
        return actual;
    }

    private static void columns(Connection caller,List<Column> batch)throws SQLException {
        String sql="SELECT v.ObjectName,v.ColumnName,HAS_PERMS_BY_NAME(v.ObjectName,'OBJECT','SELECT',v.ColumnName,'COLUMN'),"
                +"HAS_PERMS_BY_NAME(v.ObjectName,'OBJECT','UPDATE',v.ColumnName,'COLUMN') FROM (VALUES "
                +String.join(",",Collections.nCopies(batch.size(),"(?,?)"))+") AS v(ObjectName,ColumnName)";
        try(var query=caller.prepareStatement(sql)) {
            query.setQueryTimeout(AcceptanceVerificationBudget.querySeconds(3));
            for(int i=0;i<batch.size();i++){query.setString(i*2+1,batch.get(i).object);query.setString(i*2+2,batch.get(i).name);}
            var remaining=new HashSet<>(batch);
            try(var rows=query.executeQuery()) {
                while(rows.next())if(!remaining.remove(new Column(rows.getString(1),rows.getString(2)))||rows.getInt(3)==1||rows.getInt(4)==1)throw refused();
            }
            if(!remaining.isEmpty())throw refused();
        }
    }
    private static String quote(String value){return "["+value.replace("]","]]")+"]";}
    private static IllegalStateException refused(){return new IllegalStateException("Object authority verification failed closed.");}
}
