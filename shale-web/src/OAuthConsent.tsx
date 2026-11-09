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
const failed = 'This authorization request is invalid, expired, or unavailable. Start again from your integration.';
export async function oauthRequest(token: string, path: string, body?: URLSearchParams): Promise<unknown> {
  const controller = new AbortController();
  const deadline = window.setTimeout(() => controller.abort(), 8000);
  try {
    const response = await sessionFetch(token, `${apiBaseUrl()}${path}`, {
      method: body ? 'POST' : 'GET', credentials: 'omit', redirect: 'error', referrerPolicy: 'no-referrer', cache: 'no-store',
      signal: controller.signal,
      headers: { Authorization: `Bearer ${token}`, Accept: 'application/json', ...(body ? { 'Content-Type': 'application/x-www-form-urlencoded' } : {}) },
      body: body?.toString(),
    });
    if (!response.ok) { response.dispose(); throw new Error(failed); }
    return JSON.parse(await response.boundedText(16384)) as unknown;
  } finally { window.clearTimeout(deadline); controller.abort(); }
}
export function callbackTarget(value: unknown, redirect: string | null, state: string | null): string {
  if (typeof value !== 'string' || !redirect || !state) throw new Error(failed);
  const url = new URL(value), target = new URL(redirect);
  if (target.protocol !== 'https:' && !(target.protocol === 'http:' && ['127.0.0.1', '[::1]'].includes(target.hostname))) throw new Error(failed);
  if (url.origin !== target.origin || url.pathname !== target.pathname || url.hash || url.username || url.password
      || url.searchParams.getAll('state').length !== 1 || url.searchParams.get('state') !== state) throw new Error(failed);
  return value;
}
export function OAuthConsent({ token, user, request, onComplete = url => window.location.replace(url) }:
  { token: string; user: AuthenticatedUser; request: string; onComplete?: (url: string) => void }) {
  const [consent, setConsent] = useState<Consent | null>(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [expired, setExpired] = useState(false);
  const submitting = useRef(false);
  const mounted = useRef(false);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  const sessionIsCurrent = captureSessionRequestGuard(token);
  const current = () => mounted.current && sessionIsCurrent();
  useEffect(() => {
    let active = true;
    setConsent(null); setError(''); setExpired(false);
    const params = new URLSearchParams(request);
    void (async () => {
      if (!request || request.length > 16384) throw new Error(failed);
      const started = await oauthRequest(token, `/oauth/mcp/authorize?${params}`) as { redirect?: unknown };
      if (!active || !current()) return;
      if (typeof started.redirect !== 'string') throw new Error(failed);
      const next = new URL(started.redirect, apiBaseUrl());
      if (next.origin !== new URL(apiBaseUrl()).origin || next.pathname !== '/oauth/mcp/consent') throw new Error(failed);
      const details = await oauthRequest(token, next.pathname + next.search) as Consent;
      if (!active || !current()) return;
      if (details.user !== user.userId || details.tenant !== user.shaleClientId || details.client_id !== params.get('client_id')
          || !details.state || !Array.isArray(details.scopes) || !details.scopes.length || details.scopes.some(scope => !permissions[scope])
          || !Number.isFinite(Date.parse(details.intent_expires_at))) throw new Error(failed);
      setConsent(details);
      window.history.replaceState(window.history.state, '', window.location.pathname);
    })().catch(() => { if (active && current()) setError(failed); });
    return () => { active = false; };
  }, [token, user.userId, user.shaleClientId, request]);
  useEffect(() => {
    if (!consent) return;
    const remaining = Date.parse(consent.intent_expires_at) - Date.now();
    if (remaining <= 0) { setExpired(true); return; }
    const timer = window.setTimeout(() => setExpired(true), remaining);
    return () => window.clearTimeout(timer);
  }, [consent]);
  async function decide(approve: boolean) {
    if (!consent || expired || submitting.current || !current()) return;
    submitting.current = true; setBusy(true);
    try {
      const body = new URLSearchParams({ client_id: consent.client_id, state: consent.state });
      if (approve) consent.scopes.forEach(scope => body.append('scope', scope));
      const result = await oauthRequest(token, '/oauth/mcp/authorize', body) as { redirect?: unknown };
      if (!current()) return;
      const params = new URLSearchParams(request);
      onComplete(callbackTarget(result.redirect, params.get('redirect_uri'), params.get('state')));
    } catch { if (current()) setError(failed); }
  }
  return <main className="login-page"><section className="login-panel settings-page">
    <PageHeader eyebrow="Shale" title="Authorize integration" lede="Review this request before allowing access to your Shale account." />
    {error ? <Feedback kind="error">{error}</Feedback> : !consent ? <LoadingState message="Loading authorization request…" /> :
      <section>
        <h2>{consent.client_name}</h2><p>Integration ID: {consent.client_id}</p>
        <p>Approving as {user.displayName || user.email || `User ${user.userId}`} · User {consent.user} · Tenant {consent.tenant}</p>
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
