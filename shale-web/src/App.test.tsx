import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest';
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import App from './App';
import { STARTUP_VERIFICATION_TIMEOUT_MS } from './useStartupSession';
import * as api from './api';
import { destinations } from './shell/navigation';

vi.mock('./api', async original => ({
  ...await original<typeof import('./api')>(),
  readAccessToken: vi.fn(), getCurrentUser: vi.fn(), listAssignedCases: vi.fn(), listAssignedTasks: vi.fn(),
  listTeamMembers: vi.fn(), listCaseStatusSettings: vi.fn(), listPracticeAreaSettings: vi.fn(),
  getCaseDetail: vi.fn(), getContactDetail: vi.fn(), getOrganizationDetail: vi.fn(), getTaskDetail: vi.fn(),
  login: vi.fn(), logout: vi.fn(), storeAccessToken: vi.fn(), clearAccessToken: vi.fn(),
  getTeamMemberDetail: vi.fn(), listCaseTasks: vi.fn(), listCaseUpdates: vi.fn(), completeTask: vi.fn(),
}));
const user: api.AuthenticatedUser = { authenticated: true, userId: 1, shaleClientId: 1, displayName: 'Example User',
  email: 'user@example.invalid', nameFirst: null, nameLast: null, isAdmin: false, isAttorney: false, initials: null, color: null };
