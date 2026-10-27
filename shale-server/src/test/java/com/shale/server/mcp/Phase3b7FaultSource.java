package com.shale.server.mcp;

import javax.sql.DataSource;
import java.sql.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Test-only JDBC fault injection after live startup/request attestation, before the real protected RPC.
 * SQL mutations are exact task-owned temporary triggers, removed before bounded failure cleanup.
 * No parameter capture, credential logging, module edits or runtime production hooks. */
public final class Phase3b7FaultSource implements DataSource {
    @FunctionalInterface public interface Owner {Connection open()throws Exception;}
    private DataSource source;private final Owner owner;
    private final AtomicReference<String> mode=new AtomicReference<>("");
    private final ConcurrentMap<String,AtomicInteger> calls=new ConcurrentHashMap<>(),waits=new ConcurrentHashMap<>();
    public Phase3b7FaultSource(DataSource source,Owner owner){this.source=source;this.owner=owner;}
    public DataSource overPool(DataSource pool){if(!(pool instanceof com.zaxxer.hikari.HikariDataSource))throw new IllegalArgumentException("Actual dedicated pool required");this.source=pool;return this;}
    public void mode(String value){mode.set(value);}
    public Map<String,Integer> calls(){var out=new TreeMap<String,Integer>();calls.forEach((k,v)->out.put(k,v.get()));return out;}
    public Map<String,Integer> waits(){var out=new TreeMap<String,Integer>();waits.forEach((k,v)->out.put(k,v.get()));return out;}
    @Override public Connection getConnection()throws SQLException {
        Connection c=source.getConnection();var read=new AtomicBoolean();
        return proxy(Connection.class,(p,m,a)->{
            if(m.getName().equals("abort")&&source instanceof com.zaxxer.hikari.HikariDataSource pool){pool.evictConnection(c);return null;}
            if(m.getName().equals("close")&&read.get()&&mode.get().equals("close")){c.close();throw new SQLException("Synthetic close failure");}
            Object value=invoke(c,m,a);
            if(m.getName().equals("prepareCall")&&value instanceof CallableStatement statement) {
                String sql=(String)a[0];boolean protectedRead=sql.contains("AiDataSearchCasesV1")||sql.contains("AiDataGetCaseOverviewV1");
                read.set(protectedRead);
                return proxy(CallableStatement.class,(sp,sm,sa)->{
                    if(!sm.getName().equals("execute"))return invoke(statement,sm,sa);
                    String fault=mode.get();
                    if(sql.contains("AiDataAuthorizeCaseHandoffV1")&&!fault.isEmpty())calls.computeIfAbsent(fault+":handoff",k->new AtomicInteger()).incrementAndGet();
                    if(sql.contains("AiDataRecordCaseFailureV1")&&Set.of("failure-audit","cleanup").contains(fault))throw new SQLException("Synthetic failure-audit outage");
                    if(sql.contains("AiDataFinishCaseQuotaV1")&&fault.equals("cleanup"))throw new SQLException("Synthetic quota release outage");
                    if(!protectedRead||fault.isEmpty())return invoke(statement,sm,sa);
                    calls.computeIfAbsent(fault,k->new AtomicInteger()).incrementAndGet();
                    if(fault.equals("rejected-task")){var executor=Executors.newSingleThreadExecutor();executor.shutdown();executor.submit(()->{throw new AssertionError("Rejected task must never run");});}
                    if(fault.equals("cancel-before")){Thread.currentThread().interrupt();throw new SQLTimeoutException("Synthetic cancellation before SQL");}
                    String target=switch(fault){case "parent","commit","cancel-during","timeout"->"IntegrationAccessAuditLog";case "child"->"IntegrationAccessAuditResource";case "phi","failure-audit"->"auditLog";case "quota"->"AiQuotaReservation";default->null;};
                    String trigger="AcceptanceFault_"+UUID.randomUUID().toString().replace("-","");
                    boolean delayed=Set.of("commit","cancel-during","timeout").contains(fault);
                    CompletableFuture<Void> observer=null;
                    try {
                        if(target!=null)try(var setup=owner.open()) {exec(setup,"CREATE TRIGGER dbo."+trigger+" ON dbo."+target+" AFTER INSERT AS BEGIN SET NOCOUNT ON; "+(delayed?"WAITFOR DELAY '00:00:10';":"THROW 59999,'Synthetic serving fault',1;")+" END");}
                        if(fault.equals("commit")||fault.equals("cancel-during")) {
                            int spid;try(var q=c.createStatement();var r=q.executeQuery("SELECT @@SPID")){r.next();spid=r.getInt(1);}
                            observer=CompletableFuture.runAsync(()->{
                                try(var setup=owner.open()) {
                                    boolean observed=false;long until=System.nanoTime()+4_000_000_000L;
                                    while(System.nanoTime()<until) {
                                        try(var q=setup.prepareStatement("SELECT COUNT(*) FROM sys.dm_exec_requests WHERE session_id=? AND database_id=DB_ID() AND wait_type='WAITFOR'")){q.setInt(1,spid);try(var r=q.executeQuery()){r.next();if(r.getInt(1)==1){observed=true;break;}}}
                                        Thread.sleep(10);
                                    }
                                    if(!observed)throw new AssertionError("Positive SQL WAITFOR observation required before cancellation/disconnect");
                                    waits.computeIfAbsent(fault,k->new AtomicInteger()).incrementAndGet();
                                    if(fault.equals("commit"))exec(setup,"KILL "+spid);else statement.cancel();
                                }catch(Exception e){throw new CompletionException(e);}
                            });
                        }
                        Object result=invoke(statement,sm,sa);
                        if(fault.equals("lost-ack")||fault.equals("cleanup"))throw new SQLException("Synthetic lost acknowledgment after actual SQL COMMIT","08006");
                        if(fault.equals("interruption"))Thread.currentThread().interrupt();
                        return result;
                    } finally {
                        try {if(observer!=null)observer.get(6,TimeUnit.SECONDS);}
                        finally {if(target!=null)try(var setup=owner.open()){exec(setup,"DROP TRIGGER dbo."+trigger);}}
                    }
                });
            }
            return value;
        });
    }
    private static void exec(Connection c,String text)throws SQLException {try(var s=c.createStatement()){s.setQueryTimeout(10);s.execute(text);}}
    private static Object invoke(Object target,Method method,Object[] args)throws Throwable {try{return method.invoke(target,args);}catch(InvocationTargetException e){throw e.getCause();}}
    @SuppressWarnings("unchecked")private static <T>T proxy(Class<T> type,InvocationHandler h){return(T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},h);}
    @Override public Connection getConnection(String user,String password)throws SQLException{throw new SQLException("Configured fixture identity only");}
    @Override public java.io.PrintWriter getLogWriter(){return null;}
    @Override public void setLogWriter(java.io.PrintWriter writer)throws SQLException{throw new SQLException("No parameter logging");}
    @Override public void setLoginTimeout(int n)throws SQLException{source.setLoginTimeout(n);}
    @Override public int getLoginTimeout()throws SQLException{return source.getLoginTimeout();}
    @Override public java.util.logging.Logger getParentLogger()throws SQLFeatureNotSupportedException{throw new SQLFeatureNotSupportedException();}
    @Override public <T>T unwrap(Class<T> type)throws SQLException{throw new SQLException("No identity escape");}
    @Override public boolean isWrapperFor(Class<?> type){return false;}
}
