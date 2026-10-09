import { act, cleanup, configure, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createMemoryRouter } from 'react-router-dom';
import App, { appRouteObjects } from './App';
import * as api from './api';
import type { useStartupSession } from './useStartupSession';

configure({ asyncUtilTimeout: 5000 });
vi.setConfig({ testTimeout: 15000 });

let session: ReturnType<typeof useStartupSession>;
vi.mock('./useStartupSession', async original => {
  const actual = await original<typeof import('./useStartupSession')>();
  return { ...actual, useStartupSession: () => { session = actual.useStartupSession(); return session; } };
});
const user: api.AuthenticatedUser = { authenticated: true, userId: 1, shaleClientId: 1, displayName: 'Synthetic User',
  email: null, nameFirst: null, nameLast: null, isAdmin: false, isAttorney: false, initials: null, color: null };
const detail: api.OrganizationDetail = { id: 7, shaleClientId: 1, rowVer: 'AAAAAAAAAAE=',
  organizationTypeId: null, organizationTypeName: null, name: 'Synthetic Organization',
  email: 'saved@example.invalid', phone: 'legacy phone', phoneExtension: '12', fax: 'legacy fax', faxExtension: '34',
  website: 'https://example.invalid', address1: 'Address', address2: 'Suite', city: 'City', state: 'State',
  postalCode: '12345', country: 'US', notes: 'Original notes', relatedCases: [{ id: 99, name: 'Related Case', intakeDate: null, statuteOfLimitationsDate: null, responsibleAttorneyName: null, partyRoleName: null, side: null, primary: false, notes: null }] };
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status });
let router: ReturnType<typeof createMemoryRouter>;
let save: () => Promise<Response>;
let advisory: () => Promise<Response>;
let me: () => Promise<Response>;
let requests: { path: string; method: string; body: unknown }[];
function deferred() {
  let resolve!: (value: Response) => void;
  const promise = new Promise<Response>(done => { resolve = done; });
  return { promise, resolve };
}
beforeEach(() => {
  sessionStorage.clear(); requests = [];
  save = async () => json({ ...detail, notes: 'Changed notes', rowVer: 'AAAAAAAAAAI=' });
  advisory = async () => json({}); me = async () => json(user);
  vi.stubGlobal('matchMedia', () => ({ matches: true, addEventListener() {}, removeEventListener() {} }));
  // jsdom lacks the native top-layer/focus implementation; real browser fixtures cover it.
  Object.defineProperty(HTMLDialogElement.prototype, 'showModal', { configurable: true, value: function (this: HTMLDialogElement) { this.open = true; } });
  Object.defineProperty(HTMLDialogElement.prototype, 'close', { configurable: true, value: function (this: HTMLDialogElement) { this.open = false; } });
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const path = new URL(String(input)).pathname, method = init?.method ?? 'GET';
    requests.push({ path, method, body: init?.body ? JSON.parse(String(init.body)) : null });
    if (path === '/api/auth/me') return me();
    if (path === '/api/auth/logout') return json({ revoked: true });
    if (path === '/api/organizations/7') return json(detail);
    if (path === '/api/organizations/8') return json({ ...detail, id: 8, name: 'Second Organization' });
    if (path === '/api/validation/contact-value') return advisory();
    if (path === '/api/v2/organizations/7') return save();
    if (path === '/api/tasks/assigned' || path === '/api/cases/assigned') return json([]);
    throw new Error(`Unexpected synthetic request: ${path}`);
  }));
});
afterEach(() => { cleanup(); router?.dispose(); sessionStorage.clear(); vi.restoreAllMocks(); vi.unstubAllGlobals(); });
async function open() {
  api.storeAccessToken('synthetic-established');
  router = createMemoryRouter(appRouteObjects, { initialEntries: ['/cases', '/organizations/7?sort=name#profile', '/tasks'], initialIndex: 1 });
  render(<App router={router} />);
  fireEvent.click(await screen.findByRole('button', { name: 'Edit organization' }));
  await screen.findByRole('form', { name: 'Edit organization' });
}
function change(label = 'Notes', value = 'Changed notes') { fireEvent.change(label.endsWith('-extension') ? document.getElementById(label)! : screen.getByLabelText(label), { target: { value } }); }
async function navigate(to: string | number = '/tasks') { await act(async () => { if (typeof to === 'number') await router.navigate(to); else await router.navigate(to); }); }
function unload() { const event = new Event('beforeunload', { cancelable: true }); window.dispatchEvent(event); return event.defaultPrevented; }
async function prompt() { return screen.findByRole('dialog', { name: 'Discard organization changes?' }); }
function keep() { fireEvent.click(screen.getByRole('button', { name: 'Keep editing' })); }
function discard() { fireEvent.click(screen.getByRole('button', { name: 'Discard changes' })); }
function submit() { fireEvent.submit(screen.getByRole('form', { name: 'Edit organization' })); }

