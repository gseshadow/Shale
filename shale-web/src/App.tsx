import { FormEvent, useEffect, useMemo, useState } from 'react';
import { BrowserRouter, Link, Navigate, NavLink, Outlet, Route, Routes, useLocation, useNavigate, useParams } from 'react-router-dom';
import { AuthenticatedUser, CaseDetail, CaseSearchResult, CaseTaskListItem, apiBaseUrl, clearAccessToken, getCaseDetail, getCurrentUser, listAssignedCases, listAssignedTasks, login, logout, readAccessToken, searchCases, storeAccessToken } from './api';
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
          <Route path="/my-shale" element={<MyShalePage accessToken={authState.accessToken} user={authState.user} />} />
          <Route path="/cases" element={<CasesPage accessToken={authState.accessToken} />} />
          <Route path="/cases/:caseId" element={<CaseDetailPage accessToken={authState.accessToken} />} />
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


function MyShalePage({ accessToken, user }: { accessToken: string | null; user: AuthenticatedUser | null }) {
  return (
    <section className="dashboard-page" aria-labelledby="my-shale-title">
      <div className="dashboard-header">
        <p className="eyebrow">Dashboard</p>
        <h1 id="my-shale-title">My Shale</h1>
        <p className="lede">Welcome{user ? `, ${displayNameFor(user)}` : ''}. Here is your read-only Shale summary.</p>
      </div>
      <MyCasesSection accessToken={accessToken} />
      <MyTasksSection accessToken={accessToken} />
    </section>
  );
}

function MyCasesSection({ accessToken }: { accessToken: string | null }) {
  const [cases, setCases] = useState<CaseSearchResult[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!accessToken) {
      setError('Your Shale session is not available. Please sign in again.');
      setIsLoading(false);
      return;
    }
    let isCurrent = true;
    setIsLoading(true);
    setError(null);
    listAssignedCases(accessToken)
      .then((items) => isCurrent && setCases(items))
      .catch((caught) => isCurrent && setError(caught instanceof Error ? caught.message : 'Your cases could not be loaded.'))
      .finally(() => isCurrent && setIsLoading(false));
    return () => { isCurrent = false; };
  }, [accessToken]);

  return (
    <section className="dashboard-section" aria-labelledby="my-cases-title">
      <h2 id="my-cases-title">My Cases</h2>
      {isLoading && <p className="status">Loading your cases…</p>}
      {!isLoading && error && <p className="status error" role="alert">{error}</p>}
      {!isLoading && !error && cases.length === 0 && <p className="status">No assigned cases were found.</p>}
      {!isLoading && !error && cases.length > 0 && <MyCasesTable cases={cases} />}
    </section>
  );
}

function MyCasesTable({ cases }: { cases: CaseSearchResult[] }) {
  const navigate = useNavigate();
  return (
    <div className="table-wrap">
      <table className="results-table">
        <thead><tr><th>Case name</th><th>Status</th><th>Responsible attorney</th><th>Statute of limitations</th></tr></thead>
        <tbody>{cases.map((item) => (
          <tr key={item.caseId} className="clickable-row" tabIndex={0} onClick={() => navigate(`/cases/${item.caseId}`)} onKeyDown={(event) => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); navigate(`/cases/${item.caseId}`); } }}>
            <td>{item.caseName || `Case ${item.caseId}`}</td><td>{item.caseStatus || '—'}</td><td>{item.responsibleAttorney || '—'}</td><td>{item.solDate ?? '—'}</td>
          </tr>
        ))}</tbody>
      </table>
    </div>
  );
}

function MyTasksSection({ accessToken }: { accessToken: string | null }) {
  const [tasks, setTasks] = useState<CaseTaskListItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!accessToken) {
      setError('Your Shale session is not available. Please sign in again.');
      setIsLoading(false);
      return;
    }
    let isCurrent = true;
    setIsLoading(true);
    setError(null);
    listAssignedTasks(accessToken)
      .then((items) => isCurrent && setTasks(items))
      .catch((caught) => isCurrent && setError(caught instanceof Error ? caught.message : 'Your tasks could not be loaded.'))
      .finally(() => isCurrent && setIsLoading(false));
    return () => { isCurrent = false; };
  }, [accessToken]);

  return (
    <section className="dashboard-section" aria-labelledby="my-tasks-title">
      <h2 id="my-tasks-title">My Tasks</h2>
      {isLoading && <p className="status">Loading your tasks…</p>}
      {!isLoading && error && <p className="status error" role="alert">{error}</p>}
      {!isLoading && !error && tasks.length === 0 && <p className="status">No assigned tasks were found.</p>}
      {!isLoading && !error && tasks.length > 0 && <MyTasksTable tasks={tasks} />}
    </section>
  );
}

