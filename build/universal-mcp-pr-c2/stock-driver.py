"""Ordinary JSON-RPC stdio host for the unmodified mcp-remote CLI. No OAuth implementation."""
import asyncio,os,json,re,sys
from pathlib import Path
ROOT=Path('/workspace/Shale');p=ROOT/'work/pr-c2-harness';tenant=sys.argv[1] if len(sys.argv)>1 else 'a'
auto=len(sys.argv)>2 and sys.argv[2]=='auto';label=tenant+('-auto' if auto else '')
async def main():
 profile=ROOT/f'work/pr-c2-profile-{label}';fresh=not profile.exists() or not any(profile.iterdir());profile.mkdir(mode=0o700,exist_ok=True)
 env=os.environ.copy();env.update(MCP_REMOTE_CONFIG_DIR=str(profile),NODE_EXTRA_CA_CERTS=str(p/'ca.crt'),HTTP_PROXY='http://127.0.0.1:18081',HTTPS_PROXY='http://127.0.0.1:18081',http_proxy='http://127.0.0.1:18081',https_proxy='http://127.0.0.1:18081')
 args=['node',str(ROOT/'work/pr-c2-client/node_modules/mcp-remote/dist/proxy.js'),'https://shale.test/mcp','--enable-proxy']
 if auto:args+=['--protocol','auto']
 proc=await asyncio.create_subprocess_exec(*args,stdin=asyncio.subprocess.PIPE,stdout=asyncio.subprocess.PIPE,stderr=asyncio.subprocess.PIPE,env=env)
 futures={};messages=[]
 async def stderr():
  with (p/f'client-{label}-private.log').open('wb') as log:
   async for line in proc.stderr:
    log.write(line);log.flush()
    m=re.search(rb'https://shale\.test/oauth/mcp/authorize\?[^\s]+',line)
    if m:
     f=p/f'browser-url-{label}';f.write_bytes(m[0]);f.chmod(0o600)
 async def stdout():
  async for line in proc.stdout:
   try:m=json.loads(line)
   except Exception:continue
   if m.get('id') in futures and not futures[m['id']].done():futures[m['id']].set_result(m)
 async def request(i,method,params):
  futures[i]=asyncio.get_running_loop().create_future()
  proc.stdin.write((json.dumps({'jsonrpc':'2.0','id':i,'method':method,'params':params})+'\n').encode());await proc.stdin.drain()
  m=await asyncio.wait_for(futures[i],300)
  if 'error' in m:raise RuntimeError('Stock stdio RPC failed: '+str(m['error'].get('code')))
  return m['result']
 readers=[asyncio.create_task(stderr()),asyncio.create_task(stdout())]
 try:
  init=await request(1,'initialize',{'protocolVersion':'2026-07-28','capabilities':{},'clientInfo':{'name':'Shale acceptance stdio host','version':'1'}})
  proc.stdin.write(b'{"jsonrpc":"2.0","method":"notifications/initialized"}\n');await proc.stdin.drain()
  tools=await request(2,'tools/list',{});messages.append({'tools':[t['name'] for t in tools['tools']]})
  case=100 if tenant=='a' else 200
  for i,name,args in [(3,'search_cases',{'query':'Literal','page':0}),(4,'get_case_overview',{'caseId':case})]:
   result=await request(i,'tools/call',{'name':name,'arguments':args})
   assert not result.get('isError'), 'Authorized Case tool failed'
   messages.append({'tool':name,'isError':result.get('isError',False),'content':result.get('content',[])})
  (p/f'tools-{label}.json').write_text(json.dumps({'client':'mcp-remote','version':'0.16.6','command':['mcp-remote','https://shale.test/mcp','--enable-proxy']+(['--protocol','auto'] if auto else []),'fresh_profile':fresh,'url_only':not auto,'registration_method':'DCR','results':messages},indent=2))
  print('Stock client',tenant,'tools completed')
 finally:
  if proc.returncode is None:proc.terminate()
  try:await asyncio.wait_for(proc.wait(),10)
  except asyncio.TimeoutError:proc.kill();await proc.wait()
  await asyncio.gather(*readers,return_exceptions=True)
asyncio.run(main())
