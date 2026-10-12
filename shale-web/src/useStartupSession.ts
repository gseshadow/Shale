import { useEffect, useRef, useState } from 'react';
import { ApiError, getCurrentUser, login, logout } from './api';
import { SessionAttemptTimedOut, withSessionDeadline } from './sessionDeadline';
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
export const STARTUP_VERIFICATION_TIMEOUT_MS = 8_000;
// Login POST and subsequent /me share eight seconds; neither stage resets it.
export const CREDENTIAL_LOGIN_TIMEOUT_MS = 8_000;
export const SIGN_IN_UNCONFIRMED = 'Shale could not confirm sign-in. You are not signed in in this tab. A server session may have been created and may still be active. You can try signing in again.';

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
  const credentialAttempt = useRef<AbortController | null>(null);
  const renderedGeneration = generation.current;

  function invalidate() {
    generation.current++;
    credentialAttempt.current?.abort();
    credentialAttempt.current = null;
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
      const user = await withSessionDeadline(controller, STARTUP_VERIFICATION_TIMEOUT_MS,
        () => getCurrentUser(token, controller.signal));
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

  async function signInWithCredentials(email: string, password: string, signal: AbortSignal): Promise<boolean> {
    if (!mounted.current || featureBinding.current || authState.user || signal.aborted || credentialAttempt.current) return false;
    let previousCredential: string | null;
    try { previousCredential = credentialStore.read(); }
    catch { throw new CredentialStorageError('read'); }
    invalidate();
    const attempt = generation.current;
    const controller = new AbortController();
    const deadline = performance.now() + CREDENTIAL_LOGIN_TIMEOUT_MS;
    credentialAttempt.current = controller;
    const cancel = () => controller.abort();
    signal.addEventListener('abort', cancel, { once: true });
    function isCurrent() {
      if (!mounted.current || generation.current !== attempt || credentialAttempt.current !== controller) return false;
      try { return credentialStore.read() === previousCredential; }
      catch {
        signOut();
        setStorageFeedback(new CredentialStorageError('read').message);
        return false;
      }
    }
    let verifying = false;
    try {
      const verified = await withSessionDeadline(controller, CREDENTIAL_LOGIN_TIMEOUT_MS, async assertActive => {
        const result = await login(email, password, controller.signal);
        assertActive();
        if (!isCurrent()) { controller.abort(); return null; }
        verifying = true;
        const user = await getCurrentUser(result.accessToken, controller.signal);
        assertActive();
        if (!isCurrent()) { controller.abort(); return null; }
        if (user.userId !== result.user.userId || user.shaleClientId !== result.user.shaleClientId) {
          throw new Error('Unusable identity.');
        }
        return { accessToken: result.accessToken, user };
      });
      if (!verified || !isCurrent() || signal.aborted) return false;
      if (performance.now() >= deadline) throw new SessionAttemptTimedOut();
      // The exchange and timer are finished; install only under current authority.
      credentialAttempt.current = null;
      signIn(verified.accessToken, verified.user);
      return true;
    } catch (error) {
      // Storage installation owns its teardown; every other failure needs current authority.
      if (error instanceof CredentialStorageError) throw error;
      if (!isCurrent() || signal.aborted) return false;
      loginFailed();
      if (!verifying && error instanceof ApiError && error.status === 401) {
        throw new Error('The email or password was not accepted by Shale.');
      }
      if (verifying && error instanceof ApiError && error.status === 401) {
        throw new Error('Shale rejected the new session during verification. You are not signed in. Sign in again to continue.');
      }
      throw new Error(error instanceof SessionAttemptTimedOut
        ? `Sign-in timed out. ${SIGN_IN_UNCONFIRMED}` : SIGN_IN_UNCONFIRMED);
    } finally {
      signal.removeEventListener('abort', cancel);
      if (credentialAttempt.current === controller) credentialAttempt.current = null;
      controller.abort();
    }
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
    if (mounted.current && !token && credentialAttempt.current) {
      // Ending an unverified attempt is local only; no captured bearer to revoke.
      signOut();
      return true;
    }
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

  return { authState, storageFeedback, logoutFeedback, sessionEnded, sessionGeneration: generation.current, retry, signIn, signInWithCredentials, loginFailed, signOut, logoutSession };
}
