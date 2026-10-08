import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import App from './App';
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
  for (const method of [api.listAssignedCases, api.listAssignedTasks, api.listTeamMembers, api.listCaseStatusSettings,
    api.listPracticeAreaSettings, api.listCaseTasks, api.listCaseUpdates]) vi.mocked(method).mockResolvedValue([]);
  for (const method of [api.getCaseDetail, api.getContactDetail, api.getOrganizationDetail, api.getTaskDetail,
    api.getTeamMemberDetail]) vi.mocked(method).mockRejectedValue(new Error('Synthetic detail failure'));
});
afterEach(() => { cleanup(); vi.unstubAllGlobals(); });
function beta(path: string) { window.history.replaceState({}, '', path); render(<App />); }
describe('authenticated responsive shell composition', () => {
  it.each([
    ['/my-shale', 'My Shale'], ['/tasks', 'Tasks'], ['/cases', 'Cases'], ['/contacts', 'Contacts'],
    ['/organizations', 'Organizations'], ['/team', 'Team'], ['/settings', 'Settings'],
    ['/cases/1', 'Case Detail'], ['/tasks/1', 'Task Detail'], ['/contacts/1', 'Contact Detail'],
    ['/organizations/1', 'Organization Detail'], ['/team/1', 'Team Member Detail'],
  ])('preserves direct route %s', async (path, heading) => {
    beta(path); expect(await screen.findByRole('heading', { name: heading, level: 1 })).toBeTruthy();
    expect(api.getCurrentUser).toHaveBeenCalledWith('test-token');
    expect(screen.getByRole('navigation', { name: 'Primary navigation' })).toBeTruthy();
    expect(document.querySelector('.shale-authenticated')).toBeTruthy();
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
    expect(api.getCaseDetail).toHaveBeenCalledWith('test-token', 42);
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
  it('preserves verification failure and successful login contracts', async () => {
    vi.mocked(api.getCurrentUser).mockRejectedValueOnce(new Error('Synthetic verification failure'));
    vi.mocked(api.login).mockResolvedValue({ accessToken: 'new-test-token' } as api.LoginResponse);
    beta('/contacts/7');
    await screen.findByRole('heading', { name: 'Sign in' });
    expect(api.clearAccessToken).toHaveBeenCalledTimes(1); expect(screen.queryByRole('navigation')).toBeNull();
    expect(window.history.state.usr.from.pathname).toBe('/contacts/7');
    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'example@example.invalid' } });
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'synthetic-password' } });
    fireEvent.submit(screen.getByRole('button', { name: 'Sign in' }).closest('form')!);
    // The unchanged login composition currently falls back to My Shale after sign-in.
    // Baseline reproduction and the return-to acceptance gap are recorded in the review.
    await screen.findByRole('heading', { name: 'My Shale', level: 1 });
    expect(api.getCurrentUser).toHaveBeenLastCalledWith('new-test-token');
    expect(api.storeAccessToken).toHaveBeenCalledWith('new-test-token');
    expect(api.listAssignedCases).toHaveBeenCalledWith('new-test-token');
  });
  it('logs out locally and unmounts protected content before the existing remote call finishes', async () => {
    let finish!: () => void;
    vi.mocked(api.logout).mockReturnValue(new Promise(done => { finish = done; }));
    beta('/my-shale'); await screen.findByRole('heading', { name: 'My Shale', level: 1 });
    fireEvent.click(screen.getByRole('button', { name: 'Logout' }));
    await screen.findByRole('heading', { name: 'Sign in' });
    expect(api.clearAccessToken).toHaveBeenCalledTimes(1);
    expect(api.logout).toHaveBeenCalledWith('test-token'); expect(screen.queryByRole('navigation')).toBeNull();
    expect(screen.queryByText(user.displayName!)).toBeNull(); finish();
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
