#!/usr/bin/env python3
"""Generate ONLY PR C1 successor artifacts. Historical files are read-only inputs.

The predecessor projection is limited to the reviewed source constraint and three
locator definitions. Actual successor definitions are pinned before projection.
No failed verifier is caught, and no historical receipt is written.
"""
import argparse
import hashlib
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BUILD = ROOT / 'build/sql-confinement'
SQL = ROOT / 'docs/sql/ai-integration'
RES = ROOT / 'shale-data/src/main/resources/ai'
args = argparse.ArgumentParser()
args.add_argument('--check', action='store_true')
CHECK = args.parse_args().check


def emit(path, value):
    if CHECK:
        if not path.exists() or path.read_text() != value:
            raise SystemExit('Stale C1 artifact: ' + str(path.relative_to(ROOT)))
    else:
        path.write_text(value)


def quote(value):
    return "N'" + value.replace("'", "''") + "'"


def sha(value):
    return hashlib.sha256(value.encode('utf-16le')).hexdigest()


def execute(sql, variable):
    # Keep platform-dispatched verifier batches out of Azure's static compiler,
    # as the predecessor candidates do. This is encoding, not a source change.
    return f'DECLARE @{variable} nvarchar(max)=CONVERT(nvarchar(max),0x{sql.encode("utf-16le").hex()}); EXEC sys.sp_executesql @{variable};\n'


def definitions(text):
    result = {}
    for found in re.finditer(r"EXEC(?:\(| sys\.sp_executesql )N'((?:[^']|'')*)'\)?;", text, re.S):
        body = found[1].replace("''", "'")
        name = re.match(r'CREATE (?:PROCEDURE|TRIGGER) dbo\.(\w+)', body)
        if name:
            result[name[1]] = body
    for found in re.finditer(r'CONVERT\(nvarchar\(max\),0x([0-9a-f]+)\)', text):
        body = bytes.fromhex(found[1]).decode('utf-16le')
        name = re.match(r'CREATE (?:PROCEDURE|TRIGGER) dbo\.(\w+)', body)
        if name:
            result[name[1]] = body
    return result


proof = definitions((SQL / 'universal-pr4a-proof.sql').read_text())
oauth = definitions((SQL / 'universal-pr4b-oauth.sql').read_text())
onboarding = definitions((SQL / 'universal-pr-b-onboarding.sql').read_text())
original = {**proof, **oauth, **onboarding}
rename = {n: n.replace('AiCimd', 'AiExternal', 1).removesuffix('V1') + 'V2'
          for n, b in original.items() if b.startswith('CREATE PROCEDURE')}
assert len(rename) >= 30


def transform(body):
    # Rename procedure references only. Shared record/table names remain unchanged.
    for old in sorted(rename, key=len, reverse=True):
        body = re.sub(r'\bdbo\.' + old + r'\b', 'dbo.' + rename[old], body)
    return body


def provenance(alias):
    prefix = alias + '.' if alias else ''
    return (f"({prefix}Source='CIMD' OR ({prefix}Source='DCR' AND "
            f"EXISTS(SELECT 1 FROM dbo.AiDcrMetadataV2 dm WHERE dm.ApplicationId={prefix}ApplicationId "
            f"AND dm.ApplicationRevision={prefix}Revision AND dm.MetadataDigest={prefix}MetadataDigest) "
            "AND EXISTS(SELECT 1 FROM dbo.AiExternalControlV2 WITH(HOLDLOCK) WHERE Id=1 AND Enabled=1)))")


