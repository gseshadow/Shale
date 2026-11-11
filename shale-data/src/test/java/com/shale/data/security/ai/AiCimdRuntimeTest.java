package com.shale.data.security.ai;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AiCimdRuntimeTest {
    static Map<String,String> settings() {
        var values=new HashMap<String,String>();
        for(var role:AiCimdIdentity.values()) {
            String prefix="shale.ai.cimd."+role.key();
            values.put(prefix+".jdbc-url","jdbc:sqlserver://127.0.0.1:9;databaseName=synthetic");
            values.put(prefix+".username","synthetic-"+role.key()); values.put(prefix+".password","secret-sentinel");
        }
        values.put("shale.ai.cimd.catalog-version","V2"); values.put("shale.ai.cimd.catalog-certificate-sha256","a".repeat(64)); values.put("shale.ai.cimd.catalog-owner-sid","b".repeat(32));
        values.put("shale.ai.cimd.database","synthetic"); values.put("shale.ai.cimd.issuer","https://issuer.test/oauth");
        values.put("shale.ai.cimd.resource","https://issuer.test/mcp"); values.put("shale.ai.cimd.contract-version","V2"); return values;
    }
    @Test void everyDedicatedSettingIsRequiredBeforeAnyPoolAndSecretsAreRedacted() {
        var settings=settings();
        for(String key:settings.keySet()) {
            var missing=new HashMap<>(settings); missing.remove(key);
            var failure=assertThrows(IllegalStateException.class,()->AiCimdRuntime.Configuration.read(missing::get),key);
            assertFalse(failure.toString().contains("secret-sentinel"));
        }
        assertThrows(IllegalStateException.class,()->AiCimdRuntime.Configuration.read(k->null));
        var config=AiCimdRuntime.Configuration.read(settings::get);
        assertFalse(config.toString().contains("secret-sentinel"));
        config.endpoints().values().forEach(e->assertFalse(e.toString().contains("secret-sentinel")));
    }
    @Test void duplicateIdentityUnsafeTransportAndUnknownConfinementCannotCompose() {
        var settings=settings();
        settings.put("shale.ai.cimd.issuer.username",settings.get("shale.ai.cimd.browser.username"));
        var duplicate=settings; assertThrows(IllegalStateException.class,()->AiCimdRuntime.Configuration.read(duplicate::get));
        settings=settings(); settings.put("shale.ai.cimd.browser.jdbc-url","jdbc:sqlserver://localhost;trustServerCertificate=true");
        var badTls=settings; assertThrows(IllegalStateException.class,()->AiCimdRuntime.Configuration.read(badTls::get));
        var unknown=settings(); unknown.put("shale.ai.cimd.contract-version","V3");
        assertThrows(IllegalStateException.class,()->AiCimdRuntime.Configuration.read(unknown::get));
    }
    @Test void catalogV2RequiresExplicitVersionAndExactIndependentBinding() {
        for(String version:List.of("V1","V3","")) {
            var settings=settings(); settings.put("shale.ai.cimd.catalog-version",version);
            assertThrows(IllegalStateException.class,()->AiCimdRuntime.Configuration.read(settings::get));
        }
        for(String key:List.of("shale.ai.cimd.catalog-owner-sid","shale.ai.cimd.catalog-certificate-sha256")) {
            var settings=settings(); settings.put(key,"invalid");
            assertThrows(IllegalStateException.class,()->AiCimdRuntime.Configuration.read(settings::get));
        }
        assertEquals("CimdCatalogBinding[redacted]",AiCimdRuntime.Configuration.read(settings()::get).catalogBinding().toString());
    }
    @Test void poolsHaveIndependentIdentityAndFixedValidatedTlsWithBoundedResources() {
        var config=AiCimdRuntime.Configuration.read(settings()::get);
        var names=new HashSet<String>();
        for(var role:AiCimdIdentity.values()) {
            var pool=AiCimdRuntime.poolConfiguration(role,config.endpoints().get(role));
            assertTrue(names.add(pool.getPoolName())); assertEquals(config.endpoints().get(role).username(),pool.getUsername());
            assertEquals("true",pool.getDataSourceProperties().get("encrypt"));
            assertEquals("false",pool.getDataSourceProperties().get("trustServerCertificate"));
            assertEquals(0,pool.getMinimumIdle()); assertEquals(2,pool.getMaximumPoolSize()); assertEquals(3000,pool.getConnectionTimeout());
        }
    }
    @Test void caseNamespaceIsExplicitForAllPhasesAndRejectsArbitraryProcedures() {
        for(String suffix:List.of("AuthenticateV1","SearchCasesV1","GetCaseOverviewV1","AuthorizeCaseHandoffV1","RecordCaseFailureV1","FinishCaseQuotaV1")) {
            assertEquals("AiCimdData"+suffix,AiCaseDataAccess.Namespace.CIMD_V1.procedure("AiData"+suffix));
            assertEquals("AiData"+suffix,AiCaseDataAccess.Namespace.LEGACY_V1.procedure("AiData"+suffix));
        }
        assertThrows(IllegalArgumentException.class,()->AiCaseDataAccess.Namespace.CIMD_V1.procedure("AiIssueCredentialsV1"));
    }
}
