package com.shale.server.oauth.cimd;
import java.net.*;import java.io.*;import java.nio.charset.StandardCharsets;import java.time.Clock;
public final class LiveMetadata {
 static InetAddress[] resolve(String host)throws IOException {
  if(!host.equals("nousresearch.github.io")&&!host.equals(URI.create(System.getenv("HTTPS_PROXY")).getHost()))throw new IOException("Fixture destination unavailable");
  var path=java.nio.file.Path.of("/workspace/Shale/work/pr-c2-harness",host+".dns");
  if(java.nio.file.Files.getLastModifiedTime(path).toMillis()<System.currentTimeMillis()-30000)throw new IOException("Fresh platform DNS evidence required");
  var lines=java.nio.file.Files.readAllLines(path);if(lines.isEmpty()||lines.size()>16)throw new IOException("Bounded platform DNS required");
  InetAddress[] addresses=new InetAddress[lines.size()];for(int i=0;i<lines.size();i++)addresses[i]=InetAddress.getByName(lines.get(i));return addresses;
 }
 public static CimdMetadataService service(String issuer) {
  URI proxy=URI.create(System.getenv("HTTPS_PROXY"));
  var transport=new CimdHttps(LiveMetadata::resolve,CimdHttps.platformTls(),(address,port,timeout)->{
   Socket s=new Socket(Proxy.NO_PROXY);s.connect(new InetSocketAddress(resolve(proxy.getHost())[0],proxy.getPort()),timeout);s.setSoTimeout(timeout);
   String destination=address.getHostAddress();if(destination.contains(":"))destination="["+destination+"]";
   String target=destination+":"+port;
   s.getOutputStream().write(("CONNECT "+target+" HTTP/1.1\r\nHost: "+target+"\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
   var b=new ByteArrayOutputStream();int previous=0,count=0;
   while(b.size()<8192){int v=s.getInputStream().read();if(v<0)throw new IOException();b.write(v);if(v=='\n'&&previous=='\r')count++;else if(v!='\r')count=0;previous=v;if(count==2)break;}
   if(!b.toString(StandardCharsets.US_ASCII).startsWith("HTTP/1.1 200")){s.close();throw new IOException("Numeric CONNECT unavailable");}return s;
  });
  return new CimdMetadataService(issuer,Clock.systemUTC(),transport);
 }
}