modules = {}
for old, new in rename.items():
    body = transform(original[old])
    if old != 'AiCimdPublishMetadataV1':  # CIMD fetch/publish capability never writes DCR metadata.
        # Unqualified application-source predicates require an explicit alias in subqueries.
        if "Source='CIMD'" in body and "e.Source='CIMD'" not in body:
            body = body.replace('FROM dbo.AiExternalApplication WHERE', 'FROM dbo.AiExternalApplication e WHERE')
            body = body.replace('FROM dbo.AiExternalApplication WITH(HOLDLOCK)', 'FROM dbo.AiExternalApplication e WITH(HOLDLOCK)')
            body = body.replace("Source='CIMD'", provenance('e'))
        else:
            body = body.replace("e.Source='CIMD'", provenance('e'))
    # Requested/approved scopes can never exceed the stored DCR registration ceiling.
    if old == 'AiCimdOAuthAuthorityV1':
        body = body.replace('AND e.Disabled=0', "AND e.Disabled=0 AND (e.Source<>'DCR' OR EXISTS(SELECT 1 FROM dbo.AiDcrMetadataV2 ds WHERE ds.ApplicationId=e.ApplicationId AND ds.ApplicationRevision=e.Revision AND (ds.ScopeMask & @Scopes)=@Scopes))")
    if old == 'AiCimdDataLoadAuthorityV1':
        body = body.replace('IF @approvalDeadline IS NULL', "IF EXISTS(SELECT 1 FROM dbo.AiExternalApplication e JOIN dbo.AiDcrMetadataV2 ds ON ds.ApplicationId=e.ApplicationId AND ds.ApplicationRevision=e.Revision WHERE e.ApplicationId=@application AND e.Source='DCR' AND (ds.ScopeMask & @mask)<>@mask) THROW 59101,'DCR scope ceiling exceeded.',1;\n IF @approvalDeadline IS NULL")
    if old == 'AiCimdLoadClientV1':
        body = body.replace('AND e.Disabled=0', "AND e.Disabled=0 AND (e.Source<>'DCR' OR EXISTS(SELECT 1 FROM dbo.AiDcrMetadataV2 ds WHERE ds.ApplicationId=e.ApplicationId AND ds.ApplicationRevision=e.Revision AND (ds.ScopeMask & p.ScopeMask)=p.ScopeMask))")
    modules[new] = body

# Both resolver families require exact stored provenance. Original CIMD locators
# are tightened, never expanded; V2 checks either valid source with its ledger.
changed = {}
for suffix in ('Code', 'Refresh', 'Revocation'):
    old = 'AiCimdResolve' + suffix + 'V1'
    marker = ' AND x.ExternalClientId=@External'
    guard = " AND EXISTS(SELECT 1 FROM dbo.AiExternalApplication e WHERE e.ApplicationId=x.ApplicationId AND e.Source='CIMD')\n"
    assert original[old].count(marker) == 1
    changed[old] = original[old].replace(marker, guard + marker).replace('CREATE PROCEDURE', 'ALTER PROCEDURE', 1)
    modules[rename[old]] = transform(original[old]).replace(marker,
        ' AND EXISTS(SELECT 1 FROM dbo.AiExternalApplication e WHERE e.ApplicationId=x.ApplicationId AND ' + provenance('e') + ')\n' + marker)

template = (BUILD / 'external-v2-registration.sql').read_text()
schema = template[:template.index('\nGO\n')]
for section in template[template.index('\nGO\n') + 4:].split('\nGO\n'):
    match = re.search(r'CREATE (?:PROCEDURE|TRIGGER) dbo\.(\w+)', section)
    if match:
        modules[match[1]] = section[match.start():].strip()

# Metadata-only compatibility marker; no business authority or callable verifier.
marker_body = "CREATE PROCEDURE dbo.AiExternalCompatibilityV2 AS THROW 59600,'Private C1 compatibility metadata.',1;"
modules['AiExternalCompatibilityV2'] = marker_body

roles = {
    'AiDcrRegistrarV2': ['AiDcrRegisterApplicationV2'],
    'AiDcrAdministratorV2': ['AiDcrChangeStateV2'],
    'AiDcrMetadataReaderV2': ['AiDcrPrepareMetadataV2'],
    'AiExternalCaseRuntimeV2': [rename['AiCimdData' + x + 'V1'] for x in ('Authenticate', 'SearchCases', 'GetCaseOverview', 'FinishCaseQuota', 'AuthorizeCaseHandoff', 'RecordCaseFailure')],
    'AiExternalLifecycleV2': [rename['AiCimdRevokeFamilyV1']],
    'AiExternalMetadataPublisherV2': [rename['AiCimdPublishMetadataV1'], rename['AiCimdResolvePublicationV1']],
    'AiExternalOAuthBrowserV2': [rename['AiCimd' + x + 'V1'] for x in ('LoadClient', 'BeginOAuth', 'FindOAuthIntent', 'ApproveOAuth', 'CompleteOAuth', 'DenyOAuth')],
    'AiExternalOAuthIssuerV2': [rename['AiCimd' + x + 'V1'] for x in ('LoadClientProof', 'Redeem', 'Refresh', 'RevokeCredential')],
    'AiExternalOAuthResolverV2': [rename['AiCimdResolve' + x + 'V1'] for x in ('Code', 'Refresh', 'Revocation')],
    'AiExternalOAuthNeutralV2': [rename['AiCimdRevokeUnknownV1']],
    'AiExternalOnboardingRuntimeV2': [rename['AiCimdGetOnboardingV1']],
}
roots = {p for entries in roles.values() for p in entries}
permissions = []
for role, entries in roles.items():
    permissions.append('CREATE ROLE ' + role + ' AUTHORIZATION dbo;')
    permissions.extend('GRANT EXECUTE ON dbo.' + p + ' TO ' + role + ';' for p in entries)
