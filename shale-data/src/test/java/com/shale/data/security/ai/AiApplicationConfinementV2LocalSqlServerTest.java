package com.shale.data.security.ai;

/** Same new compatibility assertions on the unmodified authoritative candidate with V2. */
class AiApplicationConfinementV2LocalSqlServerTest extends AiApplicationConfinementLocalSqlServerTest {
    @Override AiCaseDataAttestation.Contract contract(){return AiCaseDataAttestation.Contract.V2;}
    @Override String confinementMigration(){return "phase3b8-confinement.sql";}
    @Override String confinementPrefix(){return "confinement-v2";}
}
