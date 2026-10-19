import java.net.*;
import java.nio.file.*;
import java.security.Permission;
import java.util.*;

/** Only the sandbox launches through this class. The production artifacts are unchanged. */
@SuppressWarnings("removal")
public final class LocalBootstrap {
    static final class LoopbackGuard extends SecurityManager {
        final Set<Integer> ports;final Path root;
        LoopbackGuard(Path root,Set<Integer> ports){this.root=root.toAbsolutePath().normalize();this.ports=Set.copyOf(ports);}
        static boolean local(String host){return Set.of("127.0.0.1","localhost","::1","0:0:0:0:0:0:0:1").contains(host);}
        @Override public void checkPermission(Permission permission){
            if(permission instanceof java.net.URLPermission){URI u=URI.create(permission.getName());checkConnect(u.getHost(),u.getPort()==-1?(u.getScheme().equals("https")?443:80):u.getPort());}
            if(permission instanceof RuntimePermission&&permission.getName().equals("setSecurityManager"))throw new SecurityException("Sandbox guard cannot be replaced");
        }
        @Override public void checkPermission(Permission permission,Object context){checkPermission(permission);}
        @Override public void checkConnect(String host,int port){if(!local(host)||(port!=-1&&!ports.contains(port)))throw new SecurityException("LOCAL sandbox blocks outbound network");}
        @Override public void checkConnect(String host,int port,Object context){checkConnect(host,port);}
        @Override public void checkListen(int port){if(port!=0&&!ports.contains(port))throw new SecurityException("LOCAL sandbox blocks unexpected listener");}
        @Override public void checkAccept(String host,int port){if(!local(host))throw new SecurityException("LOCAL sandbox blocks remote client");}
        @Override public void checkExec(String cmd){throw new SecurityException("LOCAL sandbox disables updater/external process launch");}
        @Override public void checkWrite(String file){if(!Path.of(file).toAbsolutePath().normalize().startsWith(root))throw new SecurityException("LOCAL sandbox blocks writes outside owned state");}
        @Override public void checkDelete(String file){checkWrite(file);}
        @Override public void checkMulticast(InetAddress address){throw new SecurityException("LOCAL sandbox blocks multicast");}
    }
    public static void main(String[] args)throws Exception{
        if(Runtime.version().feature()!=21||args.length!=2)throw new IllegalArgumentException("JDK 21 sandbox selector required");
        Path root=Path.of(args[1]).toRealPath();var p=new Properties();try(var in=Files.newInputStream(root.resolve("sandbox.properties"))){p.load(in);}
        Set<Integer> ports=new HashSet<>();for(String key:List.of("sqlPort","apiPort","livePort","wsPort"))ports.add(Integer.parseInt(p.getProperty(key)));
        System.setProperty("javax.net.ssl.trustStore",root.resolve("relay.p12").toString());
        System.setProperty("javax.net.ssl.trustStorePassword",Files.readString(root.resolve("relay-key-password")).strip());
        if(!args[0].equals("relay"))LocalSqlDriver.install(System.getenv("SHALE_APP_DB_URL"));
        System.setSecurityManager(new LoopbackGuard(root,ports));
        // Prove denial before loading the application, without making an external request.
        boolean denied=false;try{System.getSecurityManager().checkConnect("shale.database.windows.net",1433);}catch(SecurityException expected){denied=true;}if(!denied)throw new SecurityException("Missing isolation guard");
        denied=false;try{System.getSecurityManager().checkExec("updater");}catch(SecurityException expected){denied=true;}if(!denied)throw new SecurityException("Missing process guard");
        System.out.println("LOCAL SYNTHETIC SANDBOX; source="+p.getProperty("applicationRevision")+"; installed 1.0.136 UNVERIFIED; updates/external network/processes BLOCKED");
        switch(args[0]){
            case "api" -> Class.forName("com.shale.server.ShaleServerApplication").getMethod("main",String[].class).invoke(null,(Object)new String[]{"--spring.profiles.active=prod","--server.address=127.0.0.1","--server.port="+p.getProperty("apiPort"),"--spring.config.location=optional:classpath:/application.properties"});
            case "desktop" -> {
                System.setProperty("SHALE_SERVER_API_BASE_URL","http://127.0.0.1:"+p.getProperty("apiPort"));
                Class.forName("com.shale.desktop.MainApp").getMethod("main",String[].class).invoke(null,(Object)new String[0]);
            }
            case "relay" -> LocalRelay.run(root,p);
            default -> throw new IllegalArgumentException("Unknown sandbox child");
        }
    }
}
