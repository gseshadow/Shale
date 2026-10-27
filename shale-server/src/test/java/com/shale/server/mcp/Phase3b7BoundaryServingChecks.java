package com.shale.server.mcp;

import com.shale.core.runtime.DbSessionProvider;
import com.shale.data.dao.CaseSummaryDao;
import io.modelcontextprotocol.modern.JsonRpc.JSONRPCResponse;
import io.modelcontextprotocol.modern.McpSchema;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exact real wire-byte boundaries calibrated from valid authoritative source rows.
 * Ordinary fixture summary reads calibrate before each protected request; failed protected reads
 * are never retried. Actual SDK mapper/envelope and server identity are used for measurement. */
public final class Phase3b7BoundaryServingChecks {
    public static void run(String url,String token,Phase3b7FaultSource.Owner owner,DbSessionProvider ordinary)throws Exception {
        seed(owner);var dao=new CaseSummaryDao(ordinary);
        int initial=measure(dao.searchMinimizedCases("BND",7,72,0,10))[1];
        assertTrue(initial<65535,"Valid source starting point must fit below full response limit");
        var records=new ArrayList<Map<String,Object>>();
        try(var client=HttpClient.newHttpClient()) {
            for(int target:List.of(65535,65536,65537)) {
                int delta=target-initial,controls=delta/11;if((delta-controls*11)%2!=0)controls--;int unicode=(delta-controls*11)/2;
                assertTrue(controls>=0&&unicode>=0&&controls+unicode<=3520,"Exact byte target must be reachable with valid widths");
                int position=0;
                try(var c=owner.open();var p=c.prepareStatement("UPDATE dbo.Cases SET Name=?,CaseNumber=? WHERE Id=?")) {
                    for(int n=0;n<10;n++) {
                        var name=new StringBuilder("BND");var number=new StringBuilder();
                        for(int k=0;k<352;k++,position++){char ch=position<controls?'\u0001':position<controls+unicode?'é':'a';if(k<252)name.append(ch);else number.append(ch);}
                        p.setString(1,name.toString());p.setString(2,number.toString());p.setInt(3,500+n);p.executeUpdate();
                    }
                }
                int[] sizes=measure(dao.searchMinimizedCases("BND",7,72,0,10));assertEquals(target,sizes[1]);assertTrue(sizes[0]<=32768,"Full envelope boundary must be independent of the structured ceiling");
                String body="{\"jsonrpc\":\"2.0\",\"id\":\"boundary\",\"method\":\"tools/call\",\"params\":{\"_meta\":{\"io.modelcontextprotocol/protocolVersion\":\"2026-07-28\",\"io.modelcontextprotocol/clientCapabilities\":{}},\"name\":\"search_cases\",\"arguments\":{\"query\":\"BND\",\"page\":0}}}";
                var request=HttpRequest.newBuilder(URI.create(url)).timeout(java.time.Duration.ofSeconds(10)).header("Content-Type","application/json").header("Accept","application/json").header("Authorization","Bearer "+token).header("MCP-Protocol-Version","2026-07-28").header("Mcp-Method","tools/call").header("Mcp-Name","search_cases").POST(HttpRequest.BodyPublishers.ofString(body)).build();
                var response=client.send(request,HttpResponse.BodyHandlers.ofByteArray());assertEquals(200,response.statusCode());
                var result=McpTransportConfiguration.responseMapper().getJsonMapper().readTree(response.body()).path("result");
                if(target<=65536){assertFalse(result.path("isError").asBoolean());assertEquals(target,response.body().length);assertEquals(10,result.path("structuredContent").path("items").size());}
                else {assertTrue(result.path("isError").asBoolean());assertFalse(result.has("structuredContent"));assertEquals("PAYLOAD_TOO_LARGE",McpTransportConfiguration.responseMapper().getJsonMapper().readTree(result.path("content").get(0).path("text").asText()).path("code").asText());}
                records.add(Map.of("targetFullBytes",target,"structuredBytes",sizes[0],"actualResponseBytes",response.body().length,"protectedDisclosure",target<=65536));
            }
        }
        try(var c=owner.open();var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM dbo.IntegrationAccessAuditLog WHERE Outcome='ALLOWED_READ'")){assertTrue(r.next());assertEquals(3,r.getInt(1),"Oversize must retain its actual committed READ; no automatic reread");}
        Files.writeString(Path.of("../work/phase3b7/finished-response-boundaries.json"),McpTransportConfiguration.responseMapper().writeValueAsString(Map.of("scope","actual finished two-tool server; valid source; exact SDK wire envelope","search",records))+"\n");
    }
    private static int[] measure(Object page)throws Exception {
        var type=Class.forName("com.shale.data.security.ai.MinimizedCaseEncoder");var constructor=type.getDeclaredConstructor();constructor.setAccessible(true);
        var encoder=constructor.newInstance();var encode=type.getDeclaredMethod("encode",Object.class,UUID.class,java.io.OutputStream.class);encode.setAccessible(true);
        var bytes=new ByteArrayOutputStream();encode.invoke(encoder,page,new UUID(0,0),bytes);
        String text=bytes.toString(StandardCharsets.UTF_8);var mapper=McpTransportConfiguration.mapper();
        var result=McpSchema.CallToolResult.builder().isError(false).structuredContent(mapper.getJsonMapper().readValue(text,Map.class)).addContent(McpSchema.TextContent.builder(text).build()).build();
        var envelope=new LinkedHashMap<String,Object>(mapper.getJsonMapper().convertValue(result,Map.class));
        envelope.put("_meta",Map.of(McpSchema.MetaKeys.SERVER_INFO,McpSchema.Implementation.builder("shale-mcp","development").build()));
        return new int[]{bytes.size(),mapper.writeValueAsBytes(JSONRPCResponse.result("boundary",envelope)).length};
    }
    private static void seed(Phase3b7FaultSource.Owner owner)throws Exception {
        try(var c=owner.open();var s=c.createStatement()) {
            s.execute("SET IDENTITY_INSERT dbo.Roles ON; INSERT dbo.Roles(Id,Name,IsActive,CreatedAt,UpdatedAt,ShaleClientId) VALUES(4,N'Boundary Attorney',1,SYSUTCDATETIME(),SYSUTCDATETIME(),7),(11,N'Boundary Assistant',1,SYSUTCDATETIME(),SYSUTCDATETIME(),7); SET IDENTITY_INSERT dbo.Roles OFF");
            s.execute("INSERT dbo.PracticeAreas(ShaleClientId,Name,Color,SystemKey,IsActive,IsDeleted) VALUES(7,REPLICATE(NCHAR(1),150),'#6C757D','boundary',1,0); INSERT dbo.Statuses(ShaleClientId,Name,Color,SortOrder,SystemKey,LifecycleKey,IsClosed,IsActive,IsDeleted) VALUES(7,REPLICATE(NCHAR(1),50),'#6C757D',1,'boundary','closed',1,1,0); UPDATE dbo.Users SET name_first=REPLICATE(N'a',50),name_last=REPLICATE(N'a',50) WHERE Id IN(72,73) AND ShaleClientId=7");
            for(int n=0;n<10;n++) {
                s.execute("SET IDENTITY_INSERT dbo.Cases ON; INSERT dbo.Cases(Id,ShaleClientId,Name,CaseNumber,PracticeAreaId,IsDeleted,ClientEstate,FollowUpMeetWithClient,FollowUpNurseReview,FollowUpExpertReview,FollowUpCaseTransferred,AcceptedChronology,AcceptedConsultantExpertSearch,AcceptedTestifyingExpertSearch,AcceptedMedicalLiterature,DeniedChronology,FeeAgreementSigned,MedicalRecordsRequested,CreatedAt,UpdatedAt) SELECT "+(500+n)+",7,N'BND'+REPLICATE(N'a',252),REPLICATE(N'a',100),(SELECT Id FROM dbo.PracticeAreas WHERE SystemKey='boundary'),0,0,0,0,0,0,0,0,0,0,0,0,0,CreatedAt,UpdatedAt FROM dbo.Cases WHERE Id=100; SET IDENTITY_INSERT dbo.Cases OFF");
                s.execute("INSERT dbo.CaseStatuses(CaseId,StatusId,EffectiveDate,IsPrimary,CreatedAt,UpdatedAt) SELECT "+(500+n)+",Id,SYSUTCDATETIME(),1,SYSUTCDATETIME(),SYSUTCDATETIME() FROM dbo.Statuses WHERE SystemKey='boundary'; INSERT dbo.CaseUsers(ShaleClientId,CaseId,UserId,RoleId,IsPrimary,CreatedAt,UpdatedAt) VALUES(7,"+(500+n)+",72,4,1,SYSUTCDATETIME(),SYSUTCDATETIME()),(7,"+(500+n)+",73,11,1,SYSUTCDATETIME(),SYSUTCDATETIME())");
            }
        }
    }
}
