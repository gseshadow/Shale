package com.shale.server.mcp;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import org.slf4j.Marker;

/** SDK parser DEBUG includes raw JSON. Deny its modern payload/exception loggers even under root DEBUG. */
final class McpPayloadLogFilter extends TurboFilter {
    static void install() {
        if (!(org.slf4j.LoggerFactory.getILoggerFactory() instanceof LoggerContext context))
            throw new IllegalStateException("Reviewed MCP log filter requires the supported Logback baseline.");
        synchronized(context) {
            if(context.getTurboFilterList().stream().noneMatch(McpPayloadLogFilter.class::isInstance)) {
                var filter=new McpPayloadLogFilter();filter.start();context.addTurboFilter(filter);
            }
        }
    }
    static void installSqlProtection() {
        install();
        java.util.logging.Logger.getLogger("com.microsoft.sqlserver.jdbc").setLevel(java.util.logging.Level.OFF);
        var manager=java.util.logging.LogManager.getLogManager();var names=manager.getLoggerNames();
        while(names.hasMoreElements()) {
            String name=names.nextElement();
            if(name.startsWith("com.microsoft.sqlserver.jdbc")) {
                var logger=manager.getLogger(name);
                if(logger!=null){logger.setLevel(java.util.logging.Level.OFF);logger.setFilter(record->false);}
            }
        }
    }
    @Override public FilterReply decide(Marker marker,Logger logger,Level level,String format,Object[] params,Throwable failure) {
        if(logger.getName().startsWith("com.microsoft.sqlserver.jdbc") || logger.getName().startsWith("io.modelcontextprotocol.modern."))return FilterReply.DENY;
        // Security 7's request-line DEBUG/TRACE includes the query string before the gate rejects it.
        // Suppress MCP query lines, including a context prefix; leave ordinary OAuth operation logs alone.
        if(logger.getName().equals("org.springframework.security.web.FilterChainProxy")) {
            if(format!=null && format.contains("/mcp?"))return FilterReply.DENY;
            if(params!=null)for(Object parameter:params)
                if(String.valueOf(parameter).contains("/mcp?"))return FilterReply.DENY;
        }
        return FilterReply.NEUTRAL;
    }
}
