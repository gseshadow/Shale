import { act, cleanup, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { readCasePage, readCaseOverview } from './features/cases/client';
import * as api from './api';
import { bindSessionRequests, SessionRequestDiscarded } from './sessionRequests';
import { useStartupSession } from './useStartupSession';

const user = { authenticated: true, userId: 1, shaleClientId: 1, email: null, displayName: 'Synthetic User',
  nameFirst: null, nameLast: null, isAdmin: false, isAttorney: false, initials: null, color: null };
const fetchMock = vi.fn<typeof fetch>();
const response = (body: unknown = {}, status = 200) => new Response(JSON.stringify(body), { status });
function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>(done => { resolve = done; });
  return { promise, resolve };
}
beforeEach(() => { sessionStorage.clear(); fetchMock.mockReset(); vi.stubGlobal('fetch', fetchMock); });
afterEach(() => { cleanup(); sessionStorage.clear(); vi.unstubAllGlobals(); });
async function established() {
  const hook = renderHook(useStartupSession);
  await act(async () => {});
  act(() => hook.result.current.signIn('synthetic-established', user));
  return hook;
}

// Independent consumer inventory. Every real endpoint client must notify through the seam.
const consumers: [string, (token: string) => Promise<unknown>][] = [
  ['bounded case search', t => readCasePage(t, { mode: 'search', query: 'synthetic', page: 0 }, new AbortController())],
  ['bounded assigned', t => readCasePage(t, { mode: 'assigned', query: '', page: 0 }, new AbortController())],
  ['bounded overview', t => readCaseOverview(t, 7, new AbortController())],
  ['date families', t => api.listEffectiveCaseDateTypes(t)],
  ['advisory validation', t => api.validateContactValue(t, 'phone', '3035550123')],
  ['case search', t => api.searchCases(t, 'synthetic')],
  ['case create', t => api.createCase(t, {} as api.CreateCasePayload)],
  ['assignment write', t => api.updateCaseAssignment(t, 7, {} as api.UpdateCaseAssignmentPayload)],
  ['core write', t => api.updateCaseCoreDetails(t, 7, {} as api.UpdateCaseCoreDetailsPayload)],
  ['case detail', t => api.getCaseDetail(t, 7)],
  ['assigned cases', t => api.listAssignedCases(t)],
  ['assigned tasks', t => api.listAssignedTasks(t)],
  ['case tasks', t => api.listCaseTasks(t, 7)],
  ['task create', t => api.createCaseTask(t, 7, { title: 'Synthetic' })],
  ['update create', t => api.addCaseUpdate(t, 7, 'Synthetic')],
  ['updates read', t => api.listCaseUpdates(t, 7)],
  ['priorities', t => api.listTaskPriorityLookups(t)],
  ['task write', t => api.updateTaskDetail(t, 7, { title: 'Synthetic' })],
  ['completion write', t => api.completeTask(t, 7)],
  ['task detail', t => api.getTaskDetail(t, 7)],
  ['contact search', t => api.searchContacts(t, 'synthetic')],
  ['contact create', t => api.createContact(t, {} as api.CreateContactRequest)],
  ['contact write', t => api.updateContactDetails(t, 7, {} as api.UpdateContactDetailsRequest)],
  ['contact detail', t => api.getContactDetail(t, 7)],
  ['organization create', t => api.createOrganization(t, { name: 'Synthetic' })],
  ['organization search', t => api.searchOrganizations(t, 'synthetic')],
  ['organization write', t => api.updateOrganizationDetails(t, 7, {} as api.UpdateOrganizationDetailsRequest)],
  ['organization detail', t => api.getOrganizationDetail(t, 7)],
  ['status lookup', t => api.listCaseStatusLookup(t)],
  ['status write', t => api.updateCaseStatus(t, 7, 1)],
  ['team read', t => api.listTeamMembers(t)],
  ['team detail', t => api.getTeamMemberDetail(t, 7)],
  ['practice areas', t => api.listPracticeAreaLookups(t)],
  ['admin statuses', t => api.listCaseStatusSettings(t)],
  ['admin areas', t => api.listPracticeAreaSettings(t)],
];

