package com.shale.server.mcp;

import java.util.concurrent.*;
import org.slf4j.LoggerFactory;

/** One background integrity check at a time; a failure withdraws the worker until restart. */
final class McpInstallationMonitor implements AutoCloseable {
    private final ScheduledExecutorService executor;
    private final Runnable verify,withdraw;
    private volatile boolean closed;
    McpInstallationMonitor(Runnable verify,Runnable withdraw) {
        this(verify,withdraw,Executors.newSingleThreadScheduledExecutor(task->{
            var thread=new Thread(task,"mcp-installation-verification");thread.setDaemon(true);return thread;
        }));
    }
    McpInstallationMonitor(Runnable verify,Runnable withdraw,ScheduledExecutorService executor) {
        this.verify=verify;this.withdraw=withdraw;this.executor=executor;
        try { executor.scheduleWithFixedDelay(this::refresh,5,5,TimeUnit.MINUTES); }
        catch(RuntimeException failure) { close();throw failure; }
    }
    void refresh() {
        if(closed)return;
        try {
            verify.run();
            if(!closed)LoggerFactory.getLogger(getClass()).info("MCP_INSTALLATION_VERIFICATION result=PASSED");
        } catch(Throwable failure) {
            close();
            LoggerFactory.getLogger(getClass()).warn("MCP_INSTALLATION_VERIFICATION result=FAILED");
        }
    }
    @Override public synchronized void close() {
        if(closed)return;
        closed=true;
        try { withdraw.run(); } finally { executor.shutdownNow(); }
    }
}
