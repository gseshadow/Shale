"""Preserve only closed protocol/timing metadata before deleting a private Hermes profile."""
import json
import math
from pathlib import Path
import re
import sys
from datetime import datetime, timezone

EVENTS={"LEGACY_TO_DISCOVER":"legacy handshake rejected", "DISCOVER_MENTION":"server/discover",
        "CONNECT_RETRY":"initial connection failed", "READ_TIMEOUT":"ReadTimeout",
        "CONNECT_TIMEOUT":"ConnectTimeout", "SCHEMA_REJECTED":"ValidationError",
        "PROTOCOL_MISMATCH":"Header mismatch", "HTTP_UNAUTHORIZED":"401 Unauthorized"}

def summarize(run):
    profile=run/'hermes-private'
    result={"Scope":"Closed client metadata only; no tokens, identifiers, request URLs or raw logs", "ProfilePresent":profile.exists(), "Events":[], "TokenMetadata":{}}
    for name in ('agent.log','errors.log'):
        path=profile/'logs'/name
        if not path.is_file():continue
        with path.open('rb') as source:
            source.seek(max(0,path.stat().st_size-1048576));text=source.read().decode('utf-8','replace')
        for line in text.splitlines():
            stamp=re.match(r'^(\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}:\d{2}(?:[.,]\d+)?)',line)
            for code,phrase in EVENTS.items():
                if phrase in line and len(result['Events'])<128:
                    result['Events'].append({"Code":code,"LogTime":stamp.group(1) if stamp else None})
    # Only expiry/lifetime/presence fields leave this process. Credentials remain in the
    # existing private SDK file and memory and are never copied into the diagnostic report.
    token=profile/'mcp-tokens/shale-acceptance.json'
    if token.is_file() and token.stat().st_size<=65536:
        data=json.loads(token.read_text(encoding='utf-8'))
        safe={"FileModifiedUtc":datetime.fromtimestamp(token.stat().st_mtime,timezone.utc).isoformat(),"RefreshPresent":bool(data.get('refresh_token'))}
        for key,minimum,maximum in (('expires_in',0,86400),('expires_at',946684800,4102444800)):
            value=data.get(key)
            if isinstance(value,(float,int)) and not isinstance(value,bool) and math.isfinite(value) and minimum<=value<=maximum:safe[key]=value
        result['TokenMetadata']=safe
    return result

if __name__=='__main__':
    run=Path(sys.argv[1]).resolve()
    (run/'client-summary.json').write_text(json.dumps(summarize(run),indent=2),encoding='utf-8')
