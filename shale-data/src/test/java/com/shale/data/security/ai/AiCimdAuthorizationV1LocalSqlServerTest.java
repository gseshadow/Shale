package com.shale.data.security.ai;

/** Same complete verified composition on the preserved V1 historical contract. */
class AiCimdAuthorizationV1LocalSqlServerTest extends AiCimdAuthorizationLocalSqlServerTest {
    @Override AiCaseDataAttestation.Contract contract(){return AiCaseDataAttestation.Contract.V1;}
    @Override String confinementMigration(){return "phase3b7-confinement.sql";}
    @Override String confinementPrefix(){return "confinement";}
}
