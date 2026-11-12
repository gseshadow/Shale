import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { OAuthConsent, callbackTarget } from './OAuthConsent';
import { bindSessionRequests } from './sessionRequests';
import { apiBaseUrl } from './api';
const user = { authenticated: true as const, userId: 72, shaleClientId: 7, email: 'synthetic@example.invalid', displayName: 'Synthetic User',
  nameFirst: null, nameLast: null, isAdmin: false, isAttorney: false, initials: null, color: null };
const request = new URLSearchParams({ client_id: 'hermes', redirect_uri: 'https://client.test/cb', state: 'exact + & = é' }).toString();
const details = () => ({ client_id: 'hermes', client_name: 'Hermes', scopes: ['cases.search', 'cases.overview.read'], state: 'server-nonce',
  tenant: 7, user: 72, intent_expires_at: new Date(Date.now()+300000).toISOString(), expires_at: new Date(Date.now()+3600000).toISOString(), notice: 'Case data may leave Shale.' });
const response = (body: unknown, status=200) => new Response(JSON.stringify(body), { status });
const fetchMock = vi.fn<typeof fetch>();
let end: () => void;
beforeEach(() => { fetchMock.mockReset(); vi.stubGlobal('fetch',fetchMock); end=bindSessionRequests('web-token',()=>true,()=>{}); });
afterEach(() => { cleanup();end();vi.unstubAllGlobals(); });
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
  const target=`https://client.test/cb?${new URLSearchParams({[approve?'code':'error']:approve?'synthetic-code':'access_denied',state:'exact + & = é'})}`;
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
