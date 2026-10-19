import { act, cleanup, renderHook, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { createBrowserCredentialStore, createMemoryCredentialStore } from './credentialStore';
import { useStartupSession } from './useStartupSession';
import { getContactDetail } from './api';
import { SessionRequestDiscarded, captureSessionRequestGuard } from './sessionRequests';
const user = { authenticated: true, userId: 1, shaleClientId: 1, email: null, displayName: null, nameFirst: null, nameLast: null, isAdmin: false, isAttorney: false, initials: null, color: null };
const response = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status });
afterEach(() => { cleanup(); vi.restoreAllMocks(); vi.unstubAllGlobals(); sessionStorage.clear(); });

describe('bounded credential failures and lifecycle', () => {
  it('blocks startup on read failure, makes no request, and only explicit Retry recovers', async () => {
    const store = createBrowserCredentialStore(); store.store('synthetic');
    const read = vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('Synthetic private error'); });
    const fetch = vi.fn().mockResolvedValue(response(user)); vi.stubGlobal('fetch', fetch);
    const { result } = renderHook(() => useStartupSession(store));
    await waitFor(() => expect(result.current.authState.verification).toBe('unavailable'));
    expect(result.current.storageFeedback).toContain('could not read'); expect(fetch).not.toHaveBeenCalled();
    read.mockRestore(); await act(async () => { await result.current.retry(); });
    expect(result.current.authState.user).toEqual(user); expect(result.current.storageFeedback).toBeNull();
    expect(fetch).toHaveBeenCalledTimes(1);
  });
  it('read failure at startup settlement cannot install identity or escape as an unhandled rejection', async () => {
    const store = createMemoryCredentialStore(); store.store('synthetic');
    let done!: (value: Response) => void;
    vi.stubGlobal('fetch', vi.fn(() => new Promise<Response>(resolve => { done = resolve; })));
    const { result } = renderHook(() => useStartupSession(store));
    vi.spyOn(store, 'read').mockImplementation(() => { throw new Error('Synthetic denied'); });
    await act(async () => { done(response(user)); });
    expect(result.current.authState).toEqual({ user: null, accessToken: null, verification: 'unavailable' });
  });
  it('failed login persistence removes the previous identity and binding, even with failed cleanup', async () => {
    const store = createBrowserCredentialStore(); const { result } = renderHook(() => useStartupSession(store));
    act(() => result.current.signIn('synthetic-old', user));
    const oldGuard = captureSessionRequestGuard('synthetic-old');
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('Synthetic denied'); });
    vi.spyOn(Storage.prototype, 'removeItem').mockImplementation(() => { throw new Error('Synthetic denied'); });
    act(() => { expect(() => result.current.signIn('synthetic-new', { ...user, userId: 2 })).toThrow('could not store'); });
    expect(result.current.authState.user).toBeNull(); expect(result.current.authState.accessToken).toBeNull();
    expect(oldGuard()).toBe(false); expect(store.read()).toBeNull();
    expect(result.current.storageFeedback).toContain('reloading may restore');
    const fetch = vi.fn(); vi.stubGlobal('fetch', fetch);
    await expect(getContactDetail('synthetic-new', 1)).rejects.toBeInstanceOf(SessionRequestDiscarded);
    await act(async () => { await result.current.retry(); }); expect(fetch).not.toHaveBeenCalled();
  });
  it('cleans a partial write before reporting failed login persistence', () => {
    const store = createBrowserCredentialStore(); const { result } = renderHook(() => useStartupSession(store));
    const originalSet = Storage.prototype.setItem;
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(function(this: Storage, key, value) {
      originalSet.call(this, key, value); throw new Error('Synthetic failed after write');
    });
    act(() => { expect(() => result.current.signIn('synthetic-partial', user)).toThrow('could not store'); });
    expect(sessionStorage.getItem('shale-web.accessToken')).toBeNull(); expect(store.read()).toBeNull();
    expect(result.current.authState.user).toBeNull();
  });
  it('an established guard read failure removes identity and prevents feature dispatch', async () => {
    const store = createBrowserCredentialStore(); const { result } = renderHook(() => useStartupSession(store));
    act(() => result.current.signIn('synthetic', user));
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('Synthetic denied'); });
    const fetch = vi.fn(); vi.stubGlobal('fetch', fetch);
    await act(async () => { await expect(getContactDetail('synthetic', 1)).rejects.toBeInstanceOf(SessionRequestDiscarded); });
    expect(result.current.authState.user).toBeNull(); expect(result.current.storageFeedback).toContain('could not read');
    expect(fetch).not.toHaveBeenCalled();
  });
  it.each(['local', 'logout', 'rejection', 'startup rejection'])('failed clear during %s still tears down and never reuses residual storage', async end => {
    const store = createBrowserCredentialStore(); store.store('synthetic');
    const fetch = vi.fn().mockImplementation(async () => response(end === 'startup rejection' ? {} : user, end === 'startup rejection' ? 401 : 200)); vi.stubGlobal('fetch', fetch);
    if (end === 'startup rejection') vi.spyOn(Storage.prototype, 'removeItem').mockImplementation(() => { throw new Error('Synthetic denied'); });
    const { result } = renderHook(() => useStartupSession(store));
    await waitFor(() => expect(result.current.authState.verification).toBeNull());
    if (end !== 'startup rejection') {
      vi.spyOn(Storage.prototype, 'removeItem').mockImplementation(() => { throw new Error('Synthetic denied'); });
      fetch.mockImplementation(async () => response(end === 'logout' ? { revoked: true } : {}, end === 'rejection' ? 401 : 200));
      await act(async () => {
        if (end === 'local') result.current.signOut();
        if (end === 'logout') expect(result.current.logoutSession()).toBe(true);
        if (end === 'rejection') await expect(getContactDetail('synthetic', 1)).rejects.toBeInstanceOf(SessionRequestDiscarded);
      });
    }
    expect(result.current.authState.user).toBeNull(); expect(result.current.authState.accessToken).toBeNull();
    expect(store.read()).toBeNull(); expect(sessionStorage.getItem('shale-web.accessToken')).toBe('synthetic');
    expect(result.current.storageFeedback).toContain('could not remove');
    if (end === 'logout') expect(result.current.logoutFeedback).toBe('confirmed');
    const count = fetch.mock.calls.length; await act(async () => { await result.current.retry(); });
    await expect(getContactDetail('synthetic', 1)).rejects.toBeInstanceOf(SessionRequestDiscarded);
    expect(fetch).toHaveBeenCalledTimes(count);
  });
});
