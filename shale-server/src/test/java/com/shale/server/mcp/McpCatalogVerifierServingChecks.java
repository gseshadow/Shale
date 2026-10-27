package com.shale.server.mcp;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Owner-only public grant injection: a valid ACCESS must be denied before serving under drift. */
public final class McpCatalogVerifierServingChecks {
    public static void run(String url,String token,Phase3b7FaultSource.Owner owner)throws Exception {
        try(var client=HttpClient.newHttpClient()) {
            assertEquals(200,send(client,url,token).statusCode());
            for(String[] fault:publicFaults()) {
                try(var c=owner.open();var s=c.createStatement()){s.execute(fault[0]);}
                try {
                    var denied=send(client,url,token);assertEquals(401,denied.statusCode(),fault[0]);
                    assertFalse(denied.body().contains("structuredContent"));assertFalse(denied.body().contains("Direct overview"));
                } finally {try(var c=owner.open();var s=c.createStatement()){s.execute(fault[1]);}}
                assertEquals(200,send(client,url,token).statusCode(),"Restoration requires fresh attestation");
            }
        }
    }
    /** Exact inverse statements, exclusively for owned disposable databases. */
    public static List<String[]> publicFaults() {
        return List.of(
                new String[]{"GRANT SELECT ON dbo.Cases TO public","REVOKE SELECT ON dbo.Cases FROM public"},
                new String[]{"GRANT EXECUTE ON dbo.AiDataAuthenticateV1 TO public","REVOKE EXECUTE ON dbo.AiDataAuthenticateV1 FROM public"},
                new String[]{"GRANT SELECT ON SCHEMA::dbo TO public","REVOKE SELECT ON SCHEMA::dbo FROM public"},
                new String[]{"GRANT EXECUTE ON SCHEMA::dbo TO public","REVOKE EXECUTE ON SCHEMA::dbo FROM public"},
                new String[]{"GRANT SELECT TO public","REVOKE SELECT FROM public"},
                new String[]{"GRANT EXECUTE TO public","REVOKE EXECUTE FROM public"},
                new String[]{"GRANT SELECT ON dbo.Cases(Name) TO public","REVOKE SELECT ON dbo.Cases(Name) FROM public"},
                new String[]{"GRANT SELECT ON dbo.Cases TO public WITH GRANT OPTION","REVOKE SELECT ON dbo.Cases FROM public CASCADE"});
    }
    private static HttpResponse<String> send(HttpClient client,String url,String token)throws Exception {
        String body=new tools.jackson.databind.ObjectMapper().writeValueAsString(Map.of("jsonrpc","2.0","id","catalog-drift","method","server/discover","params",Map.of("_meta",Map.of("io.modelcontextprotocol/protocolVersion",McpGate.PROTOCOL,"io.modelcontextprotocol/clientCapabilities",Map.of()))));
        var request=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).header("Authorization","Bearer "+token).header("Content-Type","application/json").header("Accept","application/json").header("MCP-Protocol-Version",McpGate.PROTOCOL).header("Mcp-Method","server/discover").POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return client.send(request,HttpResponse.BodyHandlers.ofString());
    }
}
