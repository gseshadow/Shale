package com.shale.server.oauth.cimd;

import com.shale.server.oauth.CimdClientIdentifier;
import java.net.URI;
import java.security.interfaces.RSAPublicKey;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.LongSupplier;

/**
 * Unwired, issuer-scoped publication retrieval. No Spring bean, tenant, SQL, approval, consent,
 * assertion verifier or token authority. Owners must close this bounded process-local resource.
 */
public final class CimdMetadataService implements AutoCloseable {
    public record SigningKey(String kid, RSAPublicKey rsa) {
        @Override public String toString() { return "SigningKey[redacted]"; }
    }
    public record Snapshot(String issuer, CimdClientIdentifier identifier, String name, List<String> redirects,
                           Set<String> methods, Set<String> grants, List<SigningKey> keys,
                           String revision, Instant expiresAt) {
        public Snapshot { redirects=List.copyOf(redirects); methods=Set.copyOf(methods); grants=Set.copyOf(grants); keys=List.copyOf(keys); }
        /** Explicit policy selection: never fall back from a confidential requirement. */
        public String selectMethod(boolean requirePrivateKey) {
            String selected=requirePrivateKey?"private_key_jwt":"none";
            if (!methods.contains(selected)) throw CimdDestination.invalid();
            return selected;
        }
        @Override public String toString() { return "CimdSnapshot[redacted]"; }
    }
    private record Document(byte[] bytes, Map<String,String> headers, Instant expires, boolean noStore, long validUntilNanos) {}
    private record Entry(Snapshot snapshot, Document metadata, Document keys, Instant retryAfter, long validUntilNanos) {}
    private static final int CAPACITY=256;
    private final String issuer;
    private final Clock clock;
    private final LongSupplier cacheTime;
    private final CimdHttps https;
    private final ThreadPoolExecutor workers=new ThreadPoolExecutor(0,16,30,TimeUnit.SECONDS,
            new SynchronousQueue<>(),r -> { var t=new Thread(r,"cimd-retrieval"); t.setDaemon(true); return t; },new ThreadPoolExecutor.AbortPolicy());
    private final LinkedHashMap<CimdClientIdentifier,Entry> cache=new LinkedHashMap<>(16,0.75f,true);
    private final Map<CimdClientIdentifier,CompletableFuture<Snapshot>> flights=new HashMap<>();
    private boolean closed;

    public CimdMetadataService(String issuer) { this(issuer,Clock.systemUTC(),new CimdHttps()); }
    CimdMetadataService(String issuer, Clock clock, CimdHttps https) {
        this(issuer,clock,https,System::nanoTime);
    }
    CimdMetadataService(String issuer, Clock clock, CimdHttps https, LongSupplier cacheTime) {
        // Issuer is an exact cache/credential binding, never an outbound destination.
        // Development issuers may use .test; every actual fetch still requires CimdDestination.
        new CimdClientIdentifier(issuer); this.issuer=issuer; this.clock=clock; this.https=https; this.cacheTime=cacheTime;
    }

