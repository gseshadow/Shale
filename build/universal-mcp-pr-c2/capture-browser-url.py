"""Headless test opener: open the stock CLI's own URL without logging state/code values."""
import re,sys,time
from pathlib import Path
from urllib.parse import urlsplit,parse_qs
source=Path(sys.argv[1]);label=sys.argv[2]
for _ in range(120):
 if source.exists():
  m=re.search(r'https://shale\.test/oauth/mcp/authorize\?[^\s\x1b]+',source.read_text())
  if m:
   assert {'client_id','state','redirect_uri','code_challenge'}<=parse_qs(urlsplit(m[0]).query).keys()
   p=Path('/workspace/Shale/work/pr-c2-harness')/('browser-url-'+label);p.write_text(m[0]);p.chmod(0o600)
   print('Captured stock browser URL; values remain private');break
 time.sleep(.5)
else:raise SystemExit('Stock client did not produce an authorization URL')
