package com.shale.server.oauth.cimd;

import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Function;

/** Real trusted HTTPS; controlled DNS and a test-only dial route to an ephemeral loopback listener. */
final class CimdFixture implements AutoCloseable {
    static SSLContext serverContext, clientContext;
    static synchronized void initialize() throws Exception {
        if (serverContext!=null) return;
        Path dir=Files.createTempDirectory("cimd-tls-");
        Path store=dir.resolve("fixture.p12");
        try {
            var process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","keytool").toString(),
                    "-genkeypair","-alias","fixture","-keyalg","RSA","-keysize","2048","-validity","2",
                    "-dname","CN=metadata.public.org","-ext","SAN=dns:metadata.public.org,dns:keys.public.org,dns:callback.public.org",
                    "-storetype","PKCS12","-keystore",store.toString(),"-storepass","synthetic-password","-noprompt")
                    .redirectErrorStream(true).redirectOutput(dir.resolve("keytool.log").toFile()).start();
            if (!process.waitFor(20,TimeUnit.SECONDS) || process.exitValue()!=0) throw new IOException("Fixture keytool failed");
            var ks=KeyStore.getInstance("PKCS12");
            try (var in=Files.newInputStream(store)) { ks.load(in,"synthetic-password".toCharArray()); }
            var km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()); km.init(ks,"synthetic-password".toCharArray());
            var trust=KeyStore.getInstance("PKCS12"); trust.load(null,null); trust.setCertificateEntry("fixture",ks.getCertificate("fixture"));
            var tm=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()); tm.init(trust);
            serverContext=SSLContext.getInstance("TLS"); serverContext.init(km.getKeyManagers(),null,null);
            clientContext=SSLContext.getInstance("TLS"); clientContext.init(null,tm.getTrustManagers(),null);
        } finally {
            try (var paths=Files.walk(dir)) { for (Path p:paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p); }
        }
    }
    record Reply(String headers,byte[] body,boolean chunked) {
        static Reply json(String body,String cache) {
            return new Reply("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nCache-Control: "+cache+"\r\nETag: \"v1\"\r\n",body.getBytes(StandardCharsets.UTF_8),false);
        }
        static Reply status(int code,String extra) { return new Reply("HTTP/1.1 "+code+" Result\r\n"+extra,new byte[0],false); }
    }
    final SSLServerSocket server;
    final ExecutorService executor=Executors.newCachedThreadPool();
    final List<String> requests=new CopyOnWriteArrayList<>();
    final List<InetAddress> dialed=new CopyOnWriteArrayList<>();
    final AtomicInteger dnsCalls=new AtomicInteger();
    final AtomicReference<InetAddress[]> addresses=new AtomicReference<>(new InetAddress[]{InetAddress.getByName("93.184.215.14")});
    volatile int bodyByteDelayMillis;
    volatile Function<String,Reply> handler=request -> Reply.json(publicMetadata("https://metadata.public.org/client.json"),"max-age=60");
    volatile CimdHttps.Resolver dns=host -> { dnsCalls.incrementAndGet(); return addresses.get(); };
    CimdFixture() throws Exception {
        initialize();
        server=(SSLServerSocket)serverContext.getServerSocketFactory().createServerSocket(0,50,InetAddress.getLoopbackAddress());
        executor.submit(() -> {
            while (!server.isClosed()) {
                try { var socket=(SSLSocket)server.accept(); executor.submit(() -> serve(socket)); }
                catch (IOException ignored) { break; }
            }
        });
    }
    private void serve(SSLSocket socket) {
        try (socket) {
            socket.setSoTimeout(7000);
            var in=socket.getInputStream(); var out=new ByteArrayOutputStream();
            while (!out.toString(StandardCharsets.US_ASCII).endsWith("\r\n\r\n")) { int c=in.read(); if(c<0 || out.size()>8192)return; out.write(c); }
            String request=out.toString(StandardCharsets.US_ASCII); requests.add(request);
            Reply reply=handler.apply(request);
            String headers=reply.headers()+(reply.chunked()?"Transfer-Encoding: chunked\r\n":"Content-Length: "+reply.body().length+"\r\n")+"\r\n";
            var output=socket.getOutputStream(); output.write(headers.getBytes(StandardCharsets.US_ASCII));
            if (reply.chunked()) output.write((Integer.toHexString(reply.body().length)+"\r\n").getBytes(StandardCharsets.US_ASCII));
            if (bodyByteDelayMillis==0) output.write(reply.body());
            else for (byte b:reply.body()) { output.write(b); output.flush(); Thread.sleep(bodyByteDelayMillis); }
            if (reply.chunked()) output.write("\r\n0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            output.flush();
        } catch (Exception ignored) { /* Adversarial disconnects and rejected TLS are expected. */ }
    }
    CimdHttps transport() { return transport(clientContext.getSocketFactory()); }
    CimdHttps transport(SSLSocketFactory tls) {
        return new CimdHttps(host -> dns.resolve(host),tls,(ip,port,timeout) -> {
            dialed.add(ip); var socket=new Socket(Proxy.NO_PROXY);
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(),server.getLocalPort()),timeout); return socket;
        });
    }
    static String publicMetadata(String id) {
        return "{\"client_id\":\""+id+"\",\"client_name\":\"Synthetic\",\"redirect_uris\":[\"https://callback.public.org/callback\"]}";
    }
    static final class MutableClock extends Clock {
        private final AtomicReference<Instant> now=new AtomicReference<>(Instant.now());
        void advance(long seconds) { now.updateAndGet(i->i.plusSeconds(seconds)); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    }
    @Override public void close() throws Exception { server.close(); executor.shutdownNow(); executor.awaitTermination(10,TimeUnit.SECONDS); }
}
