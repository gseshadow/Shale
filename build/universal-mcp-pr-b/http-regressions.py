import json,ssl,urllib.request,urllib.parse,urllib.error,time
from pathlib import Path
root=Path('/workspace/Shale');p=root/'work/pr-b-harness';base='https://shale.test'
context=ssl.create_default_context(cafile=str(p/'ca.crt'));client=urllib.request.build_opener(urllib.request.ProxyHandler({'https':'http://127.0.0.1:18081'}),urllib.request.HTTPSHandler(context=context))
def request(path,body=None,bearer=None,kind='form'):
 headers={'Accept':'application/json'}
 if bearer:headers['Authorization']='Bearer '+bearer
 if body is not None:
  headers['Content-Type']='application/json' if kind=='json' else 'application/x-www-form-urlencoded'
  body=json.dumps(body).encode() if kind=='json' else urllib.parse.urlencode(body,doseq=True).encode()
 start=time.monotonic()
 try:
  r=client.open(urllib.request.Request(base+path,data=body,headers=headers),timeout=10);return r.status,(json.loads(data) if (data:=r.read()) else {}),round((time.monotonic()-start)*1000)
 except urllib.error.HTTPError as e:
  text=e.read();return e.code,json.loads(text) if text else {},round((time.monotonic()-start)*1000)
def tokens(profile):return json.loads((root/'work/pr-b-client'/profile/'mcp-tokens/shale.json').read_text())
def info(profile):return json.loads((root/'work/pr-b-client'/profile/'mcp-tokens/shale.client.json').read_text())
def overview(token,case):
 body={'jsonrpc':'2.0','id':'regression','method':'tools/call','params':{'name':'get_case_overview','arguments':{'caseId':case},'_meta':{'io.modelcontextprotocol/protocolVersion':'2026-07-28','io.modelcontextprotocol/clientCapabilities':{}}}}
 # The production SDK enforces method/name/version equality; retain it for HTTP security checks.
 headers={'Content-Type':'application/json','Accept':'application/json','Authorization':'Bearer '+token,'MCP-Protocol-Version':'2026-07-28','Mcp-Method':'tools/call','Mcp-Name':'get_case_overview'}
 start=time.monotonic()
 try:
  r=client.open(urllib.request.Request(base+'/mcp',data=json.dumps(body).encode(),headers=headers),timeout=10);return r.status,(json.loads(data) if (data:=r.read()) else {}),round((time.monotonic()-start)*1000)
 except urllib.error.HTTPError as e:return e.code,{},round((time.monotonic()-start)*1000)
if __name__=='__main__':
 evidence=[];a=tokens('profile-clean-a');b=tokens('profile-clean-b');client_id=info('profile-clean-a')['client_id']
 assert info('profile-clean-b')['client_id']==client_id
 status,b_rotated,ms=request('/oauth/mcp/token',{'grant_type':'refresh_token','client_id':client_id,'resource':base+'/mcp','refresh_token':b['refresh_token']});assert status==200
 b=b_rotated;evidence.append({'check':'HTTP tenant B refresh','status':status,'ms':ms})
 status,result,ms=overview(b['access_token'],100);assert status==200 and result['result']['isError'] is True
 evidence.append({'check':'tenant B cannot disclose tenant A Case','status':status,'isError':True,'ms':ms})
 status,new,ms=request('/oauth/mcp/token',{'grant_type':'refresh_token','client_id':client_id,'resource':base+'/mcp','refresh_token':a['refresh_token']});assert status==200,(status,new)
 (p/'rotated-a-private.json').write_text(json.dumps(new));(p/'rotated-a-private.json').chmod(0o600)
 evidence.append({'check':'HTTP SQL refresh rotation','status':status,'ms':ms})
 status,result,ms=overview(new['access_token'],100);assert status==200 and result['result']['isError'] is False
 evidence.append({'check':'rotated credential audited Case disclosure','status':status,'ms':ms})
 # Current human role + original WEB authority flow, never fixture-owner mutation.
 status,login,_=request('/api/auth/login',{'email':'a72@example.invalid','password':(root/'work/pr-b-sql/browser-password').read_text()},kind='json');assert status==200
 member=login['accessToken']
 status,state,_=request('/oauth/mcp/onboarding',{'client_id':client_id,'scope':['cases.search','cases.overview.read']},member);assert status==200 and not state['admin']
 decision={'client_id':client_id,'approval_id':state['approval_id'],'approval_version':state['approval_version'],'decision':'REVOKE'}
 status,_,_=request('/oauth/mcp/approval',decision,member);assert status==401
 evidence.append({'check':'ordinary user cannot revoke/approve tenant application','status':status})
 status,login,_=request('/api/auth/login',{'email':'admin@example.invalid','password':(root/'work/pr-b-sql/browser-password').read_text()},kind='json');assert status==200
 admin=login['accessToken'];status,state,_=request('/oauth/mcp/onboarding',{'client_id':client_id,'scope':['cases.search','cases.overview.read']},admin);assert status==200
 decision.update(approval_id=state['approval_id'],approval_version=state['approval_version'])
 status,b_admin,_=request('/api/auth/login',{'email':'badmin@example.invalid','password':(root/'work/pr-b-sql/browser-password').read_text()},kind='json');assert status==200
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
 status,_,_=request('/oauth/mcp/revoke',{'client_id':client_id,'token':b['access_token']});assert status==200
 evidence.append({'check':'HTTP credential family revocation','status':status})
 status,_,ms=overview(b['access_token'],200);assert status==401
 evidence.append({'check':'revoked credential denied','status':status,'ms':ms})
 (p/'http-security-evidence.json').write_text(json.dumps(evidence,indent=2));print(json.dumps(evidence,indent=2))
