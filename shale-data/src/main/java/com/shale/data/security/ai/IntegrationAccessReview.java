package com.shale.data.security.ai;

import com.shale.core.runtime.DbSessionProvider;
import java.time.Instant;
import java.util.*;

/** Protected typed review, independent of the PHI viewer. SQL revalidates current same-tenant admin.
 * A page contains at most 100 parents / 1000 typed resource rows; no generic dump surface. */
public final class IntegrationAccessReview {
    public enum Tool { search_cases, get_case_overview, UNSUPPORTED_TOOL }
    public enum Operation { SEARCH_MINIMIZED_CASES, READ_MINIMIZED_CASE_OVERVIEW, UNSUPPORTED }
    public record Entry(long eventId, Instant occurredAt, int tenant, int actingUser, UUID integration,
            UUID grant, UUID session, String clientId, Tool tool, Operation operation,
            IntegrationAccessAudit.Outcome outcome, IntegrationAccessAudit.Reason reason,
            IntegrationAccessAudit.Stage stage, int returnedCount, Integer page, Integer size,
            UUID correlationId, UUID invocationId, Integer caseId, IntegrationAccessAudit.Relation relation) {}
    private final DbSessionProvider runtime;
    public IntegrationAccessReview(DbSessionProvider runtime) { this.runtime=Objects.requireNonNull(runtime); }
    private static Integer integer(Object value) { return value==null ? null : ((Number)value).intValue(); }
    public List<Entry> page(long afterEventId,int size) {
        if(afterEventId<0 || size<1 || size>100)throw new IllegalArgumentException("Review page bound.");
        int[] mapped={0};
        var parents=new HashSet<Long>();
        return AiSql.call(runtime,"AiReviewCaseAccessV1",r->{
            parents.add(r.getLong("Id"));
            if(++mapped[0]>1000 || parents.size()>size)throw new java.sql.SQLException("Review bound exceeded.");
            var relation=r.getString("Relation");
            return new Entry(r.getLong("Id"),AiSql.instant(r,"OccurredAt"),r.getInt("ShaleClientId"),r.getInt("UserId"),
                    AiSql.uuid(r,"IntegrationId"),AiSql.uuid(r,"GrantId"),AiSql.uuid(r,"SessionId"),r.getString("ClientId"),
                    Tool.valueOf(r.getString("Tool")),Operation.valueOf(r.getString("Operation")),
                    IntegrationAccessAudit.Outcome.valueOf(r.getString("Outcome")),IntegrationAccessAudit.Reason.valueOf(r.getString("Reason")),
                    IntegrationAccessAudit.Stage.valueOf(r.getString("Stage")),r.getInt("ReturnedCount"),
                    integer(r.getObject("SearchPage")),integer(r.getObject("SearchSize")),AiSql.uuid(r,"CorrelationId"),AiSql.uuid(r,"InvocationId"),
                    integer(r.getObject("CaseId")),relation==null?null:IntegrationAccessAudit.Relation.valueOf(relation));
        },afterEventId,size);
    }
}