# Public DENY would defeat ownership-chain nested calls only if directly invoked;
# keep helpers without any positive grant, matching the existing private seams.

all_objects = sorted(set(modules) | {'AiDcrMetadataV2', 'AiExternalControlV2', 'AiExternalReceiptV2'})
# Receipt values are metadata properties on the private marker, not table data.
schema = schema[:schema.index('CREATE TABLE dbo.AiExternalReceiptV2')]
modules.pop('AiExternalImmutableReceiptV2')
all_objects = sorted(set(modules) | {'AiDcrMetadataV2', 'AiExternalControlV2'})
source = sha(schema + '\n'.join(modules.values()) + '\n'.join(changed.values()) + '\n'.join(permissions) + Path(__file__).read_text())
names = ','.join(quote(n) for n in all_objects)
role_names = ','.join(quote(r) for r in roles)

# Reuse the existing registry member/ownership/platform checks, replacing only
# its role/root allowlist with this increment's fixed execute-only capabilities.
members = (BUILD / 'registry-v1-graph.sql').read_text()
members = members[members.index(' DECLARE @RegistryMembers'):members.index(' /* Exact permissions:')]
members = members.replace("N'AiApplicationPublisher',N'AiApplicationTenant'", role_names)
members = members.replace("DATABASE_PRINCIPAL_ID(N'AiApplicationPublisher'),DATABASE_PRINCIPAL_ID(N'AiApplicationTenant')", ','.join('DATABASE_PRINCIPAL_ID('+quote(r)+')' for r in roles))
members = members.replace('@RegistryMembers', '@C1Members').replace('@RegistryTokens', '@C1Tokens')
members = members.replace(" INSERT @C1Tokens VALUES(DATABASE_PRINCIPAL_ID(N'AiApplicationPublisher')),(DATABASE_PRINCIPAL_ID(N'AiApplicationTenant'));",
                          ' INSERT @C1Tokens VALUES' + ','.join('(DATABASE_PRINCIPAL_ID(' + quote(r) + '))' for r in roles) + ';')
platform = (BUILD / 'registry-v1-graph.sql').read_text()
platform = platform[platform.index(' DECLARE @RegistryEdition'):].rsplit('END;', 1)[0]
platform = platform.replace('@RegistryEdition', '@C1Edition').replace('@RegistryServerCheck', '@C1ServerCheck').replace('@RegistryMembers', '@C1Members')
platform = platform.replace("N'AiApplicationPublisher',N'AiApplicationTenant'", role_names)
platform = platform.replace("N''AiApplicationPublisher'',N''AiApplicationTenant''", ','.join("N''"+r+"''" for r in roles))
platform = platform.replace("DATABASE_PRINCIPAL_ID(N''AiApplicationPublisher''),DATABASE_PRINCIPAL_ID(N''AiApplicationTenant'')", ','.join("DATABASE_PRINCIPAL_ID(N''"+r+"'')" for r in roles))
grants = ','.join('(' + quote(role) + ',' + quote(p) + ')' for role, entries in roles.items() for p in entries)
actual_pins = '\n'.join(
    f"IF NOT EXISTS(SELECT 1 FROM sys.sql_modules m JOIN sys.objects o ON o.object_id=m.object_id JOIN sys.schemas s ON s.schema_id=o.schema_id WHERE o.object_id=OBJECT_ID(N'dbo.{n}') AND COALESCE(o.principal_id,s.principal_id)=1 AND m.uses_ansi_nulls=1 AND m.uses_quoted_identifier=1 AND {'m.execute_as_principal_id=-2' if 'WITH EXECUTE AS OWNER' in b else 'm.execute_as_principal_id IS NULL'} AND HASHBYTES('SHA2_256',m.definition)=0x{sha(b)}) OR EXISTS(SELECT 1 FROM sys.crypt_properties WHERE major_id=OBJECT_ID(N'dbo.{n}')) THROW 59600,'C1 reviewed module drift.',1;"
    for n, b in {**modules, **changed}.items())

