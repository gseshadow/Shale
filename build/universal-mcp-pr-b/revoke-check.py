import runpy,json,secrets,hashlib,base64,urllib.parse
from pathlib import Path
helpers=runpy.run_path('/workspace/Shale/work/pr-b-harness/http-regressions.py')
request=helpers['request'];overview=helpers['overview'];info=helpers['info'];root=helpers['root'];p=helpers['p'];base=helpers['base']
client_info=info('profile-clean-b');client_id=client_info['client_id'];redirect=client_info['redirect_uris'][0]
evidence=[]
def record(check,status,ms=None):
 row={'check':check,'status':status}
 if ms is not None:row['ms']=ms
 evidence.append(row);(p/'http-revocation-evidence.json').write_text(json.dumps(evidence,indent=2))
status,login,_=request('/api/auth/login',{'email':'badmin@example.invalid','password':(root/'work/pr-b-sql/browser-password').read_text()},kind='json');assert status==200
web=login['accessToken'];status,current,_=request('/oauth/mcp/onboarding',{'client_id':client_id,'scope':['cases.search','cases.overview.read']},web);assert status==200 and current['state']=='APPROVED'
verifier=secrets.token_urlsafe(32);challenge=base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).decode().rstrip('=');state=secrets.token_urlsafe(24)
params={'client_id':client_id,'response_type':'code','redirect_uri':redirect,'resource':base+'/mcp','scope':'cases.search cases.overview.read','state':state,'code_challenge':challenge,'code_challenge_method':'S256'}
status,result,_=request('/oauth/mcp/authorize?'+urllib.parse.urlencode(params),bearer=web);assert status==200
consent=urllib.parse.urlsplit(result['redirect']);assert consent.scheme=='https' and consent.netloc=='shale.test'
status,details,_=request(consent.path+'?'+consent.query,bearer=web);assert status==200
status,result,_=request('/oauth/mcp/authorize',{'client_id':client_id,'state':details['state'],'scope':details['scopes']},web);assert status==200
query=urllib.parse.parse_qs(urllib.parse.urlsplit(result['redirect']).query);assert query['state']==[state]
status,family,ms=request('/oauth/mcp/token',{'grant_type':'authorization_code','client_id':client_id,'code':query['code'][0],'redirect_uri':redirect,'resource':base+'/mcp','code_verifier':verifier});assert status==200;record('supplemental HTTP fresh consent/code exchange',status,ms)
status,result,ms=overview(family['access_token'],200);assert status==200 and result['result']['isError'] is False and ms<8000;record('fresh family audited Case before revocation',status,ms)
status,_,ms=request('/oauth/mcp/revoke',{'client_id':client_id,'token':family['access_token']});assert status==200;record('RFC 7009 empty-200 revocation',status,ms)
status,_,ms=overview(family['access_token'],200);assert status==401;record('same revoked access denied',status,ms)
status,_,ms=request('/oauth/mcp/token',{'grant_type':'refresh_token','client_id':client_id,'resource':base+'/mcp','refresh_token':family['refresh_token']});assert status in (400,401);record('revoked family refresh denied',status,ms)
status,_,ms=request('/oauth/mcp/revoke',{'client_id':client_id,'token':'synthetic-unknown'});assert status==200;record('unknown credential revocation has same response',status,ms)
print(json.dumps(evidence,indent=2))
