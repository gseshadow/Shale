package com.shale.data.config;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class AcceptanceVerificationBudgetTest {
    private static Map<String,String> acceptance() {
        var env=new HashMap<String,String>();
        env.put("SHALE_AI_ACCEPTANCE_SLOWVERIFICATION","true");
        env.put("WEBSITE_SITE_NAME","shale-mcp-acceptance");
        env.put("SHALE_AI_DATA_DATABASE","ShaleMcpAcceptance");
        env.put("SHALE_AI_OAUTH_PUBLICORIGIN","https://shale-mcp-acceptance.azurewebsites.net");
        env.put("SHALE_AI_OAUTH_IDENTITY","PRODUCTION");
        env.put("SPRING_PROFILES_ACTIVE","azure");
        for(String key:AcceptanceVerificationBudget.sourceKeys())
            env.put(key,"jdbc:sqlserver://shale.database.windows.net:1433;databaseName=ShaleMcpAcceptance");
        return env;
    }
    @Test void onlyTheExactAcceptanceWorkerAndEveryAcceptanceDatasourceCanEnableLongBudgets() {
        assertFalse(AcceptanceVerificationBudget.enabled(Map.of()));
        assertFalse(AcceptanceVerificationBudget.enabled(Map.of("SHALE_AI_ACCEPTANCE_SLOWVERIFICATION","false")));
        assertTrue(AcceptanceVerificationBudget.enabled(acceptance()));
        for(String key:acceptance().keySet())for(boolean missing:List.of(false,true)) {
            var env=acceptance();
            if(missing)env.remove(key);else env.put(key,"PRIVATE_OTHER_TARGET");
            if(key.equals("SHALE_AI_ACCEPTANCE_SLOWVERIFICATION")&&missing)assertFalse(AcceptanceVerificationBudget.enabled(env));
            else {
                var error=assertThrows(IllegalStateException.class,()->AcceptanceVerificationBudget.enabled(env),key);
                assertFalse(error.getMessage().contains("PRIVATE_"));
            }
        }
        assertEquals(17,AcceptanceVerificationBudget.sourceKeys().size(),"All dedicated source destinations must be fenced");
    }
    @Test void normalBudgetsRemainUnchangedAndAcceptanceOverridesAreEffectiveInAnIsolatedProcess()throws Exception {
        for(boolean slow:List.of(false,true)) {
            var process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),
                    "-cp",System.getProperty("java.class.path"),Probe.class.getName()).redirectErrorStream(true);
            process.environment().keySet().removeIf(k->k.startsWith("SHALE_")||k.startsWith("SPRING_")||k.equals("WEBSITE_SITE_NAME"));
            if(slow)process.environment().putAll(acceptance());
            var child=process.start();
            try {
                assertTrue(child.waitFor(10,TimeUnit.SECONDS),"Pure timeout-policy probe must terminate without opening SQL");
                String result=new String(child.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).trim();
                assertEquals(0,child.exitValue(),result);
                assertEquals(slow?"600000000000,600000,180000,180,30":"10000000000,10000,5000,3,2",result);
            } finally {if(child.isAlive())child.destroyForcibly();}
        }
    }
    public static final class Probe {
        public static void main(String[] args) {
            System.out.print(AcceptanceVerificationBudget.operationNanos(10_000_000_000L)+","
                    +AcceptanceVerificationBudget.operationMillis(10000)+","
                    +AcceptanceVerificationBudget.networkMillis(5000)+","
                    +AcceptanceVerificationBudget.querySeconds(3)+","
                    +AcceptanceVerificationBudget.loginSeconds(2));
        }
    }
}