receipt = """DECLARE @C1ConstraintId int,@C1ConstraintDefinition nvarchar(max),@C1HistoricalHash binary(32),@C1CatalogHash binary(32);
SELECT @C1ConstraintId=TRY_CONVERT(int,value) FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID(N'dbo.AiExternalCompatibilityV2') AND name=N'DcrCompatV2.OldConstraintId';
SELECT @C1ConstraintDefinition=CONVERT(nvarchar(max),value) FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID(N'dbo.AiExternalCompatibilityV2') AND name=N'DcrCompatV2.OldConstraintDefinition';
SELECT @C1HistoricalHash=CONVERT(binary(32),value) FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID(N'dbo.AiExternalCompatibilityV2') AND name=N'DcrCompatV2.Historical';
SELECT @C1CatalogHash=CONVERT(binary(32),value) FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID(N'dbo.AiExternalCompatibilityV2') AND name=N'DcrCompatV2.Catalog';
"""
historical_state = """DECLARE @C1State nvarchar(max)=(SELECT class,major_id,minor_id,name,CONVERT(varchar(max),CONVERT(varbinary(max),value),2) AS ReceiptValue FROM sys.extended_properties WHERE name LIKE N'Shale.%' ORDER BY class,major_id,minor_id,name FOR XML PATH('r'),TYPE).value('.','nvarchar(max)');
SET @C1State=@C1State+(SELECT CONCAT(Id,Issuer,Resource,Contract,JavaTimeZone,SqlTimeZone) AS [text()] FROM dbo.AiCaseDataDeployment ORDER BY Id FOR XML PATH(''),TYPE).value('.','nvarchar(max)');
SET @C1State=@C1State+(SELECT CONCAT(Id,CONVERT(varchar(64),SourceHash,2),CONVERT(varchar(64),BeforeHash,2),CONVERT(varchar(64),CatalogHash,2)) AS [text()] FROM dbo.AiCimdProofReceiptV1 ORDER BY Id FOR XML PATH(''),TYPE).value('.','nvarchar(max)');
SET @C1State=@C1State+(SELECT CONCAT(Id,CONVERT(varchar(64),SourceHash,2),CONVERT(varchar(64),BeforeHash,2),CONVERT(varchar(64),CatalogHash,2)) AS [text()] FROM dbo.AiCimdOAuthReceiptV1 ORDER BY Id FOR XML PATH(''),TYPE).value('.','nvarchar(max)');
"""
# The registry reviewer already reads the binding but cannot read the additive
# proof/OAuth receipts. The existing source-pinned accessor supplies them later.
# Historical extprops/binding checks are retained in the predecessor resources;
# full receipt snapshot is checked in the additive/owner verifier only.
checks = f"""SET NOCOUNT ON;
IF NOT EXISTS(SELECT 1 FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID(N'dbo.AiExternalCompatibilityV2') AND name=N'DcrCompatV2.Source' AND CONVERT(binary(32),value)=0x{source}) THROW 59600,'Reviewed C1 successor required; no fallback.',1;
{receipt}
IF @C1ConstraintId IS NULL OR @C1ConstraintDefinition IS NULL OR @C1HistoricalHash IS NULL OR @C1CatalogHash IS NULL THROW 59600,'Incomplete C1 receipt.',1;
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE name=N'CK_AiExternalApplication_Source' AND parent_object_id=OBJECT_ID(N'dbo.AiExternalApplication') AND is_disabled=0 AND is_not_trusted=0
 AND REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(definition,N'[',N''),N']',N''),N'(',N''),N')',N''),N' ',N'') COLLATE Latin1_General_100_BIN2=N'Source=''CIMD''ORSource=''DCR''') THROW 59600,'C1 source constraint drift.',1;
{actual_pins}
DECLARE @C1Objects table(Name sysname PRIMARY KEY); INSERT @C1Objects VALUES {','.join('('+quote(n)+')' for n in all_objects)};
IF (SELECT COUNT(*) FROM sys.objects WHERE schema_id=SCHEMA_ID(N'dbo') AND name IN(SELECT Name FROM @C1Objects))<>(SELECT COUNT(*) FROM @C1Objects) THROW 59600,'Incomplete C1 objects.',1;
IF EXISTS(SELECT 1 FROM sys.objects o JOIN sys.schemas s ON s.schema_id=o.schema_id WHERE o.name IN(SELECT Name FROM @C1Objects) AND COALESCE(o.principal_id,s.principal_id)<>1) THROW 59600,'C1 ownership drift.',1;
IF (SELECT COUNT(*) FROM sys.database_principals WHERE name IN({role_names}) AND type='R' AND owning_principal_id=1)<>{len(roles)} OR EXISTS(SELECT 1 FROM sys.database_role_members WHERE member_principal_id IN({','.join('DATABASE_PRINCIPAL_ID('+quote(r)+')' for r in roles)})) THROW 59600,'C1 role ownership/inheritance drift.',1;
IF EXISTS(SELECT 1 FROM sys.triggers WHERE OBJECT_NAME(parent_id) IN(N'AiDcrMetadataV2',N'AiExternalControlV2') AND name NOT IN(SELECT Name FROM @C1Objects)) OR EXISTS(SELECT 1 FROM sys.security_predicates WHERE OBJECT_NAME(target_object_id) IN(N'AiDcrMetadataV2',N'AiExternalControlV2')) THROW 59600,'C1 unreviewed trigger/policy.',1;
IF NOT EXISTS(SELECT 1 FROM sys.triggers WHERE object_id=OBJECT_ID(N'dbo.AiDcrImmutableMetadataV2') AND parent_id=OBJECT_ID(N'dbo.AiDcrMetadataV2') AND is_disabled=0 AND is_instead_of_trigger=0) OR EXISTS(SELECT 1 FROM sys.trigger_events WHERE object_id=OBJECT_ID(N'dbo.AiDcrImmutableMetadataV2') AND (is_first=1 OR is_last=1)) THROW 59600,'DCR immutable trigger drift.',1;
{members}
{platform}
DECLARE @C1Grants table(RoleName sysname,ProcedureName sysname); INSERT @C1Grants VALUES {grants};
IF EXISTS(SELECT USER_NAME(grantee_principal_id),OBJECT_NAME(major_id) FROM sys.database_permissions WHERE grantee_principal_id IN(SELECT Id FROM @C1Tokens) AND NOT(class=0 AND permission_name='CONNECT' AND state='G' AND grantee_principal_id IN(SELECT Id FROM @C1Members)) EXCEPT SELECT RoleName,ProcedureName FROM @C1Grants)
 OR EXISTS(SELECT RoleName,ProcedureName FROM @C1Grants EXCEPT SELECT USER_NAME(grantee_principal_id),OBJECT_NAME(major_id) FROM sys.database_permissions WHERE class=1 AND minor_id=0 AND permission_name='EXECUTE' AND state='G' AND grantor_principal_id=1)
 OR EXISTS(SELECT 1 FROM sys.database_permissions WHERE grantee_principal_id IN(SELECT Id FROM @C1Tokens) AND NOT(class=1 AND minor_id=0 AND permission_name='EXECUTE' AND state='G' AND grantor_principal_id=1 OR class=0 AND permission_name='CONNECT' AND state='G' AND grantee_principal_id IN(SELECT Id FROM @C1Members))) THROW 59600,'C1 capability permissions drift.',1;
DECLARE @C1Graph nvarchar(max)=N'';
SET @C1Graph=@C1Graph+(SELECT CONCAT(o.name,N':',o.type,N':',o.principal_id,N':',m.execute_as_principal_id,N':',m.uses_ansi_nulls,N':',m.uses_quoted_identifier,N':',m.definition,N';') AS [text()] FROM sys.objects o LEFT JOIN sys.sql_modules m ON m.object_id=o.object_id WHERE o.schema_id=SCHEMA_ID(N'dbo') AND o.name IN(SELECT Name FROM @C1Objects) ORDER BY o.name FOR XML PATH(''),TYPE).value('.','nvarchar(max)');
SET @C1Graph=@C1Graph+(SELECT CONCAT(OBJECT_NAME(c.object_id),N':',c.column_id,N':',c.name,N':',c.user_type_id,N':',c.max_length,N':',c.is_nullable,N':',c.is_identity,N':',c.collation_name,N';') AS [text()] FROM sys.columns c WHERE OBJECT_NAME(c.object_id) IN(SELECT Name FROM @C1Objects) ORDER BY c.object_id,c.column_id FOR XML PATH(''),TYPE).value('.','nvarchar(max)');
SET @C1Graph=@C1Graph+(SELECT CONCAT(OBJECT_NAME(o.parent_object_id),N':',o.name,N':',o.type,N':',ch.definition,N':',ch.is_disabled,N':',ch.is_not_trusted,N':',dc.definition,N':',fk.referenced_object_id,N':',fk.is_disabled,N':',fk.is_not_trusted,N':',fk.delete_referential_action,N':',fk.update_referential_action,N';') AS [text()] FROM sys.objects o LEFT JOIN sys.check_constraints ch ON ch.object_id=o.object_id LEFT JOIN sys.default_constraints dc ON dc.object_id=o.object_id LEFT JOIN sys.foreign_keys fk ON fk.object_id=o.object_id WHERE OBJECT_NAME(o.parent_object_id) IN(SELECT Name FROM @C1Objects) ORDER BY o.object_id FOR XML PATH(''),TYPE).value('.','nvarchar(max)');
SET @C1Graph=@C1Graph+(SELECT CONCAT(f.constraint_object_id,N':',f.constraint_column_id,N':',f.parent_column_id,N':',f.referenced_column_id,N';') AS [text()] FROM sys.foreign_key_columns f WHERE OBJECT_NAME(f.parent_object_id) IN(SELECT Name FROM @C1Objects) ORDER BY f.constraint_object_id,f.constraint_column_id FOR XML PATH(''),TYPE).value('.','nvarchar(max)');
SET @C1Graph=@C1Graph+(SELECT CONCAT(i.object_id,N':',i.name,N':',i.is_unique,N':',i.is_disabled,N':',i.filter_definition,N':',c.column_id,N':',c.key_ordinal,N':',c.is_included_column,N';') AS [text()] FROM sys.indexes i JOIN sys.index_columns c ON c.object_id=i.object_id AND c.index_id=i.index_id WHERE OBJECT_NAME(i.object_id) IN(SELECT Name FROM @C1Objects) ORDER BY i.object_id,i.index_id,c.column_id FOR XML PATH(''),TYPE).value('.','nvarchar(max)');
SET @C1Graph=@C1Graph+(SELECT CONCAT(object_id,N':',column_id,N':',definition,N';') AS [text()] FROM sys.computed_columns WHERE OBJECT_NAME(object_id) IN(SELECT Name FROM @C1Objects) ORDER BY object_id,column_id FOR XML PATH(''),TYPE).value('.','nvarchar(max)');
SET @C1Graph=@C1Graph+(SELECT CONCAT(p.class,N':',p.major_id,N':',p.minor_id,N':',p.grantee_principal_id,N':',p.grantor_principal_id,N':',p.permission_name,N':',p.state,N';') AS [text()] FROM sys.database_permissions p WHERE p.class=1 AND OBJECT_NAME(p.major_id) IN(SELECT Name FROM @C1Objects) OR p.grantee_principal_id IN({','.join('DATABASE_PRINCIPAL_ID('+quote(r)+')' for r in roles)}) ORDER BY p.class,p.major_id,p.minor_id,p.grantee_principal_id,p.permission_name FOR XML PATH(''),TYPE).value('.','nvarchar(max)');
IF HASHBYTES('SHA2_256',@C1Graph)<>@C1CatalogHash THROW 59600,'C1 catalog drift; never refresh receipts.',1;
"""

