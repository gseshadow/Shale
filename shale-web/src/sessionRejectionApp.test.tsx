import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import App, { createAppRouter } from './App';
const routers: ReturnType<typeof createAppRouter>[] = [];
function testRouter() { const router = createAppRouter(); routers.push(router); return router; }
import * as api from './api';
import { readAccessToken, storeAccessToken } from './api';

const user = { authenticated: true, userId: 1, shaleClientId: 1, email: null, displayName: 'Synthetic User',
  nameFirst: null, nameLast: null, isAdmin: false, isAttorney: false, initials: null, color: null };
const response = (body: unknown = {}, status = 200) => new Response(JSON.stringify(body), { status });
function deferred() {
  let resolve!: (value: Response) => void;
  const promise = new Promise<Response>(done => { resolve = done; });
  return { promise, resolve };
}
const fetchMock = vi.fn<typeof fetch>();
let calls: { path: string; method: string }[];
beforeEach(() => {
  sessionStorage.clear(); calls = []; fetchMock.mockReset(); vi.stubGlobal('fetch', fetchMock);
  vi.stubGlobal('matchMedia', vi.fn(() => ({ matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn() })));
});
afterEach(() => { cleanup(); routers.splice(0).forEach(router => router.dispose()); sessionStorage.clear(); vi.restoreAllMocks(); vi.unstubAllGlobals(); });
function fixture(handler: (path: string, method: string) => Response | Promise<Response>) {
  fetchMock.mockImplementation(async (input, init) => {
    const path = new URL(String(input)).pathname, method = init?.method ?? 'GET';
    calls.push({ path, method });
    if (path === '/api/auth/me') return response(user);
    if (path === '/api/auth/login') return response({ accessToken: 'synthetic-new', user });
    if (path === '/api/auth/logout') return response({ revoked: true });
    return handler(path, method);
  });
}
function open(path: string) { storeAccessToken('synthetic-established'); window.history.replaceState({}, '', path); render(<App router={testRouter()} />); }
async function signIn() {
  fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'synthetic@example.invalid' } });
  fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'synthetic-password' } });
  fireEvent.click(screen.getByRole('button', { name: 'Sign in' }));
}

