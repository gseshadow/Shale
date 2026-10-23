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
    @Override public FilterReply decide(Marker marker,Logger logger,Level level,String format,Object[] params,Throwable failure) {
        return logger.getName().startsWith("io.modelcontextprotocol.modern.") ? FilterReply.DENY : FilterReply.NEUTRAL;
    }
}