registry = (RES / 'registry-confinement-v1-catalog.sql').read_text()
registry_lines = registry.splitlines()
for i, line in enumerate(registry_lines):
    if 'ch.definition' in line and 'CONCAT(' in line:
        registry_lines[i] = line.replace('ch.definition', "CASE WHEN o.name=N'CK_AiExternalApplication_Source' THEN @C1ConstraintDefinition ELSE ch.definition END")
    # Constraint metadata spans multiple lines in the additional graph projection.
    if 'ORDER BY o.object_id FOR XML' in line:
        registry_lines[i] = registry_lines[i].replace('ORDER BY o.object_id FOR XML', "ORDER BY CASE WHEN o.name=N'CK_AiExternalApplication_Source' THEN @C1ConstraintId ELSE o.object_id END FOR XML")
registry = '\n'.join(registry_lines) + '\n'
emit(RES / 'registry-external-v2-catalog.sql', '/* Reviewed C1 registry successor; validated delta, unchanged historical receipts. */\n' + checks + registry)

additive = (RES / 'cimd-authorization-v1-catalog.sql').read_text()
for name, body in changed.items():
    additive = additive.replace('0x' + sha(original[name]), '0x' + sha(body))
# Validate actual new source above, then reconstruct only these old definitions
# for the historical OAuth fingerprint. No opaque source is read from a receipt.
projection = 'CASE ' + ' '.join('WHEN o.name=' + quote(n) + ' THEN ' + quote(original[n]) for n in changed) + ' ELSE m.definition END'
additive = '\n'.join(line.replace('m.definition', projection) if 'SET @C4bCatalog=' in line and 'm.definition' in line else line for line in additive.splitlines()) + '\n'
snapshot_check = historical_state.replace("(SELECT CONCAT(Id,Issuer,Resource,Contract,JavaTimeZone,SqlTimeZone) AS [text()] FROM dbo.AiCaseDataDeployment ORDER BY Id FOR XML PATH(''),TYPE).value('.','nvarchar(max)')", '(SELECT BindingState FROM @CimdEvidence)')
snapshot_check = snapshot_check.replace("(SELECT CONCAT(Id,CONVERT(varchar(64),SourceHash,2),CONVERT(varchar(64),BeforeHash,2),CONVERT(varchar(64),CatalogHash,2)) AS [text()] FROM dbo.AiCimdProofReceiptV1 ORDER BY Id FOR XML PATH(''),TYPE).value('.','nvarchar(max)')", '(SELECT ProofState FROM @CimdEvidence)')
snapshot_check = snapshot_check.replace("(SELECT CONCAT(Id,CONVERT(varchar(64),SourceHash,2),CONVERT(varchar(64),BeforeHash,2),CONVERT(varchar(64),CatalogHash,2)) AS [text()] FROM dbo.AiCimdOAuthReceiptV1 ORDER BY Id FOR XML PATH(''),TYPE).value('.','nvarchar(max)')", '(SELECT CONCAT(1,CONVERT(varchar(64),OAuthSource,2),CONVERT(varchar(64),OAuthBefore,2),CONVERT(varchar(64),OAuthCatalog,2)) FROM @CimdEvidence)')
snapshot_check += "IF HASHBYTES('SHA2_256',@C1State)<>@C1HistoricalHash THROW 59600,'Historical receipt/binding changed.',1;\n"
additive = additive.replace('DECLARE @C4aObjects', snapshot_check + 'DECLARE @C4aObjects', 1)
emit(RES / 'external-authorization-v2-catalog.sql', '/* Reviewed C1 additive successor; all old checks retained except explicit pinned locator delta. */\n' + checks + additive)

