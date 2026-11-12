import json,ssl,urllib.request,urllib.parse,urllib.error,time,secrets,hashlib,base64
from pathlib import Path
root=Path('/workspace/Shale');p=root/'work/pr-c2-harness';base='https://shale.test'
context=ssl.create_default_context(cafile=str(p/'ca.crt'));client=urllib.request.build_opener(urllib.request.ProxyHandler({'https':'http://127.0.0.1:18081'}),urllib.request.HTTPSHandler(context=context))
def request(path,body=None,bearer=None,kind='form'):
 global last_retry_after;last_retry_after=None
 headers={'Accept':'application/json'}
 if bearer:headers['Authorization']='Bearer '+bearer
 if body is not None:
  headers['Content-Type']='application/json' if kind=='json' else 'application/x-www-form-urlencoded'
  body=json.dumps(body).encode() if kind=='json' else urllib.parse.urlencode(body,doseq=True).encode()
 start=time.monotonic()
 try:
  r=client.open(urllib.request.Request(base+path,data=body,headers=headers),timeout=10);return r.status,(json.loads(data) if (data:=r.read()) else {}),round((time.monotonic()-start)*1000)
 except urllib.error.HTTPError as e:
  last_retry_after=e.headers.get('Retry-After');text=e.read();return e.code,json.loads(text) if text else {},round((time.monotonic()-start)*1000)
def fresh_manual():
 client_id=(p/'manual-client-private').read_text()
 status,login,_=request('/api/auth/login',{'email':'a72@example.invalid','password':(root/'work/pr-c2-sql-runtime/browser-password').read_text()},kind='json');assert status==200
 web=login['accessToken'];verifier=secrets.token_urlsafe(32);state=secrets.token_urlsafe(24)
 challenge=base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).decode().rstrip('=')
 redirect='https://client.test/callback'
 params={'client_id':client_id,'response_type':'code','redirect_uri':redirect,'resource':base+'/mcp','scope':'cases.search cases.overview.read','state':state,'code_challenge':challenge,'code_challenge_method':'S256'}
 status,start,_=request('/oauth/mcp/authorize?'+urllib.parse.urlencode(params),bearer=web);assert status==200
 consent=urllib.parse.urlsplit(start['redirect']);assert consent.netloc=='shale.test'
 status,details,_=request(consent.path+'?'+consent.query,bearer=web);assert status==200
 status,done,_=request('/oauth/mcp/authorize',{'client_id':client_id,'state':details['state'],'scope':details['scopes']},web);assert status==200
 query=urllib.parse.parse_qs(urllib.parse.urlsplit(done['redirect']).query);assert query['state']==[state]
 status,family,_=request('/oauth/mcp/token',{'grant_type':'authorization_code','client_id':client_id,'code':query['code'][0],'redirect_uri':redirect,'resource':base+'/mcp','code_verifier':verifier});assert status==200
 return family['access_token']
def tokens(profile):return json.loads((root/profile/'mcp-tokens/shale.json').read_text())
def info(profile):return json.loads((root/profile/'mcp-tokens/shale.client.json').read_text())
def overview(token,case):
 body={'jsonrpc':'2.0','id':'regression','method':'tools/call','params':{'name':'get_case_overview','arguments':{'caseId':case},'_meta':{'io.modelcontextprotocol/protocolVersion':'2026-07-28','io.modelcontextprotocol/clientCapabilities':{}}}}
 # The production SDK enforces method/name/version equality; retain it for HTTP security checks.
 headers={'Content-Type':'application/json','Accept':'application/json','Authorization':'Bearer '+token,'MCP-Protocol-Version':'2026-07-28','Mcp-Method':'tools/call','Mcp-Name':'get_case_overview'}
 start=time.monotonic()
 try:
  r=client.open(urllib.request.Request(base+'/mcp',data=json.dumps(body).encode(),headers=headers),timeout=10);return r.status,(json.loads(data) if (data:=r.read()) else {}),round((time.monotonic()-start)*1000)
 except urllib.error.HTTPError as e:return e.code,{},round((time.monotonic()-start)*1000)
