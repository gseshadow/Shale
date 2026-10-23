import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import { Client, StreamableHTTPClientTransport } from '@modelcontextprotocol/client';
const protocol='2026-07-28', url=process.env.MCP_URL, records=[];
const expected=JSON.parse(readFileSync(new URL('../../shale-server/src/main/resources/mcp/search-cases.json',import.meta.url)));
async function connect(token) {
  const client=new Client({name:'client-metadata-sentinel',version:'1'},{versionNegotiation:{mode:{pin:protocol},probe:{timeoutMs:5000,maxRetries:0}}});
  await client.connect(new StreamableHTTPClientTransport(new URL(url),{requestInit:{headers:{Authorization:`Bearer ${token}`}}}));
  assert.equal(client.getProtocolEra(),'modern');return client;
}
const limited=await connect(process.env.MCP_OVERVIEW_TOKEN);
try {
  assert.deepEqual((await limited.listTools()).tools,[]);
  await assert.rejects(limited.callTool({name:'get_case_overview',arguments:{caseId:100}}));
  await assert.rejects(limited.callTool({name:'search_cases',arguments:{query:'MCP literal'}}));
  records.push('scope-filtered-overview-and-search-absent');
} finally {await limited.close();}
await assert.rejects(connect(process.env.MCP_REVOKED_TOKEN));records.push('revoked-access');
const client=await connect(process.env.MCP_TOKEN);
try {
  assert.deepEqual((await client.listTools()).tools,[expected]);records.push('exact-wire-schema');
  for(const [label,args,page,count,more,window] of [
    ['page-zero',{query:'  MCP literal  '},0,10,true,false],
    ['page-one',{query:'MCP literal',page:1},1,10,true,false],
    ['zero',{query:'no matches',page:2},2,0,false,false],
    ['window',{query:'MCP literal',page:9},9,10,true,true]]) {
    const r=await client.callTool({name:'search_cases',arguments:args});assert.equal(r.isError,false);
    const s=r.structuredContent;assert.deepEqual(Object.keys(s).sort(),['items','page','size','hasMore','windowExhausted','correlationId'].sort());
    assert.equal(s.page,page);assert.equal(s.size,10);assert.equal(s.items.length,count);assert.equal(s.hasMore,more);assert.equal(s.windowExhausted,window);
    assert.deepEqual(s.items.map(item=>item.caseId),Array.from({length:count},(_,n)=>1000+page*10+n));
    assert.match(s.correlationId,/^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/);assert.deepEqual(JSON.parse(r.content[0].text),s);
    assert.deepEqual(r._meta,{'io.modelcontextprotocol/serverInfo':{name:'shale-mcp',version:'development'}});
    for(const item of s.items) {
      assert.deepEqual(Object.keys(item).sort(),expected.outputSchema.$defs.Case.required.toSorted());
      assert.ok(item.caseId>=1000 && item.caseId<=1100);assert.equal(item.status,null);assert.equal(item.practiceArea,null);
      assert.equal(item.responsibleAttorney,null);assert.equal(item.primaryLegalAssistant,null);assert.equal(item.updatedAt,'2026-10-23T11:22:33.1234567');
    }
    records.push(label);
  }
  for(const [label,args,code] of [
    ['short',{query:'ab'},'INVALID_INPUT'],['wildcard',{query:'%_*'},'INVALID_INPUT'],
    ['page-invalid',{query:'MCP literal',page:10},'INVALID_INPUT'],['additional-property',{query:'MCP literal',tenantId:8},'INVALID_INPUT'],
    ['bound',{query:'Oversized'},'PAYLOAD_TOO_LARGE']]) {
    const r=await client.callTool({name:'search_cases',arguments:args});assert.equal(r.isError,true,label);assert.equal(r.structuredContent,undefined);
    assert.equal(JSON.parse(r.content[0].text).code,code,label);records.push(label);
  }
  // Four pages + four invalid attempts + oversized source = nine durable attempts; tenth fills the minute budget.
  await client.callTool({name:'search_cases',arguments:{query:'ab'}});
  const quota=await client.callTool({name:'search_cases',arguments:{query:'MCP literal'}});
  assert.equal(quota.isError,true);assert.equal(JSON.parse(quota.content[0].text).code,'THROTTLED');records.push('quota-exhausted');
  await assert.rejects(client.callTool({name:'get_case_overview',arguments:{caseId:100}}));records.push('overview-uninvokable');
} finally {await client.close();}
console.log(JSON.stringify({client:'@modelcontextprotocol/client',version:'2.4.0',protocol,records}));
