package com.shale.data.security.ai;

/** Minimum signed catalog compatibility repetition on the unchanged V1 foundation. */
class AiCimdCatalogV2V1SqlServerTest extends AiCimdCatalogV2SqlServerTest {
    @Override AiCaseDataAttestation.Contract contract(){return AiCaseDataAttestation.Contract.V1;}
    @Override String confinementMigration(){return "phase3b7-confinement.sql";}
    @Override String confinementPrefix(){return "confinement";}
}
