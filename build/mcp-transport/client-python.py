import asyncio, importlib.metadata, json, os
import httpx2
from mcp import Client
from mcp.client.streamable_http import streamable_http_client

async def main():
    url, token = os.environ['MCP_URL'], os.environ['MCP_TOKEN']
    headers = {'Authorization': 'Bearer ' + token}
    records = []
    async with httpx2.AsyncClient(headers=headers, timeout=5, trust_env=False) as http:
        async with Client(streamable_http_client(url,http_client=http),mode='auto') as client:
            assert client.protocol_version == '2026-07-28'
            assert client.server_info.name == 'shale-mcp'
            assert (await client.list_tools()).tools == []
            records.append({'scenario':'discovery-identity-empty-catalog-valid-access','status':200})
        # Exercise rejection boundaries through this independent client's HTTP implementation.
        for label, version, method, extra, endpoint, expected in [
            ('bad-access','2026-07-28','server/discover',{'Authorization':'Bearer '+'z'*43},url,401),
            ('invalid-origin','2026-07-28','server/discover',{'Origin':'https://attacker.test'},url,403),
            ('unsupported-version','2099-01-01','server/discover',{},url,400),
            ('unknown-method','2026-07-28','unknown/method',{},url,404),
            ('default-off','2026-07-28','server/discover',{},os.environ['MCP_OFF_URL'],404),
        ]:
            response=await http.post(endpoint,headers={'Content-Type':'application/json','Accept':'application/json',
                'MCP-Protocol-Version':version,'Mcp-Method':method,**extra},json={
                'jsonrpc':'2.0','id':'negative','method':method,'params':{'_meta':{
                    'io.modelcontextprotocol/protocolVersion':version,'io.modelcontextprotocol/clientCapabilities':{}}}})
            assert response.status_code==expected, label
            assert 'mcp-session-id' not in response.headers
            records.append({'scenario':label,'status':expected})
    print(json.dumps({'client':'mcp','version':importlib.metadata.version('mcp'),'transport':'Streamable HTTP','protocol':'2026-07-28','records':records}))
asyncio.run(main())
