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
// Some current scenarios deliberately use Node's raw HTTP client to control header bytes.
// Inject the same fixture bearer there as well; never change a different origin/path.
import http from 'node:http';
import https from 'node:https';
import {syncBuiltinESMExports} from 'node:module';
for(const [module,protocol] of [[http,'http:'],[https,'https:']]) {
 const request=module.request;
 module.request=function(...args) {
  let index=0,options,url;
  if(typeof args[0]==='string'||args[0] instanceof URL) {
   url=new URL(args[0]);index=1;options=typeof args[1]==='object'?{...args[1]}:{};
   if(typeof args[1]==='function')args.splice(1,0,options);else args[1]=options;
  } else {
   options={...args[0]};args[0]=options;
   url=new URL(`${options.protocol??protocol}//${options.hostname??options.host??'localhost'}:${options.port??(protocol==='https:'?443:80)}${options.path??'/'}`);
  }
  if(url.origin===endpoint.origin&&url.pathname===endpoint.pathname) {
   if(Array.isArray(options.headers)) {
    const headers=[];for(let n=0;n<options.headers.length;n+=2)if(options.headers[n].toLowerCase()!=='authorization')headers.push(options.headers[n],options.headers[n+1]);
    headers.push('Authorization',`Bearer ${process.env.MCP_TOKEN}`);options.headers=headers;
   } else {
    options.headers={...options.headers};for(const key of Object.keys(options.headers))if(key.toLowerCase()==='authorization')delete options.headers[key];
    options.headers.Authorization=`Bearer ${process.env.MCP_TOKEN}`;
   }
   args[index]=options;
  }
  return request.apply(this,args);
 };
}
syncBuiltinESMExports();