    public Snapshot retrieve(CimdClientIdentifier id) {
        Objects.requireNonNull(id); CimdDestination.url(id.value());
        CompletableFuture<Snapshot> flight;
        synchronized (this) {
            if (closed) throw CimdDestination.invalid();
            Entry entry=cache.get(id);
            if (entry!=null && entry.retryAfter()!=null && clock.instant().isBefore(entry.retryAfter()) && cacheTime.getAsLong()-entry.validUntilNanos()<0) throw CimdDestination.invalid();
            if (entry!=null && entry.snapshot()!=null && clock.instant().isBefore(entry.snapshot().expiresAt()) && cacheTime.getAsLong()-entry.validUntilNanos()<0) return entry.snapshot();
            flight=flights.get(id);
            if (flight==null) {
                if (flights.size()>=16) throw CimdDestination.invalid();
                flight=new CompletableFuture<>(); flights.put(id,flight);
                var completion=flight;
                try { workers.execute(() -> load(id,entry,completion)); }
                catch (RejectedExecutionException failure) { flights.remove(id); throw CimdDestination.invalid(); }
            }
        }
        try { return flight.get(5,TimeUnit.SECONDS); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw CimdDestination.invalid(); }
        catch (ExecutionException | TimeoutException failure) { throw CimdDestination.invalid(); }
    }
    private void load(CimdClientIdentifier id,Entry previous,CompletableFuture<Snapshot> completion) {
        try {
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            Document metadata=document(CimdDestination.url(id.value()),previous==null?null:previous.metadata(),deadline);
            var parsed=CimdMetadata.parse(id,metadata.bytes());
            for (String callback:parsed.redirects()) {
                URI uri=URI.create(callback);
                if ("https".equals(uri.getScheme())) https.validateCallback(uri);
            }
            Document keys=null;
            List<SigningKey> signingKeys=parsed.keys();
            if (parsed.keysUrl()!=null) {
                Document oldKeys=previous!=null && previous.metadata()!=null &&
                        Objects.equals(CimdMetadata.parse(id,previous.metadata().bytes()).keysUrl(),parsed.keysUrl())?previous.keys():null;
                keys=document(CimdDestination.url(parsed.keysUrl()),oldKeys,deadline);
                signingKeys=CimdMetadata.keys(CimdMetadata.json(keys.bytes()));
            }
            Instant expiry=keys!=null && keys.expires().isBefore(metadata.expires())?keys.expires():metadata.expires();
            var snapshot=new Snapshot(issuer,id,parsed.name(),parsed.redirects(),parsed.methods(),parsed.grants(),signingKeys,
                    CimdMetadata.digest(metadata.bytes(),signingKeys),expiry);
            CimdHttps.remaining(deadline);
            synchronized (this) {
                if (!closed) {
                    if (metadata.noStore() || keys!=null && keys.noStore()) cache.remove(id);
                    else put(id,new Entry(snapshot,metadata,keys,null,
                            keys!=null && keys.validUntilNanos()-metadata.validUntilNanos()<0?
                                    keys.validUntilNanos():metadata.validUntilNanos()));
                }
                flights.remove(id,completion);
                completion.complete(snapshot);
            }
        } catch (Exception failure) {
            synchronized (this) {
                if (!closed) put(id,new Entry(null,null,null,clock.instant().plusSeconds(5),cacheTime.getAsLong()+TimeUnit.SECONDS.toNanos(5)));
                flights.remove(id,completion);
                completion.completeExceptionally(CimdDestination.invalid());
            }
        } finally { synchronized (this) { flights.remove(id,completion); } }
    }
    private Document document(URI uri,Document previous,long deadline) throws Exception {
        String etag=previous==null?null:previous.headers().get("etag");
        long started=System.nanoTime();
        var response=https.get(uri,etag,deadline);
        Map<String,String> headers=new HashMap<>();
        byte[] body=response.body();
        if (response.status()==304) {
            if (previous==null || etag==null) throw CimdDestination.invalid();
            headers.putAll(previous.headers());
            // Age/Date from a prior response must not make a new response artificially fresh.
            headers.remove("age"); headers.remove("date");
            body=previous.bytes();
        }
        headers.putAll(response.headers());
        String tag=headers.get("etag");
        if (tag!=null && (!tag.matches("(?:W/)?\"[\\x21\\x23-\\x7e]{0,250}\""))) throw CimdDestination.invalid();
        Instant now=clock.instant();
        long ttl=0, age=0;
        boolean noStore=false, noCache=false;
        var seen=new HashSet<String>();
        for (String directive:headers.getOrDefault("cache-control","").split(",")) {
            var parts=directive.trim().toLowerCase(Locale.ROOT).split("=",2);
            if (!seen.add(parts[0])) throw CimdDestination.invalid();
            if (parts[0].equals("no-store")) noStore=true;
            if (parts[0].equals("no-cache")) noCache=true;
            if (parts[0].equals("max-age")) {
                if (parts.length!=2 || !parts[1].matches("[0-9]{1,9}")) throw CimdDestination.invalid();
                ttl=Long.parseLong(parts[1]);
            }
        }
        if (!seen.contains("max-age") && headers.containsKey("expires"))
            ttl=Math.max(0,Duration.between(date(headers.getOrDefault("date",DateTimeFormatter.RFC_1123_DATE_TIME.format(now.atZone(ZoneOffset.UTC)))),date(headers.get("expires"))).getSeconds());
        if (headers.containsKey("age")) {
            if (!headers.get("age").matches("[0-9]{1,9}")) throw CimdDestination.invalid();
            age=Long.parseLong(headers.get("age"));
        }
        long elapsed=(System.nanoTime()-started+999_999_999L)/1_000_000_000L;
        age+=elapsed;
        if (headers.containsKey("date")) age=Math.max(age,Math.max(0,Duration.between(date(headers.get("date")),now).getSeconds()));
        ttl=noCache || noStore?0:Math.min(300,Math.max(0,ttl-age));
        return new Document(body,Map.copyOf(headers),now.plusSeconds(ttl),noStore,cacheTime.getAsLong()+TimeUnit.SECONDS.toNanos(ttl));
    }
    private static Instant date(String value) { return ZonedDateTime.parse(value,DateTimeFormatter.RFC_1123_DATE_TIME).toInstant(); }
    private void put(CimdClientIdentifier id,Entry entry) {
        cache.put(id,entry);
        while (cache.size()>CAPACITY) cache.remove(cache.keySet().iterator().next());
    }
    synchronized int cachedEntries() { return cache.size(); }
    synchronized int activeRequests() { return flights.size(); }
    @Override public synchronized void close() {
        closed=true; cache.clear();
        flights.values().forEach(f -> f.completeExceptionally(CimdDestination.invalid()));
        flights.clear(); workers.shutdownNow();
    }
}
