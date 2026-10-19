import com.google.gson.*;
import com.sun.net.httpserver.*;
import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;

/** Development substitute only: minimal loopback Web PubSub JSON group routing.
 * No Azure authentication, persistence, delivery guarantee, Functions or jobs. */
public final class LocalRelay {
    record Client(Socket socket,OutputStream output,Set<String> groups){
        synchronized void send(String json)throws IOException{byte[] b=json.getBytes(StandardCharsets.UTF_8);output.write(0x81);if(b.length<126)output.write(b.length);else{output.write(126);output.write(b.length>>>8);output.write(b.length);}output.write(b);output.flush();}
    }
    static final Set<Client> clients=ConcurrentHashMap.newKeySet();
    public static void run(Path root,Properties p)throws Exception{
        char[] password=Files.readString(root.resolve("relay-key-password")).strip().toCharArray();
        var key=KeyStore.getInstance("PKCS12");try(var in=Files.newInputStream(root.resolve("relay.p12"))){key.load(in,password);}
        var km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());km.init(key,password);Arrays.fill(password,'\0');var tls=SSLContext.getInstance("TLS");tls.init(km.getKeyManagers(),null,null);
        var sockets=(SSLServerSocket)tls.getServerSocketFactory().createServerSocket();sockets.bind(new InetSocketAddress("127.0.0.1",Integer.parseInt(p.getProperty("wsPort"))));
        var http=HttpServer.create(new InetSocketAddress("127.0.0.1",Integer.parseInt(p.getProperty("livePort"))),0);
        http.createContext("/negotiate",exchange->reply(exchange,200,"{\"url\":\"wss://127.0.0.1:"+p.getProperty("wsPort")+"/local\"}"));
        http.createContext("/publish",exchange->{
            try{var data=JsonParser.parseString(new String(exchange.getRequestBody().readNBytes(65537),StandardCharsets.UTF_8)).getAsJsonObject();String group=data.has("shaleClientId")?"client-"+data.get("shaleClientId").getAsInt():null;
                for(Client c:clients)if(group==null||c.groups.contains(group))c.send("{\"type\":\"message\",\"from\":\"group\",\"group\":\""+(group==null?"local":group)+"\",\"dataType\":\"json\",\"data\":"+data+"}");reply(exchange,200,"{}");
            }catch(Exception failure){reply(exchange,400,"{}");}
        });
        http.setExecutor(Executors.newVirtualThreadPerTaskExecutor());http.start();System.out.println("LOCAL LIVE SUBSTITUTE listening; no Azure/Function/integration acceptance implied");
        while(true){Socket s=sockets.accept();Thread.startVirtualThread(()->serve(s));}
    }
    static void reply(HttpExchange e,int status,String body)throws IOException{byte[] b=body.getBytes(StandardCharsets.UTF_8);e.getResponseHeaders().set("Content-Type","application/json");e.sendResponseHeaders(status,b.length);try(var out=e.getResponseBody()){out.write(b);}e.close();}
    static void serve(Socket socket){Client client=null;try(socket){socket.setSoTimeout(30000);InputStream in=socket.getInputStream();OutputStream out=socket.getOutputStream();ByteArrayOutputStream head=new ByteArrayOutputStream();int previous=0,crlf=0;while(head.size()<8192){int b=in.read();if(b<0)return;head.write(b);if(b==10&&previous==13)crlf++;else if(b!=13)crlf=0;previous=b;if(crlf==2)break;}
        String headers=head.toString(StandardCharsets.US_ASCII);String key=Arrays.stream(headers.split("\r\n")).filter(x->x.toLowerCase(Locale.ROOT).startsWith("sec-websocket-key:")).map(x->x.substring(x.indexOf(':')+1).trim()).findFirst().orElseThrow();
        String accept=Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((key+"258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.US_ASCII)));
        out.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: "+accept+"\r\nSec-WebSocket-Protocol: json.webpubsub.azure.v1\r\n\r\n").getBytes(StandardCharsets.US_ASCII));out.flush();socket.setSoTimeout(0);
        client=new Client(socket,out,ConcurrentHashMap.newKeySet());clients.add(client);client.send("{\"type\":\"system\",\"event\":\"connected\",\"connectionId\":\"local-synthetic\"}");
        while(true){int first=in.read(),second=in.read();if(first<0||second<0||(first&15)==8)return;int n=second&127;if(n==126)n=(in.read()<<8)|in.read();if(n>65535||n<0||n==127||(second&128)==0)return;byte[] mask=in.readNBytes(4),body=in.readNBytes(n);if(mask.length!=4||body.length!=n)return;for(int i=0;i<n;i++)body[i]^=mask[i%4];if((first&15)!=1)continue;
            var message=JsonParser.parseString(new String(body,StandardCharsets.UTF_8)).getAsJsonObject();String type=message.get("type").getAsString();if(type.equals("joinGroup"))client.groups.add(message.get("group").getAsString());if(type.equals("leaveGroup"))client.groups.remove(message.get("group").getAsString());if(message.has("ackId"))client.send("{\"type\":\"ack\",\"ackId\":"+message.get("ackId")+",\"success\":true}");
        }
    }catch(Exception ignored){/* loopback synthetic client disconnected; never log payloads */}finally{if(client!=null)clients.remove(client);}}
}
