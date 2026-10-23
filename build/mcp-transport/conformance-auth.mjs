// Test-only authentication injection: the upstream CLI has no bearer option.
// Only the exact task-owned loopback endpoint receives the synthetic test bearer.
const endpoint=new URL(process.env.MCP_URL);
if(endpoint.hostname!=='127.0.0.1')throw new Error('Loopback conformance endpoint required');
const original=globalThis.fetch;
globalThis.fetch=(input,init={})=>{
 const url=new URL(typeof input==='string'||input instanceof URL?input:input.url);
 if(url.origin===endpoint.origin && url.pathname===endpoint.pathname) {
  const headers=new Headers(init.headers ?? (input instanceof Request?input.headers:undefined));
  headers.set('Authorization',`Bearer ${process.env.MCP_TOKEN}`);
  return original(input,{...init,headers});
 }
 return original(input,init);
};
