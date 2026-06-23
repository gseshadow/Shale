import { FormEvent, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { BrowserRouter, Link, Navigate, Outlet, Route, Routes, useLocation, useNavigate, useParams } from 'react-router-dom';
import { AuthenticatedUser, CaseDetail, CaseRelatedContact, CaseSearchResult, CaseStatusSetting, CaseTaskListItem, ContactDetail, ContactSearchResult, OrganizationDetail, OrganizationSearchResult, PracticeAreaSetting, TaskDetail, TeamMemberDetail, TeamMemberSummary, apiBaseUrl, clearAccessToken, getCaseDetail, getContactDetail, getCurrentUser, getOrganizationDetail, getTaskDetail, getTeamMemberDetail, listAssignedCases, listAssignedTasks, listCaseTasks, listCaseStatusSettings, listPracticeAreaSettings, listTeamMembers, login, logout, readAccessToken, searchCases, searchContacts, searchOrganizations, storeAccessToken } from './api';
import './styles.css';

interface AuthState {
  accessToken: string | null;
  user: AuthenticatedUser | null;
  isVerifying: boolean;
}

const navigationItems = [
  { path: '/my-shale', label: 'My Shale', activePrefixes: ['/my-shale', '/tasks'] },
  { path: '/cases', label: 'Cases', activePrefixes: ['/cases'] },
  { path: '/contacts', label: 'Contacts', activePrefixes: ['/contacts'] },
  { path: '/organizations', label: 'Organizations', activePrefixes: ['/organizations'] },
  { path: '/team', label: 'Team', activePrefixes: ['/team'] },
  { path: '/settings', label: 'Settings', activePrefixes: ['/settings'] },
];

const MISSING_VALUE = '—';


function isMissing(value: string | number | null | undefined): boolean {
  return value === null || value === undefined || String(value).trim() === '';
}

function displayValue(value: string | number | null | undefined, fallback = MISSING_VALUE): string {
  return isMissing(value) ? fallback : String(value);
}

function formatDate(value: string | null | undefined): string {
  if (isMissing(value)) {
    return MISSING_VALUE;
  }

  const text = String(value);
  const dateOnlyMatch = /^(\d{4})-(\d{2})-(\d{2})/.exec(text);
  const date = dateOnlyMatch
    ? new Date(Number(dateOnlyMatch[1]), Number(dateOnlyMatch[2]) - 1, Number(dateOnlyMatch[3]))
    : new Date(text);

  if (Number.isNaN(date.getTime())) {
    return text;
  }

  return new Intl.DateTimeFormat(undefined, { year: 'numeric', month: 'short', day: 'numeric' }).format(date);
}

function PageHeader({ eyebrow, title, titleId, lede, action }: { eyebrow: string; title: string; titleId?: string; lede?: string; action?: ReactNode }) {
  return (
    <div className="page-heading-row">
      <div>
        <p className="eyebrow">{eyebrow}</p>
        <h1 id={titleId}>{title}</h1>
        {lede && <p className="lede">{lede}</p>}
      </div>
      {action}
    </div>
  );
}

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
          <Route path="/tasks/:taskId" element={<TaskDetailPage accessToken={authState.accessToken} />} />
          <Route path="/contacts" element={<ContactsPage accessToken={authState.accessToken} />} />
          <Route path="/contacts/:contactId" element={<ContactDetailPage accessToken={authState.accessToken} />} />
          <Route path="/organizations" element={<OrganizationsPage accessToken={authState.accessToken} />} />
          <Route path="/organizations/:organizationId" element={<OrganizationDetailPage accessToken={authState.accessToken} />} />
          <Route path="/team" element={<TeamPage accessToken={authState.accessToken} />} />
          <Route path="/team/:userId" element={<TeamMemberDetailPage accessToken={authState.accessToken} />} />
          <Route path="/settings" element={<SettingsPage accessToken={authState.accessToken} user={authState.user} />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to={authState.user ? '/my-shale' : '/login'} replace />} />
    </Routes>
  );
}


