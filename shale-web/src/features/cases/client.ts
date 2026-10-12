import { apiBaseUrl } from '../../api';
import type { FeatureResponse } from '../../sessionRequests';
import { sessionFetch, FeaturePayloadMalformed, FeaturePayloadOversized, SessionRequestDiscarded, FeatureRequestCancelled } from '../../sessionRequests';
import { withSessionDeadline, SessionAttemptCancelled, SessionAttemptTimedOut } from '../../sessionDeadline';
import { InvalidCaseResponse, sqlId, validateOverview, validatePage } from './contracts';
export type Selection = { mode: 'assigned' | 'search'; query: string; page: number };
export class CaseReadFailure extends Error {
  constructor(public readonly kind: 'validation' | 'forbidden' | 'unavailable' | 'audit' | 'service' | 'oversized' | 'malformed' | 'timeout' | 'network') { super(kind); }
}
export const readFailureMessage = (error: unknown): string => {
  const kind = error instanceof CaseReadFailure ? error.kind : 'network';
  return {
    validation: 'The Case request was not accepted. Check the case name or Case ID and submit again.',
    forbidden: 'This Case read operation is not permitted. Your session remains signed in.',
    unavailable: 'Case unavailable.',
    audit: 'Overview is unavailable because the required read audit could not be confirmed. Retry explicitly.',
    service: 'The Case read service is unavailable. Retry explicitly.',
    oversized: 'The Case response exceeds the supported size limit. No data was displayed.',
    malformed: 'The Case response could not be validated. No data was displayed.',
    timeout: 'The Case read timed out. Its outcome is uncertain. Retry explicitly when ready.',
    network: 'The Case read could not be confirmed because of a connection failure. Retry explicitly when ready.',
  }[kind];
};
async function read<T>(token: string, path: string, init: RequestInit, limit: number, controller: AbortController, validate: (value: unknown) => T): Promise<T> {
  let response: FeatureResponse | undefined;
  try {
    return await withSessionDeadline(controller, 8000, async assertActive => {
      response = await sessionFetch(token, `${apiBaseUrl()}${path}`, { ...init, cache: 'no-store', signal: controller.signal,
        headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' } });
      assertActive();
      const body = await response.boundedText(response.ok ? limit : 8192);
      assertActive();
      let json: unknown;
      try { json = JSON.parse(body); } catch { throw new CaseReadFailure('malformed'); }
      if (!response.ok) {
        const code = json && typeof json === 'object' && 'error' in json ? json.error : null;
        if (response.status === 400) throw new CaseReadFailure('validation');
        if (response.status === 403) throw new CaseReadFailure('forbidden');
        if (response.status === 404) throw new CaseReadFailure('unavailable');
        if (code === 'case_audit_unavailable') throw new CaseReadFailure('audit');
        if (code === 'case_payload_oversized') throw new CaseReadFailure('oversized');
        if (code === 'case_read_timeout') throw new CaseReadFailure('timeout');
        throw new CaseReadFailure('service');
      }
      const value = validate(json);
      assertActive();
      return value;
    });
  } catch (error) {
    if (error instanceof SessionRequestDiscarded || error instanceof SessionAttemptCancelled || error instanceof FeatureRequestCancelled) throw error;
    if (error instanceof CaseReadFailure) throw error;
    if (error instanceof SessionAttemptTimedOut) throw new CaseReadFailure('timeout');
    if (error instanceof FeaturePayloadOversized) throw new CaseReadFailure('oversized');
    if (error instanceof FeaturePayloadMalformed || error instanceof InvalidCaseResponse) throw new CaseReadFailure('malformed');
    throw new CaseReadFailure('network');
  } finally { response?.dispose(); }
}
export function readCasePage(token: string, selection: Selection, controller: AbortController) {
  const { mode, page } = selection, query = selection.query.trim();
  if (!Number.isInteger(page) || page < 0 || page > 100 || (mode === 'search' && (!query || query.length > 100)))
    return Promise.reject(new CaseReadFailure('validation'));
  return read(token, mode === 'assigned' ? `/api/v2/cases/assigned-page?page=${page}&size=25` : '/api/v2/cases/search-page',
    mode === 'assigned' ? { method: 'GET' } : { method: 'POST', body: JSON.stringify({ query, page, size: 25 }) },
    128 * 1024, controller, json => validatePage(json, page));
}
export function readCaseOverview(token: string, id: number, controller: AbortController) {
  try { sqlId(id); } catch { return Promise.reject(new CaseReadFailure('validation')); }
  return read(token, `/api/v2/cases/${id}/overview`, { method: 'GET' }, 8192, controller, json => validateOverview(json, id));
}
