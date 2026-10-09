import { useEffect, useRef, useState } from 'react';
import { ApiError, clearAccessToken, getCurrentUser, logout, readAccessToken, storeAccessToken } from './api';
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
export function useStartupSession() {
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

  function establish(token: string, user: AuthenticatedUser) {
    const attempt = generation.current;
    featureBinding.current = bindSessionRequests(token,
      () => mounted.current && generation.current === attempt && readAccessToken() === token,
      () => {
        // The seam has already consumed this binding. No remote logout or replay.
        invalidate();
        clearAccessToken();
        setLogoutFeedback(null);
        setSessionEnded(true);
        setAuthState(signedOut);
      });
    logoutConsumed.current = false;
    setSessionEnded(false);
    setAuthState({ accessToken: token, user, verification: null });
  }

  async function retry() {
    const token = readAccessToken();
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
      if (readAccessToken() !== token) {
        // A replaced/removed credential cannot inherit this attempt's result (including 401).
        invalidate();
        setAuthState(readAccessToken() ? { ...signedOut, verification: 'unavailable' } : signedOut);
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
        clearAccessToken();
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
    storeAccessToken(accessToken);
    establish(accessToken, user);
  }

  function signOut() {
    invalidate();
    clearAccessToken();
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
      return mounted.current && generation.current === attempt && readAccessToken() === null;
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

  return { authState, logoutFeedback, sessionEnded, sessionGeneration: generation.current, retry, signIn, signOut, logoutSession };
}
