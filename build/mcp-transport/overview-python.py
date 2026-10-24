import asyncio, importlib.metadata, json, os, pathlib, uuid
from contextlib import asynccontextmanager
import httpx2
from mcp import Client
from mcp.client.streamable_http import streamable_http_client
root=pathlib.Path(__file__).resolve().parents[2]
search=json.loads((root/'shale-server/src/main/resources/mcp/search-cases.json').read_text())
overview=json.loads((root/'shale-server/src/main/resources/mcp/get-case-overview.json').read_text())
overview['outputSchema']['$defs']=search['outputSchema']['$defs']
records=[]
def obj(v):return v.model_dump(by_alias=True,exclude_none=True) if hasattr(v,'model_dump') else v
@asynccontextmanager
async def connect(key):
    async with httpx2.AsyncClient(headers={'Authorization':'Bearer '+os.environ['MCP_'+key]},timeout=5,trust_env=False) as http:
        async with Client(streamable_http_client(os.environ['MCP_URL'],http_client=http),mode='auto') as c:
            assert c.protocol_version=='2026-07-28'
            yield c
async def rejected_call(c,name,args):
    try:await c.call_tool(name,args)
    except Exception:return
    raise AssertionError('Unauthorized tool invoked')
def error(r,code):
    v=obj(r);assert v['isError'] and 'structuredContent' not in v and len(v['content'])==1
    e=json.loads(v['content'][0]['text']);assert set(e)=={'code','correlationId','retryAfterSeconds'} and e['code']==code
    uuid.UUID(e['correlationId']);assert e['retryAfterSeconds']==(1 if code=='THROTTLED' else None)
async def main():
    for key,expected in [('SEARCH_TOKEN',[search]),('BOTH_TOKEN',[search,overview])]:
        async with connect(key) as c:
            assert [obj(t) for t in (await c.list_tools()).tools]==expected
            if key=='SEARCH_TOKEN':await rejected_call(c,'get_case_overview',{'caseId':100})
            records.append(key+'-exact-schema')
    try:
        async with connect('REVOKED_TOKEN'):pass
    except Exception:records.append('revoked-authority')
    else:raise AssertionError('Revoked token accepted')
    async with connect('OVERVIEW_TOKEN') as c:
        assert [obj(t) for t in (await c.list_tools()).tools]==[overview];records.append('overview-only-exact-schema')
        await rejected_call(c,'search_cases',{'query':'MCP literal'});records.append('search-absent')
        for case_id in [100,1000]:
            v=obj(await c.call_tool('get_case_overview',{'caseId':case_id}));assert not v['isError'] and len(v['content'])==1
            s=v['structuredContent'];assert set(s)=={'case','correlationId'} and json.loads(v['content'][0]['text'])==s
            assert set(s['case'])==set(overview['outputSchema']['$defs']['Case']['required']) and s['case']['caseId']==case_id
            assert all(s['case'][k] is None for k in ['status','practiceArea','responsibleAttorney','primaryLegalAssistant'])
            assert s['case']['updatedAt']==(None if case_id==100 else '2026-10-23T11:22:33.123456700')
            assert len(v['content'][0]['text'].encode())<=8192
            records.append('direct-overview-no-search' if case_id==100 else 'same-tenant-unassigned')
        for case_id,code,label in [(999,'CASE_UNAVAILABLE','missing'),(9001,'CASE_UNAVAILABLE','foreign'),(9002,'CASE_UNAVAILABLE','deleted'),(9000,'PAYLOAD_TOO_LARGE','source-bound'),(9003,'AUDIT_UNAVAILABLE','phi-append-rollback')]:
            error(await c.call_tool('get_case_overview',{'caseId':case_id}),code);records.append(label)
        for _ in range(3):error(await c.call_tool('get_case_overview',{'caseId':0}),'INVALID_INPUT')
        error(await c.call_tool('get_case_overview',{'caseId':100}),'THROTTLED');records.append('quota-exhausted-no-refund')
    async with connect('INVALID_TOKEN') as c:
        for args in [{},{'caseId':None},{'caseId':0},{'caseId':-1},{'caseId':2147483648},{'caseId':'100'},{'caseId':1.5},{'caseId':100,'tenantId':8},{'caseId':100,'userId':71}]:
            error(await c.call_tool('get_case_overview',args),'INVALID_INPUT');records.append('strict-invalid-'+str(len(records)))
    print(json.dumps({'client':'mcp','version':importlib.metadata.version('mcp'),'protocol':'2026-07-28','records':records}))
asyncio.run(main())
