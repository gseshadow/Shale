import { useEffect, useRef, useState } from 'react';
import { apiBaseUrl, type AuthenticatedUser } from './api';
import { captureSessionRequestGuard, sessionFetch } from './sessionRequests';
import { ActionButton, SecondaryButton, Feedback, PageHeader, LoadingState } from './ui/primitives';

const permissions: Record<string, string> = {
  'cases.search': 'Search cases and read minimized case summaries',
  'cases.overview.read': 'Read minimized case overviews',
};
type Consent = { client_id: string; client_name: string; scopes: string[]; state: string; tenant: number; user: number;
  expires_at: string; intent_expires_at: string; notice: string };
type Onboarding = { client_id: string; client_name: string; tenant: number; user: number; admin: boolean;
  state: 'PENDING' | 'APPROVED' | 'UNAPPROVED' | 'APPROVAL_REVIEW_REQUIRED' | 'PUBLICATION_REVIEW_REQUIRED';
  approval_id?: string; approval_version?: string; approval_state?: 'PENDING' | 'APPROVED' };
type OAuthStage = 'REQUEST' | 'ONBOARDING' | 'APPROVAL' | 'AUTHORIZE' | 'CONSENT' | 'DECISION';
class OAuthRequestFailure extends Error {
  constructor(readonly reference: string) { super('Authorization request failed.'); }
}
export function oauthRequestDeadline(): number {
  return apiBaseUrl() === 'https://shale-mcp-acceptance.azurewebsites.net' &&
    import.meta.env.VITE_SHALE_MCP_ACCEPTANCE_BROWSER === 'true' ? 600000 : 8000;
}
function failureMessage(error: unknown, stage: OAuthStage): string {
  const reference = error instanceof OAuthRequestFailure ? error.reference : `${stage}_INVALID_RESPONSE`;
  return reference.endsWith('_TIMEOUT')
    ? `The request timed out. An approval may already have been saved; start a fresh connection to check its status. Reference: ${reference}.`
    : `Authorization could not finish. Start a fresh connection from your integration. Reference: ${reference}.`;
}
const failed = 'This authorization request is invalid, expired, or unavailable. Start again from your integration.';
export async function oauthRequest(token: string, path: string, body?: URLSearchParams, stage: OAuthStage = 'REQUEST'): Promise<unknown> {
  const controller = new AbortController();
  const deadline = window.setTimeout(() => controller.abort(), oauthRequestDeadline());
  let received = false;
  try {
    const response = await sessionFetch(token, `${apiBaseUrl()}${path}`, {
      method: body ? 'POST' : 'GET', credentials: 'omit', redirect: 'error', referrerPolicy: 'no-referrer', cache: 'no-store',
      signal: controller.signal,
      headers: { Authorization: `Bearer ${token}`, Accept: 'application/json', ...(body ? { 'Content-Type': 'application/x-www-form-urlencoded' } : {}) },
      body: body?.toString(),
    });
    received = true;
    if (!response.ok) {
      const status = Number.isInteger(response.status) && response.status >= 100 && response.status <= 599 ? response.status : 0;
      response.dispose(); throw new OAuthRequestFailure(`${stage}_HTTP_${status}`);
    }
    return JSON.parse(await response.boundedText(16384)) as unknown;
  } catch (error) {
    if (error instanceof OAuthRequestFailure) throw error;
    throw new OAuthRequestFailure(`${stage}_${controller.signal.aborted ? 'TIMEOUT' : received ? 'INVALID_RESPONSE' : 'NETWORK'}`);
  } finally { window.clearTimeout(deadline); controller.abort(); }
}
const isDcr = (id: string | null) => !!id && /^dcr\.[0-9a-f]{64}$/.test(id);
export function callbackTarget(value: unknown, redirect: string | null, state: string | null, clientId: string | null = null): string {
  if (typeof value !== 'string' || !redirect || !state) throw new Error(failed);
  const url = new URL(value), target = new URL(redirect);
  if (target.protocol !== 'https:' && !(target.protocol === 'http:' && (['127.0.0.1', '[::1]'].includes(target.hostname) || (isDcr(clientId) && target.hostname === 'localhost' && !!target.port)))) throw new Error(failed);
  if (url.origin !== target.origin || url.pathname !== target.pathname || url.hash || url.username || url.password
      || url.searchParams.getAll('state').length !== 1 || url.searchParams.get('state') !== state) throw new Error(failed);
  return value;
}
export function OAuthConsent({ token, user, request, onComplete = url => window.location.replace(url) }:
  { token: string; user: AuthenticatedUser; request: string; onComplete?: (url: string) => void }) {
  const [consent, setConsent] = useState<Consent | null>(null);
  const [error, setError] = useState('');
  const [onboarding, setOnboarding] = useState<Onboarding | null>(null);
  const [revision, setRevision] = useState(0);
  const [busy, setBusy] = useState(false);
  const [expired, setExpired] = useState(false);
  const submitting = useRef(false);
  const mounted = useRef(false);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  const sessionIsCurrent = captureSessionRequestGuard(token);
  const current = () => mounted.current && sessionIsCurrent();
  useEffect(() => {
    let active = true;
    setConsent(null); setOnboarding(null); setError(''); setExpired(false);
    const params = new URLSearchParams(request);
    let stage: OAuthStage = 'REQUEST';
    void (async () => {
      if (!request || request.length > 16384) throw new Error(failed);
      if ((params.get('client_id')?.startsWith('https://') || isDcr(params.get('client_id')))) {
        const body = new URLSearchParams({ client_id: params.get('client_id')! });
        const scopes = (params.get('scope') ?? '').split(/\s+/).filter(Boolean);
        if (!scopes.length || scopes.some(scope => !permissions[scope])) throw new Error(failed);
        scopes.forEach(scope => body.append('scope', scope));
        stage = 'ONBOARDING';
        const approval = await oauthRequest(token, '/oauth/mcp/onboarding', body, stage) as Onboarding;
        if (!active || !current()) return;
        if (approval.client_id !== params.get('client_id') || approval.tenant !== user.shaleClientId || approval.user !== user.userId
            || typeof approval.admin !== 'boolean' || !['PENDING', 'APPROVED', 'UNAPPROVED', 'APPROVAL_REVIEW_REQUIRED', 'PUBLICATION_REVIEW_REQUIRED'].includes(approval.state)) throw new Error(failed);
        if (approval.state !== 'APPROVED') { setOnboarding(approval); return; }
      }
      stage = 'AUTHORIZE';
      const started = await oauthRequest(token, `/oauth/mcp/authorize?${params}`, undefined, stage) as { redirect?: unknown };
      if (!active || !current()) return;
      if (typeof started.redirect !== 'string') throw new Error(failed);
      const next = new URL(started.redirect, apiBaseUrl());
      if (next.origin !== new URL(apiBaseUrl()).origin || next.pathname !== '/oauth/mcp/consent') throw new Error(failed);
      stage = 'CONSENT';
      const details = await oauthRequest(token, next.pathname + next.search, undefined, stage) as Consent;
      if (!active || !current()) return;
      if (details.user !== user.userId || details.tenant !== user.shaleClientId || details.client_id !== params.get('client_id')
          || !details.state || !Array.isArray(details.scopes) || !details.scopes.length || details.scopes.some(scope => !permissions[scope])
          || !Number.isFinite(Date.parse(details.intent_expires_at))) throw new Error(failed);
      setConsent(details);
      window.history.replaceState(window.history.state, '', window.location.pathname);
    })().catch(error => { if (active && current()) setError(failureMessage(error, stage)); });
    return () => { active = false; };
  }, [token, user.userId, user.shaleClientId, request, revision]);
  useEffect(() => {
    if (!consent) return;
    const remaining = Date.parse(consent.intent_expires_at) - Date.now();
    if (remaining <= 0) { setExpired(true); return; }
    const timer = window.setTimeout(() => setExpired(true), remaining);
    return () => window.clearTimeout(timer);
  }, [consent]);
  async function decideApplication(decision: 'APPROVE' | 'REVOKE' | 'DENY') {
    if (!onboarding?.admin || !onboarding.approval_id || !onboarding.approval_version || submitting.current || !current()) return;
    submitting.current = true; setBusy(true);
    try {
      const body = new URLSearchParams({ client_id: onboarding.client_id, approval_id: onboarding.approval_id,
        approval_version: onboarding.approval_version, decision });
      if (decision === 'APPROVE') (new URLSearchParams(request).get('scope') ?? '').split(/\s+/).filter(Boolean).forEach(scope => body.append('scope', scope));
      await oauthRequest(token, '/oauth/mcp/approval', body, 'APPROVAL');
      if (current()) setRevision(value => value + 1);
    } catch (error) { if (current()) setError(failureMessage(error, 'APPROVAL')); }
    finally { submitting.current = false; if (current()) setBusy(false); }
  }
  async function decide(approve: boolean) {
    if (!consent || expired || submitting.current || !current()) return;
    submitting.current = true; setBusy(true);
    try {
      const body = new URLSearchParams({ client_id: consent.client_id, state: consent.state });
      if (approve) consent.scopes.forEach(scope => body.append('scope', scope));
      const result = await oauthRequest(token, '/oauth/mcp/authorize', body, 'DECISION') as { redirect?: unknown };
      if (!current()) return;
      const params = new URLSearchParams(request);
      onComplete(callbackTarget(result.redirect, params.get('redirect_uri'), params.get('state'), consent.client_id));
    } catch (error) { if (current()) setError(failureMessage(error, 'DECISION')); }
  }
  return <main className="login-page"><section className="login-panel settings-page">
    <PageHeader eyebrow="Shale" title="Authorize integration" lede="Review this request before allowing access to your Shale account." />
    {oauthRequestDeadline() > 8000 && <p>Acceptance verification can take several minutes. Keep this window open.</p>}
    {error ? <Feedback kind="error">{error}</Feedback> : onboarding ? <section>
      <h2>Tenant application approval required</h2>
      <p>{onboarding.client_name}</p><p>Application: {onboarding.client_id}</p>
      <p>This application needs administrator approval for your Shale tenant. Approval alone does not authorize access to your account.</p>
      <ul>{(new URLSearchParams(request).get('scope') ?? '').split(/\s+/).filter(Boolean).map(scope => <li key={scope}>{permissions[scope]}</li>)}</ul>
      {onboarding.state === 'APPROVAL_REVIEW_REQUIRED' && <Feedback kind="info">The application or its approval changed. An administrator must review and replace the previous approval.</Feedback>}
      <div className="toolbar-actions">
        {onboarding.admin && onboarding.state === 'PENDING' && <ActionButton disabled={busy} onClick={() => void decideApplication('APPROVE')}>Approve tenant application</ActionButton>}
        {onboarding.admin && onboarding.state === 'APPROVAL_REVIEW_REQUIRED' && <SecondaryButton disabled={busy} onClick={() => void decideApplication(onboarding.approval_state === 'PENDING' ? 'DENY' : 'REVOKE')}>Replace previous approval</SecondaryButton>}
        <SecondaryButton disabled={busy} onClick={() => setRevision(value => value + 1)}>Check approval</SecondaryButton>
      </div>
      <p>If this request expires while waiting, start the connection again from your client.</p>
    </section> : !consent ? <LoadingState message="Loading authorization requestÃ¢â‚¬Â¦" /> :
      <section>
        <h2>{consent.client_name}</h2><p>Integration ID: {consent.client_id}</p>
        <p>Approving as {user.displayName || user.email || `User ${user.userId}`} Ã‚Â· User {consent.user} Ã‚Â· Tenant {consent.tenant}</p>
        <h3>Requested permissions</h3><ul>{consent.scopes.map(scope => <li key={scope}>{permissions[scope]}</li>)}</ul>
        <p>{consent.notice}</p><p>Access ends: {new Date(consent.expires_at).toLocaleString()}</p>
        <p>Decide before: {new Date(consent.intent_expires_at).toLocaleString()}</p>
        {expired && <Feedback kind="error">This request has expired. Start again from your integration.</Feedback>}
        <div className="toolbar-actions">
          <SecondaryButton disabled={busy || expired} onClick={() => void decide(false)}>Deny</SecondaryButton>
          <ActionButton disabled={busy || expired} onClick={() => void decide(true)}>Approve access</ActionButton>
        </div>
      </section>}
  </section></main>;
}
