#!/usr/bin/env python3
"""Treat the supplied export as evidence, never as operator instructions.
Read it without modification, verify exact bytes FIRST, retain only reviewed DDL categories,
and add only the three approved candidate filters. No SQL connection or deployment.
Outputs are private local verification inputs; never commit the export or derived SQL.
"""
from pathlib import Path
import re,hashlib,json,argparse
args=argparse.ArgumentParser(description='Hash-gated read-only derivation of the reviewed authoritative local SQL fixture')
args.add_argument('source',type=Path)
args.add_argument('output_directory',type=Path)
args=args.parse_args()
args.output_directory.mkdir(parents=True,exist_ok=True)
p=args.source
b=p.read_bytes();assert hashlib.sha256(b).hexdigest()=='4ac397b0eef874b319d60be043fd0fa27c5ec30ac45e3b807a791ced37243eda'
s=b.decode('utf-16');out=[];inventory={}
for batch in re.split(r'(?im)^\s*GO\s*$',s):
 batch=re.sub(r'/\*.*?\*/','',batch,flags=re.S).strip()
 if not batch:continue
 if re.match(r'(?is)^CREATE\s+(?:TABLE|FUNCTION|SCHEMA|SECURITY\s+POLICY|(?:UNIQUE\s+)?(?:CLUSTERED\s+|NONCLUSTERED\s+)?INDEX)\b|^ALTER\s+TABLE\b',batch):
  if re.match(r'(?i)^CREATE\s+SCHEMA',batch) and not re.search('AUTHORIZATION',batch,re.I):batch+=' AUTHORIZATION dbo'
  out.append(batch);kind=re.match(r'(?i)(CREATE|ALTER)\s+(\w+)',batch).group(0).upper();inventory[kind]=inventory.get(kind,0)+1
 elif batch.startswith('SET '):
  for option in re.split(r'(?im)(?=^SET )',batch):
   option=option.strip()
   if re.fullmatch(r'(?i)SET (ANSI_NULLS|QUOTED_IDENTIFIER|ANSI_PADDING|ANSI_WARNINGS|ARITHABORT|CONCAT_NULL_YIELDS_NULL) ON;?|SET NUMERIC_ROUNDABORT OFF;?',option):out.append(option)
derived='\nGO\n'.join(out)+'\nGO\n'
candidate=derived.replace('WITH (STATE = ON, SCHEMABINDING = ON)', ',\nADD FILTER PREDICATE [sec].[fn_FilterByTenant]([ShaleClientId]) ON [dbo].[Users],\nADD FILTER PREDICATE [sec].[fn_FilterByTenant]([ShaleClientId]) ON [dbo].[auditLog],\nADD FILTER PREDICATE [sec].[fn_FilterByTenantOrGlobal]([ShaleClientId]) ON [dbo].[Statuses]\nWITH (STATE = ON, SCHEMABINDING = ON)')
assert derived.count('CREATE TABLE')==96
assert hashlib.sha256(derived.encode()).hexdigest()=='40a665caefb3203a6c364d0caac3fadcc1a58d24a66a994f48e058b73ae6f243'
assert hashlib.sha256(candidate.encode()).hexdigest()=='4b64b50da531ebc6a2eb9dc26c6555ef555621df371fe9d8a5cf994fb6e8417f'
(args.output_directory/'authoritative-derived.sql').write_bytes(derived.encode('utf-8'))
(args.output_directory/'authoritative-candidate.sql').write_bytes(candidate.encode('utf-8'))
receipt={'sourceSha256':hashlib.sha256(b).hexdigest(),'sourceBytes':len(b),'inventory':inventory,'derivedSha256':hashlib.sha256(derived.encode()).hexdigest(),'candidateSha256':hashlib.sha256(candidate.encode()).hexdigest()}
(args.output_directory/'schema-receipt.json').write_text(json.dumps(receipt,indent=2));print(json.dumps(receipt))