function SettingsPage({ accessToken, user }: { accessToken: string | null; user: AuthenticatedUser | null }) {
  const [caseStatuses, setCaseStatuses] = useState<CaseStatusSetting[]>([]);
  const [practiceAreas, setPracticeAreas] = useState<PracticeAreaSetting[]>([]);
  const [isLoadingAdmin, setIsLoadingAdmin] = useState(false);
  const [adminError, setAdminError] = useState<string | null>(null);

  useEffect(() => {
    if (!accessToken || !user?.isAdmin) {
      setCaseStatuses([]);
      setPracticeAreas([]);
      setIsLoadingAdmin(false);
      setAdminError(null);
      return;
    }

    let isCurrent = true;
    setIsLoadingAdmin(true);
    setAdminError(null);
    Promise.all([listCaseStatusSettings(accessToken), listPracticeAreaSettings(accessToken)])
      .then(([statusRows, practiceAreaRows]) => {
        if (isCurrent) {
          setCaseStatuses(statusRows);
          setPracticeAreas(practiceAreaRows);
        }
      })
      .catch((caught) => {
        if (isCurrent) {
          setAdminError(caught instanceof Error ? caught.message : 'Administrative settings could not be loaded.');
          setCaseStatuses([]);
          setPracticeAreas([]);
        }
      })
      .finally(() => {
        if (isCurrent) {
          setIsLoadingAdmin(false);
        }
      });

    return () => {
      isCurrent = false;
    };
  }, [accessToken, user?.isAdmin]);

  return (
    <section className="settings-page">
      <div className="page-heading-row">
        <div>
          <p className="eyebrow">Settings</p>
          <h1>Settings</h1>
          <p className="lede">Read-only account and tenant settings for the signed-in Shale session.</p>
          <span className="inline-beta-badge">Read-only beta</span>
        </div>
      </div>

      <div className="detail-sections">
        <section className="settings-card">
          <h2>Current User</h2>
          {user ? (
            <dl className="detail-list compact">
              <DetailItem label="Name" value={displayNameFor(user)} />
              <DetailItem label="Email" value={user.email} />
              <DetailItem label="User ID" value={String(user.userId)} />
              <DetailItem label="Role" value={[user.isAdmin ? 'Admin' : null, user.isAttorney ? 'Attorney' : null].filter(Boolean).join(', ') || 'Team member'} />
              <DetailItem label="Initials" value={user.initials} />
              <DetailItem label="Color" value={user.color} />
            </dl>
          ) : (
            <p className="status">No current user profile was loaded.</p>
          )}
        </section>

        <section className="settings-card">
          <h2>Organization / Tenant Info</h2>
          <dl className="detail-list compact">
            <DetailItem label="Tenant ID" value={user?.shaleClientId ? String(user.shaleClientId) : null} />
            <DetailItem label="API Base URL" value={apiBaseUrl()} />
          </dl>
        </section>

        {user?.isAdmin ? (
          <section className="settings-card">
            <h2>Administrative Settings</h2>
            <p className="lede">These sections are visible only to administrators. All data is read-only in this web workflow.</p>
            {isLoadingAdmin && <p className="status">Loading administrative settings…</p>}
            {!isLoadingAdmin && adminError && <p className="status error" role="alert">{adminError}</p>}
            {!isLoadingAdmin && !adminError && (
              <div className="settings-admin-grid">
                <SettingsTable
                  title="Case Statuses"
                  emptyText="No case statuses were found for this tenant."
                  headers={['Name', 'Open/Closed', 'Sort', 'Lifecycle', 'System Key']}
                  rows={caseStatuses.map((status) => [
                    status.name || `Status ${status.id}`,
                    status.closed ? 'Closed' : 'Open',
                    status.sortOrder == null ? '—' : String(status.sortOrder),
                    status.lifecycleKey || '—',
                    status.systemKey || '—',
                  ])}
                />
                <SettingsTable
                  title="Practice Areas"
                  emptyText="No practice areas were found for this tenant."
                  headers={['Name', 'Color', 'Status', 'System Key']}
                  rows={practiceAreas.map((area) => [
                    area.name || `Practice Area ${area.id}`,
                    area.color || '—',
                    area.deleted ? 'Deleted' : area.active ? 'Active' : 'Inactive',
                    area.systemKey || '—',
                  ])}
                />
                <div className="settings-subcard">
                  <h3>Users / Team Administration</h3>
                  <p className="status">Use the Team page for the current read-only team directory. Administrative user editing will be added in a later step.</p>
                </div>
              </div>
            )}
          </section>
        ) : (
          <section className="settings-card">
            <h2>Administrative Settings</h2>
            <p className="status">Administrative settings will be added in a later step.</p>
          </section>
        )}
      </div>
    </section>
  );
}

