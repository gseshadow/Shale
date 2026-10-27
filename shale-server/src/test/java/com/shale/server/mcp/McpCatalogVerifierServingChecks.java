package com.shale.server.mcp;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Owner-only public grant injection: a valid ACCESS must be denied before serving under drift. */
public final class McpCatalogVerifierServingChecks {
    public static void run(String url,String token,Phase3b7FaultSource.Owner owner)throws Exception {
        try(var client=HttpClient.newHttpClient()) {
            assertEquals(200,send(client,url,token).statusCode());
            try(var c=owner.open();var s=c.createStatement()){s.execute("GRANT SELECT ON dbo.Cases TO public");}
            try {
                var denied=send(client,url,token);assertEquals(401,denied.statusCode());
                assertFalse(denied.body().contains("structuredContent"));assertFalse(denied.body().contains("Direct overview"));
            } finally {try(var c=owner.open();var s=c.createStatement()){s.execute("REVOKE SELECT ON dbo.Cases FROM public");}}
            assertEquals(200,send(client,url,token).statusCode(),"Restoration requires fresh attestation");
        }
    }
    private static HttpResponse<String> send(HttpClient client,String url,String token)throws Exception {
        String body=new tools.jackson.databind.ObjectMapper().writeValueAsString(Map.of("jsonrpc","2.0","id","catalog-drift","method","server/discover","params",Map.of("_meta",Map.of("io.modelcontextprotocol/protocolVersion",McpGate.PROTOCOL,"io.modelcontextprotocol/clientCapabilities",Map.of()))));
        var request=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).header("Authorization","Bearer "+token).header("Content-Type","application/json").header("Accept","application/json").header("MCP-Protocol-Version",McpGate.PROTOCOL).header("Mcp-Method","server/discover").POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return client.send(request,HttpResponse.BodyHandlers.ofString());
    }
}
