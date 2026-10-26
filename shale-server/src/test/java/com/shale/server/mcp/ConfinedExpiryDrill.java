package com.shale.server.mcp;

import javax.sql.DataSource;
import java.lang.reflect.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exact final-gate SQL lock wait; no token/RPC parameter capture and no pre-gate clock evidence. */
public final class ConfinedExpiryDrill implements AutoCloseable {
    @FunctionalInterface public interface Owner { Connection open()throws Exception; }
    final Owner owner;final UUID session;final String event="AiMcpExpiry_"+UUID.randomUUID().toString().replace("-","");
    final AtomicBoolean injected=new AtomicBoolean();final CompletableFuture<Instant> released=new CompletableFuture<>();
    public ConfinedExpiryDrill(Owner owner,UUID session){this.owner=owner;this.session=session;}
    public DataSource wrap(DataSource source) {
        return (DataSource)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{DataSource.class},(p,m,a)-> {
            Object value=invoke(source,m,a);if(!m.getName().equals("getConnection"))return value;
            Connection c=(Connection)value;
            return Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},(cp,cm,ca)-> {
                Object result=invoke(c,cm,ca);
                if(cm.getName().equals("prepareCall")&&ca[0].toString().contains("AiDataAuthorizeCaseHandoffV1")&&!injected.get()) {
                    try(var observer=owner.open()) {
                        if(number(observer,"SELECT COUNT(*) FROM dbo.AiCaseDataInvocation WHERE SessionId='"+session+"' AND ReadEventId IS NOT NULL AND HandoffConsumed=0")>0&&injected.compareAndSet(false,true))block(c);
                    }
                }
                return result;
            });
        });
    }
    void block(Connection gate)throws Exception {
        int spid=number(gate,"SELECT @@SPID");Connection held=owner.open();int blocker=number(held,"SELECT @@SPID");
        try {
            sql(held,"BEGIN TRANSACTION; UPDATE dbo.AiDelegatedSession SET IdleExpiresAt=DATEADD(second,1,SYSUTCDATETIME()) WHERE SessionId='"+session+"'; SELECT 1 FROM dbo.AiDelegatedSession WITH(TABLOCKX,HOLDLOCK)");
            try(var observer=owner.open()) {
                sql(observer,"CREATE EVENT SESSION ["+event+"] ON SERVER ADD EVENT sqlserver.sp_statement_completed(ACTION(sqlserver.session_id) WHERE ([sqlserver].[session_id]="+spid+")) ADD TARGET package0.ring_buffer WITH(MAX_MEMORY=1024 KB,EVENT_RETENTION_MODE=NO_EVENT_LOSS); ALTER EVENT SESSION ["+event+"] ON SERVER STATE=START");
            }
        }catch(Throwable failure){held.close();throw failure;}
        Thread.ofPlatform().name("confined-mcp-expiry").start(()-> {
            try(held;var observer=owner.open()) {
                long until=System.nanoTime()+2_500_000_000L;boolean waiting=false;
                while(System.nanoTime()<until){if(number(observer,"SELECT COUNT(*) FROM sys.dm_exec_requests WHERE session_id="+spid+" AND blocking_session_id="+blocker+" AND wait_type LIKE 'LCK_M_%'")>0){waiting=true;break;}Thread.sleep(10);}
                assertTrue(waiting,"MCP final-gate SPID must positively wait on the exact blocker");boolean expired=false;
                while(System.nanoTime()<until){if(number(held,"SELECT COUNT(*) FROM dbo.AiDelegatedSession WHERE SessionId='"+session+"' AND IdleExpiresAt<=SYSUTCDATETIME()")>0){expired=true;break;}Thread.sleep(10);}
                assertTrue(expired,"Database UTC must cross stored expiry while final gate is blocked");
                Instant release;try(var st=held.createStatement();var r=st.executeQuery("SELECT SYSUTCDATETIME() Sample")){assertTrue(r.next());release=com.shale.data.util.UtcJdbcTimestamp.get(r,"Sample").toInstant();}
                sql(held,"COMMIT TRANSACTION");released.complete(release);
            }catch(Throwable failure){released.completeExceptionally(failure);}
        });
    }
    public void verify()throws Exception {
        assertTrue(injected.get(),"Expiry hook must execute");Instant release=released.get(5,TimeUnit.SECONDS);
        try(var c=owner.open();var s=c.createStatement();var r=s.executeQuery("SELECT CONVERT(nvarchar(max),t.target_data) FROM sys.dm_xe_sessions s JOIN sys.dm_xe_session_targets t ON t.event_session_address=s.address WHERE s.name='"+event+"'")) {
            assertTrue(r.next());var document=javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new java.io.ByteArrayInputStream(r.getString(1).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            var events=document.getElementsByTagName("event");boolean sampled=false;
            for(int n=0;n<events.getLength();n++){var e=(org.w3c.dom.Element)events.item(n);if(e.getTextContent().contains("DECLARE @now datetime2(7)=SYSUTCDATETIME()")&&!Instant.parse(e.getAttribute("timestamp")).isBefore(release))sampled=true;}
            assertTrue(sampled,"Exact final gate must sample database UTC after lock release");
        }
    }
    @Override public void close()throws Exception {if(injected.get())try(var c=owner.open()){sql(c,"ALTER EVENT SESSION ["+event+"] ON SERVER STATE=STOP; DROP EVENT SESSION ["+event+"] ON SERVER");}}
    static Object invoke(Object target,Method method,Object[] args)throws Throwable {try{return method.invoke(target,args);}catch(InvocationTargetException failure){throw failure.getCause();}}
    static int number(Connection c,String text)throws SQLException {try(var s=c.createStatement();var r=s.executeQuery(text)){assertTrue(r.next());return r.getInt(1);}}
    static void sql(Connection c,String text)throws SQLException {try(var s=c.createStatement()){s.setQueryTimeout(5);s.execute(text);}}
}
