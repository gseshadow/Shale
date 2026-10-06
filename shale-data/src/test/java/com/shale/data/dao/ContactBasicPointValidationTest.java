package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ContactBasicPointValidationTest {
    private record Write(String sql,Map<Integer,Object> bindings){}
    private static Object defaultValue(Class<?> type){if(type==boolean.class)return false;if(type==int.class)return 0;if(type==long.class)return 0L;return null;}
    private static ResultSet rows(Object... values){boolean[] next={values.length>0};return (ResultSet)Proxy.newProxyInstance(ContactBasicPointValidationTest.class.getClassLoader(),new Class<?>[]{ResultSet.class},(p,m,a)->{if(m.getName().equals("next")){boolean found=next[0];next[0]=false;return found;}if(m.getName().startsWith("get")&&a!=null&&a[0] instanceof Integer i){Object value=values[i-1];if(m.getName().equals("getString"))return value==null?null:value.toString();return value;}return defaultValue(m.getReturnType());});}
    private static Connection connection(String original,String originalExtension,List<Write> writes,List<Write> reads){return (Connection)Proxy.newProxyInstance(ContactBasicPointValidationTest.class.getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)->{
        if(!m.getName().equals("prepareStatement"))return defaultValue(m.getReturnType());String sql=(String)a[0];Map<Integer,Object> bindings=new HashMap<>();
        return Proxy.newProxyInstance(ContactBasicPointValidationTest.class.getClassLoader(),new Class<?>[]{PreparedStatement.class},(ps,method,args)->{
            if(method.getName().startsWith("set")){bindings.put((Integer)args[0],method.getName().equals("setNull")?null:args[1]);return null;}
            if(method.getName().equals("executeQuery")){reads.add(new Write(sql,new HashMap<>(bindings)));if(sql.startsWith("SELECT TOP(1)"))return rows(15L,original,new byte[]{1},true,0,originalExtension,"WORK");if(sql.contains("PrincipalUserId"))return rows(9);if(sql.startsWith("SELECT Id,Kind"))return rows();return rows(1);}
            if(method.getName().equals("executeUpdate")){writes.add(new Write(sql,new HashMap<>(bindings)));return 1;}
            return defaultValue(method.getReturnType());
        });
    });}
    private static void save(Connection c,String desired,String extension,boolean owned)throws Exception{
        var method=ContactDao.class.getDeclaredMethod("replaceBasicStructuredPoint",Connection.class,String.class,String.class,int.class,int.class,Integer.class,String.class,String.class,String.class,boolean.class);method.setAccessible(true);
        try{method.invoke(null,c,"ContactPhoneNumbers","DisplayNumber,NormalizedNumber,Extension",11,7,9,"MOBILE",desired,extension,owned);}catch(InvocationTargetException failure){if(failure.getCause() instanceof RuntimeException runtime)throw runtime;if(failure.getCause() instanceof SQLException sql)throw sql;throw failure;}
    }
    @Test void directBasicSaveRetainsInvalidLegacyWithoutRewritingOrAudit()throws Exception{
        List<Write>writes=new ArrayList<>(),reads=new ArrayList<>();save(connection("0",null,writes,reads),"0",null,false);
        assertTrue(writes.isEmpty());assertEquals(Map.of(1,7,2,11),reads.getFirst().bindings);assertTrue(reads.getFirst().sql.contains("ORDER BY IsPrimary DESC,SortOrder,Id"));assertFalse(reads.getFirst().sql.contains("Kind=?"));
    }
    @Test void changedOrCopiedInvalidValueFailsBeforeAnyPointWrite()throws Exception{
        List<Write>writes=new ArrayList<>(),reads=new ArrayList<>();assertThrows(IllegalArgumentException.class,()->save(connection("3035550123",null,writes,reads),"0",null,true));assertTrue(writes.isEmpty());
    }
    @Test void extensionChangesUpdateOnlyTheOwnedPointWithCanonicalNumberAndSafeAudit()throws Exception{
        List<Write>writes=new ArrayList<>(),reads=new ArrayList<>();save(connection("3035550123","000",writes,reads),"3035550123","001",true);
        assertEquals(2,writes.size());var update=writes.getFirst();assertTrue(update.sql.startsWith("UPDATE dbo.ContactPhoneNumbers"));assertTrue(update.sql.contains("AND ContactId=? AND RowVer=?"));assertEquals("+13035550123",update.bindings.get(2));assertEquals("001",update.bindings.get(3));assertEquals(15L,update.bindings.get(5));assertEquals(7,update.bindings.get(6));assertEquals(11,update.bindings.get(7));assertArrayEquals(new byte[]{1},(byte[])update.bindings.get(8));
        String metadata=(String)writes.get(1).bindings.get(11);assertTrue(metadata.contains("WORK"));assertFalse(metadata.contains("3035550123"));assertFalse(metadata.contains("001"));
    }
    @Test void explicitClearSoftRemovesOnlyOwnedPointAndPreservesHistory()throws Exception{
        List<Write>writes=new ArrayList<>(),reads=new ArrayList<>();save(connection("0",null,writes,reads),null,null,true);var removal=writes.getFirst();assertTrue(removal.sql.contains("IsDeleted=1,IsPrimary=0,DeletedAt="));assertTrue(removal.sql.contains("WHERE Id=? AND ShaleClientId=? AND ContactId=? AND RowVer=?"));assertFalse(writes.stream().anyMatch(w->w.sql.startsWith("DELETE")||w.sql.startsWith("INSERT dbo.ContactPhoneNumbers")));
    }
    @Test void localSaveUsesSubscriberNormalizationAndSeparateExtensionWithoutNullBindings()throws Exception {
        List<Write> writes=new ArrayList<>(),reads=new ArrayList<>();
        save(connection("3035550123",null,writes,reads),"555-0123 x001",null,true);
        var update=writes.getFirst();
        assertEquals("555-0123",update.bindings.get(1));
        assertEquals("5550123",update.bindings.get(2));
        assertEquals("001",update.bindings.get(3));
        assertEquals(2,writes.size(),"point and existing transaction-bound audit both persist");
    }

}
