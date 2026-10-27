import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {createInterface} from 'node:readline';
import http from 'node:http';
import {Client,StreamableHTTPClientTransport} from '@modelcontextprotocol/client';
const corpus=JSON.parse(readFileSync(new URL('./acceptance-corpus.json',import.meta.url)));
const lines=createInterface({input:process.stdin})[Symbol.asyncIterator]();
const records=[];let current='';
const group=process.env.MCP_ACCEPTANCE_GROUP??'all';const vectors=corpus.vectors.filter(v=>group==='all'||(group==='faults')===(v.action==='fault'||v.id==='reuse-after-failures'));
async function prepare(id,stage='prepare') {console.log(JSON.stringify({control:stage,id}));return JSON.parse((await lines.next()).value);}
async function raw(token,id) {
 const auth=id.endsWith('duplicate')?[`Bearer ${token}`,`Bearer ${token}`]:id.endsWith('malformed')?['Bearer abc']:id.endsWith('oversized')?['Bearer '+'a'.repeat(101)]:[`Bearer ${token}`];
 const body=JSON.stringify({jsonrpc:'2.0',id:'acceptance',method:'server/discover',params:{_meta:{'io.modelcontextprotocol/protocolVersion':corpus.protocol,'io.modelcontextprotocol/clientCapabilities':{}}}});
 return new Promise((resolve,reject)=>{
  const headers=['Host',new URL(process.env.MCP_URL).host,'Content-Type','application/json','Accept','application/json','MCP-Protocol-Version',corpus.protocol,'Mcp-Method','server/discover','Content-Length',String(Buffer.byteLength(body))];
  for(const value of auth)headers.push('Authorization',value);
  const req=http.request(process.env.MCP_URL,{method:'POST',headers},res=>{let b='';res.on('data',x=>b+=x);res.on('end',()=>resolve({status:res.statusCode,body:b}));});
  req.on('error',reject);req.end(body);
 });
}
function check(r,v) {
 if(v.expected===v.expected.toUpperCase()) {assert.equal(r.isError,true);assert.equal(r.structuredContent,undefined);assert.equal(JSON.parse(r.content[0].text).code,v.expected);return;}
 assert.equal(r.isError,false);const s=r.structuredContent;assert.deepEqual(JSON.parse(r.content[0].text),s);
 if(v.expected==='page0'||v.expected==='page9') {const page=v.expected==='page0'?0:9;assert.deepEqual(s.items.map(x=>x.caseId),Array.from({length:10},(_,n)=>1000+page*10+n));assert.equal(s.page,page);assert.equal(s.hasMore,true);assert.equal(s.windowExhausted,page===9);}
 if(v.expected==='zero'){assert.deepEqual(s.items,[]);assert.equal(s.hasMore,false);}
 if(v.expected==='literal')assert.deepEqual(s.items.map(x=>x.caseId),[9004]);
 if(v.expected==='unicode')assert.deepEqual(s.items.map(x=>x.caseId),[9006]);
 if(v.expected==='overview'){assert.equal(s.case.caseId,v.arguments.caseId);assert.deepEqual(Object.keys(s.case).sort(),['caseId','caseName','caseNumber','practiceArea','primaryLegalAssistant','responsibleAttorney','status','updatedAt'].sort());assert.equal(s.case.responsibleAttorney,null);}
}
try {
 for(const v of vectors) {
  current=v.id;const {token}=await prepare(v.id);let elapsed=performance.now();
  if(v.action==='raw'){const r=await raw(token,v.id);assert.equal(r.status,v.status);if(r.status!==200){assert.ok(!r.body.includes('structuredContent'));assert.ok(!r.body.includes('Direct overview'));}}
  else {
   const args={...v.arguments};if(args.query==='$ACCESS_SENTINEL')args.query='ACCESS '+token;
   const c=new Client({name:'shale-local-acceptance',version:'1'},{versionNegotiation:{mode:{pin:corpus.protocol},probe:{timeoutMs:10000,maxRetries:0}}});
   try {
    await c.connect(new StreamableHTTPClientTransport(new URL(process.env.MCP_URL),{requestInit:{headers:{Authorization:`Bearer ${token}`}}}));
    if(v.action==='catalog')assert.deepEqual((await c.listTools()).tools.map(x=>x.name),v.names);
    else if(v.action==='call')check(await c.callTool({name:v.tool,arguments:args}),v);
    else if(v.action==='fault'){let result,closed=false;try{result=await c.callTool({name:v.tool,arguments:args});}catch{closed=true;}if(!closed){assert.equal(result.isError,true);assert.equal(result.structuredContent,undefined);assert.ok((v.id==='fault-quota'?['FORBIDDEN']:['UNAVAILABLE','TIMEOUT','AUDIT_UNAVAILABLE']).includes(JSON.parse(result.content[0].text).code),'closed code '+JSON.parse(result.content[0].text).code);}}
    else {assert.equal((await c.listTools()).tools.length,2);await prepare(v.id,'reduce');await assert.rejects(()=>c.listTools());assert.equal((await raw(token,v.id)).status,401);}
   } finally {await c.close();}
  }
  records.push({id:v.id,status:'PASS',elapsedMillis:performance.now()-elapsed});console.log(JSON.stringify({record:records.at(-1)}));
 }
 console.log(JSON.stringify({complete:true,group,client:'@modelcontextprotocol/client',version:'2.4.0',protocol:corpus.protocol,records}));
} catch(error) {console.error('ACCEPTANCE_FAILED '+current+' '+error.name+': '+error.message);process.exitCode=1;}
finally {process.stdin.destroy();}
