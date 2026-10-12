// Opaque credential only: no identity/profile data and no persistence fallback.
export interface CredentialStore {
  read(): string | null;
  store(credential: string): void;
  clear(): void;
}

export class CredentialStorageError extends Error {
  constructor(public readonly operation: 'read' | 'store' | 'clear') {
    super(operation === 'store'
      ? 'Shale could not store the sign-in credential in this tab. You are not signed in.'
      : operation === 'clear'
        ? 'Shale blocked access in this document, but could not remove the stored credential. Close this tab before reopening Shale; reloading may restore the residual credential.'
        : 'Shale could not read the sign-in credential in this tab. Protected access is unavailable.');
    this.name = 'CredentialStorageError';
  }
}

const ACCESS_TOKEN_STORAGE_KEY = 'shale-web.accessToken';

// Lazy access keeps imports (including test imports) storage-free. Suppression is
// a denial flag, never a second credential copy or an alternative persistence store.
export function createBrowserCredentialStore(): CredentialStore {
  let suppressed = false;
  return {
    read() {
      if (suppressed) return null;
      try { return window.sessionStorage.getItem(ACCESS_TOKEN_STORAGE_KEY); }
      catch { throw new CredentialStorageError('read'); }
    },
    store(credential) {
      try {
        window.sessionStorage.setItem(ACCESS_TOKEN_STORAGE_KEY, credential);
        suppressed = false;
      } catch {
        suppressed = true;
        throw new CredentialStorageError('store');
      }
    },
    clear() {
      suppressed = true; // Deny reuse even if removeItem or the storage getter throws.
      try { window.sessionStorage.removeItem(ACCESS_TOKEN_STORAGE_KEY); }
      catch { throw new CredentialStorageError('clear'); }
    },
  };
}

export const browserCredentialStore = createBrowserCredentialStore();

// Every instance is isolated; this is a test implementation, not a browser fallback.
export function createMemoryCredentialStore(): CredentialStore {
  let credential: string | null = null;
  return {
    read: () => credential,
    store: value => { credential = value; },
    clear: () => { credential = null; },
  };
}
