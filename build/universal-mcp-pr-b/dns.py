from pathlib import Path
import socket,time,urllib.parse,os,json
p=Path('/workspace/Shale/work/pr-b-harness')
hosts=['nousresearch.github.io',urllib.parse.urlsplit(os.environ['HTTPS_PROXY']).hostname]
while not (p/'stop-dns').exists():
 for host in hosts:
  answers=list(dict.fromkeys(x[4][0] for x in socket.getaddrinfo(host,443,type=socket.SOCK_STREAM)))
  tmp=p/(host+'.dns.tmp');tmp.write_text('\n'.join(answers));tmp.replace(p/(host+'.dns'))
 time.sleep(15)