function MyTasksTable({ tasks }: { tasks: CaseTaskListItem[] }) {
  return (
    <div className="table-wrap">
      <table className="results-table">
        <thead><tr><th>Task</th><th>Case</th><th>Due date</th><th>Priority</th><th>Status</th></tr></thead>
        <tbody>{tasks.map((task) => (
          <tr key={task.id}>
            <td>{task.title || `Task ${task.id}`}</td><td>{task.caseName || '—'}</td><td>{task.dueAt ?? '—'}</td><td>{task.priorityId ? `Priority ${task.priorityId}` : '—'}</td><td>{task.completedAt ? 'Completed' : 'Open'}</td>
          </tr>
        ))}</tbody>
      </table>
    </div>
  );
}

function CasesPage({ accessToken }: { accessToken: string | null }) {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<CaseSearchResult[]>([]);
  const [hasSearched, setHasSearched] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSearch(event?: FormEvent<HTMLFormElement>) {
    event?.preventDefault();
    const trimmedQuery = query.trim();

    if (!trimmedQuery) {
      setResults([]);
      setHasSearched(false);
      setError(null);
      return;
    }

    if (!accessToken) {
      setResults([]);
      setHasSearched(true);
      setError('Your Shale session is not available. Please sign in again.');
      return;
    }

    setIsLoading(true);
    setError(null);
    setHasSearched(true);

    try {
      const searchResults = await searchCases(accessToken, trimmedQuery);
      setResults(searchResults);
    } catch (caught) {
      setResults([]);
      setError(caught instanceof Error ? caught.message : 'Case search failed.');
    } finally {
      setIsLoading(false);
    }
  }

  return (
    <section className="cases-page" aria-labelledby="cases-title">
      <h1 id="cases-title">Cases</h1>
      <form className="search-form" onSubmit={handleSearch}>
        <label htmlFor="case-search">Search cases</label>
        <div className="search-row">
          <input
            id="case-search"
            type="search"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Enter a case name, number, or client"
          />
          <button type="submit" disabled={isLoading}>{isLoading ? 'Searching…' : 'Search'}</button>
        </div>
      </form>

      <div className="results-area" aria-live="polite">
        {isLoading && <p className="status">Loading case results…</p>}
        {!isLoading && error && <p className="status error" role="alert">{error}</p>}
        {!isLoading && !error && hasSearched && results.length === 0 && <p className="status">No cases matched your search.</p>}
        {!isLoading && !error && results.length > 0 && <CaseResultsTable results={results} />}
      </div>
    </section>
  );
}

