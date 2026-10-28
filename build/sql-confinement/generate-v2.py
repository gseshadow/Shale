#!/usr/bin/env python3
"""Generate V2 from frozen V1 authority; never regenerate or rewrite historical V1 files.

No database access or private SQL input. Reviewed module variants contain hashes only.
"""
from pathlib import Path
import hashlib
import json
import re

BUILD = Path(__file__).resolve().parent
REPO = BUILD.parent.parent
SQL = REPO / 'docs/sql/ai-integration'
RES = REPO / 'shale-data/src/main/resources/ai'


def case_sql():
    source = (BUILD / 'case-catalog.sql').read_text()
    source = source[:source.index('SELECT CONVERT(varchar(64),HASHBYTES')]
    # Active logical position retains order without pinning historical dropped-column IDs.
    source = source.replace("c.column_id,N':'", "ROW_NUMBER() OVER(PARTITION BY c.object_id ORDER BY c.column_id),N':'", 1)
    variants = json.loads((BUILD / 'reviewed-case-module-variants-v2.json').read_text())['modules']
    prefix = """SET NOCOUNT ON;
IF ISNULL(HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','VIEW DEFINITION'),0)<>1
 THROW 59200,'Complete V2 catalog visibility required.',1;
IF (SELECT COUNT(*) FROM sys.schemas WHERE name COLLATE Latin1_General_100_BIN2 IN(N'dbo',N'sec') AND principal_id=DATABASE_PRINCIPAL_ID(N'dbo'))<>2
 THROW 59200,'V2 dbo/sec schema ownership required.',1;
DECLARE @ReviewedCaseModules table(SchemaName sysname,ModuleName sysname,DefinitionHash binary(32),CanonicalHash varchar(64),PRIMARY KEY(SchemaName,ModuleName,DefinitionHash));
"""
    rows = []
    for module, hashes in variants.items():
        schema, name = module.split('.')
        for value in sorted(set(hashes.values())):
            rows.append(f"(N'{schema}',N'{name}',0x{value},'{hashes['authoritative']}')")
    prefix += 'INSERT @ReviewedCaseModules VALUES\n' + ',\n'.join(rows) + ';\n'
    # Only exact reviewed module bytes are accepted. No text rewriting, even outside literals.
    prefix += """IF (SELECT COUNT(*) FROM sys.objects o JOIN sys.schemas s ON s.schema_id=o.schema_id
 WHERE EXISTS(SELECT 1 FROM @ReviewedCaseModules r WHERE r.SchemaName=s.name AND r.ModuleName=o.name))<>6
 THROW 59200,'V2 reviewed Case modules missing.',1;
IF EXISTS(SELECT 1 FROM sys.objects o JOIN sys.schemas s ON s.schema_id=o.schema_id JOIN sys.sql_modules m ON m.object_id=o.object_id
 WHERE EXISTS(SELECT 1 FROM @ReviewedCaseModules r WHERE r.SchemaName=s.name AND r.ModuleName=o.name)
 AND NOT EXISTS(SELECT 1 FROM @ReviewedCaseModules r WHERE r.SchemaName=s.name AND r.ModuleName=o.name AND r.DefinitionHash=HASHBYTES('SHA2_256',m.definition)))
 THROW 59200,'V2 Case module definition is not an approved representation.',1;
"""
    canonical_function = "(SELECT TOP(1) r.CanonicalHash FROM @ReviewedCaseModules r WHERE r.SchemaName=s.name AND r.ModuleName=o.name AND r.DefinitionHash=HASHBYTES('SHA2_256',m.definition))"
    canonical_trigger = "(SELECT TOP(1) r.CanonicalHash FROM @ReviewedCaseModules r WHERE r.SchemaName=OBJECT_SCHEMA_NAME(t.object_id) AND r.ModuleName=t.name AND r.DefinitionHash=HASHBYTES('SHA2_256',m.definition))"
    source = source.replace("t.is_disabled,N':',m.definition", "t.is_disabled,N':'," + canonical_trigger)
    source = source.replace("o.name,N':',m.definition", "o.name,N':'," + canonical_function)
    source = source.replace("p.operation,N':',sp.is_enabled", "p.operation,N':',OBJECT_SCHEMA_NAME(p.object_id),N':',sp.name,N':',USER_NAME(COALESCE(sp.principal_id,(SELECT principal_id FROM sys.schemas WHERE schema_id=sp.schema_id))),N':',sp.is_enabled")
    # Do not silently drop unknown modules: the static expected hash also detects additions/removals.
    source += """
SET @caseCatalog=@caseCatalog+(SELECT CONCAT(s.name,N':',o.name,N':',o.type,N':',m.uses_ansi_nulls,N':',m.uses_quoted_identifier,N':',m.is_schema_bound,N':',m.is_recompiled,N':',m.null_on_null_input,N':',m.uses_native_compilation,N';') AS [text()]
 FROM sys.objects o JOIN sys.schemas s ON s.schema_id=o.schema_id JOIN sys.sql_modules m ON m.object_id=o.object_id
 WHERE EXISTS(SELECT 1 FROM @ReviewedCaseModules r WHERE r.SchemaName=s.name AND r.ModuleName=o.name)
 ORDER BY s.name,o.name FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @caseCatalog=@caseCatalog+(SELECT CONCAT(OBJECT_SCHEMA_NAME(p.object_id),N':',OBJECT_NAME(p.object_id),N':',p.parameter_id,N':',p.name,N':',(SELECT SCHEMA_NAME(ty.schema_id) FROM sys.types ty WHERE ty.user_type_id=p.user_type_id),N':',TYPE_NAME(p.user_type_id),N':',p.system_type_id,N':',p.max_length,N':',p.precision,N':',p.scale,N':',p.is_output,N':',p.is_readonly,N';') AS [text()]
 FROM sys.parameters p WHERE EXISTS(SELECT 1 FROM @ReviewedCaseModules r WHERE r.SchemaName=OBJECT_SCHEMA_NAME(p.object_id) AND r.ModuleName=OBJECT_NAME(p.object_id))
 ORDER BY OBJECT_SCHEMA_NAME(p.object_id),OBJECT_NAME(p.object_id),p.parameter_id FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
SET @caseCatalog=@caseCatalog+(SELECT CONCAT(OBJECT_SCHEMA_NAME(d.referencing_id),N':',OBJECT_NAME(d.referencing_id),N':',d.referenced_server_name,N':',d.referenced_database_name,N':',d.referenced_schema_name,N':',d.referenced_entity_name,N':',COL_NAME(d.referenced_id,d.referenced_minor_id),N':',d.is_schema_bound_reference,N':',d.is_caller_dependent,N':',d.is_ambiguous,N';') AS [text()]
 FROM sys.sql_expression_dependencies d WHERE EXISTS(SELECT 1 FROM @ReviewedCaseModules r WHERE r.SchemaName=OBJECT_SCHEMA_NAME(d.referencing_id) AND r.ModuleName=OBJECT_NAME(d.referencing_id))
 ORDER BY OBJECT_SCHEMA_NAME(d.referencing_id),OBJECT_NAME(d.referencing_id),d.referenced_server_name,d.referenced_database_name,d.referenced_schema_name,d.referenced_entity_name,COL_NAME(d.referenced_id,d.referenced_minor_id),d.is_schema_bound_reference,d.is_caller_dependent,d.is_ambiguous
 FOR XML PATH(''),TYPE).value(N'.',N'nvarchar(max)');
"""
    return prefix + source


