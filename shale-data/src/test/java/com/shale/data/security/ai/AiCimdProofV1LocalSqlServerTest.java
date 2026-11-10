package com.shale.data.security.ai;

/** Same new proof assertions with the unchanged authoritative V1 confinement baseline. */
class AiCimdProofV1LocalSqlServerTest extends AiCimdProofLocalSqlServerTest {
    @Override AiCaseDataAttestation.Contract contract(){return AiCaseDataAttestation.Contract.V1;}
    @Override String confinementMigration(){return "phase3b7-confinement.sql";}
    @Override String confinementPrefix(){return "confinement";}
}
