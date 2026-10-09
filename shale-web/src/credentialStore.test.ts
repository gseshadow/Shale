import { afterEach, describe, expect, it, vi } from 'vitest';
import { createBrowserCredentialStore, createMemoryCredentialStore, CredentialStorageError, browserCredentialStore } from './credentialStore';
import { clearAccessToken, readAccessToken, storeAccessToken } from './api';
afterEach(() => { vi.restoreAllMocks(); sessionStorage.clear(); });

describe('browser credential storage policy', () => {
  it('uses exactly the existing sessionStorage key and preserves opaque values', () => {
    const store = createBrowserCredentialStore();
    const local = vi.spyOn(Storage.prototype, 'setItem');
    expect(store.read()).toBeNull();
    store.store(' synthetic.opaque+/= ');
    expect(local).toHaveBeenCalledExactlyOnceWith('shale-web.accessToken', ' synthetic.opaque+/= ');
    expect(sessionStorage.getItem('shale-web.accessToken')).toBe(' synthetic.opaque+/= ');
    expect(localStorage.getItem('shale-web.accessToken')).toBeNull();
    expect(store.read()).toBe(' synthetic.opaque+/= ');
    store.clear();
    expect(sessionStorage.getItem('shale-web.accessToken')).toBeNull();
    expect(store.read()).toBeNull();
    store.store('synthetic-new'); expect(store.read()).toBe('synthetic-new');
  });
  it('keeps compatibility helpers as delegates to the same owner', () => {
    const write = vi.spyOn(browserCredentialStore, 'store');
    const read = vi.spyOn(browserCredentialStore, 'read');
    const clear = vi.spyOn(browserCredentialStore, 'clear');
    storeAccessToken('synthetic-adapter'); expect(readAccessToken()).toBe('synthetic-adapter'); clearAccessToken();
    expect(write).toHaveBeenCalledExactlyOnceWith('synthetic-adapter');
    expect(read).toHaveBeenCalledTimes(1); expect(clear).toHaveBeenCalledTimes(1);
  });
  it.each(['read', 'store', 'clear'] as const)('sanitizes %s method exceptions without another persistence mechanism', operation => {
    const store = createBrowserCredentialStore();
    const fallback = vi.spyOn(localStorage, 'setItem');
    const method = operation === 'read' ? 'getItem' : operation === 'store' ? 'setItem' : 'removeItem';
    vi.spyOn(Storage.prototype, method).mockImplementation(() => { throw new Error('Synthetic sensitive exception'); });
    expect(() => operation === 'store' ? store.store('synthetic') : store[operation]()).toThrow(CredentialStorageError);
    try { operation === 'store' ? store.store('synthetic') : store[operation](); } catch (error) {
      expect(String(error)).not.toContain('sensitive exception');
    }
    expect(fallback).not.toHaveBeenCalled();
  });
  it.each(['read', 'store', 'clear'] as const)('bounds a denied sessionStorage getter during %s', operation => {
    const store = createBrowserCredentialStore();
    vi.spyOn(window, 'sessionStorage', 'get').mockImplementation(() => { throw new Error('Synthetic denied'); });
    expect(() => operation === 'store' ? store.store('synthetic') : store[operation]()).toThrow(CredentialStorageError);
  });
  it('suppresses a residual credential after failed removal until a successful explicit store', () => {
    const store = createBrowserCredentialStore(); store.store('synthetic-residual');
    const remove = vi.spyOn(Storage.prototype, 'removeItem').mockImplementation(() => { throw new Error('Synthetic denied'); });
    expect(() => store.clear()).toThrow(CredentialStorageError);
    expect(sessionStorage.getItem('shale-web.accessToken')).toBe('synthetic-residual');
    expect(store.read()).toBeNull();
    // A new document has no suppression state: document reload is explicitly not protected.
    expect(createBrowserCredentialStore().read()).toBe('synthetic-residual');
    remove.mockRestore(); store.store('synthetic-replacement'); expect(store.read()).toBe('synthetic-replacement');
  });
});
it('isolates memory instances without touching browser storage', () => {
  const read = vi.spyOn(Storage.prototype, 'getItem'), write = vi.spyOn(Storage.prototype, 'setItem'), clear = vi.spyOn(Storage.prototype, 'removeItem');
  const a = createMemoryCredentialStore(), b = createMemoryCredentialStore();
  a.store('synthetic'); expect(a.read()).toBe('synthetic'); expect(b.read()).toBeNull();
  a.clear(); expect(a.read()).toBeNull();
  expect(read).not.toHaveBeenCalled(); expect(write).not.toHaveBeenCalled(); expect(clear).not.toHaveBeenCalled();
});
