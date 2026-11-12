#!/usr/bin/env python3
"""Package the existing new-namespace checks without duplicating predecessor checks.

AiCimdCaseData runs unchanged predecessor attestation first. No verifier body is
edited: only the two complete additive sections are packaged for metadata-only use.
"""
import argparse
import hashlib
import re
from pathlib import Path

root = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser()
parser.add_argument('--check', action='store_true')
args = parser.parse_args()
sections = []
for filename, marker in [('universal-pr4a-proof-verify.sql', 'DECLARE @C4aObjects'),
                         ('universal-pr4b-oauth-verify.sql', 'DECLARE @C4bObjects')]:
    source = (root / 'docs/sql/ai-integration' / filename).read_text()
    section = source[source.index(marker):]
    # Exact immutable metadata is read through one fixed source-checked accessor.
    # Historical grants/fingerprints and all comparisons remain unchanged.
    binding = "(SELECT CONCAT(Id,Issuer,Resource,Contract,JavaTimeZone,SqlTimeZone) AS [text()] FROM dbo.AiCaseDataDeployment ORDER BY Id FOR XML PATH(''),TYPE).value('.','nvarchar(max)')"
    assert binding in section
    section = section.replace(binding, '(SELECT BindingState FROM @CimdEvidence)')
    proofstate = "(SELECT CONCAT(Id,CONVERT(varchar(64),SourceHash,2),CONVERT(varchar(64),BeforeHash,2),CONVERT(varchar(64),CatalogHash,2)) AS [text()] FROM dbo.AiCimdProofReceiptV1 ORDER BY Id FOR XML PATH(''),TYPE).value('.','nvarchar(max)')"
    if filename == 'universal-pr4b-oauth-verify.sql':
        assert proofstate in section
        section = section.replace(proofstate, '(SELECT ProofState FROM @CimdEvidence)')
    for table, prefix in [('AiCimdProofReceiptV1', 'Proof'), ('AiCimdOAuthReceiptV1', 'OAuth')]:
        for column, suffix in [('SourceHash', 'Source'), ('BeforeHash', 'Before'), ('CatalogHash', 'Catalog')]:
            section = section.replace('(SELECT '+column+' FROM dbo.'+table+' WHERE Id=1)',
                                      '(SELECT '+prefix+suffix+' FROM @CimdEvidence)')
    sections.append(section)
helper = (root / 'build/sql-confinement/cimd-authorization-receipts.sql').read_text().strip()
digest = hashlib.sha256(helper.encode('utf-16le')).hexdigest()
prefix = f"""IF NOT EXISTS(SELECT 1 FROM sys.sql_modules m JOIN sys.objects o ON o.object_id=m.object_id JOIN sys.schemas s ON s.schema_id=o.schema_id
 WHERE o.object_id=OBJECT_ID(N'dbo.AiCimdReadAuthorizationReceiptsV1') AND o.type='P'
 AND COALESCE(o.principal_id,s.principal_id)=1 AND m.execute_as_principal_id=-2
 AND m.uses_ansi_nulls=1 AND m.uses_quoted_identifier=1 AND HASHBYTES('SHA2_256',m.definition)=0x{digest})
 OR EXISTS(SELECT 1 FROM sys.crypt_properties WHERE major_id=OBJECT_ID(N'dbo.AiCimdReadAuthorizationReceiptsV1'))
 THROW 59520,'Metadata accessor source/owner/context drift.',1;
DECLARE @CimdEvidence table(BindingState nvarchar(max),ProofState nvarchar(max),ProofSource binary(32),ProofBefore binary(32),ProofCatalog binary(32),OAuthSource binary(32),OAuthBefore binary(32),OAuthCatalog binary(32));
INSERT @CimdEvidence EXEC dbo.AiCimdReadAuthorizationReceiptsV1;
IF (SELECT COUNT(*) FROM @CimdEvidence)<>1 THROW 59520,'Immutable evidence missing.',1;
"""
text = ('/* Generated additive CIMD checks. Run unchanged predecessor attestation first. */\n'
        'SET NOCOUNT ON;\n' + prefix + '\n'.join(sections))
target = root / 'shale-data/src/main/resources/ai/cimd-authorization-v1-catalog.sql'
if args.check:
    if not target.exists() or target.read_text() != text:
        raise SystemExit('Stale CIMD authorization catalog resource')
else:
    target.write_text(text)
installer = root / 'docs/sql/ai-integration/universal-pr-a-reviewer.sql'
install = installer.read_text()
generated = "/* GENERATED_RECEIPT_ACCESSOR */\n DECLARE @accessor nvarchar(max)=CONVERT(nvarchar(max),0x"+helper.encode('utf-16le').hex()+");\n EXEC sys.sp_executesql @accessor;\n /* END_GENERATED_RECEIPT_ACCESSOR */"
if '/* END_GENERATED_RECEIPT_ACCESSOR */' in install:
    install = re.sub(r'/\* GENERATED_RECEIPT_ACCESSOR \*/.*?/\* END_GENERATED_RECEIPT_ACCESSOR \*/', lambda _: generated, install, flags=re.S)
else:
    install = install.replace('/* GENERATED_RECEIPT_ACCESSOR */', generated)
if args.check:
    if installer.read_text() != install:
        raise SystemExit('Stale metadata accessor installer')
else:
    installer.write_text(install)
