import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import App from './App';
import * as api from './api';

vi.mock('./api', async original => ({
  ...await original<typeof import('./api')>(),
  readAccessToken: vi.fn(), getCurrentUser: vi.fn(), listAssignedCases: vi.fn(), listAssignedTasks: vi.fn(),
  listTeamMembers: vi.fn(), listCaseStatusSettings: vi.fn(), listPracticeAreaSettings: vi.fn(),
  getCaseDetail: vi.fn(), getContactDetail: vi.fn(), getOrganizationDetail: vi.fn(), getTaskDetail: vi.fn(),
  getTeamMemberDetail: vi.fn(), listCaseTasks: vi.fn(), listCaseUpdates: vi.fn(), completeTask: vi.fn(),
}));
const user: api.AuthenticatedUser = { authenticated: true, userId: 1, shaleClientId: 1, displayName: 'Example User',
  email: 'user@example.invalid', nameFirst: null, nameLast: null, isAdmin: false, isAttorney: false, initials: null, color: null };
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api.readAccessToken).mockReturnValue('test-token');
  vi.mocked(api.getCurrentUser).mockResolvedValue(user);
  for (const method of [api.listAssignedCases, api.listAssignedTasks, api.listTeamMembers, api.listCaseStatusSettings,
    api.listPracticeAreaSettings, api.listCaseTasks, api.listCaseUpdates]) vi.mocked(method).mockResolvedValue([]);
  for (const method of [api.getCaseDetail, api.getContactDetail, api.getOrganizationDetail, api.getTaskDetail,
    api.getTeamMemberDetail]) vi.mocked(method).mockRejectedValue(new Error('Synthetic detail failure'));
});
afterEach(cleanup);
function beta(path: string) { window.history.replaceState({}, '', path); render(<App />); }
describe('beta composition after primitive extraction', () => {
  it.each([
    ['/my-shale', 'My Shale'], ['/tasks', 'Tasks'], ['/cases', 'Cases'], ['/contacts', 'Contacts'],
    ['/organizations', 'Organizations'], ['/team', 'Team'], ['/settings', 'Settings'],
    ['/cases/1', 'Case Detail'], ['/tasks/1', 'Task Detail'], ['/contacts/1', 'Contact Detail'],
    ['/organizations/1', 'Organization Detail'], ['/team/1', 'Team Member Detail'],
  ])('preserves direct route %s', async (path, heading) => {
    beta(path); expect(await screen.findByRole('heading', { name: heading, level: 1 })).toBeTruthy();
    expect(api.getCurrentUser).toHaveBeenCalledWith('test-token');
    expect(document.querySelector('.shale-foundation')).toBeNull();
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
});