if __name__=='__main__':
 evidence=[];a=tokens('work/pr-c2-final-hermes-a');b=tokens('work/pr-c2-final-hermes-b');client_id=info('work/pr-c2-final-hermes-a')['client_id']
 b_client=info('work/pr-c2-final-hermes-b')['client_id'];assert b_client!=client_id and b_client.startswith('dcr.') and client_id.startswith('dcr.')
 status,b_rotated,ms=request('/oauth/mcp/token',{'grant_type':'refresh_token','client_id':b_client,'resource':base+'/mcp','refresh_token':b['refresh_token']});assert status==200
 b=b_rotated;evidence.append({'check':'HTTP tenant B refresh','status':status,'ms':ms})
 status,result,ms=overview(b['access_token'],100);assert status==200 and result['result']['isError'] is True
 evidence.append({'check':'tenant B cannot disclose tenant A Case','status':status,'isError':True,'ms':ms})
 status,new,ms=request('/oauth/mcp/token',{'grant_type':'refresh_token','client_id':client_id,'resource':base+'/mcp','refresh_token':a['refresh_token']});assert status==200,(status,new)
 (p/'rotated-a-private.json').write_text(json.dumps(new));(p/'rotated-a-private.json').chmod(0o600)
 evidence.append({'check':'HTTP SQL refresh rotation','status':status,'ms':ms})
 status,result,ms=overview(new['access_token'],100);assert status==200 and result['result']['isError'] is False
 evidence.append({'check':'rotated credential audited Case disclosure','status':status,'ms':ms})
 # Current human role + original WEB authority flow, never fixture-owner mutation.
 status,login,_=request('/api/auth/login',{'email':'a72@example.invalid','password':(root/'work/pr-c2-sql-runtime/browser-password').read_text()},kind='json');assert status==200
 member=login['accessToken']
 status,state,_=request('/oauth/mcp/onboarding',{'client_id':client_id,'scope':['cases.search','cases.overview.read']},member);assert status==200 and not state['admin']
 decision={'client_id':client_id,'approval_id':state['approval_id'],'approval_version':state['approval_version'],'decision':'REVOKE'}
 status,_,_=request('/oauth/mcp/approval',decision,member);assert status==401
 evidence.append({'check':'ordinary user cannot revoke/approve tenant application','status':status})
 status,login,_=request('/api/auth/login',{'email':'admin@example.invalid','password':(root/'work/pr-c2-sql-runtime/browser-password').read_text()},kind='json');assert status==200
 admin=login['accessToken'];status,state,_=request('/oauth/mcp/onboarding',{'client_id':client_id,'scope':['cases.search','cases.overview.read']},admin);assert status==200
 decision.update(approval_id=state['approval_id'],approval_version=state['approval_version'])
 status,b_admin,_=request('/api/auth/login',{'email':'badmin@example.invalid','password':(root/'work/pr-c2-sql-runtime/browser-password').read_text()},kind='json');assert status==200
 status,_,_=request('/oauth/mcp/approval',decision,b_admin['accessToken']);assert status==401
 evidence.append({'check':'tenant B administrator cannot change tenant A approval','status':status})
 status,_,_=request('/oauth/mcp/approval',decision,admin);assert status==200
 evidence.append({'check':'current tenant A administrator revokes approval via HTTP','status':status})
 status,_,ms=overview(new['access_token'],100);assert status==401
 evidence.append({'check':'approval revocation blocks existing access','status':status,'ms':ms})
 status,_,_=request('/oauth/mcp/token',{'grant_type':'refresh_token','client_id':client_id,'resource':base+'/mcp','refresh_token':new['refresh_token']});assert status in (400,401)
 evidence.append({'check':'approval revocation blocks refresh','status':status})
 status,result,ms=overview(b['access_token'],200);assert status==200 and result['result']['isError'] is False
 evidence.append({'check':'tenant B survives tenant A approval revocation','status':status,'ms':ms})
 status,_,_=request('/oauth/mcp/revoke',{'client_id':b_client,'token':b['access_token']});assert status==200
 evidence.append({'check':'HTTP credential family revocation','status':status})
 status,_,ms=overview(b['access_token'],200);assert status==401
 evidence.append({'check':'revoked credential denied','status':status,'ms':ms})
 status,_,_=request('/oauth/mcp/token',{'grant_type':'refresh_token','client_id':b_client,'resource':base+'/mcp','refresh_token':b['refresh_token']});assert status in (400,401)
 evidence.append({'check':'revoked family refresh denied','status':status})
 status,_,_=request('/oauth/mcp/revoke',{'client_id':b_client,'token':'synthetic-unknown'});assert status==200
 evidence.append({'check':'unknown credential revocation is the same empty 200','status':status})
 (p/'http-security-evidence.json').write_text(json.dumps(evidence,indent=2))
 status,result,ms=overview(fresh_manual(),100);assert status==200 and result['result']['isError'] is False
 evidence.append({'check':'existing manual client Case HTTP continuity','status':status,'ms':ms})
 (p/'http-security-evidence.json').write_text(json.dumps(evidence,indent=2));print(json.dumps(evidence,indent=2))
