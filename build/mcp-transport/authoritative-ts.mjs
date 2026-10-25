import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {Client,StreamableHTTPClientTransport} from '@modelcontextprotocol/client';
const protocol='2026-07-28',records=[];
const search=JSON.parse(readFileSync(new URL('../../shale-server/src/main/resources/mcp/search-cases.json',import.meta.url)));
const overview=JSON.parse(readFileSync(new URL('../../shale-server/src/main/resources/mcp/get-case-overview.json',import.meta.url)));
overview.outputSchema.$defs=search.outputSchema.$defs;
async function connect(key){const c=new Client({name:'authoritative-local',version:'1'},{versionNegotiation:{mode:{pin:protocol},probe:{timeoutMs:5000,maxRetries:0}}});await c.connect(new StreamableHTTPClientTransport(new URL(process.env.MCP_URL),{requestInit:{headers:{Authorization:`Bearer ${process.env['MCP_'+key]}`}}}));assert.equal(c.getProtocolEra(),'modern');return c;}
function error(r,code){assert.equal(r.isError,true);assert.equal(r.structuredContent,undefined);assert.equal(JSON.parse(r.content[0].text).code,code);records.push(code);}
function value(r){assert.equal(r.isError,false);assert.deepEqual(JSON.parse(r.content[0].text),r.structuredContent);return r.structuredContent;}
function item(c){assert.deepEqual(Object.keys(c).sort(),search.outputSchema.$defs.Case.required.toSorted());assert.ok(c.caseId!==9001&&c.caseId!==9002);}
for(const [key,expected] of [['SEARCH_TOKEN',[search]],['OVERVIEW_TOKEN',[overview]],['BOTH_TOKEN',[search,overview]]]){const c=await connect(key);try{assert.deepEqual((await c.listTools()).tools,expected);records.push(key+'-catalog');}finally{await c.close();}}
await assert.rejects(connect('REVOKED_TOKEN'));records.push('revoked-access');
let c=await connect('SEARCH_TOKEN');
try{
 for(const [page,ids,more,window] of [[0,Array.from({length:10},(_,n)=>1000+n),true,false],[9,Array.from({length:10},(_,n)=>1090+n),true,true]]){
  const s=value(await c.callTool({name:'search_cases',arguments:{query:'MCP literal',page}}));assert.deepEqual(s.items.map(x=>x.caseId),ids);assert.equal(s.hasMore,more);assert.equal(s.windowExhausted,window);s.items.forEach(item);records.push('page-'+page+'-eleventh-row');
 }
 const zero=value(await c.callTool({name:'search_cases',arguments:{query:'no matches'}}));assert.deepEqual(zero.items,[]);assert.equal(zero.hasMore,false);records.push('zero-results');
 error(await c.callTool({name:'search_cases',arguments:{query:''}}),'INVALID_INPUT');
 error(await c.callTool({name:'search_cases',arguments:{query:'MCP literal',tenantId:8}}),'INVALID_INPUT');
}finally{await c.close();}
c=await connect('BOTH_TOKEN');
try{
 const literal=value(await c.callTool({name:'search_cases',arguments:{query:'literal %_['}}));assert.deepEqual(literal.items.map(x=>x.caseId),[9004]);records.push('literal-wildcard-escaping');
 const shared=value(await c.callTool({name:'get_case_overview',arguments:{caseId:1000}}));item(shared.case);records.push('both-tools-same-server');
 error(await c.callTool({name:'search_cases',arguments:{query:'Oversized'}}),'PAYLOAD_TOO_LARGE');
}finally{await c.close();}
c=await connect('OVERVIEW_TOKEN');
try{
 for(const id of [100,1000]){const s=value(await c.callTool({name:'get_case_overview',arguments:{caseId:id}}));item(s.case);assert.equal(s.case.caseId,id);assert.equal(s.case.responsibleAttorney,null);records.push('same-tenant-unassigned-'+id);}
 for(const id of [9001,999,9002])error(await c.callTool({name:'get_case_overview',arguments:{caseId:id}}),'CASE_UNAVAILABLE');
}finally{await c.close();}
c=await connect('FAULT_TOKEN');
try{error(await c.callTool({name:'get_case_overview',arguments:{caseId:9003}}),'AUDIT_UNAVAILABLE');}finally{await c.close();}
console.log(JSON.stringify({client:'@modelcontextprotocol/client',version:'2.4.0',protocol,phase:'core-matrix',records:[...records]}));
for(const [key,name,args] of [['RACE_REVOKE_TOKEN','search_cases',{query:'MCP literal'}],['RACE_EXPIRY_TOKEN','get_case_overview',{caseId:100}]]){const r=await connect(key);try{const result=await r.callTool({name,arguments:args});assert.equal(result.isError,true,key);error(result,'FORBIDDEN');records.push(key+'-post-commit-final-gate');console.log(JSON.stringify({phase:key,records:[...records]}));}finally{await r.close();}}
console.log(JSON.stringify({client:'@modelcontextprotocol/client',version:'2.4.0',protocol,records}));
