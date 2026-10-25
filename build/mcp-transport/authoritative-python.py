import asyncio, importlib.metadata, json, os, pathlib
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
def error(r,code):
    v=obj(r);assert v['isError'] and 'structuredContent' not in v and json.loads(v['content'][0]['text'])['code']==code
    records.append(code)
def value(r):
    v=obj(r);assert not v['isError'] and json.loads(v['content'][0]['text'])==v['structuredContent']
    return v['structuredContent']
def item(c):
    assert set(c)==set(search['outputSchema']['$defs']['Case']['required']) and c['caseId'] not in (9001,9002)
async def main():
    for key,expected in [('SEARCH_TOKEN',[search]),('OVERVIEW_TOKEN',[overview]),('BOTH_TOKEN',[search,overview])]:
        async with connect(key) as c:
            assert [obj(t) for t in (await c.list_tools()).tools]==expected
            records.append(key+'-catalog')
    try:
        async with connect('REVOKED_TOKEN'):pass
    except Exception:records.append('revoked-access')
    else:raise AssertionError('Revoked token accepted')
    async with connect('SEARCH_TOKEN') as c:
        for page,ids,more,window in [(0,list(range(1000,1010)),True,False),(9,list(range(1090,1100)),True,True)]:
            s=value(await c.call_tool('search_cases',{'query':'MCP literal','page':page}))
            assert [x['caseId'] for x in s['items']]==ids and s['hasMore']==more and s['windowExhausted']==window
            for x in s['items']:item(x)
            records.append('page-'+str(page)+'-eleventh-row')
        s=value(await c.call_tool('search_cases',{'query':'no matches'}));assert s['items']==[] and not s['hasMore'];records.append('zero-results')
        error(await c.call_tool('search_cases',{'query':''}),'INVALID_INPUT')
        error(await c.call_tool('search_cases',{'query':'MCP literal','tenantId':8}),'INVALID_INPUT')
    async with connect('BOTH_TOKEN') as c:
        s=value(await c.call_tool('search_cases',{'query':'literal %_['}));assert [x['caseId'] for x in s['items']]==[9004];records.append('literal-wildcard-escaping')
        s=value(await c.call_tool('get_case_overview',{'caseId':1000}));item(s['case']);records.append('both-tools-same-server')
        error(await c.call_tool('search_cases',{'query':'Oversized'}),'PAYLOAD_TOO_LARGE')
    async with connect('OVERVIEW_TOKEN') as c:
        for case_id in [100,1000]:
            s=value(await c.call_tool('get_case_overview',{'caseId':case_id}));item(s['case']);assert s['case']['caseId']==case_id and s['case']['responsibleAttorney'] is None;records.append('same-tenant-unassigned-'+str(case_id))
        for case_id in [9001,999,9002]:error(await c.call_tool('get_case_overview',{'caseId':case_id}),'CASE_UNAVAILABLE')
    async with connect('FAULT_TOKEN') as c:error(await c.call_tool('get_case_overview',{'caseId':9003}),'AUDIT_UNAVAILABLE')
    print(json.dumps({'client':'mcp','version':importlib.metadata.version('mcp'),'protocol':'2026-07-28','phase':'core-matrix','records':list(records)}),flush=True)
    for key,name,args in [('RACE_REVOKE_TOKEN','search_cases',{'query':'MCP literal'}),('RACE_EXPIRY_TOKEN','get_case_overview',{'caseId':100})]:
        async with connect(key) as c:error(await c.call_tool(name,args),'FORBIDDEN');records.append(key+'-post-commit-final-gate');print(json.dumps({'phase':key,'records':list(records)}),flush=True)
    print(json.dumps({'client':'mcp','version':importlib.metadata.version('mcp'),'protocol':'2026-07-28','records':records}))
asyncio.run(main())
