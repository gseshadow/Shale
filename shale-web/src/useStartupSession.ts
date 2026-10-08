import { useEffect, useRef, useState } from 'react';
import { ApiError, clearAccessToken, getCurrentUser, readAccessToken, storeAccessToken } from './api';
import type { AuthenticatedUser } from './api';

export interface AuthState {
  accessToken: string | null;
  user: AuthenticatedUser | null;
  verification: 'pending' | 'unavailable' | null;
}

const signedOut: AuthState = { accessToken: null, user: null, verification: null };

// Startup/recovery only. Established sessions retain their existing login/logout behavior.
export function useStartupSession() {
  const [authState, setAuthState] = useState<AuthState>({ ...signedOut, verification: 'pending' });
  const mounted = useRef(false);
  const generation = useRef(0);
  const pending = useRef<{ token: string } | null>(null);

  function invalidate() {
    generation.current++;
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
      if (isCurrent()) setAuthState({ accessToken: token, user, verification: null });
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
    storeAccessToken(accessToken);
    setAuthState({ accessToken, user, verification: null });
  }

  function signOut() {
    invalidate();
    clearAccessToken();
    setAuthState(signedOut);
  }

  return { authState, retry, signIn, signOut };
}