install = """/* PR C1 OFF disposable/review candidate. No historical artifact or receipt writes.
   Owner installation only; creates no users/logins/pools and enables no control. */
SET NOCOUNT ON; SET XACT_ABORT ON; SET ANSI_NULLS ON; SET QUOTED_IDENTIFIER ON;
IF USER_NAME()<>N'dbo' OR @@TRANCOUNT<>0 OR SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL OR SESSION_CONTEXT(N'PrincipalUserId') IS NOT NULL THROW 59600,'Fresh unscoped owner required.',1;
BEGIN TRY BEGIN TRANSACTION;
DECLARE @lock int;
EXEC @lock=sys.sp_getapplock @Resource=N'AiSchemaV1',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=3000;
IF @lock<0 OR NOT EXISTS(SELECT 1 FROM dbo.AiProductCapability WHERE Id=1 AND Enabled=0)
 OR NOT EXISTS(SELECT 1 FROM dbo.AiCimdProofControlV1 WHERE Id=1 AND Enabled=0)
 OR NOT EXISTS(SELECT 1 FROM dbo.AiCimdOAuthControlV1 WHERE Id=1 AND Enabled=0) THROW 59600,'Schema lock and all controls OFF required.',1;
IF OBJECT_ID(N'dbo.AiExternalCompatibilityV2') IS NOT NULL OR OBJECT_ID(N'dbo.AiDcrMetadataV2') IS NOT NULL OR OBJECT_ID(N'dbo.AiExternalControlV2') IS NOT NULL THROW 59600,'C1 already or partially installed; no repair.',1;
"""
# Execute the complete unchanged predecessor BEFORE modifying the reviewed delta.
install += execute((RES / 'registry-confinement-v1-catalog.sql').read_text(), 'C1PreRegistry')
install += "EXEC dbo.AiRegistryConfinementV1 @Phase='VERIFY';\n"
install += execute((RES / 'cimd-authorization-v1-catalog.sql').read_text(), 'C1PreOAuth')
install += historical_state
install += "DECLARE @oldId int,@oldDef nvarchar(max); SELECT @oldId=object_id,@oldDef=definition FROM sys.check_constraints WHERE name=N'CK_AiExternalApplication_Source' AND parent_object_id=OBJECT_ID(N'dbo.AiExternalApplication');\nIF @oldId IS NULL OR @oldDef IS NULL THROW 59600,'Verified predecessor constraint required.',1;\n"
install += "ALTER TABLE dbo.AiExternalApplication DROP CONSTRAINT CK_AiExternalApplication_Source;\nALTER TABLE dbo.AiExternalApplication WITH CHECK ADD CONSTRAINT CK_AiExternalApplication_Source CHECK(Source='CIMD' OR Source='DCR');\n"
install += schema
for name, body in changed.items():
    install += 'EXEC(' + quote(body.replace('CREATE PROCEDURE', 'ALTER PROCEDURE', 1)) + ');\n'
