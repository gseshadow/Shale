import { StrictMode } from 'react';
import { act, cleanup, renderHook, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { getCurrentUser } from './api';
import { createMemoryCredentialStore } from './credentialStore';
let store = createMemoryCredentialStore();
const readAccessToken = () => store.read();
const storeAccessToken = (token: string) => store.store(token);
const clearAccessToken = () => store.clear();
const useTestSession = () => useStartupSession(store);
import { STARTUP_VERIFICATION_TIMEOUT_MS, useStartupSession } from './useStartupSession';

const user = { authenticated: true, userId: 1, shaleClientId: 1, email: null, displayName: 'Synthetic User',
  nameFirst: null, nameLast: null, isAdmin: false, isAttorney: false, initials: null, color: null };
const response = (body: unknown = user, status = 200) => new Response(JSON.stringify(body), { status });
function deferred<T = Response>() {
  let resolve!: (response: T) => void;
  let reject!: (error: Error) => void;
  const promise = new Promise<T>((done, fail) => { resolve = done; reject = fail; });
  return { promise, resolve, reject };
}
const fetchMock = vi.fn<typeof fetch>();
beforeEach(() => { store = createMemoryCredentialStore(); fetchMock.mockReset(); vi.stubGlobal('fetch', fetchMock); });
afterEach(() => { cleanup(); store = createMemoryCredentialStore(); vi.useRealTimers(); vi.unstubAllGlobals(); });

describe('startup verification contract', () => {
  it('signs out without a stored bearer and makes no request', async () => {
    const { result } = renderHook(useTestSession);
    await waitFor(() => expect(result.current.authState.verification).toBeNull());
    expect(result.current.authState.user).toBeNull(); expect(fetchMock).not.toHaveBeenCalled();
  });
  it('keeps identity and feature credentials unavailable until a usable /me succeeds', async () => {
    storeAccessToken('synthetic-startup'); const held = deferred(); fetchMock.mockReturnValue(held.promise);
    const { result } = renderHook(useTestSession);
    expect(result.current.authState).toEqual({ accessToken: null, user: null, verification: 'pending' });
    await act(async () => { held.resolve(response()); });
    expect(result.current.authState.user).toEqual(user); expect(result.current.authState.verification).toBeNull();
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(String(fetchMock.mock.calls[0][0])).toMatch(/\/api\/auth\/me$/);
  });
  it('clears local storage only on the /me contract’s confirmed 401 rejection', async () => {
    storeAccessToken('synthetic-startup'); fetchMock.mockResolvedValue(response({}, 401));
    const { result } = renderHook(useTestSession);
    await waitFor(() => expect(result.current.authState.verification).toBeNull());
    expect(readAccessToken()).toBeNull(); expect(result.current.authState.user).toBeNull();
  });
  it.each([400, 403, 404, 408, 409, 429, 500, 502, 503])('retains the bearer and blocks access on HTTP %s', async status => {
    storeAccessToken('synthetic-startup'); fetchMock.mockResolvedValue(response({}, status));
    const { result } = renderHook(useTestSession);
    await waitFor(() => expect(result.current.authState.verification).toBe('unavailable'));
    expect(readAccessToken()).not.toBeNull(); expect(result.current.authState.user).toBeNull();
    expect(result.current.authState.accessToken).toBeNull();
  });
  it('retains the bearer on transport failure, repeated failure, then explicit Retry succeeds', async () => {
    storeAccessToken('synthetic-startup'); fetchMock.mockRejectedValueOnce(new TypeError('Synthetic offline'))
      .mockResolvedValueOnce(response({}, 503)).mockResolvedValueOnce(response());
    const { result } = renderHook(useTestSession);
    await waitFor(() => expect(result.current.authState.verification).toBe('unavailable'));
    await act(async () => { await result.current.retry(); });
    expect(result.current.authState.verification).toBe('unavailable'); expect(readAccessToken()).not.toBeNull();
    await act(async () => { await result.current.retry(); });
    expect(result.current.authState.user).toEqual(user); expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(fetchMock.mock.calls.every(([url, init]) => String(url).endsWith('/api/auth/me') && init?.method === 'GET')).toBe(true);
  });
  it.each([null, [], {}, { ...user, authenticated: false }, { ...user, authenticated: 'true' },
    { ...user, userId: 0 }, { ...user, shaleClientId: -1 }, { ...user, userId: 1.5 },
    { ...user, isAdmin: 'false' }, { ...user, isAttorney: undefined }, { ...user, displayName: {} },
    { ...user, color: undefined }])('never installs an unusable successful response %#', async body => {
    storeAccessToken('synthetic-startup'); fetchMock.mockResolvedValue(response(body));
    const { result } = renderHook(useTestSession);
    await waitFor(() => expect(result.current.authState.verification).toBe('unavailable'));
    expect(result.current.authState.user).toBeNull(); expect(readAccessToken()).not.toBeNull();
  });
  it.each([200, 204])('treats invalid/empty JSON at HTTP %s as uncertainty', async status => {
    storeAccessToken('synthetic-startup'); fetchMock.mockResolvedValue(new Response(status === 204 ? null : '<html>Unavailable</html>', { status }));
    const { result } = renderHook(useTestSession);
    await waitFor(() => expect(result.current.authState.verification).toBe('unavailable'));
    expect(result.current.authState.user).toBeNull(); expect(readAccessToken()).not.toBeNull();
  });
  it('coalesces duplicate explicit attempts synchronously while pending', async () => {
    storeAccessToken('synthetic-startup'); const held = deferred(); fetchMock.mockReturnValue(held.promise);
    const { result } = renderHook(useTestSession);
    act(() => { void result.current.retry(); void result.current.retry(); });
    expect(fetchMock).toHaveBeenCalledTimes(1);
    await act(async () => { held.resolve(response()); });
  });
  it.each([200, 401, 503])('local sign-out invalidates late HTTP %s results', async status => {
    storeAccessToken('synthetic-startup'); const held = deferred(); fetchMock.mockReturnValue(held.promise);
    const { result } = renderHook(useTestSession);
    act(() => result.current.signOut());
    await act(async () => { held.resolve(response(user, status)); });
    expect(result.current.authState).toEqual({ accessToken: null, user: null, verification: null });
    expect(readAccessToken()).toBeNull();
  });
  it.each([200, 401])('discards late HTTP %s after direct storage replacement and verifies the replacement explicitly', async status => {
    storeAccessToken('synthetic-startup'); const held = deferred(); fetchMock.mockReturnValueOnce(held.promise).mockResolvedValueOnce(response());
    const { result } = renderHook(useTestSession);
    storeAccessToken('synthetic-replacement');
    await act(async () => { held.resolve(response(user, status)); });
    expect(result.current.authState.verification).toBe('unavailable'); expect(result.current.authState.user).toBeNull();
    expect(readAccessToken()).toBe('synthetic-replacement');
    await act(async () => { await result.current.retry(); });
    expect(result.current.authState.user).toEqual(user);
  });
  it('does not resurrect a directly removed credential', async () => {
    storeAccessToken('synthetic-startup'); const held = deferred(); fetchMock.mockReturnValue(held.promise);
    const { result } = renderHook(useTestSession); clearAccessToken();
    await act(async () => { held.resolve(response()); });
    expect(result.current.authState.user).toBeNull(); expect(result.current.authState.verification).toBeNull();
  });
  it.each([200, 401, 503])('newer attempt wins over late HTTP %s from a superseded attempt', async status => {
    storeAccessToken('synthetic-startup'); const old = deferred(), latest = deferred();
    fetchMock.mockReturnValueOnce(old.promise).mockReturnValueOnce(latest.promise);
    const { result } = renderHook(useTestSession); storeAccessToken('synthetic-replacement');
    let retry!: Promise<void>; act(() => { retry = result.current.retry(); });
    await act(async () => { latest.resolve(response()); await retry; });
    await act(async () => { old.resolve(response(user, status)); });
    expect(result.current.authState.user).toEqual(user); expect(result.current.authState.accessToken).toBe('synthetic-replacement');
    expect(readAccessToken()).toBe('synthetic-replacement');
  });
  it('a verified login invalidates the old startup attempt', async () => {
    storeAccessToken('synthetic-startup'); const held = deferred(); fetchMock.mockReturnValue(held.promise);
    const { result } = renderHook(useTestSession);
    act(() => result.current.signIn('synthetic-login', { ...user, userId: 2 }));
    await act(async () => { held.resolve(response({}, 401)); });
    expect(result.current.authState.user?.userId).toBe(2); expect(readAccessToken()).toBe('synthetic-login');
  });
  it('unmount invalidates a late rejection without touching storage', async () => {
    storeAccessToken('synthetic-startup'); const held = deferred(); fetchMock.mockReturnValue(held.promise);
    const { unmount } = renderHook(useTestSession); unmount();
    await act(async () => { held.resolve(response({}, 401)); });
    expect(readAccessToken()).not.toBeNull();
  });
  it('StrictMode cleanup invalidates the superseded mount attempt', async () => {
    storeAccessToken('synthetic-startup'); const old = deferred(), latest = deferred();
    fetchMock.mockReturnValueOnce(old.promise).mockReturnValueOnce(latest.promise);
    const { result } = renderHook(useTestSession, { wrapper: StrictMode });
    await act(async () => { latest.resolve(response()); });
    await act(async () => { old.resolve(response({}, 401)); });
    expect(result.current.authState.user).toEqual(user); expect(readAccessToken()).not.toBeNull();
  });
});

describe('bounded startup and explicit Retry', () => {
  beforeEach(() => { vi.useFakeTimers(); storeAccessToken('synthetic-startup'); });
  const advance = async (ms = STARTUP_VERIFICATION_TIMEOUT_MS) => {
    await act(async () => { await vi.advanceTimersByTimeAsync(ms); });
  };
  it.each(['fetch', 'body'])('ends stalled %s at eight seconds, retains storage and blocks identity', async phase => {
    const held = deferred(), body = deferred<unknown>();
    fetchMock.mockReturnValue(phase === 'fetch' ? held.promise : Promise.resolve({ ok: true, status: 200, json: () => body.promise } as Response));
    const { result } = renderHook(useTestSession);
    await advance(STARTUP_VERIFICATION_TIMEOUT_MS - 1);
    expect(result.current.authState.verification).toBe('pending');
    await advance(1);
    expect(result.current.authState).toEqual({ user: null, accessToken: null, verification: 'unavailable' });
    expect(readAccessToken()).toBe('synthetic-startup');
    expect(fetchMock.mock.calls[0][1]?.signal?.aborted).toBe(true);
    expect(vi.getTimerCount()).toBe(0);
    // Ignored-abort late failures are consumed and cannot reclassify uncertainty.
    await act(async () => {
      if (phase === 'fetch') held.reject(new TypeError('Synthetic late failure'));
      else body.reject(new TypeError('Synthetic late body failure'));
    });
    expect(result.current.authState.verification).toBe('unavailable');
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
  it.each([200, 401])('honors HTTP %s before deadline and cleans its timer', async status => {
    const held = deferred(); fetchMock.mockReturnValue(held.promise);
    const { result } = renderHook(useTestSession);
    await advance(STARTUP_VERIFICATION_TIMEOUT_MS - 1);
    await act(async () => { held.resolve(response(user, status)); });
    expect(result.current.authState.verification).toBeNull();
    expect(result.current.authState.user).toEqual(status === 200 ? user : null);
    expect(readAccessToken()).toBe(status === 200 ? 'synthetic-startup' : null);
    expect(vi.getTimerCount()).toBe(0);
    await advance();
    expect(result.current.authState.verification).toBeNull();
  });
  it('gives every Retry a full fresh deadline and coalesces duplicate pending Retry', async () => {
    const old = deferred(), retry = deferred();
    fetchMock.mockReturnValueOnce(old.promise).mockReturnValueOnce(retry.promise).mockResolvedValueOnce(response());
    const { result } = renderHook(useTestSession);
    await advance(); await advance(30_000); // no automatic recovery
    expect(fetchMock).toHaveBeenCalledTimes(1);
    act(() => { void result.current.retry(); void result.current.retry(); });
    expect(fetchMock).toHaveBeenCalledTimes(2); expect(vi.getTimerCount()).toBe(1);
    await advance(STARTUP_VERIFICATION_TIMEOUT_MS - 1);
    expect(result.current.authState.verification).toBe('pending');
    await advance(1);
    expect(result.current.authState.verification).toBe('unavailable'); expect(readAccessToken()).not.toBeNull();
    expect(vi.getTimerCount()).toBe(0);
    await act(async () => { await result.current.retry(); });
    expect(result.current.authState.user).toEqual(user); expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(vi.getTimerCount()).toBe(0);
  });
  it.each([200, 401])('late HTTP %s cannot affect timeout or a newer successful Retry', async status => {
    const old = deferred(), latest = deferred();
    fetchMock.mockReturnValueOnce(old.promise).mockReturnValueOnce(latest.promise);
    const { result } = renderHook(useTestSession);
    await advance();
    act(() => { void result.current.retry(); });
    await act(async () => { old.resolve(response(user, status)); });
    expect(result.current.authState.verification).toBe('pending'); expect(result.current.authState.user).toBeNull();
    expect(readAccessToken()).toBe('synthetic-startup'); expect(vi.getTimerCount()).toBe(1);
    await act(async () => { latest.resolve(response()); });
    expect(result.current.authState.user).toEqual(user); expect(vi.getTimerCount()).toBe(0);
  });
  it.each([200, 401])('a timed-out HTTP %s cannot overwrite an already verified newer Retry', async status => {
    const old = deferred(); fetchMock.mockReturnValueOnce(old.promise).mockResolvedValueOnce(response());
    const { result } = renderHook(useTestSession); await advance();
    await act(async () => { await result.current.retry(); });
    await act(async () => { old.resolve(response({ ...user, userId: 2 }, status)); });
    expect(result.current.authState.user).toEqual(user); expect(readAccessToken()).toBe('synthetic-startup');
    expect(vi.getTimerCount()).toBe(0);
  });
  it('StrictMode cancels the replayed mount timer and leaves only the current deadline', async () => {
    const old = deferred(), latest = deferred(); fetchMock.mockReturnValueOnce(old.promise).mockReturnValueOnce(latest.promise);
    const { result } = renderHook(useTestSession, { wrapper: StrictMode });
    expect(fetchMock.mock.calls[0][1]?.signal?.aborted).toBe(true); expect(vi.getTimerCount()).toBe(1);
    await act(async () => { old.resolve(response({}, 401)); latest.resolve(response()); });
    expect(result.current.authState.user).toEqual(user); expect(vi.getTimerCount()).toBe(0);
  });
  it.each([200, 401])('late body HTTP %s after timeout cannot install or reject identity', async status => {
    // A body that completes too late cannot validate a successful response.
    // For 401 the API rejects on headers, so hold the entire exchange instead.
    const body = deferred<unknown>(), headers = deferred();
    fetchMock.mockReturnValue(status === 200 ? Promise.resolve({ ok: true, status, json: () => body.promise } as Response) : headers.promise);
    const { result } = renderHook(useTestSession);
    await advance();
    await act(async () => { body.resolve(user); headers.resolve(response(user, status)); });
    expect(result.current.authState).toEqual({ user: null, accessToken: null, verification: 'unavailable' });
    expect(readAccessToken()).toBe('synthetic-startup'); expect(vi.getTimerCount()).toBe(0);
  });
  it.each(['signOut', 'unmount', 'signIn', 'replace'])('cancels %s immediately and prevents ignored-abort effects', async action => {
    const old = deferred(), latest = deferred();
    fetchMock.mockReturnValueOnce(old.promise).mockReturnValueOnce(latest.promise);
    const hook = renderHook(useTestSession); const signal = fetchMock.mock.calls[0][1]?.signal;
    await advance(1_000);
    act(() => {
      if (action === 'unmount') hook.unmount();
      else if (action === 'signOut') hook.result.current.signOut();
      else if (action === 'signIn') hook.result.current.signIn('synthetic-login', { ...user, userId: 2 });
      else { storeAccessToken('synthetic-replacement'); void hook.result.current.retry(); }
    });
    expect(signal?.aborted).toBe(true);
    expect(vi.getTimerCount()).toBe(action === 'replace' ? 1 : 0);
    await act(async () => { old.resolve(response({}, 401)); });
    expect(readAccessToken()).toBe(action === 'signOut' ? null : action === 'signIn' ? 'synthetic-login' : action === 'replace' ? 'synthetic-replacement' : 'synthetic-startup');
    if (action === 'replace') {
      await advance(STARTUP_VERIFICATION_TIMEOUT_MS - 1);
      expect(hook.result.current.authState.verification).toBe('pending');
      await act(async () => { latest.resolve(response()); });
    }
    if (action === 'signIn') expect(hook.result.current.authState.user?.userId).toBe(2);
    expect(vi.getTimerCount()).toBe(0);
  });
  it('direct token replacement during a stall retains replacement at timeout for explicit verification', async () => {
    fetchMock.mockReturnValue(new Promise(() => {})); const { result } = renderHook(useTestSession);
    storeAccessToken('synthetic-replacement'); await advance();
    expect(result.current.authState.verification).toBe('unavailable'); expect(result.current.authState.user).toBeNull();
    expect(readAccessToken()).toBe('synthetic-replacement'); expect(vi.getTimerCount()).toBe(0);
  });
  it('does not broaden the shared login verification deadline policy', async () => {
    const held = deferred(); fetchMock.mockReturnValue(held.promise);
    const verification = getCurrentUser('synthetic-login');
    expect(vi.getTimerCount()).toBe(0); expect(fetchMock.mock.calls[0][1]?.signal).toBeUndefined();
    await advance(STARTUP_VERIFICATION_TIMEOUT_MS * 2);
    held.resolve(response()); await expect(verification).resolves.toEqual(user);
  });
});
