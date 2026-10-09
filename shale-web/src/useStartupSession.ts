import { useEffect, useRef, useState } from 'react';
import { ApiError, getCurrentUser, logout } from './api';
import { browserCredentialStore, CredentialStorageError } from './credentialStore';
import type { CredentialStore } from './credentialStore';
import type { AuthenticatedUser } from './api';
import { bindSessionRequests } from './sessionRequests';

export interface AuthState {
  accessToken: string | null;
  user: AuthenticatedUser | null;
  verification: 'pending' | 'unavailable' | null;
}

const signedOut: AuthState = { accessToken: null, user: null, verification: null };

export type LogoutFeedback = 'pending' | 'confirmed' | 'unavailable' | null;

// Match the existing logout deadline: eight seconds for fetch AND body processing.
// Only startup/explicit Retry opt in; credential login keeps its existing policy.
export const STARTUP_VERIFICATION_TIMEOUT_MS = 8_000;
async function verifyStartupSession(token: string, controller: AbortController): Promise<AuthenticatedUser> {
  let timer: ReturnType<typeof setTimeout> | undefined;
  let cancel!: () => void;
  const cancelled = new Promise<never>((_resolve, reject) => {
    cancel = () => {
      clearTimeout(timer);
      reject(new Error('Session verification is unavailable.'));
    };
    timer = setTimeout(() => { cancel(); controller.abort(); }, STARTUP_VERIFICATION_TIMEOUT_MS);
  });
  controller.signal.addEventListener('abort', cancel, { once: true });
  try {
    if (controller.signal.aborted) { cancel(); return await cancelled; }
    // Racing the entire parsed/validated result also consumes ignored-abort late settlements.
    return await Promise.race([getCurrentUser(token, controller.signal), cancelled]);
  } finally {
    clearTimeout(timer);
    controller.signal.removeEventListener('abort', cancel);
  }
}