describe('established-session recovery with actual endpoint clients', () => {
  it('coalesces current read rejections, replaces login history, restores safe suffixes and consumes return state', async () => {
    const first = deferred(), second = deferred(); let rejected = false;
    fixture(path => {
      if (path === '/api/cases/7') return rejected ? response({ caseId: 7, caseName: 'Synthetic Case', relatedContacts: [], statusHistory: [], mappedCaseDates: [] }) : first.promise;
      if (path === '/api/cases/7/tasks') return rejected ? response([]) : second.promise;
      if (path === '/api/cases/7/updates') return response([]);
      throw new Error(`Unexpected fixture endpoint ${path}`);
    });
    open('/cases/7?page=2#details'); await screen.findByRole('heading', { name: 'Case Detail' });
    await waitFor(() => expect(calls.filter(c => c.path.startsWith('/api/cases/7'))).toHaveLength(3));
    const length = window.history.length;
    await act(async () => { first.resolve(response({}, 401)); second.resolve(response({}, 401)); });
    await screen.findByRole('heading', { name: 'Sign in' });
    expect(document.querySelector('.shale-authenticated')).toBeNull(); expect(readAccessToken()).toBeNull();
    expect(screen.getAllByRole('alert')).toHaveLength(1); expect(screen.getByRole('alert').textContent).toContain('session ended');
    expect(document.activeElement).toBe(screen.getByRole('heading', { name: 'Sign in' }));
    expect(window.location.pathname).toBe('/login'); expect(window.history.length).toBe(length);
    expect(calls.some(c => c.path === '/api/auth/logout')).toBe(false);
    rejected = true; await signIn();
    await screen.findByRole('heading', { name: 'Synthetic Case' });
    expect(window.location.pathname + window.location.search + window.location.hash).toBe('/cases/7?page=2#details');
    expect(window.history.length).toBe(length); expect(window.history.state.usr).toBeNull();
    expect(calls.filter(c => c.path === '/api/cases/7')).toHaveLength(2); // New mount, no old-request replay.
    expect(screen.queryByText(/Your Shale session ended/)).toBeNull();
  });
  it('a rejected completion requires sign-in without replaying the mutation', async () => {
    fixture((path, method) => {
      if (path === '/api/cases/assigned') return response([]);
      if (path === '/api/tasks/assigned') return response([{ id: 12, caseId: 7, title: 'Synthetic Task', completedAt: null }]);
      if (path === '/api/tasks/12/complete' && method === 'PATCH') return response({}, 401);
      throw new Error(`Unexpected fixture endpoint ${path}`);
    });
    open('/my-shale'); fireEvent.click(await screen.findByRole('button', { name: 'Complete' }));
    await screen.findByRole('heading', { name: 'Sign in' });
    expect(screen.getByRole('alert').textContent).toContain('may have been saved');
    await signIn(); await screen.findByRole('button', { name: 'Complete' });
    expect(calls.filter(c => c.path.endsWith('/complete'))).toHaveLength(1);
    expect(calls.filter(c => c.path === '/api/tasks/assigned')).toHaveLength(2);
  });
  it.each(['rejection', 'logout'])('late creation success after %s cannot navigate or restore old protected UI', async end => {
    const create = deferred(), validation = deferred();
    fixture((path, method) => {
      if (path === '/api/contacts' && method === 'POST') return create.promise;
      if (path === '/api/validation/contact-value') return validation.promise;
      if (path === '/api/cases/assigned' || path === '/api/tasks/assigned') return response([]);
      throw new Error(`Unexpected fixture endpoint ${path}`);
    });
    open('/contacts'); await screen.findByRole('heading', { name: 'Contacts', level: 1 });
    fireEvent.click(screen.getByRole('button', { name: 'New contact' }));
    fireEvent.change(screen.getByLabelText('Display name'), { target: { value: 'Synthetic draft' } });
    fireEvent.blur(screen.getByLabelText('Email'));
    fireEvent.submit(screen.getByRole('form', { name: 'Create contact' }));
    await waitFor(() => expect(calls.some(c => c.path === '/api/contacts')).toBe(true));
    if (end === 'logout') fireEvent.click(screen.getByRole('button', { name: 'Logout' }));
    else await act(async () => { validation.resolve(response({}, 401)); });
    await screen.findByRole('heading', { name: 'Sign in' });
    await signIn(); await screen.findByRole('heading', { name: end === 'logout' ? 'My Shale' : 'Contacts', level: 1 });
    await act(async () => { create.resolve(response({ id: 99, displayName: 'Old Synthetic Contact' })); validation.resolve(response({ preview: 'Old' })); });
    expect(window.location.pathname).toBe(end === 'logout' ? '/my-shale' : '/contacts');
    expect(screen.queryByText('Old Synthetic Contact')).toBeNull(); expect(screen.queryByDisplayValue('Synthetic draft')).toBeNull();
    expect(calls.filter(c => c.path === '/api/contacts')).toHaveLength(1);
    expect(calls.some(c => c.path === '/api/contacts/99')).toBe(false); expect(readAccessToken()).toBe('synthetic-new');
  });
  it('the creation consumer discards an already-delivered API result queued across teardown', async () => {
    const validation = deferred(); let finish!: (value: api.ContactDetail) => void;
    // Bypass the transport's guard to isolate the consumer continuation boundary.
    vi.spyOn(api, 'createContact').mockReturnValue(new Promise(done => { finish = done; }));
    fixture(path => {
      if (path === '/api/validation/contact-value') return validation.promise;
      throw new Error(`Unexpected fixture endpoint ${path}`);
    });
    open('/contacts'); await screen.findByRole('heading', { name: 'Contacts', level: 1 });
    fireEvent.click(screen.getByRole('button', { name: 'New contact' }));
    fireEvent.change(screen.getByLabelText('Display name'), { target: { value: 'Synthetic draft' } });
    fireEvent.blur(screen.getByLabelText('Email')); fireEvent.submit(screen.getByRole('form', { name: 'Create contact' }));
    await act(async () => { validation.resolve(response({}, 401)); });
    await screen.findByRole('heading', { name: 'Sign in' }); await signIn();
    await screen.findByRole('heading', { name: 'Contacts', level: 1 });
    await act(async () => { finish({ id: 99, displayName: 'Old' } as api.ContactDetail); });
    expect(window.location.pathname).toBe('/contacts'); expect(readAccessToken()).toBe('synthetic-new');
    expect(screen.queryByText('Old')).toBeNull(); expect(calls.some(c => c.path === '/api/contacts/99')).toBe(false);
  });
  it.each(['success', 'failure'])('an unmounted login cannot overwrite or clear a replacement on late %s', async outcome => {
    const old = deferred(); let loginStarted = false;
    fetchMock.mockImplementation(async (input, init) => {
      const path = new URL(String(input)).pathname;
      calls.push({ path, method: init?.method ?? 'GET' });
      if (path === '/api/auth/login') { loginStarted = true; return old.promise; }
      if (path === '/api/auth/me') return response(user);
      if (path === '/api/cases/assigned' || path === '/api/tasks/assigned') return response([]);
      throw new Error(`Unexpected fixture endpoint ${path}`);
    });
    window.history.replaceState({}, '', '/login');
    const oldRouter = testRouter(), oldApp = render(<App router={oldRouter} />);
    await screen.findByRole('heading', { name: 'Sign in' }); await signIn();
    expect(loginStarted).toBe(true); oldApp.unmount(); oldRouter.dispose();
    open('/my-shale'); await screen.findByRole('heading', { name: 'My Shale', level: 1 });
    // Heading render does not prove the new screen's normal mount effects ran.
    // Settle those expected reads before asserting that the old login adds none.
    await waitFor(() => {
      expect(calls.filter(c => c.path === '/api/cases/assigned')).toHaveLength(1);
      expect(calls.filter(c => c.path === '/api/tasks/assigned')).toHaveLength(1);
    });
    const count = calls.length;
    await act(async () => { old.resolve(response({ accessToken: 'synthetic-old' }, outcome === 'success' ? 200 : 401)); });
    expect(readAccessToken()).toBe('synthetic-established'); expect(calls).toHaveLength(count);
    expect(screen.getByRole('heading', { name: 'My Shale', level: 1 })).toBeTruthy();
  });
  it.each([403, 409, 503, 'network'])('completion %s stays in feature feedback and leaves session valid', async status => {
    fixture(path => {
      if (path === '/api/cases/assigned') return response([]);
      if (path === '/api/tasks/assigned') return response([{ id: 12, caseId: 7, title: 'Synthetic Task', completedAt: null }]);
      if (path === '/api/tasks/12/complete') {
        if (status === 'network') throw new TypeError('Synthetic offline');
        return response({}, status as number);
      }
      throw new Error(`Unexpected fixture endpoint ${path}`);
    });
    open('/my-shale'); fireEvent.click(await screen.findByRole('button', { name: 'Complete' }));
    await screen.findByRole('alert'); expect(screen.getByRole('heading', { name: 'My Shale', level: 1 })).toBeTruthy();
    expect(readAccessToken()).toBe('synthetic-established'); expect(screen.queryByText(/Your Shale session ended/)).toBeNull();
    expect(calls.filter(c => c.path.endsWith('/complete'))).toHaveLength(1);
  });
});