function SettingsTable({ title, emptyText, headers, rows }: { title: string; emptyText: string; headers: string[]; rows: string[][] }) {
  return (
    <div className="settings-subcard">
      <h3>{title}</h3>
      {rows.length === 0 ? (
        <p className="status">{emptyText}</p>
      ) : (
        <div className="table-wrap">
          <table className="results-table settings-table">
            <thead>
              <tr>{headers.map((header) => <th key={header}>{header}</th>)}</tr>
            </thead>
            <tbody>
              {rows.map((row, rowIndex) => (
                <tr key={`${title}-${rowIndex}`}>
                  {row.map((cell, cellIndex) => <td key={`${title}-${rowIndex}-${cellIndex}`}>{cell}</td>)}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
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
  const location = useLocation();

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
        <span className="beta-badge">Read-only beta</span>
        <nav className="nav-list">
          {navigationItems.map((item) => (
            <Link key={item.path} to={item.path} className={item.activePrefixes.some((prefix) => location.pathname === prefix || location.pathname.startsWith(`${prefix}/`)) ? 'nav-link active' : 'nav-link'}>
              {item.label}
            </Link>
          ))}
        </nav>
      </aside>

      <div className="content-column">
        <header className="topbar">
          <div>
            <p className="eyebrow">Signed in</p>
            <p className="user-name">{displayNameFor(user)}</p>
            <p className="user-meta">{displayValue(user.email, 'Email not provided')}</p>
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
        <PageHeader eyebrow="Dashboard" title="My Shale" titleId="my-shale-title" lede={`Welcome${user ? `, ${displayNameFor(user)}` : ''}. Here is your read-only Shale summary.`} />
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
            <td>{item.caseName || `Case ${item.caseId}`}</td><td>{item.caseStatus || '—'}</td><td>{item.responsibleAttorney || '—'}</td><td>{formatDate(item.solDate)}</td>
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
  const navigate = useNavigate();
  return (
    <div className="table-wrap">
      <table className="results-table">
        <thead><tr><th>Task</th><th>Case</th><th>Due date</th><th>Priority</th><th>Status</th></tr></thead>
        <tbody>{tasks.map((task) => (
          <tr key={task.id} className="clickable-row" tabIndex={0} onClick={() => navigate(`/tasks/${task.id}`)} onKeyDown={(event) => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); navigate(`/tasks/${task.id}`); } }}>
            <td>{task.title || `Task ${task.id}`}</td><td>{task.caseName || '—'}</td><td>{formatDate(task.dueAt)}</td><td>{task.priorityId ? `Priority ${task.priorityId}` : '—'}</td><td>{task.completedAt ? 'Completed' : 'Open'}</td>
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
      <PageHeader eyebrow="Search" title="Cases" titleId="cases-title" lede="Search and open read-only case records." />
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
              <td>{displayValue(result.caseNumber)}</td>
              <td>{displayValue(result.caseName, `Case ${result.caseId}`)}</td>
              <td>{displayValue(result.caseStatus)}</td>
              <td>{displayValue(result.responsibleAttorney)}</td>
              <td>{displayValue(result.practiceArea)}</td>
              <td>{formatDate(result.intakeDate)}</td>
              <td>{displayValue(result.client)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function ContactsPage({ accessToken }: { accessToken: string | null }) {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<ContactSearchResult[]>([]);
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
      const searchResults = await searchContacts(accessToken, trimmedQuery);
      setResults(searchResults);
    } catch (caught) {
      setResults([]);
      setError(caught instanceof Error ? caught.message : 'Contact search failed.');
    } finally {
      setIsLoading(false);
    }
  }

  return (
    <section className="contacts-page" aria-labelledby="contacts-title">
      <PageHeader eyebrow="Search" title="Contacts" titleId="contacts-title" lede="Search and open read-only contact records." />
      <form className="search-form" onSubmit={handleSearch}>
        <label htmlFor="contact-search">Search contacts</label>
        <div className="search-row">
          <input
            id="contact-search"
            type="search"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Enter a contact name, email, or phone"
          />
          <button type="submit" disabled={isLoading}>{isLoading ? 'Searching…' : 'Search'}</button>
        </div>
      </form>

      <div className="results-area" aria-live="polite">
        {isLoading && <p className="status">Loading contact results…</p>}
        {!isLoading && error && <p className="status error" role="alert">{error}</p>}
        {!isLoading && !error && hasSearched && results.length === 0 && <p className="status">No contacts matched your search.</p>}
        {!isLoading && !error && results.length > 0 && <ContactResultsTable results={results} />}
      </div>
    </section>
  );
}

function ContactResultsTable({ results }: { results: ContactSearchResult[] }) {
  const navigate = useNavigate();

  return (
    <div className="table-wrap">
      <table className="results-table">
        <thead>
          <tr>
            <th>Contact ID</th>
            <th>Display name</th>
            <th>Email</th>
            <th>Phone</th>
          </tr>
        </thead>
        <tbody>
          {results.map((result) => (
            <tr
              key={result.id}
              className="clickable-row"
              tabIndex={0}
              onClick={() => navigate(`/contacts/${result.id}`)}
              onKeyDown={(event) => {
                if (event.key === 'Enter' || event.key === ' ') {
                  event.preventDefault();
                  navigate(`/contacts/${result.id}`);
                }
              }}
            >
              <td>{result.id}</td>
              <td>{result.displayName || `Contact ${result.id}`}</td>
              <td>{result.email || '—'}</td>
              <td>{result.phone || '—'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}


function OrganizationsPage({ accessToken }: { accessToken: string | null }) {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<OrganizationSearchResult[]>([]);
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
      const searchResults = await searchOrganizations(accessToken, trimmedQuery);
      setResults(searchResults);
    } catch (caught) {
      setResults([]);
      setError(caught instanceof Error ? caught.message : 'Organization search failed.');
    } finally {
      setIsLoading(false);
    }
  }

  return (
    <section className="organizations-page" aria-labelledby="organizations-title">
      <PageHeader eyebrow="Search" title="Organizations" titleId="organizations-title" lede="Search and open read-only organization records." />
      <form className="search-form" onSubmit={handleSearch}>
        <label htmlFor="organization-search">Search organizations</label>
        <div className="search-row">
          <input
            id="organization-search"
            type="search"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Enter an organization name"
          />
          <button type="submit" disabled={isLoading}>{isLoading ? 'Searching…' : 'Search'}</button>
        </div>
      </form>

      <div className="results-area" aria-live="polite">
        {isLoading && <p className="status">Loading organization results…</p>}
        {!isLoading && error && <p className="status error" role="alert">{error}</p>}
        {!isLoading && !error && hasSearched && results.length === 0 && <p className="status">No organizations matched your search.</p>}
        {!isLoading && !error && results.length > 0 && <OrganizationResultsTable results={results} />}
      </div>
    </section>
  );
}

function OrganizationResultsTable({ results }: { results: OrganizationSearchResult[] }) {
  const navigate = useNavigate();

  return (
    <div className="table-wrap">
      <table className="results-table">
        <thead>
          <tr><th>Organization ID</th><th>Name</th><th>Type</th><th>Email</th><th>Phone</th><th>Website</th><th>Location</th></tr>
        </thead>
        <tbody>
          {results.map((result) => (
            <tr key={result.id} className="clickable-row" tabIndex={0} onClick={() => navigate(`/organizations/${result.id}`)} onKeyDown={(event) => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); navigate(`/organizations/${result.id}`); } }}>
              <td>{result.id}</td>
              <td>{result.name || `Organization ${result.id}`}</td>
              <td>{result.organizationTypeName || '—'}</td>
              <td>{result.email || '—'}</td>
              <td>{result.phone || '—'}</td>
              <td>{result.website || '—'}</td>
              <td>{[result.city, result.state].filter(Boolean).join(', ') || '—'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}


function TeamPage({ accessToken }: { accessToken: string | null }) {
  const [members, setMembers] = useState<TeamMemberSummary[]>([]);
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

    listTeamMembers(accessToken)
      .then((items) => isCurrent && setMembers(items))
      .catch((caught) => isCurrent && setError(caught instanceof Error ? caught.message : 'Team directory could not be loaded.'))
      .finally(() => isCurrent && setIsLoading(false));

    return () => { isCurrent = false; };
  }, [accessToken]);

  return (
    <section className="team-page" aria-labelledby="team-title">
      <PageHeader eyebrow="Directory" title="Team" titleId="team-title" lede="Browse your read-only team directory." />

      <div className="results-area" aria-live="polite">
        {isLoading && <p className="status">Loading team members…</p>}
        {!isLoading && error && <p className="status error" role="alert">{error}</p>}
        {!isLoading && !error && members.length === 0 && <p className="status">No team members were found.</p>}
        {!isLoading && !error && members.length > 0 && <TeamMembersTable members={members} />}
      </div>
    </section>
  );
}

function TeamMembersTable({ members }: { members: TeamMemberSummary[] }) {
  const navigate = useNavigate();
  return (
    <div className="table-wrap">
      <table className="results-table">
        <thead><tr><th>Name</th><th>Email</th><th>Initials</th><th>Color</th><th>Attorney</th><th>Admin</th></tr></thead>
        <tbody>{members.map((member) => (
          <tr key={member.id} className="clickable-row" tabIndex={0} onClick={() => navigate(`/team/${member.id}`)} onKeyDown={(event) => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); navigate(`/team/${member.id}`); } }}>
            <td>{teamMemberName(member)}</td><td>{member.email || '—'}</td><td>{member.initials || '—'}</td><td><ColorSwatch color={member.color} /></td><td>{yesNo(member.attorney)}</td><td>{yesNo(member.admin)}</td>
          </tr>
        ))}</tbody>
      </table>
    </div>
  );
}

function TeamMemberDetailPage({ accessToken }: { accessToken: string | null }) {
  const { userId } = useParams();
  const numericUserId = Number(userId);
  const [member, setMember] = useState<TeamMemberDetail | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!Number.isInteger(numericUserId) || numericUserId <= 0) {
      setMember(null);
      setError('That team member link is not valid.');
      setIsLoading(false);
      return;
    }

    if (!accessToken) {
      setMember(null);
      setError('Your Shale session is not available. Please sign in again.');
      setIsLoading(false);
      return;
    }

    let isCurrent = true;
    setIsLoading(true);
    setError(null);

    getTeamMemberDetail(accessToken, numericUserId)
      .then((detail) => isCurrent && setMember(detail))
      .catch((caught) => {
        if (isCurrent) {
          setMember(null);
          setError(caught instanceof Error ? caught.message : 'Team member detail could not be loaded.');
        }
      })
      .finally(() => isCurrent && setIsLoading(false));

    return () => { isCurrent = false; };
  }, [accessToken, numericUserId]);

  const title = member ? teamMemberName(member) : 'Team Member Detail';

  return (
    <section className="team-member-detail-page" aria-labelledby="team-member-detail-title">
      <nav className="breadcrumb" aria-label="Breadcrumb"><Link to="/team">Team</Link><span aria-hidden="true">›</span><span>{title}</span></nav>
      <div className="page-heading-row"><div><p className="eyebrow">Team Member Detail</p><h1 id="team-member-detail-title">{title}</h1></div><Link className="button-link" to="/team">Back to Team</Link></div>
      {isLoading && <p className="status">Loading team member detail…</p>}
      {!isLoading && error && <p className="status error" role="alert">{error}</p>}
      {!isLoading && !error && !member && <p className="status">No team member detail was found.</p>}
      {!isLoading && !error && member && <TeamMemberReadOnly member={member} />}
    </section>
  );
}

function TeamMemberReadOnly({ member }: { member: TeamMemberDetail }) {
  return (
    <div className="detail-sections">
      <section aria-labelledby="team-member-info-title">
        <h2 id="team-member-info-title">Team Member Information</h2>
        <dl className="detail-list">
          <DetailItem label="Name" value={teamMemberName(member)} />
          <DetailItem label="First Name" value={member.firstName} />
          <DetailItem label="Last Name" value={member.lastName} />
          <DetailItem label="Email" value={member.email} />
          <DetailItem label="Phone" value={member.phone} />
          <DetailItem label="Initials" value={member.initials} />
          <DetailItem label="Color" value={member.color} />
          <DetailItem label="Attorney" value={yesNo(member.attorney)} />
          <DetailItem label="Admin" value={yesNo(member.admin)} />
        </dl>
      </section>
    </div>
  );
}

function teamMemberName(member: TeamMemberSummary): string {
  return member.displayName || [member.firstName, member.lastName].filter(Boolean).join(' ') || member.email || `User ${member.id}`;
}

function yesNo(value: boolean): string {
  return value ? 'Yes' : 'No';
}

function ColorSwatch({ color }: { color: string | null }) {
  if (!color) return <>—</>;
  return <span className="color-swatch-value"><span className="color-swatch" style={{ backgroundColor: color }} aria-hidden="true" />{color}</span>;
}


function TaskDetailPage({ accessToken }: { accessToken: string | null }) {
  const { taskId } = useParams();
  const numericTaskId = Number(taskId);
  const [taskDetail, setTaskDetail] = useState<TaskDetail | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!Number.isInteger(numericTaskId) || numericTaskId <= 0) {
      setTaskDetail(null);
      setError('That task link is not valid.');
      setIsLoading(false);
      return;
    }

    if (!accessToken) {
      setTaskDetail(null);
      setError('Your Shale session is not available. Please sign in again.');
      setIsLoading(false);
      return;
    }

    let isCurrent = true;
    setIsLoading(true);
    setError(null);

    getTaskDetail(accessToken, numericTaskId)
      .then((detail) => isCurrent && setTaskDetail(detail))
      .catch((caught) => {
        if (isCurrent) {
          setTaskDetail(null);
          setError(caught instanceof Error ? caught.message : 'Task detail could not be loaded.');
        }
      })
      .finally(() => isCurrent && setIsLoading(false));

    return () => { isCurrent = false; };
  }, [accessToken, numericTaskId]);

  const title = taskDetail?.title || 'Task Detail';

  return (
    <section className="task-detail-page" aria-labelledby="task-detail-title">
      <nav className="breadcrumb" aria-label="Breadcrumb">
        <Link to="/my-shale">My Shale</Link>
        <span aria-hidden="true">›</span>
        <span>{title}</span>
      </nav>
      <div className="page-heading-row">
        <div>
          <p className="eyebrow">Task Detail</p>
          <h1 id="task-detail-title">{title}</h1>
        </div>
        <Link className="button-link" to="/my-shale">Back to My Shale</Link>
      </div>

      {isLoading && <p className="status">Loading task detail…</p>}
      {!isLoading && error && <p className="status error" role="alert">{error}</p>}
      {!isLoading && !error && !taskDetail && <p className="status">No task detail was found.</p>}
      {!isLoading && !error && taskDetail && <TaskDetailReadOnly detail={taskDetail} />}
    </section>
  );
}

function TaskDetailReadOnly({ detail }: { detail: TaskDetail }) {
  return (
    <div className="detail-sections">
      <section aria-labelledby="task-info-title">
        <h2 id="task-info-title">Task Information</h2>
        <dl className="detail-list">
          <DetailItem label="Task Name" value={detail.title} />
          <DetailItem label="Status" value={detail.statusId ? `Status ${detail.statusId}` : null} />
          <DetailItem label="Priority" value={detail.priorityId ? `Priority ${detail.priorityId}` : null} />
          <DetailItem label="Assigned To" value={detail.assignedUserDisplayName} />
          <DetailItem label="Created By" value={detail.createdByDisplayName} />
          <DetailItem label="Due Date" value={formatDate(detail.dueAt)} />
          <DetailItem label="Completed Date" value={formatDate(detail.completedAt)} />
          <DetailItem label="Description" value={detail.description} preserveWhitespace />
        </dl>
      </section>

      {detail.caseId > 0 && (
        <section aria-labelledby="related-case-title">
          <h2 id="related-case-title">Related Case</h2>
          <dl className="detail-list compact">
            <DetailItem label="Related Case Name" value={detail.caseName} />
          </dl>
          <Link className="button-link inline-action" to={`/cases/${detail.caseId}`}>Open Case</Link>
        </section>
      )}
    </div>
  );
}

function CaseDetailPage({ accessToken }: { accessToken: string | null }) {
  const { caseId } = useParams();
  const numericCaseId = Number(caseId);
  const [caseDetail, setCaseDetail] = useState<CaseDetail | null>(null);
  const [caseTasks, setCaseTasks] = useState<CaseTaskListItem[]>([]);
  const [tasksError, setTasksError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!Number.isInteger(numericCaseId) || numericCaseId <= 0) {
      setCaseDetail(null);
      setCaseTasks([]);
      setTasksError(null);
      setError('That case link is not valid.');
      setIsLoading(false);
      return;
    }

    if (!accessToken) {
      setCaseDetail(null);
      setCaseTasks([]);
      setTasksError(null);
      setError('Your Shale session is not available. Please sign in again.');
      setIsLoading(false);
      return;
    }

    let isCurrent = true;
    setIsLoading(true);
    setError(null);
    setTasksError(null);

    Promise.allSettled([
      getCaseDetail(accessToken, numericCaseId),
      listCaseTasks(accessToken, numericCaseId),
    ])
      .then(([caseResult, tasksResult]) => {
        if (!isCurrent) {
          return;
        }

        if (caseResult.status === 'fulfilled') {
          setCaseDetail(caseResult.value);
        } else {
          setCaseDetail(null);
          setCaseTasks([]);
          setError(caseResult.reason instanceof Error ? caseResult.reason.message : 'Case detail could not be loaded.');
          return;
        }

        if (tasksResult.status === 'fulfilled') {
          setCaseTasks(tasksResult.value);
        } else {
          setCaseTasks([]);
          setTasksError(tasksResult.reason instanceof Error ? tasksResult.reason.message : 'Case tasks could not be loaded.');
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
      {!isLoading && !error && caseDetail && <CaseDetailReadOnly detail={caseDetail} tasks={caseTasks} tasksError={tasksError} />}
    </section>
  );
}

function CaseDetailReadOnly({ detail, tasks, tasksError }: { detail: CaseDetail; tasks: CaseTaskListItem[]; tasksError: string | null }) {
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
          <DetailItem label="Intake Date" value={formatDate(detail.callerDate)} />
          <DetailItem label="Date of Injury" value={formatDate(detail.dateOfInjury)} />
          <DetailItem label="Statute of Limitations" value={formatDate(detail.statuteOfLimitations)} />
          <DetailItem label="Tort Notice Deadline" value={formatDate(detail.tortNoticeDeadline)} />
        </dl>
      </section>

      <section aria-labelledby="assignments-title">
        <h2 id="assignments-title">Assignments</h2>
        <dl className="detail-list compact">
          <DetailItem label="Responsible Attorney" value={detail.responsibleAttorney} />
        </dl>
      </section>

      <CaseTasksSection tasks={tasks} error={tasksError} />

      <RelatedContactsSection contacts={detail.relatedContacts ?? []} />
    </div>
  );
}

function CaseTasksSection({ tasks, error }: { tasks: CaseTaskListItem[]; error: string | null }) {
  const navigate = useNavigate();

  return (
    <section aria-labelledby="case-tasks-title">
      <h2 id="case-tasks-title">Case Tasks</h2>
      {error && <p className="status error" role="alert">{error}</p>}
      {!error && tasks.length === 0 && <p className="status">No tasks are linked to this case yet.</p>}
      {!error && tasks.length > 0 && (
        <div className="table-wrap">
          <table className="results-table case-tasks-table">
            <thead><tr><th>Task</th><th>Status</th><th>Priority</th><th>Due date</th><th>Assigned user</th><th>Completed date</th></tr></thead>
            <tbody>{tasks.map((task) => (
              <tr key={task.id} className="clickable-row" tabIndex={0} onClick={() => navigate(`/tasks/${task.id}`)} onKeyDown={(event) => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); navigate(`/tasks/${task.id}`); } }}>
                <td>{task.title || `Task ${task.id}`}</td>
                <td>{task.completedAt ? 'Completed' : 'Open'}</td>
                <td>{task.priorityId ? `Priority ${task.priorityId}` : '—'}</td>
                <td>{formatDate(task.dueAt)}</td>
                <td>{task.assignedUserDisplayName || '—'}</td>
                <td>{formatDate(task.completedAt)}</td>
              </tr>
            ))}</tbody>
          </table>
        </div>
      )}
    </section>
  );
}