beforeEach(() => {
  vi.resetAllMocks();
  vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn() })));
  vi.mocked(api.readAccessToken).mockReturnValue('test-token');
  vi.mocked(api.getCurrentUser).mockResolvedValue(user);
  vi.mocked(api.logout).mockResolvedValue(undefined);
  vi.mocked(api.clearAccessToken).mockImplementation(() => { vi.mocked(api.readAccessToken).mockReturnValue(null); });
  vi.mocked(api.storeAccessToken).mockImplementation(token => { vi.mocked(api.readAccessToken).mockReturnValue(token); });
  for (const method of [api.listAssignedCases, api.listAssignedTasks, api.listTeamMembers, api.listCaseStatusSettings,
    api.listPracticeAreaSettings, api.listCaseTasks, api.listCaseUpdates]) vi.mocked(method).mockResolvedValue([]);
  for (const method of [api.getCaseDetail, api.getContactDetail, api.getOrganizationDetail, api.getTaskDetail,
    api.getTeamMemberDetail]) vi.mocked(method).mockRejectedValue(new Error('Synthetic detail failure'));
});
afterEach(() => { cleanup(); vi.useRealTimers(); vi.unstubAllGlobals(); });
function beta(path: string) { window.history.replaceState({}, '', path); render(<App />); }
describe('authenticated responsive shell composition', () => {
  it.each([
    ['/my-shale', 'My Shale', 'My Shale'], ['/tasks', 'Tasks', 'My Tasks'], ['/cases', 'Cases', 'Cases'], ['/contacts', 'Contacts', 'Contacts'],
    ['/organizations', 'Organizations', 'Organizations'], ['/team', 'Team', 'Team'], ['/settings', 'Settings', 'Settings'],
    ['/cases/1', 'Case Detail', 'Cases'], ['/tasks/1', 'Task Detail', 'My Tasks'], ['/contacts/1', 'Contact Detail', 'Contacts'],
    ['/organizations/1', 'Organization Detail', 'Organizations'], ['/team/1', 'Team Member Detail', 'Team'],
    ['/CASES/001/?page=2#details', 'Case Detail', 'Cases'], ['/TASKS/', 'Tasks', 'My Tasks'],
  ])('preserves direct route %s', async (path, heading, activeLabel) => {
    beta(path); expect(await screen.findByRole('heading', { name: heading, level: 1 })).toBeTruthy();
    expect(api.getCurrentUser).toHaveBeenCalledWith('test-token', expect.any(AbortSignal));
    expect(screen.getByRole('navigation', { name: 'Primary navigation' })).toBeTruthy();
    expect(document.querySelector('.shale-authenticated')).toBeTruthy();
    document.querySelector('details')!.open = true;
    expect(screen.getByRole('link', { name: activeLabel }).getAttribute('aria-current')).toBe('page');
    expect(screen.getAllByRole('link').filter(link => link.getAttribute('aria-current') === 'page')).toHaveLength(1);
  });
  it('retains the login route without a bearer', async () => {
    vi.mocked(api.readAccessToken).mockReturnValue(null); beta('/login');
    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeTruthy();
    expect(api.getCurrentUser).not.toHaveBeenCalled();
  });
  it('keeps task completion authoritative and separate from native card navigation', async () => {
    vi.mocked(api.listAssignedTasks).mockResolvedValue([{ id: 12, caseId: 1, title: 'Synthetic task', caseName: 'Example', priorityId: null, dueAt: null, completedAt: null }]);
    vi.mocked(api.completeTask).mockResolvedValue({ id: 12, completedAt: '2026-10-08T10:00:00' } as api.TaskDetail);
    beta('/my-shale'); fireEvent.click(await screen.findByRole('button', { name: 'Complete' }));
    await waitFor(() => expect(api.completeTask).toHaveBeenCalledWith('test-token', 12));
    expect(window.location.pathname).toBe('/my-shale'); expect(api.getTaskDetail).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: 'Open task Synthetic task' }));
    await waitFor(() => expect(api.getTaskDetail).toHaveBeenCalledWith('test-token', 12));
  });
  it('uses the registry for all available destinations and keeps future routes noninteractive', async () => {
    beta('/cases/42?review=1#detail');
    await screen.findByRole('heading', { name: 'Case Detail', level: 1 });
    const details = document.querySelector('details')!; details.open = true;
    expect(screen.getByRole('link', { name: 'Cases' }).getAttribute('aria-current')).toBe('page');
    for (const item of destinations) {
      if (item.available) expect(screen.getByRole('link', { name: item.label }).getAttribute('href')).toBe(item.path);
      else {
        expect(screen.queryByRole('link', { name: `${item.label} Unavailable` })).toBeNull();
        expect(screen.getByText(item.label)).toBeTruthy();
      }
    }
    expect(window.location.pathname + window.location.search + window.location.hash).toBe('/cases/42?review=1#detail');
    await waitFor(() => expect(api.getCaseDetail).toHaveBeenCalledWith('test-token', 42));
  });
  it('preserves browser back/forward, active navigation and route focus', async () => {
    beta('/cases'); await screen.findByRole('heading', { name: 'Cases', level: 1 });
    const details = document.querySelector('details')!; details.open = true;
    fireEvent.click(screen.getByRole('link', { name: 'Contacts' }));
    await screen.findByRole('heading', { name: 'Contacts', level: 1 });
    expect(details.open).toBe(false); expect(document.activeElement).toBe(screen.getByRole('main'));
    window.history.back();
    await screen.findByRole('heading', { name: 'Cases', level: 1 });
    details.open = true;
    expect(screen.getByRole('link', { name: 'Cases' }).getAttribute('aria-current')).toBe('page');
    window.history.forward();
    await screen.findByRole('heading', { name: 'Contacts', level: 1 });
    expect(document.activeElement).toBe(screen.getByRole('main'));
    details.open = true;
    expect(screen.getByRole('link', { name: 'Contacts' }).getAttribute('aria-current')).toBe('page');
  });
  it('keeps compact Escape/return focus, skip link and local theme changes separate from data loading', async () => {
    beta('/tasks'); await screen.findByRole('heading', { name: 'Tasks', level: 1 });
    const details = document.querySelector('details')!; details.open = true;
    const tasks = screen.getByRole('link', { name: 'My Tasks' }); tasks.focus();
    fireEvent.keyDown(tasks, { key: 'Escape' });
    expect(details.open).toBe(false); expect(document.activeElement).toBe(document.querySelector('summary'));
    fireEvent.click(screen.getByRole('link', { name: 'Skip to content' }));
    expect(document.activeElement).toBe(screen.getByRole('main'));
    await waitFor(() => expect(api.listAssignedTasks).toHaveBeenCalled());
    const calls = vi.mocked(api.listAssignedTasks).mock.calls.length;
    fireEvent.change(screen.getByLabelText('Theme (this session)'), { target: { value: 'dark' } });
    expect(document.querySelector('.shale-authenticated')?.getAttribute('data-theme')).toBe('dark');
    expect(api.listAssignedTasks).toHaveBeenCalledTimes(calls);
    expect(api.storeAccessToken).not.toHaveBeenCalled();
  });
  it('does not mount shell or load protected data until session verification finishes', async () => {
    let resolve!: (value: api.AuthenticatedUser) => void;
    vi.mocked(api.getCurrentUser).mockReturnValue(new Promise(done => { resolve = done; }));
    beta('/my-shale');
    expect(screen.getByText('Checking your Shale session…')).toBeTruthy();
    expect(screen.queryByRole('navigation')).toBeNull(); expect(api.listAssignedCases).not.toHaveBeenCalled();
    resolve(user); await screen.findByRole('heading', { name: 'My Shale', level: 1 });
    expect(screen.getByRole('navigation', { name: 'Primary navigation' })).toBeTruthy();
  });
  it('preserves confirmed rejection and successful login contracts', async () => {
    vi.mocked(api.getCurrentUser).mockRejectedValueOnce(new api.ApiError('Synthetic rejection', 401));
    vi.mocked(api.login).mockResolvedValue({ accessToken: 'new-test-token' } as api.LoginResponse);
    beta('/contacts/7');
    await screen.findByRole('heading', { name: 'Sign in' });
    expect(api.clearAccessToken).toHaveBeenCalledTimes(1); expect(screen.queryByRole('navigation')).toBeNull();
    expect(window.history.state.usr.from.pathname).toBe('/contacts/7');
    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'example@example.invalid' } });
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'synthetic-password' } });
    fireEvent.submit(screen.getByRole('button', { name: 'Sign in' }).closest('form')!);
    await screen.findByRole('heading', { name: 'Contact Detail', level: 1 });
    expect(api.getCurrentUser).toHaveBeenLastCalledWith('new-test-token');
    expect(api.storeAccessToken).toHaveBeenCalledWith('new-test-token');
    await waitFor(() => expect(api.getContactDetail).toHaveBeenCalledWith('new-test-token', 7));
  });
  it('logs out locally and unmounts protected content before the existing remote call finishes', async () => {
    let finish!: () => void;
    vi.mocked(api.logout).mockReturnValue(new Promise(done => { finish = done; }));
    beta('/my-shale'); await screen.findByRole('heading', { name: 'My Shale', level: 1 });
    fireEvent.click(screen.getByRole('button', { name: 'Logout' }));
    await screen.findByRole('heading', { name: 'Sign in' });
    expect(api.clearAccessToken).toHaveBeenCalledTimes(1);
    expect(api.logout).toHaveBeenCalledWith('test-token', expect.any(AbortSignal)); expect(screen.queryByRole('navigation')).toBeNull();
    expect(screen.queryByText(user.displayName!)).toBeNull(); finish();
  });

});