describe('real feature consumers and current-session rejection', () => {
  it.each(consumers)('%s rejects the current session without replay or remote logout', async (_name, invoke) => {
    const { result } = await established();
    fetchMock.mockResolvedValue(response({ message: 'Sensitive detail must not enter feedback' }, 401));
    await act(async () => { await expect(invoke('synthetic-established')).rejects.toBeInstanceOf(SessionRequestDiscarded); });
    expect(result.current.authState).toEqual({ accessToken: null, user: null, verification: null });
    expect(result.current.sessionEnded).toBe(true); expect(result.current.logoutFeedback).toBeNull();
    expect(api.readAccessToken()).toBeNull(); expect(fetchMock).toHaveBeenCalledTimes(1);
    await expect(invoke('synthetic-established')).rejects.toBeInstanceOf(SessionRequestDiscarded);
    expect(fetchMock).toHaveBeenCalledTimes(1); // Synchronous prevention after teardown.
  });
  it.each([400, 403, 404, 409, 429, 500, 503])('read and write HTTP %s retain valid auth and ApiError status', async status => {
    const { result } = await established();
    fetchMock.mockImplementation(async () => response({}, status));
    await expect(api.getCaseDetail('synthetic-established', 7)).rejects.toMatchObject({ name: 'ApiError', status });
    await expect(api.updateCaseCoreDetails('synthetic-established', 7, {} as api.UpdateCaseCoreDetailsPayload)).rejects.toMatchObject({ name: 'ApiError', status });
    expect(result.current.authState.user).toEqual(user); expect(result.current.sessionEnded).toBe(false);
    expect(api.readAccessToken()).toBe('synthetic-established'); expect(fetchMock).toHaveBeenCalledTimes(2);
  });
  it('retains network failures and authoritative field errors in their feature paths', async () => {
    const { result } = await established();
    fetchMock.mockRejectedValueOnce(new TypeError('Synthetic offline'));
    await expect(api.completeTask('synthetic-established', 7)).rejects.toThrow('Synthetic offline');
    const fieldErrors = [{ field: 'phone', code: 'invalid_phone', message: 'Check phone.' }];
    fetchMock.mockResolvedValue(response({ fieldErrors }, 400));
    await expect(api.createContact('synthetic-established', {} as api.CreateContactRequest)).rejects.toMatchObject({ status: 400, fieldErrors });
    expect(result.current.authState.user).toEqual(user); expect(result.current.sessionEnded).toBe(false);
  });
  it('consumes simultaneous notifications once, including mixed reads and writes', async () => {
    const onRejected = vi.fn();
    const dispose = bindSessionRequests('synthetic-concurrent', () => true, onRejected);
    const a = deferred<Response>(), b = deferred<Response>();
    fetchMock.mockReturnValueOnce(a.promise).mockReturnValueOnce(b.promise);
    const read = api.listAssignedCases('synthetic-concurrent'), write = api.completeTask('synthetic-concurrent', 7);
    const outcomes = Promise.allSettled([read, write]);
    a.resolve(response({}, 401)); b.resolve(response({}, 401)); await outcomes;
    expect(onRejected).toHaveBeenCalledTimes(1); expect(fetchMock).toHaveBeenCalledTimes(2); dispose();
  });
});

describe('generation, credential and body settlement races', () => {
  it.each(['different', 'reused'])('old 401 cannot invalidate a newer %s credential login', async credential => {
    const { result } = await established(); const held = deferred<Response>();
    fetchMock.mockReturnValue(held.promise);
    const old = api.getCaseDetail('synthetic-established', 7);
    const assertion = expect(old).rejects.toBeInstanceOf(SessionRequestDiscarded);
    const token = credential === 'reused' ? 'synthetic-established' : 'synthetic-new';
    act(() => result.current.signIn(token, { ...user, userId: 2 }));
    await assertion;
    await act(async () => { held.resolve(response({}, 401)); });
    expect(result.current.authState.user?.userId).toBe(2); expect(api.readAccessToken()).toBe(token);
    expect(result.current.sessionEnded).toBe(false); expect(fetchMock).toHaveBeenCalledTimes(1);
  });
  it.each(['logout', 'unmount', 'storage replacement'])('late 401 after %s has no rejection notification', async end => {
    const hook = await established(); const held = deferred<Response>();
    fetchMock.mockReturnValueOnce(held.promise).mockResolvedValue(response({ revoked: true }));
    const old = api.getCaseDetail('synthetic-established', 7);
    const assertion = expect(old).rejects.toBeInstanceOf(SessionRequestDiscarded);
    if (end === 'logout') act(() => { hook.result.current.logoutSession(); });
    else if (end === 'unmount') hook.unmount();
    else api.storeAccessToken('synthetic-external-replacement');
    await act(async () => { held.resolve(response({}, 401)); await assertion; });
    expect(hook.result.current.sessionEnded).toBe(false);
    if (end === 'logout') expect(hook.result.current.logoutFeedback).toBe('confirmed');
    if (end === 'storage replacement') expect(api.readAccessToken()).toBe('synthetic-external-replacement');
  });
  it.each(['headers', 'body'])('late successful %s cannot deliver protected results after teardown', async phase => {
    const { result } = await established(); const headers = deferred<Response>(), body = deferred<unknown>();
    fetchMock.mockReturnValueOnce(phase === 'headers' ? headers.promise
      : Promise.resolve({ ok: true, status: 200, json: () => body.promise } as Response));
    const delivered = vi.fn(); const read = api.getContactDetail('synthetic-established', 7).then(delivered);
    const assertion = expect(read).rejects.toBeInstanceOf(SessionRequestDiscarded);
    await act(async () => {}); // body reading has begun
    act(() => result.current.signOut()); await assertion;
    await act(async () => { headers.resolve(response({ id: 7 })); body.resolve({ id: 7 }); });
    expect(delivered).not.toHaveBeenCalled(); expect(result.current.authState.user).toBeNull();
  });
  it('a late successful write cannot navigate through its completion callback after replacement', async () => {
    const { result } = await established(); const held = deferred<Response>(); fetchMock.mockReturnValue(held.promise);
    const navigate = vi.fn();
    const write = api.createOrganization('synthetic-established', { name: 'Synthetic' }).then(navigate);
    const assertion = expect(write).rejects.toBeInstanceOf(SessionRequestDiscarded);
    act(() => result.current.signIn('synthetic-new', { ...user, userId: 2 })); await assertion;
    await act(async () => { held.resolve(response({ id: 9 })); });
    expect(navigate).not.toHaveBeenCalled(); expect(result.current.authState.user?.userId).toBe(2);
  });
});
