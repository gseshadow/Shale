"""Supplemental actual HTTP policy checks; does not replace stock-client acceptance."""
import runpy,json
from pathlib import Path
h=runpy.run_path('/workspace/Shale/build/universal-mcp-pr-c2/http-regressions.py');request=h['request'];p=h['p'];rows=[]
for name,body,expected in [
 ('unsupported RFC default auth',{'redirect_uris':['https://client.test/callback']},400),
 ('invalid redirect',{'redirect_uris':['https://client.test/callback#fragment'],'token_endpoint_auth_method':'none'},400),
 ('caller identity rejected',{'client_id':'chosen','redirect_uris':['https://client.test/callback'],'token_endpoint_auth_method':'none'},400),
 ('oversized request',{'extension':'x'*33000},413)]:
 status,result,_=request('/oauth/mcp/register',body,kind='json');assert status==expected,(name,status)
 rows.append({'check':name,'status':status,'error':result.get('error')})
# Validate the approved confidential registration profile over actual HTTP too.
from cryptography.hazmat.primitives.asymmetric import rsa
import base64
key=rsa.generate_private_key(public_exponent=65537,key_size=2048).public_key().public_numbers()
def b64(n):return base64.urlsafe_b64encode(n.to_bytes((n.bit_length()+7)//8,'big')).decode().rstrip('=')
body={'client_name':'Synthetic confidential DCR','redirect_uris':['https://client.test/callback'],'token_endpoint_auth_method':'private_key_jwt','token_endpoint_auth_signing_alg':'RS256','jwks':{'keys':[{'kty':'RSA','n':b64(key.n),'e':b64(key.e),'kid':'test-public-key','use':'sig','alg':'RS256'}]}}
status,result,_=request('/oauth/mcp/register',body,kind='json');assert status==201 and result['token_endpoint_auth_method']=='private_key_jwt' and 'client_secret' not in result
rows.append({'check':'public RS256 private_key_jwt registration','status':status,'method':result['token_endpoint_auth_method'],'private_material_submitted':False})
# Invalid attempts consume source budget without creating application/authority rows.
statuses=[]
for _ in range(21):
 status,_,_=request('/oauth/mcp/register',{},kind='json');statuses.append(status)
 if status==429:assert int(request.__globals__['last_retry_after'])>0
assert 429 in statuses,statuses
rows.append({'check':'durable source abuse limit','statuses':statuses})
(p/'http-registration-security.json').write_text(json.dumps(rows,indent=2));print(json.dumps(rows))
