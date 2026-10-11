import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { StrictMode } from 'react';
import App, { createAppRouter } from './App';
import { readAccessToken, storeAccessToken } from './api';

const user = { authenticated: true, userId: 1, shaleClientId: 1, displayName: 'Synthetic User', email: null, nameFirst: null, nameLast: null, isAdmin: false, isAttorney: false, initials: null, color: null };
const item = (caseId: number, caseName = 'Synthetic Match') => ({ caseId, caseName, caseNumber: null, status: null, practiceArea: null, responsibleAttorney: null, primaryLegalAssistant: null, updatedAt: '2026-10-10T12:34:56.123' });
const records = [...Array.from({ length: 35 }, (_, i) => item(i + 1, 'Unrelated')), ...Array.from({ length: 55 }, (_, i) => item(i + 36))];
const json = (value: unknown, status = 200) => new Response(JSON.stringify(value), { status });
const fetchMock = vi.fn<typeof fetch>();
const routers: ReturnType<typeof createAppRouter>[] = [];
let calls: { path: string; search: string; body: unknown; method: string }[];
let selectedRecords = records;
let overviewStatus = 200, overviewError = 'case_read_unavailable';
let override: ((path: string, init?: RequestInit) => Promise<Response> | Response | undefined) | undefined;
beforeEach(() => {
  sessionStorage.clear(); calls = []; selectedRecords = records; overviewStatus = 200; overviewError = 'case_read_unavailable'; override = undefined;
  vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn() })));
  vi.stubGlobal('fetch', fetchMock);
  fetchMock.mockImplementation(async (input, init) => {
    const url = new URL(String(input)); const path = url.pathname; const body = init?.body ? JSON.parse(String(init.body)) : null;
    calls.push({ path, search: url.search, method: init?.method ?? 'GET', body });
    const custom = override?.(path, init); if (custom) return custom;
    if (path === '/api/auth/me') return json(user);
    if (path === '/api/auth/login') return json({ authenticated: true, tokenType: 'Bearer', accessToken: 'synthetic-new', expiresInSeconds: 3600, user });
    if (path === '/api/auth/logout') return json({ revoked: true });
    if (path === '/api/v2/cases/search-page' || path === '/api/v2/cases/assigned-page') {
      const number = body?.page ?? Number(url.searchParams.get('page'));
      // Synthetic server applies tenant-wide name predicate BEFORE slicing; assigned is independent.
      const matches = body ? selectedRecords.filter(row => row.caseName.toLowerCase().includes(body.query.toLowerCase())) : [item(1, 'Assigned synthetic')];
      return json({ items: matches.slice(number * 25, (number + 1) * 25), page: number, size: 25, hasMore: matches.length > (number + 1) * 25 });
    }
    const overview = /^\/api\/v2\/cases\/(\d+)\/overview$/.exec(path);
    if (overview) return json(overviewStatus === 200 ? item(Number(overview[1])) : { error: overviewError, message: 'Private raw witness' }, overviewStatus);
    if (path === '/api/cases/7') return json({ caseId: 7, caseName: 'Legacy editable Case', relatedContacts: [], statusHistory: [], mappedCaseDates: [] });
    if (path === '/api/cases/7/tasks' || path === '/api/cases/7/updates' || path === '/api/cases/assigned' || path === '/api/tasks/assigned') return json([]);
    throw new Error('Excluded API requested by fixture');
  });
});
afterEach(() => { cleanup(); routers.splice(0).forEach(router => router.dispose()); sessionStorage.clear(); vi.restoreAllMocks(); vi.unstubAllGlobals(); });
function open(path = '/case-workspace', signedIn = true, strict = false) {
  if (signedIn) storeAccessToken('synthetic'); window.history.replaceState({}, '', path);
  const router = createAppRouter(); routers.push(router); const view = render(strict ? <StrictMode><App router={router} /></StrictMode> : <App router={router} />); return { router, view };
}
async function search(query = 'Match') {
  fireEvent.change(screen.getByLabelText('Case name'), { target: { value: query } }); fireEvent.submit(screen.getByRole('search', { name: 'Case-name search' }));
  await screen.findByRole('button', { name: 'Open Overview for Case 36' });
}
async function signIn() {
  fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'synthetic@example.invalid' } });
  fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'synthetic-password' } }); fireEvent.click(screen.getByRole('button', { name: 'Sign in' }));
}
function featureCalls() { return calls.filter(call => call.path !== '/api/auth/me'); }

