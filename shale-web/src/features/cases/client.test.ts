import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { bindSessionRequests, FeatureRequestCancelled, SessionRequestDiscarded, sessionFetch, captureSessionRequestGuard } from '../../sessionRequests';
import { SessionAttemptCancelled } from '../../sessionDeadline';
import { InvalidCaseResponse, validateOverview, validatePage } from './contracts';
import { readCaseOverview, readCasePage } from './client';

export const item = (id = 36) => ({ caseId: id, caseName: 'Synthetic match', caseNumber: null, status: null, practiceArea: null, responsibleAttorney: null, primaryLegalAssistant: null, updatedAt: null });
const page = (number = 0, hasMore = false) => ({ items: hasMore ? Array.from({ length: 25 }, (_, i) => item(i + 36)) : [item()], page: number, size: 25, hasMore });
const json = (value: unknown, status = 200) => new Response(JSON.stringify(value), { status });
function deferred<T>() { let resolve!: (value: T) => void, reject!: (error: Error) => void; const promise = new Promise<T>((a, b) => { resolve = a; reject = b; }); return { promise, resolve, reject }; }
let dispose: () => void;
const fetchMock = vi.fn<typeof fetch>();
beforeEach(() => { fetchMock.mockReset(); vi.stubGlobal('fetch', fetchMock); dispose = bindSessionRequests('synthetic', () => true, vi.fn()); });
afterEach(() => { dispose(); vi.useRealTimers(); vi.unstubAllGlobals(); });

describe('exact R2 unknown JSON contract', () => {
  it('accepts nullable fields, empty names, bounded relationships and stored local timestamps without conversion', () => {
    const value = { ...item(), caseName: '', caseNumber: 'N'.repeat(200), status: { id: 1, name: '', color: '#aB1234' }, practiceArea: { id: 2, name: 'Area' }, responsibleAttorney: { userId: 3, displayName: 'Name' }, updatedAt: '2024-02-29T23:59:59.123456789' };
    expect(validateOverview(value, 36)).toEqual(value);
    expect(validateOverview({ ...item(), updatedAt: '2026-10-10T12:34' }).updatedAt).toBe('2026-10-10T12:34');
    expect(validateOverview({ ...item(), updatedAt: '0001-01-01T00:00:00' }).updatedAt).toBe('0001-01-01T00:00:00');
  });
  it.each([
    { ...item(), description: 'Excluded' }, { ...item(), caseName: null }, { ...item(), caseName: 'x'.repeat(256) },
    { ...item(), caseNumber: 'x'.repeat(201) }, { ...item(), caseId: 2147483648 }, { ...item(), caseId: 0 }, { ...item(), caseId: 1.5 },
    { ...item(), status: { id: 1, name: 'S', color: 'red' } }, { ...item(), status: { id: 1, name: 'S' } },
    { ...item(), practiceArea: { id: 1, name: 'A', color: '#abcdef' } },
    { ...item(), responsibleAttorney: { userId: 1, displayName: null } },
    { ...item(), primaryLegalAssistant: { userId: -1, displayName: 'A' } },
    Object.fromEntries(Object.entries(item()).filter(([key]) => key !== 'updatedAt')),
  ])('rejects unauthorized fields, missing/null contracts and unsafe identities %#', value => {
    expect(() => validateOverview(value)).toThrow(InvalidCaseResponse);
  });
  it.each(['2025-02-29T00:00:00', '2024-04-31T00:00:00', '2024-01-01T24:00:00', '2024-01-01T00:60:00', '2024-01-01T00:00:60', '0000-01-01T00:00:00', '2024-01-01', '2024-01-01T00:00:00Z', '2024-01-01T00:00:00-06:00', '2024-13-01T00:00:00'])('rejects invalid local timestamp %s', updatedAt => {
    expect(() => validateOverview({ ...item(), updatedAt })).toThrow(InvalidCaseResponse);
  });
  it('requires the requested entity and page, fixed size, consistent continuation and unique IDs', () => {
    expect(() => validateOverview(item(), 37)).toThrow(InvalidCaseResponse);
    expect(validatePage(page(100, true), 100).hasMore).toBe(true);
    for (const value of [{ ...page(), total: 1 }, { ...page(), page: 1 }, { ...page(), size: 24 }, { ...page(), hasMore: 1 }, { ...page(), hasMore: true }, { ...page(), items: [item(), item()] }, { ...page(), items: Array.from({ length: 26 }, (_, i) => item(i + 1)) }])
      expect(() => validatePage(value, 0)).toThrow(InvalidCaseResponse);
    expect(validatePage({ ...page(), items: [] }, 0).items).toEqual([]);
  });
});

