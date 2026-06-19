import { FormEvent, useEffect, useMemo, useState } from 'react';
import { BrowserRouter, Navigate, NavLink, Outlet, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { AuthenticatedUser, apiBaseUrl, clearAccessToken, getCurrentUser, login, logout, readAccessToken, storeAccessToken } from './api';
import './styles.css';

interface AuthState {
  accessToken: string | null;
  user: AuthenticatedUser | null;
  isVerifying: boolean;
}

const navigationItems = [
  { path: '/my-shale', label: 'My Shale' },
  { path: '/cases', label: 'Cases' },
  { path: '/tasks', label: 'Tasks' },
  { path: '/contacts', label: 'Contacts' },
  { path: '/organizations', label: 'Organizations' },
  { path: '/team', label: 'Team' },
  { path: '/settings', label: 'Settings' },
];

function displayNameFor(user: AuthenticatedUser): string {
  return user.displayName || [user.nameFirst, user.nameLast].filter(Boolean).join(' ') || user.email || `User ${user.userId}`;
}

function AppRoutes() {
  const [authState, setAuthState] = useState<AuthState>(() => ({
    accessToken: readAccessToken(),
    user: null,
    isVerifying: true,
  }));

  useEffect(() => {
    const storedToken = readAccessToken();
    if (!storedToken) {
      setAuthState({ accessToken: null, user: null, isVerifying: false });
      return;
    }

    let isCurrent = true;
    getCurrentUser(storedToken)
      .then((verifiedUser) => {
        if (isCurrent) {
          setAuthState({ accessToken: storedToken, user: verifiedUser, isVerifying: false });
        }
      })
      .catch(() => {
        if (isCurrent) {
          clearAccessToken();
          setAuthState({ accessToken: null, user: null, isVerifying: false });
        }
      });

    return () => {
      isCurrent = false;
    };
  }, []);

  function handleLogin(verifiedAccessToken: string, user: AuthenticatedUser) {
    storeAccessToken(verifiedAccessToken);
    setAuthState({ accessToken: verifiedAccessToken, user, isVerifying: false });
  }

  async function handleLogout() {
    const token = authState.accessToken;
    clearAccessToken();
    setAuthState({ accessToken: null, user: null, isVerifying: false });
    if (token) {
      await logout(token);
    }
  }

  return (
    <Routes>
      <Route path="/" element={authState.user ? <Navigate to="/my-shale" replace /> : <Navigate to="/login" replace />} />
      <Route
        path="/login"
        element={authState.user ? <Navigate to="/my-shale" replace /> : <LoginPage isVerifying={authState.isVerifying} onLogin={handleLogin} />}
      />
      <Route element={<ProtectedRoute authState={authState} />}>
        <Route element={<AuthenticatedShell user={authState.user} onLogout={handleLogout} />}>
          <Route path="/my-shale" element={<PlaceholderPage title="My Shale" />} />
          <Route path="/cases" element={<PlaceholderPage title="Cases" />} />
          <Route path="/tasks" element={<PlaceholderPage title="Tasks" />} />
          <Route path="/contacts" element={<PlaceholderPage title="Contacts" />} />
          <Route path="/organizations" element={<PlaceholderPage title="Organizations" />} />
          <Route path="/team" element={<PlaceholderPage title="Team" />} />
          <Route path="/settings" element={<PlaceholderPage title="Settings" />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to={authState.user ? '/my-shale' : '/login'} replace />} />
    </Routes>
  );
}

function ProtectedRoute({ authState }: { authState: AuthState }) {
  const location = useLocation();

  if (authState.isVerifying) {
    return <FullPageStatus message="Checking your Shale session…" />;
  }

  if (!authState.user) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  return <Outlet />;
}

function LoginPage({ isVerifying, onLogin }: { isVerifying: boolean; onLogin: (verifiedAccessToken: string, verifiedUser: AuthenticatedUser) => void }) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const baseUrl = useMemo(() => apiBaseUrl(), []);
  const navigate = useNavigate();
  const location = useLocation();

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setIsSubmitting(true);

    try {
      const result = await login(email, password);
      const verifiedUser = await getCurrentUser(result.accessToken);
      onLogin(result.accessToken, verifiedUser);
      navigate(redirectPathFrom(location.state), { replace: true });
      setPassword('');
    } catch (caught) {
      clearAccessToken();
      setError(caught instanceof Error ? caught.message : 'Login failed.');
      clearAccessToken();
      setAccessToken(null);
    } finally {
      setIsSubmitting(false);
    }
  }

  if (isVerifying) {
    return <FullPageStatus message="Checking your Shale session…" />;
  }

  return (
    <main className="login-page">
      <section className="login-panel" aria-labelledby="login-title">
        <p className="eyebrow">Shale Web</p>
        <h1 id="login-title">Sign in</h1>
        <p className="lede">Use your Shale account to access the web application shell.</p>
        <p className="api-note">API target: <code>{baseUrl}</code></p>
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

function redirectPathFrom(state: unknown): string {
  if (!state || typeof state !== 'object' || !('from' in state)) {
    return '/my-shale';
  }

  const from = (state as { from?: unknown }).from;
  if (!from || typeof from !== 'object' || !('pathname' in from)) {
    return '/my-shale';
  }

  const pathname = (from as { pathname?: unknown }).pathname;
  return typeof pathname === 'string' ? pathname : '/my-shale';
}

function AuthenticatedShell({ user, onLogout }: { user: AuthenticatedUser | null; onLogout: () => void }) {
  if (!user) {
    return null;
  }

  return (
    <div className="app-layout">
      <aside className="sidebar" aria-label="Primary navigation">
        <div className="brand">
          <span className="brand-mark" aria-hidden="true">S</span>
          <span>Shale</span>
        </div>
        <nav className="nav-list">
          {navigationItems.map((item) => (
            <NavLink key={item.path} to={item.path} className={({ isActive }) => isActive ? 'nav-link active' : 'nav-link'}>
              {item.label}
            </NavLink>
          ))}
        </nav>
      </aside>

      <div className="content-column">
        <header className="topbar">
          <div>
            <p className="eyebrow">Signed in</p>
            <p className="user-name">{displayNameFor(user)}</p>
          </div>
          <button type="button" onClick={onLogout}>Logout</button>
        </header>
        <main className="page-content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}

function PlaceholderPage({ title }: { title: string }) {
  return (
    <section className="placeholder-page" aria-labelledby="page-title">
      <p className="eyebrow">Placeholder</p>
      <h1 id="page-title">{title}</h1>
      <p>This page is part of the Step 5C navigation framework. Business functionality will be added in a later step.</p>
    </section>
  );
}

function FullPageStatus({ message }: { message: string }) {
  return (
    <main className="full-page-status">
      <p className="status">{message}</p>
    </main>
  );
}

function redirectPathFrom(state: unknown): string {
  if (!state || typeof state !== 'object' || !('from' in state)) {
    return '/my-shale';
  }

  const from = (state as { from?: unknown }).from;
  if (!from || typeof from !== 'object' || !('pathname' in from)) {
    return '/my-shale';
  }

  const pathname = (from as { pathname?: unknown }).pathname;
  return typeof pathname === 'string' ? pathname : '/my-shale';
}

function AuthenticatedShell({ user, onLogout }: { user: AuthenticatedUser | null; onLogout: () => void }) {
  if (!user) {
    return null;
  }

  return (
    <div className="app-layout">
      <aside className="sidebar" aria-label="Primary navigation">
        <div className="brand">
          <span className="brand-mark" aria-hidden="true">S</span>
          <span>Shale</span>
        </div>
        <nav className="nav-list">
          {navigationItems.map((item) => (
            <NavLink key={item.path} to={item.path} className={({ isActive }) => isActive ? 'nav-link active' : 'nav-link'}>
              {item.label}
            </NavLink>
          ))}
        </nav>
      </aside>

      <div className="content-column">
        <header className="topbar">
          <div>
            <p className="eyebrow">Signed in</p>
            <p className="user-name">{displayNameFor(user)}</p>
          </div>
          <button type="button" onClick={onLogout}>Logout</button>
        </header>
        <main className="page-content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}

function PlaceholderPage({ title }: { title: string }) {
  return (
    <section className="placeholder-page" aria-labelledby="page-title">
      <p className="eyebrow">Placeholder</p>
      <h1 id="page-title">{title}</h1>
      <p>This page is part of the Step 5C navigation framework. Business functionality will be added in a later step.</p>
    </section>
  );
}

function FullPageStatus({ message }: { message: string }) {
  return (
    <main className="full-page-status">
      <p className="status">{message}</p>
    </main>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <AppRoutes />
    </BrowserRouter>
  );
}
