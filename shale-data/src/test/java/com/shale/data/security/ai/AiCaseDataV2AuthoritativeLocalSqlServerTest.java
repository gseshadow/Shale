package com.shale.data.security.ai;

/** Same V2 assertions against the unmodified authoritative candidate representation. */
class AiCaseDataV2AuthoritativeLocalSqlServerTest extends AiCaseDataV2LocalSqlServerTest {
    @Override boolean productionVariant(){return false;}
}
