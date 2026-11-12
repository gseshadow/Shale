package com.shale.server.mcp;

import javax.sql.DataSource;
import java.util.Objects;
import java.util.function.LongFunction;

/** Supplied restricted sources, bounded by the original request deadline. No pool ownership. */
public record CimdCaseConnections(LongFunction<DataSource> data,LongFunction<DataSource> reviewer) {
    public CimdCaseConnections {Objects.requireNonNull(data);Objects.requireNonNull(reviewer);}
    @Override public String toString(){return "CimdCaseConnections[redacted]";}
    /** Existing deadline decoration shared with manual MCP composition. */
    public static LongFunction<DataSource> bounded(DataSource source){return deadline->new McpJdbcDeadline(source,deadline);}
}
