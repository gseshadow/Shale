package com.shale.data.security.ai;

/** Same focused PR4C assertions on the authoritative V1 confinement baseline. */
class AiCimdRuntimeAuthoritativeV1SqlServerTest extends AiCimdRuntimeAuthoritativeSqlServerTest {
    @Override AiCaseDataAttestation.Contract contract(){return AiCaseDataAttestation.Contract.V1;}
    @Override String confinementMigration(){return "phase3b7-confinement.sql";}
    @Override String confinementPrefix(){return "confinement";}
}
