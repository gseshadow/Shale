package com.shale.data.security.ai;

import com.shale.data.runtime.RuntimeSessionService;
import javax.sql.DataSource;
import java.sql.*;
import java.util.concurrent.*;

/** Bounded pool acquisition/setup only. A timed-out borrower can never perform a protected read.
 * Late leases are closed by the acquiring task; no caller identity is stored on a worker thread. */
final class IntegrationRuntimeLease {
    private static final ThreadPoolExecutor ACQUIRERS=new ThreadPoolExecutor(0,5,30,TimeUnit.SECONDS,
            new SynchronousQueue<>(),r->{ var t=new Thread(r,"integration-lease");t.setDaemon(true);return t; },new ThreadPoolExecutor.AbortPolicy());
    private IntegrationRuntimeLease() {}
    static Connection open(DataSource source,int tenant,int user,int seconds)throws SQLException {
        var ticket=new Ticket();
        Future<?> future;
        try { future=ACQUIRERS.submit(()->{
            Connection c=null;
            try {
                var initializer=new RuntimeSessionService(source); initializer.initialize(tenant,user);
                c=initializer.getConnection(seconds);
                synchronized(ticket) {
                    if(!ticket.abandoned) { ticket.connection=c; c=null; }
                }
            } catch(Exception failure) { synchronized(ticket) { ticket.failed=true; } }
            finally { if(c!=null)try { c.close(); } catch(SQLException ignored) {} }
        }); } catch(RejectedExecutionException busy) { throw new SQLException("Integration acquisition unavailable."); }
        try {
            future.get(seconds,TimeUnit.SECONDS);
            synchronized(ticket) {
                if(ticket.failed || ticket.connection==null)throw new SQLException("Integration lease unavailable.");
                Connection c=ticket.connection; ticket.connection=null; return c;
            }
        } catch(InterruptedException cancelled) { Thread.currentThread().interrupt(); throw new SQLException("Integration acquisition cancelled."); }
        catch(ExecutionException | TimeoutException failure) { throw new SQLException("Integration acquisition unavailable."); }
        finally {
            synchronized(ticket) {
                ticket.abandoned=true;
                if(ticket.connection!=null) { try { ticket.connection.close(); } catch(SQLException ignored) {} ticket.connection=null; }
            }
            future.cancel(true);
        }
    }
    private static final class Ticket { Connection connection; boolean abandoned,failed; }
}
