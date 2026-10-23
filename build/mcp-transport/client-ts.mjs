import assert from 'node:assert/strict';
import { Client, StreamableHTTPClientTransport } from '@modelcontextprotocol/client';
const url=process.env.MCP_URL, token=process.env.MCP_TOKEN;
const protocol='2026-07-28';
const records=[];
async function clientTest(label, headers={}, mutate, expected=200, endpoint=url) {
  let lastStatus;
  const client=new Client({name:'shale-acceptance-ts',version:'1'}, {versionNegotiation:{mode:{pin:protocol},probe:{timeoutMs:5000,maxRetries:0}}});
  const transport=new StreamableHTTPClientTransport(new URL(endpoint),{requestInit:{headers:{Authorization:`Bearer ${token}`,...headers}},fetch:async(input,init)=>{
    assert.equal(init.method,'POST');
    const next=mutate ? mutate(init) : init;
    const response=await fetch(input,next);lastStatus=response.status;
    assert.equal(response.headers.get('mcp-session-id'),null);
    return response;
  }});
  let failed=false;
  try {
    await client.connect(transport);
    assert.equal(client.getProtocolEra(),'modern');assert.equal(client.getServerVersion().name,'shale-mcp');
    assert.deepEqual((await client.listTools()).tools,[]);
  } catch { failed=true; }
  finally { await client.close(); }
  assert.equal(lastStatus,expected,label);assert.equal(failed,expected!==200,label);
  records.push({scenario:label,status:lastStatus});
}
await clientTest('discovery-identity-empty-catalog-valid-access');
await clientTest('bad-access',{Authorization:`Bearer ${'z'.repeat(43)}`},undefined,401);
await clientTest('invalid-origin',{Origin:'https://attacker.test'},undefined,403);
await clientTest('unsupported-version',{},init=>{
 const body=JSON.parse(init.body);body.params._meta['io.modelcontextprotocol/protocolVersion']='2099-01-01';
 const headers=new Headers(init.headers);headers.set('MCP-Protocol-Version','2099-01-01');
 return {...init,body:JSON.stringify(body),headers};
},400);
await clientTest('unknown-method',{},init=>{
 const body=JSON.parse(init.body);body.method='unknown/method';
 const headers=new Headers(init.headers);headers.set('Mcp-Method','unknown/method');
 return {...init,body:JSON.stringify(body),headers};
},404);
await clientTest('default-off',{},undefined,404,process.env.MCP_OFF_URL);
console.log(JSON.stringify({client:'@modelcontextprotocol/client',version:'2.4.0',transport:'Streamable HTTP',protocol,records}));