describe('startup verification recovery', () => {
  const protectedCalls = [api.listAssignedCases, api.listAssignedTasks, api.getCaseDetail, api.getContactDetail,
    api.getOrganizationDetail, api.getTaskDetail, api.getTeamMemberDetail, api.listTeamMembers,
    api.listCaseTasks, api.listCaseUpdates, api.listCaseStatusSettings, api.listPracticeAreaSettings];
  function expectBlocked() {
    expect(screen.queryByRole('navigation')).toBeNull();
    expect(screen.queryByText(user.displayName!)).toBeNull();
    for (const call of protectedCalls) expect(call).not.toHaveBeenCalled();
    expect(api.login).not.toHaveBeenCalled(); expect(api.completeTask).not.toHaveBeenCalled();
  }
  it.each(['retry', 'return'])('deadline blocks features, preserves location/history, and supports %s', async action => {
    vi.useFakeTimers();
    let late!: (value: api.AuthenticatedUser) => void;
    vi.mocked(api.getCurrentUser).mockReturnValueOnce(new Promise(done => { late = done; }));
    const path = '/contacts/7?sort=name#profile'; beta(path);
    const historyLength = window.history.length;
    expectBlocked();
    await act(async () => { await vi.advanceTimersByTimeAsync(STARTUP_VERIFICATION_TIMEOUT_MS); });
    expect(screen.getByRole('alert').textContent).toMatch(/verification is unavailable/);
    expectBlocked(); expect(api.readAccessToken()).toBe('test-token');
    expect(api.clearAccessToken).not.toHaveBeenCalled();
    expect(window.location.pathname + window.location.search + window.location.hash).toBe(path);
    if (action === 'retry') {
      await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Retry' })); });
      expect(screen.getByRole('heading', { name: 'Contact Detail' })).toBeTruthy();
      expect(api.getContactDetail).toHaveBeenCalledWith('test-token', 7);
      expect(window.location.pathname + window.location.search + window.location.hash).toBe(path);
    } else {
      await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Return to sign in' })); });
      expect(screen.getByRole('heading', { name: 'Sign in' })).toBeTruthy();
      expect(api.readAccessToken()).toBeNull(); expect(api.logout).not.toHaveBeenCalled();
      expect(window.location.pathname).toBe('/login'); expect(window.history.state.usr).toBeNull();
      expectBlocked();
    }
    await act(async () => { late(user); });
    expect(window.history.length).toBe(historyLength);
    expect(api.getCurrentUser).toHaveBeenCalledTimes(action === 'retry' ? 2 : 1);
    if (action === 'return') expectBlocked();
  });
  it.each(['/my-shale', '/cases/7?page=2#details', '/login', '/', '/unknown'])
  ('blocks all route content while pending and unavailable at %s', async path => {
    let reject!: (error: Error) => void;
    vi.mocked(api.getCurrentUser).mockReturnValueOnce(new Promise((_done, fail) => { reject = fail; }));
    beta(path);
    expect(screen.getByRole('status').textContent).toBe('Checking your Shale session…');
    expect((screen.getByRole('button', { name: 'Retry' }) as HTMLButtonElement).disabled).toBe(true);
    expectBlocked(); expect(window.location.pathname + window.location.search + window.location.hash).toBe(path);
    reject(new TypeError('Synthetic offline')); await screen.findByRole('alert');
    expect(screen.getByRole('alert').textContent).toMatch(/verification is unavailable/);
    expectBlocked(); expect(api.clearAccessToken).not.toHaveBeenCalled();
    expect(window.location.pathname + window.location.search + window.location.hash).toBe(path);
  });
  it('keeps Retry focused through repeated failure, prevents duplicates, then restores the exact detail without login', async () => {
    let resolve!: (value: api.AuthenticatedUser) => void;
    vi.mocked(api.getCurrentUser).mockRejectedValueOnce(new TypeError('Synthetic offline'))
      .mockRejectedValueOnce(new api.ApiError('Synthetic outage', 503))
      .mockReturnValueOnce(new Promise(done => { resolve = done; }));
    const path = '/contacts/7?sort=name#profile'; beta(path); await screen.findByRole('alert');
    const historyLength = window.history.length;
    const retry = screen.getByRole('button', { name: 'Retry' }); retry.focus(); fireEvent.click(retry);
    await screen.findByRole('alert'); expect(document.activeElement).toBe(retry); expectBlocked();
    fireEvent.click(retry); fireEvent.click(retry);
    expect((retry as HTMLButtonElement).disabled).toBe(true); expect(retry.getAttribute('aria-busy')).toBe('true');
    expect(api.getCurrentUser).toHaveBeenCalledTimes(3); expectBlocked();
    resolve(user); await screen.findByRole('heading', { name: 'Contact Detail', level: 1 });
    expect(window.location.pathname + window.location.search + window.location.hash).toBe(path);
    expect(window.history.length).toBe(historyLength);
    await waitFor(() => expect(api.getContactDetail).toHaveBeenCalledTimes(1));
    expect(api.login).not.toHaveBeenCalled(); expect(api.storeAccessToken).not.toHaveBeenCalled();
    expect(api.clearAccessToken).not.toHaveBeenCalled(); expect(api.listAssignedCases).not.toHaveBeenCalled();
  });
  it('Retry rejection uses Phase 3A signed-out restoration with the complete location', async () => {
    vi.mocked(api.getCurrentUser).mockRejectedValueOnce(new TypeError('Synthetic offline'))
      .mockRejectedValueOnce(new api.ApiError('Synthetic rejection', 401));
    beta('/tasks/7?status=open#activity'); await screen.findByRole('alert');
    const historyLength = window.history.length; fireEvent.click(screen.getByRole('button', { name: 'Retry' }));
    await screen.findByRole('heading', { name: 'Sign in' });
    expect(api.clearAccessToken).toHaveBeenCalledTimes(1); expectBlocked();
    expect(window.history.state.usr.from).toMatchObject({ pathname: '/tasks/7', search: '?status=open', hash: '#activity' });
    expect(window.history.length).toBe(historyLength);
  });
  it.each(['pending', 'unavailable'])('Return to sign in clears locally with replacement from %s and rejects late success', async state => {
    let resolve!: (value: api.AuthenticatedUser) => void;
    if (state === 'unavailable') vi.mocked(api.getCurrentUser).mockRejectedValueOnce(new TypeError('Synthetic offline'));
    vi.mocked(api.getCurrentUser).mockReturnValueOnce(new Promise(done => { resolve = done; }));
    beta('/contacts/7?sort=name#profile');
    if (state === 'unavailable') {
      await screen.findByRole('alert'); fireEvent.click(screen.getByRole('button', { name: 'Retry' }));
    }
    const historyLength = window.history.length;
    fireEvent.click(screen.getByRole('button', { name: 'Return to sign in' }));
    const heading = await screen.findByRole('heading', { name: 'Sign in' });
    expect(api.clearAccessToken).toHaveBeenCalledTimes(1); expect(api.logout).not.toHaveBeenCalled();
    expect(window.history.state.usr).toBeNull(); expect(window.history.length).toBe(historyLength);
    expect(document.activeElement).toBe(heading);
    resolve(user); await waitFor(() => expect(screen.getByRole('heading', { name: 'Sign in' })).toBeTruthy());
    expectBlocked(); expect(api.storeAccessToken).not.toHaveBeenCalled();
  });
  it('explicit return drops the old target, while historical Back remains protected', async () => {
    vi.mocked(api.getCurrentUser).mockRejectedValueOnce(new TypeError('Synthetic offline'));
    vi.mocked(api.login).mockResolvedValue({ accessToken: 'new-test-token' } as api.LoginResponse);
    window.history.replaceState({}, '', '/tasks/7?status=open#activity');
    window.history.pushState({}, '', '/contacts/7?sort=name#profile'); render(<App />);
    await screen.findByRole('alert'); fireEvent.click(screen.getByRole('button', { name: 'Return to sign in' }));
    await screen.findByRole('heading', { name: 'Sign in' }); submitLogin();
    await screen.findByRole('heading', { name: 'My Shale', level: 1 });
    fireEvent.click(screen.getByRole('button', { name: 'Logout' })); await screen.findByRole('heading', { name: 'Sign in' });
    window.history.back(); await waitFor(() => expect(window.history.state.usr?.from?.pathname).toBe('/tasks/7'));
    expect(screen.queryByRole('navigation')).toBeNull(); expect(api.getTaskDetail).not.toHaveBeenCalled();
  });
});

