package com.shale.server.mcp;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual task-owned Caddy TLS reverse proxy, finished-server conformance and bounded timings.
 * No insecure TLS, header-only TLS claim, live ingress, diagnostic MCP tool or production hook. */
public final class Phase3b7ServingChecks {
    private static final Path ROOT=Path.of("..").toAbsolutePath().normalize(),OUT=ROOT.resolve("work/phase3b7");
    private static final String HOST=Boolean.getBoolean("shale.acceptance.production")?"shale.example":"shale.test";
    private static final tools.jackson.databind.ObjectMapper JSON=new tools.jackson.databind.ObjectMapper();
    public static void run(String backend,String performanceToken,String conformanceToken,Path temp,Phase3b7FaultSource.Owner owner)throws Exception {
        String event=beginSqlMetrics(owner);
        try {performance(backend,performanceToken);}
        finally {finishSqlMetrics(owner,event,performanceToken);}
        tls(backend,conformanceToken,temp);
        conformance(backend,conformanceToken);
    }
    /** Complete frozen revision inventory; unsupported diagnostic/extension cases remain raw failures. */
    public static void frozen(String backend,String token)throws Exception {
        Path reports=OUT.resolve("finished-conformance-frozen");Files.createDirectories(reports);
        var command=List.of("node","--import",ROOT.resolve("build/mcp-transport/conformance-auth.mjs").toString(),
                ROOT.resolve("build/mcp-transport/node_modules/@modelcontextprotocol/conformance/dist/index.js").toString(),
                "server","--url",backend,"--requirements","2026-07-28","--timeout","60000","--output-dir",reports.toString());
        Path log=OUT.resolve("finished-conformance-frozen.log");
        var builder=new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile());
        builder.environment().put("MCP_URL",backend);builder.environment().put("MCP_TOKEN",token);
        var process=builder.start();
        try {assertTrue(process.waitFor(270,TimeUnit.SECONDS),"Frozen current revision suite must finish within bounded execution");}
        finally {if(process.isAlive()){process.destroyForcibly();process.waitFor();}}
        try(var paths=Files.walk(reports)) {
            for(Path p:paths.filter(Files::isRegularFile).toList())Files.writeString(p,Files.readString(p).replace(token,"[REDACTED]"));
        }
        Files.writeString(log,Files.readString(log).replace(token,"[REDACTED]"));
        Files.writeString(OUT.resolve("finished-conformance-frozen-execution.json"),JSON.writeValueAsString(Map.of(
                "suite","@modelcontextprotocol/conformance 0.2.0-alpha.12","requirements","2026-07-28",
                "exit",process.exitValue(),"command",command,"diagnosticToolsAdded",false))+"\n");
    }
    public static void modern(String backend,String token)throws Exception {
        for(String scenario:List.of("server-stateless","http-header-validation","caching")) {
            Path reports=OUT.resolve("modern-"+scenario);Files.createDirectories(reports);
            var command=List.of("node","--import",ROOT.resolve("build/mcp-transport/conformance-auth.mjs").toString(),ROOT.resolve("build/mcp-transport/node_modules/@modelcontextprotocol/conformance/dist/index.js").toString(),"server","--url",backend,"--scenario",scenario,"--spec-version","2026-07-28","--timeout","60000","--output-dir",reports.toString());
            Path log=OUT.resolve("modern-"+scenario+".log");var builder=new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile());builder.environment().put("MCP_URL",backend);builder.environment().put("MCP_TOKEN",token);
            var process=builder.start();try{assertTrue(process.waitFor(70,TimeUnit.SECONDS),"Bounded applicable modern conformance scenario: "+scenario);}finally{if(process.isAlive()){process.destroyForcibly();process.waitFor();}}
            try(var paths=Files.walk(reports)){for(Path p:paths.filter(Files::isRegularFile).toList())Files.writeString(p,Files.readString(p).replace(token,"[REDACTED]"));}Files.writeString(log,Files.readString(log).replace(token,"[REDACTED]"));
            Files.writeString(OUT.resolve("modern-"+scenario+"-execution.json"),JSON.writeValueAsString(Map.of("suite","@modelcontextprotocol/conformance 0.2.0-alpha.12","specVersion","2026-07-28","exit",process.exitValue(),"command",command))+"\n");
        }
    }
    private static String beginSqlMetrics(Phase3b7FaultSource.Owner owner)throws Exception {
        String event="AcceptanceTiming_"+UUID.randomUUID().toString().replace("-","");
        try(var c=owner.open();var s=c.createStatement();var r=s.executeQuery("SELECT DB_ID(),OBJECT_ID('dbo.AiDataSearchCasesV1'),OBJECT_ID('dbo.AiDataGetCaseOverviewV1'),OBJECT_ID('dbo.AiDataAuthorizeCaseHandoffV1')")) {
            assertTrue(r.next());int db=r.getInt(1),search=r.getInt(2),overview=r.getInt(3),handoff=r.getInt(4);
            try(var ddl=c.createStatement()){ddl.execute("CREATE EVENT SESSION ["+event+"] ON SERVER ADD EVENT sqlserver.sp_statement_completed(ACTION(sqlserver.session_id) WHERE ([sqlserver].[database_id]="+db+" AND ([object_id]="+search+" OR [object_id]="+overview+" OR [object_id]="+handoff+"))) ADD TARGET package0.ring_buffer WITH(MAX_MEMORY=2048 KB,EVENT_RETENTION_MODE=NO_EVENT_LOSS); ALTER EVENT SESSION ["+event+"] ON SERVER STATE=START");}
        }
        return event;
    }
    private static void finishSqlMetrics(Phase3b7FaultSource.Owner owner,String event,String token)throws Exception {
        var factory=javax.xml.parsers.DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        try(var c=owner.open()) {
            String xml;try(var s=c.createStatement();var r=s.executeQuery("SELECT CONVERT(nvarchar(max),t.target_data) FROM sys.dm_xe_sessions s JOIN sys.dm_xe_session_targets t ON t.event_session_address=s.address WHERE s.name='"+event+"'")){assertTrue(r.next());xml=r.getString(1);}
            var document=factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            var started=new HashMap<String,java.time.Instant>();var rounds=new HashMap<String,Integer>();var samples=new LinkedHashMap<String,List<Double>>();
            var events=document.getElementsByTagName("event");
            for(int n=0;n<events.getLength();n++) {
                var e=(org.w3c.dom.Element)events.item(n);var values=new HashMap<String,String>();
                for(var child=e.getFirstChild();child!=null;child=child.getNextSibling())if(child instanceof org.w3c.dom.Element element)values.put(element.getAttribute("name"),element.getTextContent());
                String statement=values.getOrDefault("statement","").strip(),object=values.getOrDefault("object_id",""),key=values.getOrDefault("session_id","")+":"+object;
                java.time.Instant at=java.time.Instant.parse(e.getAttribute("timestamp"));
                if(statement.matches("(?is)^BEGIN\\s+TRANSACTION.*"))started.put(key,at);
                if(statement.matches("(?is)^COMMIT\\s+TRANSACTION.*")&&started.containsKey(key)) {
                    int round=rounds.merge(key,1,Integer::sum);double millis=java.time.Duration.between(started.remove(key),at).toNanos()/1e6;
                    String name;try(var p=c.prepareStatement("SELECT OBJECT_NAME(?)")){p.setInt(1,Integer.parseInt(object));try(var r=p.executeQuery()){r.next();name=r.getString(1);}}
                    String phase=name.contains("Handoff")?"handoff-transaction":round%2==1?"quota-admission-transaction":"protected-read-audit-transaction";
                    samples.computeIfAbsent(phase,k->new ArrayList<>()).add(millis);
                }
            }
            Files.writeString(OUT.resolve("sql-transaction-timings.json"),JSON.writeValueAsString(Map.of("unit","milliseconds","scope","actual SQL BEGIN/COMMIT intervals; static statement completion only, no RPC parameters/sql_text action","samples",samples))+"\n");
            for(String required:List.of("quota-admission-transaction","protected-read-audit-transaction","handoff-transaction"))assertTrue(samples.getOrDefault(required,List.of()).size()>=5,"Five real SQL transaction intervals required: "+required);
            int plans=0;
            try(var s=c.createStatement();var rows=s.executeQuery("SELECT OBJECT_NAME(t.objectid) Module,p.query_plan FROM sys.dm_exec_cached_plans cp CROSS APPLY sys.dm_exec_sql_text(cp.plan_handle) t CROSS APPLY sys.dm_exec_query_plan(cp.plan_handle) p WHERE t.dbid=DB_ID() AND t.objectid IN(OBJECT_ID('dbo.AiDataSearchCasesV1'),OBJECT_ID('dbo.AiDataGetCaseOverviewV1'))")) {
                while(rows.next()) {
                    String plan=rows.getString(2);if(plan==null)continue;var doc=factory.newDocumentBuilder().parse(new ByteArrayInputStream(plan.getBytes(StandardCharsets.UTF_8)));
                    var nodes=doc.getElementsByTagName("*");for(int n=0;n<nodes.getLength();n++) {
                        var node=(org.w3c.dom.Element)nodes.item(n);for(String attribute:List.of("ParameterCompiledValue","ParameterRuntimeValue","StatementText","ConstValue"))node.removeAttribute(attribute);
                    }
                    var output=new StringWriter();javax.xml.transform.TransformerFactory.newInstance().newTransformer().transform(new javax.xml.transform.dom.DOMSource(doc),new javax.xml.transform.stream.StreamResult(output));
                    String safe=output.toString();assertFalse(safe.contains(token));byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII));
                    try{assertFalse(safe.toLowerCase(Locale.ROOT).contains(HexFormat.of().formatHex(digest)));}finally{Arrays.fill(digest,(byte)0);}
                    Files.writeString(OUT.resolve(rows.getString(1)+"-plan.xml"),safe);plans++;
                }
            }
            assertTrue(plans>=2,"Actual protected search and Overview plans must be available");
        } finally {try(var c=owner.open();var s=c.createStatement()){s.execute("ALTER EVENT SESSION ["+event+"] ON SERVER STATE=STOP; DROP EVENT SESSION ["+event+"] ON SERVER");}}
    }
    private static Map<String,Object> params(){return new LinkedHashMap<>(Map.of("_meta",Map.of("io.modelcontextprotocol/protocolVersion","2026-07-28","io.modelcontextprotocol/clientCapabilities",Map.of())));}
    private static String body(String method,Map<String,Object> parameters)throws Exception{return JSON.writeValueAsString(Map.of("jsonrpc","2.0","id","acceptance","method",method,"params",parameters));}
    private static void performance(String url,String token)throws Exception {
        var samples=new LinkedHashMap<String,List<Double>>();
        try(var client=HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(5)).build()) {
            for(String operation:List.of("ACCESS-and-discovery","tools-list","search-including-quota-audit-handoff","overview-including-quota-audit-handoff")) {
                var values=new ArrayList<Double>();
                for(int n=0;n<5;n++) {
                    String method=operation.equals("ACCESS-and-discovery")?"server/discover":operation.equals("tools-list")?"tools/list":"tools/call";
                    var p=params();
                    if(method.equals("tools/call")){boolean overview=operation.startsWith("overview");p.put("name",overview?"get_case_overview":"search_cases");p.put("arguments",overview?Map.of("caseId",100):Map.of("query","MCP literal","page",0));}
                    var builder=HttpRequest.newBuilder(URI.create(url)).timeout(java.time.Duration.ofSeconds(10)).header("Authorization","Bearer "+token).header("Content-Type","application/json").header("Accept","application/json").header("MCP-Protocol-Version","2026-07-28").header("Mcp-Method",method);
                    if(method.equals("tools/call"))builder.header("Mcp-Name",(String)p.get("name"));
                    long start=System.nanoTime();var response=client.send(builder.POST(HttpRequest.BodyPublishers.ofString(body(method,p))).build(),HttpResponse.BodyHandlers.ofString());values.add((System.nanoTime()-start)/1e6);
                    assertEquals(200,response.statusCode(),operation);var result=JSON.readTree(response.body());assertFalse(result.has("error"),operation);assertFalse(result.path("result").path("isError").asBoolean(),operation);
                }
                samples.put(operation,values);
            }
        }
        Files.writeString(OUT.resolve("finished-performance.json"),JSON.writeValueAsString(Map.of("unit","milliseconds","samplesPerOperation",5,"scope","actual confined two-tool HTTP server; no SLA","samples",samples))+"\n");
    }
    private static void conformance(String url,String token)throws Exception {
        Path reports=OUT.resolve("finished-conformance-active");Files.createDirectories(reports);
        var command=List.of("node","--import",ROOT.resolve("build/mcp-transport/conformance-auth.mjs").toString(),ROOT.resolve("build/mcp-transport/node_modules/@modelcontextprotocol/conformance/dist/index.js").toString(),"server","--url",url,"--suite","active","--spec-version","2026-07-28","--timeout","60000","--output-dir",reports.toString());
        var builder=new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(OUT.resolve("finished-conformance-active.log").toFile());builder.environment().put("MCP_URL",url);builder.environment().put("MCP_TOKEN",token);
        var process=builder.start();try {assertTrue(process.waitFor(140,TimeUnit.SECONDS),"Current applicable-profile conformance suite exceeded bounded execution");}
        finally {if(process.isAlive()){process.destroyForcibly();process.waitFor();}}
        // The suite records raw failures for unavailable diagnostic tools. Preserve and classify externally.
        try(var paths=Files.walk(reports)){for(Path p:paths.filter(Files::isRegularFile).toList()){String text=Files.readString(p);Files.writeString(p,text.replace(token,"[REDACTED]"));}}
        Path log=OUT.resolve("finished-conformance-active.log");Files.writeString(log,Files.readString(log).replace(token,"[REDACTED]"));
        Files.writeString(OUT.resolve("finished-conformance-active-execution.json"),JSON.writeValueAsString(Map.of("suite","@modelcontextprotocol/conformance 0.2.0-alpha.12","selection","active","specVersion","2026-07-28","exit",process.exitValue(),"diagnosticToolsAdded",false,"command",command))+"\n");
    }
    private static int port()throws IOException {try(var socket=new ServerSocket(0,1,InetAddress.getLoopbackAddress())){return socket.getLocalPort();}}
    public static void tls(String backend,String token,Path temp)throws Exception {
        Path binary=OUT.resolve("caddy");assertTrue(Files.isExecutable(binary),"Task-owned Caddy binary required for real proxy acceptance");
        Path key=temp.resolve("proxy.key"),cert=temp.resolve("proxy.crt"),config=temp.resolve("Caddyfile");
        var openssl=new ProcessBuilder("openssl","req","-x509","-newkey","rsa:2048","-nodes","-keyout",key.toString(),"-out",cert.toString(),"-days","1","-subj","/CN="+HOST,"-addext","subjectAltName=DNS:"+HOST+",IP:127.0.0.1").redirectErrorStream(true).redirectOutput(temp.resolve("certificate.log").toFile()).start();
        assertTrue(openssl.waitFor(15,TimeUnit.SECONDS));assertEquals(0,openssl.exitValue());
        int tls=port(),http=port();assertNotEquals(tls,http);
        Files.writeString(config,"{\n admin off\n auto_https off\n persist_config off\n}\nhttps://:"+tls+" {\n bind 127.0.0.1\n tls "+cert+" "+key+"\n reverse_proxy "+backend.substring(0,backend.lastIndexOf('/'))+"\n}\nhttp://:"+http+" {\n bind 127.0.0.1\n redir https://"+HOST+"{uri} 308\n}\n");
        var builder=new ProcessBuilder(binary.toString(),"run","--adapter","caddyfile","--config",config.toString()).redirectErrorStream(true).redirectOutput(temp.resolve("proxy.log").toFile());
        builder.environment().put("XDG_DATA_HOME",temp.resolve("data").toString());builder.environment().put("XDG_CONFIG_HOME",temp.resolve("config").toString());
        var process=builder.start();
        try {
            boolean ready=false;long until=System.nanoTime()+10_000_000_000L;
            while(System.nanoTime()<until&&process.isAlive())try(var socket=new Socket()){socket.connect(new InetSocketAddress("127.0.0.1",tls),100);ready=true;break;}catch(IOException wait){Thread.sleep(50);}
            assertTrue(ready,"Caddy TLS listener must start");
            var store=KeyStore.getInstance(KeyStore.getDefaultType());store.load(null,null);try(var in=Files.newInputStream(cert)){store.setCertificateEntry("local",CertificateFactory.getInstance("X.509").generateCertificate(in));}
            var trust=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());trust.init(store);var ssl=SSLContext.getInstance("TLS");ssl.init(null,trust.getTrustManagers(),null);
            var results=new LinkedHashMap<String,Integer>();
            try(var wrong=(SSLSocket)ssl.getSocketFactory().createSocket(new Socket("127.0.0.1",tls),"wrong.example",tls,true)) {
                wrong.setSoTimeout(5000);var parameters=wrong.getSSLParameters();parameters.setEndpointIdentificationAlgorithm("HTTPS");wrong.setSSLParameters(parameters);
                assertThrows(javax.net.ssl.SSLHandshakeException.class,wrong::startHandshake);results.put("wrong-hostname-refused",0);
            }
            try(var untrusted=(SSLSocket)SSLContext.getDefault().getSocketFactory().createSocket(new Socket("127.0.0.1",tls),HOST,tls,true)) {
                untrusted.setSoTimeout(5000);var parameters=untrusted.getSSLParameters();parameters.setEndpointIdentificationAlgorithm("HTTPS");untrusted.setSSLParameters(parameters);
                assertThrows(javax.net.ssl.SSLHandshakeException.class,untrusted::startHandshake);results.put("untrusted-certificate-refused",0);
            }
            for(var item:List.of(Map.entry("canonical-origin",Map.of("Host",HOST)),Map.entry("hostile-host",Map.of("Host","evil.invalid")),Map.entry("forwarded",Map.of("Host",HOST,"Forwarded","host=evil.invalid;proto=http")),Map.entry("x-forwarded",Map.of("Host",HOST,"X-Forwarded-Host","evil.invalid","X-Forwarded-Proto","http","X-Forwarded-For","203.0.113.9")))) {
                String response=request(ssl,tls,item.getValue(),null);assertTrue(response.startsWith("HTTP/1.1 401"),item.getKey());assertTrue(response.contains("https://"+HOST+"/.well-known/oauth-protected-resource/mcp"));assertFalse(response.contains("https://evil.invalid"));results.put(item.getKey(),401);
            }
            String allowed=request(ssl,tls,Map.of("Host",HOST,"Origin","https://"+HOST),token);assertTrue(allowed.startsWith("HTTP/1.1 200"));results.put("exact-origin",200);
            var overview=params();overview.put("name","get_case_overview");overview.put("arguments",Map.of("caseId",100));
            String protectedResponse=request(ssl,tls,Map.of("Host",HOST,"Origin","https://"+HOST),token,"tools/call",overview);
            assertTrue(protectedResponse.startsWith("HTTP/1.1 200"));var protectedResult=JSON.readTree(protectedResponse.substring(protectedResponse.indexOf("\r\n\r\n")+4)).path("result");assertFalse(protectedResult.path("isError").asBoolean());assertEquals(100,protectedResult.path("structuredContent").path("case").path("caseId").asInt());results.put("protected-overview-through-tls",200);
            String missing=request(ssl,tls,Map.of("Host",HOST),token);assertTrue(missing.startsWith("HTTP/1.1 200"));results.put("missing-origin-nonbrowser",200);
            for(String origin:List.of("https://evil.invalid","https://"+HOST+"/","https://"+HOST+".evil.invalid","http://"+HOST,"null")) {
                String hostile=request(ssl,tls,Map.of("Host",HOST,"Origin",origin),token);assertTrue(hostile.startsWith("HTTP/1.1 403"),"Origin must match exactly");results.put("denied-origin-"+results.size(),403);
            }
            for(String path:Boolean.getBoolean("shale.acceptance.production")?List.of(com.shale.server.oauth.OAuthMetadataContract.PROTECTED_RESOURCE_PATH,com.shale.server.oauth.OAuthMetadataContract.AUTHORIZATION_SERVER_PATH):List.of(com.shale.server.oauth.OAuthMetadataContract.PROTECTED_RESOURCE_PATH)) {
                String metadata=get(ssl,tls,path);assertTrue(metadata.startsWith("HTTP/1.1 200"),path+" "+metadata.lines().findFirst().orElse("missing status"));
                var claims=JSON.readTree(metadata.substring(metadata.indexOf("\r\n\r\n")+4));
                if(path.contains("protected-resource")){assertEquals("https://"+HOST+"/mcp",claims.path("resource").asText());assertEquals("https://"+HOST+"/oauth/mcp",claims.path("authorization_servers").get(0).asText());}
                else assertEquals("https://"+HOST+"/oauth/mcp",claims.path("issuer").asText());
                assertFalse(metadata.contains("evil.invalid"));results.put(path,200);
            }
            try(var socket=new Socket("127.0.0.1",http)){socket.setSoTimeout(5000);socket.getOutputStream().write("GET /mcp HTTP/1.1\r\nHost: evil.invalid\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));String response=new String(socket.getInputStream().readAllBytes(),StandardCharsets.UTF_8);assertTrue(response.startsWith("HTTP/1.1 308"));assertTrue(response.contains("https://"+HOST+"/mcp"));results.put("http-enforcement",308);}
            for(String path:List.of("/mcp","/oauth/mcp/token","/oauth/mcp/authorize"))try(var socket=new Socket("127.0.0.1",http)) {
                socket.setSoTimeout(5000);
                socket.getOutputStream().write(("POST "+path+" HTTP/1.1\r\nHost: evil.invalid\r\nAuthorization: Bearer "+token+"\r\nContent-Type: application/json\r\nContent-Length: 2\r\nConnection: close\r\n\r\n{}").getBytes(StandardCharsets.US_ASCII));
                String response=new String(socket.getInputStream().readAllBytes(),StandardCharsets.UTF_8);assertTrue(response.startsWith("HTTP/1.1 308"));assertTrue(response.contains("https://"+HOST+path));assertFalse(response.contains("access_token"));assertFalse(response.contains("structuredContent"));results.put("http-credentials-"+path,308);
            }
            Files.writeString(OUT.resolve("tls-proxy.json"),JSON.writeValueAsString(Map.of("proxy","Caddy 2.11.7","scope","owned isolated real TLS reverse proxy; not deployed ingress","certificateValidated",true,"hostnameValidated",true,"cases",results))+"\n");
        } finally {process.destroy();if(!process.waitFor(5,TimeUnit.SECONDS)){process.destroyForcibly();process.waitFor();}Files.deleteIfExists(key);}
    }
    private static String get(SSLContext ssl,int port,String path)throws Exception {
        try(var socket=(SSLSocket)ssl.getSocketFactory().createSocket(new Socket("127.0.0.1",port),HOST,port,true)) {
            socket.setSoTimeout(10000);var parameters=socket.getSSLParameters();parameters.setEndpointIdentificationAlgorithm("HTTPS");socket.setSSLParameters(parameters);socket.startHandshake();
            socket.getOutputStream().write(("GET "+path+" HTTP/1.1\r\nHost: evil.invalid\r\nForwarded: host=evil.invalid;proto=http\r\nX-Forwarded-Host: evil.invalid\r\nX-Forwarded-Proto: http\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            return new String(socket.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
        }
    }
    private static String request(SSLContext ssl,int port,Map<String,String> headers,String token)throws Exception {
        return request(ssl,port,headers,token,"server/discover",params());
    }
    private static String request(SSLContext ssl,int port,Map<String,String> headers,String token,String method,Map<String,Object> params)throws Exception {
        try(var socket=(SSLSocket)ssl.getSocketFactory().createSocket(new Socket("127.0.0.1",port),HOST,port,true)) {
            socket.setSoTimeout(10000);var parameters=socket.getSSLParameters();parameters.setEndpointIdentificationAlgorithm("HTTPS");socket.setSSLParameters(parameters);socket.startHandshake();
            String body=body(method,params);var request=new StringBuilder("POST /mcp HTTP/1.1\r\nConnection: close\r\nContent-Type: application/json\r\nAccept: application/json\r\nMCP-Protocol-Version: 2026-07-28\r\nMcp-Method: "+method+"\r\n");
            if(params.containsKey("name"))request.append("Mcp-Name: ").append(params.get("name")).append("\r\n");
            for(var h:headers.entrySet())request.append(h.getKey()).append(": ").append(h.getValue()).append("\r\n");if(token!=null)request.append("Authorization: Bearer ").append(token).append("\r\n");
            request.append("Content-Length: ").append(body.getBytes(StandardCharsets.UTF_8).length).append("\r\n\r\n").append(body);socket.getOutputStream().write(request.toString().getBytes(StandardCharsets.UTF_8));
            return new String(socket.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
        }
    }
}
