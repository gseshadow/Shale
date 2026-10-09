import { act, cleanup, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { login } from './api';
import { createMemoryCredentialStore } from './credentialStore';
import { CREDENTIAL_LOGIN_TIMEOUT_MS as budget, useStartupSession } from './useStartupSession';

const user = { authenticated: true, userId: 1, shaleClientId: 1, email: null, displayName: null,
  nameFirst: null, nameLast: null, isAdmin: false, isAttorney: false, initials: null, color: null };
const payload = { authenticated: true, tokenType: 'Bearer', accessToken: 'synthetic-login', expiresInSeconds: 3600, user };
const response = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status });
function deferred<T>() {
  let resolve!: (value: T) => void, reject!: (error: Error) => void;
  const promise = new Promise<T>((done, fail) => { resolve = done; reject = fail; });
  return { promise, resolve, reject };
}
const fetchMock = vi.fn<typeof fetch>();
beforeEach(() => { vi.useFakeTimers(); fetchMock.mockReset(); vi.stubGlobal('fetch', fetchMock); });
afterEach(() => { cleanup(); vi.restoreAllMocks(); vi.useRealTimers(); vi.unstubAllGlobals(); });
async function owner() {
  const store = createMemoryCredentialStore();
  const hook = renderHook(() => useStartupSession(store));
  await act(async () => {});
  const controller = new AbortController();
  const start = () => hook.result.current.signInWithCredentials('synthetic@example.invalid', 'synthetic-password', controller.signal);
  return { ...hook, store, controller, start };
}

