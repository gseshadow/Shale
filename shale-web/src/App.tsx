import { FormEvent, useEffect, useMemo, useState } from 'react';
import type { CSSProperties, KeyboardEvent, ReactNode } from 'react';
import { BrowserRouter, Link, Navigate, Outlet, Route, Routes, useLocation, useNavigate, useParams } from 'react-router-dom';
import { AuthenticatedUser, CaseDetail, CaseRelatedContact, CaseStatusHistoryItem, CaseSearchResult, CaseUpdate, CaseStatusSetting, CaseTaskListItem, ContactDetail, ContactSearchResult, OrganizationDetail, OrganizationSearchResult, PracticeAreaSetting, TaskDetail, TaskPriorityOption, TeamMemberDetail, TeamMemberSummary, addCaseUpdate, apiBaseUrl, createCase, createCaseTask, createContact, createOrganization, completeTask, clearAccessToken, getCaseDetail, getContactDetail, getCurrentUser, getOrganizationDetail, getTaskDetail, getTeamMemberDetail, listAssignedCases, listAssignedTasks, listCaseTasks, listCaseUpdates, listCaseStatusSettings, listCaseStatusLookup, listPracticeAreaLookups, listPracticeAreaSettings, listTaskPriorityLookups, listTeamMembers, login, logout, readAccessToken, searchCases, searchContacts, searchOrganizations, storeAccessToken, updateCaseAssignment, updateCaseCoreDetails, updateCaseStatus, updateContactDetails, updateOrganizationDetails, updateTaskDetail } from './api';
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

function formatDateTime(value: string | null | undefined): string {
  if (isMissing(value)) {
    return MISSING_VALUE;
  }

  const text = String(value);
  const date = new Date(text);

  if (Number.isNaN(date.getTime())) {
    return text;
  }

  return new Intl.DateTimeFormat(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  }).format(date);
}

function PageHeader({ eyebrow, title, titleId, lede, action }: { eyebrow: string; title: string; titleId?: string; lede?: string; action?: ReactNode }) {
  return (
    <header className="page-header">
      <div className="page-heading-row">
        <div className="page-title-block">
          <p className="eyebrow">{eyebrow}</p>
          <h1 id={titleId}>{title}</h1>
          {lede && <p className="lede">{lede}</p>}
        </div>
        {action && <ToolbarActions>{action}</ToolbarActions>}
      </div>
    </header>
  );
}

function ToolbarActions({ children }: { children: ReactNode }) {
  return <div className="toolbar-actions">{children}</div>;
}

function ActionButton({ children, type = 'button', disabled = false, onClick }: { children: ReactNode; type?: 'button' | 'submit'; disabled?: boolean; onClick?: () => void }) {
  return <button className="action-button" type={type} disabled={disabled} onClick={onClick}>{children}</button>;
}

function SecondaryButton({ children, type = 'button', disabled = false, onClick }: { children: ReactNode; type?: 'button' | 'submit'; disabled?: boolean; onClick?: () => void }) {
  return <button className="secondary-button" type={type} disabled={disabled} onClick={onClick}>{children}</button>;
}

function SearchBar({ id, label, value, placeholder, isLoading, loadingLabel = 'Searching…', submitLabel = 'Search', onChange, onSubmit }: { id: string; label: string; value: string; placeholder: string; isLoading?: boolean; loadingLabel?: string; submitLabel?: string; onChange: (value: string) => void; onSubmit: (event: FormEvent<HTMLFormElement>) => void }) {
  return (
    <form className="search-form filter-bar" onSubmit={onSubmit} role="search">
      <label className="search-label" htmlFor={id}>{label}</label>
      <div className="search-row">
        <input
          id={id}
          type="search"
          value={value}
          onChange={(event) => onChange(event.target.value)}
          placeholder={placeholder}
        />
        <ActionButton type="submit" disabled={isLoading}>{isLoading ? loadingLabel : submitLabel}</ActionButton>
      </div>
    </form>
  );
}

function FilterBar({ children, label = 'Filters and actions' }: { children: ReactNode; label?: string }) {
  return <div className="filter-bar" aria-label={label}>{children}</div>;
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
        <Route element={<AppShell user={authState.user} onLogout={handleLogout} />}>
          <Route path="/my-shale" element={<MyShalePage accessToken={authState.accessToken} user={authState.user} />} />
          <Route path="/cases" element={<CasesPage accessToken={authState.accessToken} />} />
          <Route path="/cases/:caseId" element={<CaseDetailPage accessToken={authState.accessToken} />} />
          <Route path="/tasks" element={<TasksPage accessToken={authState.accessToken} />} />
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

function normalizeSettingsKey(value: string | null | undefined): string | null {
  const normalized = (value ?? '').trim().toLowerCase();
  return normalized || null;
}

function practiceAreaLogicalKey(area: PracticeAreaSetting): string {
  const systemKey = normalizeSettingsKey(area.systemKey);
  if (systemKey) {
    return `system:${systemKey}`;
  }

  const nameKey = normalizeSettingsKey(area.name);
  return nameKey ? `name:${nameKey}` : `id:${area.id}`;
}

function mergeEffectivePracticeAreas(rows: PracticeAreaSetting[], shaleClientId: number): PracticeAreaSetting[] {
  const byLogicalKey = new Map<string, PracticeAreaSetting>();

  for (const area of rows) {
    if (area.shaleClientId !== null && area.shaleClientId !== shaleClientId) {
      continue;
    }

    const key = practiceAreaLogicalKey(area);
    const existing = byLogicalKey.get(key);
    const isTenantRow = area.shaleClientId === shaleClientId;
    const existingIsTenantRow = existing?.shaleClientId === shaleClientId;

    if (!existing || (isTenantRow && !existingIsTenantRow)) {
      byLogicalKey.set(key, area);
    }
  }

  return Array.from(byLogicalKey.values()).sort((left, right) => {
    const byName = (left.name ?? '').localeCompare(right.name ?? '', undefined, { sensitivity: 'base' });
    return byName || left.id - right.id;
  });
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
          setPracticeAreas(mergeEffectivePracticeAreas(practiceAreaRows, user.shaleClientId));
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
  }, [accessToken, user?.isAdmin, user?.shaleClientId]);

  return (
    <section className="settings-page">
      <PageHeader
        eyebrow="Settings"
        title="Settings"
        lede="Read-only account and tenant settings for the signed-in Shale session."
        action={<span className="inline-beta-badge">BETA</span>}
      />

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
        <EmptyState message={emptyText} />
      ) : (
        <EntityList ariaLabel={title}>
          {rows.map((row, rowIndex) => {
            const [primary, secondary, ...rest] = row;
            return (
              <EntityCard
                key={`${title}-${rowIndex}`}
                title={displayValue(primary, `${title} ${rowIndex + 1}`)}
                subtitle={secondary ? `${headers[1]}: ${secondary}` : undefined}
                metadata={(
                  <MetadataGrid>
                    {rest.map((cell, restIndex) => (
                      <MetadataRow key={`${title}-${rowIndex}-${restIndex}`} label={headers[restIndex + 2] ?? 'Value'} value={cell} />
                    ))}
                  </MetadataGrid>
                )}
              />
            );
          })}
        </EntityList>
      )}
    </div>
  );
}

function LoadingState({ message }: { message: string }) {
  return <p className="status loading-state">{message}</p>;
}

function EmptyState({ message }: { message: string }) {
  return <p className="status empty-state">{message}</p>;
}

function StatusPill({ children, tone = 'neutral' }: { children: ReactNode; tone?: 'neutral' | 'success' | 'warning' | 'info' }) {
  return <span className={`status-pill status-pill-${tone}`}>{children}</span>;
}

function MetadataRow({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="metadata-row">
      <dt>{label}</dt>
      <dd>{value}</dd>
    </div>
  );
}

function MetadataGrid({ children }: { children: ReactNode }) {
  return <dl className="metadata-grid">{children}</dl>;
}

function EntityList({ children, ariaLabel }: { children: ReactNode; ariaLabel: string }) {
  return <div className="entity-list" role="list" aria-label={ariaLabel}>{children}</div>;
}

function EntityCard({ title, subtitle, eyebrow, badges, metadata, actions, onClick, ariaLabel }: { title: ReactNode; subtitle?: ReactNode; eyebrow?: ReactNode; badges?: ReactNode; metadata?: ReactNode; actions?: ReactNode; onClick?: () => void; ariaLabel?: string }) {
  function handleKeyDown(event: KeyboardEvent<HTMLElement>) {
    if (!onClick) return;
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      onClick();
    }
  }

  return (
    <article
      className={onClick ? 'entity-card entity-card-clickable' : 'entity-card'}
      role="listitem"
      tabIndex={onClick ? 0 : undefined}
      onClick={onClick}
      onKeyDown={handleKeyDown}
      aria-label={ariaLabel}
    >
      <div className="entity-card-header">
        <div className="entity-card-title-block">
          {eyebrow && <p className="entity-card-eyebrow">{eyebrow}</p>}
          <h3 className="entity-card-title">{title}</h3>
          {subtitle && <p className="entity-card-subtitle">{subtitle}</p>}
        </div>
        {badges && <div className="entity-card-badges">{badges}</div>}
      </div>
      {metadata && <div className="entity-card-metadata">{metadata}</div>}
      {actions && <div className="entity-card-actions" onClick={(event) => event.stopPropagation()} onKeyDown={(event) => event.stopPropagation()}>{actions}</div>}
    </article>
  );
}


function BackLink({ to, children, ariaLabel }: { to: string; children: ReactNode; ariaLabel?: string }) {
  return <Link className="back-link" to={to} aria-label={ariaLabel}>{children}</Link>;
}

function DetailShell({ children, className, titleId }: { children: ReactNode; className: string; titleId: string }) {
  return <section className={`detail-shell ${className}`} aria-labelledby={titleId}>{children}</section>;
}

function DetailHeader({ eyebrow, title, titleId, backTo, backLabel, children }: { eyebrow: string; title: string; titleId: string; backTo: string; backLabel: string; children?: ReactNode }) {
  return (
    <header className="detail-header">
      <BackLink to={backTo} ariaLabel={backLabel}>{backLabel}</BackLink>
      <div className="detail-header-main">
        <div className="detail-title-block">
          <p className="eyebrow">{eyebrow}</p>
          <h1 id={titleId}>{title}</h1>
        </div>
        {children && <div className="detail-header-meta">{children}</div>}
      </div>
    </header>
  );
}