function submitLogin() {
  fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'example@example.invalid' } });
  fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'synthetic-password' } });
  fireEvent.submit(screen.getByRole('button', { name: 'Sign in' }).closest('form')!);
}
function loginAt(state: unknown) {
  window.history.replaceState({ usr: state }, '', '/login'); render(<App />);
}
describe('verified login return restoration', () => {
  beforeEach(() => {
    vi.mocked(api.readAccessToken).mockReturnValue(null);
    vi.mocked(api.login).mockResolvedValue({ accessToken: 'new-test-token' } as api.LoginResponse);
  });
  it.each([
    ['/cases/7?page=2#details', 'Case Detail', api.getCaseDetail],
    ['/contacts/7?sort=name#profile', 'Contact Detail', api.getContactDetail],
    ['/tasks/7?status=open#activity', 'Task Detail', api.getTaskDetail],
  ])('restores signed-out detail %s only after login and verification', async (path, heading, detail) => {
    let verify!: (value: api.AuthenticatedUser) => void;
    vi.mocked(api.getCurrentUser).mockReturnValue(new Promise(done => { verify = done; }));
    beta(path); await screen.findByRole('heading', { name: 'Sign in' });
    expect(window.location.pathname).toBe('/login'); expect(detail).not.toHaveBeenCalled();
    expect(window.history.state.usr.from.pathname).toBe(path.split('?')[0]);
    const historyLength = window.history.length;
    submitLogin(); await waitFor(() => expect(api.getCurrentUser).toHaveBeenCalledWith('new-test-token'));
    expect(window.location.pathname).toBe('/login'); expect(api.storeAccessToken).not.toHaveBeenCalled();
    expect(detail).not.toHaveBeenCalled(); verify(user);
    await screen.findByRole('heading', { name: heading, level: 1 });
    expect(window.location.pathname + window.location.search + window.location.hash).toBe(path);
    await waitFor(() => expect(detail).toHaveBeenCalledWith('new-test-token', 7));
    expect(window.history.length).toBe(historyLength); expect(window.history.state.usr).toBeNull();
    expect(api.listAssignedCases).not.toHaveBeenCalled();
  });
  it.each(['credentials', 'verification'])('preserves return state through failed %s then succeeds', async failure => {
    if (failure === 'credentials') vi.mocked(api.login).mockRejectedValueOnce(new Error('Synthetic credentials rejected'));
    else vi.mocked(api.getCurrentUser).mockRejectedValueOnce(new Error('Synthetic verification rejected'));
    beta('/tasks/7?status=open#activity'); await screen.findByRole('heading', { name: 'Sign in' });
    const returnState = window.history.state.usr;
    submitLogin(); await screen.findByRole('alert');
    expect(window.location.pathname).toBe('/login'); expect(window.history.state.usr).toEqual(returnState);
    expect(api.getTaskDetail).not.toHaveBeenCalled(); expect(api.storeAccessToken).not.toHaveBeenCalled();
    submitLogin(); await screen.findByRole('heading', { name: 'Task Detail', level: 1 });
    expect(window.location.pathname + window.location.search + window.location.hash).toBe('/tasks/7?status=open#activity');
    await waitFor(() => expect(api.getTaskDetail).toHaveBeenCalledWith('new-test-token', 7));
  });
  it.each([undefined, { from: { pathname: '//evil.invalid' } }, { from: { pathname: '/login' } },
    { from: { pathname: '/unknown' } }, { from: { pathname: '/cases/7', search: '?bad=%' } }])
  ('uses default for absent or unsafe login state %#', async state => {
    loginAt(state); await screen.findByRole('heading', { name: 'Sign in' }); submitLogin();
    await screen.findByRole('heading', { name: 'My Shale', level: 1 });
    expect(window.location.pathname).toBe('/my-shale'); expect(window.history.state.usr).toBeNull();
    expect(api.getCaseDetail).not.toHaveBeenCalled();
  });
  it('replaces login so Back/Forward returns to prior authenticated navigation without a redirect loop', async () => {
    window.history.replaceState({}, '', '/login');
    window.history.pushState({}, '', '/contacts/7?sort=name#profile'); render(<App />);
    await screen.findByRole('heading', { name: 'Sign in' }); submitLogin();
    await screen.findByRole('heading', { name: 'Contact Detail', level: 1 });
    // Prior explicit login has no return state and uses its existing authenticated default.
    window.history.back(); await screen.findByRole('heading', { name: 'My Shale', level: 1 });
    window.history.forward(); await screen.findByRole('heading', { name: 'Contact Detail', level: 1 });
    expect(window.location.search + window.location.hash).toBe('?sort=name#profile');
    expect(api.login).toHaveBeenCalledTimes(1);
  });
  it('clears consumed return state on detail logout and requires fresh authentication even after Back', async () => {
    let finishLogout!: () => void;
    vi.mocked(api.logout).mockReturnValue(new Promise(done => { finishLogout = done; }));
    beta('/contacts/7?sort=name#profile'); await screen.findByRole('heading', { name: 'Sign in' }); submitLogin();
    await screen.findByRole('heading', { name: 'Contact Detail', level: 1 });
    document.querySelector('details')!.open = true;
    fireEvent.click(screen.getByRole('link', { name: 'My Tasks' }));
    await screen.findByRole('heading', { name: 'Tasks', level: 1 });
    fireEvent.click(screen.getByRole('button', { name: 'Logout' }));
    await screen.findByRole('heading', { name: 'Sign in' });
    expect(window.history.state.usr).toBeNull(); expect(screen.queryByRole('navigation')).toBeNull();
    submitLogin(); await screen.findByRole('heading', { name: 'My Shale', level: 1 });
    expect(window.location.pathname).toBe('/my-shale'); finishLogout();
    // Sign out again and navigate Back to a historical detail entry: still protected.
    fireEvent.click(screen.getByRole('button', { name: 'Logout' }));
    await screen.findByRole('heading', { name: 'Sign in' });
    const detailCalls = vi.mocked(api.getContactDetail).mock.calls.length;
    window.history.back(); await waitFor(() => expect(window.history.state.usr?.from?.pathname).toBe('/contacts/7'));
    expect(window.location.pathname).toBe('/login'); expect(screen.queryByRole('navigation')).toBeNull();
    expect(api.getContactDetail).toHaveBeenCalledTimes(detailCalls);
  });
  it.each([['/unknown', false, 'Sign in'], ['/unknown', true, 'My Shale'], ['/login', true, 'My Shale'], ['/', false, 'Sign in'], ['/', true, 'My Shale'],
    ...['/calendar', '/reports', '/search', '/cases/7/overview', '/settings/personal'].flatMap(path => [[path, false, 'Sign in'], [path, true, 'My Shale']] as [string, boolean, string][])])
  ('retains root, unknown and authenticated-login fallback for %s / signed in %s', async (path, authenticated, heading) => {
    vi.mocked(api.readAccessToken).mockReturnValue(authenticated ? 'test-token' : null);
    beta(path); await screen.findByRole('heading', { name: heading, level: 1 });
    expect(window.location.pathname).toBe(authenticated ? '/my-shale' : '/login');
  });
});


