package com.shale.data.security.ai;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AiInstallationReadinessTest {
    @Test void unverifiedInstallationCannotServeOrBeReopenedAfterRefusal() {
        var state=new AiInstallationReadiness(()->0,100);
        assertThrows(AiInstallationReadiness.Unavailable.class,state::requireReady);
        assertThrows(AiInstallationReadiness.Unavailable.class,state::verified);
    }
    @Test void successfulRefreshExtendsOnlyInstallationIntegrityLifetime() {
        var clock=new AtomicLong();var state=new AiInstallationReadiness(clock::get,100);
        state.verified();clock.set(90);state.requireReady();state.verified();
        clock.set(189);state.requireReady();clock.set(190);
        assertThrows(AiInstallationReadiness.Unavailable.class,state::requireReady);
        assertThrows(AiInstallationReadiness.Unavailable.class,state::verified);
    }
    @Test void refreshCannotReviveAnExpiredInstallationEvenWithoutAnInterveningRequest() {
        var clock=new AtomicLong();var state=new AiInstallationReadiness(clock::get,100);
        state.verified();clock.set(100);
        assertThrows(AiInstallationReadiness.Unavailable.class,state::verified);
        assertThrows(AiInstallationReadiness.Unavailable.class,state::requireReady);
    }
    @Test void closeDuringVerificationPermanentlyWithholdsReadiness() {
        var state=new AiInstallationReadiness(()->0,100);state.verified();state.close();
        assertThrows(AiInstallationReadiness.Unavailable.class,state::verified);
        assertThrows(AiInstallationReadiness.Unavailable.class,state::requireReady);
    }
    @Test void monotonicClockWrapDoesNotExtendOrPrematurelyExpireTheLease() {
        var clock=new AtomicLong(Long.MAX_VALUE-10);var state=new AiInstallationReadiness(clock::get,100);
        state.verified();clock.addAndGet(99);state.requireReady();clock.incrementAndGet();
        assertThrows(AiInstallationReadiness.Unavailable.class,state::requireReady);
    }
}