function DetailSection({ title, titleId, children, className }: { title: string; titleId: string; children: ReactNode; className?: string }) {
  return (
    <section className={className ? `detail-section ${className}` : 'detail-section'} aria-labelledby={titleId}>
      <h2 id={titleId}>{title}</h2>
      {children}
    </section>
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

function AppShell({ user, onLogout }: { user: AuthenticatedUser | null; onLogout: () => void }) {
  const location = useLocation();

  if (!user) {
    return null;
  }

  return (
    <div className="app-layout">
      <header className="topbar">
        <div className="brand" aria-label="Shale">
          <span className="brand-mark" aria-hidden="true">S</span>
          <span>Shale</span>
        </div>
        <span className="beta-badge">BETA</span>
        <div className="user-summary">
          <p className="eyebrow">Signed in</p>
          <p className="user-name">{displayNameFor(user)}</p>
          <p className="user-meta">{displayValue(user.email, 'Email not provided')}</p>
        </div>
        <button type="button" onClick={onLogout}>Logout</button>
      </header>

      <main className="page-content">
        <div className="content-container">
          <Outlet />
        </div>
      </main>

      <PrimaryNavigation locationPathname={location.pathname} />
    </div>
  );
}

function PrimaryNavigation({ locationPathname }: { locationPathname: string }) {
  return (
    <nav className="bottom-nav-list" aria-label="Primary navigation">
      {navigationItems.map((item) => {
        const isActive = item.activePrefixes.some((prefix) => locationPathname === prefix || locationPathname.startsWith(`${prefix}/`));
        return (
          <Link key={item.path} to={item.path} className={isActive ? 'nav-link active' : 'nav-link'} aria-current={isActive ? 'page' : undefined}>
            {item.label}
          </Link>
        );
      })}
    </nav>
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
      {isLoading && <LoadingState message="Loading your cases…" />}
      {!isLoading && error && <p className="status error" role="alert">{error}</p>}
      {!isLoading && !error && cases.length === 0 && <EmptyState message="No assigned cases were found." />}
      {!isLoading && !error && cases.length > 0 && <MyCasesList cases={cases} />}
    </section>
  );
}

function MyCasesList({ cases }: { cases: CaseSearchResult[] }) {
  const navigate = useNavigate();
  return (
    <EntityList ariaLabel="Assigned cases">
      {cases.map((item) => (
        <EntityCard
          key={item.caseId}
          title={displayValue(item.caseName, `Case ${item.caseId}`)}
          subtitle={displayValue(item.caseNumber, `Case ID ${item.caseId}`)}
          badges={<StatusPill tone="info">{displayValue(item.caseStatus, 'Status not set')}</StatusPill>}
          metadata={(
            <MetadataGrid>
              <MetadataRow label="Responsible attorney" value={displayValue(item.responsibleAttorney)} />
              <MetadataRow label="Statute of limitations" value={formatDate(item.solDate)} />
            </MetadataGrid>
          )}
          onClick={() => navigate(`/cases/${item.caseId}`)}
          ariaLabel={`Open case ${displayValue(item.caseName, `Case ${item.caseId}`)}`}
        />
      ))}
    </EntityList>
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
      {isLoading && <LoadingState message="Loading your tasks…" />}
      {!isLoading && error && <p className="status error" role="alert">{error}</p>}
      {!isLoading && !error && tasks.length === 0 && <EmptyState message="No assigned tasks were found." />}
      {!isLoading && !error && tasks.length > 0 && <MyTasksList tasks={tasks} accessToken={accessToken} onTasksChanged={setTasks} onError={setError} />}
    </section>
  );
}

function mergeCompletedTask(tasks: CaseTaskListItem[], completedTask: TaskDetail): CaseTaskListItem[] {
  return tasks.map((task) => task.id === completedTask.id ? { ...task, completedAt: completedTask.completedAt } : task);
}

function MyTasksList({ tasks, allTasks, accessToken, onTasksChanged, onError }: { tasks: CaseTaskListItem[]; allTasks?: CaseTaskListItem[]; accessToken: string | null; onTasksChanged: (tasks: CaseTaskListItem[]) => void; onError: (message: string | null) => void }) {
  const navigate = useNavigate();
  const [completingTaskId, setCompletingTaskId] = useState<number | null>(null);

  async function handleCompleteTask(task: CaseTaskListItem) {
    if (!accessToken) {
      onError('Your Shale session is not available. Please sign in again.');
      return;
    }
    setCompletingTaskId(task.id);
    onError(null);
    try {
      const completedTask = await completeTask(accessToken, task.id);
      onTasksChanged(mergeCompletedTask(allTasks ?? tasks, completedTask));
    } catch (caught) {
      onError(caught instanceof Error ? caught.message : 'Task could not be completed.');
    } finally {
      setCompletingTaskId(null);
    }
  }

  return (
    <EntityList ariaLabel="Assigned tasks">
      {tasks.map((task) => (
        <EntityCard
          key={task.id}
          title={displayValue(task.title, `Task ${task.id}`)}
          subtitle={displayValue(task.caseName, 'No related case')}
          badges={<StatusPill tone={task.completedAt ? 'success' : 'warning'}>{task.completedAt ? 'Completed' : 'Open'}</StatusPill>}
          metadata={(
            <MetadataGrid>
              <MetadataRow label="Due date" value={formatDate(task.dueAt)} />
              <MetadataRow label="Priority" value={task.priorityId ? `Priority ${task.priorityId}` : MISSING_VALUE} />
            </MetadataGrid>
          )}
          actions={!task.completedAt ? <SecondaryButton disabled={completingTaskId === task.id} onClick={() => handleCompleteTask(task)}>{completingTaskId === task.id ? 'Completing…' : 'Complete'}</SecondaryButton> : <span className="completed-state">Completed {formatDate(task.completedAt)}</span>}
          onClick={() => navigate(`/tasks/${task.id}`)}
          ariaLabel={`Open task ${displayValue(task.title, `Task ${task.id}`)}`}
        />
      ))}
    </EntityList>
  );
}


function TasksPage({ accessToken }: { accessToken: string | null }) {
  const [tasks, setTasks] = useState<CaseTaskListItem[]>([]);
  const [query, setQuery] = useState('');
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

  const filteredTasks = useMemo(() => {
    const trimmedQuery = query.trim().toLowerCase();
    if (!trimmedQuery) {
      return tasks;
    }

    return tasks.filter((task) => [
      task.title,
      task.caseName,
      task.assignedUserDisplayName,
      task.completedAt ? 'completed' : 'open',
    ].some((value) => value?.toLowerCase().includes(trimmedQuery)));
  }, [tasks, query]);

  function handleTaskSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
  }

  return (
    <section className="tasks-page" aria-labelledby="tasks-title">
      <PageHeader eyebrow="Tasks" title="Tasks" titleId="tasks-title" lede="Search your assigned read-only tasks." />
      <SearchBar
        id="task-search"
        label="Search tasks"
        value={query}
        onChange={setQuery}
        onSubmit={handleTaskSearch}
        placeholder="Search by task, case, assignee, or status"
        submitLabel="Filter"
      />

      <div className="results-area" aria-live="polite">
        {isLoading && <LoadingState message="Loading your tasks…" />}
        {!isLoading && error && <p className="status error" role="alert">{error}</p>}
        {!isLoading && !error && tasks.length === 0 && <EmptyState message="No assigned tasks were found." />}
        {!isLoading && !error && tasks.length > 0 && filteredTasks.length === 0 && <EmptyState message="No tasks matched your search." />}
        {!isLoading && !error && filteredTasks.length > 0 && <MyTasksList tasks={filteredTasks} allTasks={tasks} accessToken={accessToken} onTasksChanged={setTasks} onError={setError} />}
      </div>
    </section>
  );
}

function CasesPage({ accessToken }: { accessToken: string | null }) {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<CaseSearchResult[]>([]);
  const [hasSearched, setHasSearched] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isCreating, setIsCreating] = useState(false);
  const navigate = useNavigate();

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
      <PageHeader eyebrow="Search" title="Cases" titleId="cases-title" lede="Search, open, and create case records."
        action={<button type="button" onClick={() => setIsCreating((value) => !value)}>{isCreating ? 'Cancel new case' : 'New case'}</button>} />
      {isCreating && <NewCaseForm accessToken={accessToken} onCancel={() => setIsCreating(false)} onCreated={(created) => navigate(`/cases/${created.caseId}`)} />}
      <SearchBar
        id="case-search"
        label="Search cases"
        value={query}
        onChange={setQuery}
        onSubmit={handleSearch}
        placeholder="Enter a case name, number, or client"
        isLoading={isLoading}
      />

      <div className="results-area" aria-live="polite">
        {isLoading && <LoadingState message="Loading case results…" />}
        {!isLoading && error && <p className="status error" role="alert">{error}</p>}
        {!isLoading && !error && hasSearched && results.length === 0 && <EmptyState message="No cases matched your search." />}
        {!isLoading && !error && results.length > 0 && <CaseResultsList results={results} />}
      </div>
    </section>
  );
}