describe('bounded read workspace through actual routes/session/client', () => {
  it('blank search is instructions; explicit server search finds Case 36 beyond first 25, then pages and resets', async () => {
    open(); await screen.findByRole('heading', { name: 'Case workspace', level: 1 });
    expect(featureCalls()).toEqual([]);
    fireEvent.change(screen.getByLabelText('Case name'), { target: { value: 'Match' } }); expect(featureCalls()).toEqual([]);
    await search(); expect(screen.getAllByRole('button', { name: /Open Overview/ })).toHaveLength(25);
    expect(calls.at(-1)).toEqual({ path: '/api/v2/cases/search-page', search: '', method: 'POST', body: { query: 'Match', page: 0, size: 25 } });
    fireEvent.click(screen.getByRole('button', { name: 'Next' })); await screen.findByRole('button', { name: 'Open Overview for Case 61' });
    expect(calls.at(-1)?.body).toEqual({ query: 'Match', page: 1, size: 25 });
    fireEvent.click(screen.getByRole('button', { name: 'Previous' })); await screen.findByRole('button', { name: 'Open Overview for Case 36' });
    await search('Synthetic'); expect(calls.at(-1)?.body).toEqual({ query: 'Synthetic', page: 0, size: 25 });
    fireEvent.change(screen.getByLabelText('Case name'), { target: { value: ' ' } }); fireEvent.submit(screen.getByRole('search'));
    expect(screen.queryByRole('button', { name: /Open Overview/ })).toBeNull(); expect(calls.at(-1)?.body).toEqual({ query: 'Synthetic', page: 0, size: 25 });
  });
  it('Assigned selects only assignment; tenant-wide search and direct Overview can open an unassigned Case', async () => {
    const { router } = open(); await screen.findByRole('heading', { name: 'Case workspace', level: 1 });
    fireEvent.click(screen.getByRole('button', { name: 'Assigned' })); await screen.findByRole('button', { name: 'Open Overview for Case 1' });
    expect(calls.at(-1)?.search).toBe('?page=0&size=25'); expect(screen.queryByLabelText('Case name')).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Case-name search' })); await search();
    await act(async () => { await router.navigate('/case-workspace/90'); }); await screen.findByText('Case summary');
    expect(calls.at(-1)?.path).toBe('/api/v2/cases/90/overview'); expect(screen.getByText('90')).toBeTruthy();
    expect(featureCalls().every(call => call.path.startsWith('/api/v2/cases/'))).toBe(true);
  });
  it('Back restores the same bounded query/page and focus without a refetch; reopening Overview invokes its audited read', async () => {
    open(); await screen.findByRole('heading', { name: 'Case workspace', level: 1 }); await search();
    fireEvent.click(screen.getByRole('button', { name: 'Next' })); await screen.findByRole('button', { name: 'Open Overview for Case 61' });
    fireEvent.click(screen.getByRole('button', { name: 'Open Overview for Case 61' })); await screen.findByText('Case summary');
    const count = calls.length; fireEvent.click(screen.getByRole('link', { name: 'Back to Case workspace' }));
    const button = await screen.findByRole('button', { name: 'Open Overview for Case 61' });
    expect((screen.getByLabelText('Case name') as HTMLInputElement).value).toBe('Match'); expect(calls).toHaveLength(count); expect(document.activeElement).toBe(button);
    fireEvent.click(button); await screen.findByText('Case summary');
    expect(calls.filter(call => call.path === '/api/v2/cases/61/overview')).toHaveLength(2);
    expect(window.location.search).toBe(''); expect(JSON.stringify(window.history.state)).not.toContain('Match');
  });
  it.each([[400, 'invalid_case_request', 'not accepted'], [403, 'case_read_denied', 'not permitted'], [404, 'case_request_failed', 'Case unavailable'], [503, 'case_audit_unavailable', 'required read audit'], [503, 'case_read_unavailable', 'service is unavailable']] as const)('Overview %s / %s clears previous entity and preserves safe feedback/session', async (status, code, message) => {
    const { router } = open('/case-workspace/36'); await screen.findByText('Case summary');
    overviewStatus = status; overviewError = code;
    await act(async () => { await router.navigate('/case-workspace/99'); });
    expect((await screen.findByRole('alert')).textContent).toContain(message);
    expect(screen.queryByText('Case summary')).toBeNull(); expect(screen.queryByText('36')).toBeNull(); expect(screen.queryByText('Private raw witness')).toBeNull(); expect(readAccessToken()).toBe('synthetic');
  });
  it('safe signed-out deep link restores after verified login; current 401 requires explicit re-login and fresh Overview only', async () => {
    open('/case-workspace/36', false); await screen.findByRole('heading', { name: 'Sign in' }); await signIn(); await screen.findByText('Case summary');
    expect(window.location.pathname).toBe('/case-workspace/36'); expect(window.history.state.usr).toBeNull();
    const navigation = document.querySelector('details')!; navigation.open = true;
    expect(screen.getByRole('link', { name: 'Case workspace' }).getAttribute('aria-current')).toBe('page');
    overviewStatus = 401; fireEvent.click(screen.getByRole('button', { name: 'Read Overview again' })); await screen.findByRole('heading', { name: 'Sign in' });
    const count = calls.filter(call => call.path.includes('/overview')).length; expect(readAccessToken()).toBeNull();
    expect(calls.some(call => call.path.endsWith('/refresh') || call.path.endsWith('/logout'))).toBe(false);
    overviewStatus = 200; await signIn(); await screen.findByText('Case summary'); expect(calls.filter(call => call.path.includes('/overview'))).toHaveLength(count + 1);
  });
  it('logout clears query/results, including failed credential erasure; new sign-in gets no old workspace or replay', async () => {
    open(); await screen.findByRole('heading', { name: 'Case workspace', level: 1 }); await search();
    vi.spyOn(Storage.prototype, 'removeItem').mockImplementation(() => { throw new Error('Private failure'); });
    fireEvent.click(screen.getByRole('button', { name: 'Logout' })); await screen.findByRole('heading', { name: 'Sign in' });
    expect(screen.queryByDisplayValue('Match')).toBeNull(); expect(screen.queryByRole('button', { name: /Open Overview/ })).toBeNull();
    await signIn(); await screen.findByRole('heading', { name: 'My Shale', level: 1 });
    document.querySelector('details')!.open = true; fireEvent.click(screen.getByRole('link', { name: 'Case workspace' }));
    await screen.findByRole('heading', { name: 'Case workspace', level: 1 }); expect((screen.getByLabelText('Case name') as HTMLInputElement).value).toBe('');
    expect(calls.filter(call => call.path === '/api/v2/cases/search-page')).toHaveLength(1);
  });
  it('a current search 401 discards the query; explicit login returns to blank workspace without replay', async () => {
    open(); await screen.findByRole('heading', { name: 'Case workspace', level: 1 }); await search();
    override = path => path.endsWith('/search-page') ? json({}, 401) : undefined;
    fireEvent.submit(screen.getByRole('search')); await screen.findByRole('heading', { name: 'Sign in' });
    expect(screen.queryByDisplayValue('Match')).toBeNull(); expect(readAccessToken()).toBeNull();
    const count = calls.filter(c => c.path.endsWith('/search-page')).length;
    override = undefined; await signIn(); await screen.findByRole('heading', { name: 'Case workspace', level: 1 });
    expect((screen.getByLabelText('Case name') as HTMLInputElement).value).toBe('');
    expect(calls.filter(c => c.path.endsWith('/search-page'))).toHaveLength(count);
  });
  it.each(['success', 'failure'])('identity replacement clears query and aborts pending search, ignoring late %s', async late => {
    let done!: (response: Response) => void, fail!: (error: Error) => void; let signal: AbortSignal | null | undefined;
    const { router } = open(); await screen.findByRole('heading', { name: 'Case workspace', level: 1 }); await search();
    override = (path, init) => path.endsWith('/search-page') ? (signal = init?.signal, new Promise<Response>((a, b) => { done = a; fail = b; })) : undefined;
    fireEvent.submit(screen.getByRole('search')); fireEvent.click(screen.getByRole('button', { name: 'Logout' }));
    await screen.findByRole('heading', { name: 'Sign in' }); expect(signal?.aborted).toBe(true);
    const replacement = { ...user, userId: 2, shaleClientId: 2, displayName: 'Replacement identity' };
    override = path => path === '/api/auth/login' ? json({ authenticated: true, tokenType: 'Bearer', accessToken: 'synthetic-new', expiresInSeconds: 3600, user: replacement })
      : path === '/api/auth/me' ? json(replacement) : undefined;
    await signIn(); await screen.findByRole('heading', { name: 'My Shale', level: 1 });
    await act(async () => { await router.navigate('/case-workspace'); });
    await screen.findByRole('heading', { name: 'Case workspace', level: 1 });
    await act(async () => { if (late === 'success') done(json({ items: [item(99, 'Old identity result')], page: 0, size: 25, hasMore: false })); else fail(new Error('Private old identity failure')); });
    expect((screen.getByLabelText('Case name') as HTMLInputElement).value).toBe('');
    expect(screen.queryByRole('button', { name: /Open Overview/ })).toBeNull(); expect(screen.queryByRole('alert')).toBeNull();
    expect(readAccessToken()).toBe('synthetic-new'); expect(screen.getAllByText('Replacement identity').length).toBeGreaterThan(0);
    expect(calls.filter(c => c.path.endsWith('/search-page'))).toHaveLength(2);
  });
  it('does not refetch on focus/reconnect/theme or rerender and preserves legacy editor routes', async () => {
    const { router } = open('/case-workspace/36'); await screen.findByText('Case summary'); const count = calls.length;
    fireEvent(window, new Event('focus')); fireEvent(window, new Event('online')); fireEvent.change(screen.getByLabelText('Theme (this session)'), { target: { value: 'dark' } });
    await act(async () => {}); expect(calls).toHaveLength(count);
    await act(async () => { await router.navigate('/cases/7'); }); await screen.findByRole('heading', { name: 'Legacy editable Case' });
    expect(screen.getByRole('button', { name: 'Edit details' })).toBeTruthy();
    expect(calls.filter(call => call.path.startsWith('/api/cases/7'))).toHaveLength(3);
  });
  it.each(['success', 'failure'])('superseded query/route ignores late %s and aborts its caller while preserving current session', async late => {
    let done!: (response: Response) => void, fail!: (error: Error) => void; let signal: AbortSignal | null | undefined;
    override = (path, init) => path.endsWith('/search-page') ? (signal = init?.signal, new Promise<Response>((a, b) => { done = a; fail = b; })) : undefined;
    const { router } = open(); await screen.findByRole('heading', { name: 'Case workspace', level: 1 });
    fireEvent.change(screen.getByLabelText('Case name'), { target: { value: 'Old' } }); fireEvent.submit(screen.getByRole('search'));
    fireEvent.click(screen.getByRole('button', { name: 'Assigned' })); await screen.findByRole('button', { name: 'Open Overview for Case 1' }); expect(signal?.aborted).toBe(true);
    await act(async () => { if (late === 'success') done(json({ items: [item(99, 'Old private result')], page: 0, size: 25, hasMore: false })); else fail(new Error('Old private error')); });
    expect(screen.queryByText('Old private result')).toBeNull(); expect(screen.queryByRole('alert')).toBeNull();
    await act(async () => { await router.navigate('/case-workspace/99'); }); await screen.findByText('Case summary'); expect(readAccessToken()).toBe('synthetic');
  });
  it('accepted empty and malformed pages have distinct feedback, with no fallback detail', async () => {
    open(); await screen.findByRole('heading', { name: 'Case workspace', level: 1 });
    fireEvent.change(screen.getByLabelText('Case name'), { target: { value: 'Absent' } }); fireEvent.submit(screen.getByRole('search'));
    await waitFor(() => expect(screen.getByRole('status').textContent).toBe('No cases on this page.')); expect(screen.queryByRole('alert')).toBeNull();
    override = path => path.endsWith('/search-page') ? json({ items: [], page: 0, size: 25, hasMore: false, total: 0 }) : undefined;
    fireEvent.submit(screen.getByRole('search')); expect((await screen.findByRole('alert')).textContent).toContain('could not be validated'); expect(screen.queryByText('No cases on this page.')).toBeNull();
  });
  it('continues through page 100 and stops Next despite hasMore, inviting narrower search', async () => {
    selectedRecords = Array.from({ length: 2600 }, (_, i) => item(i + 36));
    open(); await screen.findByRole('heading', { name: 'Case workspace', level: 1 }); await search();
    for (let number = 1; number <= 100; number++) {
      fireEvent.click(screen.getByRole('button', { name: 'Next' })); await screen.findByRole('button', { name: `Open Overview for Case ${36 + number * 25}` });
    }
    expect((screen.getByRole('button', { name: 'Next' }) as HTMLButtonElement).disabled).toBe(true);
    expect(screen.getByText(/result window is exhausted/)).toBeTruthy(); expect(calls.at(-1)?.body).toEqual({ query: 'Match', page: 100, size: 25 });
    fireEvent.click(screen.getByRole('button', { name: 'Assigned' })); await screen.findByRole('button', { name: 'Open Overview for Case 1' });
    expect(calls.at(-1)?.search).toBe('?page=0&size=25');
  }, 60000);
  it('StrictMode setup probe dispatches one audited Overview without a replay', async () => {
    open('/case-workspace/36', true, true); await screen.findByText('Case summary');
    expect(featureCalls().map(call => call.path)).toEqual(['/api/v2/cases/36/overview']);
    expect(screen.getAllByText('Case summary')).toHaveLength(1);
  });
});
