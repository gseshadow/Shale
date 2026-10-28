package com.shale.server.mcp;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Owner-only authority faults: a valid ACCESS must be denied before serving under drift. */
public final class McpCatalogVerifierServingChecks {
    public static void run(String url,String token,Phase3b7FaultSource.Owner owner,String ai,String catalog)throws Exception {
        try(var client=HttpClient.newHttpClient()) {
            assertEquals(200,send(client,url,token).statusCode());
            for(String[] fault:authorityFaults(ai,catalog)) {
                try(var c=owner.open();var s=c.createStatement()){s.execute(fault[0]);}
                try {
                    var denied=send(client,url,token);assertEquals(401,denied.statusCode(),fault[0]);
                    assertFalse(denied.body().contains("structuredContent"));assertFalse(denied.body().contains("Direct overview"));
                    var overview=send(client,url,token,true);assertEquals(401,overview.statusCode(),fault[0]);
                    assertFalse(overview.body().contains("structuredContent"));assertFalse(overview.body().contains("Direct overview"));
                } finally {try(var c=owner.open();var s=c.createStatement()){s.execute(fault[1]);}}
                assertEquals(200,send(client,url,token).statusCode(),"Restoration requires fresh attestation");
            }
        }
    }
    /** Each fault is independently installed and removed; names come from nonce-owned fixtures. */
    public static List<String[]> authorityFaults(String ai,String catalog) {
        var faults=new java.util.ArrayList<String[]>(publicFaults());
        faults.add(new String[]{"EXEC(N'CREATE VIEW dbo.AttestationPublicView AS SELECT Id FROM dbo.Cases'); GRANT SELECT ON dbo.AttestationPublicView TO public", "DROP VIEW dbo.AttestationPublicView"});
        faults.add(new String[]{"EXEC(N'CREATE FUNCTION dbo.AttestationPublicFunction() RETURNS int AS BEGIN RETURN 1 END'); GRANT EXECUTE ON dbo.AttestationPublicFunction TO public", "DROP FUNCTION dbo.AttestationPublicFunction"});
        for(String identity:List.of(ai,catalog)) {
            String user="["+identity.replace("]","]]")+"]";
            for(String permission:List.of("SELECT ON dbo.Cases","SELECT ON dbo.Cases(Name)","SELECT ON SCHEMA::dbo","SELECT","EXECUTE","EXECUTE ON dbo.AiDataAuthenticateV1","IMPERSONATE ON USER::dbo","CREATE TABLE","ALTER ANY ROLE","CONTROL","TAKE OWNERSHIP"))
                faults.add(new String[]{"GRANT "+permission+" TO "+user,"REVOKE "+permission+" FROM "+user});
            faults.add(new String[]{"GRANT SELECT ON dbo.Cases TO "+user+" WITH GRANT OPTION","REVOKE SELECT ON dbo.Cases FROM "+user+" CASCADE"});
            faults.add(new String[]{"CREATE SCHEMA AttestationOwned AUTHORIZATION "+user,"DROP SCHEMA AttestationOwned"});
            faults.add(new String[]{"CREATE ROLE AttestationOwnedRole AUTHORIZATION "+user,"DROP ROLE AttestationOwnedRole"});
            faults.add(new String[]{"EXEC(N'CREATE VIEW dbo.AttestationDirectView AS SELECT Id FROM dbo.Cases'); GRANT SELECT ON dbo.AttestationDirectView TO "+user,"DROP VIEW dbo.AttestationDirectView"});
            faults.add(new String[]{"EXEC(N'CREATE FUNCTION dbo.AttestationDirectFunction() RETURNS int AS BEGIN RETURN 1 END'); GRANT EXECUTE ON dbo.AttestationDirectFunction TO "+user,"DROP FUNCTION dbo.AttestationDirectFunction"});
        }
        return faults;
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
        return send(client,url,token,false);
    }
    private static HttpResponse<String> send(HttpClient client,String url,String token,boolean overview)throws Exception {
        String method=overview?"tools/call":"server/discover";
        var params=new java.util.LinkedHashMap<String,Object>();
        params.put("_meta",Map.of("io.modelcontextprotocol/protocolVersion",McpGate.PROTOCOL,"io.modelcontextprotocol/clientCapabilities",Map.of()));
        if(overview){params.put("name","get_case_overview");params.put("arguments",Map.of("caseId",100));}
        String body=new tools.jackson.databind.ObjectMapper().writeValueAsString(Map.of("jsonrpc","2.0","id","catalog-drift","method",method,"params",params));
        var request=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).header("Authorization","Bearer "+token).header("Content-Type","application/json").header("Accept","application/json").header("MCP-Protocol-Version",McpGate.PROTOCOL).header("Mcp-Method",method).POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return client.send(request,HttpResponse.BodyHandlers.ofString());
    }
}