function NewCaseForm({ accessToken, onCancel, onCreated }: { accessToken: string | null; onCancel: () => void; onCreated: (created: CaseDetail) => void }) {
  const [practiceAreas, setPracticeAreas] = useState<PracticeAreaSetting[]>([]);
  const [attorneys, setAttorneys] = useState<TeamMemberSummary[]>([]);
  const [caseName, setCaseName] = useState('');
  const [caseNumber, setCaseNumber] = useState('');
  const [practiceAreaId, setPracticeAreaId] = useState('');
  const [responsibleAttorneyUserId, setResponsibleAttorneyUserId] = useState('');
  const [callerDate, setCallerDate] = useState('');
  const [dateOfInjury, setDateOfInjury] = useState('');
  const [statuteOfLimitations, setStatuteOfLimitations] = useState('');
  const [tortNoticeDeadline, setTortNoticeDeadline] = useState('');
  const [summary, setSummary] = useState('');
  const [description, setDescription] = useState('');
  const [isLoadingLookups, setIsLoadingLookups] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  useEffect(() => {
    if (!accessToken) return;
    setIsLoadingLookups(true);
    Promise.all([listPracticeAreaLookups(accessToken), listTeamMembers(accessToken)])
      .then(([areas, members]) => {
        const activeAreas = areas.filter((area) => area.active && !area.deleted);
        setPracticeAreas(activeAreas);
        setAttorneys(members.filter((member) => member.attorney));
      })
      .catch((caught) => setSubmitError(caught instanceof Error ? caught.message : 'Lookups could not be loaded.'))
      .finally(() => setIsLoadingLookups(false));
  }, [accessToken]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const selectedPracticeAreaId = Number(practiceAreaId);
    const selectedAttorneyId = Number(responsibleAttorneyUserId);
    if (!caseName.trim()) {
      setSubmitError('Enter a case name before saving.');
      return;
    }
    if (!Number.isInteger(selectedPracticeAreaId) || selectedPracticeAreaId <= 0) {
      setSubmitError('Choose a practice area before saving.');
      return;
    }
    if (!Number.isInteger(selectedAttorneyId) || selectedAttorneyId <= 0) {
      setSubmitError('Choose a responsible attorney before saving.');
      return;
    }
    if (!accessToken) {
      setSubmitError('Your Shale session is not available. Please sign in again.');
      return;
    }
    setIsSubmitting(true);
    setSubmitError(null);
    try {
      const caseDates = [
        callerDate ? { systemKey: 'intake' as const, startsAt: `${callerDate}T00:00:00`, endsAt: null, allDay: true } : null,
        dateOfInjury ? { systemKey: 'date_of_injury' as const, startsAt: `${dateOfInjury}T00:00:00`, endsAt: null, allDay: true } : null,
        statuteOfLimitations ? { systemKey: 'statute_of_limitations' as const, startsAt: `${statuteOfLimitations}T00:00:00`, endsAt: null, allDay: true } : null,
        tortNoticeDeadline ? { systemKey: 'tort_notice_deadline' as const, startsAt: `${tortNoticeDeadline}T00:00:00`, endsAt: null, allDay: true } : null,
      ].filter((value): value is NonNullable<typeof value> => value !== null);
      const created = await createCase(accessToken, {
        caseName: caseName.trim(),
        caseNumber: caseNumber.trim() || null,
        practiceAreaId: selectedPracticeAreaId,
        responsibleAttorneyUserId: selectedAttorneyId,
        caseDates,
        summary: summary.trim() || null,
        description: description.trim() || null,
      });
      onCreated(created);
    } catch (caught) {
      setSubmitError(caught instanceof Error ? caught.message : 'Case could not be created.');
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <section className="settings-subcard" aria-labelledby="new-case-title">
      <h2 id="new-case-title">New case</h2>
      <form className="case-edit-form" onSubmit={handleSubmit} aria-label="Create case">
        <label htmlFor="new-case-name">Case name<input id="new-case-name" value={caseName} onChange={(event) => setCaseName(event.target.value)} disabled={isSubmitting} maxLength={255} required /></label>
        <label htmlFor="new-case-number">Case number<input id="new-case-number" value={caseNumber} onChange={(event) => setCaseNumber(event.target.value)} disabled={isSubmitting} maxLength={200} /></label>
        <label htmlFor="new-case-practice-area">Practice area<select id="new-case-practice-area" value={practiceAreaId} onChange={(event) => setPracticeAreaId(event.target.value)} disabled={isSubmitting || isLoadingLookups} required><option value="">Choose a practice area</option>{practiceAreas.map((area) => <option key={area.id} value={area.id}>{displayValue(area.name, `Practice area ${area.id}`)}</option>)}</select></label>
        <label htmlFor="new-case-attorney">Responsible attorney<select id="new-case-attorney" value={responsibleAttorneyUserId} onChange={(event) => setResponsibleAttorneyUserId(event.target.value)} disabled={isSubmitting || isLoadingLookups} required><option value="">Choose an attorney</option>{attorneys.map((member) => <option key={member.id} value={member.id}>{displayValue(member.displayName, `User ${member.id}`)}</option>)}</select></label>
        <label htmlFor="new-case-caller-date">Intake date<input id="new-case-caller-date" type="date" value={callerDate} onChange={(event) => setCallerDate(event.target.value)} disabled={isSubmitting} /></label>
        <label htmlFor="new-case-injury-date">Date of injury<input id="new-case-injury-date" type="date" value={dateOfInjury} onChange={(event) => setDateOfInjury(event.target.value)} disabled={isSubmitting} /></label>
        <label htmlFor="new-case-sol">Statute of limitations<input id="new-case-sol" type="date" value={statuteOfLimitations} onChange={(event) => setStatuteOfLimitations(event.target.value)} disabled={isSubmitting} /></label>
        <label htmlFor="new-case-tort">Tort notice deadline<input id="new-case-tort" type="date" value={tortNoticeDeadline} onChange={(event) => setTortNoticeDeadline(event.target.value)} disabled={isSubmitting} /></label>
        <label htmlFor="new-case-summary">Summary<textarea id="new-case-summary" value={summary} onChange={(event) => setSummary(event.target.value)} disabled={isSubmitting} rows={4} maxLength={10000} /></label>
        <label htmlFor="new-case-description">Description<textarea id="new-case-description" value={description} onChange={(event) => setDescription(event.target.value)} disabled={isSubmitting} rows={5} maxLength={10000} /></label>
        {submitError && <p className="status error" role="alert">{submitError}</p>}
        <div className="form-actions">
          <button type="submit" disabled={isSubmitting || isLoadingLookups}>{isSubmitting ? 'Creating…' : isLoadingLookups ? 'Loading lookups…' : 'Create case'}</button>
          <button type="button" className="secondary-button" onClick={onCancel} disabled={isSubmitting}>Cancel</button>
        </div>
      </form>
    </section>
  );
}

function CaseResultsList({ results }: { results: CaseSearchResult[] }) {
  const navigate = useNavigate();

  return (
    <EntityList ariaLabel="Case search results">
      {results.map((result) => (
        <EntityCard
          key={result.caseId}
          eyebrow={`Case ID ${result.caseId}`}
          title={displayValue(result.caseName, `Case ${result.caseId}`)}
          subtitle={displayValue(result.caseNumber, 'Case number not set')}
          badges={<StatusPill tone="info">{displayValue(result.caseStatus, 'Status not set')}</StatusPill>}
          metadata={(
            <MetadataGrid>
              <MetadataRow label="Responsible attorney" value={displayValue(result.responsibleAttorney)} />
              <MetadataRow label="Practice area" value={displayValue(result.practiceArea)} />
              <MetadataRow label="Intake date" value={formatDate(result.intakeDate)} />
              <MetadataRow label="Client" value={displayValue(result.client)} />
            </MetadataGrid>
          )}
          onClick={() => navigate(`/cases/${result.caseId}`)}
          ariaLabel={`Open case ${displayValue(result.caseName, `Case ${result.caseId}`)}`}
        />
      ))}
    </EntityList>
  );
}

function ContactsPage({ accessToken }: { accessToken: string | null }) {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<ContactSearchResult[]>([]);
  const [hasSearched, setHasSearched] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isCreating, setIsCreating] = useState(false);
  const navigate = useNavigate();

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
      <PageHeader
        eyebrow="Search"
        title="Contacts"
        titleId="contacts-title"
        lede="Search, open, and create contact records."
        action={!isCreating ? <ActionButton onClick={() => setIsCreating(true)}>New contact</ActionButton> : undefined}
      />
      {isCreating && (
        <ContactCreateForm
          accessToken={accessToken}
          onCreated={(created) => navigate(`/contacts/${created.id}`)}
          onCancel={() => setIsCreating(false)}
        />
      )}
      <SearchBar
        id="contact-search"
        label="Search contacts"
        value={query}
        onChange={setQuery}
        onSubmit={handleSearch}
        placeholder="Enter a contact name, email, or phone"
        isLoading={isLoading}
      />

      <div className="results-area" aria-live="polite">
        {isLoading && <LoadingState message="Loading contact results…" />}
        {!isLoading && error && <p className="status error" role="alert">{error}</p>}
        {!isLoading && !error && hasSearched && results.length === 0 && <EmptyState message="No contacts matched your search." />}
        {!isLoading && !error && results.length > 0 && <ContactResultsList results={results} />}
      </div>
    </section>
  );
}

function ContactResultsList({ results }: { results: ContactSearchResult[] }) {
  const navigate = useNavigate();

  return (
    <EntityList ariaLabel="Contact search results">
      {results.map((result) => (
        <EntityCard
          key={result.id}
          eyebrow={`Contact ID ${result.id}`}
          title={displayValue(result.displayName, `Contact ${result.id}`)}
          subtitle={displayValue(result.email, 'Email not provided')}
          metadata={(
            <MetadataGrid>
              <MetadataRow label="Phone" value={displayValue(result.phone)} />
            </MetadataGrid>
          )}
          onClick={() => navigate(`/contacts/${result.id}`)}
          ariaLabel={`Open contact ${displayValue(result.displayName, `Contact ${result.id}`)}`}
        />
      ))}
    </EntityList>
  );
}

function OrganizationsPage({ accessToken }: { accessToken: string | null }) {
  const navigate = useNavigate();
  const [isCreating, setIsCreating] = useState(false);
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
      <PageHeader eyebrow="Search" title="Organizations" titleId="organizations-title" lede="Search, open, and create organization records."
        action={!isCreating ? <ActionButton onClick={() => setIsCreating(true)}>New organization</ActionButton> : undefined}
      />
      {isCreating && (
        <OrganizationCreateForm
          accessToken={accessToken}
          onCreated={(created) => navigate(`/organizations/${created.id}`)}
          onCancel={() => setIsCreating(false)}
        />
      )}
      <SearchBar
        id="organization-search"
        label="Search organizations"
        value={query}
        onChange={setQuery}
        onSubmit={handleSearch}
        placeholder="Enter an organization name"
        isLoading={isLoading}
      />

      <div className="results-area" aria-live="polite">
        {isLoading && <LoadingState message="Loading organization results…" />}
        {!isLoading && error && <p className="status error" role="alert">{error}</p>}
        {!isLoading && !error && hasSearched && results.length === 0 && <EmptyState message="No organizations matched your search." />}
        {!isLoading && !error && results.length > 0 && <OrganizationResultsList results={results} />}
      </div>
    </section>
  );
}

function OrganizationResultsList({ results }: { results: OrganizationSearchResult[] }) {
  const navigate = useNavigate();

  return (
    <EntityList ariaLabel="Organization search results">
      {results.map((result) => (
        <EntityCard
          key={result.id}
          eyebrow={`Organization ID ${result.id}`}
          title={displayValue(result.name, `Organization ${result.id}`)}
          subtitle={displayValue(result.organizationTypeName, 'Organization type not set')}
          metadata={(
            <MetadataGrid>
              <MetadataRow label="Email" value={displayValue(result.email)} />
              <MetadataRow label="Phone" value={displayValue(result.phone)} />
              <MetadataRow label="Website" value={displayValue(result.website)} />
              <MetadataRow label="Location" value={[result.city, result.state].filter(Boolean).join(', ') || MISSING_VALUE} />
            </MetadataGrid>
          )}
          onClick={() => navigate(`/organizations/${result.id}`)}
          ariaLabel={`Open organization ${displayValue(result.name, `Organization ${result.id}`)}`}
        />
      ))}
    </EntityList>
  );
}

function TeamPage({ accessToken }: { accessToken: string | null }) {
  const [members, setMembers] = useState<TeamMemberSummary[]>([]);
  const [query, setQuery] = useState('');
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

  const filteredMembers = useMemo(() => {
    const trimmedQuery = query.trim().toLowerCase();
    if (!trimmedQuery) {
      return members;
    }

    return members.filter((member) => [
      teamMemberName(member),
      member.email,
      member.phone,
      member.initials,
      member.attorney ? 'attorney' : null,
      member.admin ? 'admin' : null,
    ].some((value) => value?.toLowerCase().includes(trimmedQuery)));
  }, [members, query]);

  function handleTeamSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
  }

  return (
    <section className="team-page" aria-labelledby="team-title">
      <PageHeader eyebrow="Directory" title="Team" titleId="team-title" lede="Browse your read-only team directory." />
      <SearchBar
        id="team-search"
        label="Search team"
        value={query}
        onChange={setQuery}
        onSubmit={handleTeamSearch}
        placeholder="Search by name, email, initials, or role"
        submitLabel="Filter"
      />

      <div className="results-area" aria-live="polite">
        {isLoading && <LoadingState message="Loading team members…" />}
        {!isLoading && error && <p className="status error" role="alert">{error}</p>}
        {!isLoading && !error && members.length === 0 && <EmptyState message="No team members were found." />}
        {!isLoading && !error && members.length > 0 && filteredMembers.length === 0 && <EmptyState message="No team members matched your search." />}
        {!isLoading && !error && filteredMembers.length > 0 && <TeamMembersList members={filteredMembers} />}
      </div>
    </section>
  );
}

function TeamMembersList({ members }: { members: TeamMemberSummary[] }) {
  const navigate = useNavigate();
  return (
    <EntityList ariaLabel="Team members">
      {members.map((member) => (
        <EntityCard
          key={member.id}
          title={teamMemberName(member)}
          subtitle={displayValue(member.email, 'Email not provided')}
          badges={(
            <>
              {member.attorney && <StatusPill tone="info">Attorney</StatusPill>}
              {member.admin && <StatusPill tone="success">Admin</StatusPill>}
            </>
          )}
          metadata={(
            <MetadataGrid>
              <MetadataRow label="Initials" value={displayValue(member.initials)} />
              <MetadataRow label="Color" value={<ColorSwatch color={member.color} />} />
            </MetadataGrid>
          )}
          onClick={() => navigate(`/team/${member.id}`)}
          ariaLabel={`Open team member ${teamMemberName(member)}`}
        />
      ))}
    </EntityList>
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
    <DetailShell className="team-member-detail-page" titleId="team-member-detail-title">
      <DetailHeader eyebrow="Team Member Detail" title={title} titleId="team-member-detail-title" backTo="/team" backLabel="Back to Team" />
      {isLoading && <LoadingState message="Loading team member detail…" />}
      {!isLoading && error && <p className="status error" role="alert">{error}</p>}
      {!isLoading && !error && !member && <EmptyState message="No team member detail was found." />}
      {!isLoading && !error && member && <TeamMemberReadOnly member={member} />}
    </DetailShell>
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
  const [isEditing, setIsEditing] = useState(false);

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
      .then((detail) => { if (isCurrent) { setTaskDetail(detail); setIsEditing(false); } })
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
    <DetailShell className="task-detail-page" titleId="task-detail-title">
      <DetailHeader eyebrow="Task Detail" title={title} titleId="task-detail-title" backTo="/my-shale" backLabel="Back to My Shale" />

      {isLoading && <LoadingState message="Loading task detail…" />}
      {!isLoading && error && <p className="status error" role="alert">{error}</p>}
      {!isLoading && !error && !taskDetail && <EmptyState message="No task detail was found." />}
      {!isLoading && !error && taskDetail && (
        <TaskDetailReadOnly
          accessToken={accessToken}
          detail={taskDetail}
          isEditing={isEditing}
          onEdit={() => { setError(null); setIsEditing(true); }}
          onCancel={() => setIsEditing(false)}
          onTaskChanged={(updated) => { setTaskDetail(updated); setIsEditing(false); }}
          onError={setError}
        />
      )}
    </DetailShell>
  );
}