function RelatedContactsSection({ contacts }: { contacts: CaseRelatedContact[] }) {
  return (
    <section aria-labelledby="related-contacts-title">
      <h2 id="related-contacts-title">Related Contacts</h2>
      {contacts.length === 0 ? (
        <p className="status">No related contacts are linked to this case yet.</p>
      ) : (
        <div className="related-contact-grid">
          {contacts.map((contact) => (
            <Link className="related-contact-card" key={contact.id} to={`/contacts/${contact.id}`}>
              <span className="related-contact-name">{contact.displayName || 'Unnamed contact'}</span>
              <span className="related-contact-meta">
                {formatRelatedContactMeta(contact)}
              </span>
              <span className="related-contact-detail">Email: {contact.email || '—'}</span>
              <span className="related-contact-detail">Phone: {contact.phone || '—'}</span>
            </Link>
          ))}
        </div>
      )}
    </section>
  );
}

function formatRelatedContactMeta(contact: CaseRelatedContact): string {
  const parts = [contact.roleName, contact.side, contact.primary ? 'Primary' : null].filter(Boolean);
  return parts.length > 0 ? parts.join(' • ') : 'Related contact';
}

function ContactDetailPage({ accessToken }: { accessToken: string | null }) {
  const { contactId } = useParams();
  const numericContactId = Number(contactId);
  const [contactDetail, setContactDetail] = useState<ContactDetail | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!Number.isInteger(numericContactId) || numericContactId <= 0) {
      setContactDetail(null);
      setError('That contact link is not valid.');
      setIsLoading(false);
      return;
    }

    if (!accessToken) {
      setContactDetail(null);
      setError('Your Shale session is not available. Please sign in again.');
      setIsLoading(false);
      return;
    }

    let isCurrent = true;
    setIsLoading(true);
    setError(null);

    getContactDetail(accessToken, numericContactId)
      .then((detail) => isCurrent && setContactDetail(detail))
      .catch((caught) => {
        if (isCurrent) {
          setContactDetail(null);
          setError(caught instanceof Error ? caught.message : 'Contact detail could not be loaded.');
        }
      })
      .finally(() => isCurrent && setIsLoading(false));

    return () => { isCurrent = false; };
  }, [accessToken, numericContactId]);

  const title = contactDetail?.displayName || 'Contact Detail';

  return (
    <section className="contact-detail-page" aria-labelledby="contact-detail-title">
      <nav className="breadcrumb" aria-label="Breadcrumb">
        <Link to="/contacts">Contacts</Link>
        <span aria-hidden="true">›</span>
        <span>{title}</span>
      </nav>
      <div className="page-heading-row">
        <div>
          <p className="eyebrow">Contact Detail</p>
          <h1 id="contact-detail-title">{title}</h1>
        </div>
        <Link className="button-link" to="/contacts">Back to Contacts</Link>
      </div>

      {isLoading && <p className="status">Loading contact detail…</p>}
      {!isLoading && error && <p className="status error" role="alert">{error}</p>}
      {!isLoading && !error && !contactDetail && <p className="status">No contact detail was found.</p>}
      {!isLoading && !error && contactDetail && <ContactDetailReadOnly detail={contactDetail} />}
    </section>
  );
}

