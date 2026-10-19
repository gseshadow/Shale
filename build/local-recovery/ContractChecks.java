import java.net.URLPermission;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** No SQL mutation; the private export is supplied at runtime, never a committed fixture. */
public final class ContractChecks {
    static void rejects(Runnable test,String message){boolean refused=false;try{test.run();}catch(SecurityException|IllegalArgumentException expected){refused=true;}Sandbox.require(refused,message);}
    public static void main(String[] args)throws Exception{
        var s=new Sandbox(Path.of(args[0]),Path.of(args[1]));var a=s.adapt();
        String combined=String.join("\r\nGO\r\n",a.batches());
        Sandbox.require(!combined.contains("CREATE DATABASE")&&!combined.contains("ALTER DATABASE")&&!combined.contains("TO [Brian]")&&!combined.contains("CREATE USER")&&!combined.contains("[Prime]"),"Export production database/principals escaped adaptation");
        Sandbox.require(a.batches().stream().filter(b->Sandbox.clean(b).startsWith("CREATE TABLE ")).count()==96,"Every application table must be retained");
        for(String name:Sandbox.LEGACY){
            String canonical=Files.readString(s.state.resolve("reviewed/shale-server/src/test/resources/auth/"+name+".sql")).replace("\r\n","\n").replace("\n","\r\n");
            Sandbox.require(a.batches().contains(canonical),"Canonical legacy body not preserved: "+name);
        }
        String original=Files.readString(s.state.resolve("private-input.txt"),StandardCharsets.UTF_16);
        int protectedDefinitions=0;
        for(String b:Sandbox.batches(original)){
            String code=Sandbox.clean(b).replaceAll("\\s+"," ");
            if(code.matches("(?is)CREATE (FUNCTION|TRIGGER|SECURITY POLICY|VIEW).*")){
                Sandbox.require(a.batches().contains(b),"RLS/function/trigger/view definition changed");protectedDefinitions++;
            }
        }
        Sandbox.require(protectedDefinitions==16,"Expected 6 functions, 8 triggers, view and policy");
        Sandbox.require(a.review().stream().filter(x->x.get("action").toString().startsWith("canonical")).count()==3,"All three captured procedures must use canonical CRLF");
        var guard=new LocalBootstrap.LoopbackGuard(s.state,Set.of(14333,18080));
        guard.checkConnect("127.0.0.1",14333);guard.checkConnect("localhost",18080);guard.checkWrite(s.state.resolve("tmp/test").toString());
        rejects(()->guard.checkConnect("shale.database.windows.net",1433),"Production DNS target must fail before network");
        rejects(()->guard.checkConnect("203.0.113.1",14333),"Remote IP must fail");
        rejects(()->guard.checkConnect("127.0.0.1",1433),"Unexpected local port must fail");
        rejects(()->guard.checkExec("updater.exe"),"Updater process must fail");
        rejects(()->guard.checkWrite(s.state.resolve("../outside").toString()),"Outside profile writes must fail");
        rejects(()->guard.checkPermission(new RuntimePermission("setSecurityManager")),"Guard replacement must fail");
        rejects(()->guard.checkPermission(new URLPermission("https://example.com/a","GET:"),null),"HttpClient context permission must also reject outbound URLs");
        LocalSqlDriver.install(s.url(s.p.getProperty("database")));
        var d=java.sql.DriverManager.getDriver(s.url(s.p.getProperty("database")));
        Sandbox.require(d instanceof LocalSqlDriver,"Historical Hikari pool must select the sandbox TLS adapter");
        Sandbox.require(!d.acceptsURL("jdbc:sqlserver://shale.database.windows.net;databaseName=Shale"),"TLS adapter must reject every other database URL");
        var update=new com.shale.desktop.update.DesktopUiUpdateLauncher();Sandbox.require(!update.checkForUpdate().updateAvailable()&&!update.automaticWindowsCapabilityAvailable(),"Sandbox updater must be disabled");
        boolean disabled=false;try{update.launchUpdater();}catch(UnsupportedOperationException e){disabled=true;}Sandbox.require(disabled,"Manual updater must be disabled");
        LocalRelay.clients.add(new LocalRelay.Client(new java.net.Socket(),new java.io.OutputStream(){@Override public void write(int b)throws java.io.IOException{throw new java.io.IOException("synthetic disconnected peer");}},Set.of("client-11")));
        LocalRelay.publish("client-11","{}");Sandbox.require(LocalRelay.clients.isEmpty(),"A disconnected relay subscriber must not break later publishes");
        Sandbox.selftest();System.out.println("PASS adaptation preservation, canonical captures, local identity exclusions, network/profile/process guard, TLS scope and updater substitute");
    }
}