function TaskDetailReadOnly({ accessToken, detail, isEditing, onEdit, onCancel, onTaskChanged, onError }: { accessToken: string | null; detail: TaskDetail; isEditing: boolean; onEdit: () => void; onCancel: () => void; onTaskChanged: (detail: TaskDetail) => void; onError: (message: string | null) => void }) {
  return (
    <div className="detail-sections">
      <section aria-labelledby="task-info-title">
        <div className="section-heading-row">
          <h2 id="task-info-title">Task Information</h2>
          {!isEditing && <SecondaryButton onClick={onEdit}>Edit task</SecondaryButton>}
        </div>
        {isEditing ? (
          <TaskEditForm accessToken={accessToken} detail={detail} onCancel={onCancel} onTaskChanged={onTaskChanged} onError={onError} />
        ) : (
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
        )}
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

function TaskEditForm({ accessToken, detail, onCancel, onTaskChanged, onError }: { accessToken: string | null; detail: TaskDetail; onCancel: () => void; onTaskChanged: (detail: TaskDetail) => void; onError: (message: string | null) => void }) {
  const [title, setTitle] = useState(detail.title ?? '');
  const [description, setDescription] = useState(detail.description ?? '');
  const [dueDate, setDueDate] = useState(toDateInputValue(detail.dueAt));
  const [priorityId, setPriorityId] = useState(detail.priorityId ? String(detail.priorityId) : '');
  const [assignedUserId, setAssignedUserId] = useState(detail.assignedUserId ? String(detail.assignedUserId) : '');
  const [priorities, setPriorities] = useState<TaskPriorityOption[]>([]);
  const [teamMembers, setTeamMembers] = useState<TeamMemberSummary[]>([]);
  const [lookupError, setLookupError] = useState<string | null>(null);
  const [isLoadingLookups, setIsLoadingLookups] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  useEffect(() => {
    if (!accessToken) {
      setLookupError('Your Shale session is not available. Please sign in again.');
      return;
    }
    let isCurrent = true;
    setIsLoadingLookups(true);
    setLookupError(null);
    Promise.all([listTaskPriorityLookups(accessToken), listTeamMembers(accessToken)])
      .then(([priorityOptions, users]) => {
        if (!isCurrent) return;
        setPriorities(priorityOptions);
        setTeamMembers(users);
      })
      .catch((caught) => {
        if (!isCurrent) return;
        setLookupError(caught instanceof Error ? caught.message : 'Task edit options could not be loaded.');
      })
      .finally(() => {
        if (isCurrent) setIsLoadingLookups(false);
      });
    return () => { isCurrent = false; };
  }, [accessToken]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitError(null);
    onError(null);
    const safeTitle = title.trim();
    if (!safeTitle) {
      setSubmitError('Task name is required.');
      return;
    }
    if (!accessToken) {
      setSubmitError('Your Shale session is not available. Please sign in again.');
      return;
    }
    const selectedPriorityId = priorityId ? Number(priorityId) : null;
    const selectedAssignedUserId = assignedUserId ? Number(assignedUserId) : null;
    if (selectedPriorityId !== null && (!Number.isInteger(selectedPriorityId) || selectedPriorityId <= 0)) {
      setSubmitError('Choose a valid task priority before saving.');
      return;
    }
    if (selectedAssignedUserId !== null && (!Number.isInteger(selectedAssignedUserId) || selectedAssignedUserId <= 0)) {
      setSubmitError('Choose a valid assigned user before saving.');
      return;
    }
    setIsSubmitting(true);
    try {
      const updated = await updateTaskDetail(accessToken, detail.id, {
        title: safeTitle,
        description: description.trim() || undefined,
        dueDate: dueDate || undefined,
        priorityId: selectedPriorityId,
        assignedUserId: selectedAssignedUserId,
      });
      onTaskChanged(updated);
    } catch (caught) {
      setSubmitError(caught instanceof Error ? caught.message : 'Task detail could not be updated.');
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <form className="case-edit-form" onSubmit={handleSubmit}>
      {isLoadingLookups && <p className="status muted">Loading task options…</p>}
      {lookupError && <p className="status error" role="alert">{lookupError}</p>}
      {submitError && <p className="status error" role="alert">{submitError}</p>}
      <label htmlFor="task-title">Task name</label>
      <input id="task-title" type="text" value={title} onChange={(event) => { setTitle(event.target.value); setSubmitError(null); }} disabled={isSubmitting} maxLength={255} required />
      <label htmlFor="task-due-date">Due date</label>
      <input id="task-due-date" type="date" value={dueDate} onChange={(event) => { setDueDate(event.target.value); setSubmitError(null); }} disabled={isSubmitting} />
      <label htmlFor="task-priority">Priority</label>
      <select id="task-priority" value={priorityId} onChange={(event) => { setPriorityId(event.target.value); setSubmitError(null); }} disabled={isSubmitting || isLoadingLookups || priorities.length === 0}>
        <option value="">{priorities.length === 0 ? 'No priority options available' : 'Choose a priority'}</option>
        {priorities.map((priority) => <option key={priority.id} value={priority.id}>{priority.name || `Priority ${priority.id}`}</option>)}
      </select>
      <label htmlFor="task-assigned-user">Assigned user</label>
      <select id="task-assigned-user" value={assignedUserId} onChange={(event) => { setAssignedUserId(event.target.value); setSubmitError(null); }} disabled={isSubmitting || isLoadingLookups}>
        <option value="">Unassigned</option>
        {teamMembers.map((member) => <option key={member.id} value={member.id}>{teamMemberName(member)}</option>)}
      </select>
      <label htmlFor="task-description">Description</label>
      <textarea id="task-description" value={description} onChange={(event) => { setDescription(event.target.value); setSubmitError(null); }} disabled={isSubmitting} rows={5} maxLength={10000} />
      <p className="form-help">Status changes stay with the existing Complete action.</p>
      <div className="form-actions">
        <ActionButton type="submit" disabled={isSubmitting || isLoadingLookups}>{isSubmitting ? 'Saving…' : 'Save task'}</ActionButton>
        <SecondaryButton disabled={isSubmitting} onClick={onCancel}>Cancel</SecondaryButton>
      </div>
    </form>
  );
}

function CaseDetailPage({ accessToken }: { accessToken: string | null }) {
  const { caseId } = useParams();
  const numericCaseId = Number(caseId);
  const [caseDetail, setCaseDetail] = useState<CaseDetail | null>(null);
  const [caseTasks, setCaseTasks] = useState<CaseTaskListItem[]>([]);
  const [caseUpdates, setCaseUpdates] = useState<CaseUpdate[]>([]);
  const [tasksError, setTasksError] = useState<string | null>(null);
  const [updatesError, setUpdatesError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!Number.isInteger(numericCaseId) || numericCaseId <= 0) {
      setCaseDetail(null);
      setCaseTasks([]);
      setCaseUpdates([]);
      setTasksError(null);
      setUpdatesError(null);
      setError('That case link is not valid.');
      setIsLoading(false);
      return;
    }

    if (!accessToken) {
      setCaseDetail(null);
      setCaseTasks([]);
      setCaseUpdates([]);
      setTasksError(null);
      setUpdatesError(null);
      setError('Your Shale session is not available. Please sign in again.');
      setIsLoading(false);
      return;
    }

    let isCurrent = true;
    setIsLoading(true);
    setError(null);
    setTasksError(null);
    setUpdatesError(null);

    Promise.allSettled([
      getCaseDetail(accessToken, numericCaseId),
      listCaseTasks(accessToken, numericCaseId),
      listCaseUpdates(accessToken, numericCaseId),
    ])
      .then(([caseResult, tasksResult, updatesResult]) => {
        if (!isCurrent) {
          return;
        }

        if (caseResult.status === 'fulfilled') {
          setCaseDetail(caseResult.value);
        } else {
          setCaseDetail(null);
          setCaseTasks([]);
          setCaseUpdates([]);
          setError(caseResult.reason instanceof Error ? caseResult.reason.message : 'Case detail could not be loaded.');
          return;
        }

        if (tasksResult.status === 'fulfilled') {
          setCaseTasks(tasksResult.value);
        } else {
          setCaseTasks([]);
          setTasksError(tasksResult.reason instanceof Error ? tasksResult.reason.message : 'Case tasks could not be loaded.');
        }

        if (updatesResult.status === 'fulfilled') {
          setCaseUpdates(updatesResult.value);
        } else {
          setCaseUpdates([]);
          setUpdatesError(updatesResult.reason instanceof Error ? updatesResult.reason.message : 'Case updates could not be loaded.');
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
    <DetailShell className="case-detail-page" titleId="case-detail-title">
      <DetailHeader eyebrow="Case Detail" title={title} titleId="case-detail-title" backTo="/cases" backLabel="Back to Cases">
        {caseDetail?.caseStatus && <StatusPill tone="info">{caseDetail.caseStatus}</StatusPill>}
      </DetailHeader>

      {isLoading && <LoadingState message="Loading case detail…" />}
      {!isLoading && error && <p className="status error" role="alert">{error}</p>}
      {!isLoading && !error && caseDetail && (
        <CaseDetailReadOnly
          accessToken={accessToken}
          detail={caseDetail}
          tasks={caseTasks}
          tasksError={tasksError}
          updates={caseUpdates}
          updatesError={updatesError}
          onTasksChanged={setCaseTasks}
          onTasksError={setTasksError}
          onUpdatesChanged={setCaseUpdates}
          onUpdatesError={setUpdatesError}
          onDetailChanged={setCaseDetail}
        />
      )}
    </DetailShell>
  );
}

function CaseDetailReadOnly({ accessToken, detail, tasks, tasksError, updates, updatesError, onTasksChanged, onTasksError, onUpdatesChanged, onUpdatesError, onDetailChanged }: { accessToken: string | null; detail: CaseDetail; tasks: CaseTaskListItem[]; tasksError: string | null; updates: CaseUpdate[]; updatesError: string | null; onTasksChanged: (tasks: CaseTaskListItem[]) => void; onTasksError: (message: string | null) => void; onUpdatesChanged: (updates: CaseUpdate[]) => void; onUpdatesError: (message: string | null) => void; onDetailChanged: (detail: CaseDetail) => void }) {
  const [isEditingDetails, setIsEditingDetails] = useState(false);
  const [isEditingAssignment, setIsEditingAssignment] = useState(false);
  return (
    <div className="detail-sections">
      <section aria-labelledby="case-info-title">
        <div className="section-heading-row">
          <h2 id="case-info-title">Case Information</h2>
          {!isEditingDetails && <ActionButton onClick={() => setIsEditingDetails(true)}>Edit details</ActionButton>}
        </div>
        {isEditingDetails && <CaseCoreDetailsForm accessToken={accessToken} detail={detail} onSaved={(updated) => { onDetailChanged(updated); setIsEditingDetails(false); }} onCancel={() => setIsEditingDetails(false)} />}
        <dl className="detail-list">
          <DetailItem label="Case Name" value={detail.caseName} />
          <DetailItem label="Case Number" value={detail.caseNumber} />
          <DetailItem label="Status" value={detail.caseStatus} />
          <DetailItem label="Description" value={detail.description} preserveWhitespace />
          <DetailItem label="Summary" value={detail.summary} preserveWhitespace />
        </dl>
      </section>

      <section aria-labelledby="important-dates-title">
        <h2 id="important-dates-title">Important Dates</h2>
        <dl className="detail-list compact">
          <DetailItem label="Intake Date" value={formatMappedCaseDate(detail, 'intake')} />
          <DetailItem label="Date of Injury" value={formatMappedCaseDate(detail, 'date_of_injury')} />
          <DetailItem label="Statute of Limitations" value={formatMappedCaseDate(detail, 'statute_of_limitations')} />
          <DetailItem label="Tort Notice Deadline" value={formatMappedCaseDate(detail, 'tort_notice_deadline')} />
        </dl>
      </section>

      <section aria-labelledby="assignments-title">
        <div className="section-heading-row">
          <h2 id="assignments-title">Practice & Team</h2>
          {!isEditingAssignment && <ActionButton onClick={() => setIsEditingAssignment(true)}>Edit assignment</ActionButton>}
        </div>
        {isEditingAssignment && <CaseAssignmentForm accessToken={accessToken} detail={detail} onSaved={(updated) => { onDetailChanged(updated); setIsEditingAssignment(false); }} onCancel={() => setIsEditingAssignment(false)} />}
        <dl className="detail-list compact">
          <DetailItem label="Practice Area" value={detail.practiceAreaId ? `Practice Area ID ${detail.practiceAreaId}` : null} />
          <DetailItem label="Responsible Attorney" value={detail.responsibleAttorney} />
        </dl>
      </section>

      <CaseTasksSection accessToken={accessToken} caseId={detail.caseId} tasks={tasks} error={tasksError} onTasksChanged={onTasksChanged} onTasksError={onTasksError} />

      <StatusTimelineSection accessToken={accessToken} detail={detail} history={detail.statusHistory ?? []} onDetailChanged={onDetailChanged} />

      <RelatedContactsSection contacts={detail.relatedContacts ?? []} />

      <CaseUpdatesSection accessToken={accessToken} caseId={detail.caseId} updates={updates} error={updatesError} onUpdatesChanged={onUpdatesChanged} onUpdatesError={onUpdatesError} />
    </div>
  );
}


function CaseAssignmentForm({ accessToken, detail, onSaved, onCancel }: { accessToken: string | null; detail: CaseDetail; onSaved: (detail: CaseDetail) => void; onCancel: () => void }) {
  const [practiceAreas, setPracticeAreas] = useState<PracticeAreaSetting[]>([]);
  const [teamMembers, setTeamMembers] = useState<TeamMemberSummary[]>([]);
  const [practiceAreaId, setPracticeAreaId] = useState(detail.practiceAreaId ? String(detail.practiceAreaId) : '');
  const [responsibleAttorneyUserId, setResponsibleAttorneyUserId] = useState(detail.responsibleAttorneyId ? String(detail.responsibleAttorneyId) : '');
  const [lookupError, setLookupError] = useState<string | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [isLoadingLookups, setIsLoadingLookups] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    if (!accessToken) {
      setLookupError('Your Shale session is not available. Please sign in again.');
      return;
    }
    let isCurrent = true;
    setIsLoadingLookups(true);
    setLookupError(null);
    Promise.all([listPracticeAreaLookups(accessToken), listTeamMembers(accessToken)])
      .then(([areas, users]) => {
        if (!isCurrent) return;
        setPracticeAreas(areas.filter((area) => area.active && !area.deleted));
        setTeamMembers(users.filter((user) => user.attorney));
      })
      .catch((caught) => {
        if (!isCurrent) return;
        setLookupError(caught instanceof Error ? caught.message : 'Assignment lookup data could not be loaded.');
      })
      .finally(() => {
        if (isCurrent) setIsLoadingLookups(false);
      });
    return () => { isCurrent = false; };
  }, [accessToken]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!accessToken) {
      setSubmitError('Your Shale session is not available. Please sign in again.');
      return;
    }
    const selectedPracticeAreaId = Number(practiceAreaId);
    const selectedAttorneyId = Number(responsibleAttorneyUserId);
    if (!Number.isInteger(selectedPracticeAreaId) || selectedPracticeAreaId <= 0) {
      setSubmitError('Choose a practice area before saving.');
      return;
    }
    if (!Number.isInteger(selectedAttorneyId) || selectedAttorneyId <= 0) {
      setSubmitError('Choose a responsible attorney before saving.');
      return;
    }

    setIsSubmitting(true);
    setSubmitError(null);
    try {
      const updated = await updateCaseAssignment(accessToken, detail.caseId, {
        practiceAreaId: selectedPracticeAreaId,
        responsibleAttorneyUserId: selectedAttorneyId,
      });
      onSaved(updated);
    } catch (caught) {
      setSubmitError(caught instanceof Error ? caught.message : 'Case assignment could not be saved.');
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <form className="case-edit-form" onSubmit={handleSubmit}>
      {isLoadingLookups && <p className="status muted">Loading assignment options…</p>}
      {lookupError && <p className="status error" role="alert">{lookupError}</p>}
      <label htmlFor="case-assignment-practice-area">Practice area</label>
      <select id="case-assignment-practice-area" value={practiceAreaId} onChange={(event) => setPracticeAreaId(event.target.value)} disabled={isSubmitting || isLoadingLookups} required>
        <option value="">Choose a practice area</option>
        {practiceAreas.map((area) => <option key={area.id} value={area.id}>{area.name || `Practice Area ${area.id}`}</option>)}
      </select>
      <label htmlFor="case-assignment-attorney">Responsible attorney</label>
      <select id="case-assignment-attorney" value={responsibleAttorneyUserId} onChange={(event) => setResponsibleAttorneyUserId(event.target.value)} disabled={isSubmitting || isLoadingLookups} required>
        <option value="">Choose a responsible attorney</option>
        {teamMembers.map((member) => <option key={member.id} value={member.id}>{member.displayName || [member.firstName, member.lastName].filter(Boolean).join(' ') || `User ${member.id}`}</option>)}
      </select>
      {submitError && <p className="status error" role="alert">{submitError}</p>}
      <div className="form-actions">
        <ActionButton type="submit" disabled={isSubmitting || isLoadingLookups || !practiceAreaId || !responsibleAttorneyUserId}>{isSubmitting ? 'Saving…' : 'Save assignment'}</ActionButton>
        <SecondaryButton disabled={isSubmitting} onClick={onCancel}>Cancel</SecondaryButton>
      </div>
    </form>
  );
}

function CaseCoreDetailsForm({ accessToken, detail, onSaved, onCancel }: { accessToken: string | null; detail: CaseDetail; onSaved: (detail: CaseDetail) => void; onCancel: () => void }) {
  const [caseName, setCaseName] = useState(detail.caseName || '');
  const [description, setDescription] = useState(detail.description || '');
  const [dateOfInjury, setDateOfInjury] = useState(mappedDateInput(detail, 'date_of_injury'));
  const [statuteOfLimitations, setStatuteOfLimitations] = useState(mappedDateInput(detail, 'statute_of_limitations'));
  const [tortNoticeDeadline, setTortNoticeDeadline] = useState(mappedDateInput(detail, 'tort_notice_deadline'));
  const [summary, setSummary] = useState(detail.summary || '');
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const trimmedCaseName = caseName.trim();

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!trimmedCaseName) {
      setSubmitError('Enter a case name before saving.');
      return;
    }
    if (!accessToken) {
      setSubmitError('Your Shale session is not available. Please sign in again.');
      return;
    }
    if (!detail.rowVer) {
      setSubmitError('Case version information is missing. Refresh the case and try again.');
      return;
    }

    setIsSubmitting(true);
    setSubmitError(null);
    try {
      const updated = await updateCaseCoreDetails(accessToken, detail.caseId, {
        caseName: trimmedCaseName,
        description,
        summary,
        expectedRowVer: detail.rowVer,
        mappedCaseDates: detail.mappedCaseDates.map((date) => {
          const edited = date.systemKey === 'date_of_injury' ? dateOfInjury
            : date.systemKey === 'statute_of_limitations' ? statuteOfLimitations
            : date.systemKey === 'tort_notice_deadline' ? tortNoticeDeadline : undefined;
          return edited === undefined ? date : { ...date, startsAt: edited ? `${edited}T00:00:00` : null, endsAt: null, allDay: true };
        }),
      });
      onSaved(updated);
    } catch (caught) {
      setSubmitError(caught instanceof Error ? caught.message : 'Case details could not be saved.');
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <form className="case-edit-form" onSubmit={handleSubmit}>
      <label htmlFor="case-core-name">Case name</label>
      <input id="case-core-name" type="text" value={caseName} onChange={(event) => setCaseName(event.target.value)} disabled={isSubmitting} maxLength={255} required />
      <label htmlFor="case-core-description">Description</label>
      <textarea id="case-core-description" value={description} onChange={(event) => setDescription(event.target.value)} disabled={isSubmitting} rows={5} maxLength={10000} />
      <label htmlFor="case-core-injury-date">Date of injury</label>
      <input id="case-core-injury-date" type="date" value={dateOfInjury} onChange={(event) => setDateOfInjury(event.target.value)} disabled={isSubmitting} />
      <label htmlFor="case-core-sol-date">Statute of limitations</label>
      <input id="case-core-sol-date" type="date" value={statuteOfLimitations} onChange={(event) => setStatuteOfLimitations(event.target.value)} disabled={isSubmitting} />
      <label htmlFor="case-core-tort-notice-date">Tort notice deadline</label>
      <input id="case-core-tort-notice-date" type="date" value={tortNoticeDeadline} onChange={(event) => setTortNoticeDeadline(event.target.value)} disabled={isSubmitting} />
      <label htmlFor="case-core-summary">Summary</label>
      <textarea id="case-core-summary" value={summary} onChange={(event) => setSummary(event.target.value)} disabled={isSubmitting} rows={5} maxLength={10000} />
      {submitError && <p className="status error" role="alert">{submitError}</p>}
      <div className="form-actions">
        <ActionButton type="submit" disabled={isSubmitting || !trimmedCaseName}>{isSubmitting ? 'Saving…' : 'Save details'}</ActionButton>
        <SecondaryButton disabled={isSubmitting} onClick={onCancel}>Cancel</SecondaryButton>
      </div>
    </form>
  );
}

function toDateInputValue(value: string | null | undefined): string {
  if (!value) {
    return '';
  }
  const match = /^(\d{4}-\d{2}-\d{2})/.exec(value);
  return match ? match[1] : '';
}

function mappedCaseDate(detail: CaseDetail, systemKey: string) {
  return detail.mappedCaseDates?.find((date) => date.systemKey === systemKey);
}

function mappedDateInput(detail: CaseDetail, systemKey: string): string {
  const date = mappedCaseDate(detail, systemKey);
  return date && !date.absent && date.startsAt ? date.startsAt.slice(0, 10) : '';
}

function formatMappedCaseDate(detail: CaseDetail, systemKey: string): string | null {
  const date = mappedCaseDate(detail, systemKey);
  if (!date || date.absent || !date.startsAt) return null;
  if (date.allDay) return formatDate(date.startsAt.slice(0, 10));
  const parsed = new Date(date.startsAt);
  return Number.isNaN(parsed.getTime()) ? date.startsAt : parsed.toLocaleString();
}

function StatusTimelineSection({ accessToken, detail, history, onDetailChanged }: { accessToken: string | null; detail: CaseDetail; history: CaseStatusHistoryItem[]; onDetailChanged: (detail: CaseDetail) => void }) {
  const [isEditingStatus, setIsEditingStatus] = useState(false);
  const sortedHistory = [...history].sort((left, right) => {
    const leftDate = left.effectiveDate ? Date.parse(left.effectiveDate) : Number.MAX_SAFE_INTEGER;
    const rightDate = right.effectiveDate ? Date.parse(right.effectiveDate) : Number.MAX_SAFE_INTEGER;
    return leftDate - rightDate || left.caseStatusId - right.caseStatusId;
  });

  return (
    <section aria-labelledby="status-timeline-title">
      <div className="section-heading-row">
        <h2 id="status-timeline-title">Status Timeline</h2>
        {!isEditingStatus && <ActionButton onClick={() => setIsEditingStatus(true)}>Edit status</ActionButton>}
      </div>
      {isEditingStatus && <CaseStatusEditForm accessToken={accessToken} detail={detail} onSaved={(updated) => { onDetailChanged(updated); setIsEditingStatus(false); }} onCancel={() => setIsEditingStatus(false)} />}
      {sortedHistory.length === 0 ? (
        <EmptyState message="No status history has been recorded for this case yet." />
      ) : (
        <div className="status-timeline-list">
          {sortedHistory.map((item) => {
            const accentColor = normalizeStatusColor(item.color);
            return (
              <article className="status-timeline-card" key={item.caseStatusId} style={{ '--status-accent': accentColor } as CSSProperties}>
                <div className="status-timeline-marker" aria-hidden="true" />
                <div className="status-timeline-body">
                  <div className="status-timeline-heading">
                    <span className="status-timeline-name">{item.statusName || `Status ${item.statusId}`}</span>
                    {(item.current || !item.endDate) && <span className="status-current-pill">Current / Open</span>}
                  </div>
                  <div className="status-timeline-dates">
                    <span>Effective {formatDateTime(item.effectiveDate)}</span>
                    <span>Ends {item.endDate ? formatDateTime(item.endDate) : 'Current / open'}</span>
                  </div>
                  {item.notes && <p className="status-timeline-notes preserve-whitespace">{item.notes}</p>}
                </div>
              </article>
            );
          })}
        </div>
      )}
    </section>
  );
}

function CaseStatusEditForm({ accessToken, detail, onSaved, onCancel }: { accessToken: string | null; detail: CaseDetail; onSaved: (detail: CaseDetail) => void; onCancel: () => void }) {
  const currentStatusId = detail.statusHistory?.find((item) => item.current || !item.endDate)?.statusId ?? null;
  const [statuses, setStatuses] = useState<CaseStatusSetting[]>([]);
  const [selectedStatusId, setSelectedStatusId] = useState(currentStatusId == null ? '' : String(currentStatusId));
  const [lookupError, setLookupError] = useState<string | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [isLoadingLookup, setIsLoadingLookup] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    if (!accessToken) {
      setLookupError('Your Shale session is not available. Please sign in again.');
      return;
    }
    let isCurrent = true;
    setIsLoadingLookup(true);
    setLookupError(null);
    listCaseStatusLookup(accessToken)
      .then((rows) => { if (isCurrent) setStatuses(rows); })
      .catch((caught) => { if (isCurrent) setLookupError(caught instanceof Error ? caught.message : 'Case status options could not be loaded.'); })
      .finally(() => { if (isCurrent) setIsLoadingLookup(false); });
    return () => { isCurrent = false; };
  }, [accessToken]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const numericStatusId = Number(selectedStatusId);
    if (!Number.isInteger(numericStatusId) || numericStatusId <= 0) {
      setSubmitError('Choose a status before saving.');
      return;
    }
    if (!accessToken) {
      setSubmitError('Your Shale session is not available. Please sign in again.');
      return;
    }
    setIsSubmitting(true);
    setSubmitError(null);
    try {
      const updated = await updateCaseStatus(accessToken, detail.caseId, numericStatusId);
      onSaved(updated);
    } catch (caught) {
      setSubmitError(caught instanceof Error ? caught.message : 'Case status could not be saved.');
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <form className="case-edit-form status-edit-form" onSubmit={handleSubmit}>
      <fieldset disabled={isSubmitting || isLoadingLookup}>
        <legend>Choose the current case status</legend>
        {isLoadingLookup && <p className="status">Loading status options…</p>}
        {lookupError && <p className="status error" role="alert">{lookupError}</p>}
        {!isLoadingLookup && !lookupError && statuses.length === 0 && <EmptyState message="No case statuses are available for this tenant." />}
        <div className="status-choice-list">
          {statuses.map((status) => {
            const value = String(status.id);
            const color = normalizeStatusColor(status.color);
            return (
              <label className="status-choice-card" key={status.id} style={{ '--status-accent': color } as CSSProperties}>
                <input type="radio" name="case-status" value={value} checked={selectedStatusId === value} onChange={(event) => setSelectedStatusId(event.target.value)} />
                <span className="status-choice-body">
                  <span className="status-choice-name">{status.name || `Status ${status.id}`}</span>
                  {status.closed && <span className="status-current-pill">Closed lifecycle</span>}
                </span>
              </label>
            );
          })}
        </div>
      </fieldset>
      {submitError && <p className="status error" role="alert">{submitError}</p>}
      <div className="form-actions">
        <ActionButton type="submit" disabled={isSubmitting || isLoadingLookup || !!lookupError || !selectedStatusId}>{isSubmitting ? 'Saving…' : 'Save status'}</ActionButton>
        <SecondaryButton disabled={isSubmitting} onClick={onCancel}>Cancel</SecondaryButton>
      </div>
    </form>
  );
}

function normalizeStatusColor(color: string | null | undefined): string {
  const trimmed = color?.trim();
  return trimmed && /^#[0-9a-fA-F]{3}([0-9a-fA-F]{3})?$/.test(trimmed) ? trimmed : '#2f80b7';
}

function CaseTasksSection({ accessToken, caseId, tasks, error, onTasksChanged, onTasksError }: { accessToken: string | null; caseId: number; tasks: CaseTaskListItem[]; error: string | null; onTasksChanged: (tasks: CaseTaskListItem[]) => void; onTasksError: (message: string | null) => void }) {
  const navigate = useNavigate();
  const [isAdding, setIsAdding] = useState(false);
  const [title, setTitle] = useState('');
  const [dueDate, setDueDate] = useState('');
  const [description, setDescription] = useState('');
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [completingTaskId, setCompletingTaskId] = useState<number | null>(null);
  const trimmedTitle = title.trim();
  const trimmedDescription = description.trim();

  function resetForm() {
    setTitle('');
    setDueDate('');
    setDescription('');
    setSubmitError(null);
    setIsAdding(false);
  }

  async function handleCompleteTask(task: CaseTaskListItem) {
    if (!accessToken) {
      setSubmitError('Your Shale session is not available. Please sign in again.');
      return;
    }
    setCompletingTaskId(task.id);
    setSubmitError(null);
    try {
      const completedTask = await completeTask(accessToken, task.id);
      onTasksChanged(mergeCompletedTask(tasks, completedTask));
      onTasksError(null);
    } catch (caught) {
      setSubmitError(caught instanceof Error ? caught.message : 'Task could not be completed.');
    } finally {
      setCompletingTaskId(null);
    }
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!trimmedTitle) {
      setSubmitError('Enter a task title before saving.');
      return;
    }
    if (!accessToken) {
      setSubmitError('Your Shale session is not available. Please sign in again.');
      return;
    }

    setIsSubmitting(true);
    setSubmitError(null);
    try {
      const refreshedTasks = await createCaseTask(accessToken, caseId, {
        title: trimmedTitle,
        description: trimmedDescription || undefined,
        dueDate: dueDate || undefined,
      });
      onTasksChanged(refreshedTasks);
      onTasksError(null);
      resetForm();
    } catch (caught) {
      setSubmitError(caught instanceof Error ? caught.message : 'Case task could not be saved.');
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <section aria-labelledby="case-tasks-title">
      <div className="section-heading-row">
        <h2 id="case-tasks-title">Case Tasks</h2>
        {!isAdding && <ActionButton onClick={() => setIsAdding(true)}>Add task</ActionButton>}
      </div>

      {isAdding && (
        <form className="case-edit-form" onSubmit={handleSubmit}>
          <label htmlFor="case-task-title">Task title</label>
          <input
            id="case-task-title"
            type="text"
            value={title}
            onChange={(event) => setTitle(event.target.value)}
            placeholder="Add a task title…"
            disabled={isSubmitting}
            maxLength={255}
            required
          />
          <label htmlFor="case-task-due-date">Due date</label>
          <input
            id="case-task-due-date"
            type="date"
            value={dueDate}
            onChange={(event) => setDueDate(event.target.value)}
            disabled={isSubmitting}
          />
          <label htmlFor="case-task-description">Notes</label>
          <textarea
            id="case-task-description"
            value={description}
            onChange={(event) => setDescription(event.target.value)}
            placeholder="Add optional task notes…"
            rows={4}
            disabled={isSubmitting}
          />
          {submitError && <p className="status error" role="alert">{submitError}</p>}
          <div className="form-actions">
            <ActionButton type="submit" disabled={isSubmitting || !trimmedTitle}>{isSubmitting ? 'Saving…' : 'Save task'}</ActionButton>
            <SecondaryButton disabled={isSubmitting} onClick={resetForm}>Cancel</SecondaryButton>
          </div>
        </form>
      )}
      {error && <p className="status error" role="alert">{error}</p>}
      {!error && tasks.length === 0 && <EmptyState message="No tasks are linked to this case yet." />}
      {!error && tasks.length > 0 && (
        <EntityList ariaLabel="Case tasks">
          {tasks.map((task) => (
            <EntityCard
              key={task.id}
              title={displayValue(task.title, `Task ${task.id}`)}
              subtitle={displayValue(task.assignedUserDisplayName, 'Unassigned')}
              badges={<StatusPill tone={task.completedAt ? 'success' : 'warning'}>{task.completedAt ? 'Completed' : 'Open'}</StatusPill>}
              metadata={(
                <MetadataGrid>
                  <MetadataRow label="Priority" value={task.priorityId ? `Priority ${task.priorityId}` : MISSING_VALUE} />
                  <MetadataRow label="Due date" value={formatDate(task.dueAt)} />
                  <MetadataRow label="Completed date" value={formatDate(task.completedAt)} />
                </MetadataGrid>
              )}
              actions={!task.completedAt ? <SecondaryButton disabled={completingTaskId === task.id} onClick={() => handleCompleteTask(task)}>{completingTaskId === task.id ? 'Completing…' : 'Complete'}</SecondaryButton> : <span className="completed-state">Completed {formatDate(task.completedAt)}</span>}
              onClick={() => navigate(`/tasks/${task.id}`)}
              ariaLabel={`Open task ${displayValue(task.title, `Task ${task.id}`)}`}
            />
          ))}
        </EntityList>
      )}
    </section>
  );
}

function CaseUpdatesSection({ accessToken, caseId, updates, error, onUpdatesChanged, onUpdatesError }: { accessToken: string | null; caseId: number; updates: CaseUpdate[]; error: string | null; onUpdatesChanged: (updates: CaseUpdate[]) => void; onUpdatesError: (message: string | null) => void }) {
  const [isAdding, setIsAdding] = useState(false);
  const [noteText, setNoteText] = useState('');
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const trimmedNote = noteText.trim();

  function resetForm() {
    setNoteText('');
    setSubmitError(null);
    setIsAdding(false);
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!trimmedNote) {
      setSubmitError('Enter an update before saving.');
      return;
    }
    if (!accessToken) {
      setSubmitError('Your Shale session is not available. Please sign in again.');
      return;
    }

    setIsSubmitting(true);
    setSubmitError(null);
    try {
      const refreshedUpdates = await addCaseUpdate(accessToken, caseId, trimmedNote);
      onUpdatesChanged(refreshedUpdates);
      onUpdatesError(null);
      resetForm();
    } catch (caught) {
      setSubmitError(caught instanceof Error ? caught.message : 'Case update could not be saved.');
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <section aria-labelledby="case-updates-title">
      <div className="section-heading-row">
        <h2 id="case-updates-title">Case Updates</h2>
        {!isAdding && <ActionButton onClick={() => setIsAdding(true)}>Add update</ActionButton>}
      </div>

      {isAdding && (
        <form className="case-edit-form" onSubmit={handleSubmit}>
          <label htmlFor="case-update-note">Update note</label>
          <textarea
            id="case-update-note"
            value={noteText}
            onChange={(event) => setNoteText(event.target.value)}
            placeholder="Add a case update…"
            rows={5}
            disabled={isSubmitting}
          />
          {submitError && <p className="status error" role="alert">{submitError}</p>}
          <div className="form-actions">
            <ActionButton type="submit" disabled={isSubmitting || !trimmedNote}>{isSubmitting ? 'Saving…' : 'Save update'}</ActionButton>
            <SecondaryButton disabled={isSubmitting} onClick={resetForm}>Cancel</SecondaryButton>
          </div>
        </form>
      )}

      {error && <p className="status error" role="alert">{error}</p>}
      {!error && updates.length === 0 && <EmptyState message="No case updates have been added yet." />}
      {!error && updates.length > 0 && (
        <div className="case-updates-timeline">
          {updates.map((update) => (
            <article className="case-update-card" key={update.id}>
              <div className="case-update-meta">
                <time dateTime={update.createdAt ?? undefined}>{formatDateTime(update.createdAt)}</time>
                <span>{update.createdByDisplayName || 'Unknown author'}</span>
              </div>
              <p className="case-update-note preserve-whitespace">{update.noteText}</p>
            </article>
          ))}
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
        <EmptyState message="No related contacts are linked to this case yet." />
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
  const [isEditing, setIsEditing] = useState(false);

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
    <DetailShell className="contact-detail-page" titleId="contact-detail-title">
      <DetailHeader eyebrow="Contact Detail" title={title} titleId="contact-detail-title" backTo="/contacts" backLabel="Back to Contacts" />

      {isLoading && <LoadingState message="Loading contact detail…" />}
      {!isLoading && error && <p className="status error" role="alert">{error}</p>}
      {!isLoading && !error && !contactDetail && <EmptyState message="No contact detail was found." />}
      {!isLoading && !error && contactDetail && <ContactDetailReadOnly accessToken={accessToken} detail={contactDetail} onDetailChanged={setContactDetail} />}
    </DetailShell>
  );
}

function ContactDetailReadOnly({ accessToken, detail, onDetailChanged }: { accessToken: string | null; detail: ContactDetail; onDetailChanged: (detail: ContactDetail) => void }) {
  const [isEditingDetails, setIsEditingDetails] = useState(false);
  const [isEditingAssignment, setIsEditingAssignment] = useState(false);
  return (
    <div className="detail-sections">
      <section aria-labelledby="contact-info-title">
        <div className="section-heading-row">
          <h2 id="contact-info-title">Contact Information</h2>
          {!isEditingDetails && <ActionButton onClick={() => setIsEditingDetails(true)}>Edit contact</ActionButton>}
        </div>
        {isEditingDetails && <ContactDetailsForm accessToken={accessToken} detail={detail} onSaved={(updated) => { onDetailChanged(updated); setIsEditingDetails(false); }} onCancel={() => setIsEditingDetails(false)} />}
        <dl className="detail-list">
          <DetailItem label="Display Name" value={detail.displayName} />
          <DetailItem label="First Name" value={detail.firstName} />
          <DetailItem label="Last Name" value={detail.lastName} />
          <DetailItem label="Email" value={detail.email} />
          <DetailItem label="Phone" value={detail.phone} />
          <DetailItem label="Home Address" value={detail.addressHome} preserveWhitespace />
          <DetailItem label="Date of Birth" value={formatDate(detail.dateOfBirth)} />
          <DetailItem label="Notes" value={detail.condition} preserveWhitespace />
          <DetailItem label="Deceased" value={detail.deceased ? 'Yes' : 'No'} />
          <DetailItem label="Client" value={detail.client ? 'Yes' : 'No'} />
        </dl>
      </section>
    </div>
  );
}


function ContactCreateForm({ accessToken, onCreated, onCancel }: { accessToken: string | null; onCreated: (detail: ContactDetail) => void; onCancel: () => void }) {
  const [name, setName] = useState('');
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [addressHome, setAddressHome] = useState('');
  const [dateOfBirth, setDateOfBirth] = useState('');
  const [condition, setCondition] = useState('');
  const [deceased, setDeceased] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const hasRequiredName = Boolean(name.trim() || firstName.trim() || lastName.trim());

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!hasRequiredName) {
      setSubmitError('Enter a display name, first name, or last name before saving.');
      return;
    }
    if (email.trim() && !email.includes('@')) {
      setSubmitError('Enter a valid email address.');
      return;
    }
    if (!accessToken) {
      setSubmitError('Your Shale session is not available. Please sign in again.');
      return;
    }

    setIsSubmitting(true);
    setSubmitError(null);
    try {
      const created = await createContact(accessToken, {
        name: name.trim() || null,
        firstName: firstName.trim() || null,
        lastName: lastName.trim() || null,
        email: email.trim() || null,
        phone: phone.trim() || null,
        addressHome: addressHome.trim() || null,
        dateOfBirth: dateOfBirth || null,
        condition: condition.trim() || null,
        deceased,
      });
      onCreated(created);
    } catch (caught) {
      setSubmitError(caught instanceof Error ? caught.message : 'Contact could not be created.');
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <form className="case-edit-form" onSubmit={handleSubmit} aria-label="Create contact">
      <label htmlFor="new-contact-display-name">Display name</label>
      <input id="new-contact-display-name" type="text" value={name} onChange={(event) => setName(event.target.value)} disabled={isSubmitting} autoComplete="name" maxLength={255} />
      <label htmlFor="new-contact-first-name">First name</label>
      <input id="new-contact-first-name" type="text" value={firstName} onChange={(event) => setFirstName(event.target.value)} disabled={isSubmitting} autoComplete="given-name" maxLength={255} />
      <label htmlFor="new-contact-last-name">Last name</label>
      <input id="new-contact-last-name" type="text" value={lastName} onChange={(event) => setLastName(event.target.value)} disabled={isSubmitting} autoComplete="family-name" maxLength={255} />
      <label htmlFor="new-contact-email">Email</label>
      <input id="new-contact-email" type="email" value={email} onChange={(event) => setEmail(event.target.value)} disabled={isSubmitting} autoComplete="email" maxLength={254} />
      <label htmlFor="new-contact-phone">Phone</label>
      <input id="new-contact-phone" type="tel" value={phone} onChange={(event) => setPhone(event.target.value)} disabled={isSubmitting} autoComplete="tel" maxLength={100} />
      <label htmlFor="new-contact-address-home">Home address</label>
      <textarea id="new-contact-address-home" value={addressHome} onChange={(event) => setAddressHome(event.target.value)} disabled={isSubmitting} autoComplete="street-address" rows={3} maxLength={2000} />
      <label htmlFor="new-contact-date-of-birth">Date of birth</label>
      <input id="new-contact-date-of-birth" type="date" value={dateOfBirth} onChange={(event) => setDateOfBirth(event.target.value)} disabled={isSubmitting} />
      <label htmlFor="new-contact-notes">Notes</label>
      <textarea id="new-contact-notes" value={condition} onChange={(event) => setCondition(event.target.value)} disabled={isSubmitting} rows={5} maxLength={10000} />
      <label className="checkbox-row" htmlFor="new-contact-deceased"><input id="new-contact-deceased" type="checkbox" checked={deceased} onChange={(event) => setDeceased(event.target.checked)} disabled={isSubmitting} /> Deceased</label>
      {submitError && <p className="status error" role="alert">{submitError}</p>}
      <div className="form-actions">
        <ActionButton type="submit" disabled={isSubmitting || !hasRequiredName}>{isSubmitting ? 'Saving…' : 'Create contact'}</ActionButton>
        <SecondaryButton disabled={isSubmitting} onClick={onCancel}>Cancel</SecondaryButton>
      </div>
    </form>
  );
}

function ContactDetailsForm({ accessToken, detail, onSaved, onCancel }: { accessToken: string | null; detail: ContactDetail; onSaved: (detail: ContactDetail) => void; onCancel: () => void }) {
  const [name, setName] = useState(detail.name || detail.displayName || '');
  const [firstName, setFirstName] = useState(detail.firstName || '');
  const [lastName, setLastName] = useState(detail.lastName || '');
  const [email, setEmail] = useState(detail.email || '');
  const [phone, setPhone] = useState(detail.phone || '');
  const [addressHome, setAddressHome] = useState(detail.addressHome || '');
  const [dateOfBirth, setDateOfBirth] = useState(toDateInputValue(detail.dateOfBirth));
  const [condition, setCondition] = useState(detail.condition || '');
  const [deceased, setDeceased] = useState(detail.deceased);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const hasRequiredName = Boolean(name.trim() || firstName.trim() || lastName.trim());

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!hasRequiredName) {
      setSubmitError('Enter a display name, first name, or last name before saving.');
      return;
    }
    if (email.trim() && !email.includes('@')) {
      setSubmitError('Enter a valid email address.');
      return;
    }
    if (!accessToken) {
      setSubmitError('Your Shale session is not available. Please sign in again.');
      return;
    }

    setIsSubmitting(true);
    setSubmitError(null);
    try {
      const updated = await updateContactDetails(accessToken, detail.id, {
        name: name.trim() || null,
        firstName: firstName.trim() || null,
        lastName: lastName.trim() || null,
        email: email.trim() || null,
        phone: phone.trim() || null,
        addressHome: addressHome.trim() || null,
        dateOfBirth: dateOfBirth || null,
        condition: condition.trim() || null,
        deceased,
      });
      onSaved(updated);
    } catch (caught) {
      setSubmitError(caught instanceof Error ? caught.message : 'Contact details could not be saved.');
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <form className="case-edit-form" onSubmit={handleSubmit}>
      <label htmlFor="contact-display-name">Display name</label>
      <input id="contact-display-name" type="text" value={name} onChange={(event) => setName(event.target.value)} disabled={isSubmitting} autoComplete="name" maxLength={255} />
      <label htmlFor="contact-first-name">First name</label>
      <input id="contact-first-name" type="text" value={firstName} onChange={(event) => setFirstName(event.target.value)} disabled={isSubmitting} autoComplete="given-name" maxLength={255} />
      <label htmlFor="contact-last-name">Last name</label>
      <input id="contact-last-name" type="text" value={lastName} onChange={(event) => setLastName(event.target.value)} disabled={isSubmitting} autoComplete="family-name" maxLength={255} />
      <label htmlFor="contact-email">Email</label>
      <input id="contact-email" type="email" value={email} onChange={(event) => setEmail(event.target.value)} disabled={isSubmitting} autoComplete="email" maxLength={254} />
      <label htmlFor="contact-phone">Phone</label>
      <input id="contact-phone" type="tel" value={phone} onChange={(event) => setPhone(event.target.value)} disabled={isSubmitting} autoComplete="tel" maxLength={100} />
      <label htmlFor="contact-address-home">Home address</label>
      <textarea id="contact-address-home" value={addressHome} onChange={(event) => setAddressHome(event.target.value)} disabled={isSubmitting} autoComplete="street-address" rows={3} maxLength={2000} />
      <label htmlFor="contact-date-of-birth">Date of birth</label>
      <input id="contact-date-of-birth" type="date" value={dateOfBirth} onChange={(event) => setDateOfBirth(event.target.value)} disabled={isSubmitting} />
      <label htmlFor="contact-notes">Notes</label>
      <textarea id="contact-notes" value={condition} onChange={(event) => setCondition(event.target.value)} disabled={isSubmitting} rows={5} maxLength={10000} />
      <label className="checkbox-row" htmlFor="contact-deceased"><input id="contact-deceased" type="checkbox" checked={deceased} onChange={(event) => setDeceased(event.target.checked)} disabled={isSubmitting} /> Deceased</label>
      {submitError && <p className="status error" role="alert">{submitError}</p>}
      <div className="form-actions">
        <ActionButton type="submit" disabled={isSubmitting || !hasRequiredName}>{isSubmitting ? 'Saving…' : 'Save contact'}</ActionButton>
        <SecondaryButton disabled={isSubmitting} onClick={onCancel}>Cancel</SecondaryButton>
      </div>
    </form>
  );
}


function OrganizationCreateForm({ accessToken, onCreated, onCancel }: { accessToken: string | null; onCreated: (detail: OrganizationDetail) => void; onCancel: () => void }) {
  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const [fax, setFax] = useState('');
  const [email, setEmail] = useState('');
  const [website, setWebsite] = useState('');
  const [address1, setAddress1] = useState('');
  const [address2, setAddress2] = useState('');
  const [city, setCity] = useState('');
  const [state, setState] = useState('');
  const [postalCode, setPostalCode] = useState('');
  const [country, setCountry] = useState('');
  const [notes, setNotes] = useState('');
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const hasRequiredName = Boolean(name.trim());

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!hasRequiredName) {
      setSubmitError('Enter an organization name before saving.');
      return;
    }
    if (email.trim() && !email.includes('@')) {
      setSubmitError('Enter a valid email address.');
      return;
    }
    if (!accessToken) {
      setSubmitError('Your Shale session is not available. Please sign in again.');
      return;
    }

    setIsSubmitting(true);
    setSubmitError(null);
    try {
      const created = await createOrganization(accessToken, {
        name: name.trim(),
        phone: phone.trim() || null,
        fax: fax.trim() || null,
        email: email.trim() || null,
        website: website.trim() || null,
        address1: address1.trim() || null,
        address2: address2.trim() || null,
        city: city.trim() || null,
        state: state.trim() || null,
        postalCode: postalCode.trim() || null,
        country: country.trim() || null,
        notes: notes.trim() || null,
      });
      onCreated(created);
    } catch (caught) {
      setSubmitError(caught instanceof Error ? caught.message : 'Organization could not be created.');
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <form className="case-edit-form" onSubmit={handleSubmit} aria-label="Create organization">
      <label htmlFor="new-organization-name">Organization name</label>
      <input id="new-organization-name" type="text" value={name} onChange={(event) => setName(event.target.value)} disabled={isSubmitting} autoComplete="organization" maxLength={255} required />
      <label htmlFor="new-organization-email">Email</label>
      <input id="new-organization-email" type="email" value={email} onChange={(event) => setEmail(event.target.value)} disabled={isSubmitting} autoComplete="email" maxLength={254} />
      <label htmlFor="new-organization-phone">Phone</label>
      <input id="new-organization-phone" type="tel" value={phone} onChange={(event) => setPhone(event.target.value)} disabled={isSubmitting} autoComplete="tel" maxLength={100} />
      <label htmlFor="new-organization-fax">Fax</label>
      <input id="new-organization-fax" type="tel" value={fax} onChange={(event) => setFax(event.target.value)} disabled={isSubmitting} maxLength={100} />
      <label htmlFor="new-organization-website">Website</label>
      <input id="new-organization-website" type="url" value={website} onChange={(event) => setWebsite(event.target.value)} disabled={isSubmitting} autoComplete="url" maxLength={500} />
      <label htmlFor="new-organization-address1">Address line 1</label>
      <input id="new-organization-address1" type="text" value={address1} onChange={(event) => setAddress1(event.target.value)} disabled={isSubmitting} autoComplete="address-line1" maxLength={500} />
      <label htmlFor="new-organization-address2">Address line 2</label>
      <input id="new-organization-address2" type="text" value={address2} onChange={(event) => setAddress2(event.target.value)} disabled={isSubmitting} autoComplete="address-line2" maxLength={500} />
      <label htmlFor="new-organization-city">City</label>
      <input id="new-organization-city" type="text" value={city} onChange={(event) => setCity(event.target.value)} disabled={isSubmitting} autoComplete="address-level2" maxLength={200} />
      <label htmlFor="new-organization-state">State</label>
      <input id="new-organization-state" type="text" value={state} onChange={(event) => setState(event.target.value)} disabled={isSubmitting} autoComplete="address-level1" maxLength={100} />
      <label htmlFor="new-organization-postal-code">Zip</label>
      <input id="new-organization-postal-code" type="text" value={postalCode} onChange={(event) => setPostalCode(event.target.value)} disabled={isSubmitting} autoComplete="postal-code" maxLength={100} />
      <label htmlFor="new-organization-country">Country</label>
      <input id="new-organization-country" type="text" value={country} onChange={(event) => setCountry(event.target.value)} disabled={isSubmitting} autoComplete="country-name" maxLength={100} />
      <label htmlFor="new-organization-notes">Notes</label>
      <textarea id="new-organization-notes" value={notes} onChange={(event) => setNotes(event.target.value)} disabled={isSubmitting} rows={5} maxLength={10000} />
      {submitError && <p className="status error" role="alert">{submitError}</p>}
      <div className="form-actions">
        <ActionButton type="submit" disabled={isSubmitting || !hasRequiredName}>{isSubmitting ? 'Saving…' : 'Create organization'}</ActionButton>
        <SecondaryButton disabled={isSubmitting} onClick={onCancel}>Cancel</SecondaryButton>
      </div>
    </form>
  );
}