function CaseResultsTable({ results }: { results: CaseSearchResult[] }) {
  const navigate = useNavigate();

  return (
    <div className="table-wrap">
      <table className="results-table">
        <thead>
          <tr>
            <th>Case ID</th>
            <th>Case number</th>
            <th>Case name</th>
            <th>Status</th>
            <th>Responsible attorney</th>
            <th>Practice area</th>
            <th>Intake date</th>
            <th>Client</th>
          </tr>
        </thead>
        <tbody>
          {results.map((result) => (
            <tr
              key={result.caseId}
              className="clickable-row"
              tabIndex={0}
              onClick={() => navigate(`/cases/${result.caseId}`)}
              onKeyDown={(event) => {
                if (event.key === 'Enter' || event.key === ' ') {
                  event.preventDefault();
                  navigate(`/cases/${result.caseId}`);
                }
              }}
            >
              <td>{result.caseId}</td>
              <td>{result.caseNumber}</td>
              <td>{result.caseName}</td>
              <td>{result.caseStatus}</td>
              <td>{result.responsibleAttorney}</td>
              <td>{result.practiceArea}</td>
              <td>{result.intakeDate ?? '—'}</td>
              <td>{result.client}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}


function CaseDetailPage({ accessToken }: { accessToken: string | null }) {
  const { caseId } = useParams();
  const numericCaseId = Number(caseId);
  const [caseDetail, setCaseDetail] = useState<CaseDetail | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!Number.isInteger(numericCaseId) || numericCaseId <= 0) {
      setCaseDetail(null);
      setError('That case link is not valid.');
      setIsLoading(false);
      return;
    }

    if (!accessToken) {
      setCaseDetail(null);
      setError('Your Shale session is not available. Please sign in again.');
      setIsLoading(false);
      return;
    }

    let isCurrent = true;
    setIsLoading(true);
    setError(null);

    getCaseDetail(accessToken, numericCaseId)
      .then((detail) => {
        if (isCurrent) {
          setCaseDetail(detail);
        }
      })
      .catch((caught) => {
        if (isCurrent) {
          setCaseDetail(null);
          setError(caught instanceof Error ? caught.message : 'Case detail could not be loaded.');
        }
      })
      .finally(() => {
        if (isCurrent) {
          setIsLoading(false);
        }
      });

    return () => {
      isCurrent = false;
    };
  }, [accessToken, numericCaseId]);

  const title = caseDetail?.caseName || 'Case Detail';

  return (
    <section className="case-detail-page" aria-labelledby="case-detail-title">
      <nav className="breadcrumb" aria-label="Breadcrumb">
        <Link to="/cases">Cases</Link>
        <span aria-hidden="true">›</span>
        <span>{title}</span>
      </nav>
      <div className="page-heading-row">
        <div>
          <p className="eyebrow">Case Detail</p>
          <h1 id="case-detail-title">{title}</h1>
        </div>
        <Link className="button-link" to="/cases">Back to Cases</Link>
      </div>

      {isLoading && <p className="status">Loading case detail…</p>}
      {!isLoading && error && <p className="status error" role="alert">{error}</p>}
      {!isLoading && !error && caseDetail && <CaseDetailReadOnly detail={caseDetail} />}
    </section>
  );
}

function CaseDetailReadOnly({ detail }: { detail: CaseDetail }) {
  return (
    <div className="detail-sections">
      <section aria-labelledby="case-info-title">
        <h2 id="case-info-title">Case Information</h2>
        <dl className="detail-list">
          <DetailItem label="Case Name" value={detail.caseName} />
          <DetailItem label="Case Number" value={detail.caseNumber} />
          <DetailItem label="Status" value={detail.caseStatus} />
          <DetailItem label="Practice Area" value={detail.practiceAreaId ? `Practice Area ID ${detail.practiceAreaId}` : null} />
          <DetailItem label="Description" value={detail.description} preserveWhitespace />
          <DetailItem label="Summary" value={detail.summary} preserveWhitespace />
        </dl>
      </section>

      <section aria-labelledby="important-dates-title">
        <h2 id="important-dates-title">Important Dates</h2>
        <dl className="detail-list compact">
          <DetailItem label="Intake Date" value={detail.callerDate} />
          <DetailItem label="Date of Injury" value={detail.dateOfInjury} />
          <DetailItem label="Statute of Limitations" value={detail.statuteOfLimitations} />
          <DetailItem label="Tort Notice Deadline" value={detail.tortNoticeDeadline} />
        </dl>
      </section>

      <section aria-labelledby="assignments-title">
        <h2 id="assignments-title">Assignments</h2>
        <dl className="detail-list compact">
          <DetailItem label="Responsible Attorney" value="Not currently returned by the case-detail endpoint." />
        </dl>
      </section>
    </div>
  );
}

function DetailItem({ label, value, preserveWhitespace = false }: { label: string; value: string | number | null | undefined; preserveWhitespace?: boolean }) {
  const displayValue = value === null || value === undefined || value === '' ? '—' : String(value);
  return (
    <div>
      <dt>{label}</dt>
      <dd className={preserveWhitespace ? 'preserve-whitespace' : undefined}>{displayValue}</dd>
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
