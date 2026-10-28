package com.shale.server.oauth;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual HTTP -> ordinary auth/session/Case SQL, with no replacement application beans. */
final class ProductionOrdinarySmoke {
    private final String origin;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final tools.jackson.databind.ObjectMapper json=new tools.jackson.databind.ObjectMapper();
    private int checks;
    private ProductionOrdinarySmoke(int port){origin="http://127.0.0.1:"+port;}
    static int run(int port)throws Exception {
        var smoke=new ProductionOrdinarySmoke(port);
        try {smoke.check();return smoke.checks;}finally{smoke.client.close();}
    }
    private void check()throws Exception {
        call("/api/auth/login",Map.of("email","a72@example.invalid","password","wrong"),null,401);
        String web=call("/api/auth/login",Map.of("email","a72@example.invalid","password","synthetic-password"),null,200).path("accessToken").asText();assertFalse(web.isBlank());
        call("/api/auth/me",null,web,200);
        var search=call("/api/v2/cases/search-page",Map.of("query","Direct overview","page",0,"size",10),web,200);assertFalse(search.path("items").isEmpty());
        call("/api/v2/cases/100/overview",null,web,200);
        for(int id:List.of(9001,9002,999999))call("/api/v2/cases/"+id+"/overview",null,web,404);
        String refreshed=call("/api/auth/refresh",Map.of(),web,200).path("accessToken").asText();assertNotEquals(web,refreshed);
        call("/api/auth/me",null,web,401);call("/api/auth/me",null,refreshed,200);
        call("/api/sessions",null,refreshed,200);
        call("/api/auth/logout",Map.of(),refreshed,200);call("/api/auth/me",null,refreshed,401);
        UUID installation=UUID.randomUUID();
        var enrolled=call("/api/auth/desktop-session",Map.of("email","a72@example.invalid","password","synthetic-password","remember",true,"installationId",installation),null,200);
        String desktop=enrolled.path("accessToken").asText(),remember=enrolled.path("rememberCredential").asText();assertFalse(desktop.isBlank());assertFalse(remember.isBlank());
        call("/api/auth/me",null,desktop,200);
        String replacement=UUID.randomUUID().toString()+UUID.randomUUID();
        var restored=call("/api/auth/desktop-session/restore",Map.of("credential",remember,"replacementCredential",replacement,"installationId",installation),null,200);
        String restoredToken=restored.path("accessToken").asText();assertFalse(restoredToken.isBlank());
        call("/api/auth/me",null,desktop,401);call("/api/auth/me",null,restoredToken,200);
        call("/api/auth/desktop-session/restore",Map.of("credential",remember,"replacementCredential",replacement,"installationId",installation),null,401);
        call("/api/auth/logout",Map.of(),restoredToken,200);call("/api/auth/me",null,restoredToken,401);
        call("/api/auth/desktop-session/restore",Map.of("credential",replacement,"replacementCredential",remember,"installationId",installation),null,401);
    }
    private tools.jackson.databind.JsonNode call(String path,Object body,String token,int expected)throws Exception {
        var request=HttpRequest.newBuilder(URI.create(origin+path)).timeout(Duration.ofSeconds(10));
        if(token!=null)request.header("Authorization","Bearer "+token);
        if(body==null)request.GET();else request.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        // Test-only timing: never print bodies, identities, Authorization headers or exceptions.
        long started=System.nanoTime();
        System.err.printf("ORDINARY_HTTP start check=%d path=%s%n",checks+1,path);
        try {
            var response=client.send(request.build(),HttpResponse.BodyHandlers.ofString());
            System.err.printf("ORDINARY_HTTP finish check=%d status=%d elapsedMs=%d%n",
                    checks+1,response.statusCode(),(System.nanoTime()-started)/1_000_000);
            assertEquals(expected,response.statusCode(),path+" status; response payload intentionally omitted");checks++;
            return json.readTree(response.body());
        } catch(Exception failure) {
            System.err.printf("ORDINARY_HTTP failed check=%d elapsedMs=%d type=%s%n",
                    checks+1,(System.nanoTime()-started)/1_000_000,failure.getClass().getSimpleName());
            throw failure;
        }
    }
}
