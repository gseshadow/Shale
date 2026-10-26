import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {Client,StreamableHTTPClientTransport} from '@modelcontextprotocol/client';
const search=JSON.parse(readFileSync(new URL('../../shale-server/src/main/resources/mcp/search-cases.json',import.meta.url)));
const overview=JSON.parse(readFileSync(new URL('../../shale-server/src/main/resources/mcp/get-case-overview.json',import.meta.url)));
overview.outputSchema.$defs=search.outputSchema.$defs;
const client=new Client({name:'confined-sql-regression',version:'1'},{versionNegotiation:{mode:{pin:'2026-07-28'},probe:{timeoutMs:10000,maxRetries:0}}});
try {
 await client.connect(new StreamableHTTPClientTransport(new URL(process.env.MCP_URL),{requestInit:{headers:{Authorization:`Bearer ${process.env.MCP_TOKEN}`}}}));
 assert.deepEqual((await client.listTools()).tools,[search,overview]);
 const page=await client.callTool({name:'search_cases',arguments:{query:'MCP literal',page:0}});
 assert.equal(page.isError,false);assert.equal(page.structuredContent.items.length,10);assert.equal(page.structuredContent.size,10);assert.equal(page.structuredContent.hasMore,true);assert.equal(page.structuredContent.windowExhausted,false);
 assert.deepEqual(JSON.parse(page.content[0].text),page.structuredContent);
 const read=await client.callTool({name:'get_case_overview',arguments:{caseId:100}});
 assert.equal(read.isError,false);assert.equal(read.structuredContent.case.caseId,100);assert.deepEqual(Object.keys(read.structuredContent.case).sort(),search.outputSchema.$defs.Case.required.toSorted());assert.deepEqual(JSON.parse(read.content[0].text),read.structuredContent);
 const foreign=await client.callTool({name:'get_case_overview',arguments:{caseId:9001}});
 assert.equal(foreign.isError,true);assert.equal(foreign.structuredContent,undefined);assert.equal(JSON.parse(foreign.content[0].text).code,'CASE_UNAVAILABLE');
 const expired=new Client({name:'confined-expiry',version:'1'},{versionNegotiation:{mode:{pin:'2026-07-28'},probe:{timeoutMs:10000,maxRetries:0}}});
 try {
  await expired.connect(new StreamableHTTPClientTransport(new URL(process.env.MCP_URL),{requestInit:{headers:{Authorization:`Bearer ${process.env.MCP_EXPIRY_TOKEN}`}}}));
  const denied=await expired.callTool({name:'get_case_overview',arguments:{caseId:100}});
  assert.equal(denied.isError,true);assert.equal(denied.structuredContent,undefined);assert.equal(JSON.parse(denied.content[0].text).code,'FORBIDDEN');
 } finally {await expired.close();}
 console.log(JSON.stringify({client:'@modelcontextprotocol/client',version:'2.4.0',protocol:'2026-07-28',schema:true,search:true,overview:true,foreignUnavailable:true,expiryDenied:true}));
} finally {await client.close();}