// Startup recovery and the established-session sign-out boundary share invalidation.
export function useStartupSession(credentialStore: CredentialStore = browserCredentialStore) {
  const [storageFeedback, setStorageFeedback] = useState<string | null>(null);
  const locallyEnded = useRef(false);
  const [authState, setAuthState] = useState<AuthState>({ ...signedOut, verification: 'pending' });
  const [logoutFeedback, setLogoutFeedback] = useState<LogoutFeedback>(null);
  const [sessionEnded, setSessionEnded] = useState(false);
  const featureBinding = useRef<(() => void) | null>(null);
  const remoteLogout = useRef<AbortController | null>(null);
  const logoutConsumed = useRef(false);
  const mounted = useRef(false);
  const generation = useRef(0);
  const pending = useRef<{ token: string; controller: AbortController } | null>(null);
  const renderedGeneration = generation.current;

  function invalidate() {
    generation.current++;
    featureBinding.current?.();
    featureBinding.current = null;
    remoteLogout.current?.abort();
    remoteLogout.current = null;
    pending.current?.controller.abort();
    pending.current = null;
  }

  function clearCredential() {
    locallyEnded.current = true;
    try { credentialStore.clear(); }
    catch { setStorageFeedback(new CredentialStorageError('clear').message); }
  }

  function credentialIsCurrent(token: string) {
    if (locallyEnded.current) return false;
    try { return credentialStore.read() === token; }
    catch {
      signOut();
      setStorageFeedback(previous => previous ?? new CredentialStorageError('read').message);
      return false;
    }
  }

  function establish(token: string, user: AuthenticatedUser) {
    const attempt = generation.current;
    featureBinding.current = bindSessionRequests(token,
      () => mounted.current && generation.current === attempt && credentialIsCurrent(token),
      () => {
        // The seam has already consumed this binding. No remote logout or replay.
        invalidate();
        clearCredential();
        setLogoutFeedback(null);
        setSessionEnded(true);
        setAuthState(signedOut);
      });
    logoutConsumed.current = false;
    setSessionEnded(false);
    setAuthState({ accessToken: token, user, verification: null });
  }

  async function retry() {
    if (!mounted.current || locallyEnded.current) return;
    let token: string | null;
    try { token = credentialStore.read(); }
    catch {
      invalidate();
      setStorageFeedback(new CredentialStorageError('read').message);
      setAuthState({ ...signedOut, verification: 'unavailable' });
      return;
    }
    setStorageFeedback(null);
    if (!mounted.current || (pending.current && pending.current.token === token)) return;
    invalidate();
    if (!token) {
      setAuthState(signedOut);
      return;
    }
    const attempt = generation.current;
    const controller = new AbortController();
    pending.current = { token, controller };
    setAuthState({ ...signedOut, verification: 'pending' });

    function isCurrent() {
      if (!mounted.current || generation.current !== attempt) return false;
      let stored: string | null;
      try { stored = credentialStore.read(); }
      catch {
        invalidate();
        setStorageFeedback(new CredentialStorageError('read').message);
        setAuthState({ ...signedOut, verification: 'unavailable' });
        return false;
      }
      if (stored !== token) {
        // A replaced/removed credential cannot inherit this attempt's result (including 401).
        invalidate();
        setAuthState(stored ? { ...signedOut, verification: 'unavailable' } : signedOut);
        return false;
      }
      pending.current = null;
      return true;
    }

    try {
      const user = await verifyStartupSession(token, controller);
      if (isCurrent()) {
        establish(token, user);
      }
    } catch (error) {
      if (!isCurrent()) return;
      // /me's resolver uses 401 for absent/invalid/expired/revoked/ineligible sessions.
      // Other 4xx, transport, server and unusable-body failures do not reject the bearer.
      if (error instanceof ApiError && error.status === 401) {
        clearCredential();
        setAuthState(signedOut);
      } else {
        setAuthState({ ...signedOut, verification: 'unavailable' });
      }
    }
  }

  useEffect(() => {
    mounted.current = true;
    void retry();
    return () => { mounted.current = false; invalidate(); };
  }, []);

  function signIn(accessToken: string, user: AuthenticatedUser) {
    if (!mounted.current) return;
    invalidate();
    logoutConsumed.current = false;
    setLogoutFeedback(null);
    setAuthState(signedOut);
    setStorageFeedback(null);
    try { credentialStore.store(accessToken); }
    catch {
      clearCredential();
      throw new CredentialStorageError('store');
    }
    locallyEnded.current = false;
    establish(accessToken, user);
  }

  function loginFailed() {
    // LoginPage is still signed out: preserve existing logout/session-ended feedback.
    // A failed persistence installation is already handled by signIn itself.
    if (mounted.current) clearCredential();
  }

  function signOut() {
    invalidate();
    clearCredential();
    setLogoutFeedback(null);
    setSessionEnded(false);
    setAuthState(signedOut);
  }

  function logoutSession(): boolean {
    const token = authState.accessToken;
    if (!mounted.current || !token || logoutConsumed.current || generation.current !== renderedGeneration) return false;
    logoutConsumed.current = true; // Synchronous: a stale handler cannot activate twice.
    signOut();
    setLogoutFeedback('pending');
    const attempt = generation.current;
    const controller = new AbortController();
    remoteLogout.current = controller;
    function isCurrent() {
      if (!mounted.current || generation.current !== attempt || !locallyEnded.current) return false;
      try { return credentialStore.read() === null; } catch { return false; }
    }
    // The only captured bearer belongs to this bounded attempt; nothing is restored/replayed.
    void logout(token, controller.signal).then(() => {
      if (isCurrent()) setLogoutFeedback('confirmed');
    }, () => {
      if (isCurrent()) setLogoutFeedback('unavailable');
    }).finally(() => {
      if (remoteLogout.current === controller) remoteLogout.current = null;
    });
    return true;
  }

  return { authState, storageFeedback, logoutFeedback, sessionEnded, sessionGeneration: generation.current, retry, signIn, loginFailed, signOut, logoutSession };
}