describe('bounded deliberate read transport', () => {
  it('sends search in the body at later/ceiling pages and uses only the three additive contracts', async () => {
    fetchMock.mockResolvedValueOnce(json(page(1))).mockResolvedValueOnce(json(page(100, true))).mockResolvedValueOnce(json(item(99)));
    await readCasePage('synthetic', { mode: 'search', query: ' Élan_%[ ', page: 1 }, new AbortController());
    await readCasePage('synthetic', { mode: 'assigned', query: '', page: 100 }, new AbortController());
    await readCaseOverview('synthetic', 99, new AbortController());
    const [url, init] = fetchMock.mock.calls[0]; expect(String(url)).toMatch(/\/api\/v2\/cases\/search-page$/); expect(String(url)).not.toContain('Élan');
    expect(init).toMatchObject({ method: 'POST', cache: 'no-store', body: JSON.stringify({ query: 'Élan_%[', page: 1, size: 25 }) });
    expect(String(fetchMock.mock.calls[1][0])).toMatch(/assigned-page\?page=100&size=25$/);
    expect(String(fetchMock.mock.calls[2][0])).toMatch(/99\/overview$/);
  });
  it('invalid or blank search and page 101 never dispatch', async () => {
    for (const selection of [{ mode: 'search' as const, query: '', page: 0 }, { mode: 'search' as const, query: 'x'.repeat(101), page: 0 }, { mode: 'assigned' as const, query: '', page: 101 }])
      await expect(readCasePage('synthetic', selection, new AbortController())).rejects.toMatchObject({ kind: 'validation' });
    expect(fetchMock).not.toHaveBeenCalled();
  });
  it.each([[400, 'invalid_case_request', 'validation'], [403, 'case_read_denied', 'forbidden'], [404, 'case_request_failed', 'unavailable'], [503, 'case_audit_unavailable', 'audit'], [503, 'case_read_unavailable', 'service'], [503, 'case_payload_oversized', 'oversized'], [503, 'case_read_timeout', 'timeout']] as const)('classifies safe HTTP %s / %s without session rejection or raw feedback', async (status, error, kind) => {
    fetchMock.mockResolvedValue(json({ error, message: 'Sensitive raw witness' }, status));
    await expect(readCaseOverview('synthetic', 36, new AbortController())).rejects.toMatchObject({ kind, message: kind });
  });
  it.each(['malformed', 'invalid-utf8', 'oversized-overview', 'oversized-page'])('rejects %s actual bytes, regardless of Content-Length', async mode => {
    let response: Response;
    if (mode === 'invalid-utf8') response = new Response(new Uint8Array([0xff]));
    else response = new Response(mode === 'malformed' ? '<html>failure</html>' : ' '.repeat(mode === 'oversized-page' ? 128 * 1024 + 1 : 8193), { headers: { 'Content-Length': '1' } });
    fetchMock.mockResolvedValue(response);
    const read = mode === 'oversized-page' ? readCasePage('synthetic', { mode: 'assigned', query: '', page: 0 }, new AbortController()) : readCaseOverview('synthetic', 36, new AbortController());
    await expect(read).rejects.toMatchObject({ kind: mode.startsWith('oversized') ? 'oversized' : 'malformed' });
  });
  it('counts multibyte bytes across streamed chunks before JSON rendering', async () => {
    fetchMock.mockResolvedValue(new Response(new ReadableStream({ start(controller) {
      controller.enqueue(new TextEncoder().encode('é'.repeat(3000))); controller.enqueue(new TextEncoder().encode('é'.repeat(2000))); controller.close();
    } })));
    await expect(readCaseOverview('synthetic', 36, new AbortController())).rejects.toMatchObject({ kind: 'oversized' });
  });
  it.each(['fetch', 'body'])('bounds stalled %s and cleans up timers with no retry', async stage => {
    vi.useFakeTimers(); const held = deferred<Response>();
    fetchMock.mockReturnValue(stage === 'fetch' ? held.promise : Promise.resolve(new Response(new ReadableStream({ start() {} }))));
    const read = readCaseOverview('synthetic', 36, new AbortController()); const outcome = expect(read).rejects.toMatchObject({ kind: 'timeout' });
    await vi.advanceTimersByTimeAsync(8000); await outcome; expect(vi.getTimerCount()).toBe(0); expect(fetchMock).toHaveBeenCalledTimes(1);
    held.resolve(json(item())); await vi.advanceTimersByTimeAsync(1000); expect(fetchMock).toHaveBeenCalledTimes(1);
  });
  it('fetch and body share one eight-second budget', async () => {
    vi.useFakeTimers(); const headers = deferred<Response>(); fetchMock.mockReturnValue(headers.promise);
    const read = readCaseOverview('synthetic', 36, new AbortController()); const outcome = expect(read).rejects.toMatchObject({ kind: 'timeout' });
    await vi.advanceTimersByTimeAsync(6000); headers.resolve(new Response(new ReadableStream({ start() {} })));
    await vi.advanceTimersByTimeAsync(1999); expect(vi.getTimerCount()).toBe(1);
    await vi.advanceTimersByTimeAsync(1); await outcome; expect(vi.getTimerCount()).toBe(0);
  });
  it.each(['caller', 'session'])('composes %s cancellation at body time without late delivery', async owner => {
    let stream!: ReadableStreamDefaultController<Uint8Array>; const cancel = vi.fn();
    fetchMock.mockResolvedValue(new Response(new ReadableStream({ start(controller) { stream = controller; }, cancel })));
    const controller = new AbortController(); const read = readCaseOverview('synthetic', 36, controller);
    const outcome = expect(read).rejects.toBeInstanceOf(owner === 'session' ? SessionRequestDiscarded : SessionAttemptCancelled);
    await new Promise(resolve => setTimeout(resolve, 0));
    if (owner === 'session') dispose(); else controller.abort();
    await outcome; expect(cancel).toHaveBeenCalledTimes(1); expect(() => stream.enqueue(new TextEncoder().encode('{}'))).toThrow();
  });
  it('confirmed 401 consumes the session and aborts the rejected transport body', async () => {
    let wire: AbortSignal | null | undefined;
    fetchMock.mockImplementation(async (_input, init) => { wire = init?.signal; return json({}, 401); });
    await expect(readCaseOverview('synthetic', 1, new AbortController())).rejects.toBeInstanceOf(SessionRequestDiscarded);
    expect(wire?.aborted).toBe(true); expect(captureSessionRequestGuard('synthetic')()).toBe(false);
  });
  it.each(['success', 'failure', '401'])('discarded caller late %s cannot deliver or reject the session', async late => {
    const held = deferred<Response>(), rejected = vi.fn(); dispose(); dispose = bindSessionRequests('synthetic', () => true, rejected);
    fetchMock.mockReturnValue(held.promise); const controller = new AbortController();
    const read = readCaseOverview('synthetic', 36, controller); const outcome = expect(read).rejects.toBeInstanceOf(SessionAttemptCancelled);
    controller.abort(); await outcome;
    if (late === 'failure') held.reject(new Error('Sensitive late error')); else held.resolve(json(item(), late === '401' ? 401 : 200));
    await Promise.resolve(); expect(rejected).not.toHaveBeenCalled(); expect(fetchMock).toHaveBeenCalledTimes(1);
  });
  it('retains caller/session abort across the headers-to-body gap and removes forwarding on disposal', async () => {
    let wire: AbortSignal | null | undefined;
    const caller = new AbortController();
    fetchMock.mockImplementation(async (_url, init) => { wire = init?.signal; return json(item()); });
    const response = await sessionFetch('synthetic', '/synthetic-only', { signal: caller.signal });
    expect(wire?.aborted).toBe(false); caller.abort(); expect(wire?.aborted).toBe(true);
    await expect(response.boundedText(8192)).rejects.toBeInstanceOf(FeatureRequestCancelled);
    expect(captureSessionRequestGuard('synthetic')()).toBe(true); response.dispose();
    const next = new AbortController();
    const headers = await sessionFetch('synthetic', '/synthetic-only', { signal: next.signal });
    dispose(); expect(wire?.aborted).toBe(true);
    await expect(headers.boundedText(8192)).rejects.toBeInstanceOf(SessionRequestDiscarded); headers.dispose();
  });
  it.each(['success', 'malformed', 'network', 'timeout'])('cleans caller listeners and timers after %s', async outcome => {
    vi.useFakeTimers(); const controller = new AbortController(); const listeners = new Set<unknown>();
    const add = controller.signal.addEventListener.bind(controller.signal), remove = controller.signal.removeEventListener.bind(controller.signal);
    vi.spyOn(controller.signal, 'addEventListener').mockImplementation((type, listener, options) => { if (type === 'abort') listeners.add(listener); add(type, listener, options); });
    vi.spyOn(controller.signal, 'removeEventListener').mockImplementation((type, listener, options) => { if (type === 'abort') listeners.delete(listener); remove(type, listener, options); });
    if (outcome === 'timeout') fetchMock.mockReturnValue(new Promise(() => {}));
    else if (outcome === 'network') fetchMock.mockRejectedValue(new TypeError('Private network witness'));
    else fetchMock.mockResolvedValue(outcome === 'success' ? json(item()) : json({ excluded: true }));
    const read = readCaseOverview('synthetic', 36, controller);
    const result = outcome === 'success' ? expect(read).resolves.toEqual(item()) : expect(read).rejects.toMatchObject({ kind: outcome });
    if (outcome === 'timeout') await vi.advanceTimersByTimeAsync(8000);
    await result; expect(listeners.size).toBe(0); expect(vi.getTimerCount()).toBe(0);
  });
  it('pre-aborted caller never dispatches or consumes current session', async () => {
    const controller = new AbortController(); controller.abort();
    await expect(readCaseOverview('synthetic', 36, controller)).rejects.toBeInstanceOf(SessionAttemptCancelled);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
