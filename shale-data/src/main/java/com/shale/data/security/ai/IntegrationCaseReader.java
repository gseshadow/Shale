package com.shale.data.security.ai;

import java.util.function.ToIntFunction;

/** Protocol-independent, bounded read surface. No ordinary-user or generic DAO fallback. */
public interface IntegrationCaseReader {
    DisclosureBuffer search(IntegrationInvocation invocation, String query, Integer page, ToIntFunction<DisclosureBuffer> envelopeBytes);
    DisclosureBuffer overview(IntegrationInvocation invocation, Long caseId, ToIntFunction<DisclosureBuffer> envelopeBytes);
}