describe('bounded Organization Detail draft protection with real API clients', () => {
  it('clean forms and normalized scalar reversions navigate normally without unload protection', async () => {
    await open(); expect(unload()).toBe(false);
    change('Notes', 'Changed notes'); expect(unload()).toBe(true);
    change('Notes', '  Original notes  '); expect(unload()).toBe(false);
    await navigate(); expect(await screen.findByRole('heading', { name: 'Tasks', level: 1 })).toBeTruthy();
    expect(screen.queryByRole('dialog')).toBeNull();
  });
  it.each([
    ['Organization name', 'Changed', 'Synthetic Organization'], ['Website', 'Changed', 'https://example.invalid'],
    ['Address line 1', 'Changed', 'Address'], ['Address line 2', 'Changed', 'Suite'], ['City', 'Changed', 'City'],
    ['State', 'Changed', 'State'], ['Zip', 'Changed', '12345'], ['Country', 'Changed', 'US'], ['Notes', 'Changed', 'Original notes'],
    ['Email', '', 'saved@example.invalid'], ['Email', 'new@example.invalid', 'saved@example.invalid'],
    ['Phone', '', 'legacy phone'], ['Phone', '5551234', 'legacy phone'],
    ['Fax', '', 'legacy fax'], ['Fax', '5551234', 'legacy fax'],
    ['organization-phone-extension', '13', '12'], ['organization-fax-extension', '35', '34'],
  ])('%s edits and exact reversions respect the loaded baseline', async (label, value, baseline) => {
    await open(); change(label, value); expect(unload()).toBe(true);
    change(label, baseline); expect(unload()).toBe(false);
    await navigate(); expect(screen.queryByRole('dialog')).toBeNull();
  });
  it('advisory formatting back to the baseline releases an obsolete prompt without replay', async () => {
    await open(); const validation = deferred(); advisory = () => validation.promise;
    change('Phone', '5551234'); fireEvent.blur(screen.getByLabelText('Phone')); await navigate(); await prompt();
    await act(async () => validation.resolve(json({ displayInput: detail.phone, extension: detail.phoneExtension })));
    expect(screen.queryByRole('dialog')).toBeNull(); expect(unload()).toBe(false);
    expect(router.state.location.pathname).toBe('/organizations/7'); await navigate(); await screen.findByRole('heading', { name: 'Tasks' });
  });
  it('shell links block, Keep editing preserves values/focus, and Discard proceeds once', async () => {
    await open(); change(); const link = screen.getByRole('link', { name: 'My Tasks' }); link.focus(); fireEvent.click(link);
    await prompt(); expect(router.state.location.pathname).toBe('/organizations/7');
    expect(document.activeElement).toBe(screen.getByRole('button', { name: 'Keep editing' }));
    keep(); expect(screen.queryByRole('dialog')).toBeNull(); expect(document.activeElement).toBe(link);
    expect((screen.getByLabelText('Notes') as HTMLTextAreaElement).value).toBe('Changed notes');
    fireEvent.click(link); await prompt(); discard();
    await screen.findByRole('heading', { name: 'Tasks', level: 1 });
    expect(unload()).toBe(false); expect(requests.filter(r => r.method === 'PATCH')).toHaveLength(0);
    await navigate(-1); await screen.findByRole('button', { name: 'Edit organization' });
    expect(screen.queryByRole('form', { name: 'Edit organization' })).toBeNull(); expect(screen.queryByRole('dialog')).toBeNull();
  });
  it('repeated blocked attempts show one prompt and discarded unknown-route navigation cannot redirect-loop', async () => {
    await open(); change(); await navigate('/tasks'); await prompt(); await navigate('/unknown');
    expect(screen.getAllByRole('dialog')).toHaveLength(1); discard();
    await screen.findByRole('heading', { name: 'My Shale', level: 1 });
    expect(screen.queryByRole('dialog')).toBeNull(); expect(unload()).toBe(false);
  });
  it.each([-1, 1])('Back/Forward (%s) preserves suffixes on Stay and proceeds on Discard', async delta => {
    await open(); change(); await navigate(delta); await prompt(); keep();
    expect(router.state.location.pathname + router.state.location.search + router.state.location.hash).toBe('/organizations/7?sort=name#profile');
    await navigate(delta); await prompt(); discard();
    await waitFor(() => expect(router.state.location.pathname).toBe(delta < 0 ? '/cases' : '/tasks'));
    expect(screen.queryByRole('dialog')).toBeNull();
  });
  it('Cancel closes a clean form, but requires deliberate discard for dirty values; Escape keeps them', async () => {
    await open(); fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(screen.queryByRole('form', { name: 'Edit organization' })).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Edit organization' })); change();
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' })); const dialog = await prompt();
    fireEvent(dialog, new Event('cancel', { cancelable: true }));
    expect(screen.queryByRole('dialog')).toBeNull(); expect(unload()).toBe(true);
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' })); await prompt(); discard();
    expect(screen.queryByRole('form', { name: 'Edit organization' })).toBeNull(); expect(unload()).toBe(false);
    expect(document.activeElement).toBe(screen.getByRole('button', { name: 'Edit organization' }));
    fireEvent.click(screen.getByRole('button', { name: 'Edit organization' }));
    expect((screen.getByLabelText('Notes') as HTMLTextAreaElement).value).toBe('Original notes');
  });
  it('Discard to another Organization location cannot retain the old editor, including query/hash only changes', async () => {
    await open(); change(); await navigate('/organizations/7?sort=other#new'); await prompt(); discard();
    await screen.findByRole('button', { name: 'Edit organization' }); expect(screen.queryByRole('form', { name: 'Edit organization' })).toBeNull();
    expect(router.state.location.search + router.state.location.hash).toBe('?sort=other#new');
  });
  it.each([400, 409, 503, 'network'])('save failure %s retains dirty values and never automatically replays', async status => {
    await open(); save = async () => { if (status === 'network') throw new TypeError('Synthetic network failure');
      return json({ fieldErrors: [{ field: 'email', code: 'invalid_email', message: 'Check email.' }] }, Number(status)); };
    change(); submit(); await screen.findByRole('alert');
    expect((screen.getByLabelText('Notes') as HTMLTextAreaElement).value).toBe('Changed notes'); expect(unload()).toBe(true);
    await navigate(); await prompt(); keep(); expect(requests.filter(r => r.method === 'PATCH')).toHaveLength(1);
  });
  it.each([null, {}, { ...detail, id: 8 }, { ...detail, shaleClientId: 2 }, { ...detail, rowVer: null },
    { ...detail, rowVer: '' }, { ...detail, rowVer: ' ' + detail.rowVer }, { ...detail, rowVer: 'not-base64!' }, { ...detail, name: null },
    { ...detail, website: undefined }, { ...detail, faxExtension: 42 }, { ...detail, relatedCases: null }, { ...detail, relatedCases: [{}] }])('unusable successful save body %# preserves the draft without optimistic confirmation', async body => {
    await open(); save = async () => json(body); change(); submit(); await screen.findByRole('alert');
    expect((screen.getByLabelText('Notes') as HTMLTextAreaElement).value).toBe('Changed notes'); expect(unload()).toBe(true);
    expect(screen.queryByRole('button', { name: 'Edit organization' })).toBeNull();
    expect(requests.filter(r => r.method === 'PATCH')).toHaveLength(1);
  });
  it('a late advisory after failed Save cannot repaint the retained draft', async () => {
    await open(); const validation = deferred(); advisory = () => validation.promise; save = async () => json({}, 409);
    change('Phone', '5551234'); fireEvent.blur(screen.getByLabelText('Phone')); submit(); await screen.findByRole('alert');
    await act(async () => validation.resolve(json({ displayInput: '555-1234' })));
    expect((screen.getByLabelText('Phone') as HTMLInputElement).value).toBe('5551234'); expect(unload()).toBe(true);
  });
  it('authoritative save success closes the editor and establishes the next opening baseline', async () => {
    await open(); change(); submit(); await screen.findByRole('button', { name: 'Edit organization' }); expect(unload()).toBe(false);
    fireEvent.click(screen.getByRole('button', { name: 'Edit organization' }));
    expect((screen.getByLabelText('Notes') as HTMLTextAreaElement).value).toBe('Changed notes'); expect(unload()).toBe(false);
    change('Notes', 'Original notes'); expect(unload()).toBe(true);
    submit(); await screen.findByRole('button', { name: 'Edit organization' });
    expect(requests.filter(r => r.method === 'PATCH')[1].body).toMatchObject({ rowVer: 'AAAAAAAAAAI=' });
    expect(requests.find(r => r.method === 'PATCH')?.body).toMatchObject({ rowVer: detail.rowVer, phone: { action: 'RETAIN' }, fax: { action: 'RETAIN' }, email: { action: 'RETAIN' } });
  });
  it.each(['success', 'failure'])('pending save %s after Stay respects the draft and clears any blocker on known success', async outcome => {
    await open(); const pending = deferred(); save = () => pending.promise; change(); submit(); submit();
    await navigate(); await prompt(); expect(screen.getByText(/may already have committed/)).toBeTruthy(); keep();
    await act(async () => { pending.resolve(outcome === 'success' ? json({ ...detail, notes: 'Changed notes' }) : json({}, 409)); });
    if (outcome === 'success') { await screen.findByRole('button', { name: 'Edit organization' }); expect(unload()).toBe(false); }
    else { await screen.findByRole('alert'); expect(unload()).toBe(true); }
    expect(router.state.location.pathname).toBe('/organizations/7'); expect(requests.filter(r => r.method === 'PATCH')).toHaveLength(1);
  });
  it('success while a navigation prompt is open closes it without replaying the abandoned navigation', async () => {
    await open(); const pending = deferred(); save = () => pending.promise; change(); submit(); await navigate(); await prompt();
    await act(async () => pending.resolve(json({ ...detail, notes: 'Changed notes' })));
    await screen.findByRole('button', { name: 'Edit organization' }); expect(screen.queryByRole('dialog')).toBeNull();
    expect(router.state.location.pathname).toBe('/organizations/7'); await navigate(); await screen.findByRole('heading', { name: 'Tasks' });
  });
  it.each(['navigation', 'cancel'])('discard during pending save via %s ignores late success and advisory formatting', async action => {
    await open(); const pending = deferred(), validation = deferred(); save = () => pending.promise; advisory = () => validation.promise;
    change('Phone', '5551234'); fireEvent.blur(screen.getByLabelText('Phone')); submit();
    if (action === 'navigation') await navigate(); else fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    await prompt(); discard();
    if (action === 'navigation') await screen.findByRole('heading', { name: 'Tasks' }); else await screen.findByRole('button', { name: 'Edit organization' });
    await act(async () => { pending.resolve(json({ ...detail, name: 'Late saved name' })); validation.resolve(json({ displayInput: 'Late formatted phone' })); });
    expect(screen.queryByText('Late saved name')).toBeNull(); expect(screen.queryByDisplayValue('Late formatted phone')).toBeNull();
    expect(unload()).toBe(false); expect(requests.filter(r => r.method === 'PATCH')).toHaveLength(1);
  });
  it.each(['logout', 'rejection', 'replacement', 'same-credential replacement', 'local-return', 'failed-clear logout', 'failed-clear rejection', 'failed-clear local-return', 'failed-store replacement'])('%s bypasses an active blocker and clears the editor and unload handler', async end => {
    await open(); const pending = deferred(); save = () => pending.promise; change(); submit(); await navigate(); await prompt();
    if (end.startsWith('failed-clear')) vi.spyOn(Storage.prototype, 'removeItem').mockImplementation(() => { throw new Error('Synthetic denied'); });
    if (end === 'failed-store replacement') {
      vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('Synthetic denied'); });
      act(() => { expect(() => session.signIn('synthetic-replacement', { ...user, userId: 2 })).toThrow('could not store'); });
    }
    if (end === 'logout' || end === 'failed-clear logout') fireEvent.click(screen.getByRole('button', { name: 'Logout' }));
    if (end === 'rejection' || end === 'failed-clear rejection') await act(async () => pending.resolve(json({}, 401)));
    if (end === 'same-credential replacement') await act(async () => session.signIn('synthetic-established', { ...user, userId: 2 }));
    if (end === 'replacement') await act(async () => session.signIn('synthetic-replacement', { ...user, userId: 2, shaleClientId: 2 }));
    if (end === 'local-return' || end === 'failed-clear local-return') {
      me = () => new Promise(() => {});
      act(() => { void session.retry(); });
      fireEvent.click(await screen.findByRole('button', { name: 'Return to sign in' }));
    }
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
    expect(screen.queryByRole('form', { name: 'Edit organization' })).toBeNull(); expect(unload()).toBe(false);
    await act(async () => pending.resolve(json({ ...detail, name: 'Old late save' })));
    expect(screen.queryByText('Old late save')).toBeNull(); expect(requests.filter(r => r.method === 'PATCH')).toHaveLength(1);
    if (end === 'replacement' || end === 'same-credential replacement') { expect(api.readAccessToken()).toBe(end === 'replacement' ? 'synthetic-replacement' : 'synthetic-established'); await screen.findByRole('button', { name: 'Edit organization' }); }
    else { await screen.findByRole('heading', { name: 'Sign in' }); expect(api.readAccessToken()).toBeNull(); }
  });
  it('beforeunload listeners are registered only while dirty/pending and removed on clean reversion/unmount', async () => {
    const add = vi.spyOn(window, 'addEventListener'), remove = vi.spyOn(window, 'removeEventListener');
    await open(); expect(add.mock.calls.some(([name]) => name === 'beforeunload')).toBe(false);
    change(); expect(add.mock.calls.some(([name]) => name === 'beforeunload')).toBe(true);
    change('Notes', 'Original notes'); expect(unload()).toBe(false);
    expect(remove.mock.calls.some(([name]) => name === 'beforeunload')).toBe(true);
    const pending = deferred(); save = () => pending.promise; submit(); expect(unload()).toBe(true);
    cleanup(); expect(unload()).toBe(false);
  });
  it.each(['phone', 'fax', 'email'])('preserves RETAIN/SET/CLEAR wire semantics for %s', async field => {
    await open(); const label = field === 'phone' ? 'Phone' : field === 'fax' ? 'Fax' : 'Email';
    const extension = field === 'email' ? null : `organization-${field}-extension`;
    change(label, field === 'email' ? 'new@example.invalid' : '5551234');
    if (extension) change(extension, '001');
    save = async () => json({}, 409); submit(); await screen.findByRole('alert');
    expect(requests.filter(r => r.method === 'PATCH')[0].body).toMatchObject({
      rowVer: detail.rowVer, [field]: { action: 'SET', value: field === 'email' ? 'new@example.invalid' : '5551234', ...(extension ? { extension: '001' } : {}) },
    });
    change(label, ''); if (extension) change(extension, ''); submit();
    await waitFor(() => expect(requests.filter(r => r.method === 'PATCH')).toHaveLength(2));
    expect(requests.filter(r => r.method === 'PATCH')[1].body).toMatchObject({ rowVer: detail.rowVer, [field]: { action: 'CLEAR' } });
  });
  it('ordinary related-case and parent links reuse the same guard', async () => {
    await open(); change(); fireEvent.click(screen.getByRole('link', { name: 'Back to Organizations' }));
    await prompt(); keep(); expect(unload()).toBe(true);
    fireEvent.click(screen.getByRole('button', { name: 'Open related case Related Case' })); await prompt(); discard();
    await waitFor(() => expect(router.state.location.pathname).toBe('/cases/99'));
    expect(unload()).toBe(false);
  });
  it('discard to a different Organization prevents a late failure from touching its editor or focus', async () => {
    await open(); const pending = deferred(); save = () => pending.promise; change(); submit();
    await navigate('/organizations/8'); await prompt(); discard();
    fireEvent.click(await screen.findByRole('button', { name: 'Edit organization' }));
    const input = screen.getByLabelText('Organization name'); input.focus();
    await act(async () => pending.resolve(json({ fieldErrors: [{ field: 'email', code: 'invalid_email', message: 'Old failed email' }] }, 400)));
    expect((input as HTMLInputElement).value).toBe('Second Organization'); expect(document.activeElement).toBe(input);
    expect(screen.queryByRole('alert')).toBeNull(); expect(unload()).toBe(false);
  });
  it.each(['Phone', 'Fax', 'Email'])('typing invalidates stale %s advisory results without clearing the draft', async label => {
    await open(); const validation = deferred(); advisory = () => validation.promise;
    change(label, '5551234'); fireEvent.blur(screen.getByLabelText(label)); change(label, 'newer draft');
    await act(async () => validation.resolve(json({ displayInput: 'Old formatted value', preview: 'Old advisory' })));
    expect((screen.getByLabelText(label) as HTMLInputElement).value).toBe('newer draft');
    expect(screen.queryByText('Old advisory')).toBeNull(); expect(unload()).toBe(true);
  });
  it.each(['Fax', 'Email'])('late %s advisory after failed Save cannot repaint or dismiss authoritative errors', async label => {
    await open(); const validation = deferred(); advisory = () => validation.promise;
    const field = label.toLowerCase();
    save = async () => json({ fieldErrors: [{ field, code: 'invalid_' + field, message: 'Check ' + field }] }, 400);
    change(label, 'draft'); fireEvent.blur(screen.getByLabelText(label)); submit(); await screen.findByRole('alert');
    await act(async () => validation.resolve(json({ displayInput: 'Late value', preview: 'Late advisory' })));
    expect((screen.getByLabelText(label) as HTMLInputElement).value).toBe('draft');
    expect(screen.getByRole('alert').textContent).toContain('Check ' + field); expect(unload()).toBe(true);
  });

  it('Keep editing preserves every staged scalar, contact value and extension', async () => {
    await open();
    const values = [['Organization name', 'Draft name'], ['Phone', '5551234'], ['Fax', '5555678'],
      ['organization-phone-extension', '001'], ['organization-fax-extension', '002'], ['Email', 'draft@example.invalid'],
      ['Website', 'https://draft.invalid'], ['Address line 1', 'Draft address'], ['Address line 2', 'Draft suite'],
      ['City', 'Draft city'], ['State', 'Draft state'], ['Zip', '98765'], ['Country', 'CA'], ['Notes', 'Draft notes']];
    values.forEach(([label, value]) => change(label, value));
    await navigate(); await prompt(); keep();
    values.forEach(([label, value]) => expect((label.endsWith('-extension') ? document.getElementById(label) : screen.getByLabelText(label))?.getAttribute('value')
      ?? (screen.getByLabelText(label) as HTMLTextAreaElement).value).toBe(value));
    expect(requests.filter(r => r.method === 'PATCH')).toHaveLength(0); expect(unload()).toBe(true);
  });
  it('a failed clean pending Save releases an obsolete prompt without replaying navigation', async () => {
    await open(); const pending = deferred(); save = () => pending.promise; submit();
    expect(unload()).toBe(true); await navigate(); await prompt();
    await act(async () => pending.resolve(json({}, 409))); await screen.findByRole('alert');
    expect(screen.queryByRole('dialog')).toBeNull(); expect(unload()).toBe(false);
    expect(router.state.location.pathname).toBe('/organizations/7');
    await navigate(); await screen.findByRole('heading', { name: 'Tasks' });
  });
  it('local required-name validation keeps drafts and the opening version without sending a mutation', async () => {
    await open(); change('Organization name', ''); change('Email', 'draft@example.invalid'); submit();
    expect(screen.getByRole('alert').textContent).toContain('Enter an organization name');
    expect((screen.getByLabelText('Email') as HTMLInputElement).value).toBe('draft@example.invalid');
    expect(requests.filter(r => r.method === 'PATCH')).toHaveLength(0); expect(unload()).toBe(true);
  });
  it('Organization creation remains outside this bounded protection', async () => {
    api.storeAccessToken('synthetic-established');
    router = createMemoryRouter(appRouteObjects, { initialEntries: ['/organizations'] }); render(<App router={router} />);
    fireEvent.click(await screen.findByRole('button', { name: 'New organization' }));
    fireEvent.change(screen.getByLabelText('Organization name'), { target: { value: 'Unprotected create draft' } });
    expect(unload()).toBe(false); await navigate(); await screen.findByRole('heading', { name: 'Tasks' });
    expect(screen.queryByRole('dialog')).toBeNull(); expect(requests.filter(r => r.method === 'POST')).toHaveLength(0);
  });

});
