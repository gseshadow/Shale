import http.server,http.client,ssl,threading,socketserver,socket,select,json,time
from pathlib import Path
from urllib.parse import urlsplit
root=Path('/workspace/Shale');p=root/'work/pr-c2-harness'
lock=threading.Lock()
class Front(http.server.BaseHTTPRequestHandler):
 def log_message(self,*a):pass
 def do_GET(self):self.serve()
 def do_POST(self):self.serve()
 def do_OPTIONS(self):self.serve()
 def serve(self):
  u=urlsplit(self.path);accept=self.headers.get('Accept','')
  static=self.command=='GET' and not u.path.startswith(('/api/','/.well-known/','/mcp')) and not (u.path.startswith('/oauth/') and (u.query or accept=='application/json'))
  if static:
   name=u.path.lstrip('/') if u.path.startswith('/assets/') else 'index.html'
   f=root/'shale-web/dist'/name
   if not f.exists():self.send_error(404);return
   b=f.read_bytes();self.send_response(200);self.send_header('Content-Type','text/javascript' if name.endswith('.js') else 'text/css' if name.endswith('.css') else 'text/html');self.send_header('Content-Length',str(len(b)));self.send_header('Cache-Control','no-store');self.end_headers();self.wfile.write(b);return
  b=self.rfile.read(int(self.headers.get('Content-Length','0')))
  h={k:v for k,v in self.headers.items() if k.lower() not in ('host','connection','accept-encoding','content-length')};h['Host']='shale.test'
  safe={}
  if u.path=='/mcp':
   try:
    rpc=json.loads(b);method=rpc.get('method');allowed={'server/discover','initialize','tools/list','tools/call','notifications/initialized'}
    params=rpc.get('params') or {};meta=params.get('_meta') or {}
    safe={'rpc_method':method if method in allowed else 'OTHER','header_method':self.headers.get('Mcp-Method'),'header_protocol':self.headers.get('MCP-Protocol-Version'),'body_protocol':meta.get('io.modelcontextprotocol/protocolVersion'),'header_name':self.headers.get('Mcp-Name')}
   except Exception:pass
  if u.path=='/oauth/mcp/register':
   try:
    meta=json.loads(b);safe['registration_request']={k:v for k,v in meta.items() if k in ('client_name','redirect_uris','grant_types','response_types','scope','token_endpoint_auth_method','software_id','software_version','application_type')}
   except Exception:pass
  start=time.monotonic();c=http.client.HTTPConnection('127.0.0.1',18080,timeout=15)
  try:
   c.request(self.command,self.path,b,h);r=c.getresponse();data=r.read()
   if u.path=='/oauth/mcp/register' and r.status==201:
    registered=json.loads(data);registered['generated_client_id_valid']=bool(__import__('re').fullmatch(r'dcr\.[0-9a-f]{64}',registered.get('client_id','')));registered['client_id']='<generated-dcr-id>';safe['registration_response']=registered
   if u.path=='/.well-known/oauth-authorization-server/oauth/mcp' and r.status==200:
    safe['metadata']=json.loads(data)
   self.send_response(r.status)
   for k,v in r.getheaders():
    if k.lower() not in ('transfer-encoding','connection','content-length'):self.send_header(k,v)
   self.send_header('Content-Length',str(len(data)));self.end_headers();self.wfile.write(data)
   with lock,open(p/'http-evidence.jsonl','a') as f:f.write(json.dumps({'method':self.command,'path':u.path,'status':r.status,'ms':round((time.monotonic()-start)*1000),'phase':(p/'phase').read_text().strip() if (p/'phase').exists() else 'initial',**safe})+'\n')
  except Exception:self.send_error(502)
  finally:c.close()
class Proxy(socketserver.StreamRequestHandler):
 def handle(self):
  first=self.rfile.readline().decode().strip();h=[]
  while self.rfile.readline() not in (b'\r\n',b'\n',b''):pass
  if first!='CONNECT shale.test:443 HTTP/1.1':self.wfile.write(b'HTTP/1.1 403 Forbidden\r\n\r\n');return
  with socket.create_connection(('127.0.0.1',443)) as s:
   self.wfile.write(b'HTTP/1.1 200 Connection established\r\n\r\n');self.wfile.flush()
   while True:
    readable,_,_=select.select([s,self.connection],[],[],30)
    if not readable:return
    for source in readable:
     b=source.recv(65536)
     if not b:return
     (self.connection if source is s else s).sendall(b)
proxy=socketserver.ThreadingTCPServer(('127.0.0.1',18081),Proxy);proxy.daemon_threads=True
threading.Thread(target=proxy.serve_forever,daemon=True).start()
s=http.server.ThreadingHTTPServer(('127.0.0.1',443),Front)
c=ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER);c.load_cert_chain(p/'web.crt',p/'web.key');s.socket=c.wrap_socket(s.socket,server_side=True)
print('Disposable TLS frontend and scoped CONNECT proxy ready',flush=True);s.serve_forever()
