import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { OAuthConsent, callbackTarget, oauthRequest, oauthRequestDeadline } from './OAuthConsent';
import { bindSessionRequests } from './sessionRequests';
import { apiBaseUrl } from './api';
const user = { authenticated: true as const, userId: 72, shaleClientId: 7, email: 'synthetic@example.invalid', displayName: 'Synthetic User',
  nameFirst: null, nameLast: null, isAdmin: false, isAttorney: false, initials: null, color: null };
const request = new URLSearchParams({ client_id: 'hermes', redirect_uri: 'https://client.test/cb', state: 'exact + & = ÃƒÂ©' }).toString();
const details = () => ({ client_id: 'hermes', client_name: 'Hermes', scopes: ['cases.search', 'cases.overview.read'], state: 'server-nonce',
  tenant: 7, user: 72, intent_expires_at: new Date(Date.now()+300000).toISOString(), expires_at: new Date(Date.now()+3600000).toISOString(), notice: 'Case data may leave Shale.' });
const response = (body: unknown, status=200) => new Response(JSON.stringify(body), { status });
const fetchMock = vi.fn<typeof fetch>();
let end: () => void;
beforeEach(() => { fetchMock.mockReset(); vi.stubGlobal('fetch',fetchMock); end=bindSessionRequests('web-token',()=>true,()=>{}); });
afterEach(() => { cleanup();end();vi.useRealTimers();vi.unstubAllEnvs();vi.unstubAllGlobals(); });
function setup(overrides = {}) {
  fetchMock.mockResolvedValueOnce(response({redirect: `${apiBaseUrl()}/oauth/mcp/consent?client_id=hermes&state=server-nonce`}))
    .mockResolvedValueOnce(response({...details(),...overrides}));
  const completed = vi.fn();
  render(<OAuthConsent token="web-token" user={user} request={request} onComplete={completed} />);
  return completed;
}
it('shows integration, permissions and actor, and never approves on load',async()=>{
  setup();await screen.findByRole('heading',{name:'Hermes'});
  expect(screen.getByText(/Synthetic User.*User 72.*Tenant 7/)).toBeTruthy();
  expect(screen.getByText('Read minimized case overviews')).toBeTruthy();
  expect(fetchMock).toHaveBeenCalledTimes(2);
  expect(fetchMock.mock.calls.every(call=>call[1]?.method==='GET')).toBe(true);
  for (const [,init] of fetchMock.mock.calls) {
    expect(init?.redirect).toBe('error');expect(init?.credentials).toBe('omit');expect(init?.referrerPolicy).toBe('no-referrer');
    expect(init?.headers).toMatchObject({Authorization:'Bearer web-token',Accept:'application/json'});
  }
});
it.each([true,false])('submits exactly one explicit decision, approval=%s',async approve=>{
  const complete=setup();await screen.findByRole('heading',{name:'Hermes'});
  const target=`https://client.test/cb?${new URLSearchParams({[approve?'code':'error']:approve?'synthetic-code':'access_denied',state:'exact + & = ÃƒÂ©'})}`;
  fetchMock.mockResolvedValueOnce(response({redirect:target}));
  const button=screen.getByRole('button',{name:approve?'Approve access':'Deny'});
  fireEvent.click(button);fireEvent.click(button);
  await waitFor(()=>expect(complete).toHaveBeenCalledWith(target));
  expect(fetchMock).toHaveBeenCalledTimes(3);
  const body=new URLSearchParams(fetchMock.mock.calls[2][1]?.body as string);
  expect(body.get('state')).toBe('server-nonce');expect(body.get('client_id')).toBe('hermes');
  expect(body.getAll('scope')).toEqual(approve?['cases.search','cases.overview.read']:[]);
  expect(body.has('redirect_uri')).toBe(false);
});
it.each([{tenant:8},{user:73},{scopes:['admin']},{intent_expires_at:'invalid'}])('fails closed for mismatched or invalid consent %s',async overrides=>{
  const complete=setup(overrides);await screen.findByRole('alert');
  expect(screen.queryByRole('button',{name:'Approve access'})).toBeNull();expect(complete).not.toHaveBeenCalled();
  expect(fetchMock).toHaveBeenCalledTimes(2);
});
it('disables expired requests without issuing a decision',async()=>{
  setup({intent_expires_at:new Date(Date.now()-1000).toISOString()});
  await screen.findByText(/This request has expired/);
  expect((screen.getByRole('button',{name:'Approve access'}) as HTMLButtonElement).disabled).toBe(true);
  expect(fetchMock).toHaveBeenCalledTimes(2);
});
it('late decision after session teardown cannot navigate',async()=>{
  const complete=setup();await screen.findByRole('heading',{name:'Hermes'});
  let resolve!: (r:Response)=>void;
  fetchMock.mockReturnValueOnce(new Promise(r=>{resolve=r;}));
  fireEvent.click(screen.getByRole('button',{name:'Approve access'}));
  await act(async()=>{end();resolve(response({redirect:'https://client.test/cb?code=synthetic&state=exact'}));});
  expect(complete).not.toHaveBeenCalled();
});
it('a decision response after leaving the screen cannot navigate',async()=>{
  const complete=setup();await screen.findByRole('heading',{name:'Hermes'});
  let resolve!: (r:Response)=>void;
  fetchMock.mockReturnValueOnce(new Promise(r=>{resolve=r;}));
  fireEvent.click(screen.getByRole('button',{name:'Approve access'}));
  cleanup();
  await act(async()=>{resolve(response({redirect:'https://client.test/cb?code=synthetic&state=exact'}));});
  expect(complete).not.toHaveBeenCalled();
});
it('denies substituted callbacks, schemes and state',()=>{
  for(const target of ['https://evil.test/cb?state=exact','https://client.test/other?state=exact','javascript:alert(1)',
    'https://client.test/cb?state=other','https://client.test/cb?state=exact&state=exact','https://client.test/cb?state=exact#leak'])
    expect(()=>callbackTarget(target,'https://client.test/cb','exact')).toThrow();
});
it('does not retry a decision whose response is lost',async()=>{
  setup();await screen.findByRole('heading',{name:'Hermes'});fetchMock.mockRejectedValueOnce(new Error('network'));
  fireEvent.click(screen.getByRole('button',{name:'Approve access'}));await screen.findByRole('alert');
  expect(fetchMock).toHaveBeenCalledTimes(3);expect(screen.queryByRole('button',{name:'Approve access'})).toBeNull();
});

