import { createContext, useContext, useEffect, useRef, useState } from 'react';
import type { ReactNode, Dispatch, SetStateAction } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { routePath } from '../../app/routeRegistry';
import { captureSessionRequestGuard, observeSessionRequestEnd } from '../../sessionRequests';
import { Button, EntityCard, EntityList, Feedback, Field, MetadataGrid, MetadataRow, NavigationButton, PageHeader, SectionRegion, StatusPill, ToolbarActions } from '../../ui/primitives';
import { readCaseOverview, readCasePage, readFailureMessage } from './client';
import type { Selection } from './client';
import type { CaseOverview, CasePage } from './contracts';

type Snapshot = { draft: string; selection: Selection; result: CasePage | null; returnCaseId: number | null };
const initial = (): Snapshot => ({ draft: '', selection: { mode: 'search', query: '', page: 0 }, result: null, returnCaseId: null });
const WorkspaceContext = createContext<{ snapshot: Snapshot; update: Dispatch<SetStateAction<Snapshot>> } | null>(null);
export function CaseWorkspaceMemory({ token, children }: { token: string; children: ReactNode }) {
  const [snapshot, update] = useState(initial);
  useEffect(() => observeSessionRequestEnd(token, () => update(initial())), [token]);
  return <WorkspaceContext.Provider value={{ snapshot, update }}>{children}</WorkspaceContext.Provider>;
}
function useMemory() { const value = useContext(WorkspaceContext); if (!value) throw new Error('Case workspace session boundary required.'); return value; }
function CaseFacts({ value, includeIdentity = true }: { value: CaseOverview; includeIdentity?: boolean }) {
  return <MetadataGrid>
    {includeIdentity && <><MetadataRow label="Case ID" value={value.caseId} /><MetadataRow label="Case name" value={value.caseName || 'Unnamed case'} /></>}
    <MetadataRow label="Case number" value={value.caseNumber ?? '—'} />
    <MetadataRow label="Status" value={value.status ? <StatusPill color={value.status.color}>{value.status.name || 'Unnamed status'}</StatusPill> : '—'} />
    <MetadataRow label="Practice area" value={value.practiceArea?.name ?? '—'} />
    <MetadataRow label="Responsible attorney" value={value.responsibleAttorney?.displayName ?? '—'} />
    <MetadataRow label="Primary legal assistant" value={value.primaryLegalAssistant?.displayName ?? '—'} />
    <MetadataRow label="Updated (stored local time)" value={value.updatedAt?.replace('T', ' ') ?? '—'} />
  </MetadataGrid>;
}
function useAttempt(token: string) {
  const attempt = useRef<AbortController | null>(null);
  const mounted = useRef(false);
  const currentSession = captureSessionRequestGuard(token);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; attempt.current?.abort(); }; }, []);
  return {
    begin() { attempt.current?.abort(); const next = new AbortController(); attempt.current = next; return next; },
    cancel() { attempt.current?.abort(); attempt.current = null; },
    current(controller: AbortController) { return mounted.current && attempt.current === controller && currentSession(); },
  };
}
const limitation = 'Limited read-only Overview: eight summary fields. Session expiry requires sign-in again. Automatic refresh is off.';
export function CaseWorkspace({ token }: { token: string }) {
  const { snapshot, update } = useMemory();
  const navigate = useNavigate();
  const attempt = useAttempt(token);
  const [loading, setLoading] = useState(false), [error, setError] = useState<string | null>(null), [announcement, announce] = useState('');
  const page = useRef<HTMLElement | null>(null);
  useEffect(() => {
    let active = true;
    // Shell route focus runs first; a deliberate return then restores the opening action.
    queueMicrotask(() => { if (active && snapshot.returnCaseId && captureSessionRequestGuard(token)())
      page.current?.querySelector<HTMLButtonElement>(`[data-case-id="${snapshot.returnCaseId}"]`)?.focus(); });
    return () => { active = false; };
  }, []);
  async function load(selection: Selection) {
    const controller = attempt.begin();
    const next = { ...snapshot, selection, result: null, returnCaseId: null };
    update(next); setLoading(true); setError(null); announce('Loading Case page…');
    try {
      const result = await readCasePage(token, selection, controller);
      if (!attempt.current(controller)) return;
      update(previous => ({ ...previous, selection, result }));
      announce(result.items.length ? `Case page ${selection.page + 1} loaded. ${result.items.length} cases on this page.${result.page === 100 && result.hasMore ? ' Result window exhausted. Narrow the case-name search.' : ''}` : 'No cases on this page.');
    } catch (error) { if (attempt.current(controller)) { setError(readFailureMessage(error)); announce('Case page could not be loaded.'); } }
    finally { if (attempt.current(controller)) setLoading(false); }
  }
  function search() {
    const query = snapshot.draft.trim();
    if (!query) {
      attempt.cancel(); setLoading(false); setError(null); announce('Enter a case name and submit Search.');
      update({ ...snapshot, selection: { mode: 'search', query: '', page: 0 }, result: null, returnCaseId: null }); return;
    }
    if (query.length > 100) { setError('Enter a case name of 100 characters or fewer.'); return; }
    void load({ mode: 'search', query, page: 0 });
  }
  const { result, selection } = snapshot;
  return <section className="shale-presentation shale-work-page" ref={page}>
    <PageHeader eyebrow="Cases" title="Case workspace" lede={limitation} />
    <ToolbarActions><Button aria-pressed={selection.mode === 'assigned'} onClick={() => { void load({ mode: 'assigned', query: '', page: 0 }); }}>Assigned</Button>
      <Button aria-pressed={selection.mode === 'search'} onClick={() => {
        attempt.cancel(); setLoading(false); setError(null); announce('Enter a case name and submit Search.');
        update({ ...snapshot, selection: { mode: 'search', query: '', page: 0 }, result: null, returnCaseId: null });
      }}>Case-name search</Button></ToolbarActions>
    {selection.mode === 'search' && <form role="search" aria-label="Case-name search" onSubmit={event => { event.preventDefault(); search(); }}>
      <Field type="search" label="Case name" value={snapshot.draft} maxLength={100} autoComplete="off" hint="Search active cases throughout your tenant. Submit to search; case names stay in session memory and must be re-entered after reload."
        onChange={event => update({ ...snapshot, draft: event.target.value.slice(0, 100) })} />
      <Button purpose="primary" type="submit">Search</Button>
    </form>}
    <p role="status" aria-live="polite" aria-atomic="true">{announcement}</p>
    {error && <Feedback kind="error">{error}</Feedback>}
    {loading && <p aria-hidden="true">Loading Case page…</p>}
    {!loading && !result && !error && <p>{selection.mode === 'search' && !selection.query ? 'Enter a case name and submit Search, or select Assigned.' : 'No page is retained. Retry explicitly to load this selection.'}</p>}
    {!loading && (error || (!result && !!selection.query) || (!result && selection.mode === 'assigned')) && <Button onClick={() => { void load(selection); }}>Retry page</Button>}
    {result && <SectionRegion title={selection.mode === 'assigned' ? 'Assigned cases' : 'Case-name results'}>
      <p>Page {result.page + 1}. Up to 25 cases per page. Results can change between pages.</p>
      {!result.items.length && <p>No cases on this page.</p>}
      <EntityList ariaLabel="Cases on this page">{result.items.map(value => <EntityCard key={value.caseId} compact title={value.caseName || 'Unnamed case'}
        metadata={<CaseFacts value={value} includeIdentity={false} />}
        actions={<Button purpose="navigation" data-case-id={value.caseId}
          onClick={() => {
            if (!captureSessionRequestGuard(token)()) return;
            update({ ...snapshot, returnCaseId: value.caseId });
            navigate(routePath('caseOverview', { caseId: value.caseId }));
          }}>Open Overview for Case {value.caseId}</Button>} />)}</EntityList>
      <ToolbarActions>
        <Button disabled={result.page === 0 || loading} onClick={() => { void load({ ...selection, page: result.page - 1 }); }}>Previous</Button>
        <Button disabled={!result.hasMore || result.page === 100 || loading} aria-describedby={result.page === 100 && result.hasMore ? 'case-window-limit' : undefined}
          onClick={() => { void load({ ...selection, page: result.page + 1 }); }}>Next</Button>
      </ToolbarActions>
      {result.page === 100 && result.hasMore && <p id="case-window-limit">The result window is exhausted. More matching cases may exist; narrow the case-name search to continue.</p>}
    </SectionRegion>}
  </section>;
}
export function ReadOnlyCaseOverview({ token }: { token: string }) {
  const { caseId } = useParams();
  const id = caseId && /^\d+$/.test(caseId) ? Number(caseId) : NaN;
  const attempt = useAttempt(token);
  const [value, setValue] = useState<CaseOverview | null>(null), [loading, setLoading] = useState(false), [error, setError] = useState<string | null>(null), [announcement, announce] = useState('');
  async function load() {
    const controller = attempt.begin();
    setValue(null); setError(null); setLoading(true); announce('Loading read-only Overview…');
    try {
      const result = await readCaseOverview(token, id, controller);
      if (!attempt.current(controller)) return;
      setValue(result); announce('Read-only Overview loaded.');
    } catch (error) { if (attempt.current(controller)) { setError(readFailureMessage(error)); announce('Overview could not be loaded.'); } }
    finally { if (attempt.current(controller)) setLoading(false); }
  }
  useEffect(() => {
    let active = true;
    // Coalesce StrictMode's setup/cleanup probe before dispatching an audited read.
    queueMicrotask(() => { if (active) void load(); });
    return () => { active = false; };
  }, []); // Each route open is an authoritative audited read; no background refetch.
  return <section className="shale-presentation shale-work-page">
    <PageHeader eyebrow="Cases" title="Limited read-only Overview" lede={limitation} action={<NavigationButton to={routePath('caseWorkspace')}>Back to Case workspace</NavigationButton>} />
    <p role="status" aria-live="polite" aria-atomic="true">{announcement}</p>
    {loading && <p aria-hidden="true">Loading read-only Overview…</p>}
    {error && <Feedback kind="error">{error}</Feedback>}
    {value && <SectionRegion title="Case summary"><CaseFacts value={value} /></SectionRegion>}
    <ToolbarActions><Button disabled={loading} onClick={() => { void load(); }}>{error ? 'Retry Overview' : 'Read Overview again'}</Button></ToolbarActions>
    <p>Opening or reading Overview again requires the server read audit. Cancelling a request does not undo a committed audit.</p>
  </section>;
}
