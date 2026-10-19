import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.google.gson.*;
import com.shale.desktop.net.*;

/** Exercises the real desktop negotiate/WebSocket clients against the explicit local relay. */
public final class RelayChecks {
    static final class Receiver implements LiveBusClient.Handler{
        final CountDownLatch joined=new CountDownLatch(1),delivered=new CountDownLatch(1);final int tenant;final AtomicInteger wrong=new AtomicInteger();
        Receiver(int tenant){this.tenant=tenant;}
        @Override public void onMessage(String text){
            var o=JsonParser.parseString(text).getAsJsonObject();
            if(o.has("type")&&o.get("type").getAsString().equals("ack"))joined.countDown();
            if(o.has("data")){if(o.getAsJsonObject("data").get("shaleClientId").getAsInt()!=tenant)wrong.incrementAndGet();delivered.countDown();}
        }
    }
    public static void main(String[] args)throws Exception{
        var root=Path.of(args[0]);var p=new Properties();try(var in=Files.newInputStream(root.resolve("sandbox.properties"))){p.load(in);}
        var keys=java.security.KeyStore.getInstance("PKCS12");char[] password=Files.readString(root.resolve("relay-key-password")).strip().toCharArray();
        try(var in=Files.newInputStream(root.resolve("relay.p12"))){keys.load(in,password);}Arrays.fill(password,'\0');
        var trust=javax.net.ssl.TrustManagerFactory.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());trust.init(keys);
        var tls=javax.net.ssl.SSLContext.getInstance("TLS");tls.init(null,trust.getTrustManagers(),null);javax.net.ssl.SSLContext.setDefault(tls);
        var a=new Receiver(11);var b=new Receiver(22);var one=new LiveBusClient(a);var two=new LiveBusClient(b);
        String origin="http://127.0.0.1:"+p.getProperty("livePort");
        try{
            one.connect(new NegotiateClient(origin+"/negotiate").negotiateForTenant(11,1)).get(8,TimeUnit.SECONDS);one.joinGroup("client-11",1).get(5,TimeUnit.SECONDS);
            two.connect(new NegotiateClient(origin+"/negotiate").negotiateForTenant(22,3)).get(8,TimeUnit.SECONDS);two.joinGroup("client-22",1).get(5,TimeUnit.SECONDS);
            Sandbox.require(a.joined.await(5,TimeUnit.SECONDS)&&b.joined.await(5,TimeUnit.SECONDS),"Local group joins not acknowledged");
            var http=HttpClient.newHttpClient();for(int tenant:List.of(22,11)){
                var request=HttpRequest.newBuilder(URI.create(origin+"/publish")).timeout(Duration.ofSeconds(5)).POST(HttpRequest.BodyPublishers.ofString("{\"type\":\"EntityUpdated\",\"entityType\":\"User\",\"entityId\":1,\"shaleClientId\":"+tenant+"}")).build();Sandbox.require(http.send(request,HttpResponse.BodyHandlers.discarding()).statusCode()==200,"Local relay publish failed");
            }
            Sandbox.require(a.delivered.await(5,TimeUnit.SECONDS)&&b.delivered.await(5,TimeUnit.SECONDS)&&a.wrong.get()==0&&b.wrong.get()==0,"Local group routing failed");
            System.out.println("PASS actual desktop negotiate/TLS WebSocket/group clients + synthetic local routing; Azure/Functions remain UNVERIFIED");
        }finally{one.closeSilently();two.closeSilently();}
    }
}
