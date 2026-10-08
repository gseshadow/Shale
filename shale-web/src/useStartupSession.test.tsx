import { StrictMode } from 'react';
import { act, cleanup, renderHook, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { clearAccessToken, readAccessToken, storeAccessToken } from './api';
import { useStartupSession } from './useStartupSession';

const user = { authenticated: true, userId: 1, shaleClientId: 1, email: null, displayName: 'Synthetic User',
  nameFirst: null, nameLast: null, isAdmin: false, isAttorney: false, initials: null, color: null };
const response = (body: unknown = user, status = 200) => new Response(JSON.stringify(body), { status });
function deferred() {
  let resolve!: (response: Response) => void;
  let reject!: (error: Error) => void;
  const promise = new Promise<Response>((done, fail) => { resolve = done; reject = fail; });
  return { promise, resolve, reject };
}
const fetchMock = vi.fn<typeof fetch>();
beforeEach(() => { sessionStorage.clear(); fetchMock.mockReset(); vi.stubGlobal('fetch', fetchMock); });
afterEach(() => { cleanup(); sessionStorage.clear(); vi.unstubAllGlobals(); });

describe('startup verification contract', () => {
  it('signs out without a stored bearer and makes no request', async () => {
    const { result } = renderHook(useStartupSession);
    await waitFor(() => expect(result.current.authState.verification).toBeNull());
    expect(result.current.authState.user).toBeNull(); expect(fetchMock).not.toHaveBeenCalled();
  });
  it('keeps identity and feature credentials unavailable until a usable /me succeeds', async () => {
    storeAccessToken('synthetic-startup'); const held = deferred(); fetchMock.mockReturnValue(held.promise);
    const { result } = renderHook(useStartupSession);
    expect(result.current.authState).toEqual({ accessToken: null, user: null, verification: 'pending' });
    await act(async () => { held.resolve(response()); });
    expect(result.current.authState.user).toEqual(user); expect(result.current.authState.verification).toBeNull();
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(String(fetchMock.mock.calls[0][0])).toMatch(/\/api\/auth\/me$/);
  });
  it('clears local storage only on the /me contract’s confirmed 401 rejection', async () => {
    storeAccessToken('synthetic-startup'); fetchMock.mockResolvedValue(response({}, 401));
    const { result } = renderHook(useStartupSession);
    await waitFor(() => expect(result.current.authState.verification).toBeNull());
    expect(readAccessToken()).toBeNull(); expect(result.current.authState.user).toBeNull();
  });
  it.each([400, 403, 404, 408, 409, 429, 500, 502, 503])('retains the bearer and blocks access on HTTP %s', async status => {
    storeAccessToken('synthetic-startup'); fetchMock.mockResolvedValue(response({}, status));
    const { result } = renderHook(useStartupSession);
    await waitFor(() => expect(result.current.authState.verification).toBe('unavailable'));
    expect(readAccessToken()).not.toBeNull(); expect(result.current.authState.user).toBeNull();
    expect(result.current.authState.accessToken).toBeNull();
  });
  it('retains the bearer on transport failure, repeated failure, then explicit Retry succeeds', async () => {
    storeAccessToken('synthetic-startup'); fetchMock.mockRejectedValueOnce(new TypeError('Synthetic offline'))
      .mockResolvedValueOnce(response({}, 503)).mockResolvedValueOnce(response());
    const { result } = renderHook(useStartupSession);
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
    const { result } = renderHook(useStartupSession);
    await waitFor(() => expect(result.current.authState.verification).toBe('unavailable'));
    expect(result.current.authState.user).toBeNull(); expect(readAccessToken()).not.toBeNull();
  });
  it.each([200, 204])('treats invalid/empty JSON at HTTP %s as uncertainty', async status => {
    storeAccessToken('synthetic-startup'); fetchMock.mockResolvedValue(new Response(status === 204 ? null : '<html>Unavailable</html>', { status }));
    const { result } = renderHook(useStartupSession);
    await waitFor(() => expect(result.current.authState.verification).toBe('unavailable'));
    expect(result.current.authState.user).toBeNull(); expect(readAccessToken()).not.toBeNull();
  });
  it('coalesces duplicate explicit attempts synchronously while pending', async () => {
    storeAccessToken('synthetic-startup'); const held = deferred(); fetchMock.mockReturnValue(held.promise);
    const { result } = renderHook(useStartupSession);
    act(() => { void result.current.retry(); void result.current.retry(); });
    expect(fetchMock).toHaveBeenCalledTimes(1);
    await act(async () => { held.resolve(response()); });
  });
  it.each([200, 401, 503])('local sign-out invalidates late HTTP %s results', async status => {
    storeAccessToken('synthetic-startup'); const held = deferred(); fetchMock.mockReturnValue(held.promise);
    const { result } = renderHook(useStartupSession);
    act(() => result.current.signOut());
    await act(async () => { held.resolve(response(user, status)); });
    expect(result.current.authState).toEqual({ accessToken: null, user: null, verification: null });
    expect(readAccessToken()).toBeNull();
  });
  it.each([200, 401])('discards late HTTP %s after direct storage replacement and verifies the replacement explicitly', async status => {
    storeAccessToken('synthetic-startup'); const held = deferred(); fetchMock.mockReturnValueOnce(held.promise).mockResolvedValueOnce(response());
    const { result } = renderHook(useStartupSession);
    storeAccessToken('synthetic-replacement');
    await act(async () => { held.resolve(response(user, status)); });
    expect(result.current.authState.verification).toBe('unavailable'); expect(result.current.authState.user).toBeNull();
    expect(readAccessToken()).toBe('synthetic-replacement');
    await act(async () => { await result.current.retry(); });
    expect(result.current.authState.user).toEqual(user);
  });
  it('does not resurrect a directly removed credential', async () => {
    storeAccessToken('synthetic-startup'); const held = deferred(); fetchMock.mockReturnValue(held.promise);
    const { result } = renderHook(useStartupSession); clearAccessToken();
    await act(async () => { held.resolve(response()); });
    expect(result.current.authState.user).toBeNull(); expect(result.current.authState.verification).toBeNull();
  });
  it.each([200, 401, 503])('newer attempt wins over late HTTP %s from a superseded attempt', async status => {
    storeAccessToken('synthetic-startup'); const old = deferred(), latest = deferred();
    fetchMock.mockReturnValueOnce(old.promise).mockReturnValueOnce(latest.promise);
    const { result } = renderHook(useStartupSession); storeAccessToken('synthetic-replacement');
    let retry!: Promise<void>; act(() => { retry = result.current.retry(); });
    await act(async () => { latest.resolve(response()); await retry; });
    await act(async () => { old.resolve(response(user, status)); });
    expect(result.current.authState.user).toEqual(user); expect(result.current.authState.accessToken).toBe('synthetic-replacement');
    expect(readAccessToken()).toBe('synthetic-replacement');
  });
  it('a verified login invalidates the old startup attempt', async () => {
    storeAccessToken('synthetic-startup'); const held = deferred(); fetchMock.mockReturnValue(held.promise);
    const { result } = renderHook(useStartupSession);
    act(() => result.current.signIn('synthetic-login', { ...user, userId: 2 }));
    await act(async () => { held.resolve(response({}, 401)); });
    expect(result.current.authState.user?.userId).toBe(2); expect(readAccessToken()).toBe('synthetic-login');
  });
  it('unmount invalidates a late rejection without touching storage', async () => {
    storeAccessToken('synthetic-startup'); const held = deferred(); fetchMock.mockReturnValue(held.promise);
    const { unmount } = renderHook(useStartupSession); unmount();
    await act(async () => { held.resolve(response({}, 401)); });
    expect(readAccessToken()).not.toBeNull();
  });
  it('StrictMode cleanup invalidates the superseded mount attempt', async () => {
    storeAccessToken('synthetic-startup'); const old = deferred(), latest = deferred();
    fetchMock.mockReturnValueOnce(old.promise).mockReturnValueOnce(latest.promise);
    const { result } = renderHook(useStartupSession, { wrapper: StrictMode });
    await act(async () => { latest.resolve(response()); });
    await act(async () => { old.resolve(response({}, 401)); });
    expect(result.current.authState.user).toEqual(user); expect(readAccessToken()).not.toBeNull();
  });
});