def generate():
    case = case_sql()
    expected = (BUILD / 'case-catalog-v2.sha256').read_text().strip()
    assert re.fullmatch('[0-9a-f]{64}', expected)
    check = case + f"IF HASHBYTES('SHA2_256',@caseCatalog)<>0x{expected} THROW 59200,'V2 authoritative Case/RLS prerequisite drift.',1;\n"
    (BUILD / 'case-catalog-v2.sql').write_text(case + "SELECT CONVERT(varchar(64),HASHBYTES('SHA2_256',@caseCatalog),2) AS CaseCatalogV2Hash;\n")
    (SQL / 'phase3b8-confinement-prerequisite.sql').write_text(check + "SELECT CAST(0 AS int) CaseCatalogV2DriftFindingCount;\n")
    old_case = (BUILD / 'case-catalog.sql').read_text().split('SELECT CONVERT(varchar(64),HASHBYTES', 1)[0]
    old_case += "IF HASHBYTES('SHA2_256',@caseCatalog)<>0x" + (BUILD / 'case-catalog.sha256').read_text().strip() + " THROW 59100,'Authoritative Case/RLS catalog prerequisite drift.',1;\n"
    installer = (SQL / 'phase3b7-confinement.sql').read_text()
    assert installer.count(old_case) == 1
    installer = installer.replace(old_case, '').replace('Shale.AiCaseDataV1', 'Shale.AiCaseDataV2')
    lock_end = "IF @rc<0 THROW 59100,'Schema lock unavailable.',1;\n"
    installer = installer.replace(lock_end, lock_end + check, 1)
    # File checkout line endings must not change the 13 newly installed confined definitions.
    # Emit each reviewed body as UTF-16LE hex, not a multiline SQL literal.
    pattern = re.compile(r"EXEC\(N'(CREATE (?:PROCEDURE|FUNCTION|TRIGGER) dbo\.(?:[^']|'')*)'\);")
    count = [0]
    def hex_module(match):
        count[0] += 1
        body = match.group(1).replace("''", "'")
        return "SET @InstallDefinition=CONVERT(nvarchar(max),0x" + body.encode('utf-16le').hex() + "); EXEC sys.sp_executesql @InstallDefinition;"
    installer = pattern.sub(hex_module, installer)
    assert count[0] == 13, count
    installer = installer.replace('BEGIN TRY BEGIN TRANSACTION;', 'BEGIN TRY BEGIN TRANSACTION;\nDECLARE @InstallDefinition nvarchar(max);', 1)
    installer = installer.replace('/* Phase 3B.7 SQL confinement.', '/* Phase 3B.8 / AiCaseDataV2 confinement. V1 remains immutable.', 1)
    source_match = re.search(r'@sourceHash varbinary\(32\)=0x([0-9a-f]{64})', installer)
    installer = installer[:source_match.start(1)] + '0'*64 + installer[source_match.end(1):]
    source_hash = hashlib.sha256(installer.encode()).hexdigest()
    installer = installer[:source_match.start(1)] + source_hash + installer[source_match.end(1):]
    (SQL / 'phase3b8-confinement.sql').write_text(installer)
    verifier = (SQL / 'phase3b7-confinement-verify.sql').read_text()
    assert old_case in verifier
    verifier = verifier.replace(old_case, check).replace('Shale.AiCaseDataV1', 'Shale.AiCaseDataV2')
    old_source = dict(line.split('=', 1) for line in (RES / 'confinement-modules.properties').read_text().splitlines())['Source']
    verifier = verifier.replace(old_source, source_hash)
    (SQL / 'phase3b8-confinement-verify.sql').write_text(verifier)
    (RES / 'confinement-v2-catalog.sql').write_text(verifier)
    (RES / 'confinement-v2-modules.properties').write_text((RES / 'confinement-modules.properties').read_text().replace('Source='+old_source, 'Source='+source_hash))
    cumulative = (SQL / 'verify.sql').read_text()
    marker = '/* CONFINEMENT V2 DISPATCH END */\n'
    if marker in cumulative:
        cumulative = cumulative.split(marker, 1)[1]
    dispatch = "/* V2 receipt selects V2 exclusively. Failure never falls back to V1. */\nIF EXISTS(SELECT 1 FROM sys.extended_properties WHERE class=1 AND major_id=OBJECT_ID(N'dbo.AiCaseDataInvocation') AND name IN(N'Shale.AiCaseDataV2',N'Shale.AiCaseDataV2.Source')) BEGIN\n EXEC(N'" + verifier.replace("'", "''") + "');\n RETURN;\nEND;\n" + marker
    (SQL / 'verify.sql').write_text(dispatch + cumulative)
    print('V2 Case hash:', expected, 'V2 source:', source_hash)


if __name__ == '__main__':
    generate()