const assignedTask: api.CaseTaskListItem = { id: 12, caseId: 1, title: 'Synthetic task', caseName: 'Example', priorityId: null, dueAt: null, completedAt: null };
describe('My Shale shared presentation adoption', () => {
  it.each(['/my-shale', '/my-shale/'])('names sections and limits adoption to My Shale at %s while theme changes preserve loaded work', async path => {
    vi.mocked(api.listAssignedTasks).mockResolvedValue([assignedTask]);
    beta(path);
    await screen.findByRole('button', { name: 'Complete' });
    expect(screen.getByRole('region', { name: 'My Cases' })).toBeTruthy();
    expect(screen.getByRole('region', { name: 'My Tasks' })).toBeTruthy();
    expect(screen.queryByText(/read-only Shale summary/)).toBeNull();
    expect(document.querySelector('.shale-presentation')).toBeTruthy();
    expect(document.querySelector('.legacy-route-content')).toBeNull();
    const calls = vi.mocked(api.listAssignedTasks).mock.calls.length;
    fireEvent.change(screen.getByLabelText('Theme (this session)'), { target: { value: 'dark' } });
    expect(screen.getByRole('button', { name: 'Complete' })).toBeTruthy();
    expect(api.listAssignedTasks).toHaveBeenCalledTimes(calls);
    expect(api.completeTask).not.toHaveBeenCalled();
  });
  it('retains the shared task-list legacy action on the separate Tasks route', async () => {
    vi.mocked(api.listAssignedTasks).mockResolvedValue([assignedTask]); beta('/tasks');
    const complete = await screen.findByRole('button', { name: 'Complete' });
    expect(complete.classList.contains('secondary-button')).toBe(true);
    expect(document.querySelector('.legacy-route-content')).toBeTruthy();
    expect(document.querySelector('.shale-presentation')).toBeNull();
    expect(screen.queryByRole('status')).toBeNull();
  });
  it('keeps a pending completion disabled, does not complete optimistically, and merges only the returned task', async () => {
    let finish!: (task: api.TaskDetail) => void;
    vi.mocked(api.listAssignedTasks).mockResolvedValue([assignedTask, { ...assignedTask, id: 13, title: 'Other task' }]);
    vi.mocked(api.completeTask).mockReturnValue(new Promise(done => { finish = done; }));
    beta('/my-shale'); await screen.findAllByRole('button', { name: 'Complete' });
    const card = screen.getByRole('button', { name: 'Open task Synthetic task' }).closest('article')!;
    const complete = within(card).getByRole('button', { name: 'Complete' });
    expect(complete.classList.contains('secondary-button')).toBe(false);
    const status = within(screen.getByRole('region', { name: 'My Tasks' })).getByRole('status');
    expect(status.textContent).toBe('');
    expect(status.getAttribute('aria-live')).toBe('polite');
    expect(status.getAttribute('aria-atomic')).toBe('true');
    fireEvent.click(complete);
    expect(status.textContent).toBe('Completing Synthetic task…');
    expect((complete as HTMLButtonElement).disabled).toBe(true);
    expect(complete.getAttribute('aria-busy')).toBe('true');
    expect(complete.textContent).toBe('Completing…');
    fireEvent.click(complete); expect(api.completeTask).toHaveBeenCalledTimes(1);
    expect(within(card).getByText('Open')).toBeTruthy();
    expect(window.location.pathname).toBe('/my-shale');
    finish({ id: 12, completedAt: '2026-10-08T10:00:00' } as api.TaskDetail);
    await waitFor(() => expect(within(card).queryByRole('button', { name: /Complet/ })).toBeNull());
    expect(within(card).getByText('Completed')).toBeTruthy();
    expect(status.textContent).toBe('Completed Synthetic task.');
    expect(within(screen.getByRole('region', { name: 'My Tasks' })).getByRole('status')).toBe(status);
    const other = screen.getByRole('button', { name: 'Open task Other task' }).closest('article')!;
    expect(within(other).getByRole('button', { name: 'Complete' })).toBeTruthy();
    expect(api.listAssignedTasks).toHaveBeenCalledTimes(1);
    expect(api.getTaskDetail).not.toHaveBeenCalled();
  });
  it('announces completion failure, retains work and navigation, and never retries the mutation automatically', async () => {
    vi.mocked(api.listAssignedTasks).mockResolvedValue([assignedTask]);
    vi.mocked(api.completeTask).mockRejectedValue(new Error('Synthetic completion rejected'));
    beta('/my-shale'); fireEvent.click(await screen.findByRole('button', { name: 'Complete' }));
    expect((await screen.findByRole('alert')).textContent).toBe('Synthetic completion rejected');
    const complete = screen.getByRole('button', { name: 'Complete' });
    expect((complete as HTMLButtonElement).disabled).toBe(false);
    expect(within(screen.getByRole('region', { name: 'My Tasks' })).getByRole('status').textContent).toBe('');
    expect(screen.getByRole('list', { name: 'Assigned tasks' })).toBeTruthy();
    expect(screen.getByText('Open')).toBeTruthy(); expect(screen.queryByText('Completed')).toBeNull();
    expect(api.completeTask).toHaveBeenCalledTimes(1);
    expect(window.location.pathname).toBe('/my-shale');
    fireEvent.click(screen.getByRole('button', { name: 'Open task Synthetic task' }));
    await waitFor(() => expect(api.getTaskDetail).toHaveBeenCalledWith('test-token', 12));
  });
  it('distinguishes independent loading, successful empty, and failed sections', async () => {
    let finish!: (rows: api.CaseSearchResult[]) => void;
    vi.mocked(api.listAssignedCases).mockReturnValue(new Promise(done => { finish = done; }));
    vi.mocked(api.listAssignedTasks).mockRejectedValue(new Error('Synthetic task read failed'));
    beta('/my-shale'); await screen.findByRole('region', { name: 'My Cases' });
    expect(screen.getByText('Loading your cases…').getAttribute('role')).toBe('status');
    expect((await screen.findByRole('alert')).textContent).toBe('Synthetic task read failed');
    expect(screen.queryByText('No assigned tasks were found.')).toBeNull();
    finish([]); expect(await screen.findByText('No assigned cases were found.')).toBeTruthy();
    expect(screen.queryByText('Loading your cases…')).toBeNull();
    expect(screen.getByRole('alert')).toBeTruthy();
  });
  it('preserves missing-value fallbacks and already completed task facts without offering Complete', async () => {
    vi.mocked(api.listAssignedCases).mockResolvedValue([{ caseId: 3, caseName: '', caseNumber: '', caseStatus: '', responsibleAttorney: '', solDate: null } as api.CaseSearchResult]);
    vi.mocked(api.listAssignedTasks).mockResolvedValue([{ ...assignedTask, title: null, caseName: null, completedAt: '2026-10-08T10:00:00' }]);
    beta('/my-shale'); await screen.findByRole('button', { name: 'Open task Task 12' });
    expect(screen.getByRole('button', { name: 'Open case Case 3' })).toBeTruthy();
    for (const text of ['Case ID 3', 'Status not set', 'No related case', 'Completed']) expect(screen.getByText(text)).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Complete' })).toBeNull();
    expect(api.completeTask).not.toHaveBeenCalled();
  });
});

