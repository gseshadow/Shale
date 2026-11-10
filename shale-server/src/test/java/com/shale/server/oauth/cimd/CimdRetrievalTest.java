package com.shale.server.oauth.cimd;

import com.shale.server.oauth.CimdClientIdentifier;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import org.junit.jupiter.api.*;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.SSLContext;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.io.*;
import java.util.zip.GZIPOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class CimdRetrievalTest {
    static final CimdClientIdentifier ID=CimdMetadataTest.ID;
    static final String ISSUER="https://issuer.public.org/oauth/mcp";
    static final String BODY=CimdFixture.publicMetadata(ID.value());
    CimdFixture fixture;
    CimdFixture.MutableClock clock;
    CimdMetadataService service;
    @BeforeEach void setup() throws Exception {
        fixture=new CimdFixture(); clock=new CimdFixture.MutableClock();
        service=new CimdMetadataService(ISSUER,clock,fixture.transport());
    }
    @AfterEach void teardown() throws Exception { service.close(); fixture.close(); }

    @Test void trustedHttpsProducesImmutableIssuerScopedSnapshotWithoutAmbientCredentials() {
        var old=ProxySelector.getDefault();
        ProxySelector.setDefault(new ProxySelector() {
            @Override public List<Proxy> select(URI uri) { throw new AssertionError("Ambient proxy resolution must never run"); }
            @Override public void connectFailed(URI uri,SocketAddress address,IOException failure) {}
        });
        try {
            var snapshot=service.retrieve(ID);
            assertEquals(ID,snapshot.identifier()); assertEquals(ISSUER,snapshot.issuer());
            assertEquals("none",snapshot.selectMethod(false));
            assertThrows(IllegalArgumentException.class,()->snapshot.selectMethod(true),"Confidential selection must not downgrade");
            assertThrows(UnsupportedOperationException.class,()->snapshot.redirects().add("https://evil.public.org/cb"));
            assertSame(snapshot,service.retrieve(ID)); assertEquals(1,fixture.requests.size());
            String request=fixture.requests.getFirst();
            assertTrue(request.contains("Host: metadata.public.org\r\n"));
            for (String sensitive:List.of("Authorization:","Cookie:","Proxy-Authorization:","tenant","Shale")) assertFalse(request.contains(sensitive),sensitive);
            assertFalse(snapshot.toString().contains(ID.value()));
        } finally { ProxySelector.setDefault(old); }
    }

    @Test void expiryRevalidatesWithEtagAndNeverServesStaleAfterDnsBecomesPrivate() throws Exception {
        var first=service.retrieve(ID); clock.advance(61);
        fixture.handler=r -> CimdFixture.Reply.status(304,"Cache-Control: max-age=120\r\nETag: \"v1\"\r\n");
        var revalidated=service.retrieve(ID);
        assertEquals(first.revision(),revalidated.revision());
        assertTrue(revalidated.expiresAt().isAfter(first.expiresAt()));
        assertTrue(fixture.requests.getLast().contains("If-None-Match: \"v1\""));
        int connections=fixture.dialed.size(); clock.advance(121);
        fixture.addresses.set(new InetAddress[]{InetAddress.getByName("10.0.0.1")});
        assertThrows(IllegalArgumentException.class,()->service.retrieve(ID));
        assertEquals(connections,fixture.dialed.size(),"Rebinding must be rejected before a connection");
        assertThrows(IllegalArgumentException.class,()->service.retrieve(ID),"No stale-on-error or negative-cache bypass");
    }

    @Test void eachConnectionUsesOnePinnedPublicResolutionAndMixedAnswersNeverDial() throws Exception {
        var first=InetAddress.getByName("93.184.215.14"); var next=InetAddress.getByName("1.1.1.1");
        fixture.addresses.set(new InetAddress[]{first}); service.retrieve(ID);
        assertEquals(List.of(first),fixture.dialed);
        fixture.addresses.set(new InetAddress[]{next}); clock.advance(61); service.retrieve(ID);
        assertEquals(List.of(first,next),fixture.dialed);
        clock.advance(61); fixture.addresses.set(new InetAddress[]{first,InetAddress.getByName("::1")});
        assertThrows(IllegalArgumentException.class,()->service.retrieve(ID)); assertEquals(2,fixture.dialed.size());
    }

    @Test void callbackPrivateResolutionRejectsPublicationWithoutFetchingCallback() throws Exception {
        fixture.dns=host -> new InetAddress[]{InetAddress.getByName(host.startsWith("callback")?"192.168.1.1":"93.184.215.14")};
        assertThrows(IllegalArgumentException.class,()->service.retrieve(ID));
        assertEquals(1,fixture.requests.size()); assertTrue(fixture.requests.getFirst().startsWith("GET /client.json"));
    }

    @Test void redirectTlsTrustAndHostnameFailuresNeverProduceMetadata() throws Exception {
        for (int code:List.of(301,302,303,307,308)) {
            fixture.handler=r -> CimdFixture.Reply.status(code,"Location: https://169.254.169.254/latest/meta-data\r\n");
            try (var s=new CimdMetadataService(ISSUER,clock,fixture.transport())) { assertThrows(IllegalArgumentException.class,()->s.retrieve(ID)); }
        }
        assertEquals(5,fixture.requests.size(),"Redirect targets must never be followed");
        fixture.handler=r -> CimdFixture.Reply.json(BODY,"max-age=60");
        try (var untrusted=new CimdMetadataService(ISSUER,clock,fixture.transport((SSLSocketFactory)SSLSocketFactory.getDefault()))) {
            assertThrows(IllegalArgumentException.class,()->untrusted.retrieve(ID));
        }
        try (var mismatch=new CimdMetadataService(ISSUER,clock,fixture.transport())) {
            assertThrows(IllegalArgumentException.class,()->mismatch.retrieve(new CimdClientIdentifier("https://wrong.public.org/client.json")));
        }
        assertEquals(5,fixture.requests.size(),"Untrusted and wrong-host TLS must fail before HTTP");
    }

    @Test void streamedChunkedGzipAndMalformedResponsesRespectWireAndExpandedBounds() throws Exception {
        var valid=CimdFixture.Reply.json(BODY,"no-store");
        fixture.handler=r -> new CimdFixture.Reply(valid.headers(),valid.body(),true);
        assertEquals(ID,service.retrieve(ID).identifier());
        var gzip=new ByteArrayOutputStream();
        try (var zip=new GZIPOutputStream(gzip)) { zip.write(BODY.getBytes(StandardCharsets.UTF_8)); }
        fixture.handler=r -> new CimdFixture.Reply(valid.headers()+"Content-Encoding: gzip\r\n",gzip.toByteArray(),false);
        assertEquals(ID,service.retrieve(ID).identifier());
        var bomb=new ByteArrayOutputStream();
        try (var zip=new GZIPOutputStream(bomb)) { zip.write(" ".repeat(32769).getBytes(StandardCharsets.UTF_8)); }
        List<CimdFixture.Reply> bad=List.of(
                new CimdFixture.Reply(valid.headers(),new byte[32769],false),
                new CimdFixture.Reply(valid.headers(),new byte[32769],true),
                new CimdFixture.Reply(valid.headers()+"Content-Encoding: gzip\r\n",bomb.toByteArray(),false),
                new CimdFixture.Reply(valid.headers()+"Content-Length: 1\r\n",valid.body(),false),
                new CimdFixture.Reply(valid.headers()+"Content-Encoding: br\r\n",valid.body(),false),
                new CimdFixture.Reply(valid.headers().replace("application/json","text/html"),valid.body(),false),
                new CimdFixture.Reply(valid.headers()+"X-Large: "+"a".repeat(8192)+"\r\n",valid.body(),false),
                CimdFixture.Reply.json(BODY.replace("Synthetic","evil\\u202ename"),"no-store"),
                CimdFixture.Reply.json(BODY.replace(ID.value(),"https://evil.public.org/client.json"),"no-store"),
                CimdFixture.Reply.status(304,"Cache-Control: max-age=60\r\n"));
        for (var reply:bad) {
            fixture.handler=r -> reply;
            try (var s=new CimdMetadataService(ISSUER,clock,fixture.transport())) {
                var error=assertThrows(IllegalArgumentException.class,()->s.retrieve(ID));
                assertEquals("CIMD retrieval rejected.",error.getMessage()); assertNull(error.getCause());
            }
        }
    }

    @Test void noStoreNoCacheAgeFreshnessCapAndNegativeLookupsHaveControlledLifetimes() throws Exception {
        fixture.handler=r -> CimdFixture.Reply.json(BODY,"no-store, max-age=600");
        service.retrieve(ID); service.retrieve(ID); assertEquals(0,service.cachedEntries()); assertEquals(2,fixture.requests.size());
        fixture.handler=r -> CimdFixture.Reply.json(BODY,"no-cache, max-age=600");
        service.retrieve(ID); service.retrieve(ID); assertEquals(4,fixture.requests.size());
        fixture.handler=r -> new CimdFixture.Reply(CimdFixture.Reply.json(BODY,"max-age=100").headers()+"Age: 99\r\n",BODY.getBytes(StandardCharsets.UTF_8),false);
        var aged=service.retrieve(ID); assertTrue(Duration.between(clock.instant(),aged.expiresAt()).getSeconds()<=1);
        clock.advance(2);
        fixture.handler=r -> CimdFixture.Reply.json(BODY,"max-age=999999");
        var capped=service.retrieve(ID); assertTrue(Duration.between(clock.instant(),capped.expiresAt()).getSeconds()<=300);
        clock.advance(301);
        AtomicInteger lookups=new AtomicInteger();
        fixture.dns=host -> { lookups.incrementAndGet(); throw new UnknownHostException("untrusted details"); };
        assertThrows(IllegalArgumentException.class,()->service.retrieve(ID));
        assertThrows(IllegalArgumentException.class,()->service.retrieve(ID)); assertEquals(1,lookups.get());
        clock.advance(6); assertThrows(IllegalArgumentException.class,()->service.retrieve(ID)); assertEquals(2,lookups.get());
    }

    @Test void remoteJwksIndependentlyRevalidatesAndKeyRotationChangesRevision() throws Exception {
        var key1=new RSAKeyGenerator(2048).keyID("one").algorithm(com.nimbusds.jose.JWSAlgorithm.RS256).generate().toPublicJWK();
        var key2=new RSAKeyGenerator(2048).keyID("two").algorithm(com.nimbusds.jose.JWSAlgorithm.RS256).generate().toPublicJWK();
        String metadata=CimdMetadataTest.metadata(",\"token_endpoint_auth_method\":\"private_key_jwt\",\"jwks_uri\":\"https://keys.public.org/keys.json\"");
        var keyBody=new AtomicReference<>("{\"keys\":["+key1.toJSONString()+"]}");
        fixture.handler=r -> CimdFixture.Reply.json(r.startsWith("GET /keys.json")?keyBody.get():metadata,r.startsWith("GET /keys.json")?"max-age=2":"max-age=200");
        var first=service.retrieve(ID); assertEquals("private_key_jwt",first.selectMethod(true));
        assertThrows(IllegalArgumentException.class,()->first.selectMethod(false));
        assertEquals(2,fixture.requests.size()); assertTrue(Duration.between(clock.instant(),first.expiresAt()).getSeconds()<=2);
        keyBody.set("{\"keys\":["+key2.toJSONString()+"]}"); clock.advance(3);
        var second=service.retrieve(ID); assertNotEquals(first.revision(),second.revision()); assertEquals("two",second.keys().getFirst().kid());
        clock.advance(3);
        fixture.dns=host -> new InetAddress[]{InetAddress.getByName(host.startsWith("keys")?"169.254.169.254":"93.184.215.14")};
        assertThrows(IllegalArgumentException.class,()->service.retrieve(ID),"JWKS gets its own SSRF policy and cannot fall back to prior keys");
    }

    @Test void concurrentSameIdentityCoalescesAndCacheEvictsWithinBound() throws Exception {
        var entered=new CountDownLatch(1); var release=new CountDownLatch(1);
        fixture.handler=r -> {
            entered.countDown();
            try { if(!release.await(3,TimeUnit.SECONDS))throw new IllegalStateException(); } catch(InterruptedException e){throw new IllegalStateException(e);}
            return CimdFixture.Reply.json(BODY,"max-age=60");
        };
        try (var callers=Executors.newFixedThreadPool(8)) {
            var results=new ArrayList<Future<CimdMetadataService.Snapshot>>();
            for(int i=0;i<8;i++) results.add(callers.submit(()->service.retrieve(ID)));
            assertTrue(entered.await(2,TimeUnit.SECONDS)); release.countDown();
            var snapshot=results.getFirst().get(5,TimeUnit.SECONDS);
            for(var result:results) assertSame(snapshot,result.get(5,TimeUnit.SECONDS));
            assertEquals(1,fixture.requests.size());
        }
        // Fast failed lookups exercise the shared positive/negative LRU cap without network load.
        fixture.dns=host -> { throw new UnknownHostException(); };
        for(int i=0;i<260;i++) {
            var id=new CimdClientIdentifier("https://host"+i+".public.org/client.json");
            assertThrows(IllegalArgumentException.class,()->service.retrieve(id));
        }
        assertEquals(256,service.cachedEntries());
    }

    @Test void slowDnsIsBoundedByTotalDeadlineAndSixteenWorkerAdmission() throws Exception {
        var entered=new CountDownLatch(16); var release=new CountDownLatch(1);
        fixture.dns=host -> {
            entered.countDown();
            try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(); }
            return fixture.addresses.get();
        };
        try (var callers=Executors.newFixedThreadPool(16)) {
            var results=new ArrayList<Future<?>>();
            for(int i=0;i<16;i++) {
                var id=new CimdClientIdentifier("https://host"+i+".public.org/client.json");
                results.add(callers.submit(()->assertThrows(IllegalArgumentException.class,()->service.retrieve(id))));
            }
            assertTrue(entered.await(2,TimeUnit.SECONDS));
            assertEquals(16,service.activeRequests());
            long before=System.nanoTime();
            assertThrows(IllegalArgumentException.class,()->service.retrieve(ID));
            assertTrue(System.nanoTime()-before<TimeUnit.SECONDS.toNanos(1));
            for(var result:results) result.get(7,TimeUnit.SECONDS);
            assertTrue(fixture.dialed.isEmpty(),"Blocked DNS must not create a connection");
        } finally { release.countDown(); }
    }

    @Test void slowHttpsResponseCannotExtendTotalDeadlineAndClosedServiceRejectsWork() throws Exception {
        var release=new CountDownLatch(1);
        fixture.handler=r -> {
            try { release.await(7,TimeUnit.SECONDS); } catch(InterruptedException ignored) { Thread.currentThread().interrupt(); }
            return CimdFixture.Reply.json(BODY,"max-age=60");
        };
        long start=System.nanoTime();
        try { assertThrows(IllegalArgumentException.class,()->service.retrieve(ID)); }
        finally { release.countDown(); }
        assertTrue(System.nanoTime()-start<TimeUnit.SECONDS.toNanos(6));
        service.close(); assertThrows(IllegalArgumentException.class,()->service.retrieve(ID));
    }
    @Test void slowDripCannotKeepSocketOpenBeyondAbsoluteBudget() {
        fixture.bodyByteDelayMillis=15;
        fixture.handler=r -> CimdFixture.Reply.json(BODY.replace("Synthetic","x".repeat(1000)),"no-store");
        long before=System.nanoTime();
        assertThrows(IllegalArgumentException.class,()->service.retrieve(ID));
        assertTrue(System.nanoTime()-before<TimeUnit.SECONDS.toNanos(6),"Continuous bytes must not renew the total timeout");
    }

    @Test void cacheCannotExtendAuthorityThroughWallClockRollback() {
        var ticks=new AtomicLong();
        try (var s=new CimdMetadataService(ISSUER,clock,fixture.transport(),ticks::get)) {
            s.retrieve(ID); clock.advance(-3600); ticks.addAndGet(TimeUnit.SECONDS.toNanos(61));
            s.retrieve(ID);
            assertEquals(2,fixture.requests.size(),"Monotonic expiry must override a wall-clock rollback");
        }
    }

    @Test void noStoreRevalidationErasesRepresentationAndCannotAcceptLaterUnsolicited304() {
        service.retrieve(ID); clock.advance(61);
        fixture.handler=r -> CimdFixture.Reply.status(304,"Cache-Control: no-store\r\n");
        service.retrieve(ID); assertEquals(0,service.cachedEntries());
        assertThrows(IllegalArgumentException.class,()->service.retrieve(ID));
        assertFalse(fixture.requests.getLast().contains("If-None-Match:"));
    }

    @Test void encodedIdentifiersRemainExactOnWireAndInPublishedIdentity() {
        var encoded=new CimdClientIdentifier("https://metadata.public.org/%63lient.json");
        fixture.handler=r -> CimdFixture.Reply.json(CimdFixture.publicMetadata(encoded.value()),"max-age=60");
        assertEquals(encoded,service.retrieve(encoded).identifier());
        assertTrue(fixture.requests.getLast().startsWith("GET /%63lient.json HTTP/1.1"));
    }

    @Test void productionTlsDoesNotInheritReplacedApplicationDefaultContext() throws Exception {
        var original=SSLContext.getDefault();
        try {
            SSLContext.setDefault(CimdFixture.clientContext);
            try (var isolated=new CimdMetadataService(ISSUER,clock,fixture.transport(CimdHttps.platformTls()))) {
                assertThrows(IllegalArgumentException.class,()->isolated.retrieve(ID),
                        "An application's replaced SSLContext must not replace the metadata trust boundary");
            }
            assertTrue(fixture.requests.isEmpty());
        } finally { SSLContext.setDefault(original); }
    }

}
