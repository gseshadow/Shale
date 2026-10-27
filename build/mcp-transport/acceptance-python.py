import asyncio,json,os,pathlib,time,importlib.metadata
import httpx2
from mcp import Client
from mcp.client.streamable_http import streamable_http_client
corpus=json.loads((pathlib.Path(__file__).parent/'acceptance-corpus.json').read_text())
def obj(v):return v.model_dump(by_alias=True,exclude_none=True) if hasattr(v,'model_dump') else v
def prepare(id,stage='prepare'):
 print(json.dumps({'control':stage,'id':id}),flush=True)
 return json.loads(input())
async def raw(token,id):
 auth=['Bearer '+token]*2 if id.endswith('duplicate') else ['Bearer abc'] if id.endswith('malformed') else ['Bearer '+'a'*101] if id.endswith('oversized') else ['Bearer '+token]
 headers=[('Content-Type','application/json'),('Accept','application/json'),('MCP-Protocol-Version',corpus['protocol']),('Mcp-Method','server/discover')]+[('Authorization',a) for a in auth]
 body={'jsonrpc':'2.0','id':'acceptance','method':'server/discover','params':{'_meta':{'io.modelcontextprotocol/protocolVersion':corpus['protocol'],'io.modelcontextprotocol/clientCapabilities':{}}}}
 async with httpx2.AsyncClient(timeout=10,trust_env=False) as http:return await http.post(os.environ['MCP_URL'],headers=headers,json=body)
def check(r,v):
 r=obj(r);expected=v['expected']
 if expected==expected.upper():
  assert r['isError'] and 'structuredContent' not in r and json.loads(r['content'][0]['text'])['code']==expected
  return
 assert not r['isError'];s=r['structuredContent'];assert json.loads(r['content'][0]['text'])==s
 if expected in ['page0','page9']:
  page=0 if expected=='page0' else 9
  assert [x['caseId'] for x in s['items']]==list(range(1000+page*10,1010+page*10)) and s['page']==page and s['hasMore'] and s['windowExhausted']==(page==9)
 if expected=='zero':assert not s['items'] and not s['hasMore']
 if expected=='literal':assert [x['caseId'] for x in s['items']]==[9004]
 if expected=='unicode':assert [x['caseId'] for x in s['items']]==[9006]
 if expected=='overview':assert s['case']['caseId']==v['arguments']['caseId'] and s['case']['responsibleAttorney'] is None and set(s['case'])=={'caseId','caseName','caseNumber','practiceArea','primaryLegalAssistant','responsibleAttorney','status','updatedAt'}
def assertion_in(error):
 return isinstance(error,AssertionError) or any(assertion_in(child) for child in getattr(error,'exceptions',()))
async def main():
 records=[];current='';group=os.environ.get('MCP_ACCEPTANCE_GROUP','all')
 vectors=[v for v in corpus['vectors'] if group=='all' or (group=='faults')==(v['action']=='fault' or v['id']=='reuse-after-failures')]
 try:
  for v in vectors:
   current=v['id'];token=prepare(current)['token'];start=time.monotonic()
   if v['action']=='raw':
    r=await raw(token,current);assert r.status_code==v['status']
    if r.status_code!=200:assert 'structuredContent' not in r.text and 'Direct overview' not in r.text
   else:
    args=dict(v.get('arguments',{}))
    if args.get('query')=='$ACCESS_SENTINEL':args['query']='ACCESS '+token
    try:
     async with httpx2.AsyncClient(headers={'Authorization':'Bearer '+token},timeout=10,trust_env=False) as http:
      async with Client(streamable_http_client(os.environ['MCP_URL'],http_client=http),mode='auto') as c:
       assert c.protocol_version==corpus['protocol']
       if v['action']=='catalog':assert [obj(t)['name'] for t in (await c.list_tools()).tools]==v['names']
       elif v['action']=='call':check(await c.call_tool(v['tool'],args),v)
       elif v['action']=='fault':
        try:r=obj(await c.call_tool(v['tool'],args))
        except Exception:pass
        else:
         code=json.loads(r['content'][0]['text']).get('code') if r.get('isError') else 'PROTECTED_RESULT'
         assert r['isError'] and 'structuredContent' not in r and code in (['FORBIDDEN'] if current=='fault-quota' else ['UNAVAILABLE','TIMEOUT','AUDIT_UNAVAILABLE']), ('fault-result',code)
       else:
        assert len((await c.list_tools()).tools)==2;prepare(current,'reduce')
        try:await c.list_tools()
        except Exception:pass
        else:raise AssertionError('Live scope reduction accepted')
        assert (await raw(token,current)).status_code==401
    except Exception as error:
     # Python's anyio client can report the intentionally broken response during
     # context exit, after call_tool raised. Java independently requires one RPC,
     # zero handoff, and exact committed/rolled-back history for every fault.
     if v['action']!='fault' or assertion_in(error):raise
     print('CLOSED_TRANSPORT '+current+' '+type(error).__name__,flush=True)
   records.append({'id':current,'status':'PASS','elapsedMillis':(time.monotonic()-start)*1000});print(json.dumps({'record':records[-1]}),flush=True)
  print(json.dumps({'complete':True,'group':group,'client':'mcp','version':importlib.metadata.version('mcp'),'protocol':corpus['protocol'],'records':records}),flush=True)
 except Exception as error:
  print('ACCEPTANCE_FAILED '+current+' '+type(error).__name__,flush=True)
  if isinstance(error,AssertionError):print(str(error),flush=True)
  raise SystemExit(1)
asyncio.run(main())