function OrganizationDetailPage({ accessToken }: { accessToken: string | null }) {
  const { organizationId } = useParams();
  const numericOrganizationId = Number(organizationId);
  const [organizationDetail, setOrganizationDetail] = useState<OrganizationDetail | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [isEditing, setIsEditing] = useState(false);

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
    <DetailShell className="organization-detail-page" titleId="organization-detail-title">
      <DetailHeader eyebrow="Organization Detail" title={title} titleId="organization-detail-title" backTo="/organizations" backLabel="Back to Organizations" />
      {isLoading && <LoadingState message="Loading organization detail…" />}
      {!isLoading && error && <p className="status error" role="alert">{error}</p>}
      {!isLoading && !error && !organizationDetail && <EmptyState message="No organization detail was found." />}
      {!isLoading && !error && organizationDetail && <OrganizationDetailReadOnly accessToken={accessToken} detail={organizationDetail} onDetailChanged={setOrganizationDetail} />}
    </DetailShell>
  );
}

function OrganizationDetailReadOnly({ accessToken, detail, onDetailChanged }: { accessToken: string | null; detail: OrganizationDetail; onDetailChanged: (detail: OrganizationDetail) => void }) {
  const [isEditingDetails, setIsEditingDetails] = useState(false);
  const [isEditingAssignment, setIsEditingAssignment] = useState(false);
  return (
    <div className="detail-sections">
      <section aria-labelledby="organization-info-title">
        <div className="section-heading-row">
          <h2 id="organization-info-title">Organization Information</h2>
          {!isEditingDetails && <ActionButton onClick={() => setIsEditingDetails(true)}>Edit organization</ActionButton>}
        </div>
        {isEditingDetails && <OrganizationDetailsForm accessToken={accessToken} detail={detail} onSaved={(updated) => { onDetailChanged(updated); setIsEditingDetails(false); }} onCancel={() => setIsEditingDetails(false)} />}
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
          <RelatedOrganizationCasesList cases={detail.relatedCases} />
        </section>
      )}
    </div>
  );
}

