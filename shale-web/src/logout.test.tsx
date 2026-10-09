import { act, cleanup, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { LOGOUT_TIMEOUT_MS, logout, readAccessToken } from './api';
import { useStartupSession } from './useStartupSession';

const user = { authenticated: true, userId: 1, shaleClientId: 1, email: null, displayName: 'Synthetic User',
  nameFirst: null, nameLast: null, isAdmin: false, isAttorney: false, initials: null, color: null };
const fetchMock = vi.fn<typeof fetch>();
const response = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status });
function deferred<T>() {
  let resolve!: (value: T) => void, reject!: (error: Error) => void;
  const promise = new Promise<T>((done, fail) => { resolve = done; reject = fail; });
  return { promise, resolve, reject };
}
beforeEach(() => { sessionStorage.clear(); vi.stubGlobal('fetch', fetchMock); fetchMock.mockReset(); });
afterEach(() => { cleanup(); sessionStorage.clear(); vi.useRealTimers(); vi.unstubAllGlobals(); });

describe('logout response authority and bounded attempt', () => {
  it('confirms only the existing revoked=true response and sends one captured bearer POST', async () => {
    fetchMock.mockResolvedValue(response({ revoked: true, message: 'Logged out.' }));
    await expect(logout('synthetic-logout')).resolves.toBeUndefined();
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toMatch(/\/api\/auth\/logout$/); expect(init?.method).toBe('POST');
    expect(init?.headers).toEqual({ Accept: 'application/json', Authorization: 'Bearer synthetic-logout' });
    expect(init?.redirect).toBe('error'); expect(init?.signal).toBeInstanceOf(AbortSignal); expect(readAccessToken()).toBeNull();
  });
  it.each([201, 202, 400, 401, 403, 429, 500, 503])('HTTP %s cannot confirm revocation even with a true body', async status => {
    fetchMock.mockResolvedValue(response({ revoked: true }, status));
    await expect(logout('synthetic-logout')).rejects.toThrow('could not be confirmed');
    expect(fetchMock).toHaveBeenCalledTimes(1); expect(readAccessToken()).toBeNull();
  });
  it.each([null, [], {}, { revoked: false }, { revoked: 'true' }, { loggedOut: true }])('unusable/negative response %# is unconfirmed', async body => {
    fetchMock.mockResolvedValue(response(body));
    await expect(logout('synthetic-logout')).rejects.toThrow('could not be confirmed');
  });
  it.each([200, 204])('empty/malformed HTTP %s does not confirm', async status => {
    fetchMock.mockResolvedValue(new Response(status === 204 ? null : '<html>Unavailable</html>', { status }));
    await expect(logout('synthetic-logout')).rejects.toThrow();
  });
  it('propagates network failure to the lifecycle without replay', async () => {
    fetchMock.mockRejectedValue(new TypeError('Synthetic offline'));
    await expect(logout('synthetic-logout')).rejects.toThrow();
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
  it.each(['fetch', 'body'])('bounds stalled %s even when it ignores abort; late rejection is consumed', async phase => {
    vi.useFakeTimers(); const held = deferred<Response>(), body = deferred<unknown>();
    fetchMock.mockReturnValue(phase === 'fetch' ? held.promise : Promise.resolve({ status: 200, json: () => body.promise } as Response));
    const attempt = logout('synthetic-logout');
    const assertion = expect(attempt).rejects.toThrow('could not be confirmed');
    await vi.advanceTimersByTimeAsync(LOGOUT_TIMEOUT_MS); await assertion;
    expect(fetchMock.mock.calls[0][1]?.signal?.aborted).toBe(true);
    if (phase === 'fetch') held.reject(new TypeError('Synthetic late failure'));
    else body.reject(new TypeError('Synthetic late body failure'));
    await Promise.resolve();
    expect(fetchMock).toHaveBeenCalledTimes(1); expect(vi.getTimerCount()).toBe(0);
  });
  it('cancels without a request if the lifecycle signal is already aborted', async () => {
    const controller = new AbortController(); controller.abort();
    await expect(logout('synthetic-logout', controller.signal)).rejects.toThrow('could not be confirmed');
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

async function established() {
  const hook = renderHook(useStartupSession);
  await act(async () => {});
  act(() => hook.result.current.signIn('synthetic-established', user));
  return hook;
}
describe('immediate local teardown and logout generation', () => {
  it('clears bearer/identity before remote fetch, prevents duplicate stale activation, and confirms success', async () => {
    const held = deferred<Response>(); const { result } = await established();
    fetchMock.mockImplementation(() => {
      expect(readAccessToken()).toBeNull();
      return held.promise;
    });
    const activate = result.current.logoutSession;
    act(() => { expect(activate()).toBe(true); expect(activate()).toBe(false); });
    expect(result.current.authState).toEqual({ user: null, accessToken: null, verification: null });
    expect(result.current.logoutFeedback).toBe('pending'); expect(fetchMock).toHaveBeenCalledTimes(1);
    await act(async () => { held.resolve(response({ revoked: true })); });
    expect(result.current.logoutFeedback).toBe('confirmed'); expect(readAccessToken()).toBeNull();
    act(() => expect(activate()).toBe(false)); expect(fetchMock).toHaveBeenCalledTimes(1);
  });
  it.each(['http', 'network', 'negative'])('consumes %s failure and never restores/retries credentials', async outcome => {
    const { result } = await established();
    if (outcome === 'network') fetchMock.mockRejectedValue(new TypeError('Sensitive synthetic failure'));
    else fetchMock.mockResolvedValue(response({ revoked: false }, outcome === 'http' ? 503 : 200));
    await act(async () => { result.current.logoutSession(); });
    expect(result.current.logoutFeedback).toBe('unavailable'); expect(readAccessToken()).toBeNull();
    expect(result.current.authState.user).toBeNull(); expect(fetchMock).toHaveBeenCalledTimes(1);
  });
  it('ends pending at the deadline without proving server failure or replaying', async () => {
    const { result } = await established(); vi.useFakeTimers();
    const held = deferred<Response>(); fetchMock.mockReturnValue(held.promise);
    act(() => { result.current.logoutSession(); });
    await act(async () => { await vi.advanceTimersByTimeAsync(LOGOUT_TIMEOUT_MS); });
    expect(result.current.logoutFeedback).toBe('unavailable'); expect(readAccessToken()).toBeNull();
    await act(async () => { held.resolve(response({ revoked: true })); });
    expect(result.current.logoutFeedback).toBe('unavailable'); expect(fetchMock).toHaveBeenCalledTimes(1);
  });
  it.each(['success', 'failure'])('a newer login invalidates late %s, including reuse of the same opaque bearer', async outcome => {
    const { result } = await established(); const held = deferred<Response>(); fetchMock.mockReturnValue(held.promise);
    const oldActivate = result.current.logoutSession;
    act(() => { oldActivate(); });
    const signal = fetchMock.mock.calls[0][1]?.signal;
    act(() => result.current.signIn('synthetic-established', { ...user, userId: 2 }));
    expect(signal?.aborted).toBe(true);
    act(() => expect(oldActivate()).toBe(false));
    await act(async () => {
      if (outcome === 'success') held.resolve(response({ revoked: true }));
      else held.reject(new TypeError('Synthetic late offline'));
    });
    expect(result.current.authState.user?.userId).toBe(2); expect(result.current.logoutFeedback).toBeNull();
    expect(readAccessToken()).toBe('synthetic-established'); expect(fetchMock).toHaveBeenCalledTimes(1);
  });
  it('unmount cancels and consumes late rejection without restoring storage', async () => {
    const { result, unmount } = await established(); const held = deferred<Response>(); fetchMock.mockReturnValue(held.promise);
    act(() => { result.current.logoutSession(); }); const signal = fetchMock.mock.calls[0][1]?.signal;
    unmount(); expect(signal?.aborted).toBe(true);
    await act(async () => { held.reject(new TypeError('Synthetic late offline')); });
    expect(readAccessToken()).toBeNull(); expect(fetchMock).toHaveBeenCalledTimes(1);
  });
  it('uncertain startup Return to sign in remains purely local', async () => {
    sessionStorage.setItem('shale-web.accessToken', 'synthetic-startup'); fetchMock.mockResolvedValue(response({}, 503));
    const { result } = renderHook(useStartupSession); await act(async () => {});
    act(() => result.current.signOut());
    expect(readAccessToken()).toBeNull(); expect(result.current.logoutFeedback).toBeNull();
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(String(fetchMock.mock.calls[0][0])).toMatch(/\/api\/auth\/me$/);
  });
});
