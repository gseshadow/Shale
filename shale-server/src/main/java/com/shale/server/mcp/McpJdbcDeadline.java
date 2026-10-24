package com.shale.server.mcp;

import javax.sql.DataSource;
import java.sql.*;
import java.lang.reflect.*;
import java.util.logging.Logger;

/** JDBC-only decoration, never tool dispatch. The request deadline travels explicitly in each lease.
 * Uses the existing runtime pool with Hikari 5.1's bounded borrow; ordinary pool settings are untouched. */
final class McpJdbcDeadline implements DataSource {
    private final DataSource source;
    private final long deadline;
    private final boolean independentAudit;
    McpJdbcDeadline(DataSource source,long deadline) { this(source,deadline,false); }
    private McpJdbcDeadline(DataSource source,long deadline,boolean independentAudit) {
        this.source=source;this.deadline=deadline;this.independentAudit=independentAudit;
    }
    static DataSource failureAudit(DataSource source) { return new McpJdbcDeadline(source,0,true); }
    private int remaining() throws SQLException {
        long nanos=deadline-System.nanoTime();
        if(nanos<=0 || Thread.currentThread().isInterrupted())throw new SQLTimeoutException("MCP deadline.");
        return (int)Math.max(1,Math.min(10000,nanos/1_000_000));
    }
    @Override public Connection getConnection()throws SQLException {
        if(independentAudit)return new McpJdbcDeadline(source,System.nanoTime()+3_000_000_000L).getConnection();
        Connection connection;
        int budget=remaining();
        if(source instanceof com.zaxxer.hikari.HikariDataSource pool) {
            if(!(pool.getHikariPoolMXBean() instanceof com.zaxxer.hikari.pool.HikariPool bounded))
                throw new SQLException("Bounded runtime pool unavailable.");
            connection=bounded.getConnection(budget);
        } else connection=source.getConnection(); // Dedicated bootstrap has a fixed two-second login bound.
        try {
            int previous=connection.getNetworkTimeout();
            connection.setNetworkTimeout(Runnable::run,remaining());
            return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)->{
                if(method.getName().equals("close")) {
                    try {connection.setNetworkTimeout(Runnable::run,previous);}
                    finally {connection.close();}return null;
                }
                if(method.getDeclaringClass()==Object.class)return objectMethod(proxy,method,args);
                connection.setNetworkTimeout(Runnable::run,remaining());
                Object result=invoke(connection,method,args);
                if(result instanceof Statement statement) return statement(connection,statement);
                return result;
            });
        } catch(SQLException|RuntimeException failure) {try{connection.close();}catch(SQLException ignored){}throw failure;}
    }
    private Object statement(Connection connection,Statement statement) {
        Class<?> type=statement instanceof CallableStatement?CallableStatement.class:statement instanceof PreparedStatement?PreparedStatement.class:Statement.class;
        return Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(proxy,method,args)->{
            if(method.getName().equals("close"))return invoke(statement,method,args);
            if(method.getDeclaringClass()==Object.class)return objectMethod(proxy,method,args);
            int budget=remaining();connection.setNetworkTimeout(Runnable::run,budget);
            if(method.getName().equals("setQueryTimeout"))args=new Object[]{Math.min((Integer)args[0],Math.max(1,Math.min(5,budget/1000)))};
            Object result=invoke(statement,method,args);
            if(result instanceof ResultSet rows) return Proxy.newProxyInstance(ResultSet.class.getClassLoader(),new Class<?>[]{ResultSet.class},(p,m,a)->{
                if(m.getDeclaringClass()==Object.class)return objectMethod(p,m,a);
                if(!m.getName().equals("close"))connection.setNetworkTimeout(Runnable::run,remaining());
                return invoke(rows,m,a);
            });
            return result;
        });
    }
    private static Object invoke(Object target,Method method,Object[] args)throws Throwable {
        try{return method.invoke(target,args);}catch(InvocationTargetException failure){throw failure.getCause();}
    }
    private static Object objectMethod(Object proxy,Method method,Object[] args) {
        return switch(method.getName()){case "toString"->"McpJdbcLease[redacted]";case "hashCode"->System.identityHashCode(proxy);case "equals"->proxy==args[0];default->throw new IllegalStateException();};
    }
    @Override public Connection getConnection(String user,String password)throws SQLException {throw new SQLException("Configured identity only.");}
    @Override public java.io.PrintWriter getLogWriter()throws SQLException {return null;}
    @Override public void setLogWriter(java.io.PrintWriter writer)throws SQLException {throw new SQLException("No JDBC payload logging.");}
    @Override public void setLoginTimeout(int seconds)throws SQLException {throw new SQLException("Operator configuration only.");}
    @Override public int getLoginTimeout()throws SQLException {return source.getLoginTimeout();}
    @Override public Logger getParentLogger()throws SQLFeatureNotSupportedException {throw new SQLFeatureNotSupportedException();}
    @Override public <T>T unwrap(Class<T> type)throws SQLException {throw new SQLException("No unbounded lease escape.");}
    @Override public boolean isWrapperFor(Class<?> type) {return false;}
}