// These tests prevent unverified/stale credentials from gaining protected access.
describe('one bounded user-initiated credential attempt', () => {
  it.each(['login fetch', 'login body', 'verification fetch', 'verification body'])('stalled %s times out, cleans up and consumes ignored-abort late success', async stage => {
    const held = deferred<Response>(), body = deferred<unknown>();
    fetchMock.mockImplementation((_input, init) => {
      const verifying = init?.method === 'GET';
      if (stage === (verifying ? 'verification fetch' : 'login fetch')) return held.promise;
      if (stage === (verifying ? 'verification body' : 'login body')) return Promise.resolve({ ok: true, status: 200, json: () => body.promise } as Response);
      return Promise.resolve(response(verifying ? user : payload));
    });
    const { start, store, result } = await owner();
    const attempt = start(); const assertion = expect(attempt).rejects.toThrow(/timed out.*server session may have been created/);
    await act(async () => { await vi.advanceTimersByTimeAsync(budget); await assertion; });
    expect(result.current.authState.user).toBeNull(); expect(store.read()).toBeNull();
    expect(fetchMock.mock.calls.every(call => call[1]?.signal?.aborted)).toBe(true);
    expect(vi.getTimerCount()).toBe(0);
    await act(async () => { held.resolve(response(stage.startsWith('verification') ? user : payload)); body.resolve(stage.startsWith('verification') ? user : payload); });
    expect(result.current.authState.user).toBeNull(); expect(store.read()).toBeNull();
    expect(fetchMock).toHaveBeenCalledTimes(stage.startsWith('verification') ? 2 : 1);
  });
  it.each([budget - 1, budget])('stages share one budget: verification at total %s milliseconds', async total => {
    const first = deferred<Response>(), second = deferred<Response>();
    fetchMock.mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise);
    const { start, result, store } = await owner(); const attempt = start();
    const assertion = total === budget ? expect(attempt).rejects.toThrow('timed out') : expect(attempt).resolves.toBe(true);
    await act(async () => { await vi.advanceTimersByTimeAsync(6000); first.resolve(response(payload)); });
    expect(fetchMock).toHaveBeenCalledTimes(2); expect(store.read()).toBeNull();
    await act(async () => { await vi.advanceTimersByTimeAsync(total - 6000); second.resolve(response(user)); await assertion; });
    expect(result.current.authState.user).toEqual(total === budget ? null : user);
    expect(vi.getTimerCount()).toBe(0);
  });
  it('checks elapsed monotonic time even before the deadline callback can run', async () => {
    const held = deferred<Response>(); fetchMock.mockReturnValueOnce(held.promise);
    const { start, store } = await owner(); const now = vi.spyOn(performance, 'now').mockReturnValue(0);
    const attempt = start(); const assertion = expect(attempt).rejects.toThrow('timed out');
    now.mockReturnValue(budget);
    await act(async () => { held.resolve(response(payload)); await assertion; });
    expect(store.read()).toBeNull(); expect(fetchMock).toHaveBeenCalledTimes(1); expect(vi.getTimerCount()).toBe(0);
  });
  it('coalesces synchronous duplicates and allows deliberate success after failure', async () => {
    const held = deferred<Response>(); fetchMock.mockReturnValueOnce(held.promise);
    const { start, result, store } = await owner();
    const first = start(); const assertion = expect(first).rejects.toThrow('not accepted');
    await expect(start()).resolves.toBe(false); expect(fetchMock).toHaveBeenCalledTimes(1);
    await act(async () => { held.resolve(response({}, 401)); await assertion; });
    expect(store.read()).toBeNull(); expect(vi.getTimerCount()).toBe(0);
    fetchMock.mockResolvedValueOnce(response(payload)).mockResolvedValueOnce(response(user));
    await act(async () => { expect(await start()).toBe(true); });
    expect(result.current.authState.user).toEqual(user); expect(store.read()).toBe(payload.accessToken);
    expect(fetchMock).toHaveBeenCalledTimes(3); expect(vi.getTimerCount()).toBe(0);
  });
  it.each(['login', 'verification'])('a newer attempt wins after %s timeout; old late rejection cannot clear it', async stage => {
    const held = deferred<Response>();
    if (stage === 'verification') fetchMock.mockResolvedValueOnce(response(payload));
    fetchMock.mockReturnValueOnce(held.promise);
    const { start, result, store } = await owner(); const old = start(); const assertion = expect(old).rejects.toThrow('timed out');
    await act(async () => { await vi.advanceTimersByTimeAsync(budget); await assertion; });
    fetchMock.mockResolvedValueOnce(response(payload)).mockResolvedValueOnce(response(user));
    await act(async () => { await start(); held.reject(new Error('Synthetic sensitive late error')); });
    expect(result.current.authState.user).toEqual(user); expect(store.read()).toBe(payload.accessToken); expect(vi.getTimerCount()).toBe(0);
  });
  it.each(['login', 'verification'])('cancellation during %s permits a newer attempt and discards old success', async stage => {
    const held = deferred<Response>();
    if (stage === 'verification') fetchMock.mockResolvedValueOnce(response(payload));
    fetchMock.mockReturnValueOnce(held.promise);
    const { start, controller, result, store } = await owner(); const old = start();
    await act(async () => {}); controller.abort(); await expect(old).resolves.toBe(false);
    expect(vi.getTimerCount()).toBe(0);
    fetchMock.mockResolvedValueOnce(response(payload)).mockResolvedValueOnce(response(user));
    await act(async () => { await result.current.signInWithCredentials('synthetic', 'synthetic', new AbortController().signal); held.resolve(response(stage === 'login' ? payload : user)); });
    expect(result.current.authState.user).toEqual(user); expect(store.read()).toBe(payload.accessToken); expect(vi.getTimerCount()).toBe(0);
  });
  it.each(['login', 'verification', 'login body', 'verification body'].flatMap(stage => ['signOut', 'logout', 'replacement', 'unmount', 'storage replacement'].map(end => [stage, end])))('late %s cannot install after %s', async (stage, end) => {
    const held = deferred<Response>(), body = deferred<unknown>();
    if (stage.startsWith('verification')) fetchMock.mockResolvedValueOnce(response(payload));
    if (stage.endsWith('body')) fetchMock.mockResolvedValueOnce({ ok: true, status: 200, json: () => body.promise } as Response);
    else fetchMock.mockReturnValueOnce(held.promise);
    const { start, result, unmount, store } = await owner();
    const old = start(); await act(async () => {});
    await act(async () => {
      if (end === 'unmount') unmount();
      else if (end === 'replacement') result.current.signIn('synthetic-replacement', { ...user, userId: 2 });
      else if (end === 'storage replacement') store.store('synthetic-replacement');
      else if (end === 'logout') expect(result.current.logoutSession()).toBe(true);
      else result.current.signOut();
    });
    if (end !== 'storage replacement') expect(vi.getTimerCount()).toBe(0);
    await act(async () => { held.resolve(response(stage.startsWith('login') ? payload : user)); body.resolve(stage.startsWith('login') ? payload : user); expect(await old).toBe(false); });
    expect(store.read()).toBe(end === 'replacement' || end === 'storage replacement' ? 'synthetic-replacement' : null);
    if (end !== 'unmount') expect(result.current.authState.user).toEqual(end === 'replacement' ? { ...user, userId: 2 } : null);
    expect(vi.getTimerCount()).toBe(0);
  });
  it.each(['success', 'failure', 'timeout', 'cancel'])('removes cancellation listeners and timers after %s', async end => {
    const { start, controller, store } = await owner();
    const add = vi.spyOn(AbortSignal.prototype, 'addEventListener');
    const remove = vi.spyOn(AbortSignal.prototype, 'removeEventListener');
    if (end === 'success') fetchMock.mockResolvedValueOnce(response(payload)).mockResolvedValueOnce(response(user));
    else if (end === 'failure') fetchMock.mockResolvedValueOnce(response({}, 401));
    else fetchMock.mockReturnValue(new Promise(() => {}));
    const attempt = start();
    const assertion = end === 'success' ? expect(attempt).resolves.toBe(true) : end === 'cancel' ? expect(attempt).resolves.toBe(false) : expect(attempt).rejects.toThrow();
    await act(async () => {
      if (end === 'timeout') await vi.advanceTimersByTimeAsync(budget);
      if (end === 'cancel') controller.abort();
      await assertion;
    });
    const abortAdds = add.mock.calls.filter(call => call[0] === 'abort');
    for (const call of abortAdds) expect(remove.mock.calls.some(removal => removal[0] === 'abort' && removal[1] === call[1])).toBe(true);
    expect(vi.getTimerCount()).toBe(0);
    controller.abort();
    expect(store.read()).toBe(end === 'success' ? payload.accessToken : null);
  });
  it('initial credential read failure is sanitized and dispatches nothing', async () => {
    const { start, store } = await owner();
    vi.spyOn(store, 'read').mockImplementation(() => { throw new Error('Sensitive synthetic error'); });
    await expect(start()).rejects.toThrow('could not read');
    expect(fetchMock).not.toHaveBeenCalled(); expect(vi.getTimerCount()).toBe(0);
  });
  it('already-cancelled submission dispatches nothing and creates no timer', async () => {
    const { start, controller } = await owner(); controller.abort(); await expect(start()).resolves.toBe(false);
    expect(fetchMock).not.toHaveBeenCalled(); expect(vi.getTimerCount()).toBe(0);
  });
  it('verified login preserves partial-write cleanup failure behavior', async () => {
    fetchMock.mockResolvedValueOnce(response(payload)).mockResolvedValueOnce(response(user));
    const { start, store, result } = await owner();
    const original = store.store; vi.spyOn(store, 'store').mockImplementation(value => { original(value); throw new Error('Sensitive synthetic write error'); });
    await act(async () => { await expect(start()).rejects.toThrow('could not store'); });
    expect(store.read()).toBeNull(); expect(result.current.authState.user).toBeNull(); expect(vi.getTimerCount()).toBe(0);
  });
});

