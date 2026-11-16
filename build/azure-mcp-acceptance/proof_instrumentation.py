"""Pure byte-preserving reference for reviewing v1/v2 acceptance transforms. No SQL/processes."""
from pathlib import Path
import re

TOOL = Path(__file__).resolve().parent
BEGIN = '/* ACCEPTANCE_PROOF_V2_BEGIN */\n'
END = '/* ACCEPTANCE_PROOF_V2_END */\n'
THROW = "THROW 59400,'CIMD proof module source/owner/context drift.',1;"
CATCH = 'END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK TRANSACTION; THROW; END CATCH;'
SEAL = re.compile(r'DECLARE @C4aSealVerify nvarchar\(max\)=CONVERT\(nvarchar\(max\),0x([0-9a-f]+)\); EXEC sys.sp_executesql @C4aSealVerify;')


def block(text):
    return BEGIN + text + END


def assertion(body, phase):
    lines = re.findall(r'^IF EXISTS\(SELECT 1 FROM @C4aModules e WHERE NOT EXISTS\([^\r\n]+', body, flags=re.M)
    if len(lines) != 1 or not lines[0].endswith(THROW):
        raise ValueError('Unsupported assertion')
    diagnostic = (TOOL / 'ProofModuleFailure.sql').read_text().replace('__PHASE__', phase)
    changed = lines[0][:-len(THROW)] + block('BEGIN\n' + diagnostic + '\n') + THROW + block('\nEND;\n')
    return body.replace(lines[0], changed)


def instrument(body, installer):
    if not installer:
        return assertion(body, 'STANDALONE')
    body = assertion(body, 'INSTALL')
    seals = list(SEAL.finditer(body))
    if len(seals) != 1:
        raise ValueError('Unsupported seal')
    seal = seals[0]
    decoded = bytes.fromhex(seal[1]).decode('utf-16le')
    changed = assertion(decoded, 'SEAL').encode('utf-16le').hex()
    body = body.replace(seal[0], seal[0].replace(seal[1], changed))
    body = body.replace('BEGIN TRY BEGIN TRANSACTION;', block("DECLARE @AcceptanceProofPhase varchar(16)='INSTALL';\n") + 'BEGIN TRY BEGIN TRANSACTION;')
    body = body.replace('DECLARE @C4aSealVerify nvarchar(max)=', block("SET @AcceptanceProofPhase='SEAL';\n") + 'DECLARE @C4aSealVerify nvarchar(max)=')
    if body.count(CATCH) != 1:
        raise ValueError('Unsupported catch')
    state = (TOOL / 'ProofCatchState.sql').read_text()
    rollback = (TOOL / 'ProofRollbackState.sql').read_text()
    return body.replace(CATCH, 'END TRY BEGIN CATCH ' + block(state) + 'IF XACT_STATE()<>0 ROLLBACK TRANSACTION; ' + block(rollback) + 'THROW; END CATCH;')


def strip_v2(text):
    # Recover the nested verifier first: it is transport code, not one of the 16 modules.
    def original_seal(match):
        decoded = bytes.fromhex(match[1]).decode('utf-16le')
        decoded = re.sub(re.escape(BEGIN) + '.*?' + re.escape(END), '', decoded, flags=re.S)
        return match[0].replace(match[1], decoded.encode('utf-16le').hex())
    text = SEAL.sub(original_seal, text)
    return re.sub(re.escape(BEGIN) + '.*?' + re.escape(END), '', text, flags=re.S)
