import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import { Client, StreamableHTTPClientTransport } from '@modelcontextprotocol/client';
const protocol='2026-07-28', records=[], url=process.env.MCP_URL;
const search=JSON.parse(readFileSync(new URL('../../shale-server/src/main/resources/mcp/search-cases.json',import.meta.url)));
const overview=JSON.parse(readFileSync(new URL('../../shale-server/src/main/resources/mcp/get-case-overview.json',import.meta.url)));
overview.outputSchema.$defs=search.outputSchema.$defs;
async function connect(token) {
  const c=new Client({name:'client-metadata-sentinel',version:'1'},{versionNegotiation:{mode:{pin:protocol},probe:{timeoutMs:5000,maxRetries:0}}});
  await c.connect(new StreamableHTTPClientTransport(new URL(url),{requestInit:{headers:{Authorization:`Bearer ${token}`}}}));
  assert.equal(c.getProtocolEra(),'modern');return c;
}
for(const [key,expected] of [['SEARCH_TOKEN',[search]],['BOTH_TOKEN',[search,overview]]]) {
  const c=await connect(process.env['MCP_'+key]);try {assert.deepEqual((await c.listTools()).tools,expected);records.push(key+'-exact-schema');
    if(key==='SEARCH_TOKEN')await assert.rejects(c.callTool({name:'get_case_overview',arguments:{caseId:100}}));
  } finally {await c.close();}
}
await assert.rejects(connect(process.env.MCP_REVOKED_TOKEN));records.push('revoked-authority');
function error(r,code) {
  assert.equal(r.isError,true);assert.equal(r.structuredContent,undefined);assert.equal(r.content.length,1);
  const v=JSON.parse(r.content[0].text);assert.deepEqual(Object.keys(v).sort(),['code','correlationId','retryAfterSeconds']);assert.equal(v.code,code);
  assert.match(v.correlationId,/^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/);assert.equal(v.retryAfterSeconds,code==='THROTTLED'?1:null);return v;
}
const c=await connect(process.env.MCP_OVERVIEW_TOKEN);
try {
  assert.deepEqual((await c.listTools()).tools,[overview]);records.push('overview-only-exact-schema');
  await assert.rejects(c.callTool({name:'search_cases',arguments:{query:'MCP literal'}}));records.push('search-absent');
  for(const id of [100,1000]) {
    const r=await c.callTool({name:'get_case_overview',arguments:{caseId:id}});assert.equal(r.isError,false);assert.equal(r.content.length,1);
    const s=r.structuredContent;assert.deepEqual(Object.keys(s).sort(),['case','correlationId']);assert.deepEqual(JSON.parse(r.content[0].text),s);
    assert.deepEqual(Object.keys(s.case).sort(),overview.outputSchema.$defs.Case.required.toSorted());assert.equal(s.case.caseId,id);
    for(const key of ['status','practiceArea','responsibleAttorney','primaryLegalAssistant'])assert.equal(s.case[key],null);
    assert.equal(s.case.updatedAt,id===100?null:'2026-10-23T11:22:33.123456700');
    assert.ok(Buffer.byteLength(r.content[0].text)<=8192);records.push(id===100?'direct-overview-no-search':'same-tenant-unassigned');
  }
  for(const [id,code,label] of [[999,'CASE_UNAVAILABLE','missing'],[9001,'CASE_UNAVAILABLE','foreign'],[9002,'CASE_UNAVAILABLE','deleted'],[9000,'PAYLOAD_TOO_LARGE','source-bound'],[9003,'AUDIT_UNAVAILABLE','phi-append-rollback']]) {
    error(await c.callTool({name:'get_case_overview',arguments:{caseId:id}}),code);records.push(label);
  }
  for(let n=0;n<3;n++)error(await c.callTool({name:'get_case_overview',arguments:{caseId:0}}),'INVALID_INPUT');
  error(await c.callTool({name:'get_case_overview',arguments:{caseId:100}}),'THROTTLED');records.push('quota-exhausted-no-refund');
} finally {await c.close();}
const invalid=await connect(process.env.MCP_INVALID_TOKEN);
try {
  for(const args of [{},{caseId:null},{caseId:0},{caseId:-1},{caseId:2147483648},{caseId:'100'},{caseId:1.5},{caseId:100,tenantId:8},{caseId:100,userId:71}]) {
    error(await invalid.callTool({name:'get_case_overview',arguments:args}),'INVALID_INPUT');records.push('strict-invalid-'+records.length);
  }
} finally {await invalid.close();}
console.log(JSON.stringify({client:'@modelcontextprotocol/client',version:'2.4.0',protocol,records}));
