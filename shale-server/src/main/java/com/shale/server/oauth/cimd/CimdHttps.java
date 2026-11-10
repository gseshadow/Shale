package com.shale.server.oauth.cimd;

import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.concurrent.*;

/** Minimal dedicated HTTP/1.1 GET transport. Direct pinned socket, no ambient HTTP client state. */
final class CimdHttps {
    static final int LIMIT=32768;
    private static final ScheduledThreadPoolExecutor DEADLINES=new ScheduledThreadPoolExecutor(1,r -> {
        var thread=new Thread(r,"cimd-socket-deadline"); thread.setDaemon(true); return thread;
    });
    static { DEADLINES.setRemoveOnCancelPolicy(true); }
    interface Resolver { InetAddress[] resolve(String host) throws IOException; }
    record Response(int status, Map<String,String> headers, byte[] body) {}
    private final Resolver resolver;
    private final SSLSocketFactory tls;
    interface Connector { Socket connect(InetAddress address, int port, int timeout) throws IOException; }
    private final Connector connector;

    CimdHttps() { this(InetAddress::getAllByName, (SSLSocketFactory) SSLSocketFactory.getDefault()); }
    // Package-private fixture seam: production always uses platform DNS and verified default TLS.
    CimdHttps(Resolver resolver, SSLSocketFactory tls) {
        this(resolver,tls,(address,port,timeout) -> {
            Socket socket=new Socket(Proxy.NO_PROXY);
            try { socket.connect(new InetSocketAddress(address,port),timeout); return socket; }
            catch (IOException failure) { socket.close(); throw failure; }
        });
    }
    CimdHttps(Resolver resolver, SSLSocketFactory tls, Connector connector) {
        this.resolver=resolver; this.tls=tls; this.connector=connector;
    }
    void validateCallback(URI uri) throws IOException {
        CimdDestination.approved(resolver.resolve(uri.getHost()));
    }

    Response get(URI uri, String etag, long deadline) throws IOException {
        InetAddress pinned=CimdDestination.approved(resolver.resolve(uri.getHost()));
        return exchange(uri, pinned, etag, deadline);
    }

