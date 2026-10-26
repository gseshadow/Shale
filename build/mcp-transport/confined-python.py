import asyncio,json,os,pathlib,importlib.metadata
import httpx2
from mcp import Client
from mcp.client.streamable_http import streamable_http_client
root=pathlib.Path(__file__).resolve().parents[2]
search=json.loads((root/'shale-server/src/main/resources/mcp/search-cases.json').read_text())
overview=json.loads((root/'shale-server/src/main/resources/mcp/get-case-overview.json').read_text())
overview['outputSchema']['$defs']=search['outputSchema']['$defs']
def obj(value):return value.model_dump(by_alias=True,exclude_none=True) if hasattr(value,'model_dump') else value
async def main():
 async with httpx2.AsyncClient(headers={'Authorization':'Bearer '+os.environ['MCP_TOKEN']},timeout=10,trust_env=False) as http:
  async with Client(streamable_http_client(os.environ['MCP_URL'],http_client=http),mode='auto') as c:
   assert c.protocol_version=='2026-07-28'
   assert [obj(t) for t in (await c.list_tools()).tools]==[search,overview]
   p=obj(await c.call_tool('search_cases',{'query':'MCP literal','page':0}))
   assert not p['isError'] and len(p['structuredContent']['items'])==10 and p['structuredContent']['size']==10 and p['structuredContent']['hasMore'] and not p['structuredContent']['windowExhausted']
   assert json.loads(p['content'][0]['text'])==p['structuredContent']
   r=obj(await c.call_tool('get_case_overview',{'caseId':100}))
   assert not r['isError'] and r['structuredContent']['case']['caseId']==100 and set(r['structuredContent']['case'])==set(search['outputSchema']['$defs']['Case']['required'])
   assert json.loads(r['content'][0]['text'])==r['structuredContent']
   f=obj(await c.call_tool('get_case_overview',{'caseId':9001}))
   assert f['isError'] and 'structuredContent' not in f and json.loads(f['content'][0]['text'])['code']=='CASE_UNAVAILABLE'
 async with httpx2.AsyncClient(headers={'Authorization':'Bearer '+os.environ['MCP_EXPIRY_TOKEN']},timeout=10,trust_env=False) as http:
  async with Client(streamable_http_client(os.environ['MCP_URL'],http_client=http),mode='auto') as c:
   denied=obj(await c.call_tool('get_case_overview',{'caseId':100}))
   assert denied['isError'] and 'structuredContent' not in denied and json.loads(denied['content'][0]['text'])['code']=='FORBIDDEN'
 print(json.dumps({'client':'mcp','version':importlib.metadata.version('mcp'),'protocol':'2026-07-28','schema':True,'search':True,'overview':True,'foreignUnavailable':True,'expiryDenied':True}))
asyncio.run(main())
