import asyncio, importlib.metadata, json, os, pathlib, uuid
import httpx2
from mcp import Client
from mcp.client.streamable_http import streamable_http_client

expected=json.loads((pathlib.Path(__file__).resolve().parents[2]/'shale-server/src/main/resources/mcp/search-cases.json').read_text())
records=[]

def obj(value):
    return value.model_dump(by_alias=True,exclude_none=True) if hasattr(value,'model_dump') else value

async def main():
    url=os.environ['MCP_URL']
    async with httpx2.AsyncClient(headers={'Authorization':'Bearer '+os.environ['MCP_OVERVIEW_TOKEN']},timeout=5,trust_env=False) as http:
        async with Client(streamable_http_client(url,http_client=http),mode='auto') as client:
            assert (await client.list_tools()).tools==[]
            for name in ['get_case_overview','search_cases']:
                try: await client.call_tool(name,{'query':'MCP literal'})
                except Exception: pass
                else: raise AssertionError('Disabled tool invoked')
            records.append('scope-filtered-overview-and-search-absent')
    async with httpx2.AsyncClient(headers={'Authorization':'Bearer '+os.environ['MCP_REVOKED_TOKEN']},timeout=5,trust_env=False) as http:
        try:
            async with Client(streamable_http_client(url,http_client=http),mode='auto'): pass
        except Exception: pass
        else: raise AssertionError('Revoked ACCESS accepted')
        records.append('revoked-access')
    async with httpx2.AsyncClient(headers={'Authorization':'Bearer '+os.environ['MCP_TOKEN']},timeout=5,trust_env=False) as http:
        async with Client(streamable_http_client(url,http_client=http),mode='auto') as client:
            assert client.protocol_version=='2026-07-28'
            tools=(await client.list_tools()).tools
            assert len(tools)==1 and obj(tools[0])==expected
            records.append('exact-wire-schema')
            for label,args,page,count,more,window in [
                ('page-zero',{'query':'  MCP literal  '},0,10,True,False),('page-one',{'query':'MCP literal','page':1},1,10,True,False),
                ('zero',{'query':'no matches','page':2},2,0,False,False),('window',{'query':'MCP literal','page':9},9,10,True,True)]:
                r=await client.call_tool('search_cases',args)
                value=obj(r);s=value['structuredContent']
                assert not value['isError'] and set(s)=={'items','page','size','hasMore','windowExhausted','correlationId'}
                assert (s['page'],s['size'],len(s['items']),s['hasMore'],s['windowExhausted'])==(page,10,count,more,window)
                assert [item['caseId'] for item in s['items']]==list(range(1000+page*10,1000+page*10+count))
                uuid.UUID(s['correlationId']);assert json.loads(value['content'][0]['text'])==s
                assert value['_meta']=={'io.modelcontextprotocol/serverInfo':{'name':'shale-mcp','version':'development'}}
                for item in s['items']:
                    assert set(item)==set(expected['outputSchema']['$defs']['Case']['required'])
                    assert 1000<=item['caseId']<=1100 and all(item[k] is None for k in ['status','practiceArea','responsibleAttorney','primaryLegalAssistant'])
                    assert item['updatedAt']=='2026-10-23T11:22:33.123456700'
                records.append(label)
            for label,args,code in [('short',{'query':'ab'},'INVALID_INPUT'),('wildcard',{'query':'%_*'},'INVALID_INPUT'),
                ('page-invalid',{'query':'MCP literal','page':10},'INVALID_INPUT'),('additional-property',{'query':'MCP literal','tenantId':8},'INVALID_INPUT'),
                ('bound',{'query':'Oversized'},'PAYLOAD_TOO_LARGE')]:
                value=obj(await client.call_tool('search_cases',args))
                assert value['isError'] and 'structuredContent' not in value and json.loads(value['content'][0]['text'])['code']==code,label
                records.append(label)
            await client.call_tool('search_cases',{'query':'ab'})
            value=obj(await client.call_tool('search_cases',{'query':'MCP literal'}))
            assert value['isError'] and json.loads(value['content'][0]['text'])['code']=='THROTTLED'
            records.append('quota-exhausted')
            try: await client.call_tool('get_case_overview',{'caseId':100})
            except Exception: pass
            else: raise AssertionError('Overview handler exists')
            records.append('overview-uninvokable')
    print(json.dumps({'client':'mcp','version':importlib.metadata.version('mcp'),'protocol':'2026-07-28','records':records}))
asyncio.run(main())
