import { FormEvent, useEffect, useMemo, useState } from 'react';
import {
  AuthenticatedUser,
  CaseSearchResult,
  apiBaseUrl,
  clearAccessToken,
  getCurrentUser,
  login,
  logout,
  readAccessToken,
  searchCases,
  storeAccessToken,
} from './api';
import './styles.css';

function displayNameFor(user: AuthenticatedUser): string {
  return user.displayName || [user.nameFirst, user.nameLast].filter(Boolean).join(' ') || user.email || `User ${user.userId}`;
}

function displayDate(value: string | null): string {
  return value || '—';
}

export default function App() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [accessToken, setAccessToken] = useState<string | null>(() => readAccessToken());
  const [user, setUser] = useState<AuthenticatedUser | null>(null);
  const [tokenPreview, setTokenPreview] = useState<string | null>(null);
  const [caseQuery, setCaseQuery] = useState('');
  const [caseResults, setCaseResults] = useState<CaseSearchResult[]>([]);
  const [caseSearchAttempted, setCaseSearchAttempted] = useState(false);
  const [caseSearchLoading, setCaseSearchLoading] = useState(false);
  const [caseSearchError, setCaseSearchError] = useState<string | null>(null);
  const baseUrl = useMemo(() => apiBaseUrl(), []);

  useEffect(() => {
    if (!accessToken || user) {
      return;
    }

    let isCurrent = true;
    getCurrentUser(accessToken)
      .then((verifiedUser) => {
        if (isCurrent) {
          setUser(verifiedUser);
          setTokenPreview(`Bearer ${accessToken.slice(0, 16)}…`);
        }
      })
      .catch(() => {
        if (isCurrent) {
          clearAccessToken();
          setAccessToken(null);
        }
      });

    return () => {
      isCurrent = false;
    };
  }, [accessToken, user]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setUser(null);
    setTokenPreview(null);
    setIsSubmitting(true);

    try {
      const result = await login(email, password);
      storeAccessToken(result.accessToken);
      setAccessToken(result.accessToken);
      const verifiedUser = await getCurrentUser(result.accessToken);
      setUser(verifiedUser);
      setTokenPreview(`${result.tokenType} ${result.accessToken.slice(0, 16)}…`);
      setPassword('');
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Login failed.');
      clearAccessToken();
      setAccessToken(null);
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handleLogout() {
    const token = accessToken;
    clearAccessToken();
    setAccessToken(null);
    setUser(null);
    setTokenPreview(null);
    setCaseResults([]);
    setCaseSearchAttempted(false);
    setCaseSearchError(null);
    if (token) {
      await logout(token);
    }
  }

  async function handleCaseSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!accessToken) {
      setCaseSearchError('Sign in before searching cases.');
      return;
    }

    setCaseSearchAttempted(true);
    setCaseSearchLoading(true);
    setCaseSearchError(null);

    try {
      const results = await searchCases(accessToken, caseQuery);
      setCaseResults(results);
    } catch (caught) {
      setCaseResults([]);
      setCaseSearchError(caught instanceof Error ? caught.message : 'Case search failed.');
    } finally {
      setCaseSearchLoading(false);
    }
  }

  return (
    <main className="app-shell">
      <section className="hero-card">
        <p className="eyebrow">Shale Web</p>
        <h1>Browser login for the deployed Azure API</h1>
        <p className="lede">This standalone React + TypeScript + Vite app validates the first web milestone without changing the existing JavaFX/Maven desktop app.</p>
        <p className="api-note">API target: <code>{baseUrl}</code></p>
      </section>

      <section className="login-card" aria-labelledby="login-title">
        <div className="section-header">
          <h2 id="login-title">Sign in</h2>
          {user && <button type="button" onClick={handleLogout}>Logout</button>}
        </div>
        <form onSubmit={handleSubmit}>
          <label>
            Email
            <input type="email" value={email} onChange={(event) => setEmail(event.target.value)} autoComplete="username" required />
          </label>
          <label>
            Password
            <input type="password" value={password} onChange={(event) => setPassword(event.target.value)} autoComplete="current-password" required />
          </label>
          <button type="submit" disabled={isSubmitting}>{isSubmitting ? 'Signing in…' : 'Sign in'}</button>
        </form>
        {error && <p className="status error" role="alert">{error}</p>}
        {user && <p className="status success">Verified by <code>/api/auth/me</code> as <strong>{displayNameFor(user)}</strong> for tenant <strong>{user.shaleClientId}</strong>. Token: <code>{tokenPreview}</code></p>}
      </section>

      {user && (
        <section className="search-panel" aria-labelledby="case-search-title">
          <h2 id="case-search-title">Case search</h2>
          <form className="search-form" onSubmit={handleCaseSearch}>
            <label>
              Search cases
              <input type="search" value={caseQuery} onChange={(event) => setCaseQuery(event.target.value)} placeholder="Name, number, client, or attorney" />
            </label>
            <button type="submit" disabled={caseSearchLoading}>{caseSearchLoading ? 'Searching…' : 'Search'}</button>
          </form>

          {caseSearchLoading && <p className="status">Loading case results…</p>}
          {caseSearchError && <p className="status error" role="alert">{caseSearchError}</p>}
          {!caseSearchLoading && !caseSearchError && caseSearchAttempted && caseResults.length === 0 && <p className="status">No cases matched this search.</p>}
          {!caseSearchLoading && caseResults.length > 0 && (
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Case #</th>
                    <th>Name</th>
                    <th>Status</th>
                    <th>Client</th>
                    <th>Attorney</th>
                    <th>Practice area</th>
                    <th>Intake</th>
                  </tr>
                </thead>
                <tbody>
                  {caseResults.map((result) => (
                    <tr key={result.caseId}>
                      <td>{result.caseNumber || result.caseId}</td>
                      <td>{result.caseName || '—'}</td>
                      <td>{result.caseStatus || '—'}</td>
                      <td>{result.client || '—'}</td>
                      <td>{result.responsibleAttorney || '—'}</td>
                      <td>{result.practiceArea || '—'}</td>
                      <td>{displayDate(result.intakeDate)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      )}
    </main>
  );
}