function ContactDetailReadOnly({ detail }: { detail: ContactDetail }) {
  return (
    <div className="detail-sections">
      <section aria-labelledby="contact-info-title">
        <h2 id="contact-info-title">Contact Information</h2>
        <dl className="detail-list">
          <DetailItem label="Display Name" value={detail.displayName} />
          <DetailItem label="First Name" value={detail.firstName} />
          <DetailItem label="Last Name" value={detail.lastName} />
          <DetailItem label="Email" value={detail.email} />
          <DetailItem label="Phone" value={detail.phone} />
        </dl>
      </section>
    </div>
  );
}

function OrganizationDetailPage({ accessToken }: { accessToken: string | null }) {
  const { organizationId } = useParams();
  const numericOrganizationId = Number(organizationId);
  const [organizationDetail, setOrganizationDetail] = useState<OrganizationDetail | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!Number.isInteger(numericOrganizationId) || numericOrganizationId <= 0) {
      setOrganizationDetail(null);
      setError('That organization link is not valid.');
      setIsLoading(false);
      return;
    }

    if (!accessToken) {
      setOrganizationDetail(null);
      setError('Your Shale session is not available. Please sign in again.');
      setIsLoading(false);
      return;
    }

    let isCurrent = true;
    setIsLoading(true);
    setError(null);

    getOrganizationDetail(accessToken, numericOrganizationId)
      .then((detail) => isCurrent && setOrganizationDetail(detail))
      .catch((caught) => {
        if (isCurrent) {
          setOrganizationDetail(null);
          setError(caught instanceof Error ? caught.message : 'Organization detail could not be loaded.');
        }
      })
      .finally(() => isCurrent && setIsLoading(false));

    return () => { isCurrent = false; };
  }, [accessToken, numericOrganizationId]);

  const title = organizationDetail?.name || 'Organization Detail';

  return (
    <section className="organization-detail-page" aria-labelledby="organization-detail-title">
      <nav className="breadcrumb" aria-label="Breadcrumb"><Link to="/organizations">Organizations</Link><span aria-hidden="true">›</span><span>{title}</span></nav>
      <div className="page-heading-row"><div><p className="eyebrow">Organization Detail</p><h1 id="organization-detail-title">{title}</h1></div><Link className="button-link" to="/organizations">Back to Organizations</Link></div>
      {isLoading && <p className="status">Loading organization detail…</p>}
      {!isLoading && error && <p className="status error" role="alert">{error}</p>}
      {!isLoading && !error && !organizationDetail && <p className="status">No organization detail was found.</p>}
      {!isLoading && !error && organizationDetail && <OrganizationDetailReadOnly detail={organizationDetail} />}
    </section>
  );
}