    private Response exchange(URI uri, InetAddress pinned, String etag, long deadline) throws IOException {
        int port=uri.getPort()<0?443:uri.getPort();
        try (Socket tcp=connector.connect(pinned, port, Math.min(2000, remaining(deadline)))) {
            // Closing the underlying socket enforces the absolute budget even during a slow-drip
            // TLS handshake/write; per-read SO_TIMEOUT alone cannot establish a total deadline.
            var timeout=DEADLINES.schedule(() -> { try { tcp.close(); } catch (IOException ignored) {} },
                    remaining(deadline),TimeUnit.MILLISECONDS);
            try (SSLSocket socket=(SSLSocket)tls.createSocket(tcp, uri.getHost(), port, true)) {
                var parameters=socket.getSSLParameters();
                parameters.setEndpointIdentificationAlgorithm("HTTPS");
                parameters.setServerNames(List.of(new SNIHostName(uri.getHost())));
                parameters.setApplicationProtocols(new String[]{"http/1.1"});
                socket.setSSLParameters(parameters);
                socket.setSoTimeout(remaining(deadline));
                socket.startHandshake();
                String path=uri.getRawPath().isEmpty()?"/":uri.getRawPath();
                String request="GET "+path+" HTTP/1.1\r\nHost: "+uri.getRawAuthority()+
                        "\r\nAccept: application/json\r\nAccept-Encoding: gzip\r\nConnection: close\r\n"+
                        (etag==null?"":"If-None-Match: "+etag+"\r\n")+"\r\n";
                socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
                InputStream in=new FilterInputStream(socket.getInputStream()) {
                    @Override public int read() throws IOException { socket.setSoTimeout(remaining(deadline)); return super.read(); }
                    @Override public int read(byte[] b,int o,int n) throws IOException { socket.setSoTimeout(remaining(deadline)); return this.in.read(b,o,n); }
                };
                int[] headerBytes={0};
                String status=line(in,headerBytes);
                if (!status.matches("HTTP/1\\.[01] (200|304)( .*)?")) throw CimdDestination.invalid();
                Map<String,String> headers=new HashMap<>();
                for (String h; !(h=line(in,headerBytes)).isEmpty();) {
                    int colon=h.indexOf(':');
                    if (colon<=0 || !h.substring(0,colon).matches("[A-Za-z0-9-]+")) throw CimdDestination.invalid();
                    String key=h.substring(0,colon).toLowerCase(Locale.ROOT), value=h.substring(colon+1).trim();
                    if (headers.putIfAbsent(key,value)!=null) throw CimdDestination.invalid();
                }
                if (status.startsWith("HTTP/1.1 304") || status.startsWith("HTTP/1.0 304")) return new Response(304,Map.copyOf(headers),new byte[0]);
                String type=headers.getOrDefault("content-type","");
                if (!type.matches("(?i)application/(json|jwk-set\\+json)(?:\\s*;\\s*charset=utf-8)?")) throw CimdDestination.invalid();
                String length=headers.get("content-length"), transfer=headers.get("transfer-encoding");
                if (length!=null && (transfer!=null || !length.matches("[0-9]{1,8}") || Long.parseLong(length)>LIMIT)) throw CimdDestination.invalid();
                byte[] encoded;
                if (transfer!=null) {
                    if (!"chunked".equalsIgnoreCase(transfer)) throw CimdDestination.invalid();
                    var out=new ByteArrayOutputStream();
                    while (true) {
                        String chunk=line(in,headerBytes);
                        if (!chunk.matches("[0-9a-fA-F]{1,8}")) throw CimdDestination.invalid();
                        long size=Long.parseLong(chunk,16);
                        if (size>LIMIT-out.size()) throw CimdDestination.invalid();
                        if (size==0) { if (!line(in,headerBytes).isEmpty()) throw CimdDestination.invalid(); break; }
                        byte[] bytes=in.readNBytes((int)size);
                        if (bytes.length!=size || !line(in,headerBytes).isEmpty()) throw CimdDestination.invalid();
                        out.write(bytes);
                    }
                    encoded=out.toByteArray();
                } else {
                    encoded=in.readNBytes(length==null?LIMIT+1:Integer.parseInt(length));
                    if (encoded.length>LIMIT || length!=null && encoded.length!=Integer.parseInt(length)) throw CimdDestination.invalid();
                }
                String encoding=headers.getOrDefault("content-encoding","identity");
                byte[] body;
                if ("gzip".equalsIgnoreCase(encoding)) {
                    try (var gzip=new GZIPInputStream(new ByteArrayInputStream(encoded))) { body=gzip.readNBytes(LIMIT+1); }
                } else if ("identity".equalsIgnoreCase(encoding)) body=encoded;
                else throw CimdDestination.invalid();
                if (body.length>LIMIT) throw CimdDestination.invalid();
                remaining(deadline);
                return new Response(200,Map.copyOf(headers),body);
            } finally { timeout.cancel(false); }
        }
    }
    private static String line(InputStream in,int[] count) throws IOException {
        var out=new ByteArrayOutputStream();
        for (int c; (c=in.read())!=-1;) {
            if (++count[0]>8192 || c>127 || c<32 && c!='\r' && c!='\n' && c!='\t') throw CimdDestination.invalid();
            if (c=='\r') { if (in.read()!='\n') throw CimdDestination.invalid(); return out.toString(StandardCharsets.US_ASCII); }
            if (c=='\n') throw CimdDestination.invalid();
            out.write(c);
        }
        throw new EOFException();
    }
    static int remaining(long deadline) throws SocketTimeoutException {
        long millis=(deadline-System.nanoTime())/1_000_000;
        if (millis<=0 || Thread.currentThread().isInterrupted()) throw new SocketTimeoutException();
        return (int)Math.min(millis,5000);
    }
}
