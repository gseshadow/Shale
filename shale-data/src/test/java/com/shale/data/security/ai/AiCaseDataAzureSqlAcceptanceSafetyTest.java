package com.shale.data.security.ai;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Offline guards: unsafe targets/secret reuse must fail before opening any connection. */
class AiCaseDataAzureSqlAcceptanceSafetyTest {
    private static Map<String,String> settings() {
        var env=new HashMap<String,String>();String db="ShaleAzureReadOnly_"+"a".repeat(32);
        env.put("SHALE_AZURE_DATABASE",db);env.put("SHALE_AZURE_JDBC_URL","jdbc:sqlserver://shale-attest-test.database.windows.net:1433;databaseName="+db);
        env.put("SHALE_AZURE_CONTRACT_VERSION","V2");env.put("SHALE_AZURE_ISSUER",AiCaseDataLocalSqlServerTest.ISSUER);env.put("SHALE_AZURE_RESOURCE",AiCaseDataLocalSqlServerTest.RESOURCE);
        for(String identity:AiCaseDataAzureSqlDatabaseReadOnlyTest.IDENTITIES) {
            env.put("SHALE_AZURE_"+identity+"_USERNAME","azro_"+identity.toLowerCase(Locale.ROOT));
            env.put("SHALE_AZURE_"+identity+"_PASSWORD","synthetic-independent-"+identity);
        }
        return env;
    }
    @Test void explicitDisposableV2ConfigurationIsAcceptedOffline(){assertDoesNotThrow(()->AiCaseDataAzureSqlDatabaseReadOnlyTest.validate(settings()));}
    @Test void productionImplicitDatabaseTlsOverridesAndAlternatePlatformsAreRefused() {
        var env=settings();String db=env.get("SHALE_AZURE_DATABASE");
        for(String url:List.of("jdbc:sqlserver://shale.database.windows.net;databaseName="+db,"jdbc:sqlserver://shale-attest-test.database.windows.net","jdbc:sqlserver://localhost:1433;databaseName="+db,env.get("SHALE_AZURE_JDBC_URL")+";trustServerCertificate=true",env.get("SHALE_AZURE_JDBC_URL")+";databaseName=Shale")) {
            var changed=new HashMap<>(env);changed.put("SHALE_AZURE_JDBC_URL",url);
            assertThrows(IllegalArgumentException.class,()->AiCaseDataAzureSqlDatabaseReadOnlyTest.validate(changed));
        }
        env.put("SHALE_AZURE_DATABASE","Shale");assertThrows(IllegalArgumentException.class,()->AiCaseDataAzureSqlDatabaseReadOnlyTest.validate(env));
    }
    @Test void reusedSecretsIdentitiesWrongContractAndBindingAreRefused() {
        for(var entry:Map.of("AI_PASSWORD","SHALE_AZURE_VERIFIER_PASSWORD","AI_USERNAME","SHALE_AZURE_VERIFIER_USERNAME").entrySet()) {
            var env=settings();env.put("SHALE_AZURE_"+entry.getKey(),env.get(entry.getValue()));assertThrows(IllegalArgumentException.class,()->AiCaseDataAzureSqlDatabaseReadOnlyTest.validate(env));
        }
        for(var entry:Map.of("CONTRACT_VERSION","V1","ISSUER","https://production.example/oauth/mcp","RESOURCE","https://production.example/mcp").entrySet()) {
            var env=settings();env.put("SHALE_AZURE_"+entry.getKey(),entry.getValue());assertThrows(IllegalArgumentException.class,()->AiCaseDataAzureSqlDatabaseReadOnlyTest.validate(env));
        }
    }
}
