package com.shale.data.security.ai;

class AiApplicationCombinedConfinementV2LocalSqlServerTest extends AiApplicationCombinedConfinementLocalSqlServerTest {
    @Override AiCaseDataAttestation.Contract contract(){return AiCaseDataAttestation.Contract.V2;}
    @Override String confinementMigration(){return "phase3b8-confinement.sql";}
    @Override String confinementPrefix(){return "confinement-v2";}
}
