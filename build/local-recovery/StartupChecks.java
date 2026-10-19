import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.nio.channels.Selector;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

/** Offline subprocess checks: no SQL, provisioned state, credentials or application launch. */
@SuppressWarnings("removal")
public final class StartupChecks {
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static void refuses(Runnable action,String message){
        try{action.run();}catch(SecurityException expected){return;}
        throw new AssertionError(message);
    }
    static final class RecordingGuard extends LocalBootstrap.LoopbackGuard {
        final List<Path> deleted=new ArrayList<>();
        RecordingGuard(Path root,Set<Integer> ports){super(root,ports);}
        @Override public void checkDelete(String file){
            deleted.add(Path.of(file).toAbsolutePath().normalize());
            super.checkDelete(file);
        }
    }
    public static void main(String[] args)throws Exception {
        require(Runtime.version().feature()==21,"Run selector startup checks on JDK 21");
        if(args.length==0){
            Path evidence=Files.createDirectory(Path.of(System.getProperty("java.io.tmpdir")).resolve("ss-"+UUID.randomUUID().toString().substring(0,8))).toRealPath();
            Path root=Files.createDirectory(evidence.resolve("state"));
            Path outside=Files.createDirectory(evidence.resolve("outside"));
            System.out.println("Startup check evidence: "+evidence);
            if(System.getProperty("os.name").startsWith("Windows"))child(root,outside,true);
            child(root,outside,false);
            System.out.println("PASS startup checks on "+System.getProperty("os.name")+" / "+Runtime.version()
                    +"; native Windows coverage="+System.getProperty("os.name").startsWith("Windows"));
            return;
        }
        Path root=Path.of(args[1]).toRealPath();
        boolean legacy=args[0].equals("legacy");
        if(!legacy)StartupPaths.verify(root);
        int port;try(var socket=new java.net.ServerSocket(0,0,InetAddress.getByName("127.0.0.1"))){port=socket.getLocalPort();}
        Path sentinel=root.getParent().resolve("outside/sentinel");
        Files.writeString(sentinel,"retain");
        var guard=new RecordingGuard(root,Set.of(port));
        System.setSecurityManager(guard);
        if(legacy){
            try(var selector=Selector.open()){throw new AssertionError("Expected Windows selector cleanup refusal");}
            catch(SecurityException expected){
                Path rejected=guard.deleted.getLast();
                require(rejected.getParent().equals(sentinel.getParent())&&rejected.getFileName().toString().matches("socket_[0-9]+"),
                        "Windows rejected a different path; inspect native JDK before updating tooling");
                System.out.println("Observed native Windows rejected AF_UNIX socket delete: "+rejected);
            }
            return;
        }
        for(int i=0;i<3;i++)try(var selector=Selector.open()){
            selector.wakeup();selector.selectNow();
            System.out.println("Selector: "+selector.getClass().getName());
        }
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",port),0);
        server.createContext("/startup",exchange->{
            byte[] body="local".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            exchange.sendResponseHeaders(200,body.length);
            try(var out=exchange.getResponseBody()){out.write(body);}finally{exchange.close();}
        });
        try{
            server.start();
            // HttpClient's selector is also used by desktop negotiate/WebSocket and API live publishing.
            try(var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()){
                var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/startup")).timeout(Duration.ofSeconds(5)).build();
                var response=client.send(request,HttpResponse.BodyHandlers.ofString());
                require(response.statusCode()==200&&response.body().equals("local"),"Guarded HttpServer/HttpClient request failed");
            }
        }finally{server.stop(0);}
        if(System.getProperty("os.name").startsWith("Windows")){
            require(guard.deleted.stream().anyMatch(p->p.getParent().equals(root.resolve("tmp"))&&p.getFileName().toString().matches("socket_[0-9]+")),
                    "Windows AF_UNIX cleanup was not exercised; fallback is not acceptance of this regression");
        }
        refuses(()->guard.checkWrite(sentinel.toString()),"Outside/profile write permitted");
        refuses(()->guard.checkDelete(sentinel.toString()),"Outside/profile deletion permitted");
        refuses(()->guard.checkConnect("shale.database.windows.net",1433),"Production SQL endpoint permitted");
        refuses(()->guard.checkConnect("example.com",443),"Remote endpoint permitted");
        refuses(()->guard.checkConnect("127.0.0.1",1),"Unexpected local port permitted");
        refuses(()->guard.checkListen(1),"Unexpected listener permitted");
        refuses(()->guard.checkExec("updater.exe"),"Process launch permitted");
        refuses(()->guard.checkPermission(new URLPermission("https://example.com/","GET:"),null),"HTTP context bypass permitted");
        refuses(()->guard.checkPermission(new RuntimePermission("setSecurityManager")),"Guard replacement permitted");
        require(Files.readString(sentinel).equals("retain"),"Outside sentinel changed");
        require(System.getSecurityManager()==guard,"Startup lost guard");
        System.out.println("PASS guarded selector/wakeup, HttpServer, HttpClient and isolation denials");
    }
    static void child(Path root,Path outside,boolean legacy)throws Exception{
        var command=new ArrayList<String>();
        command.add(Path.of(System.getProperty("java.home"),"bin","java").toString());
        command.add("-Djava.security.manager=allow");
        if(legacy)command.add("-Djava.io.tmpdir="+root.resolve("tmp"));
        else command.addAll(StartupPaths.vmArgs(root));
        command.addAll(List.of("-cp",System.getProperty("java.class.path"),StartupChecks.class.getName(),legacy?"legacy":"fixed",root.toString()));
        var child=new ProcessBuilder(command).inheritIO();
        child.environment().put("TEMP",outside.toString());child.environment().put("TMP",outside.toString());
        // Deliberately retain the outside TEMP in the fixed test: explicit socket property must win.
        require(child.start().waitFor()==0,"Startup subprocess failed; preserve evidence above");
    }
}
