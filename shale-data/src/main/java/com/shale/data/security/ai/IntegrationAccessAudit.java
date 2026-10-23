package com.shale.data.security.ai;

import java.sql.*;
import java.util.*;

/** Fixed-column append API. No text/metadata payload or Case existence resolution. */
public final class IntegrationAccessAudit {
    public enum Outcome { ALLOWED_READ, DENIED, INVALID_INPUT, UNAVAILABLE, THROTTLED, CANCELLED }
    public enum Reason { NONE, AUTHORITY, INPUT, READ, AUDIT, COMMIT, CLOSE, SERIALIZATION, OVERSIZED, DEADLINE, QUOTA, CANCELLED, CASE_UNAVAILABLE }
    public enum Stage { READ, TERMINAL }
    public enum Relation { RETURNED, REQUESTED }

    void append(Connection c, IntegrationInvocation i, Stage stage, Outcome outcome, Reason reason,
            Integer page, List<Integer> returned, Integer requested) throws SQLException {
        if (returned.size()>10 || (requested!=null && !returned.isEmpty())) throw new IllegalArgumentException("Audit resource bounds.");
        var a=i.authority();
        var args=new ArrayList<Object>(List.of(i.invocationId(),i.correlationId(),a.integrationId(),a.grantId(),
                a.delegatedSessionId(),a.oauthClientId(),i.operation().tool(),stage.name(),outcome.name(),reason.name(),returned.size()));
        args.add(AiSql.nullable(page,Types.INTEGER));
        args.add(AiSql.nullable(requested,Types.INTEGER));
        for(int n=0;n<10;n++) args.add(AiSql.nullable(n<returned.size()?returned.get(n):null,Types.INTEGER));
        if (!AiSql.one(AiSql.call(c,"AiAppendCaseAccessV1",r->r.getInt("AppendedCount")==1,args.toArray())))
            throw new SQLException("Required integration audit unavailable.");
    }
}
