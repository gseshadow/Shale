package com.shale.data.security.ai;

/** Focused issuer compatibility on the unchanged authoritative V1 Case baseline. */
class AiCimdOAuthV1LocalSqlServerTest extends AiCimdOAuthLocalSqlServerTest {
    @Override AiCaseDataAttestation.Contract contract(){return AiCaseDataAttestation.Contract.V1;}
    @Override String confinementMigration(){return "phase3b7-confinement.sql";}
    @Override String confinementPrefix(){return "confinement";}
}