function OrganizationDetailsForm({ accessToken, detail, onSaved, onCancel }: { accessToken: string | null; detail: OrganizationDetail; onSaved: (detail: OrganizationDetail) => void; onCancel: () => void }) {
  const [name, setName] = useState(detail.name || '');
  const [phone, setPhone] = useState(detail.phone || '');
  const [fax, setFax] = useState(detail.fax || '');
  const [email, setEmail] = useState(detail.email || '');
  const [website, setWebsite] = useState(detail.website || '');
  const [address1, setAddress1] = useState(detail.address1 || '');
  const [address2, setAddress2] = useState(detail.address2 || '');
  const [city, setCity] = useState(detail.city || '');
  const [state, setState] = useState(detail.state || '');
  const [postalCode, setPostalCode] = useState(detail.postalCode || '');
  const [country, setCountry] = useState(detail.country || '');
  const [notes, setNotes] = useState(detail.notes || '');
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const hasRequiredName = Boolean(name.trim());

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!hasRequiredName) {
      setSubmitError('Enter an organization name before saving.');
      return;
    }
    if (email.trim() && !email.includes('@')) {
      setSubmitError('Enter a valid email address.');
      return;
    }
    if (!accessToken) {
      setSubmitError('Your Shale session is not available. Please sign in again.');
      return;
    }

    setIsSubmitting(true);
    setSubmitError(null);
    try {
      const updated = await updateOrganizationDetails(accessToken, detail.id, {
        name: name.trim(),
        phone: phone.trim() || null,
        fax: fax.trim() || null,
        email: email.trim() || null,
        website: website.trim() || null,
        address1: address1.trim() || null,
        address2: address2.trim() || null,
        city: city.trim() || null,
        state: state.trim() || null,
        postalCode: postalCode.trim() || null,
        country: country.trim() || null,
        notes: notes.trim() || null,
      });
      onSaved(updated);
    } catch (caught) {
      setSubmitError(caught instanceof Error ? caught.message : 'Organization details could not be saved.');
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <form className="case-edit-form" onSubmit={handleSubmit}>
      <label htmlFor="organization-name">Organization name</label>
      <input id="organization-name" type="text" value={name} onChange={(event) => setName(event.target.value)} disabled={isSubmitting} autoComplete="organization" maxLength={255} required />
      <label htmlFor="organization-email">Email</label>
      <input id="organization-email" type="email" value={email} onChange={(event) => setEmail(event.target.value)} disabled={isSubmitting} autoComplete="email" maxLength={254} />
      <label htmlFor="organization-phone">Phone</label>
      <input id="organization-phone" type="tel" value={phone} onChange={(event) => setPhone(event.target.value)} disabled={isSubmitting} autoComplete="tel" maxLength={100} />
      <label htmlFor="organization-fax">Fax</label>
      <input id="organization-fax" type="tel" value={fax} onChange={(event) => setFax(event.target.value)} disabled={isSubmitting} maxLength={100} />
      <label htmlFor="organization-website">Website</label>
      <input id="organization-website" type="url" value={website} onChange={(event) => setWebsite(event.target.value)} disabled={isSubmitting} autoComplete="url" maxLength={500} />
      <label htmlFor="organization-address1">Address line 1</label>
      <input id="organization-address1" type="text" value={address1} onChange={(event) => setAddress1(event.target.value)} disabled={isSubmitting} autoComplete="address-line1" maxLength={500} />
      <label htmlFor="organization-address2">Address line 2</label>
      <input id="organization-address2" type="text" value={address2} onChange={(event) => setAddress2(event.target.value)} disabled={isSubmitting} autoComplete="address-line2" maxLength={500} />
      <label htmlFor="organization-city">City</label>
      <input id="organization-city" type="text" value={city} onChange={(event) => setCity(event.target.value)} disabled={isSubmitting} autoComplete="address-level2" maxLength={200} />
      <label htmlFor="organization-state">State</label>
      <input id="organization-state" type="text" value={state} onChange={(event) => setState(event.target.value)} disabled={isSubmitting} autoComplete="address-level1" maxLength={100} />
      <label htmlFor="organization-postal-code">Zip</label>
      <input id="organization-postal-code" type="text" value={postalCode} onChange={(event) => setPostalCode(event.target.value)} disabled={isSubmitting} autoComplete="postal-code" maxLength={100} />
      <label htmlFor="organization-country">Country</label>
      <input id="organization-country" type="text" value={country} onChange={(event) => setCountry(event.target.value)} disabled={isSubmitting} autoComplete="country-name" maxLength={100} />
      <label htmlFor="organization-notes">Notes</label>
      <textarea id="organization-notes" value={notes} onChange={(event) => setNotes(event.target.value)} disabled={isSubmitting} rows={5} maxLength={10000} />
      {submitError && <p className="status error" role="alert">{submitError}</p>}
      <div className="form-actions">
        <ActionButton type="submit" disabled={isSubmitting || !hasRequiredName}>{isSubmitting ? 'Saving…' : 'Save organization'}</ActionButton>
        <SecondaryButton disabled={isSubmitting} onClick={onCancel}>Cancel</SecondaryButton>
      </div>
    </form>
  );
}

function RelatedOrganizationCasesList({ cases }: { cases: OrganizationDetail['relatedCases'] }) {
  const navigate = useNavigate();
  return (
    <EntityList ariaLabel="Related organization cases">
      {cases.map((item) => (
        <EntityCard
          key={item.id}
          title={displayValue(item.name, `Case ${item.id}`)}
          subtitle={[item.partyRoleName, item.side].filter(Boolean).join(' • ') || 'Related case'}
          metadata={(
            <MetadataGrid>
              <MetadataRow label="Responsible attorney" value={displayValue(item.responsibleAttorneyName)} />
              <MetadataRow label="Intake date" value={formatDate(item.intakeDate)} />
              <MetadataRow label="Statute of limitations" value={formatDate(item.statuteOfLimitationsDate)} />
            </MetadataGrid>
          )}
          onClick={() => navigate(`/cases/${item.id}`)}
          ariaLabel={`Open related case ${displayValue(item.name, `Case ${item.id}`)}`}
        />
      ))}
    </EntityList>
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
