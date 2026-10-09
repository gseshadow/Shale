import { useEffect, useRef, useState } from 'react';
import { ApiError, clearAccessToken, getCurrentUser, logout, readAccessToken, storeAccessToken } from './api';
import type { AuthenticatedUser } from './api';

export interface AuthState {
  accessToken: string | null;
  user: AuthenticatedUser | null;
  verification: 'pending' | 'unavailable' | null;
}

const signedOut: AuthState = { accessToken: null, user: null, verification: null };

export type LogoutFeedback = 'pending' | 'confirmed' | 'unavailable' | null;

// Startup recovery and the established-session sign-out boundary share invalidation.
export function useStartupSession() {
  const [authState, setAuthState] = useState<AuthState>({ ...signedOut, verification: 'pending' });
  const [logoutFeedback, setLogoutFeedback] = useState<LogoutFeedback>(null);
  const remoteLogout = useRef<AbortController | null>(null);
  const logoutConsumed = useRef(false);
  const mounted = useRef(false);
  const generation = useRef(0);
  const pending = useRef<{ token: string } | null>(null);
  const renderedGeneration = generation.current;

  function invalidate() {
    generation.current++;
    remoteLogout.current?.abort();
    remoteLogout.current = null;
    pending.current = null;
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
    pending.current = { token };
    setAuthState({ ...signedOut, verification: 'pending' });

    function isCurrent() {
      if (!mounted.current || generation.current !== attempt) return false;
      if (readAccessToken() !== token) {
        // A replaced/removed credential cannot inherit this attempt's result (including 401).
        pending.current = null;
        setAuthState(readAccessToken() ? { ...signedOut, verification: 'unavailable' } : signedOut);
        return false;
      }
      pending.current = null;
      return true;
    }

    try {
      const user = await getCurrentUser(token);
      if (isCurrent()) {
        logoutConsumed.current = false;
        setAuthState({ accessToken: token, user, verification: null });
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
    invalidate();
    logoutConsumed.current = false;
    setLogoutFeedback(null);
    storeAccessToken(accessToken);
    setAuthState({ accessToken, user, verification: null });
  }

  function signOut() {
    invalidate();
    clearAccessToken();
    setLogoutFeedback(null);
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

  return { authState, logoutFeedback, retry, signIn, signOut, logoutSession };
}
