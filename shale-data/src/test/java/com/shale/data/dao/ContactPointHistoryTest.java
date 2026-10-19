package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ContactPointHistoryTest {
    @Test void onlyTenantOwnedPersistedPrimaryHistoryAuthorizesRestoration()throws Exception {
        for(String metadata:Arrays.asList(null,"{}","{\"PRIMARY\":\"false\"}","{\"PRIMARY\":\"true\"}")) {
            Map<Integer,Object> bindings=new HashMap<>();String[] sql={null};boolean[] next={true};
            ResultSet rows=(ResultSet)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{ResultSet.class},(p,m,a)->switch(m.getName()){case "next"->{boolean value=next[0];next[0]=false;yield value;}case "getString"->metadata;default->null;});
            PreparedStatement statement=(PreparedStatement)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{PreparedStatement.class},(p,m,a)->{if(m.getName().startsWith("set")){bindings.put((Integer)a[0],a[1]);return null;}return m.getName().equals("executeQuery")?rows:null;});
            Connection connection=(Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)->{if(m.getName().equals("prepareStatement")){sql[0]=(String)a[0];return statement;}return null;});
            assertEquals(metadata!=null&&metadata.contains("true"),ContactPointHistory.wasPrimary(connection,42,"CONTACT_PHONE_NUMBER",10,"CONTACT",7));
            assertEquals(Map.of(1,42,2,"CONTACT_PHONE_NUMBER",3,10L,4,"CONTACT",5,7L),bindings);
            assertTrue(sql[0].contains("ORDER BY Id DESC"));assertTrue(sql[0].contains("ParentEntityId=?"));
        }
    }
}