const external = 'https://metadata.public.org/client.json';
const externalRequest = new URLSearchParams({client_id:external,redirect_uri:'http://127.0.0.1:27890/callback',state:'exact',scope:'cases.search cases.overview.read'}).toString();
const pending = (admin:boolean) => ({client_id:external,client_name:'Stock client',tenant:7,user:72,admin,state:'PENDING',approval_id:'00000000-0000-0000-0000-000000000001',approval_version:'0000000000000001'});
it('requires explicit tenant approval and then separate user consent for CIMD',async()=>{
  fetchMock.mockResolvedValueOnce(response(pending(true)));
  const complete=vi.fn();
  render(<OAuthConsent token="web-token" user={user} request={externalRequest} onComplete={complete}/>);
  const approval=await screen.findByRole('button',{name:'Approve tenant application'});
  expect(fetchMock).toHaveBeenCalledTimes(1);
  expect(screen.queryByRole('button',{name:'Approve access'})).toBeNull();
  fetchMock.mockResolvedValueOnce(response({...pending(true),state:'APPROVED'}))
    .mockResolvedValueOnce(response({...pending(true),state:'APPROVED'}))
    .mockResolvedValueOnce(response({redirect:`${apiBaseUrl()}/oauth/mcp/consent?client_id=${encodeURIComponent(external)}&state=server-nonce`}))
    .mockResolvedValueOnce(response({...details(),client_id:external}));
  fireEvent.click(approval);fireEvent.click(approval);
  await screen.findByRole('button',{name:'Approve access'});
  expect(fetchMock).toHaveBeenCalledTimes(5);
  const decision=new URLSearchParams(fetchMock.mock.calls[1][1]?.body as string);
  expect(decision.get('decision')).toBe('APPROVE');expect(decision.get('client_id')).toBe(external);
  expect(decision.has('tenant')).toBe(false);expect(decision.has('user')).toBe(false);
  expect(complete).not.toHaveBeenCalled();
});
it('ordinary users can check approval but cannot approve an application',async()=>{
  fetchMock.mockResolvedValueOnce(response(pending(false)));
  render(<OAuthConsent token="web-token" user={user} request={externalRequest}/>);
  await screen.findByRole('button',{name:'Check approval'});
  expect(screen.queryByRole('button',{name:'Approve tenant application'})).toBeNull();
  expect(screen.queryByRole('button',{name:'Approve access'})).toBeNull();
});
it.each([{tenant:8},{user:81},{client_id:'https://evil.public.org/client.json'},{admin:'true'}])('rejects mismatched onboarding %s',async invalid=>{
  fetchMock.mockResolvedValueOnce(response({...pending(true),...invalid}));
  render(<OAuthConsent token="web-token" user={user} request={externalRequest}/>);
  await screen.findByRole('alert');expect(fetchMock).toHaveBeenCalledTimes(1);
  expect(screen.queryByRole('button',{name:'Approve tenant application'})).toBeNull();
});