describe('truthful signed-out feedback', () => {
  function signInAgain() {
    vi.mocked(api.login).mockResolvedValue({ accessToken: 'synthetic-new-login' } as api.LoginResponse);
    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'user@example.invalid' } });
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'synthetic-password' } });
    fireEvent.submit(screen.getByRole('button', { name: 'Sign in' }).closest('form')!);
  }
  it('keeps one polite atomic region, truthful pending/success and usable login without moving input focus', async () => {
    let finish!: () => void;
    vi.mocked(api.logout).mockReturnValue(new Promise(done => { finish = done; }));
    beta('/contacts/7?sort=name#profile'); await screen.findByRole('heading', { name: 'Contact Detail' });
    const historyLength = window.history.length;
    fireEvent.click(screen.getByRole('button', { name: 'Logout' }));
    await screen.findByRole('heading', { name: 'Sign in' });
    const status = screen.getByRole('status');
    expect(status.textContent).toContain('You are signed out in this tab. Waiting for the server');
    expect(status.getAttribute('aria-live')).toBe('polite'); expect(status.getAttribute('aria-atomic')).toBe('true');
    expect(window.location.pathname).toBe('/login'); expect(window.history.state.usr).toBeNull();
    expect(window.history.length).toBe(historyLength); expect(api.storeAccessToken).not.toHaveBeenCalled();
    expect(screen.getByRole('button', { name: 'Sign in' }).hasAttribute('disabled')).toBe(false);
    screen.getByLabelText('Email').focus(); finish();
    await waitFor(() => expect(status.textContent).toContain('server confirmed revocation of the session used here'));
    expect(screen.getByRole('status')).toBe(status); expect(document.activeElement).toBe(screen.getByLabelText('Email'));
    expect(api.logout).toHaveBeenCalledTimes(1);
  });
  it.each([new api.ApiError('Sensitive HTTP detail', 401), new api.ApiError('Sensitive HTTP detail', 503), new TypeError('Sensitive network detail')])
  ('shows local certainty and remote uncertainty while keeping login usable (%#)', async error => {
    vi.mocked(api.logout).mockRejectedValue(error);
    beta('/my-shale'); await screen.findByRole('heading', { name: 'My Shale' });
    fireEvent.click(screen.getByRole('button', { name: 'Logout' }));
    await screen.findByRole('heading', { name: 'Sign in' });
    await waitFor(() => expect(screen.getByRole('status').textContent).toContain('Server session revocation could not be confirmed'));
    expect(screen.getByRole('status').textContent).toContain('You are signed out in this tab');
    expect(document.body.textContent).not.toContain('Sensitive'); expect(document.body.textContent).not.toContain('test-token');
    expect(api.storeAccessToken).not.toHaveBeenCalled(); signInAgain();
    await screen.findByRole('heading', { name: 'My Shale' });
    expect(api.logout).toHaveBeenCalledTimes(1); expect(api.storeAccessToken).toHaveBeenCalledWith('synthetic-new-login');
  });
  it('allows login while pending and discards the older logout result', async () => {
    let finish!: () => void;
    vi.mocked(api.logout).mockReturnValue(new Promise(done => { finish = done; }));
    beta('/contacts/7'); await screen.findByRole('heading', { name: 'Contact Detail' });
    fireEvent.click(screen.getByRole('button', { name: 'Logout' })); await screen.findByRole('heading', { name: 'Sign in' });
    const signal = vi.mocked(api.logout).mock.calls[0][1]; signInAgain();
    await screen.findByRole('heading', { name: 'My Shale' }); expect(signal?.aborted).toBe(true);
    finish(); await waitFor(() => expect(api.storeAccessToken).toHaveBeenCalledWith('synthetic-new-login'));
    expect(screen.queryByText(/revocation of the session used here/)).toBeNull();
    expect(screen.queryByRole('heading', { name: 'Sign in' })).toBeNull(); expect(api.clearAccessToken).toHaveBeenCalledTimes(1);
  });
});
