import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
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