describe('response authority and safe classification', () => {
  it.each([null, [], {}, { ...payload, authenticated: false }, { ...payload, tokenType: 'Basic' }, { ...payload, accessToken: '' },
    { ...payload, accessToken: 'bad\r\nvalue' }, { ...payload, expiresInSeconds: 0 }, { ...payload, expiresInSeconds: '3600' },
    { ...payload, user: { ...user, userId: 0 } }])('unusable login body %# never verifies or persists', async body => {
    fetchMock.mockResolvedValue(response(body)); const { start, store } = await owner();
    await act(async () => { await expect(start()).rejects.toThrow('could not confirm sign-in'); });
    expect(store.read()).toBeNull(); expect(fetchMock).toHaveBeenCalledTimes(1); expect(vi.getTimerCount()).toBe(0);
  });
  it.each([400, 403, 429, 500, 503])('login HTTP %s is unavailable, never incorrect credentials or raw body', async status => {
    fetchMock.mockResolvedValue(response({ message: 'Sensitive synthetic body' }, status)); const { start } = await owner();
    await act(async () => { await expect(start()).rejects.toThrow('could not confirm sign-in'); });
    expect(fetchMock).toHaveBeenCalledTimes(1); expect(vi.getTimerCount()).toBe(0);
  });
  it.each(['network', 'login JSON', 'verification JSON', 'verification identity', 'verification 401', 'verification 503'])('classifies %s without exposing sensitive messages', async kind => {
    if (kind === 'network') fetchMock.mockRejectedValueOnce(new Error('Sensitive synthetic network details'));
    else if (kind === 'login JSON') fetchMock.mockResolvedValueOnce(new Response('Sensitive synthetic invalid JSON'));
    else {
      fetchMock.mockResolvedValueOnce(response(payload));
      fetchMock.mockResolvedValueOnce(kind === 'verification JSON' ? new Response('Sensitive synthetic invalid JSON')
        : response(kind === 'verification identity' ? { ...user, userId: 2 } : {}, kind === 'verification 401' ? 401 : kind === 'verification 503' ? 503 : 200));
    }
    const { start, store } = await owner();
    await act(async () => {
      try { await start(); throw new Error('Expected failed sign-in'); }
      catch (error) {
        expect((error as Error).message).toContain(kind === 'verification 401' ? 'rejected the new session' : 'could not confirm sign-in');
        expect((error as Error).message).not.toMatch(/Sensitive|email or password/);
      }
    });
    expect(store.read()).toBeNull(); expect(vi.getTimerCount()).toBe(0);
  });
  it('login sends cancellation and rejects redirects without storage access', async () => {
    fetchMock.mockResolvedValue(response(payload)); const controller = new AbortController();
    await login('synthetic', 'synthetic', controller.signal);
    expect(fetchMock.mock.calls[0][1]?.signal).toBe(controller.signal); expect(fetchMock.mock.calls[0][1]?.redirect).toBe('error');
  });
});