function OrganizationDetailReadOnly({ detail }: { detail: OrganizationDetail }) {
  return (
    <div className="detail-sections">
      <section aria-labelledby="organization-info-title">
        <h2 id="organization-info-title">Organization Information</h2>
        <dl className="detail-list">
          <DetailItem label="Organization Name" value={detail.name} />
          <DetailItem label="Organization Type" value={detail.organizationTypeName} />
          <DetailItem label="Email" value={detail.email} />
          <DetailItem label="Phone" value={detail.phone} />
          <DetailItem label="Website" value={detail.website} />
          <DetailItem label="Fax" value={detail.fax} />
          <DetailItem label="Address" value={[detail.address1, detail.address2, detail.city, detail.state, detail.postalCode, detail.country].filter(Boolean).join(', ')} />
          <DetailItem label="Notes" value={detail.notes} preserveWhitespace />
        </dl>
      </section>

      {detail.relatedCases.length > 0 && (
        <section aria-labelledby="related-cases-title">
          <h2 id="related-cases-title">Related Cases</h2>
          <RelatedOrganizationCasesTable cases={detail.relatedCases} />
        </section>
      )}
    </div>
  );
}

function RelatedOrganizationCasesTable({ cases }: { cases: OrganizationDetail['relatedCases'] }) {
  const navigate = useNavigate();
  return (
    <div className="table-wrap"><table className="results-table"><thead><tr><th>Case name</th><th>Role</th><th>Side</th><th>Responsible attorney</th><th>Intake date</th><th>Statute of limitations</th></tr></thead><tbody>{cases.map((item) => (
      <tr key={item.id} className="clickable-row" tabIndex={0} onClick={() => navigate(`/cases/${item.id}`)} onKeyDown={(event) => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); navigate(`/cases/${item.id}`); } }}>
        <td>{item.name || `Case ${item.id}`}</td><td>{item.partyRoleName || '—'}</td><td>{item.side || '—'}</td><td>{item.responsibleAttorneyName || '—'}</td><td>{formatDate(item.intakeDate)}</td><td>{formatDate(item.statuteOfLimitationsDate)}</td>
      </tr>
    ))}</tbody></table></div>
  );
}


function DetailItem({ label, value, preserveWhitespace = false }: { label: string; value: string | number | null | undefined; preserveWhitespace?: boolean }) {
  const renderedValue = displayValue(value);
  return (
    <div>
      <dt>{label}</dt>
      <dd className={preserveWhitespace ? 'preserve-whitespace' : undefined}>{renderedValue}</dd>
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