for name, body in modules.items():
    install += 'EXEC(' + quote(body) + ');\n'
install += '\n'.join(permissions) + '\n'
props = {'Source': '0x' + source, 'OldConstraintId': '@oldId', 'OldConstraintDefinition': 'CONVERT(nvarchar(4000),@oldDef)', 'Historical': "HASHBYTES('SHA2_256',@C1State)"}
for key, value in props.items():
    install += f"DECLARE @property{key} sql_variant={value}; EXEC sys.sp_addextendedproperty @name=N'DcrCompatV2.{key}',@value=@property{key},@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'PROCEDURE',@level1name=N'AiExternalCompatibilityV2';\n"
# Seal only the NEW contract once, after exact source and permission checks.
sealing = checks[checks.index('DECLARE @C1Objects'):]
sealing = sealing[:sealing.index("IF HASHBYTES('SHA2_256',@C1Graph)")]
install += execute(actual_pins + '\n' + sealing + "\nDECLARE @seal binary(32)=HASHBYTES('SHA2_256',@C1Graph); EXEC sys.sp_addextendedproperty @name=N'DcrCompatV2.Catalog',@value=@seal,@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'PROCEDURE',@level1name=N'AiExternalCompatibilityV2';", 'C1Seal')
install += '/* C1_FINAL_VERIFY */\n' + execute(checks + registry, 'C1FinalRegistry') + execute(checks + additive, 'C1FinalOAuth')
install += "COMMIT TRANSACTION;\nEND TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;\n"
emit(SQL / 'universal-pr-c1-external-v2.sql', install)
emit(BUILD / 'external-v2-modules.sql', '\nGO\n'.join(modules.values()) + '\n')
print(f'C1 external successor source {source}; new modules={len(modules)}, predecessor locators={len(changed)}')