it('DCR uses the existing administrator approval, then a separate explicit consent and exact localhost callback', async () => {
  const client = 'dcr.' + 'a'.repeat(64), redirect = 'http://localhost:3334/oauth/callback';
  const request = new URLSearchParams({client_id: client, redirect_uri: redirect, state: 'exact', scope: 'cases.search cases.overview.read'}).toString();
  const state = {...pending(true), client_id: client};
  fetchMock.mockResolvedValueOnce(response(state));
  const complete = vi.fn();
  render(<OAuthConsent token="web-token" user={user} request={request} onComplete={complete}/>);
  const approval = await screen.findByRole('button', {name: 'Approve tenant application'});
  expect(screen.queryByRole('button', {name: 'Approve access'})).toBeNull();
  fetchMock.mockResolvedValueOnce(response({...state, state: 'APPROVED'}))
    .mockResolvedValueOnce(response({...state, state: 'APPROVED'}))
    .mockResolvedValueOnce(response({redirect: `${apiBaseUrl()}/oauth/mcp/consent?client_id=${client}&state=server-nonce`}))
    .mockResolvedValueOnce(response({...details(), client_id: client}));
  fireEvent.click(approval);
  await screen.findByRole('button', {name: 'Approve access'});
  expect(complete).not.toHaveBeenCalled();
  const target = redirect + '?code=synthetic-code&state=exact';
  fetchMock.mockResolvedValueOnce(response({redirect: target}));
  fireEvent.click(screen.getByRole('button', {name: 'Approve access'}));
  await waitFor(() => expect(complete).toHaveBeenCalledWith(target));
  expect(() => callbackTarget(target, redirect, 'exact')).toThrow();
  expect(() => callbackTarget(target.replace(':3334', ':3335'), redirect, 'exact', client)).toThrow();
});


it('extends the browser deadline only for the explicitly bound acceptance package', () => {
  vi.stubEnv('VITE_SHALE_MCP_ACCEPTANCE_BROWSER', 'true');
  vi.stubEnv('VITE_SHALE_API_BASE_URL', 'https://production.invalid');
  expect(oauthRequestDeadline()).toBe(8000);
  vi.stubEnv('VITE_SHALE_API_BASE_URL', 'https://shale-mcp-acceptance.azurewebsites.net.evil.invalid');
  expect(oauthRequestDeadline()).toBe(8000);
  vi.stubEnv('VITE_SHALE_API_BASE_URL', 'https://shale-mcp-acceptance.azurewebsites.net');
  expect(oauthRequestDeadline()).toBe(600000);
  vi.stubEnv('VITE_SHALE_MCP_ACCEPTANCE_BROWSER', 'false');
  expect(oauthRequestDeadline()).toBe(8000);
});
it('shows a closed onboarding status without echoing response bodies, URLs or tokens', async () => {
  fetchMock.mockResolvedValueOnce(response({error:'SENSITIVE_RESPONSE_SENTINEL'}, 503));
  render(<OAuthConsent token="web-token" user={user} request={externalRequest}/>);
  const alert = await screen.findByRole('alert');
  expect(alert.textContent).toContain('ONBOARDING_HTTP_503');
  for (const secret of ['SENSITIVE_RESPONSE_SENTINEL', 'web-token', external, 'server-nonce']) expect(alert.textContent).not.toContain(secret);
  expect(fetchMock).toHaveBeenCalledTimes(1);
});
it('labels a rejected authorization after tenant approval without replaying the decision', async () => {
  fetchMock.mockResolvedValueOnce(response(pending(true)));
  render(<OAuthConsent token="web-token" user={user} request={externalRequest}/>);
  const approval = await screen.findByRole('button', {name:'Approve tenant application'});
  fetchMock.mockResolvedValueOnce(response({...pending(true),state:'APPROVED'}))
    .mockResolvedValueOnce(response({...pending(true),state:'APPROVED'}))
    .mockResolvedValueOnce(response({error:'SENSITIVE_RESPONSE_SENTINEL'},400));
  fireEvent.click(approval);
  expect((await screen.findByRole('alert')).textContent).toContain('AUTHORIZE_HTTP_400');
  expect(fetchMock.mock.calls.filter(call => String(call[0]).endsWith('/approval'))).toHaveLength(1);
  expect(fetchMock).toHaveBeenCalledTimes(4);
});
it('labels invalid consent responses without displaying untrusted error messages', async () => {
  setup({intent_expires_at:'SENSITIVE_RESPONSE_SENTINEL'});
  const alert = await screen.findByRole('alert');
  expect(alert.textContent).toContain('CONSENT_INVALID_RESPONSE');
  expect(alert.textContent).not.toContain('SENSITIVE_RESPONSE_SENTINEL');
});
it('labels deadline cancellation and never retries an uncertain approval', async () => {
  vi.useFakeTimers();
  fetchMock.mockImplementation((_input, init) => new Promise((_resolve, reject) => {
    init?.signal?.addEventListener('abort', () => reject(new Error('SENSITIVE_TRANSPORT_SENTINEL')));
  }));
  const outcome = oauthRequest('web-token', '/oauth/mcp/approval', new URLSearchParams({decision:'APPROVE'}), 'APPROVAL').catch(error => error);
  await vi.advanceTimersByTimeAsync(8001);
  const error = await outcome;
  expect(error).toMatchObject({reference:'APPROVAL_TIMEOUT', message:expect.not.stringContaining('SENSITIVE_TRANSPORT_SENTINEL')});
  expect(fetchMock).toHaveBeenCalledTimes(1);
});
